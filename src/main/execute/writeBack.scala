package borb.execute

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.dispatch.RegFileWrite
import borb.common.Common._
import borb.common.LaneKey
import borb.frontend.Decoder
import borb.fetch.{Fetch, PC}
import spinal.core.sim._

object WriteBack extends AreaObject {
  val RESULT = Payload(new RegFileWrite())
  val DUPLICATE_RETIRE = Payload(Bool())
}

case class WriteBack(
    wbNode: CtrlLink,
    writePorts: Vec[RegFileWrite],
    currentEpoch: UInt,
    squashCycle: Bool = False,
    redirectPending: Bool = False,
    redirectSeq: UInt = U(0, 32 bits)
) extends Area {
  require(writePorts.length >= 2, "2-wide writeback needs at least two integer write ports")

  val logic = new wbNode.Area {
     val recentRetireValid = Reg(Bits(2 bits)) init(0)
     val recentRetireSeq0 = Reg(UInt(32 bits)) init(0)
     val recentRetireSeq1 = Reg(UInt(32 bits)) init(0)

     val commitPulse = Vec(Bool(), 2)
     val duplicateRetire = Vec(Bool(), 2)
     val retireReq = Vec(Bool(), 2)
     val fetchSeq = Vec(UInt(32 bits), 2)

     for(laneId <- 0 until 2) {
       val laneKey = LaneKey(laneId)
       val epochMatches = up(SPEC_EPOCH, laneKey) === currentEpoch
       val redirectingInsn = up(SELF_REDIRECT, laneKey)
       val stageValid = up.isValid && up(Decoder.VALID, laneKey)
       val olderThanPendingRedirect = redirectPending && (up(Fetch.FETCH_SEQ, laneKey) < redirectSeq)
       val olderInstructionMayRetire = olderThanPendingRedirect && !up(TRAP, laneKey)
       fetchSeq(laneId) := up(Fetch.FETCH_SEQ, laneKey)
       retireReq(laneId) := stageValid &&
         up(LANE_SEL, laneKey) &&
         ((((epochMatches || olderInstructionMayRetire) && !squashCycle)) || redirectingInsn)
       val sameCycleDuplicate = if(laneId == 1) commitPulse(0) && (fetchSeq(1) === fetchSeq(0)) else False
       duplicateRetire(laneId) := retireReq(laneId) &&
         (
           (recentRetireValid(0) && (fetchSeq(laneId) === recentRetireSeq0)) ||
           (recentRetireValid(1) && (fetchSeq(laneId) === recentRetireSeq1)) ||
           sameCycleDuplicate
         )
       commitPulse(laneId) := retireReq(laneId) && !duplicateRetire(laneId)
     }

     // Preserve in-order lane retirement. Lane 1 can retire only if lane 0 is
     // absent or also retires in the same cycle.
     when(up.isValid && up(Decoder.VALID, LaneKey.Lane0) && up(LANE_SEL, LaneKey.Lane0) && !commitPulse(0)) {
       commitPulse(1) := False
     }

     for(laneId <- 0 until 2) {
       val laneKey = LaneKey(laneId)
       up(COMMIT, laneKey) := commitPulse(laneId)
       up(WriteBack.DUPLICATE_RETIRE, laneKey) := duplicateRetire(laneId)
       writePorts(laneId).address := up(WriteBack.RESULT, laneKey).address
       writePorts(laneId).data := up(WriteBack.RESULT, laneKey).data
       writePorts(laneId).valid := up(WriteBack.RESULT, laneKey).valid && commitPulse(laneId)
     }

     when(commitPulse(0) && commitPulse(1)) {
       recentRetireValid := B"11"
       recentRetireSeq1 := fetchSeq(1)
       recentRetireSeq0 := fetchSeq(0)
     } elsewhen(commitPulse(0)) {
       recentRetireValid := B"11"
       recentRetireSeq1 := recentRetireSeq0
       recentRetireSeq0 := fetchSeq(0)
     } elsewhen(commitPulse(1)) {
       recentRetireValid := B"11"
       recentRetireSeq1 := recentRetireSeq0
       recentRetireSeq0 := fetchSeq(1)
     }

     down.ready := True

   }
}
