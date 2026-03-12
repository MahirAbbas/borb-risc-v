package borb.dispatch

import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.sim._
import spinal.lib.misc.pipeline._

import borb.frontend.Decoder._
import borb.frontend.Decoder
import borb.frontend.ExecutionUnitEnum
import borb.dispatch.ExecutionRoute._
import borb.execute.WriteBack
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
  val WRITES_INT_RD = Payload(Bool())
  val WRITES_FP_RD = Payload(Bool())
  val READS_INT_RS1 = Payload(Bool())
  val READS_INT_RS2 = Payload(Bool())
  val READS_FP_RS1 = Payload(Bool())
  val READS_FP_RS2 = Payload(Bool())
  val READS_FP_RS3 = Payload(Bool())

  case class RouteRequest(
      priority: Int,
      matches: Bool,
      ready: Bool,
      structuralGroup: SpinalEnumElement[StructuralGroup.type] = StructuralGroup.NONE,
      sendAlu: Boolean = false,
      sendBranch: Boolean = false,
      sendAgu: Boolean = false,
      newRoute: Boolean = false,
      euId: SpinalEnumElement[EuId.type] = EuId.NONE,
      fuKind: SpinalEnumElement[FuKind.type] = FuKind.NONE
  )

  object RegisterClass extends SpinalEnum {
    val NONE, INT, FP, VEC = newElement()
  }

  object ProducerLatency extends SpinalEnum {
    val NONE, EXECUTE, WRITEBACK, VARIABLE = newElement()
  }

  object StructuralGroup extends SpinalEnum {
    val NONE, INT, BRANCH, MEMORY, FP, VECTOR, CUSTOM = newElement()
  }

  case class ProducerDescriptor() extends Bundle {
    val valid = Bool()
    val regClass = RegisterClass()
    val rd = UInt(5 bits)
    val bypassReady = Bool()
    val latency = ProducerLatency()
    val structuralGroup = StructuralGroup()
    val reservesStructure = Bool()
  }

  case class ReadDescriptor() extends Bundle {
    val enable = Bool()
    val regClass = RegisterClass()
    val address = UInt(5 bits)
  }

  case class ProducerSlotSpec(
      name: String,
      regClass: SpinalEnumElement[RegisterClass.type],
      writes: CtrlLink => Bool,
      rd: CtrlLink => UInt,
      bypassReady: (Int, CtrlLink) => Bool,
      latency: (Int, CtrlLink) => SpinalEnumElement[ProducerLatency.type],
      structuralGroup: CtrlLink => StructuralGroup.C
  ) {
    def materialize(stageId: Int, ctrl: CtrlLink): ProducerDescriptor = {
      val desc = ProducerDescriptor()
      desc.valid := writes(ctrl)
      desc.regClass := regClass
      desc.rd := rd(ctrl)
      desc.bypassReady := bypassReady(stageId, ctrl)
      desc.latency := latency(stageId, ctrl)
      desc.structuralGroup := structuralGroup(ctrl)
      desc.reservesStructure := desc.valid && (desc.latency === ProducerLatency.VARIABLE) && (desc.structuralGroup =/= StructuralGroup.NONE)
      desc
    }
  }

  case class HazardStage(
      id: Int,
      ctrl: CtrlLink,
      producerSlots: Seq[ProducerSlotSpec] = Dispatch.defaultProducerSlots
  )

  def stageHasLane(ctrl: CtrlLink): Bool = {
    ctrl.up.isValid && ctrl(Decoder.VALID) && ctrl(borb.common.Common.LANE_SEL)
  }

  def stageWritesIntRd(ctrl: CtrlLink): Bool = {
    stageHasLane(ctrl) && ctrl(WRITES_INT_RD) && (ctrl(Decoder.RD_ADDR) =/= 0)
  }

  def stageWritesFpRd(ctrl: CtrlLink): Bool = {
    stageHasLane(ctrl) && ctrl(WRITES_FP_RD) && (ctrl(Decoder.RD_ADDR) =/= 0)
  }

  def stageWritesFpCsr(ctrl: CtrlLink): Bool = {
    stageHasLane(ctrl) &&
    borb.dispatch.ExecutionHazardMeta.writesFpCsrFromInsn(ctrl(Decoder.DECODED_INSTRUCTION))
  }

  def stageIntBypassReady(stageId: Int, ctrl: CtrlLink): Bool = {
    val writesInt = stageWritesIntRd(ctrl)
    stageId match {
      case 6 => writesInt && ctrl.down(WriteBack.RESULT).valid
      case 7 => writesInt && ctrl.up(WriteBack.RESULT).valid
      case _ => False
    }
  }

  def stageStructuralGroup(ctrl: CtrlLink): StructuralGroup.C = {
    val group = StructuralGroup()
    group := StructuralGroup.NONE
    when(ctrl(NEW_ROUTE_VALID)) {
      switch(ctrl(NEW_EU_ID)) {
        is(EuId.IntEu) { group := StructuralGroup.INT }
        is(EuId.BranchEu) { group := StructuralGroup.BRANCH }
        is(EuId.AguEu) { group := StructuralGroup.MEMORY }
      }
    } otherwise {
      switch(ctrl(Decoder.EXECUTION_UNIT)) {
        is(ExecutionUnitEnum.ALU) { group := StructuralGroup.INT }
        is(ExecutionUnitEnum.BR) { group := StructuralGroup.BRANCH }
        is(ExecutionUnitEnum.AGU) {
          group := Mux(ctrl(Decoder.IS_FP) === borb.frontend.YESNO.Y, StructuralGroup.FP, StructuralGroup.MEMORY)
        }
      }
    }
    group
  }

  val intProducerSlot = ProducerSlotSpec(
    name = "int-rd",
    regClass = RegisterClass.INT,
    writes = stageWritesIntRd,
    rd = _.apply(Decoder.RD_ADDR).asUInt,
    bypassReady = stageIntBypassReady,
    latency = (stageId, _) => if (stageId == 7) ProducerLatency.WRITEBACK else ProducerLatency.EXECUTE,
    structuralGroup = stageStructuralGroup
  )

  val fpProducerSlot = ProducerSlotSpec(
    name = "fp-rd",
    regClass = RegisterClass.FP,
    writes = stageWritesFpRd,
    rd = _.apply(Decoder.RD_ADDR).asUInt,
    bypassReady = (_, _) => False,
    latency = (_, _) => ProducerLatency.VARIABLE,
    structuralGroup = _ => StructuralGroup.FP
  )

  val defaultProducerSlots: Seq[ProducerSlotSpec] = Seq(intProducerSlot, fpProducerSlot)
}

