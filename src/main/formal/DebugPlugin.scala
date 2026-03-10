package borb

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.backend.TrapRedirectOutcome
import borb.common.Common._
import borb.fetch.PC
import borb.frontend.Decoder
import borb.dispatch.SrcPlugin
import borb.execute.IntAlu
import borb.execute.Lsu

case class RedirectDebugProbe() extends Bundle {
  val execEpochMatches = Bool()
  val branchRedirect = Bool()
  val trapRedirect = Bool()
  val mretRedirect = Bool()
  val redirectPipeline = Bool()
  val pcJumpValid = Bool()
  val pcJumpTarget = UInt(64 bits)
  val pcExceptionValid = Bool()
  val pcExceptionTarget = UInt(64 bits)
  val liveTrapCause = Bits(64 bits)
  val liveTrapTval = Bits(64 bits)
}

case class DebugArea() extends Bundle {
  val commitValid = Bool()
  val commitOrder = UInt(64 bits)
  val commitSeq = UInt(32 bits)
  val commitPulse = Bool()
  val duplicateRetire = Bool()
  val commitPc = UInt(64 bits)
  val commitInsn = Bits(32 bits)
  val commitRs1 = UInt(5 bits)
  val commitRs2 = UInt(5 bits)
  val commitRs1Data = Bits(64 bits)
  val commitRs2Data = Bits(64 bits)
  val commitRd = UInt(5 bits)
  val commitWe = Bool()
  val commitWdata = Bits(64 bits)
  val commitTrap = Bool()
  val commitTrapCause = Bits(64 bits)
  val commitTrapTval = Bits(64 bits)
  val redirectBranch = Bool()
  val redirectTrap = Bool()
  val redirectMret = Bool()
  val redirectAny = Bool()
  val redirectExecEpochMatches = Bool()
  val redirectPcJumpValid = Bool()
  val redirectPcJumpTarget = UInt(64 bits)
  val redirectPcExceptionValid = Bool()
  val redirectPcExceptionTarget = UInt(64 bits)
  val liveTrapCause = Bits(64 bits)
  val liveTrapTval = Bits(64 bits)
  val squashed = Bool()

  // Optional: stage PCs / valids
  val f_pc = UInt(64 bits)
  val d_pc = UInt(64 bits)
  val x_pc = UInt(64 bits)
  val wb_pc = UInt(64 bits)
  val s4_valid = Bool()
  val s4_fire = Bool()
  val s4_seq = UInt(32 bits)
  val s5_valid = Bool()
  val s5_fire = Bool()
  val s5_lane = Bool()
  val s5_seq = UInt(32 bits)
  val s6_valid = Bool()
  val s6_fire = Bool()
  val s6_lane = Bool()
  val s6_seq = UInt(32 bits)
  val s7_valid = Bool()
  val s7_fire = Bool()
  val s7_lane = Bool()
  val s7_seq = UInt(32 bits)

  // Memory access (from LSU payloads)
  val memAddr = UInt(64 bits)
  val memRmask = Bits(8 bits)
  val memWmask = Bits(8 bits)
  val memRdata = Bits(64 bits)
  val memWdata = Bits(64 bits)
}

