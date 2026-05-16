package borb.playground

import spinal.core._
import spinal.lib._

case class sign(indexBits: Int = 10, outputBits: Int = 16) extends Component {
  require(indexBits > 1)
  require(outputBits > 2)
  require(indexBits <= 30)

  private val lutSize = 1 << indexBits
  private val amplitude = (1 << (outputBits - 1)) - 1
  private val quarterWave = (0 until lutSize).map { i =>
    val radians = i.toDouble * scala.math.Pi / 2.0 / (lutSize - 1).toDouble
    S(scala.math.round(scala.math.sin(radians) * amplitude).toInt, outputBits bits)
  }

  val io = new Bundle {
    val input = in Bits (32 bits)
    val output = out SInt (outputBits bits)
  }

  val phase = io.input.asUInt
  val quadrant = phase(31 downto 30)
  val rawIndex = phase(29 downto (30 - indexBits))
  val mirroredIndex = ~rawIndex

  val index = UInt(indexBits bits)
  index := rawIndex
  when(quadrant(0)) {
    index := mirroredIndex
  }

  val lut = Vec(quarterWave)
  val magnitude = lut(index)
  io.output := magnitude
  when(quadrant(1)) {
    io.output := -magnitude
  }
}

object sign  {
  def main(args: Array[String]) {
    SpinalVhdl(sign())
  }
}
