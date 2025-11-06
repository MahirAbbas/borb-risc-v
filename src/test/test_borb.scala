import spinal.core._
import spinal.core.sim._
import borb._
import borb.memory._

class brobSoc extends Component {
  val brob = new Borb()
  val ram  = new UnifiedRam(addressWidth = 32, dataWidth = 32, idWidth = 16)
  brob.srcPlugin.regfileread.regfile.mem.simPublic()
  brob.writeback.uop.simPublic()
  // brob.writeback.valid.simPublic()
  brob.io.ramRead.simPublic()

  // brob.io.ramRead <> ram.io.reads
  // brob.io.ramRead.cmd <> ram.io.reads.cmd
  // brob.io.ramRead.rsp <> ram.io.reads.rsp

  brob.io.ramRead <> ram.io.reads
}


object BorbTest extends App {
  SimConfig.withWave.compile(new brobSoc).doSim{dut =>
    dut.clockDomain.forkStimulus(period = 10)
    val instructions: List[(Long, BigInt)] = List(
        (0,  BigInt("00000000010100000000000010010011", 2)),
        (4,  BigInt("00000000101000000000000100010011", 2)),
        (8,  BigInt("11111111110100000000000110010011", 2)),
        (12, BigInt("00001111111100000000001000010011", 2)),
      )
    
    for((address, data) <- instructions) {
      dut.ram.memory.setBigInt(address.toLong, data)
    }
    

    for (i <- 0 until 15) {
      dut.clockDomain.waitSampling(1)
      println(s" ram fetch rsp: ${dut.brob.io.ramRead.rsp.payload.data.toLong.toHexString}")
      println(s"${dut.brob.writeback.uop.toEnum.toString()}")

      // println(s" ram fetch valid: ${dut.brob.io.ramRead.cmd.valid.toBigInt}")
      // println(s" ram fetch ready: ${dut.brob.io.ramRead.cmd.ready.toBigInt}")
      // println(s"uop: ${dut.brob.writeback.uop.toEnum.toString()}")
      // println(s"valid Decode: ${dut.brob.writeback.valid.toBoolean}")
      // println(s"x1: ${dut.brob.srcPlugin.regfile.mem.getBigInt(1)}")
    }

  }

}
