package test.frontend

import spinal.core._
import spinal.core.sim._
import borb.fetch._
// import borb.LsuL1._
import spinal.lib.misc.pipeline._
import spinal.lib._
import spinal.lib.sim._
import borb.memory._
import borb.fetch.PC
import borb.frontend.Decoder._
import borb.frontend.Decoder
import borb.dispatch._
import borb.dispatch.Dispatch.SENDTOALU
// import borb.execute.Execute.RESULT
import borb.execute.IntAlu
import borb.execute.IntAlu._
import borb.dispatch.SrcPlugin._

case class test_writeback() extends Component {
  val io = new Bundle {
    val clk = in port Bool()
    val clkEnable = in port Bool()
    val reset = in port Bool()
  }
  val coreClockDomain = ClockDomain(
    io.clk, 
    reset = io.reset,
    clockEnable = io.clkEnable,
    config = ClockDomainConfig(
      clockEdge = RISING,
      resetKind = ASYNC,
      resetActiveLevel = HIGH 

    ))
  val coreArea = new ClockingArea(coreClockDomain) {
    val pipeline = new StageCtrlPipeline()
    val pc = new PC(pipeline.ctrl(0), addressWidth = 32)
    val fetch = Fetch(pipeline.ctrl(1), addressWidth = 32, dataWidth = 32)
    val ram = new UnifiedRam(addressWidth = 32, dataWidth = 32, idWidth = 16)
    val decode = new Decoder(pipeline.ctrl(2))
    val hazardRange = Array(3, 4, 5, 6).map(e => pipeline.ctrl(e)).toSeq
    val dispatcher = new Dispatch(pipeline.ctrl(3),hazardRange, pipeline )
    val srcPlugin = new SrcPlugin(pipeline.ctrl(4))
    val intalu = new IntAlu(pipeline.ctrl(5))
    
    val write = pipeline.ctrl(6)


    val writeback = new write.Area {
      srcPlugin.regfileread.regfile.io.writer.address := RESULT.address
      srcPlugin.regfileread.regfile.io.writer.data := RESULT.data
      srcPlugin.regfileread.regfile.io.writer.valid := RESULT.valid
      val uop = borb.common.MicroCode()
      val op = borb.frontend.Decoder.MicroCode
      uop := up(op)

      val valid = (borb.frontend.Decoder.VALID)
      // uop := up(MicroCode)

    }
    val readStage = pipeline.ctrl(6)
    val readHere = new readStage.Area {
      // val pc = up(Fetch.PC_delayed)
      // pc.simPublic()
      val valid          = up(VALID)
      val legal          = up(LEGAL)
      val is_fp          = up(IS_FP)
      val execution_unit = up(EXECUTION_UNIT)
      val rdtype         = up(RDTYPE)
      val rs1type        = up(RS1TYPE)
      val rs2type        = up(RS2TYPE)
      val fsr3en         = up(FSR3EN)
      val immsel         = up(IMMSEL)
      val microcode      = up(MicroCode)
      val is_br          = up(IS_BR)
      val is_w           = up(IS_W)
      val use_ldq        = up(USE_LDQ)
      val use_stq        = up(USE_STQ)
      
      val SelALU         = up(SENDTOALU)
      val result         = up(RESULT).data
      val valid_result   = up(RESULT).valid
      val rdaddr         = up(RESULT).address
      
      val src1           = up(RS1)
      val src2           = up(RS2)
      val immed          = up(IMMED)
      val sendtoalu      = up(SENDTOALU)
      

    val signals = Seq(valid, legal,is_fp,execution_unit,rdtype,rs1type,rs2type,fsr3en,immsel,
      microcode,is_br,is_w,use_ldq,use_stq, SelALU, result, rdaddr, src1, src2, immed, sendtoalu, valid_result)

    signals.foreach(e => e.simPublic())
    }
    srcPlugin.regfileread.regfile.io.simPublic()
    srcPlugin.regfileread.regfile.mem.simPublic()
    srcPlugin.rs1Reader.simPublic()
    srcPlugin.rs2Reader.simPublic()

    pc.exception.setIdle()
    pc.jump.setIdle()
    pc.flush.setIdle()
    ram.io.reads.cmd << fetch.io.readCmd.cmd
    fetch.io.readCmd.simPublic
    ram.io.reads.rsp >> fetch.io.readCmd.rsp
    fetch.io.readCmd.simPublic()
    pipeline.build()
      
    }
}


