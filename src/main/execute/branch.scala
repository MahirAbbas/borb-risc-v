
package borb.execute

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.fetch.PC
import borb.fetch.JumpCmd
import borb.frontend.Decoder._
import borb.common.Common._
import borb.common.MicroCode._
import borb.dispatch.SrcPlugin._
import borb.dispatch.Dispatch._
import borb.dispatch.IssueSemantics
import borb.frontend.YESNO
import borb.dispatch.RegFileWrite

object Branch extends AreaObject {
  val BRANCH_TAKEN = Payload(Bool())
  val BRANCH_TARGET = Payload(UInt(64 bits))

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

case class Branch(node : CtrlLink, pc : PC, withCompressed: Boolean = false) extends Area {
  import Branch._

  val branchResolved = Bool()
  val actualTaken = Bool()
  val actualTarget = UInt(64 bits)
  val actualIsJump = Bool()
  val actualIsBranch = Bool()
  val fallthroughPc = UInt(64 bits)
  val logic = new node.Area {
    val pcValue = up(PC.PC)
    val resolution = Branch.resolve(up(MicroCode), up(RS1), up(RS2), pcValue, up(IMMED), withCompressed = withCompressed)
    val condition = resolution.condition
    val target = resolution.target
    val isJump = resolution.isJump
    val isBranch = resolution.isBranch
    // MAY_FLUSH should NOT prevent branches from executing - it only marks 
    // instructions that may be squashed. The flushing instruction completes normally
    // (stage 6 is excluded from self-throw in CPU.scala).
    // Redirect must be one-shot per actual execute-stage firing transaction.
    val isBrUnit = up(IssueSemantics.PROPS).isControlFlow
    val execFire = up.isValid && up.isFiring
    val doJump = resolution.doJump &&
      isBrUnit && up(LANE_SEL) && up(SENDTOBRANCH) && up(VALID) && execFire
    val fallthrough = pcValue + Mux(up(IS_COMPRESSED), U(2, 64 bits), U(4, 64 bits))
    val willTrap = doJump && resolution.misaligned

    // down(TRAP) := willTrap // Moved to CPU.scala logic integration
    down(BRANCH_TAKEN) := doJump && !willTrap
    down(BRANCH_TARGET) := resolution.target
    branchResolved := (resolution.isJump || resolution.isBranch) && isBrUnit && up(LANE_SEL) && up(SENDTOBRANCH) && up(VALID) && execFire
    actualTaken := doJump && !willTrap
    actualTarget := resolution.target
    actualIsJump := resolution.isJump
    actualIsBranch := resolution.isBranch
    fallthroughPc := fallthrough

    val jumpCmd = Flow(JumpCmd(pc.addressWidth))
    jumpCmd.valid := doJump && !willTrap // Mask jump if trapping
    jumpCmd.payload.target := resolution.target
    jumpCmd.payload.is_jump := resolution.isJump
    jumpCmd.payload.is_branch := resolution.isBranch
    
    when(isBrUnit && up(LANE_SEL) && up(SENDTOBRANCH) && up(VALID) && execFire) {
      when(resolution.isJump) {
        val isX0 = up(RD_ADDR).asUInt === 0
        down(WriteBack.RESULT).address.allowOverride := up(RD_ADDR).asUInt
        down(WriteBack.RESULT).data.allowOverride := isX0 ? B(0, 64 bits) | fallthrough.asBits
        // Squash writeback if trapping
        down(WriteBack.RESULT).valid.allowOverride := (up(LEGAL) === YESNO.Y) && up(VALID) && !willTrap
      }
    }
  }
}
