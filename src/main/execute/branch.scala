
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
import borb.execute.IntAlu.RESULT
import borb.frontend.YESNO

case class Branch(node : CtrlLink, pc : PC) extends Area {
  val branchResolved = Bool()
  val logic = new node.Area {
    val src1 = up(RS1).asSInt
    val src2 = up(RS2).asSInt
    val src1U = up(RS1).asUInt
    val src2U = up(RS2).asUInt
    val pcValue = up(PC.PC)
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
    val doJump = (isJump || (isBranch && condition)) && up(LANE_SEL) && up(SENDTOBRANCH)
    branchResolved := (isJump || isBranch) && up(LANE_SEL) && up(SENDTOBRANCH) && down.isFiring

    val jumpCmd = Flow(JumpCmd(pc.addressWidth))
    jumpCmd.valid := doJump
    jumpCmd.payload.target := target
    jumpCmd.payload.is_jump := isJump
    jumpCmd.payload.is_branch := isBranch
    

    when(up(LANE_SEL) && up(SENDTOBRANCH)) {
      when(isJump) {
        down(RESULT).address := up(RD_ADDR).asUInt
        down(RESULT).data := (pcValue + 4).asBits
        down(RESULT).valid := (LEGAL === YESNO.Y) && up(VALID)
      }
      when(up(MicroCode) === uopAUIPC) {
        down(RESULT).address := up(RD_ADDR).asUInt
        down(RESULT).data := (pcValue + imm).asBits
        down(RESULT).valid := (LEGAL === YESNO.Y) && up(VALID)
      }
    }
  }
}
