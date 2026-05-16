package borb.fetch

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba4.axi._
import spinal.lib.misc.pipeline._

import borb.common.Common._
import borb.common.{LaneContracts, LaneKey}
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
    val bundleConsume = Bool()
    val bundleHold = Bool()
    val bundleDeferRefill = Bool()
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

  val nextBundleSeq = Reg(UInt(32 bits)) init(0)
  val nextFetchSeq = Reg(UInt(32 bits)) init(0)
  val acceptedTraversal = Reg(FrontendAcceptedTraversal(cfg)) init(FrontendAcceptedTraversal(cfg).getZero)
  val acceptedTraversalValid = RegInit(False)
  val flushFrontend = io.recover.valid
  val invalidateIcache = io.recover.valid && io.recover.payload.invalidateIcache
  val flushIcacheState = flushFrontend

  val control = FrontendControl(cfg)
  control.io.flush := flushFrontend
  control.io.recover <> io.recover
  control.io.commandValid := cmdStage.up.isValid
  control.io.commandPc := cmdStage(PC.PC)
  control.io.commandEpoch := io.currentEpoch

  val predictor = FrontendPredictor(cfg)
  predictor.io.active <> control.io.active
  predictor.io.branchResolve <> io.branchResolve
  predictor.io.indirectResolve <> io.indirectResolve
  predictor.io.recover <> io.recover

  val traversal = FrontendTraversal(cfg)
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

  val rspTraversal = Payload(TraversalRsp(cfg)).setName("FETCH_RSP_TRAVERSAL")
  val rspLookupRsps = Payload(Vec(ICacheLookupRsp(cfg), cfg.lookupLanes)).setName("FETCH_RSP_LOOKUP_RSPS")
  rspStage.up(rspTraversal).setAsReg().init(TraversalRsp(cfg).getZero)
  val rspLookupRspRegs = rspStage.up(rspLookupRsps).setAsReg()
  for(lane <- 0 until cfg.lookupLanes) {
    rspLookupRspRegs(lane).init(ICacheLookupRsp(cfg).getZero)
  }

  cmdStage.down(rspTraversal).allowOverride := traversal.io.metadata
  cmdStage.down(rspLookupRsps).allowOverride := l1i.io.lookupRsp
  cmdStage.down(INSTRUCTION).allowOverride := 0
  cmdStage.down(PC.PC).allowOverride := control.io.active.payload.pc
  cmdStage.down(SPEC_EPOCH).allowOverride := io.currentEpoch
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

  val builder = FrontendBundleBuilder(cfg)
  builder.io.traversal := rspStage.up(rspTraversal)
  builder.io.lookupRsps := rspStage.up(rspLookupRsps)
  builder.io.bundleSeqBase := nextBundleSeq
  builder.io.fetchSeqBase := nextFetchSeq

  val queue = FrontendQueue(cfg)
  queue.io.flush := flushFrontend
  def driveLaneDefaults(key: Any): Unit = {
    rspStage.down(INSTRUCTION, key).allowOverride := 0
    rspStage.down(PC.PC, key).allowOverride := 0
    rspStage.down(SPEC_EPOCH, key).allowOverride := 0
    rspStage.down(Fetch.FETCH_SEQ, key).allowOverride := 0
    rspStage.down(Fetch.FETCH_BUNDLE_SEQ, key).allowOverride := 0
    rspStage.down(FETCH_FTQ_IDX, key).allowOverride := 0
    rspStage.down(FETCH_SLOT_IDX, key).allowOverride := 0
    rspStage.down(FETCH_SLOT_COUNT, key).allowOverride := 0
    rspStage.down(FETCH_BLOCK_PC, key).allowOverride := 0
    rspStage.down(FETCH_BYTE_OFFSET, key).allowOverride := 0
    rspStage.down(FETCH_PREDICTED_VALID, key).allowOverride := False
    rspStage.down(FETCH_PREDICTED_TAKEN, key).allowOverride := False
    rspStage.down(FETCH_PREDICTED_TARGET, key).allowOverride := 0
  }

  def driveLaneFromSlot(key: Any, bundle: FetchBundle, lane: Int): Unit = {
    val slot = bundle.slots(lane)
    rspStage.down(INSTRUCTION, key).allowOverride := slot.insn
    rspStage.down(PC.PC, key).allowOverride := slot.pc
    rspStage.down(SPEC_EPOCH, key).allowOverride := bundle.epoch
    rspStage.down(Fetch.FETCH_SEQ, key).allowOverride := bundle.bundleMeta.fetchSeqBase + U(lane, 32 bits)
    rspStage.down(Fetch.FETCH_BUNDLE_SEQ, key).allowOverride := bundle.bundleSeq
    rspStage.down(FETCH_FTQ_IDX, key).allowOverride := slot.ftqIndex.resized
    rspStage.down(FETCH_SLOT_IDX, key).allowOverride := slot.slotIdx.resized
    rspStage.down(FETCH_SLOT_COUNT, key).allowOverride := bundle.slotCount.resized
    rspStage.down(FETCH_BLOCK_PC, key).allowOverride := slot.blockPc
    rspStage.down(FETCH_BYTE_OFFSET, key).allowOverride := slot.byteOffsetInBlock.resized
    rspStage.down(FETCH_PREDICTED_VALID, key).allowOverride := slot.predictionMeta.valid
    rspStage.down(FETCH_PREDICTED_TAKEN, key).allowOverride := slot.predictionMeta.predictedTaken
    rspStage.down(FETCH_PREDICTED_TARGET, key).allowOverride := slot.predictionMeta.predictedTarget
  }

  rspStage.down(INSTRUCTION).allowOverride := 0
  rspStage.down(PC.PC).allowOverride := 0
  rspStage.down(SPEC_EPOCH).allowOverride := 0
  rspStage.down(Fetch.FETCH_SEQ).allowOverride := 0
  rspStage.down(Fetch.FETCH_BUNDLE_SEQ).allowOverride := 0
  rspStage.down(FETCH_FTQ_IDX).allowOverride := 0
  rspStage.down(FETCH_SLOT_IDX).allowOverride := 0
  rspStage.down(FETCH_SLOT_COUNT).allowOverride := 0
  rspStage.down(FETCH_BLOCK_PC).allowOverride := 0
  rspStage.down(FETCH_BYTE_OFFSET).allowOverride := 0
  rspStage.down(FETCH_PREDICTED_VALID).allowOverride := False
  rspStage.down(FETCH_PREDICTED_TAKEN).allowOverride := False
  rspStage.down(FETCH_PREDICTED_TARGET).allowOverride := 0
  rspStage.down(LANE_ID).allowOverride := 0
  rspStage.down(LANE_MASK).allowOverride := B"00"
  LaneKey.all.foreach(driveLaneDefaults)

  val outputBundle = queue.io.pop.payload
  val outputBundleValid = queue.io.pop.valid &&
    (outputBundle.epoch === io.currentEpoch) &&
    !flushFrontend

  rspStage.terminateWhen(control.io.ownsSequencing && (!outputBundleValid || io.bundleHold))
  when(control.io.ownsSequencing && outputBundleValid) {
    driveLaneFromSlot(LaneKey.Lane0, outputBundle, 0)
    if(cfg.bundleSlots > 1) {
      driveLaneFromSlot(LaneKey.Lane1, outputBundle, 1)
    }
    rspStage.down(LANE_MASK).allowOverride := LaneContracts.laneMaskFromSlotCount(outputBundle.slotCount)
  }

  queue.io.pop.ready := rspStage.down.isFiring && outputBundleValid && !io.bundleHold
  rspStage.haltWhen(builder.io.bundle.valid && !queue.io.push.ready)

  queue.io.push.valid := builder.io.bundle.valid && !flushFrontend
  queue.io.push.payload := builder.io.bundle.payload
  builder.io.bundle.ready := queue.io.push.ready && !flushFrontend

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
    nextFetchSeq := nextFetchSeq + builder.io.bundle.payload.slotCount.resized
  }

  io.iAxi.w.valid := False
  io.iAxi.w.data := 0
  io.iAxi.w.strb := 0
  io.iAxi.w.last := False
  io.iAxi.b.ready := True

  inflight := l1i.io.hasPendingMiss.asUInt.resize(4)
  epoch := io.currentEpoch

  beatValid := outputBundleValid
  val perf = FetchPerf(
    cfg,
    control,
    traversal,
    l1i,
    builder,
    queue,
    outputBundleValid,
    bundleAccepted,
    flushFrontend,
    io.branchResolve,
    io.recover,
    io.predictedJump.valid
  )
}
