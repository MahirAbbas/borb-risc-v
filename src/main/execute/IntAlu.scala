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
import spinal.lib.misc.plugin.FiberPlugin

object IntAlu extends AreaObject {
  // val RESULT = Payload(new RegFileWrite())

}
case class IntAlu(aluNode: CtrlLink) extends FiberPlugin {
  import IntAlu._
  val SRC1 = borb.dispatch.SrcPlugin.RS1
  val SRC2 = borb.dispatch.SrcPlugin.RS2

  val aluNodeStage = new aluNode.Area {
    import borb.dispatch.Dispatch._
    import borb.dispatch.SrcPlugin._
    // import borb.frontend.AluOp
    val result = Bits(64 bits)
    result.assignDontCare()
    when(up(borb.dispatch.Dispatch.SENDTOALU) === True) {
      val src1S = SRC1.asSInt
      val src2S = SRC2.asSInt
      val src1U = SRC1.asUInt
      val src2U = SRC2.asUInt
      val divByZero = SRC2 === 0

      val minInt64 = S(BigInt("-9223372036854775808"), 64 bits)
      val negOne64 = S(-1, 64 bits)
      val divOverflow = (src1S === minInt64) && (src2S === negOne64)
      val src1Neg = SRC1.msb
      val src2Neg = SRC2.msb
      val src1Negated = ((~src1U) + U(1, 64 bits)).resize(64)
      val src2Negated = ((~src2U) + U(1, 64 bits)).resize(64)
      val src1Abs = Mux(src1Neg, src1Negated, src1U).resize(64)
      val src2Abs = Mux(src2Neg, src2Negated, src2U).resize(64)
      val divQuotAbs = (src1Abs / src2Abs).resize(64)
      val divRemAbs = (src1Abs % src2Abs).resize(64)
      val divQuotSigned = Mux(src1Neg ^ src2Neg, ((~divQuotAbs) + U(1, 64 bits)).resize(64), divQuotAbs).resize(64).asBits
      val divRemSigned = Mux(src1Neg, ((~divRemAbs) + U(1, 64 bits)).resize(64), divRemAbs).resize(64).asBits

      val src1W = SRC1(31 downto 0)
      val src2W = SRC2(31 downto 0)
      val src1WS = src1W.asSInt
      val src2WS = src2W.asSInt
      val src1WU = src1W.asUInt
      val src2WU = src2W.asUInt
      val divByZeroW = src2W === 0
      val minInt32 = S(BigInt("-2147483648"), 32 bits)
      val negOne32 = S(-1, 32 bits)
      val divOverflowW = (src1WS === minInt32) && (src2WS === negOne32)
      val src1NegW = src1W.msb
      val src2NegW = src2W.msb
      val src1NegatedW = ((~src1WU) + U(1, 32 bits)).resize(32)
      val src2NegatedW = ((~src2WU) + U(1, 32 bits)).resize(32)
      val src1AbsW = Mux(src1NegW, src1NegatedW, src1WU).resize(32)
      val src2AbsW = Mux(src2NegW, src2NegatedW, src2WU).resize(32)
      val divQuotAbsW = (src1AbsW / src2AbsW).resize(32)
      val divRemAbsW = (src1AbsW % src2AbsW).resize(32)
      val divQuotSignedW = Mux(src1NegW ^ src2NegW, ((~divQuotAbsW) + U(1, 32 bits)).resize(32), divQuotAbsW).resize(32).asBits
      val divRemSignedW = Mux(src1NegW, ((~divRemAbsW) + U(1, 32 bits)).resize(32), divRemAbsW).resize(32).asBits

      val mulLow = (src1U * src2U).asBits
      val mulSS = ((SRC1.msb ## SRC1).asSInt.resize(130) * (SRC2.msb ## SRC2).asSInt.resize(130)).asBits
      val mulHSU = ((SRC1.msb ## SRC1).asSInt.resize(130) * (False ## SRC2).asSInt.resize(130)).asBits
      val mulUU = ((False ## SRC1).asUInt.resize(130) * (False ## SRC2).asUInt.resize(130)).asBits
      val pcRaw = up(borb.fetch.PC.PC)

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
        uopMUL -> mulLow(63 downto 0),
        uopMULH -> mulSS(127 downto 64),
        uopMULHSU -> mulHSU(127 downto 64),
        uopMULHU -> mulUU(127 downto 64),
        uopDIV -> Mux(divByZero, B(BigInt("FFFFFFFFFFFFFFFF", 16), 64 bits), Mux(divOverflow, minInt64.asBits, divQuotSigned)),
        uopDIVU -> Mux(divByZero, B(BigInt("FFFFFFFFFFFFFFFF", 16), 64 bits), (src1U / src2U).asBits),
        uopREM -> Mux(divByZero, SRC1.asBits, Mux(divOverflow, B(0, 64 bits), divRemSigned)),
        uopREMU -> Mux(divByZero, SRC1.asBits, (src1U % src2U).asBits),
        uopMULW -> (src1WS * src2WS).asBits(31 downto 0).asSInt.resize(64).asBits,
        uopDIVW -> Mux(divByZeroW, B(BigInt("FFFFFFFF", 16), 32 bits), Mux(divOverflowW, minInt32.asBits, divQuotSignedW)).asSInt.resize(64).asBits,
        uopDIVUW -> Mux(divByZeroW, B(BigInt("FFFFFFFF", 16), 32 bits), (src1WU / src2WU).asBits).asSInt.resize(64).asBits,
        uopREMW -> Mux(divByZeroW, src1W, Mux(divOverflowW, B(0, 32 bits), divRemSignedW)).asSInt.resize(64).asBits,
        uopREMUW -> Mux(divByZeroW, src1W, (src1WU % src2WU).asBits).asSInt.resize(64).asBits,
        uopLUI -> (IMMED.asBits),
        uopAUIPC -> (IMMED.asSInt + pcRaw.asSInt).asBits,
        // FCVT ops are handled in CPU trap/CSR area with FCSR state visibility.
        uopFCVTLS -> B(0, 64 bits),
        uopFCVTLUS -> B(0, 64 bits),
        uopFCVTSL -> B(0, 64 bits),
        uopFCVTSLU -> B(0, 64 bits),
        uopFCVTWS -> B(0, 64 bits),
        uopFCVTWUS -> B(0, 64 bits),
        uopFCVTSW -> B(0, 64 bits),
        uopFCVTSWU -> B(0, 64 bits),
        uopFMVXW -> B(0, 64 bits),
        uopFMVWX -> B(0, 64 bits)
      )
    }

    down(WriteBack.RESULT).address.allowOverride := 0
    down(WriteBack.RESULT).data.allowOverride := 0
    down(WriteBack.RESULT).valid.allowOverride := False

    // Only drive result if this instruction is dispatched to ALU
    when(up(VALID) === True && up(SENDTOALU)) {
      // down(RESULT) := result.asBits
      // Enforce x0 invariant: writes to x0 must have 0 data (architecturally).
      // This ensures RVFI sees the correct "ignore" behavior.
      val isX0 = up(RD_ADDR).asUInt === 0
      down(WriteBack.RESULT).data := isX0 ? B(0, 64 bits) | result.asBits
      down(WriteBack.RESULT).address := up(RD_ADDR).asUInt
      down(WriteBack.RESULT).valid := (up(LEGAL) === YESNO.Y) && up(VALID) && (up(RDTYPE) === borb.frontend.REGFILE.RDTYPE.RD_INT)
    }
  }
}
