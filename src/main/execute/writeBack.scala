package borb.execute

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.dispatch.RegFileWrite
import borb.common.Common._
import borb.frontend.Decoder
import borb.fetch.{Fetch, PC}
import spinal.core.sim._

object WriteBack extends AreaObject {
  val RESULT = Payload(new RegFileWrite())
  val DUPLICATE_RETIRE = Payload(Bool())
}

case class WriteBack(wbNode: CtrlLink, writePort: RegFileWrite, currentEpoch: UInt, squashCycle: Bool = False) extends Area {
  val logic = new wbNode.Area {
     val epochMatches = up(SPEC_EPOCH) === currentEpoch
     val redirectingInsn = up(SELF_REDIRECT)
     val stageValid = up.isValid && up(Decoder.VALID)
     val retireReq = stageValid && up(LANE_SEL) && ((epochMatches && !squashCycle) || redirectingInsn)
     val lastRetireValid = RegInit(False)
     val lastRetireSeq = Reg(UInt(32 bits)) init(0)
     val duplicateRetire =
       lastRetireValid &&
       retireReq &&
       (up(Fetch.FETCH_SEQ) === lastRetireSeq)
     val commitPulse = retireReq && !duplicateRetire
     // Retire every lane-selected instruction (including traps).
     // Traps still suppress register writeback via RESULT.valid path below.
     up(COMMIT) := commitPulse
     up(WriteBack.DUPLICATE_RETIRE) := duplicateRetire
     when(retireReq) {
       lastRetireValid := True
       lastRetireSeq := up(Fetch.FETCH_SEQ)
     }
     
     // Drive write port
     writePort.address := up(WriteBack.RESULT).address
     writePort.data    := up(WriteBack.RESULT).data
     
     // Gated by COMMIT
     writePort.valid   := up(WriteBack.RESULT).valid && commitPulse

     down.ready := True

   }
}
