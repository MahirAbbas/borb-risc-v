package borb

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.fetch._
import borb.fetch.FrontendRedirectReason
import borb.backend.{
  BackendIssue,
  BackendPipe,
  FpBackend,
  IntegerBackend,
  PipelineSlot,
  RetirePacket,
  TrapCsrBackend,
  TrapRedirectOutcome
}
import borb.frontend.Decoder
import borb.frontend.Decoder._
import borb.dispatch._
import borb.dispatch.IssueSemantics
import borb.execute.IntAlu
import borb.execute.IntAlu._
import borb.execute.IntMisc
import borb.execute.IntMulDiv
import borb.execute.{DataBus, DataSideCache, WriteBack}
import borb.execute.vector.VectorBackend
import borb.dispatch.SrcPlugin
import borb.dispatch.SrcPlugin._
import borb.formal._
import spinal.core.sim._
import spinal.lib.bus.amba4.axi._
import borb.core.CpuConfig
import spinal.lib.misc.plugin.PluginHost
import borb.common.MicroCode._
import borb.common.{LaneContracts, LaneKey}

object CPU {
  def main(args: Array[String]) {
    val config = SpinalConfig(
      targetDirectory = "formal/cores/borb"
    )
    config.generateSystemVerilog(
      new CPU(
        CpuConfig.default.copy(
          perfCountersEnabled = false,
          debugEnabled = false,
          pmpImplementedEntries = 4,
          vmShadowEntries = 8,
          vmTablePageEntries = 4
        )
      )
    )
  }
}

