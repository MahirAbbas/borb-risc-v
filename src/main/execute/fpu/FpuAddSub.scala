package borb.execute.fpu

import spinal.core._

case class FpuFpResult(data: Bits, flags: Bits)

object FpuAddSub {
  def addSubS(a: Bits, b: Bits, rm: Bits, isSub: Bool): FpuFpResult = {
    val bEff = Bits(32 bits)
    bEff := b
    when(isSub) {
      bEff := (!b(31)).asBits ## b(30 downto 0)
    }

    val aSign = a(31)
    val bSign = bEff(31)
    val aExp = a(30 downto 23).asUInt
    val bExp = bEff(30 downto 23).asUInt
    val aFrac = a(22 downto 0).asUInt
    val bFrac = bEff(22 downto 0).asUInt
    val aIsZero = (aExp === U(0, 8 bits)) && (aFrac === U(0, 23 bits))
    val bIsZero = (bExp === U(0, 8 bits)) && (bFrac === U(0, 23 bits))
    val aIsInf = (aExp === U(255, 8 bits)) && (aFrac === U(0, 23 bits))
    val bIsInf = (bExp === U(255, 8 bits)) && (bFrac === U(0, 23 bits))
    val aIsNaN = (aExp === U(255, 8 bits)) && (aFrac =/= U(0, 23 bits))
    val bIsNaN = (bExp === U(255, 8 bits)) && (bFrac =/= U(0, 23 bits))
    val aIsSNaN = aIsNaN && !a(22)
    val bIsSNaN = bIsNaN && !bEff(22)
    val anyNaN = aIsNaN || bIsNaN
    val anySNaN = aIsSNaN || bIsSNaN
    val invalidInf = aIsInf && bIsInf && (aSign =/= bSign)

    val aExpAdj = UInt(8 bits)
    val bExpAdj = UInt(8 bits)
    aExpAdj := aIsZero ? U(0, 8 bits) | ((aExp === U(0, 8 bits)) ? U(1, 8 bits) | aExp)
    bExpAdj := bIsZero ? U(0, 8 bits) | ((bExp === U(0, 8 bits)) ? U(1, 8 bits) | bExp)
    val aSig24 = UInt(24 bits)
    val bSig24 = UInt(24 bits)
    aSig24 := ((((aExp === U(0, 8 bits)) ? U(0, 1 bits) | U(1, 1 bits)).asBits) ## aFrac.asBits).asUInt
    bSig24 := ((((bExp === U(0, 8 bits)) ? U(0, 1 bits) | U(1, 1 bits)).asBits) ## bFrac.asBits).asUInt
    val aSig27 = UInt(27 bits)
    val bSig27 = UInt(27 bits)
    aSig27 := (aSig24.resize(27) |<< 3).resized
    bSig27 := (bSig24.resize(27) |<< 3).resized

    val swapMag = Bool()
    swapMag := False
    when(aExpAdj < bExpAdj) {
      swapMag := True
    } elsewhen((aExpAdj === bExpAdj) && (aSig27 < bSig27)) {
      swapMag := True
    }

    val bigSign = Bool()
    val smlSign = Bool()
    val bigExpAdj = UInt(8 bits)
    val smlExpAdj = UInt(8 bits)
    val bigSig27 = UInt(27 bits)
    val smlSig27 = UInt(27 bits)
    bigSign := aSign
    smlSign := bSign
    bigExpAdj := aExpAdj
    smlExpAdj := bExpAdj
    bigSig27 := aSig27
    smlSig27 := bSig27
    when(swapMag) {
      bigSign := bSign
      smlSign := aSign
      bigExpAdj := bExpAdj
      smlExpAdj := aExpAdj
      bigSig27 := bSig27
      smlSig27 := aSig27
    }
    val expDiff = (bigExpAdj - smlExpAdj).resized
    val smlAligned = FpuSoftFloatUtils.shiftRightJam27(smlSig27, expDiff)
    val sameSign = bigSign === smlSign

    val sumWide = UInt(28 bits)
    sumWide := bigSig27.resize(28) + smlAligned.resize(28)
    val diff27 = UInt(27 bits)
    diff27 := (bigSig27 - smlAligned).resized
    val exactZero = !sameSign && (bigSig27 === smlAligned)

    val preNormSig = UInt(27 bits)
    val preNormExp = UInt(8 bits)
    val outSign = Bool()
    preNormSig := 0
    preNormExp := bigExpAdj
    outSign := bigSign
    when(sameSign) {
      when(sumWide(27)) {
        preNormSig := sumWide(27 downto 1) | sumWide(0).asUInt.resize(27)
        preNormExp := bigExpAdj + U(1, 8 bits)
      } otherwise {
        preNormSig := sumWide(26 downto 0)
      }
    } otherwise {
      preNormSig := diff27
      when(exactZero) {
        outSign := False
        preNormExp := 0
      }
    }

    val leadShift = UInt(5 bits)
    leadShift := 0
    when(!sameSign && !exactZero) {
      when(!preNormSig(26)) {
        leadShift := 26
        when(preNormSig(25)) { leadShift := U(1, 5 bits) }
        when(preNormSig(24) && !preNormSig(25)) { leadShift := U(2, 5 bits) }
        when(preNormSig(23) && !preNormSig(25 downto 24).orR) { leadShift := U(3, 5 bits) }
        when(preNormSig(22) && !preNormSig(25 downto 23).orR) { leadShift := U(4, 5 bits) }
        when(preNormSig(21) && !preNormSig(25 downto 22).orR) { leadShift := U(5, 5 bits) }
        when(preNormSig(20) && !preNormSig(25 downto 21).orR) { leadShift := U(6, 5 bits) }
        when(preNormSig(19) && !preNormSig(25 downto 20).orR) { leadShift := U(7, 5 bits) }
        when(preNormSig(18) && !preNormSig(25 downto 19).orR) { leadShift := U(8, 5 bits) }
        when(preNormSig(17) && !preNormSig(25 downto 18).orR) { leadShift := U(9, 5 bits) }
        when(preNormSig(16) && !preNormSig(25 downto 17).orR) { leadShift := U(10, 5 bits) }
        when(preNormSig(15) && !preNormSig(25 downto 16).orR) { leadShift := U(11, 5 bits) }
        when(preNormSig(14) && !preNormSig(25 downto 15).orR) { leadShift := U(12, 5 bits) }
        when(preNormSig(13) && !preNormSig(25 downto 14).orR) { leadShift := U(13, 5 bits) }
        when(preNormSig(12) && !preNormSig(25 downto 13).orR) { leadShift := U(14, 5 bits) }
        when(preNormSig(11) && !preNormSig(25 downto 12).orR) { leadShift := U(15, 5 bits) }
        when(preNormSig(10) && !preNormSig(25 downto 11).orR) { leadShift := U(16, 5 bits) }
        when(preNormSig(9) && !preNormSig(25 downto 10).orR) { leadShift := U(17, 5 bits) }
        when(preNormSig(8) && !preNormSig(25 downto 9).orR) { leadShift := U(18, 5 bits) }
        when(preNormSig(7) && !preNormSig(25 downto 8).orR) { leadShift := U(19, 5 bits) }
        when(preNormSig(6) && !preNormSig(25 downto 7).orR) { leadShift := U(20, 5 bits) }
        when(preNormSig(5) && !preNormSig(25 downto 6).orR) { leadShift := U(21, 5 bits) }
        when(preNormSig(4) && !preNormSig(25 downto 5).orR) { leadShift := U(22, 5 bits) }
        when(preNormSig(3) && !preNormSig(25 downto 4).orR) { leadShift := U(23, 5 bits) }
        when(preNormSig(2) && !preNormSig(25 downto 3).orR) { leadShift := U(24, 5 bits) }
        when(preNormSig(1) && !preNormSig(25 downto 2).orR) { leadShift := U(25, 5 bits) }
        when(preNormSig(0) && !preNormSig(25 downto 1).orR) { leadShift := U(26, 5 bits) }
      }
    }

    val normSig = UInt(27 bits)
    val normExp = UInt(8 bits)
    normSig := preNormSig
    normExp := preNormExp
    when(!sameSign && !exactZero && (preNormSig =/= U(0, 27 bits))) {
      when(preNormExp > leadShift.resize(8)) {
        normSig := (preNormSig |<< leadShift).resized
        normExp := preNormExp - leadShift.resize(8)
      } otherwise {
        val toSub = (preNormExp - U(1, 8 bits)).resized
        normSig := (preNormSig |<< toSub.resize(5)).resized
        normExp := U(1, 8 bits)
      }
    }

    val roundSrc = UInt(27 bits)
    val roundExp = UInt(8 bits)
    roundSrc := normSig
    roundExp := normExp
    when((normExp === U(1, 8 bits)) && !normSig(26) && (normSig =/= U(0, 27 bits))) {
      roundExp := 0
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

    when(anySNaN || invalidInf) {
      packed := FpuSoftFloatUtils.canonicalNaN32
      flags(4) := True
    } elsewhen(anyNaN) {
      packed := FpuSoftFloatUtils.canonicalNaN32
    } elsewhen(aIsInf || bIsInf) {
      packed := outSign.asBits ## B(255, 8 bits) ## B(0, 23 bits)
    } elsewhen(exactZero) {
      val zeroSign = rm === B"010"
      packed := zeroSign.asBits ## B(0, 31 bits)
    } elsewhen(roundSrc === U(0, 27 bits)) {
      packed := outSign.asBits ## B(0, 31 bits)
      when(roundRemNZ) {
        flags(0) := True
      }
    } otherwise {
      val finalExp = UInt(9 bits)
      finalExp := roundExp.resize(9)
      val finalSig = UInt(24 bits)
      finalSig := roundedWide(23 downto 0)
      when(roundedCarry) {
        finalSig := (roundedWide |>> 1).resize(24)
        finalExp := roundExp.resize(9) + U(1, 9 bits)
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
