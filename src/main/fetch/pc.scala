package borb.fetch

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import spinal.lib.misc.plugin._

import scala.collection.mutable.ArrayBuffer

case class JumpCmd(addressWidth: Int) extends Bundle {
  val target = UInt(addressWidth bits)
  val is_jump = Bool()
  val is_branch = Bool()
}

case class FlushCmd(addressWidth: Int) extends Bundle {
  val address = UInt(addressWidth bits)
}

case class ExceptionCmd(addressWidth: Int) extends Bundle {
  val vector = UInt(addressWidth bits)
}

object PC extends AreaObject {
  val addressWidth =32 
  val PC = Payload(UInt(addressWidth bits))
  val FLUSH = Payload(Bool())
}

case class PC(stage: CtrlLink,addressWidth: Int , withCompressed: Boolean = false) extends Area {

  val jump = Flow(JumpCmd(addressWidth))
  val flush = Flow(FlushCmd(addressWidth))
  val exception = Flow(ExceptionCmd(addressWidth))

  // allows for future support of 'C' extension
  val fetch_offset = withCompressed generate in(UInt(3 bits))

  val PC_cur = Reg(UInt(addressWidth bits)).init(0)

  // Control flow change interfaces

  val logic = new stage.Area {

    val sequentialNextPc =
      if (withCompressed) PC_cur + fetch_offset else PC_cur + 4

    // Priority: exception > flush > jump > sequential
    when(exception.valid) {
      sequentialNextPc := exception.payload.vector
    }.elsewhen(flush.valid) {
      sequentialNextPc := flush.payload.address
    }.elsewhen(jump.valid) {
      sequentialNextPc := jump.payload.target
    }

    PC.PC := PC_cur
    when(down.isFiring) {
      PC_cur := PC_cur + 4
      down(PC.PC) := PC_cur
    }


    // PC.FLUSH := jump.valid || flush.valid || exception.valid
  }
}
