package borb.formal

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.common.Common._
import borb.fetch.{Fetch, PC}
import borb.frontend.Decoder
import borb.dispatch.SrcPlugin
import borb.common.MicroCode._

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
    val isCommitted = up(COMMIT)

    // Increment order on VALID commit
    when(isCommitted) {
      order := order + 1
    }

    io.rvfi.valid := isCommitted
    io.rvfi.order := order
    io.rvfi.insn := up(Decoder.DECODED_INSTRUCTION)
    io.rvfi.trap := up(TRAP)
    io.rvfi.halt := False
    io.rvfi.intr := False
    io.rvfi.mode := B"11" // M-Mode
    io.rvfi.ixl := B"10" // 64-bit

    val issueProps = up(borb.dispatch.IssueSemantics.PROPS)
    val readsIntRs1 = issueProps.readsIntRs1
    val readsIntRs2 = issueProps.readsIntRs2

    io.rvfi.rs1_addr := readsIntRs1 ? up(Decoder.RS1_ADDR).asUInt | U(0, 5 bits)
    io.rvfi.rs2_addr := readsIntRs2 ? up(Decoder.RS2_ADDR).asUInt | U(0, 5 bits)

    // RVFI integer source operands are only meaningful for integer-register
    // reads. FP/other register classes must report x0/0 here.
    io.rvfi.rs1_rdata := readsIntRs1 ? up(SrcPlugin.RS1) | B(0, 64 bits)
    io.rvfi.rs2_rdata := readsIntRs2 ? up(SrcPlugin.RS2) | B(0, 64 bits)

    val result = up(borb.execute.WriteBack.RESULT)
    // Retire Packet Logic for RD
    // If result.valid is set, it writes to RD.

    io.rvfi.rd_addr := (issueProps.writesIntRd && result.valid) ? result.address | U(0, 5 bits)
    io.rvfi.rd_wdata := (issueProps.writesIntRd && result.valid) ? result.data | B(0, 64 bits)

    val currentPc = (up(Fetch.FETCH_BLOCK_PC) + up(Fetch.FETCH_BYTE_OFFSET).resized).resize(64)
    io.rvfi.pc_rdata := currentPc.asBits
    val microOp = up(Decoder.MicroCode)
    val rs1S = up(SrcPlugin.RS1).asSInt
    val rs2S = up(SrcPlugin.RS2).asSInt
    val rs1U = up(SrcPlugin.RS1).asUInt
    val rs2U = up(SrcPlugin.RS2).asUInt
    val immS = up(SrcPlugin.IMMED).asSInt
    val branchCondition = Bool()
    switch(microOp) {
      is(uopBEQ)  { branchCondition := rs1S === rs2S }
      is(uopBNE)  { branchCondition := rs1S =/= rs2S }
      is(uopBLT)  { branchCondition := rs1S < rs2S }
      is(uopBGE)  { branchCondition := rs1S >= rs2S }
      is(uopBLTU) { branchCondition := rs1U < rs2U }
      is(uopBGEU) { branchCondition := rs1U >= rs2U }
      default     { branchCondition := False }
    }

    val branchTarget = UInt(64 bits)
    switch(microOp) {
      is(uopJALR) {
        branchTarget := (rs1U.asSInt + immS).asUInt
        branchTarget(0) := False
      }
      default {
        branchTarget := (currentPc.asSInt + immS).asUInt
      }
    }

    val branchTaken =
      (microOp === uopJAL) ||
      (microOp === uopJALR) ||
      ((microOp === uopBEQ) && branchCondition) ||
      ((microOp === uopBNE) && branchCondition) ||
      ((microOp === uopBLT) && branchCondition) ||
      ((microOp === uopBGE) && branchCondition) ||
      ((microOp === uopBLTU) && branchCondition) ||
      ((microOp === uopBGEU) && branchCondition)
    val sequentialPcStep = Mux(up(Decoder.IS_COMPRESSED), U(2, 64 bits), U(4, 64 bits))
    io.rvfi.pc_wdata := Mux(branchTaken, branchTarget.asBits, (currentPc + sequentialPcStep).asBits)

    // Memory access signals from LSU
    io.rvfi.mem_addr := up(borb.execute.Lsu.MEM_ADDR).asBits
    io.rvfi.mem_rmask := up(borb.execute.Lsu.MEM_RMASK)
    io.rvfi.mem_wmask := up(borb.execute.Lsu.MEM_WMASK)
    io.rvfi.mem_rdata := up(borb.execute.Lsu.MEM_RDATA)
    io.rvfi.mem_wdata := up(borb.execute.Lsu.MEM_WDATA)
  }
}
