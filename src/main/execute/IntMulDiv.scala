package borb.execute

import spinal.core._
import spinal.lib._
import borb.common.MicroCode._
import borb.frontend.ExecutionUnitEnum

object IntMulDiv extends AreaObject {
  val SupportedUops = Seq(
    uopMUL,
    uopMULH,
    uopMULHSU,
    uopMULHU,
    uopDIV,
    uopDIVU,
    uopREM,
    uopREMU,
    uopMULW,
    uopDIVW,
    uopDIVUW,
    uopREMW,
    uopREMUW
  )

  def computeResult(microCode: borb.common.MicroCode.C, src1: Bits, src2: Bits): Bits = {
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

    result := microCode.muxDc(
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
      uopREMUW -> Mux(divByZeroW, src1W, (src1WU % src2WU).asBits).asSInt.resize(64).asBits
    )

    result
  }
}

case class IntMulDiv() extends FunctionalUnit(ExecutionUnitEnum.ALU) {
  IntMulDiv.SupportedUops.foreach(add)
}
