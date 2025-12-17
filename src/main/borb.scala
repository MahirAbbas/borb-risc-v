package borb

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.frontend._
import borb.dispatch._
import borb.fetch._
import borb.memory._
import borb.execute._
import borb.common.MicroCode
// import borb.datapath.execute.lsu.Memory

class Borb extends Component {
  val io = new Bundle {
    val ramRead = master(new RamFetchBus(addressWidth = 32, dataWidth = 32, idWidth = 16))
  }
  // PIPELINE
  // FETCH
  // DECODE
  // EXECUTE
  // WRITEBACK
  // MEMORY
  val pipeline = new StageCtrlPipeline()

  val pc = new PC(pipeline.ctrl(0), withCompressed = false, addressWidth = 32)
  val fetch = new Fetch(pipeline.ctrl(1), pipeline.ctrl(2), addressWidth = 32, dataWidth = 32)
  
  io.ramRead.cmd.valid := fetch.io.readCmd.cmd.valid
  io.ramRead.cmd.payload := fetch.io.readCmd.cmd.payload
  fetch.io.readCmd.cmd.ready := io.ramRead.cmd.ready
  val decoder = new Decoder(pipeline.ctrl(3))
  val srcPlugin = new SrcPlugin(pipeline.ctrl(4))
  val hazardRange = Array(4, 5, 6, 7).map(e => pipeline.ctrl(e)).toSeq
  val dispatcher = new Dispatch(pipeline.ctrl(5), hazardRange, pipeline)
  // val hazardRange = Array(2,3,4,5).map(e => pipeline.ctrls.getOrElseUpdate(e, pipeline.ctrl(0))).toSeq
  // val hazardRange = Array(2,3,4).map(e => pipeline.ctrl(e)).toSeq
  // val hazardChecker = new HazardChecker(hazardRange)
  val intalu = new IntAlu(pipeline.ctrl(6))

  import borb.execute.IntAlu._


  val write = pipeline.ctrl(7)

  val writeback = new write.Area {
    srcPlugin.regfileread.regfile.io.writer.address := RESULT.address
    srcPlugin.regfileread.regfile.io.writer.data := RESULT.data
    srcPlugin.regfileread.regfile.io.writer.valid := RESULT.valid
    val uop = borb.common.MicroCode()
    val op = borb.frontend.Decoder.MicroCode
    uop := up(op)


    // uop := up(MicroCode)

  }
  
  // val writeback = new pipeline.ctrl(6).Area {
  //
  // }

  // val execute = new Area {
  //   val memory_address = UInt(64 bits)
  //   val memory_write_data = Bits(64 bits)
  //   val memory_write_enable = Bool()
  // }

  // memory.io.address := execute.memory_address
  // memory.io.writeData := execute.memory_write_data
  // memory.io.writeEnable := execute.memory_write_enable
  // ram.io.d_port_read <> memory.io.read
  // ram.io.d_port_write <> memory.io.write

  // Decoder.INSTRUCTION := fetch.io.instruction
  // val regfile = new IntRegFile(RamReads = 1, RamWrites = 1, dataWidth = 64)
  //
  //
  pipeline.build()
}
