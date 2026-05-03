package borb.dispatch

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.frontend.Decoder._
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
}

object SrcPlugin extends AreaObject {
  val RS1, RS2 = Payload(Bits(64 bits))
  val IMMED = Payload(Bits(64 bits))

  def resolveIntRead(readValid: Bool, readAddress: UInt, readData: Bits, bypassSources: Seq[IntBypassSource]): Bits = {
    val resolved = Bits(64 bits)
    resolved := readData
    for (src <- bypassSources) {
      when(
        readValid &&
        (readAddress =/= 0) &&
        src.valid &&
        (src.address === readAddress)
      ) {
        resolved := src.data
      }
    }
    resolved
  }
}
case class SrcPlugin(stage: CtrlLink, bypassSources: Seq[IntBypassSource] = Seq.empty) extends Area {
  val wasReset = Reg(Bool()) init False
  // when(ClockDomain.isResetActive) {
  //   wasReset := True
  // }
  import SrcPlugin._

  import spinal.core.sim._

  val immsel = new stage.Area {
    val sext = Bits(64 bits).simPublic()
    sext := B(0, 64 bits)
    val imm = new IMM(up(borb.frontend.Decoder.DECODED_INSTRUCTION))
    switch(up(IssueSemantics.PROPS).immSel) {
      is(Imm_Select.I_IMM) { sext := imm.i_sext.asBits }
      is(Imm_Select.S_IMM) { sext := imm.s_sext.asBits }
      is(Imm_Select.B_IMM) { sext := imm.b_sext.asBits }
      is(Imm_Select.U_IMM) { sext := imm.u_sext.asBits }
      is(Imm_Select.J_IMM) { sext := imm.j_sext.asBits }
      default { sext := B(0, 64 bits) }
    }
  }

  val rs1Reader = (new RegFileRead())
  val rs2Reader = (new RegFileRead())

  val regfileread = new stage.Area {
    val regfile = new IntRegFile(dataWidth = 64, readPorts = 4, writePorts = 2)

    rs1Reader.valid := up.isValid && up(IssueSemantics.PROPS).readsIntRs1 && up(VALID)
    rs2Reader.valid := up.isValid && up(IssueSemantics.PROPS).readsIntRs2 && up(VALID)
    rs1Reader.address := up(borb.frontend.Decoder.RS1_ADDR).asUInt
    rs2Reader.address := up(borb.frontend.Decoder.RS2_ADDR).asUInt

    regfile.io.reads(0).address := rs1Reader.address
    regfile.io.reads(0).valid := rs1Reader.valid
    // Enforce x0 invariant: reads from x0 must be 0
    rs1Reader.data := (rs1Reader.address === 0) ? B(
      0,
      64 bits
    ) | regfile.io.reads(0).data

    regfile.io.reads(1).address := rs2Reader.address
    regfile.io.reads(1).valid := rs2Reader.valid
    // Enforce x0 invariant: reads from x0 must be 0
    rs2Reader.data := (rs2Reader.address === 0) ? B(
      0,
      64 bits
    ) | regfile.io.reads(1).data

    for (port <- 2 until regfile.io.reads.length) {
      regfile.io.reads(port).address := 0
      regfile.io.reads(port).valid := False
    }

  }

  val rs = new stage.Area {
    RS1.assignDontCare()
    RS2.assignDontCare()
    IMMED.assignDontCare()

    val rs1Data =
      up(IssueSemantics.PROPS).readsIntRs1 ? SrcPlugin.resolveIntRead(rs1Reader.valid, rs1Reader.address, rs1Reader.data, bypassSources) | B(0, 64 bits)
    val rs2Data =
      up(IssueSemantics.PROPS).readsIntRs2 ? SrcPlugin.resolveIntRead(rs2Reader.valid, rs2Reader.address, rs2Reader.data, bypassSources) | B(0, 64 bits)

    down(RS1) := rs1Data
    down(RS2) := rs2Data
    down(IMMED) := immsel.sext
  }
}
