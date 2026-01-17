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
import spinal.core.sim._

case class CPU() extends Component {

  val io = new Bundle {
    val clk = in port Bool()
    val clkEnable = in port Bool()
    val reset = in port Bool()
    val iBus = master(new RamFetchBus(addressWidth = 64, dataWidth = 64, idWidth = 16))
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
    val fetch = Fetch(pipeline.ctrl(1), pipeline.ctrl(2), addressWidth = 64, dataWidth = 64)
    // RAM is external (via io.iBus)
    
    val decode = new Decoder(pipeline.ctrl(3))
    
    val hazardRange = Array(4, 5, 6, 7).map(e => pipeline.ctrl(e)).toSeq
    val dispatcher = new Dispatch(pipeline.ctrl(4), hazardRange, pipeline)
    val srcPlugin = new SrcPlugin(pipeline.ctrl(5))
    val intalu = new IntAlu(pipeline.ctrl(6))
    val branch = new borb.execute.Branch(pipeline.ctrl(6), pc)

    decode.branchResolved := branch.branchResolved

    // Flush Logic
    // If a branch mispredicts (redirects), flush all instructions that were speculatively fetched (MAY_FLUSH)
    val flushPipeline = branch.logic.jumpCmd.valid
    pc.jump << branch.logic.jumpCmd
    
    fetch.io.flush := flushPipeline
    
    // Flush all upstream stages combinationally
    //val upstreamStages = Array(3, 4, 5).map(pipeline.ctrl(_))
    //upstreamStages.foreach { ctrl => 
       //ctrl.throwWhen(flushPipeline)
    //}

    // Iterate over stages after Decode (Dispatch onwards) that might hold speculative instructions
    // Note: Stage 6 (Execute/Branch) is EXCLUDED - the instruction that fires the flush has already
    // executed. Stage 7 (Writeback) is also excluded - instructions there have committed architecturally.
    // Only upstream stages (3,4,5) need to be flushed.
    
    // Flush State Machine: Extend the flush signal for enough cycles to clear stages 3, 4, 5
    // When a branch fires at stage 6, speculative instructions may be in stages 3, 4, or 5.
    // We need to keep throwing them until they've all been cleared (3 cycles).
    val flushDelay1 = RegNext(flushPipeline) init False
    val flushDelay2 = RegNext(flushDelay1) init False
    val flushDelay3 = RegNext(flushDelay2) init False
    val extendedFlush = flushPipeline || flushDelay1 || flushDelay2 || flushDelay3
    
    val executionStages = Array(3, 4, 5).map(pipeline.ctrl(_))
    executionStages.foreach { ctrl => 
       ctrl.throwWhen(ctrl(MAY_FLUSH) && extendedFlush)
    }
    
    // Flush Fetch stages (PC in transit) unconditionally on redirect
    val fetchStages = Array(1, 2).map(pipeline.ctrl(_))
    fetchStages.foreach { ctrl =>
       ctrl.throwWhen(flushPipeline)
    }
    
    val write = pipeline.ctrl(7)
    import borb.execute.WriteBack
    val writeback = new WriteBack(pipeline.ctrl(7), srcPlugin.regfileread.regfile.io.writer)
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
        val flush          = up(MAY_FLUSH)
        val pc             = up(PC.PC)
        
        valid.simPublic()
        immed.simPublic()
        sendtoalu.simPublic()
        result.simPublic()
        valid_result.simPublic()
        rdaddr.simPublic()
        lane_sel.simPublic()
        commit.simPublic()
        flush.simPublic()
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
        
        pipeline.ctrl(6).up(borb.common.Common.MAY_FLUSH).simPublic()
        pipeline.ctrl(6).up(borb.frontend.Decoder.MicroCode).simPublic()
        pipeline.ctrl(5).up(borb.common.Common.MAY_FLUSH).simPublic()
        pipeline.ctrl(7).up(borb.common.Common.MAY_FLUSH).simPublic()
        
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
