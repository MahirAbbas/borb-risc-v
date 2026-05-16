package borb.execute.fpu

import spinal.core._

case class FpConvertResult(data: Bits, flags: Bits)

object FpConvert {
  private def srcMagnitude(srcInt64: UInt, srcFrom32: Bool, srcSigned: Bool): (Bool, UInt) = {
    val srcInt32 = srcInt64(31 downto 0)
    val srcSign = srcSigned && Mux(srcFrom32, srcInt32.msb, srcInt64.msb)
    val srcMag = UInt(64 bits)
    srcMag := Mux(srcFrom32, srcInt32.resize(64), srcInt64)
    when(srcSign) {
      when(srcFrom32) {
        srcMag := ((~srcInt32) + U(1, 32 bits)).resize(64)
      } otherwise {
        srcMag := ((~srcInt64) + U(1, 64 bits)).resized
      }
    }
    (srcSign, srcMag)
  }

  private def msbIndex64(srcMag: UInt): UInt = {
    val msbIdx = UInt(6 bits)
    msbIdx := 0
    for (i <- 0 until 64) {
      when(srcMag(i)) {
        msbIdx := i
      }
    }
    msbIdx
  }

  private def roundedIntSig(srcMag: UInt, sigBits: Int, rm: Bits, srcSign: Bool): (UInt, Bool, UInt) = {
    val msbIdx = msbIndex64(srcMag)
    val truncSig = UInt(sigBits bits)
    truncSig := 0
    val remNZ = Bool()
    remNZ := False
    val gtHalf = Bool()
    gtHalf := False
    val eqHalf = Bool()
    eqHalf := False

    when(srcMag =/= 0) {
      when(msbIdx > U(sigBits - 1, 6 bits)) {
        val rshift = (msbIdx - U(sigBits - 1, 6 bits)).resized
        val mask = (U(1, 64 bits) |<< rshift) - U(1, 64 bits)
        val rem = srcMag & mask
        val half = U(1, 64 bits) |<< (rshift - U(1, 6 bits)).resized
        truncSig := (srcMag |>> rshift).resize(sigBits)
        remNZ := rem =/= 0
        gtHalf := rem > half
        eqHalf := rem === half
      } otherwise {
        truncSig := (srcMag |<< (U(sigBits - 1, 6 bits) - msbIdx).resized).resize(sigBits)
      }
    }

    val inc = FpuSoftFloatUtils.roundInc(rm, srcSign, remNZ, gtHalf, eqHalf, truncSig(0))
    val rounded = truncSig.resize(sigBits + 1) + inc.asUInt.resize(sigBits + 1)
    (rounded, remNZ, msbIdx)
  }

  def intToHalf(srcInt64: UInt, srcSigned: Bool, rm: Bits): FpConvertResult = {
    val (srcSign, srcMag) = srcMagnitude(srcInt64, False, srcSigned)
    val msbIdx = msbIndex64(srcMag)
    val truncSig = UInt(11 bits)
    truncSig := 0
    val remNZ = Bool()
    remNZ := False
    val gtHalf = Bool()
    gtHalf := False
    val eqHalf = Bool()
    eqHalf := False

    when(srcMag =/= 0) {
      when(msbIdx > U(10, 6 bits)) {
        val rshift = (msbIdx - U(10, 6 bits)).resized
        val mask = (U(1, 64 bits) |<< rshift) - U(1, 64 bits)
        val rem = srcMag & mask
        val half = U(1, 64 bits) |<< (rshift - U(1, 6 bits)).resized
        truncSig := (srcMag |>> rshift).resize(11)
        remNZ := rem =/= 0
        gtHalf := rem > half
        eqHalf := rem === half
      } otherwise {
        truncSig := (srcMag |<< (U(10, 6 bits) - msbIdx).resized).resize(11)
      }
    }

    val inc = FpuSoftFloatUtils.roundInc(rm, srcSign, remNZ, gtHalf, eqHalf, truncSig(0))
    val rounded = truncSig.resize(12) + inc.asUInt.resize(12)
    val carry = rounded(11)
    val normSig = UInt(11 bits)
    normSig := rounded(10 downto 0)
    when(carry) {
      normSig := (rounded |>> 1).resize(11)
    }

    val outExp = UInt(7 bits)
    outExp := 0
    when(srcMag =/= 0) {
      outExp := (msbIdx.resize(7) + U(15, 7 bits) + carry.asUInt.resize(7)).resized
    }

    val flags = Bits(5 bits)
    flags := 0
    val outHalf = Bits(16 bits)
    outHalf := 0
    when(srcMag === 0) {
      outHalf := B(0, 16 bits)
    } elsewhen(outExp >= U(31, 7 bits)) {
      outHalf := srcSign.asBits ## B(31, 5 bits) ## B(0, 10 bits)
      flags(2) := True
      flags(0) := True
    } otherwise {
      outHalf := srcSign.asBits ## outExp(4 downto 0).asBits ## normSig(9 downto 0).asBits
      when(remNZ) {
        flags(0) := True
      }
    }
    FpConvertResult(FpuFormatUtils.boxedH(outHalf), flags)
  }

  def intToSingle(srcInt64: UInt, srcFrom32: Bool, srcSigned: Bool, rm: Bits): FpConvertResult = {
    val (srcSign, srcMag) = srcMagnitude(srcInt64, srcFrom32, srcSigned)
    val msbIdx = msbIndex64(srcMag)
    val roundedInfo = roundedIntSig(srcMag, 24, rm, srcSign)
    val rounded = roundedInfo._1
    val remNZ = roundedInfo._2
    val carry = rounded(24)
    val normSig = UInt(24 bits)
    normSig := rounded(23 downto 0)
    when(carry) {
      normSig := (rounded |>> 1).resize(24)
    }

    val outExp = UInt(8 bits)
    outExp := 0
    when(srcMag =/= 0) {
      outExp := (msbIdx.resize(8) + U(127, 8 bits) + carry.asUInt.resize(8)).resized
    }

    val outFrac = normSig(22 downto 0).asBits
    val outSign = srcSign && (srcMag =/= 0)
    val flags = Bits(5 bits)
    flags := 0
    when(remNZ) {
      flags(0) := True
    }
    FpConvertResult(FpuFormatUtils.boxedS(outSign.asBits ## outExp.asBits ## outFrac), flags)
  }

  def intToDouble(srcInt64: UInt, srcFrom32: Bool, srcSigned: Bool, rm: Bits): FpConvertResult = {
    val (srcSign, srcMag) = srcMagnitude(srcInt64, srcFrom32, srcSigned)
    val msbIdx = msbIndex64(srcMag)
    val roundedInfo = roundedIntSig(srcMag, 53, rm, srcSign)
    val rounded = roundedInfo._1
    val remNZ = roundedInfo._2
    val carry = rounded(53)
    val normSig = UInt(53 bits)
    normSig := rounded(52 downto 0)
    when(carry) {
      normSig := (rounded |>> 1).resize(53)
    }

    val outExp = UInt(11 bits)
    outExp := 0
    when(srcMag =/= 0) {
      outExp := (msbIdx.resize(11) + U(1023, 11 bits) + carry.asUInt.resize(11)).resized
    }

    val outFrac = normSig(51 downto 0).asBits
    val outSign = srcSign && (srcMag =/= 0)
    val flags = Bits(5 bits)
    flags := 0
    when(remNZ) {
      flags(0) := True
    }
    FpConvertResult(outSign.asBits ## outExp.asBits ## outFrac, flags)
  }
}
