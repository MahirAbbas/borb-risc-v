package borb.execute.fpu

import spinal.core._
import borb.common.MicroCode
import borb.common.MicroCode._
import borb.execute.Lsu
import borb.execute.FunctionalUnit
import borb.frontend.ExecutionUnitEnum

object FpLoadStore {
  val SupportedUops = Seq(
    uopFLH,
    uopFLW,
    uopFLD,
    uopFSH,
    uopFSW,
    uopFSD
  )
}

case class FpLoadStore(
    microCode: MicroCode.C,
    aguFire: Bool,
    fpUsesLdq: Bool,
    fpUsesStq: Bool,
    fpRs2Data: Bits,
    rd: UInt,
    lsu: Lsu,
    writeFp: (UInt, Bits) => Unit,
    clearIntWriteback: () => Unit
) extends FunctionalUnit(ExecutionUnitEnum.AGU) {
  FpLoadStore.SupportedUops.foreach(add)

  when(aguFire && fpUsesStq) {
    when(microCode === uopFSH) {
      lsu.logic.rawStoreData.allowOverride := fpRs2Data(15 downto 0).resize(64)
    } elsewhen(microCode === uopFSW) {
      lsu.logic.rawStoreData.allowOverride := fpRs2Data(31 downto 0).resize(64)
    } elsewhen(microCode === uopFSD) {
      lsu.logic.rawStoreData.allowOverride := fpRs2Data
    }
  }

  when(aguFire && fpUsesLdq && lsu.logic.responseArriving && !lsu.logic.suppress) {
    when(microCode === uopFLH) {
      writeFp(rd, FpuFormatUtils.boxedH(lsu.logic.shiftedLoadData(15 downto 0)))
    } elsewhen(microCode === uopFLW) {
      writeFp(rd, FpuFormatUtils.boxedS(lsu.logic.shiftedLoadData(31 downto 0)))
    } elsewhen(microCode === uopFLD) {
      writeFp(rd, lsu.logic.shiftedLoadData)
    }
    clearIntWriteback()
  }
}
