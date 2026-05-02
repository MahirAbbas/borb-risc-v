package borb

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.fetch._
import borb.fetch.FrontendRedirectReason
import borb.backend.{BackendIssue, BackendPipe, FpBackend, IntegerBackend, PipelineSlot, RetirePacket, TrapCsrBackend}
import borb.frontend.Decoder
import borb.frontend.Decoder._
import borb.dispatch._
import borb.dispatch.IssueSemantics
import borb.execute.IntAlu
import borb.execute.IntAlu._
import borb.execute.{DataBus, DataSideCache, WriteBack}
import borb.dispatch.SrcPlugin
import borb.dispatch.SrcPlugin._
import borb.formal._
import spinal.core.sim._
import spinal.lib.bus.amba4.axi._
import borb.core.CpuConfig
import borb.vector.{DormantSharedVectorEngine, VectorDecode, VectorExceptionCause, VectorMemOp}
import spinal.lib.misc.plugin.PluginHost
import borb.common.MicroCode._
import borb.common.{LaneContracts, LaneId}

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

    // Defaults for Execution Stages: LANE_SEL is False if not propagated (Bubble)
    import borb.common.Common._
    pipeline.ctrls.filter(_._1 >= 7).foreach { 
      case (id, ctrl) => ctrl.up(LANE_SEL).setAsReg().init(False)

    }
    pipeline.ctrls.filter(e => e._1 >= 7 && e._1 < 9).foreach { 
      case(id, ctrl) => ctrl.up(COMMIT).setAsReg().init(False)
    }
    pipeline.ctrls.filter(_._1 >= 9).foreach {
      case (_, ctrl) => ctrl.up(SELF_REDIRECT).setAsReg().init(False)
    }
    pipeline.ctrls.filter(_._1 >= 9).foreach {
      case (_, ctrl) => ctrl.up(TRAP).setAsReg().init(False)
    }
    val resetPcValue = BigInt("80000000", 16)

    // Keep speculation epoch instruction-local across stalls/flushes.
    pipeline.ctrls.filter(_._1 >= 2).foreach {
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
    pipeline.ctrls.filter(_._1 >= 2).foreach {
      case (_, ctrl) => ctrl.up(borb.frontend.Decoder.INSTRUCTION).setAsReg().init(0)
    }
    pipeline.ctrls.filter(_._1 >= 2).foreach {
      case (_, ctrl) => ctrl.up(borb.fetch.Fetch.FETCH_SEQ).setAsReg().init(0)
    }
    pipeline.ctrls.filter(_._1 >= 2).foreach {
      case (_, ctrl) => ctrl.up(borb.fetch.Fetch.FETCH_BUNDLE_SEQ).setAsReg().init(0)
    }
    pipeline.ctrls.filter(_._1 >= 2).foreach {
      case (_, ctrl) => ctrl.up(borb.fetch.Fetch.FETCH_FTQ_IDX).setAsReg().init(0)
    }
    pipeline.ctrls.filter(_._1 >= 2).foreach {
      case (_, ctrl) => ctrl.up(borb.fetch.Fetch.FETCH_SLOT_IDX).setAsReg().init(0)
    }
    pipeline.ctrls.filter(_._1 >= 2).foreach {
      case (_, ctrl) => ctrl.up(borb.fetch.Fetch.FETCH_SLOT_COUNT).setAsReg().init(0)
    }
    pipeline.ctrls.filter(_._1 >= 2).foreach {
      case (_, ctrl) => ctrl.up(borb.fetch.Fetch.FETCH_BLOCK_PC).setAsReg().init(0)
    }
    pipeline.ctrls.filter(_._1 >= 2).foreach {
      case (_, ctrl) => ctrl.up(borb.fetch.Fetch.FETCH_BYTE_OFFSET).setAsReg().init(0)
    }
    pipeline.ctrls.filter(_._1 >= 2).foreach {
      case (_, ctrl) => ctrl.up(borb.fetch.Fetch.FETCH_PREDICTED_VALID).setAsReg().init(False)
    }
    pipeline.ctrls.filter(_._1 >= 2).foreach {
      case (_, ctrl) => ctrl.up(borb.fetch.Fetch.FETCH_PREDICTED_TAKEN).setAsReg().init(False)
    }
    pipeline.ctrls.filter(_._1 >= 2).foreach {
      case (_, ctrl) => ctrl.up(borb.fetch.Fetch.FETCH_PREDICTED_TARGET).setAsReg().init(0)
    }
    pipeline.ctrls.filter(_._1 >= 2).foreach {
      case (_, ctrl) => ctrl.up(LANE_ID).setAsReg().init(0)
    }
    pipeline.ctrls.filter(_._1 >= 2).foreach {
      case (_, ctrl) => ctrl.up(LANE_MASK).setAsReg().init(B"00")
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
      case (_, ctrl) => ctrl.up(borb.frontend.Decoder.MicroCode).setAsReg().init(borb.common.MicroCode.uopNOP)
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
      case (_, ctrl) => ctrl.up(borb.frontend.Decoder.RS3_ADDR).setAsReg().init(0)
    }
    pipeline.ctrls.filter(_._1 >= 4).foreach {
      case (_, ctrl) => ctrl.up(borb.frontend.Decoder.VALID).setAsReg().init(False)
    }
    pipeline.ctrls.filter(_._1 >= 4).foreach {
      case (_, ctrl) => ctrl.up(borb.frontend.Decoder.DECODE_ILLEGAL).setAsReg().init(False)
    }
    pipeline.ctrls.filter(_._1 >= 7).foreach {
      case (_, ctrl) => ctrl.up(IssueSemantics.PROPS).setAsReg().init(IssuePropertyBundle().getZero)
    }
    pipeline.ctrls.filter(_._1 >= 7).foreach {
      case (_, ctrl) => ctrl.up(BackendIssue.SELECTED_PIPE).setAsReg().init(BackendPipe.None)
    }
    // Keep dispatch lane routing instruction-local once an instruction leaves
    // dispatch. Otherwise a stalled backend instruction can observe a newer
    // execution-unit selection and execute through the wrong side-effect path.
    pipeline.ctrls.filter(_._1 >= 7).foreach {
      case (_, ctrl) => ctrl.up(borb.dispatch.Dispatch.SENDTOALU).setAsReg().init(False)
    }
    pipeline.ctrls.filter(_._1 >= 7).foreach {
      case (_, ctrl) => ctrl.up(borb.dispatch.Dispatch.SENDTOBRANCH).setAsReg().init(False)
    }
    pipeline.ctrls.filter(_._1 >= 7).foreach {
      case (_, ctrl) => ctrl.up(borb.dispatch.Dispatch.SENDTOAGU).setAsReg().init(False)
    }
    // Keep resolved operands instruction-local once they leave the source
    // stage. Otherwise a held execute-stage instruction can observe a newer
    // regfile/bypass value and re-execute with different operands.
    pipeline.ctrls.filter(_._1 >= 8).foreach {
      case (_, ctrl) => ctrl.up(borb.dispatch.SrcPlugin.RS1).setAsReg().init(0)
    }
    pipeline.ctrls.filter(_._1 >= 8).foreach {
      case (_, ctrl) => ctrl.up(borb.dispatch.SrcPlugin.RS2).setAsReg().init(0)
    }
    pipeline.ctrls.filter(_._1 >= 8).foreach {
      case (_, ctrl) => ctrl.up(borb.dispatch.SrcPlugin.IMMED).setAsReg().init(0)
    }
    pipeline.ctrls.filter(_._1 >= 9).foreach {
      case (_, ctrl) => ctrl.up(borb.execute.Branch.BRANCH_TAKEN).setAsReg().init(False)
    }
    pipeline.ctrls.filter(_._1 >= 9).foreach {
      case (_, ctrl) => ctrl.up(borb.execute.Branch.BRANCH_TARGET).setAsReg().init(0)
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
    pc.redirect.setIdle()
    val fetch = Fetch(
      pipeline.ctrl(1),
      pipeline.ctrl(2),
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
    fetch.io.scalarConsume.allowOverride := False
    fetch.io.scalarSkip.allowOverride := False
    fetch.io.scalarHold.allowOverride := False
    fetch.io.scalarDeferRefill.allowOverride := False
    fetch.io.suppressPrefetch.allowOverride := False
    pc.sequentialValid := fetch.io.pcAdvance
    pc.sequentialStep := fetch.io.pcStep
    // RAM is external (via io.iAxi/io.dAxi)

    val decode = new Decoder(pipeline.ctrl(3), withCompressed = config.cExtensionEnabled, xlen = config.xlen)

    val execStage = pipeline.ctrl(8)
    val integerBackend = IntegerBackend(pipeline.ctrl(8), pipeline.ctrl(9))
    val hazardRange = Array(6, 7, 8, 9).map(e => pipeline.ctrl(e)).toSeq
    val dispatcher = new Dispatch(
      pipeline.ctrl(6),
      hazardRange,
      pipeline,
      intBypassReady = Seq(False, integerBackend.exeBypassReady, integerBackend.wbIntBypass.valid)
    )
    val srcPlugin = new SrcPlugin(pipeline.ctrl(7), Seq(integerBackend.exeIntBypass, integerBackend.wbIntBypass))
    val intalu = new IntAlu(pipeline.ctrl(8))
    val branch = new borb.execute.Branch(pipeline.ctrl(8), pc, withCompressed = config.cExtensionEnabled)
    val lsuKillOutstanding = RegInit(False)
    val lsuKillCboZero = Bool()
    val lsu = new borb.execute.Lsu(pipeline.ctrl(8), pipeline.ctrl(9), currentEpoch, lsuKillOutstanding, lsuKillCboZero)

    val lane1PairPending = RegInit(False)
    val lane1PairOlderSeq = Reg(UInt(32 bits)) init(0)
    val lane1PairEpoch = Reg(UInt(16 bits)) init(0)
    val lane1Pipe = Vec.fill(4)(Reg(PipelineSlot(config)) init(PipelineSlot(config).getZero))
    val lane1DecodeSlot = lane1Pipe(0)
    val lane1SrcSlot = lane1Pipe(1)
    val lane1ExecSlot = lane1Pipe(2)
    val lane1WbSlot = lane1Pipe(3)
    val lastCommittedSeqValid = Reg(Bits(2 bits)) init(0)
    val lastCommittedSeq0 = Reg(UInt(32 bits)) init(0)
    val lastCommittedSeq1 = Reg(UInt(32 bits)) init(0)
    val lane1Inflight = lane1PairPending || lane1DecodeSlot.valid || lane1SrcSlot.valid || lane1ExecSlot.valid || lane1WbSlot.valid

    val lane1CandidateValid = fetch.adapter.io.scalar.valid &&
      (fetch.adapter.io.scalar.payload.epoch === currentEpoch) &&
      (fetch.adapter.io.scalar.payload.slotIdx === 1)
    val lane1Candidate = fetch.adapter.io.scalar.payload
    val lane1Decoded = Decoder.decodeInstruction(lane1Candidate.insn, withCompressed = config.cExtensionEnabled, xlen = config.xlen)
    val lane1IssueProps = IssueSemantics.classify(lane1Decoded.microCode)
    val lane0BoundaryDecoded = Decoder.decodeInstruction(fetch.scalarBoundaryPayload.insn, withCompressed = config.cExtensionEnabled, xlen = config.xlen)
    val lane0BoundaryProps = IssueSemantics.classify(lane0BoundaryDecoded.microCode)
    val lane1PreviewDecoded = Decoder.decodeInstruction(fetch.adapter.io.slot1PreviewInsn, withCompressed = config.cExtensionEnabled, xlen = config.xlen)
    val lane1PreviewProps = IssueSemantics.classify(lane1PreviewDecoded.microCode)
    def isConditionalBranch(microCode: borb.common.MicroCode.C): Bool = {
      (microCode === uopBEQ) ||
      (microCode === uopBNE) ||
      (microCode === uopBLT) ||
      (microCode === uopBGE) ||
      (microCode === uopBLTU) ||
      (microCode === uopBGEU)
    }

    val lane1IsConditionalBranch = isConditionalBranch(lane1Decoded.microCode)
    val lane1PreviewIsConditionalBranch = isConditionalBranch(lane1PreviewDecoded.microCode)
    val lane0BoundaryIsConditionalBranch = isConditionalBranch(lane0BoundaryDecoded.microCode)
    val lane1BoundaryStaticRaw =
      lane0BoundaryProps.writesIntRd &&
      (lane0BoundaryDecoded.rdAddr =/= 0) &&
      (
        (lane1PreviewProps.readsIntRs1 && (lane1PreviewDecoded.rs1Addr === lane0BoundaryDecoded.rdAddr)) ||
        (lane1PreviewProps.readsIntRs2 && (lane1PreviewDecoded.rs2Addr === lane0BoundaryDecoded.rdAddr))
      )
    val lane1BoundaryStaticWaw =
      lane0BoundaryProps.writesIntRd &&
      lane1PreviewProps.writesIntRd &&
      (lane0BoundaryDecoded.rdAddr =/= 0) &&
      (lane1PreviewDecoded.rdAddr === lane0BoundaryDecoded.rdAddr)
    val lane0BoundaryOlderPairable =
      !lane0BoundaryProps.pairBarrier &&
      (!lane0BoundaryProps.isControlFlow || lane0BoundaryIsConditionalBranch)

    def seqRecentlyCommitted(seq: UInt): Bool = {
      (pipeline.ctrl(9).up(COMMIT) && (pipeline.ctrl(9).up(borb.fetch.Fetch.FETCH_SEQ) === seq)) ||
      (lastCommittedSeqValid(0) && (lastCommittedSeq0 === seq)) ||
      (lastCommittedSeqValid(1) && (lastCommittedSeq1 === seq))
    }

    def buildLaneIssueSlot(
        laneId: Int,
        valid: Bool,
        entry: ScalarFetchEntry,
        olderSeq: UInt,
        decoded: Decoder.DecoderResult,
        issueProps: IssuePropertyBundle,
        waitForOlderCommit: Bool
    ): PipelineSlot = {
      val slot = PipelineSlot(config)
      slot.assignDontCare()
      slot.valid := valid
      slot.epoch := entry.epoch
      slot.fetchSeq := entry.scalarSeq
      slot.olderSeq := olderSeq
      slot.bundleSeq := entry.bundleSeq
      slot.slotIdx := entry.slotIdx.resized
      slot.slotCount := entry.slotCount.resized
      slot.ftqIdx := entry.ftqIndex.resized
      slot.pc := entry.pc
      slot.blockPc := entry.blockPc
      slot.byteOffset := entry.byteOffsetInBlock.resized
      slot.predictedValid := entry.predictedValid
      slot.predictedTaken := entry.predictedTaken
      slot.predictedTarget := entry.predictedTarget
      slot.decodedInstruction := decoded.decodedInstruction
      slot.isCompressed := decoded.isCompressed
      slot.legal := decoded.legal
      slot.microCode := decoded.microCode
      slot.rdAddr := decoded.rdAddr
      slot.rs1Addr := decoded.rs1Addr
      slot.rs2Addr := decoded.rs2Addr
      slot.rs3Addr := decoded.rs3Addr
      slot.issueProps := issueProps
      slot.waitForOlderCommit := waitForOlderCommit
      slot.sendToAlu := issueProps.fuMask(0)
      slot.sendToBranch := issueProps.fuMask(1)
      slot.selectedPipe := BackendPipe.select(decoded.microCode, issueProps, preferAlu1 = if(laneId == LaneId.Lane1) True else False)
      slot
    }

    val lsuBus = DataBus(addressWidth = 64, dataWidth = 64, idWidth = 16)

    val perfCounters = if (config.perfCountersEnabled) Some(new borb.core.PerfCountersPlugin(pipeline.ctrl(9))) else None
    val perfCounterOutputs = perfCounters.map(_.counters).getOrElse(borb.core.PerfCountersBundle().getZero)
    io.perf := perfCounterOutputs

    val trapLogic = TrapCsrBackend(execStage, pipeline.ctrl(9), config, currentEpoch, pc, fetch, branch, lsu, perfCounterOutputs)
    val fpBackend = FpBackend(execStage, lsu, currentEpoch, trapLogic.frm)
    val vectorEngine = DormantSharedVectorEngine(config.vectorConfig.copy(xlen = config.xlen, addressWidth = config.physicalAddrWidth))

    trapLogic.fpFlagsSetValid := fpBackend.fpFlags.valid
    trapLogic.fpFlagsSetBits := fpBackend.fpFlags.bits

    val vectorExecInsn = execStage.up(Decoder.DECODED_INSTRUCTION)
    val vectorExecMicroCode = execStage.up(Decoder.MicroCode)
    val vectorExecContext = trapLogic.vectorContext
    val vectorDecodedOp =
      (vectorExecMicroCode === uopVSETVLI) ||
      (vectorExecMicroCode === uopVSETIVLI) ||
      (vectorExecMicroCode === uopVSETVL) ||
      (vectorExecMicroCode === uopVADDVV) ||
      (vectorExecMicroCode === uopVADDVI) ||
      (vectorExecMicroCode === uopVMVVI) ||
      (vectorExecMicroCode === uopVMVXS) ||
      (vectorExecMicroCode === uopVLE32) ||
      (vectorExecMicroCode === uopVSE32) ||
      (vectorExecMicroCode === uopVSLIDEUPVI) ||
      (vectorExecMicroCode === uopVSLIDEDOWNVI) ||
      (vectorExecMicroCode === uopVRGATHERVI) ||
      (vectorExecMicroCode === uopVREDSUMVS) ||
      (vectorExecMicroCode === uopVFADDVV) ||
      (vectorExecMicroCode === uopVFSUBVV) ||
      (vectorExecMicroCode === uopVFWCVTFFV) ||
      (vectorExecMicroCode === uopVFNCVTFFW) ||
      (vectorExecMicroCode === uopVANDNVV) ||
      (vectorExecMicroCode === uopVBREV8V) ||
      (vectorExecMicroCode === uopVREV8V) ||
      (vectorExecMicroCode === uopVCLZV) ||
      (vectorExecMicroCode === uopVCPOPV) ||
      (vectorExecMicroCode === uopVRORVI)
    val vectorExecPacket = execStage.up.isValid &&
      (execStage.up(SPEC_EPOCH) === currentEpoch) &&
      (execStage.up(PC.PC) >= resetPcValue) &&
      execStage.up(Decoder.VALID) &&
      execStage.up(LANE_SEL) &&
      (execStage.up(BackendIssue.SELECTED_PIPE) === BackendPipe.Vector) &&
      vectorDecodedOp
    val vectorMemoryExec = (vectorExecMicroCode === uopVLE32) || (vectorExecMicroCode === uopVSE32)
    val vectorMemoryActive = RegInit(False)
    when(vectorExecPacket && vectorMemoryExec && !vectorMemoryActive) {
      vectorMemoryActive := True
    }
    when(vectorMemoryActive && vectorEngine.io.memoryComplete) {
      vectorMemoryActive := False
    }
    val vectorMemoryTrap = vectorMemoryActive && vectorEngine.io.memoryComplete && vectorEngine.io.memoryException.valid
    trapLogic.vectorMemoryComplete := vectorMemoryActive && vectorEngine.io.memoryComplete
    trapLogic.vectorMemoryTrapValid := vectorMemoryTrap
    trapLogic.vectorMemoryTrapIsStore := vectorExecMicroCode === uopVSE32
    trapLogic.vectorMemoryTrapTval := vectorEngine.io.memoryException.tval
    trapLogic.vectorMemoryTrapElement := vectorEngine.io.memoryFaultElement.resized
    vectorEngine.io.command.valid := vectorExecPacket || vectorMemoryActive
    vectorEngine.io.command.payload.hartId := 0
    vectorEngine.io.command.payload.pc := execStage.up(PC.PC)
    vectorEngine.io.command.payload.instruction := vectorExecInsn
    vectorEngine.io.command.payload.opClass := VectorDecode.classify(vectorExecInsn)
    vectorEngine.io.command.payload.rd := vectorExecInsn(11 downto 7).asUInt
    vectorEngine.io.command.payload.rs1 := vectorExecInsn(19 downto 15).asUInt
    vectorEngine.io.command.payload.rs2 := vectorExecInsn(24 downto 20).asUInt
    vectorEngine.io.command.payload.rs3 := vectorExecInsn(31 downto 27).asUInt
    vectorEngine.io.command.payload.scalarRs1 := execStage.up(SrcPlugin.RS1)
    vectorEngine.io.command.payload.scalarRs2 := execStage.up(SrcPlugin.RS2)
    vectorEngine.io.command.payload.funct3 := vectorExecInsn(14 downto 12)
    vectorEngine.io.command.payload.funct6 := vectorExecInsn(31 downto 26)
    vectorEngine.io.command.payload.vm := vectorExecInsn(25)
    vectorEngine.io.command.payload.context := vectorExecContext
    vectorEngine.io.response.ready := True
    execStage.haltWhen(vectorExecPacket && vectorMemoryExec && (!vectorMemoryActive || !vectorEngine.io.memoryComplete))
    when(vectorEngine.io.response.valid && vectorEngine.io.response.payload.writesScalar) {
      execStage.down(WriteBack.RESULT).address.allowOverride := vectorEngine.io.response.payload.scalarRd
      execStage.down(WriteBack.RESULT).data.allowOverride := Mux(
        vectorEngine.io.response.payload.scalarRd === 0,
        B(0, config.xlen bits),
        vectorEngine.io.response.payload.scalarData
      )
      execStage.down(WriteBack.RESULT).valid.allowOverride := True
    }

    val vectorMemId = U(65535, 16 bits)
    val vectorMemSelected = vectorEngine.io.memReq.valid
    lsuBus.cmd.valid := lsu.io.dBus.cmd.valid || vectorEngine.io.memReq.valid
    lsuBus.cmd.payload.address := lsu.io.dBus.cmd.payload.address
    lsuBus.cmd.payload.data := lsu.io.dBus.cmd.payload.data
    lsuBus.cmd.payload.mask := lsu.io.dBus.cmd.payload.mask
    lsuBus.cmd.payload.id := lsu.io.dBus.cmd.payload.id
    lsuBus.cmd.payload.write := lsu.io.dBus.cmd.payload.write
    when(vectorMemSelected) {
      lsuBus.cmd.payload.address := vectorEngine.io.memReq.payload.address
      lsuBus.cmd.payload.data := vectorEngine.io.memReq.payload.data(63 downto 0)
      lsuBus.cmd.payload.mask := vectorEngine.io.memReq.payload.mask(7 downto 0)
      lsuBus.cmd.payload.id := vectorMemId
      lsuBus.cmd.payload.write := vectorEngine.io.memReq.payload.op === VectorMemOp.Store
    }
    lsu.io.dBus.cmd.ready := lsuBus.cmd.ready && !vectorMemSelected
    vectorEngine.io.memReq.ready := lsuBus.cmd.ready

    val vectorMemResponse = lsuBus.rsp.valid && (lsuBus.rsp.payload.id === vectorMemId)
    lsu.io.dBus.rsp.valid := lsuBus.rsp.valid && !vectorMemResponse
    lsu.io.dBus.rsp.payload.data := lsuBus.rsp.payload.data
    lsu.io.dBus.rsp.payload.id := lsuBus.rsp.payload.id
    vectorEngine.io.memResp.valid := vectorMemResponse
    vectorEngine.io.memResp.payload.hartId := 0
    vectorEngine.io.memResp.payload.data := lsuBus.rsp.payload.data.resize(config.vectorConfig.vlen)
    vectorEngine.io.memResp.payload.exception.valid := False
    vectorEngine.io.memResp.payload.exception.cause := VectorExceptionCause.None
    vectorEngine.io.memResp.payload.exception.tval := 0

    decode.branchResolved := branch.branchResolved

    val relaxIntProducerHazards = True
    val relaxControlFlowSerialization = True

    val dispatchCtrl = pipeline.ctrl(6)
    val srcCtrl = pipeline.ctrl(7)
    val exeCtrl = pipeline.ctrl(8)
    val wbCtrl = pipeline.ctrl(9)
    val srcEpochMatches = srcCtrl.up(SPEC_EPOCH) === currentEpoch
    val exeEpochMatchesForHazard = exeCtrl.up(SPEC_EPOCH) === currentEpoch
    val srcHasControlFlow = srcCtrl.up.isValid &&
      srcEpochMatches &&
      srcCtrl(Decoder.VALID) &&
      srcCtrl(borb.common.Common.LANE_SEL) &&
      srcCtrl(IssueSemantics.PROPS).isControlFlow
    val exeHasControlFlow = exeCtrl.up.isValid &&
      exeEpochMatchesForHazard &&
      exeCtrl(Decoder.VALID) &&
      exeCtrl(borb.common.Common.LANE_SEL) &&
      exeCtrl(IssueSemantics.PROPS).isControlFlow
    val controlHazardBusy = srcHasControlFlow || exeHasControlFlow
    when(!relaxControlFlowSerialization) {
      Array(3, 4, 5, 6).map(pipeline.ctrl(_)).foreach { ctrl =>
        ctrl.haltWhen(controlHazardBusy)
      }
      srcCtrl.haltWhen(exeHasControlFlow)
    }
    when(srcCtrl.up.isValid && (srcCtrl.up(SPEC_EPOCH) =/= currentEpoch)) {
      srcCtrl.up(LANE_SEL).allowOverride := False
    }
    when(
      pipeline.ctrl(9).up.isValid &&
      (pipeline.ctrl(9).up(SPEC_EPOCH) =/= currentEpoch) &&
      !pipeline.ctrl(9).up(SELF_REDIRECT)
    ) {
      pipeline.ctrl(9).up(LANE_SEL).allowOverride := False
      pipeline.ctrl(9).up(COMMIT).allowOverride := False
    }

    val exeIntProducer = exeCtrl.up.isValid &&
      exeCtrl(Decoder.VALID) &&
      exeCtrl(borb.common.Common.LANE_SEL) &&
      exeCtrl(IssueSemantics.PROPS).writesIntRd &&
      (exeCtrl(Decoder.RD_ADDR) =/= 0)
    val srcNeedsExeRdRs1 = srcCtrl(IssueSemantics.PROPS).readsIntRs1 &&
      (srcCtrl(Decoder.RS1_ADDR) === exeCtrl(Decoder.RD_ADDR))
    val srcNeedsExeRdRs2 = srcCtrl(IssueSemantics.PROPS).readsIntRs2 &&
      (srcCtrl(Decoder.RS2_ADDR) === exeCtrl(Decoder.RD_ADDR))
    when(!relaxIntProducerHazards) {
      srcCtrl.haltWhen(exeIntProducer && (srcNeedsExeRdRs1 || srcNeedsExeRdRs2))
    }

    val wbIntProducer = wbCtrl.up.isValid &&
      wbCtrl(Decoder.VALID) &&
      wbCtrl(borb.common.Common.LANE_SEL) &&
      wbCtrl(IssueSemantics.PROPS).writesIntRd &&
      (wbCtrl(Decoder.RD_ADDR) =/= 0)
    val srcNeedsWbRdRs1 = srcCtrl(IssueSemantics.PROPS).readsIntRs1 &&
      (srcCtrl(Decoder.RS1_ADDR) === wbCtrl(Decoder.RD_ADDR))
    val srcNeedsWbRdRs2 = srcCtrl(IssueSemantics.PROPS).readsIntRs2 &&
      (srcCtrl(Decoder.RS2_ADDR) === wbCtrl(Decoder.RD_ADDR))
    when(!relaxIntProducerHazards) {
      srcCtrl.haltWhen(wbIntProducer && (srcNeedsWbRdRs1 || srcNeedsWbRdRs2))
    }

    val lane1OlderStageValid = dispatchCtrl.up.isValid &&
      dispatchCtrl(Decoder.VALID) &&
      dispatchCtrl(LANE_SEL) &&
      (dispatchCtrl.up(SPEC_EPOCH) === currentEpoch)
    val lane1PairDecisionCycle = lane1PairPending &&
      lane1OlderStageValid &&
      (dispatchCtrl.up(borb.fetch.Fetch.FETCH_SEQ) === lane1PairOlderSeq)
    val lane1OlderProps = dispatchCtrl(IssueSemantics.PROPS)
    case class LaneIssueView(
        props: IssuePropertyBundle,
        microCode: borb.common.MicroCode.C,
        rdAddr: Bits,
        rs1Addr: Bits,
        rs2Addr: Bits
    )
    val issueLane0 = LaneIssueView(
      lane1OlderProps,
      dispatchCtrl(Decoder.MicroCode),
      dispatchCtrl(Decoder.RD_ADDR),
      dispatchCtrl(Decoder.RS1_ADDR),
      dispatchCtrl(Decoder.RS2_ADDR)
    )
    val issueLane1 = LaneIssueView(
      lane1IssueProps,
      lane1Decoded.microCode,
      lane1Decoded.rdAddr,
      lane1Decoded.rs1Addr,
      lane1Decoded.rs2Addr
    )
    def issuePairable(view: LaneIssueView): Bool =
      !view.props.pairBarrier && (!view.props.isControlFlow || isConditionalBranch(view.microCode))
    def olderBusyReject(younger: LaneIssueView): Bool = {
      val rs1Busy = younger.props.readsIntRs1 && (younger.rs1Addr =/= 0) && dispatcher.hcs.regBusy(younger.rs1Addr.asUInt)
      val rs2Busy = younger.props.readsIntRs2 && (younger.rs2Addr =/= 0) && dispatcher.hcs.regBusy(younger.rs2Addr.asUInt)
      rs1Busy || rs2Busy
    }
    def sameCycleRawReject(older: LaneIssueView, younger: LaneIssueView): Bool =
      older.props.writesIntRd &&
      (older.rdAddr =/= 0) &&
      (
        (younger.props.readsIntRs1 && (younger.rs1Addr === older.rdAddr)) ||
        (younger.props.readsIntRs2 && (younger.rs2Addr === older.rdAddr))
      )
    def sameCycleWawReject(older: LaneIssueView, younger: LaneIssueView): Bool =
      older.props.writesIntRd &&
      younger.props.writesIntRd &&
      (older.rdAddr =/= 0) &&
      (younger.rdAddr === older.rdAddr)
    val laneIssueMatrixOlderPairable = issuePairable(issueLane0)
    val laneIssueMatrixYoungerBusy = olderBusyReject(issueLane1)
    val laneIssueMatrixSameCycleRaw = sameCycleRawReject(issueLane0, issueLane1)
    val laneIssueMatrixSameCycleWaw = sameCycleWawReject(issueLane0, issueLane1)
    val laneIssueMatrixYoungerMemory = issueLane1.props.isLoad || issueLane1.props.isStore
    val lane1OlderMemory = lane1OlderProps.isLoad || lane1OlderProps.isStore
    val lane1OlderFp =
      lane1OlderProps.readsFpRs1 || lane1OlderProps.readsFpRs2 || lane1OlderProps.readsFpRs3 || lane1OlderProps.writesFpRd
    val lane1PairAccepted = lane1PairDecisionCycle &&
      lane1CandidateValid &&
      lane1Decoded.valid &&
      lane1IssueProps.lane1Compatible &&
      !laneIssueMatrixYoungerMemory &&
      (!lane1IssueProps.isControlFlow || lane1IsConditionalBranch) &&
      laneIssueMatrixOlderPairable &&
      !lane1OlderProps.pairBarrier &&
      !lane1IssueProps.pairBarrier &&
      !laneIssueMatrixYoungerBusy &&
      !laneIssueMatrixSameCycleRaw &&
      !laneIssueMatrixSameCycleWaw
    val lane1PairRejected = lane1PairDecisionCycle && !lane1PairAccepted

    def initLaneSlotFrom(dst: PipelineSlot, src: PipelineSlot): Unit = {
      dst.valid := src.valid
      dst.epoch := src.epoch
      dst.fetchSeq := src.fetchSeq
      dst.olderSeq := src.olderSeq
      dst.bundleSeq := src.bundleSeq
      dst.slotIdx := src.slotIdx
      dst.slotCount := src.slotCount
      dst.ftqIdx := src.ftqIdx
      dst.pc := src.pc
      dst.blockPc := src.blockPc
      dst.byteOffset := src.byteOffset
      dst.predictedValid := src.predictedValid
      dst.predictedTaken := src.predictedTaken
      dst.predictedTarget := src.predictedTarget
      dst.decodedInstruction := src.decodedInstruction
      dst.isCompressed := src.isCompressed
      dst.legal := src.legal
      dst.microCode := src.microCode
      dst.rdAddr := src.rdAddr
      dst.rs1Addr := src.rs1Addr
      dst.rs2Addr := src.rs2Addr
      dst.rs3Addr := src.rs3Addr
      dst.issueProps := src.issueProps
      dst.waitForOlderCommit := src.waitForOlderCommit
      dst.selectedPipe := src.selectedPipe
      dst.sendToAlu := src.sendToAlu
      dst.sendToBranch := src.sendToBranch
    }

    val lane1IssueCapture = PipelineSlot(config)
    lane1IssueCapture := buildLaneIssueSlot(
      LaneId.Lane1,
      lane1PairAccepted,
      lane1Candidate,
      lane1PairOlderSeq,
      lane1Decoded,
      lane1IssueProps,
      lane1OlderMemory || lane1OlderFp
    )

    case class LaneIntReadPorts(rs1: Int, rs2: Int)
    val laneIntReadPorts = Seq(
      LaneIntReadPorts(rs1 = 0, rs2 = 1),
      LaneIntReadPorts(rs1 = 2, rs2 = 3)
    )
    def connectLaneIntReadPorts(laneId: Int, slot: PipelineSlot): Unit = {
      val ports = laneIntReadPorts(laneId)
      srcPlugin.regfileread.regfile.io.reads(ports.rs1).address := slot.rs1Addr.asUInt
      srcPlugin.regfileread.regfile.io.reads(ports.rs1).valid := slot.valid && slot.issueProps.readsIntRs1
      srcPlugin.regfileread.regfile.io.reads(ports.rs2).address := slot.rs2Addr.asUInt
      srcPlugin.regfileread.regfile.io.reads(ports.rs2).valid := slot.valid && slot.issueProps.readsIntRs2
    }
    def resolveLaneIntRead(laneId: Int, slot: PipelineSlot, rs2: Boolean): Bits = {
      val ports = laneIntReadPorts(laneId)
      val readPort = if(rs2) ports.rs2 else ports.rs1
      val readValid = slot.valid && (if(rs2) slot.issueProps.readsIntRs2 else slot.issueProps.readsIntRs1)
      val readAddress = if(rs2) slot.rs2Addr.asUInt else slot.rs1Addr.asUInt
      SrcPlugin.resolveIntRead(
        readValid,
        readAddress,
        srcPlugin.regfileread.regfile.io.reads(readPort).data,
        Seq(integerBackend.exeIntBypass, integerBackend.wbIntBypass)
      )
    }

    connectLaneIntReadPorts(LaneId.Lane1, lane1DecodeSlot)

    val lane1Rs1Resolved = resolveLaneIntRead(LaneId.Lane1, lane1DecodeSlot, rs2 = false)
    val lane1Rs2Resolved = resolveLaneIntRead(LaneId.Lane1, lane1DecodeSlot, rs2 = true)
    val lane1Imm = new IMM(lane1DecodeSlot.decodedInstruction)
    val lane1ImmValue = Bits(64 bits)
    lane1ImmValue := B(0, 64 bits)
    switch(lane1DecodeSlot.issueProps.immSel) {
      is(borb.frontend.Imm_Select.I_IMM) { lane1ImmValue := lane1Imm.i_sext.asBits }
      is(borb.frontend.Imm_Select.S_IMM) { lane1ImmValue := lane1Imm.s_sext.asBits }
      is(borb.frontend.Imm_Select.B_IMM) { lane1ImmValue := lane1Imm.b_sext.asBits }
      is(borb.frontend.Imm_Select.U_IMM) { lane1ImmValue := lane1Imm.u_sext.asBits }
      is(borb.frontend.Imm_Select.J_IMM) { lane1ImmValue := lane1Imm.j_sext.asBits }
      default                            { lane1ImmValue := B(0, 64 bits) }
    }

    val lane1ExecResult = IntAlu.computeResult(lane1SrcSlot.microCode, lane1SrcSlot.rs1, lane1SrcSlot.rs2, lane1SrcSlot.immed, lane1SrcSlot.pc)
    val lane1BranchResolution = borb.execute.Branch.resolve(
      lane1SrcSlot.microCode,
      lane1SrcSlot.rs1,
      lane1SrcSlot.rs2,
      lane1SrcSlot.pc,
      lane1SrcSlot.immed,
      withCompressed = config.cExtensionEnabled
    )
    val lane1ExecWritesInt = lane1SrcSlot.issueProps.writesIntRd &&
      lane1SrcSlot.sendToAlu &&
      lane1SrcSlot.valid &&
      (lane1SrcSlot.legal === borb.frontend.YESNO.Y)
    val lane1BranchResolved = lane1ExecSlot.valid && lane1ExecSlot.sendToBranch && lane1ExecSlot.issueProps.isControlFlow
    val lane1BranchMispredict = lane1BranchResolved && (
      (lane1ExecSlot.branchTaken =/= lane1ExecSlot.predictedTaken) ||
      (lane1ExecSlot.branchTaken && lane1ExecSlot.predictedTaken && (lane1ExecSlot.branchTarget =/= lane1ExecSlot.predictedTarget))
    )

    val suppressPrefetchInTrapService = RegInit(False)
    when(trapLogic.redirect.trapFire) {
      suppressPrefetchInTrapService := True
    } elsewhen(trapLogic.redirect.mretFire) {
      suppressPrefetchInTrapService := False
    }
    val suppressPrefetchBase =
      suppressPrefetchInTrapService ||
      trapLogic.redirect.trapFire ||
      trapLogic.redirect.mretFire

    // ========== Speculation Epoch Architecture ==========
    // Clean, scalable speculation handling for in-order superscalar CPU
    //
    // Design:
    // - Global epoch counter maintained here, passed to Fetch
    // - Each instruction is tagged with SPEC_EPOCH when it enters the pipeline
    // - When a branch is TAKEN (flushPipeline), epoch increments
    // - All instructions with old epoch are flushed (their SPEC_EPOCH != currentEpoch)
    
    // Flush Logic - fires when a non-stale branch/jump redirects.
    val execEpochMatches = pipeline.ctrl(8)(SPEC_EPOCH) === currentEpoch
    val execStageValid = pipeline.ctrl(8).up.isValid
    val fenceiRedirect = execStageValid &&
      pipeline.ctrl(8).up.isFiring &&
      execEpochMatches &&
      pipeline.ctrl(8)(Decoder.VALID) &&
      pipeline.ctrl(8)(borb.common.Common.LANE_SEL) &&
      (pipeline.ctrl(8)(Decoder.MicroCode) === uopFENCE_I)
    val predictedValid = pipeline.ctrl(8).up(borb.fetch.Fetch.FETCH_PREDICTED_VALID)
    val predictedTaken = pipeline.ctrl(8).up(borb.fetch.Fetch.FETCH_PREDICTED_TAKEN)
    val predictedTarget = pipeline.ctrl(8).up(borb.fetch.Fetch.FETCH_PREDICTED_TARGET)
    val frontendPredictedRedirectsEnabled = config.frontendConfig.predictedRedirectEnabled
    val frontendPredictorTrainingEnabled = config.frontendConfig.predictorTrainingEnabled
    val actualTaken = branch.actualTaken
    val actualTarget = branch.actualTarget
    val controlResolved = branch.branchResolved && execEpochMatches
    val branchMispredict = controlResolved && (
      (actualTaken =/= predictedTaken) ||
      (actualTaken && predictedTaken && (actualTarget =/= predictedTarget))
    )
    val lane1ExecEpochMatches = lane1ExecSlot.valid && (lane1ExecSlot.epoch === currentEpoch)
    val lane1BranchRedirect = lane1ExecEpochMatches && lane1BranchMispredict && !trapLogic.redirect.trapFire && !branchMispredict
    val branchRedirect = branchMispredict && !trapLogic.redirect.trapFire
    val flushPipeline = branchRedirect || lane1BranchRedirect
    val trapRedirect = execStageValid && trapLogic.redirect.trapFire && execEpochMatches
    val mretRedirect = execStageValid && trapLogic.redirect.mretFire && execEpochMatches
    val redirectPipeline = flushPipeline || trapRedirect || mretRedirect || fenceiRedirect
    pipeline.ctrl(8).down(SELF_REDIRECT) := branchRedirect || trapRedirect || mretRedirect || fenceiRedirect
    val fenceiTarget = pipeline.ctrl(8)(borb.fetch.PC.PC) + U(4, 64 bits)
    val redirectEpochValue = (currentEpoch + 1).resized

    pc.redirect.valid.allowOverride := branchRedirect || lane1BranchRedirect || mretRedirect || fenceiRedirect
    pc.redirect.payload.target := Mux(
      mretRedirect,
      trapLogic.redirect.mretTarget,
      Mux(
        fenceiRedirect,
        fenceiTarget,
        Mux(
          lane1BranchRedirect,
          Mux(lane1ExecSlot.branchTaken, lane1ExecSlot.branchTarget, lane1ExecSlot.fallthrough),
          Mux(actualTaken, actualTarget, branch.fallthroughPc)
        )
      )
    )
    pc.redirect.payload.reason := FrontendRedirectReason.branch
    when(mretRedirect) {
      pc.redirect.payload.reason := FrontendRedirectReason.mret
    } elsewhen(fenceiRedirect) {
      pc.redirect.payload.reason := FrontendRedirectReason.fencei
    }
    pc.redirect.payload.epoch := redirectEpochValue
    pc.redirect.payload.flushFrontend := True
    pc.jump.valid := pc.redirect.valid
    pc.jump.payload.target := pc.redirect.payload.target
    pc.jump.payload.is_jump := mretRedirect || fenceiRedirect || branch.actualIsJump
    pc.jump.payload.is_branch := (!mretRedirect) && (!fenceiRedirect) && (branch.actualIsBranch || lane1BranchRedirect)
    
    // Increment epoch on taken branch
    when(branchRedirect) {
      currentEpoch := currentEpoch + 1
    }
    when(lane1BranchRedirect) {
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
      ctrl.up.isValid && (if(idx == 3) ctrl(Decoder.VALID) else ctrl.up(Decoder.VALID))
    }
    def stageLogicallyLive(idx: Int): Bool = {
      val ctrl = pipeline.ctrl(idx)
      val stageLaneLive = if(idx < 7) True else ctrl.up(LANE_SEL)
      ctrl.up.isValid && stageDecodedValid(idx) && stageLaneLive
    }
    val wbStageValidForRedirect = stageLogicallyLive(9)
    val redirectingBundleSeq = UInt(32 bits)
    redirectingBundleSeq := Mux(lane1BranchRedirect, lane1ExecSlot.bundleSeq, pipeline.ctrl(8).up(borb.fetch.Fetch.FETCH_BUNDLE_SEQ))
    val redirectingSlotIdx = UInt(2 bits)
    redirectingSlotIdx := Mux(lane1BranchRedirect, lane1ExecSlot.slotIdx, pipeline.ctrl(8).up(borb.fetch.Fetch.FETCH_SLOT_IDX))
    val redirectingSeq = UInt(32 bits)
    redirectingSeq := Mux(lane1BranchRedirect, lane1ExecSlot.fetchSeq, pipeline.ctrl(8).up(borb.fetch.Fetch.FETCH_SEQ))
    val wbBundleSeq = pipeline.ctrl(9).up(borb.fetch.Fetch.FETCH_BUNDLE_SEQ)
    val wbSlotIdx = pipeline.ctrl(9).up(borb.fetch.Fetch.FETCH_SLOT_IDX)
    val redirectRspBubbleCounter = Reg(UInt(2 bits)) init(0)
    val redirectCommitPending = RegInit(False)
    val redirectCommitSeq = Reg(UInt(32 bits)) init(0)
    val redirectCommitBundleSeq = Reg(UInt(32 bits)) init(0)
    val redirectCommitSlotIdx = Reg(UInt(2 bits)) init(0)
    val rspOldEpoch = pipeline.ctrl(2).up.isValid &&
      (pipeline.ctrl(2)(SPEC_EPOCH) =/= currentEpoch)
    when(rspOldEpoch) {
      pipeline.ctrl(2).up.valid.allowOverride := False
    }
    val wbYoungerThanRedirect = wbStageValidForRedirect &&
      pipeline.ctrl(8).up.isValid &&
      pipeline.ctrl(8).up(Decoder.VALID) &&
      LaneContracts.isYounger(wbBundleSeq, wbSlotIdx, redirectingBundleSeq, redirectingSlotIdx)
    def stageIsRedirectOrigin(idx: Int): Bool = {
      val ctrl = pipeline.ctrl(idx)
      redirectCommitPending &&
      ctrl.up.isValid &&
      LaneContracts.sameSlot(
        ctrl.up(borb.fetch.Fetch.FETCH_BUNDLE_SEQ),
        ctrl.up(borb.fetch.Fetch.FETCH_SLOT_IDX),
        redirectCommitBundleSeq,
        redirectCommitSlotIdx
      )
    }
    val youngerThanPendingRedirect = Array(3, 4, 5, 6, 7, 8, 9).map { idx =>
      val ctrl = pipeline.ctrl(idx)
      stageLogicallyLive(idx) &&
      !stageIsRedirectOrigin(idx) &&
      LaneContracts.isYounger(
        ctrl.up(borb.fetch.Fetch.FETCH_BUNDLE_SEQ),
        ctrl.up(borb.fetch.Fetch.FETCH_SLOT_IDX),
        redirectCommitBundleSeq,
        redirectCommitSlotIdx
      )
    }.reduce(_ || _)
    val staleEpochBehindRedirect = Array(3, 4, 5, 6, 7, 8, 9).map { idx =>
      val ctrl = pipeline.ctrl(idx)
      stageLogicallyLive(idx) &&
      !stageIsRedirectOrigin(idx) &&
      (ctrl.up(SPEC_EPOCH) =/= currentEpoch)
    }.reduce(_ || _)
    when(redirectPipeline) {
      redirectRspBubbleCounter := U(2, redirectRspBubbleCounter.getWidth bits)
      redirectCommitPending := True
      redirectCommitSeq := redirectingSeq
      redirectCommitBundleSeq := redirectingBundleSeq
      redirectCommitSlotIdx := redirectingSlotIdx.resized
    } elsewhen(redirectRspBubbleCounter =/= 0) {
      redirectRspBubbleCounter := redirectRspBubbleCounter - 1
    }
    val rspResident = pipeline.ctrl(2).up.isValid
    when(redirectCommitPending && !redirectPipeline && !rspResident && !rspOldEpoch && !staleEpochBehindRedirect && !youngerThanPendingRedirect) {
      redirectCommitPending := False
    }
    val redirectRspPending = redirectRspBubbleCounter =/= 0
    val wbYoungerThanPendingRedirect = redirectCommitPending &&
      wbStageValidForRedirect &&
      LaneContracts.isYounger(wbBundleSeq, wbSlotIdx, redirectCommitBundleSeq, redirectCommitSlotIdx)
    lsuKillOutstanding := redirectPipeline || redirectCommitPending
    lsuKillCboZero := redirectPipeline
    when(
      pipeline.ctrl(8).up.isValid &&
      pipeline.ctrl(8).up(Decoder.VALID) &&
      redirectCommitPending &&
      (pipeline.ctrl(8).up(SPEC_EPOCH) =/= currentEpoch)
    ) {
      pipeline.ctrl(8).up(LANE_SEL).allowOverride := False
    }
    // A taken redirect discovered in execute can coincide with a wrong-path
    // younger instruction already sitting in writeback. Squash that commit in
    // the same cycle, then keep the existing next-cycle bubble to catch any
    // younger instruction that would otherwise slide forward one stage later.
    val redirectCommitBubble = (redirectPipeline && wbYoungerThanRedirect) || wbYoungerThanPendingRedirect
    // A redirecting stage-6 instruction can otherwise allow a younger
    // same-epoch payload to slide forward one stage before its redirect is
    // observed. Track the redirecting sequence so only younger instructions
    // are flushed; older lagging instructions must still be allowed to retire.
    val redirectExecuteBubble = redirectCommitPending &&
      pipeline.ctrl(8).up.isValid &&
      pipeline.ctrl(8).up(Decoder.VALID) &&
      LaneContracts.isYounger(
        pipeline.ctrl(8).up(borb.fetch.Fetch.FETCH_BUNDLE_SEQ),
        pipeline.ctrl(8).up(borb.fetch.Fetch.FETCH_SLOT_IDX),
        redirectCommitBundleSeq,
        redirectCommitSlotIdx
      )
    
    // Connect epoch to Fetch so new instructions get tagged with current epoch
    fetch.io.currentEpoch := currentEpoch
    val branchBlockPc = UInt(64 bits)
    branchBlockPc := pipeline.ctrl(8).up(borb.fetch.PC.PC)
    if(config.frontendConfig.fetchBlockBytes > 1) {
      branchBlockPc(log2Up(config.frontendConfig.fetchBlockBytes) - 1 downto 0) := 0
    }
    val takenByteOffset = UInt(log2Up(config.frontendConfig.fetchBlockBytes max 2) bits)
    takenByteOffset := pipeline.ctrl(8).up(borb.fetch.PC.PC)(log2Up(config.frontendConfig.fetchBlockBytes max 2) - 1 downto 0)
    val isCall = branch.actualIsJump &&
      (
        (pipeline.ctrl(8).up(Decoder.MicroCode) === uopJAL && ((pipeline.ctrl(8).up(Decoder.RD_ADDR) === B"00001") || (pipeline.ctrl(8).up(Decoder.RD_ADDR) === B"00101"))) ||
        (pipeline.ctrl(8).up(Decoder.MicroCode) === uopJALR && ((pipeline.ctrl(8).up(Decoder.RD_ADDR) === B"00001") || (pipeline.ctrl(8).up(Decoder.RD_ADDR) === B"00101")))
      )
    val isReturn = (pipeline.ctrl(8).up(Decoder.MicroCode) === uopJALR) &&
      (pipeline.ctrl(8).up(Decoder.RD_ADDR) === B"00000") &&
      ((pipeline.ctrl(8).up(Decoder.RS1_ADDR) === B"00001") || (pipeline.ctrl(8).up(Decoder.RS1_ADDR) === B"00101"))
    val isIndirect = (pipeline.ctrl(8).up(Decoder.MicroCode) === uopJALR) && !isReturn
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
          Mux(
            lane1BranchRedirect,
            Mux(lane1ExecSlot.branchTaken, lane1ExecSlot.branchTarget, lane1ExecSlot.fallthrough),
            Mux(actualTaken, actualTarget, branch.fallthroughPc)
          )
        )
      )
    )
    fetch.io.recover.payload.redirectReason := FrontendRedirectReason.branch
    when(trapRedirect) {
      fetch.io.recover.payload.redirectReason := FrontendRedirectReason.trap
    } elsewhen(mretRedirect) {
      fetch.io.recover.payload.redirectReason := FrontendRedirectReason.mret
    } elsewhen(fenceiRedirect) {
      fetch.io.recover.payload.redirectReason := FrontendRedirectReason.fencei
    }
    fetch.io.recover.payload.epoch := redirectEpochValue
    fetch.io.recover.payload.invalidateIcache := fenceiRedirect
    fetch.io.recover.payload.recovery.valid := Mux(lane1BranchRedirect, lane1ExecSlot.valid, pipeline.ctrl(8).up.isValid)
    fetch.io.recover.payload.recovery.ftqIndex := Mux(lane1BranchRedirect, lane1ExecSlot.ftqIdx, pipeline.ctrl(8).up(borb.fetch.Fetch.FETCH_FTQ_IDX).resized)
    fetch.io.recover.payload.recovery.bundleSeq := Mux(lane1BranchRedirect, lane1ExecSlot.bundleSeq, pipeline.ctrl(8).up(borb.fetch.Fetch.FETCH_BUNDLE_SEQ))
    fetch.io.recover.payload.recovery.slotIdx := Mux(lane1BranchRedirect, lane1ExecSlot.slotIdx, pipeline.ctrl(8).up(borb.fetch.Fetch.FETCH_SLOT_IDX).resized)
    fetch.io.recover.payload.recovery.blockPc := Mux(lane1BranchRedirect, lane1ExecSlot.blockPc, pipeline.ctrl(8).up(borb.fetch.Fetch.FETCH_BLOCK_PC))
    fetch.io.recover.payload.recovery.byteOffsetInBlock := Mux(lane1BranchRedirect, lane1ExecSlot.byteOffset, pipeline.ctrl(8).up(borb.fetch.Fetch.FETCH_BYTE_OFFSET).resized)

    fetch.io.branchResolve.valid := (branch.branchResolved && execEpochMatches) || (!branch.branchResolved && lane1BranchResolved && lane1ExecEpochMatches)
    fetch.io.branchResolve.payload.epoch := currentEpoch
    fetch.io.branchResolve.payload.ftqIndex := Mux(branch.branchResolved && execEpochMatches, pipeline.ctrl(8).up(borb.fetch.Fetch.FETCH_FTQ_IDX).resized, lane1ExecSlot.ftqIdx)
    fetch.io.branchResolve.payload.bundleSeq := Mux(branch.branchResolved && execEpochMatches, pipeline.ctrl(8).up(borb.fetch.Fetch.FETCH_BUNDLE_SEQ), lane1ExecSlot.bundleSeq)
    fetch.io.branchResolve.payload.slotIdx := Mux(branch.branchResolved && execEpochMatches, pipeline.ctrl(8).up(borb.fetch.Fetch.FETCH_SLOT_IDX).resized, lane1ExecSlot.slotIdx)
    fetch.io.branchResolve.payload.pc := Mux(branch.branchResolved && execEpochMatches, pipeline.ctrl(8).up(borb.fetch.PC.PC), lane1ExecSlot.pc)
    fetch.io.branchResolve.payload.blockPc := Mux(branch.branchResolved && execEpochMatches, pipeline.ctrl(8).up(borb.fetch.Fetch.FETCH_BLOCK_PC), lane1ExecSlot.blockPc)
    fetch.io.branchResolve.payload.byteOffsetInBlock := Mux(branch.branchResolved && execEpochMatches, pipeline.ctrl(8).up(borb.fetch.Fetch.FETCH_BYTE_OFFSET).resized, lane1ExecSlot.byteOffset)
    fetch.io.branchResolve.payload.fallthrough := Mux(branch.branchResolved && execEpochMatches, branch.fallthroughPc, lane1ExecSlot.fallthrough)
    fetch.io.branchResolve.payload.actualTaken := Mux(branch.branchResolved && execEpochMatches, actualTaken, lane1ExecSlot.branchTaken)
    fetch.io.branchResolve.payload.actualTarget := Mux(branch.branchResolved && execEpochMatches, actualTarget, lane1ExecSlot.branchTarget)
    fetch.io.branchResolve.payload.predictedValid := Mux(branch.branchResolved && execEpochMatches, predictedValid, lane1ExecSlot.predictedValid)
    fetch.io.branchResolve.payload.predictedTaken := Mux(branch.branchResolved && execEpochMatches, predictedTaken, lane1ExecSlot.predictedTaken)
    fetch.io.branchResolve.payload.predictedTarget := Mux(branch.branchResolved && execEpochMatches, predictedTarget, lane1ExecSlot.predictedTarget)
    fetch.io.branchResolve.payload.mispredict := Mux(branch.branchResolved && execEpochMatches, branchMispredict, lane1BranchMispredict)
    fetch.io.branchResolve.payload.isConditional := Mux(branch.branchResolved && execEpochMatches, branch.actualIsBranch, True)
    fetch.io.branchResolve.payload.isJump := Mux(branch.branchResolved && execEpochMatches, branch.actualIsJump, False)
    fetch.io.branchResolve.payload.isCall := Mux(branch.branchResolved && execEpochMatches, isCall, False)
    fetch.io.branchResolve.payload.isReturn := Mux(branch.branchResolved && execEpochMatches, isReturn, False)
    fetch.io.branchResolve.payload.isIndirect := Mux(branch.branchResolved && execEpochMatches, isIndirect, False)

    fetch.io.indirectResolve.valid := branch.branchResolved && execEpochMatches && isIndirect && actualTaken
    fetch.io.indirectResolve.payload.epoch := currentEpoch
    fetch.io.indirectResolve.payload.ftqIndex := pipeline.ctrl(8).up(borb.fetch.Fetch.FETCH_FTQ_IDX).resized
    fetch.io.indirectResolve.payload.bundleSeq := pipeline.ctrl(8).up(borb.fetch.Fetch.FETCH_BUNDLE_SEQ)
    fetch.io.indirectResolve.payload.slotIdx := pipeline.ctrl(8).up(borb.fetch.Fetch.FETCH_SLOT_IDX).resized
    fetch.io.indirectResolve.payload.blockPc := pipeline.ctrl(8).up(borb.fetch.Fetch.FETCH_BLOCK_PC)
    fetch.io.indirectResolve.payload.byteOffsetInBlock := pipeline.ctrl(8).up(borb.fetch.Fetch.FETCH_BYTE_OFFSET).resized
    fetch.io.indirectResolve.payload.target := actualTarget
    fetch.io.indirectResolve.payload.history := 0
    val lane1LeadConsume = pipeline.ctrl(2).down.isFiring &&
      !lane1Inflight &&
      fetch.scalarBoundaryValid &&
      (fetch.scalarBoundaryPayload.slotCount > U(1, fetch.scalarBoundaryPayload.slotCount.getWidth bits)) &&
      (fetch.scalarBoundaryPayload.slotIdx === 0) &&
      lane0BoundaryDecoded.valid &&
      lane0BoundaryOlderPairable &&
      fetch.adapter.io.slot1PreviewValid &&
      lane1PreviewDecoded.valid &&
      lane1PreviewProps.lane1Compatible &&
      (!lane1PreviewProps.isControlFlow || lane1PreviewIsConditionalBranch) &&
      !lane1BoundaryStaticRaw &&
      !lane1BoundaryStaticWaw
    fetch.io.scalarHold := redirectPipeline || redirectRspPending || redirectCommitPending || rspOldEpoch
    fetch.io.scalarDeferRefill := lane1LeadConsume || lane1PairPending
    fetch.io.scalarSkip := lane1PairAccepted
    fetch.io.scalarConsume := pipeline.ctrl(2).down.isFiring &&
      !(redirectPipeline || redirectRspPending || redirectCommitPending || rspOldEpoch)
    
    // Flush fetch/decode/src younger stages on redirect so a new target beat
    // cannot be consumed against a stale stage-local PC offset.
    // Note: Stage 6 (Execute) is excluded from unconditional redirect kill -
    //       the redirecting instruction executes. Stage 7 is now explicitly
    //       seq-filtered below so younger wrong-path writeback occupants are
    //       dropped instead of merely having their commit suppressed.
    pipeline.ctrl(1).throwWhen(redirectPipeline || redirectRspPending || redirectCommitPending || rspOldEpoch)
    // Stage 2 sits before sequence tagging becomes fully instruction-local, so
    // on a redirect it is always younger than execute and must be dropped
    // unconditionally. Hold it in bubble state until the seq-tracked younger
    // backend stages are drained; otherwise a stale scalar fetched before the
    // redirect can still slip forward after the short rsp-only bubble expires.
    val stage2Kill = redirectPipeline || redirectRspPending || redirectCommitPending || rspOldEpoch
    pipeline.ctrl(2).throwWhen(stage2Kill)
    when(stage2Kill) {
      pipeline.ctrl(2).up.valid.allowOverride := False
    }
    Array(3, 4, 5, 6, 7).map(pipeline.ctrl(_)).foreach { ctrl =>
      val stageDecodedValid = if(ctrl == pipeline.ctrl(3)) ctrl(Decoder.VALID) else ctrl.up(Decoder.VALID)
      val stageOldEpoch = ctrl.up.isValid && (ctrl.up(SPEC_EPOCH) =/= currentEpoch)
      val stageKill = redirectPipeline || redirectRspPending || redirectCommitPending || stageOldEpoch
      ctrl.throwWhen(stageKill)
      when(stageKill) {
        ctrl.up.valid.allowOverride := False
        stageDecodedValid.allowOverride := False
        if(ctrl == pipeline.ctrl(7)) {
          ctrl.up(LANE_SEL).allowOverride := False
        }
      }
    }
    val executeOldEpoch = pipeline.ctrl(8).up.isValid &&
      pipeline.ctrl(8).up(Decoder.VALID) &&
      (pipeline.ctrl(8).up(SPEC_EPOCH) =/= currentEpoch)
    val wbOldEpoch = pipeline.ctrl(9).up.isValid &&
      pipeline.ctrl(9).up(Decoder.VALID) &&
      (pipeline.ctrl(9).up(SPEC_EPOCH) =/= currentEpoch) &&
      !pipeline.ctrl(9).up(SELF_REDIRECT)
    val executeKill = redirectExecuteBubble || executeOldEpoch
    val wbKill = (redirectPipeline && wbYoungerThanRedirect) || wbYoungerThanPendingRedirect || wbOldEpoch
    pipeline.ctrl(8).throwWhen(executeKill)
    pipeline.ctrl(9).throwWhen(wbKill)
    when(executeKill) {
      pipeline.ctrl(8).up.valid.allowOverride := False
      pipeline.ctrl(8).up(Decoder.VALID).allowOverride := False
      pipeline.ctrl(8).up(LANE_SEL).allowOverride := False
    }
    when(wbKill) {
      pipeline.ctrl(9).up.valid.allowOverride := False
      pipeline.ctrl(9).up(Decoder.VALID).allowOverride := False
      pipeline.ctrl(9).up(LANE_SEL).allowOverride := False
      pipeline.ctrl(9).up(COMMIT).allowOverride := False
      pipeline.ctrl(9).up(TRAP).allowOverride := False
    }

    val lane1SrcSlotNext = PipelineSlot(config)
    initLaneSlotFrom(lane1SrcSlotNext, lane1DecodeSlot)
    lane1SrcSlotNext.rs1 := lane1DecodeSlot.issueProps.readsIntRs1 ? lane1Rs1Resolved | B(0, 64 bits)
    lane1SrcSlotNext.rs2 := lane1DecodeSlot.issueProps.readsIntRs2 ? lane1Rs2Resolved | B(0, 64 bits)
    lane1SrcSlotNext.immed := lane1ImmValue
    lane1SrcSlotNext.branchTaken := False
    lane1SrcSlotNext.branchTarget := 0
    lane1SrcSlotNext.branchIsBranch := False
    lane1SrcSlotNext.branchIsJump := False
    lane1SrcSlotNext.fallthrough := 0
    lane1SrcSlotNext.result.valid := False
    lane1SrcSlotNext.result.address := 0
    lane1SrcSlotNext.result.data := 0
    lane1SrcSlotNext.commit := False

    val lane1ExecSlotNext = PipelineSlot(config)
    initLaneSlotFrom(lane1ExecSlotNext, lane1SrcSlot)
    lane1ExecSlotNext.branchTaken := lane1BranchResolution.doJump && !lane1BranchResolution.willTrap
    lane1ExecSlotNext.branchTarget := lane1BranchResolution.target
    lane1ExecSlotNext.branchIsBranch := lane1BranchResolution.isBranch
    lane1ExecSlotNext.branchIsJump := lane1BranchResolution.isJump
    lane1ExecSlotNext.fallthrough := lane1SrcSlot.pc + Mux(lane1SrcSlot.isCompressed, U(2, 64 bits), U(4, 64 bits))
    lane1ExecSlotNext.result.valid := lane1ExecWritesInt
    lane1ExecSlotNext.result.address := lane1SrcSlot.rdAddr.asUInt
    lane1ExecSlotNext.result.data := (lane1SrcSlot.rdAddr === B"00000") ? B(0, 64 bits) | lane1ExecResult
    when(lane1SrcSlot.sendToBranch && lane1SrcSlot.issueProps.isControlFlow && lane1BranchResolution.isJump) {
      lane1ExecSlotNext.result.valid := lane1SrcSlot.valid && (lane1SrcSlot.legal === borb.frontend.YESNO.Y)
      lane1ExecSlotNext.result.address := lane1SrcSlot.rdAddr.asUInt
      lane1ExecSlotNext.result.data := (lane1SrcSlot.rdAddr === B"00000") ? B(0, 64 bits) | lane1ExecSlotNext.fallthrough.asBits
    }
    lane1ExecSlotNext.commit := False

    val lane1WbSlotNext = PipelineSlot(config)
    initLaneSlotFrom(lane1WbSlotNext, lane1ExecSlot)
    lane1WbSlotNext.rs1 := lane1ExecSlot.rs1
    lane1WbSlotNext.rs2 := lane1ExecSlot.rs2
    lane1WbSlotNext.immed := lane1ExecSlot.immed
    lane1WbSlotNext.branchTaken := lane1ExecSlot.branchTaken
    lane1WbSlotNext.branchTarget := lane1ExecSlot.branchTarget
    lane1WbSlotNext.branchIsBranch := lane1ExecSlot.branchIsBranch
    lane1WbSlotNext.branchIsJump := lane1ExecSlot.branchIsJump
    lane1WbSlotNext.fallthrough := lane1ExecSlot.fallthrough
    lane1WbSlotNext.result.valid := lane1ExecSlot.result.valid
    lane1WbSlotNext.result.address := lane1ExecSlot.result.address
    lane1WbSlotNext.result.data := lane1ExecSlot.result.data
    lane1WbSlotNext.commit := lane1ExecSlot.valid &&
      (((lane1ExecSlot.epoch === currentEpoch) && !redirectCommitPending) || lane1BranchRedirect)

    when(redirectPipeline || redirectRspPending || redirectCommitPending || rspOldEpoch) {
      lane1PairPending := False
    } elsewhen(lane1PairDecisionCycle) {
      lane1PairPending := False
    } elsewhen(lane1LeadConsume) {
      lane1PairPending := True
      lane1PairOlderSeq := fetch.scalarBoundaryPayload.scalarSeq
      lane1PairEpoch := fetch.scalarBoundaryPayload.epoch
    }

    val lane1StageKill = redirectPipeline || redirectRspPending || redirectCommitPending
    val lane1DecodeSlotKilled = lane1StageKill || (lane1DecodeSlot.valid && (lane1DecodeSlot.epoch =/= currentEpoch))
    val lane1SrcSlotKilled = lane1StageKill || (lane1SrcSlot.valid && (lane1SrcSlot.epoch =/= currentEpoch))
    val lane1ExecSlotKilled = ((redirectPipeline && !lane1BranchRedirect) || redirectRspPending || redirectCommitPending) ||
      (lane1ExecSlot.valid && (lane1ExecSlot.epoch =/= currentEpoch))
    val lane1WbSlotKilled = (redirectPipeline && !lane1WbSlot.commit) || redirectRspPending || (redirectCommitPending && !lane1WbSlot.commit) ||
      (lane1WbSlot.valid && (lane1WbSlot.epoch =/= currentEpoch) && !lane1WbSlot.commit)
    val lane1DecodeSlotCanAdvance = !lane1DecodeSlot.valid || !lane1DecodeSlot.waitForOlderCommit || seqRecentlyCommitted(lane1DecodeSlot.olderSeq)

    when(lane1WbSlotKilled) {
      lane1WbSlot.valid := False
      lane1WbSlot.commit := False
      lane1WbSlot.result.valid := False
    } otherwise {
      lane1WbSlot := lane1WbSlotNext
    }
    when(lane1ExecSlotKilled) {
      lane1ExecSlot.valid := False
      lane1ExecSlot.commit := False
      lane1ExecSlot.result.valid := False
    } otherwise {
      lane1ExecSlot := lane1ExecSlotNext
    }
    when(lane1SrcSlotKilled) {
      lane1SrcSlot.valid := False
      lane1SrcSlot.commit := False
      lane1SrcSlot.result.valid := False
    } elsewhen(!lane1DecodeSlotCanAdvance) {
      lane1SrcSlot.valid := False
      lane1SrcSlot.commit := False
      lane1SrcSlot.result.valid := False
    } otherwise {
      lane1SrcSlot := lane1SrcSlotNext
    }
    when(lane1DecodeSlotKilled) {
      lane1DecodeSlot.valid := False
      lane1DecodeSlot.commit := False
      lane1DecodeSlot.result.valid := False
    } elsewhen(lane1DecodeSlot.valid && !lane1DecodeSlotCanAdvance) {
      lane1DecodeSlot := lane1DecodeSlot
    } otherwise {
      lane1DecodeSlot := lane1IssueCapture
    }

    val lane0Retire = RetirePacket(config)
    lane0Retire.valid := pipeline.ctrl(9).up(COMMIT)
    lane0Retire.slot.valid := lane0Retire.valid
    lane0Retire.slot.epoch := pipeline.ctrl(9).up(SPEC_EPOCH)
    lane0Retire.slot.fetchSeq := pipeline.ctrl(9).up(borb.fetch.Fetch.FETCH_SEQ)
    lane0Retire.slot.olderSeq := 0
    lane0Retire.slot.bundleSeq := pipeline.ctrl(9).up(borb.fetch.Fetch.FETCH_BUNDLE_SEQ)
    lane0Retire.slot.slotIdx := pipeline.ctrl(9).up(borb.fetch.Fetch.FETCH_SLOT_IDX).resized
    lane0Retire.slot.slotCount := pipeline.ctrl(9).up(borb.fetch.Fetch.FETCH_SLOT_COUNT).resized
    lane0Retire.slot.ftqIdx := pipeline.ctrl(9).up(borb.fetch.Fetch.FETCH_FTQ_IDX).resized
    lane0Retire.slot.pc := pipeline.ctrl(9).up(borb.fetch.PC.PC)
    lane0Retire.slot.blockPc := pipeline.ctrl(9).up(borb.fetch.Fetch.FETCH_BLOCK_PC)
    lane0Retire.slot.byteOffset := pipeline.ctrl(9).up(borb.fetch.Fetch.FETCH_BYTE_OFFSET).resized
    lane0Retire.slot.predictedValid := pipeline.ctrl(9).up(borb.fetch.Fetch.FETCH_PREDICTED_VALID)
    lane0Retire.slot.predictedTaken := pipeline.ctrl(9).up(borb.fetch.Fetch.FETCH_PREDICTED_TAKEN)
    lane0Retire.slot.predictedTarget := pipeline.ctrl(9).up(borb.fetch.Fetch.FETCH_PREDICTED_TARGET)
    lane0Retire.slot.decodedInstruction := pipeline.ctrl(9).up(Decoder.DECODED_INSTRUCTION)
    lane0Retire.slot.isCompressed := pipeline.ctrl(9).up(Decoder.IS_COMPRESSED)
    lane0Retire.slot.legal := pipeline.ctrl(9).up(Decoder.LEGAL)
    lane0Retire.slot.microCode := pipeline.ctrl(9).up(Decoder.MicroCode)
    lane0Retire.slot.rdAddr := pipeline.ctrl(9).up(Decoder.RD_ADDR)
    lane0Retire.slot.rs1Addr := pipeline.ctrl(9).up(Decoder.RS1_ADDR)
    lane0Retire.slot.rs2Addr := pipeline.ctrl(9).up(Decoder.RS2_ADDR)
    lane0Retire.slot.rs3Addr := pipeline.ctrl(9).up(Decoder.RS3_ADDR)
    lane0Retire.slot.issueProps := pipeline.ctrl(9).up(IssueSemantics.PROPS)
    lane0Retire.slot.waitForOlderCommit := False
    lane0Retire.slot.selectedPipe := pipeline.ctrl(9).up(BackendIssue.SELECTED_PIPE)
    lane0Retire.slot.rs1 := 0
    lane0Retire.slot.rs2 := 0
    lane0Retire.slot.immed := 0
    lane0Retire.slot.sendToAlu := False
    lane0Retire.slot.sendToBranch := False
    lane0Retire.slot.branchTaken := False
    lane0Retire.slot.branchTarget := 0
    lane0Retire.slot.branchIsBranch := False
    lane0Retire.slot.branchIsJump := False
    lane0Retire.slot.fallthrough := 0
    lane0Retire.slot.result.valid := pipeline.ctrl(9).up(WriteBack.RESULT).valid
    lane0Retire.slot.result.address := pipeline.ctrl(9).up(WriteBack.RESULT).address
    lane0Retire.slot.result.data := pipeline.ctrl(9).up(WriteBack.RESULT).data
    lane0Retire.slot.trap := trapLogic.redirect
    lane0Retire.slot.commit := lane0Retire.valid
    lane0Retire.intWrite.valid := lane0Retire.valid && pipeline.ctrl(9).up(WriteBack.RESULT).valid
    lane0Retire.intWrite.address := pipeline.ctrl(9).up(WriteBack.RESULT).address
    lane0Retire.intWrite.data := pipeline.ctrl(9).up(WriteBack.RESULT).data
    lane0Retire.fpWrite.valid := lane0Retire.valid && fpBackend.fpWrite.valid
    lane0Retire.fpWrite.address := fpBackend.fpWrite.address
    lane0Retire.fpWrite.data := fpBackend.fpWrite.data
    lane0Retire.fpFlags.valid := lane0Retire.valid && fpBackend.fpFlags.valid
    lane0Retire.fpFlags.bits := fpBackend.fpFlags.bits
    lane0Retire.storeCommit := lane0Retire.valid && pipeline.ctrl(9).up(IssueSemantics.PROPS).isStore
    lane0Retire.trap := trapLogic.redirect

    val lane1Retire = RetirePacket(config)
    lane1Retire.valid := lane1WbSlot.commit
    lane1Retire.slot := lane1WbSlot
    lane1Retire.intWrite.valid := lane1Retire.valid && lane1WbSlot.result.valid
    lane1Retire.intWrite.address := lane1WbSlot.result.address
    lane1Retire.intWrite.data := lane1WbSlot.result.data
    lane1Retire.fpWrite.valid := False
    lane1Retire.fpWrite.address := 0
    lane1Retire.fpWrite.data := 0
    lane1Retire.fpFlags.valid := False
    lane1Retire.fpFlags.bits := 0
    lane1Retire.storeCommit := False
    lane1Retire.trap := lane1WbSlot.trap

    val retirePackets = Vec(RetirePacket(config), 2)
    retirePackets(0) := lane0Retire
    retirePackets(1) := lane1Retire

    srcPlugin.regfileread.regfile.io.writes(1).valid := retirePackets(1).intWrite.valid
    srcPlugin.regfileread.regfile.io.writes(1).address := retirePackets(1).intWrite.address
    srcPlugin.regfileread.regfile.io.writes(1).data := retirePackets(1).intWrite.data

    val rvfiPlugin = new RvfiPlugin(pipeline.ctrl(9))
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
        Some(new DebugPlugin(pipeline, trapLogic.redirect, redirectProbe).io.dbg)
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
    } elsewhen(retirePackets(0).valid) {
      lastCommittedSeqValid := B"11"
      lastCommittedSeq1 := lastCommittedSeq0
      lastCommittedSeq0 := retirePackets(0).slot.fetchSeq
    } elsewhen(retirePackets(1).valid) {
      lastCommittedSeqValid := B"11"
      lastCommittedSeq1 := lastCommittedSeq0
      lastCommittedSeq0 := retirePackets(1).slot.fetchSeq
    }
    Array(4, 5, 6, 7, 8, 9).foreach { idx =>
      val ctrl = pipeline.ctrl(idx)
      val decodedValid = ctrl.up(VALID)
      val duplicateLiveSeq = Array.range(idx + 1, 10).map { laterIdx =>
        val laterCtrl = pipeline.ctrl(laterIdx)
        stageLogicallyLive(idx) &&
        stageLogicallyLive(laterIdx) &&
        (ctrl.up(borb.fetch.Fetch.FETCH_SEQ) === laterCtrl.up(borb.fetch.Fetch.FETCH_SEQ))
      }.reduceOption(_ || _).getOrElse(False)
      val staleCommittedSeq =
        lastCommittedSeqValid.orR &&
        ctrl.up.isValid &&
        decodedValid &&
        (
          (lastCommittedSeqValid(0) && (ctrl.up(borb.fetch.Fetch.FETCH_SEQ) === lastCommittedSeq0)) ||
          (lastCommittedSeqValid(1) && (ctrl.up(borb.fetch.Fetch.FETCH_SEQ) === lastCommittedSeq1))
        )
      val duplicateSeqKill = staleCommittedSeq || duplicateLiveSeq
      ctrl.throwWhen(duplicateSeqKill)
      when(duplicateSeqKill) {
        ctrl.up.valid.allowOverride := False
        ctrl.up(Decoder.VALID).allowOverride := False
        if(idx >= 7) {
          ctrl.up(LANE_SEL).allowOverride := False
        }
        if(idx >= 9) {
          ctrl.up(COMMIT).allowOverride := False
          ctrl.up(TRAP).allowOverride := False
        }
      }
    }
    // Wire event signals to performance counters
    val hazardStall = dispatcher.hcs.writes.hazard
    val fetchStall = !fetch.beatValid
    val memStall = lsu.logic.waitingResponse
    val lsuReplayOrWait = lsu.logic.waitingResponse || lsu.logic.amoWaitingResponse || lsu.logic.amoStorePending || lsu.logic.cboZeroActive
    val srcCtrlPerf = pipeline.ctrl(7)
    val committedThisCycle = retirePackets(0).valid || retirePackets(1).valid
    val writeCtrl = pipeline.ctrl(9)
    val dispatchValid = dispatchCtrl.up.isValid && dispatchCtrl(VALID) && dispatchCtrl(LANE_SEL)
    val srcValid = srcCtrlPerf.up.isValid && srcCtrlPerf(VALID) && srcCtrlPerf(LANE_SEL)
    val execValid = pipeline.ctrl(8).up.isValid && pipeline.ctrl(8)(VALID) && pipeline.ctrl(8)(LANE_SEL)
    val writeValid = writeCtrl.up.isValid && writeCtrl(VALID) && writeCtrl(LANE_SEL)
    val dispatchFire = dispatchCtrl.up.isFiring && dispatchCtrl(VALID) && dispatchCtrl(LANE_SEL)
    val srcFire = srcCtrlPerf.up.isFiring && srcCtrlPerf(VALID) && srcCtrlPerf(LANE_SEL)
    val execFire = pipeline.ctrl(8).up.isFiring && pipeline.ctrl(8)(VALID) && pipeline.ctrl(8)(LANE_SEL)
    val writeFire = writeCtrl.up.isFiring && writeCtrl(VALID) && writeCtrl(LANE_SEL)
    val backendOccCount = UInt(3 bits)
    backendOccCount := dispatchValid.asUInt.resize(3) +
      srcValid.asUInt.resize(3) +
      execValid.asUInt.resize(3) +
      writeValid.asUInt.resize(3)
    val writebackStall = writeValid && !committedThisCycle
    val mulDivBusy = execValid && pipeline.ctrl(8)(MicroCode).mux(
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
    val backendActive = Array(3, 4, 5, 6, 7, 8, 9).map { idx =>
      val ctrl = pipeline.ctrl(idx)
      ctrl.up.isValid && ctrl(VALID)
    }.reduce(_ || _)
    val backendStall = backendActive && !committedThisCycle && !hazardStall && !fetchStall && !memStall

    perfCounters.foreach { counters =>
      counters.hazardStall := hazardStall
      counters.fetchStall := fetchStall
      counters.memStall := memStall
      counters.backendStall := backendStall
      counters.writebackStall := writebackStall
      counters.commitStall := commitStall
      counters.mulDivBusyStall := mulDivBusyStall
      counters.lsuReplayOrWaitStall := lsuReplayOrWait
      counters.dispatchToSrcStall := dispatchToSrcStall
      counters.srcToExecStall := srcToExecStall
      counters.execToWriteStall := execToWriteStall
      counters.dispatchValid := dispatchValid
      counters.srcValid := srcValid
      counters.execValid := execValid
      counters.writeValid := writeValid
      counters.dispatchFire := dispatchFire
      counters.srcFire := srcFire
      counters.execFire := execFire
      counters.writeFire := writeFire
      counters.frontendPendingReq := fetch.perfPendingReq
      counters.frontendBeat0Valid := fetch.perfBeat0Valid
      counters.frontendBeat1Valid := fetch.perfBeat1Valid
      counters.frontendReqIssuedEvent := fetch.perfReqIssued
      counters.frontendRspAcceptedEvent := fetch.perfRspAccepted
      counters.frontendNeedCurrentReqEvent := fetch.perfNeedCurrentReq
      counters.frontendNeedNextReqEvent := fetch.perfNeedNextReq
      counters.frontendPrefetchReqEvent := fetch.perfPrefetchReq
      counters.frontendWaitCurBeatEvent := fetch.perfWaitCurBeat
      counters.frontendWaitNextBeatEvent := fetch.perfWaitNextBeat
      counters.frontendTakeInsnEvent := fetch.perfTakeInsn
      counters.frontendCurBeatHitEvent := fetch.perfCurBeatHit
      counters.frontendNextBeatHitEvent := fetch.perfNextBeatHit
      counters.frontendCmdValidCycleEvent := fetch.perfCmdValid
      counters.frontendPrefetchWindowEvent := fetch.perfPrefetchWindow
      counters.frontendPrefetchBlockedNoCmdEvent := fetch.perfPrefetchBlockedNoCmd
      counters.frontendPrefetchBlockedPendingEvent := fetch.perfPrefetchBlockedPending
      counters.frontendPrefetchBlockedNextHitEvent := fetch.perfPrefetchBlockedNextHit
      counters.frontendLoopPredictUsedEvent := fetch.perfLoopPredictUsed
      counters.frontendLoopPredictHitEvent := fetch.perfLoopPredictHit
      counters.frontendFastPredictHitEvent := fetch.perfFastPredictHit
      counters.frontendMainPredictHitEvent := fetch.perfMainPredictHit
      counters.frontendIndirectPredictHitEvent := fetch.perfIndirectPredictHit
      counters.frontendRasUseEvent := fetch.perfRasUse
      counters.frontendRasRepairEvent := fetch.perfRasRepair
      counters.frontendFtqAllocEvent := fetch.perfFtqAlloc
      counters.frontendFtqRestoreEvent := fetch.perfFtqRestore
      counters.frontendPredictedRedirectEvent := fetch.perfPredictedRedirect
      counters.frontendMissCurrentBlockEvent := fetch.perfMissCurrentBlock
      counters.frontendMissNextBlockEvent := fetch.perfMissNextBlock
      counters.frontendMissPrefetchEvent := fetch.perfMissPrefetch
      counters.frontendReqBlockedOutstandingEvent := fetch.perfReqBlockedOutstanding
      counters.frontendPacketQueueFullCycleEvent := fetch.perfPacketQueueFull
      counters.frontendStraddlePacketEvent := fetch.perfStraddlePacket
      counters.frontendSecondBlockUsedEvent := fetch.perfSecondBlockUsed
      counters.frontendSecondBlockLateEvent := fetch.perfSecondBlockLate
      counters.frontendWrongPathBeatEvent := fetch.perfWrongPathBeat
      counters.frontendWrongPathInsnEvent := fetch.perfWrongPathInsn
      counters.l1iBankConflictCycleEvent := fetch.perfL1iBankConflict
      counters.l1iBankBusyCycleEvent := fetch.perfL1iBankBusy
      counters.l1iCrossBankDualFetchSuccessEvent := fetch.perfL1iDualFetch
      counters.backendOcc0 := backendOccCount === U(0, 3 bits)
      counters.backendOcc1 := backendOccCount === U(1, 3 bits)
      counters.backendOcc2 := backendOccCount === U(2, 3 bits)
      counters.backendOcc3 := backendOccCount === U(3, 3 bits)
      counters.backendOcc4 := backendOccCount === U(4, 3 bits)
      counters.backendOverlapDispatchSrcEvent := dispatchValid && srcValid
      counters.backendOverlapSrcExecEvent := srcValid && execValid
      counters.backendOverlapExecWriteEvent := execValid && writeValid
      counters.branchExecuted := branch.logic.resolution.isBranch && branch.logic.up(LANE_SEL)
      counters.branchTaken := branch.logic.doJump
      counters.pipelineFlush := flushPipeline
    }

    val write = pipeline.ctrl(9)
    //val dispCtrl = pipeline.ctrl(6)

    import borb.execute.WriteBack
    val writeback = new WriteBack(
      pipeline.ctrl(9),
      srcPlugin.regfileread.regfile.io.writes(0),
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

    pipeline.ctrls.drop(1).foreach(e => e._2.throwWhen(clockDomain.reset))
    // Build the pipeline
    pipeline.build()
  }
}
