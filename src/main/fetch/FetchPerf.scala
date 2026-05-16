package borb.fetch

import spinal.core._
import spinal.lib._

case class FetchPerfEvents(config: FrontendConfig) extends Bundle {
  val pendingReq = Bool()
  val beat0Valid = Bool()
  val beat1Valid = Bool()
  val reqIssued = Bool()
  val rspAccepted = Bool()
  val needCurrentReq = Bool()
  val needNextReq = Bool()
  val prefetchReq = Bool()
  val waitCurBeat = Bool()
  val waitNextBeat = Bool()
  val takeInsn = Bool()
  val curBeatHit = Bool()
  val nextBeatHit = Bool()
  val cmdValid = Bool()
  val prefetchWindow = Bool()
  val prefetchBlockedNoCmd = Bool()
  val prefetchBlockedPending = Bool()
  val prefetchBlockedNextHit = Bool()
  val loopPredictUsed = Bool()
  val loopPredictHit = Bool()
  val fastPredictHit = Bool()
  val mainPredictHit = Bool()
  val indirectPredictHit = Bool()
  val rasUse = Bool()
  val rasRepair = Bool()
  val ftqAlloc = Bool()
  val ftqRestore = Bool()
  val predictedRedirect = Bool()
  val missCurrentBlock = Bool()
  val missNextBlock = Bool()
  val missPrefetch = Bool()
  val reqBlockedOutstanding = Bool()
  val packetQueueFull = Bool()
  val straddlePacket = Bool()
  val secondBlockUsed = Bool()
  val secondBlockLate = Bool()
  val wrongPathBeat = Bool()
  val wrongPathInsn = Bool()
  val l1iBankConflict = Bool()
  val l1iBankBusy = Bool()
  val l1iDualFetch = Bool()
}

object FetchPerf {
  def apply(
    config: FrontendConfig,
    control: FrontendControl,
    traversal: FrontendTraversal,
    l1i: FrontendL1I,
    builder: FrontendBundleBuilder,
    queue: FrontendQueue,
    outputBundleValid: Bool,
    bundleAccepted: Bool,
    flushFrontend: Bool,
    branchResolve: Flow[BranchResolveUpdate],
    recover: Flow[FrontendRecoverUpdate],
    predictedJumpValid: Bool
  ): FetchPerfEvents = {
    val events = FetchPerfEvents(config)

    events.pendingReq := l1i.io.hasPendingMiss
    events.beat0Valid := outputBundleValid
    events.beat1Valid := queue.io.pop.valid && (queue.io.pop.payload.slotCount > U(1, queue.io.pop.payload.slotCount.getWidth bits))
    events.reqIssued := l1i.io.missReq.fire
    events.rspAccepted := l1i.io.missRsp.valid
    val controlActive = control.io.active.valid
    events.needCurrentReq := controlActive && !l1i.io.lookupRsp(0).hit
    events.needNextReq := (if(config.lookupLanes > 1) controlActive && traversal.io.metadata.blocks(1).valid && !l1i.io.lookupRsp(1).hit else False)
    events.prefetchReq := l1i.io.prefetchReqIssued
    events.waitCurBeat := controlActive && !builder.io.bundle.valid
    events.waitNextBeat := controlActive && builder.io.bundle.valid && (builder.io.bundle.payload.slotCount === U(1, builder.io.bundle.payload.slotCount.getWidth bits))
    events.takeInsn := bundleAccepted
    events.curBeatHit := l1i.io.lookupRsp(0).hit
    events.nextBeatHit := (if(config.lookupLanes > 1) l1i.io.lookupRsp(1).hit else False)
    events.cmdValid := controlActive
    events.prefetchWindow := False
    events.prefetchBlockedNoCmd := False
    events.prefetchBlockedPending := False
    events.prefetchBlockedNextHit := False
    events.loopPredictUsed := builder.io.bundle.valid && builder.io.bundle.payload.bundleMeta.traversedBlocks.map(_.loopPredicted).foldLeft(False)(_ || _)
    events.loopPredictHit := events.loopPredictUsed && builder.io.bundle.payload.bundleMeta.predictedRedirectValid
    events.fastPredictHit := builder.io.bundle.valid && builder.io.bundle.payload.bundleMeta.traversedBlocks.map(_.rasUsed).foldLeft(False)(_ || _)
    events.mainPredictHit := builder.io.bundle.valid && builder.io.bundle.payload.bundleMeta.traversedBlocks.map(_.predictedTaken).foldLeft(False)(_ || _)
    events.indirectPredictHit := builder.io.bundle.valid && builder.io.bundle.payload.bundleMeta.traversedBlocks.map(_.indirectProvided).foldLeft(False)(_ || _)
    events.rasUse := builder.io.bundle.valid && builder.io.bundle.payload.bundleMeta.traversedBlocks.map(_.rasUsed).foldLeft(False)(_ || _)
    events.rasRepair := branchResolve.valid && (branchResolve.payload.isCall || branchResolve.payload.isReturn) && branchResolve.payload.mispredict
    events.ftqAlloc := bundleAccepted
    events.ftqRestore := recover.valid && recover.payload.recovery.valid
    events.predictedRedirect := predictedJumpValid
    events.missCurrentBlock := l1i.io.lookupReq(0).valid && !l1i.io.lookupRsp(0).hit
    events.missNextBlock := (if(config.lookupLanes > 1) traversal.io.lookupReqs(1).valid && !l1i.io.lookupRsp(1).hit else False)
    events.missPrefetch := l1i.io.prefetchReqIssued
    events.reqBlockedOutstanding := l1i.io.reqBlockedOutstanding
    events.packetQueueFull := builder.io.bundle.valid && !queue.io.push.ready
    events.straddlePacket := False
    events.secondBlockUsed := (if(config.bundleSlots > 1) {
      builder.io.bundle.valid && builder.io.bundle.payload.slots(1).valid && (builder.io.bundle.payload.slots(1).blockPc =/= builder.io.bundle.payload.slots(0).blockPc)
    } else {
      False
    })
    events.secondBlockLate := controlActive && !builder.io.bundle.valid && l1i.io.lookupRsp.map(_.missPending).foldLeft(False)(_ || _)
    events.wrongPathBeat := l1i.io.staleRspDropped
    events.wrongPathInsn := flushFrontend && (queue.io.occupancy =/= 0)
    events.l1iBankConflict := l1i.io.bankConflictCycle
    events.l1iBankBusy := l1i.io.bankBusyCycle
    events.l1iDualFetch := l1i.io.dualLookupSuccess

    events
  }
}