object test_writeback_app extends App {
  SimConfig.withWave.compile(new test_writeback()).doSim(seed = 1902073593) { dut =>

    dut.coreClockDomain.forkStimulus(20)
    dut.coreClockDomain.assertReset();sleep(10)
    dut.coreClockDomain.deassertReset()
    
    for(i <- 0 until 31) {
      dut.coreArea.srcPlugin.regfileread.regfile.mem.setBigInt(i, BigInt("0", 10))
    }
    
    val instructions: List[(Long, BigInt)] = List(
      (0,  BigInt("00500093",16 )),     // addi x1, x0, 5          #  0 + 5  = 5
      (4,  BigInt("ffd00113",16 )),     // addi x2, x0, -3         #  0 + (-3) = 0xFFFFFFFD
      (8,  BigInt("00c00193",16 )),     // addi x3, x0, 12         #  0 + 12 = 12
      (12,  BigInt("0f000213",16 )),     // addi x4, x0, 0xF0       #  load test pattern

      (16,  BigInt("00a0a293",16 )),     // slti x5, x1, 10         #  (5 < 10) = 1
      (20,  BigInt("00012313",16 )),     // slti x6, x2, 0          #  (-3 < 0) = 1
      (24,  BigInt("00013393",16 )),     // sltiu x7, x2, 0         #  unsigned(0xFFFFFFFD) < 0 ? 0
      (28,  BigInt("0ff0b413",16 )),     // sltiu x8, x1, 0xFF      #  5 < 255 = 1

      (32,  BigInt("00f24493",16 )),     // xori x9, x4, 0x0F       #  0xF0 ^ 0x0F = 0xFF
      (36,  BigInt("00a26513",16 )),     // ori  x10, x4, 0x0A      #  0xF0 | 0x0A = 0xFA
      (40,  BigInt("00f27593",16 )),     // andi x11, x4, 0x0F      #  0xF0 & 0x0F = 0x00

      (44,  BigInt("00109613",16 )),     // slli x12, x1, 1         #  5 << 1 = 10
      (48,  BigInt("00409693",16 )),     // slli x13, x1, 4         #  5 << 4 = 80
      (52,  BigInt("00225713",16 )),     // srli x14, x4, 2         #  0xF0 >> 2 = 0x3C
      (56,  BigInt("40115793",16 )),     // srai x15, x2, 1         #  -3 >> 1 = -2 (arithmetic shift)
      
    )

    for ((address, data) <- instructions) {
      dut.coreArea.ram.memory.setBigInt(address.toLong, data)
    }


    for(i <- 0 to 25) {
      
      // dut.clockDomain.waitSampling(1)
      dut.coreClockDomain.waitSampling(1)
      println(s"RESULT.valid ${dut.coreArea.readHere.valid_result.toBoolean}, RESULT.address ${dut.coreArea.readHere.rdaddr.toLong}, RESULT.data ${dut.coreArea.readHere.result.toLong}, IMMED: ${dut.coreArea.readHere.immed.toLong},SENDTOALU: ${dut.coreArea.readHere.sendtoalu.toBoolean}, VALID: ${dut.coreArea.readHere.valid.toBoolean}, UOP: ${dut.coreArea.readHere.microcode.toEnum}")
      // println(s"${dut.coreArea.dispatcher.hcs.regBusy.toBigInt.toString(2).reverse.padTo(32, '0').reverse}")
    }

    println(s"${dut.coreArea.srcPlugin.regfileread.regfile.mem.getBigInt(1).toLong}")
    println(s"${dut.coreArea.srcPlugin.regfileread.regfile.mem.getBigInt(2).toLong}")
    println(s"${dut.coreArea.srcPlugin.regfileread.regfile.mem.getBigInt(3).toLong}")
    println(s"${dut.coreArea.srcPlugin.regfileread.regfile.mem.getBigInt(4).toLong}")
    println(s"${dut.coreArea.srcPlugin.regfileread.regfile.mem.getBigInt(5).toLong}")
    println(s"${dut.coreArea.srcPlugin.regfileread.regfile.mem.getBigInt(6).toLong}")
    println(s"${dut.coreArea.srcPlugin.regfileread.regfile.mem.getBigInt(7).toLong}")
    println(s"${dut.coreArea.srcPlugin.regfileread.regfile.mem.getBigInt(8).toLong}")
    println(s"${dut.coreArea.srcPlugin.regfileread.regfile.mem.getBigInt(9).toLong}")
    println(s"${dut.coreArea.srcPlugin.regfileread.regfile.mem.getBigInt(10).toLong}")
    println(s"${dut.coreArea.srcPlugin.regfileread.regfile.mem.getBigInt(11).toLong}")
    println(s"${dut.coreArea.srcPlugin.regfileread.regfile.mem.getBigInt(12).toLong}")
    println(s"${dut.coreArea.srcPlugin.regfileread.regfile.mem.getBigInt(13).toLong}")
    println(s"${dut.coreArea.srcPlugin.regfileread.regfile.mem.getBigInt(14).toLong}")
    println(s"${dut.coreArea.srcPlugin.regfileread.regfile.mem.getBigInt(15).toLong}")

    // assert(s"${dut.srcPlugin.regfileread.regfile.mem.getBigInt(1).toLong}" == "5")
    // assert(s"${dut.srcPlugin.regfileread.regfile.mem.getBigInt(2).toLong}" == "-3")
    // assert(s"${dut.srcPlugin.regfileread.regfile.mem.getBigInt(3).toLong}" == "12")
    // assert(s"${dut.srcPlugin.regfileread.regfile.mem.getBigInt(4).toLong}" == "240")
    // assert(s"${dut.srcPlugin.regfileread.regfile.mem.getBigInt(5).toLong}" == "1")
    // assert(s"${dut.srcPlugin.regfileread.regfile.mem.getBigInt(6).toLong}" == "1")
    // assert(s"${dut.srcPlugin.regfileread.regfile.mem.getBigInt(7).toLong}" == "0")
    // assert(s"${dut.srcPlugin.regfileread.regfile.mem.getBigInt(8).toLong}" == "1")
    // assert(s"${dut.srcPlugin.regfileread.regfile.mem.getBigInt(9).toLong}" == "255")
    // assert(s"${dut.srcPlugin.regfileread.regfile.mem.getBigInt(10).toLong}" == "250")
    // assert(s"${dut.srcPlugin.regfileread.regfile.mem.getBigInt(11).toLong}" == "0")
    // assert(s"${dut.srcPlugin.regfileread.regfile.mem.getBigInt(12).toLong}" == "10")
    // assert(s"${dut.srcPlugin.regfileread.regfile.mem.getBigInt(13).toLong}" == "80")
    // assert(s"${dut.srcPlugin.regfileread.regfile.mem.getBigInt(14).toLong}" == "60")
    // assert(s"${dut.srcPlugin.regfileread.regfile.mem.getBigInt(15).toLong}" == "-2")
  }
}

