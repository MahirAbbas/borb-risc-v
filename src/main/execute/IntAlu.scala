package borb.execute

import spinal.core._
import spinal.lib._

import borb.common.MicroCode._
import borb.frontend.ExecutionUnitEnum

object IntAlu extends AreaObject {
  val SupportedUops = Seq(
    uopXORI,
    uopORI,
    uopANDI,
    uopADDI,
    uopSLTI,
    uopSLTIU,
    uopSLLI,
    uopSRLI,
    uopSRAI,
    uopXOR,
    uopOR,
    uopAND,
    uopADD,
    uopSLL,
    uopSRL,
    uopSRA,
    uopSUB,
    uopSLT,
    uopSLTU,
    uopADDW,
    uopSUBW,
    uopADDIW,
    uopSLLW,
    uopSRLW,
    uopSRAW,
    uopSLLIW,
    uopSRLIW,
    uopSRAIW,
    uopSH1ADD,
    uopSH2ADD,
    uopSH3ADD,
    uopADD_UW,
    uopSH1ADD_UW,
    uopSH2ADD_UW,
    uopSH3ADD_UW,
    uopSLLI_UW,
    uopANDN,
    uopORN,
    uopXNOR,
    uopCLZ,
    uopCTZ,
    uopCPOP,
    uopCLZW,
    uopCTZW,
    uopCPOPW,
    uopMAX,
    uopMAXU,
    uopMIN,
    uopMINU,
    uopSEXTB,
    uopSEXTH,
    uopZEXTH,
    uopROL,
    uopROR,
    uopRORI,
    uopROLW,
    uopRORW,
    uopRORIW,
    uopORCB,
    uopREV8,
    uopBCLR,
    uopBCLRI,
    uopBEXT,
    uopBEXTI,
    uopBINV,
    uopBINVI,
    uopBSET,
    uopBSETI,
    uopCZERO_EQZ,
    uopCZERO_NEZ,
    uopLUI,
    uopAUIPC
  )

