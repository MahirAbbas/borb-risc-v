package borb.fetch

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba4.axi._

case class FetchBusBridge(config: FrontendConfig, axi: Axi4Shared, source: FetchSourceBus) extends Area {
  val requestAddrQ = StreamFifo(UInt(config.addressWidth bits), depth = config.requestQueueDepth)

  requestAddrQ.io.push.valid := source.req.valid && axi.arw.ready
  requestAddrQ.io.push.payload := source.req.address

  axi.arw.valid := source.req.valid && requestAddrQ.io.push.ready
  axi.arw.addr := source.req.address
  axi.arw.id := source.req.epoch.resized
  axi.arw.len := 0
  axi.arw.size := log2Up(config.dataWidth / 8)
  axi.arw.burst := Axi4.burst.INCR
  axi.arw.write := False

  source.req.ready := axi.arw.ready && requestAddrQ.io.push.ready

  axi.r.ready := requestAddrQ.io.pop.valid
  requestAddrQ.io.pop.ready := axi.r.fire

  val rspValid = RegInit(False)
  val rspData = Reg(Bits(config.dataWidth bits)) init(0)
  val rspAddr = Reg(UInt(config.addressWidth bits)) init(0)
  val rspEpoch = Reg(UInt(config.epochWidth bits)) init(0)

  rspValid := axi.r.fire
  when(axi.r.fire) {
    rspData := axi.r.data(config.dataWidth - 1 downto 0)
    rspAddr := requestAddrQ.io.pop.payload
    rspEpoch := axi.r.id.resized
  }

  source.rsp.valid := rspValid
  source.rsp.data := rspData
  source.rsp.address := rspAddr
  source.rsp.epoch := rspEpoch
}
