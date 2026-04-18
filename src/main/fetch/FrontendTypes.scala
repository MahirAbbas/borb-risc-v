package borb.fetch

import spinal.core._
import spinal.lib._

case class FrontendConfig(
  addressWidth: Int,
  dataWidth: Int,
  epochWidth: Int = 16,
  withCompressed: Boolean = false,
  experimentalFrontendEnable: Boolean = false,
  enablePredictorTraining: Boolean = false,
  enablePredictedRedirect: Boolean = false,
  fetchBlockBytes: Int = 8,
  lineBytes: Int = 16,
  ftqDepth: Int = 16,
  requestQueueDepth: Int = 8,
  rasDepth: Int = 32,
  gshareEntries: Int = 128,
  globalHistoryWidth: Int = 32,
  tageTableEntries: Int = 64,
  tageTableCount: Int = 3,
  tageCtrBits: Int = 3,
  tageUsefulBits: Int = 2,
  tageTagWidth: Int = 10,
  loopPredictorEnable: Boolean = true,
  loopPredictorEntries: Int = 8,
  nanoBtbEntries: Int = 8,
  ftbEntries: Int = 32,
  ftbWays: Int = 2,
  indirectEntries: Int = 16,
  indirectWays: Int = 2,
  indirectHistoryWidth: Int = 16,
  indirectCtrBits: Int = 2,
  indirectTagWidth: Int = 10,
  icacheBanks: Int = 2,
  icacheSets: Int = 64,
  icacheWays: Int = 2,
  lookupLanes: Int = 2,
  maxOutstandingMisses: Int = 2,
  bundleQueueDepth: Int = 4,
  bundleSlots: Int = 2,
  maxBlocksPerCycle: Int = 2
) {
  require(lineBytes >= fetchBlockBytes, "Frontend line must be at least one fetch block")
  require((lineBytes % fetchBlockBytes) == 0, "Frontend lineBytes must be a multiple of fetchBlockBytes")
  require((lineBytes % beatBytes) == 0, "Frontend lineBytes must be a multiple of data bus beatBytes")

  def beatBytes: Int = dataWidth / 8
  def lineDataWidth: Int = lineBytes * 8
  def blocksPerLine: Int = lineBytes / fetchBlockBytes
  def ftqIndexWidth: Int = log2Up(ftqDepth max 2)
  def bundleSlotIdxWidth: Int = log2Up(bundleSlots max 2)
  def bundleQueueIdxWidth: Int = log2Up(bundleQueueDepth max 2)
  def gshareHistoryWidth: Int = globalHistoryWidth
  def bimodalIndexWidth: Int = log2Up(gshareEntries max 2)
  def fetchBlockOffsetWidth: Int = log2Up(fetchBlockBytes max 2)
  def lineOffsetWidth: Int = log2Up(lineBytes max 2)
  def ftbSets: Int = (ftbEntries / (ftbWays max 1)) max 1
  def ftbSetIndexWidth: Int = log2Up(ftbSets max 2)
  def indirectSets: Int = (indirectEntries / (indirectWays max 1)) max 1
  def indirectSetIndexWidth: Int = log2Up(indirectSets max 2)
  def requestTagWidth: Int = log2Up(maxOutstandingMisses max 2)
  def beatsPerLine: Int = lineBytes / beatBytes
  def predictorTrainingEnabled: Boolean = experimentalFrontendEnable && enablePredictorTraining
  def predictedRedirectEnabled: Boolean = experimentalFrontendEnable && enablePredictedRedirect
  def loopPredictorActive: Boolean = predictorTrainingEnabled && loopPredictorEnable

  // Compatibility aliases while the rest of the core catches up.
  def packetQueueDepth: Int = bundleQueueDepth
  def maxOutstandingFetchReqs: Int = maxOutstandingMisses
}

object FrontendTargetKind extends SpinalEnum {
  val none, direct, ret, indirect = newElement()
}

object FrontendStopReason extends SpinalEnum {
  val none, slotLimit, predictedTaken, cacheMiss, redirect, invalidate = newElement()
}

object ICacheLookupKind extends SpinalEnum {
  val demand, prefetch = newElement()
}

object FrontendMissReason extends SpinalEnum {
  val demand, prefetch = newElement()
}

case class RasCheckpoint(config: FrontendConfig) extends Bundle {
  val sp = UInt(log2Up(config.rasDepth max 2) bits)
  val count = UInt(log2Up(config.rasDepth + 1) bits)
}

