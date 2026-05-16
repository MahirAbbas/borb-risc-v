package borb

import spinal.core._
import spinal.lib._
import spinal.core.sim._
import spinal.lib.bus.amba4.axi._
import borb.core.{CpuConfig, PerfCountersBundle}

case class SoC() extends Component {
  val io = new Bundle {
    val clk = in port Bool()
    val clkEnable = in port Bool()
    val reset = in port Bool()
    val dbg = out(DebugArea())
    val perf = out(PerfCountersBundle())
  }

  // CPU-side Config (16-bit ID)
  val cpuAxiConfig = Axi4Config(
    addressWidth = 64,
    dataWidth = 64,
    idWidth = 16,
    useId = true,
    useRegion = false,
    useLock = false,
    useQos = false,
    useProt = false,
    useCache = false
  )

  // SoC-side Config (17-bit ID due to Arbiter routing bit)
  val socAxiConfig = cpuAxiConfig.copy(idWidth = 17)

  val socClockDomain = ClockDomain(
    io.clk,
    reset = io.reset,
    clockEnable = io.clkEnable
  )

  val area = new ClockingArea(socClockDomain) {
    val cpu = CPU(CpuConfig.default.copy(debugEnabled = false))
    cpu.io.clk := io.clk
    cpu.io.clkEnable := io.clkEnable
    cpu.io.reset := io.reset
    io.dbg := cpu.io.dbg
    io.perf := cpu.io.perf

    // RAM
    val ram = Axi4SharedOnChipRam(
      dataWidth = 64,
      byteCount = 8 MiB,
      idWidth = 17
    )
    import spinal.core.sim._
    ram.ram.simPublic()

    val iAxi = cpu.io.iAxi
    val dAxi = cpu.io.dAxi

    def routeId(isData: Bool, id: UInt): UInt =
      (isData.asBits ## id.asBits).asUInt

    val writePending = RegInit(False)
    val writeArwSent = RegInit(False)
    val writeWSent = RegInit(False)
    val writeRouteIsData = RegInit(False)
    val writeAddr = Reg(UInt(64 bits)) init (0)
    val writeId = Reg(UInt(16 bits)) init (0)
    val writeLen = Reg(UInt(8 bits)) init (0)
    val writeSize = Reg(UInt(3 bits)) init (0)
    val writeBurst = Reg(Bits(2 bits)) init (0)
    val writeData = Reg(Bits(64 bits)) init (0)
    val writeStrb = Reg(Bits(8 bits)) init (0)
    val writeLast = Reg(Bool()) init (False)

    val iWriteReq = iAxi.arw.valid && iAxi.arw.write && iAxi.w.valid
    val dWriteReq = dAxi.arw.valid && dAxi.arw.write && dAxi.w.valid
    val acceptDataWrite = !writePending && dWriteReq
    val acceptInstrWrite = !writePending && !dWriteReq && iWriteReq

    val dReadReq = dAxi.arw.valid && !dAxi.arw.write
    val iReadReq = iAxi.arw.valid && !iAxi.arw.write
    val issueDataRead = !writePending && dReadReq
    val issueInstrRead = !writePending && !dReadReq && iReadReq

    iAxi.arw.ready := False
    iAxi.w.ready := False
    iAxi.b.valid := ram.io.axi.b.valid && !ram.io.axi.b.id.msb
    iAxi.b.id := ram.io.axi.b.id(15 downto 0)
    iAxi.b.resp := ram.io.axi.b.resp
    iAxi.r.valid := ram.io.axi.r.valid && !ram.io.axi.r.id.msb
    iAxi.r.data := ram.io.axi.r.data
    iAxi.r.id := ram.io.axi.r.id(15 downto 0)
    iAxi.r.resp := ram.io.axi.r.resp
    iAxi.r.last := ram.io.axi.r.last

    dAxi.arw.ready := False
    dAxi.w.ready := False
    dAxi.b.valid := ram.io.axi.b.valid && ram.io.axi.b.id.msb
    dAxi.b.id := ram.io.axi.b.id(15 downto 0)
    dAxi.b.resp := ram.io.axi.b.resp
    dAxi.r.valid := ram.io.axi.r.valid && ram.io.axi.r.id.msb
    dAxi.r.data := ram.io.axi.r.data
    dAxi.r.id := ram.io.axi.r.id(15 downto 0)
    dAxi.r.resp := ram.io.axi.r.resp
    dAxi.r.last := ram.io.axi.r.last

    ram.io.axi.arw.valid := False
    ram.io.axi.arw.addr := 0
    ram.io.axi.arw.id := 0
    ram.io.axi.arw.len := 0
    ram.io.axi.arw.size := 0
    ram.io.axi.arw.burst := 0
    ram.io.axi.arw.write := False

    ram.io.axi.w.valid := False
    ram.io.axi.w.data := 0
    ram.io.axi.w.strb := 0
    ram.io.axi.w.last := False

    ram.io.axi.b.ready := Mux(ram.io.axi.b.id.msb, dAxi.b.ready, iAxi.b.ready)
    ram.io.axi.r.ready := Mux(ram.io.axi.r.id.msb, dAxi.r.ready, iAxi.r.ready)

    when(acceptDataWrite) {
      dAxi.arw.ready := True
      dAxi.w.ready := True
      writePending := True
      writeArwSent := False
      writeWSent := False
      writeRouteIsData := True
      writeAddr := dAxi.arw.addr
      writeId := dAxi.arw.id
      writeLen := dAxi.arw.len
      writeSize := dAxi.arw.size
      writeBurst := dAxi.arw.burst
      writeData := dAxi.w.data
      writeStrb := dAxi.w.strb
      writeLast := dAxi.w.last
    } elsewhen (acceptInstrWrite) {
      iAxi.arw.ready := True
      iAxi.w.ready := True
      writePending := True
      writeArwSent := False
      writeWSent := False
      writeRouteIsData := False
      writeAddr := iAxi.arw.addr
      writeId := iAxi.arw.id
      writeLen := iAxi.arw.len
      writeSize := iAxi.arw.size
      writeBurst := iAxi.arw.burst
      writeData := iAxi.w.data
      writeStrb := iAxi.w.strb
      writeLast := iAxi.w.last
    }

    when(writePending) {
      ram.io.axi.arw.valid := !writeArwSent
      ram.io.axi.arw.addr := writeAddr.resized
      ram.io.axi.arw.id := routeId(writeRouteIsData, writeId)
      ram.io.axi.arw.len := writeLen
      ram.io.axi.arw.size := writeSize
      ram.io.axi.arw.burst := writeBurst
      ram.io.axi.arw.write := True

      ram.io.axi.w.valid := !writeWSent
      ram.io.axi.w.data := writeData
      ram.io.axi.w.strb := writeStrb
      ram.io.axi.w.last := writeLast

      when(ram.io.axi.arw.fire) {
        writeArwSent := True
      }
      when(ram.io.axi.w.fire) {
        writeWSent := True
      }
      when(ram.io.axi.b.fire) {
        writePending := False
        writeArwSent := False
        writeWSent := False
      }
    } otherwise {
      when(issueDataRead) {
        ram.io.axi.arw.valid := True
        ram.io.axi.arw.addr := dAxi.arw.addr.resized
        ram.io.axi.arw.id := routeId(True, dAxi.arw.id)
        ram.io.axi.arw.len := dAxi.arw.len
        ram.io.axi.arw.size := dAxi.arw.size
        ram.io.axi.arw.burst := dAxi.arw.burst
        ram.io.axi.arw.write := False
        dAxi.arw.ready := ram.io.axi.arw.ready
      } elsewhen (issueInstrRead) {
        ram.io.axi.arw.valid := True
        ram.io.axi.arw.addr := iAxi.arw.addr.resized
        ram.io.axi.arw.id := routeId(False, iAxi.arw.id)
        ram.io.axi.arw.len := iAxi.arw.len
        ram.io.axi.arw.size := iAxi.arw.size
        ram.io.axi.arw.burst := iAxi.arw.burst
        ram.io.axi.arw.write := False
        iAxi.arw.ready := ram.io.axi.arw.ready
      }
    }
  }
}

object SoC {
  def main(args: Array[String]) {
    val config = SpinalConfig(
      defaultConfigForClockDomains = ClockDomainConfig(
        resetKind = SYNC,
        resetActiveLevel = HIGH
      )
    )
    config.generateVerilog(SoC())
  }
}
