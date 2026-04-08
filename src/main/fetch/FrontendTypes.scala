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
  lineBytes: Int = 8,
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
  icacheSets: Int = 32,
  icacheWays: Int = 1
) {
  def beatBytes: Int = dataWidth / 8
  def ftqIndexWidth: Int = log2Up(ftqDepth max 2)
  def gshareHistoryWidth: Int = globalHistoryWidth
  def bimodalIndexWidth: Int = log2Up(gshareEntries max 2)
  def fetchBlockOffsetWidth: Int = log2Up(fetchBlockBytes max 2)
  def lineOffsetWidth: Int = log2Up(lineBytes max 2)
  def ftbSets: Int = (ftbEntries / (ftbWays max 1)) max 1
  def ftbSetIndexWidth: Int = log2Up(ftbSets max 2)
  def indirectSets: Int = (indirectEntries / (indirectWays max 1)) max 1
  def indirectSetIndexWidth: Int = log2Up(indirectSets max 2)
  def predictorTrainingEnabled: Boolean = experimentalFrontendEnable && enablePredictorTraining
  def predictedRedirectEnabled: Boolean = experimentalFrontendEnable && enablePredictedRedirect
  def loopPredictorActive: Boolean = predictorTrainingEnabled && loopPredictorEnable
}

object FrontendTargetKind extends SpinalEnum {
  val none, direct, ret, indirect = newElement()
}

case class FetchBlockMeta(config: FrontendConfig) extends Bundle {
  val blockPc = UInt(config.addressWidth bits)
  val fallthrough = UInt(config.addressWidth bits)
  val predictedTaken = Bool()
  val predictedPcValid = Bool()
  val takenByteOffset = UInt(config.fetchBlockOffsetWidth bits)
  val target = UInt(config.addressWidth bits)
  val targetKind = FrontendTargetKind()
  val isConditional = Bool()
  val isCall = Bool()
  val isReturn = Bool()
  val isIndirect = Bool()
  val loopPredicted = Bool()
  val indirectProvided = Bool()
  val rasUsed = Bool()
}

case class RasCheckpoint(config: FrontendConfig) extends Bundle {
  val sp = UInt(log2Up(config.rasDepth max 2) bits)
  val count = UInt(log2Up(config.rasDepth + 1) bits)
}

case class FtqEntry(config: FrontendConfig) extends Bundle {
  val valid = Bool()
  val blockPc = UInt(config.addressWidth bits)
  val epoch = UInt(config.epochWidth bits)
  val meta = FetchBlockMeta(config)
  val history = UInt(config.gshareHistoryWidth bits)
  val ras = RasCheckpoint(config)
}

case class PredictRequest(config: FrontendConfig) extends Bundle {
  val pc = UInt(config.addressWidth bits)
  val history = UInt(config.gshareHistoryWidth bits)
}

case class PredictResponse(config: FrontendConfig) extends Bundle {
  val valid = Bool()
  val meta = FetchBlockMeta(config)
  val fastHit = Bool()
  val mainHit = Bool()
  val indirectHit = Bool()
}

case class LoopPredictRequest(config: FrontendConfig) extends Bundle {
  val blockPc = UInt(config.addressWidth bits)
  val branchPc = UInt(config.addressWidth bits)
  val fallthrough = UInt(config.addressWidth bits)
}

case class LoopPredictResponse(config: FrontendConfig) extends Bundle {
  val valid = Bool()
  val taken = Bool()
  val target = UInt(config.addressWidth bits)
  val fallthrough = UInt(config.addressWidth bits)
  val tripCount = UInt(8 bits)
  val confidence = UInt(2 bits)
}

case class LoopLearn(config: FrontendConfig) extends Bundle {
  val valid = Bool()
  val blockPc = UInt(config.addressWidth bits)
  val branchPc = UInt(config.addressWidth bits)
  val target = UInt(config.addressWidth bits)
  val fallthrough = UInt(config.addressWidth bits)
  val taken = Bool()
}

case class RedirectUpdate(config: FrontendConfig) extends Bundle {
  val ftqIndex = UInt(config.ftqIndexWidth bits)
  val blockPc = UInt(config.addressWidth bits)
  val branchPc = UInt(config.addressWidth bits)
  val target = UInt(config.addressWidth bits)
  val fallthrough = UInt(config.addressWidth bits)
  val epoch = UInt(config.epochWidth bits)
  val taken = Bool()
  val predictedTaken = Bool()
  val predictedTarget = UInt(config.addressWidth bits)
  val mispredict = Bool()
  val isConditional = Bool()
  val isJump = Bool()
  val isCall = Bool()
  val isReturn = Bool()
  val isIndirect = Bool()
  val takenByteOffset = UInt(config.fetchBlockOffsetWidth bits)
}

case class BranchLearn(config: FrontendConfig) extends Bundle {
  val redirect = RedirectUpdate(config)
}

case class IndirectLearn(config: FrontendConfig) extends Bundle {
  val valid = Bool()
  val blockPc = UInt(config.addressWidth bits)
  val history = UInt(config.gshareHistoryWidth bits)
  val target = UInt(config.addressWidth bits)
}

case class FrontendMissReq(config: FrontendConfig) extends Bundle {
  val address = UInt(config.addressWidth bits)
  val ftqIndex = UInt(config.ftqIndexWidth bits)
}

case class FrontendMissRsp(config: FrontendConfig) extends Bundle {
  val address = UInt(config.addressWidth bits)
  val data = Bits(config.dataWidth bits)
}

case class FetchReq(addressWidth: Int, epochWidth: Int) extends Bundle {
  val address = UInt(addressWidth bits)
  val epoch = UInt(epochWidth bits)
}

case class FetchRspBeat(addressWidth: Int, dataWidth: Int, epochWidth: Int) extends Bundle {
  val data = Bits(dataWidth bits)
  val address = UInt(addressWidth bits)
  val epoch = UInt(epochWidth bits)
}

case class FetchPacket(addressWidth: Int, dataWidth: Int, epochWidth: Int) extends Bundle {
  val data = Bits(dataWidth bits)
  val beatAddr = UInt(addressWidth bits)
  val epoch = UInt(epochWidth bits)
}

case class FrontendPacket(addressWidth: Int, epochWidth: Int) extends Bundle {
  val instruction = Bits(32 bits)
  val isCompressed = Bool()
  val pc = UInt(addressWidth bits)
  val nextPc = UInt(addressWidth bits)
  val epoch = UInt(epochWidth bits)
  val fetchSeq = UInt(32 bits)
  val valid = Bool()
  val illegal = Bool()
  val fetchFault = Bool()
  val rd = Bits(5 bits)
  val rs1 = Bits(5 bits)
  val rs2 = Bits(5 bits)
  val rs3 = Bits(5 bits)
}

case class FetchSourceBus(addressWidth: Int, dataWidth: Int, epochWidth: Int) extends Bundle with IMasterSlave {
  val req = Stream(FetchReq(addressWidth, epochWidth))
  val rsp = Flow(FetchRspBeat(addressWidth, dataWidth, epochWidth))

  override def asMaster(): Unit = {
    master(req)
    slave(rsp)
  }
}