case class DebugPlugin(
    pipeline: StageCtrlPipeline,
    trapRedirect: TrapRedirectOutcome,
    redirectProbe: RedirectDebugProbe
) extends Area {
  val s4Stage = pipeline.ctrl(4)
  val s5Stage = pipeline.ctrl(5)
  val s6Stage = pipeline.ctrl(6)
  val wbStage = pipeline.ctrl(7)
  val io = new Bundle {
    val dbg = out(DebugArea())
  }

  val order = Reg(UInt(64 bits)) init (0)

  val wb = new wbStage.Area {
    val stageValid = up.isValid && up(Decoder.VALID) && up(LANE_SEL)
    val isCommitted = stageValid && up(COMMIT)
    when(isCommitted) {
      order := order + 1
    }

    io.dbg.commitValid := isCommitted
    io.dbg.commitPulse := up(COMMIT)
    io.dbg.duplicateRetire := up(borb.execute.WriteBack.DUPLICATE_RETIRE)
    io.dbg.commitOrder := order
    io.dbg.commitSeq := up(borb.fetch.Fetch.FETCH_SEQ)
    io.dbg.commitPc := up(PC.PC)
    io.dbg.commitInsn := up(Decoder.DECODED_INSTRUCTION)
    io.dbg.commitRs1 := up(Decoder.RS1_ADDR).asUInt
    io.dbg.commitRs2 := up(Decoder.RS2_ADDR).asUInt
    io.dbg.commitRs1Data := up(SrcPlugin.RS1)
    io.dbg.commitRs2Data := up(SrcPlugin.RS2)

    val result = up(borb.execute.WriteBack.RESULT)
    io.dbg.commitRd := result.valid ? result.address | U(0, 5 bits)
    io.dbg.commitWe := result.valid && isCommitted
    io.dbg.commitWdata := result.valid ? result.data | B(0, 64 bits)
    io.dbg.commitTrap := up(TRAP)
    io.dbg.commitTrapCause := trapRedirect.trapCause
    io.dbg.commitTrapTval := trapRedirect.trapTval
    io.dbg.redirectBranch := redirectProbe.branchRedirect
    io.dbg.redirectTrap := redirectProbe.trapRedirect
    io.dbg.redirectMret := redirectProbe.mretRedirect
    io.dbg.redirectAny := redirectProbe.redirectPipeline
    io.dbg.redirectExecEpochMatches := redirectProbe.execEpochMatches
    io.dbg.redirectPcJumpValid := redirectProbe.pcJumpValid
    io.dbg.redirectPcJumpTarget := redirectProbe.pcJumpTarget
    io.dbg.redirectPcExceptionValid := redirectProbe.pcExceptionValid
    io.dbg.redirectPcExceptionTarget := redirectProbe.pcExceptionTarget
    io.dbg.liveTrapCause := redirectProbe.liveTrapCause
    io.dbg.liveTrapTval := redirectProbe.liveTrapTval

    io.dbg.squashed := !up(LANE_SEL) || up(TRAP)

    io.dbg.wb_pc := up(PC.PC)

    io.dbg.memAddr := up(Lsu.MEM_ADDR)
    io.dbg.memRmask := up(Lsu.MEM_RMASK)
    io.dbg.memWmask := up(Lsu.MEM_WMASK)
    io.dbg.memRdata := up(Lsu.MEM_RDATA)
    io.dbg.memWdata := up(Lsu.MEM_WDATA)
  }

  // Wire up PC signals from other stages for debug visibility
  io.dbg.f_pc := pipeline.ctrl(2)(PC.PC) // Fetch Rsp
  io.dbg.d_pc := pipeline.ctrl(3)(PC.PC) // Decode
  io.dbg.x_pc := pipeline.ctrl(6)(PC.PC) // Execute
  io.dbg.s4_valid := s4Stage.up.isValid
  io.dbg.s4_fire := s4Stage.up.isFiring
  io.dbg.s4_seq := s4Stage.up(borb.fetch.Fetch.FETCH_SEQ)
  io.dbg.s5_valid := s5Stage.up.isValid
  io.dbg.s5_fire := s5Stage.up.isFiring
  io.dbg.s5_lane := s5Stage.up(LANE_SEL)
  io.dbg.s5_seq := s5Stage.up(borb.fetch.Fetch.FETCH_SEQ)
  io.dbg.s6_valid := s6Stage.up.isValid
  io.dbg.s6_fire := s6Stage.up.isFiring
  io.dbg.s6_lane := s6Stage.up(LANE_SEL)
  io.dbg.s6_seq := s6Stage.up(borb.fetch.Fetch.FETCH_SEQ)
  io.dbg.s7_valid := wbStage.up.isValid
  io.dbg.s7_fire := wbStage.up.isFiring
  io.dbg.s7_lane := wbStage.up(LANE_SEL)
  io.dbg.s7_seq := wbStage.up(borb.fetch.Fetch.FETCH_SEQ)
}
