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

object CPU {
  def main(args: Array[String]) {
    val config = SpinalConfig(
      targetDirectory = "formal/cores/borb"
    )
    config.generateSystemVerilog(new CPU())
  }
}

case class CPU() extends Component {

  val io = new Bundle {
    val clk = in port Bool()
    val clkEnable = in port Bool()
    val reset = in port Bool()
    val iBus = master(
      new RamFetchBus(addressWidth = 64, dataWidth = 64, idWidth = 16)
    )
    val dBus = master(
      borb.execute.DataBus(addressWidth = 64, dataWidth = 64)
    )
    val rvfi = out(Rvfi())
    val dbg = out(Dbg())
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
    pipeline.ctrls.foreach(e => e._2.throwWhen(clockDomain.reset))

    // Define Stages
    // 0: PC
    // 1: Fetch Cmd
    // 2: Fetch Rsp
    // 3: Decoder
    // 4: Dispatch
    // 5: SrcPlugin
    // 6: IntAlu
    // 7: Writeback
    // Define Stages ...
    // Defaults for Execution Stages: LANE_SEL is False if not propagated (Bubble)
    import borb.common.Common._
    pipeline.ctrls.filter(_._1 >= 5).foreach { case (id, ctrl) =>
      // "insertNode" like logic if we had one, but here we just init the register for stages > Dispatch(4)
      ctrl.up(LANE_SEL).setAsReg().init(False)
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
    
    // Global speculation epoch (4-bit = supports 16 in-flight speculation points)
    val currentEpoch = Reg(UInt(4 bits)) init 0
    
    // Flush Logic - fires when branch is taken
    val flushPipeline = branch.logic.jumpCmd.valid
    pc.jump << branch.logic.jumpCmd
    
    // Increment epoch on taken branch
    when(flushPipeline) {
      currentEpoch := currentEpoch + 1
    }
    
    // Connect epoch to Fetch so new instructions get tagged with current epoch
    fetch.io.flush := flushPipeline
    fetch.io.currentEpoch := currentEpoch
    
    // Flush execution stages (Decode, Dispatch, Src) based on epoch match
    // When a branch is taken, currentEpoch still has the OLD value (register update is next cycle)
    // Speculative instructions have SPEC_EPOCH = old_epoch, so we throw if they MATCH
    // After this cycle, currentEpoch increments, and new instructions get the new epoch
    // Note: Stage 6 (Execute) is excluded - the branch has already executed
    //       Stage 7 (Writeback) is excluded - architecturally committed
    val executionStages = Array(3, 4, 5).map(pipeline.ctrl(_))
    executionStages.foreach { ctrl =>
      // Throw if: flush is active AND instruction's epoch matches current (old) epoch
      // These are the speculative instructions that need to be flushed
      ctrl.throwWhen(flushPipeline && (ctrl(SPEC_EPOCH) === currentEpoch))
    }
    
    // Flush Fetch stages (PC in transit) unconditionally on redirect
    val fetchStages = Array(1, 2).map(pipeline.ctrl(_))
    fetchStages.foreach { ctrl =>
       ctrl.throwWhen(flushPipeline)
    }

    val rvfiPlugin = new RvfiPlugin(pipeline.ctrl(7))
    io.rvfi := rvfiPlugin.io.rvfi

    val debugPlugin = new DebugPlugin(pipeline.ctrl(7))
    io.dbg := debugPlugin.io.dbg

    // Wire up PC signals from other stages for debug visibility
    debugPlugin.io.dbg.f_pc := pipeline.ctrl(2)(PC.PC) // Fetch Rsp
    debugPlugin.io.dbg.d_pc := pipeline.ctrl(3)(PC.PC) // Decode
    debugPlugin.io.dbg.x_pc := pipeline.ctrl(6)(PC.PC) // Execute

    val write = pipeline.ctrl(7)
    //val dispCtrl = pipeline.ctrl(4)

    import borb.execute.WriteBack
    val writeback =
      new WriteBack(pipeline.ctrl(7), srcPlugin.regfileread.regfile.io.writer)
    val wbArea = new write.Area {
      // Expose signals for simulation
      srcPlugin.regfileread.regfile.io.simPublic()
      fetch.io.readCmd.simPublic()
      pc.PC_cur.simPublic()

      val readHere = new Area {
        import borb.common.Common._
        val valid          = up(borb.frontend.Decoder.VALID) 
        val immed          = up(borb.dispatch.SrcPlugin.IMMED)
        val sendtoalu      = up(borb.dispatch.Dispatch.SENDTOALU)
        val result         = up(borb.execute.IntAlu.RESULT).data
        val valid_result   = up(borb.execute.IntAlu.RESULT).valid
        val rdaddr         = up(borb.execute.IntAlu.RESULT).address
        val lane_sel       = up(LANE_SEL)
        val commit         = up(COMMIT)
        val specEpoch      = up(SPEC_EPOCH)  // Replaced MAY_FLUSH with SPEC_EPOCH
        val pc             = up(PC.PC)
        
        valid.simPublic()
        immed.simPublic()
        sendtoalu.simPublic()
        result.simPublic()
        valid_result.simPublic()
        rdaddr.simPublic()
        lane_sel.simPublic()
        commit.simPublic()
        specEpoch.simPublic()
        pc.simPublic()
        
        // Debug signals
        pipeline.ctrls.foreach { case (id, ctrl) =>
           ctrl.isValid.simPublic()
           ctrl.down.isFiring.simPublic()
        }
        pipeline.ctrl(3).up(PC.PC).simPublic() // Decode
        pipeline.ctrl(4).up(PC.PC).simPublic() // Dispatch
        pipeline.ctrl(5).up(PC.PC).simPublic() // Src
        pipeline.ctrl(6).up(PC.PC).simPublic() // Ex
        
        dispatcher.hcs.regBusy.simPublic()
        branch.logic.jumpCmd.valid.simPublic()
        branch.logic.pcValue.simPublic()
        branch.logic.target.simPublic()
        
        // Epoch debug signals (replaced MAY_FLUSH)
        pipeline.ctrl(6).up(borb.common.Common.SPEC_EPOCH).simPublic()
        pipeline.ctrl(6).up(borb.frontend.Decoder.MicroCode).simPublic()
        pipeline.ctrl(5).up(borb.common.Common.SPEC_EPOCH).simPublic()
        
        // Fetch debug
        fetch.inflight.simPublic()
        fetch.epoch.simPublic()
        fetch.fifo.io.availability.simPublic()
        fetch.io.readCmd.cmd.valid.simPublic()
        fetch.io.readCmd.rsp.valid.simPublic()
      }
    }

    // Connect Fetch to External Memory Bus
    io.iBus.cmd << fetch.io.readCmd.cmd
    io.iBus.rsp >> fetch.io.readCmd.rsp

    // Build the pipeline
    pipeline.build()
  }
}
