package borb.dispatch

import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.sim._
import spinal.lib.misc.pipeline._

import borb.frontend.Decoder._
import borb.frontend.Decoder
import borb.frontend.ExecutionUnitEnum
// import borb.frontend.AluOp
import borb.LsuL1.PC
import spinal.core.sim._
import scala.collection.immutable.LazyList.cons
import borb.execute.IntAlu.RESULT
import scala.collection.mutable.ArrayBuffer
import borb.common.MicroCode.uopADDI

// object Dispatch extends AreaObject {
//   val alu_valid = Payload(Bool())
// }

// class UopLayerSpec(val uop: MicroOp, val elImpl : LaneLayer) {

// }

// class LaneLayer(val name : String, var priority : Int) {

// }

/*
How to detect RD->RSx hazards for a given candidate:
0)  for each ctrl (pipeline deepness)
      for each execute lane
        decode if RD is readable or not
1)  for each ctrl (pipeline deepness)
      for each execute lane,
        generate a hazard signal := ctrl.rd == RSx
      Aggregate the lanes hazards
2)  for each implementation slot
      process if scheduled to that slot would produce hazards
3)  schedule to the implementation slot with the best priority

 */

// case class HazardChecker(hzRange: Seq[CtrlLink]) extends Area {
//   // RAW Hazards
//   // WAW hazard
//   // Control hazards (branch not yet resolved)
//   // Structural hazard

//   // hzRange = rfRead -> rfWriteback

//   // WAR hazard
//   // intended : write RD after reading RS

//   // something like
//   // takes in range
//   // gets Stage(1), Stage(2)
//   // checks if RD /RS is same
//   // then call functionally on whole range
//   // if match, stallIt/Upper until hazard fixed

//   // RAW hazard
//   // if RD is hzRange(0) === RSx in hzRange(1 .. n-1)

//   // hzRange.head(RD)
//   // val rs1Hazard = for (stage <- hzRange.tail) {

//   // }

//   val isRs1Haz = hzRange.tail.map(e =>(hzRange.head(RS1_ADDR) =/= 0) &&(hzRange.head(RS1_ADDR) === e(RD_ADDR)) && e.up(borb.frontend.Decoder.RDTYPE) === (borb.frontend.REGFILE.RDTYPE.RD_INT))
//   isRs1Haz.foreach(e => e.simPublic())
//   // isRs1Haz.simPublic()
  
//   // val isRS1Haz = hzRange.tail.map(e => hzRange.head(RS1_ADDR) === e(RD_ADDR)).orR

//   // isRs1Haz.zipWithIndex.foreach(e => hzRange(e._2).haltWhen(e._1))
//   // hzRange.head.haltWhen(isRs1Haz.reduce(_ || _)).simPublic()
//   // when (isRs1Haz.reduce(_ || _).simPublic()) {
//   //   hzRange.head.haltIt()
//   //   // hzRange.head.isReady := False
//   // }

//   val isRs2Haz = hzRange.tail.map(e => (hzRange.head(RS2_ADDR) =/= 0) && (hzRange.head(RS2_ADDR) === e(RD_ADDR)) &&e.up(borb.frontend.Decoder.RDTYPE) === (borb.frontend.REGFILE.RDTYPE.RD_INT))

//   // isRs2Haz.zipWithIndex.foreach(e => hzRange(e._2).haltWhen(e._1))

//   // when(isRs2Haz.reduce(_ || _).simPublic()) {
//   //   hzRange.head.haltIt()
//   // }

// }

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

