package borb.fetch

import spinal.core._
import spinal.lib._

case class FrontendConfig(
  addressWidth: Int,
  dataWidth: Int,
  epochWidth: Int = 16,
  beatBufferDepth: Int = 8,
  requestQueueDepth: Int = 8,
  maxInflight: Int = 4,
  withCompressed: Boolean = false
) {
  def beatBytes: Int = dataWidth / 8
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
