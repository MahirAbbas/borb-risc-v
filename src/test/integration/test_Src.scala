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
import borb.execute.IntAlu
import borb.dispatch.SrcPlugin._

case class src() extends Component {
  val pipeline = new StageCtrlPipeline()
  val pc = new PC(pipeline.ctrl(0), addressWidth = 32)
  val fetch = Fetch(pipeline.ctrl(1), addressWidth = 32, dataWidth = 32)
  val ram = new UnifiedRam(addressWidth = 32, dataWidth = 32, idWidth = 16)
  val decode = new Decoder(pipeline.ctrl(2))
  val srcPlugin = new SrcPlugin(pipeline.ctrl(3))
  val hazardRange = Array(3, 4, 5, 6).map(e => pipeline.ctrl(e)).toSeq
  val dispatcher = new Dispatch(pipeline.ctrl(4), hazardRange, pipeline)
  val intalu = new IntAlu(pipeline.ctrl(5))


  
  val readStage = pipeline.ctrl(6)
  val readHere = new readStage.Area {
    import borb.execute.IntAlu._
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
    val rdaddr         = up(RD_ADDR)
    
    val src1           = up(RS1)
    val src2           = up(RS2)
    

   val signals = Seq(valid, legal,is_fp,execution_unit,rdtype,rs1type,rs2type,fsr3en,immsel,
     microcode,is_br,is_w,use_ldq,use_stq, SelALU, result, rdaddr, src1, src2)

  signals.foreach(e => e.simPublic())
  }
  srcPlugin.regfileread.regfile.io.simPublic()
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


object test_Src extends App {
  SimConfig.withWave.compile(new src()).doSim { dut =>
    dut.clockDomain.forkStimulus(period = 10)


    for(i <- 0 until dut.ram.memory.wordCount) {
      dut.ram.memory.setBigInt(i, (BigInt("00000000", 16)))
    }
    // Pre-load RAM with some data
    /* val instructions: List[(Long, BigInt)] = List(
      (0,  BigInt("002082b3",16)),      //add 
      (4,  BigInt("40110333",16 )),     //sub 
      (8,  BigInt("003093b3",16 )),     //sll 
      (12, BigInt("0011a433",16 )),     //slt 
      (16, BigInt("0011b4b3",16 )),     //sltu
      (20, BigInt("0040c533",16 )),     //xor
      (24, BigInt("001255b3",16 )),     //srl
      (28, BigInt("4011d633",16 )),     //sra
      (32, BigInt("0030e6b3",16 )),     //or 
      (36, BigInt("00327733",16 )),     //and
    ) */
    

    
    val instructions: List[(Long, BigInt)] = List(
      (0,  BigInt("00500093", 16)),     // add 
      (4,  BigInt("00a00113",16 )),     //sub 
      (8,  BigInt("002081b3",16 )),     //sll 
      (12, BigInt("40218233",16 )),     //slt 
      (16, BigInt("000092b3",16 )),     //sltu
      (20, BigInt("0020c333",16 )),     //xor
    )

    for ((address, data) <- instructions) {
      dut.ram.memory.setBigInt(address.toLong, data)
    }

    dut.clockDomain.assertReset()
    dut.clockDomain.deassertReset()
    dut.clockDomain.assertReset()
    dut.clockDomain.deassertReset()

    for(i <- 0 to 20) {
      dut.clockDomain.waitSampling(1)
      // println(dut.readHere.valid.toBoolean)
      // println(s"Microcode: ${dut.readHere.microcode.toEnum.toString}, LEGAL ${dut.readHere.valid.toBoolean}, ALU ${dut.readHere.SelALU.toBoolean}, RESULT: ${dut.readHere.result.toBigInt}, RD: ${dut.readHere.rdaddr.toBigInt}, SRC1: ${dut.readHere.src1.toBigInt}, SRC2: ${dut.readHere.src2.toBigInt}")
      println(s"regfile valid: ${dut.srcPlugin.regfileread.regfile.io.readerRS1.valid.toBoolean}, srcPlugin valid: ${dut.srcPlugin.rs1Reader.valid.toBoolean}")
      // println(s"${dut.readHere.valid.toBoolean}")
      
      // println(dut.fetch.io.readCmd.cmd.payload.address.toBigInt)
      // println(dut.fetch.io.readCmd.cmd.valid.toBoolean)
      // println(dut.fetch.io.readCmd.cmd.ready.toBoolean)
      // println(dut.fetch.io.readCmd.rsp.payload.data.toBigInt)
      // println(dut.fetch.io.readCmd.rsp.valid.toBoolean)
      // println(dut.readHere.legal.toEnum.toString)
      }
    }
  }

