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
  val addressWidth = 32
  val PC_delayed = Payload(UInt(addressWidth bits))
}

case class Fetch(stage: CtrlLink,addressWidth: Int,dataWidth: Int) extends Area {
  import Fetch._
  val io = new Bundle {
    val readCmd = new RamFetchBus(addressWidth, dataWidth, idWidth = 16)
  }

  // val waitingForRsp = Reg(Bool()) init False
  val logic = new stage.Area {
    val pcReg = Reg(UInt(addressWidth bits))
    pcReg.simPublic()
    pcReg := PC.PC
    io.readCmd.cmd.valid := False
    io.readCmd.cmd.payload.address.assignDontCare()

    INSTRUCTION.assignDontCare()
    PC_delayed.assignDontCare()


    val pcVal = stage(PC.PC)
    val instr = stage(INSTRUCTION)

    when(stage.isValid) {
      io.readCmd.cmd.address := up(PC.PC)
      io.readCmd.cmd.valid := True
      when((io.readCmd.cmd.valid && io.readCmd.cmd.ready)) {
        // waitingForRsp := True
      }
      // haltWhen(waitingForRsp)

    }
    when(io.readCmd.rsp.valid) {
      // waitingForRsp := False
      INSTRUCTION := io.readCmd.rsp.data
      // INSTRUCTION := B(0).resized
      PC_delayed := pcReg

    }
  }

  // val fifo = StreamFifo()
}
