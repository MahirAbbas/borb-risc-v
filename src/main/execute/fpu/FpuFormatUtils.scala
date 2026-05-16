package borb.execute.fpu

import spinal.core._

object FpuFormatUtils {
  def boxedS(data: Bits): Bits = B(BigInt("FFFFFFFF", 16), 32 bits) ## data

  def boxedH(data: Bits): Bits = B(BigInt("FFFFFFFFFFFF", 16), 48 bits) ## data

  def halfToSingle(data: Bits): Bits = {
    val out = Bits(32 bits)
    val sign = data(15)
    val exp = data(14 downto 10).asUInt
    val frac = data(9 downto 0)
    val fracUInt = frac.asUInt
    out := sign.asBits ## B(0, 31 bits)
    when(exp === U(31, 5 bits)) {
      out := sign.asBits ## B(255, 8 bits) ## B(0, 23 bits)
      when(frac =/= B(0, 10 bits)) {
        out := FpuSoftFloatUtils.canonicalNaN32
      }
    } elsewhen(exp =/= U(0, 5 bits)) {
      out := sign.asBits ## (exp.resize(8) + U(112, 8 bits)).asBits ## frac ## B(0, 13 bits)
    } elsewhen(frac =/= B(0, 10 bits)) {
      val msbIdx = UInt(4 bits)
      msbIdx := 0
      for (i <- 0 until 10) {
        when(frac(i)) {
          msbIdx := i
        }
      }
      val norm = (fracUInt |<< (U(9, 4 bits) - msbIdx).resized).resize(10).asBits
      out := sign.asBits ## (msbIdx.resize(8) + U(103, 8 bits)).asBits ## norm(8 downto 0) ## B(0, 14 bits)
    }
    out
  }

  def singleToHalf(data: Bits, rm: Bits): (Bits, Bits) = {
    val out = Bits(16 bits)
    val flags = Bits(5 bits)
    val sign = data(31)
    val exp = data(30 downto 23).asUInt
    val frac = data(22 downto 0)
    val fracUInt = frac.asUInt
    val isZero = (exp === U(0, 8 bits)) && (frac === B(0, 23 bits))
    val isInf = (exp === U(255, 8 bits)) && (frac === B(0, 23 bits))
    val isNaN = (exp === U(255, 8 bits)) && (frac =/= B(0, 23 bits))
    val isSNaN = isNaN && !frac(22)
    val sig = UInt(24 bits)
    sig := (U(1, 1 bits) ## fracUInt).asUInt
    when(exp === U(0, 8 bits)) {
      sig := (U(0, 1 bits) ## fracUInt).asUInt
    }
    out := sign.asBits ## B(0, 15 bits)
    flags := 0

    when(isNaN) {
      out := sign.asBits ## B(31, 5 bits) ## B(BigInt("200", 16), 10 bits)
      when(isSNaN) {
        flags(4) := True
      }
    } elsewhen(isInf) {
      out := sign.asBits ## B(31, 5 bits) ## B(0, 10 bits)
    } elsewhen(!isZero) {
      when(exp >= U(113, 8 bits)) {
        val trunc = UInt(11 bits)
        trunc := sig(23 downto 13)
        val rem = sig(12 downto 0)
        val remNZ = rem =/= U(0, 13 bits)
        val gtHalf = rem > U(4096, 13 bits)
        val eqHalf = rem === U(4096, 13 bits)
        val inc = FpuSoftFloatUtils.roundInc(rm, sign, remNZ, gtHalf, eqHalf, trunc(0))
        val rounded = trunc.resize(12) + inc.asUInt.resize(12)
        val carry = rounded(11)
        val outExp = (exp - U(112, 8 bits)).resize(6) + carry.asUInt.resize(6)
        val roundedSig = UInt(11 bits)
        roundedSig := rounded(10 downto 0)
        when(carry) {
          roundedSig := (rounded |>> 1).resize(11)
        }

        when((exp >= U(143, 8 bits)) || (outExp >= U(31, 6 bits))) {
          val toInf = (rm === B"000") || (rm === B"100") || (rm === B"010" && sign) || (rm === B"011" && !sign)
          out := sign.asBits ## B(30, 5 bits) ## B(BigInt("3FF", 16), 10 bits)
          when(toInf) {
            out := sign.asBits ## B(31, 5 bits) ## B(0, 10 bits)
          }
          flags(2) := True
          flags(0) := True
        } otherwise {
          out := sign.asBits ## outExp(4 downto 0).asBits ## roundedSig(9 downto 0).asBits
          when(remNZ) {
            flags(0) := True
          }
        }
      } otherwise {
        val shift = (U(126, 8 bits) - exp).resize(6)
        val trunc = UInt(11 bits)
        val remNZ = Bool()
        val gtHalf = Bool()
        val eqHalf = Bool()
        trunc := 0
        remNZ := sig =/= U(0, 24 bits)
        gtHalf := False
        eqHalf := False
        when(shift < U(32, 6 bits)) {
          val sig32 = sig.resize(32)
          val mask = (U(1, 32 bits) |<< shift) - U(1, 32 bits)
          val rem = sig32 & mask
          val half = U(1, 32 bits) |<< (shift - U(1, 6 bits))
          trunc := (sig32 |>> shift).resize(11)
          remNZ := rem =/= U(0, 32 bits)
          gtHalf := rem > half
          eqHalf := rem === half
        }
        val inc = FpuSoftFloatUtils.roundInc(rm, sign, remNZ, gtHalf, eqHalf, trunc(0))
        val rounded = trunc + inc.asUInt.resize(11)
        when(rounded(10)) {
          out := sign.asBits ## B(1, 5 bits) ## B(0, 10 bits)
        } otherwise {
          out := sign.asBits ## B(0, 5 bits) ## rounded(9 downto 0).asBits
        }
        when(remNZ) {
          flags(0) := True
          when(!rounded(10)) {
            flags(1) := True
          }
        }
      }
    }
    (out, flags)
  }

  def classifyH(in: Bits): Bits = {
    val sign = in(15)
    val exp = in(14 downto 10)
    val frac = in(9 downto 0)
    val isZero = (exp === B(0, 5 bits)) && (frac === B(0, 10 bits))
    val isSub = (exp === B(0, 5 bits)) && (frac =/= B(0, 10 bits))
    val isInf = (exp === B(31, 5 bits)) && (frac === B(0, 10 bits))
    val isNaN = (exp === B(31, 5 bits)) && (frac =/= B(0, 10 bits))
    val cls = Bits(10 bits)
    cls := 0
    when(isInf && sign) { cls(0) := True }
    when(!isNaN && !isInf && !isZero && !isSub && sign) { cls(1) := True }
    when(isSub && sign) { cls(2) := True }
    when(isZero && sign) { cls(3) := True }
    when(isZero && !sign) { cls(4) := True }
    when(isSub && !sign) { cls(5) := True }
    when(!isNaN && !isInf && !isZero && !isSub && !sign) { cls(6) := True }
    when(isInf && !sign) { cls(7) := True }
    when(isNaN && !frac(9)) { cls(8) := True }
    when(isNaN && frac(9)) { cls(9) := True }
    cls
  }
}
