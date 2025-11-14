package borb.playground

import spinal.core._
import spinal.lib.misc.pipeline._
import spinal.lib.experimental.math._
import spinal.lib._

case class intMul() extends Component {
  val io = new Bundle {
    val a = slave Flow SInt(32 bits)
    val b = slave Flow SInt(32 bits)
    val c = master Flow SInt(32 bits)
  }
  
  val payload = (io.a.payload * io.b.payload).resize(32)
  val regPay = Reg(payload)
  regPay := payload
  io.c.push(regPay)

}
object intMul extends App {
  SpinalVerilog(new intMul())
}
