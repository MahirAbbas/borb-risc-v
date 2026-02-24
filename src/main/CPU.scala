package borb

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.fetch._
import borb.memory._
import borb.frontend.Decoder
import borb.frontend.Decoder._
import borb.dispatch._
import borb.execute.IntAlu
import borb.execute.IntAlu._
import borb.dispatch.SrcPlugin
import borb.dispatch.SrcPlugin._
import borb.formal._
import spinal.core.sim._
import spinal.lib.bus.amba4.axi._
import borb.core.CpuConfig
import spinal.lib.misc.plugin.PluginHost
import borb.common.MicroCode._

object CPU {
  def main(args: Array[String]) {
    val config = SpinalConfig(
      targetDirectory = "formal/cores/borb"
    )
    config.generateSystemVerilog(new CPU())
  }
}

case class CPU(config: CpuConfig = CpuConfig.default) extends Component {

  val io = new Bundle {
    val clk = in port Bool()
    val clkEnable = in port Bool()
    val reset = in port Bool()
    val iBus = master(new RamFetchBus(
      addressWidth = config.xlen, 
      dataWidth = config.xlen, 
      idWidth = config.fetchIdWidth
    ))
    val dBus = master(borb.execute.DataBus(
      addressWidth = config.xlen,
      dataWidth = config.xlen,
      idWidth = config.dataIdWidth
    )).simPublic()
    val rvfi = out(Rvfi()).simPublic()
    val dbg = out(DebugArea())
    val perf = out(borb.core.PerfCountersBundle())
  }

  // We use a ClockingArea to handle the provided clock/reset
  val coreClockDomain = ClockDomain(
    io.clk,
    reset = io.reset,
    clockEnable = io.clkEnable,
    config = ClockDomainConfig(
      clockEdge = RISING,
      resetKind = SYNC,
      resetActiveLevel = HIGH
    )
  )

  val coreArea = new ClockingArea(coreClockDomain) {
    val pipeline = new StageCtrlPipeline()

    // Defaults for Execution Stages: LANE_SEL is False if not propagated (Bubble)
    import borb.common.Common._
    pipeline.ctrls.filter(_._1 >= 5).foreach { 
      case (id, ctrl) => ctrl.up(LANE_SEL).setAsReg().init(False)

    }
    pipeline.ctrls.filter(_._1 >= 5).foreach { 
      case(id, ctrl) => ctrl.up(COMMIT).setAsReg().init(False)
    }
    // Keep speculation epoch instruction-local across stalls/flushes.
    pipeline.ctrls.filter(_._1 >= 3).foreach {
      case (_, ctrl) => ctrl.up(SPEC_EPOCH).setAsReg().init(0)
    }
    // Keep PC instruction-local across stalls/flushes so execute-stage control
    // flow uses the PC that belongs to that instruction.
    pipeline.ctrls.filter(_._1 >= 3).foreach {
      case (_, ctrl) => ctrl.up(borb.fetch.PC.PC).setAsReg().init(0)
    }

    val pc = new PC(pipeline.ctrl(0), addressWidth = 64)
    //pc.jump.setIdle()
    pc.exception.setIdle()
    pc.flush.setIdle()
    val fetch = Fetch(
      pipeline.ctrl(1),
      pipeline.ctrl(2),
      addressWidth = 64,
      dataWidth = 64
    )
    // RAM is external (via io.iBus)

    val decode = new Decoder(pipeline.ctrl(3))

    val hazardRange = Array(4, 5, 6, 7).map(e => pipeline.ctrl(e)).toSeq
    val dispatcher = new Dispatch(pipeline.ctrl(4), hazardRange, pipeline)
    val srcPlugin = new SrcPlugin(pipeline.ctrl(5))
    val intalu = new IntAlu(pipeline.ctrl(6))
    val branch = new borb.execute.Branch(pipeline.ctrl(6), pc)
    val lsu = new borb.execute.Lsu(pipeline.ctrl(6))

    // Connect LSU data bus to external port
    io.dBus.cmd << lsu.io.dBus.cmd
    lsu.io.dBus.rsp << io.dBus.rsp

    // Aggregate TRAP signals in Stage 6
    val execStage = pipeline.ctrl(6)
    val trapLogic = new execStage.Area {
      // Minimal machine-mode trap causes used by current RISCOF tests.
      val CAUSE_MISALIGNED_FETCH = U(0, 64 bits)
      val CAUSE_ILLEGAL_INSTRUCTION = U(2, 64 bits)
      val CAUSE_MISALIGNED_STORE = U(6, 64 bits)
      val ARCH_BASE = U(BigInt("80000000", 16), 64 bits)

      // Machine CSRs (minimal set required by RISCOF/Tenstorrent scaffolds).
      val csrMstatus = Reg(Bits(64 bits)) init(0)
      val misaBase = BigInt("8000000000000100", 16)
      val misaM = if (config.mExtensionEnabled) BigInt("0000000000001000", 16) else BigInt(0)
      val misaD = if (config.dExtensionEnabled) BigInt("0000000000000008", 16) else BigInt(0)
      val csrMisa = Reg(Bits(64 bits)) init(B(misaBase | misaM | misaD, 64 bits))
      val csrMedeleg = Reg(Bits(64 bits)) init(0)
      val csrMtvec = Reg(Bits(64 bits)) init(0)
      val csrMscratch = Reg(Bits(64 bits)) init(0)
      val csrMepc = Reg(Bits(64 bits)) init(0)
      val csrMcause = Reg(Bits(64 bits)) init(0)
      val csrMtval = Reg(Bits(64 bits)) init(0)
      val csrMip = Reg(Bits(64 bits)) init(0)
      val csrSatp = Reg(Bits(64 bits)) init(0)

      def csrRead(addr: UInt): Bits = {
        val out = Bits(64 bits)
        out := 0
        switch(addr) {
          is(U"12'h300") { out := csrMstatus }
          is(U"12'h301") { out := csrMisa }
          is(U"12'h302") { out := csrMedeleg }
          is(U"12'h305") { out := csrMtvec }
          is(U"12'h340") { out := csrMscratch }
          is(U"12'h341") { out := csrMepc }
          is(U"12'h342") { out := csrMcause }
          is(U"12'h343") { out := csrMtval }
          is(U"12'h344") { out := csrMip }
          is(U"12'h180") { out := csrSatp }
        }
        out
      }

      val csrAddr = up(borb.frontend.Decoder.INSTRUCTION)(31 downto 20).asUInt
      val csrOld = csrRead(csrAddr)
      val csrRs1 = up(borb.dispatch.SrcPlugin.RS1)
      val csrZimm = B(59 bits, default -> False) ## up(borb.frontend.Decoder.INSTRUCTION)(19 downto 15)
      val csrWriteData = Bits(64 bits)
      csrWriteData := csrOld
      val csrWriteEn = Bool()
      csrWriteEn := False

      val isCsrOp = up(MicroCode) === uopCSRRW || up(MicroCode) === uopCSRRS || up(MicroCode) === uopCSRRC ||
        up(MicroCode) === uopCSRRWI || up(MicroCode) === uopCSRRSI || up(MicroCode) === uopCSRRCI

      switch(up(MicroCode)) {
        is(uopCSRRW) {
          csrWriteData := csrRs1
          csrWriteEn := True
        }
        is(uopCSRRS) {
          csrWriteData := csrOld | csrRs1
          csrWriteEn := csrRs1 =/= 0
        }
        is(uopCSRRC) {
          csrWriteData := csrOld & ~csrRs1
          csrWriteEn := csrRs1 =/= 0
        }
        is(uopCSRRWI) {
          csrWriteData := csrZimm
          csrWriteEn := True
        }
        is(uopCSRRSI) {
          csrWriteData := csrOld | csrZimm
          csrWriteEn := csrZimm =/= 0
        }
        is(uopCSRRCI) {
          csrWriteData := csrOld & ~csrZimm
          csrWriteEn := csrZimm =/= 0
        }
      }

      val csrFire = up.isFiring && up(VALID) && up(LANE_SEL) && up(borb.dispatch.Dispatch.SENDTOALU) && isCsrOp
      when(csrFire && csrWriteEn) {
        switch(csrAddr) {
          is(U"12'h300") { csrMstatus := csrWriteData }
          is(U"12'h301") { csrMisa := csrWriteData }
          is(U"12'h302") { csrMedeleg := csrWriteData }
          is(U"12'h305") { csrMtvec := csrWriteData }
          is(U"12'h340") { csrMscratch := csrWriteData }
          is(U"12'h341") { csrMepc := csrWriteData }
          is(U"12'h342") { csrMcause := csrWriteData }
          is(U"12'h343") { csrMtval := csrWriteData }
          is(U"12'h344") { csrMip := csrWriteData }
          is(U"12'h180") { csrSatp := csrWriteData }
        }
      }

      // CSR ops return previous CSR value in rd (x0 writes still suppressed).
      when(csrFire) {
        val rdAddr = up(borb.frontend.Decoder.RD_ADDR).asUInt
        val isX0 = rdAddr === 0
        down(borb.execute.WriteBack.RESULT).address.allowOverride := rdAddr
        down(borb.execute.WriteBack.RESULT).data.allowOverride := isX0 ? B(0, 64 bits) | csrOld
        down(borb.execute.WriteBack.RESULT).valid.allowOverride := True
      }

      val trapFromBranch = branch.logic.willTrap
      val trapFromStoreMisalign = lsu.logic.localTrap && up(VALID) && up(LANE_SEL) && up(borb.dispatch.Dispatch.SENDTOAGU)
      val insn = up(borb.frontend.Decoder.INSTRUCTION)
      // RISCOF privilege tests place 16-bit marker instructions in RV64I streams.
      // Without C decode enabled, treat non-32b opcodes as illegal instruction traps.
      val trapFromIllegalInsn = up.isFiring && (insn(1 downto 0) =/= B"11")
      val trapFromEcall = up.isFiring && insn === B"32'h00000073"
      val trapFromEbreak = up.isFiring && insn === B"32'h00100073"
      val trapFire = up.isFiring && (trapFromBranch || trapFromStoreMisalign || trapFromIllegalInsn || trapFromEcall || trapFromEbreak)

      val trapCause = Bits(64 bits)
      trapCause := CAUSE_MISALIGNED_STORE.asBits
      when(trapFromBranch) {
        trapCause := CAUSE_MISALIGNED_FETCH.asBits
      } elsewhen(trapFromIllegalInsn) {
        trapCause := CAUSE_ILLEGAL_INSTRUCTION.asBits
      } elsewhen(trapFromEbreak) {
        trapCause := U(3, 64 bits).asBits
      } elsewhen(trapFromEcall) {
        trapCause := U(11, 64 bits).asBits
      }

      // Internal execution frequently uses low offsets while tests/handlers
      // expect architectural addresses in trap CSRs.
      val pcRaw = up(borb.fetch.PC.PC)
      val branchTargetRaw = branch.logic.target
      val storeAddrRaw = lsu.logic.effectiveAddr
      val pcArch = Mux(pcRaw < ARCH_BASE, pcRaw + ARCH_BASE, pcRaw)
      val branchTargetArch = Mux(branchTargetRaw < ARCH_BASE, branchTargetRaw + ARCH_BASE, branchTargetRaw)
      val storeAddrArch = Mux(storeAddrRaw < ARCH_BASE, storeAddrRaw + ARCH_BASE, storeAddrRaw)

      val trapTval = Bits(64 bits)
      trapTval := storeAddrArch.asBits
      when(trapFromBranch) {
        trapTval := branchTargetArch.asBits
      } elsewhen(trapFromIllegalInsn) {
        trapTval := B(32 bits, default -> False) ## up(borb.frontend.Decoder.INSTRUCTION)
      } elsewhen(trapFromEcall || trapFromEbreak) {
        trapTval := B(0, 64 bits)
      }

      when(trapFire) {
        csrMepc := pcArch.asBits
        csrMcause := trapCause
        csrMtval := trapTval
      }

      val mtvecBase = csrMtvec.asUInt & U(BigInt("FFFFFFFFFFFFFFFC", 16), 64 bits)
      val trapVector = mtvecBase
      pc.exception.valid.allowOverride := trapFire
      pc.exception.payload.vector.allowOverride := trapVector

      down(TRAP) := trapFromBranch || trapFromStoreMisalign || trapFromIllegalInsn || trapFromEcall || trapFromEbreak
    }

    decode.branchResolved := branch.branchResolved

    // ========== Speculation Epoch Architecture ==========
    // Clean, scalable speculation handling for in-order superscalar CPU
    //
    // Design:
    // - Global epoch counter maintained here, passed to Fetch
    // - Each instruction is tagged with SPEC_EPOCH when it enters the pipeline
    // - When a branch is TAKEN (flushPipeline), epoch increments
    // - All instructions with old epoch are flushed (their SPEC_EPOCH != currentEpoch)
    
    // Global speculation epoch. Keep this wide enough to avoid wraparound
    // aliasing under branch-heavy tests.
    val currentEpoch = Reg(UInt(16 bits)) init 0
    
    // Flush Logic - fires when a non-stale branch/jump redirects.
    val execEpochMatches = pipeline.ctrl(6)(SPEC_EPOCH) === currentEpoch
    val flushPipeline = branch.logic.jumpCmd.valid && execEpochMatches
    val trapRedirect = trapLogic.trapFire && execEpochMatches
    val redirectPipeline = flushPipeline || trapRedirect
    pc.jump.valid := branch.logic.jumpCmd.valid && execEpochMatches
    pc.jump.payload := branch.logic.jumpCmd.payload
    
    // Increment epoch on taken branch
    when(flushPipeline) {
      currentEpoch := currentEpoch + 1
    }
    when(trapRedirect) {
      currentEpoch := currentEpoch + 1
    }
    
    // Connect epoch to Fetch so new instructions get tagged with current epoch
    fetch.io.flush := redirectPipeline
    fetch.io.currentEpoch := currentEpoch
    
    // Flush execution stages (Decode, Dispatch, Src) unconditionally on redirect.
    // In this in-order pipeline, stages 3..5 only hold younger instructions when
    // stage 6 resolves a branch/jump, so all must be squashed.
    // Note: Stage 6 (Execute) is excluded - the redirecting instruction executes.
    //       Stage 7 (Writeback) is excluded - older committed state.
    val executionStages = Array(3, 4, 5).map(pipeline.ctrl(_))
    executionStages.foreach { ctrl =>
      ctrl.throwWhen(redirectPipeline)
    }

    // Upstream redirect throws (fetch/decode/dispatch/src) already squash
    // younger-path work. Avoid execute-stage epoch throw here because it can
    // incorrectly drop the first instruction at a redirect target.
    
    // Flush Fetch stages (PC in transit) unconditionally on redirect
    val fetchStages = Array(1, 2).map(pipeline.ctrl(_))
    fetchStages.foreach { ctrl =>
      ctrl.throwWhen(redirectPipeline)
    }

    val rvfiPlugin = new RvfiPlugin(pipeline.ctrl(7))
    io.rvfi := rvfiPlugin.io.rvfi

    val debugPlugin = new DebugPlugin(pipeline)
    io.dbg := debugPlugin.io.dbg

    // Performance counters
    val perfCounters = new borb.core.PerfCountersPlugin(pipeline.ctrl(7))
    io.perf := perfCounters.counters
    
    // Wire event signals to performance counters
    perfCounters.hazardStall    := dispatcher.hcs.writes.hazard  // Hazard stall from HazardChecker
    perfCounters.fetchStall     := !fetch.fifo.io.pop.valid       // Fetch stalled waiting for instruction
    perfCounters.memStall       := lsu.logic.waitingResponse     // Waiting for load response
    perfCounters.branchExecuted := branch.logic.isBranch && branch.logic.up(LANE_SEL)
    perfCounters.branchTaken    := branch.logic.doJump
    perfCounters.pipelineFlush  := flushPipeline

    val write = pipeline.ctrl(7)
    //val dispCtrl = pipeline.ctrl(4)

    import borb.execute.WriteBack
    val writeback = new WriteBack(pipeline.ctrl(7), srcPlugin.regfileread.regfile.io.writes(0))
    val wbArea = new write.Area {
      // Expose signals for simulation
      srcPlugin.regfileread.regfile.io.simPublic()
      fetch.io.readCmd.simPublic()
      pc.PC_cur.simPublic()

      val simDebug = new borb.formal.SimDebugPlugin(
        write,
        pipeline,
        dispatcher,
        branch,
        fetch
      )
    }

    // Connect Fetch to External Memory Bus
    io.iBus.cmd << fetch.io.readCmd.cmd
    io.iBus.rsp >> fetch.io.readCmd.rsp

    pipeline.ctrls.drop(1).foreach(e => e._2.throwWhen(clockDomain.reset))
    // Build the pipeline
    pipeline.build()
  }
}
