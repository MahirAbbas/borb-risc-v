package test.frontend

import spinal.core._
import spinal.core.sim._
import borb.fetch._
import borb.LsuL1._
import spinal.lib.misc.pipeline._
import spinal.lib._
import spinal.lib.sim._
import borb.memory._
import borb.fetch.PC
import borb.frontend.Decoder.INSTRUCTION

case class frontEnd() extends Component {
  val pipeline = new StageCtrlPipeline()
  val pc = new PC(pipeline.ctrl(0), addressWidth = 32)
  // pc.PC_cur.simPublic()
  val fetch = Fetch(pipeline.ctrl(1), pipeline.ctrl(2), addressWidth = 32, dataWidth = 32)
  val ram = new UnifiedRam(addressWidth = 32, dataWidth = 32, idWidth = 16)
  val readStage = pipeline.ctrl(3)
  val readHere = new readStage.Area {
    val instr = up(INSTRUCTION)
    // val pc = up(Fetch.PC_delayed)
    instr.simPublic()
    // pc.simPublic()
  }

  pc.exception.setIdle()
  pc.jump.setIdle()
  pc.flush.setIdle()
  // ram.io.reads.cmd << fetch.io.readCmd.cmd
  fetch.io.readCmd.simPublic
  // ram.io.reads.rsp >> fetch.io.readCmd.rsp
  ram.io.reads <> fetch.io.readCmd
  fetch.io.readCmd.simPublic()
  pipeline.build()
}

object test_frontEndCompile extends App {
  SimConfig.compile(new frontEnd)
}

object test_fetch extends App {
  SimConfig.compile(new frontEnd()).doSim { dut =>
      dut.clockDomain.forkStimulus(period = 10)

      // Pre-load RAM with some data
      val instructions: List[(Long, BigInt)] = List(
        (0,  BigInt("11223344", 16)),
        (4,  BigInt("55667788", 16)),
        (8,  BigInt("99aabbcc", 16)),
        (12, BigInt("ddeeff00", 16)),
        (16, BigInt("DEADBEEF", 16)),
        (20, BigInt("CAFEBABE", 16)),
        (24, BigInt("BADF00D0", 16)),
        (28, BigInt("12345678", 16)),
        (32, BigInt("89ABCDEF", 16)),
        (36, BigInt("F1E2D3C0", 16)),
        (40, BigInt("4B5A6978", 16)),
        (44, BigInt("90ABC123", 16)),
        (48, BigInt("76543210", 16)),
        (52, BigInt("FEEDFACE", 16)),
        (56, BigInt("C001D00D", 16)),
        (60, BigInt("AAAAAAAA", 16))
      )

      for ((address, data) <- instructions) {
        dut.ram.memory.setBigInt(address.toLong, data)
      }

      dut.clockDomain.assertReset()
      dut.clockDomain.deassertReset()
      dut.clockDomain.assertReset()
      dut.clockDomain.deassertReset()
      dut.clockDomain.waitSampling(1)


      for(i <- 0 to (instructions.length + 1)) {
        dut.clockDomain.waitSampling(1)
        if(i >= 2) {
          var idx = i-2
          if(idx >=15) {
            idx=15
          }
          val(address, data) = instructions(idx)
          // println(s"Data should be ${data.toLong.toHexString}, got ${dut.readHere.instr.toBigInt.toLong.toHexString} at ${dut.readHere.pc.toBigInt.toLong}")
          // println(s"Data should be ${data.toLong.toHexString}, got ${dut.readHere.instr.toBigInt.toLong.toHexString}")
          println(s"Data should be ${data.toLong.toHexString}, got ${dut.readHere.instr.toBigInt.toLong.toHexString}")
          // print(s"at ${dut.readHere.pc.toBigInt}")
          // print(s"at ${dut.readHere.pc}")
        }

      }
    }
}
