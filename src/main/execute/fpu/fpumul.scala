package borb.execute.fpu

import spinal.core._

object FpuMul {
  def mulS(a: Bits, b: Bits, rm: Bits): FpuFpResult = {
    val aSign = a(31)
    val bSign = b(31)
    val outSign = aSign ^ bSign
    val aExp = a(30 downto 23).asUInt
    val bExp = b(30 downto 23).asUInt
    val aFrac = a(22 downto 0).asUInt
    val bFrac = b(22 downto 0).asUInt

    val aIsZero = (aExp === U(0, 8 bits)) && (aFrac === U(0, 23 bits))
    val bIsZero = (bExp === U(0, 8 bits)) && (bFrac === U(0, 23 bits))
    val aIsInf = (aExp === U(255, 8 bits)) && (aFrac === U(0, 23 bits))
    val bIsInf = (bExp === U(255, 8 bits)) && (bFrac === U(0, 23 bits))
    val aIsNaN = (aExp === U(255, 8 bits)) && (aFrac =/= U(0, 23 bits))
    val bIsNaN = (bExp === U(255, 8 bits)) && (bFrac =/= U(0, 23 bits))
    val aIsSNaN = aIsNaN && !a(22)
    val bIsSNaN = bIsNaN && !b(22)
    val anyNaN = aIsNaN || bIsNaN
    val anySNaN = aIsSNaN || bIsSNaN
    val invalidZeroInf = (aIsInf && bIsZero) || (aIsZero && bIsInf)

    val aExpAdj = UInt(8 bits)
    val bExpAdj = UInt(8 bits)
    aExpAdj := aIsZero ? U(0, 8 bits) | ((aExp === U(0, 8 bits)) ? U(1, 8 bits) | aExp)
    bExpAdj := bIsZero ? U(0, 8 bits) | ((bExp === U(0, 8 bits)) ? U(1, 8 bits) | bExp)

    val aSig24 = UInt(24 bits)
    val bSig24 = UInt(24 bits)
    aSig24 := ((((aExp === U(0, 8 bits)) ? U(0, 1 bits) | U(1, 1 bits)).asBits) ## aFrac.asBits).asUInt
    bSig24 := ((((bExp === U(0, 8 bits)) ? U(0, 1 bits) | U(1, 1 bits)).asBits) ## bFrac.asBits).asUInt

    val product48 = UInt(48 bits)
    product48 := (aSig24 * bSig24).resized

    val baseExpS = SInt(10 bits)
    baseExpS := aExpAdj.resize(10).asSInt + bExpAdj.resize(10).asSInt - S(127, 10 bits)

    val leadIdx = UInt(6 bits)
    leadIdx := 0
    for (i <- 0 until 48) {
      when(product48(i)) {
        leadIdx := U(i, 6 bits)
      }
    }

    val normProd = UInt(50 bits)
    normProd := product48.resize(50)
    val roundExpPre = SInt(10 bits)
    roundExpPre := baseExpS
    when(product48(47)) {
      normProd := FpuSoftFloatUtils.shiftRightJam50(product48.resize(50), U(1, 6 bits))
      roundExpPre := baseExpS + S(1, 10 bits)
    } otherwise {
      normProd := (product48.resize(50) |<< (U(46, 6 bits) - leadIdx)).resized
      roundExpPre := baseExpS - (U(46, 6 bits) - leadIdx).asSInt.resize(10)
    }

    val roundSrcPre = UInt(27 bits)
    roundSrcPre := (normProd(46 downto 23) ## normProd(22) ## normProd(21) ## normProd(20 downto 0).orR).asUInt

    val roundSrc = UInt(27 bits)
    roundSrc := roundSrcPre
    val roundExp9 = UInt(9 bits)
    roundExp9 := 0
    when(roundExpPre <= S(0, 10 bits)) {
      val subShift = UInt(6 bits)
      subShift := (S(1, 10 bits) - roundExpPre).asUInt.resize(6)
      when((S(1, 10 bits) - roundExpPre) >= S(27, 10 bits)) {
        subShift := U(27, 6 bits)
      }
      roundSrc := FpuSoftFloatUtils.shiftRightJam27(roundSrcPre, subShift)
      roundExp9 := U(0, 9 bits)
    } elsewhen((roundExpPre === S(1, 10 bits)) && !roundSrcPre(26) && (roundSrcPre =/= U(0, 27 bits))) {
      roundExp9 := U(0, 9 bits)
    } otherwise {
      roundExp9 := roundExpPre.asUInt.resize(9)
    }

    val roundSigMain = roundSrc(26 downto 3)
    val roundRemNZ = roundSrc(2 downto 0).orR
    val roundGtHalf = roundSrc(2) && (roundSrc(1) || roundSrc(0))
    val roundEqHalf = roundSrc(2) && !roundSrc(1) && !roundSrc(0)
    val roundCarryIn = FpuSoftFloatUtils.roundInc(rm, outSign, roundRemNZ, roundGtHalf, roundEqHalf, roundSigMain(0))
    val roundedWide = roundSigMain.resize(25) + roundCarryIn.asUInt.resize(25)
    val roundedCarry = roundedWide(24)

    val packed = Bits(32 bits)
    packed := 0
    val flags = Bits(5 bits)
    flags := 0

    when(anySNaN || invalidZeroInf) {
      packed := FpuSoftFloatUtils.canonicalNaN32
      flags(4) := True
    } elsewhen(anyNaN) {
      packed := FpuSoftFloatUtils.canonicalNaN32
    } elsewhen(aIsInf || bIsInf) {
      packed := outSign.asBits ## B(255, 8 bits) ## B(0, 23 bits)
    } elsewhen(aIsZero || bIsZero) {
      packed := outSign.asBits ## B(0, 31 bits)
    } elsewhen(roundSrc === U(0, 27 bits)) {
      packed := outSign.asBits ## B(0, 31 bits)
      when(roundRemNZ) {
        flags(0) := True
      }
    } otherwise {
      val finalExp = UInt(9 bits)
      finalExp := roundExp9
      val finalSig = UInt(24 bits)
      finalSig := roundedWide(23 downto 0)
      when(roundedCarry) {
        finalSig := (roundedWide |>> 1).resize(24)
        finalExp := roundExp9 + U(1, 9 bits)
      }

      when(finalExp >= U(255, 9 bits)) {
        val overflowToInf = Bool()
        overflowToInf := False
        when((rm === B"000") || (rm === B"100")) {
          overflowToInf := True
        } elsewhen((rm === B"011") && !outSign) {
          overflowToInf := True
        } elsewhen((rm === B"010") && outSign) {
          overflowToInf := True
        }
        when(overflowToInf) {
          packed := outSign.asBits ## B(255, 8 bits) ## B(0, 23 bits)
        } otherwise {
          packed := outSign.asBits ## B(254, 8 bits) ## B(8388607, 23 bits)
        }
        flags(2) := True
        flags(0) := True
      } otherwise {
        val packedExp = UInt(8 bits)
        packedExp := finalExp(7 downto 0)
        when((finalExp === U(0, 9 bits)) && finalSig(23)) {
          packedExp := U(1, 8 bits)
        }
        packed := outSign.asBits ## packedExp.asBits ## finalSig(22 downto 0).asBits
        when(roundRemNZ) {
          flags(0) := True
          when((packedExp === U(0, 8 bits)) && !finalSig(23)) {
            flags(1) := True
          }
        }
      }
    }

    FpuFpResult(packed, flags)
  }
}
