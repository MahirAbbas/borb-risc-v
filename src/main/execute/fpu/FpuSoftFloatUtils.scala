package borb.execute.fpu

import spinal.core._

object FpuSoftFloatUtils {
  def canonicalNaN32: Bits = B(BigInt("7FC00000", 16), 32 bits)

  def roundInc(rm: Bits, sign: Bool, remNZ: Bool, gtHalf: Bool, eqHalf: Bool, lsb: Bool): Bool = {
    val inc = Bool()
    inc := False
    switch(rm) {
      is(B"000") { inc := gtHalf || (eqHalf && lsb) } // RNE
      is(B"001") { inc := False } // RTZ
      is(B"010") { inc := sign && remNZ } // RDN
      is(B"011") { inc := (!sign) && remNZ } // RUP
      is(B"100") { inc := gtHalf || eqHalf } // RMM
      default { inc := False }
    }
    inc
  }

  def shiftRightJam27(value: UInt, dist: UInt): UInt = {
    val out = UInt(27 bits)
    out := value
    when(dist >= U(27, dist.getWidth bits)) {
      out := value.orR.asUInt.resize(27)
    } otherwise {
      for (i <- 0 until 27) {
        when(dist === U(i, dist.getWidth bits)) {
          if (i == 0) {
            out := value
          } else {
            val shifted = (value |>> i).resized
            val lost = value(i - 1 downto 0).orR
            out := shifted | lost.asUInt.resize(27)
          }
        }
      }
    }
    out
  }

  def shiftRightJam50(value: UInt, dist: UInt): UInt = {
    val out = UInt(50 bits)
    out := value
    when(dist >= U(50, dist.getWidth bits)) {
      out := value.orR.asUInt.resize(50)
    } otherwise {
      for (i <- 0 until 50) {
        when(dist === U(i, dist.getWidth bits)) {
          if (i == 0) {
            out := value
          } else {
            val shifted = (value |>> i).resized
            val lost = value(i - 1 downto 0).orR
            out := shifted | lost.asUInt.resize(50)
          }
        }
      }
    }
    out
  }

  def shiftRightJam52(value: UInt, dist: UInt): UInt = {
    val out = UInt(52 bits)
    out := value
    when(dist >= U(52, dist.getWidth bits)) {
      out := value.orR.asUInt.resize(52)
    } otherwise {
      for (i <- 0 until 52) {
        when(dist === U(i, dist.getWidth bits)) {
          if (i == 0) {
            out := value
          } else {
            val shifted = (value |>> i).resized
            val lost = value(i - 1 downto 0).orR
            out := shifted | lost.asUInt.resize(52)
          }
        }
      }
    }
    out
  }

  def shiftRightJam53(value: UInt, dist: UInt): UInt = {
    val out = UInt(53 bits)
    out := value
    when(dist >= U(53, dist.getWidth bits)) {
      out := value.orR.asUInt.resize(53)
    } otherwise {
      for (i <- 0 until 53) {
        when(dist === U(i, dist.getWidth bits)) {
          if (i == 0) {
            out := value
          } else {
            val shifted = (value |>> i).resized
            val lost = value(i - 1 downto 0).orR
            out := shifted | lost.asUInt.resize(53)
          }
        }
      }
    }
    out
  }

  def shiftRightJam56(value: UInt, dist: UInt): UInt = {
    val out = UInt(56 bits)
    out := value
    when(dist >= U(56, dist.getWidth bits)) {
      out := value.orR.asUInt.resize(56)
    } otherwise {
      for (i <- 0 until 56) {
        when(dist === U(i, dist.getWidth bits)) {
          if (i == 0) {
            out := value
          } else {
            val shifted = (value |>> i).resized
            val lost = value(i - 1 downto 0).orR
            out := shifted | lost.asUInt.resize(56)
          }
        }
      }
    }
    out
  }

  def intSqrtFloor(rad: UInt, rootBits: Int): UInt = {
    var guess = U(0, rootBits bits)
    for (i <- (rootBits - 1) downto 0) {
      val trial = guess | (U(1, rootBits bits) |<< i)
      val trialSq = (trial.resize(64) * trial.resize(64)).resized
      guess = Mux(trialSq <= rad.resize(64), trial, guess)
    }
    guess
  }
}
