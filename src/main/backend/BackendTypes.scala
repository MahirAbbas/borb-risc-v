package borb.backend

import spinal.core._
import spinal.lib.misc.pipeline._
import borb.core.CpuConfig
import borb.dispatch.{IssuePropertyBundle, RegFileWrite}
import borb.common.MicroCode
import borb.common.MicroCode._

case class IntResultIntent(epochWidth: Int = 16) extends Bundle {
  val valid = Bool()
  val rd = UInt(5 bits)
  val data = Bits(64 bits)
  val writesRd = Bool()
  val commitEligible = Bool()
  val epoch = UInt(epochWidth bits)
}

case class FpWriteIntent() extends Bundle {
  val valid = Bool()
  val address = UInt(5 bits)
  val data = Bits(64 bits)
}

case class FpFlagsIntent() extends Bundle {
  val valid = Bool()
  val bits = Bits(5 bits)
}

case class TrapRedirectOutcome() extends Bundle {
  val trapFire = Bool()
  val mretFire = Bool()
  val mretTarget = UInt(64 bits)
  val trapCause = Bits(64 bits)
  val trapTval = Bits(64 bits)
}

object BackendPipe extends SpinalEnum {
  val None, Alu0, Alu1, Branch, MulDiv, LoadStore, Falu, Fmac, CsrTrap, Vector = newElement()

  private def oneOf(value: MicroCode.C, options: MicroCode.E*): Bool = {
    if(options.isEmpty) False else options.map(value === _).reduce(_ || _)
  }

  def select(microCode: MicroCode.C, props: IssuePropertyBundle, preferAlu1: Bool = False): BackendPipe.C = {
    val pipe = BackendPipe()
    pipe := BackendPipe.None

    when(props.isLoad || props.isStore) {
      pipe := BackendPipe.LoadStore
    } elsewhen(props.isControlFlow) {
      pipe := BackendPipe.Branch
    } elsewhen(oneOf(
      microCode,
      uopMUL, uopMULH, uopMULHSU, uopMULHU, uopDIV, uopDIVU, uopREM, uopREMU,
      uopMULW, uopDIVW, uopDIVUW, uopREMW, uopREMUW
    )) {
      pipe := BackendPipe.MulDiv
    } elsewhen(oneOf(
      microCode,
      uopCSRRW, uopCSRRS, uopCSRRC, uopCSRRWI, uopCSRRSI, uopCSRRCI,
      uopFENCE, uopFENCE_I, uopECALL, uopEBREAK, uopSRET, uopMRET, uopSFENCEVMA
    )) {
      pipe := BackendPipe.CsrTrap
    } elsewhen(oneOf(
      microCode,
      uopVSETVLI, uopVSETIVLI, uopVSETVL, uopVMVVI, uopVADDVI, uopVADDVV, uopVMVXS,
      uopVLE32, uopVSE32, uopVSLIDEUPVI, uopVSLIDEDOWNVI, uopVRGATHERVI, uopVREDSUMVS,
      uopVFADDVV, uopVFSUBVV, uopVFWCVTFFV, uopVFNCVTFFW,
      uopVANDNVV, uopVBREV8V, uopVREV8V, uopVCLZV, uopVCPOPV, uopVRORVI
    )) {
      pipe := BackendPipe.Vector
    } elsewhen(props.readsFpRs1 || props.readsFpRs2 || props.readsFpRs3 || props.writesFpRd) {
      pipe := Mux(
        oneOf(microCode, uopFMULS, uopFMULD, uopFMADDS, uopFMSUBS, uopFNMSUBS, uopFNMADDS, uopFMADDD, uopFMSUBD, uopFNMSUBD, uopFNMADDD),
        BackendPipe.Fmac,
        BackendPipe.Falu
      )
    } elsewhen(props.fuMask(0)) {
      pipe := Mux(preferAlu1, BackendPipe.Alu1, BackendPipe.Alu0)
    }

    pipe
  }
}

object BackendIssue extends AreaObject {
  val SELECTED_PIPE = Payload(BackendPipe()).setName("BACKEND_SELECTED_PIPE")
}

object RetireQueue {
  // Enough room for the planned fixed-latency FP pipes plus several frontend
  // skid entries while the lane path is still being migrated lane by lane.
  val Depth = 8
  val Width = 2
}

case class PipelineSlot(config: CpuConfig) extends Bundle {
  val valid = Bool()
  val epoch = UInt(16 bits)
  val fetchSeq = UInt(32 bits)
  val olderSeq = UInt(32 bits)
  val bundleSeq = UInt(32 bits)
  val slotIdx = UInt(config.frontendConfig.bundleSlotIdxWidth bits)
  val slotCount = UInt(log2Up(config.frontendConfig.bundleSlots + 1) bits)
  val ftqIdx = UInt(config.frontendConfig.ftqIndexWidth bits)
  val pc = UInt(64 bits)
  val blockPc = UInt(64 bits)
  val byteOffset = UInt(config.frontendConfig.fetchBlockOffsetWidth bits)
  val predictedValid = Bool()
  val predictedTaken = Bool()
  val predictedTarget = UInt(64 bits)

  val decodedInstruction = Bits(32 bits)
  val isCompressed = Bool()
  val legal = borb.frontend.YESNO()
  val microCode = borb.common.MicroCode()
  val rdAddr = Bits(5 bits)
  val rs1Addr = Bits(5 bits)
  val rs2Addr = Bits(5 bits)
  val rs3Addr = Bits(5 bits)
  val issueProps = IssuePropertyBundle()
  val waitForOlderCommit = Bool()
  val selectedPipe = BackendPipe()

  val rs1 = Bits(64 bits)
  val rs2 = Bits(64 bits)
  val immed = Bits(64 bits)
  val sendToAlu = Bool()
  val sendToBranch = Bool()

  val branchTaken = Bool()
  val branchTarget = UInt(64 bits)
  val branchIsBranch = Bool()
  val branchIsJump = Bool()
  val fallthrough = UInt(64 bits)
  val result = RegFileWrite()
  val trap = TrapRedirectOutcome()
  val commit = Bool()
}

case class RetirePacket(config: CpuConfig) extends Bundle {
  val valid = Bool()
  val slot = PipelineSlot(config)
  val intWrite = RegFileWrite()
  val fpWrite = FpWriteIntent()
  val fpFlags = FpFlagsIntent()
  val storeCommit = Bool()
  val trap = TrapRedirectOutcome()
}
