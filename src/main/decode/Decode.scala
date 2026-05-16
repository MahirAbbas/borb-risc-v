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
import _root_.borb.common.LaneKey

case class uop() extends Bundle {
  
}
object Decoder extends AreaObject {
  val LANES = 2

  val INSTRUCTION = Payload(Bits(32 bits))
  val DECODED_INSTRUCTION = Payload(Bits(32 bits))
  val IS_COMPRESSED = Payload(Bool())
  val IS_VEC = Payload(YESNO())
  val IS_FLOAT = Payload(YESNO())
  val USES_LDQ = Payload(YESNO())
  val USES_STQ = Payload(YESNO())

  val LEGAL = Payload(YESNO())
  val MicroCode = Payload(common.MicroCode())

  val RD_ADDR = Payload(Bits(5 bits))
  val RS1_ADDR = Payload(Bits(5 bits))
  val RS2_ADDR = Payload(Bits(5 bits))
  val RS3_ADDR = Payload(Bits(5 bits))

  val VALID = Payload(Bool())
  val DECODE_ILLEGAL = Payload(Bool())

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
    IS_FLOAT -> 1,
    IS_VEC -> 2,
    MicroCode -> 9,
    USES_LDQ -> 12,
    USES_STQ -> 13
  )

  val specs = payloads.map { case (payload, column) => (new DecodingSpec(payload), payload, column) }

  assert(DecodeTable.X_table(0)._2.length > 9)
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

  class DecodeLaneArea(laneId: Int) extends stage.Area {
    val laneKey = LaneKey(laneId)

    def up[T <: Data](payload: Payload[T]): T = stage.up(payload, laneKey)
    def down[T <: Data](payload: Payload[T]): T = stage.down(payload, laneKey)

    val instruction = up(INSTRUCTION)
    val decodedInstruction = Bits(32 bits)
    decodedInstruction := instruction

    val isCompressed = instruction(1 downto 0) =/= B"11"
    val rvc = RVC(instruction(15 downto 0), xlen = xlen)

    if(withCompressed) {
      when(isCompressed) {
        decodedInstruction := rvc.inst
      }
    }

    val decodeIllegal = if(withCompressed) (isCompressed && rvc.illegal) else False
    val decodeSupported = Symplify(decodedInstruction, all)
    val laneValid = stage.up.isValid && stage.up(common.Common.LANE_MASK)(laneId)
    val pcInRam = up(borb.fetch.PC.PC) >= U(BigInt("80000000", 16), 64 bits)

    down(DECODED_INSTRUCTION) := decodedInstruction
    if(withCompressed) {
      down(IS_COMPRESSED) := isCompressed && !rvc.illegal
    } else {
      down(IS_COMPRESSED) := False
    }
    down(VALID) := laneValid && decodeSupported && !decodeIllegal
    down(DECODE_ILLEGAL) := laneValid && decodeIllegal && pcInRam

    for ((signal, spec) <- specs.map { case (spec, signal, _) => signal -> spec }) {
      down(signal).assignFromBits(spec.build(decodedInstruction, all).asBits)
    }

    down(RD_ADDR) := decodedInstruction(11 downto 7)
    down(RS1_ADDR) := decodedInstruction(19 downto 15)
    down(RS2_ADDR) := decodedInstruction(24 downto 20)
    down(RS3_ADDR) := decodedInstruction(31 downto 27)
  }

  val laneDecoding = for(laneId <- 0 until Decoder.LANES) yield new DecodeLaneArea(laneId)

  // branchResolved signal - set by branch.scala when a branch resolves
  // This was used by shadowLogic (now removed) but may be useful for future branch prediction
  val branchResolved = Bool()
}
