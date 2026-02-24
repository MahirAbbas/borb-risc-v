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
      down(TRAP) := branch.logic.willTrap || lsu.logic.localTrap
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
    pc.jump.valid := branch.logic.jumpCmd.valid && execEpochMatches
    pc.jump.payload := branch.logic.jumpCmd.payload
    
    // Increment epoch on taken branch
    when(flushPipeline) {
      currentEpoch := currentEpoch + 1
    }
    
    // Connect epoch to Fetch so new instructions get tagged with current epoch
    fetch.io.flush := flushPipeline
    fetch.io.currentEpoch := currentEpoch
    
    // Flush execution stages (Decode, Dispatch, Src) unconditionally on redirect.
    // In this in-order pipeline, stages 3..5 only hold younger instructions when
    // stage 6 resolves a branch/jump, so all must be squashed.
    // Note: Stage 6 (Execute) is excluded - the redirecting instruction executes.
    //       Stage 7 (Writeback) is excluded - older committed state.
    val executionStages = Array(3, 4, 5).map(pipeline.ctrl(_))
    executionStages.foreach { ctrl =>
      ctrl.throwWhen(flushPipeline)
    }

    // Safety net: kill any stale-epoch op that slipped past fetch/decode flush.
    // This prevents post-redirect younger instructions from committing side effects.
    execStage.throwWhen(!execEpochMatches)
    pipeline.ctrl(7).throwWhen(pipeline.ctrl(7)(SPEC_EPOCH) =/= currentEpoch)
    
    // Flush Fetch stages (PC in transit) unconditionally on redirect
    val fetchStages = Array(1, 2).map(pipeline.ctrl(_))
    fetchStages.foreach { ctrl =>
       ctrl.throwWhen(flushPipeline)
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
