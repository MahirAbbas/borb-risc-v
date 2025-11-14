package borb.playground


import spinal.core._
import spinal.lib.misc.pipeline._
import spinal.lib.experimental.math._
import spinal.lib._


case class afixMul() extends Component {
  val io = new Bundle {
    val a = slave Flow SFix(8 exp,32 bits)
    val b = slave Flow SFix(8 exp,32 bits)
    val c = master Flow SFix(8 exp,32 bits)
  }
  
  val payload = (io.a.payload * io.b.payload).truncated(8 exp, 32 bits)
  val regPay = Reg(payload)
  regPay := payload
  io.c.push(regPay)

}
object compileafix extends App {
  SpinalVerilog(new afixMul())
}