case class CPU(config: CpuConfig = CpuConfig.default) extends Component {
  private val cpuAxiConfig = Axi4Config(
    addressWidth = config.xlen,
    dataWidth = config.xlen,
    idWidth = 16,
    useId = true,
    useRegion = false,
    useLock = false,
    useQos = false,
    useProt = false,
    useCache = false
  )

  val io = new Bundle {
    val clk = in port Bool()
    val clkEnable = in port Bool()
    val reset = in port Bool()
    val iAxi = master(Axi4Shared(cpuAxiConfig))
    val dAxi = master(Axi4Shared(cpuAxiConfig)).simPublic()
    val rvfi = out(Rvfi()).simPublic()
    val dbg = out(DebugArea())
    val perf = out(borb.core.PerfCountersBundle())
  }

  // We use a ClockingArea to handle the provided clock/reset
  val coreClockDomain = ClockDomain(
    io.clk,
    reset = io.reset,
    clockEnable = io.clkEnable,
    config = ClockDomainConfig(
      clockEdge = RISING,
      resetKind = SYNC,
      resetActiveLevel = HIGH
    )
  )

  val coreArea = new ClockingArea(coreClockDomain) {
    val pipeline = new StageCtrlPipeline()
    private val pcStageId = 0
    private val fetchCmdStageId = 1
    private val fetchRspStageId = 2
    private val decodeStageId = 3
    private val dispatchStageId = 4
    private val srcStageId = 5
    private val execStageId = 6
    private val wbStageId = 7

    val pcCtrl = pipeline.ctrl(pcStageId)
    val fetchCmdCtrl = pipeline.ctrl(fetchCmdStageId)
    val fetchRspCtrl = pipeline.ctrl(fetchRspStageId)
    val decodeCtrl = pipeline.ctrl(decodeStageId)
    val dispatchCtrl = pipeline.ctrl(dispatchStageId)
    val srcCtrl = pipeline.ctrl(srcStageId)
    val execStage = pipeline.ctrl(execStageId)
    val wbStage = pipeline.ctrl(wbStageId)

    import borb.common.Common._
    val resetPcValue = BigInt("80000000", 16)

    private case class PipelinePayloadReg(
        fromStage: Int,
        untilStage: Int,
        initPayload: CtrlLink => Unit
    )

    private def pipelinePayloadReg[T <: Data](
        payload: Payload[T],
        fromStage: Int,
        untilStage: Int = Int.MaxValue
    )(initPayload: T => Unit): PipelinePayloadReg = {
      PipelinePayloadReg(
        fromStage = fromStage,
        untilStage = untilStage,
        initPayload = ctrl => initPayload(ctrl.up(payload))
      )
    }

    private def pipelinePayloadReg[T <: Data](
        payload: Payload[T],
        key: Any,
        fromStage: Int,
        untilStage: Int
    )(initPayload: T => Unit): PipelinePayloadReg = {
      PipelinePayloadReg(
        fromStage = fromStage,
        untilStage = untilStage,
        initPayload = ctrl => initPayload(ctrl.up(payload, key))
      )
    }

    private def pipelinePayloadReg[T <: Data](
        payload: Payload[T],
        key: Any,
        fromStage: Int
    )(initPayload: T => Unit): PipelinePayloadReg = {
      pipelinePayloadReg(payload, key, fromStage, Int.MaxValue)(initPayload)
    }

    private def registerPipelinePayload(spec: PipelinePayloadReg): Unit = {
      pipeline.ctrls
        .filter { case (id, _) =>
          id >= spec.fromStage && id < spec.untilStage
        }
        .foreach { case (_, ctrl) =>
          spec.initPayload(ctrl)
        }
    }

    Seq(
      pipelinePayloadReg(LANE_ID, fetchRspStageId)(_.setAsReg().init(0)),
      pipelinePayloadReg(LANE_MASK, fetchRspStageId)(_.setAsReg().init(B"00")),
      pipelinePayloadReg(SELF_REDIRECT, LaneKey.Lane0, wbStageId)( _.setAsReg().init(False)),
      pipelinePayloadReg(SELF_REDIRECT, LaneKey.Lane1, wbStageId)( _.setAsReg().init(False)),
      pipelinePayloadReg(TRAP, LaneKey.Lane0, wbStageId)( _.setAsReg().init(False)),
      pipelinePayloadReg(TRAP, LaneKey.Lane1, wbStageId)(
        _.setAsReg().init(False)
      ),
      pipelinePayloadReg(COMMIT, LaneKey.Lane0, wbStageId)(
        _.setAsReg().init(False)
      ),
      pipelinePayloadReg(COMMIT, LaneKey.Lane1, wbStageId)(
        _.setAsReg().init(False)
      ),

      // Lane-qualified frontend payloads.
      pipelinePayloadReg(borb.fetch.PC.PC, LaneKey.Lane0, decodeStageId)(
        _.setAsReg().init(0)
      ),
      pipelinePayloadReg(borb.fetch.PC.PC, LaneKey.Lane1, decodeStageId)(
        _.setAsReg().init(0)
      ),
      pipelinePayloadReg(
        borb.frontend.Decoder.INSTRUCTION,
        LaneKey.Lane0,
        decodeStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.frontend.Decoder.INSTRUCTION,
        LaneKey.Lane1,
        decodeStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(SPEC_EPOCH, LaneKey.Lane0, decodeStageId)(
        _.setAsReg().init(0)
      ),
      pipelinePayloadReg(SPEC_EPOCH, LaneKey.Lane1, decodeStageId)(
        _.setAsReg().init(0)
      ),
      pipelinePayloadReg(
        borb.fetch.Fetch.FETCH_SEQ,
        LaneKey.Lane0,
        decodeStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.fetch.Fetch.FETCH_SEQ,
        LaneKey.Lane1,
        decodeStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.fetch.Fetch.FETCH_BUNDLE_SEQ,
        LaneKey.Lane0,
        decodeStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.fetch.Fetch.FETCH_BUNDLE_SEQ,
        LaneKey.Lane1,
        decodeStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.fetch.Fetch.FETCH_FTQ_IDX,
        LaneKey.Lane0,
        decodeStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.fetch.Fetch.FETCH_FTQ_IDX,
        LaneKey.Lane1,
        decodeStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.fetch.Fetch.FETCH_SLOT_IDX,
        LaneKey.Lane0,
        decodeStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.fetch.Fetch.FETCH_SLOT_IDX,
        LaneKey.Lane1,
        decodeStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.fetch.Fetch.FETCH_SLOT_COUNT,
        LaneKey.Lane0,
        decodeStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.fetch.Fetch.FETCH_SLOT_COUNT,
        LaneKey.Lane1,
        decodeStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.fetch.Fetch.FETCH_BLOCK_PC,
        LaneKey.Lane0,
        decodeStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.fetch.Fetch.FETCH_BLOCK_PC,
        LaneKey.Lane1,
        decodeStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.fetch.Fetch.FETCH_BYTE_OFFSET,
        LaneKey.Lane0,
        decodeStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.fetch.Fetch.FETCH_BYTE_OFFSET,
        LaneKey.Lane1,
        decodeStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.fetch.Fetch.FETCH_PREDICTED_VALID,
        LaneKey.Lane0,
        decodeStageId
      )(_.setAsReg().init(False)),
      pipelinePayloadReg(
        borb.fetch.Fetch.FETCH_PREDICTED_VALID,
        LaneKey.Lane1,
        decodeStageId
      )(_.setAsReg().init(False)),
      pipelinePayloadReg(
        borb.fetch.Fetch.FETCH_PREDICTED_TAKEN,
        LaneKey.Lane0,
        decodeStageId
      )(_.setAsReg().init(False)),
      pipelinePayloadReg(
        borb.fetch.Fetch.FETCH_PREDICTED_TAKEN,
        LaneKey.Lane1,
        decodeStageId
      )(_.setAsReg().init(False)),
      pipelinePayloadReg(
        borb.fetch.Fetch.FETCH_PREDICTED_TARGET,
        LaneKey.Lane0,
        decodeStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.fetch.Fetch.FETCH_PREDICTED_TARGET,
        LaneKey.Lane1,
        decodeStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.frontend.Decoder.DECODED_INSTRUCTION,
        LaneKey.Lane0,
        dispatchStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.frontend.Decoder.DECODED_INSTRUCTION,
        LaneKey.Lane1,
        dispatchStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.frontend.Decoder.IS_COMPRESSED,
        LaneKey.Lane0,
        dispatchStageId
      )(_.setAsReg().init(False)),
      pipelinePayloadReg(
        borb.frontend.Decoder.IS_COMPRESSED,
        LaneKey.Lane1,
        dispatchStageId
      )(_.setAsReg().init(False)),
      pipelinePayloadReg(
        borb.frontend.Decoder.LEGAL,
        LaneKey.Lane0,
        dispatchStageId
      )(_.setAsReg().init(borb.frontend.YESNO.N)),
      pipelinePayloadReg(
        borb.frontend.Decoder.LEGAL,
        LaneKey.Lane1,
        dispatchStageId
      )(_.setAsReg().init(borb.frontend.YESNO.N)),
      pipelinePayloadReg(
        borb.frontend.Decoder.IS_FLOAT,
        LaneKey.Lane0,
        dispatchStageId
      )(_.setAsReg().init(borb.frontend.YESNO.N)),
      pipelinePayloadReg(
        borb.frontend.Decoder.IS_FLOAT,
        LaneKey.Lane1,
        dispatchStageId
      )(_.setAsReg().init(borb.frontend.YESNO.N)),
      pipelinePayloadReg(
        borb.frontend.Decoder.IS_VEC,
        LaneKey.Lane0,
        dispatchStageId
      )(_.setAsReg().init(borb.frontend.YESNO.N)),
      pipelinePayloadReg(
        borb.frontend.Decoder.IS_VEC,
        LaneKey.Lane1,
        dispatchStageId
      )(_.setAsReg().init(borb.frontend.YESNO.N)),
      pipelinePayloadReg(
        borb.frontend.Decoder.USES_LDQ,
        LaneKey.Lane0,
        dispatchStageId
      )(_.setAsReg().init(borb.frontend.YESNO.N)),
      pipelinePayloadReg(
        borb.frontend.Decoder.USES_LDQ,
        LaneKey.Lane1,
        dispatchStageId
      )(_.setAsReg().init(borb.frontend.YESNO.N)),
      pipelinePayloadReg(
        borb.frontend.Decoder.USES_STQ,
        LaneKey.Lane0,
        dispatchStageId
      )(_.setAsReg().init(borb.frontend.YESNO.N)),
      pipelinePayloadReg(
        borb.frontend.Decoder.USES_STQ,
        LaneKey.Lane1,
        dispatchStageId
      )(_.setAsReg().init(borb.frontend.YESNO.N)),
      pipelinePayloadReg(
        borb.frontend.Decoder.MicroCode,
        LaneKey.Lane0,
        dispatchStageId
      )(_.setAsReg().init(borb.common.MicroCode.uopNOP)),
      pipelinePayloadReg(
        borb.frontend.Decoder.MicroCode,
        LaneKey.Lane1,
        dispatchStageId
      )(_.setAsReg().init(borb.common.MicroCode.uopNOP)),
      pipelinePayloadReg(
        borb.frontend.Decoder.RD_ADDR,
        LaneKey.Lane0,
        dispatchStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.frontend.Decoder.RD_ADDR,
        LaneKey.Lane1,
        dispatchStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.frontend.Decoder.RS1_ADDR,
        LaneKey.Lane0,
        dispatchStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.frontend.Decoder.RS1_ADDR,
        LaneKey.Lane1,
        dispatchStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.frontend.Decoder.RS2_ADDR,
        LaneKey.Lane0,
        dispatchStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.frontend.Decoder.RS2_ADDR,
        LaneKey.Lane1,
        dispatchStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.frontend.Decoder.RS3_ADDR,
        LaneKey.Lane0,
        dispatchStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.frontend.Decoder.RS3_ADDR,
        LaneKey.Lane1,
        dispatchStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.frontend.Decoder.VALID,
        LaneKey.Lane0,
        dispatchStageId
      )(_.setAsReg().init(False)),
      pipelinePayloadReg(
        borb.frontend.Decoder.VALID,
        LaneKey.Lane1,
        dispatchStageId
      )(_.setAsReg().init(False)),
      pipelinePayloadReg(
        borb.frontend.Decoder.DECODE_ILLEGAL,
        LaneKey.Lane0,
        dispatchStageId
      )(_.setAsReg().init(False)),
      pipelinePayloadReg(
        borb.frontend.Decoder.DECODE_ILLEGAL,
        LaneKey.Lane1,
        dispatchStageId
      )(_.setAsReg().init(False)),
      pipelinePayloadReg(LANE_SEL, LaneKey.Lane0, srcStageId)(
        _.setAsReg().init(False)
      ),
      pipelinePayloadReg(LANE_SEL, LaneKey.Lane1, srcStageId)(
        _.setAsReg().init(False)
      ),
      pipelinePayloadReg(IssueSemantics.PROPS, LaneKey.Lane0, srcStageId)(
        _.setAsReg().init(IssuePropertyBundle().getZero)
      ),
      pipelinePayloadReg(IssueSemantics.PROPS, LaneKey.Lane1, srcStageId)(
        _.setAsReg().init(IssuePropertyBundle().getZero)
      ),
      pipelinePayloadReg(BackendIssue.SELECTED_PIPE, LaneKey.Lane0, srcStageId)(
        _.setAsReg().init(BackendPipe.None)
      ),
      pipelinePayloadReg(BackendIssue.SELECTED_PIPE, LaneKey.Lane1, srcStageId)(
        _.setAsReg().init(BackendPipe.None)
      ),
      pipelinePayloadReg(
        borb.dispatch.Dispatch.SENDTOALU,
        LaneKey.Lane0,
        srcStageId
      )(_.setAsReg().init(False)),
      pipelinePayloadReg(
        borb.dispatch.Dispatch.SENDTOALU,
        LaneKey.Lane1,
        srcStageId
      )(_.setAsReg().init(False)),
      pipelinePayloadReg(
        borb.dispatch.Dispatch.SENDTOBRANCH,
        LaneKey.Lane0,
        srcStageId
      )(_.setAsReg().init(False)),
      pipelinePayloadReg(
        borb.dispatch.Dispatch.SENDTOBRANCH,
        LaneKey.Lane1,
        srcStageId
      )(_.setAsReg().init(False)),
      pipelinePayloadReg(
        borb.dispatch.Dispatch.SENDTOAGU,
        LaneKey.Lane0,
        srcStageId
      )(_.setAsReg().init(False)),
      pipelinePayloadReg(
        borb.dispatch.Dispatch.SENDTOAGU,
        LaneKey.Lane1,
        srcStageId
      )(_.setAsReg().init(False)),
      pipelinePayloadReg(
        borb.dispatch.SrcPlugin.RS1,
        LaneKey.Lane0,
        execStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.dispatch.SrcPlugin.RS1,
        LaneKey.Lane1,
        execStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.dispatch.SrcPlugin.RS2,
        LaneKey.Lane0,
        execStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.dispatch.SrcPlugin.RS2,
        LaneKey.Lane1,
        execStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.dispatch.SrcPlugin.IMMED,
        LaneKey.Lane0,
        execStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.dispatch.SrcPlugin.IMMED,
        LaneKey.Lane1,
        execStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.execute.WriteBack.RESULT,
        LaneKey.Lane0,
        wbStageId
      )(_.setAsReg().init(borb.dispatch.RegFileWrite().getZero)),
      pipelinePayloadReg(
        borb.execute.WriteBack.RESULT,
        LaneKey.Lane1,
        wbStageId
      )(_.setAsReg().init(borb.dispatch.RegFileWrite().getZero)),
      pipelinePayloadReg(
        borb.execute.Branch.BRANCH_TAKEN,
        LaneKey.Lane0,
        wbStageId
      )(_.setAsReg().init(False)),
      pipelinePayloadReg(
        borb.execute.Branch.BRANCH_TAKEN,
        LaneKey.Lane1,
        wbStageId
      )(_.setAsReg().init(False)),
      pipelinePayloadReg(
        borb.execute.Branch.BRANCH_TARGET,
        LaneKey.Lane0,
        wbStageId
      )(_.setAsReg().init(0)),
      pipelinePayloadReg(
        borb.execute.Branch.BRANCH_TARGET,
        LaneKey.Lane1,
        wbStageId
      )(_.setAsReg().init(0))
    ).foreach(registerPipelinePayload)

    // Global speculation epoch. Keep this wide enough to avoid wraparound
    // aliasing under branch-heavy tests.
    val currentEpoch = Reg(UInt(16 bits)) init 0

    val pc = new PC(
      pcCtrl,
      addressWidth = 64,
      withCompressed = config.cExtensionEnabled,
      resetPc = resetPcValue
    )
    // pc.jump.setIdle()
    pc.exception.setIdle()
    pc.flush.setIdle()
    pc.redirect.setIdle()
    val fetch = Fetch(
      fetchCmdCtrl,
      fetchRspCtrl,
      addressWidth = 64,
      dataWidth = 64,
      idWidth = config.fetchIdWidth,
      withCompressed = config.cExtensionEnabled,
      fetchBufferDepth = 16,
      xlen = config.xlen,
      frontendConfig = config.frontendConfig
    )
    fetch.io.recover.valid.allowOverride := False
    fetch.io.recover.payload.assignDontCare()
    fetch.io.branchResolve.valid.allowOverride := False
    fetch.io.branchResolve.payload.assignDontCare()
    fetch.io.indirectResolve.valid.allowOverride := False
    fetch.io.indirectResolve.payload.assignDontCare()
    fetch.io.bundleConsume.allowOverride := False
    fetch.io.bundleHold.allowOverride := False
    fetch.io.bundleDeferRefill.allowOverride := False
    fetch.io.suppressPrefetch.allowOverride := False
    pc.sequentialValid := fetch.io.pcAdvance
    pc.sequentialStep := fetch.io.pcStep
    // RAM is external (via io.iAxi/io.dAxi)

    val decode = new Decoder(
      decodeCtrl,
      withCompressed = config.cExtensionEnabled,
      xlen = config.xlen
    )

    val integerBackend = IntegerBackend(execStage, wbStage)
    // TODO(execute): build the shared issue backend that uses FunctionalUnit
    // metadata to route lane-selected operands/results. FUs intentionally do
    // not know about lanes.
    val intalu = IntAlu()
    val intMisc = IntMisc()
    val intMulDiv = IntMulDiv()
    val executeIssueDefaults = new execStage.Area {
      for (laneId <- 0 until Decoder.LANES) {
        val laneKey = LaneKey(laneId)
        down(WriteBack.RESULT, laneKey).valid := False
        down(WriteBack.RESULT, laneKey).address := 0
        down(WriteBack.RESULT, laneKey).data := 0
      }
    }
    val branch = new borb.execute.Branch(
      execStage,
      pc,
      withCompressed = config.cExtensionEnabled
    )
    val lsuKillOutstanding = RegInit(False)
    val lsuKillCboZero = Bool()
    val lsu = new borb.execute.Lsu(
      execStage,
      wbStage,
      currentEpoch,
      lsuKillOutstanding,
      lsuKillCboZero
    )

    val lastCommittedSeqValid = Reg(Bits(2 bits)) init (0)
    val lastCommittedSeq0 = Reg(UInt(32 bits)) init (0)
    val lastCommittedSeq1 = Reg(UInt(32 bits)) init (0)

    val lsuBus = DataBus(addressWidth = 64, dataWidth = 64, idWidth = 16)

    val perfCounters =
      if (config.perfCountersEnabled)
        Some(new borb.core.PerfCountersPlugin(wbStage))
      else None
    val perfCounterOutputs = perfCounters
      .map(_.counters)
      .getOrElse(borb.core.PerfCountersBundle().getZero)
    io.perf := perfCounterOutputs

    val trapLogic = TrapCsrBackend(
      execStage,
      wbStage,
      config,
      currentEpoch,
      pc,
      fetch,
      branch,
      lsu,
      perfCounterOutputs
    )
    val fpBackend = FpBackend(execStage, lsu, currentEpoch, trapLogic.frm)
    val vectorBackend = VectorBackend(
      execStage,
      lsu,
      lsuBus,
      currentEpoch,
      config,
      trapLogic.vectorContext,
      resetPcValue
    )
    val functionalUnits = Seq(
      intalu,
      intMisc,
      intMulDiv,
      branch,
      lsu,
      trapLogic,
      fpBackend,
      vectorBackend
    )
    for (fu <- functionalUnits; laneId <- 0 until Decoder.LANES) {
      val laneKey = LaneKey(laneId)
      pipeline.ctrls.filter(_._1 >= srcStageId).foreach { case (_, ctrl) =>
        ctrl.up(fu.SEL, laneKey).setAsReg().init(False)
      }
    }
    val hazardRange = Seq(dispatchCtrl, srcCtrl, execStage, wbStage)
    val dispatcher = new Dispatch(
      dispatchCtrl,
      hazardRange,
      pipeline,
      functionalUnits = functionalUnits,
      intBypassReady = Seq(
        False,
        integerBackend.exeBypassReady,
        integerBackend.wbIntBypass.valid
      )
    )
    val srcPlugin = new SrcPlugin(
      srcCtrl,
      integerBackend.exeIntBypasses ++ integerBackend.wbIntBypasses
    )

    trapLogic.fpFlagsSetValid := fpBackend.fpFlags.valid
    trapLogic.fpFlagsSetBits := fpBackend.fpFlags.bits
    trapLogic.vectorMemoryComplete := vectorBackend.memoryComplete
    trapLogic.vectorMemoryTrapValid := vectorBackend.memoryTrapValid
    trapLogic.vectorMemoryTrapIsStore := vectorBackend.memoryTrapIsStore
    trapLogic.vectorMemoryTrapTval := vectorBackend.memoryTrapTval
    trapLogic.vectorMemoryTrapElement := vectorBackend.memoryTrapElement

    decode.branchResolved := branch.branchResolved

    val relaxIntProducerHazards = True
    val relaxControlFlowSerialization = True

    val exeCtrl = execStage
    val wbCtrl = wbStage
    def laneIsLive(ctrl: CtrlLink, laneId: Int): Bool = {
      val laneKey = LaneKey(laneId)
      ctrl.up.isValid &&
      (ctrl.up(SPEC_EPOCH, laneKey) === currentEpoch) &&
      ctrl(Decoder.VALID, laneKey) &&
      ctrl(borb.common.Common.LANE_SEL, laneKey)
    }
    val srcHasControlFlow = (0 until Decoder.LANES)
      .map { laneId =>
        laneIsLive(srcCtrl, laneId) && srcCtrl(
          IssueSemantics.PROPS,
          LaneKey(laneId)
        ).isControlFlow
      }
      .reduce(_ || _)
    val exeHasControlFlow = (0 until Decoder.LANES)
      .map { laneId =>
        laneIsLive(exeCtrl, laneId) && exeCtrl(
          IssueSemantics.PROPS,
          LaneKey(laneId)
        ).isControlFlow
      }
      .reduce(_ || _)
    val controlHazardBusy = srcHasControlFlow || exeHasControlFlow
    when(!relaxControlFlowSerialization) {
      Array
        .range(decodeStageId, dispatchStageId + 1)
        .map(pipeline.ctrl(_))
        .foreach { ctrl =>
          ctrl.haltWhen(controlHazardBusy)
        }
      srcCtrl.haltWhen(exeHasControlFlow)
    }
    for (laneId <- 0 until Decoder.LANES) {
      val laneKey = LaneKey(laneId)
      when(
        srcCtrl.up.isValid && (srcCtrl.up(SPEC_EPOCH, laneKey) =/= currentEpoch)
      ) {
        srcCtrl.up(LANE_SEL, laneKey).allowOverride := False
      }
    }
    for (laneId <- 0 until Decoder.LANES) {
      val laneKey = LaneKey(laneId)
      when(
        wbStage.up.isValid &&
          (wbStage.up(SPEC_EPOCH, laneKey) =/= currentEpoch) &&
          !wbStage.up(SELF_REDIRECT, laneKey)
      ) {
        wbStage.up(LANE_SEL, laneKey).allowOverride := False
        wbStage.up(COMMIT, laneKey).allowOverride := False
      }
    }

    val exeIntProducer = exeCtrl.up.isValid &&
      exeCtrl(Decoder.VALID, LaneKey.Lane0) &&
      exeCtrl(borb.common.Common.LANE_SEL, LaneKey.Lane0) &&
      exeCtrl(IssueSemantics.PROPS, LaneKey.Lane0).writesIntRd &&
      (exeCtrl(Decoder.RD_ADDR, LaneKey.Lane0) =/= 0)
    val srcNeedsExeRdRs1 =
      srcCtrl(IssueSemantics.PROPS, LaneKey.Lane0).readsIntRs1 &&
        (srcCtrl(Decoder.RS1_ADDR, LaneKey.Lane0) === exeCtrl(
          Decoder.RD_ADDR,
          LaneKey.Lane0
        ))
    val srcNeedsExeRdRs2 =
      srcCtrl(IssueSemantics.PROPS, LaneKey.Lane0).readsIntRs2 &&
        (srcCtrl(Decoder.RS2_ADDR, LaneKey.Lane0) === exeCtrl(
          Decoder.RD_ADDR,
          LaneKey.Lane0
        ))
    when(!relaxIntProducerHazards) {
      srcCtrl.haltWhen(exeIntProducer && (srcNeedsExeRdRs1 || srcNeedsExeRdRs2))
    }

    val wbIntProducer = wbCtrl.up.isValid &&
      wbCtrl(Decoder.VALID, LaneKey.Lane0) &&
      wbCtrl(borb.common.Common.LANE_SEL, LaneKey.Lane0) &&
      wbCtrl(IssueSemantics.PROPS, LaneKey.Lane0).writesIntRd &&
      (wbCtrl(Decoder.RD_ADDR, LaneKey.Lane0) =/= 0)
    val srcNeedsWbRdRs1 =
      srcCtrl(IssueSemantics.PROPS, LaneKey.Lane0).readsIntRs1 &&
        (srcCtrl(Decoder.RS1_ADDR, LaneKey.Lane0) === wbCtrl(
          Decoder.RD_ADDR,
          LaneKey.Lane0
        ))
    val srcNeedsWbRdRs2 =
      srcCtrl(IssueSemantics.PROPS, LaneKey.Lane0).readsIntRs2 &&
        (srcCtrl(Decoder.RS2_ADDR, LaneKey.Lane0) === wbCtrl(
          Decoder.RD_ADDR,
          LaneKey.Lane0
        ))
    when(!relaxIntProducerHazards) {
      srcCtrl.haltWhen(wbIntProducer && (srcNeedsWbRdRs1 || srcNeedsWbRdRs2))
    }

    val suppressPrefetchInTrapService = RegInit(False)
    when(trapLogic.redirect.trapFire) {
      suppressPrefetchInTrapService := True
    } elsewhen (trapLogic.redirect.mretFire) {
      suppressPrefetchInTrapService := False
    }
    val suppressPrefetchBase =
      suppressPrefetchInTrapService ||
        trapLogic.redirect.trapFire ||
        trapLogic.redirect.mretFire

    // ========== Speculation Epoch Architecture ==========
    // Clean, scalable speculation handling for in-order dual-lane CPU
    //
    // Design:
    // - Global epoch counter maintained here, passed to Fetch
    // - Each instruction is tagged with SPEC_EPOCH when it enters the pipeline
    // - When a branch is TAKEN (flushPipeline), epoch increments
    // - All instructions with old epoch are flushed (their SPEC_EPOCH != currentEpoch)

    // Flush Logic - fires when a non-stale branch/jump redirects.
    val branchResolvedLane1 = branch.logic.selectedLane1
    def selectedBranchPayload[T <: Data](payload: Payload[T]): T =
      Mux(
        branchResolvedLane1,
        execStage.up(payload, LaneKey.Lane1),
        execStage.up(payload, LaneKey.Lane0)
      )

    val execEpochMatches = selectedBranchPayload(SPEC_EPOCH) === currentEpoch
    val execStageValid = execStage.up.isValid
    val fenceiRedirect = execStageValid &&
      execStage.up.isFiring &&
      (0 until Decoder.LANES)
        .map { laneId =>
          val laneKey = LaneKey(laneId)
          (execStage(SPEC_EPOCH, laneKey) === currentEpoch) &&
          execStage(Decoder.VALID, laneKey) &&
          execStage(borb.common.Common.LANE_SEL, laneKey) &&
          (execStage(Decoder.MicroCode, laneKey) === uopFENCE_I)
        }
        .reduce(_ || _)
    val predictedValid = selectedBranchPayload(
      borb.fetch.Fetch.FETCH_PREDICTED_VALID
    )
    val predictedTaken = selectedBranchPayload(
      borb.fetch.Fetch.FETCH_PREDICTED_TAKEN
    )
    val predictedTarget = selectedBranchPayload(
      borb.fetch.Fetch.FETCH_PREDICTED_TARGET
    )
    val frontendPredictedRedirectsEnabled =
      config.frontendConfig.predictedRedirectEnabled
    val frontendPredictorTrainingEnabled =
      config.frontendConfig.predictorTrainingEnabled
    val actualTaken = branch.actualTaken
    val actualTarget = branch.actualTarget
    val controlResolved = branch.branchResolved && execEpochMatches
    val branchMispredict = controlResolved && (
      (actualTaken =/= predictedTaken) ||
        (actualTaken && predictedTaken && (actualTarget =/= predictedTarget))
    )
    val branchRedirect = branchMispredict && !trapLogic.redirect.trapFire
    val flushPipeline = branchRedirect
    val trapRedirect =
      execStageValid && trapLogic.redirect.trapFire && execEpochMatches
    val mretRedirect =
      execStageValid && trapLogic.redirect.mretFire && execEpochMatches
    val redirectPipeline =
      flushPipeline || trapRedirect || mretRedirect || fenceiRedirect
    execStage.down(
      SELF_REDIRECT,
      LaneKey.Lane0
    ) := (branchRedirect && !branchResolvedLane1) || trapRedirect || mretRedirect || fenceiRedirect
    execStage.down(
      SELF_REDIRECT,
      LaneKey.Lane1
    ) := branchRedirect && branchResolvedLane1
    val fenceiTarget = selectedBranchPayload(borb.fetch.PC.PC) + U(4, 64 bits)
    val redirectEpochValue = (currentEpoch + 1).resized

    pc.redirect.valid.allowOverride := branchRedirect || mretRedirect || fenceiRedirect
    pc.redirect.payload.target := Mux(
      mretRedirect,
      trapLogic.redirect.mretTarget,
      Mux(
        fenceiRedirect,
        fenceiTarget,
        Mux(actualTaken, actualTarget, branch.fallthroughPc)
      )
    )
    pc.redirect.payload.reason := FrontendRedirectReason.branch
    when(mretRedirect) {
      pc.redirect.payload.reason := FrontendRedirectReason.mret
    } elsewhen (fenceiRedirect) {
      pc.redirect.payload.reason := FrontendRedirectReason.fencei
    }
    pc.redirect.payload.epoch := redirectEpochValue
    pc.redirect.payload.flushFrontend := True
    pc.jump.valid := pc.redirect.valid
    pc.jump.payload.target := pc.redirect.payload.target
    pc.jump.payload.is_jump := mretRedirect || fenceiRedirect || branch.actualIsJump
    pc.jump.payload.is_branch := (!mretRedirect) && (!fenceiRedirect) && branch.actualIsBranch

    // Increment epoch on taken branch
    when(branchRedirect) {
      currentEpoch := currentEpoch + 1
    }
    when(trapRedirect) {
      currentEpoch := currentEpoch + 1
    }
    when(mretRedirect) {
      currentEpoch := currentEpoch + 1
    }
    when(fenceiRedirect) {
      currentEpoch := currentEpoch + 1
    }
    def stageDecodedValid(idx: Int): Bool = {
      val ctrl = pipeline.ctrl(idx)
      ctrl.up.isValid && (0 until Decoder.LANES)
        .map { laneId =>
          val laneKey = LaneKey(laneId)
          if (idx == decodeStageId) ctrl(Decoder.VALID, laneKey)
          else ctrl.up(Decoder.VALID, laneKey)
        }
        .reduce(_ || _)
    }
    def stageLogicallyLive(idx: Int): Bool = {
      val ctrl = pipeline.ctrl(idx)
      ctrl.up.isValid && (0 until Decoder.LANES)
        .map { laneId =>
          val laneKey = LaneKey(laneId)
          val decodedValid =
            if (idx == decodeStageId) ctrl(Decoder.VALID, laneKey)
            else ctrl.up(Decoder.VALID, laneKey)
          val laneLive =
            if (idx < srcStageId) True else ctrl.up(LANE_SEL, laneKey)
          decodedValid && laneLive
        }
        .reduce(_ || _)
    }
    val wbStageValidForRedirect = stageLogicallyLive(wbStageId)
    val redirectingBundleSeq = UInt(32 bits)
    redirectingBundleSeq := selectedBranchPayload(
      borb.fetch.Fetch.FETCH_BUNDLE_SEQ
    )
    val redirectingSlotIdx = UInt(2 bits)
    redirectingSlotIdx := selectedBranchPayload(borb.fetch.Fetch.FETCH_SLOT_IDX)
    val redirectingSeq = UInt(32 bits)
    redirectingSeq := selectedBranchPayload(borb.fetch.Fetch.FETCH_SEQ)
    val wbLane1LiveForRedirect =
      wbStage.up.isValid && wbStage.up(Decoder.VALID, LaneKey.Lane1) && wbStage
        .up(LANE_SEL, LaneKey.Lane1)
    val wbBundleSeq = Mux(
      wbLane1LiveForRedirect,
      wbStage.up(borb.fetch.Fetch.FETCH_BUNDLE_SEQ, LaneKey.Lane1),
      wbStage.up(borb.fetch.Fetch.FETCH_BUNDLE_SEQ, LaneKey.Lane0)
    )
    val wbSlotIdx = Mux(
      wbLane1LiveForRedirect,
      wbStage.up(borb.fetch.Fetch.FETCH_SLOT_IDX, LaneKey.Lane1),
      wbStage.up(borb.fetch.Fetch.FETCH_SLOT_IDX, LaneKey.Lane0)
    )
    val redirectRspBubbleCounter = Reg(UInt(2 bits)) init (0)
    val redirectCommitPending = RegInit(False)
    val redirectCommitSeq = Reg(UInt(32 bits)) init (0)
    val redirectCommitBundleSeq = Reg(UInt(32 bits)) init (0)
    val redirectCommitSlotIdx = Reg(UInt(2 bits)) init (0)
    val rspOldEpoch = pipeline.ctrl(2).up.isValid &&
      (0 until Decoder.LANES)
        .map(laneId =>
          pipeline.ctrl(2)(SPEC_EPOCH, LaneKey(laneId)) =/= currentEpoch
        )
        .reduce(_ || _)
    when(rspOldEpoch) {
      pipeline.ctrl(2).up.valid.allowOverride := False
    }
    val wbYoungerThanRedirect = wbStageValidForRedirect &&
      execStage.up.isValid &&
      (0 until Decoder.LANES)
        .map(laneId => execStage.up(Decoder.VALID, LaneKey(laneId)))
        .reduce(_ || _) &&
      LaneContracts.isYounger(
        wbBundleSeq,
        wbSlotIdx,
        redirectingBundleSeq,
        redirectingSlotIdx
      )
    def stageIsRedirectOrigin(idx: Int): Bool = {
      val ctrl = pipeline.ctrl(idx)
      redirectCommitPending &&
      ctrl.up.isValid &&
      (0 until Decoder.LANES)
        .map { laneId =>
          val laneKey = LaneKey(laneId)
          LaneContracts.sameSlot(
            ctrl.up(borb.fetch.Fetch.FETCH_BUNDLE_SEQ, laneKey),
            ctrl.up(borb.fetch.Fetch.FETCH_SLOT_IDX, laneKey),
            redirectCommitBundleSeq,
            redirectCommitSlotIdx
          )
        }
        .reduce(_ || _)
    }
    val youngerThanPendingRedirect = Array
      .range(decodeStageId, wbStageId + 1)
      .map { idx =>
        val ctrl = pipeline.ctrl(idx)
        stageLogicallyLive(idx) &&
        !stageIsRedirectOrigin(idx) &&
        (0 until Decoder.LANES)
          .map { laneId =>
            val laneKey = LaneKey(laneId)
            (ctrl.up(SPEC_EPOCH, laneKey) =/= currentEpoch) &&
            LaneContracts.isYounger(
              ctrl.up(borb.fetch.Fetch.FETCH_BUNDLE_SEQ, laneKey),
              ctrl.up(borb.fetch.Fetch.FETCH_SLOT_IDX, laneKey),
              redirectCommitBundleSeq,
              redirectCommitSlotIdx
            )
          }
          .reduce(_ || _)
      }
      .reduce(_ || _)
    val staleEpochBehindRedirect = Array
      .range(decodeStageId, wbStageId + 1)
      .map { idx =>
        val ctrl = pipeline.ctrl(idx)
        stageLogicallyLive(idx) &&
        !stageIsRedirectOrigin(idx) &&
        (0 until Decoder.LANES)
          .map(laneId => ctrl.up(SPEC_EPOCH, LaneKey(laneId)) =/= currentEpoch)
          .reduce(_ || _)
      }
      .reduce(_ || _)
    when(redirectPipeline) {
      redirectRspBubbleCounter := U(2, redirectRspBubbleCounter.getWidth bits)
      redirectCommitPending := True
      redirectCommitSeq := redirectingSeq
      redirectCommitBundleSeq := redirectingBundleSeq
      redirectCommitSlotIdx := redirectingSlotIdx.resized
    } elsewhen (redirectRspBubbleCounter =/= 0) {
      redirectRspBubbleCounter := redirectRspBubbleCounter - 1
    }
    val rspResident = pipeline.ctrl(2).up.isValid
    when(
      redirectCommitPending && !redirectPipeline && !rspResident && !rspOldEpoch && !staleEpochBehindRedirect && !youngerThanPendingRedirect
    ) {
      redirectCommitPending := False
    }
    val redirectRspPending = redirectRspBubbleCounter =/= 0
    val wbYoungerThanPendingRedirect = redirectCommitPending &&
      wbStageValidForRedirect &&
      (0 until Decoder.LANES)
        .map(laneId => wbStage.up(SPEC_EPOCH, LaneKey(laneId)) =/= currentEpoch)
        .reduce(_ || _) &&
      LaneContracts.isYounger(
        wbBundleSeq,
        wbSlotIdx,
        redirectCommitBundleSeq,
        redirectCommitSlotIdx
      )
    lsuKillOutstanding := redirectPipeline || redirectCommitPending
    lsuKillCboZero := redirectPipeline
    when(
      execStage.up.isValid &&
        (0 until Decoder.LANES)
          .map(laneId => execStage.up(Decoder.VALID, LaneKey(laneId)))
          .reduce(_ || _) &&
        redirectCommitPending &&
        (0 until Decoder.LANES)
          .map(laneId =>
            execStage.up(SPEC_EPOCH, LaneKey(laneId)) =/= currentEpoch
          )
          .reduce(_ || _)
    ) {
      for (laneId <- 0 until Decoder.LANES) {
        execStage.up(LANE_SEL, LaneKey(laneId)).allowOverride := False
      }
    }
    // A taken redirect discovered in execute can coincide with a wrong-path
    // younger instruction already sitting in writeback. Squash that commit in
    // the same cycle, then keep the existing next-cycle bubble to catch any
    // younger instruction that would otherwise slide forward one stage later.
    val redirectCommitBubble =
      (redirectPipeline && wbYoungerThanRedirect) || wbYoungerThanPendingRedirect
    // A redirecting stage-6 instruction can otherwise allow a younger
    // same-epoch payload to slide forward one stage before its redirect is
    // observed. Track the redirecting sequence so only younger instructions
    // are flushed; older lagging instructions must still be allowed to retire.
    val redirectExecuteBubble = redirectCommitPending &&
      execStage.up.isValid &&
      (0 until Decoder.LANES)
        .map { laneId =>
          val laneKey = LaneKey(laneId)
          execStage.up(Decoder.VALID, laneKey) &&
          (execStage.up(SPEC_EPOCH, laneKey) =/= currentEpoch) &&
          LaneContracts.isYounger(
            execStage.up(borb.fetch.Fetch.FETCH_BUNDLE_SEQ, laneKey),
            execStage.up(borb.fetch.Fetch.FETCH_SLOT_IDX, laneKey),
            redirectCommitBundleSeq,
            redirectCommitSlotIdx
          )
        }
        .reduce(_ || _)

    // Connect epoch to Fetch so new instructions get tagged with current epoch
    fetch.io.currentEpoch := currentEpoch
    val branchBlockPc = UInt(64 bits)
    branchBlockPc := selectedBranchPayload(borb.fetch.PC.PC)
    if (config.frontendConfig.fetchBlockBytes > 1) {
      branchBlockPc(
        log2Up(config.frontendConfig.fetchBlockBytes) - 1 downto 0
      ) := 0
    }
    val takenByteOffset = UInt(
      log2Up(config.frontendConfig.fetchBlockBytes max 2) bits
    )
    takenByteOffset := selectedBranchPayload(borb.fetch.PC.PC)(
      log2Up(config.frontendConfig.fetchBlockBytes max 2) - 1 downto 0
    )
    val isCall = branch.actualIsJump &&
      (
        (selectedBranchPayload(
          Decoder.MicroCode
        ) === uopJAL && ((selectedBranchPayload(
          Decoder.RD_ADDR
        ) === B"00001") || (selectedBranchPayload(
          Decoder.RD_ADDR
        ) === B"00101"))) ||
          (selectedBranchPayload(
            Decoder.MicroCode
          ) === uopJALR && ((selectedBranchPayload(
            Decoder.RD_ADDR
          ) === B"00001") || (selectedBranchPayload(
            Decoder.RD_ADDR
          ) === B"00101")))
      )
    val isReturn = (selectedBranchPayload(Decoder.MicroCode) === uopJALR) &&
      (selectedBranchPayload(Decoder.RD_ADDR) === B"00000") &&
      ((selectedBranchPayload(
        Decoder.RS1_ADDR
      ) === B"00001") || (selectedBranchPayload(Decoder.RS1_ADDR) === B"00101"))
    val isIndirect =
      (selectedBranchPayload(Decoder.MicroCode) === uopJALR) && !isReturn
    fetch.io.recover.valid := redirectPipeline
    fetch.io.recover.payload.redirectTarget := Mux(
      trapRedirect,
      pc.exception.payload.vector,
      Mux(
        mretRedirect,
        trapLogic.redirect.mretTarget,
        Mux(
          fenceiRedirect,
          fenceiTarget,
          Mux(actualTaken, actualTarget, branch.fallthroughPc)
        )
      )
    )
    fetch.io.recover.payload.redirectReason := FrontendRedirectReason.branch
    when(trapRedirect) {
      fetch.io.recover.payload.redirectReason := FrontendRedirectReason.trap
    } elsewhen (mretRedirect) {
      fetch.io.recover.payload.redirectReason := FrontendRedirectReason.mret
    } elsewhen (fenceiRedirect) {
      fetch.io.recover.payload.redirectReason := FrontendRedirectReason.fencei
    }
    fetch.io.recover.payload.epoch := redirectEpochValue
    fetch.io.recover.payload.invalidateIcache := fenceiRedirect
    fetch.io.recover.payload.recovery.valid := execStage.up.isValid
    fetch.io.recover.payload.recovery.ftqIndex := selectedBranchPayload(
      borb.fetch.Fetch.FETCH_FTQ_IDX
    ).resized
    fetch.io.recover.payload.recovery.bundleSeq := selectedBranchPayload(
      borb.fetch.Fetch.FETCH_BUNDLE_SEQ
    )
    fetch.io.recover.payload.recovery.slotIdx := selectedBranchPayload(
      borb.fetch.Fetch.FETCH_SLOT_IDX
    ).resized
    fetch.io.recover.payload.recovery.blockPc := selectedBranchPayload(
      borb.fetch.Fetch.FETCH_BLOCK_PC
    )
    fetch.io.recover.payload.recovery.byteOffsetInBlock := selectedBranchPayload(
      borb.fetch.Fetch.FETCH_BYTE_OFFSET
    ).resized

    fetch.io.branchResolve.valid := branch.branchResolved && execEpochMatches
    fetch.io.branchResolve.payload.epoch := currentEpoch
    fetch.io.branchResolve.payload.ftqIndex := selectedBranchPayload(
      borb.fetch.Fetch.FETCH_FTQ_IDX
    ).resized
    fetch.io.branchResolve.payload.bundleSeq := selectedBranchPayload(
      borb.fetch.Fetch.FETCH_BUNDLE_SEQ
    )
    fetch.io.branchResolve.payload.slotIdx := selectedBranchPayload(
      borb.fetch.Fetch.FETCH_SLOT_IDX
    ).resized
    fetch.io.branchResolve.payload.pc := selectedBranchPayload(borb.fetch.PC.PC)
    fetch.io.branchResolve.payload.blockPc := selectedBranchPayload(
      borb.fetch.Fetch.FETCH_BLOCK_PC
    )
    fetch.io.branchResolve.payload.byteOffsetInBlock := selectedBranchPayload(
      borb.fetch.Fetch.FETCH_BYTE_OFFSET
    ).resized
    fetch.io.branchResolve.payload.fallthrough := branch.fallthroughPc
    fetch.io.branchResolve.payload.actualTaken := actualTaken
    fetch.io.branchResolve.payload.actualTarget := actualTarget
    fetch.io.branchResolve.payload.predictedValid := predictedValid
    fetch.io.branchResolve.payload.predictedTaken := predictedTaken
    fetch.io.branchResolve.payload.predictedTarget := predictedTarget
    fetch.io.branchResolve.payload.mispredict := branchMispredict
    fetch.io.branchResolve.payload.isConditional := branch.actualIsBranch
    fetch.io.branchResolve.payload.isJump := branch.actualIsJump
    fetch.io.branchResolve.payload.isCall := isCall
    fetch.io.branchResolve.payload.isReturn := isReturn
    fetch.io.branchResolve.payload.isIndirect := isIndirect

    fetch.io.indirectResolve.valid := branch.branchResolved && execEpochMatches && isIndirect && actualTaken
    fetch.io.indirectResolve.payload.epoch := currentEpoch
    fetch.io.indirectResolve.payload.ftqIndex := selectedBranchPayload(
      borb.fetch.Fetch.FETCH_FTQ_IDX
    ).resized
    fetch.io.indirectResolve.payload.bundleSeq := selectedBranchPayload(
      borb.fetch.Fetch.FETCH_BUNDLE_SEQ
    )
    fetch.io.indirectResolve.payload.slotIdx := selectedBranchPayload(
      borb.fetch.Fetch.FETCH_SLOT_IDX
    ).resized
    fetch.io.indirectResolve.payload.blockPc := selectedBranchPayload(
      borb.fetch.Fetch.FETCH_BLOCK_PC
    )
    fetch.io.indirectResolve.payload.byteOffsetInBlock := selectedBranchPayload(
      borb.fetch.Fetch.FETCH_BYTE_OFFSET
    ).resized
    fetch.io.indirectResolve.payload.target := actualTarget
    fetch.io.indirectResolve.payload.history := 0
    fetch.io.bundleHold := redirectPipeline || redirectRspPending || redirectCommitPending || rspOldEpoch
    fetch.io.bundleDeferRefill := False
    fetch.io.bundleConsume := fetchRspCtrl.down.isFiring &&
      !(redirectPipeline || redirectRspPending || redirectCommitPending || rspOldEpoch)

    // Flush fetch/decode/src younger stages on redirect so a new target beat
    // cannot be consumed against a stale stage-local PC offset.
    // Note: Stage 6 (Execute) is excluded from unconditional redirect kill -
    //       the redirecting instruction executes. Stage 7 is now explicitly
    //       seq-filtered below so younger wrong-path writeback occupants are
    //       dropped instead of merely having their commit suppressed.
    pipeline
      .ctrl(1)
      .throwWhen(
        redirectPipeline || redirectRspPending || redirectCommitPending || rspOldEpoch
      )
    // Stage 2 sits before sequence tagging becomes fully instruction-local, so
    // on a redirect it is always younger than execute and must be dropped
    // unconditionally. Hold it in bubble state until the seq-tracked younger
    // backend stages are drained; otherwise a stale fetch packet fetched before the
    // redirect can still slip forward after the short rsp-only bubble expires.
    val stage2Kill =
      redirectPipeline || redirectRspPending || redirectCommitPending || rspOldEpoch
    pipeline.ctrl(2).throwWhen(stage2Kill)
    when(stage2Kill) {
      pipeline.ctrl(2).up.valid.allowOverride := False
    }
    Array.range(decodeStageId, srcStageId + 1).map(pipeline.ctrl(_)).foreach {
      ctrl =>
        val stageOldEpoch = ctrl.up.isValid && (0 until Decoder.LANES)
          .map(laneId => ctrl.up(SPEC_EPOCH, LaneKey(laneId)) =/= currentEpoch)
          .reduce(_ || _)
        val stageKill =
          redirectPipeline || redirectRspPending || redirectCommitPending || stageOldEpoch
        ctrl.throwWhen(stageKill)
        when(stageKill) {
          ctrl.up.valid.allowOverride := False
          for (laneId <- 0 until Decoder.LANES) {
            val laneKey = LaneKey(laneId)
            val stageDecodedValid =
              if (ctrl == decodeCtrl) ctrl(Decoder.VALID, laneKey)
              else ctrl.up(Decoder.VALID, laneKey)
            stageDecodedValid.allowOverride := False
            if (ctrl == srcCtrl) {
              ctrl.up(LANE_SEL, laneKey).allowOverride := False
            }
          }
        }
    }
    val executeOldEpoch = execStage.up.isValid &&
      (0 until Decoder.LANES)
        .map { laneId =>
          val laneKey = LaneKey(laneId)
          execStage.up(Decoder.VALID, laneKey) &&
          (execStage.up(SPEC_EPOCH, laneKey) =/= currentEpoch)
        }
        .reduce(_ || _)
    val wbOldEpoch = wbStage.up.isValid &&
      (0 until Decoder.LANES)
        .map { laneId =>
          val laneKey = LaneKey(laneId)
          wbStage.up(Decoder.VALID, laneKey) &&
          (wbStage.up(SPEC_EPOCH, laneKey) =/= currentEpoch) &&
          !wbStage.up(SELF_REDIRECT, laneKey)
        }
        .reduce(_ || _)
    val executeKill = redirectExecuteBubble || executeOldEpoch
    val wbKill =
      (redirectPipeline && wbYoungerThanRedirect) || wbYoungerThanPendingRedirect || wbOldEpoch
    execStage.throwWhen(executeKill)
    wbStage.throwWhen(wbKill)
    when(executeKill) {
      execStage.up.valid.allowOverride := False
      for (laneId <- 0 until Decoder.LANES) {
        val laneKey = LaneKey(laneId)
        execStage.up(Decoder.VALID, laneKey).allowOverride := False
        execStage.up(LANE_SEL, laneKey).allowOverride := False
      }
    }
    when(wbKill) {
      wbStage.up.valid.allowOverride := False
      for (laneId <- 0 until Decoder.LANES) {
        val laneKey = LaneKey(laneId)
        wbStage.up(Decoder.VALID, laneKey).allowOverride := False
        wbStage.up(LANE_SEL, laneKey).allowOverride := False
        wbStage.up(COMMIT, laneKey).allowOverride := False
        wbStage.up(TRAP, laneKey).allowOverride := False
      }
    }

    val writebackStage = wbStage
    def buildRetirePacket(laneId: Int): RetirePacket = {
      val laneKey = LaneKey(laneId)
      val packet = RetirePacket(config)
      val result = writebackStage.up(WriteBack.RESULT, laneKey)
      packet.valid := writebackStage.up(COMMIT, laneKey)
      packet.slot.valid := packet.valid
      packet.slot.epoch := writebackStage.up(SPEC_EPOCH, laneKey)
      packet.slot.fetchSeq := writebackStage.up(
        borb.fetch.Fetch.FETCH_SEQ,
        laneKey
      )
      packet.slot.olderSeq := 0
      packet.slot.bundleSeq := writebackStage.up(
        borb.fetch.Fetch.FETCH_BUNDLE_SEQ,
        laneKey
      )
      packet.slot.slotIdx := writebackStage
        .up(borb.fetch.Fetch.FETCH_SLOT_IDX, laneKey)
        .resized
      packet.slot.slotCount := writebackStage
        .up(borb.fetch.Fetch.FETCH_SLOT_COUNT, laneKey)
        .resized
      packet.slot.ftqIdx := writebackStage
        .up(borb.fetch.Fetch.FETCH_FTQ_IDX, laneKey)
        .resized
      packet.slot.pc := writebackStage.up(borb.fetch.PC.PC, laneKey)
      packet.slot.blockPc := writebackStage.up(
        borb.fetch.Fetch.FETCH_BLOCK_PC,
        laneKey
      )
      packet.slot.byteOffset := writebackStage
        .up(borb.fetch.Fetch.FETCH_BYTE_OFFSET, laneKey)
        .resized
      packet.slot.predictedValid := writebackStage.up(
        borb.fetch.Fetch.FETCH_PREDICTED_VALID,
        laneKey
      )
      packet.slot.predictedTaken := writebackStage.up(
        borb.fetch.Fetch.FETCH_PREDICTED_TAKEN,
        laneKey
      )
      packet.slot.predictedTarget := writebackStage.up(
        borb.fetch.Fetch.FETCH_PREDICTED_TARGET,
        laneKey
      )
      packet.slot.decodedInstruction := writebackStage.up(
        Decoder.DECODED_INSTRUCTION,
        laneKey
      )
      packet.slot.isCompressed := writebackStage.up(
        Decoder.IS_COMPRESSED,
        laneKey
      )
      packet.slot.legal := writebackStage.up(Decoder.LEGAL, laneKey)
      packet.slot.microCode := writebackStage.up(Decoder.MicroCode, laneKey)
      packet.slot.rdAddr := writebackStage.up(Decoder.RD_ADDR, laneKey)
      packet.slot.rs1Addr := writebackStage.up(Decoder.RS1_ADDR, laneKey)
      packet.slot.rs2Addr := writebackStage.up(Decoder.RS2_ADDR, laneKey)
      packet.slot.rs3Addr := writebackStage.up(Decoder.RS3_ADDR, laneKey)
      packet.slot.issueProps := writebackStage.up(IssueSemantics.PROPS, laneKey)
      packet.slot.waitForOlderCommit := False
      packet.slot.selectedPipe := writebackStage.up(
        BackendIssue.SELECTED_PIPE,
        laneKey
      )
      packet.slot.rs1 := writebackStage.up(SrcPlugin.RS1, laneKey)
      packet.slot.rs2 := writebackStage.up(SrcPlugin.RS2, laneKey)
      packet.slot.immed := 0
      packet.slot.sendToAlu := writebackStage.up(
        borb.dispatch.Dispatch.SENDTOALU,
        laneKey
      )
      packet.slot.sendToBranch := writebackStage.up(
        borb.dispatch.Dispatch.SENDTOBRANCH,
        laneKey
      )
      packet.slot.branchTaken := False
      packet.slot.branchTarget := 0
      packet.slot.branchIsBranch := False
      packet.slot.branchIsJump := False
      packet.slot.fallthrough := 0
      packet.slot.result := result
      packet.slot.trap := (if (laneId == 0) trapLogic.redirect
                           else TrapRedirectOutcome().getZero)
      packet.slot.commit := packet.valid
      packet.intWrite.valid := packet.valid && result.valid
      packet.intWrite.address := result.address
      packet.intWrite.data := result.data
      packet.fpWrite.valid := (if (laneId == 0)
                                 packet.valid && fpBackend.fpWrite.valid
                               else False)
      packet.fpWrite.address := fpBackend.fpWrite.address
      packet.fpWrite.data := fpBackend.fpWrite.data
      packet.fpFlags.valid := (if (laneId == 0)
                                 packet.valid && fpBackend.fpFlags.valid
                               else False)
      packet.fpFlags.bits := fpBackend.fpFlags.bits
      packet.storeCommit := packet.valid && writebackStage
        .up(IssueSemantics.PROPS, laneKey)
        .isStore
      packet.trap := (if (laneId == 0) trapLogic.redirect
                      else TrapRedirectOutcome().getZero)
      packet
    }

    val retirePackets = Vec(RetirePacket(config), 2)
    retirePackets(0) := buildRetirePacket(0)
    retirePackets(1) := buildRetirePacket(1)

    val rvfiPlugin = new RvfiPlugin(wbStage)
    io.rvfi := rvfiPlugin.io.rvfi

    val redirectProbe = borb.RedirectDebugProbe()
    redirectProbe.execEpochMatches := execEpochMatches
    redirectProbe.branchRedirect := flushPipeline
    redirectProbe.trapRedirect := trapRedirect
    redirectProbe.mretRedirect := mretRedirect
    redirectProbe.redirectPipeline := redirectPipeline
    redirectProbe.pcJumpValid := pc.jump.valid
    redirectProbe.pcJumpTarget := pc.jump.payload.target
    redirectProbe.pcExceptionValid := pc.exception.valid
    redirectProbe.pcExceptionTarget := pc.exception.payload.vector
    redirectProbe.liveTrapCause := trapLogic.redirect.trapCause
    redirectProbe.liveTrapTval := trapLogic.redirect.trapTval

    val debugOutputs =
      if (config.debugEnabled) {
        Some(
          new DebugPlugin(pipeline, trapLogic.redirect, redirectProbe).io.dbg
        )
      } else {
        None
      }
    io.dbg := debugOutputs.getOrElse(DebugArea().getZero)

    // Once a sequence has committed, any lingering older-stage copy of that
    // same instruction is stale and must be killed before it can refire side
    // effects or traps. This keeps backend ownership of a sequence exclusive.
    when(retirePackets(0).valid && retirePackets(1).valid) {
      lastCommittedSeqValid := B"11"
      lastCommittedSeq0 := retirePackets(0).slot.fetchSeq
      lastCommittedSeq1 := retirePackets(1).slot.fetchSeq
    } elsewhen (retirePackets(0).valid) {
      lastCommittedSeqValid := B"11"
      lastCommittedSeq1 := lastCommittedSeq0
      lastCommittedSeq0 := retirePackets(0).slot.fetchSeq
    } elsewhen (retirePackets(1).valid) {
      lastCommittedSeqValid := B"11"
      lastCommittedSeq1 := lastCommittedSeq0
      lastCommittedSeq0 := retirePackets(1).slot.fetchSeq
    }
    Array.range(dispatchStageId, wbStageId + 1).foreach { idx =>
      val ctrl = pipeline.ctrl(idx)
      val duplicateLiveSeq = (0 until Decoder.LANES)
        .map { laneId =>
          val laneKey = LaneKey(laneId)
          Array
            .range(idx + 1, wbStageId + 1)
            .map { laterIdx =>
              val laterCtrl = pipeline.ctrl(laterIdx)
              stageLogicallyLive(idx) &&
              stageLogicallyLive(laterIdx) &&
              ctrl.up(Decoder.VALID, laneKey) &&
              laterCtrl.up(Decoder.VALID, laneKey) &&
              (ctrl.up(borb.fetch.Fetch.FETCH_SEQ, laneKey) === laterCtrl.up(
                borb.fetch.Fetch.FETCH_SEQ,
                laneKey
              ))
            }
            .reduceOption(_ || _)
            .getOrElse(False)
        }
        .reduce(_ || _)
      val staleCommittedSeq = (0 until Decoder.LANES)
        .map { laneId =>
          val laneKey = LaneKey(laneId)
          lastCommittedSeqValid.orR &&
          ctrl.up.isValid &&
          ctrl.up(Decoder.VALID, laneKey) &&
          (
            (lastCommittedSeqValid(0) && (ctrl.up(
              borb.fetch.Fetch.FETCH_SEQ,
              laneKey
            ) === lastCommittedSeq0)) ||
              (lastCommittedSeqValid(1) && (ctrl.up(
                borb.fetch.Fetch.FETCH_SEQ,
                laneKey
              ) === lastCommittedSeq1))
          )
        }
        .reduce(_ || _)
      val duplicateSeqKill = staleCommittedSeq || duplicateLiveSeq
      ctrl.throwWhen(duplicateSeqKill)
      when(duplicateSeqKill) {
        ctrl.up.valid.allowOverride := False
        for (laneId <- 0 until Decoder.LANES) {
          val laneKey = LaneKey(laneId)
          ctrl.up(Decoder.VALID, laneKey).allowOverride := False
          if (idx >= srcStageId) {
            ctrl.up(LANE_SEL, laneKey).allowOverride := False
          }
          if (idx >= wbStageId) {
            ctrl.up(COMMIT, laneKey).allowOverride := False
            ctrl.up(TRAP, laneKey).allowOverride := False
          }
        }
      }
    }
    perfCounters.foreach(
      _.wireFromCore(
        pipeline = pipeline,
        decodeStageId = decodeStageId,
        wbStageId = wbStageId,
        dispatchCtrl = dispatchCtrl,
        srcCtrl = srcCtrl,
        execStage = execStage,
        dispatcher = dispatcher,
        fetch = fetch,
        lsu = lsu,
        branch = branch,
        retirePackets = retirePackets,
        controlHazardBusy = controlHazardBusy,
        flushPipeline = flushPipeline
      )
    )

    val write = wbStage
    import borb.execute.WriteBack
    val writeback = new WriteBack(
      wbStage,
      srcPlugin.regfileread.regfile.io.writes,
      currentEpoch,
      redirectCommitBubble,
      redirectCommitPending,
      redirectCommitSeq
    )
    val wbArea = new write.Area {
      // Expose signals for simulation
      srcPlugin.regfileread.regfile.io.simPublic()
      fetch.io.iAxi.arw.simPublic()
      fetch.io.iAxi.r.simPublic()
      pc.PC_cur.simPublic()

      val simDebug = new borb.formal.SimDebugPlugin(
        write,
        pipeline,
        dispatcher,
        branch,
        fetch
      )
    }

    val dcache = DataSideCache(lsuBus, io.dAxi)
    val dStoreAddrArbitrating = dcache.writeAddressArbitrating
    fetch.io.suppressPrefetch.allowOverride := suppressPrefetchBase || dStoreAddrArbitrating
    // Do not withdraw an already-arbitrating fetch request under the shared AXI
    // arbiter. Suppressing new prefetches is enough; masking the outgoing valid
    // here can strand the arbiter on the fetch input while a store waits.

    fetch.io.iAxi <> io.iAxi

    pipeline.ctrls.drop(1).foreach(e => e._2.throwWhen(clockDomain.reset))
    // Build the pipeline
    pipeline.build()
  }
}
