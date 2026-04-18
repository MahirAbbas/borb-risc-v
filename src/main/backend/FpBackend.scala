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
import borb.execute.fpu.{FpuAddSub, FpuDivSqrt, FpuFma, FpuMul, FpuRegisterFile, FpuScalarMisc, FpuSoftFloatUtils}
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

    val isFlwOp = microCode === uopFLW
    val isFswOp = microCode === uopFSW
    val isFcvtWsOp = microCode === uopFCVTWS
    val isFcvtWuSOp = microCode === uopFCVTWUS
    val isFcvtLsOp = microCode === uopFCVTLS
    val isFcvtLuSOp = microCode === uopFCVTLUS
    val isFcvtSwOp = microCode === uopFCVTSW
    val isFcvtSwuOp = microCode === uopFCVTSWU
    val isFcvtSlOp = microCode === uopFCVTSL
    val isFcvtSluOp = microCode === uopFCVTSLU
    val isFaddSOp = microCode === uopFADDS
    val isFsubSOp = microCode === uopFSUBS
    val isFmulSOp = microCode === uopFMULS
    val isFdivSOp = microCode === uopFDIVS
    val isFsqrtSOp = microCode === uopFSQRTS
    val isFmaddSOp = microCode === uopFMADDS
    val isFmsubSOp = microCode === uopFMSUBS
    val isFnmsubSOp = microCode === uopFNMSUBS
    val isFnmaddSOp = microCode === uopFNMADDS
    val isFmvXWOp = microCode === uopFMVXW
    val isFmvWXOp = microCode === uopFMVWX
    val isFclassSOp = microCode === uopFCLASSS
    val isFsgnjSOp = microCode === uopFSGNJS
    val isFsgnjnSOp = microCode === uopFSGNJNS
    val isFsgnjxSOp = microCode === uopFSGNJXS
    val isFminSOp = microCode === uopFMINS
    val isFmaxSOp = microCode === uopFMAXS
    val isFleSOp = microCode === uopFLES
    val isFltSOp = microCode === uopFLTS
    val isFeqSOp = microCode === uopFEQS

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

    when(aguFire && isFswOp) {
      lsu.logic.rawStoreData.allowOverride := fpRs2Data(31 downto 0).resize(64)
    }

    val flwWritebackFire = aguFire && isFlwOp && lsu.logic.responseArriving && !lsu.logic.suppress
    when(flwWritebackFire) {
      fpWrite.valid := True
      fpWrite.address := flwRd
      fpWrite.data := B(BigInt("FFFFFFFF", 16), 32 bits) ## lsu.logic.shiftedLoadData(31 downto 0)
      suppressIntWriteback := True
      down(WriteBack.RESULT).address.allowOverride := 0
      down(WriteBack.RESULT).data.allowOverride := 0
      down(WriteBack.RESULT).valid.allowOverride := False
    }

    val fcvtFToIntFire = aluFire && (isFcvtWsOp || isFcvtWuSOp || isFcvtLsOp || isFcvtLuSOp)
    val fcvtSToFpFire = aluFire && (isFcvtSwOp || isFcvtSwuOp || isFcvtSlOp || isFcvtSluOp)
    val fmvXWFire = aluFire && isFmvXWOp
    val fmvWXFire = aluFire && isFmvWXOp
    val faddsubSFire = aluFire && (isFaddSOp || isFsubSOp)
    val fmulSFire = aluFire && isFmulSOp
    val fdivSFire = aluFire && isFdivSOp
    val fsqrtSFire = aluFire && isFsqrtSOp
    val fmaSFire = aluFire && (isFmaddSOp || isFmsubSOp || isFnmsubSOp || isFnmaddSOp)
    val fclassSFire = aluFire && isFclassSOp
    val fsgnjSFire = aluFire && (isFsgnjSOp || isFsgnjnSOp || isFsgnjxSOp)
    val fminmaxSFire = aluFire && (isFminSOp || isFmaxSOp)
    val fcmpSFire = aluFire && (isFleSOp || isFltSOp || isFeqSOp)

    val fcvtRmRaw = Mux(insn(14 downto 12) === B"111", frm, insn(14 downto 12))
    val fcvtRm = Bits(3 bits)
    fcvtRm := fcvtRmRaw
    when(fcvtRmRaw === B"101" || fcvtRmRaw === B"110" || fcvtRmRaw === B"111") {
      fcvtRm := B"001"
    }

    val fcvtLSrc = fpRs1Data(31 downto 0)
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

    when(fmvXWFire) {
      val moved = fpRs1Data(31 downto 0).asSInt.resize(64).asBits
      requestIntResult(up(Decoder.RD_ADDR).asUInt, moved)
    }

    when(faddsubSFire) {
      val addSub = FpuAddSub.addSubS(fpRs1Data(31 downto 0), fpRs2Data(31 downto 0), fcvtRm, isFsubSOp)
      fpWrite.valid := True
      fpWrite.address := up(Decoder.RD_ADDR).asUInt
      fpWrite.data := B(BigInt("FFFFFFFF", 16), 32 bits) ## addSub.data
      requestFlags(addSub.flags)
    }

    when(fmulSFire) {
      val mul = FpuMul.mulS(fpRs1Data(31 downto 0), fpRs2Data(31 downto 0), fcvtRm)
      fpWrite.valid := True
      fpWrite.address := up(Decoder.RD_ADDR).asUInt
      fpWrite.data := B(BigInt("FFFFFFFF", 16), 32 bits) ## mul.data
      requestFlags(mul.flags)
    }

    when(fdivSFire) {
      val div = FpuDivSqrt.divS(fpRs1Data(31 downto 0), fpRs2Data(31 downto 0), fcvtRm)
      fpWrite.valid := True
      fpWrite.address := up(Decoder.RD_ADDR).asUInt
      fpWrite.data := B(BigInt("FFFFFFFF", 16), 32 bits) ## div.data
      requestFlags(div.flags)
    }

    when(fsqrtSFire) {
      val sqrt = FpuDivSqrt.sqrtS(fpRs1Data(31 downto 0), fcvtRm)
      fpWrite.valid := True
      fpWrite.address := up(Decoder.RD_ADDR).asUInt
      fpWrite.data := B(BigInt("FFFFFFFF", 16), 32 bits) ## sqrt.data
      requestFlags(sqrt.flags)
    }

    when(fmaSFire) {
      val fma = FpuFma.fmaS(
        fpRs1Data(31 downto 0),
        fpRs2Data(31 downto 0),
        fpRs3Data(31 downto 0),
        fcvtRm,
        isFmsubSOp,
        isFnmsubSOp,
        isFnmaddSOp
      )
      fpWrite.valid := True
      fpWrite.address := up(Decoder.RD_ADDR).asUInt
      fpWrite.data := B(BigInt("FFFFFFFF", 16), 32 bits) ## fma.data
      requestFlags(fma.flags)
    }

    when(fmvWXFire) {
      fpWrite.valid := True
      fpWrite.address := fcvtSRd
      fpWrite.data := B(BigInt("FFFFFFFF", 16), 32 bits) ## up(SrcPlugin.RS1)(31 downto 0)
    }

    when(fclassSFire) {
      val cls = FpuScalarMisc.classifyS(fpRs1Data(31 downto 0))
      requestIntResult(up(Decoder.RD_ADDR).asUInt, B(0, 54 bits) ## cls)
    }

    when(fsgnjSFire) {
      val out = FpuScalarMisc.signInjectS(fpRs1Data(31 downto 0), fpRs2Data(31 downto 0), isFsgnjnSOp, isFsgnjxSOp)
      fpWrite.valid := True
      fpWrite.address := up(Decoder.RD_ADDR).asUInt
      fpWrite.data := B(BigInt("FFFFFFFF", 16), 32 bits) ## out
    }

    when(fcmpSFire) {
      val cmp = FpuScalarMisc.compareS(fpRs1Data(31 downto 0), fpRs2Data(31 downto 0), isFeqSOp, isFltSOp, isFleSOp)
      requestFlags(cmp.flags)
      requestIntResult(up(Decoder.RD_ADDR).asUInt, cmp.result)
    }

    when(fminmaxSFire) {
      val minMax = FpuScalarMisc.minMaxS(fpRs1Data(31 downto 0), fpRs2Data(31 downto 0), isFminSOp)
      requestFlags(minMax.flags)
      fpWrite.valid := True
      fpWrite.address := up(Decoder.RD_ADDR).asUInt
      fpWrite.data := B(BigInt("FFFFFFFF", 16), 32 bits) ## minMax.data
    }
  }
}