case class Dispatch(
    dispatchNode: CtrlLink,
    hzRange: Seq[Dispatch.HazardStage],
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
    import borb.dispatch.ExecutionHazardMeta._
    down(LANE_SEL) := False

    // when(up.isValid) {
    //   eus.foreach(f => f.SEL := False)
    // }
    down(SENDTOALU) := False
    down(SENDTOBRANCH) := False
    down(SENDTOAGU) := False
    down(NEW_ROUTE_VALID) := False
    down(NEW_EU_ID) := EuId.NONE
    down(NEW_FU_KIND) := FuKind.NONE
    val readsIntRs1Meta = up(Decoder.RS1TYPE) === borb.frontend.REGFILE.RSTYPE.RS_INT
    val readsIntRs2Meta = up(Decoder.RS2TYPE) === borb.frontend.REGFILE.RSTYPE.RS_INT
    val readsFpRs1Meta = readsFpRs1FromInsn(up(Decoder.DECODED_INSTRUCTION))
    val readsFpRs2Meta = readsFpRs2FromInsn(up(Decoder.DECODED_INSTRUCTION))
    val readsFpRs3Meta = readsFpRs3FromInsn(up(Decoder.DECODED_INSTRUCTION))
    val writesFpRdMeta = writesFpRdFromInsn(up(Decoder.DECODED_INSTRUCTION))

    // Logic to select an execution lane implies LANE_SEL is true
    // Crucially, it must only be True if we are actually firing (not stalled by hazard)
    // LANE_SEL acts as the valid bit for the lane.
    val firing = up.isFiring
    val intEu = new borb.dispatch.IntExecutionUnit(up(Decoder.MicroCode), ready = True)
    val branchEu = new borb.dispatch.BranchExecutionUnit(up(Decoder.MicroCode), ready = True)
    val memoryEu = new borb.dispatch.MemoryExecutionUnit(
      up(Decoder.MicroCode),
      up(Decoder.IS_FP) === borb.frontend.YESNO.Y,
      ready = True
    )
    val migratedIntRoute = up(Decoder.VALID) && intEu.routeValid
    val migratedBranchRoute = up(Decoder.VALID) && branchEu.routeValid
    val migratedMemoryRoute = up(Decoder.VALID) && memoryEu.routeValid

    val routeRequests = Seq(
      RouteRequest(
        priority = 10,
        matches = migratedIntRoute,
        ready = intEu.selected,
        structuralGroup = StructuralGroup.INT,
        sendAlu = true,
        newRoute = true,
        euId = IntExecutionUnit.spec.id,
        fuKind = IntExecutionUnit.intComputeFu.kind
      ),
      RouteRequest(
        priority = 20,
        matches = migratedBranchRoute,
        ready = branchEu.selected,
        structuralGroup = StructuralGroup.BRANCH,
        sendBranch = true,
        newRoute = true,
        euId = BranchExecutionUnit.spec.id,
        fuKind = BranchExecutionUnit.controlFlowFu.kind
      ),
      RouteRequest(
        priority = 30,
        matches = migratedMemoryRoute,
        ready = memoryEu.selected,
        structuralGroup = StructuralGroup.MEMORY,
        sendAgu = true,
        newRoute = true,
        euId = MemoryExecutionUnit.spec.id,
        fuKind = MemoryExecutionUnit.memoryAccessFu.kind
      ),
      RouteRequest(
        priority = 110,
        matches = up(Decoder.VALID) && up(Decoder.EXECUTION_UNIT) === ExecutionUnitEnum.ALU,
        ready = True,
        structuralGroup = StructuralGroup.INT,
        sendAlu = true
      ),
      RouteRequest(
        priority = 120,
        matches = up(Decoder.VALID) && up(Decoder.EXECUTION_UNIT) === ExecutionUnitEnum.BR,
        ready = True,
        structuralGroup = StructuralGroup.BRANCH,
        sendBranch = true
      ),
      RouteRequest(
        priority = 130,
        matches = up(Decoder.VALID) && up(Decoder.EXECUTION_UNIT) === ExecutionUnitEnum.AGU,
        ready = True,
        structuralGroup = StructuralGroup.MEMORY,
        sendAgu = true
      )
    ).sortBy(_.priority)

    val inflightStructuralReservations = hzRange.tail.flatMap { stage =>
      stage.producerSlots.map(_.materialize(stage.id, stage.ctrl))
    }

    def structuralGroupAvailable(group: SpinalEnumElement[StructuralGroup.type]): Bool = {
      if (group == StructuralGroup.NONE || inflightStructuralReservations.isEmpty) {
        True
      } else {
        !inflightStructuralReservations
          .map { producer =>
            producer.valid &&
            producer.reservesStructure &&
            (producer.structuralGroup === group)
          }
          .reduce(_ || _)
      }
    }

    var routeTaken: Bool = False
    for (req <- routeRequests) {
      when(!routeTaken && req.matches) {
        val structuralReady = structuralGroupAvailable(req.structuralGroup)
        if (req.sendAlu) {
          down(SENDTOALU) := True
        }
        if (req.sendBranch) {
          down(SENDTOBRANCH) := True
        }
        if (req.sendAgu) {
          down(SENDTOAGU) := True
        }
        if (req.newRoute) {
          down(NEW_ROUTE_VALID) := True
          down(NEW_EU_ID) := req.euId
          down(NEW_FU_KIND) := req.fuKind
        }
        down(LANE_SEL) := firing && req.ready && structuralReady
      }
      routeTaken = routeTaken || req.matches
    }

    val writesIntRdMeta = writesIntRdFromMigratedRoute(
      up(Decoder.MicroCode),
      down(NEW_ROUTE_VALID),
      down(NEW_EU_ID),
      down(NEW_FU_KIND)
    ) || (!down(NEW_ROUTE_VALID) && (up(Decoder.RDTYPE) === borb.frontend.REGFILE.RDTYPE.RD_INT))
    down(WRITES_INT_RD) := writesIntRdMeta
    down(WRITES_FP_RD) := writesFpRdMeta
    down(READS_INT_RS1) := readsIntRs1Meta
    down(READS_INT_RS2) := readsIntRs2Meta
    down(READS_FP_RS1) := readsFpRs1Meta
    down(READS_FP_RS2) := readsFpRs2Meta
    down(READS_FP_RS3) := readsFpRs3Meta

    // Explicitly handle invalid/bubble case if needed, but default False covers it.
  }
  case class HazardChecker(hzRange: Seq[HazardStage], regCount: Int = 32)
      extends Area {
    import borb.dispatch.ExecutionHazardMeta._
    val producerDescriptors = hzRange.tail.flatMap { stage =>
      stage.producerSlots.map(_.materialize(stage.id, stage.ctrl))
    }

    // Keep per-class busy maps as debug views, but derive them from the
    // generic producer descriptor list instead of hardcoding the stage model.
    val regBusy = Bits(regCount bits)
    val fpRegBusy = Bits(regCount bits)
    regBusy.clearAll()
    fpRegBusy.clearAll()
    for (producer <- producerDescriptors) {
      when(producer.valid && (producer.regClass === RegisterClass.INT) && !producer.bypassReady) {
        regBusy(producer.rd) := True
      }
      when(producer.valid && (producer.regClass === RegisterClass.FP)) {
        fpRegBusy(producer.rd) := True
      }
    }

    def readDescriptor(enable: Bool, regClass: SpinalEnumElement[RegisterClass.type], address: UInt): ReadDescriptor = {
      val desc = ReadDescriptor()
      desc.enable := enable
      desc.regClass := regClass
      desc.address := address
      desc
    }

    def hasProducerHazard(read: ReadDescriptor): Bool = {
      if (producerDescriptors.isEmpty) {
        False
      } else {
        producerDescriptors
          .map { producer =>
            read.enable &&
            (read.address =/= 0) &&
            producer.valid &&
            (producer.regClass === read.regClass) &&
            (producer.rd === read.address) &&
            !producer.bypassReady
          }
          .reduce(_ || _)
      }
    }

    val writes = new dispatchNode.Area {
      val valid = up.isValid && up(Decoder.VALID)
      val rs1 = up(Decoder.RS1_ADDR)
      val rs2 = up(Decoder.RS2_ADDR)
      val insn = up(Decoder.DECODED_INSTRUCTION)
      val readsIntRs1 = up(Decoder.RS1TYPE) === borb.frontend.REGFILE.RSTYPE.RS_INT
      val readsIntRs2 = up(Decoder.RS2TYPE) === borb.frontend.REGFILE.RSTYPE.RS_INT
      val fpReadsRs1 = readsFpRs1FromInsn(insn)
      val fpReadsRs2 = readsFpRs2FromInsn(insn)
      val fpReadsRs3 = readsFpRs3FromInsn(insn)
      val fpCsrInteraction = readsFpCsrFromInsn(insn) || writesFpCsrFromInsn(insn)
      val consumerReads = Seq(
        readDescriptor(readsIntRs1, RegisterClass.INT, rs1.asUInt),
        readDescriptor(readsIntRs2, RegisterClass.INT, rs2.asUInt),
        readDescriptor(fpReadsRs1, RegisterClass.FP, insn(19 downto 15).asUInt),
        readDescriptor(fpReadsRs2, RegisterClass.FP, insn(24 downto 20).asUInt),
        readDescriptor(fpReadsRs3, RegisterClass.FP, insn(31 downto 27).asUInt)
      )
      val regHazard = consumerReads.map(hasProducerHazard).reduce(_ || _)
      val fpCsrHazard =
        if (hzRange.tail.isEmpty) {
          False
        } else {
          hzRange.tail.map(stage => fpCsrInteraction && stageWritesFpCsr(stage.ctrl)).reduce(_ || _)
        }
      val hazard = valid && (regHazard || fpCsrHazard)
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