  // val RESULT = Payload(new RegFileWrite())
  def computeResult(microCode: borb.common.MicroCode.C, src1: Bits, src2: Bits, immed: Bits, pcRaw: UInt): Bits = {
    val result = Bits(64 bits)

    def signExtendWord(value: Bits): Bits = value(31 downto 0).asSInt.resize(64).asBits
    def zeroExtendWord(value: Bits): UInt = (B(0, 32 bits) ## value(31 downto 0)).asUInt
    def boolResult(value: Bool): Bits = value.asBits.resized
    def add(a: Bits, b: Bits): Bits = (a.asSInt + b.asSInt).asBits
    def sub(a: Bits, b: Bits): Bits = (a.asSInt - b.asSInt).asBits
    def addWord(a: Bits, b: Bits): Bits = signExtendWord((a(31 downto 0).asSInt + b(31 downto 0).asSInt).asBits)
    def subWord(a: Bits, b: Bits): Bits = signExtendWord((a(31 downto 0).asSInt - b(31 downto 0).asSInt).asBits)
    def shiftLeft(value: Bits, amount: UInt): Bits = (value.asUInt |<< amount).asBits
    def shiftRightLogical(value: Bits, amount: UInt): Bits = (value.asUInt |>> amount).asBits
    def shiftRightArithmetic(value: Bits, amount: UInt): Bits = (value.asSInt >> amount).asBits
    def shiftLeftWord(value: Bits, amount: UInt): Bits = signExtendWord((value(31 downto 0).asUInt |<< amount).asBits)
    def shiftRightLogicalWord(value: Bits, amount: UInt): Bits = signExtendWord((value(31 downto 0).asUInt |>> amount).asBits)
    def shiftRightArithmeticWord(value: Bits, amount: UInt): Bits = (value(31 downto 0).asSInt >> amount).resize(64).asBits
    def shiftedAdd(a: Bits, b: Bits, shift: Int): Bits = ((a.asUInt |<< shift) + b.asUInt).asBits
    def shiftedAddUw(a: Bits, b: Bits, shift: Int): Bits = ((zeroExtendWord(a) |<< shift) + b.asUInt).asBits
    def minMaxSigned(a: Bits, b: Bits, wantMax: Boolean): Bits = Mux(if(wantMax) a.asSInt > b.asSInt else a.asSInt < b.asSInt, a, b)
    def minMaxUnsigned(a: Bits, b: Bits, wantMax: Boolean): Bits = Mux(if(wantMax) a.asUInt > b.asUInt else a.asUInt < b.asUInt, a, b)
    def signExtendLow(value: Bits, bits: Int): Bits = value(bits - 1 downto 0).asSInt.resize(64).asBits
    def zeroExtendLow(value: Bits, bits: Int): Bits = value(bits - 1 downto 0).asUInt.resize(64).asBits

    val shamt = src2(5 downto 0).asUInt
    val shamtImm = immed(5 downto 0).asUInt
    val shamtW = src2(4 downto 0).asUInt
    val shamtImmW = immed(4 downto 0).asUInt

    def leadingZeroCount(value: Bits, width: Int): Bits = {
      val count = UInt(7 bits)
      count := width
      for(i <- 0 until width) {
        when(value(i)) {
          count := width - 1 - i
        }
      }
      count.asBits.resized
    }

    def trailingZeroCount(value: Bits, width: Int): Bits = {
      val count = UInt(7 bits)
      count := width
      for(i <- (0 until width).reverse) {
        when(value(i)) {
          count := i
        }
      }
      count.asBits.resized
    }

    def popCount(value: Bits): Bits = CountOne(value).asBits.resized
    def countWord(count: Bits): Bits = signExtendWord(count.asUInt.resize(32).asBits)
    def rol64(value: Bits, amount: UInt): Bits = ((value.asUInt |<< amount) | (value.asUInt |>> (U(64, 7 bits) - amount).resize(6))).asBits
    def ror64(value: Bits, amount: UInt): Bits = ((value.asUInt |>> amount) | (value.asUInt |<< (U(64, 7 bits) - amount).resize(6))).asBits
    def rol32(value: Bits, amount: UInt): Bits = {
      val lo = value(31 downto 0).asUInt
      ((lo |<< amount) | (lo |>> (U(32, 6 bits) - amount).resize(5))).asBits(31 downto 0).asSInt.resize(64).asBits
    }
    def ror32(value: Bits, amount: UInt): Bits = {
      val lo = value(31 downto 0).asUInt
      ((lo |>> amount) | (lo |<< (U(32, 6 bits) - amount).resize(5))).asBits(31 downto 0).asSInt.resize(64).asBits
    }
    val orcBytes = Bits(64 bits)
    val revBytes = Bits(64 bits)
    for(i <- 0 until 8) {
      val byte = src1(8 * i + 7 downto 8 * i)
      orcBytes(8 * i + 7 downto 8 * i) := Mux(byte.orR, B"8'xFF", B"8'x00")
      revBytes(8 * i + 7 downto 8 * i) := src1(8 * (7 - i) + 7 downto 8 * (7 - i))
    }
    val bitMaskRs2 = (U(1, 64 bits) |<< shamt).asBits
    val bitMaskImm = (U(1, 64 bits) |<< shamtImm).asBits
    def bitClear(mask: Bits): Bits = src1 & ~mask
    def bitExtract(amount: UInt): Bits = boolResult((src1.asUInt |>> amount)(0))
    def bitInvert(mask: Bits): Bits = src1 ^ mask
    def bitSet(mask: Bits): Bits = src1 | mask
    def conditionalZero(zeroWhen: Bool): Bits = Mux(zeroWhen, B(0, 64 bits), src1)

    result := microCode.muxDc(
      uopXORI -> (src1 ^ immed),
      uopORI -> (src1 | immed),
      uopANDI -> (src1 & immed),
      uopADDI -> add(src1, immed),
      uopSLTI -> boolResult(src1.asSInt < immed.asSInt),
      uopSLTIU -> boolResult(src1.asUInt < immed.asUInt),
      uopSLLI -> shiftLeft(src1, shamtImm),
      uopSRLI -> shiftRightLogical(src1, shamtImm),
      uopSRAI -> shiftRightArithmetic(src1, shamtImm),
      uopXOR -> (src1 ^ src2),
      uopOR -> (src1 | src2),
      uopAND -> (src1 & src2),
      uopADD -> add(src1, src2),
      uopSLL -> shiftLeft(src1, shamt),
      uopSRL -> shiftRightLogical(src1, shamt),
      uopSRA -> shiftRightArithmetic(src1, shamt),
      uopSUB -> sub(src1, src2),
      uopSLT -> boolResult(src1.asSInt < src2.asSInt),
      uopSLTU -> boolResult(src1.asUInt < src2.asUInt),
      uopADDW -> addWord(src1, src2),
      uopSUBW -> subWord(src1, src2),
      uopADDIW -> addWord(src1, immed),
      uopSLLW -> shiftLeftWord(src1, shamtW),
      uopSRLW -> shiftRightLogicalWord(src1, shamtW),
      uopSRAW -> shiftRightArithmeticWord(src1, shamtW),
      uopSLLIW -> shiftLeftWord(src1, shamtImmW),
      uopSRLIW -> shiftRightLogicalWord(src1, shamtImmW),
      uopSRAIW -> shiftRightArithmeticWord(src1, shamtImmW),
      uopSH1ADD -> shiftedAdd(src1, src2, 1),
      uopSH2ADD -> shiftedAdd(src1, src2, 2),
      uopSH3ADD -> shiftedAdd(src1, src2, 3),
      uopADD_UW -> (zeroExtendWord(src1) + src2.asUInt).asBits,
      uopSH1ADD_UW -> shiftedAddUw(src1, src2, 1),
      uopSH2ADD_UW -> shiftedAddUw(src1, src2, 2),
      uopSH3ADD_UW -> shiftedAddUw(src1, src2, 3),
      uopSLLI_UW -> (zeroExtendWord(src1) |<< shamtImm).asBits,
      uopANDN -> (src1 & ~src2),
      uopORN -> (src1 | ~src2),
      uopXNOR -> ~(src1 ^ src2),
      uopCLZ -> leadingZeroCount(src1, 64),
      uopCTZ -> trailingZeroCount(src1, 64),
      uopCPOP -> popCount(src1),
      uopCLZW -> countWord(leadingZeroCount(src1(31 downto 0), 32)),
      uopCTZW -> countWord(trailingZeroCount(src1(31 downto 0), 32)),
      uopCPOPW -> countWord(popCount(src1(31 downto 0))),
      uopMAX -> minMaxSigned(src1, src2, wantMax = true),
      uopMAXU -> minMaxUnsigned(src1, src2, wantMax = true),
      uopMIN -> minMaxSigned(src1, src2, wantMax = false),
      uopMINU -> minMaxUnsigned(src1, src2, wantMax = false),
      uopSEXTB -> signExtendLow(src1, 8),
      uopSEXTH -> signExtendLow(src1, 16),
      uopZEXTH -> zeroExtendLow(src1, 16),
      uopROL -> rol64(src1, shamt),
      uopROR -> ror64(src1, shamt),
      uopRORI -> ror64(src1, shamtImm),
      uopROLW -> rol32(src1, shamtW),
      uopRORW -> ror32(src1, shamtW),
      uopRORIW -> ror32(src1, shamtImmW),
      uopORCB -> orcBytes,
      uopREV8 -> revBytes,
      uopBCLR -> bitClear(bitMaskRs2),
      uopBCLRI -> bitClear(bitMaskImm),
      uopBEXT -> bitExtract(shamt),
      uopBEXTI -> bitExtract(shamtImm),
      uopBINV -> bitInvert(bitMaskRs2),
      uopBINVI -> bitInvert(bitMaskImm),
      uopBSET -> bitSet(bitMaskRs2),
      uopBSETI -> bitSet(bitMaskImm),
      uopCZERO_EQZ -> conditionalZero(src2 === 0),
      uopCZERO_NEZ -> conditionalZero(src2 =/= 0),
      uopLUI -> immed.asBits,
      uopAUIPC -> add(immed, pcRaw.asBits)
    )
    result
  }
}
case class IntAlu() extends FunctionalUnit(ExecutionUnitEnum.ALU) {
  IntAlu.SupportedUops.foreach(add)
}
