package borb.backend

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.common.Common._
import borb.dispatch.IntBypassSource
import borb.execute.WriteBack
import borb.frontend.Decoder._

case class IntegerBackend(stage6: CtrlLink, stage7: CtrlLink, currentEpoch: UInt) extends Area {
  private val exeEpochMatches = stage6.up(borb.common.Common.SPEC_EPOCH) === currentEpoch
  private val exeHasResult = stage6.up.isValid &&
    stage6(VALID) &&
    stage6(LANE_SEL) &&
    exeEpochMatches &&
    stage6.down(WriteBack.RESULT).valid

  val exeBypassReady = exeHasResult

  val exeIntBypass = IntBypassSource()
  exeIntBypass.valid := exeHasResult
  exeIntBypass.address := stage6.down(WriteBack.RESULT).address
  exeIntBypass.data := stage6.down(WriteBack.RESULT).data

  val wbIntBypass = IntBypassSource()
  val wbEpochMatches = stage7.up(borb.common.Common.SPEC_EPOCH) === currentEpoch
  wbIntBypass.valid := stage7.up.isValid &&
    stage7(VALID) &&
    stage7(LANE_SEL) &&
    wbEpochMatches &&
    stage7.up(WriteBack.RESULT).valid
  wbIntBypass.address := stage7.up(WriteBack.RESULT).address
  wbIntBypass.data := stage7.up(WriteBack.RESULT).data
}
