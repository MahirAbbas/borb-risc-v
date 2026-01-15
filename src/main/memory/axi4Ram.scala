package spinal.lib.bus.amba4.axi

import spinal.core._
import spinal.core.fiber._
import spinal.lib._

object Axi4OnChipRam {
  def getAxiConfig(dataWidth: Int, byteCount: BigInt, idWidth: Int) =
    Axi4Config(
      addressWidth = log2Up(byteCount),
      dataWidth = dataWidth,
      idWidth = idWidth,
      useLock = false,
      useRegion = false,
      useCache = false,
      useProt = false,
      useQos = false
    )

  def main(args: Array[String]) {
    SpinalVhdl(new Axi4OnChipRam(32, 1024, 4).setDefinitionName("TopLevel"))
  }
}

case class Axi4OnChipRam(dataWidth: Int, byteCount: BigInt, idWidth: Int)
    extends Component {
  val axiConfig = Axi4OnChipRam.getAxiConfig(dataWidth, byteCount, idWidth)

  val io = new Bundle {
    val axi = slave(Axi4(axiConfig))
  }

  val wordCount = byteCount / axiConfig.bytePerWord
  val ram = Mem(axiConfig.dataType, wordCount.toInt)
  val wordRange =
    log2Up(wordCount) + log2Up(axiConfig.bytePerWord) - 1 downto log2Up(
      axiConfig.bytePerWord
    )

  // Write Path
  val aw = io.axi.aw.unburstify
  val w = io.axi.w

  val joinWrite = StreamJoin(aw, w)

  ram.write(
    address = joinWrite.payload._1.addr(wordRange).resized,
    data = joinWrite.payload._2.data,
    enable = joinWrite.fire,
    mask = joinWrite.payload._2.strb
  )

  // B Response
  val bStream =
    joinWrite.throwWhen(!joinWrite.payload._1.last).map { joinedPayload =>
      val awPayload = joinedPayload._1
      val b = Axi4B(axiConfig)
      b.id := awPayload.id
      b.setOKAY()
      if (axiConfig.useBUser) b.user := awPayload.user
      b
    }
  io.axi.b << bStream.stage()

  // Read Path
  val ar = io.axi.ar.unburstify
  val readData = ram.readSync(
    address = ar.addr(wordRange).resized,
    enable = ar.fire
  )

  val readStage = ar.stage()

  // Hand-rolled flow control to hold data if R is not ready
  val dataValid = RegNext(ar.fire).init(False)
  val savedReadData = RegNextWhen(readData, dataValid)
  val outData = CombInit(savedReadData)
  when(dataValid) { outData := readData }

  io.axi.r.valid := readStage.valid
  io.axi.r.data := outData
  io.axi.r.id := readStage.id
  io.axi.r.last := readStage.last
  io.axi.r.setOKAY()
  if (axiConfig.useRUser) io.axi.r.user := readStage.user

  readStage.ready := io.axi.r.ready
}

object Axi4OnChipRamPort {
  def apply(config: Axi4Config, ram: Mem[Bits]) = {
    val port = new Axi4OnChipRamPort(config)
    port.attach(ram)
    port
  }
}

case class Axi4OnChipRamPort(config: Axi4Config) extends ImplicitArea[Axi4] {
  val axi = Axi4(config)
  override def implicitValue: Axi4 = this.axi

  val ram = Handle[Mem[Bits]]

  val logic = Handle {
    new Area {
      val wordRange = config.wordRange

      // Write Path
      val aw = axi.aw.unburstify
      val w = axi.w
      val joinWrite = StreamJoin(aw, w)
      val address =
        joinWrite.payload._1.addr(wordRange).resize(ram.addressWidth bits)

      ram.write(
        address = address,
        data = joinWrite.payload._2.data,
        enable = joinWrite.fire,
        mask = joinWrite.payload._2.strb
      )

      val bStream =
        joinWrite.throwWhen(!joinWrite.payload._1.last).map { joinedPayload =>
          val awPayload = joinedPayload._1
          val b = Axi4B(config)
          b.id := awPayload.id
          b.setOKAY()
          if (config.useBUser) b.user := awPayload.user
          b
        }
      val bStage = bStream.queue(1)
      axi.b << bStage

      // Read Path
      val ar = axi.ar.unburstify
      val readData = ram.readSync(
        address = ar.addr(wordRange).resize(ram.addressWidth bits),
        enable = ar.fire
      )

      val readStage = ar.stage()
      val dataValid = RegNext(ar.fire).init(False)
      val savedReadData = RegNextWhen(readData, dataValid)
      val outData = CombInit(savedReadData)
      when(dataValid) {
        outData := readData
      }

      axi.r.valid := readStage.valid
      axi.r.data := outData
      axi.r.id := readStage.id
      axi.r.last := readStage.last
      axi.r.setOKAY()
      if (axi.config.useRUser) axi.r.user := readStage.user
      readStage.ready := axi.r.ready

      axi.ar.ready.noBackendCombMerge // Verilator perf
    }
  }

  def attach(ram: Mem[Bits]) = {
    this.ram.load(ram)
  }
}

object Axi4OnChipRamMultiPort {
  def apply(portCount: Int, dataWidth: Int, byteCount: BigInt, idWidth: Int) = {
    val axiConfig = Axi4OnChipRam.getAxiConfig(dataWidth, byteCount, idWidth)
    val wordCount = byteCount / axiConfig.bytePerWord
    new Axi4OnChipRamMultiPort(axiConfig, wordCount, portCount)
  }
  def apply(config: Axi4Config, wordCount: BigInt, portCount: Int) =
    new Axi4OnChipRamMultiPort(config, wordCount, portCount)
}

class Axi4OnChipRamMultiPort(
    config: Axi4Config,
    wordCount: BigInt,
    portCount: Int
) extends Component {
  val io = new Bundle {
    val axis = Vec(slave(Axi4(config)), portCount)
  }

  if (
    config.useLock || config.useRegion || config.useCache || config.useProt || config.useQos
  )
    SpinalWarning(
      "Axi4OnChipRamMultiPort might not support Axi4 Lock, Region, Cahce, Prot and Qos featrues!"
    )

  val ram = Mem(config.dataType, wordCount.toInt)
  val ports = Array.fill(portCount)(Axi4OnChipRamPort(config, ram))
  (io.axis, ports).zipped foreach ((m, s) => m >> s)
}
