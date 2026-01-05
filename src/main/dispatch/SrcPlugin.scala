package borb.dispatch

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.frontend.Decoder._
import spinal.lib.logic.DecodingSpec
import spinal.lib.logic.Masked

import scala.collection.mutable
import borb.frontend.Imm_Select

case class IMM(instruction: Bits) extends Area {
  // immediates
  def i = instruction(31 downto 20)
  def h = instruction(31 downto 24)
  def s = instruction(31 downto 25) ## instruction(11 downto 7)
  def b = instruction(31) ## instruction(7) ## instruction(
    30 downto 25
  ) ## instruction(11 downto 8)
  def u = instruction(31 downto 12) ## B(0, 12 bits)
  def j = instruction(31) ## instruction(19 downto 12) ## instruction(
    20
  ) ## instruction(30 downto 21)
  def z = instruction(19 downto 15)

  import spinal.core.sim._
  // sign-extend immediates
  def i_sext = S(i).resize(64)
  def h_sext = S(h).resize(64)
  def s_sext = S(s).resize(64)
  def b_sext = S(b ## False).resize(64)
  def j_sext = S(j ## False).resize(64)
  def u_sext = S(u).resize(64).simPublic()
  val i_sext_pub = SInt(64 bits).simPublic()
  val h_sext_pub = SInt(64 bits).simPublic()
  val s_sext_pub = SInt(64 bits).simPublic()
  val b_sext_pub = SInt(64 bits).simPublic()
  val j_sext_pub = SInt(64 bits).simPublic()
  val u_sext_pub = SInt(64 bits).simPublic()
  i_sext_pub := i_sext
  h_sext_pub := h_sext
  s_sext_pub := s_sext
  b_sext_pub := b_sext
  j_sext_pub := j_sext
  u_sext_pub := u_sext
}

object SrcPlugin extends AreaObject {
  val RS1, RS2 = Payload(Bits(64 bits))
  val IMMED = Payload(Bits(64 bits))
  

}
case class SrcPlugin(stage: CtrlLink) extends Area {
  val wasReset = Reg(Bool()) init False
  // when(ClockDomain.isResetActive) {
  //   wasReset := True
  // }
  import SrcPlugin._

  import spinal.core.sim._

  val immsel = new stage.Area {
    val sext = Bits(64 bits).simPublic()
    sext.assignDontCare()
    val imm = new IMM(borb.frontend.Decoder.INSTRUCTION)
    // when(up.isFiring) {
      sext := up(IMMSEL)
        .muxDc(
          Imm_Select.I_IMM -> imm.i_sext,
          Imm_Select.S_IMM -> imm.s_sext,
          Imm_Select.B_IMM -> imm.b_sext,
          Imm_Select.U_IMM -> imm.u_sext,
          Imm_Select.J_IMM -> imm.j_sext
        )
        .asBits
    // }
  }

  import borb.frontend.REGFILE._


  val rs1Reader = (new RegFileRead())
  val rs2Reader = (new RegFileRead())

  val regfileread = new stage.Area {
    val regfile = new IntRegFile(dataWidth = 64)

    rs1Reader.valid := (RS1TYPE === RSTYPE.RS_INT && up(VALID) === True)
    rs2Reader.valid := (RS2TYPE === RSTYPE.RS_INT && up(VALID) === True)
    rs1Reader.address := up(borb.frontend.Decoder.RS1_ADDR).asUInt
    rs2Reader.address := up(borb.frontend.Decoder.RS2_ADDR).asUInt
    

    regfile.io.readerRS1.address := rs1Reader.address
    regfile.io.readerRS1.valid := rs1Reader.valid
    rs1Reader.data := regfile.io.readerRS1.data
    
    regfile.io.readerRS2.address := rs2Reader.address
    regfile.io.readerRS2.valid := rs2Reader.valid
    rs2Reader.data := regfile.io.readerRS2.data
    

  }

  val rs = new stage.Area {
    RS1.assignDontCare()
    RS2.assignDontCare()
    IMMED.assignDontCare()

    down(RS1) := up(RS1TYPE).muxDc(
      RSTYPE.RS_INT -> rs1Reader.data
    )
    down(RS2) := up(RS2TYPE).muxDc(
      RSTYPE.RS_INT -> rs2Reader.data
    )
    IMMED := immsel.sext
  }
}
