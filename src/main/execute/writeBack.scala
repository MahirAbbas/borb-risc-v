package borb.execute

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.dispatch.RegFileWrite
import borb.common.Common._
import borb.execute.IntAlu.RESULT
import spinal.core.sim._

case class WriteBack(wbNode: CtrlLink, writePort: RegFileWrite) extends Area {
  val logic = new wbNode.Area {
     // Initialize TRAP to False (no traps implemented yet)
     up(TRAP) := False
     
     // Derive COMMIT
     // COMMIT is valid only if valid lane and no trap
     up(COMMIT) := !up(TRAP) && up(LANE_SEL)
     
     // Drive write port
     writePort.address := up(RESULT).address
     writePort.data    := up(RESULT).data
     
     // Gated by COMMIT
     writePort.valid   := up(RESULT).valid && down.isFiring && up(COMMIT)

     // Simulation hooks
     val valid          = up(borb.frontend.Decoder.VALID) 
     val immed          = up(borb.dispatch.SrcPlugin.IMMED)
     val sendtoalu      = up(borb.dispatch.Dispatch.SENDTOALU)
     val result         = up(borb.execute.IntAlu.RESULT).data
     val valid_result   = up(borb.execute.IntAlu.RESULT).valid
     val rdaddr         = up(borb.execute.IntAlu.RESULT).address
     val lane_sel       = up(LANE_SEL)
     val commit         = up(COMMIT)
     
     valid.simPublic()
     immed.simPublic()
     sendtoalu.simPublic()
     result.simPublic()
     valid_result.simPublic()
     rdaddr.simPublic()
     lane_sel.simPublic()
     commit.simPublic()
  }
}
