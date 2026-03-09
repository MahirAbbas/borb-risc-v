package borb.fetch

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import spinal.lib.bus.amba4.axi._

import borb.fetch.PC
import borb.frontend.Decoder.INSTRUCTION
import borb.common.Common._
import spinal.core.sim._

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
    val currentEpoch = UInt(16 bits)  // Global speculation epoch from CPU
    val pcAdvance = Bool()
    val pcStep = UInt(3 bits)
  }

  // One outstanding request in v1, but keep burst metadata so extending to
  // linefills later doesn't require a new internal contract.
  case class FetchRequest() extends Bundle {
    val baseAddr = UInt(addressWidth bits)
    val epoch = UInt(16 bits)
    val burstLen = UInt(8 bits)
    val beatIndex = UInt(8 bits)
  }

  case class FetchBeat() extends Bundle {
    val data = Bits(dataWidth bits)
    val epoch = UInt(16 bits)
    val beatAddr = UInt(addressWidth bits)
    val beatIndex = UInt(8 bits)
    val burstLast = Bool()
  }

  val fifo = StreamFifo(FetchBeat(), depth = 2)
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

  val pendingReqValid = RegInit(False)
  val pendingReq = Reg(FetchRequest()) init(FetchRequest().getZero)
  val inflight = UInt(4 bits)
  inflight := pendingReqValid.asUInt.resize(4)

  // Epoch/ID handshake:
  // - bump epoch on redirect
  // - treat current cycle as the new epoch for stale filtering
  //   so late responses from the old path are dropped immediately.
  val epoch = RegInit(U(0, 16 bits))
  when(io.flush) {
    epoch := epoch + 1
  }
  val activeEpoch = epoch + U(io.flush)

  // Cross-beat assembly state for 32-bit instruction starting at byte offset 6.
  val needSecondBeat = Reg(Bool()) init(False)
  val secondBeatAddr = Reg(UInt(addressWidth bits)) init(0)
  val firstHalfword = Reg(Bits(16 bits)) init(0)
  val replayGuardValid = RegInit(False)
  val lastTakenPc = Reg(UInt(addressWidth bits)) init(0)
  val lastTakenEpoch = Reg(UInt(16 bits)) init(0)
  val lastTakenBeatAddr = Reg(UInt(addressWidth bits)) init(0)

  when(io.flush) {
    needSecondBeat := False
    replayGuardValid := False
  }

  val beatBytes = dataWidth / 8
  val rspBeatAddr = UInt(addressWidth bits)
  rspBeatAddr := (pendingReq.baseAddr + (pendingReq.beatIndex.resize(addressWidth bits) << log2Up(beatBytes))).resized
  fifo.io.push.valid := io.iAxi.r.valid && pendingReqValid
  fifo.io.push.payload.data := io.iAxi.r.data
  fifo.io.push.payload.epoch := pendingReq.epoch
  fifo.io.push.payload.beatAddr := rspBeatAddr
  fifo.io.push.payload.beatIndex := pendingReq.beatIndex
  fifo.io.push.payload.burstLast := io.iAxi.r.last
  io.iAxi.r.ready := fifo.io.push.ready && pendingReqValid
  fifo.io.flush := io.flush

  when(io.iAxi.r.fire) {
    when(io.iAxi.r.last) {
      pendingReqValid := False
    } otherwise {
      pendingReq.beatIndex := pendingReq.beatIndex + 1
    }
  }

  val cmdArea = new cmdStage.Area {
    // 64-bit fetch beat base address
    val beatAddr = UInt(addressWidth bits)
    beatAddr := archAddr(cmdStage(PC.PC))
    beatAddr(2 downto 0) := 0

    // Correctness-first mode: always (re)request the current beat.
    // This avoids stale beat reuse and keeps PC/insn aligned across redirects.
    val reqAddr = UInt(addressWidth bits)
    reqAddr := needSecondBeat ? secondBeatAddr | beatAddr

    io.iAxi.arw.valid.allowOverride := cmdStage.up.isValid && !pendingReqValid && !io.flush
    io.iAxi.arw.addr.allowOverride := reqAddr
    io.iAxi.arw.id.allowOverride := activeEpoch.resized
    io.iAxi.arw.len.allowOverride := 0

    // Stall until request is accepted.
    haltWhen(!io.iAxi.arw.fire)

    when(io.iAxi.arw.fire) {
      pendingReqValid := True
      pendingReq.baseAddr := reqAddr
      pendingReq.epoch := activeEpoch
      pendingReq.burstLen := 0
      pendingReq.beatIndex := 0
    }
  }

  val rspArea = new rspStage.Area {
    val beatAddr = UInt(addressWidth bits)
    beatAddr := archAddr(rspStage(PC.PC))
    beatAddr(2 downto 0) := 0

    val srcValid = fifo.io.pop.valid
    val srcData = fifo.io.pop.payload.data
    val srcEpoch = fifo.io.pop.payload.epoch
    val srcBeatAddr = fifo.io.pop.payload.beatAddr
    val expectedBeat = UInt(addressWidth bits)
    expectedBeat := needSecondBeat ? secondBeatAddr | beatAddr
    val stalePacket = srcValid && (srcEpoch =/= activeEpoch)
    val beatMismatch = srcValid && (srcBeatAddr =/= expectedBeat)
    // Immediate replay guard: suppress only back-to-back re-consume of the
    // same fetch packet while rsp stage still holds the same PC.
    val duplicatePc = srcValid &&
      replayGuardValid &&
      (rspStage(PC.PC) === lastTakenPc) &&
      (srcEpoch === lastTakenEpoch) &&
      (srcBeatAddr === lastTakenBeatAddr)

    when(replayGuardValid && (rspStage(PC.PC) =/= lastTakenPc)) {
      replayGuardValid := False
    }

    // Drop stale packets and keep PC/insn in sync by throwing stage entry.
    throwWhen(stalePacket)
    throwWhen(duplicatePc)

    val hw0 = srcData(15 downto 0)
    val hw1 = srcData(31 downto 16)
    val hw2 = srcData(47 downto 32)
    val hw3 = srcData(63 downto 48)
    val hwIndex = rspStage(PC.PC)(2 downto 1)
    val first16 = hwIndex.mux(
      U(0) -> hw0,
      U(1) -> hw1,
      U(2) -> hw2,
      default -> hw3
    )
    val needs32 = first16(1 downto 0) === B"11"
    val straddle = needs32 && (hwIndex === U(3))

    val assembledInsn = Bits(32 bits)
    assembledInsn := B"32'h00000013"
    when(needSecondBeat) {
      assembledInsn := srcData(15 downto 0) ## firstHalfword
    } otherwise {
      if(withCompressed) {
        when(!needs32) {
          assembledInsn := B"16'h0000" ## first16
        } otherwise {
          switch(hwIndex) {
            is(U(0)) { assembledInsn := hw1 ## hw0 }
            is(U(1)) { assembledInsn := hw2 ## hw1 }
            is(U(2)) { assembledInsn := hw3 ## hw2 }
            default { assembledInsn := B"32'h00000013" }
          }
        }
      } else {
        assembledInsn := Mux(rspStage(PC.PC)(2), srcData(63 downto 32), srcData(31 downto 0))
      }
    }
    rspStage.down(INSTRUCTION) := assembledInsn
    
    // Preserve the fetch packet epoch so stale control-flow ops can't be
    // re-tagged as current after a redirect.
    rspStage.down(SPEC_EPOCH) := srcEpoch
    
    val waitingSecond = Bool()
    if(withCompressed) {
      waitingSecond := straddle && !needSecondBeat
    } else {
      waitingSecond := False
    }
    haltWhen((!srcValid || beatMismatch || waitingSecond) && !stalePacket)

    val takeInsn = rspStage.down.isFiring && srcValid && !stalePacket && !beatMismatch && !waitingSecond

    when(waitingSecond && srcValid && !stalePacket && !beatMismatch) {
      needSecondBeat := True
      secondBeatAddr := beatAddr + U(8, addressWidth bits)
      firstHalfword := first16
    }

    when(takeInsn) {
      replayGuardValid := True
      lastTakenPc := rspStage(PC.PC)
      lastTakenEpoch := activeEpoch
      lastTakenBeatAddr := srcBeatAddr
      io.pcAdvance := True
      if(withCompressed) {
        val isCompressed = assembledInsn(1 downto 0) =/= B"11"
        io.pcStep := isCompressed ? U(2, 3 bits) | U(4, 3 bits)
      } else {
        io.pcStep := U(4, 3 bits)
      }
      when(needSecondBeat) {
        needSecondBeat := False
      }
    }

    // Pop when consumed or when dropped due stale/mismatch/duplicate or when
    // latching first half.
    fifo.io.pop.ready := takeInsn || stalePacket || beatMismatch || waitingSecond || duplicatePc
  }
}
