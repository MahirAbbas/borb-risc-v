package borb.execute.fpu

import spinal.core._

object FpuSoftFloatUtils {
  def canonicalNaN32: Bits = B(BigInt("7FC00000", 16), 32 bits)
  def canonicalNaN64: Bits = B(BigInt("7FF8000000000000", 16), 64 bits)

  private def shiftRightJamGeneric(value: UInt, dist: UInt, width: Int): UInt = {
    val out = UInt(width bits)
    out := value.resize(width)
    when(dist >= U(width, dist.getWidth bits)) {
      out := value.orR.asUInt.resize(width)
    } otherwise {
      for (i <- 0 until width) {
        when(dist === U(i, dist.getWidth bits)) {
          if (i == 0) {
            out := value.resize(width)
          } else {
            val shifted = (value |>> i).resize(width)
            val lost = value(i - 1 downto 0).orR
            out := shifted | lost.asUInt.resize(width)
          }
        }
      }
    }
    out
  }

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
    shiftRightJamGeneric(value, dist, 27)
  }

  def shiftRightJam50(value: UInt, dist: UInt): UInt = {
    shiftRightJamGeneric(value, dist, 50)
  }

  def shiftRightJam52(value: UInt, dist: UInt): UInt = {
    shiftRightJamGeneric(value, dist, 52)
  }

  def shiftRightJam53(value: UInt, dist: UInt): UInt = {
    shiftRightJamGeneric(value, dist, 53)
  }

  def shiftRightJam56(value: UInt, dist: UInt): UInt = {
    shiftRightJamGeneric(value, dist, 56)
  }

  def shiftRightJam108(value: UInt, dist: UInt): UInt = {
    shiftRightJamGeneric(value, dist, 108)
  }

  def shiftRightJam109(value: UInt, dist: UInt): UInt = {
    shiftRightJamGeneric(value, dist, 109)
  }

  def shiftRightJam110(value: UInt, dist: UInt): UInt = {
    shiftRightJamGeneric(value, dist, 110)
  }

  def intSqrtFloor(rad: UInt, rootBits: Int): UInt = {
    var guess = U(0, rootBits bits)
    val sqWidth = rootBits * 2
    for (i <- (rootBits - 1) downto 0) {
      val trial = guess | (U(1, rootBits bits) |<< i)
      val trialSq = (trial.resize(sqWidth) * trial.resize(sqWidth)).resized
      guess = Mux(trialSq <= rad.resize(sqWidth), trial, guess)
    }
    guess
  }
}
