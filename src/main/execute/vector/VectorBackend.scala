package borb.execute.vector

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.backend.{BackendIssue, BackendPipe}
import borb.common.Common._
import borb.common.LaneKey
import borb.common.MicroCode._
import borb.core.CpuConfig
import borb.dispatch.SrcPlugin
import borb.execute.{DataBus, FunctionalUnit, Lsu, WriteBack}
import borb.fetch.PC
import borb.frontend.Decoder
import borb.frontend.ExecutionUnitEnum
import borb.vector.{DormantSharedVectorEngine, VectorDecode, VectorExceptionCause, VectorHartContext, VectorMemOp}

object VectorBackend {
  val SupportedUops = Seq(
    uopVSETVLI,
    uopVSETIVLI,
    uopVSETVL,
    uopVADDVV,
    uopVADDVI,
    uopVMVVI,
    uopVMVXS,
    uopVLE32,
    uopVSE32,
    uopVSLIDEUPVI,
    uopVSLIDEDOWNVI,
    uopVRGATHERVI,
    uopVREDSUMVS,
    uopVFADDVV,
    uopVFSUBVV,
    uopVFWCVTFFV,
    uopVFNCVTFFW,
    uopVANDNVV,
    uopVBREV8V,
    uopVREV8V,
    uopVCLZV,
    uopVCPOPV,
    uopVRORVI
  )
}

