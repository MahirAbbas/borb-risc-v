package borb.dispatch

import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.sim._
import spinal.lib.misc.pipeline._

import borb.frontend.Decoder._
import borb.frontend.Decoder
import borb.execute.WriteBack
import borb.dispatch.IssueSemantics
import borb.backend.{BackendIssue, BackendPipe}
// import borb.frontend.AluOp
import spinal.core.sim._
import scala.collection.immutable.LazyList.cons
import scala.collection.mutable.ArrayBuffer
import borb.common.MicroCode.uopADDI


/*
How to check if a instruction can schedule :
- If one of the pipeline which implement its micro op is free
- There is no inflight non-bypassed RF write to one of the source operand
- There is no scheduling fence
- For credit based execution, check there is enough credit

Schedule heuristic :
- In priority order, go through the slots
- Check which pipeline could schedule it (free && compatible)
- Select the pipeline which the highest priority (to avoid using the one which can do load and store, for instance)
- If the slot can't be schedule, disable all following ones with same HART_ID
 */

object Dispatch extends AreaObject {
  val SENDTOALU = Payload(Bool())
  val SENDTOBRANCH = Payload(Bool())
  val SENDTOAGU = Payload(Bool())
}

case class Dispatch(
    dispatchNode: CtrlLink,
    hzRange: Seq[CtrlLink],
    pipeline: StageCtrlPipeline,
    intBypassReady: Seq[Bool] = Seq()
) extends Area {

  // import borb.decode.Decoder._
  import Dispatch._
  // val op = uop.toStream

  // if uop match EU uop
  // check hazards
  // set EU to fire

  val logic = new dispatchNode.Area {
    import borb.common.Common._
    val issueProps = IssueSemantics.classify(up(Decoder.MicroCode))
    val selectedPipe = BackendPipe.select(up(Decoder.MicroCode), issueProps)

    down(LANE_SEL) := False
    down(IssueSemantics.PROPS) := issueProps
    down(BackendIssue.SELECTED_PIPE) := Mux(up(Decoder.VALID), selectedPipe, BackendPipe.None)

    // when(up.isValid) {
    //   eus.foreach(f => f.SEL := False)
    // }
    down(SENDTOALU) := False
    down(SENDTOBRANCH) := False
    down(SENDTOAGU) := False

    // LANE_SEL identifies that this instruction occupies the lane.
    // Downstream hazard/flush logic relies on it staying asserted while stalled;
    // actual side effects are still gated by stage fire conditions elsewhere.
    val laneOccupied = up(Decoder.VALID)

    when(up(Decoder.VALID) && issueProps.fuMask(0)) {
      down(SENDTOALU) := True
      down(LANE_SEL) := laneOccupied
    }
    when(up(Decoder.VALID) && issueProps.fuMask(1)) {
      down(SENDTOBRANCH) := True
      down(LANE_SEL) := laneOccupied
    }
    when(up(Decoder.VALID) && issueProps.fuMask(2)) {
      down(SENDTOAGU) := True
      down(LANE_SEL) := laneOccupied
    }

    // Explicitly handle invalid/bubble case if needed, but default False covers it.
  }
  case class HazardChecker(hzRange: Seq[CtrlLink], regCount: Int = 32)
      extends Area {
    private val bypassReadyPerStage = if (intBypassReady.nonEmpty) {
      intBypassReady
    } else {
      Seq.fill(hzRange.tail.size)(False)
    }
    // Derive a combinational busy map from younger in-flight stages.
    // This avoids sticky scoreboard bits after flushes/stalls.
    val regBusy = Bits(regCount bits)
    val fpRegBusy = Bits(regCount bits)
    regBusy.clearAll()
    fpRegBusy.clearAll()
    for ((stage, bypassReady) <- hzRange.tail.zip(bypassReadyPerStage)) {
      val stValid = stage.up.isValid && stage(Decoder.VALID) && stage(borb.common.Common.LANE_SEL)
      val stRd = stage(Decoder.RD_ADDR)
      val stProps = stage(IssueSemantics.PROPS)
      val stWritesIntRd = stValid &&
        stProps.writesIntRd &&
        (stRd =/= 0)
      val stWritesFpRd = stValid &&
        (stRd =/= 0) &&
        stProps.writesFpRd
      when(stWritesIntRd && !bypassReady) {
        regBusy(stRd.asUInt) := True
      }
      when(stWritesFpRd) {
        fpRegBusy(stRd.asUInt) := True
      }
    }

    val writes = new dispatchNode.Area {
      val valid = up.isValid && up(Decoder.VALID)
      val rd = up(Decoder.RD_ADDR)
      val rs1 = up(Decoder.RS1_ADDR)
      val rs2 = up(Decoder.RS2_ADDR)
      val rs3 = up(Decoder.RS3_ADDR)
      val props = IssueSemantics.classify(up(Decoder.MicroCode))

      val rs1Busy = props.readsIntRs1 &&
        (rs1 =/= 0) && regBusy(rs1.asUInt)
      val rs2Busy = props.readsIntRs2 &&
        (rs2 =/= 0) && regBusy(rs2.asUInt)
      val fpRs1Busy = props.readsFpRs1 && (rs1 =/= 0) && fpRegBusy(rs1.asUInt)
      val fpRs2Busy = props.readsFpRs2 && (rs2 =/= 0) && fpRegBusy(rs2.asUInt)
      val fpRs3Busy = props.readsFpRs3 && (rs3 =/= 0) && fpRegBusy(rs3.asUInt)

      val hazard = valid && (rs1Busy || rs2Busy || fpRs1Busy || fpRs2Busy || fpRs3Busy)
      hazard.simPublic()

      haltWhen(hazard)
    }

    regBusy.simPublic()
    fpRegBusy.simPublic()
  }
  val hcs = new HazardChecker(hzRange, 32)

  // logic?
  // for each EU check if UOP maps.
  // if maps send to, and execute, as long as no hazard
  // if uop match EU, SEL := TRUE
  // eus.foreach {
  //   if (uopList(op)) {
  //     eus.SEL := True
  //   }
  //   // check if transaction is moving, indicates EU is free/stage is free
  //   eus.node.isMoving()
  // }

  // val nodeArea = new dispatchNode.Area {
  //   down(alu_valid) := False

  //   when(hazard) {
  //     haltIt()
  //   }
  //   when(up(FU_ALU) && !hazard) {
  //     down(alu_valid) := True

  //   }

  // }

  // what EU does it want

  // when not hazard, and EU free

}

