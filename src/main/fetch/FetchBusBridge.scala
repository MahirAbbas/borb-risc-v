package borb.fetch

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba4.axi._

case class FetchBusBridge(
    config: FrontendConfig,
    axi: Axi4Shared,
    flush: Bool,
    missReq: Stream[ICacheMissReq],
    missRsp: Flow[ICacheMissRsp],
    translatedAddress: UInt,
    translateEnable: Bool
) extends Area {
  case class IssuedReq() extends Bundle {
    val lineAddr = UInt(config.addressWidth bits)
    val physAddr = UInt(config.addressWidth bits)
    val epoch = UInt(config.epochWidth bits)
    val tag = UInt(config.requestTagWidth bits)
    val reqId = UInt(axi.arw.id.getWidth bits)
    val beatCount = UInt(log2Up(config.beatsPerLine + 1) bits)
    val lineData = Bits(config.lineDataWidth bits)
  }

  val issueQ = StreamFifo(IssuedReq(), depth = config.requestQueueDepth)
  val inflight = Vec.fill(config.maxOutstandingMisses)(Reg(IssuedReq()) init(IssuedReq().getZero))
  val inflightValid = Vec.fill(config.maxOutstandingMisses)(RegInit(False))
  val nextReqId = Reg(UInt(axi.arw.id.getWidth bits)) init(0)

  issueQ.io.flush := flush

  issueQ.io.push.valid := missReq.valid
  issueQ.io.push.payload.lineAddr := missReq.lineAddr
  issueQ.io.push.payload.physAddr := Mux(translateEnable, translatedAddress, missReq.lineAddr)
  issueQ.io.push.payload.epoch := missReq.epoch
  issueQ.io.push.payload.tag := missReq.tag
  issueQ.io.push.payload.reqId := 0
  issueQ.io.push.payload.beatCount := U(0, issueQ.io.push.payload.beatCount.getWidth bits)
  issueQ.io.push.payload.lineData := 0
  missReq.ready := issueQ.io.push.ready

  val issueSlot = issueQ.io.pop.tag.resized
  val issueFire = issueQ.io.pop.valid && axi.arw.ready && !flush
  issueQ.io.pop.ready := axi.arw.ready && !flush

  axi.arw.valid := issueQ.io.pop.valid && !flush
  axi.arw.addr := issueQ.io.pop.physAddr
  axi.arw.id := nextReqId
  axi.arw.len := config.beatsPerLine - 1
  axi.arw.size := log2Up(config.dataWidth / 8)
  axi.arw.burst := Axi4.burst.INCR
  axi.arw.write := False
  axi.r.ready := True

  when(issueFire) {
    inflight(issueSlot) := issueQ.io.pop.payload
    inflight(issueSlot).reqId := nextReqId
    inflightValid(issueSlot) := True
    nextReqId := nextReqId + 1
  }

  val rspValid = RegInit(False)
  val rspPayload = Reg(ICacheMissRsp(config)) init(ICacheMissRsp(config).getZero)
  rspValid := False

  when(axi.r.fire && !flush) {
    val matched = Bool()
    matched := False
    for(slot <- 0 until config.maxOutstandingMisses) {
      when(inflightValid(slot) && (inflight(slot).reqId === axi.r.id)) {
        matched := True
        val nextBeatCount = inflight(slot).beatCount + 1
        for(beat <- 0 until config.beatsPerLine) {
          when(inflight(slot).beatCount === U(beat, inflight(slot).beatCount.getWidth bits)) {
            inflight(slot).lineData(((beat + 1) * config.dataWidth) - 1 downto beat * config.dataWidth) := axi.r.data(config.dataWidth - 1 downto 0)
          }
        }
        inflight(slot).beatCount := nextBeatCount
        when(axi.r.last || (nextBeatCount === U(config.beatsPerLine, nextBeatCount.getWidth bits))) {
          rspValid := True
          rspPayload.lineAddr := inflight(slot).lineAddr
          rspPayload.data := inflight(slot).lineData
          for(beat <- 0 until config.beatsPerLine) {
            when(inflight(slot).beatCount === U(beat, inflight(slot).beatCount.getWidth bits)) {
              rspPayload.data(((beat + 1) * config.dataWidth) - 1 downto beat * config.dataWidth) := axi.r.data(config.dataWidth - 1 downto 0)
            }
          }
          rspPayload.tag := inflight(slot).tag
          rspPayload.epoch := inflight(slot).epoch
          inflightValid(slot) := False
          inflight(slot).beatCount := U(0, inflight(slot).beatCount.getWidth bits)
        }
      }
    }
  }

  when(flush) {
    rspValid := False
    for(slot <- 0 until config.maxOutstandingMisses) {
      inflightValid(slot) := False
      inflight(slot).beatCount := U(0, inflight(slot).beatCount.getWidth bits)
    }
  }

  missRsp.valid := rspValid
  missRsp.payload := rspPayload
}
