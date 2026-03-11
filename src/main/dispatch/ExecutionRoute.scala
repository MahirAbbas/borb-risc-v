package borb.dispatch

import borb.common.MicroCode
import borb.common.MicroCode._
import spinal.core._
import spinal.lib.misc.pipeline._

object ExecutionRoute extends AreaObject {
  object EuId extends SpinalEnum {
    val NONE, IntEu, BranchEu, AguEu = newElement()
  }

  object FuKind extends SpinalEnum {
    val NONE, IntCompute, ControlFlow, MemoryAccess = newElement()
  }

  val NEW_ROUTE_VALID = Payload(Bool())
  val NEW_EU_ID = Payload(EuId())
  val NEW_FU_KIND = Payload(FuKind())
}

object ExecutionHazardMeta {
  import ExecutionRoute._

  def isMigratedIntRoute(routeValid: Bool, euId: ExecutionRoute.EuId.C, fuKind: ExecutionRoute.FuKind.C): Bool =
    routeValid && (euId === EuId.IntEu) && (fuKind === FuKind.IntCompute)

  def isMigratedBranchRoute(routeValid: Bool, euId: ExecutionRoute.EuId.C, fuKind: ExecutionRoute.FuKind.C): Bool =
    routeValid && (euId === EuId.BranchEu) && (fuKind === FuKind.ControlFlow)

  def isMigratedMemoryRoute(routeValid: Bool, euId: ExecutionRoute.EuId.C, fuKind: ExecutionRoute.FuKind.C): Bool =
    routeValid && (euId === EuId.AguEu) && (fuKind === FuKind.MemoryAccess)

  def isIntLoad(op: MicroCode.C): Bool = op.mux(
    uopLB -> True, uopLH -> True, uopLW -> True, uopLBU -> True, uopLHU -> True, uopLWU -> True, uopLD -> True,
    default -> False
  )

  def isIntStore(op: MicroCode.C): Bool = op.mux(
    uopSB -> True, uopSH -> True, uopSW -> True, uopSD -> True,
    default -> False
  )

  def isAmo(op: MicroCode.C): Bool = op.mux(
    uopAMOSWAPW -> True, uopAMOSWAPD -> True, uopAMOADDW -> True, uopAMOADDD -> True,
    uopAMOXORW -> True, uopAMOXORD -> True, uopAMOANDW -> True, uopAMOANDD -> True,
    uopAMOORW -> True, uopAMOORD -> True, uopAMOMINW -> True, uopAMOMIND -> True,
    uopAMOMAXW -> True, uopAMOMAXD -> True, uopAMOMINUW -> True, uopAMOMINUD -> True,
    uopAMOMAXUW -> True, uopAMOMAXUD -> True,
    default -> False
  )

  def writesIntRdFromMigratedRoute(
      op: MicroCode.C,
      routeValid: Bool,
      euId: ExecutionRoute.EuId.C,
      fuKind: ExecutionRoute.FuKind.C
  ): Bool = {
    val intComputeWrite = isMigratedIntRoute(routeValid, euId, fuKind)
    val branchWrite = isMigratedBranchRoute(routeValid, euId, fuKind) &&
      ((op === uopJAL) || (op === uopJALR))
    val memoryWrite = isMigratedMemoryRoute(routeValid, euId, fuKind) &&
      (isIntLoad(op) || isAmo(op))
    intComputeWrite || branchWrite || memoryWrite
  }

  def isFlw(insn: Bits): Bool = {
    (insn(6 downto 0) === B"0000111") && (insn(14 downto 12) === B"010")
  }

  def isFsw(insn: Bits): Bool = {
    (insn(6 downto 0) === B"0100111") && (insn(14 downto 12) === B"010")
  }

  def isFcvtFToInt(insn: Bits): Bool = {
    (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"1100000")
  }

  def isFaddsubS(insn: Bits): Bool = {
    (insn(6 downto 0) === B"1010011") &&
    ((insn(31 downto 25) === B"0000000") || (insn(31 downto 25) === B"0000100"))
  }

  def isFmulS(insn: Bits): Bool = {
    (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"0001000")
  }

  def isFdivsqrtS(insn: Bits): Bool = {
    (insn(6 downto 0) === B"1010011") &&
    ((insn(31 downto 25) === B"0001100") || (insn(31 downto 25) === B"0101100"))
  }