// case class HazardChecker(hazardStages: Seq[StageCtrl],
//                          forwardingMatrix: Seq[Seq[Bool]] = Seq.empty  // optional: forwardingMatrix(i)(j) true if stage i can forward from stage j
//                         ) extends Area {

//   // Wrap StageCtrls into StageViews so we can attach usesRs1/2 flags if needed
//   val views = hazardStages.map(new StageView(_))

//   // helper: check equality but guard against rd==0
//   def regConflict(rd: Bits, rs: Bits): Bool = {
//     (rd =/= 0) && (rd === rs)
//   }

//   // For each consumer stage i, compute hazards with any later stage j
//   for (i <- views.indices) {
//     val consumer = views(i)

//     // accumulate hazard reasons from all later stages
//     var hazardFromLater: Bool = False

//     for (j <- (i + 1) until views.length) {
//       val producer = views(j)

//       // RAW checks (consumer reads a reg written by producer)
//       val rs1Conflict = consumer.usesRs1 && regConflict(producer.rd, consumer.rs1) && consumer.valid && producer.valid
//       val rs2Conflict = consumer.usesRs2 && regConflict(producer.rd, consumer.rs2) && consumer.valid && producer.valid

//       var conflict = rs1Conflict || rs2Conflict

//       // If a forwarding matrix was provided, allow bypassing:
//       // forwardingMatrix(i)(j) = true means stage i can get forwarded value from stage j
//       if (forwardingMatrix.nonEmpty) {
//         val canForward = forwardingMatrix(i)(j) // user-supplied Bool
//         // If forwarding is available from producer j to consumer i, don't treat it as a conflict.
//         conflict = conflict && !canForward
//       }

//       hazardFromLater = hazardFromLater || conflict
//     }

//     // Stall only the consumer stage when there is a hazard
//     // This localizes the stall to the stage where the consumer resides.
//     consumer.stageCtrl.arbitration.haltItWhen(hazardFromLater)
//   }

//   // optional debug outputs
//   val anyHazard = views.indices.map(i => views(i).stageCtrl.arbitration.isStuck).fold(False)(_ || _)
// }
