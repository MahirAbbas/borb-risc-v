package borb.formal

import spinal.core._
import spinal.core.sim._
import spinal.lib.misc.pipeline._
import borb.common.Common._
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
    val valid          = up(borb.frontend.Decoder.VALID) 
    val immed          = up(borb.dispatch.SrcPlugin.IMMED)
    val sendtoalu      = up(borb.dispatch.Dispatch.SENDTOALU)
    val result         = up(borb.execute.WriteBack.RESULT).data
    val valid_result   = up(borb.execute.WriteBack.RESULT).valid
    val rdaddr         = up(borb.execute.WriteBack.RESULT).address
    val lane_sel       = up(LANE_SEL)
    val commit         = up(COMMIT)
    val specEpoch      = up(SPEC_EPOCH)  // Replaced MAY_FLUSH with SPEC_EPOCH
    val pc             = up(PC.PC)
    
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
    pipeline.ctrl(3).up(PC.PC).simPublic() // Decode
    pipeline.ctrl(4).up(PC.PC).simPublic() // Dispatch
    pipeline.ctrl(5).up(PC.PC).simPublic() // Src
    pipeline.ctrl(6).up(PC.PC).simPublic() // Ex
    
    dispatcher.hcs.regBusy.simPublic()
    branch.logic.jumpCmd.valid.simPublic()
    branch.logic.pcValue.simPublic()
    branch.logic.target.simPublic()
    
    // Epoch debug signals (replaced MAY_FLUSH)
    pipeline.ctrl(6).up(borb.common.Common.SPEC_EPOCH).simPublic()
    pipeline.ctrl(6).up(borb.frontend.Decoder.MicroCode).simPublic()
    pipeline.ctrl(5).up(borb.common.Common.SPEC_EPOCH).simPublic()
    
    // Fetch debug
    fetch.inflight.simPublic()
    fetch.epoch.simPublic()
    fetch.fifo.io.availability.simPublic()
    fetch.io.readCmd.cmd.valid.simPublic()
    fetch.io.readCmd.rsp.valid.simPublic()
  }
}
