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
case class IntRegFile(dataWidth: Int, readPorts: Int = 2, writePorts: Int = 1) extends Component {

  val io = new Bundle {
    val reads = Vec(slave(new RegFileRead()), readPorts)
    val writes = Vec(slave(new RegFileWrite()), writePorts)
  }
  io.simPublic()
  
  val mem = Mem.fill(32)(Bits(dataWidth bits)).init(Seq.fill(32)(B(0, dataWidth bits)))

  // Read logic: x0 always returns 0. Explicitly forward same-cycle writes so
  // dependent consumers do not rely on backend-specific Mem read-during-write
  // behavior.
  for (port <- io.reads) {
    val readData = Bits(dataWidth bits)
    readData := mem.readAsync(port.address)
    for (w <- io.writes.reverse) {
      when(w.valid && (w.address =/= 0) && (w.address === port.address)) {
        readData := w.data
      }
    }
    port.data := (port.address === 0) ? B(0, dataWidth bits) | readData
  }

  // Write logic: x0 writes are ignored
  for (w <- io.writes) {
    when(w.valid && w.address =/= 0) {
      mem.write(w.address, w.data)
    }
  }
}
