package borb.backend

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.common.Common._
import borb.common.LaneKey
import borb.common.MicroCode
import borb.common.MicroCode._
import borb.dispatch.Dispatch
import borb.dispatch.SrcPlugin
import borb.execute.Lsu
import borb.execute.WriteBack
import borb.execute.FunctionalUnit
import borb.execute.fpu.{FpArithmetic, FpConvert, FpLoadStore, FpMisc, FpuRegisterFile, FpuSoftFloatUtils}
import borb.execute.fpu.FpuFormatUtils._
import borb.frontend.Decoder
import borb.frontend.Decoder._
import borb.frontend.ExecutionUnitEnum

object FpBackend {
  val SupportedUops =
    FpLoadStore.SupportedUops ++
    FpArithmetic.SupportedUops ++
    FpMisc.SupportedUops ++
    Seq(
      uopFCVTWS,
      uopFCVTWUS,
      uopFCVTLS,
      uopFCVTLUS,
      uopFCVTSW,
      uopFCVTSWU,
      uopFCVTSL,
      uopFCVTSLU,
      uopFCVTWD,
      uopFCVTWUD,
      uopFCVTLD,
      uopFCVTLUD,
      uopFCVTMODWD,
      uopFCVTDW,
      uopFCVTDWU,
      uopFCVTDL,
      uopFCVTDLU,
      uopFCVTSD,
      uopFCVTDS,
      uopFCVTLH,
      uopFCVTLUH,
      uopFCVTHL,
      uopFCVTHLU
    )
}

