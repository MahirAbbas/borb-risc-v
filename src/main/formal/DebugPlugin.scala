package borb

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.common.Common._
import borb.fetch.PC
import borb.frontend.Decoder
import borb.dispatch.SrcPlugin
import borb.execute.IntAlu

case class Dbg() extends Bundle {
  val commitValid = Bool()
  val commitPc = UInt(64 bits)
  val commitInsn = Bits(32 bits)
  val commitRd = UInt(5 bits)
  val commitWe = Bool()
  val commitWdata = Bits(64 bits)
  val squashed = Bool()

  // Optional: stage PCs / valids
  val f_pc = UInt(64 bits)
  val d_pc = UInt(64 bits)
  val x_pc = UInt(64 bits)
  val wb_pc = UInt(64 bits)
}

case class DebugPlugin(wbStage: CtrlLink) extends Area {
  val io = new Bundle {
    val dbg = out(Dbg())
  }

  val wb = new wbStage.Area {
    io.dbg.commitValid := up(COMMIT)
    io.dbg.commitPc := up(PC.PC)
    io.dbg.commitInsn := up(Decoder.INSTRUCTION)

    val result = up(borb.execute.WriteBack.RESULT)
    io.dbg.commitRd := result.valid ? result.address | U(0, 5 bits)
    io.dbg.commitWe := result.valid && up(
      COMMIT
    ) // Only valid write if committed
    io.dbg.commitWdata := result.valid ? result.data | B(0, 64 bits)

    io.dbg.squashed := !up(LANE_SEL) || up(TRAP)

    io.dbg.wb_pc := up(PC.PC)

    // Placeholder for other stages (need to be passed in or accessed)
  }
}
