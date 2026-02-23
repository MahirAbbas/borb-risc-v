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

case class Fetch(cmdStage: CtrlLink, rspStage: CtrlLink, addressWidth: Int, dataWidth: Int) extends Area {
  import Fetch._
  val io = new Bundle {
    val readCmd = new RamFetchBus(addressWidth, dataWidth, idWidth = 16)
    val flush = Bool()
    val currentEpoch = UInt(4 bits)  // Global speculation epoch from CPU
  }

  // Fetch Packet: instruction data + epoch tag
  case class FetchPacket() extends Bundle {
    val data = Bits(dataWidth bits)
    val epoch = UInt(16 bits)
  }
  
  val fifo = StreamFifo(FetchPacket(), depth = 2)
  
  // Track inflight requests to prevent FIFO overflow
  val inflight = RegInit(U(0, 4 bits))
  val cmdFire = io.readCmd.cmd.fire
  val rspFire = io.readCmd.rsp.valid
  inflight := inflight + U(cmdFire) - U(rspFire)

  // Epoch/ID Handshake logic
  // Delay epoch increment by one cycle to prevent race conditions
  // where a stale response on the flush cycle accidentally gets the new epoch
  val epoch = RegInit(U(0, 16 bits))
  val flushPending = RegNext(io.flush) init False
  
  when(flushPending) {
    epoch := epoch + 1
  }

  // Connect memory response to FIFO
  // Push ALL responses with their epoch tag (no filtering at push time)
  val rspEpoch = io.readCmd.rsp.id
  fifo.io.push.valid := io.readCmd.rsp.valid
  fifo.io.push.payload.data := io.readCmd.rsp.data
  fifo.io.push.payload.epoch := rspEpoch
  
  // Also flush FIFO storage when io.flush is asserted
  fifo.io.flush := io.flush

  val cmdArea = new cmdStage.Area {
    val requestedBeatValid = RegInit(False)
    val requestedBeatAddr = Reg(UInt(addressWidth bits)) init(0)

    // 64-bit fetch beat base address
    val beatAddr = UInt(addressWidth bits)
    beatAddr := cmdStage(PC.PC)
    beatAddr(2 downto 0) := 0
    val needReq = !requestedBeatValid || (beatAddr =/= requestedBeatAddr)

    when(io.readCmd.cmd.fire) {
      requestedBeatValid := True
      requestedBeatAddr := beatAddr
    }

    when(io.flush) {
      requestedBeatValid := False
    }

    // Request once per 64-bit beat (not once per 32-bit instruction).
    io.readCmd.cmd.valid := cmdStage.up.isValid && needReq && (fifo.io.availability > inflight) && !io.flush
    io.readCmd.cmd.payload.address := beatAddr
    io.readCmd.cmd.payload.id := epoch

    // If this PC needs a new beat, stall until the request is accepted.
    haltWhen(needReq && !io.readCmd.cmd.fire)
  }

  val rspArea = new rspStage.Area {
    // Keep one beat locally so both 32-bit halves can be consumed.
    val holdValid = RegInit(False)
    val holdData = Reg(Bits(dataWidth bits)) init (0)
    val holdEpoch = Reg(UInt(16 bits)) init (0)

    when(io.flush) {
      holdValid := False
    }

    val useHold = holdValid
    val srcValid = useHold || fifo.io.pop.valid
    val srcData = useHold ? holdData | fifo.io.pop.payload.data
    val srcEpoch = useHold ? holdEpoch | fifo.io.pop.payload.epoch
    val stalePacket = srcValid && (srcEpoch =/= epoch)

    // Drop stale packets and keep PC/insn in sync by throwing stage entry.
    throwWhen(stalePacket)

    // Halt until we have data, except when stale data is being discarded.
    haltWhen(!srcValid && !stalePacket)

    // iBus returns 64-bit beats. Select the 32-bit half based on PC[2].
    INSTRUCTION := Mux(rspStage(PC.PC)(2), srcData(63 downto 32), srcData(31 downto 0))
    
    // Tag instruction with current speculation epoch from CPU
    // All downstream stages inherit this epoch for flush comparison
    SPEC_EPOCH := io.currentEpoch
    
    val takeInsn = rspStage.down.isFiring && srcValid && !stalePacket
    val keepForUpperHalf = takeInsn && !rspStage(PC.PC)(2)

    when(stalePacket) {
      holdValid := False
    }.elsewhen(takeInsn) {
      holdValid := keepForUpperHalf
      when(keepForUpperHalf) {
        holdData := srcData
        holdEpoch := srcEpoch
      }
    }

    // Pop only when source is FIFO (not hold) and we consume or discard.
    fifo.io.pop.ready := !useHold && (takeInsn || stalePacket)
  }
}
