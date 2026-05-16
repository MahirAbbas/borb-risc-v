package borb.core

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.backend.RetirePacket
import borb.dispatch.Dispatch
import borb.execute.{Branch, Lsu}
import borb.fetch.Fetch

/**
  * Performance counters bundle - read-only output for observation.
  */
case class PerfCountersBundle() extends Bundle {
  val cycles        = UInt(64 bits)
  val instret       = UInt(64 bits)
  val stallsHazard  = UInt(64 bits)
  val stallsFetch   = UInt(64 bits)
  val stallsMem     = UInt(64 bits)
  val stallsBackend = UInt(64 bits)
  val stallsWriteback = UInt(64 bits)
  val stallsCommit = UInt(64 bits)
  val stallsMulDivBusy = UInt(64 bits)
  val stallsLsuReplayOrWait = UInt(64 bits)
  val stallsDispatchToSrc = UInt(64 bits)
  val stallsSrcToExec = UInt(64 bits)
  val stallsExecToWrite = UInt(64 bits)
  val cyclesDispatchValid = UInt(64 bits)
  val cyclesSrcValid = UInt(64 bits)
  val cyclesExecValid = UInt(64 bits)
  val cyclesWriteValid = UInt(64 bits)
  val cyclesDispatchFire = UInt(64 bits)
  val cyclesSrcFire = UInt(64 bits)
  val cyclesExecFire = UInt(64 bits)
  val cyclesWriteFire = UInt(64 bits)
  val frontendPendingReqCycles = UInt(64 bits)
  val frontendBeat0ValidCycles = UInt(64 bits)
  val frontendBeat1ValidCycles = UInt(64 bits)
  val frontendReqIssued = UInt(64 bits)
  val frontendRspAccepted = UInt(64 bits)
  val frontendNeedCurrentReq = UInt(64 bits)
  val frontendNeedNextReq = UInt(64 bits)
  val frontendPrefetchReq = UInt(64 bits)
  val frontendWaitCurBeat = UInt(64 bits)
  val frontendWaitNextBeat = UInt(64 bits)
  val frontendTakeInsn = UInt(64 bits)
  val frontendCurBeatHit = UInt(64 bits)
  val frontendNextBeatHit = UInt(64 bits)
  val frontendCmdValidCycles = UInt(64 bits)
  val frontendPrefetchWindow = UInt(64 bits)
  val frontendPrefetchBlockedNoCmd = UInt(64 bits)
  val frontendPrefetchBlockedPending = UInt(64 bits)
  val frontendPrefetchBlockedNextHit = UInt(64 bits)
  val frontendLoopPredictUsed = UInt(64 bits)
  val frontendLoopPredictHit = UInt(64 bits)
  val frontendFastPredictHit = UInt(64 bits)
  val frontendMainPredictHit = UInt(64 bits)
  val frontendIndirectPredictHit = UInt(64 bits)
  val frontendRasUse = UInt(64 bits)
  val frontendRasRepair = UInt(64 bits)
  val frontendFtqAlloc = UInt(64 bits)
  val frontendFtqRestore = UInt(64 bits)
  val frontendPredictedRedirect = UInt(64 bits)
  val frontendMissCurrentBlock = UInt(64 bits)
  val frontendMissNextBlock = UInt(64 bits)
  val frontendMissPrefetch = UInt(64 bits)
  val frontendReqBlockedOutstanding = UInt(64 bits)
  val frontendPacketQueueFullCycles = UInt(64 bits)
  val frontendStraddlePackets = UInt(64 bits)
  val frontendSecondBlockUsed = UInt(64 bits)
  val frontendSecondBlockLate = UInt(64 bits)
  val frontendWrongPathBeats = UInt(64 bits)
  val frontendWrongPathInsns = UInt(64 bits)
  val l1iBankConflictCycles = UInt(64 bits)
  val l1iBankBusyCycles = UInt(64 bits)
  val l1iCrossBankDualFetchSuccess = UInt(64 bits)
  val backendOccupancy0 = UInt(64 bits)
  val backendOccupancy1 = UInt(64 bits)
  val backendOccupancy2 = UInt(64 bits)
  val backendOccupancy3 = UInt(64 bits)
  val backendOccupancy4 = UInt(64 bits)
  val backendOverlapDispatchSrc = UInt(64 bits)
  val backendOverlapSrcExec = UInt(64 bits)
  val backendOverlapExecWrite = UInt(64 bits)
  val branches      = UInt(64 bits)
  val branchesTaken = UInt(64 bits)
  val flushes       = UInt(64 bits)
  val loads         = UInt(64 bits)
  val stores        = UInt(64 bits)
  val jumps         = UInt(64 bits)
  val csrOps        = UInt(64 bits)
  val mulDivOps     = UInt(64 bits)
  val trapCommits   = UInt(64 bits)
}

/**
  * Performance monitoring plugin.
  * 
  * Contains internal 64-bit registers for each counter.
  * Exposes signals for event input and counter output.
  */
