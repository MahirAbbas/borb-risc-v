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
    val currentEpoch = UInt(16 bits)  // Global speculation epoch from CPU
  }

  // Fetch Packet: instruction data + epoch tag
  case class FetchPacket() extends Bundle {
    val data = Bits(dataWidth bits)
    val epoch = UInt(16 bits)
    val beatAddr = UInt(addressWidth bits)
  }
  
  val fifo = StreamFifo(FetchPacket(), depth = 2)
  
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
    beatAddr := cmdStage(PC.PC)
    beatAddr(2 downto 0) := 0

    // Correctness-first mode: always (re)request the current beat.
    // This avoids stale beat reuse and keeps PC/insn aligned across redirects.
    io.readCmd.cmd.valid := cmdStage.up.isValid && (inflight === 0) && !io.flush
    io.readCmd.cmd.payload.address := beatAddr
    io.readCmd.cmd.payload.id := activeEpoch

    // Stall until request is accepted.
    haltWhen(!io.readCmd.cmd.fire)
  }

  val rspArea = new rspStage.Area {
    val beatAddr = UInt(addressWidth bits)
    beatAddr := rspStage(PC.PC)
    beatAddr(2 downto 0) := 0

    val srcValid = fifo.io.pop.valid
    val srcData = fifo.io.pop.payload.data
    val srcEpoch = fifo.io.pop.payload.epoch
    val srcBeatAddr = fifo.io.pop.payload.beatAddr
    val stalePacket = srcValid && (srcEpoch =/= activeEpoch)
    val beatMismatch = srcValid && (srcBeatAddr =/= beatAddr)

    // Drop stale packets and keep PC/insn in sync by throwing stage entry.
    throwWhen(stalePacket)

    // Halt until we have data for the requested beat.
    // If FIFO/hold provides a different beat, don't let that mismatched
    // instruction advance with the current PC; discard it first.
    haltWhen((!srcValid || beatMismatch) && !stalePacket)

    // iBus returns 64-bit beats. Select the 32-bit half based on PC[2].
    rspStage.down(INSTRUCTION) := Mux(rspStage(PC.PC)(2), srcData(63 downto 32), srcData(31 downto 0))
    
    // Preserve the fetch packet epoch so stale control-flow ops can't be
    // re-tagged as current after a redirect.
    rspStage.down(SPEC_EPOCH) := srcEpoch
    
    val takeInsn = rspStage.down.isFiring && srcValid && !stalePacket && !beatMismatch

    // Pop when consumed or when dropped due stale/mismatch.
    fifo.io.pop.ready := takeInsn || stalePacket || beatMismatch
  }
}