case class PredictorCheckpoint(config: FrontendConfig) extends Bundle {
  val history = UInt(config.gshareHistoryWidth bits)
  val ras = RasCheckpoint(config)
}

case class FtqEntry(config: FrontendConfig) extends Bundle {
  val valid = Bool()
  val blockPc = UInt(config.addressWidth bits)
  val epoch = UInt(config.epochWidth bits)
  val bundleSeq = UInt(32 bits)
  val checkpoint = PredictorCheckpoint(config)
}

case class FtqRecoveryPoint(config: FrontendConfig) extends Bundle {
  val valid = Bool()
  val ftqIndex = UInt(config.ftqIndexWidth bits)
  val bundleSeq = UInt(32 bits)
  val slotIdx = UInt(config.bundleSlotIdxWidth bits)
  val blockPc = UInt(config.addressWidth bits)
  val byteOffsetInBlock = UInt(config.fetchBlockOffsetWidth bits)
}

case class TraversalReq(config: FrontendConfig) extends Bundle {
  val startPc = UInt(config.addressWidth bits)
  val history = UInt(config.gshareHistoryWidth bits)
  val rasCheckpoint = RasCheckpoint(config)
  val epoch = UInt(config.epochWidth bits)
}

case class TraversalBlockDescriptor(config: FrontendConfig) extends Bundle {
  val valid = Bool()
  val ftqIndex = UInt(config.ftqIndexWidth bits)
  val blockPc = UInt(config.addressWidth bits)
  val needLookup = Bool()
  val predictedTaken = Bool()
  val target = UInt(config.addressWidth bits)
  val targetKind = FrontendTargetKind()
  val takenByteOffset = UInt(config.fetchBlockOffsetWidth bits)
  val fallthrough = UInt(config.addressWidth bits)
  val checkpoint = PredictorCheckpoint(config)
  val isConditional = Bool()
  val isCall = Bool()
  val isReturn = Bool()
  val isIndirect = Bool()
  val loopPredicted = Bool()
  val indirectProvided = Bool()
  val rasUsed = Bool()
}

case class TraversalRsp(config: FrontendConfig) extends Bundle {
  val valid = Bool()
  val startPc = UInt(config.addressWidth bits)
  val epoch = UInt(config.epochWidth bits)
  val blockCount = UInt(log2Up(config.maxBlocksPerCycle + 1) bits)
  val blocks = Vec(TraversalBlockDescriptor(config), config.maxBlocksPerCycle)
  val predictedStopReason = FrontendStopReason()
  val predictedRedirectValid = Bool()
  val predictedRedirectTarget = UInt(config.addressWidth bits)
  val nextStartPc = UInt(config.addressWidth bits)
}

case class FrontendAcceptedTraversal(config: FrontendConfig) extends Bundle {
  val bundleSeq = UInt(32 bits)
  val traversal = TraversalRsp(config)
}

case class ICacheLookupReq(config: FrontendConfig) extends Bundle {
  val valid = Bool()
  val blockAddr = UInt(config.addressWidth bits)
  val epoch = UInt(config.epochWidth bits)
  val kind = ICacheLookupKind()
}

case class ICacheLookupRsp(config: FrontendConfig) extends Bundle {
  val valid = Bool()
  val accepted = Bool()
  val hit = Bool()
  val blockAddr = UInt(config.addressWidth bits)
  val lineAddr = UInt(config.addressWidth bits)
  val lineData = Bits(config.lineDataWidth bits)
  val missPending = Bool()
  val bankConflict = Bool()
}

case class ICacheMissReq(config: FrontendConfig) extends Bundle {
  val lineAddr = UInt(config.addressWidth bits)
  val tag = UInt(config.requestTagWidth bits)
  val epoch = UInt(config.epochWidth bits)
  val reason = FrontendMissReason()
}

case class ICacheMissRsp(config: FrontendConfig) extends Bundle {
  val lineAddr = UInt(config.addressWidth bits)
  val data = Bits(config.lineDataWidth bits)
  val tag = UInt(config.requestTagWidth bits)
  val epoch = UInt(config.epochWidth bits)
}

case class FetchSlotPredictionMeta(config: FrontendConfig) extends Bundle {
  val valid = Bool()
  val ftqIndex = UInt(config.ftqIndexWidth bits)
  val predictedTaken = Bool()
  val predictedTarget = UInt(config.addressWidth bits)
  val targetKind = FrontendTargetKind()
  val blockStop = Bool()
}

