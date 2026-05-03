package borb.backend

import spinal.core._
import spinal.lib._
import spinal.lib.misc.pipeline._
import borb.common.Common._
import borb.common.MicroCode._
import borb.dispatch.Dispatch
import borb.dispatch.SrcPlugin
import borb.execute.Lsu
import borb.execute.WriteBack
import borb.execute.fpu.{FpuAddSub, FpuCompareResult, FpuDivSqrt, FpuFma, FpuMinMaxResult, FpuMul, FpuRegisterFile, FpuScalarMisc, FpuSoftFloatUtils}
import borb.frontend.Decoder
import borb.frontend.Decoder._

case class FpBackend(execStage: CtrlLink, lsu: Lsu, currentEpoch: UInt, frm: Bits) extends Area {
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
    val epochMatches = up(SPEC_EPOCH) === currentEpoch
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
      intResult.epoch := up(SPEC_EPOCH)

      down(WriteBack.RESULT).address.allowOverride := rd
      down(WriteBack.RESULT).data.allowOverride := Mux(rd === 0, B(0, 64 bits), data)
      down(WriteBack.RESULT).valid.allowOverride := True
    }

    def requestFlags(bits: Bits): Unit = {
      when(bits =/= 0) {
        fpFlags.valid := True
        fpFlags.bits := bits
      }
    }

    val insn = up(Decoder.DECODED_INSTRUCTION)
    val microCode = up(Decoder.MicroCode)
    val stagePayloadValid = up.isValid && up(Decoder.VALID) && up(LANE_SEL)
    val aguFire = stagePayloadValid && up(Dispatch.SENDTOAGU)
    val aluFire = stagePayloadValid && up(Dispatch.SENDTOALU)
    val faluFire = aluFire && (up(BackendIssue.SELECTED_PIPE) === BackendPipe.Falu)
    val fmacFire = aluFire && (up(BackendIssue.SELECTED_PIPE) === BackendPipe.Fmac)
    val fpUsesLdq = (up(Decoder.IS_FLOAT) === borb.frontend.YESNO.Y) && (up(Decoder.USES_LDQ) === borb.frontend.YESNO.Y)
    val fpUsesStq = (up(Decoder.IS_FLOAT) === borb.frontend.YESNO.Y) && (up(Decoder.USES_STQ) === borb.frontend.YESNO.Y)

    val isFlhOp = microCode === uopFLH
    val isFlwOp = microCode === uopFLW
    val isFldOp = microCode === uopFLD
    val isFshOp = microCode === uopFSH
    val isFswOp = microCode === uopFSW
    val isFsdOp = microCode === uopFSD
    val isFcvtWsOp = microCode === uopFCVTWS
    val isFcvtWuSOp = microCode === uopFCVTWUS
    val isFcvtLsOp = microCode === uopFCVTLS
    val isFcvtLuSOp = microCode === uopFCVTLUS
    val isFcvtSwOp = microCode === uopFCVTSW
    val isFcvtSwuOp = microCode === uopFCVTSWU
    val isFcvtSlOp = microCode === uopFCVTSL
    val isFcvtSluOp = microCode === uopFCVTSLU
    val isFcvtWdOp = microCode === uopFCVTWD
    val isFcvtWuDOp = microCode === uopFCVTWUD
    val isFcvtLdOp = microCode === uopFCVTLD
    val isFcvtLuDOp = microCode === uopFCVTLUD
    val isFcvtDwOp = microCode === uopFCVTDW
    val isFcvtDwuOp = microCode === uopFCVTDWU
    val isFcvtDlOp = microCode === uopFCVTDL
    val isFcvtDluOp = microCode === uopFCVTDLU
    val isFcvtSdOp = microCode === uopFCVTSD
    val isFcvtDsOp = microCode === uopFCVTDS
    val isFcvtLhOp = microCode === uopFCVTLH
    val isFcvtLuHOp = microCode === uopFCVTLUH
    val isFcvtHlOp = microCode === uopFCVTHL
    val isFcvtHluOp = microCode === uopFCVTHLU
    val isFaddSOp = microCode === uopFADDS
    val isFsubSOp = microCode === uopFSUBS
    val isFmulSOp = microCode === uopFMULS
    val isFdivSOp = microCode === uopFDIVS
    val isFsqrtSOp = microCode === uopFSQRTS
    val isFaddDOp = microCode === uopFADDD
    val isFsubDOp = microCode === uopFSUBD
    val isFmulDOp = microCode === uopFMULD
    val isFdivDOp = microCode === uopFDIVD
    val isFsqrtDOp = microCode === uopFSQRTD
    val isFmaddSOp = microCode === uopFMADDS
    val isFmsubSOp = microCode === uopFMSUBS
    val isFnmsubSOp = microCode === uopFNMSUBS
    val isFnmaddSOp = microCode === uopFNMADDS
    val isFmaddDOp = microCode === uopFMADDD
    val isFmsubDOp = microCode === uopFMSUBD
    val isFnmsubDOp = microCode === uopFNMSUBD
    val isFnmaddDOp = microCode === uopFNMADDD
    val isFmvXWOp = microCode === uopFMVXW
    val isFmvWXOp = microCode === uopFMVWX
    val isFmvXDOp = microCode === uopFMVXD
    val isFmvDXOp = microCode === uopFMVDX
    val isFclassSOp = microCode === uopFCLASSS
    val isFclassDOp = microCode === uopFCLASSD
    val isFsgnjSOp = microCode === uopFSGNJS
    val isFsgnjnSOp = microCode === uopFSGNJNS
    val isFsgnjxSOp = microCode === uopFSGNJXS
    val isFsgnjDOp = microCode === uopFSGNJD
    val isFsgnjnDOp = microCode === uopFSGNJND
    val isFsgnjxDOp = microCode === uopFSGNJXD
    val isFminSOp = microCode === uopFMINS
    val isFmaxSOp = microCode === uopFMAXS
    val isFminmSOp = microCode === uopFMINMS
    val isFmaxmSOp = microCode === uopFMAXMS
    val isFminDOp = microCode === uopFMIND
    val isFmaxDOp = microCode === uopFMAXD
    val isFminmDOp = microCode === uopFMINMD
    val isFmaxmDOp = microCode === uopFMAXMD
    val isFleSOp = microCode === uopFLES
    val isFltSOp = microCode === uopFLTS
    val isFeqSOp = microCode === uopFEQS
    val isFleqSOp = microCode === uopFLEQS
    val isFltqSOp = microCode === uopFLTQS
    val isFleDOp = microCode === uopFLED
    val isFltDOp = microCode === uopFLTD
    val isFeqDOp = microCode === uopFEQD
    val isFleqDOp = microCode === uopFLEQD
    val isFltqDOp = microCode === uopFLTQD
    val isFliSOp = microCode === uopFLIS
    val isFliDOp = microCode === uopFLID
    val isFroundSOp = microCode === uopFROUNDS
    val isFroundnxSOp = microCode === uopFROUNDNXS
    val isFroundDOp = microCode === uopFROUNDD
    val isFroundnxDOp = microCode === uopFROUNDNXD
    val isFcvtmodWdOp = microCode === uopFCVTMODWD

    val flwRd = up(Decoder.RD_ADDR).asUInt
    val fcvtSRd = up(Decoder.RD_ADDR).asUInt
    val fpRs1Addr = up(Decoder.RS1_ADDR).asUInt
    val fpRs2Addr = up(Decoder.RS2_ADDR).asUInt
    val fpRs3Addr = up(Decoder.RS3_ADDR).asUInt

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

    def boxedS(data: Bits): Bits = B(BigInt("FFFFFFFF", 16), 32 bits) ## data
    def boxedH(data: Bits): Bits = B(BigInt("FFFFFFFFFFFF", 16), 48 bits) ## data
    def halfToSingle(data: Bits): Bits = {
      val out = Bits(32 bits)
      val sign = data(15)
      val exp = data(14 downto 10).asUInt
      val frac = data(9 downto 0)
      val fracUInt = frac.asUInt
      out := sign.asBits ## B(0, 31 bits)
      when(exp === U(31, 5 bits)) {
        out := sign.asBits ## B(255, 8 bits) ## B(0, 23 bits)
        when(frac =/= B(0, 10 bits)) {
          out := FpuSoftFloatUtils.canonicalNaN32
        }
      } elsewhen(exp =/= U(0, 5 bits)) {
        out := sign.asBits ## (exp.resize(8) + U(112, 8 bits)).asBits ## frac ## B(0, 13 bits)
      } elsewhen(frac =/= B(0, 10 bits)) {
        val msbIdx = UInt(4 bits)
        msbIdx := 0
        for (i <- 0 until 10) {
          when(frac(i)) {
            msbIdx := i
          }
        }
        val norm = (fracUInt |<< (U(9, 4 bits) - msbIdx).resized).resize(10).asBits
        out := sign.asBits ## (msbIdx.resize(8) + U(103, 8 bits)).asBits ## norm(8 downto 0) ## B(0, 14 bits)
      }
      out
    }
    def singleToHalf(data: Bits, rm: Bits): (Bits, Bits) = {
      val out = Bits(16 bits)
      val flags = Bits(5 bits)
      val sign = data(31)
      val exp = data(30 downto 23).asUInt
      val frac = data(22 downto 0)
      val fracUInt = frac.asUInt
      val isZero = (exp === U(0, 8 bits)) && (frac === B(0, 23 bits))
      val isInf = (exp === U(255, 8 bits)) && (frac === B(0, 23 bits))
      val isNaN = (exp === U(255, 8 bits)) && (frac =/= B(0, 23 bits))
      val isSNaN = isNaN && !frac(22)
      val sig = UInt(24 bits)
      sig := (U(1, 1 bits) ## fracUInt).asUInt
      when(exp === U(0, 8 bits)) {
        sig := (U(0, 1 bits) ## fracUInt).asUInt
      }
      out := sign.asBits ## B(0, 15 bits)
      flags := 0

      when(isNaN) {
        out := sign.asBits ## B(31, 5 bits) ## B(BigInt("200", 16), 10 bits)
        when(isSNaN) {
          flags(4) := True
        }
      } elsewhen(isInf) {
        out := sign.asBits ## B(31, 5 bits) ## B(0, 10 bits)
      } elsewhen(!isZero) {
        when(exp >= U(113, 8 bits)) {
          val trunc = UInt(11 bits)
          trunc := sig(23 downto 13)
          val rem = sig(12 downto 0)
          val remNZ = rem =/= U(0, 13 bits)
          val gtHalf = rem > U(4096, 13 bits)
          val eqHalf = rem === U(4096, 13 bits)
          val inc = FpuSoftFloatUtils.roundInc(rm, sign, remNZ, gtHalf, eqHalf, trunc(0))
          val rounded = trunc.resize(12) + inc.asUInt.resize(12)
          val carry = rounded(11)
          val outExp = (exp - U(112, 8 bits)).resize(6) + carry.asUInt.resize(6)
          val roundedSig = UInt(11 bits)
          roundedSig := rounded(10 downto 0)
          when(carry) {
            roundedSig := (rounded |>> 1).resize(11)
          }

          when((exp >= U(143, 8 bits)) || (outExp >= U(31, 6 bits))) {
            val toInf = (rm === B"000") || (rm === B"100") || (rm === B"010" && sign) || (rm === B"011" && !sign)
            out := sign.asBits ## B(30, 5 bits) ## B(BigInt("3FF", 16), 10 bits)
            when(toInf) {
              out := sign.asBits ## B(31, 5 bits) ## B(0, 10 bits)
            }
            flags(2) := True
            flags(0) := True
          } otherwise {
            out := sign.asBits ## outExp(4 downto 0).asBits ## roundedSig(9 downto 0).asBits
            when(remNZ) {
              flags(0) := True
            }
          }
        } otherwise {
          val shift = (U(126, 8 bits) - exp).resize(6)
          val trunc = UInt(11 bits)
          val remNZ = Bool()
          val gtHalf = Bool()
          val eqHalf = Bool()
          trunc := 0
          remNZ := sig =/= U(0, 24 bits)
          gtHalf := False
          eqHalf := False
          when(shift < U(32, 6 bits)) {
            val sig32 = sig.resize(32)
            val mask = (U(1, 32 bits) |<< shift) - U(1, 32 bits)
            val rem = sig32 & mask
            val half = U(1, 32 bits) |<< (shift - U(1, 6 bits))
            trunc := (sig32 |>> shift).resize(11)
            remNZ := rem =/= U(0, 32 bits)
            gtHalf := rem > half
            eqHalf := rem === half
          }
          val inc = FpuSoftFloatUtils.roundInc(rm, sign, remNZ, gtHalf, eqHalf, trunc(0))
          val rounded = trunc + inc.asUInt.resize(11)
          when(rounded(10)) {
            out := sign.asBits ## B(1, 5 bits) ## B(0, 10 bits)
          } otherwise {
            out := sign.asBits ## B(0, 5 bits) ## rounded(9 downto 0).asBits
          }
          when(remNZ) {
            flags(0) := True
            when(!rounded(10)) {
              flags(1) := True
            }
          }
        }
      }
      (out, flags)
    }
    def classifyH(in: Bits): Bits = {
      val sign = in(15)
      val exp = in(14 downto 10)
      val frac = in(9 downto 0)
      val isZero = (exp === B(0, 5 bits)) && (frac === B(0, 10 bits))
      val isSub = (exp === B(0, 5 bits)) && (frac =/= B(0, 10 bits))
      val isInf = (exp === B(31, 5 bits)) && (frac === B(0, 10 bits))
      val isNaN = (exp === B(31, 5 bits)) && (frac =/= B(0, 10 bits))
      val cls = Bits(10 bits)
      cls := 0
      when(isInf && sign) { cls(0) := True }
      when(!isNaN && !isInf && !isZero && !isSub && sign) { cls(1) := True }
      when(isSub && sign) { cls(2) := True }
      when(isZero && sign) { cls(3) := True }
      when(isZero && !sign) { cls(4) := True }
      when(isSub && !sign) { cls(5) := True }
      when(!isNaN && !isInf && !isZero && !isSub && !sign) { cls(6) := True }
      when(isInf && !sign) { cls(7) := True }
      when(isNaN && !frac(9)) { cls(8) := True }
      when(isNaN && frac(9)) { cls(9) := True }
      cls
    }

    val isHalfFmt = insn(26 downto 25) === B"10"
    val isFmvXHInsn = insn(31 downto 25) === B"1110010"
    val isFmvHXInsn = insn(31 downto 25) === B"1111010"
    val isFcvtSFromH = (insn(31 downto 25) === B"0100000") && (insn(24 downto 20) === B"00010")
    val isFcvtHFromS = (insn(31 downto 25) === B"0100010") && (insn(24 downto 20) === B"00000")
    val isFcvtDFromH = (insn(31 downto 25) === B"0100001") && (insn(24 downto 20) === B"00010")
    val isFcvtHFromD = (insn(31 downto 25) === B"0100010") && (insn(24 downto 20) === B"00001")

    when(aguFire && fpUsesStq && isFshOp) {
      lsu.logic.rawStoreData.allowOverride := fpRs2Data(15 downto 0).resize(64)
    }
    when(aguFire && fpUsesStq && isFswOp) {
      lsu.logic.rawStoreData.allowOverride := fpRs2Data(31 downto 0).resize(64)
    }
    when(aguFire && fpUsesStq && isFsdOp) {
      lsu.logic.rawStoreData.allowOverride := fpRs2Data
    }

    val fpLoadWritebackFire = aguFire && fpUsesLdq && lsu.logic.responseArriving && !lsu.logic.suppress
    val flhWritebackFire = fpLoadWritebackFire && isFlhOp
    val flwWritebackFire = fpLoadWritebackFire && isFlwOp
    val fldWritebackFire = fpLoadWritebackFire && isFldOp
    when(flhWritebackFire) {
      fpWrite.valid := True
      fpWrite.address := flwRd
      fpWrite.data := B(BigInt("FFFFFFFFFFFF", 16), 48 bits) ## lsu.logic.shiftedLoadData(15 downto 0)
      suppressIntWriteback := True
      down(WriteBack.RESULT).address.allowOverride := 0
      down(WriteBack.RESULT).data.allowOverride := 0
      down(WriteBack.RESULT).valid.allowOverride := False
    }
    when(flwWritebackFire) {
      fpWrite.valid := True
      fpWrite.address := flwRd
      fpWrite.data := B(BigInt("FFFFFFFF", 16), 32 bits) ## lsu.logic.shiftedLoadData(31 downto 0)
      suppressIntWriteback := True
      down(WriteBack.RESULT).address.allowOverride := 0
      down(WriteBack.RESULT).data.allowOverride := 0
      down(WriteBack.RESULT).valid.allowOverride := False
    }
    when(fldWritebackFire) {
      fpWrite.valid := True
      fpWrite.address := flwRd
      fpWrite.data := lsu.logic.shiftedLoadData
      suppressIntWriteback := True
      down(WriteBack.RESULT).address.allowOverride := 0
      down(WriteBack.RESULT).data.allowOverride := 0
      down(WriteBack.RESULT).valid.allowOverride := False
    }

    val fcvtFToIntFire = faluFire && (isFcvtWsOp || isFcvtWuSOp || isFcvtLsOp || isFcvtLuSOp)
    val fcvtSToFpFire = faluFire && (isFcvtSwOp || isFcvtSwuOp || isFcvtSlOp || isFcvtSluOp)
    val fcvtDToIntFire = faluFire && (isFcvtWdOp || isFcvtWuDOp || isFcvtLdOp || isFcvtLuDOp)
    val fcvtmodWdFire = faluFire && isFcvtmodWdOp
    val fcvtDToFpFire = faluFire && (isFcvtDwOp || isFcvtDwuOp || isFcvtDlOp || isFcvtDluOp)
    val fcvtDToSFire = faluFire && isFcvtSdOp
    val fcvtSToDFire = faluFire && isFcvtDsOp
    val fcvtHToIntFire = faluFire && (isFcvtLhOp || isFcvtLuHOp)
    val fcvtIntToHFire = faluFire && (isFcvtHlOp || isFcvtHluOp)
    val fmvXWFire = faluFire && isFmvXWOp
    val fmvWXFire = faluFire && isFmvWXOp
    val fmvXDFire = faluFire && isFmvXDOp
    val fmvDXFire = faluFire && isFmvDXOp
    val faddsubSFire = faluFire && (isFaddSOp || isFsubSOp)
    val fmulSFire = fmacFire && isFmulSOp
    val fdivSFire = faluFire && isFdivSOp
    val fsqrtSFire = faluFire && isFsqrtSOp
    val faddsubDFire = faluFire && (isFaddDOp || isFsubDOp)
    val fmulDFire = fmacFire && isFmulDOp
    val fdivDFire = faluFire && isFdivDOp
    val fsqrtDFire = faluFire && isFsqrtDOp
    val fmaSFire = fmacFire && (isFmaddSOp || isFmsubSOp || isFnmsubSOp || isFnmaddSOp)
    val fmaDFire = fmacFire && (isFmaddDOp || isFmsubDOp || isFnmsubDOp || isFnmaddDOp)
    val fclassSFire = faluFire && isFclassSOp
    val fclassDFire = faluFire && isFclassDOp
    val fsgnjSFire = faluFire && (isFsgnjSOp || isFsgnjnSOp || isFsgnjxSOp)
    val fsgnjDFire = faluFire && (isFsgnjDOp || isFsgnjnDOp || isFsgnjxDOp)
    val fminmaxSFire = faluFire && (isFminSOp || isFmaxSOp || isFminmSOp || isFmaxmSOp)
    val fminmaxDFire = faluFire && (isFminDOp || isFmaxDOp || isFminmDOp || isFmaxmDOp)
    val fcmpSFire = faluFire && (isFleSOp || isFltSOp || isFeqSOp || isFleqSOp || isFltqSOp)
    val fcmpDFire = faluFire && (isFleDOp || isFltDOp || isFeqDOp || isFleqDOp || isFltqDOp)
    val fliSFire = faluFire && isFliSOp
    val fliDFire = faluFire && isFliDOp
    val froundSFire = faluFire && (isFroundSOp || isFroundnxSOp)
    val froundDFire = faluFire && (isFroundDOp || isFroundnxDOp)

    val fcvtRmRaw = Mux(insn(14 downto 12) === B"111", frm, insn(14 downto 12))
    val fcvtRm = Bits(3 bits)
    fcvtRm := fcvtRmRaw
    when(fcvtRmRaw === B"101" || fcvtRmRaw === B"110" || fcvtRmRaw === B"111") {
      fcvtRm := B"001"
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
    val fcvtTarget32 = isFcvtWsOp || isFcvtWuSOp
    val fcvtSigned = isFcvtWsOp || isFcvtLsOp
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

      requestIntResult(up(Decoder.RD_ADDR).asUInt, fcvtLResult)
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
    val fcvtDTarget32 = isFcvtWdOp || isFcvtWuDOp
    val fcvtDSigned = isFcvtWdOp || isFcvtLdOp
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

      requestIntResult(up(Decoder.RD_ADDR).asUInt, fcvtDResult)
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
      requestIntResult(up(Decoder.RD_ADDR).asUInt, Mux(fcvtDIsNaN || fcvtDIsInf, B(0, 64 bits), result))
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
      when(isFcvtLhOp) {
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
      requestIntResult(up(Decoder.RD_ADDR).asUInt, result)
      requestFlags(flags)
    }

    when(fcvtIntToHFire) {
      val srcInt64 = up(SrcPlugin.RS1).asUInt
      val srcSigned = isFcvtHlOp
      val srcSign = srcSigned && srcInt64.msb
      val srcMag = UInt(64 bits)
      srcMag := srcInt64
      when(srcSign) {
        srcMag := ((~srcInt64) + U(1, 64 bits)).resized
      }

      val msbIdx = UInt(6 bits)
      msbIdx := 0
      for (i <- 0 until 64) {
        when(srcMag(i)) {
          msbIdx := i
        }
      }

      val truncSig = UInt(11 bits)
      truncSig := 0
      val remNZ = Bool()
      remNZ := False
      val gtHalf = Bool()
      gtHalf := False
      val eqHalf = Bool()
      eqHalf := False

      when(srcMag =/= 0) {
        when(msbIdx > U(10, 6 bits)) {
          val rshift = (msbIdx - U(10, 6 bits)).resized
          val mask = (U(1, 64 bits) |<< rshift) - U(1, 64 bits)
          val rem = srcMag & mask
          val half = U(1, 64 bits) |<< (rshift - U(1, 6 bits)).resized
          truncSig := (srcMag |>> rshift).resize(11)
          remNZ := rem =/= 0
          gtHalf := rem > half
          eqHalf := rem === half
        } otherwise {
          truncSig := (srcMag |<< (U(10, 6 bits) - msbIdx).resized).resize(11)
        }
      }

      val inc = FpuSoftFloatUtils.roundInc(fcvtRm, srcSign, remNZ, gtHalf, eqHalf, truncSig(0))
      val rounded = truncSig.resize(12) + inc.asUInt.resize(12)
      val carry = rounded(11)
      val normSig = UInt(11 bits)
      normSig := rounded(10 downto 0)
      when(carry) {
        normSig := (rounded |>> 1).resize(11)
      }

      val outExp = UInt(7 bits)
      outExp := 0
      when(srcMag =/= 0) {
        outExp := (msbIdx.resize(7) + U(15, 7 bits) + carry.asUInt.resize(7)).resized
      }
      val flags = Bits(5 bits)
      flags := 0
      val outHalf = Bits(16 bits)
      outHalf := 0
      when(srcMag === 0) {
        outHalf := B(0, 16 bits)
      } elsewhen(outExp >= U(31, 7 bits)) {
        outHalf := srcSign.asBits ## B(31, 5 bits) ## B(0, 10 bits)
        flags(2) := True
        flags(0) := True
      } otherwise {
        outHalf := srcSign.asBits ## outExp(4 downto 0).asBits ## normSig(9 downto 0).asBits
        when(remNZ) {
          flags(0) := True
        }
      }
      fpWrite.valid := True
      fpWrite.address := fcvtSRd
      fpWrite.data := B(BigInt("FFFFFFFFFFFF", 16), 48 bits) ## outHalf
      requestFlags(flags)
    }

    when(fcvtSToFpFire) {
      val srcInt64 = up(SrcPlugin.RS1).asUInt
      val srcInt32 = up(SrcPlugin.RS1)(31 downto 0).asUInt
      val srcFrom32 = isFcvtSwOp || isFcvtSwuOp
      val srcSigned = isFcvtSwOp || isFcvtSlOp
      val srcSign = srcSigned && Mux(srcFrom32, srcInt32.msb, srcInt64.msb)
      val srcMag = UInt(64 bits)
      srcMag := Mux(srcFrom32, srcInt32.resize(64), srcInt64)
      when(srcSign) {
        when(srcFrom32) {
          srcMag := ((~srcInt32) + U(1, 32 bits)).resize(64)
        } otherwise {
          srcMag := ((~srcInt64) + U(1, 64 bits)).resized
        }
      }

      val msbIdx = UInt(6 bits)
      msbIdx := 0
      for (i <- 0 until 64) {
        when(srcMag(i)) {
          msbIdx := i
        }
      }

      val truncSig = UInt(24 bits)
      truncSig := 0
      val remNZ = Bool()
      remNZ := False
      val gtHalf = Bool()
      gtHalf := False
      val eqHalf = Bool()
      eqHalf := False

      when(srcMag =/= 0) {
        when(msbIdx > U(23, 6 bits)) {
          val rshift = (msbIdx - U(23, 6 bits)).resized
          val mask = (U(1, 64 bits) |<< rshift) - U(1, 64 bits)
          val rem = srcMag & mask
          val half = U(1, 64 bits) |<< (rshift - U(1, 6 bits)).resized
          truncSig := (srcMag |>> rshift).resize(24)
          remNZ := rem =/= 0
          gtHalf := rem > half
          eqHalf := rem === half
        } otherwise {
          truncSig := (srcMag |<< (U(23, 6 bits) - msbIdx).resized).resize(24)
        }
      }

      val inc = FpuSoftFloatUtils.roundInc(fcvtRm, srcSign, remNZ, gtHalf, eqHalf, truncSig(0))
      val rounded = truncSig.resize(25) + inc.asUInt.resize(25)
      val carry = rounded(24)
      val normSig = UInt(24 bits)
      normSig := rounded(23 downto 0)
      when(carry) {
        normSig := (rounded |>> 1).resize(24)
      }

      val outExp = UInt(8 bits)
      outExp := 0
      when(srcMag =/= 0) {
        outExp := (msbIdx.resize(8) + U(127, 8 bits) + carry.asUInt.resize(8)).resized
      }

      val outFrac = normSig(22 downto 0).asBits
      val outSign = srcSign && (srcMag =/= 0)
      val outFp32 = outSign.asBits ## outExp.asBits ## outFrac
      fpWrite.valid := True
      fpWrite.address := fcvtSRd
      fpWrite.data := B(BigInt("FFFFFFFF", 16), 32 bits) ## outFp32
      when(remNZ) {
        requestFlags(B"00001")
      }
    }

    when(fcvtDToFpFire) {
      val srcInt64 = up(SrcPlugin.RS1).asUInt
      val srcInt32 = up(SrcPlugin.RS1)(31 downto 0).asUInt
      val srcFrom32 = isFcvtDwOp || isFcvtDwuOp
      val srcSigned = isFcvtDwOp || isFcvtDlOp
      val srcSign = srcSigned && Mux(srcFrom32, srcInt32.msb, srcInt64.msb)
      val srcMag = UInt(64 bits)
      srcMag := Mux(srcFrom32, srcInt32.resize(64), srcInt64)
      when(srcSign) {
        when(srcFrom32) {
          srcMag := ((~srcInt32) + U(1, 32 bits)).resize(64)
        } otherwise {
          srcMag := ((~srcInt64) + U(1, 64 bits)).resized
        }
      }

      val msbIdx = UInt(6 bits)
      msbIdx := 0
      for (i <- 0 until 64) {
        when(srcMag(i)) {
          msbIdx := i
        }
      }

      val truncSig = UInt(53 bits)
      truncSig := 0
      val remNZ = Bool()
      remNZ := False
      val gtHalf = Bool()
      gtHalf := False
      val eqHalf = Bool()
      eqHalf := False

      when(srcMag =/= 0) {
        when(msbIdx > U(52, 6 bits)) {
          val rshift = (msbIdx - U(52, 6 bits)).resized
          val mask = (U(1, 64 bits) |<< rshift) - U(1, 64 bits)
          val rem = srcMag & mask
          val half = U(1, 64 bits) |<< (rshift - U(1, 6 bits)).resized
          truncSig := (srcMag |>> rshift).resize(53)
          remNZ := rem =/= 0
          gtHalf := rem > half
          eqHalf := rem === half
        } otherwise {
          truncSig := (srcMag |<< (U(52, 6 bits) - msbIdx).resized).resize(53)
        }
      }

      val inc = FpuSoftFloatUtils.roundInc(fcvtRm, srcSign, remNZ, gtHalf, eqHalf, truncSig(0))
      val rounded = truncSig.resize(54) + inc.asUInt.resize(54)
      val carry = rounded(53)
      val normSig = UInt(53 bits)
      normSig := rounded(52 downto 0)
      when(carry) {
        normSig := (rounded |>> 1).resize(53)
      }

      val outExp = UInt(11 bits)
      outExp := 0
      when(srcMag =/= 0) {
        outExp := (msbIdx.resize(11) + U(1023, 11 bits) + carry.asUInt.resize(11)).resized
      }

      val outFrac = normSig(51 downto 0).asBits
      val outSign = srcSign && (srcMag =/= 0)
      fpWrite.valid := True
      fpWrite.address := fcvtSRd
      fpWrite.data := outSign.asBits ## outExp.asBits ## outFrac
      when(remNZ) {
        requestFlags(B"00001")
      }
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

      fpWrite.valid := True
      fpWrite.address := fcvtSRd
      fpWrite.data := out
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

      fpWrite.valid := True
      fpWrite.address := fcvtSRd
      fpWrite.data := boxedS(outFp32)
      val halfFromD = singleToHalf(outFp32, fcvtRm)
      when(isFcvtHFromD) {
        fpWrite.data := boxedH(halfFromD._1)
      }
      requestFlags(Mux(isFcvtHFromD, flags | halfFromD._2, flags))
    }

    when(fmvXWFire) {
      val moved = Mux(isFmvXHInsn, fpRs1Data(15 downto 0).asSInt.resize(64).asBits, fpRs1Data(31 downto 0).asSInt.resize(64).asBits)
      requestIntResult(up(Decoder.RD_ADDR).asUInt, moved)
    }
    when(fmvXDFire) {
      requestIntResult(up(Decoder.RD_ADDR).asUInt, fpRs1Data)
    }

    when(faddsubSFire) {
      val addSub = FpuAddSub.addSubS(Mux(isHalfFmt, halfToSingle(fpRs1HValue), fpRs1SValue), Mux(isHalfFmt, halfToSingle(fpRs2HValue), fpRs2SValue), fcvtRm, isFsubSOp)
      val half = singleToHalf(addSub.data, fcvtRm)
      fpWrite.valid := True
      fpWrite.address := up(Decoder.RD_ADDR).asUInt
      fpWrite.data := Mux(isHalfFmt, boxedH(half._1), boxedS(addSub.data))
      requestFlags(Mux(isHalfFmt, addSub.flags | half._2, addSub.flags))
    }
    when(faddsubDFire) {
      val addSub = FpuAddSub.addSubD(fpRs1Data, fpRs2Data, fcvtRm, isFsubDOp)
      fpWrite.valid := True
      fpWrite.address := up(Decoder.RD_ADDR).asUInt
      fpWrite.data := addSub.data
      requestFlags(addSub.flags)
    }

    when(fmulSFire) {
      val mul = FpuMul.mulS(Mux(isHalfFmt, halfToSingle(fpRs1HValue), fpRs1SValue), Mux(isHalfFmt, halfToSingle(fpRs2HValue), fpRs2SValue), fcvtRm)
      val half = singleToHalf(mul.data, fcvtRm)
      fpWrite.valid := True
      fpWrite.address := up(Decoder.RD_ADDR).asUInt
      fpWrite.data := Mux(isHalfFmt, boxedH(half._1), boxedS(mul.data))
      requestFlags(Mux(isHalfFmt, mul.flags | half._2, mul.flags))
    }
    when(fmulDFire) {
      val mul = FpuMul.mulD(fpRs1Data, fpRs2Data, fcvtRm)
      fpWrite.valid := True
      fpWrite.address := up(Decoder.RD_ADDR).asUInt
      fpWrite.data := mul.data
      requestFlags(mul.flags)
    }

    when(fdivSFire) {
      val div = FpuDivSqrt.divS(Mux(isHalfFmt, halfToSingle(fpRs1HValue), fpRs1SValue), Mux(isHalfFmt, halfToSingle(fpRs2HValue), fpRs2SValue), fcvtRm)
      val half = singleToHalf(div.data, fcvtRm)
      fpWrite.valid := True
      fpWrite.address := up(Decoder.RD_ADDR).asUInt
      fpWrite.data := Mux(isHalfFmt, boxedH(half._1), boxedS(div.data))
      requestFlags(Mux(isHalfFmt, div.flags | half._2, div.flags))
    }
    when(fdivDFire) {
      val div = FpuDivSqrt.divD(fpRs1Data, fpRs2Data, fcvtRm)
      fpWrite.valid := True
      fpWrite.address := up(Decoder.RD_ADDR).asUInt
      fpWrite.data := div.data
      requestFlags(div.flags)
    }

    when(fsqrtSFire) {
      val sqrt = FpuDivSqrt.sqrtS(Mux(isHalfFmt, halfToSingle(fpRs1HValue), fpRs1SValue), fcvtRm)
      val half = singleToHalf(sqrt.data, fcvtRm)
      fpWrite.valid := True
      fpWrite.address := up(Decoder.RD_ADDR).asUInt
      fpWrite.data := Mux(isHalfFmt, boxedH(half._1), boxedS(sqrt.data))
      requestFlags(Mux(isHalfFmt, sqrt.flags | half._2, sqrt.flags))
    }
    when(fsqrtDFire) {
      val sqrt = FpuDivSqrt.sqrtD(fpRs1Data, fcvtRm)
      fpWrite.valid := True
      fpWrite.address := up(Decoder.RD_ADDR).asUInt
      fpWrite.data := sqrt.data
      requestFlags(sqrt.flags)
    }

    when(fmaSFire) {
      val fma = FpuFma.fmaS(
        Mux(isHalfFmt, halfToSingle(fpRs1HValue), fpRs1SValue),
        Mux(isHalfFmt, halfToSingle(fpRs2HValue), fpRs2SValue),
        Mux(isHalfFmt, halfToSingle(fpRs3HValue), fpRs3SValue),
        fcvtRm,
        isFmsubSOp,
        isFnmsubSOp,
        isFnmaddSOp
      )
      val half = singleToHalf(fma.data, fcvtRm)
      fpWrite.valid := True
      fpWrite.address := up(Decoder.RD_ADDR).asUInt
      fpWrite.data := Mux(isHalfFmt, boxedH(half._1), boxedS(fma.data))
      requestFlags(Mux(isHalfFmt, fma.flags | half._2, fma.flags))
    }
    when(fmaDFire) {
      val fma = FpuFma.fmaD(
        fpRs1Data,
        fpRs2Data,
        fpRs3Data,
        fcvtRm,
        isFmsubDOp,
        isFnmsubDOp,
        isFnmaddDOp
      )
      fpWrite.valid := True
      fpWrite.address := up(Decoder.RD_ADDR).asUInt
      fpWrite.data := fma.data
      requestFlags(fma.flags)
    }

    when(fmvWXFire) {
      fpWrite.valid := True
      fpWrite.address := fcvtSRd
      fpWrite.data := Mux(isFmvHXInsn, boxedH(up(SrcPlugin.RS1)(15 downto 0)), boxedS(up(SrcPlugin.RS1)(31 downto 0)))
    }
    when(fmvDXFire) {
      fpWrite.valid := True
      fpWrite.address := fcvtSRd
      fpWrite.data := up(SrcPlugin.RS1)
    }

    when(fclassSFire) {
      val cls = Mux(isHalfFmt, classifyH(fpRs1HValue), FpuScalarMisc.classifyS(fpRs1SValue))
      requestIntResult(up(Decoder.RD_ADDR).asUInt, B(0, 54 bits) ## cls)
    }
    when(fclassDFire) {
      val cls = FpuScalarMisc.classifyD(fpRs1Data)
      requestIntResult(up(Decoder.RD_ADDR).asUInt, B(0, 54 bits) ## cls)
    }

    when(fliSFire) {
      fpWrite.valid := True
      fpWrite.address := up(Decoder.RD_ADDR).asUInt
      val fli = FpuScalarMisc.fliS(insn(19 downto 15).asUInt)
      val half = singleToHalf(fli, fcvtRm)
      fpWrite.data := Mux(isHalfFmt, boxedH(half._1), boxedS(fli))
    }
    when(fliDFire) {
      fpWrite.valid := True
      fpWrite.address := up(Decoder.RD_ADDR).asUInt
      fpWrite.data := FpuScalarMisc.fliD(insn(19 downto 15).asUInt)
    }

    when(fsgnjSFire) {
      val out = FpuScalarMisc.signInjectS(fpRs1SValue, fpRs2SValue, isFsgnjnSOp, isFsgnjxSOp)
      val halfSign = Bool()
      halfSign := fpRs2HValue(15)
      when(isFsgnjnSOp) {
        halfSign := !fpRs2HValue(15)
      }
      when(isFsgnjxSOp) {
        halfSign := fpRs1HValue(15) ^ fpRs2HValue(15)
      }
      val outH = halfSign.asBits ## fpRs1HValue(14 downto 0)
      fpWrite.valid := True
      fpWrite.address := up(Decoder.RD_ADDR).asUInt
      fpWrite.data := Mux(isHalfFmt, boxedH(outH), boxedS(out))
    }
    when(fsgnjDFire) {
      val out = FpuScalarMisc.signInjectD(fpRs1Data, fpRs2Data, isFsgnjnDOp, isFsgnjxDOp)
      fpWrite.valid := True
      fpWrite.address := up(Decoder.RD_ADDR).asUInt
      fpWrite.data := out
    }

    when(fcmpSFire) {
      val cmp = FpuCompareResult(B(0, 64 bits), B(0, 5 bits))
      when(isFleqSOp || isFltqSOp) {
        val quiet = FpuScalarMisc.compareQuietS(Mux(isHalfFmt, halfToSingle(fpRs1HValue), fpRs1SValue), Mux(isHalfFmt, halfToSingle(fpRs2HValue), fpRs2SValue), isFltqSOp, isFleqSOp)
        cmp.result := quiet.result
        cmp.flags := quiet.flags
      } otherwise {
        val normal = FpuScalarMisc.compareS(Mux(isHalfFmt, halfToSingle(fpRs1HValue), fpRs1SValue), Mux(isHalfFmt, halfToSingle(fpRs2HValue), fpRs2SValue), isFeqSOp, isFltSOp, isFleSOp)
        cmp.result := normal.result
        cmp.flags := normal.flags
      }
      requestFlags(cmp.flags)
      requestIntResult(up(Decoder.RD_ADDR).asUInt, cmp.result)
    }
    when(fcmpDFire) {
      val cmp = FpuCompareResult(B(0, 64 bits), B(0, 5 bits))
      when(isFleqDOp || isFltqDOp) {
        val quiet = FpuScalarMisc.compareQuietD(fpRs1Data, fpRs2Data, isFltqDOp, isFleqDOp)
        cmp.result := quiet.result
        cmp.flags := quiet.flags
      } otherwise {
        val normal = FpuScalarMisc.compareD(fpRs1Data, fpRs2Data, isFeqDOp, isFltDOp, isFleDOp)
        cmp.result := normal.result
        cmp.flags := normal.flags
      }
      requestFlags(cmp.flags)
      requestIntResult(up(Decoder.RD_ADDR).asUInt, cmp.result)
    }

    when(fminmaxSFire) {
      val minMax = FpuMinMaxResult(B(0, 32 bits), B(0, 5 bits))
      when(isFminmSOp || isFmaxmSOp) {
        val mag = FpuScalarMisc.minMaxMagS(Mux(isHalfFmt, halfToSingle(fpRs1HValue), fpRs1SValue), Mux(isHalfFmt, halfToSingle(fpRs2HValue), fpRs2SValue), isFminmSOp)
        minMax.data := mag.data
        minMax.flags := mag.flags
      } otherwise {
        val normal = FpuScalarMisc.minMaxS(Mux(isHalfFmt, halfToSingle(fpRs1HValue), fpRs1SValue), Mux(isHalfFmt, halfToSingle(fpRs2HValue), fpRs2SValue), isFminSOp)
        minMax.data := normal.data
        minMax.flags := normal.flags
      }
      requestFlags(minMax.flags)
      fpWrite.valid := True
      fpWrite.address := up(Decoder.RD_ADDR).asUInt
      val half = singleToHalf(minMax.data, fcvtRm)
      fpWrite.data := Mux(isHalfFmt, boxedH(half._1), boxedS(minMax.data))
    }
    when(fminmaxDFire) {
      val minMax = FpuMinMaxResult(B(0, 64 bits), B(0, 5 bits))
      when(isFminmDOp || isFmaxmDOp) {
        val mag = FpuScalarMisc.minMaxMagD(fpRs1Data, fpRs2Data, isFminmDOp)
        minMax.data := mag.data
        minMax.flags := mag.flags
      } otherwise {
        val normal = FpuScalarMisc.minMaxD(fpRs1Data, fpRs2Data, isFminDOp)
        minMax.data := normal.data
        minMax.flags := normal.flags
      }
      requestFlags(minMax.flags)
      fpWrite.valid := True
      fpWrite.address := up(Decoder.RD_ADDR).asUInt
      fpWrite.data := minMax.data
    }
    when(froundSFire) {
      val rounded = FpuScalarMisc.roundIntegralS(Mux(isHalfFmt, halfToSingle(fpRs1HValue), fpRs1SValue), fcvtRm, isFroundnxSOp)
      requestFlags(rounded.flags)
      fpWrite.valid := True
      fpWrite.address := up(Decoder.RD_ADDR).asUInt
      val half = singleToHalf(rounded.data, fcvtRm)
      fpWrite.data := Mux(isHalfFmt, boxedH(half._1), boxedS(rounded.data))
    }
    when(froundDFire) {
      val rounded = FpuScalarMisc.roundIntegralD(fpRs1Data, fcvtRm, isFroundnxDOp)
      requestFlags(rounded.flags)
      fpWrite.valid := True
      fpWrite.address := up(Decoder.RD_ADDR).asUInt
      fpWrite.data := rounded.data
    }
  }
}
