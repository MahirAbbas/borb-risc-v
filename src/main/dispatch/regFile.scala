package borb.dispatch

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import spinal.lib.misc.plugin._
import spinal.core.sim._
import scala.collection.mutable.ArrayBuffer

case class RegFileWrite(dataWidth: Int = 64) extends Bundle with IMasterSlave {
  val valid = Bool()
  val address = UInt(5 bits)
  val data = Bits(dataWidth bits)
  override def asMaster(): Unit = {
    out(valid, address, data)
  }
}

case class RegFileRead(dataWidth: Int = 64) extends Bundle with IMasterSlave {
  val valid = Bool()
  val address = UInt(5 bits)
  val data = Bits(dataWidth bits)

  override def asMaster(): Unit = {
    out(address, valid)
    in(data)
  }
}

object IntRegFile extends AreaObject {
  val RegFile_RS1 = Payload(Bits(64 bits))
  val RegFile_RS2 = Payload(Bits(64 bits))
}
case class RegisterFile(
    dataWidth: Int,
    readPorts: Int = 2,
    writePorts: Int = 1,
    zeroReadAddress: Boolean = false,
    ignoreZeroWrites: Boolean = false,
    invalidReadZero: Boolean = false,
    initValue: BigInt = 0,
    lastWriteWins: Boolean = false
) extends Area {

  val io = new Bundle {
    val reads = Vec(RegFileRead(dataWidth), readPorts)
    val writes = Vec(RegFileWrite(dataWidth), writePorts)
  }
  io.simPublic()

  val mem = Mem
    .fill(32)(Bits(dataWidth bits))
    .init(Seq.fill(32)(B(initValue, dataWidth bits)))

  for (port <- io.reads) {
    val raw = mem.readAsync(port.address)
    val zeroed =
      if (zeroReadAddress) Mux(port.address === 0, B(0, dataWidth bits), raw)
      else raw
    val readData =
      if (invalidReadZero) Mux(port.valid, zeroed, B(0, dataWidth bits))
      else zeroed
    port.data := readData
  }

  if (lastWriteWins && writePorts > 1) {
    val selectedAddress = UInt(5 bits)
    val selectedData = Bits(dataWidth bits)
    val selectedValid = Bool()
    selectedAddress := io.writes(0).address
    selectedData := io.writes(0).data
    selectedValid := io.writes(0).valid

    for (i <- 1 until writePorts) {
      when(io.writes(i).valid) {
        selectedAddress := io.writes(i).address
        selectedData := io.writes(i).data
        selectedValid := True
      }
    }

    val selectedWriteAllowed =
      if (ignoreZeroWrites) selectedValid && (selectedAddress =/= 0)
      else selectedValid
    when(selectedWriteAllowed) {
      mem.write(selectedAddress, selectedData)
    }
  } else {
    for ((w, index) <- io.writes.zipWithIndex) {
      val youngerSameAddress = if (writePorts > 1) {
        io.writes
          .drop(index + 1)
          .map { younger =>
            younger.valid && (younger.address === w.address)
          }
          .reduceOption(_ || _)
          .getOrElse(False)
      } else {
        False
      }
      val writeAllowed =
        if (ignoreZeroWrites) w.valid && (w.address =/= 0) else w.valid
      when(writeAllowed && !youngerSameAddress) {
        mem.write(w.address, w.data)
      }
    }
  }
}

case class IntRegFile(dataWidth: Int, readPorts: Int = 2, writePorts: Int = 1)
    extends Area {
  val regFile = RegisterFile(
    dataWidth = dataWidth,
    readPorts = readPorts,
    writePorts = writePorts,
    zeroReadAddress = true,
    ignoreZeroWrites = true
  )

  val io = regFile.io
}
