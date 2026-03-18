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
  val IS_FP = Payload(YESNO())
  val EXECUTION_UNIT = Payload(ExecutionUnitEnum())
  val RDTYPE = Payload(REGFILE.RDTYPE())
  val RS1TYPE = Payload(REGFILE.RSTYPE())
  val RS2TYPE = Payload(REGFILE.RSTYPE())
  val FSR3EN = Payload(YESNO())
  val IMMSEL = Payload(Imm_Select())
  val MicroCode = Payload(common.MicroCode())
  val IS_BR = Payload(YESNO())
  val IS_W = Payload(YESNO())
  val USE_LDQ = Payload(YESNO())
  val USE_STQ = Payload(YESNO())

  val RD_ADDR = Payload(Bits(5 bits))
  val RS1_ADDR = Payload(Bits(5 bits))
  val RS2_ADDR = Payload(Bits(5 bits))

  val VALID = Payload(Bool())
}

object ExecutionUnitEnum extends SpinalEnum {
  val ALU, FPU, AGU, BR, FC_F2I, FC_FPU, FC_AGEN, FC_I2F, NA = newElement()
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
  import ExecutionUnitEnum._
  import Decoder._

  val all = mutable.LinkedHashSet[Masked]()
  val payloads = Seq(
    LEGAL,
    IS_FP,
    EXECUTION_UNIT,
    RDTYPE,
    RS1TYPE,
    RS2TYPE,
    FSR3EN,
    IMMSEL,
    MicroCode,
    IS_BR,
    IS_W,
    USE_LDQ,
    USE_STQ
  )

  val specs = payloads.map(k => new DecodingSpec(k)).zip(payloads)

  assert(payloads.length == DecodeTable.X_table(0)._2.length)
  for ((instr, vals) <- DecodeTable.X_table) {
    all += Masked(instr)
    for (((spec, signal), i) <- specs.zipWithIndex) {
      // each spec is the DecodingSpec and its associated CtrlSig
      spec.addNeeds(Masked(instr), Masked(vals(i)))
    }
  }

  //set defaults for sigs as nop
  specs.zipWithIndex.foreach(e => e._1._1.setDefault(Masked(nop(e._2))))


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
    VALID := Symplify(decodeInst, all) && !decodeIllegal

    for (spec <- specs) {
      // down(spec._2).assignDontCare()
      // when(up.isFiring) {
        down(spec._2).assignFromBits(spec._1.build(decodeInst, all).asBits)
      // }
    }

  }

  val logic = new stage.Area {
    down(Decoder.RD_ADDR) := down(Decoder.DECODED_INSTRUCTION)(11 downto 7)
    down(Decoder.RS1_ADDR) := down(Decoder.DECODED_INSTRUCTION)(19 downto 15)
    down(Decoder.RS2_ADDR) := down(Decoder.DECODED_INSTRUCTION)(24 downto 20)
  }

  // branchResolved signal - set by branch.scala when a branch resolves
  // This was used by shadowLogic (now removed) but may be useful for future branch prediction
  val branchResolved = Bool()
}
