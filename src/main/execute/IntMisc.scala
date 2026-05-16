package borb.execute

import spinal.core._
import spinal.lib._
import borb.common.MicroCode._
import borb.frontend.ExecutionUnitEnum

object IntMisc extends AreaObject {
  val SupportedUops = Seq(
    uopMOP_R,
    uopMOP_RR
  )

  def accepts(microCode: borb.common.MicroCode.C): Bool = {
    SupportedUops.map(microCode === _).reduce(_ || _)
  }
}

case class IntMisc() extends FunctionalUnit(ExecutionUnitEnum.ALU) {
  IntMisc.SupportedUops.foreach(add)
}
