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

case class test_hazards() extends Component {

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
      resetKind = SYNC,
      resetActiveLevel = HIGH 

    ))
  val coreArea = new ClockingArea(coreClockDomain) {
    val pipeline = new StageCtrlPipeline()
    // clockDomain.reset
    pipeline.ctrls.foreach(e => e._2.throwWhen(clockDomain.reset))
    // val wasReset = Reg(Bool()) init False
    // when(ClockDomain.isResetActive) {
    //   wasReset := True
    // }
    // wasReset.simPublic()
    
      

    val pc = new PC(pipeline.ctrl(0), addressWidth = 32)
    val fetch = Fetch(pipeline.ctrl(1), pipeline.ctrl(2), addressWidth = 32, dataWidth = 32)
    val ram = new UnifiedRam(addressWidth = 32, dataWidth = 32, idWidth = 16)
    val decode = new Decoder(pipeline.ctrl(3))
    val hazardRange = Array(4, 5, 6, 7).map(e => pipeline.ctrl(e)).toSeq
    val dispatcher = new Dispatch(pipeline.ctrl(4),hazardRange, pipeline)
    val srcPlugin = new SrcPlugin(pipeline.ctrl(5))
    // srcPlugin.wasReset.simPublic()
    // val hazardChecker = new HazardChecker(hazardRange)
    val intalu = new IntAlu(pipeline.ctrl(6))
    


    
    val write = pipeline.ctrl(7)

    val writeback = new write.Area {
      down.ready := True
      srcPlugin.regfileread.regfile.io.writer.address := RESULT.address
      srcPlugin.regfileread.regfile.io.writer.data := RESULT.data
      srcPlugin.regfileread.regfile.io.writer.valid := RESULT.valid
      // val uop = borb.common.MicroCode()
      // val op = borb.frontend.Decoder.MicroCode
      // uop := up(op)

      // val valid = (borb.frontend.Decoder.VALID)
      // uop := up(MicroCode)

    }
    val readStage = pipeline.ctrl(7)
    val readHere = new readStage.Area {
      // val pc = up(Fetch.PC_delayed)
      val peecee = up(PC.PC)
      peecee.simPublic()
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


object test_hazards_app extends App {
  SimConfig.withWave.compile(new test_hazards()).doSim { dut =>
    dut.coreClockDomain.forkStimulus(period = 50)
    
    for(i <- 0 until 31) {
      dut.coreArea.srcPlugin.regfileread.regfile.mem.setBigInt(i, BigInt("0", 10))
    }
    
    
    val instructions: List[(Long, BigInt)] = List(
                                      // # ----------------------------
                                      // # RAW Hazards (Back-to-Back)
                                      // # ----------------------------
      (0,  BigInt("00500093",16 )),  // addi x1, x0, 5          # x1 = 5
      (4,  BigInt("00300113",16 )),  // addi x2, x0, 3          # x2 = 3
      (8,  BigInt("002081b3",16 )),  // add  x3, x1, x2         # x3 = 8
      (12,  BigInt("00118233",16 )),  // add  x4, x3, x1         # DEPENDS on x3 immediately # x4 = 13
      (16,  BigInt("402202b3",16 )),  // sub  x5, x4, x2         # DEPENDS on x4 immediately # x5 = 10
      (20,  BigInt("0032f333",16 )),  // and  x6, x5, x3         # DEPENDS on x5 # x6 = 8
      (24,  BigInt("004363b3",16 )),  // or   x7, x6, x4         # DEPENDS on x6 # x7 = 13
      (28,  BigInt("0053c433",16 )),  // xor  x8, x7, x5         # DEPENDS on x7 # x8 = 5
      
                                    // # ----------------------------
                                    // # Mixed dependency patterns
                                    // # ----------------------------
      (32,  BigInt("002084b3",16 )),  // add  x9, x1, x2         # x9 = 8 (fresh)
      (36,  BigInt("00348533",16 )),  // add  x10, x9, x3        # depends on x9
      (40,  BigInt("004505b3",16 )),  // add  x11, x10, x4       # depends on x10
      (44,  BigInt("00558633",16 )),   // add  x12, x11, x5       # chain dependency
     
                                   // # ----------------------------
                                   // # One-source dependencies
                                   // # ----------------------------
      (48,  BigInt("00200693",16 )), // addi x13, x0, 2         # independent
      (52,  BigInt("00c68733",16 )), // add  x14, x13, x12      # depends only on x12
      (56,  BigInt("401707b3",16 )), // sub  x15, x14, x1       # depends on x14
    
                                   // # ----------------------------
                                   // # Forwarding test (cross reuse)
                                   // # ----------------------------
      (60,  BigInt("00208833",16 )), // add  x16, x1, x2        # produces x16
      (64,  BigInt("003808b3",16 )), // add  x17, x16, x3       # uses x16 immediately
      (68,  BigInt("00488933",16 )), // add  x18, x17, x4       # uses x17 immediately
      (72,  BigInt("005909b3",16 )), // add  x19, x18, x5       # uses x18 immediately
   
                                    // # ----------------------------
                                    // # Delayed dependency (stall test)
                                    // # ----------------------------
      (76,  BigInt("00208a33",16 )),  // add  x20, x1, x2        # x20 = 8
      (80,  BigInt("00418ab3",16 )),  // add  x21, x3, x4        # unrelated, should not stall
      (84,  BigInt("015a0b33",16 )),  // add  x22, x20, x21      # depends on x20, tests correct stall or bypass timing

  )
    for ((address, data) <- instructions) {
      dut.coreArea.ram.memory.setBigInt(address.toLong, data)
    }

    // println(s"${dut.clockDomain.isResetActive}")
    
    // println(dut.clockDomain.hasResetSignal)
    // println(s"${dut.clockDomain.isResetAsserted}")
    dut.coreClockDomain.assertReset()
    sleep(10)
    // println(dut.coreClockDomain.isResetAsserted)
    dut.coreClockDomain.deassertReset()



    for(i <- 0 to 65) {
      dut.coreClockDomain.waitSampling(1)
      // println(s"${dut.readHere.valid.toBoolean}")
      
      println(s"RegBusy: ${dut.coreArea.dispatcher.hcs.regBusy.toBigInt.toString(2).reverse.padTo(32, '0').reverse} is hazard ${dut.coreArea.dispatcher.hcs.writes.hazard.toBoolean}")
      println(s"RESULT.valid ${dut.coreArea.readHere.valid_result.toBoolean}, RESULT.address ${dut.coreArea.readHere.rdaddr.toLong}, RESULT.data ${dut.coreArea.readHere.result.toLong}, IMMED: ${dut.coreArea.readHere.immed.toLong},SENDTOALU: ${dut.coreArea.readHere.sendtoalu.toBoolean}, VALID: ${dut.coreArea.readHere.valid.toBoolean} ")
      
    }
    // println(dut.srcPlugin.wasReset.toBoolean)
    // println(dut.wasReset.toBoolean)

    // println(s"${dut.srcPlugin.regfileread.regfile.mem.getBigInt(1).toLong}")
    // println(s"${dut.srcPlugin.regfileread.regfile.mem.getBigInt(2).toLong}")
    // println(s"${dut.srcPlugin.regfileread.regfile.mem.getBigInt(3).toLong}")
    // println(s"${dut.srcPlugin.regfileread.regfile.mem.getBigInt(4).toLong}")
    // println(s"${dut.srcPlugin.regfileread.regfile.mem.getBigInt(5).toLong}")
    // println(s"${dut.srcPlugin.regfileread.regfile.mem.getBigInt(6).toLong}")
    // println(s"${dut.srcPlugin.regfileread.regfile.mem.getBigInt(7).toLong}")
    // println(s"${dut.srcPlugin.regfileread.regfile.mem.getBigInt(8).toLong}")
    // println(s"${dut.srcPlugin.regfileread.regfile.mem.getBigInt(9).toLong}")
    // println(s"${dut.srcPlugin.regfileread.regfile.mem.getBigInt(10).toLong}")
    // println(s"${dut.srcPlugin.regfileread.regfile.mem.getBigInt(11).toLong}")
    // println(s"${dut.srcPlugin.regfileread.regfile.mem.getBigInt(12).toLong}")
    // println(s"${dut.srcPlugin.regfileread.regfile.mem.getBigInt(13).toLong}")
    // println(s"${dut.srcPlugin.regfileread.regfile.mem.getBigInt(14).toLong}")
    // println(s"${dut.srcPlugin.regfileread.regfile.mem.getBigInt(15).toLong}")
  }
}

