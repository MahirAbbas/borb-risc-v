package borb.backend

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.common.Common._
import borb.common.LaneKey
import borb.dispatch.IntBypassSource
import borb.execute.WriteBack
import borb.frontend.Decoder._

case class IntegerBackend(stage6: CtrlLink, stage7: CtrlLink) extends Area {
  private val exeHasResult = Vec(Bool(), LANES)
  private val wbHasResult = Vec(Bool(), LANES)

  val exeIntBypasses = for(laneId <- 0 until LANES) yield {
    val laneKey = LaneKey(laneId)
    exeHasResult(laneId) := stage6.up.isValid &&
      stage6.up.isFiring &&
      stage6(VALID, laneKey) &&
      stage6(LANE_SEL, laneKey) &&
      stage6.down(WriteBack.RESULT, laneKey).valid

    val source = IntBypassSource()
    source.valid := exeHasResult(laneId)
    source.address := stage6.down(WriteBack.RESULT, laneKey).address
    source.data := stage6.down(WriteBack.RESULT, laneKey).data
    source
  }

  val wbIntBypasses = for(laneId <- 0 until LANES) yield {
    val laneKey = LaneKey(laneId)
    wbHasResult(laneId) := stage7.up.isValid &&
      stage7(VALID, laneKey) &&
      stage7(LANE_SEL, laneKey) &&
      stage7.up(WriteBack.RESULT, laneKey).valid

    val source = IntBypassSource()
    source.valid := wbHasResult(laneId)
    source.address := stage7.up(WriteBack.RESULT, laneKey).address
    source.data := stage7.up(WriteBack.RESULT, laneKey).data
    source
  }

  val exeBypassReady = exeHasResult.asBits.orR
  val exeIntBypass = exeIntBypasses.head
  val wbIntBypass = wbIntBypasses.head
}
