package borb.dispatch

import spinal.core._
import spinal.lib.misc.pipeline._
import borb.common.MicroCode
import borb.common.MicroCode._
import borb.frontend.Imm_Select

case class IssuePropertyBundle() extends Bundle {
  val readsIntRs1 = Bool()
  val readsIntRs2 = Bool()
  val readsFpRs1 = Bool()
  val readsFpRs2 = Bool()
  val readsFpRs3 = Bool()
  val writesIntRd = Bool()
  val writesFpRd = Bool()
  val isLoad = Bool()
  val isStore = Bool()
  val isBranch = Bool()
  val isControlFlow = Bool()
  val isSerializing = Bool()
  val immSel = Imm_Select()
  val fuMask = Bits(3 bits)
}

object IssueSemantics extends AreaObject {
  val PROPS = Payload(IssuePropertyBundle())

  private def oneOf(value: MicroCode.C, options: MicroCode.E*): Bool = {
    if(options.isEmpty) False else options.map(value === _).reduce(_ || _)
  }

  def classify(microCode: MicroCode.C): IssuePropertyBundle = {
    val props = IssuePropertyBundle()
    props.readsIntRs1 := False
    props.readsIntRs2 := False
    props.readsFpRs1 := False
    props.readsFpRs2 := False
    props.readsFpRs3 := False
    props.writesIntRd := False
    props.writesFpRd := False
    props.isLoad := False
    props.isStore := False
    props.isBranch := False
    props.isControlFlow := False
    props.isSerializing := False
    props.immSel := Imm_Select.N_IMM
    props.fuMask := B"000"

    props.readsIntRs1 := oneOf(
      microCode,
      uopADDI, uopSLTI, uopSLTIU, uopXORI, uopORI, uopANDI, uopSLLI, uopSRLI, uopSRAI,
      uopADDIW, uopSLLIW, uopSRLIW, uopSRAIW,
      uopLB, uopLH, uopLW, uopLBU, uopLHU, uopLWU, uopLD, uopFLW,
      uopSB, uopSH, uopSW, uopSD, uopFSW,
      uopADD, uopSUB, uopSLL, uopSLT, uopSLTU, uopXOR, uopSRL, uopSRA, uopOR, uopAND,
      uopADDW, uopSUBW, uopSLLW, uopSRLW, uopSRAW,
      uopMUL, uopMULH, uopMULHSU, uopMULHU, uopDIV, uopDIVU, uopREM, uopREMU,
      uopMULW, uopDIVW, uopDIVUW, uopREMW, uopREMUW,
      uopAMOSWAPW, uopAMOSWAPD, uopAMOADDW, uopAMOADDD, uopAMOXORW, uopAMOXORD,
      uopAMOANDW, uopAMOANDD, uopAMOORW, uopAMOORD, uopAMOMINW, uopAMOMIND,
      uopAMOMAXW, uopAMOMAXD, uopAMOMINUW, uopAMOMINUD, uopAMOMAXUW, uopAMOMAXUD,
      uopJALR, uopBEQ, uopBNE, uopBLT, uopBGE, uopBLTU, uopBGEU,
      uopCSRRW, uopCSRRS, uopCSRRC, uopSFENCEVMA,
      uopFCVTSW, uopFCVTSWU, uopFCVTSL, uopFCVTSLU, uopFMVWX
    )
    props.readsIntRs2 := oneOf(
      microCode,
      uopSB, uopSH, uopSW, uopSD,
      uopADD, uopSUB, uopSLL, uopSLT, uopSLTU, uopXOR, uopSRL, uopSRA, uopOR, uopAND,
      uopADDW, uopSUBW, uopSLLW, uopSRLW, uopSRAW,
      uopMUL, uopMULH, uopMULHSU, uopMULHU, uopDIV, uopDIVU, uopREM, uopREMU,
      uopMULW, uopDIVW, uopDIVUW, uopREMW, uopREMUW,
      uopAMOSWAPW, uopAMOSWAPD, uopAMOADDW, uopAMOADDD, uopAMOXORW, uopAMOXORD,
      uopAMOANDW, uopAMOANDD, uopAMOORW, uopAMOORD, uopAMOMINW, uopAMOMIND,
      uopAMOMAXW, uopAMOMAXD, uopAMOMINUW, uopAMOMINUD, uopAMOMAXUW, uopAMOMAXUD,
      uopBEQ, uopBNE, uopBLT, uopBGE, uopBLTU, uopBGEU, uopSFENCEVMA
    )
    props.readsFpRs1 := oneOf(
      microCode,
      uopFCVTLS, uopFCVTLUS, uopFCVTWS, uopFCVTWUS, uopFMVXW, uopFCLASSS,
      uopFSGNJS, uopFSGNJNS, uopFSGNJXS, uopFMINS, uopFMAXS, uopFLES, uopFLTS, uopFEQS,
      uopFADDS, uopFSUBS, uopFMULS, uopFDIVS, uopFSQRTS,
      uopFMADDS, uopFMSUBS, uopFNMSUBS, uopFNMADDS
    )
    props.readsFpRs2 := oneOf(
      microCode,
      uopFSW, uopFSGNJS, uopFSGNJNS, uopFSGNJXS, uopFMINS, uopFMAXS, uopFLES, uopFLTS, uopFEQS,
      uopFADDS, uopFSUBS, uopFMULS, uopFDIVS,
      uopFMADDS, uopFMSUBS, uopFNMSUBS, uopFNMADDS
    )
    props.readsFpRs3 := oneOf(microCode, uopFMADDS, uopFMSUBS, uopFNMSUBS, uopFNMADDS)
    props.writesIntRd := oneOf(
      microCode,
      uopLUI, uopAUIPC, uopJAL, uopJALR,
      uopLB, uopLH, uopLW, uopLBU, uopLHU, uopLWU, uopLD,
      uopADDI, uopSLTI, uopSLTIU, uopXORI, uopORI, uopANDI, uopSLLI, uopSRLI, uopSRAI,
      uopADD, uopSUB, uopSLL, uopSLT, uopSLTU, uopXOR, uopSRL, uopSRA, uopOR, uopAND,
      uopADDIW, uopSLLIW, uopSRLIW, uopSRAIW, uopADDW, uopSUBW, uopSLLW, uopSRLW, uopSRAW,
      uopMUL, uopMULH, uopMULHSU, uopMULHU, uopDIV, uopDIVU, uopREM, uopREMU,
      uopMULW, uopDIVW, uopDIVUW, uopREMW, uopREMUW,
      uopAMOSWAPW, uopAMOSWAPD, uopAMOADDW, uopAMOADDD, uopAMOXORW, uopAMOXORD,
      uopAMOANDW, uopAMOANDD, uopAMOORW, uopAMOORD, uopAMOMINW, uopAMOMIND,
      uopAMOMAXW, uopAMOMAXD, uopAMOMINUW, uopAMOMINUD, uopAMOMAXUW, uopAMOMAXUD,
      uopCSRRW, uopCSRRS, uopCSRRC, uopCSRRWI, uopCSRRSI, uopCSRRCI,
      uopFCVTLS, uopFCVTLUS, uopFCVTWS, uopFCVTWUS, uopFMVXW, uopFCLASSS, uopFLES, uopFLTS, uopFEQS
    )
    props.writesFpRd := oneOf(
      microCode,
      uopFLW, uopFMADDS, uopFMSUBS, uopFNMSUBS, uopFNMADDS,
      uopFADDS, uopFSUBS, uopFMULS, uopFDIVS, uopFSQRTS,
      uopFSGNJS, uopFSGNJNS, uopFSGNJXS, uopFMINS, uopFMAXS,
      uopFCVTSW, uopFCVTSWU, uopFCVTSL, uopFCVTSLU, uopFMVWX
    )
    props.isLoad := oneOf(
      microCode,
      uopLB, uopLH, uopLW, uopLBU, uopLHU, uopLWU, uopLD, uopFLW,
      uopAMOSWAPW, uopAMOSWAPD, uopAMOADDW, uopAMOADDD, uopAMOXORW, uopAMOXORD,
      uopAMOANDW, uopAMOANDD, uopAMOORW, uopAMOORD, uopAMOMINW, uopAMOMIND,
      uopAMOMAXW, uopAMOMAXD, uopAMOMINUW, uopAMOMINUD, uopAMOMAXUW, uopAMOMAXUD
    )
    props.isStore := oneOf(
      microCode,
      uopSB, uopSH, uopSW, uopSD, uopFSW,
      uopAMOSWAPW, uopAMOSWAPD, uopAMOADDW, uopAMOADDD, uopAMOXORW, uopAMOXORD,
      uopAMOANDW, uopAMOANDD, uopAMOORW, uopAMOORD, uopAMOMINW, uopAMOMIND,
      uopAMOMAXW, uopAMOMAXD, uopAMOMINUW, uopAMOMINUD, uopAMOMAXUW, uopAMOMAXUD
    )
    props.isBranch := oneOf(microCode, uopJAL, uopJALR, uopBEQ, uopBNE, uopBLT, uopBGE, uopBLTU, uopBGEU)
    props.isControlFlow := props.isBranch
    props.isSerializing := oneOf(microCode, uopFENCE, uopFENCE_I, uopECALL, uopEBREAK, uopSRET, uopMRET, uopSFENCEVMA)

    switch(microCode) {
      is(uopADDI, uopSLTI, uopSLTIU, uopXORI, uopORI, uopANDI, uopSLLI, uopSRLI, uopSRAI,
         uopADDIW, uopSLLIW, uopSRLIW, uopSRAIW,
         uopLB, uopLH, uopLW, uopLBU, uopLHU, uopLWU, uopLD, uopFLW,
         uopJALR) {
        props.immSel := Imm_Select.I_IMM
      }
      is(uopSB, uopSH, uopSW, uopSD, uopFSW) {
        props.immSel := Imm_Select.S_IMM
      }
      is(uopBEQ, uopBNE, uopBLT, uopBGE, uopBLTU, uopBGEU) {
        props.immSel := Imm_Select.B_IMM
      }
      is(uopLUI, uopAUIPC) {
        props.immSel := Imm_Select.U_IMM
      }
      is(uopJAL) {
        props.immSel := Imm_Select.J_IMM
      }
    }

    when(props.isLoad || props.isStore) {
      props.fuMask(2) := True
    } elsewhen(props.isControlFlow) {
      props.fuMask(1) := True
    } otherwise {
      props.fuMask(0) := True
    }

    props
  }
}