case class FpBackend(execStage: CtrlLink, lsu: Lsu, currentEpoch: UInt, frm: Bits) extends FunctionalUnit(ExecutionUnitEnum.FPU) {
  FpBackend.SupportedUops.foreach(add)

  val fpWrite = FpWriteIntent()
  val fpFlags = FpFlagsIntent()
  val intResult = IntResultIntent()
  val suppressIntWriteback = Bool()

  fpWrite.valid := False
  fpWrite.address := 0
  fpWrite.data := 0

  fpFlags.valid := False
  fpFlags.bits := 0

  intResult.valid := False
  intResult.rd := 0
  intResult.data := 0
  intResult.writesRd := False
  intResult.commitEligible := False
  intResult.epoch := 0

  suppressIntWriteback := False

  val logic = new execStage.Area {
    val epochMatches = up(SPEC_EPOCH, LaneKey.Lane0) === currentEpoch
    val fpRegFile = FpuRegisterFile(flen = 64, readPorts = 3, writePorts = 1)

    fpRegFile.io.writes(0).valid := fpWrite.valid
    fpRegFile.io.writes(0).address := fpWrite.address
    fpRegFile.io.writes(0).data := fpWrite.data

    def requestIntResult(rd: UInt, data: Bits): Unit = {
      intResult.valid := True
      intResult.rd := rd
      intResult.data := data
      intResult.writesRd := True
      intResult.commitEligible := epochMatches
      intResult.epoch := up(SPEC_EPOCH, LaneKey.Lane0)

      down(WriteBack.RESULT, LaneKey.Lane0).address.allowOverride := rd
      down(WriteBack.RESULT, LaneKey.Lane0).data.allowOverride := Mux(rd === 0, B(0, 64 bits), data)
      down(WriteBack.RESULT, LaneKey.Lane0).valid.allowOverride := True
    }

    def requestFlags(bits: Bits): Unit = {
      when(bits =/= 0) {
        fpFlags.valid := True
        fpFlags.bits := bits
      }
    }

    def writeFp(rd: UInt, data: Bits): Unit = {
      fpWrite.valid := True
      fpWrite.address := rd
      fpWrite.data := data
    }

    val insn = up(Decoder.DECODED_INSTRUCTION, LaneKey.Lane0)
    val microCode = up(Decoder.MicroCode, LaneKey.Lane0)
    val stagePayloadValid = up.isValid && up(Decoder.VALID, LaneKey.Lane0) && up(LANE_SEL, LaneKey.Lane0)
    val aguFire = stagePayloadValid && up(Dispatch.SENDTOAGU, LaneKey.Lane0)
    val aluFire = stagePayloadValid && up(Dispatch.SENDTOALU, LaneKey.Lane0)
    val faluFire = aluFire && (up(BackendIssue.SELECTED_PIPE, LaneKey.Lane0) === BackendPipe.Falu)
    val fmacFire = aluFire && (up(BackendIssue.SELECTED_PIPE, LaneKey.Lane0) === BackendPipe.Fmac)
    val fpUsesLdq = (up(Decoder.IS_FLOAT, LaneKey.Lane0) === borb.frontend.YESNO.Y) && (up(Decoder.USES_LDQ, LaneKey.Lane0) === borb.frontend.YESNO.Y)
    val fpUsesStq = (up(Decoder.IS_FLOAT, LaneKey.Lane0) === borb.frontend.YESNO.Y) && (up(Decoder.USES_STQ, LaneKey.Lane0) === borb.frontend.YESNO.Y)

    def isAny(options: MicroCode.E*): Bool = {
      if(options.isEmpty) False else options.map(microCode === _).reduce(_ || _)
    }

    val flwRd = up(Decoder.RD_ADDR, LaneKey.Lane0).asUInt
    val fcvtSRd = up(Decoder.RD_ADDR, LaneKey.Lane0).asUInt
    val fpRs1Addr = up(Decoder.RS1_ADDR, LaneKey.Lane0).asUInt
    val fpRs2Addr = up(Decoder.RS2_ADDR, LaneKey.Lane0).asUInt
    val fpRs3Addr = up(Decoder.RS3_ADDR, LaneKey.Lane0).asUInt

    fpRegFile.io.reads(0).valid := True
    fpRegFile.io.reads(0).address := fpRs1Addr
    fpRegFile.io.reads(1).valid := True
    fpRegFile.io.reads(1).address := fpRs2Addr
    fpRegFile.io.reads(2).valid := True
    fpRegFile.io.reads(2).address := fpRs3Addr

    val fpRs1Data = fpRegFile.io.reads(0).data
    val fpRs2Data = fpRegFile.io.reads(1).data
    val fpRs3Data = fpRegFile.io.reads(2).data
    val fpRs1SBoxed = fpRs1Data(63 downto 32) === B(BigInt("FFFFFFFF", 16), 32 bits)
    val fpRs2SBoxed = fpRs2Data(63 downto 32) === B(BigInt("FFFFFFFF", 16), 32 bits)
    val fpRs3SBoxed = fpRs3Data(63 downto 32) === B(BigInt("FFFFFFFF", 16), 32 bits)
    val fpRs1HBoxed = fpRs1Data(63 downto 16) === B(BigInt("FFFFFFFFFFFF", 16), 48 bits)
    val fpRs2HBoxed = fpRs2Data(63 downto 16) === B(BigInt("FFFFFFFFFFFF", 16), 48 bits)
    val fpRs3HBoxed = fpRs3Data(63 downto 16) === B(BigInt("FFFFFFFFFFFF", 16), 48 bits)
    val fpRs1SValue = Mux(fpRs1SBoxed, fpRs1Data(31 downto 0), FpuSoftFloatUtils.canonicalNaN32)
    val fpRs2SValue = Mux(fpRs2SBoxed, fpRs2Data(31 downto 0), FpuSoftFloatUtils.canonicalNaN32)
    val fpRs3SValue = Mux(fpRs3SBoxed, fpRs3Data(31 downto 0), FpuSoftFloatUtils.canonicalNaN32)
    val fpRs1HValue = Mux(fpRs1HBoxed, fpRs1Data(15 downto 0), B"16'x7E00")
    val fpRs2HValue = Mux(fpRs2HBoxed, fpRs2Data(15 downto 0), B"16'x7E00")
    val fpRs3HValue = Mux(fpRs3HBoxed, fpRs3Data(15 downto 0), B"16'x7E00")

    val isHalfFmt = insn(26 downto 25) === B"10"
    val isFmvXHInsn = insn(31 downto 25) === B"1110010"
    val isFmvHXInsn = insn(31 downto 25) === B"1111010"
    val isFcvtSFromH = (insn(31 downto 25) === B"0100000") && (insn(24 downto 20) === B"00010")
    val isFcvtHFromS = (insn(31 downto 25) === B"0100010") && (insn(24 downto 20) === B"00000")
    val isFcvtDFromH = (insn(31 downto 25) === B"0100001") && (insn(24 downto 20) === B"00010")
    val isFcvtHFromD = (insn(31 downto 25) === B"0100010") && (insn(24 downto 20) === B"00001")

    FpLoadStore(
      microCode = microCode,
      aguFire = aguFire,
      fpUsesLdq = fpUsesLdq,
      fpUsesStq = fpUsesStq,
      fpRs2Data = fpRs2Data,
      rd = flwRd,
      lsu = lsu,
      writeFp = writeFp,
      clearIntWriteback = () => {
      suppressIntWriteback := True
      down(WriteBack.RESULT, LaneKey.Lane0).address.allowOverride := 0
      down(WriteBack.RESULT, LaneKey.Lane0).data.allowOverride := 0
      down(WriteBack.RESULT, LaneKey.Lane0).valid.allowOverride := False
      }
    )

    val fcvtFToIntFire = faluFire && isAny(uopFCVTWS, uopFCVTWUS, uopFCVTLS, uopFCVTLUS)
    val fcvtSToFpFire = faluFire && isAny(uopFCVTSW, uopFCVTSWU, uopFCVTSL, uopFCVTSLU)
    val fcvtDToIntFire = faluFire && isAny(uopFCVTWD, uopFCVTWUD, uopFCVTLD, uopFCVTLUD)
    val fcvtmodWdFire = faluFire && (microCode === uopFCVTMODWD)
    val fcvtDToFpFire = faluFire && isAny(uopFCVTDW, uopFCVTDWU, uopFCVTDL, uopFCVTDLU)
    val fcvtDToSFire = faluFire && (microCode === uopFCVTSD)
    val fcvtSToDFire = faluFire && (microCode === uopFCVTDS)
    val fcvtHToIntFire = faluFire && isAny(uopFCVTLH, uopFCVTLUH)
    val fcvtIntToHFire = faluFire && isAny(uopFCVTHL, uopFCVTHLU)
    val fmvXWFire = faluFire && (microCode === uopFMVXW)
    val fmvWXFire = faluFire && (microCode === uopFMVWX)
    val fmvXDFire = faluFire && (microCode === uopFMVXD)
    val fmvDXFire = faluFire && (microCode === uopFMVDX)
    val faddsubSFire = faluFire && isAny(uopFADDS, uopFSUBS)
    val fmulSFire = fmacFire && (microCode === uopFMULS)
    val fdivSFire = faluFire && (microCode === uopFDIVS)
    val fsqrtSFire = faluFire && (microCode === uopFSQRTS)
    val faddsubDFire = faluFire && isAny(uopFADDD, uopFSUBD)
    val fmulDFire = fmacFire && (microCode === uopFMULD)
    val fdivDFire = faluFire && (microCode === uopFDIVD)
    val fsqrtDFire = faluFire && (microCode === uopFSQRTD)
    val fmaSFire = fmacFire && isAny(uopFMADDS, uopFMSUBS, uopFNMSUBS, uopFNMADDS)
    val fmaDFire = fmacFire && isAny(uopFMADDD, uopFMSUBD, uopFNMSUBD, uopFNMADDD)
    val fclassSFire = faluFire && (microCode === uopFCLASSS)
    val fclassDFire = faluFire && (microCode === uopFCLASSD)
    val fsgnjSFire = faluFire && isAny(uopFSGNJS, uopFSGNJNS, uopFSGNJXS)
    val fsgnjDFire = faluFire && isAny(uopFSGNJD, uopFSGNJND, uopFSGNJXD)
    val fminmaxSFire = faluFire && isAny(uopFMINS, uopFMAXS, uopFMINMS, uopFMAXMS)
    val fminmaxDFire = faluFire && isAny(uopFMIND, uopFMAXD, uopFMINMD, uopFMAXMD)
    val fcmpSFire = faluFire && isAny(uopFLES, uopFLTS, uopFEQS, uopFLEQS, uopFLTQS)
    val fcmpDFire = faluFire && isAny(uopFLED, uopFLTD, uopFEQD, uopFLEQD, uopFLTQD)
    val fliSFire = faluFire && (microCode === uopFLIS)
    val fliDFire = faluFire && (microCode === uopFLID)
    val froundSFire = faluFire && isAny(uopFROUNDS, uopFROUNDNXS)
    val froundDFire = faluFire && isAny(uopFROUNDD, uopFROUNDNXD)

    val fcvtRmRaw = Mux(insn(14 downto 12) === B"111", frm, insn(14 downto 12))
    val fcvtRm = Bits(3 bits)
    fcvtRm := fcvtRmRaw
    when(fcvtRmRaw === B"101" || fcvtRmRaw === B"110" || fcvtRmRaw === B"111") {
      fcvtRm := B"001"
    }

    def writeFpSOrH(rd: UInt, data: Bits, flags: Bits): Unit = {
      val half = singleToHalf(data, fcvtRm)
      writeFp(rd, Mux(isHalfFmt, boxedH(half._1), boxedS(data)))
      requestFlags(Mux(isHalfFmt, flags | half._2, flags))
    }

    val fcvtLSrc = fpRs1SValue
    val fcvtLSign = fcvtLSrc(31)
    val fcvtLExp = fcvtLSrc(30 downto 23).asUInt
    val fcvtLFrac = fcvtLSrc(22 downto 0).asUInt
    val fcvtLIsZero = (fcvtLExp === U(0, 8 bits)) && (fcvtLFrac === U(0, 23 bits))
    val fcvtLIsInf = (fcvtLExp === U(255, 8 bits)) && (fcvtLFrac === U(0, 23 bits))
    val fcvtLIsNaN = (fcvtLExp === U(255, 8 bits)) && (fcvtLFrac =/= U(0, 23 bits))
    val fcvtLNormMant = (U(1, 1 bits) ## fcvtLFrac).asUInt
    val fcvtLMant = UInt(24 bits)
    fcvtLMant := fcvtLNormMant
    when(fcvtLExp === U(0, 8 bits)) {
      fcvtLMant := (U(0, 1 bits) ## fcvtLFrac).asUInt
    }
    val fcvtLTruncMag = UInt(65 bits)
    fcvtLTruncMag := 0
    val fcvtLRemNZ = Bool()
    fcvtLRemNZ := False
    val fcvtLGtHalf = Bool()
    fcvtLGtHalf := False
    val fcvtLEqHalf = Bool()
    fcvtLEqHalf := False
    when((fcvtLExp >= U(127, 8 bits)) && (fcvtLExp <= U(191, 8 bits))) {
      val e = (fcvtLExp - U(127, 8 bits)).resized
      when(e >= U(23, 8 bits)) {
        fcvtLTruncMag := fcvtLMant.resize(65) |<< (e - U(23, 8 bits)).resized
      } otherwise {
        val rshift = (U(23, 8 bits) - e).resized
        val mask = (U(1, 65 bits) |<< rshift) - U(1, 65 bits)
        val rem = fcvtLMant.resize(65) & mask
        val half = U(1, 65 bits) |<< (rshift - U(1, 8 bits)).resized
        fcvtLTruncMag := fcvtLMant.resize(65) |>> rshift
        fcvtLRemNZ := rem =/= 0
        fcvtLGtHalf := rem > half
        fcvtLEqHalf := rem === half
      }
    } elsewhen(fcvtLExp === U(126, 8 bits)) {
      val rem = fcvtLMant.resize(65)
      val half = U(1, 65 bits) |<< 23
      fcvtLRemNZ := rem =/= 0
      fcvtLGtHalf := rem > half
      fcvtLEqHalf := rem === half
    } elsewhen(!fcvtLIsZero && !fcvtLIsInf && !fcvtLIsNaN) {
      fcvtLRemNZ := True
    }

    val fcvtLInc = FpuSoftFloatUtils.roundInc(fcvtRm, fcvtLSign, fcvtLRemNZ, fcvtLGtHalf, fcvtLEqHalf, fcvtLTruncMag(0))
    val fcvtLRoundedMag = fcvtLTruncMag + fcvtLInc.asUInt.resize(65)
    val fcvtLInexact = fcvtLRemNZ

    val fcvtLResult = Bits(64 bits)
    fcvtLResult := 0
    val fcvtLFlags = Bits(5 bits)
    fcvtLFlags := 0

    val sMax = U(BigInt("7FFFFFFFFFFFFFFF", 16), 65 bits)
    val sMinMag = U(BigInt("8000000000000000", 16), 65 bits)
    val wSMax = U(BigInt("7FFFFFFF", 16), 65 bits)
    val wSMinMag = U(BigInt("80000000", 16), 65 bits)
    val fcvtTarget32 = isAny(uopFCVTWS, uopFCVTWUS)
    val fcvtSigned = isAny(uopFCVTWS, uopFCVTLS)
    val fcvtLTooLarge32 = (!fcvtLIsZero) && (!fcvtLIsInf) && (!fcvtLIsNaN) && (fcvtLExp > U(158, 8 bits))
    val fcvtLTooLarge64 = (!fcvtLIsZero) && (!fcvtLIsInf) && (!fcvtLIsNaN) && (fcvtLExp > U(191, 8 bits))

    when(fcvtFToIntFire) {
      when(fcvtSigned) {
        val signedOverflow32 = fcvtLTooLarge32 || ((!fcvtLSign && (fcvtLRoundedMag > wSMax)) || (fcvtLSign && (fcvtLRoundedMag > wSMinMag)))
        val signedOverflow64 = fcvtLTooLarge64 || ((!fcvtLSign && (fcvtLRoundedMag > sMax)) || (fcvtLSign && (fcvtLRoundedMag > sMinMag)))
        val signedOverflow = Mux(fcvtTarget32, signedOverflow32, signedOverflow64)
        when(fcvtLIsNaN || fcvtLIsInf || signedOverflow) {
          fcvtLFlags(4) := True
          when(fcvtLIsNaN) {
            fcvtLResult := Mux(fcvtTarget32, B(BigInt("000000007FFFFFFF", 16), 64 bits), B(BigInt("7FFFFFFFFFFFFFFF", 16), 64 bits))
          } otherwise {
            when(fcvtTarget32) {
              fcvtLResult := Mux(fcvtLSign, B(BigInt("FFFFFFFF80000000", 16), 64 bits), B(BigInt("000000007FFFFFFF", 16), 64 bits))
            } otherwise {
              fcvtLResult := Mux(fcvtLSign, B(BigInt("8000000000000000", 16), 64 bits), B(BigInt("7FFFFFFFFFFFFFFF", 16), 64 bits))
            }
          }
        } otherwise {
          val mag64 = fcvtLRoundedMag(63 downto 0).asBits
          val sVal = Mux(fcvtLSign, ((~mag64).asUInt + U(1, 64 bits)).asBits, mag64)
          when(fcvtTarget32) {
            val mag32 = fcvtLRoundedMag(31 downto 0).asBits
            val sVal32 = Mux(fcvtLSign, ((~mag32).asUInt + U(1, 32 bits)).asBits, mag32)
            fcvtLResult := sVal32.asSInt.resize(64).asBits
          } otherwise {
            fcvtLResult := sVal
          }
          when(fcvtLInexact) {
            fcvtLFlags(0) := True
          }
        }
      } otherwise {
        val unsignedInvalidNeg = fcvtLSign && (fcvtLRoundedMag =/= U(0, 65 bits))
        val unsignedOverflow32 = fcvtLTooLarge32 || fcvtLRoundedMag(64 downto 32).orR
        val unsignedOverflow64 = fcvtLTooLarge64 || fcvtLRoundedMag.msb
        val unsignedOverflow = Mux(fcvtTarget32, unsignedOverflow32, unsignedOverflow64)
        when(fcvtLIsNaN || fcvtLIsInf || unsignedInvalidNeg || unsignedOverflow) {
          fcvtLFlags(4) := True
          when(fcvtLIsNaN) {
            fcvtLResult := B(BigInt("FFFFFFFFFFFFFFFF", 16), 64 bits)
          } otherwise {
            fcvtLResult := Mux(fcvtLSign, B(0, 64 bits), B(BigInt("FFFFFFFFFFFFFFFF", 16), 64 bits))
          }
        } otherwise {
          val wuVal32 = fcvtLRoundedMag(31 downto 0).asBits
          fcvtLResult := Mux(fcvtTarget32, wuVal32.asSInt.resize(64).asBits, fcvtLRoundedMag(63 downto 0).asBits)
          when(fcvtLInexact) {
            fcvtLFlags(0) := True
          }
        }
      }

      requestIntResult(up(Decoder.RD_ADDR, LaneKey.Lane0).asUInt, fcvtLResult)
      requestFlags(fcvtLFlags)
    }

    val fcvtDSrc = fpRs1Data
    val fcvtDSign = fcvtDSrc(63)
    val fcvtDExp = fcvtDSrc(62 downto 52).asUInt
    val fcvtDFrac = fcvtDSrc(51 downto 0).asUInt
    val fcvtDIsZero = (fcvtDExp === U(0, 11 bits)) && (fcvtDFrac === U(0, 52 bits))
    val fcvtDIsInf = (fcvtDExp === U(2047, 11 bits)) && (fcvtDFrac === U(0, 52 bits))
    val fcvtDIsNaN = (fcvtDExp === U(2047, 11 bits)) && (fcvtDFrac =/= U(0, 52 bits))
    val fcvtDNormMant = (U(1, 1 bits) ## fcvtDFrac).asUInt
    val fcvtDMant = UInt(53 bits)
    fcvtDMant := fcvtDNormMant
    when(fcvtDExp === U(0, 11 bits)) {
      fcvtDMant := (U(0, 1 bits) ## fcvtDFrac).asUInt
    }
    val fcvtDTruncMag = UInt(65 bits)
    fcvtDTruncMag := 0
    val fcvtDRemNZ = Bool()
    fcvtDRemNZ := False
    val fcvtDGtHalf = Bool()
    fcvtDGtHalf := False
    val fcvtDEqHalf = Bool()
    fcvtDEqHalf := False
    when((fcvtDExp >= U(1023, 11 bits)) && (fcvtDExp <= U(1086, 11 bits))) {
      val e = (fcvtDExp - U(1023, 11 bits)).resized
      when(e >= U(52, 11 bits)) {
        fcvtDTruncMag := fcvtDMant.resize(65) |<< (e - U(52, 11 bits)).resized
      } otherwise {
        val rshift = (U(52, 11 bits) - e).resized
        val mask = (U(1, 65 bits) |<< rshift) - U(1, 65 bits)
        val rem = fcvtDMant.resize(65) & mask
        val half = U(1, 65 bits) |<< (rshift - U(1, 11 bits)).resized
        fcvtDTruncMag := fcvtDMant.resize(65) |>> rshift
        fcvtDRemNZ := rem =/= 0
        fcvtDGtHalf := rem > half
        fcvtDEqHalf := rem === half
      }
    } elsewhen(fcvtDExp === U(1022, 11 bits)) {
      val rem = fcvtDMant.resize(65)
      val half = U(1, 65 bits) |<< 52
      fcvtDRemNZ := rem =/= 0
      fcvtDGtHalf := rem > half
      fcvtDEqHalf := rem === half
    } elsewhen(!fcvtDIsZero && !fcvtDIsInf && !fcvtDIsNaN) {
      fcvtDRemNZ := True
    }

    val fcvtDInc = FpuSoftFloatUtils.roundInc(fcvtRm, fcvtDSign, fcvtDRemNZ, fcvtDGtHalf, fcvtDEqHalf, fcvtDTruncMag(0))
    val fcvtDRoundedMag = fcvtDTruncMag + fcvtDInc.asUInt.resize(65)
    val fcvtDResult = Bits(64 bits)
    fcvtDResult := 0
    val fcvtDFlags = Bits(5 bits)
    fcvtDFlags := 0
    val fcvtDTarget32 = isAny(uopFCVTWD, uopFCVTWUD)
    val fcvtDSigned = isAny(uopFCVTWD, uopFCVTLD)
    val fcvtDTooLarge32 = (!fcvtDIsZero) && (!fcvtDIsInf) && (!fcvtDIsNaN) && (fcvtDExp > U(1054, 11 bits))
    val fcvtDTooLarge64 = (!fcvtDIsZero) && (!fcvtDIsInf) && (!fcvtDIsNaN) && (fcvtDExp > U(1086, 11 bits))

    when(fcvtDToIntFire) {
      when(fcvtDSigned) {
        val signedOverflow32 = fcvtDTooLarge32 || ((!fcvtDSign && (fcvtDRoundedMag > wSMax)) || (fcvtDSign && (fcvtDRoundedMag > wSMinMag)))
        val signedOverflow64 = fcvtDTooLarge64 || ((!fcvtDSign && (fcvtDRoundedMag > sMax)) || (fcvtDSign && (fcvtDRoundedMag > sMinMag)))
        val signedOverflow = Mux(fcvtDTarget32, signedOverflow32, signedOverflow64)
        when(fcvtDIsNaN || fcvtDIsInf || signedOverflow) {
          fcvtDFlags(4) := True
          when(fcvtDIsNaN) {
            fcvtDResult := Mux(fcvtDTarget32, B(BigInt("000000007FFFFFFF", 16), 64 bits), B(BigInt("7FFFFFFFFFFFFFFF", 16), 64 bits))
          } otherwise {
            when(fcvtDTarget32) {
              fcvtDResult := Mux(fcvtDSign, B(BigInt("FFFFFFFF80000000", 16), 64 bits), B(BigInt("000000007FFFFFFF", 16), 64 bits))
            } otherwise {
              fcvtDResult := Mux(fcvtDSign, B(BigInt("8000000000000000", 16), 64 bits), B(BigInt("7FFFFFFFFFFFFFFF", 16), 64 bits))
            }
          }
        } otherwise {
          val mag64 = fcvtDRoundedMag(63 downto 0).asBits
          val sVal = Mux(fcvtDSign, ((~mag64).asUInt + U(1, 64 bits)).asBits, mag64)
          when(fcvtDTarget32) {
            val mag32 = fcvtDRoundedMag(31 downto 0).asBits
            val sVal32 = Mux(fcvtDSign, ((~mag32).asUInt + U(1, 32 bits)).asBits, mag32)
            fcvtDResult := sVal32.asSInt.resize(64).asBits
          } otherwise {
            fcvtDResult := sVal
          }
          when(fcvtDRemNZ) {
            fcvtDFlags(0) := True
          }
        }
      } otherwise {
        val unsignedInvalidNeg = fcvtDSign && (fcvtDRoundedMag =/= U(0, 65 bits))
        val unsignedOverflow32 = fcvtDTooLarge32 || fcvtDRoundedMag(64 downto 32).orR
        val unsignedOverflow64 = fcvtDTooLarge64 || fcvtDRoundedMag.msb
        val unsignedOverflow = Mux(fcvtDTarget32, unsignedOverflow32, unsignedOverflow64)
        when(fcvtDIsNaN || fcvtDIsInf || unsignedInvalidNeg || unsignedOverflow) {
          fcvtDFlags(4) := True
          when(fcvtDIsNaN) {
            fcvtDResult := B(BigInt("FFFFFFFFFFFFFFFF", 16), 64 bits)
          } otherwise {
            fcvtDResult := Mux(fcvtDSign, B(0, 64 bits), B(BigInt("FFFFFFFFFFFFFFFF", 16), 64 bits))
          }
        } otherwise {
          val wuVal32 = fcvtDRoundedMag(31 downto 0).asBits
          fcvtDResult := Mux(fcvtDTarget32, wuVal32.asSInt.resize(64).asBits, fcvtDRoundedMag(63 downto 0).asBits)
          when(fcvtDRemNZ) {
            fcvtDFlags(0) := True
          }
        }
      }

      requestIntResult(up(Decoder.RD_ADDR, LaneKey.Lane0).asUInt, fcvtDResult)
      requestFlags(fcvtDFlags)
    }

    when(fcvtmodWdFire) {
      val modMag = UInt(64 bits)
      modMag := 0
      when((fcvtDExp >= U(1023, 11 bits)) && (fcvtDExp =/= U(2047, 11 bits))) {
        val trueExp = (fcvtDExp - U(1023, 11 bits)).resized
        when(trueExp >= U(52, 11 bits)) {
          val leftShift = trueExp - U(52, 11 bits)
          when(leftShift < U(64, 11 bits)) {
            modMag := (fcvtDMant.resize(64) |<< leftShift).resized
          }
        } otherwise {
          modMag := (fcvtDMant.resize(64) |>> (U(52, 11 bits) - trueExp)).resized
        }
      }
      val mag32 = modMag(31 downto 0).asBits
      val signed32 = Mux(fcvtDSign, ((~mag32).asUInt + U(1, 32 bits)).asBits, mag32)
      val result = signed32.asSInt.resize(64).asBits
      val signedOverflow = (!fcvtDIsZero) && (!fcvtDIsInf) && (!fcvtDIsNaN) &&
        ((fcvtDExp > U(1054, 11 bits)) ||
          ((!fcvtDSign && (fcvtDTruncMag > wSMax)) || (fcvtDSign && (fcvtDTruncMag > wSMinMag))))
      val flags = Bits(5 bits)
      flags := 0
      when(fcvtDIsNaN || fcvtDIsInf || signedOverflow) {
        flags(4) := True
      } elsewhen(fcvtDRemNZ) {
        flags(0) := True
      }
      requestIntResult(up(Decoder.RD_ADDR, LaneKey.Lane0).asUInt, Mux(fcvtDIsNaN || fcvtDIsInf, B(0, 64 bits), result))
      requestFlags(flags)
    }

    val fcvtHSrc = fpRs1HValue
    val fcvtHSign = fcvtHSrc(15)
    val fcvtHExp = fcvtHSrc(14 downto 10).asUInt
    val fcvtHFrac = fcvtHSrc(9 downto 0).asUInt
    val fcvtHIsZero = (fcvtHExp === U(0, 5 bits)) && (fcvtHFrac === U(0, 10 bits))
    val fcvtHIsInf = (fcvtHExp === U(31, 5 bits)) && (fcvtHFrac === U(0, 10 bits))
    val fcvtHIsNaN = (fcvtHExp === U(31, 5 bits)) && (fcvtHFrac =/= U(0, 10 bits))
    val fcvtHNormMant = (U(1, 1 bits) ## fcvtHFrac).asUInt
    val fcvtHMant = UInt(11 bits)
    fcvtHMant := fcvtHNormMant
    when(fcvtHExp === U(0, 5 bits)) {
      fcvtHMant := (U(0, 1 bits) ## fcvtHFrac).asUInt
    }
    val fcvtHTruncMag = UInt(65 bits)
    fcvtHTruncMag := 0
    val fcvtHRemNZ = Bool()
    fcvtHRemNZ := False
    val fcvtHGtHalf = Bool()
    fcvtHGtHalf := False
    val fcvtHEqHalf = Bool()
    fcvtHEqHalf := False
    when(fcvtHExp >= U(15, 5 bits) && fcvtHExp < U(31, 5 bits)) {
      val e = (fcvtHExp - U(15, 5 bits)).resized
      when(e >= U(10, 5 bits)) {
        fcvtHTruncMag := fcvtHMant.resize(65) |<< (e - U(10, 5 bits)).resized
      } otherwise {
        val rshift = (U(10, 5 bits) - e).resized
        val mask = (U(1, 65 bits) |<< rshift) - U(1, 65 bits)
        val rem = fcvtHMant.resize(65) & mask
        val half = U(1, 65 bits) |<< (rshift - U(1, 5 bits)).resized
        fcvtHTruncMag := fcvtHMant.resize(65) |>> rshift
        fcvtHRemNZ := rem =/= 0
        fcvtHGtHalf := rem > half
        fcvtHEqHalf := rem === half
      }
    } elsewhen(fcvtHExp === U(14, 5 bits)) {
      val rem = fcvtHMant.resize(65)
      val half = U(1, 65 bits) |<< 10
      fcvtHRemNZ := rem =/= 0
      fcvtHGtHalf := rem > half
      fcvtHEqHalf := rem === half
    } elsewhen(!fcvtHIsZero && !fcvtHIsInf && !fcvtHIsNaN) {
      fcvtHRemNZ := True
    }

    val fcvtHInc = FpuSoftFloatUtils.roundInc(fcvtRm, fcvtHSign, fcvtHRemNZ, fcvtHGtHalf, fcvtHEqHalf, fcvtHTruncMag(0))
    val fcvtHRoundedMag = fcvtHTruncMag + fcvtHInc.asUInt.resize(65)
    when(fcvtHToIntFire) {
      val result = Bits(64 bits)
      result := 0
      val flags = Bits(5 bits)
      flags := 0
      when(microCode === uopFCVTLH) {
        when(fcvtHIsNaN || fcvtHIsInf) {
          flags(4) := True
          when(fcvtHIsNaN) {
            result := B(BigInt("7FFFFFFFFFFFFFFF", 16), 64 bits)
          } otherwise {
            result := Mux(fcvtHSign, B(BigInt("8000000000000000", 16), 64 bits), B(BigInt("7FFFFFFFFFFFFFFF", 16), 64 bits))
          }
        } otherwise {
          val mag64 = fcvtHRoundedMag(63 downto 0).asBits
          result := Mux(fcvtHSign, ((~mag64).asUInt + U(1, 64 bits)).asBits, mag64)
          when(fcvtHRemNZ) {
            flags(0) := True
          }
        }
      } otherwise {
        val invalidNeg = fcvtHSign && (fcvtHRoundedMag =/= U(0, 65 bits))
        when(fcvtHIsNaN || fcvtHIsInf || invalidNeg) {
          flags(4) := True
          when(fcvtHIsNaN) {
            result := B(BigInt("FFFFFFFFFFFFFFFF", 16), 64 bits)
          } otherwise {
            result := Mux(fcvtHSign, B(0, 64 bits), B(BigInt("FFFFFFFFFFFFFFFF", 16), 64 bits))
          }
        } otherwise {
          result := fcvtHRoundedMag(63 downto 0).asBits
          when(fcvtHRemNZ) {
            flags(0) := True
          }
        }
      }
      requestIntResult(up(Decoder.RD_ADDR, LaneKey.Lane0).asUInt, result)
      requestFlags(flags)
    }

    when(fcvtIntToHFire) {
      val converted = FpConvert.intToHalf(up(SrcPlugin.RS1, LaneKey.Lane0).asUInt, microCode === uopFCVTHL, fcvtRm)
      writeFp(fcvtSRd, converted.data)
      requestFlags(converted.flags)
    }

    when(fcvtSToFpFire) {
      val converted = FpConvert.intToSingle(
        up(SrcPlugin.RS1, LaneKey.Lane0).asUInt,
        isAny(uopFCVTSW, uopFCVTSWU),
        isAny(uopFCVTSW, uopFCVTSL),
        fcvtRm
      )
      writeFp(fcvtSRd, converted.data)
      requestFlags(converted.flags)
    }

    when(fcvtDToFpFire) {
      val converted = FpConvert.intToDouble(
        up(SrcPlugin.RS1, LaneKey.Lane0).asUInt,
        isAny(uopFCVTDW, uopFCVTDWU),
        isAny(uopFCVTDW, uopFCVTDL),
        fcvtRm
      )
      writeFp(fcvtSRd, converted.data)
      requestFlags(converted.flags)
    }

    when(fcvtSToDFire) {
      val src = Mux(isFcvtDFromH, halfToSingle(fpRs1HValue), fpRs1SValue)
      val sign = src(31)
      val exp = src(30 downto 23).asUInt
      val frac = src(22 downto 0).asUInt
      val isZero = (exp === U(0, 8 bits)) && (frac === U(0, 23 bits))
      val isInf = (exp === U(255, 8 bits)) && (frac === U(0, 23 bits))
      val isNaN = (exp === U(255, 8 bits)) && (frac =/= U(0, 23 bits))
      val isSNaN = isNaN && !src(22)
      val isSub = (exp === U(0, 8 bits)) && (frac =/= U(0, 23 bits))
      val msbIdx = UInt(5 bits)
      msbIdx := 0
      for (i <- 0 until 23) {
        when(frac(i)) {
          msbIdx := i
        }
      }

      val out = Bits(64 bits)
      out := 0
      when(isZero) {
        out := sign.asBits ## B(0, 63 bits)
      } elsewhen(isInf) {
        out := sign.asBits ## B(2047, 11 bits) ## B(0, 52 bits)
      } elsewhen(isNaN) {
        out := FpuSoftFloatUtils.canonicalNaN64
      } elsewhen(isSub) {
        val shift = (U(22, 5 bits) - msbIdx).resized
        val normFrac = (frac |<< shift).resize(23)
        val outExp = (msbIdx.resize(11) + U(874, 11 bits)).resized
        out := sign.asBits ## outExp.asBits ## normFrac(21 downto 0).asBits ## B(0, 30 bits)
      } otherwise {
        val outExp = (exp.resize(11) + U(896, 11 bits)).resized
        out := sign.asBits ## outExp.asBits ## frac.asBits ## B(0, 29 bits)
      }

      writeFp(fcvtSRd, out)
      when(isFcvtHFromS) {
        val half = singleToHalf(fpRs1SValue, fcvtRm)
        fpWrite.data := boxedH(half._1)
        requestFlags(half._2)
      }
      when(isSNaN) {
        requestFlags(B"10000")
      }
    }

    when(fcvtDToSFire) {
      val sign = fcvtDSrc(63)
      val isSNaN = fcvtDIsNaN && !fcvtDSrc(51)
      val isHalfSNaN = isFcvtSFromH && (fpRs1HValue(14 downto 10) === B(31, 5 bits)) && (fpRs1HValue(9 downto 0) =/= B(0, 10 bits)) && !fpRs1HValue(9)
      val outFp32 = Bits(32 bits)
      outFp32 := 0
      val flags = Bits(5 bits)
      flags := 0

      when(isFcvtSFromH) {
        outFp32 := halfToSingle(fpRs1HValue)
        when(isHalfSNaN) {
          flags(4) := True
        }
      } elsewhen(fcvtDIsNaN) {
        outFp32 := FpuSoftFloatUtils.canonicalNaN32
        when(isSNaN) {
          flags(4) := True
        }
      } elsewhen(fcvtDIsInf) {
        outFp32 := sign.asBits ## B(255, 8 bits) ## B(0, 23 bits)
      } elsewhen(fcvtDIsZero) {
        outFp32 := sign.asBits ## B(0, 31 bits)
      } elsewhen(fcvtDExp === U(0, 11 bits)) {
        outFp32 := sign.asBits ## B(0, 31 bits)
        flags(1) := True
        flags(0) := True
      } otherwise {
        val unbiasedExp = (fcvtDExp - U(1023, 11 bits)).resized
        val sig53 = (U(1, 1 bits) ## fcvtDFrac).asUInt
        val normalPath = fcvtDExp >= U(874, 11 bits)
        val truncSig = UInt(24 bits)
        truncSig := 0
        val remNZ = Bool()
        remNZ := False
        val gtHalf = Bool()
        gtHalf := False
        val eqHalf = Bool()
        eqHalf := False
        val extraShift = UInt(11 bits)
        extraShift := 0

        when(normalPath) {
          when(fcvtDExp >= U(897, 11 bits) && fcvtDExp <= U(1150, 11 bits)) {
            when(fcvtDExp >= U(897, 11 bits)) {
              val rem = sig53(28 downto 0)
              truncSig := sig53(52 downto 29)
              remNZ := rem =/= 0
              gtHalf := rem > (U(1, 29 bits) |<< 28)
              eqHalf := rem === (U(1, 29 bits) |<< 28)
            } otherwise {
              extraShift := (U(897, 11 bits) - fcvtDExp).resized
              val totalShift = (U(29, 11 bits) + extraShift).resized
              when(totalShift < U(64, 11 bits)) {
                val shifted = (sig53.resize(64) |>> totalShift).resize(24)
                truncSig := shifted
                val mask = (U(1, 64 bits) |<< totalShift.resized) - U(1, 64 bits)
                val rem = sig53.resize(64) & mask
                val half = U(1, 64 bits) |<< (totalShift - U(1, 11 bits)).resized
                remNZ := rem =/= 0
                gtHalf := rem > half
                eqHalf := rem === half
              } otherwise {
                truncSig := 0
                remNZ := sig53 =/= 0
              }
            }
          } otherwise {
            truncSig := 0
            remNZ := True
          }
        } otherwise {
          truncSig := 0
          remNZ := True
        }

        val inc = FpuSoftFloatUtils.roundInc(fcvtRm, sign, remNZ, gtHalf, eqHalf, truncSig(0))
        val rounded = truncSig.resize(25) + inc.asUInt.resize(25)
        val carry = rounded(24)
        val normSig = UInt(24 bits)
        normSig := rounded(23 downto 0)
        when(carry) {
          normSig := (rounded |>> 1).resize(24)
        }

        val outExp = UInt(8 bits)
        outExp := 0
        when(fcvtDExp >= U(897, 11 bits)) {
          outExp := (unbiasedExp.resize(8) + U(127, 8 bits) + carry.asUInt.resize(8)).resized
        }

        val overflowToInf = Bool()
        overflowToInf := False
        when(fcvtRm === B"000" || fcvtRm === B"100") {
          overflowToInf := True
        } elsewhen(fcvtRm === B"010" && sign) {
          overflowToInf := True
        } elsewhen(fcvtRm === B"011" && !sign) {
          overflowToInf := True
        }

        when(fcvtDExp > U(1150, 11 bits) || outExp === U(255, 8 bits)) {
          when(overflowToInf) {
            outFp32 := sign.asBits ## B(255, 8 bits) ## B(0, 23 bits)
          } otherwise {
            outFp32 := sign.asBits ## B(254, 8 bits) ## B(BigInt("7FFFFF", 16), 23 bits)
          }
          flags(2) := True
          flags(0) := True
        } otherwise {
          when(fcvtDExp >= U(897, 11 bits)) {
            outFp32 := sign.asBits ## outExp.asBits ## normSig(22 downto 0).asBits
          } otherwise {
            when(normSig(23)) {
              outFp32 := sign.asBits ## B(1, 8 bits) ## B(0, 23 bits)
            } otherwise {
              outFp32 := sign.asBits ## B(0, 8 bits) ## normSig(22 downto 0).asBits
            }
            when(remNZ || inc || !normalPath) {
              flags(1) := True
            }
          }
          when(remNZ || inc) {
            flags(0) := True
          }
        }
      }

      writeFp(fcvtSRd, boxedS(outFp32))
      val halfFromD = singleToHalf(outFp32, fcvtRm)
      when(isFcvtHFromD) {
        fpWrite.data := boxedH(halfFromD._1)
      }
      requestFlags(Mux(isFcvtHFromD, flags | halfFromD._2, flags))
    }

    FpArithmetic(
      microCode = microCode,
      rd = up(Decoder.RD_ADDR, LaneKey.Lane0).asUInt,
      fcvtRm = fcvtRm,
      isHalfFmt = isHalfFmt,
      fpRs1Data = fpRs1Data,
      fpRs2Data = fpRs2Data,
      fpRs3Data = fpRs3Data,
      fpRs1SValue = fpRs1SValue,
      fpRs2SValue = fpRs2SValue,
      fpRs3SValue = fpRs3SValue,
      fpRs1HValue = fpRs1HValue,
      fpRs2HValue = fpRs2HValue,
      fpRs3HValue = fpRs3HValue,
      faddsubSFire = faddsubSFire,
      faddsubDFire = faddsubDFire,
      fmulSFire = fmulSFire,
      fmulDFire = fmulDFire,
      fdivSFire = fdivSFire,
      fdivDFire = fdivDFire,
      fsqrtSFire = fsqrtSFire,
      fsqrtDFire = fsqrtDFire,
      fmaSFire = fmaSFire,
      fmaDFire = fmaDFire,
      writeFp = writeFp,
      writeFpSOrH = writeFpSOrH,
      requestFlags = requestFlags
    )

    FpMisc(
      microCode = microCode,
      insn = insn,
      rd = up(Decoder.RD_ADDR, LaneKey.Lane0).asUInt,
      srcInt = up(SrcPlugin.RS1, LaneKey.Lane0),
      fcvtRm = fcvtRm,
      isHalfFmt = isHalfFmt,
      isFmvXHInsn = isFmvXHInsn,
      isFmvHXInsn = isFmvHXInsn,
      fpRs1Data = fpRs1Data,
      fpRs2Data = fpRs2Data,
      fpRs1SValue = fpRs1SValue,
      fpRs2SValue = fpRs2SValue,
      fpRs1HValue = fpRs1HValue,
      fpRs2HValue = fpRs2HValue,
      fmvXWFire = fmvXWFire,
      fmvWXFire = fmvWXFire,
      fmvXDFire = fmvXDFire,
      fmvDXFire = fmvDXFire,
      fclassSFire = fclassSFire,
      fclassDFire = fclassDFire,
      fliSFire = fliSFire,
      fliDFire = fliDFire,
      fsgnjSFire = fsgnjSFire,
      fsgnjDFire = fsgnjDFire,
      fcmpSFire = fcmpSFire,
      fcmpDFire = fcmpDFire,
      fminmaxSFire = fminmaxSFire,
      fminmaxDFire = fminmaxDFire,
      froundSFire = froundSFire,
      froundDFire = froundDFire,
      writeFp = writeFp,
      writeFpSOrH = writeFpSOrH,
      requestIntResult = requestIntResult,
      requestFlags = requestFlags
    )
  }
}
