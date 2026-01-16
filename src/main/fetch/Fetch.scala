package borb.fetch

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._

import borb.fetch.PC
import borb.frontend.Decoder.INSTRUCTION
import borb.memory._
import spinal.core.sim._

object Fetch extends AreaObject {
  val addressWidth = 64
}

case class Fetch(cmdStage: CtrlLink, rspStage: CtrlLink, addressWidth: Int, dataWidth: Int) extends Area {
  import Fetch._
  val io = new Bundle {
    val readCmd = new RamFetchBus(addressWidth, dataWidth, idWidth = 16)
    val flush = Bool()
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
    val reqSent = RegInit(False)
    when(io.readCmd.cmd.fire) {
      reqSent := True
    }
    when(cmdStage.down.isFiring) {
      reqSent := False
    }

    // Explicitly clear reqSent on flush
    when(io.flush) {
      reqSent := False
    }

    // Only send request if we haven't sent it yet and there is space in FIFO (accounting for inflight)
    // Do NOT send on flush cycle - wait for redirected PC next cycle.
    io.readCmd.cmd.valid := cmdStage.up.isValid && !reqSent && (fifo.io.availability > inflight) && !io.flush
    io.readCmd.cmd.payload.address := cmdStage(PC.PC)
    io.readCmd.cmd.payload.id := epoch
    
    // Halt if we haven't successfully sent the request yet
    haltWhen(!reqSent && !io.readCmd.cmd.fire)
  }

  val rspArea = new rspStage.Area {
    // Check epoch at pop time - only accept if epoch matches current epoch
    val packetValid = fifo.io.pop.valid
    val packetEpoch = fifo.io.pop.payload.epoch
    val epochMatch = packetEpoch === epoch
    
    // If we have a stale packet, THROW the stage to discard both the stale PC 
    // and the stale instruction, keeping them in sync
    val stalePacket = packetValid && !epochMatch
    throwWhen(stalePacket)
    
    // Halt until we have a valid instruction (but not for wrong epoch - that's a throw)
    haltWhen(!packetValid)
    
    val rawData = fifo.io.pop.payload.data
    INSTRUCTION := rawData(31 downto 0)
    
    // Pop from FIFO when:
    // 1. Stage fires downstream (normal flow), OR
    // 2. We have a stale packet (epoch mismatch) - discard it (throw handles the PC)
    fifo.io.pop.ready := rspStage.down.isFiring || stalePacket
  }
}
