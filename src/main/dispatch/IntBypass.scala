package borb.dispatch

import spinal.core._

case class IntBypassSource() extends Bundle {
  val valid = Bool()
  val address = UInt(5 bits)
  val data = Bits(64 bits)
}
