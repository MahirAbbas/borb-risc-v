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
    // Keep at most one fetch beat in-flight to preserve PC/data alignment.
    io.readCmd.cmd.valid := cmdStage.up.isValid && needReq && (inflight === 0) && !io.flush
    io.readCmd.cmd.payload.address := beatAddr
    io.readCmd.cmd.payload.id := activeEpoch

    // If this PC needs a new beat, stall until the request is accepted.
    haltWhen(needReq && !io.readCmd.cmd.fire)
  }

  val rspArea = new rspStage.Area {
    // Keep one beat locally so either 32-bit half can be consumed in any order.
    // This avoids losing an instruction when control flow jumps from +4 back to +0
    // within the same 64-bit fetch beat.
    val holdValid = RegInit(False)
    val holdData = Reg(Bits(dataWidth bits)) init (0)
    val holdEpoch = Reg(UInt(16 bits)) init (0)
    val holdBeatAddr = Reg(UInt(addressWidth bits)) init (0)

    when(io.flush) {
      holdValid := False
    }

    // Invalidate cached beat if its speculation epoch is stale.
    when(holdValid && (holdEpoch =/= activeEpoch)) {
      holdValid := False
    }

    val beatAddr = UInt(addressWidth bits)
    beatAddr := rspStage(PC.PC)
    beatAddr(2 downto 0) := 0

    val useHold = holdValid && (holdBeatAddr === beatAddr)
    val srcValid = useHold || fifo.io.pop.valid
    val srcData = useHold ? holdData | fifo.io.pop.payload.data
    val srcEpoch = useHold ? holdEpoch | fifo.io.pop.payload.epoch
    val srcBeatAddr = useHold ? holdBeatAddr | fifo.io.pop.payload.beatAddr
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
    val loadHoldFromFifo = takeInsn && !useHold

    when((stalePacket || beatMismatch) && useHold) {
      // Drop stale/mismatched hold packet.
      holdValid := False
    }.elsewhen((stalePacket || beatMismatch) && !useHold) {
      // Drop stale FIFO packet.
      holdValid := False
    }.elsewhen(loadHoldFromFifo) {
      // Cache fetched beat for potential same-beat control flow changes.
      holdValid := True
      holdData := srcData
      holdEpoch := srcEpoch
      holdBeatAddr := beatAddr
    }

    // Pop only when source is FIFO (not hold) and we consume or discard it.
    fifo.io.pop.ready := !useHold && (takeInsn || stalePacket || beatMismatch)
  }
}
