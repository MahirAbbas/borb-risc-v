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
import spinal.core.sim._
import scala.collection.immutable.LazyList.cons
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

case class Dispatch(
    dispatchNode: CtrlLink,
    hzRange: Seq[CtrlLink],
    pipeline: StageCtrlPipeline
) extends Area {

  // import borb.decode.Decoder._
  import Dispatch._
  // val op = uop.toStream

  // if uop match EU uop
  // check hazards
  // set EU to fire

  val logic = new dispatchNode.Area {
    import borb.common.Common._
    down(LANE_SEL) := False

    // when(up.isValid) {
    //   eus.foreach(f => f.SEL := False)
    // }
    down(SENDTOALU) := False
    down(SENDTOBRANCH) := False
    down(SENDTOAGU) := False

    // Logic to select an execution lane implies LANE_SEL is true
    // Crucially, it must only be True if we are actually firing (not stalled by hazard)
    // LANE_SEL acts as the valid bit for the lane.
    val firing = up.isFiring

    when(up(Decoder.EXECUTION_UNIT) === ExecutionUnitEnum.ALU) {
      down(SENDTOALU) := True
      down(LANE_SEL) := firing
    }
    when(up(Decoder.EXECUTION_UNIT) === ExecutionUnitEnum.BR) {
      down(SENDTOBRANCH) := True
      down(LANE_SEL) := firing
    }
    when(up(Decoder.EXECUTION_UNIT) === ExecutionUnitEnum.AGU) {
      down(SENDTOAGU) := True
      down(LANE_SEL) := firing
    }

    // Explicitly handle invalid/bubble case if needed, but default False covers it.
  }
  case class HazardChecker(hzRange: Seq[CtrlLink], regCount: Int = 32)
      extends Area {
    def isFlw(insn: Bits): Bool = {
      (insn(6 downto 0) === B"0000111") && (insn(14 downto 12) === B"010")
    }

    def isFsw(insn: Bits): Bool = {
      (insn(6 downto 0) === B"0100111") && (insn(14 downto 12) === B"010")
    }

    def isFcvtFToInt(insn: Bits): Bool = {
      (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"1100000")
    }

    def isFaddsubS(insn: Bits): Bool = {
      (insn(6 downto 0) === B"1010011") &&
      ((insn(31 downto 25) === B"0000000") || (insn(31 downto 25) === B"0000100"))
    }

    def isFmulS(insn: Bits): Bool = {
      (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"0001000")
    }

    def isFcvtIntToF(insn: Bits): Bool = {
      (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"1101000")
    }

    def isFmvXW(insn: Bits): Bool = {
      (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"1110000") &&
      (insn(24 downto 20) === B"00000") && (insn(14 downto 12) === B"000")
    }

    def isFmvWX(insn: Bits): Bool = {
      (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"1111000") &&
      (insn(24 downto 20) === B"00000") && (insn(14 downto 12) === B"000")
    }

    def isFclassS(insn: Bits): Bool = {
      (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"1110000") &&
      (insn(24 downto 20) === B"00000") && (insn(14 downto 12) === B"001")
    }

    def isFsgnjFamily(insn: Bits): Bool = {
      (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"0010000") &&
      ((insn(14 downto 12) === B"000") || (insn(14 downto 12) === B"001") || (insn(14 downto 12) === B"010"))
    }

    def isFminmaxS(insn: Bits): Bool = {
      (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"0010100") &&
      ((insn(14 downto 12) === B"000") || (insn(14 downto 12) === B"001"))
    }

    def isFcmpS(insn: Bits): Bool = {
      (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"1010000") &&
      ((insn(14 downto 12) === B"000") || (insn(14 downto 12) === B"001") || (insn(14 downto 12) === B"010"))
    }

    // Derive a combinational busy map from younger in-flight stages.
    // This avoids sticky scoreboard bits after flushes/stalls.
    val regBusy = Bits(regCount bits)
    val fpRegBusy = Bits(regCount bits)
    regBusy.clearAll()
    fpRegBusy.clearAll()
    for (stage <- hzRange.tail) {
      val stValid = stage.up.isValid && stage(Decoder.VALID) && stage(borb.common.Common.LANE_SEL)
      val stRd = stage(Decoder.RD_ADDR)
      val stInsn = stage(Decoder.DECODED_INSTRUCTION)
      val stWritesIntRd = stValid &&
        (stage(Decoder.RDTYPE) === borb.frontend.REGFILE.RDTYPE.RD_INT) &&
        (stRd =/= 0)
      val stWritesFpRd = stValid &&
        (stRd =/= 0) &&
        (isFlw(stInsn) || isFcvtIntToF(stInsn) || isFmvWX(stInsn) || isFsgnjFamily(stInsn) || isFminmaxS(stInsn) || isFaddsubS(stInsn) || isFmulS(stInsn))
      when(stWritesIntRd) {
        regBusy(stRd.asUInt) := True
      }
      when(stWritesFpRd) {
        fpRegBusy(stRd.asUInt) := True
      }
    }

    val writes = new dispatchNode.Area {
      val valid = up.isValid && up(Decoder.VALID)
      val rd = up(Decoder.RD_ADDR)
      val rs1Type = up(Decoder.RS1TYPE)
      val rs2Type = up(Decoder.RS2TYPE)
      val rs1 = up(Decoder.RS1_ADDR)
      val rs2 = up(Decoder.RS2_ADDR)
      val insn = up(Decoder.DECODED_INSTRUCTION)

      val rs1Busy = (rs1Type === borb.frontend.REGFILE.RSTYPE.RS_INT) &&
        (rs1 =/= 0) && regBusy(rs1.asUInt)
      val rs2Busy = (rs2Type === borb.frontend.REGFILE.RSTYPE.RS_INT) &&
        (rs2 =/= 0) && regBusy(rs2.asUInt)
      val fpReadsRs1 = isFcvtFToInt(insn) || isFmvXW(insn) || isFclassS(insn) || isFsgnjFamily(insn) || isFcmpS(insn) || isFminmaxS(insn) || isFaddsubS(insn)
      val fpRs1Busy = fpReadsRs1 && (insn(19 downto 15) =/= 0) && fpRegBusy(insn(19 downto 15).asUInt)
      val fpReadsRs2 = isFsw(insn) || isFsgnjFamily(insn) || isFcmpS(insn) || isFminmaxS(insn) || isFaddsubS(insn)
      val fpRs2Busy = fpReadsRs2 && (insn(24 downto 20) =/= 0) && fpRegBusy(insn(24 downto 20).asUInt)

      val hazard = valid && (rs1Busy || rs2Busy || fpRs1Busy || fpRs2Busy)
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
