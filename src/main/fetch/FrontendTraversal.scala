package borb.fetch

import spinal.core._
import spinal.lib._

case class FrontendTraversal(config: FrontendConfig) extends Component {
  val io = new Bundle {
    val activeValid = in Bool()
    val traversal = in(TraversalRsp(config))
    val lookupReqs = out Vec(ICacheLookupReq(config), config.lookupLanes)
    val metadata = out(TraversalRsp(config))
  }

  def alignedBlock(address: UInt): UInt = {
    val ret = UInt(config.addressWidth bits)
    ret := address
    if(config.fetchBlockBytes > 1) {
      ret(config.fetchBlockOffsetWidth - 1 downto 0) := 0
    }
    ret
  }

  io.metadata := io.traversal

  val block0Addr = alignedBlock(io.traversal.startPc)
  val block1Addr = block0Addr + U(config.fetchBlockBytes, config.addressWidth bits)

  for(lane <- 0 until config.lookupLanes) {
    val req = ICacheLookupReq(config)
    req.epoch := io.traversal.epoch
    req.kind := ICacheLookupKind.demand
    if(lane == 0) {
      req.valid := io.activeValid && io.traversal.valid
      req.blockAddr := block0Addr
    } else if(lane == 1) {
      // The bundle builder may need the next physical block even when
      // predictor traversal stops in block 0, e.g. a 32-bit instruction
      // straddling the end of the current fetch block.
      req.valid := io.activeValid && io.traversal.valid
      req.blockAddr := block1Addr
    } else {
      req.valid := False
      req.blockAddr := 0
    }
    io.lookupReqs(lane) := req
  }
}
