package borb.memory

import spinal.core._
import spinal.lib._

case class RamFetchCmd(addressWidth: Int, idWidth: Int) extends Bundle {
  val address = UInt(addressWidth bits)
  val id = UInt(idWidth bits)
}
case class RamFetchRsp(dataWidth: Int, addressWidth: Int, idWidth: Int) extends Bundle {
  val data = Bits(dataWidth bits)
  val address = UInt(addressWidth bits)
  val id = UInt(idWidth bits)
}
case class RamFetchBus(addressWidth: Int, dataWidth: Int, idWidth: Int) extends Bundle with IMasterSlave{
  val cmd = Stream(RamFetchCmd(addressWidth, idWidth))
  val rsp = Flow(RamFetchRsp(dataWidth, addressWidth, idWidth))
  
  def <>(that: RamFetchBus) {
    this.cmd <> that.cmd
    this.rsp <> that.rsp

  }
  override def asMaster() = {
    master(cmd)
    slave(rsp)
  }
}

