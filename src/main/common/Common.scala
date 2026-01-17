package borb.common

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._

object Common extends AreaObject {
  val COMMIT   = Payload(Bool())
  val TRAP     = Payload(Bool())
  val LANE_SEL = Payload(Bool())
  val SPEC_EPOCH = Payload(UInt(4 bits))  // Speculation epoch for branch handling
}
