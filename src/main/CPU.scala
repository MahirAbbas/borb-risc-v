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
    //val flushPipeline = pc.jump.valid
    val flushPipeline = branch.logic.jumpCmd.valid
    pc.jump << branch.logic.jumpCmd
    
    fetch.fifo.io.flush := flushPipeline
    // Iterate over stages after Decode (Dispatch onwards) that might hold speculative instructions
    val executionStages = Array(4, 5, 6, 7).map(pipeline.ctrl(_))
    executionStages.foreach { ctrl => 
       ctrl.throwWhen(ctrl(MAY_FLUSH) && flushPipeline)
    }
    
    val write = pipeline.ctrl(7)
    val dispCtrl = pipeline.ctrl(4)
    //dispCtrl.down.ready := True

    //write.down.ready := True

  
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
        
        valid.simPublic()
        immed.simPublic()
        sendtoalu.simPublic()
        result.simPublic()
        valid_result.simPublic()
        rdaddr.simPublic()
        lane_sel.simPublic()
        commit.simPublic()
        flush.simPublic()
      }
    }


    // Connect Fetch to External Memory Bus
    io.iBus.cmd << fetch.io.readCmd.cmd
    io.iBus.rsp >> fetch.io.readCmd.rsp

    // Build the pipeline
    pipeline.build()
  }
}
