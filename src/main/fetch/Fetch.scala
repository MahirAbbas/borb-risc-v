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

  val fifo = StreamFifo(Bits(dataWidth bits), depth = 2)
  
  // Track inflight requests to prevent FIFO overflow
  val inflight = RegInit(U(0, 4 bits))
  val cmdFire = io.readCmd.cmd.fire
  val rspFire = io.readCmd.rsp.valid
  inflight := inflight + U(cmdFire) - U(rspFire)

  // Epoch/ID Handshake logic
  val epoch = RegInit(U(0, 16 bits))
  
  when(io.flush) {
    epoch := epoch + 1
  }

  // Connect memory response to FIFO (Gated)
  // Only push if response ID matches current epoch
  val rspEpoch = io.readCmd.rsp.id
  fifo.io.push.valid := io.readCmd.rsp.valid && (rspEpoch === epoch)
  fifo.io.push.payload := io.readCmd.rsp.data
  
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
    io.readCmd.cmd.valid := cmdStage.up.isValid && !reqSent && (fifo.io.availability > inflight)
    io.readCmd.cmd.payload.address := cmdStage(PC.PC)
    io.readCmd.cmd.payload.id := epoch
    
    // Halt if we haven't successfully sent the request yet
    haltWhen(!reqSent && !io.readCmd.cmd.fire)
  }

  val rspArea = new rspStage.Area {
    // Wait for data in FIFO
    haltWhen(!fifo.io.pop.valid)
    val rawData = fifo.io.pop.payload
    //val wordSel = rspStage(PC.PC)(1)
    INSTRUCTION := rawData(31 downto 0)
    fifo.io.pop.ready := rspStage.down.isFiring
  }
}
