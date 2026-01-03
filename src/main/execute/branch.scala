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

    val target = UInt(32 bits)
    switch(up(MicroCode)) {
      is(uopJALR) { target := (src1U.asSInt + imm.asSInt).asUInt }
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
    val doJump = (isJump || (isBranch && condition)) && up(LANE_SEL) && up(SENDTOBRANCH)

    pc.jump.valid := doJump && down.isFiring
    pc.jump.payload.target := target(31 downto 0).resized // PC target is 32 bits typically in this design
    pc.jump.payload.is_jump := isJump
    pc.jump.payload.is_branch := isBranch

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
