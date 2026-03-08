package borb.execute

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.dispatch.RegFileWrite
import borb.common.Common._
import spinal.core.sim._

object WriteBack extends AreaObject {
  val RESULT = Payload(new RegFileWrite())
}

case class WriteBack(wbNode: CtrlLink, writePort: RegFileWrite, currentEpoch: UInt) extends Area {
  val logic = new wbNode.Area {
     val epochMatches = up(SPEC_EPOCH) === currentEpoch
     val redirectingInsn = up(SELF_REDIRECT)
     // Retire every lane-selected instruction (including traps).
     // Traps still suppress register writeback via RESULT.valid path below.
     up(COMMIT) := up(LANE_SEL) && (epochMatches || redirectingInsn)
     
     // Drive write port
     writePort.address := up(WriteBack.RESULT).address
     writePort.data    := up(WriteBack.RESULT).data
     
     // Gated by COMMIT
     writePort.valid   := up(WriteBack.RESULT).valid && down.isFiring && up(COMMIT)

     down.ready := True

   }
}