  def isFmaS(insn: Bits): Bool = {
    ((insn(6 downto 0) === B"1000011") || (insn(6 downto 0) === B"1000111") ||
      (insn(6 downto 0) === B"1001011") || (insn(6 downto 0) === B"1001111")) &&
    (insn(26 downto 25) === B"00")
  }

  def isFcvtIntToF(insn: Bits): Bool = {
    (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"1101000")
  }

  def isFmvXW(insn: Bits): Bool = {
    (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"1110000") &&
    (insn(24 downto 20) === B"00000") && (insn(14 downto 12) === B"000")
  }

  def isFmvWX(insn: Bits): Bool = {
    (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"1111000") &&
    (insn(24 downto 20) === B"00000") && (insn(14 downto 12) === B"000")
  }

  def isFclassS(insn: Bits): Bool = {
    (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"1110000") &&
    (insn(24 downto 20) === B"00000") && (insn(14 downto 12) === B"001")
  }

  def isFsgnjFamily(insn: Bits): Bool = {
    (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"0010000") &&
    ((insn(14 downto 12) === B"000") || (insn(14 downto 12) === B"001") || (insn(14 downto 12) === B"010"))
  }

  def isFminmaxS(insn: Bits): Bool = {
    (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"0010100") &&
    ((insn(14 downto 12) === B"000") || (insn(14 downto 12) === B"001"))
  }

  def isFcmpS(insn: Bits): Bool = {
    (insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"1010000") &&
    ((insn(14 downto 12) === B"000") || (insn(14 downto 12) === B"001") || (insn(14 downto 12) === B"010"))
  }

  def writesFpRdFromInsn(insn: Bits): Bool = {
    isFlw(insn) || isFcvtIntToF(insn) || isFmvWX(insn) || isFsgnjFamily(insn) ||
    isFminmaxS(insn) || isFaddsubS(insn) || isFmulS(insn) || isFdivsqrtS(insn) || isFmaS(insn)
  }

  def readsFpRs1FromInsn(insn: Bits): Bool = {
    isFcvtFToInt(insn) || isFmvXW(insn) || isFclassS(insn) || isFsgnjFamily(insn) ||
    isFcmpS(insn) || isFminmaxS(insn) || isFaddsubS(insn) || isFmulS(insn) ||
    isFdivsqrtS(insn) || isFmaS(insn)
  }

  def readsFpRs2FromInsn(insn: Bits): Bool = {
    isFsw(insn) || isFsgnjFamily(insn) || isFcmpS(insn) || isFminmaxS(insn) ||
    isFaddsubS(insn) || isFmulS(insn) ||
    ((insn(6 downto 0) === B"1010011") && (insn(31 downto 25) === B"0001100")) ||
    isFmaS(insn)
  }

  def readsFpRs3FromInsn(insn: Bits): Bool = isFmaS(insn)
}

case class FuSpec(
    name: String,
    kind: SpinalEnumElement[ExecutionRoute.FuKind.type],
    supportedOps: Seq[SpinalEnumElement[MicroCode.type]],
    priority: Int,
    writesIntRd: Boolean = true,
    mayUseRs1: Boolean = true,
    mayUseRs2: Boolean = true,
    mayUseImm: Boolean = true
) {
  def supports(op: MicroCode.C): Bool = {
    if (supportedOps.isEmpty) {
      False
    } else {
      supportedOps.map(op === _).reduce(_ || _)
    }
  }
}

case class ExecutionUnitSpec(
    name: String,
    id: SpinalEnumElement[ExecutionRoute.EuId.type],
    fus: Seq[FuSpec]
)

object IntExecutionUnit {
  import ExecutionRoute._

  val intComputeFu = FuSpec(
    name = "IntCompute",
    kind = FuKind.IntCompute,
    supportedOps = Seq(
      uopLUI, uopAUIPC,
      uopADDI, uopSLTI, uopSLTIU, uopXORI, uopORI, uopANDI, uopSLLI, uopSRLI, uopSRAI,
      uopADD, uopSUB, uopSLL, uopSLT, uopSLTU, uopXOR, uopSRL, uopSRA, uopOR, uopAND,
      uopADDIW, uopSLLIW, uopSRLIW, uopSRAIW, uopADDW, uopSUBW, uopSLLW, uopSRLW, uopSRAW,
      uopMUL, uopMULH, uopMULHSU, uopMULHU, uopDIV, uopDIVU, uopREM, uopREMU,
      uopMULW, uopDIVW, uopDIVUW, uopREMW, uopREMUW
    ),
    priority = 0
  )

