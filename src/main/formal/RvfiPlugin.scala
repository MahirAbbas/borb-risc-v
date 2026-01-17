package borb.formal

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.common.Common._
import borb.fetch.PC
import borb.frontend.Decoder
import borb.dispatch.SrcPlugin
import borb.execute.IntAlu
import borb.execute.Branch

case class Rvfi() extends Bundle {
  val valid = Bool()
  val order = UInt(64 bits) // monotonically increasing commit counter
  // Instruction metadata
  val insn = Bits(32 bits)
  val trap = Bool()
  val halt = Bool()
  val intr = Bool()
  val mode = Bits(2 bits)
  val ixl = Bits(2 bits)

  // Integer register read/write
  val rs1_addr = UInt(5 bits)
  val rs2_addr = UInt(5 bits)
  val rs1_rdata = Bits(64 bits)
  val rs2_rdata = Bits(64 bits)

  val rd_addr = UInt(5 bits)
  val rd_wdata = Bits(64 bits)

  // Program counter
  val pc_rdata = Bits(64 bits) // PC of committed instruction
  val pc_wdata = Bits(64 bits) // next PC after commit

  // Memory access
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
  KeepAttribute(io.rvfi)

  val order = Reg(UInt(64 bits)) init (0)

  val wb = new wbStage.Area {
    // Valid gating: must be firing (no stall) and valid and committed
    // We use COMMIT signal which we defined as the commit event.
    // Also ensuring no TRAP (unless trap commits? Spec implies valid for trap instr).
    // For now assuming normal retirement.
    // User asked for "gated by reset" -> RegInit(0) for order handles it.
    // rvfi_valid itself is combinatorial based on pipeline valid?
    // If we want rvfi_valid=0 during reset, just ensure pipeline valid=0 during reset (Spinal default).

    val isCommitted = up(COMMIT)

    // Increment order on VALID commit
    when(isCommitted) {
      order := order + 1
    }

    io.rvfi.valid := isCommitted
    io.rvfi.order := order
    io.rvfi.insn := up(Decoder.INSTRUCTION)
    io.rvfi.trap := up(TRAP)
    io.rvfi.halt := False
    io.rvfi.intr := False
    io.rvfi.mode := B"11" // M-Mode
    io.rvfi.ixl := B"10" // 64-bit

    io.rvfi.rs1_addr := up(Decoder.RS1_ADDR).asUInt
    io.rvfi.rs2_addr := up(Decoder.RS2_ADDR).asUInt

    // CAPTURED operands from SrcPlugin
    io.rvfi.rs1_rdata := up(SrcPlugin.RS1)
    io.rvfi.rs2_rdata := up(SrcPlugin.RS2)

    val result = up(IntAlu.RESULT)
    // Retire Packet Logic for RD
    // If result.valid is set, it writes to RD.

    io.rvfi.rd_addr := result.address
    io.rvfi.rd_wdata := result.data

    val currentPc = up(PC.PC)
    io.rvfi.pc_rdata := currentPc.asBits
    // Report actual next PC based on branch outcome
    val branchTaken = up(Branch.BRANCH_TAKEN)
    val branchTarget = up(Branch.BRANCH_TARGET)
    io.rvfi.pc_wdata := Mux(branchTaken, branchTarget.asBits, (currentPc + 4).asBits)

    // Memory access signals from LSU
    io.rvfi.mem_addr := up(borb.execute.Lsu.MEM_ADDR).asBits
    io.rvfi.mem_rmask := up(borb.execute.Lsu.MEM_RMASK)
    io.rvfi.mem_wmask := up(borb.execute.Lsu.MEM_WMASK)
    io.rvfi.mem_rdata := up(borb.execute.Lsu.MEM_RDATA)
    io.rvfi.mem_wdata := up(borb.execute.Lsu.MEM_WDATA)
  }
}
