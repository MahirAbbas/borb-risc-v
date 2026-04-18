package borb.fetch

import spinal.core._
import spinal.lib._

case class FrontendQueue(config: FrontendConfig) extends Component {
  val io = new Bundle {
    val flush = in Bool()
    val push = slave(Stream(FetchBundle(config)))
    val pop = master(Stream(FetchBundle(config)))
    val occupancy = out UInt(log2Up(config.bundleQueueDepth + 1) bits)
  }

  private val fifo = StreamFifo(FetchBundle(config), config.bundleQueueDepth)

  fifo.io.flush := io.flush
  fifo.io.push << io.push
  io.pop << fifo.io.pop
  io.occupancy := fifo.io.occupancy
}
