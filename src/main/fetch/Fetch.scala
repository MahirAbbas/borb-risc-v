package borb.fetch

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba4.axi._
import spinal.lib.misc.pipeline._

import borb.common.Common._
import borb.common.LaneContracts
import borb.fetch.PC
import borb.frontend.Decoder.INSTRUCTION

object Fetch extends AreaObject {
  val addressWidth = 64
  val FETCH_SEQ = Payload(UInt(32 bits))
  val FETCH_BUNDLE_SEQ = Payload(UInt(32 bits))
  val FETCH_FTQ_IDX = Payload(UInt(8 bits))
  val FETCH_SLOT_IDX = Payload(UInt(2 bits))
  val FETCH_SLOT_COUNT = Payload(UInt(2 bits))
  val FETCH_BLOCK_PC = Payload(UInt(addressWidth bits))
  val FETCH_BYTE_OFFSET = Payload(UInt(4 bits))
  val FETCH_PREDICTED_VALID = Payload(Bool())
  val FETCH_PREDICTED_TAKEN = Payload(Bool())
  val FETCH_PREDICTED_TARGET = Payload(UInt(addressWidth bits))
}

case class Fetch(
  cmdStage: CtrlLink,
  rspStage: CtrlLink,
  addressWidth: Int,
  dataWidth: Int,
  idWidth: Int = 16,
  withCompressed: Boolean = false,
  fetchBufferDepth: Int = 8,
  xlen: Int = 64,
  frontendConfig: FrontendConfig = FrontendConfig(addressWidth = 64, dataWidth = 64, withCompressed = true)
) extends Area {
  import Fetch._

  private val cfg = frontendConfig.copy(
    addressWidth = addressWidth,
    dataWidth = dataWidth,
    withCompressed = withCompressed
  )

  private val axiConfig = Axi4Config(
    addressWidth = addressWidth,
    dataWidth = dataWidth,
    idWidth = idWidth,
    useId = true,
    useRegion = false,
    useLock = false,
    useQos = false,
    useProt = false,
    useCache = false
  )

  val io = new Bundle {
    val iAxi = Axi4Shared(axiConfig)
    val currentEpoch = UInt(cfg.epochWidth bits)
    val scalarConsume = Bool()
    val scalarSkip = Bool()
    val scalarHold = Bool()
    val scalarDeferRefill = Bool()
    val suppressPrefetch = Bool()
    val iAxiReqIsPrefetch = Bool()
    val pcAdvance = Bool()
    val pcStep = UInt(4 bits)
    val vmTranslateVirt = UInt(addressWidth bits)
    val vmTranslatePhys = UInt(addressWidth bits)
    val vmTranslateEnable = Bool()
    val vmTranslateValid = Bool()
    val predictedJump = Flow(JumpCmd(addressWidth))
    val recover = Flow(FrontendRecoverUpdate(cfg))
    val branchResolve = Flow(BranchResolveUpdate(cfg))
    val indirectResolve = Flow(IndirectResolveUpdate(cfg))
  }

  val beatValid = Bool()
  val inflight = UInt(4 bits)
  val epoch = UInt(cfg.epochWidth bits)
  val perfPendingReq = Bool()
  val perfBeat0Valid = Bool()
  val perfBeat1Valid = Bool()
  val perfReqIssued = Bool()
  val perfRspAccepted = Bool()
  val perfNeedCurrentReq = Bool()
  val perfNeedNextReq = Bool()
  val perfPrefetchReq = Bool()
  val perfWaitCurBeat = Bool()
  val perfWaitNextBeat = Bool()
  val perfTakeInsn = Bool()
  val perfCurBeatHit = Bool()
  val perfNextBeatHit = Bool()
  val perfCmdValid = Bool()
  val perfPrefetchWindow = Bool()
  val perfPrefetchBlockedNoCmd = Bool()
  val perfPrefetchBlockedPending = Bool()
  val perfPrefetchBlockedNextHit = Bool()
  val perfLoopPredictUsed = Bool()
  val perfLoopPredictHit = Bool()
  val perfFastPredictHit = Bool()
  val perfMainPredictHit = Bool()
  val perfIndirectPredictHit = Bool()
  val perfRasUse = Bool()
  val perfRasRepair = Bool()
  val perfFtqAlloc = Bool()
  val perfFtqRestore = Bool()
  val perfPredictedRedirect = Bool()
  val perfMissCurrentBlock = Bool()
  val perfMissNextBlock = Bool()
  val perfMissPrefetch = Bool()
  val perfReqBlockedOutstanding = Bool()
  val perfPacketQueueFull = Bool()
  val perfStraddlePacket = Bool()
  val perfSecondBlockUsed = Bool()
  val perfSecondBlockLate = Bool()
  val perfWrongPathBeat = Bool()
  val perfWrongPathInsn = Bool()
  val perfL1iBankConflict = Bool()
  val perfL1iBankBusy = Bool()
  val perfL1iDualFetch = Bool()

  val nextBundleSeq = Reg(UInt(32 bits)) init(0)
  val nextScalarSeq = Reg(UInt(32 bits)) init(0)
  val acceptedTraversal = Reg(FrontendAcceptedTraversal(cfg)) init(FrontendAcceptedTraversal(cfg).getZero)
  val acceptedTraversalValid = RegInit(False)
  val flushFrontend = io.recover.valid
  val invalidateIcache = io.recover.valid && io.recover.payload.invalidateIcache
  val flushIcacheState = flushFrontend

  val control = FrontendControl(cfg)
  control.io.flush := flushFrontend
  control.io.redirectValid := io.recover.valid
  control.io.redirectPc := io.recover.payload.redirectTarget
  control.io.redirectEpoch := io.recover.payload.epoch
  control.io.commandValid := cmdStage.up.isValid
  control.io.commandPc := cmdStage(PC.PC)
  control.io.commandEpoch := io.currentEpoch

  val predictor = FrontendPredictor(cfg)
  predictor.io.valid := control.io.activeValid
  predictor.io.startPc := control.io.startPc
  predictor.io.epoch := control.io.epoch
  predictor.io.branchResolve := io.branchResolve
  predictor.io.indirectResolve := io.indirectResolve
  predictor.io.recover := io.recover

  val traversal = FrontendTraversal(cfg)
  traversal.io.activeValid := control.io.activeValid
  traversal.io.traversal := predictor.io.traversal

  val l1i = FrontendL1I(cfg)
  l1i.io.flush := flushIcacheState
  l1i.io.invalidate := invalidateIcache
  l1i.io.currentEpoch := io.currentEpoch
  l1i.io.suppressPrefetch := io.suppressPrefetch
  l1i.io.lookupReq := traversal.io.lookupReqs

  io.vmTranslateVirt.allowOverride := l1i.io.missReq.lineAddr
  io.vmTranslateValid := l1i.io.missReq.valid
  val busBridge = FetchBusBridge(cfg, io.iAxi, flushIcacheState, l1i.io.missReq, l1i.io.missRsp, io.vmTranslatePhys, io.vmTranslateEnable)
  io.iAxiReqIsPrefetch := busBridge.issuingPrefetch

  val builder = FrontendBundleBuilder(cfg)
  builder.io.traversal := traversal.io.metadata
  builder.io.lookupRsps := l1i.io.lookupRsp
  builder.io.bundleSeqBase := nextBundleSeq
  builder.io.scalarSeqBase := nextScalarSeq

  val queue = FrontendQueue(cfg)
  queue.io.flush := flushFrontend

  val adapter = FrontendScalarAdapter(cfg)
  adapter.io.flush := flushFrontend
  adapter.io.currentEpoch := io.currentEpoch
  adapter.io.bundles << queue.io.pop
  val scalarBoundaryValid = RegInit(False)
  val scalarBoundaryPayload = Reg(ScalarFetchEntry(cfg)) init(ScalarFetchEntry(cfg).getZero)
  val scalarBoundaryStale = scalarBoundaryValid && (scalarBoundaryPayload.epoch =/= io.currentEpoch)
  adapter.io.consume := False
  when(flushFrontend || io.scalarHold || scalarBoundaryStale) {
    scalarBoundaryValid := False
  } otherwise {
    when(io.scalarSkip) {
      adapter.io.consume := True
    }
    when(io.scalarConsume) {
      scalarBoundaryValid := False
      when(io.scalarDeferRefill) {
        adapter.io.consume := True
      }
    }
    when((!scalarBoundaryValid || io.scalarConsume) &&
      !io.scalarSkip &&
      !io.scalarDeferRefill &&
      adapter.io.scalar.valid &&
      (adapter.io.scalar.payload.epoch === io.currentEpoch)) {
      scalarBoundaryValid := True
      scalarBoundaryPayload := adapter.io.scalar.payload
      adapter.io.consume := True
    }
  }
  cmdStage.down(INSTRUCTION).allowOverride := 0
  cmdStage.down(SPEC_EPOCH).allowOverride := 0
  cmdStage.down(Fetch.FETCH_SEQ).allowOverride := 0
  cmdStage.down(Fetch.FETCH_BUNDLE_SEQ).allowOverride := 0
  cmdStage.down(FETCH_FTQ_IDX).allowOverride := 0
  cmdStage.down(FETCH_SLOT_IDX).allowOverride := 0
  cmdStage.down(FETCH_SLOT_COUNT).allowOverride := 0
  cmdStage.down(FETCH_BLOCK_PC).allowOverride := 0
  cmdStage.down(FETCH_BYTE_OFFSET).allowOverride := 0
  cmdStage.down(FETCH_PREDICTED_VALID).allowOverride := False
  cmdStage.down(FETCH_PREDICTED_TAKEN).allowOverride := False
  cmdStage.down(FETCH_PREDICTED_TARGET).allowOverride := 0
  cmdStage.down(LANE_ID).allowOverride := 0
  cmdStage.down(LANE_MASK).allowOverride := B"00"
  // Stage 2 latches from cmdStage.down via the pipeline StageLink. Once the
  // frontend has bootstrapped, terminate the legacy seed path whenever the
  // scalar boundary is empty, and override the stage-2 PC via the link bypass.
  cmdStage.terminateWhen(control.io.ownsSequencing && !scalarBoundaryValid)
  when(control.io.ownsSequencing) {
    cmdStage.bypass(PC.PC) := scalarBoundaryPayload.pc
    cmdStage.down(INSTRUCTION).allowOverride := scalarBoundaryPayload.insn
    cmdStage.down(SPEC_EPOCH).allowOverride := scalarBoundaryPayload.epoch
    cmdStage.down(Fetch.FETCH_SEQ).allowOverride := scalarBoundaryPayload.scalarSeq
    cmdStage.down(Fetch.FETCH_BUNDLE_SEQ).allowOverride := scalarBoundaryPayload.bundleSeq
    cmdStage.down(FETCH_FTQ_IDX).allowOverride := scalarBoundaryPayload.ftqIndex.resized
    cmdStage.down(FETCH_SLOT_IDX).allowOverride := scalarBoundaryPayload.slotIdx.resized
    cmdStage.down(FETCH_SLOT_COUNT).allowOverride := scalarBoundaryPayload.slotCount.resized
    cmdStage.down(FETCH_BLOCK_PC).allowOverride := scalarBoundaryPayload.blockPc
    cmdStage.down(FETCH_BYTE_OFFSET).allowOverride := scalarBoundaryPayload.byteOffsetInBlock.resized
    cmdStage.down(FETCH_PREDICTED_VALID).allowOverride := scalarBoundaryPayload.predictedValid
    cmdStage.down(FETCH_PREDICTED_TAKEN).allowOverride := scalarBoundaryPayload.predictedTaken
    cmdStage.down(FETCH_PREDICTED_TARGET).allowOverride := scalarBoundaryPayload.predictedTarget
    cmdStage.down(LANE_ID).allowOverride := scalarBoundaryPayload.slotIdx(0).asUInt
    cmdStage.down(LANE_MASK).allowOverride := LaneContracts.laneMaskFromSlotCount(scalarBoundaryPayload.slotCount)
  }

  // The scalar compatibility path must not let fetch get ahead of the adapter.
  // Otherwise an execute-stage redirect can invalidate queued same-path bundles
  // while an older scalar boundary is still being killed, leaving no current
  // epoch instruction to drain the pipeline.
  val scalarCompatibilityBusy = adapter.io.active || scalarBoundaryValid || (queue.io.occupancy =/= 0)
  queue.io.push.valid := builder.io.bundle.valid && !scalarCompatibilityBusy && !flushFrontend
  queue.io.push.payload := builder.io.bundle.payload
  builder.io.bundle.ready := queue.io.push.ready && !scalarCompatibilityBusy && !flushFrontend

  val bundleAccepted = builder.io.bundle.fire
  acceptedTraversalValid := False
  when(bundleAccepted) {
    acceptedTraversal.bundleSeq := builder.io.bundle.payload.bundleSeq
    acceptedTraversal.traversal.valid := builder.io.bundle.payload.valid
    acceptedTraversal.traversal.startPc := builder.io.bundle.payload.startPc
    acceptedTraversal.traversal.epoch := builder.io.bundle.payload.epoch
    acceptedTraversal.traversal.blockCount := builder.io.bundle.payload.bundleMeta.traversedBlockCount
    acceptedTraversal.traversal.blocks := builder.io.bundle.payload.bundleMeta.traversedBlocks
    acceptedTraversal.traversal.predictedStopReason := builder.io.bundle.payload.bundleMeta.predictedStopReason
    acceptedTraversal.traversal.predictedRedirectValid := builder.io.bundle.payload.bundleMeta.predictedRedirectValid
    acceptedTraversal.traversal.predictedRedirectTarget := builder.io.bundle.payload.bundleMeta.predictedRedirectTarget
    acceptedTraversal.traversal.nextStartPc := builder.io.bundle.payload.bundleMeta.nextStartPc
    acceptedTraversalValid := True
  }
  predictor.io.accepted.valid := acceptedTraversalValid
  predictor.io.accepted.payload := acceptedTraversal
  control.io.bundleAccepted := bundleAccepted

  val consumedBytes = UInt(4 bits)
  consumedBytes := 0
  when(builder.io.bundle.payload.slotCount === U(2, builder.io.bundle.payload.slotCount.getWidth bits)) {
    consumedBytes := (builder.io.bundle.payload.slots(1).nextPc - builder.io.bundle.payload.startPc).resized
  } elsewhen(builder.io.bundle.payload.slotCount === U(1, builder.io.bundle.payload.slotCount.getWidth bits)) {
    consumedBytes := (builder.io.bundle.payload.slots(0).nextPc - builder.io.bundle.payload.startPc).resized
  }
  val bundleSequentialNextPc = UInt(cfg.addressWidth bits)
  bundleSequentialNextPc := builder.io.bundle.payload.startPc + consumedBytes.resized
  if(cfg.predictedRedirectEnabled) {
    control.io.bundleNextPc := builder.io.bundle.payload.bundleMeta.nextStartPc
  } else {
    control.io.bundleNextPc := bundleSequentialNextPc
  }

  io.pcAdvance := bundleAccepted
  io.pcStep := consumedBytes
  io.predictedJump.valid := (if(cfg.predictedRedirectEnabled) bundleAccepted && builder.io.bundle.payload.bundleMeta.predictedRedirectValid else False)
  io.predictedJump.payload.target := builder.io.bundle.payload.bundleMeta.predictedRedirectTarget
  io.predictedJump.payload.is_jump := io.predictedJump.valid
  io.predictedJump.payload.is_branch := False

  when(bundleAccepted) {
    nextBundleSeq := nextBundleSeq + 1
    nextScalarSeq := nextScalarSeq + builder.io.bundle.payload.slotCount.resized
  }

  io.iAxi.w.valid := False
  io.iAxi.w.data := 0
  io.iAxi.w.strb := 0
  io.iAxi.w.last := False
  io.iAxi.b.ready := True

  inflight := l1i.io.hasPendingMiss.asUInt.resize(4)
  epoch := io.currentEpoch

  beatValid := scalarBoundaryValid
  perfPendingReq := l1i.io.hasPendingMiss
  perfBeat0Valid := scalarBoundaryValid
  perfBeat1Valid := queue.io.pop.valid && (queue.io.pop.payload.slotCount > U(1, queue.io.pop.payload.slotCount.getWidth bits))
  perfReqIssued := l1i.io.missReq.fire
  perfRspAccepted := l1i.io.missRsp.valid
  perfNeedCurrentReq := control.io.activeValid && !l1i.io.lookupRsp(0).hit
  perfNeedNextReq := (if(cfg.lookupLanes > 1) control.io.activeValid && traversal.io.metadata.blocks(1).valid && !l1i.io.lookupRsp(1).hit else False)
  perfPrefetchReq := l1i.io.prefetchReqIssued
  perfWaitCurBeat := control.io.activeValid && !builder.io.bundle.valid
  perfWaitNextBeat := control.io.activeValid && builder.io.bundle.valid && (builder.io.bundle.payload.slotCount === U(1, builder.io.bundle.payload.slotCount.getWidth bits))
  perfTakeInsn := bundleAccepted
  perfCurBeatHit := l1i.io.lookupRsp(0).hit
  perfNextBeatHit := (if(cfg.lookupLanes > 1) l1i.io.lookupRsp(1).hit else False)
  perfCmdValid := control.io.activeValid
  perfPrefetchWindow := False
  perfPrefetchBlockedNoCmd := False
  perfPrefetchBlockedPending := False
  perfPrefetchBlockedNextHit := False
  perfLoopPredictUsed := builder.io.bundle.valid && builder.io.bundle.payload.bundleMeta.traversedBlocks.map(_.loopPredicted).foldLeft(False)(_ || _)
  perfLoopPredictHit := perfLoopPredictUsed && builder.io.bundle.payload.bundleMeta.predictedRedirectValid
  perfFastPredictHit := builder.io.bundle.valid && builder.io.bundle.payload.bundleMeta.traversedBlocks.map(_.rasUsed).foldLeft(False)(_ || _)
  perfMainPredictHit := builder.io.bundle.valid && builder.io.bundle.payload.bundleMeta.traversedBlocks.map(_.predictedTaken).foldLeft(False)(_ || _)
  perfIndirectPredictHit := builder.io.bundle.valid && builder.io.bundle.payload.bundleMeta.traversedBlocks.map(_.indirectProvided).foldLeft(False)(_ || _)
  perfRasUse := builder.io.bundle.valid && builder.io.bundle.payload.bundleMeta.traversedBlocks.map(_.rasUsed).foldLeft(False)(_ || _)
  perfRasRepair := io.branchResolve.valid && (io.branchResolve.payload.isCall || io.branchResolve.payload.isReturn) && io.branchResolve.payload.mispredict
  perfFtqAlloc := bundleAccepted
  perfFtqRestore := io.recover.valid && io.recover.payload.recovery.valid
  perfPredictedRedirect := io.predictedJump.valid
  perfMissCurrentBlock := l1i.io.lookupReq(0).valid && !l1i.io.lookupRsp(0).hit
  perfMissNextBlock := (if(cfg.lookupLanes > 1) traversal.io.lookupReqs(1).valid && !l1i.io.lookupRsp(1).hit else False)
  perfMissPrefetch := l1i.io.prefetchReqIssued
  perfReqBlockedOutstanding := l1i.io.reqBlockedOutstanding
  perfPacketQueueFull := builder.io.bundle.valid && !queue.io.push.ready
  perfStraddlePacket := False
  perfSecondBlockUsed := (if(cfg.bundleSlots > 1) {
    builder.io.bundle.valid && builder.io.bundle.payload.slots(1).valid && (builder.io.bundle.payload.slots(1).blockPc =/= builder.io.bundle.payload.slots(0).blockPc)
  } else {
    False
  })
  perfSecondBlockLate := control.io.activeValid && !builder.io.bundle.valid && l1i.io.lookupRsp.map(_.missPending).foldLeft(False)(_ || _)
  perfWrongPathBeat := l1i.io.staleRspDropped
  perfWrongPathInsn := flushFrontend && ((queue.io.occupancy =/= 0) || adapter.io.active)
  perfL1iBankConflict := l1i.io.bankConflictCycle
  perfL1iBankBusy := l1i.io.bankBusyCycle
  perfL1iDualFetch := l1i.io.dualLookupSuccess
}
