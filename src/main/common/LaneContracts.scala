package borb.common

import spinal.core._
import spinal.lib.misc.pipeline._

object LaneId {
  val Lane0 = 0
  val Lane1 = 1
  val Count = 2
}

object LaneKey {
  case object Lane0
  case object Lane1

  val all: Seq[Any] = Seq(Lane0, Lane1)

  def apply(laneId: Int): Any = laneId match {
    case LaneId.Lane0 => Lane0
    case LaneId.Lane1 => Lane1
  }
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
