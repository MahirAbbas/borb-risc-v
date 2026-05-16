package borb.fetch

import spinal.core._
import spinal.lib._

case class FrontendControl(config: FrontendConfig) extends Component {
  val io = new Bundle {
    val flush = in Bool()
    val recover = slave(Flow(FrontendRecoverUpdate(config)))
    val commandValid = in Bool()
    val commandPc = in UInt(config.addressWidth bits)
    val commandEpoch = in UInt(config.epochWidth bits)
    val bundleAccepted = in Bool()
    val bundleNextPc = in UInt(config.addressWidth bits)
    val ownsSequencing = out Bool()
    val active = master(Flow(FrontendPcState(config.addressWidth, config.epochWidth)))
  }

  val activeValid = RegInit(False)
  val activePc = Reg(UInt(config.addressWidth bits)) init(0)
  val activeEpoch = Reg(UInt(config.epochWidth bits)) init(0)
  val bootstrapped = RegInit(False)

  when(io.recover.valid) {
    activeValid := True
    activePc := io.recover.payload.redirectTarget
    activeEpoch := io.recover.payload.epoch
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
  io.active.valid := activeValid && !io.recover.valid && !io.flush
  io.active.payload.pc := activePc
  io.active.payload.epoch := activeEpoch
}
