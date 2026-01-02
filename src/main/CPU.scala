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
    val iBus = master(new RamFetchBus(addressWidth = 32, dataWidth = 32, idWidth = 16))
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

    val pc = new PC(pipeline.ctrl(0), addressWidth = 32)
    pc.jump.setIdle()
    pc.exception.setIdle()
    pc.flush.setIdle()
    val fetch = Fetch(pipeline.ctrl(1), pipeline.ctrl(2), addressWidth = 32, dataWidth = 32)
    // RAM is external (via io.iBus)
    
    val decode = new Decoder(pipeline.ctrl(3))
    
    val hazardRange = Array(4, 5, 6, 7).map(e => pipeline.ctrl(e)).toSeq
    val dispatcher = new Dispatch(pipeline.ctrl(4), hazardRange, pipeline)
    val srcPlugin = new SrcPlugin(pipeline.ctrl(5))
    val intalu = new IntAlu(pipeline.ctrl(6))
    
    val write = pipeline.ctrl(7)
    val dispCtrl = pipeline.ctrl(4)
    //dispCtrl.down.ready := True

    //write.down.ready := True

  
    val writeback = new write.Area {
      import borb.common.Common._
      // Initialize TRAP to False (no traps implemented yet) and derive COMMIT
      // COMMIT is valid only if valid lane and no trap
      up(TRAP) := False
      up(COMMIT) := !up(TRAP) && up(LANE_SEL)

      // Expose signals for simulation
      srcPlugin.regfileread.regfile.io.simPublic()
      fetch.io.readCmd.simPublic()
      pc.PC_cur.simPublic()
      down.ready := True
      //forgetOneWhen(dispatcher.hcs.writes.hazard)
      
      //down.ready := !dispatcher.hcs.writes.hazard
      //when(dispatcher.hcs.writes.hazard) {
      //  down.ready := False
      //}

      val readHere = new Area {
        val valid          = up(borb.frontend.Decoder.VALID) 
        val immed          = up(borb.dispatch.SrcPlugin.IMMED)
        val sendtoalu      = up(borb.dispatch.Dispatch.SENDTOALU)
        val result         = up(borb.execute.IntAlu.RESULT).data
        val valid_result   = up(borb.execute.IntAlu.RESULT).valid
        val rdaddr         = up(borb.execute.IntAlu.RESULT).address
        val lane_sel       = up(LANE_SEL)
        val commit         = up(COMMIT)
        
        valid.simPublic()
        immed.simPublic()
        sendtoalu.simPublic()
        result.simPublic()
        valid_result.simPublic()
        rdaddr.simPublic()
        lane_sel.simPublic()
        commit.simPublic()
      }

      
      srcPlugin.regfileread.regfile.io.writer.address := RESULT.address
      srcPlugin.regfileread.regfile.io.writer.data := RESULT.data
      // Gated by COMMIT and LANE_SEL
      srcPlugin.regfileread.regfile.io.writer.valid := RESULT.valid && down.isFiring && up(COMMIT) && up(LANE_SEL)
      //srcPlugin.regfileread.regfile.io.writer.valid := RESULT.valid && down.isFiring
      // val uop = borb.common.MicroCode()
      // val op = borb.frontend.Decoder.MicroCode
      // uop := up(op)
    }

    // Connect Fetch to External Memory Bus
    io.iBus.cmd << fetch.io.readCmd.cmd
    io.iBus.rsp >> fetch.io.readCmd.rsp

    // Build the pipeline
    pipeline.build()
  }
}