case class VectorBackend(
    execStage: CtrlLink,
    lsu: Lsu,
    lsuBus: DataBus,
    currentEpoch: UInt,
    config: CpuConfig,
    vectorContext: VectorHartContext,
    resetPcValue: BigInt
) extends FunctionalUnit(ExecutionUnitEnum.ALU) {
  VectorBackend.SupportedUops.foreach(add)

  val vectorEngine = DormantSharedVectorEngine(config.vectorConfig.copy(xlen = config.xlen, addressWidth = config.physicalAddrWidth))

  val memoryComplete = Bool()
  val memoryTrapValid = Bool()
  val memoryTrapIsStore = Bool()
  val memoryTrapTval = Bits(config.xlen bits)
  val memoryTrapElement = UInt(config.xlen bits)

  val vectorExecInsn = execStage.up(Decoder.DECODED_INSTRUCTION, LaneKey.Lane0)
  val vectorExecMicroCode = execStage.up(Decoder.MicroCode, LaneKey.Lane0)
  val vectorDecodedOp = execStage.up(Decoder.IS_VEC, LaneKey.Lane0) === borb.frontend.YESNO.Y
  val vectorExecPacket = execStage.up.isValid &&
    (execStage.up(SPEC_EPOCH, LaneKey.Lane0) === currentEpoch) &&
    (execStage.up(PC.PC, LaneKey.Lane0) >= resetPcValue) &&
    execStage.up(Decoder.VALID, LaneKey.Lane0) &&
    execStage.up(LANE_SEL, LaneKey.Lane0) &&
    (execStage.up(BackendIssue.SELECTED_PIPE, LaneKey.Lane0) === BackendPipe.Vector) &&
    vectorDecodedOp
  val vectorMemoryExec = (vectorExecMicroCode === uopVLE32) || (vectorExecMicroCode === uopVSE32)
  val vectorMemoryActive = RegInit(False)
  when(vectorExecPacket && vectorMemoryExec && !vectorMemoryActive) {
    vectorMemoryActive := True
  }
  when(vectorMemoryActive && vectorEngine.io.memoryComplete) {
    vectorMemoryActive := False
  }
  val vectorMemoryTrap = vectorMemoryActive && vectorEngine.io.memoryComplete && vectorEngine.io.memoryException.valid
  memoryComplete := vectorMemoryActive && vectorEngine.io.memoryComplete
  memoryTrapValid := vectorMemoryTrap
  memoryTrapIsStore := vectorExecMicroCode === uopVSE32
  memoryTrapTval := vectorEngine.io.memoryException.tval
  memoryTrapElement := vectorEngine.io.memoryFaultElement.resized
  vectorEngine.io.command.valid := vectorExecPacket || vectorMemoryActive
  vectorEngine.io.command.payload.hartId := 0
  vectorEngine.io.command.payload.pc := execStage.up(PC.PC, LaneKey.Lane0)
  vectorEngine.io.command.payload.instruction := vectorExecInsn
  vectorEngine.io.command.payload.opClass := VectorDecode.classify(vectorExecInsn)
  vectorEngine.io.command.payload.rd := vectorExecInsn(11 downto 7).asUInt
  vectorEngine.io.command.payload.rs1 := vectorExecInsn(19 downto 15).asUInt
  vectorEngine.io.command.payload.rs2 := vectorExecInsn(24 downto 20).asUInt
  vectorEngine.io.command.payload.rs3 := vectorExecInsn(31 downto 27).asUInt
  vectorEngine.io.command.payload.intRs1 := execStage.up(SrcPlugin.RS1, LaneKey.Lane0)
  vectorEngine.io.command.payload.intRs2 := execStage.up(SrcPlugin.RS2, LaneKey.Lane0)
  vectorEngine.io.command.payload.funct3 := vectorExecInsn(14 downto 12)
  vectorEngine.io.command.payload.funct6 := vectorExecInsn(31 downto 26)
  vectorEngine.io.command.payload.vm := vectorExecInsn(25)
  vectorEngine.io.command.payload.context := vectorContext
  vectorEngine.io.response.ready := True
  execStage.haltWhen(vectorExecPacket && vectorMemoryExec && (!vectorMemoryActive || !vectorEngine.io.memoryComplete))
  when(vectorEngine.io.response.valid && vectorEngine.io.response.payload.writesInt) {
    execStage.down(WriteBack.RESULT, LaneKey.Lane0).address.allowOverride := vectorEngine.io.response.payload.intRd
    execStage.down(WriteBack.RESULT, LaneKey.Lane0).data.allowOverride := Mux(
      vectorEngine.io.response.payload.intRd === 0,
      B(0, config.xlen bits),
      vectorEngine.io.response.payload.intData
    )
    execStage.down(WriteBack.RESULT, LaneKey.Lane0).valid.allowOverride := True
  }

  val vectorMemId = U(65535, 16 bits)
  val vectorMemSelected = vectorEngine.io.memReq.valid
  lsuBus.cmd.valid := lsu.io.dBus.cmd.valid || vectorEngine.io.memReq.valid
  lsuBus.cmd.payload.address := lsu.io.dBus.cmd.payload.address
  lsuBus.cmd.payload.data := lsu.io.dBus.cmd.payload.data
  lsuBus.cmd.payload.mask := lsu.io.dBus.cmd.payload.mask
  lsuBus.cmd.payload.id := lsu.io.dBus.cmd.payload.id
  lsuBus.cmd.payload.write := lsu.io.dBus.cmd.payload.write
  when(vectorMemSelected) {
    lsuBus.cmd.payload.address := vectorEngine.io.memReq.payload.address
    lsuBus.cmd.payload.data := vectorEngine.io.memReq.payload.data(63 downto 0)
    lsuBus.cmd.payload.mask := vectorEngine.io.memReq.payload.mask(7 downto 0)
    lsuBus.cmd.payload.id := vectorMemId
    lsuBus.cmd.payload.write := vectorEngine.io.memReq.payload.op === VectorMemOp.Store
  }
  lsu.io.dBus.cmd.ready := lsuBus.cmd.ready && !vectorMemSelected
  vectorEngine.io.memReq.ready := lsuBus.cmd.ready

  val vectorMemResponse = lsuBus.rsp.valid && (lsuBus.rsp.payload.id === vectorMemId)
  lsu.io.dBus.rsp.valid := lsuBus.rsp.valid && !vectorMemResponse
  lsu.io.dBus.rsp.payload.data := lsuBus.rsp.payload.data
  lsu.io.dBus.rsp.payload.id := lsuBus.rsp.payload.id
  vectorEngine.io.memResp.valid := vectorMemResponse
  vectorEngine.io.memResp.payload.hartId := 0
  vectorEngine.io.memResp.payload.data := lsuBus.rsp.payload.data.resize(config.vectorConfig.vlen)
  vectorEngine.io.memResp.payload.exception.valid := False
  vectorEngine.io.memResp.payload.exception.cause := VectorExceptionCause.None
  vectorEngine.io.memResp.payload.exception.tval := 0
}
