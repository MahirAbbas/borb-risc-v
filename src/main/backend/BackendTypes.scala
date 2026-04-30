package borb.backend

import spinal.core._
import borb.core.CpuConfig
import borb.dispatch.{IssuePropertyBundle, RegFileWrite}

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
