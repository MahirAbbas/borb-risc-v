package borb.dispatch

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.common.{Common, LaneKey}
import borb.frontend.Decoder._
import borb.frontend.Decoder
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

  val regfileread = new stage.Area {
    val regfile = new IntRegFile(dataWidth = 64, readPorts = Decoder.LANES * 2, writePorts = 2)
  }

  class SourceLaneArea(laneId: Int) extends stage.Area {
    val laneKey = LaneKey(laneId)

    def up[T <: Data](payload: Payload[T]): T = stage.up(payload, laneKey)
    def down[T <: Data](payload: Payload[T]): T = stage.down(payload, laneKey)

    val immsel = new Area {
      val sext = Bits(64 bits).simPublic()
      sext := B(0, 64 bits)
      val imm = new IMM(up(Decoder.DECODED_INSTRUCTION))
      switch(up(IssueSemantics.PROPS).immSel) {
        is(Imm_Select.I_IMM) { sext := imm.i_sext.asBits }
        is(Imm_Select.S_IMM) { sext := imm.s_sext.asBits }
        is(Imm_Select.B_IMM) { sext := imm.b_sext.asBits }
        is(Imm_Select.U_IMM) { sext := imm.u_sext.asBits }
        is(Imm_Select.J_IMM) { sext := imm.j_sext.asBits }
        default { sext := B(0, 64 bits) }
      }
    }

    val rs1Reader = RegFileRead()
    val rs2Reader = RegFileRead()
    val valid = stage.up.isValid && up(Common.LANE_SEL) && up(Decoder.VALID)

    rs1Reader.valid := valid && up(IssueSemantics.PROPS).readsIntRs1
    rs2Reader.valid := valid && up(IssueSemantics.PROPS).readsIntRs2
    rs1Reader.address := up(Decoder.RS1_ADDR).asUInt
    rs2Reader.address := up(Decoder.RS2_ADDR).asUInt

    val rs1Port = laneId * 2
    val rs2Port = laneId * 2 + 1

    regfileread.regfile.io.reads(rs1Port).address := rs1Reader.address
    regfileread.regfile.io.reads(rs1Port).valid := rs1Reader.valid
    rs1Reader.data := (rs1Reader.address === 0) ? B(0, 64 bits) | regfileread.regfile.io.reads(rs1Port).data

    regfileread.regfile.io.reads(rs2Port).address := rs2Reader.address
    regfileread.regfile.io.reads(rs2Port).valid := rs2Reader.valid
    rs2Reader.data := (rs2Reader.address === 0) ? B(0, 64 bits) | regfileread.regfile.io.reads(rs2Port).data

    val rs1Data =
      up(IssueSemantics.PROPS).readsIntRs1 ? SrcPlugin.resolveIntRead(rs1Reader.valid, rs1Reader.address, rs1Reader.data, bypassSources) | B(0, 64 bits)
    val rs2Data =
      up(IssueSemantics.PROPS).readsIntRs2 ? SrcPlugin.resolveIntRead(rs2Reader.valid, rs2Reader.address, rs2Reader.data, bypassSources) | B(0, 64 bits)

    down(RS1) := rs1Data
    down(RS2) := rs2Data
    down(IMMED) := immsel.sext
  }

  val lanes = for(laneId <- 0 until Decoder.LANES) yield new SourceLaneArea(laneId)
}
