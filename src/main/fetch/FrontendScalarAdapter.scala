package borb.fetch

import spinal.core._
import spinal.lib._

case class FrontendScalarAdapter(config: FrontendConfig) extends Component {
  val io = new Bundle {
    val flush = in Bool()
    val currentEpoch = in UInt(config.epochWidth bits)
    val bundles = slave(Stream(FetchBundle(config)))
    val scalar = master(Stream(ScalarFetchEntry(config)))
    val consume = in Bool()
    val active = out Bool()
  }

  val activeValid = RegInit(False)
  val activeBundle = Reg(FetchBundle(config)) init(FetchBundle(config).getZero)
  val activeSlot = Reg(UInt(config.bundleSlotIdxWidth bits)) init(0)
  val nextSlotCountCmp = UInt(log2Up(config.bundleSlots + 1) bits)
  nextSlotCountCmp := activeSlot.resize(nextSlotCountCmp.getWidth) + 1

  io.bundles.ready := !activeValid

  val activeBundleStale = activeValid && (activeBundle.epoch =/= io.currentEpoch)

  when(io.flush || activeBundleStale) {
    activeValid := False
    activeSlot := 0
  } otherwise {
    when(io.bundles.fire) {
      activeBundle := io.bundles.payload
      activeValid := io.bundles.payload.epoch === io.currentEpoch
      activeSlot := 0
    } elsewhen(io.consume) {
      when(nextSlotCountCmp < activeBundle.slotCount) {
        activeSlot := activeSlot + 1
      } otherwise {
        activeValid := False
        activeSlot := 0
      }
    }
  }

  val slot = activeBundle.slots(activeSlot)

  io.scalar.valid := activeValid && slot.valid && (activeBundle.epoch === io.currentEpoch)
  io.scalar.payload.valid := activeValid && slot.valid && (activeBundle.epoch === io.currentEpoch)
  io.scalar.payload.scalarSeq := activeBundle.bundleMeta.scalarSeqBase + activeSlot.resized
  io.scalar.payload.bundleSeq := activeBundle.bundleSeq
  io.scalar.payload.epoch := activeBundle.epoch
  io.scalar.payload.pc := slot.pc
  io.scalar.payload.insn := slot.insn
  io.scalar.payload.isCompressed := slot.isCompressed
  io.scalar.payload.nextPc := slot.nextPc
  io.scalar.payload.ftqIndex := slot.ftqIndex
  io.scalar.payload.slotIdx := slot.slotIdx
  io.scalar.payload.blockPc := slot.blockPc
  io.scalar.payload.byteOffsetInBlock := slot.byteOffsetInBlock
  io.scalar.payload.predictedValid := slot.predictionMeta.valid
  io.scalar.payload.predictedTaken := slot.predictionMeta.predictedTaken
  io.scalar.payload.predictedTarget := slot.predictionMeta.predictedTarget
  io.scalar.payload.illegal := slot.illegal
  io.scalar.payload.fetchFault := slot.fetchFault
  io.active := activeValid && (activeBundle.epoch === io.currentEpoch)
}
