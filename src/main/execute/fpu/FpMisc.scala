package borb.execute.fpu

import spinal.core._
import borb.common.MicroCode
import borb.common.MicroCode._
import borb.execute.FunctionalUnit
import borb.frontend.ExecutionUnitEnum

object FpMisc {
  val SupportedUops = Seq(
    uopFMVXW,
    uopFMVWX,
    uopFMVXD,
    uopFMVDX,
    uopFCLASSS,
    uopFCLASSD,
    uopFLIS,
    uopFLID,
    uopFSGNJS,
    uopFSGNJNS,
    uopFSGNJXS,
    uopFSGNJD,
    uopFSGNJND,
    uopFSGNJXD,
    uopFEQS,
    uopFLTS,
    uopFLES,
    uopFLEQS,
    uopFLTQS,
    uopFEQD,
    uopFLTD,
    uopFLED,
    uopFLEQD,
    uopFLTQD,
    uopFMINS,
    uopFMAXS,
    uopFMINMS,
    uopFMAXMS,
    uopFMIND,
    uopFMAXD,
    uopFMINMD,
    uopFMAXMD,
    uopFROUNDS,
    uopFROUNDNXS,
    uopFROUNDD,
    uopFROUNDNXD
  )
}

case class FpMisc(
    microCode: MicroCode.C,
    insn: Bits,
    rd: UInt,
    srcInt: Bits,
    fcvtRm: Bits,
    isHalfFmt: Bool,
    isFmvXHInsn: Bool,
    isFmvHXInsn: Bool,
    fpRs1Data: Bits,
    fpRs2Data: Bits,
    fpRs1SValue: Bits,
    fpRs2SValue: Bits,
    fpRs1HValue: Bits,
    fpRs2HValue: Bits,
    fmvXWFire: Bool,
    fmvWXFire: Bool,
    fmvXDFire: Bool,
    fmvDXFire: Bool,
    fclassSFire: Bool,
    fclassDFire: Bool,
    fliSFire: Bool,
    fliDFire: Bool,
    fsgnjSFire: Bool,
    fsgnjDFire: Bool,
    fcmpSFire: Bool,
    fcmpDFire: Bool,
    fminmaxSFire: Bool,
    fminmaxDFire: Bool,
    froundSFire: Bool,
    froundDFire: Bool,
    writeFp: (UInt, Bits) => Unit,
    writeFpSOrH: (UInt, Bits, Bits) => Unit,
    requestIntResult: (UInt, Bits) => Unit,
    requestFlags: Bits => Unit
) extends FunctionalUnit(ExecutionUnitEnum.FPU) {
  FpMisc.SupportedUops.foreach(add)

  private def oneOf(options: MicroCode.E*): Bool = {
    if(options.isEmpty) False else options.map(microCode === _).reduce(_ || _)
  }

  private def s1: Bits = Mux(isHalfFmt, FpuFormatUtils.halfToSingle(fpRs1HValue), fpRs1SValue)
  private def s2: Bits = Mux(isHalfFmt, FpuFormatUtils.halfToSingle(fpRs2HValue), fpRs2SValue)

  private def packSOrH(data: Bits): Bits = {
    val half = FpuFormatUtils.singleToHalf(data, fcvtRm)
    Mux(isHalfFmt, FpuFormatUtils.boxedH(half._1), FpuFormatUtils.boxedS(data))
  }

  when(fmvXWFire) {
    val moved = Mux(isFmvXHInsn, fpRs1Data(15 downto 0).asSInt.resize(64).asBits, fpRs1Data(31 downto 0).asSInt.resize(64).asBits)
    requestIntResult(rd, moved)
  }
  when(fmvXDFire) {
    requestIntResult(rd, fpRs1Data)
  }
  when(fmvWXFire) {
    writeFp(rd, Mux(isFmvHXInsn, FpuFormatUtils.boxedH(srcInt(15 downto 0)), FpuFormatUtils.boxedS(srcInt(31 downto 0))))
  }
  when(fmvDXFire) {
    writeFp(rd, srcInt)
  }

  when(fclassSFire) {
    val cls = Mux(isHalfFmt, FpuFormatUtils.classifyH(fpRs1HValue), FpuElementMisc.classifyS(fpRs1SValue))
    requestIntResult(rd, B(0, 54 bits) ## cls)
  }
  when(fclassDFire) {
    val cls = FpuElementMisc.classifyD(fpRs1Data)
    requestIntResult(rd, B(0, 54 bits) ## cls)
  }

  when(fliSFire) {
    val fli = FpuElementMisc.fliS(insn(19 downto 15).asUInt)
    writeFp(rd, packSOrH(fli))
  }
  when(fliDFire) {
    writeFp(rd, FpuElementMisc.fliD(insn(19 downto 15).asUInt))
  }

  when(fsgnjSFire) {
    val out = FpuElementMisc.signInjectS(fpRs1SValue, fpRs2SValue, microCode === uopFSGNJNS, microCode === uopFSGNJXS)
    val halfSign = Bool()
    halfSign := fpRs2HValue(15)
    when(microCode === uopFSGNJNS) {
      halfSign := !fpRs2HValue(15)
    }
    when(microCode === uopFSGNJXS) {
      halfSign := fpRs1HValue(15) ^ fpRs2HValue(15)
    }
    val outH = halfSign.asBits ## fpRs1HValue(14 downto 0)
    writeFp(rd, Mux(isHalfFmt, FpuFormatUtils.boxedH(outH), FpuFormatUtils.boxedS(out)))
  }
  when(fsgnjDFire) {
    val out = FpuElementMisc.signInjectD(fpRs1Data, fpRs2Data, microCode === uopFSGNJND, microCode === uopFSGNJXD)
    writeFp(rd, out)
  }

  when(fcmpSFire) {
    val cmp = FpuCompareResult(B(0, 64 bits), B(0, 5 bits))
    when(oneOf(uopFLEQS, uopFLTQS)) {
      val quiet = FpuElementMisc.compareQuietS(s1, s2, microCode === uopFLTQS, microCode === uopFLEQS)
      cmp.result := quiet.result
      cmp.flags := quiet.flags
    } otherwise {
      val normal = FpuElementMisc.compareS(s1, s2, microCode === uopFEQS, microCode === uopFLTS, microCode === uopFLES)
      cmp.result := normal.result
      cmp.flags := normal.flags
    }
    requestFlags(cmp.flags)
    requestIntResult(rd, cmp.result)
  }
  when(fcmpDFire) {
    val cmp = FpuCompareResult(B(0, 64 bits), B(0, 5 bits))
    when(oneOf(uopFLEQD, uopFLTQD)) {
      val quiet = FpuElementMisc.compareQuietD(fpRs1Data, fpRs2Data, microCode === uopFLTQD, microCode === uopFLEQD)
      cmp.result := quiet.result
      cmp.flags := quiet.flags
    } otherwise {
      val normal = FpuElementMisc.compareD(fpRs1Data, fpRs2Data, microCode === uopFEQD, microCode === uopFLTD, microCode === uopFLED)
      cmp.result := normal.result
      cmp.flags := normal.flags
    }
    requestFlags(cmp.flags)
    requestIntResult(rd, cmp.result)
  }

  when(fminmaxSFire) {
    val minMax = FpuMinMaxResult(B(0, 32 bits), B(0, 5 bits))
    when(oneOf(uopFMINMS, uopFMAXMS)) {
      val mag = FpuElementMisc.minMaxMagS(s1, s2, microCode === uopFMINMS)
      minMax.data := mag.data
      minMax.flags := mag.flags
    } otherwise {
      val normal = FpuElementMisc.minMaxS(s1, s2, microCode === uopFMINS)
      minMax.data := normal.data
      minMax.flags := normal.flags
    }
    requestFlags(minMax.flags)
    writeFp(rd, packSOrH(minMax.data))
  }
  when(fminmaxDFire) {
    val minMax = FpuMinMaxResult(B(0, 64 bits), B(0, 5 bits))
    when(oneOf(uopFMINMD, uopFMAXMD)) {
      val mag = FpuElementMisc.minMaxMagD(fpRs1Data, fpRs2Data, microCode === uopFMINMD)
      minMax.data := mag.data
      minMax.flags := mag.flags
    } otherwise {
      val normal = FpuElementMisc.minMaxD(fpRs1Data, fpRs2Data, microCode === uopFMIND)
      minMax.data := normal.data
      minMax.flags := normal.flags
    }
    requestFlags(minMax.flags)
    writeFp(rd, minMax.data)
  }

  when(froundSFire) {
    val rounded = FpuElementMisc.roundIntegralS(s1, fcvtRm, microCode === uopFROUNDNXS)
    writeFpSOrH(rd, rounded.data, rounded.flags)
  }
  when(froundDFire) {
    val rounded = FpuElementMisc.roundIntegralD(fpRs1Data, fcvtRm, microCode === uopFROUNDNXD)
    writeFp(rd, rounded.data)
    requestFlags(rounded.flags)
  }
}
