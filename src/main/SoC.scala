package borb

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba4.axi._
import borb.memory._

case class SoC() extends Component {
  val io = new Bundle {
    val clk = in port Bool()
    val clkEnable = in port Bool()
    val reset = in port Bool()
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
    val cpu = CPU()
    cpu.io.clk := io.clk
    cpu.io.clkEnable := io.clkEnable
    cpu.io.reset := io.reset

    // Shims: Bridge CPU Generic Buses to AXI (CPU Config)
    val fetchShim = new RamFetchBusToAxi4Shared(cpuAxiConfig)
    fetchShim.io.fetch <> cpu.io.iBus

    val lsuShim = new DataBusToAxi4Shared(cpuAxiConfig)
    lsuShim.io.dBus <> cpu.io.dBus

    // Arbiter (2 Inputs -> 1 Output)
    val arbiter = new Axi4SharedArbiter(
      outputConfig = socAxiConfig,
      readInputsCount = 0,
      writeInputsCount = 0,
      sharedInputsCount = 2,
      routeBufferSize = 4
    )
    
    // Connect Shims to Arbiter
    arbiter.io.sharedInputs(0) <> fetchShim.io.axi
    arbiter.io.sharedInputs(1) <> lsuShim.io.axi

    // RAM
    val ram = Axi4SharedOnChipRam(
      dataWidth = 64,
      byteCount = 16 KiB,
      idWidth = 17
    )
    
    // Connect Arbiter to RAM with Address Resizing (64 -> 14 bits)
    ram.io.axi.arw.valid   := arbiter.io.output.arw.valid
    ram.io.axi.arw.addr    := arbiter.io.output.arw.addr.resized
    ram.io.axi.arw.id      := arbiter.io.output.arw.id
    ram.io.axi.arw.len     := arbiter.io.output.arw.len
    ram.io.axi.arw.size    := arbiter.io.output.arw.size
    ram.io.axi.arw.burst   := arbiter.io.output.arw.burst
    // Optional signals disabled in config (NPE if assigned)
    // ram.io.axi.arw.lock    := ...
    // ram.io.axi.arw.cache   := ...
    // ram.io.axi.arw.prot    := ...
    // ram.io.axi.arw.qos     := ...
    // ram.io.axi.arw.region  := ...
    ram.io.axi.arw.write   := arbiter.io.output.arw.write
    arbiter.io.output.arw.ready := ram.io.axi.arw.ready

    ram.io.axi.w.valid     := arbiter.io.output.w.valid
    ram.io.axi.w.data      := arbiter.io.output.w.data
    ram.io.axi.w.strb      := arbiter.io.output.w.strb
    ram.io.axi.w.last      := arbiter.io.output.w.last
    arbiter.io.output.w.ready := ram.io.axi.w.ready
    
    arbiter.io.output.b.valid     := ram.io.axi.b.valid
    arbiter.io.output.b.id        := ram.io.axi.b.id
    arbiter.io.output.b.resp      := ram.io.axi.b.resp
    ram.io.axi.b.ready           := arbiter.io.output.b.ready

    arbiter.io.output.r.valid     := ram.io.axi.r.valid
    arbiter.io.output.r.data      := ram.io.axi.r.data
    arbiter.io.output.r.id        := ram.io.axi.r.id
    arbiter.io.output.r.resp      := ram.io.axi.r.resp
    arbiter.io.output.r.last      := ram.io.axi.r.last
    ram.io.axi.r.ready           := arbiter.io.output.r.ready
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
