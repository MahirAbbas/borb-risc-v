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
  withCompressed: Boolean = false,
  fetchBufferDepth: Int = 8
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
    val slotIndex = UInt(log2Up(fetchBufferDepth) bits)
  }

  case class FetchBeat() extends Bundle {
    val valid = Bool()
    val data = Bits(dataWidth bits)
    val epoch = UInt(16 bits)
    val beatAddr = UInt(addressWidth bits)
  }

  val beats = Vec.fill(fetchBufferDepth)(Reg(FetchBeat()) init(FetchBeat().getZero))
  val pendingReqValid = RegInit(False)
  val pendingReq = Reg(FetchRequest()) init(FetchRequest().getZero)

  val inflight = UInt(4 bits)
  inflight := pendingReqValid.asUInt.resize(4)
  val beatValid = beats.map(_.valid).orR
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

  perfPendingReq := pendingReqValid
  perfBeat0Valid := beats(0).valid
  perfBeat1Valid := beats.lift(1).map(_.valid).getOrElse(False)
  perfReqIssued := False
  perfRspAccepted := False
  perfNeedCurrentReq := False
  perfNeedNextReq := False
  perfPrefetchReq := False
  perfWaitCurBeat := False
  perfWaitNextBeat := False
  perfTakeInsn := False
  perfCurBeatHit := False
  perfNextBeatHit := False
  perfCmdValid := False
  perfPrefetchWindow := False
  perfPrefetchBlockedNoCmd := False
  perfPrefetchBlockedPending := False
  perfPrefetchBlockedNextHit := False

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
    for(slot <- beats) {
      slot.valid := False
    }
    pendingReqValid := False
    replayGuardValid := False
  }

  def beatHit(slot: FetchBeat, addr: UInt): Bool = {
    slot.valid && (slot.epoch === activeEpoch) && (slot.beatAddr === addr)
  }

  def hitVec(addr: UInt): Bits = {
    val hits = Bits(fetchBufferDepth bits)
    for(i <- 0 until fetchBufferDepth) {
      hits(i) := beatHit(beats(i), addr)
    }
    hits
  }

  def selectBeatData(hits: Bits): Bits = {
    val data = Bits(dataWidth bits)
    data := beats(0).data
    for(i <- 0 until fetchBufferDepth) {
      when(hits(i)) {
        data := beats(i).data
      }
    }
    data
  }

  def selectBeatEpoch(hits: Bits): UInt = {
    val epoch = UInt(16 bits)
    epoch := beats(0).epoch
    for(i <- 0 until fetchBufferDepth) {
      when(hits(i)) {
        epoch := beats(i).epoch
      }
    }
    epoch
  }

  def selectBeatAddr(hits: Bits): UInt = {
    val addr = UInt(addressWidth bits)
    addr := beats(0).beatAddr
    for(i <- 0 until fetchBufferDepth) {
      when(hits(i)) {
        addr := beats(i).beatAddr
      }
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

    val curHits = hitVec(pcBeatAddr)
    val curHit = curHits.orR
    val curData = selectBeatData(curHits)

    val nextBeatAddr = pcBeatAddr + U(8, addressWidth bits)
    val nextHits = hitVec(nextBeatAddr)
    val nextHit = nextHits.orR

    val hwIndex = cmdStage(PC.PC)(2 downto 1)
    val first16 = selectHalfword(curData, hwIndex)
    val needStraddleBeat = if(withCompressed) {
      curHit && (hwIndex === U(3)) && (first16(1 downto 0) === B"11")
    } else {
      False
    }

    val issueAddr = UInt(addressWidth bits)
    issueAddr := pcBeatAddr
    val freeVec = Bits(fetchBufferDepth bits)
    for(i <- 0 until fetchBufferDepth) {
      freeVec(i) := !beats(i).valid || (beats(i).epoch =/= activeEpoch)
    }
    val hasFreeSlot = freeVec.orR
    val firstFreeOh = OHMasking.first(freeVec)
    val firstFreeIndex = OHToUInt(firstFreeOh)

    val furthestAddr = UInt(addressWidth bits)
    var furthestExpr = pcBeatAddr
    for(i <- 0 until fetchBufferDepth) {
      val slotAddr = beats(i).beatAddr
      val slotIsFurthest = beats(i).valid && (beats(i).epoch === activeEpoch) && (slotAddr > furthestExpr)
      furthestExpr = Mux(slotIsFurthest, slotAddr, furthestExpr)
    }
    furthestAddr := furthestExpr
    val prefetchAddr = furthestAddr + U(8, addressWidth bits)
    val prefetchHits = hitVec(prefetchAddr)
    val prefetchHit = prefetchHits.orR
    val issueSlotIndex = UInt(log2Up(fetchBufferDepth) bits)
    issueSlotIndex := firstFreeIndex

    val needCurrentRequest = cmdStage.up.isValid && !curHit && !pendingReqValid && !io.flush && hasFreeSlot
    val needNextRequest = curHit && needStraddleBeat && !nextHit && !pendingReqValid && !io.flush && hasFreeSlot
    val prefetchWindow = if(withCompressed) {
      False
    } else {
      curHit && hasFreeSlot && !prefetchHit && !io.flush
    }
    val usePrefetch = if(withCompressed) {
      False
    } else {
      prefetchWindow && !pendingReqValid
    }

    when(needNextRequest || usePrefetch) {
      issueAddr := usePrefetch ? prefetchAddr | nextBeatAddr
    }

    val needRequest = needCurrentRequest || needNextRequest || usePrefetch

    perfNeedCurrentReq.allowOverride := needCurrentRequest
    perfNeedNextReq.allowOverride := needNextRequest
    perfPrefetchReq.allowOverride := usePrefetch
    perfCurBeatHit.allowOverride := curHit
    perfNextBeatHit.allowOverride := nextHit
    perfCmdValid.allowOverride := cmdStage.up.isValid
    perfPrefetchWindow.allowOverride := prefetchWindow
    perfPrefetchBlockedNoCmd.allowOverride := prefetchWindow && !cmdStage.up.isValid
    perfPrefetchBlockedPending.allowOverride := prefetchWindow && pendingReqValid
    perfPrefetchBlockedNextHit.allowOverride := (if(withCompressed) False else curHit && prefetchHit && !io.flush)

    io.iAxi.arw.valid.allowOverride := needRequest
    io.iAxi.arw.addr.allowOverride := issueAddr
    io.iAxi.arw.id.allowOverride := activeEpoch.resized
    io.iAxi.arw.len.allowOverride := 0

    haltWhen((needCurrentRequest || needNextRequest) && !io.iAxi.arw.fire)

    when(io.iAxi.arw.fire) {
      perfReqIssued := True
      pendingReqValid := True
      pendingReq.baseAddr := issueAddr
      pendingReq.epoch := activeEpoch
      pendingReq.slotIndex := issueSlotIndex
    }
  }

  val rspArea = new rspStage.Area {
    val pcBeatAddr = UInt(addressWidth bits)
    pcBeatAddr := archAddr(rspStage(PC.PC))
    pcBeatAddr(2 downto 0) := 0

    for(slot <- beats) {
      when(slot.valid && (slot.epoch =/= activeEpoch)) {
        slot.valid := False
      }
    }

    val curHits = hitVec(pcBeatAddr)
    val curValid = curHits.orR
    val curData = selectBeatData(curHits)
    val srcEpoch = selectBeatEpoch(curHits)
    val srcBeatAddr = selectBeatAddr(curHits)

    val nextBeatAddr = pcBeatAddr + U(8, addressWidth bits)
    val nextHits = hitVec(nextBeatAddr)
    val nextValid = nextHits.orR
    val nextData = selectBeatData(nextHits)

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
    perfWaitCurBeat.allowOverride := !curValid
    perfWaitNextBeat.allowOverride := waitingSecond
    haltWhen(!curValid || waitingSecond)

    val takeInsn = rspStage.down.isFiring && curValid && !waitingSecond
    val takenStep = UInt(addressWidth bits)
    if(withCompressed) {
      val isCompressed = assembledInsn(1 downto 0) =/= B"11"
      takenStep := isCompressed ? U(2, addressWidth bits) | U(4, addressWidth bits)
    } else {
      takenStep := U(4, addressWidth bits)
    }

    when(takeInsn) {
      perfTakeInsn := True
      replayGuardValid := True
      lastTakenPc := rspStage(PC.PC)
      lastTakenEpoch := srcEpoch
      lastTakenBeatAddr := srcBeatAddr
      io.pcAdvance := True
      io.pcStep := takenStep.resize(3)

      val nextPc = rspStage(PC.PC) + takenStep
      val nextPcBeatAddr = archAddr(nextPc)
      nextPcBeatAddr(2 downto 0) := 0
      for(slot <- beats) {
        when(slot.valid && (slot.epoch === srcEpoch) && (slot.beatAddr < nextPcBeatAddr)) {
          slot.valid := False
        }
      }
    }
  }

  when(io.iAxi.r.fire) {
    perfRspAccepted := True
    beats(pendingReq.slotIndex).valid := True
    beats(pendingReq.slotIndex).data := io.iAxi.r.data
    beats(pendingReq.slotIndex).epoch := pendingReq.epoch
    beats(pendingReq.slotIndex).beatAddr := pendingReq.baseAddr
    pendingReqValid := False
  }

  io.iAxi.r.ready.allowOverride := pendingReqValid
}
