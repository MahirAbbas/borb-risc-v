package borb.fetch

import spinal.core._
import spinal.lib._

case class FrontendControl(config: FrontendConfig) extends Component {
  val io = new Bundle {
    val flush = in Bool()
    val redirectValid = in Bool()
    val redirectPc = in UInt(config.addressWidth bits)
    val redirectEpoch = in UInt(config.epochWidth bits)
    val commandValid = in Bool()
    val commandPc = in UInt(config.addressWidth bits)
    val commandEpoch = in UInt(config.epochWidth bits)
    val bundleAccepted = in Bool()
    val bundleNextPc = in UInt(config.addressWidth bits)
    val ownsSequencing = out Bool()
    val activeValid = out Bool()
    val startPc = out UInt(config.addressWidth bits)
    val epoch = out UInt(config.epochWidth bits)
  }

  val activeValid = RegInit(False)
  val activePc = Reg(UInt(config.addressWidth bits)) init(0)
  val activeEpoch = Reg(UInt(config.epochWidth bits)) init(0)
  val bootstrapped = RegInit(False)

  when(io.redirectValid) {
    activeValid := True
    activePc := io.redirectPc
    activeEpoch := io.redirectEpoch
    bootstrapped := True
  } otherwise {
    when(io.bundleAccepted) {
      activeValid := True
      activePc := io.bundleNextPc
    } elsewhen(!bootstrapped && io.commandValid) {
      activeValid := True
      activePc := io.commandPc
      activeEpoch := io.commandEpoch
      bootstrapped := True
    } elsewhen(io.flush) {
      activeValid := False
    }
  }

  io.ownsSequencing := bootstrapped
  io.activeValid := activeValid && !io.redirectValid && !io.flush
  io.startPc := activePc
  io.epoch := activeEpoch
}
