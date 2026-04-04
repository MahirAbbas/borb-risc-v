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

object FrontendRedirectReason extends SpinalEnum {
  val branch, trap, mret, fencei, externalFlush = newElement()
}

case class FrontendRedirect(addressWidth: Int, epochWidth: Int = 16) extends Bundle {
  val target = UInt(addressWidth bits)
  val reason = FrontendRedirectReason()
  val epoch = UInt(epochWidth bits)
  val flushFrontend = Bool()
}

case class FrontendPcState(addressWidth: Int, epochWidth: Int = 16) extends Bundle {
  val pc = UInt(addressWidth bits)
  val epoch = UInt(epochWidth bits)
}

object PC extends AreaObject {
  val addressWidth = 64
  val PC = Payload(UInt(addressWidth bits))
  val FLUSH = Payload(Bool())
}

case class PC(stage: CtrlLink,addressWidth: Int , withCompressed: Boolean = false, resetPc: BigInt = 0) extends Area {
  stage.up.valid := True

  val jump = Flow(JumpCmd(addressWidth))
  val flush = Flow(FlushCmd(addressWidth))
  val exception = Flow(ExceptionCmd(addressWidth))
  val redirect = Flow(FrontendRedirect(addressWidth))
  val sequentialValid = Bool()
  val sequentialStep = UInt(3 bits)
  val state = FrontendPcState(addressWidth)
  state.epoch := 0

  // allows for future support of 'C' extension
  // val fetch_offset = withCompressed generate in(UInt(3 bits))

  val PC_cur = Reg(UInt(addressWidth bits)).init(U(resetPc, addressWidth bits))

  // Control flow change interfaces

  val logic = new stage.Area {

    // val sequentialNextPc =
    //   if (withCompressed) PC_cur + fetch_offset else PC_cur + 4

    // Priority: exception > flush > jump > sequential

    when(redirect.valid) {
      PC_cur := redirect.payload.target
    }.elsewhen(exception.valid) {
      PC_cur := exception.payload.vector
    }.elsewhen(flush.valid) {
      PC_cur := flush.payload.address
    }.elsewhen(jump.valid) {
      PC_cur := jump.payload.target
    }.elsewhen(sequentialValid) {
      val step = if(withCompressed) sequentialStep.resize(addressWidth bits) else U(4, addressWidth bits)
      PC_cur := PC_cur + step
    }
    down(PC.PC) := PC_cur
    state.pc := PC_cur

    // PC.FLUSH := jump.valid || flush.valid || exception.valid
  }
}
