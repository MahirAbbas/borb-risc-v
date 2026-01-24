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

case class WriteBack(wbNode: CtrlLink, writePort: RegFileWrite) extends Area {
  val logic = new wbNode.Area {
     // Initialize TRAP to False (no traps implemented yet)
     up(TRAP) := False
     
     // Derive COMMIT
     // COMMIT is valid only if valid lane and no trap
     up(COMMIT) := !up(TRAP) && up(LANE_SEL)
     
     // Drive write port
     writePort.address := up(WriteBack.RESULT).address
     writePort.data    := up(WriteBack.RESULT).data
     
     // Gated by COMMIT
     writePort.valid   := up(WriteBack.RESULT).valid && down.isFiring && up(COMMIT)

     down.ready := True

   }
}