case class PerfCountersPlugin(wbStage: CtrlLink) extends Area {
  import borb.common.Common._
import borb.common.LaneKey
  import borb.common.MicroCode._
  
  // Internal counter registers
  val cycles        = Reg(UInt(64 bits)) init 0
  val instret       = Reg(UInt(64 bits)) init 0
  val stallsHazard  = Reg(UInt(64 bits)) init 0
  val stallsFetch   = Reg(UInt(64 bits)) init 0
  val stallsMem     = Reg(UInt(64 bits)) init 0
  val stallsBackend = Reg(UInt(64 bits)) init 0
  val stallsWriteback = Reg(UInt(64 bits)) init 0
  val stallsCommit = Reg(UInt(64 bits)) init 0
  val stallsMulDivBusy = Reg(UInt(64 bits)) init 0
  val stallsLsuReplayOrWait = Reg(UInt(64 bits)) init 0
  val stallsDispatchToSrc = Reg(UInt(64 bits)) init 0
  val stallsSrcToExec = Reg(UInt(64 bits)) init 0
  val stallsExecToWrite = Reg(UInt(64 bits)) init 0
  val cyclesDispatchValid = Reg(UInt(64 bits)) init 0
  val cyclesSrcValid = Reg(UInt(64 bits)) init 0
  val cyclesExecValid = Reg(UInt(64 bits)) init 0
  val cyclesWriteValid = Reg(UInt(64 bits)) init 0
  val cyclesDispatchFire = Reg(UInt(64 bits)) init 0
  val cyclesSrcFire = Reg(UInt(64 bits)) init 0
  val cyclesExecFire = Reg(UInt(64 bits)) init 0
  val cyclesWriteFire = Reg(UInt(64 bits)) init 0
  val frontendPendingReqCycles = Reg(UInt(64 bits)) init 0
  val frontendBeat0ValidCycles = Reg(UInt(64 bits)) init 0
  val frontendBeat1ValidCycles = Reg(UInt(64 bits)) init 0
  val frontendReqIssued = Reg(UInt(64 bits)) init 0
  val frontendRspAccepted = Reg(UInt(64 bits)) init 0
  val frontendNeedCurrentReq = Reg(UInt(64 bits)) init 0
  val frontendNeedNextReq = Reg(UInt(64 bits)) init 0
  val frontendPrefetchReq = Reg(UInt(64 bits)) init 0
  val frontendWaitCurBeat = Reg(UInt(64 bits)) init 0
  val frontendWaitNextBeat = Reg(UInt(64 bits)) init 0
  val frontendTakeInsn = Reg(UInt(64 bits)) init 0
  val frontendCurBeatHit = Reg(UInt(64 bits)) init 0
  val frontendNextBeatHit = Reg(UInt(64 bits)) init 0
  val frontendCmdValidCycles = Reg(UInt(64 bits)) init 0
  val frontendPrefetchWindow = Reg(UInt(64 bits)) init 0
  val frontendPrefetchBlockedNoCmd = Reg(UInt(64 bits)) init 0
  val frontendPrefetchBlockedPending = Reg(UInt(64 bits)) init 0
  val frontendPrefetchBlockedNextHit = Reg(UInt(64 bits)) init 0
  val frontendLoopPredictUsed = Reg(UInt(64 bits)) init 0
  val frontendLoopPredictHit = Reg(UInt(64 bits)) init 0
  val frontendFastPredictHit = Reg(UInt(64 bits)) init 0
  val frontendMainPredictHit = Reg(UInt(64 bits)) init 0
  val frontendIndirectPredictHit = Reg(UInt(64 bits)) init 0
  val frontendRasUse = Reg(UInt(64 bits)) init 0
  val frontendRasRepair = Reg(UInt(64 bits)) init 0
  val frontendFtqAlloc = Reg(UInt(64 bits)) init 0
  val frontendFtqRestore = Reg(UInt(64 bits)) init 0
  val frontendPredictedRedirect = Reg(UInt(64 bits)) init 0
  val frontendMissCurrentBlock = Reg(UInt(64 bits)) init 0
  val frontendMissNextBlock = Reg(UInt(64 bits)) init 0
  val frontendMissPrefetch = Reg(UInt(64 bits)) init 0
  val frontendReqBlockedOutstanding = Reg(UInt(64 bits)) init 0
  val frontendPacketQueueFullCycles = Reg(UInt(64 bits)) init 0
  val frontendStraddlePackets = Reg(UInt(64 bits)) init 0
  val frontendSecondBlockUsed = Reg(UInt(64 bits)) init 0
  val frontendSecondBlockLate = Reg(UInt(64 bits)) init 0
  val frontendWrongPathBeats = Reg(UInt(64 bits)) init 0
  val frontendWrongPathInsns = Reg(UInt(64 bits)) init 0
  val l1iBankConflictCycles = Reg(UInt(64 bits)) init 0
  val l1iBankBusyCycles = Reg(UInt(64 bits)) init 0
  val l1iCrossBankDualFetchSuccess = Reg(UInt(64 bits)) init 0
  val backendOccupancy0 = Reg(UInt(64 bits)) init 0
  val backendOccupancy1 = Reg(UInt(64 bits)) init 0
  val backendOccupancy2 = Reg(UInt(64 bits)) init 0
  val backendOccupancy3 = Reg(UInt(64 bits)) init 0
  val backendOccupancy4 = Reg(UInt(64 bits)) init 0
  val backendOverlapDispatchSrc = Reg(UInt(64 bits)) init 0
  val backendOverlapSrcExec = Reg(UInt(64 bits)) init 0
  val backendOverlapExecWrite = Reg(UInt(64 bits)) init 0
  val branches      = Reg(UInt(64 bits)) init 0
  val branchesTaken = Reg(UInt(64 bits)) init 0
  val flushes       = Reg(UInt(64 bits)) init 0
  val loads         = Reg(UInt(64 bits)) init 0
  val stores        = Reg(UInt(64 bits)) init 0
  val jumps         = Reg(UInt(64 bits)) init 0
  val csrOps        = Reg(UInt(64 bits)) init 0
  val mulDivOps     = Reg(UInt(64 bits)) init 0
  val trapCommits   = Reg(UInt(64 bits)) init 0
  
  // cycles: always increments
  cycles := cycles + 1
  
  // instret: increments on commit
  val wbArea = new wbStage.Area {
    def count(events: Seq[Bool]): UInt = events.map(_.asUInt.resize(2)).reduce(_ + _)
    def committed(laneId: Int): Bool = up(COMMIT, LaneKey(laneId))
    def microCode(laneId: Int) = up(borb.frontend.Decoder.MicroCode, LaneKey(laneId))
    def isLoad(laneId: Int): Bool = microCode(laneId).mux(
      uopLB -> True, uopLH -> True, uopLW -> True, uopLBU -> True, uopLHU -> True, uopLWU -> True, uopLD -> True, uopFLW -> True,
      default -> False
    )
    def isStore(laneId: Int): Bool = microCode(laneId).mux(
      uopSB -> True, uopSH -> True, uopSW -> True, uopSD -> True, uopFSW -> True,
      default -> False
    )
    def isJump(laneId: Int): Bool = microCode(laneId).mux(
      uopJAL -> True, uopJALR -> True,
      default -> False
    )
    def isCsr(laneId: Int): Bool = microCode(laneId).mux(
      uopCSRRW -> True, uopCSRRS -> True, uopCSRRC -> True, uopCSRRWI -> True, uopCSRRSI -> True, uopCSRRCI -> True,
      default -> False
    )
    def isMulDiv(laneId: Int): Bool = microCode(laneId).mux(
      uopMUL -> True, uopMULH -> True, uopMULHSU -> True, uopMULHU -> True, uopDIV -> True, uopDIVU -> True, uopREM -> True, uopREMU -> True,
      uopMULW -> True, uopDIVW -> True, uopDIVUW -> True, uopREMW -> True, uopREMUW -> True,
      default -> False
    )

    val commitCount = count((0 until borb.frontend.Decoder.LANES).map(committed))
    when(commitCount =/= 0) {
      instret := instret + commitCount.resized
      loads := loads + count((0 until borb.frontend.Decoder.LANES).map(laneId => committed(laneId) && isLoad(laneId))).resized
      stores := stores + count((0 until borb.frontend.Decoder.LANES).map(laneId => committed(laneId) && isStore(laneId))).resized
      jumps := jumps + count((0 until borb.frontend.Decoder.LANES).map(laneId => committed(laneId) && isJump(laneId))).resized
      csrOps := csrOps + count((0 until borb.frontend.Decoder.LANES).map(laneId => committed(laneId) && isCsr(laneId))).resized
      mulDivOps := mulDivOps + count((0 until borb.frontend.Decoder.LANES).map(laneId => committed(laneId) && isMulDiv(laneId))).resized
      trapCommits := trapCommits + count((0 until borb.frontend.Decoder.LANES).map(laneId => committed(laneId) && up(TRAP, LaneKey(laneId)))).resized
    }
  }
  
  // Event input signals (to be assigned from CPU)
  val hazardStall    = Bool()
  val fetchStall     = Bool()
  val memStall       = Bool()
  val backendStall   = Bool()
  val writebackStall = Bool()
  val commitStall = Bool()
  val mulDivBusyStall = Bool()
  val lsuReplayOrWaitStall = Bool()
  val dispatchToSrcStall = Bool()
  val srcToExecStall = Bool()
  val execToWriteStall = Bool()
  val dispatchValid = Bool()
  val srcValid = Bool()
  val execValid = Bool()
  val writeValid = Bool()
  val dispatchFire = Bool()
  val srcFire = Bool()
  val execFire = Bool()
  val writeFire = Bool()
  val frontendPendingReq = Bool()
  val frontendBeat0Valid = Bool()
  val frontendBeat1Valid = Bool()
  val frontendReqIssuedEvent = Bool()
  val frontendRspAcceptedEvent = Bool()
  val frontendNeedCurrentReqEvent = Bool()
  val frontendNeedNextReqEvent = Bool()
  val frontendPrefetchReqEvent = Bool()
  val frontendWaitCurBeatEvent = Bool()
  val frontendWaitNextBeatEvent = Bool()
  val frontendTakeInsnEvent = Bool()
  val frontendCurBeatHitEvent = Bool()
  val frontendNextBeatHitEvent = Bool()
  val frontendCmdValidCycleEvent = Bool()
  val frontendPrefetchWindowEvent = Bool()
  val frontendPrefetchBlockedNoCmdEvent = Bool()
  val frontendPrefetchBlockedPendingEvent = Bool()
  val frontendPrefetchBlockedNextHitEvent = Bool()
  val frontendLoopPredictUsedEvent = Bool()
  val frontendLoopPredictHitEvent = Bool()
  val frontendFastPredictHitEvent = Bool()
  val frontendMainPredictHitEvent = Bool()
  val frontendIndirectPredictHitEvent = Bool()
  val frontendRasUseEvent = Bool()
  val frontendRasRepairEvent = Bool()
  val frontendFtqAllocEvent = Bool()
  val frontendFtqRestoreEvent = Bool()
  val frontendPredictedRedirectEvent = Bool()
  val frontendMissCurrentBlockEvent = Bool()
  val frontendMissNextBlockEvent = Bool()
  val frontendMissPrefetchEvent = Bool()
  val frontendReqBlockedOutstandingEvent = Bool()
  val frontendPacketQueueFullCycleEvent = Bool()
  val frontendStraddlePacketEvent = Bool()
  val frontendSecondBlockUsedEvent = Bool()
  val frontendSecondBlockLateEvent = Bool()
  val frontendWrongPathBeatEvent = Bool()
  val frontendWrongPathInsnEvent = Bool()
  val l1iBankConflictCycleEvent = Bool()
  val l1iBankBusyCycleEvent = Bool()
  val l1iCrossBankDualFetchSuccessEvent = Bool()
  val backendOcc0 = Bool()
  val backendOcc1 = Bool()
  val backendOcc2 = Bool()
  val backendOcc3 = Bool()
  val backendOcc4 = Bool()
  val backendOverlapDispatchSrcEvent = Bool()
  val backendOverlapSrcExecEvent = Bool()
  val backendOverlapExecWriteEvent = Bool()
  val branchExecuted = Bool()
  val branchTaken    = Bool()
  val pipelineFlush  = Bool()

  def wireFromCore(
      pipeline: StageCtrlPipeline,
      decodeStageId: Int,
      wbStageId: Int,
      dispatchCtrl: CtrlLink,
      srcCtrl: CtrlLink,
      execStage: CtrlLink,
      dispatcher: Dispatch,
      fetch: Fetch,
      lsu: Lsu,
      branch: Branch,
      retirePackets: Vec[RetirePacket],
      controlHazardBusy: Bool,
      flushPipeline: Bool
  ): Unit = {
    import borb.frontend.Decoder

    val hazardStall = dispatcher.hcs.writes.hazard
    val fetchStall = !fetch.beatValid
    val memStall = lsu.logic.waitingResponse
    val lsuReplayOrWait = lsu.logic.waitingResponse ||
      lsu.logic.amoWaitingResponse ||
      lsu.logic.amoStorePending ||
      lsu.logic.cboZeroActive
    val committedThisCycle = retirePackets(0).valid || retirePackets(1).valid
    val writeCtrl = wbStage
    def laneActive(ctrl: CtrlLink, laneId: Int, hasLaneSel: Boolean = true): Bool = {
      val laneKey = LaneKey(laneId)
      ctrl(Decoder.VALID, laneKey) && (if(hasLaneSel) ctrl(LANE_SEL, laneKey) else True)
    }
    def anyLaneActive(ctrl: CtrlLink, hasLaneSel: Boolean = true): Bool =
      (0 until Decoder.LANES).map(laneId => laneActive(ctrl, laneId, hasLaneSel)).reduce(_ || _)
    val dispatchValid = dispatchCtrl.up.isValid && anyLaneActive(dispatchCtrl, hasLaneSel = false)
    val srcValid = srcCtrl.up.isValid && anyLaneActive(srcCtrl)
    val execValid = execStage.up.isValid && anyLaneActive(execStage)
    val writeValid = writeCtrl.up.isValid && anyLaneActive(writeCtrl)
    val dispatchFire = dispatchCtrl.up.isFiring && anyLaneActive(dispatchCtrl, hasLaneSel = false)
    val srcFire = srcCtrl.up.isFiring && anyLaneActive(srcCtrl)
    val execFire = execStage.up.isFiring && anyLaneActive(execStage)
    val writeFire = writeCtrl.up.isFiring && anyLaneActive(writeCtrl)
    val backendOccCount = UInt(3 bits)
    backendOccCount := dispatchValid.asUInt.resize(3) +
      srcValid.asUInt.resize(3) +
      execValid.asUInt.resize(3) +
      writeValid.asUInt.resize(3)
    val writebackStall = writeValid && !committedThisCycle
    def laneMulDiv(laneId: Int): Bool = {
      val laneKey = LaneKey(laneId)
      laneActive(execStage, laneId) && execStage(Decoder.MicroCode, laneKey).mux(
        uopMUL -> True, uopMULH -> True, uopMULHSU -> True, uopMULHU -> True,
        uopDIV -> True, uopDIVU -> True, uopREM -> True, uopREMU -> True,
        uopMULW -> True, uopDIVW -> True, uopDIVUW -> True, uopREMW -> True, uopREMUW -> True,
        default -> False
      )
    }
    val mulDivBusy = execValid && (0 until Decoder.LANES).map(laneMulDiv).reduce(_ || _)
    val mulDivBusyStall = mulDivBusy && !committedThisCycle
    val commitStall = execValid && !writeValid && !hazardStall && !fetchStall && !lsuReplayOrWait
    val dispatchToSrcStall = dispatchValid && !srcValid && !hazardStall && !fetchStall
    val srcToExecStall = srcValid && !execValid && !hazardStall && !fetchStall && !controlHazardBusy
    val execToWriteStall = execValid && !writeValid && !lsuReplayOrWait
    val backendActive = Array.range(decodeStageId, wbStageId + 1).map { idx =>
      val ctrl = pipeline.ctrl(idx)
      ctrl.up.isValid && anyLaneActive(ctrl, hasLaneSel = idx >= 5)
    }.reduce(_ || _)
    val backendStall = backendActive && !committedThisCycle && !hazardStall && !fetchStall && !memStall

    this.hazardStall := hazardStall
    this.fetchStall := fetchStall
    this.memStall := memStall
    this.backendStall := backendStall
    this.writebackStall := writebackStall
    this.commitStall := commitStall
    this.mulDivBusyStall := mulDivBusyStall
    this.lsuReplayOrWaitStall := lsuReplayOrWait
    this.dispatchToSrcStall := dispatchToSrcStall
    this.srcToExecStall := srcToExecStall
    this.execToWriteStall := execToWriteStall
    this.dispatchValid := dispatchValid
    this.srcValid := srcValid
    this.execValid := execValid
    this.writeValid := writeValid
    this.dispatchFire := dispatchFire
    this.srcFire := srcFire
    this.execFire := execFire
    this.writeFire := writeFire
    this.frontendPendingReq := fetch.perf.pendingReq
    this.frontendBeat0Valid := fetch.perf.beat0Valid
    this.frontendBeat1Valid := fetch.perf.beat1Valid
    this.frontendReqIssuedEvent := fetch.perf.reqIssued
    this.frontendRspAcceptedEvent := fetch.perf.rspAccepted
    this.frontendNeedCurrentReqEvent := fetch.perf.needCurrentReq
    this.frontendNeedNextReqEvent := fetch.perf.needNextReq
    this.frontendPrefetchReqEvent := fetch.perf.prefetchReq
    this.frontendWaitCurBeatEvent := fetch.perf.waitCurBeat
    this.frontendWaitNextBeatEvent := fetch.perf.waitNextBeat
    this.frontendTakeInsnEvent := fetch.perf.takeInsn
    this.frontendCurBeatHitEvent := fetch.perf.curBeatHit
    this.frontendNextBeatHitEvent := fetch.perf.nextBeatHit
    this.frontendCmdValidCycleEvent := fetch.perf.cmdValid
    this.frontendPrefetchWindowEvent := fetch.perf.prefetchWindow
    this.frontendPrefetchBlockedNoCmdEvent := fetch.perf.prefetchBlockedNoCmd
    this.frontendPrefetchBlockedPendingEvent := fetch.perf.prefetchBlockedPending
    this.frontendPrefetchBlockedNextHitEvent := fetch.perf.prefetchBlockedNextHit
    this.frontendLoopPredictUsedEvent := fetch.perf.loopPredictUsed
    this.frontendLoopPredictHitEvent := fetch.perf.loopPredictHit
    this.frontendFastPredictHitEvent := fetch.perf.fastPredictHit
    this.frontendMainPredictHitEvent := fetch.perf.mainPredictHit
    this.frontendIndirectPredictHitEvent := fetch.perf.indirectPredictHit
    this.frontendRasUseEvent := fetch.perf.rasUse
    this.frontendRasRepairEvent := fetch.perf.rasRepair
    this.frontendFtqAllocEvent := fetch.perf.ftqAlloc
    this.frontendFtqRestoreEvent := fetch.perf.ftqRestore
    this.frontendPredictedRedirectEvent := fetch.perf.predictedRedirect
    this.frontendMissCurrentBlockEvent := fetch.perf.missCurrentBlock
    this.frontendMissNextBlockEvent := fetch.perf.missNextBlock
    this.frontendMissPrefetchEvent := fetch.perf.missPrefetch
    this.frontendReqBlockedOutstandingEvent := fetch.perf.reqBlockedOutstanding
    this.frontendPacketQueueFullCycleEvent := fetch.perf.packetQueueFull
    this.frontendStraddlePacketEvent := fetch.perf.straddlePacket
    this.frontendSecondBlockUsedEvent := fetch.perf.secondBlockUsed
    this.frontendSecondBlockLateEvent := fetch.perf.secondBlockLate
    this.frontendWrongPathBeatEvent := fetch.perf.wrongPathBeat
    this.frontendWrongPathInsnEvent := fetch.perf.wrongPathInsn
    this.l1iBankConflictCycleEvent := fetch.perf.l1iBankConflict
    this.l1iBankBusyCycleEvent := fetch.perf.l1iBankBusy
    this.l1iCrossBankDualFetchSuccessEvent := fetch.perf.l1iDualFetch
    this.backendOcc0 := backendOccCount === U(0, 3 bits)
    this.backendOcc1 := backendOccCount === U(1, 3 bits)
    this.backendOcc2 := backendOccCount === U(2, 3 bits)
    this.backendOcc3 := backendOccCount === U(3, 3 bits)
    this.backendOcc4 := backendOccCount === U(4, 3 bits)
    this.backendOverlapDispatchSrcEvent := dispatchValid && srcValid
    this.backendOverlapSrcExecEvent := srcValid && execValid
    this.backendOverlapExecWriteEvent := execValid && writeValid
    this.branchExecuted := branch.actualIsBranch && branch.branchResolved
    this.branchTaken := branch.actualTaken
    this.pipelineFlush := flushPipeline
  }
  
  // Increment on events
  when(hazardStall)    { stallsHazard  := stallsHazard + 1 }
  when(fetchStall)     { stallsFetch   := stallsFetch + 1 }
  when(memStall)       { stallsMem     := stallsMem + 1 }
  when(backendStall)   { stallsBackend := stallsBackend + 1 }
  when(writebackStall) { stallsWriteback := stallsWriteback + 1 }
  when(commitStall) { stallsCommit := stallsCommit + 1 }
  when(mulDivBusyStall) { stallsMulDivBusy := stallsMulDivBusy + 1 }
  when(lsuReplayOrWaitStall) { stallsLsuReplayOrWait := stallsLsuReplayOrWait + 1 }
  when(dispatchToSrcStall) { stallsDispatchToSrc := stallsDispatchToSrc + 1 }
  when(srcToExecStall) { stallsSrcToExec := stallsSrcToExec + 1 }
  when(execToWriteStall) { stallsExecToWrite := stallsExecToWrite + 1 }
  when(dispatchValid) { cyclesDispatchValid := cyclesDispatchValid + 1 }
  when(srcValid) { cyclesSrcValid := cyclesSrcValid + 1 }
  when(execValid) { cyclesExecValid := cyclesExecValid + 1 }
  when(writeValid) { cyclesWriteValid := cyclesWriteValid + 1 }
  when(dispatchFire) { cyclesDispatchFire := cyclesDispatchFire + 1 }
  when(srcFire) { cyclesSrcFire := cyclesSrcFire + 1 }
  when(execFire) { cyclesExecFire := cyclesExecFire + 1 }
  when(writeFire) { cyclesWriteFire := cyclesWriteFire + 1 }
  when(frontendPendingReq) { frontendPendingReqCycles := frontendPendingReqCycles + 1 }
  when(frontendBeat0Valid) { frontendBeat0ValidCycles := frontendBeat0ValidCycles + 1 }
  when(frontendBeat1Valid) { frontendBeat1ValidCycles := frontendBeat1ValidCycles + 1 }
  when(frontendReqIssuedEvent) { frontendReqIssued := frontendReqIssued + 1 }
  when(frontendRspAcceptedEvent) { frontendRspAccepted := frontendRspAccepted + 1 }
  when(frontendNeedCurrentReqEvent) { frontendNeedCurrentReq := frontendNeedCurrentReq + 1 }
  when(frontendNeedNextReqEvent) { frontendNeedNextReq := frontendNeedNextReq + 1 }
  when(frontendPrefetchReqEvent) { frontendPrefetchReq := frontendPrefetchReq + 1 }
  when(frontendWaitCurBeatEvent) { frontendWaitCurBeat := frontendWaitCurBeat + 1 }
  when(frontendWaitNextBeatEvent) { frontendWaitNextBeat := frontendWaitNextBeat + 1 }
  when(frontendTakeInsnEvent) { frontendTakeInsn := frontendTakeInsn + 1 }
  when(frontendCurBeatHitEvent) { frontendCurBeatHit := frontendCurBeatHit + 1 }
  when(frontendNextBeatHitEvent) { frontendNextBeatHit := frontendNextBeatHit + 1 }
  when(frontendCmdValidCycleEvent) { frontendCmdValidCycles := frontendCmdValidCycles + 1 }
  when(frontendPrefetchWindowEvent) { frontendPrefetchWindow := frontendPrefetchWindow + 1 }
  when(frontendPrefetchBlockedNoCmdEvent) { frontendPrefetchBlockedNoCmd := frontendPrefetchBlockedNoCmd + 1 }
  when(frontendPrefetchBlockedPendingEvent) { frontendPrefetchBlockedPending := frontendPrefetchBlockedPending + 1 }
  when(frontendPrefetchBlockedNextHitEvent) { frontendPrefetchBlockedNextHit := frontendPrefetchBlockedNextHit + 1 }
  when(frontendLoopPredictUsedEvent) { frontendLoopPredictUsed := frontendLoopPredictUsed + 1 }
  when(frontendLoopPredictHitEvent) { frontendLoopPredictHit := frontendLoopPredictHit + 1 }
  when(frontendFastPredictHitEvent) { frontendFastPredictHit := frontendFastPredictHit + 1 }
  when(frontendMainPredictHitEvent) { frontendMainPredictHit := frontendMainPredictHit + 1 }
  when(frontendIndirectPredictHitEvent) { frontendIndirectPredictHit := frontendIndirectPredictHit + 1 }
  when(frontendRasUseEvent) { frontendRasUse := frontendRasUse + 1 }
  when(frontendRasRepairEvent) { frontendRasRepair := frontendRasRepair + 1 }
  when(frontendFtqAllocEvent) { frontendFtqAlloc := frontendFtqAlloc + 1 }
  when(frontendFtqRestoreEvent) { frontendFtqRestore := frontendFtqRestore + 1 }
  when(frontendPredictedRedirectEvent) { frontendPredictedRedirect := frontendPredictedRedirect + 1 }
  when(frontendMissCurrentBlockEvent) { frontendMissCurrentBlock := frontendMissCurrentBlock + 1 }
  when(frontendMissNextBlockEvent) { frontendMissNextBlock := frontendMissNextBlock + 1 }
  when(frontendMissPrefetchEvent) { frontendMissPrefetch := frontendMissPrefetch + 1 }
  when(frontendReqBlockedOutstandingEvent) { frontendReqBlockedOutstanding := frontendReqBlockedOutstanding + 1 }
  when(frontendPacketQueueFullCycleEvent) { frontendPacketQueueFullCycles := frontendPacketQueueFullCycles + 1 }
  when(frontendStraddlePacketEvent) { frontendStraddlePackets := frontendStraddlePackets + 1 }
  when(frontendSecondBlockUsedEvent) { frontendSecondBlockUsed := frontendSecondBlockUsed + 1 }
  when(frontendSecondBlockLateEvent) { frontendSecondBlockLate := frontendSecondBlockLate + 1 }
  when(frontendWrongPathBeatEvent) { frontendWrongPathBeats := frontendWrongPathBeats + 1 }
  when(frontendWrongPathInsnEvent) { frontendWrongPathInsns := frontendWrongPathInsns + 1 }
  when(l1iBankConflictCycleEvent) { l1iBankConflictCycles := l1iBankConflictCycles + 1 }
  when(l1iBankBusyCycleEvent) { l1iBankBusyCycles := l1iBankBusyCycles + 1 }
  when(l1iCrossBankDualFetchSuccessEvent) { l1iCrossBankDualFetchSuccess := l1iCrossBankDualFetchSuccess + 1 }
  when(backendOcc0) { backendOccupancy0 := backendOccupancy0 + 1 }
  when(backendOcc1) { backendOccupancy1 := backendOccupancy1 + 1 }
  when(backendOcc2) { backendOccupancy2 := backendOccupancy2 + 1 }
  when(backendOcc3) { backendOccupancy3 := backendOccupancy3 + 1 }
  when(backendOcc4) { backendOccupancy4 := backendOccupancy4 + 1 }
  when(backendOverlapDispatchSrcEvent) { backendOverlapDispatchSrc := backendOverlapDispatchSrc + 1 }
  when(backendOverlapSrcExecEvent) { backendOverlapSrcExec := backendOverlapSrcExec + 1 }
  when(backendOverlapExecWriteEvent) { backendOverlapExecWrite := backendOverlapExecWrite + 1 }
  when(branchExecuted) { branches      := branches + 1 }
  when(branchTaken)    { branchesTaken := branchesTaken + 1 }
  when(pipelineFlush)  { flushes       := flushes + 1 }
  
  // Output bundle
  val counters = PerfCountersBundle()
  counters.cycles        := cycles
  counters.instret       := instret
  counters.stallsHazard  := stallsHazard
  counters.stallsFetch   := stallsFetch
  counters.stallsMem     := stallsMem
  counters.stallsBackend := stallsBackend
  counters.stallsWriteback := stallsWriteback
  counters.stallsCommit := stallsCommit
  counters.stallsMulDivBusy := stallsMulDivBusy
  counters.stallsLsuReplayOrWait := stallsLsuReplayOrWait
  counters.stallsDispatchToSrc := stallsDispatchToSrc
  counters.stallsSrcToExec := stallsSrcToExec
  counters.stallsExecToWrite := stallsExecToWrite
  counters.cyclesDispatchValid := cyclesDispatchValid
  counters.cyclesSrcValid := cyclesSrcValid
  counters.cyclesExecValid := cyclesExecValid
  counters.cyclesWriteValid := cyclesWriteValid
  counters.cyclesDispatchFire := cyclesDispatchFire
  counters.cyclesSrcFire := cyclesSrcFire
  counters.cyclesExecFire := cyclesExecFire
  counters.cyclesWriteFire := cyclesWriteFire
  counters.frontendPendingReqCycles := frontendPendingReqCycles
  counters.frontendBeat0ValidCycles := frontendBeat0ValidCycles
  counters.frontendBeat1ValidCycles := frontendBeat1ValidCycles
  counters.frontendReqIssued := frontendReqIssued
  counters.frontendRspAccepted := frontendRspAccepted
  counters.frontendNeedCurrentReq := frontendNeedCurrentReq
  counters.frontendNeedNextReq := frontendNeedNextReq
  counters.frontendPrefetchReq := frontendPrefetchReq
  counters.frontendWaitCurBeat := frontendWaitCurBeat
  counters.frontendWaitNextBeat := frontendWaitNextBeat
  counters.frontendTakeInsn := frontendTakeInsn
  counters.frontendCurBeatHit := frontendCurBeatHit
  counters.frontendNextBeatHit := frontendNextBeatHit
  counters.frontendCmdValidCycles := frontendCmdValidCycles
  counters.frontendPrefetchWindow := frontendPrefetchWindow
  counters.frontendPrefetchBlockedNoCmd := frontendPrefetchBlockedNoCmd
  counters.frontendPrefetchBlockedPending := frontendPrefetchBlockedPending
  counters.frontendPrefetchBlockedNextHit := frontendPrefetchBlockedNextHit
  counters.frontendLoopPredictUsed := frontendLoopPredictUsed
  counters.frontendLoopPredictHit := frontendLoopPredictHit
  counters.frontendFastPredictHit := frontendFastPredictHit
  counters.frontendMainPredictHit := frontendMainPredictHit
  counters.frontendIndirectPredictHit := frontendIndirectPredictHit
  counters.frontendRasUse := frontendRasUse
  counters.frontendRasRepair := frontendRasRepair
  counters.frontendFtqAlloc := frontendFtqAlloc
  counters.frontendFtqRestore := frontendFtqRestore
  counters.frontendPredictedRedirect := frontendPredictedRedirect
  counters.frontendMissCurrentBlock := frontendMissCurrentBlock
  counters.frontendMissNextBlock := frontendMissNextBlock
  counters.frontendMissPrefetch := frontendMissPrefetch
  counters.frontendReqBlockedOutstanding := frontendReqBlockedOutstanding
  counters.frontendPacketQueueFullCycles := frontendPacketQueueFullCycles
  counters.frontendStraddlePackets := frontendStraddlePackets
  counters.frontendSecondBlockUsed := frontendSecondBlockUsed
  counters.frontendSecondBlockLate := frontendSecondBlockLate
  counters.frontendWrongPathBeats := frontendWrongPathBeats
  counters.frontendWrongPathInsns := frontendWrongPathInsns
  counters.l1iBankConflictCycles := l1iBankConflictCycles
  counters.l1iBankBusyCycles := l1iBankBusyCycles
  counters.l1iCrossBankDualFetchSuccess := l1iCrossBankDualFetchSuccess
  counters.backendOccupancy0 := backendOccupancy0
  counters.backendOccupancy1 := backendOccupancy1
  counters.backendOccupancy2 := backendOccupancy2
  counters.backendOccupancy3 := backendOccupancy3
  counters.backendOccupancy4 := backendOccupancy4
  counters.backendOverlapDispatchSrc := backendOverlapDispatchSrc
  counters.backendOverlapSrcExec := backendOverlapSrcExec
  counters.backendOverlapExecWrite := backendOverlapExecWrite
  counters.branches      := branches
  counters.branchesTaken := branchesTaken
  counters.flushes       := flushes
  counters.loads         := loads
  counters.stores        := stores
  counters.jumps         := jumps
  counters.csrOps        := csrOps
  counters.mulDivOps     := mulDivOps
  counters.trapCommits   := trapCommits
}
