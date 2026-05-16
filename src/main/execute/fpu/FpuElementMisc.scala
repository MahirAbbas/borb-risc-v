package borb.execute.fpu

import spinal.core._

case class FpuCompareResult(result: Bits, flags: Bits)
case class FpuMinMaxResult(data: Bits, flags: Bits)
case class FpuRoundResult(data: Bits, flags: Bits)

object FpuElementMisc {
  def classifyD(in: Bits): Bits = {
    val sign = in(63)
    val exp = in(62 downto 52)
    val frac = in(51 downto 0)
    val isZero = (exp === B(0, 11 bits)) && (frac === B(0, 52 bits))
    val isSub = (exp === B(0, 11 bits)) && (frac =/= B(0, 52 bits))
    val isInf = (exp === B(2047, 11 bits)) && (frac === B(0, 52 bits))
    val isNaN = (exp === B(2047, 11 bits)) && (frac =/= B(0, 52 bits))
    val isQNaN = isNaN && frac(51)
    val isSNaN = isNaN && !frac(51)
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

  def signInjectD(a: Bits, b: Bits, negate: Bool, xor: Bool): Bits = {
    val signOut = Bool()
    signOut := b(63)
    when(negate) {
      signOut := !b(63)
    }
    when(xor) {
      signOut := a(63) ^ b(63)
    }
    signOut.asBits ## a(62 downto 0)
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

  def compareQuietS(a: Bits, b: Bits, isLt: Bool, isLe: Bool): FpuCompareResult = {
    val cmp = compareS(a, b, False, isLt, isLe)
    val aExp = a(30 downto 23)
    val bExp = b(30 downto 23)
    val aFrac = a(22 downto 0)
    val bFrac = b(22 downto 0)
    val aIsSNaN = (aExp === B(255, 8 bits)) && (aFrac =/= B(0, 23 bits)) && !aFrac(22)
    val bIsSNaN = (bExp === B(255, 8 bits)) && (bFrac =/= B(0, 23 bits)) && !bFrac(22)
    val flags = Bits(5 bits)
    flags := 0
    when(aIsSNaN || bIsSNaN) {
      flags(4) := True
    }
    FpuCompareResult(cmp.result, flags)
  }

  def compareD(a: Bits, b: Bits, isEq: Bool, isLt: Bool, isLe: Bool): FpuCompareResult = {
    val aSign = a(63)
    val bSign = b(63)
    val aExp = a(62 downto 52)
    val bExp = b(62 downto 52)
    val aFrac = a(51 downto 0)
    val bFrac = b(51 downto 0)
    val aIsNaN = (aExp === B(2047, 11 bits)) && (aFrac =/= B(0, 52 bits))
    val bIsNaN = (bExp === B(2047, 11 bits)) && (bFrac =/= B(0, 52 bits))
    val aIsSNaN = aIsNaN && !aFrac(51)
    val bIsSNaN = bIsNaN && !bFrac(51)
    val anyNaN = aIsNaN || bIsNaN
    val anySNaN = aIsSNaN || bIsSNaN
    val aIsZero = (aExp === B(0, 11 bits)) && (aFrac === B(0, 52 bits))
    val bIsZero = (bExp === B(0, 11 bits)) && (bFrac === B(0, 52 bits))
    val bothZero = aIsZero && bIsZero
    val aMag = a(62 downto 0).asUInt
    val bMag = b(62 downto 0).asUInt
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

  def compareQuietD(a: Bits, b: Bits, isLt: Bool, isLe: Bool): FpuCompareResult = {
    val cmp = compareD(a, b, False, isLt, isLe)
    val aExp = a(62 downto 52)
    val bExp = b(62 downto 52)
    val aFrac = a(51 downto 0)
    val bFrac = b(51 downto 0)
    val aIsSNaN = (aExp === B(2047, 11 bits)) && (aFrac =/= B(0, 52 bits)) && !aFrac(51)
    val bIsSNaN = (bExp === B(2047, 11 bits)) && (bFrac =/= B(0, 52 bits)) && !bFrac(51)
    val flags = Bits(5 bits)
    flags := 0
    when(aIsSNaN || bIsSNaN) {
      flags(4) := True
    }
    FpuCompareResult(cmp.result, flags)
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

  def minMaxD(a: Bits, b: Bits, isMin: Bool): FpuMinMaxResult = {
    val aSign = a(63)
    val bSign = b(63)
    val aExp = a(62 downto 52)
    val bExp = b(62 downto 52)
    val aFrac = a(51 downto 0)
    val bFrac = b(51 downto 0)
    val aIsNaN = (aExp === B(2047, 11 bits)) && (aFrac =/= B(0, 52 bits))
    val bIsNaN = (bExp === B(2047, 11 bits)) && (bFrac =/= B(0, 52 bits))
    val aIsSNaN = aIsNaN && !aFrac(51)
    val bIsSNaN = bIsNaN && !bFrac(51)
    val aIsZero = (aExp === B(0, 11 bits)) && (aFrac === B(0, 52 bits))
    val bIsZero = (bExp === B(0, 11 bits)) && (bFrac === B(0, 52 bits))
    val bothZero = aIsZero && bIsZero
    val aMag = a(62 downto 0).asUInt
    val bMag = b(62 downto 0).asUInt
    val aLessThanB = Bool()
    aLessThanB := False
    when(!bothZero) {
      when(aSign =/= bSign) {
        aLessThanB := aSign && !bSign
      } otherwise {
        aLessThanB := aSign ? (aMag > bMag) | (aMag < bMag)
      }
    }

    val out = Bits(64 bits)
    out := FpuSoftFloatUtils.canonicalNaN64
    when(aIsNaN && bIsNaN) {
      out := FpuSoftFloatUtils.canonicalNaN64
    } elsewhen(aIsNaN) {
      out := b
    } elsewhen(bIsNaN) {
      out := a
    } otherwise {
      when(isMin) {
        when(bothZero) {
          out := (aSign || bSign) ? B(BigInt("8000000000000000", 16), 64 bits) | B(0, 64 bits)
        } otherwise {
          out := aLessThanB ? a | b
        }
      } otherwise {
        when(bothZero) {
          out := (!aSign || !bSign) ? B(0, 64 bits) | B(BigInt("8000000000000000", 16), 64 bits)
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

  def minMaxMagS(a: Bits, b: Bits, isMin: Bool): FpuMinMaxResult = {
    val base = minMaxS(a, b, isMin)
    val aExp = a(30 downto 23)
    val bExp = b(30 downto 23)
    val aFrac = a(22 downto 0)
    val bFrac = b(22 downto 0)
    val aIsNaN = (aExp === B(255, 8 bits)) && (aFrac =/= B(0, 23 bits))
    val bIsNaN = (bExp === B(255, 8 bits)) && (bFrac =/= B(0, 23 bits))
    val out = Bits(32 bits)
    out := base.data
    when(aIsNaN || bIsNaN) {
      out := FpuSoftFloatUtils.canonicalNaN32
    }
    FpuMinMaxResult(out, base.flags)
  }

  def minMaxMagD(a: Bits, b: Bits, isMin: Bool): FpuMinMaxResult = {
    val base = minMaxD(a, b, isMin)
    val aExp = a(62 downto 52)
    val bExp = b(62 downto 52)
    val aFrac = a(51 downto 0)
    val bFrac = b(51 downto 0)
    val aIsNaN = (aExp === B(2047, 11 bits)) && (aFrac =/= B(0, 52 bits))
    val bIsNaN = (bExp === B(2047, 11 bits)) && (bFrac =/= B(0, 52 bits))
    val out = Bits(64 bits)
    out := base.data
    when(aIsNaN || bIsNaN) {
      out := FpuSoftFloatUtils.canonicalNaN64
    }
    FpuMinMaxResult(out, base.flags)
  }

  def fliS(index: UInt): Bits = {
    val out = Bits(32 bits)
    out := 0
    switch(index) {
      is(U(0, 5 bits)) { out := B(BigInt("BF800000", 16), 32 bits) }
      is(U(1, 5 bits)) { out := B(BigInt("00800000", 16), 32 bits) }
      is(U(2, 5 bits)) { out := B(BigInt("37800000", 16), 32 bits) }
      is(U(3, 5 bits)) { out := B(BigInt("38000000", 16), 32 bits) }
      is(U(4, 5 bits)) { out := B(BigInt("3B800000", 16), 32 bits) }
      is(U(5, 5 bits)) { out := B(BigInt("3C000000", 16), 32 bits) }
      is(U(6, 5 bits)) { out := B(BigInt("3D800000", 16), 32 bits) }
      is(U(7, 5 bits)) { out := B(BigInt("3E000000", 16), 32 bits) }
      is(U(8, 5 bits)) { out := B(BigInt("3E800000", 16), 32 bits) }
      is(U(9, 5 bits)) { out := B(BigInt("3EA00000", 16), 32 bits) }
      is(U(10, 5 bits)) { out := B(BigInt("3EC00000", 16), 32 bits) }
      is(U(11, 5 bits)) { out := B(BigInt("3EE00000", 16), 32 bits) }
      is(U(12, 5 bits)) { out := B(BigInt("3F000000", 16), 32 bits) }
      is(U(13, 5 bits)) { out := B(BigInt("3F200000", 16), 32 bits) }
      is(U(14, 5 bits)) { out := B(BigInt("3F400000", 16), 32 bits) }
      is(U(15, 5 bits)) { out := B(BigInt("3F600000", 16), 32 bits) }
      is(U(16, 5 bits)) { out := B(BigInt("3F800000", 16), 32 bits) }
      is(U(17, 5 bits)) { out := B(BigInt("3FA00000", 16), 32 bits) }
      is(U(18, 5 bits)) { out := B(BigInt("3FC00000", 16), 32 bits) }
      is(U(19, 5 bits)) { out := B(BigInt("3FE00000", 16), 32 bits) }
      is(U(20, 5 bits)) { out := B(BigInt("40000000", 16), 32 bits) }
      is(U(21, 5 bits)) { out := B(BigInt("40200000", 16), 32 bits) }
      is(U(22, 5 bits)) { out := B(BigInt("40400000", 16), 32 bits) }
      is(U(23, 5 bits)) { out := B(BigInt("40800000", 16), 32 bits) }
      is(U(24, 5 bits)) { out := B(BigInt("41000000", 16), 32 bits) }
      is(U(25, 5 bits)) { out := B(BigInt("41800000", 16), 32 bits) }
      is(U(26, 5 bits)) { out := B(BigInt("43000000", 16), 32 bits) }
      is(U(27, 5 bits)) { out := B(BigInt("43800000", 16), 32 bits) }
      is(U(28, 5 bits)) { out := B(BigInt("47000000", 16), 32 bits) }
      is(U(29, 5 bits)) { out := B(BigInt("47800000", 16), 32 bits) }
      is(U(30, 5 bits)) { out := B(BigInt("7F800000", 16), 32 bits) }
      is(U(31, 5 bits)) { out := FpuSoftFloatUtils.canonicalNaN32 }
    }
    out
  }

  def fliD(index: UInt): Bits = {
    val out = Bits(64 bits)
    out := 0
    switch(index) {
      is(U(0, 5 bits)) { out := B(BigInt("BFF0000000000000", 16), 64 bits) }
      is(U(1, 5 bits)) { out := B(BigInt("0010000000000000", 16), 64 bits) }
      is(U(2, 5 bits)) { out := B(BigInt("3EF0000000000000", 16), 64 bits) }
      is(U(3, 5 bits)) { out := B(BigInt("3F00000000000000", 16), 64 bits) }
      is(U(4, 5 bits)) { out := B(BigInt("3F70000000000000", 16), 64 bits) }
      is(U(5, 5 bits)) { out := B(BigInt("3F80000000000000", 16), 64 bits) }
      is(U(6, 5 bits)) { out := B(BigInt("3FB0000000000000", 16), 64 bits) }
      is(U(7, 5 bits)) { out := B(BigInt("3FC0000000000000", 16), 64 bits) }
      is(U(8, 5 bits)) { out := B(BigInt("3FD0000000000000", 16), 64 bits) }
      is(U(9, 5 bits)) { out := B(BigInt("3FD4000000000000", 16), 64 bits) }
      is(U(10, 5 bits)) { out := B(BigInt("3FD8000000000000", 16), 64 bits) }
      is(U(11, 5 bits)) { out := B(BigInt("3FDC000000000000", 16), 64 bits) }
      is(U(12, 5 bits)) { out := B(BigInt("3FE0000000000000", 16), 64 bits) }
      is(U(13, 5 bits)) { out := B(BigInt("3FE4000000000000", 16), 64 bits) }
      is(U(14, 5 bits)) { out := B(BigInt("3FE8000000000000", 16), 64 bits) }
      is(U(15, 5 bits)) { out := B(BigInt("3FEC000000000000", 16), 64 bits) }
      is(U(16, 5 bits)) { out := B(BigInt("3FF0000000000000", 16), 64 bits) }
      is(U(17, 5 bits)) { out := B(BigInt("3FF4000000000000", 16), 64 bits) }
      is(U(18, 5 bits)) { out := B(BigInt("3FF8000000000000", 16), 64 bits) }
      is(U(19, 5 bits)) { out := B(BigInt("3FFC000000000000", 16), 64 bits) }
      is(U(20, 5 bits)) { out := B(BigInt("4000000000000000", 16), 64 bits) }
      is(U(21, 5 bits)) { out := B(BigInt("4004000000000000", 16), 64 bits) }
      is(U(22, 5 bits)) { out := B(BigInt("4008000000000000", 16), 64 bits) }
      is(U(23, 5 bits)) { out := B(BigInt("4010000000000000", 16), 64 bits) }
      is(U(24, 5 bits)) { out := B(BigInt("4020000000000000", 16), 64 bits) }
      is(U(25, 5 bits)) { out := B(BigInt("4030000000000000", 16), 64 bits) }
      is(U(26, 5 bits)) { out := B(BigInt("4060000000000000", 16), 64 bits) }
      is(U(27, 5 bits)) { out := B(BigInt("4070000000000000", 16), 64 bits) }
      is(U(28, 5 bits)) { out := B(BigInt("40E0000000000000", 16), 64 bits) }
      is(U(29, 5 bits)) { out := B(BigInt("40F0000000000000", 16), 64 bits) }
      is(U(30, 5 bits)) { out := B(BigInt("7FF0000000000000", 16), 64 bits) }
      is(U(31, 5 bits)) { out := FpuSoftFloatUtils.canonicalNaN64 }
    }
    out
  }

  def roundIntegralS(in: Bits, rm: Bits, setInexact: Bool): FpuRoundResult = {
    val sign = in(31)
    val exp = in(30 downto 23).asUInt
    val frac = in(22 downto 0).asUInt
    val isZero = (exp === U(0, 8 bits)) && (frac === U(0, 23 bits))
    val isInf = (exp === U(255, 8 bits)) && (frac === U(0, 23 bits))
    val isNaN = (exp === U(255, 8 bits)) && (frac =/= U(0, 23 bits))
    val isSNaN = isNaN && !in(22)
    val out = Bits(32 bits)
    out := in
    val flags = Bits(5 bits)
    flags := 0
    val inexact = Bool()
    inexact := False

    when(isNaN) {
      out := FpuSoftFloatUtils.canonicalNaN32
      when(isSNaN) { flags(4) := True }
    } elsewhen(!isInf && !isZero) {
      when(exp < U(127, 8 bits)) {
        inexact := True
        val gtHalf = exp > U(126, 8 bits) || ((exp === U(126, 8 bits)) && (frac =/= 0))
        val eqHalf = (exp === U(126, 8 bits)) && (frac === 0)
        val inc = (rm === B"000" && gtHalf) ||
          (rm === B"010" && sign) ||
          (rm === B"011" && !sign) ||
          (rm === B"100" && (gtHalf || eqHalf))
        out := sign.asBits ## B(0, 31 bits)
        when(inc) {
          out := sign.asBits ## B(127, 8 bits) ## B(0, 23 bits)
        }
      } elsewhen(exp < U(150, 8 bits)) {
        val e = (exp - U(127, 8 bits)).resized
        val rshift = (U(23, 8 bits) - e).resized
        val sig = (U(1, 1 bits) ## frac).asUInt.resize(25)
        val mask = (U(1, 25 bits) |<< rshift) - U(1, 25 bits)
        val rem = sig & mask
        val half = U(1, 25 bits) |<< (rshift - U(1, 8 bits)).resized
        val truncSig = sig & ~mask
        val gtHalf = rem > half
        val eqHalf = rem === half
        val lsb = (truncSig |>> rshift)(0)
        val inc = FpuSoftFloatUtils.roundInc(rm, sign, rem =/= 0, gtHalf, eqHalf, lsb)
        val roundedSig = truncSig + (inc.asUInt.resize(25) |<< rshift)
        inexact := rem =/= 0
        when(roundedSig(24)) {
          out := sign.asBits ## (exp + U(1, 8 bits)).asBits ## B(0, 23 bits)
        } otherwise {
          out := sign.asBits ## exp.asBits ## roundedSig(22 downto 0).asBits
        }
      }
    }
    when(setInexact && inexact) { flags(0) := True }
    FpuRoundResult(out, flags)
  }

  def roundIntegralD(in: Bits, rm: Bits, setInexact: Bool): FpuRoundResult = {
    val sign = in(63)
    val exp = in(62 downto 52).asUInt
    val frac = in(51 downto 0).asUInt
    val isZero = (exp === U(0, 11 bits)) && (frac === U(0, 52 bits))
    val isInf = (exp === U(2047, 11 bits)) && (frac === U(0, 52 bits))
    val isNaN = (exp === U(2047, 11 bits)) && (frac =/= U(0, 52 bits))
    val isSNaN = isNaN && !in(51)
    val out = Bits(64 bits)
    out := in
    val flags = Bits(5 bits)
    flags := 0
    val inexact = Bool()
    inexact := False

    when(isNaN) {
      out := FpuSoftFloatUtils.canonicalNaN64
      when(isSNaN) { flags(4) := True }
    } elsewhen(!isInf && !isZero) {
      when(exp < U(1023, 11 bits)) {
        inexact := True
        val gtHalf = exp > U(1022, 11 bits) || ((exp === U(1022, 11 bits)) && (frac =/= 0))
        val eqHalf = (exp === U(1022, 11 bits)) && (frac === 0)
        val inc = (rm === B"000" && gtHalf) ||
          (rm === B"010" && sign) ||
          (rm === B"011" && !sign) ||
          (rm === B"100" && (gtHalf || eqHalf))
        out := sign.asBits ## B(0, 63 bits)
        when(inc) {
          out := sign.asBits ## B(1023, 11 bits) ## B(0, 52 bits)
        }
      } elsewhen(exp < U(1075, 11 bits)) {
        val e = (exp - U(1023, 11 bits)).resized
        val rshift = (U(52, 11 bits) - e).resized
        val sig = (U(1, 1 bits) ## frac).asUInt.resize(54)
        val mask = (U(1, 54 bits) |<< rshift) - U(1, 54 bits)
        val rem = sig & mask
        val half = U(1, 54 bits) |<< (rshift - U(1, 11 bits)).resized
        val truncSig = sig & ~mask
        val gtHalf = rem > half
        val eqHalf = rem === half
        val lsb = (truncSig |>> rshift)(0)
        val inc = FpuSoftFloatUtils.roundInc(rm, sign, rem =/= 0, gtHalf, eqHalf, lsb)
        val roundedSig = truncSig + (inc.asUInt.resize(54) |<< rshift)
        inexact := rem =/= 0
        when(roundedSig(53)) {
          out := sign.asBits ## (exp + U(1, 11 bits)).asBits ## B(0, 52 bits)
        } otherwise {
          out := sign.asBits ## exp.asBits ## roundedSig(51 downto 0).asBits
        }
      }
    }
    when(setInexact && inexact) { flags(0) := True }
    FpuRoundResult(out, flags)
  }
}
