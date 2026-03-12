package borb.backend

import spinal.core._

case class IntResultIntent(epochWidth: Int = 16) extends Bundle {
  val valid = Bool()
  val rd = UInt(5 bits)
  val data = Bits(64 bits)
  val writesRd = Bool()
  val commitEligible = Bool()
  val epoch = UInt(epochWidth bits)
}

case class FpWriteIntent() extends Bundle {
  val valid = Bool()
  val address = UInt(5 bits)
  val data = Bits(64 bits)
}

case class FpFlagsIntent() extends Bundle {
  val valid = Bool()
  val bits = Bits(5 bits)
}

case class TrapRedirectOutcome() extends Bundle {
  val trapFire = Bool()
  val trapTargetPriv = UInt(2 bits)
  val mretFire = Bool()
  val mretTarget = UInt(64 bits)
  val mretTargetPriv = UInt(2 bits)
  val sretFire = Bool()
  val sretTarget = UInt(64 bits)
  val sretTargetPriv = UInt(2 bits)
  val trapCause = Bits(64 bits)
  val trapTval = Bits(64 bits)
}
