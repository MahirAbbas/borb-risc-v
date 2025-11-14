package borb.playground

import spinal.core._
import spinal.lib.misc.pipeline._
import spinal.lib.experimental.math._
import spinal.lib._

case class FP32Mul() extends Component {
  val io = new Bundle {
    val input = slave Flow(new Bundle {
      val a = Floating32()
      val b = Floating32()
    })
    val result = master Stream(Floating32())
  }
  val pipeline = new StageCtrlPipeline()
  
  // val n0 = pipeline.ctrl(0)
  // n0.up.arbitrateFrom(io.input)
  // val n0Area = new n0.Area{

    
    
  // }
  
  val n0 = new pipeline.Ctrl(0) {
    val a = insert(io.input.payload.a)
    val b = insert(io.input.payload.b)
    val is_nan = insert(Bool())
    val is_inf = insert(Bool())
    val mant_a = insert(U(1, 1 bits) @@ a.mantissa.asUInt)
    val mant_b = insert(U(1, 1 bits) @@ b.mantissa.asUInt)
    
    val sign_mul = (a.sign ^ b.sign)
  }
  
  val n1 = new pipeline.Ctrl(1) {
    // val exp_mul = insert((n0.a.exponent.asUInt +^ n0.b.exponent.asUInt).asSInt)
    val exp_mul = ((n0.a.exponent.asUInt +^ n0.b.exponent.asUInt).asSInt)
    val mant_mul = (n0.mant_a * n0.mant_b)
  }
  
  val n2 = new Node {
    arbitrateTo(io.result)
    val mant_mul_adj = ((n1.mant_mul @@ U(0, 1 bit)) |>> n1.mant_mul.msb.asUInt)(0, n1.mant_mul.getBitsWidth+1 - 2 bits)

    val mant_mul_rounded = mant_mul_adj.fixTo(mant_mul_adj.getWidth downto mant_mul_adj.getWidth - io.result.payload.mantissa.getBitsWidth)
    
    val exp_mul_adj = n1.exp_mul + n1.mant_mul.msb.asUInt.intoSInt + mant_mul_rounded.msb.asSInt
    
    val result = io.result.payload
    
    result.sign := n0.sign_mul
    result.exponent := exp_mul_adj.asBits.resize(io.result.payload.exponent.getBitsWidth)
    result.mantissa := mant_mul_rounded.asBits.resize(io.result.payload.mantissa.getBitsWidth)
  }
  n2.isValid := True
  
  pipeline.build()
}

import spinal.core.sim._

object FPUMul extends App {
  SpinalSystemVerilog(new FP32Mul)


}