case class FetchSlot(config: FrontendConfig) extends Bundle {
  val valid = Bool()
  val pc = UInt(config.addressWidth bits)
  val insn = Bits(32 bits)
  val isCompressed = Bool()
  val nextPc = UInt(config.addressWidth bits)
  val slotIdx = UInt(config.bundleSlotIdxWidth bits)
  val ftqIndex = UInt(config.ftqIndexWidth bits)
  val blockPc = UInt(config.addressWidth bits)
  val byteOffsetInBlock = UInt(config.fetchBlockOffsetWidth bits)
  val predictionMeta = FetchSlotPredictionMeta(config)
  val illegal = Bool()
  val fetchFault = Bool()
}

case class FetchBundleMeta(config: FrontendConfig) extends Bundle {
  val traversedBlockCount = UInt(log2Up(config.maxBlocksPerCycle + 1) bits)
  val traversedBlocks = Vec(TraversalBlockDescriptor(config), config.maxBlocksPerCycle)
  val predictedStopReason = FrontendStopReason()
  val predictedRedirectValid = Bool()
  val predictedRedirectTarget = UInt(config.addressWidth bits)
  val recovery = FtqRecoveryPoint(config)
  val nextStartPc = UInt(config.addressWidth bits)
  val scalarSeqBase = UInt(32 bits)
}

case class FetchBundle(config: FrontendConfig) extends Bundle {
  val valid = Bool()
  val bundleSeq = UInt(32 bits)
  val ftqIndexBase = UInt(config.ftqIndexWidth bits)
  val epoch = UInt(config.epochWidth bits)
  val startPc = UInt(config.addressWidth bits)
  val slotCount = UInt(log2Up(config.bundleSlots + 1) bits)
  val slots = Vec(FetchSlot(config), config.bundleSlots)
  val bundleMeta = FetchBundleMeta(config)
}

case class ScalarFetchEntry(config: FrontendConfig) extends Bundle {
  val valid = Bool()
  val scalarSeq = UInt(32 bits)
  val bundleSeq = UInt(32 bits)
  val epoch = UInt(config.epochWidth bits)
  val pc = UInt(config.addressWidth bits)
  val insn = Bits(32 bits)
  val isCompressed = Bool()
  val nextPc = UInt(config.addressWidth bits)
  val ftqIndex = UInt(config.ftqIndexWidth bits)
  val slotIdx = UInt(config.bundleSlotIdxWidth bits)
  val blockPc = UInt(config.addressWidth bits)
  val byteOffsetInBlock = UInt(config.fetchBlockOffsetWidth bits)
  val predictedValid = Bool()
  val predictedTaken = Bool()
  val predictedTarget = UInt(config.addressWidth bits)
  val illegal = Bool()
  val fetchFault = Bool()
}

case class BranchResolveUpdate(config: FrontendConfig) extends Bundle {
  val epoch = UInt(config.epochWidth bits)
  val ftqIndex = UInt(config.ftqIndexWidth bits)
  val bundleSeq = UInt(32 bits)
  val slotIdx = UInt(config.bundleSlotIdxWidth bits)
  val pc = UInt(config.addressWidth bits)
  val blockPc = UInt(config.addressWidth bits)
  val byteOffsetInBlock = UInt(config.fetchBlockOffsetWidth bits)
  val fallthrough = UInt(config.addressWidth bits)
  val actualTaken = Bool()
  val actualTarget = UInt(config.addressWidth bits)
  val predictedValid = Bool()
  val predictedTaken = Bool()
  val predictedTarget = UInt(config.addressWidth bits)
  val mispredict = Bool()
  val isConditional = Bool()
  val isJump = Bool()
  val isCall = Bool()
  val isReturn = Bool()
  val isIndirect = Bool()
}

case class IndirectResolveUpdate(config: FrontendConfig) extends Bundle {
  val epoch = UInt(config.epochWidth bits)
  val ftqIndex = UInt(config.ftqIndexWidth bits)
  val bundleSeq = UInt(32 bits)
  val slotIdx = UInt(config.bundleSlotIdxWidth bits)
  val blockPc = UInt(config.addressWidth bits)
  val byteOffsetInBlock = UInt(config.fetchBlockOffsetWidth bits)
  val target = UInt(config.addressWidth bits)
  val history = UInt(config.gshareHistoryWidth bits)
}

case class FrontendRecoverUpdate(config: FrontendConfig) extends Bundle {
  val redirectTarget = UInt(config.addressWidth bits)
  val redirectReason = FrontendRedirectReason()
  val epoch = UInt(config.epochWidth bits)
  val invalidateIcache = Bool()
  val recovery = FtqRecoveryPoint(config)
}
