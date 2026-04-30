package borb.execute

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.frontend.Decoder._
import borb.frontend.YESNO
// import borb.dispatch.SrcPlugin.IMMED

import borb.dispatch._
import borb.common.MicroCode._
import borb.common.Common._
import spinal.lib.misc.plugin.FiberPlugin

object IntAlu extends AreaObject {
  // val RESULT = Payload(new RegFileWrite())
  def computeResult(microCode: borb.common.MicroCode.C, src1: Bits, src2: Bits, immed: Bits, pcRaw: UInt): Bits = {
    val result = Bits(64 bits)
    val src1S = src1.asSInt
    val src2S = src2.asSInt
    val src1U = src1.asUInt
    val src2U = src2.asUInt
    val divByZero = src2 === 0

    val minInt64 = S(BigInt("-9223372036854775808"), 64 bits)
    val negOne64 = S(-1, 64 bits)
    val divOverflow = (src1S === minInt64) && (src2S === negOne64)
    val src1Neg = src1.msb
    val src2Neg = src2.msb
    val src1Negated = ((~src1U) + U(1, 64 bits)).resize(64)
    val src2Negated = ((~src2U) + U(1, 64 bits)).resize(64)
    val src1Abs = Mux(src1Neg, src1Negated, src1U).resize(64)
    val src2Abs = Mux(src2Neg, src2Negated, src2U).resize(64)
    val divQuotAbs = (src1Abs / src2Abs).resize(64)
    val divRemAbs = (src1Abs % src2Abs).resize(64)
    val divQuotSigned = Mux(src1Neg ^ src2Neg, ((~divQuotAbs) + U(1, 64 bits)).resize(64), divQuotAbs).resize(64).asBits
    val divRemSigned = Mux(src1Neg, ((~divRemAbs) + U(1, 64 bits)).resize(64), divRemAbs).resize(64).asBits

    val src1W = src1(31 downto 0)
    val src2W = src2(31 downto 0)
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
    val mulSS = ((src1.msb ## src1).asSInt.resize(130) * (src2.msb ## src2).asSInt.resize(130)).asBits
    val mulHSU = ((src1.msb ## src1).asSInt.resize(130) * (False ## src2).asSInt.resize(130)).asBits
    val mulUU = ((False ## src1).asUInt.resize(130) * (False ## src2).asUInt.resize(130)).asBits
    val src1Uw = (B(0, 32 bits) ## src1(31 downto 0)).asUInt
    val shamt = src2(5 downto 0).asUInt
    val shamtImm = immed(5 downto 0).asUInt
    val shamtW = src2(4 downto 0).asUInt
    val shamtImmW = immed(4 downto 0).asUInt

    def leadingZeroCount(value: Bits, width: Int): Bits = {
      val count = UInt(7 bits)
      count := width
      for(i <- 0 until width) {
        when(value(i)) {
          count := width - 1 - i
        }
      }
      count.asBits.resized
    }

    def trailingZeroCount(value: Bits, width: Int): Bits = {
      val count = UInt(7 bits)
      count := width
      for(i <- (0 until width).reverse) {
        when(value(i)) {
          count := i
        }
      }
      count.asBits.resized
    }

    def popCount(value: Bits): Bits = CountOne(value).asBits.resized
    def rol64(value: Bits, amount: UInt): Bits = ((value.asUInt |<< amount) | (value.asUInt |>> (U(64, 7 bits) - amount).resize(6))).asBits
    def ror64(value: Bits, amount: UInt): Bits = ((value.asUInt |>> amount) | (value.asUInt |<< (U(64, 7 bits) - amount).resize(6))).asBits
    def rol32(value: Bits, amount: UInt): Bits = {
      val lo = value(31 downto 0).asUInt
      ((lo |<< amount) | (lo |>> (U(32, 6 bits) - amount).resize(5))).asBits(31 downto 0).asSInt.resize(64).asBits
    }
    def ror32(value: Bits, amount: UInt): Bits = {
      val lo = value(31 downto 0).asUInt
      ((lo |>> amount) | (lo |<< (U(32, 6 bits) - amount).resize(5))).asBits(31 downto 0).asSInt.resize(64).asBits
    }
    val orcBytes = Bits(64 bits)
    val revBytes = Bits(64 bits)
    for(i <- 0 until 8) {
      val byte = src1(8 * i + 7 downto 8 * i)
      orcBytes(8 * i + 7 downto 8 * i) := Mux(byte.orR, B"8'xFF", B"8'x00")
      revBytes(8 * i + 7 downto 8 * i) := src1(8 * (7 - i) + 7 downto 8 * (7 - i))
    }
    val bitMaskRs2 = (U(1, 64 bits) |<< shamt).asBits
    val bitMaskImm = (U(1, 64 bits) |<< shamtImm).asBits

    result := microCode.muxDc(
      uopXORI -> (src1 ^ immed),
      uopORI -> (src1 | immed),
      uopANDI -> (src1 & immed),
      uopADDI -> (src1.asSInt + immed.asSInt).asBits,
      uopSLTI -> (src1.asSInt < immed.asSInt).asBits.resized,
      uopSLTIU -> (src1.asUInt < immed.asUInt).asBits.resized,
      uopSLLI -> (src1.asUInt |<< (immed(5 downto 0)).asUInt).asBits,
      uopSRLI -> (src1.asUInt |>> (immed(5 downto 0)).asUInt).asBits,
      uopSRAI -> (src1.asSInt >> (immed(5 downto 0)).asUInt).asBits,
      uopXOR -> (src1 ^ src2),
      uopOR -> (src1 | src2),
      uopAND -> (src1 & src2),
      uopADD -> (src1.asSInt + src2.asSInt).asBits,
      uopSLL -> (src1.asUInt |<< src2(5 downto 0).asUInt).asBits,
      uopSRL -> (src1.asUInt |>> src2(5 downto 0).asUInt).asBits,
      uopSRA -> (src1.asSInt >> src2(5 downto 0).asUInt).asBits,
      uopSUB -> (src1.asSInt - src2.asSInt).asBits,
      uopSLT -> (src1.asSInt < src2.asSInt).asBits.resized,
      uopSLTU -> (src1.asUInt < src2.asUInt).asBits.resized,
      uopADDW -> (src1(31 downto 0).asSInt + src2(31 downto 0).asSInt).resize(64).asBits,
      uopSUBW -> (src1(31 downto 0).asSInt - src2(31 downto 0).asSInt).resize(64).asBits,
      uopADDIW -> (src1(31 downto 0).asSInt + immed(31 downto 0).asSInt).resize(64).asBits,
      uopSLLW -> (src1(31 downto 0).asUInt |<< src2(4 downto 0).asUInt)(31 downto 0).asSInt.resize(64).asBits,
      uopSRLW -> (src1(31 downto 0).asUInt |>> src2(4 downto 0).asUInt).asSInt.resize(64).asBits,
      uopSRAW -> (src1(31 downto 0).asSInt >> src2(4 downto 0).asUInt).resize(64).asBits,
      uopSLLIW -> (src1(31 downto 0).asUInt |<< immed(4 downto 0).asUInt)(31 downto 0).asSInt.resize(64).asBits,
      uopSRLIW -> (src1(31 downto 0).asUInt |>> immed(4 downto 0).asUInt).asSInt.resize(64).asBits,
      uopSRAIW -> (src1(31 downto 0).asSInt >> immed(4 downto 0).asUInt).resize(64).asBits,
      uopMUL -> mulLow(63 downto 0),
      uopMULH -> mulSS(127 downto 64),
      uopMULHSU -> mulHSU(127 downto 64),
      uopMULHU -> mulUU(127 downto 64),
      uopDIV -> Mux(divByZero, B(BigInt("FFFFFFFFFFFFFFFF", 16), 64 bits), Mux(divOverflow, minInt64.asBits, divQuotSigned)),
      uopDIVU -> Mux(divByZero, B(BigInt("FFFFFFFFFFFFFFFF", 16), 64 bits), (src1U / src2U).asBits),
      uopREM -> Mux(divByZero, src1.asBits, Mux(divOverflow, B(0, 64 bits), divRemSigned)),
      uopREMU -> Mux(divByZero, src1.asBits, (src1U % src2U).asBits),
      uopMULW -> (src1WS * src2WS).asBits(31 downto 0).asSInt.resize(64).asBits,
      uopDIVW -> Mux(divByZeroW, B(BigInt("FFFFFFFF", 16), 32 bits), Mux(divOverflowW, minInt32.asBits, divQuotSignedW)).asSInt.resize(64).asBits,
      uopDIVUW -> Mux(divByZeroW, B(BigInt("FFFFFFFF", 16), 32 bits), (src1WU / src2WU).asBits).asSInt.resize(64).asBits,
      uopREMW -> Mux(divByZeroW, src1W, Mux(divOverflowW, B(0, 32 bits), divRemSignedW)).asSInt.resize(64).asBits,
      uopREMUW -> Mux(divByZeroW, src1W, (src1WU % src2WU).asBits).asSInt.resize(64).asBits,
      uopSH1ADD -> ((src1.asUInt |<< 1) + src2.asUInt).asBits,
      uopSH2ADD -> ((src1.asUInt |<< 2) + src2.asUInt).asBits,
      uopSH3ADD -> ((src1.asUInt |<< 3) + src2.asUInt).asBits,
      uopADD_UW -> (src1Uw + src2.asUInt).asBits,
      uopSH1ADD_UW -> ((src1Uw |<< 1) + src2.asUInt).asBits,
      uopSH2ADD_UW -> ((src1Uw |<< 2) + src2.asUInt).asBits,
      uopSH3ADD_UW -> ((src1Uw |<< 3) + src2.asUInt).asBits,
      uopSLLI_UW -> (src1Uw |<< shamtImm).asBits,
      uopANDN -> (src1 & ~src2),
      uopORN -> (src1 | ~src2),
      uopXNOR -> ~(src1 ^ src2),
      uopCLZ -> leadingZeroCount(src1, 64),
      uopCTZ -> trailingZeroCount(src1, 64),
      uopCPOP -> popCount(src1),
      uopCLZW -> leadingZeroCount(src1(31 downto 0), 32).asUInt.resize(32).asBits.asSInt.resize(64).asBits,
      uopCTZW -> trailingZeroCount(src1(31 downto 0), 32).asUInt.resize(32).asBits.asSInt.resize(64).asBits,
      uopCPOPW -> popCount(src1(31 downto 0)).asUInt.resize(32).asBits.asSInt.resize(64).asBits,
      uopMAX -> Mux(src1.asSInt > src2.asSInt, src1, src2),
      uopMAXU -> Mux(src1.asUInt > src2.asUInt, src1, src2),
      uopMIN -> Mux(src1.asSInt < src2.asSInt, src1, src2),
      uopMINU -> Mux(src1.asUInt < src2.asUInt, src1, src2),
      uopSEXTB -> src1(7 downto 0).asSInt.resize(64).asBits,
      uopSEXTH -> src1(15 downto 0).asSInt.resize(64).asBits,
      uopZEXTH -> src1(15 downto 0).asUInt.resize(64).asBits,
      uopROL -> rol64(src1, shamt),
      uopROR -> ror64(src1, shamt),
      uopRORI -> ror64(src1, shamtImm),
      uopROLW -> rol32(src1, shamtW),
      uopRORW -> ror32(src1, shamtW),
      uopRORIW -> ror32(src1, shamtImmW),
      uopORCB -> orcBytes,
      uopREV8 -> revBytes,
      uopBCLR -> (src1 & ~bitMaskRs2),
      uopBCLRI -> (src1 & ~bitMaskImm),
      uopBEXT -> ((src1.asUInt |>> shamt)(0)).asBits.resized,
      uopBEXTI -> ((src1.asUInt |>> shamtImm)(0)).asBits.resized,
      uopBINV -> (src1 ^ bitMaskRs2),
      uopBINVI -> (src1 ^ bitMaskImm),
      uopBSET -> (src1 | bitMaskRs2),
      uopBSETI -> (src1 | bitMaskImm),
      uopCZERO_EQZ -> Mux(src2 === 0, B(0, 64 bits), src1),
      uopCZERO_NEZ -> Mux(src2 =/= 0, B(0, 64 bits), src1),
      uopMOP_R -> B(0, 64 bits),
      uopMOP_RR -> B(0, 64 bits),
      uopLUI -> immed.asBits,
      uopAUIPC -> (immed.asSInt + pcRaw.asSInt).asBits,
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
    result
  }
}
case class IntAlu(aluNode: CtrlLink) extends FiberPlugin {
  import IntAlu._
  val SRC1 = borb.dispatch.SrcPlugin.RS1
  val SRC2 = borb.dispatch.SrcPlugin.RS2

  val aluNodeStage = new aluNode.Area {
    import borb.dispatch.Dispatch._
    import borb.dispatch.SrcPlugin._
    // import borb.frontend.AluOp
    val execFire = up.isFiring && up(VALID) && up(LANE_SEL) && up(SENDTOALU)
    val result = IntAlu.computeResult(up(MicroCode), SRC1, SRC2, IMMED, up(borb.fetch.PC.PC))

    down(WriteBack.RESULT).address.allowOverride := 0
    down(WriteBack.RESULT).data.allowOverride := 0
    down(WriteBack.RESULT).valid.allowOverride := False

    // Only drive result if this instruction is dispatched to ALU
    when(execFire) {
      // down(RESULT) := result.asBits
      // Enforce x0 invariant: writes to x0 must have 0 data (architecturally).
      // This ensures RVFI sees the correct "ignore" behavior.
      val isX0 = up(RD_ADDR).asUInt === 0
      down(WriteBack.RESULT).data := isX0 ? B(0, 64 bits) | result.asBits
      down(WriteBack.RESULT).address := up(RD_ADDR).asUInt
      down(WriteBack.RESULT).valid := (up(LEGAL) === YESNO.Y) && up(IssueSemantics.PROPS).writesIntRd
    }
  }
}
