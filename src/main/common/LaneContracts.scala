package borb.common

import spinal.core._
import spinal.lib.misc.pipeline._

object LaneId {
  val Lane0 = 0
  val Lane1 = 1
  val Count = 2
}

case class LaneKeyedPayload[T <: Data](baseName: String, dataType: HardType[T]) {
  val lanes = Seq.tabulate(LaneId.Count) { laneId =>
    Payload(dataType()).setName(s"${baseName}_L$laneId")
  }

  def apply(laneId: Int): Payload[T] = lanes(laneId)

  def lane0: Payload[T] = lanes(LaneId.Lane0)
  def lane1: Payload[T] = lanes(LaneId.Lane1)
}

object LaneContracts {
  val slotCountWidth = 2

  def laneMaskFromSlotCount(slotCount: UInt): Bits = {
    val mask = Bits(2 bits)
    mask := B"00"
    switch(slotCount) {
      is(U(1, slotCountWidth bits)) {
        mask := B"01"
      }
      is(U(2, slotCountWidth bits)) {
        mask := B"11"
      }
    }
    mask
  }

  def laneMaskFromSlotCount(slotCount: Bits): Bits = laneMaskFromSlotCount(slotCount.asUInt)

  def peerPresent(laneMask: Bits): Bool = laneMask === B"11"

  def isYounger(bundleSeqA: UInt, slotA: UInt, bundleSeqB: UInt, slotB: UInt): Bool = {
    (bundleSeqA > bundleSeqB) || ((bundleSeqA === bundleSeqB) && (slotA > slotB))
  }

  def sameSlot(bundleSeqA: UInt, slotA: UInt, bundleSeqB: UInt, slotB: UInt): Bool = {
    (bundleSeqA === bundleSeqB) && (slotA === slotB)
  }
}
