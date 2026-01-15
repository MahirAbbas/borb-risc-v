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
    pc.jump.setIdle()
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

    val rvfiPlugin = new RvfiPlugin(pipeline.ctrl(7))
    io.rvfi := rvfiPlugin.io.rvfi

    val debugPlugin = new DebugPlugin(pipeline.ctrl(7))
    io.dbg := debugPlugin.io.dbg

    // Wire up PC signals from other stages for debug visibility
    debugPlugin.io.dbg.f_pc := pipeline.ctrl(2)(PC.PC) // Fetch Rsp
    debugPlugin.io.dbg.d_pc := pipeline.ctrl(3)(PC.PC) // Decode
    debugPlugin.io.dbg.x_pc := pipeline.ctrl(6)(PC.PC) // Execute

    val write = pipeline.ctrl(7)
    val dispCtrl = pipeline.ctrl(4)

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
        val valid = up(borb.frontend.Decoder.VALID)
        val immed = up(borb.dispatch.SrcPlugin.IMMED)
        val sendtoalu = up(borb.dispatch.Dispatch.SENDTOALU)
        val result = up(borb.execute.IntAlu.RESULT).data
        val valid_result = up(borb.execute.IntAlu.RESULT).valid
        val rdaddr = up(borb.execute.IntAlu.RESULT).address
        val lane_sel = up(LANE_SEL)
        val commit = up(COMMIT)

        valid.simPublic()
        immed.simPublic()
        sendtoalu.simPublic()
        result.simPublic()
        valid_result.simPublic()
        rdaddr.simPublic()
        lane_sel.simPublic()
        commit.simPublic()
      }
    }

    // Connect Fetch to External Memory Bus
    io.iBus.cmd << fetch.io.readCmd.cmd
    io.iBus.rsp >> fetch.io.readCmd.rsp

    // Build the pipeline
    pipeline.build()
  }
}
