package borb.execute

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.frontend.Decoder._
import borb.frontend.YESNO
import borb.frontend.Imm_Select
import borb.frontend.ExecutionUnitEnum.ALU
// import borb.dispatch.SrcPlugin.IMMED

import borb.dispatch._
import borb.common.MicroCode._
import borb.common.Common._

object IntAlu extends AreaObject {
  val RESULT = Payload(new RegFileWrite())

}
case class IntAlu(aluNode: CtrlLink) extends Area {
  import IntAlu._
  val SRC1 = borb.dispatch.SrcPlugin.RS1
  val SRC2 = borb.dispatch.SrcPlugin.RS2

  // override val FUType = borb.frontend.ExecutionUnitEnum.ALU
  // import borb.execute.Execute._

  val aluNodeStage = new aluNode.Area {
    import borb.dispatch.Dispatch._
    import borb.dispatch.SrcPlugin._
    // import borb.frontend.AluOp
    val result = Bits(64 bits)
    result.assignDontCare()
    when(up(borb.dispatch.Dispatch.SENDTOALU) === True) {
      result := up(MicroCode).muxDc(
        uopXORI -> (SRC1 ^ IMMED),
        uopORI -> (SRC1 | IMMED),
        uopANDI -> (SRC1 & IMMED),
        uopADDI -> (SRC1.asSInt + IMMED.asSInt).asBits,
        uopSLTI -> (SRC1.asSInt < IMMED.asSInt).asBits.resized,
        uopSLTIU -> (SRC1.asUInt < IMMED.asUInt).asBits.resized,
        uopSLLI -> (SRC1.asUInt |<< (IMMED(5 downto 0)).asUInt).asBits,
        uopSRLI -> (SRC1.asUInt |>> (IMMED(5 downto 0)).asUInt).asBits,
        uopSRAI -> (SRC1.asSInt >> (IMMED(5 downto 0)).asUInt).asBits,
        uopXOR -> (SRC1 ^ SRC2),
        uopOR -> (SRC1 | SRC2),
        uopAND -> (SRC1 & SRC2),
        uopADD -> (SRC1.asSInt + SRC2.asSInt).asBits,
        uopSLL -> (SRC1.asUInt |<< (SRC2(5 downto 0)).asUInt).asBits,
        uopSRL -> (SRC1.asUInt |>> (SRC2(5 downto 0)).asUInt).asBits,
        uopSRA -> (SRC1.asSInt >> (SRC2(5 downto 0)).asUInt).asBits,
        uopSUB -> (SRC1.asSInt - SRC2.asSInt).asBits,
        uopSLT -> (SRC1.asSInt < SRC2.asSInt).asBits.resized,
        uopSLTU -> (SRC1.asUInt < SRC2.asUInt).asBits.resized,
        // RV64I W-Instructions (32-bit operations, sign-extended result)
        uopADDW -> (SRC1(31 downto 0).asSInt + SRC2(31 downto 0).asSInt).resize(64).asBits,
        uopSUBW -> (SRC1(31 downto 0).asSInt - SRC2(31 downto 0).asSInt).resize(64).asBits,
        uopADDIW -> (SRC1(31 downto 0).asSInt + IMMED(31 downto 0).asSInt).resize(64).asBits,

        uopSLLW -> (SRC1(31 downto 0).asUInt |<< SRC2(4 downto 0).asUInt)(31 downto 0).asSInt.resize(64).asBits,
        uopSRLW -> (SRC1(31 downto 0).asUInt |>> SRC2(4 downto 0).asUInt).asSInt.resize(64).asBits,
        uopSRAW -> (SRC1(31 downto 0).asSInt >> SRC2(4 downto 0).asUInt).resize(64).asBits,

        uopSLLIW -> (SRC1(31 downto 0).asUInt |<< IMMED(4 downto 0).asUInt)(31 downto 0).asSInt.resize(64).asBits,
        uopSRLIW -> (SRC1(31 downto 0).asUInt |>> IMMED(4 downto 0).asUInt).asSInt.resize(64).asBits,
        uopSRAIW -> (SRC1(31 downto 0).asSInt >> IMMED(4 downto 0).asUInt).resize(64).asBits,
        uopLUI -> (IMMED.asBits),
        uopAUIPC -> (IMMED.asSInt + borb.fetch.PC.PC.asSInt).asBits
      )
    }

    down(RESULT).address := 0
    down(RESULT).data := 0
    down(RESULT).valid := False

    // Only drive result if this instruction is dispatched to ALU
    when(up(VALID) === True && up(SENDTOALU)) {
      // down(RESULT) := result.asBits
      // Enforce x0 invariant: writes to x0 must have 0 data (architecturally).
      // This ensures RVFI sees the correct "ignore" behavior.
      val isX0 = up(RD_ADDR).asUInt === 0
      down(RESULT).data := isX0 ? B(0, 64 bits) | result.asBits
      down(RESULT).address := up(RD_ADDR).asUInt
      down(RESULT).valid := (up(LEGAL) === YESNO.Y) && up(VALID)
    }
  }
}
