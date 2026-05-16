package borb.execute.fpu

import spinal.core._
import borb.common.MicroCode
import borb.common.MicroCode._
import borb.execute.FunctionalUnit
import borb.frontend.ExecutionUnitEnum

object FpArithmetic {
  val SupportedUops = Seq(
    uopFADDS,
    uopFSUBS,
    uopFADDD,
    uopFSUBD,
    uopFMULS,
    uopFMULD,
    uopFDIVS,
    uopFDIVD,
    uopFSQRTS,
    uopFSQRTD,
    uopFMADDS,
    uopFMSUBS,
    uopFNMSUBS,
    uopFNMADDS,
    uopFMADDD,
    uopFMSUBD,
    uopFNMSUBD,
    uopFNMADDD
  )
}

case class FpArithmetic(
    microCode: MicroCode.C,
    rd: UInt,
    fcvtRm: Bits,
    isHalfFmt: Bool,
    fpRs1Data: Bits,
    fpRs2Data: Bits,
    fpRs3Data: Bits,
    fpRs1SValue: Bits,
    fpRs2SValue: Bits,
    fpRs3SValue: Bits,
    fpRs1HValue: Bits,
    fpRs2HValue: Bits,
    fpRs3HValue: Bits,
    faddsubSFire: Bool,
    faddsubDFire: Bool,
    fmulSFire: Bool,
    fmulDFire: Bool,
    fdivSFire: Bool,
    fdivDFire: Bool,
    fsqrtSFire: Bool,
    fsqrtDFire: Bool,
    fmaSFire: Bool,
    fmaDFire: Bool,
    writeFp: (UInt, Bits) => Unit,
    writeFpSOrH: (UInt, Bits, Bits) => Unit,
    requestFlags: Bits => Unit
) extends FunctionalUnit(ExecutionUnitEnum.FPU) {
  FpArithmetic.SupportedUops.foreach(add)

  private def s1: Bits = Mux(isHalfFmt, FpuFormatUtils.halfToSingle(fpRs1HValue), fpRs1SValue)
  private def s2: Bits = Mux(isHalfFmt, FpuFormatUtils.halfToSingle(fpRs2HValue), fpRs2SValue)
  private def s3: Bits = Mux(isHalfFmt, FpuFormatUtils.halfToSingle(fpRs3HValue), fpRs3SValue)

  when(faddsubSFire) {
    val addSub = FpuAddSub.addSubS(s1, s2, fcvtRm, microCode === uopFSUBS)
    writeFpSOrH(rd, addSub.data, addSub.flags)
  }
  when(faddsubDFire) {
    val addSub = FpuAddSub.addSubD(fpRs1Data, fpRs2Data, fcvtRm, microCode === uopFSUBD)
    writeFp(rd, addSub.data)
    requestFlags(addSub.flags)
  }

  when(fmulSFire) {
    val mul = FpuMul.mulS(s1, s2, fcvtRm)
    writeFpSOrH(rd, mul.data, mul.flags)
  }
  when(fmulDFire) {
    val mul = FpuMul.mulD(fpRs1Data, fpRs2Data, fcvtRm)
    writeFp(rd, mul.data)
    requestFlags(mul.flags)
  }

  when(fdivSFire) {
    val div = FpuDivSqrt.divS(s1, s2, fcvtRm)
    writeFpSOrH(rd, div.data, div.flags)
  }
  when(fdivDFire) {
    val div = FpuDivSqrt.divD(fpRs1Data, fpRs2Data, fcvtRm)
    writeFp(rd, div.data)
    requestFlags(div.flags)
  }

  when(fsqrtSFire) {
    val sqrt = FpuDivSqrt.sqrtS(s1, fcvtRm)
    writeFpSOrH(rd, sqrt.data, sqrt.flags)
  }
  when(fsqrtDFire) {
    val sqrt = FpuDivSqrt.sqrtD(fpRs1Data, fcvtRm)
    writeFp(rd, sqrt.data)
    requestFlags(sqrt.flags)
  }

  when(fmaSFire) {
    val fma = FpuFma.fmaS(
      s1,
      s2,
      s3,
      fcvtRm,
      microCode === uopFMSUBS,
      microCode === uopFNMSUBS,
      microCode === uopFNMADDS
    )
    writeFpSOrH(rd, fma.data, fma.flags)
  }
  when(fmaDFire) {
    val fma = FpuFma.fmaD(
      fpRs1Data,
      fpRs2Data,
      fpRs3Data,
      fcvtRm,
      microCode === uopFMSUBD,
      microCode === uopFNMSUBD,
      microCode === uopFNMADDD
    )
    writeFp(rd, fma.data)
    requestFlags(fma.flags)
  }
}
