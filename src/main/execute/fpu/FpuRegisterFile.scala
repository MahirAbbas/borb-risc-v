package borb.execute.fpu

import spinal.core._
import spinal.lib._

case class FpuRegFileReadPort(flen: Int) extends Bundle with IMasterSlave {
  val valid = Bool()
  val address = UInt(5 bits)
  val data = Bits(flen bits)

  override def asMaster(): Unit = {
    out(valid, address)
    in(data)
  }
}

case class FpuRegFileWritePort(flen: Int) extends Bundle with IMasterSlave {
  val valid = Bool()
  val address = UInt(5 bits)
  val data = Bits(flen bits)

  override def asMaster(): Unit = {
    out(valid, address, data)
  }
}

case class FpuRegisterFile(
    flen: Int = 64,
    readPorts: Int = 3,
    writePorts: Int = 1
) extends Component {
  require(flen == 32 || flen == 64, "FPU register file supports FLEN=32/64")
  require(readPorts >= 1, "Need at least one FP read port")
  require(writePorts >= 1, "Need at least one FP write port")

  val io = new Bundle {
    val reads = Vec(slave(FpuRegFileReadPort(flen)), readPorts)
    val writes = Vec(slave(FpuRegFileWritePort(flen)), writePorts)
  }

  private val initValue =
    if (flen == 64) B(BigInt("FFFFFFFF00000000", 16), flen bits) else B(0, flen bits)

  val mem = Mem.fill(32)(Bits(flen bits)).init(Seq.fill(32)(initValue))

  for (rp <- io.reads) {
    val raw = mem.readAsync(rp.address)
    rp.data := rp.valid ? raw | B(0, flen bits)
  }

  val selectedAddress = UInt(5 bits)
  val selectedData = Bits(flen bits)
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

  when(selectedValid) {
    mem.write(selectedAddress, selectedData)
  }
}
