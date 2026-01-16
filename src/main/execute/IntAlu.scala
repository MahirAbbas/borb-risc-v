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
case class IntAlu(aluNode : CtrlLink) extends Area {
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
          uopXORI     -> (SRC1 ^ IMMED),
          uopORI      -> (SRC1 | IMMED),
          uopANDI     -> (SRC1 & IMMED),
          uopADDI     -> (SRC1.asSInt + IMMED.asSInt).asBits,
          uopSLTI     -> (SRC1.asSInt < IMMED.asSInt).asBits.resized,
          uopSLTIU    -> (SRC1.asUInt < IMMED.asUInt).asBits.resized,
          uopSLLI     -> (SRC1.asUInt |<< (IMMED(5 downto 0)).asUInt).asBits,
          uopSRLI     -> (SRC1.asUInt |>> (IMMED(5 downto 0)).asUInt).asBits,
          uopSRAI     -> (SRC1.asSInt  >> (IMMED(5 downto 0)).asUInt).asBits,
          
          uopXOR      -> (SRC1 ^ SRC2),
          uopOR       -> (SRC1 | SRC2),
          uopAND      -> (SRC1 & SRC2),
          uopADD      -> (SRC1.asSInt + SRC2.asSInt).asBits,
          uopSLL      -> (SRC1.asUInt |<< (SRC2(5 downto 0)).asUInt).asBits,
          uopSRL      -> (SRC1.asUInt |>> (SRC2(5 downto 0)).asUInt).asBits,
          uopSRA      -> (SRC1.asSInt  >> (SRC2(5 downto 0)).asUInt).asBits,
          uopSUB      -> (SRC1.asSInt - SRC2.asSInt).asBits,
          
          uopSLT      -> (SRC1.asSInt < SRC2.asSInt).asBits.resized,
          uopSLTU     -> (SRC1.asUInt < SRC2.asUInt).asBits.resized,

          uopADDW     -> (SRC1.asSInt + SRC2.asSInt)(31 downto 0).resize(64).asBits,
          uopSLLW     -> (SRC1.asUInt |<< SRC2(4 downto 0).asUInt)(31 downto 0).resize(64).asBits,
          uopSRAW     -> (SRC1.asSInt  >> SRC2(4 downto 0).asUInt)(31 downto 0).resize(64).asBits,
          uopSRLW     -> (SRC1.asUInt |>> SRC2(4 downto 0).asUInt)(31 downto 0).resize(64).asBits,
          uopSUBW     -> (SRC1.asSInt - SRC2.asSInt)(31 downto 0).resize(64).asBits,

          uopLUI      -> (SRC2.asBits)
        )
      }
      down(RESULT).valid := False
      down(RESULT).address := 0
      down(RESULT).data := 0


      when(up(borb.dispatch.Dispatch.SENDTOALU) === True && up(LANE_SEL)) {
        down(RESULT).data := result.asBits
        down(RESULT).address := up(RD_ADDR).asUInt
        down(RESULT).valid := (LEGAL === YESNO.Y) && up(VALID)
      }
  }
}
