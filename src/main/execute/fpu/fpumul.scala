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

  def mulD(a: Bits, b: Bits, rm: Bits): FpuFpResult = {
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
    val invalidZeroInf = (aIsInf && bIsZero) || (aIsZero && bIsInf)

    val aExpAdj = UInt(11 bits)
    val bExpAdj = UInt(11 bits)
    aExpAdj := aIsZero ? U(0, 11 bits) | ((aExp === U(0, 11 bits)) ? U(1, 11 bits) | aExp)
    bExpAdj := bIsZero ? U(0, 11 bits) | ((bExp === U(0, 11 bits)) ? U(1, 11 bits) | bExp)

    val aSig53 = UInt(53 bits)
    val bSig53 = UInt(53 bits)
    aSig53 := ((((aExp === U(0, 11 bits)) ? U(0, 1 bits) | U(1, 1 bits)).asBits) ## aFrac.asBits).asUInt
    bSig53 := ((((bExp === U(0, 11 bits)) ? U(0, 1 bits) | U(1, 1 bits)).asBits) ## bFrac.asBits).asUInt

    val product106 = UInt(106 bits)
    product106 := (aSig53 * bSig53).resized

    val baseExpS = SInt(13 bits)
    baseExpS := aExpAdj.resize(13).asSInt + bExpAdj.resize(13).asSInt - S(1023, 13 bits)

    val leadIdx = UInt(7 bits)
    leadIdx := 0
    for (i <- 0 until 106) {
      when(product106(i)) {
        leadIdx := U(i, 7 bits)
      }
    }

    val normProd = UInt(108 bits)
    normProd := product106.resize(108)
    val roundExpPre = SInt(13 bits)
    roundExpPre := baseExpS
    when(product106(105)) {
      normProd := FpuSoftFloatUtils.shiftRightJam108(product106.resize(108), U(1, 7 bits))
      roundExpPre := baseExpS + S(1, 13 bits)
    } otherwise {
      val sh = (U(104, 7 bits) - leadIdx).resized
      normProd := (product106.resize(108) |<< sh).resized
      roundExpPre := baseExpS - sh.asSInt.resize(13)
    }

    val roundSrcPre = UInt(56 bits)
    roundSrcPre := (normProd(104 downto 52) ## normProd(51) ## normProd(50) ## normProd(49 downto 0).orR).asUInt

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

    when(anySNaN || invalidZeroInf) {
      packed := FpuSoftFloatUtils.canonicalNaN64
      flags(4) := True
    } elsewhen(anyNaN) {
      packed := FpuSoftFloatUtils.canonicalNaN64
    } elsewhen(aIsInf || bIsInf) {
      packed := outSign.asBits ## B(2047, 11 bits) ## B(0, 52 bits)
    } elsewhen(aIsZero || bIsZero) {
      packed := outSign.asBits ## B(0, 63 bits)
    } elsewhen(roundSrc === U(0, 56 bits)) {
      packed := outSign.asBits ## B(0, 63 bits)
      when(roundRemNZ) {
        flags(0) := True
      }
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
}
