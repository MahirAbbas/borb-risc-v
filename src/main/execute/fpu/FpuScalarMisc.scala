package borb.execute.fpu

import spinal.core._

case class FpuCompareResult(result: Bits, flags: Bits)
case class FpuMinMaxResult(data: Bits, flags: Bits)

object FpuScalarMisc {
  def classifyS(in: Bits): Bits = {
    val sign = in(31)
    val exp = in(30 downto 23)
    val frac = in(22 downto 0)
    val isZero = (exp === B(0, 8 bits)) && (frac === B(0, 23 bits))
    val isSub = (exp === B(0, 8 bits)) && (frac =/= B(0, 23 bits))
    val isInf = (exp === B(255, 8 bits)) && (frac === B(0, 23 bits))
    val isNaN = (exp === B(255, 8 bits)) && (frac =/= B(0, 23 bits))
    val isQNaN = isNaN && frac(22)
    val isSNaN = isNaN && !frac(22)
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
    when(isSNaN) { cls(8) := True }
    when(isQNaN) { cls(9) := True }
    cls
  }

  def signInjectS(a: Bits, b: Bits, negate: Bool, xor: Bool): Bits = {
    val signOut = Bool()
    signOut := b(31)
    when(negate) {
      signOut := !b(31)
    }
    when(xor) {
      signOut := a(31) ^ b(31)
    }
    signOut.asBits ## a(30 downto 0)
  }

  def compareS(a: Bits, b: Bits, isEq: Bool, isLt: Bool, isLe: Bool): FpuCompareResult = {
    val aSign = a(31)
    val bSign = b(31)
    val aExp = a(30 downto 23)
    val bExp = b(30 downto 23)
    val aFrac = a(22 downto 0)
    val bFrac = b(22 downto 0)
    val aIsNaN = (aExp === B(255, 8 bits)) && (aFrac =/= B(0, 23 bits))
    val bIsNaN = (bExp === B(255, 8 bits)) && (bFrac =/= B(0, 23 bits))
    val aIsSNaN = aIsNaN && !aFrac(22)
    val bIsSNaN = bIsNaN && !bFrac(22)
    val anyNaN = aIsNaN || bIsNaN
    val anySNaN = aIsSNaN || bIsSNaN
    val aIsZero = (aExp === B(0, 8 bits)) && (aFrac === B(0, 23 bits))
    val bIsZero = (bExp === B(0, 8 bits)) && (bFrac === B(0, 23 bits))
    val bothZero = aIsZero && bIsZero
    val aMag = a(30 downto 0).asUInt
    val bMag = b(30 downto 0).asUInt
    val eq = bothZero || (a === b)
    val lt = Bool()
    lt := False
    when(!bothZero) {
      when(aSign =/= bSign) {
        lt := aSign && !bSign
      } otherwise {
        lt := aSign ? (aMag > bMag) | (aMag < bMag)
      }
    }
    val le = lt || eq

    val cmpRes = Bits(64 bits)
    cmpRes := 0
    when(!anyNaN) {
      when(isEq) { cmpRes := B(0, 63 bits) ## eq.asBits }
      when(isLt) { cmpRes := B(0, 63 bits) ## lt.asBits }
      when(isLe) { cmpRes := B(0, 63 bits) ## le.asBits }
    }

    val flags = Bits(5 bits)
    flags := 0
    val setNv = (isEq && anySNaN) || ((isLt || isLe) && anyNaN)
    when(setNv) {
      flags(4) := True
    }
    FpuCompareResult(cmpRes, flags)
  }

  def minMaxS(a: Bits, b: Bits, isMin: Bool): FpuMinMaxResult = {
    val aSign = a(31)
    val bSign = b(31)
    val aExp = a(30 downto 23)
    val bExp = b(30 downto 23)
    val aFrac = a(22 downto 0)
    val bFrac = b(22 downto 0)
    val aIsNaN = (aExp === B(255, 8 bits)) && (aFrac =/= B(0, 23 bits))
    val bIsNaN = (bExp === B(255, 8 bits)) && (bFrac =/= B(0, 23 bits))
    val aIsSNaN = aIsNaN && !aFrac(22)
    val bIsSNaN = bIsNaN && !bFrac(22)
    val aIsZero = (aExp === B(0, 8 bits)) && (aFrac === B(0, 23 bits))
    val bIsZero = (bExp === B(0, 8 bits)) && (bFrac === B(0, 23 bits))
    val bothZero = aIsZero && bIsZero
    val aMag = a(30 downto 0).asUInt
    val bMag = b(30 downto 0).asUInt
    val aLessThanB = Bool()
    aLessThanB := False
    when(!bothZero) {
      when(aSign =/= bSign) {
        aLessThanB := aSign && !bSign
      } otherwise {
        aLessThanB := aSign ? (aMag > bMag) | (aMag < bMag)
      }
    }

    val out = Bits(32 bits)
    out := FpuSoftFloatUtils.canonicalNaN32
    when(aIsNaN && bIsNaN) {
      out := FpuSoftFloatUtils.canonicalNaN32
    } elsewhen(aIsNaN) {
      out := b
    } elsewhen(bIsNaN) {
      out := a
    } otherwise {
      when(isMin) {
        when(bothZero) {
          out := (aSign || bSign) ? B(BigInt("80000000", 16), 32 bits) | B(0, 32 bits)
        } otherwise {
          out := aLessThanB ? a | b
        }
      } otherwise {
        when(bothZero) {
          out := (!aSign || !bSign) ? B(0, 32 bits) | B(BigInt("80000000", 16), 32 bits)
        } otherwise {
          out := aLessThanB ? b | a
        }
      }
    }

    val flags = Bits(5 bits)
    flags := 0
    when(aIsSNaN || bIsSNaN) {
      flags(4) := True
    }
    FpuMinMaxResult(out, flags)
  }
}
