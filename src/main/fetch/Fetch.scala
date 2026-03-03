package borb.fetch

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._

import borb.fetch.PC
import borb.frontend.Decoder.INSTRUCTION
import borb.memory._
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
  withCompressed: Boolean = false
) extends Area {
  import Fetch._
  val ARCH_BASE = U(BigInt("80000000", 16), addressWidth bits)
  def archAddr(addr: UInt): UInt = Mux(addr < ARCH_BASE, addr + ARCH_BASE, addr)

  val io = new Bundle {
    val readCmd = new RamFetchBus(addressWidth, dataWidth, idWidth = 16)
    val flush = Bool()
    val currentEpoch = UInt(16 bits)  // Global speculation epoch from CPU
    val pcStep = UInt(3 bits)
  }

  // Fetch Packet: instruction data + epoch tag
  case class FetchPacket() extends Bundle {
    val data = Bits(dataWidth bits)
    val epoch = UInt(16 bits)
    val beatAddr = UInt(addressWidth bits)
  }
  
  val fifo = StreamFifo(FetchPacket(), depth = 2)
  val stepReg = Reg(UInt(3 bits)) init(U(4, 3 bits))
  io.pcStep := stepReg
  
  // Track inflight requests to prevent FIFO overflow
  val inflight = RegInit(U(0, 4 bits))
  val cmdFire = io.readCmd.cmd.fire
  val rspFire = io.readCmd.rsp.valid
  inflight := inflight + U(cmdFire) - U(rspFire)

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

  when(io.flush) {
    needSecondBeat := False
    stepReg := U(4, 3 bits)
  }

  // Connect memory response to FIFO
  // Push ALL responses with their epoch tag (no filtering at push time)
  val rspEpoch = io.readCmd.rsp.id
  fifo.io.push.valid := io.readCmd.rsp.valid
  fifo.io.push.payload.data := io.readCmd.rsp.data
  fifo.io.push.payload.epoch := rspEpoch
  fifo.io.push.payload.beatAddr := io.readCmd.rsp.address
  
  // Also flush FIFO storage when io.flush is asserted
  fifo.io.flush := io.flush

  val cmdArea = new cmdStage.Area {
    // 64-bit fetch beat base address
    val beatAddr = UInt(addressWidth bits)
    beatAddr := archAddr(cmdStage(PC.PC))
    beatAddr(2 downto 0) := 0

    // Correctness-first mode: always (re)request the current beat.
    // This avoids stale beat reuse and keeps PC/insn aligned across redirects.
    val reqAddr = UInt(addressWidth bits)
    reqAddr := needSecondBeat ? secondBeatAddr | beatAddr

    io.readCmd.cmd.valid := cmdStage.up.isValid && (inflight === 0) && !io.flush
    io.readCmd.cmd.payload.address := reqAddr
    io.readCmd.cmd.payload.id := activeEpoch

    // Stall until request is accepted.
    haltWhen(!io.readCmd.cmd.fire)
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

    // Drop stale packets and keep PC/insn in sync by throwing stage entry.
    throwWhen(stalePacket)

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
      if(withCompressed) {
        val isCompressed = assembledInsn(1 downto 0) =/= B"11"
        stepReg := isCompressed ? U(2, 3 bits) | U(4, 3 bits)
      } else {
        stepReg := U(4, 3 bits)
      }
      when(needSecondBeat) {
        needSecondBeat := False
      }
    }

    // Pop when consumed or when dropped due stale/mismatch or when latching first half.
    fifo.io.pop.ready := takeInsn || stalePacket || beatMismatch || waitingSecond
  }
}
