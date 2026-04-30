package borb.execute

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba4.axi._

case class DCacheLine(tagWidth: Int) extends Bundle {
  val valid = Bool()
  val dirty = Bool()
  val tag = UInt(tagWidth bits)
  val data = Vec(Bits(64 bits), 8)
}

case class DataSideCache(dBus: DataBus, axi: Axi4Shared) extends Area {
  private val lineBytes = 64
  private val wordBytes = 8
  private val wordsPerLine = lineBytes / wordBytes
  private val sets = 128
  private val ways = 4
  private val setWidth = log2Up(sets)
  private val wayWidth = log2Up(ways)
  private val wordWidth = log2Up(wordsPerLine)
  private val tagWidth = 64 - log2Up(lineBytes) - setWidth

  object State extends SpinalEnum {
    val idle, writebackReq, writebackResp, refillReq, refillResp = newElement()
  }
  import State._

  val writeAddressArbitrating = Bool()

  val cache = Vec.fill(sets)(
    Vec.fill(ways)(Reg(DCacheLine(tagWidth)) init(DCacheLine(tagWidth).getZero))
  )
  val replacePtr = Vec.fill(sets)(Reg(UInt(wayWidth bits)) init 0)

  val state = RegInit(idle)
  val activeCmd = Reg(DataBusCmd(64, 64, 16))
  val activeSet = Reg(UInt(setWidth bits)) init 0
  val activeTag = Reg(UInt(tagWidth bits)) init 0
  val activeWord = Reg(UInt(wordWidth bits)) init 0
  val activeWay = Reg(UInt(wayWidth bits)) init 0
  val victimTag = Reg(UInt(tagWidth bits)) init 0
  val beat = Reg(UInt(wordWidth bits)) init 0
  val missLoadData = Reg(Bits(64 bits)) init 0
  val arwSent = RegInit(False)
  val wSent = RegInit(False)

  val rspValid = RegInit(False)
  val rspData = Reg(Bits(64 bits)) init 0
  val rspId = Reg(UInt(16 bits)) init 0

  val archBase = U(BigInt("80000000", 16), 64 bits)
  val lowDataAliasBase = U(4 MiB, 64 bits)
  def toPhys(addr: UInt): UInt = Mux(addr < archBase, addr + lowDataAliasBase, addr)

  def mergeBytes(oldData: Bits, newData: Bits, mask: Bits): Bits = {
    val out = Bits(64 bits)
    for(i <- 0 until 8) {
      out((i * 8 + 7) downto (i * 8)) := Mux(mask(i), newData((i * 8 + 7) downto (i * 8)), oldData((i * 8 + 7) downto (i * 8)))
    }
    out
  }

  def lineWordAddress(tag: UInt, set: UInt, word: UInt): UInt =
    (tag.asBits ## set.asBits ## word.asBits ## B"000").asUInt

  val lookupAddr = toPhys(dBus.cmd.payload.address)
  val lookupSet = lookupAddr(12 downto 6)
  val lookupTag = lookupAddr(63 downto 13)
  val lookupWord = lookupAddr(5 downto 3)

  val hitVec = Bits(ways bits)
  for(way <- 0 until ways) {
    hitVec(way) := cache(lookupSet)(way).valid && (cache(lookupSet)(way).tag === lookupTag)
  }
  val hit = hitVec.orR
  val hitWay = UInt(wayWidth bits)
  hitWay := 0
  for(way <- 0 until ways) {
    when(hitVec(way)) {
      hitWay := U(way, wayWidth bits)
    }
  }

  val invalidVec = Bits(ways bits)
  for(way <- 0 until ways) {
    invalidVec(way) := !cache(lookupSet)(way).valid
  }
  val victimWay = UInt(wayWidth bits)
  victimWay := replacePtr(lookupSet)
  for(way <- (ways - 1) downto 0) {
    when(invalidVec(way)) {
      victimWay := U(way, wayWidth bits)
    }
  }

  dBus.cmd.ready := (state === idle) && !rspValid
  dBus.rsp.valid := rspValid
  dBus.rsp.payload.data := rspData
  dBus.rsp.payload.id := rspId

  axi.arw.valid := False
  axi.arw.id := activeCmd.id.resized
  axi.arw.addr := lineWordAddress(activeTag, activeSet, beat)
  axi.arw.len := 0
  axi.arw.size := log2Up(64 / 8)
  axi.arw.burst := Axi4.burst.INCR
  axi.arw.write := False

  axi.w.valid := False
  axi.w.data := cache(activeSet)(activeWay).data(beat)
  axi.w.strb := B"11111111"
  axi.w.last := True

  axi.b.ready := False
  axi.r.ready := False

  writeAddressArbitrating := (state === writebackReq) && !arwSent

  when(rspValid) {
    rspValid := False
  }

  switch(state) {
    is(idle) {
      arwSent := False
      wSent := False
      beat := 0
      when(dBus.cmd.fire) {
        when(hit) {
          when(dBus.cmd.payload.write) {
            cache(lookupSet)(hitWay).data(lookupWord) := mergeBytes(
              cache(lookupSet)(hitWay).data(lookupWord),
              dBus.cmd.payload.data,
              dBus.cmd.payload.mask
            )
            cache(lookupSet)(hitWay).dirty := True
          } otherwise {
            rspData := cache(lookupSet)(hitWay).data(lookupWord)
            rspId := dBus.cmd.payload.id
            rspValid := True
          }
        } otherwise {
          activeCmd := dBus.cmd.payload
          activeSet := lookupSet
          activeTag := lookupTag
          activeWord := lookupWord
          activeWay := victimWay
          victimTag := cache(lookupSet)(victimWay).tag
          missLoadData := 0
          when(cache(lookupSet)(victimWay).valid && cache(lookupSet)(victimWay).dirty) {
            state := writebackReq
          } otherwise {
            state := refillReq
          }
        }
      }
    }

    is(writebackReq) {
      axi.arw.valid := !arwSent
      axi.arw.addr := lineWordAddress(victimTag, activeSet, beat)
      axi.arw.write := True
      axi.w.valid := !wSent
      axi.w.data := cache(activeSet)(activeWay).data(beat)
      when(axi.arw.fire) { arwSent := True }
      when(axi.w.fire) { wSent := True }
      when((arwSent || axi.arw.fire) && (wSent || axi.w.fire)) {
        state := writebackResp
      }
    }

    is(writebackResp) {
      axi.b.ready := True
      when(axi.b.valid) {
        arwSent := False
        wSent := False
        when(beat === U(wordsPerLine - 1, wordWidth bits)) {
          beat := 0
          state := refillReq
        } otherwise {
          beat := beat + 1
          state := writebackReq
        }
      }
    }

    is(refillReq) {
      axi.arw.valid := True
      axi.arw.addr := lineWordAddress(activeTag, activeSet, beat)
      axi.arw.write := False
      when(axi.arw.fire) {
        state := refillResp
      }
    }

    is(refillResp) {
      axi.r.ready := True
      when(axi.r.valid) {
        cache(activeSet)(activeWay).data(beat) := axi.r.data
        when(beat === activeWord) {
          missLoadData := axi.r.data
        }
        when(beat === U(wordsPerLine - 1, wordWidth bits)) {
          cache(activeSet)(activeWay).valid := True
          cache(activeSet)(activeWay).dirty := False
          cache(activeSet)(activeWay).tag := activeTag
          replacePtr(activeSet) := activeWay + 1
          when(activeCmd.write) {
            cache(activeSet)(activeWay).data(activeWord) := mergeBytes(
              Mux(activeWord === beat, axi.r.data, missLoadData),
              activeCmd.data,
              activeCmd.mask
            )
            cache(activeSet)(activeWay).dirty := True
          } otherwise {
            rspData := Mux(activeWord === beat, axi.r.data, missLoadData)
            rspId := activeCmd.id
            rspValid := True
          }
          state := idle
        } otherwise {
          beat := beat + 1
          state := refillReq
        }
      }
    }
  }
}
