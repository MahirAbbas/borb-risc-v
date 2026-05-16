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
import borb.fetch.{Fetch, PC}
// import borb.frontend.AluOp
import spinal.core.sim._
import scala.collection.immutable.LazyList.cons
import scala.collection.mutable.ArrayBuffer
import borb.common.MicroCode.uopADDI
import borb.common.{LaneKey, Common}
import borb.common.MicroCode
import borb.execute.FunctionalUnit

object Dispatch extends AreaObject {
  val SENDTOALU = Payload(Bool())
  val SENDTOBRANCH = Payload(Bool())
  val SENDTOAGU = Payload(Bool())
}

case class ExecutionUnit(
    name: String,
    pipe: BackendPipe.E,
    issueSlots: Int = 1,
    functionalUnits: Seq[FunctionalUnit] = Seq.empty
) {
  val supportedUops: Seq[MicroCode.E] = functionalUnits.flatMap(_.supportedUops)

  def accepts(selectedPipe: BackendPipe.C): Bool = selectedPipe === pipe

  def supports(uop: MicroCode.E): Boolean = supportedUops.contains(uop)

  def supportsHw(microCode: MicroCode.C): Bool = {
    supportedUops.map(microCode === _).reduceOption(_ || _).getOrElse(False)
  }
}

case class DispatchSkidSlot() extends Bundle {
  val pc = UInt(64 bits)
  val instruction = Bits(32 bits)
  val epoch = UInt(16 bits)
  val fetchSeq = UInt(32 bits)
  val fetchBundleSeq = UInt(32 bits)
  val fetchFtqIdx = UInt(8 bits)
  val fetchSlotIdx = UInt(2 bits)
  val fetchSlotCount = UInt(2 bits)
  val fetchBlockPc = UInt(64 bits)
  val fetchByteOffset = UInt(4 bits)
  val fetchPredictedValid = Bool()
  val fetchPredictedTaken = Bool()
  val fetchPredictedTarget = UInt(64 bits)

  val decodedInstruction = Bits(32 bits)
  val isCompressed = Bool()
  val legal = borb.frontend.YESNO()
  val isFloat = borb.frontend.YESNO()
  val isVec = borb.frontend.YESNO()
  val usesLdq = borb.frontend.YESNO()
  val usesStq = borb.frontend.YESNO()
  val microCode = borb.common.MicroCode()
  val rdAddr = Bits(5 bits)
  val rs1Addr = Bits(5 bits)
  val rs2Addr = Bits(5 bits)
  val rs3Addr = Bits(5 bits)
  val valid = Bool()
  val decodeIllegal = Bool()
}

