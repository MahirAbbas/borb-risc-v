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

  def divD(a: Bits, b: Bits, rm: Bits): FpuFpResult = {
    val aSign = a(63)
    val bSign = b(63)
    val outSign = aSign ^ bSign
    val aExp = a(62 downto 52).asUInt
    val bExp = b(62 downto 52).asUInt
    val aFrac = a(51 downto 0).asUInt
    val bFrac = b(51 downto 0).asUInt

    val aIsZero = (aExp === U(0, 11 bits)) && (aFrac === U(0, 52 bits))
    val bIsZero = (bExp === U(0, 11 bits)) && (bFrac === U(0, 52 bits))
    val aIsInf = (aExp === U(2047, 11 bits)) && (aFrac === U(0, 52 bits))
    val bIsInf = (bExp === U(2047, 11 bits)) && (bFrac === U(0, 52 bits))
    val aIsNaN = (aExp === U(2047, 11 bits)) && (aFrac =/= U(0, 52 bits))
    val bIsNaN = (bExp === U(2047, 11 bits)) && (bFrac =/= U(0, 52 bits))
    val aIsSNaN = aIsNaN && !a(51)
    val bIsSNaN = bIsNaN && !b(51)
    val anyNaN = aIsNaN || bIsNaN
    val anySNaN = aIsSNaN || bIsSNaN
    val invalidDiv = (aIsZero && bIsZero) || (aIsInf && bIsInf)

    val aSigRaw53 = UInt(53 bits)
    val bSigRaw53 = UInt(53 bits)
    aSigRaw53 := ((((aExp === U(0, 11 bits)) ? U(0, 1 bits) | U(1, 1 bits)).asBits) ## aFrac.asBits).asUInt
    bSigRaw53 := ((((bExp === U(0, 11 bits)) ? U(0, 1 bits) | U(1, 1 bits)).asBits) ## bFrac.asBits).asUInt

    val aLeadIdx = UInt(6 bits)
    val bLeadIdx = UInt(6 bits)
    aLeadIdx := 0
    bLeadIdx := 0
    for (i <- 0 until 53) {
      when(aSigRaw53(i)) { aLeadIdx := U(i, 6 bits) }
      when(bSigRaw53(i)) { bLeadIdx := U(i, 6 bits) }
    }

    val aNormShift = UInt(6 bits)
    val bNormShift = UInt(6 bits)
    aNormShift := 0
    bNormShift := 0
    when((aExp === U(0, 11 bits)) && (aFrac =/= U(0, 52 bits))) {
      aNormShift := (U(52, 6 bits) - aLeadIdx).resized
    }
    when((bExp === U(0, 11 bits)) && (bFrac =/= U(0, 52 bits))) {
      bNormShift := (U(52, 6 bits) - bLeadIdx).resized
    }

    val aSig53 = UInt(53 bits)
    val bSig53 = UInt(53 bits)
    aSig53 := (aSigRaw53 |<< aNormShift).resized
    bSig53 := (bSigRaw53 |<< bNormShift).resized

    val aExpNormS = SInt(13 bits)
    val bExpNormS = SInt(13 bits)
    aExpNormS := 0
    bExpNormS := 0
    when((aExp =/= U(0, 11 bits)) && (aExp =/= U(2047, 11 bits))) {
      aExpNormS := aExp.resize(13).asSInt - S(1023, 13 bits)
    } elsewhen((aExp === U(0, 11 bits)) && (aFrac =/= U(0, 52 bits))) {
      aExpNormS := S(-1022, 13 bits) - aNormShift.resize(13).asSInt
    }
    when((bExp =/= U(0, 11 bits)) && (bExp =/= U(2047, 11 bits))) {
      bExpNormS := bExp.resize(13).asSInt - S(1023, 13 bits)
    } elsewhen((bExp === U(0, 11 bits)) && (bFrac =/= U(0, 52 bits))) {
      bExpNormS := S(-1022, 13 bits) - bNormShift.resize(13).asSInt
    }

    val normNum = UInt(54 bits)
    normNum := aSig53.resize(54)
    val roundExpPre = SInt(13 bits)
    roundExpPre := (aExpNormS - bExpNormS + S(1023, 13 bits)).resize(13)
    when((aSig53 =/= U(0, 53 bits)) && (bSig53 =/= U(0, 53 bits)) && (aSig53 < bSig53)) {
      normNum := (aSig53.resize(54) |<< 1).resized
      roundExpPre := (aExpNormS - bExpNormS + S(1022, 13 bits)).resize(13)
    }

    val divNumerator = UInt(112 bits)
    divNumerator := (normNum.resize(112) |<< 58).resized
    val divDenominator = bSig53.resize(112)
    val divQuot = UInt(112 bits)
    val divRem = UInt(112 bits)
    divQuot := 0
    divRem := 0
    when(divDenominator =/= U(0, 112 bits)) {
      divQuot := (divNumerator / divDenominator).resized
      divRem := (divNumerator % divDenominator).resized
    }

    val roundSrcPreBase = divQuot(58 downto 3)
    val roundSrcPre = UInt(56 bits)
    roundSrcPre := roundSrcPreBase
    roundSrcPre(0) := roundSrcPreBase(0) || divQuot(2 downto 0).orR || (divRem =/= U(0, 112 bits))

    val roundSrc = UInt(56 bits)
    roundSrc := roundSrcPre
    val roundExp12 = UInt(12 bits)
    roundExp12 := 0
    when(roundExpPre <= S(0, 13 bits)) {
      val subShift = UInt(7 bits)
      subShift := (S(1, 13 bits) - roundExpPre).asUInt.resize(7)
      when((S(1, 13 bits) - roundExpPre) >= S(56, 13 bits)) {
        subShift := U(56, 7 bits)
      }
      roundSrc := FpuSoftFloatUtils.shiftRightJam56(roundSrcPre, subShift)
      roundExp12 := U(0, 12 bits)
    } elsewhen((roundExpPre === S(1, 13 bits)) && !roundSrcPre(55) && (roundSrcPre =/= U(0, 56 bits))) {
      roundExp12 := U(0, 12 bits)
    } otherwise {
      roundExp12 := roundExpPre.asUInt.resize(12)
    }

    val roundSigMain = roundSrc(55 downto 3)
    val roundRemNZ = roundSrc(2 downto 0).orR
    val roundGtHalf = roundSrc(2) && (roundSrc(1) || roundSrc(0))
    val roundEqHalf = roundSrc(2) && !roundSrc(1) && !roundSrc(0)
    val roundCarryIn = FpuSoftFloatUtils.roundInc(rm, outSign, roundRemNZ, roundGtHalf, roundEqHalf, roundSigMain(0))
    val roundedWide = roundSigMain.resize(54) + roundCarryIn.asUInt.resize(54)
    val roundedCarry = roundedWide(53)

    val packed = Bits(64 bits)
    packed := 0
    val flags = Bits(5 bits)
    flags := 0

    when(anySNaN || invalidDiv) {
      packed := FpuSoftFloatUtils.canonicalNaN64
      flags(4) := True
    } elsewhen(anyNaN) {
      packed := FpuSoftFloatUtils.canonicalNaN64
    } elsewhen(aIsInf) {
      packed := outSign.asBits ## B(2047, 11 bits) ## B(0, 52 bits)
    } elsewhen(bIsInf) {
      packed := outSign.asBits ## B(0, 63 bits)
    } elsewhen(bIsZero) {
      packed := outSign.asBits ## B(2047, 11 bits) ## B(0, 52 bits)
      flags(3) := True
    } elsewhen(aIsZero) {
      packed := outSign.asBits ## B(0, 63 bits)
    } elsewhen(roundSrc === U(0, 56 bits)) {
      packed := outSign.asBits ## B(0, 63 bits)
      when(roundRemNZ) { flags(0) := True }
    } otherwise {
      val finalExp = UInt(12 bits)
      finalExp := roundExp12
      val finalSig = UInt(53 bits)
      finalSig := roundedWide(52 downto 0)
      when(roundedCarry) {
        finalSig := (roundedWide |>> 1).resize(53)
        finalExp := roundExp12 + U(1, 12 bits)
      }

      when(finalExp >= U(2047, 12 bits)) {
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
          packed := outSign.asBits ## B(2047, 11 bits) ## B(0, 52 bits)
        } otherwise {
          packed := outSign.asBits ## B(2046, 11 bits) ## B(BigInt("FFFFFFFFFFFFF", 16), 52 bits)
        }
        flags(2) := True
        flags(0) := True
      } otherwise {
        val packedExp = UInt(11 bits)
        packedExp := finalExp(10 downto 0)
        when((finalExp === U(0, 12 bits)) && finalSig(52)) {
          packedExp := U(1, 11 bits)
        }
        packed := outSign.asBits ## packedExp.asBits ## finalSig(51 downto 0).asBits
        when(roundRemNZ) {
          flags(0) := True
          when((packedExp === U(0, 11 bits)) && !finalSig(52)) {
            flags(1) := True
          }
        }
      }
    }

    FpuFpResult(packed, flags)
  }

  def sqrtD(a: Bits, rm: Bits): FpuFpResult = {
    val aSign = a(63)
    val aExp = a(62 downto 52).asUInt
    val aFrac = a(51 downto 0).asUInt

    val aIsZero = (aExp === U(0, 11 bits)) && (aFrac === U(0, 52 bits))
    val aIsInf = (aExp === U(2047, 11 bits)) && (aFrac === U(0, 52 bits))
    val aIsNaN = (aExp === U(2047, 11 bits)) && (aFrac =/= U(0, 52 bits))
    val aIsSNaN = aIsNaN && !a(51)
    val aIsNegNonZero = aSign && !aIsZero
    val invalidSqrt = aIsNegNonZero && !aIsNaN

    val aSigRaw53 = UInt(53 bits)
    aSigRaw53 := ((((aExp === U(0, 11 bits)) ? U(0, 1 bits) | U(1, 1 bits)).asBits) ## aFrac.asBits).asUInt
    val aLeadIdx = UInt(6 bits)
    aLeadIdx := 0
    for (i <- 0 until 53) {
      when(aSigRaw53(i)) {
        aLeadIdx := U(i, 6 bits)
      }
    }
    val aNormShift = UInt(6 bits)
    aNormShift := 0
    when((aExp === U(0, 11 bits)) && (aFrac =/= U(0, 52 bits))) {
      aNormShift := (U(52, 6 bits) - aLeadIdx).resized
    }

    val aSig53 = UInt(53 bits)
    aSig53 := (aSigRaw53 |<< aNormShift).resized
    val aExpNormS = SInt(13 bits)
    aExpNormS := 0
    when((aExp =/= U(0, 11 bits)) && (aExp =/= U(2047, 11 bits))) {
      aExpNormS := aExp.resize(13).asSInt - S(1023, 13 bits)
    } elsewhen((aExp === U(0, 11 bits)) && (aFrac =/= U(0, 52 bits))) {
      aExpNormS := S(-1022, 13 bits) - aNormShift.resize(13).asSInt
    }

    val sqrtRadSig = UInt(54 bits)
    sqrtRadSig := aSig53.resize(54)
    val sqrtExpHalfS = SInt(13 bits)
    sqrtExpHalfS := (aExpNormS |>> 1).resized
    when(aExpNormS(0)) {
      sqrtRadSig := (aSig53.resize(54) |<< 1).resized
      sqrtExpHalfS := ((aExpNormS - S(1, 13 bits)) |>> 1).resized
    }

    val sqrtRadWide = UInt(118 bits)
    sqrtRadWide := (sqrtRadSig.resize(118) |<< 64).resized
    val sqrtRoot59 = FpuSoftFloatUtils.intSqrtFloor(sqrtRadWide, 59)
    val sqrtRootSq = (sqrtRoot59.resize(118) * sqrtRoot59.resize(118)).resized
    val sqrtRem = sqrtRadWide - sqrtRootSq

    val roundSrcPreBase = sqrtRoot59(58 downto 3)
    val roundSrcPre = UInt(56 bits)
    roundSrcPre := roundSrcPreBase
    roundSrcPre(0) := roundSrcPreBase(0) || sqrtRoot59(2 downto 0).orR || (sqrtRem =/= U(0, 118 bits))

    val roundSrc = UInt(56 bits)
    roundSrc := roundSrcPre
    val roundExpPre = (sqrtExpHalfS + S(1023, 13 bits)).resize(13)
    val roundExp12 = UInt(12 bits)
    roundExp12 := 0
    when(roundExpPre <= S(0, 13 bits)) {
      val subShift = UInt(7 bits)
      subShift := (S(1, 13 bits) - roundExpPre).asUInt.resize(7)
      when((S(1, 13 bits) - roundExpPre) >= S(56, 13 bits)) {
        subShift := U(56, 7 bits)
      }
      roundSrc := FpuSoftFloatUtils.shiftRightJam56(roundSrcPre, subShift)
      roundExp12 := U(0, 12 bits)
    } elsewhen((roundExpPre === S(1, 13 bits)) && !roundSrcPre(55) && (roundSrcPre =/= U(0, 56 bits))) {
      roundExp12 := U(0, 12 bits)
    } otherwise {
      roundExp12 := roundExpPre.asUInt.resize(12)
    }

    val roundSigMain = roundSrc(55 downto 3)
    val roundRemNZ = roundSrc(2 downto 0).orR
    val roundGtHalf = roundSrc(2) && (roundSrc(1) || roundSrc(0))
    val roundEqHalf = roundSrc(2) && !roundSrc(1) && !roundSrc(0)
    val roundCarryIn = FpuSoftFloatUtils.roundInc(rm, False, roundRemNZ, roundGtHalf, roundEqHalf, roundSigMain(0))
    val roundedWide = roundSigMain.resize(54) + roundCarryIn.asUInt.resize(54)
    val roundedCarry = roundedWide(53)

    val packed = Bits(64 bits)
    packed := 0
    val flags = Bits(5 bits)
    flags := 0

    when(aIsSNaN || invalidSqrt) {
      packed := FpuSoftFloatUtils.canonicalNaN64
      flags(4) := True
    } elsewhen(aIsNaN) {
      packed := FpuSoftFloatUtils.canonicalNaN64
    } elsewhen(aIsInf) {
      packed := B(0, 1 bits) ## B(2047, 11 bits) ## B(0, 52 bits)
    } elsewhen(aIsZero) {
      packed := aSign.asBits ## B(0, 63 bits)
    } elsewhen(roundSrc === U(0, 56 bits)) {
      packed := B(0, 64 bits)
      when(roundRemNZ) { flags(0) := True }
    } otherwise {
      val finalExp = UInt(12 bits)
      finalExp := roundExp12
      val finalSig = UInt(53 bits)
      finalSig := roundedWide(52 downto 0)
      when(roundedCarry) {
        finalSig := (roundedWide |>> 1).resize(53)
        finalExp := roundExp12 + U(1, 12 bits)
      }

      when(finalExp >= U(2047, 12 bits)) {
        packed := B(0, 1 bits) ## B(2047, 11 bits) ## B(0, 52 bits)
        flags(2) := True
        flags(0) := True
      } otherwise {
        val packedExp = UInt(11 bits)
        packedExp := finalExp(10 downto 0)
        when((finalExp === U(0, 12 bits)) && finalSig(52)) {
          packedExp := U(1, 11 bits)
        }
        packed := B(0, 1 bits) ## packedExp.asBits ## finalSig(51 downto 0).asBits
        when(roundRemNZ) {
          flags(0) := True
          when((packedExp === U(0, 11 bits)) && !finalSig(52)) {
            flags(1) := True
          }
        }
      }
    }

    FpuFpResult(packed, flags)
  }
}
