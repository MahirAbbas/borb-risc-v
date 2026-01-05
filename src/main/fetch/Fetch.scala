package borb.fetch

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
// import borb.memory.RamRead

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
  }

  val fifo = StreamFifo(Bits(dataWidth bits), depth = 8)
  
  // Track inflight requests to prevent FIFO overflow
  val inflight = RegInit(U(0, 4 bits))
  val cmdFire = io.readCmd.cmd.fire
  val rspFire = io.readCmd.rsp.valid
  inflight := inflight + U(cmdFire) - U(rspFire)

  // Connect memory response to FIFO (Gated)
  fifo.io.push.valid := io.readCmd.rsp.valid 
  fifo.io.push.payload := io.readCmd.rsp.data

  val cmdArea = new cmdStage.Area {
    val reqSent = RegInit(False)
    when(io.readCmd.cmd.fire) {
      reqSent := True
    }
    when(cmdStage.down.isFiring) {
      reqSent := False
    }

    // Only send request if we haven't sent it yet and there is space in FIFO (accounting for inflight)
    io.readCmd.cmd.valid := cmdStage.up.isValid && !reqSent && (fifo.io.availability > inflight)
    io.readCmd.cmd.payload.address := cmdStage(PC.PC)
    io.readCmd.cmd.payload.id.assignDontCare()
    
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
