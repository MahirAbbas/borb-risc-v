package borb

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.fetch._
import borb.backend.{FpBackend, IntegerBackend, TrapCsrBackend}
import borb.frontend.Decoder
import borb.frontend.Decoder._
import borb.dispatch._
import borb.execute.IntAlu
import borb.execute.IntAlu._
import borb.execute.{DataBus, DataBusCmd}
import borb.dispatch.SrcPlugin
import borb.dispatch.SrcPlugin._
import borb.formal._
import spinal.core.sim._
import spinal.lib.bus.amba4.axi._
import borb.core.CpuConfig
import spinal.lib.misc.plugin.PluginHost
import borb.common.MicroCode._

object CPU {
  def main(args: Array[String]) {
    val config = SpinalConfig(
      targetDirectory = "formal/cores/borb"
    )
    config.generateSystemVerilog(new CPU())
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

    // Defaults for Execution Stages: LANE_SEL is False if not propagated (Bubble)
    import borb.common.Common._
    pipeline.ctrls.filter(_._1 >= 5).foreach { 
      case (id, ctrl) => ctrl.up(LANE_SEL).setAsReg().init(False)

    }
    pipeline.ctrls.filter(e => e._1 >= 5 && e._1 < 7).foreach { 
      case(id, ctrl) => ctrl.up(COMMIT).setAsReg().init(False)
    }
    pipeline.ctrls.filter(_._1 >= 7).foreach {
      case (_, ctrl) => ctrl.up(SELF_REDIRECT).setAsReg().init(False)
    }
    val resetPcValue = BigInt("80000000", 16)

    // Keep speculation epoch instruction-local across stalls/flushes.
    pipeline.ctrls.filter(_._1 >= 3).foreach {
      case (_, ctrl) => ctrl.up(SPEC_EPOCH).setAsReg().init(0)
    }
    pipeline.ctrls.filter(e => e._1 >= 1 && e._1 < 3).foreach {
      case (_, ctrl) => ctrl.up(borb.fetch.PC.PC).setAsReg().init(resetPcValue)
    }
    // Keep PC instruction-local across stalls/flushes so execute-stage control
    // flow uses the PC that belongs to that instruction.
    pipeline.ctrls.filter(_._1 >= 3).foreach {
      case (_, ctrl) => ctrl.up(borb.fetch.PC.PC).setAsReg().init(0)
    }
    // Keep fetched instruction instruction-local starting at the fetch
    // response stage so mixed-width fetch cannot present a newer halfword
    // boundary under an older PC at the stage-2/3 handoff.
    pipeline.ctrls.filter(_._1 >= 3).foreach {
      case (_, ctrl) => ctrl.up(borb.frontend.Decoder.INSTRUCTION).setAsReg().init(0)
    }
    pipeline.ctrls.filter(_._1 >= 3).foreach {
      case (_, ctrl) => ctrl.up(borb.fetch.Fetch.FETCH_SEQ).setAsReg().init(0)
    }
    // Keep decode outputs instruction-local once they leave decode. Otherwise
    // a stalled downstream instruction can observe a newer decode result while
    // still carrying the older PC/epoch payloads.
    pipeline.ctrls.filter(_._1 >= 4).foreach {
      case (_, ctrl) => ctrl.up(borb.frontend.Decoder.DECODED_INSTRUCTION).setAsReg().init(0)
    }
    pipeline.ctrls.filter(_._1 >= 4).foreach {
      case (_, ctrl) => ctrl.up(borb.frontend.Decoder.IS_COMPRESSED).setAsReg().init(False)
    }
    pipeline.ctrls.filter(_._1 >= 4).foreach {
      case (_, ctrl) => ctrl.up(borb.frontend.Decoder.LEGAL).setAsReg().init(borb.frontend.YESNO.N)
    }
    pipeline.ctrls.filter(_._1 >= 4).foreach {
      case (_, ctrl) => ctrl.up(borb.frontend.Decoder.IS_FP).setAsReg().init(borb.frontend.YESNO.N)
    }
    pipeline.ctrls.filter(_._1 >= 4).foreach {
      case (_, ctrl) => ctrl.up(borb.frontend.Decoder.EXECUTION_UNIT).setAsReg().init(borb.frontend.ExecutionUnitEnum.NA)
    }
    pipeline.ctrls.filter(_._1 >= 4).foreach {
      case (_, ctrl) => ctrl.up(borb.frontend.Decoder.RDTYPE).setAsReg().init(borb.frontend.REGFILE.RDTYPE.RD_NA)
    }
    pipeline.ctrls.filter(_._1 >= 4).foreach {
      case (_, ctrl) => ctrl.up(borb.frontend.Decoder.RS1TYPE).setAsReg().init(borb.frontend.REGFILE.RSTYPE.RS_NA)
    }
    pipeline.ctrls.filter(_._1 >= 4).foreach {
      case (_, ctrl) => ctrl.up(borb.frontend.Decoder.RS2TYPE).setAsReg().init(borb.frontend.REGFILE.RSTYPE.RS_NA)
    }
    pipeline.ctrls.filter(_._1 >= 4).foreach {
      case (_, ctrl) => ctrl.up(borb.frontend.Decoder.FSR3EN).setAsReg().init(borb.frontend.YESNO.N)
    }
    pipeline.ctrls.filter(_._1 >= 4).foreach {
      case (_, ctrl) => ctrl.up(borb.frontend.Decoder.IMMSEL).setAsReg().init(borb.frontend.Imm_Select.N_IMM)
    }
    pipeline.ctrls.filter(_._1 >= 4).foreach {
      case (_, ctrl) => ctrl.up(borb.frontend.Decoder.MicroCode).setAsReg().init(borb.common.MicroCode.uopNOP)
    }
    pipeline.ctrls.filter(_._1 >= 4).foreach {
      case (_, ctrl) => ctrl.up(borb.frontend.Decoder.IS_BR).setAsReg().init(borb.frontend.YESNO.N)
    }
    pipeline.ctrls.filter(_._1 >= 4).foreach {
      case (_, ctrl) => ctrl.up(borb.frontend.Decoder.IS_W).setAsReg().init(borb.frontend.YESNO.N)
    }
    pipeline.ctrls.filter(_._1 >= 4).foreach {
      case (_, ctrl) => ctrl.up(borb.frontend.Decoder.USE_LDQ).setAsReg().init(borb.frontend.YESNO.N)
    }
    pipeline.ctrls.filter(_._1 >= 4).foreach {
      case (_, ctrl) => ctrl.up(borb.frontend.Decoder.USE_STQ).setAsReg().init(borb.frontend.YESNO.N)
    }
    pipeline.ctrls.filter(_._1 >= 4).foreach {
      case (_, ctrl) => ctrl.up(borb.frontend.Decoder.RD_ADDR).setAsReg().init(0)
    }
    pipeline.ctrls.filter(_._1 >= 4).foreach {
      case (_, ctrl) => ctrl.up(borb.frontend.Decoder.RS1_ADDR).setAsReg().init(0)
    }
    pipeline.ctrls.filter(_._1 >= 4).foreach {
      case (_, ctrl) => ctrl.up(borb.frontend.Decoder.RS2_ADDR).setAsReg().init(0)
    }
    pipeline.ctrls.filter(_._1 >= 4).foreach {
      case (_, ctrl) => ctrl.up(borb.frontend.Decoder.VALID).setAsReg().init(False)
    }
    // Keep dispatch lane routing instruction-local once an instruction leaves
    // dispatch. Otherwise a stalled backend instruction can observe a newer
    // execution-unit selection and execute through the wrong side-effect path.
    pipeline.ctrls.filter(_._1 >= 5).foreach {
      case (_, ctrl) => ctrl.up(borb.dispatch.Dispatch.SENDTOALU).setAsReg().init(False)
    }
    pipeline.ctrls.filter(_._1 >= 5).foreach {
      case (_, ctrl) => ctrl.up(borb.dispatch.Dispatch.SENDTOBRANCH).setAsReg().init(False)
    }
    pipeline.ctrls.filter(_._1 >= 5).foreach {
      case (_, ctrl) => ctrl.up(borb.dispatch.Dispatch.SENDTOAGU).setAsReg().init(False)
    }
    // Keep resolved operands instruction-local once they leave the source
    // stage. Otherwise a held execute-stage instruction can observe a newer
    // regfile/bypass value and re-execute with different operands.
    pipeline.ctrls.filter(_._1 >= 6).foreach {
      case (_, ctrl) => ctrl.up(borb.dispatch.SrcPlugin.RS1).setAsReg().init(0)
    }
    pipeline.ctrls.filter(_._1 >= 6).foreach {
      case (_, ctrl) => ctrl.up(borb.dispatch.SrcPlugin.RS2).setAsReg().init(0)
    }
    pipeline.ctrls.filter(_._1 >= 6).foreach {
      case (_, ctrl) => ctrl.up(borb.dispatch.SrcPlugin.IMMED).setAsReg().init(0)
    }

    // Global speculation epoch. Keep this wide enough to avoid wraparound
    // aliasing under branch-heavy tests.
    val currentEpoch = Reg(UInt(16 bits)) init 0

    val pc = new PC(
      pipeline.ctrl(0),
      addressWidth = 64,
      withCompressed = config.cExtensionEnabled,
      resetPc = resetPcValue
    )
    //pc.jump.setIdle()
    pc.exception.setIdle()
    pc.flush.setIdle()
    val fetch = Fetch(
      pipeline.ctrl(1),
      pipeline.ctrl(2),
      addressWidth = 64,
      dataWidth = 64,
      idWidth = config.fetchIdWidth,
      withCompressed = config.cExtensionEnabled,
      fetchBufferDepth = 16,
      xlen = config.xlen
    )
    pc.sequentialValid := fetch.io.pcAdvance
    pc.sequentialStep := fetch.io.pcStep
    // RAM is external (via io.iAxi/io.dAxi)

    val decode = new Decoder(pipeline.ctrl(3), withCompressed = config.cExtensionEnabled, xlen = config.xlen)

    val execStage = pipeline.ctrl(6)
    val integerBackend = IntegerBackend(pipeline.ctrl(6), pipeline.ctrl(7))
    val hazardRange = Array(4, 5, 6, 7).map(e => pipeline.ctrl(e)).toSeq
    val dispatcher = new Dispatch(
      pipeline.ctrl(4),
      hazardRange,
      pipeline,
      intBypassReady = Seq(False, integerBackend.exeBypassReady, integerBackend.wbIntBypass.valid)
    )
    val srcPlugin = new SrcPlugin(pipeline.ctrl(5), Seq(integerBackend.exeIntBypass, integerBackend.wbIntBypass))
    val intalu = new IntAlu(pipeline.ctrl(6))
    val branch = new borb.execute.Branch(pipeline.ctrl(6), pc, withCompressed = config.cExtensionEnabled)
    val lsu = new borb.execute.Lsu(pipeline.ctrl(6), pipeline.ctrl(7), currentEpoch)

    val lsuBus = DataBus(addressWidth = 64, dataWidth = 64, idWidth = 16)
    lsuBus.cmd << lsu.io.dBus.cmd
    lsu.io.dBus.rsp << lsuBus.rsp

    val perfCounters = new borb.core.PerfCountersPlugin(pipeline.ctrl(7))
    io.perf := perfCounters.counters

    val trapLogic = TrapCsrBackend(execStage, pipeline.ctrl(7), config, currentEpoch, pc, branch, lsu, perfCounters.counters)
    val fpBackend = FpBackend(execStage, lsu, currentEpoch, trapLogic.frm)

    trapLogic.fpFlagsSetValid := fpBackend.fpFlags.valid
    trapLogic.fpFlagsSetBits := fpBackend.fpFlags.bits

    decode.branchResolved := branch.branchResolved

    // Keep younger instructions from overtaking unresolved control-flow ops.
    // This is conservative, but it closes the wrong-path window while the
    // front-end/control-flow cleanup is still in progress.
    val srcCtrl = pipeline.ctrl(5)
    val exeCtrl = pipeline.ctrl(6)
    val wbCtrl = pipeline.ctrl(7)
    val srcHasControlFlow = srcCtrl.up.isValid &&
      srcCtrl(Decoder.VALID) &&
      srcCtrl(borb.common.Common.LANE_SEL) &&
      (srcCtrl(Decoder.EXECUTION_UNIT) === borb.frontend.ExecutionUnitEnum.BR)
    val exeHasControlFlow = exeCtrl.up.isValid &&
      exeCtrl(Decoder.VALID) &&
      exeCtrl(borb.common.Common.LANE_SEL) &&
      (exeCtrl(Decoder.EXECUTION_UNIT) === borb.frontend.ExecutionUnitEnum.BR)

    def isSerializingSystem(ctrl: CtrlLink): Bool = {
      val valid = ctrl.up.isValid && ctrl(Decoder.VALID) && ctrl(borb.common.Common.LANE_SEL)
      val micro = ctrl(Decoder.MicroCode)
      val insn = ctrl(Decoder.INSTRUCTION)
      valid && (
        (micro === uopFENCE_I) ||
        (micro === uopECALL) ||
        (micro === uopEBREAK) ||
        (micro === uopSRET) ||
        (micro === uopSFENCEVMA) ||
        (micro === uopCSRRW) ||
        (micro === uopCSRRS) ||
        (micro === uopCSRRC) ||
        (micro === uopCSRRWI) ||
        (micro === uopCSRRSI) ||
        (micro === uopCSRRCI) ||
        (insn === B"32'h30200073") ||
        (insn === B"32'h10200073")
      )
    }

    val srcHasSystem = isSerializingSystem(srcCtrl)
    val exeHasSystem = isSerializingSystem(exeCtrl)
    val controlHazardBusy = srcHasControlFlow || exeHasControlFlow || srcHasSystem || exeHasSystem
    Array(2, 3, 4).map(pipeline.ctrl(_)).foreach { ctrl =>
      ctrl.haltWhen(controlHazardBusy)
    }
    srcCtrl.haltWhen(exeHasControlFlow || exeHasSystem)

    val exeIntProducer = exeCtrl.up.isValid &&
      exeCtrl(Decoder.VALID) &&
      exeCtrl(borb.common.Common.LANE_SEL) &&
      (exeCtrl(Decoder.RDTYPE) === borb.frontend.REGFILE.RDTYPE.RD_INT) &&
      (exeCtrl(Decoder.RD_ADDR) =/= 0)
    val srcNeedsExeRdRs1 = (srcCtrl(Decoder.RS1TYPE) === borb.frontend.REGFILE.RSTYPE.RS_INT) &&
      (srcCtrl(Decoder.RS1_ADDR) === exeCtrl(Decoder.RD_ADDR))
    val srcNeedsExeRdRs2 = (srcCtrl(Decoder.RS2TYPE) === borb.frontend.REGFILE.RSTYPE.RS_INT) &&
      (srcCtrl(Decoder.RS2_ADDR) === exeCtrl(Decoder.RD_ADDR))
    srcCtrl.haltWhen(exeIntProducer && (srcNeedsExeRdRs1 || srcNeedsExeRdRs2))

    val wbIntProducer = wbCtrl.up.isValid &&
      wbCtrl(Decoder.VALID) &&
      wbCtrl(borb.common.Common.LANE_SEL) &&
      (wbCtrl(Decoder.RDTYPE) === borb.frontend.REGFILE.RDTYPE.RD_INT) &&
      (wbCtrl(Decoder.RD_ADDR) =/= 0)
    val srcNeedsWbRdRs1 = (srcCtrl(Decoder.RS1TYPE) === borb.frontend.REGFILE.RSTYPE.RS_INT) &&
      (srcCtrl(Decoder.RS1_ADDR) === wbCtrl(Decoder.RD_ADDR))
    val srcNeedsWbRdRs2 = (srcCtrl(Decoder.RS2TYPE) === borb.frontend.REGFILE.RSTYPE.RS_INT) &&
      (srcCtrl(Decoder.RS2_ADDR) === wbCtrl(Decoder.RD_ADDR))
    srcCtrl.haltWhen(wbIntProducer && (srcNeedsWbRdRs1 || srcNeedsWbRdRs2))

    // ========== Speculation Epoch Architecture ==========
    // Clean, scalable speculation handling for in-order superscalar CPU
    //
    // Design:
    // - Global epoch counter maintained here, passed to Fetch
    // - Each instruction is tagged with SPEC_EPOCH when it enters the pipeline
    // - When a branch is TAKEN (flushPipeline), epoch increments
    // - All instructions with old epoch are flushed (their SPEC_EPOCH != currentEpoch)
    
    // Flush Logic - fires when a non-stale branch/jump redirects.
    val execEpochMatches = pipeline.ctrl(6)(SPEC_EPOCH) === currentEpoch
    val fenceiRedirect = pipeline.ctrl(6).up.isFiring &&
      execEpochMatches &&
      pipeline.ctrl(6)(Decoder.VALID) &&
      pipeline.ctrl(6)(borb.common.Common.LANE_SEL) &&
      (pipeline.ctrl(6)(Decoder.MicroCode) === uopFENCE_I)
    val flushPipeline = branch.logic.jumpCmd.valid && execEpochMatches
    val trapRedirect = trapLogic.redirect.trapFire && execEpochMatches
    val mretRedirect = trapLogic.redirect.mretFire && execEpochMatches
    val sretRedirect = trapLogic.redirect.sretFire && execEpochMatches
    val redirectPipeline = flushPipeline || trapRedirect || mretRedirect || sretRedirect || fenceiRedirect
    pipeline.ctrl(6).down(SELF_REDIRECT) := redirectPipeline
    val fenceiTarget = pipeline.ctrl(6)(borb.fetch.PC.PC) + U(4, 64 bits)
    pc.jump.valid := (branch.logic.jumpCmd.valid && execEpochMatches) || mretRedirect || sretRedirect || fenceiRedirect
    pc.jump.payload.target := (
      mretRedirect ? trapLogic.redirect.mretTarget |
      (sretRedirect ? trapLogic.redirect.sretTarget |
      (fenceiRedirect ? fenceiTarget | branch.logic.jumpCmd.payload.target))
    )
    pc.jump.payload.is_jump := mretRedirect || sretRedirect || fenceiRedirect || branch.logic.jumpCmd.payload.is_jump
    pc.jump.payload.is_branch := (!mretRedirect) && (!sretRedirect) && (!fenceiRedirect) && branch.logic.jumpCmd.payload.is_branch
    
    // Increment epoch on taken branch
    when(flushPipeline) {
      currentEpoch := currentEpoch + 1
    }
    when(trapRedirect) {
      currentEpoch := currentEpoch + 1
    }
    when(mretRedirect) {
      currentEpoch := currentEpoch + 1
    }
    when(sretRedirect) {
      currentEpoch := currentEpoch + 1
    }
    when(fenceiRedirect) {
      currentEpoch := currentEpoch + 1
    }
    val redirectCommitBubble = RegNext(redirectPipeline) init(False)
    
    // Connect epoch to Fetch so new instructions get tagged with current epoch
    fetch.io.flush := redirectPipeline
    fetch.io.currentEpoch := currentEpoch
    
    // Flush fetch/decode/src younger stages on redirect so a new target beat
    // cannot be consumed against a stale stage-local PC offset.
    // Note: Stage 6 (Execute) is excluded - the redirecting instruction executes.
    //       Stage 7 (Writeback) is excluded - older committed state.
    val youngerStages = Array(1, 2, 3, 4, 5).map(pipeline.ctrl(_))
    youngerStages.foreach { ctrl =>
      ctrl.throwWhen(redirectPipeline)
    }

    val rvfiPlugin = new RvfiPlugin(pipeline.ctrl(7))
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

    val debugPlugin = new DebugPlugin(pipeline, trapLogic.redirect, redirectProbe)
    io.dbg := debugPlugin.io.dbg

    // Once a sequence has committed, any lingering older-stage copy of that
    // same instruction is stale and must be killed before it can refire side
    // effects or traps. This keeps backend ownership of a sequence exclusive.
    val lastCommittedSeqValid = RegInit(False)
    val lastCommittedSeq = Reg(UInt(32 bits)) init(0)
    when(pipeline.ctrl(7).up(COMMIT)) {
      lastCommittedSeqValid := True
      lastCommittedSeq := pipeline.ctrl(7).up(borb.fetch.Fetch.FETCH_SEQ)
    }
    Array(4, 5, 6).foreach { idx =>
      val ctrl = pipeline.ctrl(idx)
      val staleCommittedSeq =
        lastCommittedSeqValid &&
        ctrl.up.isValid &&
        ctrl(VALID) &&
        (ctrl.up(borb.fetch.Fetch.FETCH_SEQ) === lastCommittedSeq)
      ctrl.throwWhen(staleCommittedSeq)
    }

    // Wire event signals to performance counters
    val hazardStall = dispatcher.hcs.writes.hazard
    val fetchStall = !fetch.beatValid
    val memStall = lsu.logic.waitingResponse
    val lsuReplayOrWait = lsu.logic.waitingResponse || lsu.logic.amoWaitingResponse || lsu.logic.amoStorePending
    val dispatchCtrl = pipeline.ctrl(4)
    val srcCtrlPerf = pipeline.ctrl(5)
    val committedThisCycle = pipeline.ctrl(7).up(COMMIT)
    val writeCtrl = pipeline.ctrl(7)
    val dispatchValid = dispatchCtrl.up.isValid && dispatchCtrl(VALID) && dispatchCtrl(LANE_SEL)
    val srcValid = srcCtrlPerf.up.isValid && srcCtrlPerf(VALID) && srcCtrlPerf(LANE_SEL)
    val execValid = pipeline.ctrl(6).up.isValid && pipeline.ctrl(6)(VALID) && pipeline.ctrl(6)(LANE_SEL)
    val writeValid = writeCtrl.up.isValid && writeCtrl(VALID) && writeCtrl(LANE_SEL)
    val dispatchFire = dispatchCtrl.up.isFiring && dispatchCtrl(VALID) && dispatchCtrl(LANE_SEL)
    val srcFire = srcCtrlPerf.up.isFiring && srcCtrlPerf(VALID) && srcCtrlPerf(LANE_SEL)
    val execFire = pipeline.ctrl(6).up.isFiring && pipeline.ctrl(6)(VALID) && pipeline.ctrl(6)(LANE_SEL)
    val writeFire = writeCtrl.up.isFiring && writeCtrl(VALID) && writeCtrl(LANE_SEL)
    val backendOccCount = UInt(3 bits)
    backendOccCount := dispatchValid.asUInt.resize(3) +
      srcValid.asUInt.resize(3) +
      execValid.asUInt.resize(3) +
      writeValid.asUInt.resize(3)
    val writebackStall = writeValid && !committedThisCycle
    val mulDivBusy = execValid && pipeline.ctrl(6)(MicroCode).mux(
      uopMUL -> True, uopMULH -> True, uopMULHSU -> True, uopMULHU -> True,
      uopDIV -> True, uopDIVU -> True, uopREM -> True, uopREMU -> True,
      uopMULW -> True, uopDIVW -> True, uopDIVUW -> True, uopREMW -> True, uopREMUW -> True,
      default -> False
    )
    val mulDivBusyStall = mulDivBusy && !committedThisCycle
    val commitStall = execValid && !writeValid && !hazardStall && !fetchStall && !lsuReplayOrWait
    val dispatchToSrcStall = dispatchValid && !srcValid && !hazardStall && !fetchStall
    val srcToExecStall = srcValid && !execValid && !hazardStall && !fetchStall && !controlHazardBusy
    val execToWriteStall = execValid && !writeValid && !lsuReplayOrWait
    val backendActive = Array(3, 4, 5, 6, 7).map { idx =>
      val ctrl = pipeline.ctrl(idx)
      ctrl.up.isValid && ctrl(VALID)
    }.reduce(_ || _)
    val backendStall = backendActive && !committedThisCycle && !hazardStall && !fetchStall && !memStall

    perfCounters.hazardStall    := hazardStall                   // Hazard stall from HazardChecker
    perfCounters.fetchStall     := fetchStall                    // Fetch stalled waiting for instruction
    perfCounters.memStall       := memStall                      // Waiting for load response
    perfCounters.backendStall   := backendStall                  // Active backend cycle not explained by other stall classes
    perfCounters.writebackStall := writebackStall
    perfCounters.commitStall := commitStall
    perfCounters.mulDivBusyStall := mulDivBusyStall
    perfCounters.lsuReplayOrWaitStall := lsuReplayOrWait
    perfCounters.dispatchToSrcStall := dispatchToSrcStall
    perfCounters.srcToExecStall := srcToExecStall
    perfCounters.execToWriteStall := execToWriteStall
    perfCounters.dispatchValid := dispatchValid
    perfCounters.srcValid := srcValid
    perfCounters.execValid := execValid
    perfCounters.writeValid := writeValid
    perfCounters.dispatchFire := dispatchFire
    perfCounters.srcFire := srcFire
    perfCounters.execFire := execFire
    perfCounters.writeFire := writeFire
    perfCounters.frontendPendingReq := fetch.perfPendingReq
    perfCounters.frontendBeat0Valid := fetch.perfBeat0Valid
    perfCounters.frontendBeat1Valid := fetch.perfBeat1Valid
    perfCounters.frontendReqIssuedEvent := fetch.perfReqIssued
    perfCounters.frontendRspAcceptedEvent := fetch.perfRspAccepted
    perfCounters.frontendNeedCurrentReqEvent := fetch.perfNeedCurrentReq
    perfCounters.frontendNeedNextReqEvent := fetch.perfNeedNextReq
    perfCounters.frontendPrefetchReqEvent := fetch.perfPrefetchReq
    perfCounters.frontendWaitCurBeatEvent := fetch.perfWaitCurBeat
    perfCounters.frontendWaitNextBeatEvent := fetch.perfWaitNextBeat
    perfCounters.frontendTakeInsnEvent := fetch.perfTakeInsn
    perfCounters.frontendCurBeatHitEvent := fetch.perfCurBeatHit
    perfCounters.frontendNextBeatHitEvent := fetch.perfNextBeatHit
    perfCounters.frontendCmdValidCycleEvent := fetch.perfCmdValid
    perfCounters.frontendPrefetchWindowEvent := fetch.perfPrefetchWindow
    perfCounters.frontendPrefetchBlockedNoCmdEvent := fetch.perfPrefetchBlockedNoCmd
    perfCounters.frontendPrefetchBlockedPendingEvent := fetch.perfPrefetchBlockedPending
    perfCounters.frontendPrefetchBlockedNextHitEvent := fetch.perfPrefetchBlockedNextHit
    perfCounters.backendOcc0 := backendOccCount === U(0, 3 bits)
    perfCounters.backendOcc1 := backendOccCount === U(1, 3 bits)
    perfCounters.backendOcc2 := backendOccCount === U(2, 3 bits)
    perfCounters.backendOcc3 := backendOccCount === U(3, 3 bits)
    perfCounters.backendOcc4 := backendOccCount === U(4, 3 bits)
    perfCounters.backendOverlapDispatchSrcEvent := dispatchValid && srcValid
    perfCounters.backendOverlapSrcExecEvent := srcValid && execValid
    perfCounters.backendOverlapExecWriteEvent := execValid && writeValid
    perfCounters.branchExecuted := branch.logic.isBranch && branch.logic.up(LANE_SEL)
    perfCounters.branchTaken    := branch.logic.doJump
    perfCounters.pipelineFlush  := flushPipeline

    val write = pipeline.ctrl(7)
    //val dispCtrl = pipeline.ctrl(4)

    import borb.execute.WriteBack
    val writeback = new WriteBack(pipeline.ctrl(7), srcPlugin.regfileread.regfile.io.writes(0), currentEpoch, redirectCommitBubble)
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

    io.iAxi.arw.valid := fetch.io.iAxi.arw.valid
    io.iAxi.arw.addr := fetch.io.iAxi.arw.addr
    io.iAxi.arw.id := fetch.io.iAxi.arw.id
    io.iAxi.arw.len := fetch.io.iAxi.arw.len
    io.iAxi.arw.size := fetch.io.iAxi.arw.size
    io.iAxi.arw.burst := fetch.io.iAxi.arw.burst
    io.iAxi.arw.write := fetch.io.iAxi.arw.write
    fetch.io.iAxi.arw.ready := io.iAxi.arw.ready

    io.iAxi.w.valid := fetch.io.iAxi.w.valid
    io.iAxi.w.data := fetch.io.iAxi.w.data
    io.iAxi.w.strb := fetch.io.iAxi.w.strb
    io.iAxi.w.last := fetch.io.iAxi.w.last
    fetch.io.iAxi.w.ready := io.iAxi.w.ready

    fetch.io.iAxi.b.valid := io.iAxi.b.valid
    fetch.io.iAxi.b.id := io.iAxi.b.id
    fetch.io.iAxi.b.resp := io.iAxi.b.resp
    io.iAxi.b.ready := fetch.io.iAxi.b.ready

    fetch.io.iAxi.r.valid := io.iAxi.r.valid
    fetch.io.iAxi.r.data := io.iAxi.r.data
    fetch.io.iAxi.r.id := io.iAxi.r.id
    fetch.io.iAxi.r.resp := io.iAxi.r.resp
    fetch.io.iAxi.r.last := io.iAxi.r.last
    io.iAxi.r.ready := fetch.io.iAxi.r.ready

    // LSU DataBus -> AXI4Shared bridge
    val dCmd = lsuBus.cmd
    val dRsp = lsuBus.rsp

    object DMemAxiState extends SpinalEnum {
      val idle, sendWrite, waitWriteResp, sendRead, waitReadResp = newElement()
    }
    import DMemAxiState._
    val dAxiState = RegInit(idle)
    val dActiveCmd = Reg(DataBusCmd(64, 64, 16))
    val dArwFired = RegInit(False)
    val dWFired = RegInit(False)
    val archBase = U(BigInt("80000000", 16), 64 bits)
    val lowDataAliasBase = U(4 MiB, 64 bits)
    val dPhysAddr = UInt(64 bits)
    dPhysAddr := Mux(dActiveCmd.address < archBase, dActiveCmd.address + lowDataAliasBase, dActiveCmd.address)
    dCmd.ready := False

    io.dAxi.arw.valid := False
    io.dAxi.arw.id := dActiveCmd.id.resized
    io.dAxi.arw.addr := dPhysAddr
    io.dAxi.arw.len := 0
    io.dAxi.arw.size := log2Up(config.xlen / 8)
    io.dAxi.arw.burst := Axi4.burst.INCR
    io.dAxi.arw.write := dActiveCmd.write

    io.dAxi.w.valid := False
    io.dAxi.w.data := dActiveCmd.data
    io.dAxi.w.strb := dActiveCmd.mask
    io.dAxi.w.last := True

    io.dAxi.b.ready := False
    io.dAxi.r.ready := False

    dRsp.valid := False
    dRsp.data := io.dAxi.r.data
    dRsp.id := io.dAxi.r.id.resized

    switch(dAxiState) {
      is(idle) {
        dCmd.ready := True
        dArwFired := False
        dWFired := False
        when(dCmd.valid) {
          dActiveCmd := dCmd.payload
          when(dCmd.write) {
            dAxiState := sendWrite
          } otherwise {
            dAxiState := sendRead
          }
        }
      }

      is(sendWrite) {
        io.dAxi.arw.valid := !dArwFired
        io.dAxi.w.valid := !dWFired
        when(io.dAxi.arw.fire) { dArwFired := True }
        when(io.dAxi.w.fire) { dWFired := True }
        when((dArwFired || io.dAxi.arw.fire) && (dWFired || io.dAxi.w.fire)) {
          dAxiState := waitWriteResp
        }
      }

      is(waitWriteResp) {
        io.dAxi.b.ready := True
        when(io.dAxi.b.valid) {
          dAxiState := idle
        }
      }

      is(sendRead) {
        io.dAxi.arw.valid := True
        when(io.dAxi.arw.ready) {
          dAxiState := waitReadResp
        }
      }

      is(waitReadResp) {
        io.dAxi.r.ready := True
        when(io.dAxi.r.valid) {
          dRsp.valid := True
          dAxiState := idle
        }
      }
    }

    pipeline.ctrls.drop(1).foreach(e => e._2.throwWhen(clockDomain.reset))
    // Build the pipeline
    pipeline.build()
  }
}