case class Dispatch(dispatchNode: CtrlLink, hzRange: Seq[CtrlLink], pipeline: StageCtrlPipeline) extends Area {

  // import borb.decode.Decoder._
  import Dispatch._
  // val op = uop.toStream

  // if uop match EU uop
  // check hazards
  // set EU to fire

  val logic = new dispatchNode.Area {
    // when(up.isValid) {
    //   eus.foreach(f => f.SEL := False)
    // }
    down(SENDTOALU) := False
    down(SENDTOBRANCH) := False
    down(SENDTOAGU) := False
    when(up(Decoder.EXECUTION_UNIT) === ExecutionUnitEnum.ALU) {
      down(SENDTOALU) := True
    }
    when(up(Decoder.EXECUTION_UNIT) === ExecutionUnitEnum.BR) {
      down(SENDTOBRANCH) := True
    }
    when(up(Decoder.EXECUTION_UNIT) === ExecutionUnitEnum.AGU) {
      down(SENDTOAGU) := True
    }

  }
 case class HazardChecker(hzRange: Seq[CtrlLink], regCount: Int = 32) extends Area {

    // ===============================================================
    // 1. The "scoreboard": one bit per register to indicate busy
    // ===============================================================
    val regBusy = RegInit(Bits(regCount bits)) init(0)

    // ===============================================================
    // 2. Each cycle, update busy bits based on writes in pipeline
    // ===============================================================

    // Step 1: detect writes (set busy)
    // for (stage <- hzRange) {
    //   val valid = stage(Decoder.VALID)
    //   val rd    = stage(Decoder.RD_ADDR)
    // //   // val writes = stage == hzRange.last // or use a .writesResult flag if earlier stages also write

    //   when(valid && (rd =/= 0)) {
    //     regBusy(rd.asUInt) := True
    //   }
    // }
    // Step 2: detect completions (clear busy)
    val writes = new dispatchNode.Area {
      // val stage = dispatchNode
      val valid = up(Decoder.VALID)
      val rd    = up(Decoder.RD_ADDR)
      
      when(up.isFiring && (rd =/= 0)) {
        regBusy(rd.asUInt) := True
      }
      
      val wbStage = hzRange.last
      val wbValid = wbStage(RESULT).valid
      val wbRd    = wbStage(RESULT).address

      when(wbValid && (wbRd =/= 0)) {
        regBusy(wbRd) := False
      }

      // val stage = hzRange.head
      val rs1   = up(Decoder.RS1_ADDR)
      val rs2   = up(Decoder.RS2_ADDR)
      val rs1Busy = regBusy(rs1.asUInt)
      val rs2Busy = regBusy(rs2.asUInt)

      val hazard = valid && (rs1Busy || rs2Busy)
      hazard.simPublic()
      

      //down(Decoder.VALID) := (!hazard)
      haltWhen(hazard)
    }
    // Step 2: detect completions (clear busy)
    import borb.execute.IntAlu._

    // ===============================================================
    // 3. For each consumer stage, stall if its sources are busy
    // ===============================================================
    // val hazards = RegInit(Bits(hzRange.size bits)) init(0)
    // for ((stage,i) <- hzRange.zipWithIndex) {
    //   val valid = stage(Decoder.VALID)
    //   val rs1   = stage(Decoder.RS1_ADDR)
    //   val rs2   = stage(Decoder.RS2_ADDR)

    //   val rs1Busy = regBusy(rs1.asUInt)
    //   val rs2Busy = regBusy(rs2.asUInt)
    //   val hazard  = valid && (rs1Busy || rs2Busy)
    //   hazards(i) := valid && (rs1Busy || rs2Busy)

    //   // hzRange.head.haltWhen(hazard)
    // }
    // hzRange.head.haltWhen(hazards.orR)
    


    val hazards = RegInit(Bits(hzRange.size bits)) init(0)

      val stall = new Area {

        // stage.haltWhen(hazard)
        // hzRange.take(2).tail.head.haltWhen(hazard)
        //TODO: NEED HALT DISPATCH. CANDIDATES AND SLOT architecture
    }

    val init = Counter(1 to 5)
    when(init =/= 5) {
      init.increment()
      regBusy.clearAll()
      // hazards.clearAll()
    }
    regBusy.simPublic()
    val inValue = init.value
    inValue.simPublic()
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
