package borb.dispatch

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import spinal.lib.misc.plugin._
import spinal.core.sim._
import scala.collection.mutable.ArrayBuffer

case class RegFileWrite() extends Bundle with IMasterSlave {
  val valid = Bool()
  val address = UInt(5 bits)
  val data = Bits(64 bits)
  override def asMaster(): Unit = {
    out(valid, address, data)
  }
}

case class RegFileRead() extends Bundle with IMasterSlave {
  val valid = Bool()
  val address = UInt(5 bits)
  val data = Bits(64 bits)

  override def asMaster(): Unit = {
    out(address, valid)
    in(data)

  }
}

object IntRegFile extends AreaObject {
  val RegFile_RS1 = Payload(Bits(64 bits))
  val RegFile_RS2 = Payload(Bits(64 bits))
}

case class IntRegFile(dataWidth: Int) extends Component {

  // val readers: ArrayBuffer[RegFileRead] = ArrayBuffer(RegFileRead())
  // val writers: ArrayBuffer[RegFileWrite] = ArrayBuffer(RegFileWrite())
  //
  // def newRead() = {
  //   readers += new RegFileRead()
  // }
  // def newWrite() = {
  //   writers += new RegFileWrite()
  // }
  //
  // val io = new Bundle {
  //   val reads = Vec(readers.map(e => slave(new RegFileRead())))
  //   val writes = Vec(writers.map(e => slave(new RegFileWrite())))
  // }

  val io = new Bundle {
    val readerRS1 = slave(new RegFileRead())
    val readerRS2 = slave(new RegFileRead())
    val writer = slave(new RegFileWrite())
  }
  io.simPublic()
  val mem = Mem.fill(32)(Bits(dataWidth bits)).simPublic()

  // io.readerRS1.data := mem.readSync(address = io.readerRS1.address, enable = io.readerRS1.valid)
  // io.readerRS2.data := mem.readSync(address = io.readerRS2.address, enable = io.readerRS2.valid)
  io.readerRS1.data := (io.readerRS1.address === 0) ? B(0, dataWidth bits) | mem.readAsync(address = io.readerRS1.address)
  io.readerRS2.data := (io.readerRS2.address === 0) ? B(0, dataWidth bits) | mem.readAsync(address = io.readerRS2.address)

  when(io.writer.valid && io.writer.address =/= 0) {
    mem.write(address = io.writer.address, data = io.writer.data)
  }


  // Read logic
  // for (port <- io.reads) {
  //   when(port.address === 0) {
  //     port.data := 0
  //   } otherwise {
  //     port.data := mem.readAsync(port.address)
  //   }
  // }
  //
  // // Write logic
  // for (w <- io.writes) {
  //   when(w.valid && w.address =/= 0) {
  //     mem.write(w.address, w.data)
  //   }
  // }
}
