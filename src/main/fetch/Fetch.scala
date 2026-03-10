package borb.fetch

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba4.axi._
import spinal.lib.misc.pipeline._

import borb.common.Common._
import borb.fetch.PC
import borb.frontend.Decoder.INSTRUCTION
import borb.frontend.RVC

object Fetch extends AreaObject {
  val addressWidth = 64
  val FETCH_SEQ = Payload(UInt(32 bits))
}

case class Fetch(
  cmdStage: CtrlLink,
  rspStage: CtrlLink,
  addressWidth: Int,
  dataWidth: Int,
  idWidth: Int = 16,
  withCompressed: Boolean = false,
  fetchBufferDepth: Int = 8,
  xlen: Int = 64
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
    val resetQueue = Bool()
  }

  case class FetchBeat() extends Bundle {
    val valid = Bool()
    val data = Bits(dataWidth bits)
    val epoch = UInt(16 bits)
    val beatAddr = UInt(addressWidth bits)
  }

  val fetchPacketDepth = 4
  case class FetchPacket() extends Bundle {
    val valid = Bool()
    val pc = UInt(addressWidth bits)
    val insn = Bits(32 bits)
    val epoch = UInt(16 bits)
    val beatAddr = UInt(addressWidth bits)
    val step = UInt(3 bits)
    val seq = UInt(32 bits)
  }

  val beats = Vec.fill(fetchBufferDepth)(Reg(FetchBeat()) init(FetchBeat().getZero))
  val packet = Reg(FetchPacket()) init(FetchPacket().getZero)
  val pendingReqValid = RegInit(False)
  val pendingReq = Reg(FetchRequest()) init(FetchRequest().getZero)
  val queueHead = Reg(UInt(log2Up(fetchBufferDepth) bits)) init(0)
  val queueCount = Reg(UInt(log2Up(fetchBufferDepth + 1) bits)) init(0)
  val packetValid = RegInit(False)
  val streamNextValid = RegInit(False)
  val streamNextAddr = Reg(UInt(addressWidth bits)) init(0)
  val compressedNextReqValid = RegInit(False)
  val compressedNextReqAddr = Reg(UInt(addressWidth bits)) init(0)
  val packetEnqueue = Bool()
  val packetEnqueuePc = UInt(addressWidth bits)
  val packetEnqueueInsn = Bits(32 bits)
  val packetEnqueueEpoch = UInt(16 bits)
  val packetEnqueueBeatAddr = UInt(addressWidth bits)
  val packetEnqueueStep = UInt(3 bits)
  val packetEnqueueSeq = UInt(32 bits)
  val packetPop = Bool()
  val packetAccepted = Bool()

  packetEnqueue.allowOverride := False
  packetEnqueuePc.allowOverride := 0
  packetEnqueueInsn.allowOverride := 0
  packetEnqueueEpoch.allowOverride := 0
  packetEnqueueBeatAddr.allowOverride := 0
  packetEnqueueStep.allowOverride := 0
  packetEnqueueSeq.allowOverride := 0
  packetPop.allowOverride := False
  packetAccepted.allowOverride := False

  val nextPacketSeq = Reg(UInt(32 bits)) init(0)

  val inflight = UInt(4 bits)
  inflight := pendingReqValid.asUInt.resize(4)
  val beatValid = queueCount =/= 0
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
  perfBeat0Valid := queueCount =/= 0
  perfBeat1Valid := queueCount > 1
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
    packet.valid := False
    pendingReqValid := False
    replayGuardValid := False
    queueHead := 0
    queueCount := 0
    streamNextValid := False
    streamNextAddr := 0
    compressedNextReqValid := False
    compressedNextReqAddr := 0
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

  def wrapIndex(base: UInt, offset: UInt): UInt = {
    val sum = UInt((log2Up(fetchBufferDepth) + 1) bits)
    sum := base.resize(sum.getWidth) + offset.resize(sum.getWidth)
    val wrapped = UInt(log2Up(fetchBufferDepth) bits)
    if(isPow2(fetchBufferDepth)) {
      wrapped := sum(log2Up(fetchBufferDepth) - 1 downto 0)
    } else {
      wrapped := (sum >= fetchBufferDepth) ? (sum - fetchBufferDepth).resized | sum.resized
    }
    wrapped
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

  def anyResident(addr: UInt): Bool = hitVec(addr).orR

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
    val curRvc = RVC(first16, xlen = xlen)
    val curIsCompressed = if(withCompressed) {
      curHit && (first16(1 downto 0) =/= B"11")
    } else {
      False
    }
    val curDecodedOpcode = curRvc.inst(6 downto 0)
    val curCompressedControl = if(withCompressed) {
      curIsCompressed &&
      !curRvc.illegal &&
      ((curDecodedOpcode === B"7'b1101111") || // JAL
        (curDecodedOpcode === B"7'b1100111") || // JALR
        (curDecodedOpcode === B"7'b1100011") || // BRANCH
        (curDecodedOpcode === B"7'b1110011"))   // EBREAK/SYSTEM
    } else {
      False
    }
    val needStraddleBeat = if(withCompressed) {
      curHit && (hwIndex === U(3)) && (first16(1 downto 0) === B"11")
    } else {
      False
    }

    val assembleBeatAddr = pcBeatAddr
    val assembleHits = curHits
    val assembleCurValid = curHit
    val assembleCurData = curData
    val assembleSrcEpoch = selectBeatEpoch(assembleHits)
    val assembleSrcBeatAddr = selectBeatAddr(assembleHits)
    val assembleNextBeatAddr = nextBeatAddr
    val assembleNextHits = nextHits
    val assembleNextValid = nextHit
    val assembleNextData = selectBeatData(assembleNextHits)
    val assembleHwIndex = hwIndex
    val assembleFirst16 = first16
    val assembleNeeds32 = if(withCompressed) assembleFirst16(1 downto 0) === B"11" else True
    val assembleStraddle = assembleNeeds32 && (assembleHwIndex === U(3))
    val waitingSecond = if(withCompressed) assembleCurValid && assembleStraddle && !assembleNextValid else False

    val assembledInsn = Bits(32 bits)
    assembledInsn := B"32'h00000013"
    if(withCompressed) {
      when(!assembleNeeds32) {
        assembledInsn := B"16'h0000" ## assembleFirst16
      } otherwise {
        switch(assembleHwIndex) {
          is(U(0)) { assembledInsn := assembleCurData(31 downto 16) ## assembleCurData(15 downto 0) }
          is(U(1)) { assembledInsn := assembleCurData(47 downto 32) ## assembleCurData(31 downto 16) }
          is(U(2)) { assembledInsn := assembleCurData(63 downto 48) ## assembleCurData(47 downto 32) }
          default { assembledInsn := assembleNextData(15 downto 0) ## assembleCurData(63 downto 48) }
        }
      }
    } else {
      assembledInsn := Mux(cmdStage(PC.PC)(2), assembleCurData(63 downto 32), assembleCurData(31 downto 0))
    }

    val packetHasSpace = !packetValid || packetAccepted
    val duplicatePc = assembleCurValid &&
      replayGuardValid &&
      (cmdStage(PC.PC) === lastTakenPc) &&
      (assembleSrcEpoch === lastTakenEpoch) &&
      (assembleSrcBeatAddr === lastTakenBeatAddr)
    when(replayGuardValid && (cmdStage(PC.PC) =/= lastTakenPc)) {
      replayGuardValid := False
    }

    val takenStep = UInt(addressWidth bits)
    if(withCompressed) {
      takenStep := assembleNeeds32 ? U(4, addressWidth bits) | U(2, addressWidth bits)
    } else {
      takenStep := U(4, addressWidth bits)
    }
    val packetReady = assembleCurValid && !waitingSecond
    val canEnqueuePacket = packetReady && !duplicatePc && packetHasSpace

    val issueAddr = UInt(addressWidth bits)
    issueAddr := pcBeatAddr
    val hasFreeSlot = queueCount =/= fetchBufferDepth
    val queueTailIndex = wrapIndex(queueHead, queueCount.resized)
    val pendingSameAddr = pendingReqValid && (pendingReq.baseAddr === issueAddr)
    val queueHeadBeatAddr = UInt(addressWidth bits)
    queueHeadBeatAddr := pcBeatAddr
    when(queueCount =/= 0) {
      queueHeadBeatAddr := beats(queueHead).beatAddr
    }
    val streamHits = hitVec(streamNextAddr)
    val streamHit = streamHits.orR
    val issueSlotIndex = UInt(log2Up(fetchBufferDepth) bits)
    issueSlotIndex := 0
    val resetQueueOnRsp = Bool()
    resetQueueOnRsp := False

    val needCurrentRequest = cmdStage.up.isValid && !curHit && !pendingReqValid && !io.flush
    val needNextRequest = curHit && needStraddleBeat && !nextHit && !pendingReqValid && !io.flush && hasFreeSlot
    val compressedPrefetchWindow = curIsCompressed &&
      !curRvc.illegal &&
      !curCompressedControl &&
      (hwIndex === U(2) || hwIndex === U(3)) &&
      !nextHit &&
      !needStraddleBeat &&
      hasFreeSlot &&
      !io.flush
    val compressedPrefetchTracked = compressedNextReqValid && (compressedNextReqAddr === nextBeatAddr)
    val streamPrefetchWindow = streamNextValid &&
      hasFreeSlot &&
      curHit &&
      !curIsCompressed &&
      !io.flush
    val useCompressedPrefetch = compressedPrefetchWindow &&
      !pendingReqValid &&
      !needCurrentRequest &&
      !needNextRequest &&
      !compressedPrefetchTracked
    val useStreamPrefetch = streamPrefetchWindow &&
      !pendingReqValid &&
      !needCurrentRequest &&
      !needNextRequest &&
      !streamHit
    val prefetchWindow = compressedPrefetchWindow || streamPrefetchWindow
    val usePrefetch = useCompressedPrefetch || useStreamPrefetch

    when(needCurrentRequest) {
      issueAddr := pcBeatAddr
      issueSlotIndex := 0
      resetQueueOnRsp := True
    }

    when(needNextRequest || usePrefetch) {
      issueAddr := useCompressedPrefetch ? nextBeatAddr | streamNextAddr
      issueSlotIndex := queueTailIndex
    }

    val issueResident = anyResident(issueAddr)
    val issuePending = pendingReqValid && (pendingReq.baseAddr === issueAddr)
    val needRequest = (needCurrentRequest || needNextRequest || usePrefetch) && !issueResident && !issuePending

    perfNeedCurrentReq.allowOverride := needCurrentRequest
    perfNeedNextReq.allowOverride := needNextRequest
    perfPrefetchReq.allowOverride := usePrefetch
    perfCurBeatHit.allowOverride := curHit
    perfNextBeatHit.allowOverride := nextHit
    perfWaitCurBeat.allowOverride := !assembleCurValid
    perfWaitNextBeat.allowOverride := waitingSecond
    perfCmdValid.allowOverride := cmdStage.up.isValid
    perfPrefetchWindow.allowOverride := prefetchWindow
    perfPrefetchBlockedNoCmd.allowOverride := False
    perfPrefetchBlockedPending.allowOverride := prefetchWindow && pendingReqValid
    perfPrefetchBlockedNextHit.allowOverride := streamPrefetchWindow && streamHit && !io.flush

    io.iAxi.arw.valid.allowOverride := needRequest
    io.iAxi.arw.addr.allowOverride := issueAddr
    io.iAxi.arw.id.allowOverride := activeEpoch.resized
    io.iAxi.arw.len.allowOverride := 0

    val packetQueueBlocks = packetReady && !duplicatePc && !packetHasSpace
    haltWhen((needCurrentRequest || needNextRequest) && !io.iAxi.arw.fire)
    haltWhen(packetQueueBlocks)

    when(io.iAxi.arw.fire) {
      perfReqIssued := True
      pendingReqValid := True
      pendingReq.baseAddr := issueAddr
      pendingReq.epoch := activeEpoch
      pendingReq.slotIndex := issueSlotIndex
      pendingReq.resetQueue := resetQueueOnRsp
      when(useCompressedPrefetch) {
        compressedNextReqValid := True
        compressedNextReqAddr := nextBeatAddr
      }
      when(useStreamPrefetch) {
        streamNextAddr := streamNextAddr + U(8, addressWidth bits)
      }
    }

    when(canEnqueuePacket) {
      packetEnqueue.allowOverride := True
      packetEnqueuePc.allowOverride := cmdStage(PC.PC)
      packetEnqueueInsn.allowOverride := assembledInsn
      packetEnqueueEpoch.allowOverride := assembleSrcEpoch
      packetEnqueueBeatAddr.allowOverride := assembleSrcBeatAddr
      packetEnqueueStep.allowOverride := takenStep.resize(3)
      packetEnqueueSeq.allowOverride := nextPacketSeq

      perfTakeInsn := True
      replayGuardValid := True
      lastTakenPc := cmdStage(PC.PC)
      lastTakenEpoch := assembleSrcEpoch
      lastTakenBeatAddr := assembleSrcBeatAddr
      io.pcAdvance := True
      io.pcStep := takenStep.resize(3)
      nextPacketSeq := nextPacketSeq + 1

      val nextPc = cmdStage(PC.PC) + takenStep
      val nextPcBeatAddr = archAddr(nextPc)
      nextPcBeatAddr(2 downto 0) := 0
      when(compressedNextReqValid && (compressedNextReqAddr < nextPcBeatAddr)) {
        compressedNextReqValid := False
      }
      when(queueCount =/= 0 && beats(queueHead).valid && (beats(queueHead).epoch === assembleSrcEpoch) && (beats(queueHead).beatAddr < nextPcBeatAddr)) {
        beats(queueHead).valid := False
        queueHead := wrapIndex(queueHead, U(1))
        queueCount := queueCount - 1
      }
    }
  }

  val rspArea = new rspStage.Area {
    for(i <- 0 until fetchBufferDepth) {
      when(beats(i).valid && (beats(i).epoch =/= activeEpoch)) {
        beats(i).valid := False
      }
    }
    val headPacket = packet

    rspStage.down(PC.PC).allowOverride := headPacket.pc
    rspStage.down(INSTRUCTION).allowOverride := headPacket.insn
    rspStage.down(SPEC_EPOCH).allowOverride := headPacket.epoch
    rspStage.down(Fetch.FETCH_SEQ).allowOverride := headPacket.seq

    rspStage.up.valid := packetValid
    packetAccepted.allowOverride := packetValid && rspStage.up.isFiring
    packetPop.allowOverride := packetAccepted
  }
  when(packetEnqueue) {
    packet.valid := True
    packet.pc := packetEnqueuePc
    packet.insn := packetEnqueueInsn
    packet.epoch := packetEnqueueEpoch
    packet.beatAddr := packetEnqueueBeatAddr
    packet.step := packetEnqueueStep
    packet.seq := packetEnqueueSeq
  }
  packetValid := Mux(io.flush, False, (packetValid && !packetPop) || packetEnqueue)

  when(io.iAxi.r.fire) {
    perfRspAccepted := True
    val rspResident = anyResident(pendingReq.baseAddr)
    when(pendingReq.resetQueue) {
      for(slot <- beats) {
        slot.valid := False
      }
      queueHead := 0
      queueCount := 1
      streamNextValid := True
      streamNextAddr := pendingReq.baseAddr + U(8, addressWidth bits)
      compressedNextReqValid := False
    } otherwise {
      when(!rspResident && queueCount =/= fetchBufferDepth) {
        queueCount := queueCount + 1
      }
      streamNextValid := True
      streamNextAddr := pendingReq.baseAddr + U(8, addressWidth bits)
    }
    when(compressedNextReqValid && (compressedNextReqAddr === pendingReq.baseAddr)) {
      compressedNextReqValid := False
    }
    when(!rspResident || pendingReq.resetQueue) {
      beats(pendingReq.slotIndex).valid := True
      beats(pendingReq.slotIndex).data := io.iAxi.r.data
      beats(pendingReq.slotIndex).epoch := pendingReq.epoch
      beats(pendingReq.slotIndex).beatAddr := pendingReq.baseAddr
    }
    pendingReqValid := False
  }

  io.iAxi.r.ready.allowOverride := pendingReqValid
}
