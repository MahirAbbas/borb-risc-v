package borb

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.common.Common._
import borb.fetch.PC
import borb.frontend.Decoder
import borb.dispatch.SrcPlugin
import borb.execute.IntAlu

case class Rvfi() extends Bundle {
  val valid = Bool()
  val order = UInt(64 bits) // monotonically increasing commit counter
  val insn = Bits(32 bits)
  val trap = Bool()
  val halt = Bool()
  val intr = Bool()

  val rs1_addr = UInt(5 bits)
  val rs2_addr = UInt(5 bits)
  val rs1_rdata = Bits(64 bits)
  val rs2_rdata = Bits(64 bits)

  val rd_addr = UInt(5 bits)
  val rd_wdata = Bits(64 bits)

  val pc_rdata = Bits(64 bits) // PC of committed instruction
  val pc_wdata = Bits(64 bits) // next PC after commit

  // For later (loads/stores):
  val mem_addr = Bits(64 bits)
  val mem_rmask = Bits(8 bits)
  val mem_wmask = Bits(8 bits)
  val mem_rdata = Bits(64 bits)
  val mem_wdata = Bits(64 bits)
}

case class RvfiPlugin(wbStage: CtrlLink) extends Area {
  val io = new Bundle {
    val rvfi = out(Rvfi())
  }

  val order = Reg(UInt(64 bits)) init (0)

  val wb = new wbStage.Area {
    val valid = up(COMMIT)

    // Increment order on commit
    when(valid) {
      order := order + 1
    }

    io.rvfi.valid := valid
    io.rvfi.order := order
    io.rvfi.insn := up(Decoder.INSTRUCTION)
    io.rvfi.trap := up(TRAP)
    io.rvfi.halt := False
    io.rvfi.intr := False

    io.rvfi.rs1_addr := up(Decoder.RS1_ADDR).asUInt
    io.rvfi.rs2_addr := up(Decoder.RS2_ADDR).asUInt
    io.rvfi.rs1_rdata := up(SrcPlugin.RS1)
    io.rvfi.rs2_rdata := up(SrcPlugin.RS2)

    val result = up(IntAlu.RESULT)
    // If we write to a register, pass that info.
    // If valid=1, we assume instruction is retired.
    // If result.valid is set, it writes to RD.

    io.rvfi.rd_addr := result.valid ? result.address | U(0, 5 bits)
    io.rvfi.rd_wdata := result.valid ? result.data | B(0, 64 bits)

    val currentPc = up(PC.PC)
    io.rvfi.pc_rdata := currentPc.asBits
    // Simple next PC for now
    io.rvfi.pc_wdata := (currentPc + 4).asBits

    io.rvfi.mem_addr := 0
    io.rvfi.mem_rmask := 0
    io.rvfi.mem_wmask := 0
    io.rvfi.mem_rdata := 0
    io.rvfi.mem_wdata := 0

    // Keep signals alive in the pipeline if they are optimized out
    // (pipeline handles this usually, but to be sure)
  }
}
