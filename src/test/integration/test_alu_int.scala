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
import borb.dispatch.SrcPlugin._

case class alu_int() extends Component {
  val pipeline = new StageCtrlPipeline()
  val pc = new PC(pipeline.ctrl(0), addressWidth = 32)
  val fetch = Fetch(pipeline.ctrl(1), pipeline.ctrl(2), addressWidth = 32, dataWidth = 32)
  val ram = new UnifiedRam(addressWidth = 32, dataWidth = 32, idWidth = 16)
  val decode = new Decoder(pipeline.ctrl(3))
  val srcPlugin = new SrcPlugin(pipeline.ctrl(4))
  val hazardRange = Array(4, 5, 6, 7).map(e => pipeline.ctrl(e)).toSeq
  val dispatcher = new Dispatch(pipeline.ctrl(5), hazardRange, pipeline)
  val intalu = new IntAlu(pipeline.ctrl(6))


  
  val readStage = pipeline.ctrl(7)
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
    val valid_result   = up(RESULT).valid
    val rdaddr         = up(RD_ADDR)
    
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


object alu_int_app extends App {
  SimConfig.withWave.compile(new alu_int()).doSim { dut =>
    dut.clockDomain.forkStimulus(period = 10)
    
    for(i <- 0 until 31) {
      dut.srcPlugin.regfileread.regfile.mem.setBigInt(i, BigInt("0"))
    }
    
    val instructions: List[(Long, BigInt)] = List(
      (0,  BigInt("06400013", 16)),     // addi 100
      (4,  BigInt("0c800013",16 )),     // addi 200
      (8,  BigInt("002081b3",16 )),     // add
      (12, BigInt("40218233",16 )),     // sub
      (16, BigInt("000092b3",16 )),     // sll
      (20, BigInt("0020c333",16 )),     //xor
    )

    for ((address, data) <- instructions) {
      dut.ram.memory.setBigInt(address.toLong, data)
    }

    dut.clockDomain.assertReset()
    dut.clockDomain.deassertReset()
    dut.clockDomain.assertReset()
    dut.clockDomain.deassertReset()

    for(i <- 0 to 15) {
      dut.clockDomain.waitSampling(1)
      println(s"SRC1: ${dut.readHere.src1.toBigInt}, SRC2: ${dut.readHere.src2.toBigInt}, IMMED: ${dut.readHere.immed.toBigInt}, RESULT: ${dut.readHere.result.toBigInt}, SENDTOALU: ${dut.readHere.sendtoalu.toBoolean}, VALOD: ${dut.readHere.valid.toBoolean}, valid result: ${dut.readHere.valid_result.toBoolean} UOP: ${dut.readHere.microcode.toEnum}")
      // println(s"${dut.srcPlugin.regfileread.regfile.io.readerRS1.data.toBigInt}")
      // println(s"${dut.srcPlugin.regfileread.regfile.io.readerRS2.data.toBigInt}")
      // println(s" restul value: ${dut.readHere.result.toBigInt}")
      // println(s"MicroCode: ${dut.readHere.microcode.toEnum.toString()}, SRC2 source: ${dut.readHere.rs2type.toEnum.toString()}")
      }
    }
  }

