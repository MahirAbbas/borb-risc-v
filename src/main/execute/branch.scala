
package borb.execute

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.fetch.PC
import borb.fetch.JumpCmd
import borb.frontend.Decoder._
import borb.frontend.ExecutionUnitEnum
import borb.common.Common._
import borb.common.MicroCode._
import borb.dispatch.SrcPlugin._
import borb.dispatch.Dispatch._
import borb.frontend.YESNO
import borb.dispatch.RegFileWrite

object Branch extends AreaObject {
  val BRANCH_TAKEN = Payload(Bool())
  val BRANCH_TARGET = Payload(UInt(64 bits))
}

case class Branch(node : CtrlLink, pc : PC) extends Area {  
  import Branch._

  val branchResolved = Bool()
  val logic = new node.Area {
    val src1 = up(RS1).asSInt
    val src2 = up(RS2).asSInt
    val src1U = up(RS1).asUInt
    val src2U = up(RS2).asUInt
    val pcValue = up(PC.PC)
    val imm = up(IMMED).asUInt
    val insn = up(INSTRUCTION)
    val bImm = S(insn(31) ## insn(7) ## insn(30 downto 25) ## insn(11 downto 8) ## False).resize(64)

    val condition = Bool()
    switch(up(MicroCode)) {
      is(uopBEQ)  { condition := src1 === src2 }
      is(uopBNE)  { condition := src1 =/= src2 }
      is(uopBLT)  { condition := src1 < src2 }
      is(uopBGE)  { condition := src1 >= src2 }
      is(uopBLTU) { condition := src1U < src2U }
      is(uopBGEU) { condition := src1U >= src2U }
      default     { condition := False }
    }

    val target = UInt(64 bits)
    switch(up(MicroCode)) {
      is(uopJALR) { 
        target := (src1U.asSInt + imm.asSInt).asUInt 
        target(0) := False
      }
      is(uopBEQ, uopBNE, uopBLT, uopBGE, uopBLTU, uopBGEU) {
        // Compute B-type immediate directly from instruction bits to avoid
        // stage-crossing immediate aliasing on control-flow ops.
        target := (pcValue.asSInt + bImm).asUInt
      }
      default     { target := (pcValue.asSInt + imm.asSInt).asUInt }
    }

    val isJump = Bool()
    switch(up(MicroCode)) {
      is(uopJAL)  { isJump := True }
      is(uopJALR) { isJump := True }
      default     { isJump := False }
    }

    val isBranch = Bool()
    switch(up(MicroCode)) {
      is(uopBEQ)  { isBranch := True }
      is(uopBNE)  { isBranch := True }
      is(uopBLT)  { isBranch := True }
      is(uopBGE)  { isBranch := True }
      is(uopBLTU) { isBranch := True }
      is(uopBGEU) { isBranch := True }
      default     { isBranch := False }
    }
    // MAY_FLUSH should NOT prevent branches from executing - it only marks 
    // instructions that may be squashed. The flushing instruction completes normally
    // (stage 6 is excluded from self-throw in CPU.scala).
    // Redirect must be one-shot per retired branch/jump.
    // Gate with down.isFiring so stalled execute cycles don't re-issue redirects.
    val isBrUnit = up(EXECUTION_UNIT) === ExecutionUnitEnum.BR
    val doJump = (isJump || (isBranch && condition)) &&
      isBrUnit && up(LANE_SEL) && up(SENDTOBRANCH) && up(VALID) && down.isFiring
    val misaligned = target(1 downto 0) =/= 0
    val willTrap = doJump && misaligned

    // down(TRAP) := willTrap // Moved to CPU.scala logic integration
    down(BRANCH_TAKEN) := doJump && !willTrap
    down(BRANCH_TARGET) := target
    branchResolved := (isJump || isBranch) && isBrUnit && up(LANE_SEL) && up(SENDTOBRANCH) && down.isFiring

    val jumpCmd = Flow(JumpCmd(pc.addressWidth))
    jumpCmd.valid := doJump && !willTrap // Mask jump if trapping
    jumpCmd.payload.target := target
    jumpCmd.payload.is_jump := isJump
    jumpCmd.payload.is_branch := isBranch
    
    when(isBrUnit && up(LANE_SEL) && up(SENDTOBRANCH)) {
      when(isJump) {
        val isX0 = up(RD_ADDR).asUInt === 0
        down(WriteBack.RESULT).address := up(RD_ADDR).asUInt
        down(WriteBack.RESULT).data := isX0 ? B(0, 64 bits) | (pcValue + 4).asBits
        // Squash writeback if trapping
        down(WriteBack.RESULT).valid := (LEGAL === YESNO.Y) && up(VALID) && !willTrap
      }
    }
  }
}
