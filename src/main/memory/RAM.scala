package borb.memory

import spinal.core._
import spinal.lib._
import spinal.core.sim._
import spinal.core
import borb.execute.DataBus

case class RamFetchCmd(addressWidth: Int, idWidth: Int) extends Bundle {
  val address = UInt(addressWidth bits)
  val id = UInt(idWidth bits)
}
case class RamFetchRsp(dataWidth: Int, addressWidth: Int, idWidth: Int) extends Bundle {
  val data = Bits(dataWidth bits)
  val address = UInt(addressWidth bits)
  val id = UInt(idWidth bits)
}
case class RamFetchBus(addressWidth: Int, dataWidth: Int, idWidth: Int) extends Bundle with IMasterSlave{
  val cmd = Stream(RamFetchCmd(addressWidth, idWidth))
  val rsp = Flow(RamFetchRsp(dataWidth, addressWidth, idWidth))
  
  def <>(that: RamFetchBus) {
    this.cmd <> that.cmd
    this.rsp <> that.rsp

  }
  override def asMaster() = {
    master(cmd)
    slave(rsp)
  }
}
//case class RamWriteCmd(addressWidth: Int, dataWidth: Int) extends Bundle {
  //val address = UInt(addressWidth bits)
  //val data = Bits(dataWidth bits)
//}
//case class RamWriteRsp() extends Bundle {
  //val success = Bool()
//}
//case class RamWriteBus(addressWidth: Int, dataWidth: Int) extends Bundle {
  //val cmd = master(Stream(RamWriteCmd(addressWidth, dataWidth)))
  //val rsp = slave(Stream(RamWriteRsp()))
//}

class UnifiedRam(addressWidth: Int, dataWidth: Int, idWidth: Int)
    extends Component {

  val io = new Bundle {
    val reads = new RamFetchBus(addressWidth, dataWidth, idWidth)
    reads.setAsSlave()
    val writes = slave(DataBus(addressWidth, dataWidth, idWidth))
  }

  val memSize = 16 KiB
  val addressSize = log2Up(memSize / 8)
  // 8 bytes = 64 bits

  val memory = Mem(Bits(dataWidth bits), wordCount = memSize / 8).simPublic()

  // READING (for instruction fetch)
  io.reads.rsp.payload.data := memory.readSync(
    address = io.reads.cmd.payload.address(addressSize - 1 + 3 downto 3),
    enable = io.reads.cmd.valid
  )
  io.reads.cmd.ready := True
  io.reads.rsp.payload.address := RegNext(io.reads.cmd.payload.address)
  io.reads.rsp.payload.id := RegNext(io.reads.cmd.payload.id)
  io.reads.rsp.valid := RegNext(io.reads.cmd.valid) init False

  // WRITING (for stores via DataBus)
  // Word-aligned address for memory access
  // WRITING (for stores via DataBus) OR READING (for Loads)
  // Word-aligned address for memory access
  val writeWordAddr = io.writes.cmd.payload.address(addressSize - 1 + 3 downto 3)
  
  // Read-modify-write for byte-masked stores
  val currentWord = memory.readAsync(writeWordAddr)
  val newWord = Bits(dataWidth bits)
  
  // Apply byte mask: for each byte, select new data if mask bit set, else keep old
  for (i <- 0 until (dataWidth / 8)) {
    newWord(i * 8 + 7 downto i * 8) := Mux(
      io.writes.cmd.payload.mask(i),
      io.writes.cmd.payload.data(i * 8 + 7 downto i * 8),
      currentWord(i * 8 + 7 downto i * 8)
    )
  }
  
  // Write back the merged word
  memory.write(
    address = writeWordAddr,
    data = newWord,
    enable = io.writes.cmd.valid && io.writes.cmd.payload.write
  )
  
  // DataBus Read Logic (Loads)
  // Uses synchronous read port (1 cycle latency)
  val dBusReadData = memory.readSync(
    address = writeWordAddr,
    enable = io.writes.cmd.valid && !io.writes.cmd.payload.write
  )
  
  // DataBus handshaking and response
  io.writes.cmd.ready := True
  
  // Response valid 1 cycle after command if it was a read
  io.writes.rsp.valid := RegNext(io.writes.cmd.valid && !io.writes.cmd.payload.write) init False
  io.writes.rsp.payload.data := dBusReadData
  io.writes.rsp.payload.id := RegNext(io.writes.cmd.payload.id)

}
