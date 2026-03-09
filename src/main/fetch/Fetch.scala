package borb.fetch

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba4.axi._
import spinal.lib.misc.pipeline._

import borb.common.Common._
import borb.fetch.PC
import borb.frontend.Decoder.INSTRUCTION

object Fetch extends AreaObject {
  val addressWidth = 64
}

case class Fetch(
  cmdStage: CtrlLink,
  rspStage: CtrlLink,
  addressWidth: Int,
  dataWidth: Int,
  idWidth: Int = 16,
  withCompressed: Boolean = false
) extends Area {
  import Fetch._

  val ARCH_BASE = U(BigInt("80000000", 16), addressWidth bits)
  def archAddr(addr: UInt): UInt = Mux(addr < ARCH_BASE, addr + ARCH_BASE, addr)

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
    val flush = Bool()
    val currentEpoch = UInt(16 bits)
    val pcAdvance = Bool()
    val pcStep = UInt(3 bits)
  }

  case class FetchRequest() extends Bundle {
    val baseAddr = UInt(addressWidth bits)
    val epoch = UInt(16 bits)
    val toNextSlot = Bool()
  }

  case class FetchBeat() extends Bundle {
    val valid = Bool()
    val data = Bits(dataWidth bits)
    val epoch = UInt(16 bits)
    val beatAddr = UInt(addressWidth bits)
  }

  val beat0 = Reg(FetchBeat()) init(FetchBeat().getZero)
  val beat1 = Reg(FetchBeat()) init(FetchBeat().getZero)
  val pendingReqValid = RegInit(False)
  val pendingReq = Reg(FetchRequest()) init(FetchRequest().getZero)

  val inflight = UInt(4 bits)
  inflight := pendingReqValid.asUInt.resize(4)
  val beatValid = beat0.valid || beat1.valid

  io.pcAdvance := False
  io.pcStep := U(4, 3 bits)

  io.iAxi.arw.valid := False
  io.iAxi.arw.addr := 0
  io.iAxi.arw.id := 0
  io.iAxi.arw.len := 0
  io.iAxi.arw.size := log2Up(dataWidth / 8)
  io.iAxi.arw.burst := Axi4.burst.INCR
  io.iAxi.arw.write := False
  io.iAxi.w.valid := False
  io.iAxi.w.data := 0
  io.iAxi.w.strb := 0
  io.iAxi.w.last := False
  io.iAxi.b.ready := True
  io.iAxi.r.ready.allowOverride := False

  val epoch = UInt(16 bits)
  epoch := io.currentEpoch + io.flush.asUInt.resize(16)
  val activeEpoch = epoch

  val replayGuardValid = RegInit(False)
  val lastTakenPc = Reg(UInt(addressWidth bits)) init(0)
  val lastTakenEpoch = Reg(UInt(16 bits)) init(0)
  val lastTakenBeatAddr = Reg(UInt(addressWidth bits)) init(0)

  when(io.flush) {
    beat0.valid := False
    beat1.valid := False
    pendingReqValid := False
    replayGuardValid := False
  }

  def beatHit(slot: FetchBeat, addr: UInt): Bool = {
    slot.valid && (slot.epoch === activeEpoch) && (slot.beatAddr === addr)
  }

  def selectBeatData(hit0: Bool, hit1: Bool): Bits = {
    val data = Bits(dataWidth bits)
    data := beat1.data
    when(hit0) {
      data := beat0.data
    }
    data
  }

  def selectBeatEpoch(hit0: Bool): UInt = {
    val epoch = UInt(16 bits)
    epoch := beat1.epoch
    when(hit0) {
      epoch := beat0.epoch
    }
    epoch
  }

  def selectBeatAddr(hit0: Bool): UInt = {
    val addr = UInt(addressWidth bits)
    addr := beat1.beatAddr
    when(hit0) {
      addr := beat0.beatAddr
    }
    addr
  }

  def selectHalfword(data: Bits, index: UInt): Bits = {
    index.mux(
      U(0) -> data(15 downto 0),
      U(1) -> data(31 downto 16),
      U(2) -> data(47 downto 32),
      default -> data(63 downto 48)
    )
  }

  val cmdArea = new cmdStage.Area {
    val pcBeatAddr = UInt(addressWidth bits)
    pcBeatAddr := archAddr(cmdStage(PC.PC))
    pcBeatAddr(2 downto 0) := 0

    val curBeat0Hit = beatHit(beat0, pcBeatAddr)
    val curBeat1Hit = beatHit(beat1, pcBeatAddr)
    val curHit = curBeat0Hit || curBeat1Hit
    val curData = selectBeatData(curBeat0Hit, curBeat1Hit)

    val nextBeatAddr = pcBeatAddr + U(8, addressWidth bits)
    val nextHit = beatHit(beat0, nextBeatAddr) || beatHit(beat1, nextBeatAddr)

    val hwIndex = cmdStage(PC.PC)(2 downto 1)
    val first16 = selectHalfword(curData, hwIndex)
    val needStraddleBeat = if(withCompressed) {
      curHit && (hwIndex === U(3)) && (first16(1 downto 0) === B"11")
    } else {
      False
    }

    val canPrefetch = if(withCompressed) {
      False
    } else {
      curHit && !pendingReqValid && !nextHit
    }

    val issueAddr = UInt(addressWidth bits)
    issueAddr := pcBeatAddr
    val issueToNextSlot = Bool()
    issueToNextSlot := False

    val needCurrentRequest = cmdStage.up.isValid && !curHit && !pendingReqValid && !io.flush
    val needNextRequest = curHit && needStraddleBeat && !nextHit && !pendingReqValid && !io.flush
    val usePrefetch = canPrefetch && cmdStage.up.isValid && !cmdStage(PC.PC)(2)

    when(needNextRequest || usePrefetch) {
      issueAddr := nextBeatAddr
      issueToNextSlot := True
    }

    val needRequest = needCurrentRequest || needNextRequest || usePrefetch

    io.iAxi.arw.valid.allowOverride := needRequest
    io.iAxi.arw.addr.allowOverride := issueAddr
    io.iAxi.arw.id.allowOverride := activeEpoch.resized
    io.iAxi.arw.len.allowOverride := 0

    haltWhen((needCurrentRequest || needNextRequest) && !io.iAxi.arw.fire)

    when(io.iAxi.arw.fire) {
      pendingReqValid := True
      pendingReq.baseAddr := issueAddr
      pendingReq.epoch := activeEpoch
      pendingReq.toNextSlot := issueToNextSlot
    }
  }

  val rspArea = new rspStage.Area {
    val pcBeatAddr = UInt(addressWidth bits)
    pcBeatAddr := archAddr(rspStage(PC.PC))
    pcBeatAddr(2 downto 0) := 0

    val curBeat0Hit = beatHit(beat0, pcBeatAddr)
    val curBeat1Hit = beatHit(beat1, pcBeatAddr)
    val curValid = curBeat0Hit || curBeat1Hit
    val curData = selectBeatData(curBeat0Hit, curBeat1Hit)
    val srcEpoch = selectBeatEpoch(curBeat0Hit)
    val srcBeatAddr = selectBeatAddr(curBeat0Hit)

    val nextBeatAddr = pcBeatAddr + U(8, addressWidth bits)
    val nextBeat0Hit = beatHit(beat0, nextBeatAddr)
    val nextBeat1Hit = beatHit(beat1, nextBeatAddr)
    val nextValid = nextBeat0Hit || nextBeat1Hit
    val nextData = selectBeatData(nextBeat0Hit, nextBeat1Hit)

    val staleBeat0 = beat0.valid && (beat0.epoch =/= activeEpoch)
    val staleBeat1 = beat1.valid && (beat1.epoch =/= activeEpoch)
    when(staleBeat0) {
      beat0.valid := False
    }
    when(staleBeat1) {
      beat1.valid := False
    }

    val duplicatePc = curValid &&
      replayGuardValid &&
      (rspStage(PC.PC) === lastTakenPc) &&
      (srcEpoch === lastTakenEpoch) &&
      (srcBeatAddr === lastTakenBeatAddr)

    when(replayGuardValid && (rspStage(PC.PC) =/= lastTakenPc)) {
      replayGuardValid := False
    }

    throwWhen(duplicatePc)

    val hwIndex = rspStage(PC.PC)(2 downto 1)
    val first16 = selectHalfword(curData, hwIndex)
    val needs32 = if(withCompressed) first16(1 downto 0) === B"11" else True
    val straddle = needs32 && (hwIndex === U(3))

    val assembledInsn = Bits(32 bits)
    assembledInsn := B"32'h00000013"
    if(withCompressed) {
      when(!needs32) {
        assembledInsn := B"16'h0000" ## first16
      } otherwise {
        switch(hwIndex) {
          is(U(0)) { assembledInsn := curData(31 downto 16) ## curData(15 downto 0) }
          is(U(1)) { assembledInsn := curData(47 downto 32) ## curData(31 downto 16) }
          is(U(2)) { assembledInsn := curData(63 downto 48) ## curData(47 downto 32) }
          default { assembledInsn := nextData(15 downto 0) ## curData(63 downto 48) }
        }
      }
    } else {
      assembledInsn := Mux(rspStage(PC.PC)(2), curData(63 downto 32), curData(31 downto 0))
    }

    rspStage.down(INSTRUCTION) := assembledInsn
    rspStage.down(SPEC_EPOCH) := srcEpoch

    val waitingSecond = if(withCompressed) straddle && !nextValid else False
    haltWhen(!curValid || waitingSecond)

    val takeInsn = rspStage.down.isFiring && curValid && !waitingSecond
    val takenStep = UInt(addressWidth bits)
    if(withCompressed) {
      val isCompressed = assembledInsn(1 downto 0) =/= B"11"
      takenStep := isCompressed ? U(2, addressWidth bits) | U(4, addressWidth bits)
    } else {
      takenStep := U(4, addressWidth bits)
    }

    when(curValid && curBeat1Hit && !curBeat0Hit) {
      beat0.valid := beat1.valid
      beat0.data := beat1.data
      beat0.epoch := beat1.epoch
      beat0.beatAddr := beat1.beatAddr
    }

    when(takeInsn) {
      replayGuardValid := True
      lastTakenPc := rspStage(PC.PC)
      lastTakenEpoch := srcEpoch
      lastTakenBeatAddr := srcBeatAddr
      io.pcAdvance := True
      io.pcStep := takenStep.resize(3)
    }
  }

  when(io.iAxi.r.fire) {
    when(pendingReq.toNextSlot) {
      beat1.valid := True
      beat1.data := io.iAxi.r.data
      beat1.epoch := pendingReq.epoch
      beat1.beatAddr := pendingReq.baseAddr
    } otherwise {
      beat0.valid := True
      beat0.data := io.iAxi.r.data
      beat0.epoch := pendingReq.epoch
      beat0.beatAddr := pendingReq.baseAddr
    }
    pendingReqValid := False
  }

  io.iAxi.r.ready.allowOverride := pendingReqValid
}
