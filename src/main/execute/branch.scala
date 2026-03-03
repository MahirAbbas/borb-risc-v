
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

case class Branch(node : CtrlLink, pc : PC, withCompressed: Boolean = false) extends Area {
  import Branch._

  val branchResolved = Bool()
  val logic = new node.Area {
    val src1 = up(RS1).asSInt
    val src2 = up(RS2).asSInt
    val src1U = up(RS1).asUInt
    val src2U = up(RS2).asUInt
    val pcValue = up(PC.PC)
    val archBase = U(BigInt("80000000", 16), 64 bits)
    val pcArch = Mux(pcValue < archBase, pcValue + archBase, pcValue)
    val imm = up(IMMED).asUInt

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
    // Redirect must be one-shot per actual execute-stage firing transaction.
    val isBrUnit = up(EXECUTION_UNIT) === ExecutionUnitEnum.BR
    val execFire = up.isFiring
    val doJump = (isJump || (isBranch && condition)) &&
      isBrUnit && up(LANE_SEL) && up(SENDTOBRANCH) && up(VALID) && execFire
    val misaligned = if(withCompressed) (target(0) =/= False) else (target(1 downto 0) =/= 0)
    val willTrap = doJump && misaligned

    // down(TRAP) := willTrap // Moved to CPU.scala logic integration
    down(BRANCH_TAKEN) := doJump && !willTrap
    down(BRANCH_TARGET) := target
    branchResolved := (isJump || isBranch) && isBrUnit && up(LANE_SEL) && up(SENDTOBRANCH) && execFire

    val jumpCmd = Flow(JumpCmd(pc.addressWidth))
    jumpCmd.valid := doJump && !willTrap // Mask jump if trapping
    jumpCmd.payload.target := target
    jumpCmd.payload.is_jump := isJump
    jumpCmd.payload.is_branch := isBranch
    
    when(isBrUnit && up(LANE_SEL) && up(SENDTOBRANCH) && up(VALID) && execFire) {
      when(isJump) {
        val isX0 = up(RD_ADDR).asUInt === 0
        down(WriteBack.RESULT).address := up(RD_ADDR).asUInt
        val linkStep = Mux(up(IS_COMPRESSED), U(2, 64 bits), U(4, 64 bits))
        down(WriteBack.RESULT).data := isX0 ? B(0, 64 bits) | (pcArch + linkStep).asBits
        // Squash writeback if trapping
        down(WriteBack.RESULT).valid := (LEGAL === YESNO.Y) && up(VALID) && !willTrap
      }
    }
  }
}