case class Dispatch(
    dispatchNode: CtrlLink,
    hzRange: Seq[CtrlLink],
    pipeline: StageCtrlPipeline,
    functionalUnits: Seq[FunctionalUnit] = Seq.empty,
    intBypassReady: Seq[Bool] = Seq()
) extends Area {

  // import borb.decode.Decoder._
  import Dispatch._
  // val op = uop.toStream

  // if uop match EU uop
  // check hazards
  // set EU to fire
  val executionUnits = Seq(
    ExecutionUnit(
      "alu0",
      BackendPipe.Alu0,
      functionalUnits = functionalUnits.filter(
        _.executionUnit == borb.frontend.ExecutionUnitEnum.ALU
      )
    ),
    ExecutionUnit(
      "branch",
      BackendPipe.Branch,
      functionalUnits = functionalUnits.filter(
        _.executionUnit == borb.frontend.ExecutionUnitEnum.BR
      )
    ),
    ExecutionUnit(
      "loadStore",
      BackendPipe.LoadStore,
      functionalUnits = functionalUnits.filter(
        _.executionUnit == borb.frontend.ExecutionUnitEnum.AGU
      )
    ),
    ExecutionUnit(
      "fpu",
      BackendPipe.Falu,
      functionalUnits = functionalUnits.filter(
        _.executionUnit == borb.frontend.ExecutionUnitEnum.FPU
      )
    )
  )

  class DispatchLaneArea(laneId: Int) extends dispatchNode.Area {
    import borb.common.Common._

    val laneKey = LaneKey(laneId)

    def up[T <: Data](payload: Payload[T]): T =
      dispatchNode.up(payload, laneKey)
    def down[T <: Data](payload: Payload[T]): T =
      dispatchNode.down(payload, laneKey)

    val issueProps = IssueSemantics.classify(up(Decoder.MicroCode))
    val selectedPipe = BackendPipe.select(up(Decoder.MicroCode), issueProps)
    val decodedValid = up(Decoder.VALID)

    down(LANE_SEL) := False
    down(IssueSemantics.PROPS) := issueProps
    down(BackendIssue.SELECTED_PIPE) := Mux(
      decodedValid,
      selectedPipe,
      BackendPipe.None
    )
    down(SENDTOALU) := False
    down(SENDTOBRANCH) := False
    down(SENDTOAGU) := False
    for (fu <- functionalUnits) {
      down(fu.SEL) := False
    }
  }

  val laneDispatch =
    for (laneId <- 0 until Decoder.LANES) yield new DispatchLaneArea(laneId)

  val scheduler = new dispatchNode.Area {
    import borb.common.Common._

    // TODO(writeback): dual-lane WAW is architecturally OK only if writeback
    // and retirement preserve lane order. When writeback becomes 2-wide,
    // guarantee lane 0 commits/writes before lane 1 for same-rd WAW cases.
    //
    // TODO(dispatch): this execution-unit table is the initial scheduling
    // model. Wire it to concrete FunctionalUnit instances so scheduling
    // decisions are made from FU-supported uops, not duplicated pipe guesses.
    // As more execution units are added, replace same-pipe conflict checks
    // with capacity-aware issue-slot allocation per ExecutionUnit.
    //
    // TODO(control): dispatch should use predictor/recovery metadata to
    // enforce the frontend invariant that no younger lane issues after a
    // stopping control-flow instruction. For now lane 0 control flow forces
    // lane 1 through the skid path.
    //
    // TODO(trap-order): when exception/trap plumbing becomes lane-aware, lane
    // 1 must not issue or side-effect before a lane 0 illegal/trap packet.
    //
    // TODO(bypass): lane 1 RAW on lane 0 currently falls back to whole-stage
    // hazard handling. Add same-bundle or next-stage bypass before relaxing it.
    //
    // TODO(src): source/regfile needs explicit 2-wide read-port allocation.
    // Worst case is four integer reads plus FP/vector source pressure.
    //
    // TODO(branch-pred): dispatch should consume lane-local prediction
    // metadata instead of only relying on micro-op semantics.

    val skidValid = RegInit(False)
    val skid = Reg(DispatchSkidSlot()) init (DispatchSkidSlot().getZero)

    def readSlot(key: Any): DispatchSkidSlot = {
      val slot = DispatchSkidSlot()
      slot.pc := up(PC.PC, key)
      slot.instruction := up(Decoder.INSTRUCTION, key)
      slot.epoch := up(Common.SPEC_EPOCH, key)
      slot.fetchSeq := up(Fetch.FETCH_SEQ, key)
      slot.fetchBundleSeq := up(Fetch.FETCH_BUNDLE_SEQ, key)
      slot.fetchFtqIdx := up(Fetch.FETCH_FTQ_IDX, key)
      slot.fetchSlotIdx := up(Fetch.FETCH_SLOT_IDX, key)
      slot.fetchSlotCount := up(Fetch.FETCH_SLOT_COUNT, key)
      slot.fetchBlockPc := up(Fetch.FETCH_BLOCK_PC, key)
      slot.fetchByteOffset := up(Fetch.FETCH_BYTE_OFFSET, key)
      slot.fetchPredictedValid := up(Fetch.FETCH_PREDICTED_VALID, key)
      slot.fetchPredictedTaken := up(Fetch.FETCH_PREDICTED_TAKEN, key)
      slot.fetchPredictedTarget := up(Fetch.FETCH_PREDICTED_TARGET, key)
      slot.decodedInstruction := up(Decoder.DECODED_INSTRUCTION, key)
      slot.isCompressed := up(Decoder.IS_COMPRESSED, key)
      slot.legal := up(Decoder.LEGAL, key)
      slot.isFloat := up(Decoder.IS_FLOAT, key)
      slot.isVec := up(Decoder.IS_VEC, key)
      slot.usesLdq := up(Decoder.USES_LDQ, key)
      slot.usesStq := up(Decoder.USES_STQ, key)
      slot.microCode := up(Decoder.MicroCode, key)
      slot.rdAddr := up(Decoder.RD_ADDR, key)
      slot.rs1Addr := up(Decoder.RS1_ADDR, key)
      slot.rs2Addr := up(Decoder.RS2_ADDR, key)
      slot.rs3Addr := up(Decoder.RS3_ADDR, key)
      slot.valid := up(Decoder.VALID, key)
      slot.decodeIllegal := up(Decoder.DECODE_ILLEGAL, key)
      slot
    }

    def writeSlot(key: Any, slot: DispatchSkidSlot): Unit = {
      down(PC.PC, key).allowOverride := slot.pc
      down(Decoder.INSTRUCTION, key).allowOverride := slot.instruction
      down(Common.SPEC_EPOCH, key).allowOverride := slot.epoch
      down(Fetch.FETCH_SEQ, key).allowOverride := slot.fetchSeq
      down(Fetch.FETCH_BUNDLE_SEQ, key).allowOverride := slot.fetchBundleSeq
      down(Fetch.FETCH_FTQ_IDX, key).allowOverride := slot.fetchFtqIdx
      down(Fetch.FETCH_SLOT_IDX, key).allowOverride := slot.fetchSlotIdx
      down(Fetch.FETCH_SLOT_COUNT, key).allowOverride := slot.fetchSlotCount
      down(Fetch.FETCH_BLOCK_PC, key).allowOverride := slot.fetchBlockPc
      down(Fetch.FETCH_BYTE_OFFSET, key).allowOverride := slot.fetchByteOffset
      down(
        Fetch.FETCH_PREDICTED_VALID,
        key
      ).allowOverride := slot.fetchPredictedValid
      down(
        Fetch.FETCH_PREDICTED_TAKEN,
        key
      ).allowOverride := slot.fetchPredictedTaken
      down(
        Fetch.FETCH_PREDICTED_TARGET,
        key
      ).allowOverride := slot.fetchPredictedTarget
      down(
        Decoder.DECODED_INSTRUCTION,
        key
      ).allowOverride := slot.decodedInstruction
      down(Decoder.IS_COMPRESSED, key).allowOverride := slot.isCompressed
      down(Decoder.LEGAL, key).allowOverride := slot.legal
      down(Decoder.IS_FLOAT, key).allowOverride := slot.isFloat
      down(Decoder.IS_VEC, key).allowOverride := slot.isVec
      down(Decoder.USES_LDQ, key).allowOverride := slot.usesLdq
      down(Decoder.USES_STQ, key).allowOverride := slot.usesStq
      down(Decoder.MicroCode, key).allowOverride := slot.microCode
      down(Decoder.RD_ADDR, key).allowOverride := slot.rdAddr
      down(Decoder.RS1_ADDR, key).allowOverride := slot.rs1Addr
      down(Decoder.RS2_ADDR, key).allowOverride := slot.rs2Addr
      down(Decoder.RS3_ADDR, key).allowOverride := slot.rs3Addr
      down(Decoder.VALID, key).allowOverride := slot.valid
      down(Decoder.DECODE_ILLEGAL, key).allowOverride := slot.decodeIllegal
    }

    val lane0Key = LaneKey.Lane0
    val lane1Key = LaneKey.Lane1
    val lane0Valid = up.isValid && up(Decoder.VALID, lane0Key)
    val lane1Valid = up.isValid && up(Decoder.VALID, lane1Key)
    val lane0Props = IssueSemantics.classify(up(Decoder.MicroCode, lane0Key))
    val lane1Props = IssueSemantics.classify(up(Decoder.MicroCode, lane1Key))
    val lane0Pipe =
      BackendPipe.select(up(Decoder.MicroCode, lane0Key), lane0Props)
    val lane1Pipe =
      BackendPipe.select(up(Decoder.MicroCode, lane1Key), lane1Props)
    val lane0FuHits =
      functionalUnits.map(_.supportsHw(up(Decoder.MicroCode, lane0Key)))
    val lane1FuHits =
      functionalUnits.map(_.supportsHw(up(Decoder.MicroCode, lane1Key)))
    val lane0HasFu =
      if (functionalUnits.isEmpty) lane0Valid
      else lane0FuHits.foldLeft(False)(_ || _)
    val lane1HasFu =
      if (functionalUnits.isEmpty) lane1Valid
      else lane1FuHits.foldLeft(False)(_ || _)
    val lane0FuSelected = lane0FuHits.zipWithIndex.map { case (hit, index) =>
      hit && !lane0FuHits.take(index).foldLeft(False)(_ || _)
    }
    val lane1FuSelected = lane1FuHits.zipWithIndex.map { case (hit, index) =>
      hit && !lane1FuHits.take(index).foldLeft(False)(_ || _)
    }

    val structuralConflict = lane0Valid && lane1Valid && executionUnits
      .map { eu =>
        eu.issueSlots == 1 generate (eu
          .accepts(lane0Pipe) && eu.accepts(lane1Pipe))
      }
      .foldLeft(False)(_ || _)
    val sameFuConflict = lane0Valid && lane1Valid && functionalUnits.indices
      .map { index =>
        lane0FuSelected(index) && lane1FuSelected(index)
      }
      .foldLeft(False)(_ || _)
    val memoryOrderingConflict = lane0Valid && lane1Valid &&
      (lane0Props.isLoad || lane0Props.isStore) &&
      (lane1Props.isLoad || lane1Props.isStore)
    val controlFlowBarrier = lane0Valid && lane0Props.isControlFlow
    val lane0ExceptionBarrier =
      up.isValid && up(Decoder.DECODE_ILLEGAL, lane0Key)
    val lane1RawAfterLane0 = lane0Valid && lane1Valid && {
      val lane0Rd = up(Decoder.RD_ADDR, lane0Key)
      val lane1Rs1 = up(Decoder.RS1_ADDR, lane1Key)
      val lane1Rs2 = up(Decoder.RS2_ADDR, lane1Key)
      val lane1Rs3 = up(Decoder.RS3_ADDR, lane1Key)
      val lane0WritesInt = lane0Props.writesIntRd && (lane0Rd =/= 0)
      val lane0WritesFp = lane0Props.writesFpRd && (lane0Rd =/= 0)
      (lane0WritesInt && (
        (lane1Props.readsIntRs1 && (lane1Rs1 === lane0Rd)) ||
          (lane1Props.readsIntRs2 && (lane1Rs2 === lane0Rd))
      )) ||
      (lane0WritesFp && (
        (lane1Props.readsFpRs1 && (lane1Rs1 === lane0Rd)) ||
          (lane1Props.readsFpRs2 && (lane1Rs2 === lane0Rd)) ||
          (lane1Props.readsFpRs3 && (lane1Rs3 === lane0Rd))
      ))
    }
    val lane1NeedsSkid = lane1Valid && (
      structuralConflict ||
        sameFuConflict ||
        memoryOrderingConflict ||
        controlFlowBarrier ||
        lane0ExceptionBarrier ||
        lane1RawAfterLane0
    )
    val lane0Issue = lane0Valid && lane0HasFu && !skidValid
    val lane1Issue = lane1Valid && lane1HasFu && !lane1NeedsSkid && !skidValid
    val unmatchedFu =
      (lane0Valid && !lane0HasFu) || (lane1Valid && !lane1HasFu && !lane1NeedsSkid)

    def driveIssueSignals(
        key: Any,
        issue: Bool,
        props: IssuePropertyBundle,
        pipe: BackendPipe.C
    ): Unit = {
      down(LANE_SEL, key).allowOverride := issue
      down(IssueSemantics.PROPS, key).allowOverride := props
      down(BackendIssue.SELECTED_PIPE, key).allowOverride := Mux(
        issue,
        pipe,
        BackendPipe.None
      )
      down(SENDTOALU, key).allowOverride := issue && props.fuMask(0)
      down(SENDTOBRANCH, key).allowOverride := issue && props.fuMask(1)
      down(SENDTOAGU, key).allowOverride := issue && props.fuMask(2)
    }

    driveIssueSignals(lane0Key, lane0Issue, lane0Props, lane0Pipe)
    driveIssueSignals(lane1Key, lane1Issue, lane1Props, lane1Pipe)
    for ((fu, index) <- functionalUnits.zipWithIndex) {
      down(fu.SEL, lane0Key).allowOverride := lane0Issue && lane0FuSelected(
        index
      )
      down(fu.SEL, lane1Key).allowOverride := lane1Issue && lane1FuSelected(
        index
      )
    }

    haltWhen(unmatchedFu)

    when(!skidValid && dispatchNode.up.isFiring && lane1NeedsSkid) {
      skid := readSlot(lane1Key)
      skidValid := True
    } elsewhen (skidValid && dispatchNode.down.isFiring) {
      skidValid := False
    }

    when(lane1NeedsSkid) {
      down(LANE_SEL, lane1Key).allowOverride := False
      down(Decoder.VALID, lane1Key).allowOverride := False
      down(
        BackendIssue.SELECTED_PIPE,
        lane1Key
      ).allowOverride := BackendPipe.None
      down(SENDTOALU, lane1Key).allowOverride := False
      down(SENDTOBRANCH, lane1Key).allowOverride := False
      down(SENDTOAGU, lane1Key).allowOverride := False
    }

    when(skidValid) {
      writeSlot(lane0Key, skid)
      val skidProps = IssueSemantics.classify(skid.microCode)
      val skidPipe = BackendPipe.select(skid.microCode, skidProps)
      val skidFuHits = functionalUnits.map(_.supportsHw(skid.microCode))
      val skidHasFu =
        if (functionalUnits.isEmpty) skid.valid
        else skidFuHits.foldLeft(False)(_ || _)
      driveIssueSignals(lane0Key, skid.valid && skidHasFu, skidProps, skidPipe)
      for ((fu, index) <- functionalUnits.zipWithIndex) {
        val earlierHit = skidFuHits.take(index).foldLeft(False)(_ || _)
        down(fu.SEL, lane0Key).allowOverride := skid.valid && skidFuHits(
          index
        ) && !earlierHit
      }
      down(LANE_SEL, lane1Key).allowOverride := False
      down(Decoder.VALID, lane1Key).allowOverride := False
      down(
        BackendIssue.SELECTED_PIPE,
        lane1Key
      ).allowOverride := BackendPipe.None
      down(SENDTOALU, lane1Key).allowOverride := False
      down(SENDTOBRANCH, lane1Key).allowOverride := False
      down(SENDTOAGU, lane1Key).allowOverride := False
      for (fu <- functionalUnits) {
        down(fu.SEL, lane1Key).allowOverride := False
      }
      down(LANE_MASK).allowOverride := B"01"
      // TODO(skid): once all later stages are lane-aware, turn this replay
      // path into an explicit ready/valid side queue so new decode input is
      // back-pressured while the skid entry is being emitted.
    }
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
      for (laneId <- 0 until Decoder.LANES) {
        val laneKey = LaneKey(laneId)
        val stValid = stage.up.isValid &&
          stage.up(Decoder.VALID, laneKey) &&
          stage.up(borb.common.Common.LANE_SEL, laneKey)
        val stRd = stage.up(Decoder.RD_ADDR, laneKey)
        val stProps = stage.up(IssueSemantics.PROPS, laneKey)
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
    }

    val writes = new dispatchNode.Area {
      def laneHazard(laneId: Int): Bool = {
        val laneKey = LaneKey(laneId)
        val valid = up.isValid && up(Decoder.VALID, laneKey)
        val rd = up(Decoder.RD_ADDR, laneKey)
        val rs1 = up(Decoder.RS1_ADDR, laneKey)
        val rs2 = up(Decoder.RS2_ADDR, laneKey)
        val rs3 = up(Decoder.RS3_ADDR, laneKey)
        val props = IssueSemantics.classify(up(Decoder.MicroCode, laneKey))

        val rs1Busy = props.readsIntRs1 &&
          (rs1 =/= 0) && regBusy(rs1.asUInt)
        val rs2Busy = props.readsIntRs2 &&
          (rs2 =/= 0) && regBusy(rs2.asUInt)
        val fpRs1Busy = props.readsFpRs1 && (rs1 =/= 0) && fpRegBusy(rs1.asUInt)
        val fpRs2Busy = props.readsFpRs2 && (rs2 =/= 0) && fpRegBusy(rs2.asUInt)
        val fpRs3Busy = props.readsFpRs3 && (rs3 =/= 0) && fpRegBusy(rs3.asUInt)

        valid && (rs1Busy || rs2Busy || fpRs1Busy || fpRs2Busy || fpRs3Busy)
      }

      val hazard = (0 until Decoder.LANES).map(laneHazard).reduce(_ || _)
      hazard.simPublic()

      haltWhen(hazard)
    }

    regBusy.simPublic()
    fpRegBusy.simPublic()
  }
  val hcs = new HazardChecker(hzRange, 32)

}
