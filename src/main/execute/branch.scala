
package borb.execute

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.fetch.PC
import borb.fetch.JumpCmd
import borb.frontend.Decoder._
import borb.common.Common._
import borb.common.LaneKey
import borb.common.MicroCode._
import borb.dispatch.SrcPlugin._
import borb.dispatch.Dispatch._
import borb.dispatch.IssueSemantics
import borb.frontend.YESNO
import borb.dispatch.RegFileWrite
import borb.frontend.ExecutionUnitEnum

object Branch extends AreaObject {
  val BRANCH_TAKEN = Payload(Bool())
  val BRANCH_TARGET = Payload(UInt(64 bits))
  val SupportedUops = Seq(
    uopBEQ,
    uopBNE,
    uopBLT,
    uopBGE,
    uopBLTU,
    uopBGEU,
    uopJAL,
    uopJALR
  )

  case class BranchResolution() extends Bundle {
    val condition = Bool()
    val target = UInt(64 bits)
    val isJump = Bool()
    val isBranch = Bool()
    val doJump = Bool()
    val misaligned = Bool()
    val willTrap = Bool()
    val fallthrough = UInt(64 bits)
  }

  def resolve(microCode: borb.common.MicroCode.C, src1Bits: Bits, src2Bits: Bits, pcValue: UInt, immBits: Bits, withCompressed: Boolean): BranchResolution = {
    val src1 = src1Bits.asSInt
    val src2 = src2Bits.asSInt
    val src1U = src1Bits.asUInt
    val src2U = src2Bits.asUInt
    val imm = immBits.asUInt
    val resolution = BranchResolution()

    switch(microCode) {
      is(uopBEQ)  { resolution.condition := src1 === src2 }
      is(uopBNE)  { resolution.condition := src1 =/= src2 }
      is(uopBLT)  { resolution.condition := src1 < src2 }
      is(uopBGE)  { resolution.condition := src1 >= src2 }
      is(uopBLTU) { resolution.condition := src1U < src2U }
      is(uopBGEU) { resolution.condition := src1U >= src2U }
      default     { resolution.condition := False }
    }

    switch(microCode) {
      is(uopJALR) {
        resolution.target := (src1U.asSInt + imm.asSInt).asUInt
        resolution.target(0) := False
      }
      default {
        resolution.target := (pcValue.asSInt + imm.asSInt).asUInt
      }
    }

    switch(microCode) {
      is(uopJAL, uopJALR) { resolution.isJump := True }
      default             { resolution.isJump := False }
    }

    switch(microCode) {
      is(uopBEQ, uopBNE, uopBLT, uopBGE, uopBLTU, uopBGEU) { resolution.isBranch := True }
      default                                              { resolution.isBranch := False }
    }

    resolution.doJump := resolution.isJump || (resolution.isBranch && resolution.condition)
    resolution.misaligned := (if(withCompressed) (resolution.target(0) =/= False) else (resolution.target(1 downto 0) =/= 0))
    resolution.willTrap := resolution.doJump && resolution.misaligned
    resolution.fallthrough := pcValue + Mux(False, U(2, 64 bits), U(4, 64 bits))
    resolution
  }
}

case class Branch(node : CtrlLink, pc : PC, withCompressed: Boolean = false) extends FunctionalUnit(ExecutionUnitEnum.BR) {
  import Branch._

  Branch.SupportedUops.foreach(add)

  val branchResolved = Bool()
  val actualTaken = Bool()
  val actualTarget = UInt(64 bits)
  val actualIsJump = Bool()
  val actualIsBranch = Bool()
  val fallthroughPc = UInt(64 bits)
  val logic = new node.Area {
    val execFire = up.isValid && up.isFiring
    val laneResolved = Vec(Bool(), borb.frontend.Decoder.LANES)
    val laneTaken = Vec(Bool(), borb.frontend.Decoder.LANES)
    val laneTarget = Vec(UInt(64 bits), borb.frontend.Decoder.LANES)
    val laneIsJump = Vec(Bool(), borb.frontend.Decoder.LANES)
    val laneIsBranch = Vec(Bool(), borb.frontend.Decoder.LANES)
    val laneFallthrough = Vec(UInt(64 bits), borb.frontend.Decoder.LANES)
    val laneWillTrap = Vec(Bool(), borb.frontend.Decoder.LANES)

    // MAY_FLUSH should NOT prevent branches from executing - it only marks 
    // instructions that may be squashed. The flushing instruction completes normally
    // (stage 6 is excluded from self-throw in CPU.scala).
    // Redirect must be one-shot per actual execute-stage firing transaction.
    for(laneId <- 0 until borb.frontend.Decoder.LANES) {
      val laneKey = LaneKey(laneId)
      val pcValue = up(PC.PC, laneKey)
      val resolution = Branch.resolve(up(MicroCode, laneKey), up(RS1, laneKey), up(RS2, laneKey), pcValue, up(IMMED, laneKey), withCompressed = withCompressed)
      val isBrUnit = up(IssueSemantics.PROPS, laneKey).isControlFlow
      val laneFire = isBrUnit && up(LANE_SEL, laneKey) && up(SENDTOBRANCH, laneKey) && up(VALID, laneKey) && execFire
      val doJump = resolution.doJump && laneFire
      val fallthrough = pcValue + Mux(up(IS_COMPRESSED, laneKey), U(2, 64 bits), U(4, 64 bits))
      val willTrap = doJump && resolution.misaligned

      down(BRANCH_TAKEN, laneKey) := doJump && !willTrap
      down(BRANCH_TARGET, laneKey) := resolution.target
      laneResolved(laneId) := (resolution.isJump || resolution.isBranch) && laneFire
      laneTaken(laneId) := doJump && !willTrap
      laneTarget(laneId) := resolution.target
      laneIsJump(laneId) := resolution.isJump
      laneIsBranch(laneId) := resolution.isBranch
      laneFallthrough(laneId) := fallthrough
      laneWillTrap(laneId) := willTrap

      when(laneFire && resolution.isJump) {
        val isX0 = up(RD_ADDR, laneKey).asUInt === 0
        down(WriteBack.RESULT, laneKey).address.allowOverride := up(RD_ADDR, laneKey).asUInt
        down(WriteBack.RESULT, laneKey).data.allowOverride := isX0 ? B(0, 64 bits) | fallthrough.asBits
        down(WriteBack.RESULT, laneKey).valid.allowOverride := (up(LEGAL, laneKey) === YESNO.Y) && up(VALID, laneKey) && !willTrap
      }
    }

    val selectedLane1 = !laneResolved(0) && laneResolved(1)
    branchResolved := laneResolved.asBits.orR
    actualTaken := Mux(selectedLane1, laneTaken(1), laneTaken(0))
    actualTarget := Mux(selectedLane1, laneTarget(1), laneTarget(0))
    actualIsJump := Mux(selectedLane1, laneIsJump(1), laneIsJump(0))
    actualIsBranch := Mux(selectedLane1, laneIsBranch(1), laneIsBranch(0))
    fallthroughPc := Mux(selectedLane1, laneFallthrough(1), laneFallthrough(0))
    val willTrap = Mux(selectedLane1, laneWillTrap(1), laneWillTrap(0))
    val target = actualTarget

    val jumpCmd = Flow(JumpCmd(pc.addressWidth))
    jumpCmd.valid := actualTaken
    jumpCmd.payload.target := actualTarget
    jumpCmd.payload.is_jump := actualIsJump
    jumpCmd.payload.is_branch := actualIsBranch
  }
}
