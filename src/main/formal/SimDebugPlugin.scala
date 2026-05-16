package borb.formal

import spinal.core._
import spinal.core.sim._
import spinal.lib.misc.pipeline._
import borb.common.Common._
import borb.common.LaneKey
import borb.fetch.PC
import borb.fetch.Fetch
import borb.dispatch.Dispatch
import borb.execute.Branch
import borb.execute.WriteBack
import borb.frontend.Decoder
import borb.dispatch.SrcPlugin

class SimDebugPlugin(
    val wbStage: CtrlLink, 
    val pipeline: StageCtrlPipeline, 
    val dispatcher: Dispatch, 
    val branch: Branch, 
    val fetch: Fetch
) extends Area {
  val logic = new wbStage.Area {
    import borb.common.Common._
import borb.common.LaneKey
    val valid          = up(borb.frontend.Decoder.VALID, LaneKey.Lane0)
    val immed          = up(borb.dispatch.SrcPlugin.IMMED, LaneKey.Lane0)
    val sendtoalu      = up(borb.dispatch.Dispatch.SENDTOALU, LaneKey.Lane0)
    val result         = up(borb.execute.WriteBack.RESULT, LaneKey.Lane0).data
    val valid_result   = up(borb.execute.WriteBack.RESULT, LaneKey.Lane0).valid
    val rdaddr         = up(borb.execute.WriteBack.RESULT, LaneKey.Lane0).address
    val lane_sel       = up(LANE_SEL, LaneKey.Lane0)
    val commit         = up(COMMIT, LaneKey.Lane0)
    val specEpoch      = up(SPEC_EPOCH, LaneKey.Lane0)  // Replaced MAY_FLUSH with SPEC_EPOCH
    val pc             = up(PC.PC, LaneKey.Lane0)
    
    valid.simPublic()
    immed.simPublic()
    sendtoalu.simPublic()
    result.simPublic()
    valid_result.simPublic()
    rdaddr.simPublic()
    lane_sel.simPublic()
    commit.simPublic()
    specEpoch.simPublic()
    pc.simPublic()
    
    // Debug signals
    pipeline.ctrls.foreach { case (id, ctrl) =>
       ctrl.isValid.simPublic()
       ctrl.down.isFiring.simPublic()
    }
    pipeline.ctrl(3).up(PC.PC, LaneKey.Lane0).simPublic() // Decode
    pipeline.ctrl(4).up(PC.PC, LaneKey.Lane0).simPublic() // Dispatch
    pipeline.ctrl(5).up(PC.PC, LaneKey.Lane0).simPublic() // Src
    pipeline.ctrl(6).up(PC.PC, LaneKey.Lane0).simPublic() // Ex
    
    dispatcher.hcs.regBusy.simPublic()
    branch.logic.jumpCmd.valid.simPublic()
    branch.actualTarget.simPublic()
    
    // Epoch debug signals (replaced MAY_FLUSH)
    pipeline.ctrl(6).up(borb.common.Common.SPEC_EPOCH, LaneKey.Lane0).simPublic()
    pipeline.ctrl(6).up(borb.frontend.Decoder.MicroCode, LaneKey.Lane0).simPublic()
    pipeline.ctrl(5).up(borb.common.Common.SPEC_EPOCH, LaneKey.Lane0).simPublic()
    
    // Fetch debug
    fetch.inflight.simPublic()
    fetch.epoch.simPublic()
    fetch.beatValid.simPublic()
    fetch.io.iAxi.arw.valid.simPublic()
    fetch.io.iAxi.r.valid.simPublic()
  }
}
