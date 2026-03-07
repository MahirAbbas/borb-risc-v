package borb.execute.fpu

import spinal.core._

object FpuDivSqrt {
  def divS(a: Bits, b: Bits, rm: Bits): FpuFpResult = {
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
    val invalidDiv = (aIsZero && bIsZero) || (aIsInf && bIsInf)

    val aSigRaw24 = UInt(24 bits)
    val bSigRaw24 = UInt(24 bits)
    aSigRaw24 := ((((aExp === U(0, 8 bits)) ? U(0, 1 bits) | U(1, 1 bits)).asBits) ## aFrac.asBits).asUInt
    bSigRaw24 := ((((bExp === U(0, 8 bits)) ? U(0, 1 bits) | U(1, 1 bits)).asBits) ## bFrac.asBits).asUInt

    val aLeadIdx = UInt(5 bits)
    val bLeadIdx = UInt(5 bits)
    aLeadIdx := 0
    bLeadIdx := 0
    for (i <- 0 until 24) {
      when(aSigRaw24(i)) { aLeadIdx := U(i, 5 bits) }
      when(bSigRaw24(i)) { bLeadIdx := U(i, 5 bits) }
    }

    val aNormShift = UInt(5 bits)
    val bNormShift = UInt(5 bits)
    aNormShift := 0
    bNormShift := 0
    when((aExp === U(0, 8 bits)) && (aFrac =/= U(0, 23 bits))) {
      aNormShift := (U(23, 5 bits) - aLeadIdx).resized
    }
    when((bExp === U(0, 8 bits)) && (bFrac =/= U(0, 23 bits))) {
      bNormShift := (U(23, 5 bits) - bLeadIdx).resized
    }

    val aSig24 = UInt(24 bits)
    val bSig24 = UInt(24 bits)
    aSig24 := (aSigRaw24 |<< aNormShift).resized
    bSig24 := (bSigRaw24 |<< bNormShift).resized

    val aExpNormS = SInt(11 bits)
    val bExpNormS = SInt(11 bits)
    aExpNormS := 0
    bExpNormS := 0
    when((aExp =/= U(0, 8 bits)) && (aExp =/= U(255, 8 bits))) {
      aExpNormS := aExp.resize(11).asSInt - S(127, 11 bits)
    } elsewhen((aExp === U(0, 8 bits)) && (aFrac =/= U(0, 23 bits))) {
      aExpNormS := S(-126, 11 bits) - aNormShift.resize(11).asSInt
    }
    when((bExp =/= U(0, 8 bits)) && (bExp =/= U(255, 8 bits))) {
      bExpNormS := bExp.resize(11).asSInt - S(127, 11 bits)
    } elsewhen((bExp === U(0, 8 bits)) && (bFrac =/= U(0, 23 bits))) {
      bExpNormS := S(-126, 11 bits) - bNormShift.resize(11).asSInt
    }

    val normNum = UInt(25 bits)
    normNum := aSig24.resize(25)
    val roundExpPre = SInt(10 bits)
    roundExpPre := (aExpNormS - bExpNormS + S(127, 11 bits)).resize(10)
    when((aSig24 =/= U(0, 24 bits)) && (bSig24 =/= U(0, 24 bits)) && (aSig24 < bSig24)) {
      normNum := (aSig24.resize(25) |<< 1).resized
      roundExpPre := (aExpNormS - bExpNormS + S(126, 11 bits)).resize(10)
    }

    val divNumerator = UInt(56 bits)
    divNumerator := (normNum.resize(56) |<< 29).resized
    val divDenominator = bSig24.resize(56)
    val divQuot = UInt(56 bits)
    val divRem = UInt(56 bits)
    divQuot := 0
    divRem := 0
    when(divDenominator =/= U(0, 56 bits)) {
      divQuot := (divNumerator / divDenominator).resized
      divRem := (divNumerator % divDenominator).resized
    }

    val roundSrcPreBase = divQuot(29 downto 3)
    val roundSrcPre = UInt(27 bits)
    roundSrcPre := roundSrcPreBase
    roundSrcPre(0) := roundSrcPreBase(0) || divQuot(2 downto 0).orR || (divRem =/= U(0, 56 bits))

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

    when(anySNaN || invalidDiv) {
      packed := FpuSoftFloatUtils.canonicalNaN32
      flags(4) := True
    } elsewhen(anyNaN) {
      packed := FpuSoftFloatUtils.canonicalNaN32
    } elsewhen(aIsInf) {
      packed := outSign.asBits ## B(255, 8 bits) ## B(0, 23 bits)
    } elsewhen(bIsInf) {
      packed := outSign.asBits ## B(0, 31 bits)
    } elsewhen(bIsZero) {
      packed := outSign.asBits ## B(255, 8 bits) ## B(0, 23 bits)
      flags(3) := True
    } elsewhen(aIsZero) {
      packed := outSign.asBits ## B(0, 31 bits)
    } elsewhen(roundSrc === U(0, 27 bits)) {
      packed := outSign.asBits ## B(0, 31 bits)
      when(roundRemNZ) { flags(0) := True }
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

  def sqrtS(a: Bits, rm: Bits): FpuFpResult = {
    val aSign = a(31)
    val aExp = a(30 downto 23).asUInt
    val aFrac = a(22 downto 0).asUInt

    val aIsZero = (aExp === U(0, 8 bits)) && (aFrac === U(0, 23 bits))
    val aIsInf = (aExp === U(255, 8 bits)) && (aFrac === U(0, 23 bits))
    val aIsNaN = (aExp === U(255, 8 bits)) && (aFrac =/= U(0, 23 bits))
    val aIsSNaN = aIsNaN && !a(22)
    val aIsNegNonZero = aSign && !aIsZero
    val invalidSqrt = aIsNegNonZero && !aIsNaN

    val aSigRaw24 = UInt(24 bits)
    aSigRaw24 := ((((aExp === U(0, 8 bits)) ? U(0, 1 bits) | U(1, 1 bits)).asBits) ## aFrac.asBits).asUInt
    val aLeadIdx = UInt(5 bits)
    aLeadIdx := 0
    for (i <- 0 until 24) {
      when(aSigRaw24(i)) {
        aLeadIdx := U(i, 5 bits)
      }
    }
    val aNormShift = UInt(5 bits)
    aNormShift := 0
    when((aExp === U(0, 8 bits)) && (aFrac =/= U(0, 23 bits))) {
      aNormShift := (U(23, 5 bits) - aLeadIdx).resized
    }

    val aSig24 = UInt(24 bits)
    aSig24 := (aSigRaw24 |<< aNormShift).resized
    val aExpNormS = SInt(11 bits)
    aExpNormS := 0
    when((aExp =/= U(0, 8 bits)) && (aExp =/= U(255, 8 bits))) {
      aExpNormS := aExp.resize(11).asSInt - S(127, 11 bits)
    } elsewhen((aExp === U(0, 8 bits)) && (aFrac =/= U(0, 23 bits))) {
      aExpNormS := S(-126, 11 bits) - aNormShift.resize(11).asSInt
    }

    val sqrtRadSig = UInt(25 bits)
    sqrtRadSig := aSig24.resize(25)
    val sqrtExpHalfS = SInt(11 bits)
    sqrtExpHalfS := (aExpNormS |>> 1).resized
    when(aExpNormS(0)) {
      sqrtRadSig := (aSig24.resize(25) |<< 1).resized
      sqrtExpHalfS := ((aExpNormS - S(1, 11 bits)) |>> 1).resized
    }

    val sqrtRadWide = UInt(60 bits)
    sqrtRadWide := (sqrtRadSig.resize(60) |<< 35).resized
    val sqrtRoot30 = FpuSoftFloatUtils.intSqrtFloor(sqrtRadWide, 30)
    val sqrtRootSq = (sqrtRoot30.resize(64) * sqrtRoot30.resize(64)).resized
    val sqrtRem = sqrtRadWide.resize(64) - sqrtRootSq

    val roundSrcPreBase = sqrtRoot30(29 downto 3)
    val roundSrcPre = UInt(27 bits)
    roundSrcPre := roundSrcPreBase
    roundSrcPre(0) := roundSrcPreBase(0) || sqrtRoot30(2 downto 0).orR || (sqrtRem =/= U(0, 64 bits))

    val roundSrc = UInt(27 bits)
    roundSrc := roundSrcPre
    val roundExpPre = (sqrtExpHalfS + S(127, 11 bits)).resize(10)
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
    val roundCarryIn = FpuSoftFloatUtils.roundInc(rm, False, roundRemNZ, roundGtHalf, roundEqHalf, roundSigMain(0))
    val roundedWide = roundSigMain.resize(25) + roundCarryIn.asUInt.resize(25)
    val roundedCarry = roundedWide(24)

    val packed = Bits(32 bits)
    packed := 0
    val flags = Bits(5 bits)
    flags := 0

    when(aIsSNaN || invalidSqrt) {
      packed := FpuSoftFloatUtils.canonicalNaN32
      flags(4) := True
    } elsewhen(aIsNaN) {
      packed := FpuSoftFloatUtils.canonicalNaN32
    } elsewhen(aIsInf) {
      packed := B(0, 1 bits) ## B(255, 8 bits) ## B(0, 23 bits)
    } elsewhen(aIsZero) {
      packed := aSign.asBits ## B(0, 31 bits)
    } elsewhen(roundSrc === U(0, 27 bits)) {
      packed := B(0, 32 bits)
      when(roundRemNZ) { flags(0) := True }
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
        packed := B(0, 1 bits) ## B(255, 8 bits) ## B(0, 23 bits)
        flags(2) := True
        flags(0) := True
      } otherwise {
        val packedExp = UInt(8 bits)
        packedExp := finalExp(7 downto 0)
        when((finalExp === U(0, 9 bits)) && finalSig(23)) {
          packedExp := U(1, 8 bits)
        }
        packed := B(0, 1 bits) ## packedExp.asBits ## finalSig(22 downto 0).asBits
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