  val spec = ExecutionUnitSpec(
    name = "IntEu",
    id = EuId.IntEu,
    fus = Seq(intComputeFu)
  )

  def supports(op: MicroCode.C): Bool = intComputeFu.supports(op)
}

case class IntExecutionUnit(op: MicroCode.C, ready: Bool) extends Area {
  import ExecutionRoute._

  val routeValid = Bool()
  val selected = Bool()
  val euId = EuId()
  val fuKind = FuKind()

  routeValid := IntExecutionUnit.supports(op)
  selected := routeValid && ready
  euId := EuId.NONE
  fuKind := FuKind.NONE

  when(routeValid) {
    euId := IntExecutionUnit.spec.id
    fuKind := IntExecutionUnit.intComputeFu.kind
  }
}

object BranchExecutionUnit {
  import ExecutionRoute._

  val controlFlowFu = FuSpec(
    name = "ControlFlow",
    kind = FuKind.ControlFlow,
    supportedOps = Seq(
      uopJAL, uopJALR,
      uopBEQ, uopBNE, uopBLT, uopBGE, uopBLTU, uopBGEU
    ),
    priority = 0,
    writesIntRd = true,
    mayUseRs1 = true,
    mayUseRs2 = true,
    mayUseImm = true
  )

  val spec = ExecutionUnitSpec(
    name = "BranchEu",
    id = EuId.BranchEu,
    fus = Seq(controlFlowFu)
  )

  def supports(op: MicroCode.C): Bool = controlFlowFu.supports(op)
}

case class BranchExecutionUnit(op: MicroCode.C, ready: Bool) extends Area {
  import ExecutionRoute._

  val routeValid = Bool()
  val selected = Bool()
  val euId = EuId()
  val fuKind = FuKind()

  routeValid := BranchExecutionUnit.supports(op)
  selected := routeValid && ready
  euId := EuId.NONE
  fuKind := FuKind.NONE

  when(routeValid) {
    euId := BranchExecutionUnit.spec.id
    fuKind := BranchExecutionUnit.controlFlowFu.kind
  }
}

object MemoryExecutionUnit {
  import ExecutionRoute._

  val memoryAccessFu = FuSpec(
    name = "MemoryAccess",
    kind = FuKind.MemoryAccess,
    supportedOps = Seq(
      uopLB, uopLH, uopLW, uopLBU, uopLHU, uopLWU, uopLD,
      uopSB, uopSH, uopSW, uopSD,
      uopAMOSWAPW, uopAMOSWAPD, uopAMOADDW, uopAMOADDD, uopAMOXORW, uopAMOXORD,
      uopAMOANDW, uopAMOANDD, uopAMOORW, uopAMOORD, uopAMOMINW, uopAMOMIND,
      uopAMOMAXW, uopAMOMAXD, uopAMOMINUW, uopAMOMINUD, uopAMOMAXUW, uopAMOMAXUD
    ),
    priority = 0,
    writesIntRd = true,
    mayUseRs1 = true,
    mayUseRs2 = true,
    mayUseImm = true
  )

  val spec = ExecutionUnitSpec(
    name = "AguEu",
    id = EuId.AguEu,
    fus = Seq(memoryAccessFu)
  )

  def supports(op: MicroCode.C, isFpInsn: Bool): Bool = !isFpInsn && memoryAccessFu.supports(op)
}

case class MemoryExecutionUnit(op: MicroCode.C, isFpInsn: Bool, ready: Bool) extends Area {
  import ExecutionRoute._

  val routeValid = Bool()
  val selected = Bool()
  val euId = EuId()
  val fuKind = FuKind()

  routeValid := MemoryExecutionUnit.supports(op, isFpInsn)
  selected := routeValid && ready
  euId := EuId.NONE
  fuKind := FuKind.NONE

  when(routeValid) {
    euId := MemoryExecutionUnit.spec.id
    fuKind := MemoryExecutionUnit.memoryAccessFu.kind
  }
}
