package borb.frontend

import spinal.lib.misc.pipeline._
import spinal.core._
import spinal.lib._
import spinal.lib.logic.Masked
import spinal.lib.logic.DecodingSpec
import scala.collection.mutable
// import borb.decode.Decoder._
import spinal.lib.cpu.riscv.impl.Alu
import spinal.lib.logic.Symplify
import _root_.borb.common

case class uop() extends Bundle {
  
}
object Decoder extends AreaObject {

  val INSTRUCTION = Payload(Bits(32 bits))
  val DECODED_INSTRUCTION = Payload(Bits(32 bits))
  val IS_COMPRESSED = Payload(Bool())

  val LEGAL = Payload(YESNO())
  val MicroCode = Payload(common.MicroCode())

  val RD_ADDR = Payload(Bits(5 bits))
  val RS1_ADDR = Payload(Bits(5 bits))
  val RS2_ADDR = Payload(Bits(5 bits))
  val RS3_ADDR = Payload(Bits(5 bits))

  val VALID = Payload(Bool())
  val DECODE_ILLEGAL = Payload(Bool())

  case class DecoderResult() extends Bundle {
    val decodedInstruction = Bits(32 bits)
    val isCompressed = Bool()
    val legal = YESNO()
    val microCode = common.MicroCode()
    val rdAddr = Bits(5 bits)
    val rs1Addr = Bits(5 bits)
    val rs2Addr = Bits(5 bits)
    val rs3Addr = Bits(5 bits)
    val decodeIllegal = Bool()
    val valid = Bool()
  }

  def decodeInstruction(instruction: Bits, withCompressed: Boolean = false, xlen: Int = 64): DecoderResult = {
    import DecodeTable._

    val all = mutable.LinkedHashSet[Masked]()
    val payloads = Seq(
      LEGAL -> 0,
      MicroCode -> 8
    )
    val specs = payloads.map { case (payload, column) => (new DecodingSpec(payload), payload, column) }

    assert(DecodeTable.X_table(0)._2.length > 8)
    for ((instr, vals) <- DecodeTable.X_table) {
      all += Masked(instr)
      for ((spec, _, column) <- specs) {
        spec.addNeeds(Masked(instr), Masked(vals(column)))
      }
    }
    specs.foreach { case (spec, _, column) => spec.setDefault(Masked(nop(column))) }

    val result = DecoderResult()
    val decodeInst = Bits(32 bits)
    decodeInst := instruction
    val isCompressed = instruction(1 downto 0) =/= B"11"
    val rvc = RVC(instruction(15 downto 0), xlen = xlen)

    if(withCompressed) {
      when(isCompressed) {
        decodeInst := rvc.inst
      }
    }

    val decodeIllegal = if(withCompressed) (isCompressed && rvc.illegal) else False
    val supported = Symplify(decodeInst, all)
    val legal = YESNO()
    val microCode = common.MicroCode()
    legal.assignFromBits(specs.find(_._2 == LEGAL).get._1.build(decodeInst, all).asBits)
    microCode.assignFromBits(specs.find(_._2 == MicroCode).get._1.build(decodeInst, all).asBits)

    result.decodedInstruction := decodeInst
    result.isCompressed := (if(withCompressed) isCompressed && !rvc.illegal else False)
    result.legal := legal
    result.microCode := microCode
    result.rdAddr := decodeInst(11 downto 7)
    result.rs1Addr := decodeInst(19 downto 15)
    result.rs2Addr := decodeInst(24 downto 20)
    result.rs3Addr := decodeInst(31 downto 27)
    result.decodeIllegal := decodeIllegal
    result.valid := supported && !decodeIllegal
    result
  }
}

object ExecutionUnitEnum extends SpinalEnum {
  val ALU, FPU, AGU, BR, NA = newElement()
}

object REGFILE {
  object REGTYPES extends SpinalEnum {
    val INT, FP, VEC = newElement()
  }
  object RDTYPE extends SpinalEnum {
    val RD_INT, RD_FP, RD_VEC, RD_NA = newElement()
  }
  object RSTYPE extends SpinalEnum() {
    val RS_INT, RS_FP, RS_VEC, IMMED, RS_NA = newElement()
  }
}
object Imm_Select extends SpinalEnum {
  val N_IMM, I_IMM, S_IMM, B_IMM, U_IMM, J_IMM = newElement()
}

object YESNO extends SpinalEnum {
  val Y, N = newElement()
}

case class Decoder(stage: CtrlLink, withCompressed: Boolean = false, xlen: Int = 64) extends Area {
  import DecodeTable._
  import Decoder._

  val all = mutable.LinkedHashSet[Masked]()
  val payloads = Seq(
    LEGAL -> 0,
    MicroCode -> 8
  )

  val specs = payloads.map { case (payload, column) => (new DecodingSpec(payload), payload, column) }

  assert(DecodeTable.X_table(0)._2.length > 8)
  for ((instr, vals) <- DecodeTable.X_table) {
    all += Masked(instr)
    for ((spec, _, column) <- specs) {
      spec.addNeeds(Masked(instr), Masked(vals(column)))
    }
  }

  //set defaults for sigs as nop
  specs.foreach { case (spec, _, column) => spec.setDefault(Masked(nop(column))) }


  import spinal.core.sim._
  val trap = new stage.Area {}

  val decodeLane = new stage.Area {
    val decodeInst = Bits(32 bits)
    decodeInst := up(INSTRUCTION)
    val isCompressed = up(INSTRUCTION)(1 downto 0) =/= B"11"
    val rvc = RVC(up(INSTRUCTION)(15 downto 0), xlen = xlen)

    if(withCompressed) {
      when(isCompressed) {
        decodeInst := rvc.inst
      }
    }

    val decodeIllegal = if(withCompressed) (isCompressed && rvc.illegal) else False

    down(DECODED_INSTRUCTION) := decodeInst
    if(withCompressed) {
      down(IS_COMPRESSED) := isCompressed && !rvc.illegal
    } else {
      down(IS_COMPRESSED) := False
    }

    val decodeSupported = Symplify(decodeInst, all)
    val pcInRam = up(borb.fetch.PC.PC) >= U(BigInt("80000000", 16), 64 bits)
    VALID := stage.up.isValid && decodeSupported && !decodeIllegal
    down(DECODE_ILLEGAL) := stage.up.isValid && decodeIllegal && pcInRam

    for ((spec, signal, _) <- specs) {
      down(signal).assignFromBits(spec.build(decodeInst, all).asBits)
    }

  }

  val logic = new stage.Area {
    down(Decoder.RD_ADDR) := down(Decoder.DECODED_INSTRUCTION)(11 downto 7)
    down(Decoder.RS1_ADDR) := down(Decoder.DECODED_INSTRUCTION)(19 downto 15)
    down(Decoder.RS2_ADDR) := down(Decoder.DECODED_INSTRUCTION)(24 downto 20)
    down(Decoder.RS3_ADDR) := down(Decoder.DECODED_INSTRUCTION)(31 downto 27)
  }

  // branchResolved signal - set by branch.scala when a branch resolves
  // This was used by shadowLogic (now removed) but may be useful for future branch prediction
  val branchResolved = Bool()
}
