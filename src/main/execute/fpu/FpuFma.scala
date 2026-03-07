package borb.execute.fpu

import spinal.core._

object FpuFma {
  def fmaS(a: Bits, b: Bits, c: Bits, rm: Bits, isFmsub: Bool, isFnmsub: Bool, isFnmadd: Bool): FpuFpResult = {
    val aSign = a(31)
    val bSign = b(31)
    val cSign = c(31)
    val aExp = a(30 downto 23).asUInt
    val bExp = b(30 downto 23).asUInt
    val cExp = c(30 downto 23).asUInt
    val aFrac = a(22 downto 0).asUInt
    val bFrac = b(22 downto 0).asUInt
    val cFrac = c(22 downto 0).asUInt

    val aIsZero = (aExp === U(0, 8 bits)) && (aFrac === U(0, 23 bits))
    val bIsZero = (bExp === U(0, 8 bits)) && (bFrac === U(0, 23 bits))
    val cIsZero = (cExp === U(0, 8 bits)) && (cFrac === U(0, 23 bits))
    val aIsInf = (aExp === U(255, 8 bits)) && (aFrac === U(0, 23 bits))
    val bIsInf = (bExp === U(255, 8 bits)) && (bFrac === U(0, 23 bits))
    val cIsInf = (cExp === U(255, 8 bits)) && (cFrac === U(0, 23 bits))
    val aIsNaN = (aExp === U(255, 8 bits)) && (aFrac =/= U(0, 23 bits))
    val bIsNaN = (bExp === U(255, 8 bits)) && (bFrac =/= U(0, 23 bits))
    val cIsNaN = (cExp === U(255, 8 bits)) && (cFrac =/= U(0, 23 bits))
    val aIsSNaN = aIsNaN && !a(22)
    val bIsSNaN = bIsNaN && !b(22)
    val cIsSNaN = cIsNaN && !c(22)
    val anyNaN = aIsNaN || bIsNaN || cIsNaN
    val anySNaN = aIsSNaN || bIsSNaN || cIsSNaN

    val aExpAdj = UInt(8 bits)
    val bExpAdj = UInt(8 bits)
    val cExpAdj = UInt(8 bits)
    aExpAdj := aIsZero ? U(0, 8 bits) | ((aExp === U(0, 8 bits)) ? U(1, 8 bits) | aExp)
    bExpAdj := bIsZero ? U(0, 8 bits) | ((bExp === U(0, 8 bits)) ? U(1, 8 bits) | bExp)
    cExpAdj := cIsZero ? U(0, 8 bits) | ((cExp === U(0, 8 bits)) ? U(1, 8 bits) | cExp)

    val aSig24 = UInt(24 bits)
    val bSig24 = UInt(24 bits)
    val cSig24 = UInt(24 bits)
    aSig24 := ((((aExp === U(0, 8 bits)) ? U(0, 1 bits) | U(1, 1 bits)).asBits) ## aFrac.asBits).asUInt
    bSig24 := ((((bExp === U(0, 8 bits)) ? U(0, 1 bits) | U(1, 1 bits)).asBits) ## bFrac.asBits).asUInt
    cSig24 := ((((cExp === U(0, 8 bits)) ? U(0, 1 bits) | U(1, 1 bits)).asBits) ## cFrac.asBits).asUInt

    val mulSignRaw = aSign ^ bSign
    val prodSign = Bool()
    prodSign := mulSignRaw
    when(isFnmsub || isFnmadd) {
      prodSign := !mulSignRaw
    }
    val cEffSign = Bool()
    cEffSign := cSign
    when(isFmsub || isFnmadd) {
      cEffSign := !cSign
    }

    val prodIsInf = aIsInf || bIsInf
    val prodIsZero = aIsZero || bIsZero
    val invalidZeroInf = (aIsInf && bIsZero) || (aIsZero && bIsInf)
    val invalidInfAdd = prodIsInf && cIsInf && (prodSign =/= cEffSign)
    val invalidFma = invalidZeroInf || invalidInfAdd

    val product48 = UInt(48 bits)
    product48 := (aSig24 * bSig24).resized
    val prodSig52 = UInt(52 bits)
    prodSig52 := (product48.resize(52) |<< 3).resized
    val cAligned47 = UInt(47 bits)
    cAligned47 := (cSig24.resize(47) |<< 23).resized
    val cSig52 = UInt(52 bits)
    cSig52 := (cAligned47.resize(52) |<< 3).resized

    val prodExpS = SInt(10 bits)
    prodExpS := aExpAdj.resize(10).asSInt + bExpAdj.resize(10).asSInt - S(127, 10 bits)
    val cExpS = SInt(10 bits)
    cExpS := cExpAdj.resize(10).asSInt

    val prodLeadIdx = UInt(6 bits)
    val cLeadIdx = UInt(6 bits)
    prodLeadIdx := 0
    cLeadIdx := 0
    for (i <- 0 until 52) {
      when(prodSig52(i)) { prodLeadIdx := U(i, 6 bits) }
      when(cSig52(i)) { cLeadIdx := U(i, 6 bits) }
    }

    val prodNormSig52 = UInt(52 bits)
    val prodNormExpS = SInt(10 bits)
    prodNormSig52 := 0
    prodNormExpS := prodExpS
    when(prodSig52 =/= U(0, 52 bits)) {
      when(prodLeadIdx > U(49, 6 bits)) {
        val sh = (prodLeadIdx - U(49, 6 bits)).resize(10)
        prodNormSig52 := FpuSoftFloatUtils.shiftRightJam52(prodSig52, sh)
        prodNormExpS := prodExpS + sh.asSInt.resize(10)
      } otherwise {
        val sh = (U(49, 6 bits) - prodLeadIdx).resize(6)
        prodNormSig52 := (prodSig52 |<< sh).resized
        prodNormExpS := prodExpS - sh.asSInt.resize(10)
      }
    }

    val cNormSig52 = UInt(52 bits)
    val cNormExpS = SInt(10 bits)
    cNormSig52 := 0
    cNormExpS := cExpS
    when(cSig52 =/= U(0, 52 bits)) {
      when(cLeadIdx > U(49, 6 bits)) {
        val sh = (cLeadIdx - U(49, 6 bits)).resize(10)
        cNormSig52 := FpuSoftFloatUtils.shiftRightJam52(cSig52, sh)
        cNormExpS := cExpS + sh.asSInt.resize(10)
      } otherwise {
        val sh = (U(49, 6 bits) - cLeadIdx).resize(6)
        cNormSig52 := (cSig52 |<< sh).resized
        cNormExpS := cExpS - sh.asSInt.resize(10)
      }
    }

    val swapTerms = Bool()
    swapTerms := False
    when((prodNormSig52 === U(0, 52 bits)) && (cNormSig52 =/= U(0, 52 bits))) {
      swapTerms := True
    } elsewhen((prodNormSig52 =/= U(0, 52 bits)) && (cNormSig52 === U(0, 52 bits))) {
      swapTerms := False
    } elsewhen(prodNormExpS < cNormExpS) {
      swapTerms := True
    } elsewhen((prodNormExpS === cNormExpS) && (prodNormSig52 < cNormSig52)) {
      swapTerms := True
    }

    val bigSign = Bool()
    val smlSign = Bool()
    val bigExpS = SInt(10 bits)
    val smlExpS = SInt(10 bits)
    val bigSig52 = UInt(52 bits)
    val smlSig52 = UInt(52 bits)
    bigSign := prodSign
    smlSign := cEffSign
    bigExpS := prodNormExpS
    smlExpS := cNormExpS
    bigSig52 := prodNormSig52
    smlSig52 := cNormSig52
    when(swapTerms) {
      bigSign := cEffSign
      smlSign := prodSign
      bigExpS := cNormExpS
      smlExpS := prodNormExpS
      bigSig52 := cNormSig52
      smlSig52 := prodNormSig52
    }

    val expDiff = UInt(10 bits)
    expDiff := (bigExpS - smlExpS).asUInt.resize(10)
    val smlAligned52 = FpuSoftFloatUtils.shiftRightJam52(smlSig52, expDiff)
    val sameSign = bigSign === smlSign
    val sum53 = UInt(53 bits)
    sum53 := bigSig52.resize(53) + smlAligned52.resize(53)
    val diff52 = UInt(52 bits)
    diff52 := (bigSig52 - smlAligned52).resized
    val exactZero = !sameSign && (bigSig52 === smlAligned52)

    val preNormSig53 = UInt(53 bits)
    preNormSig53 := 0
    val outSign = Bool()
    outSign := bigSign
    when(sameSign) {
      preNormSig53 := sum53
    } otherwise {
      preNormSig53 := diff52.resize(53)
      when(exactZero) { outSign := False }
    }

    val leadIdx = UInt(6 bits)
    leadIdx := 0
    for (i <- 0 until 53) {
      when(preNormSig53(i)) {
        leadIdx := U(i, 6 bits)
      }
    }

    val roundSrcPre = UInt(27 bits)
    roundSrcPre := 0
    val roundExpPre = SInt(11 bits)
    roundExpPre := 0
    when(preNormSig53 =/= U(0, 53 bits)) {
      val leadShiftRight = Bool()
      leadShiftRight := leadIdx > U(26, 6 bits)
      when(leadShiftRight) {
        val shifted = FpuSoftFloatUtils.shiftRightJam53(preNormSig53, (leadIdx - U(26, 6 bits)).resize(10))
        roundSrcPre := shifted(26 downto 0)
      } otherwise {
        roundSrcPre := (preNormSig53 |<< (U(26, 6 bits) - leadIdx)).resize(27)
      }
      roundExpPre := bigExpS.resize(11) + leadIdx.resize(11).asSInt - S(49, 11 bits)
    }

    val roundSrc = UInt(27 bits)
    roundSrc := roundSrcPre
    val roundExp9 = UInt(9 bits)
    roundExp9 := 0
    when(roundExpPre <= S(0, 11 bits)) {
      val subShift = UInt(6 bits)
      subShift := (S(1, 11 bits) - roundExpPre).asUInt.resize(6)
      when((S(1, 11 bits) - roundExpPre) >= S(27, 11 bits)) {
        subShift := U(27, 6 bits)
      }
      roundSrc := FpuSoftFloatUtils.shiftRightJam27(roundSrcPre, subShift)
      roundExp9 := 0
    } elsewhen((roundExpPre === S(1, 11 bits)) && !roundSrcPre(26) && (roundSrcPre =/= U(0, 27 bits))) {
      roundExp9 := 0
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

    when(anySNaN || invalidFma) {
      packed := FpuSoftFloatUtils.canonicalNaN32
      flags(4) := True
    } elsewhen(anyNaN) {
      packed := FpuSoftFloatUtils.canonicalNaN32
    } elsewhen(prodIsInf || cIsInf) {
      val infSign = Bool()
      infSign := prodSign
      when(cIsInf && !prodIsInf) {
        infSign := cEffSign
      }
      packed := infSign.asBits ## B(255, 8 bits) ## B(0, 23 bits)
    } elsewhen(prodIsZero) {
      when(cIsZero) {
        val zeroSign = Bool()
        zeroSign := prodSign
        when(prodSign =/= cEffSign) {
          zeroSign := rm === B"010"
        }
        packed := zeroSign.asBits ## B(0, 31 bits)
      } otherwise {
        packed := cEffSign.asBits ## cExp.asBits ## cFrac.asBits
      }
    } elsewhen(exactZero) {
      val zeroSign = rm === B"010"
      packed := zeroSign.asBits ## B(0, 31 bits)
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
}
