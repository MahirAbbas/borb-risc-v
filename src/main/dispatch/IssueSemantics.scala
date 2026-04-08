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
    val readsIntRs1 = oneOf(
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
    val readsIntRs2 = oneOf(
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
    val readsFpRs1 = oneOf(
      microCode,
      uopFCVTLS, uopFCVTLUS, uopFCVTWS, uopFCVTWUS, uopFMVXW, uopFCLASSS,
      uopFSGNJS, uopFSGNJNS, uopFSGNJXS, uopFMINS, uopFMAXS, uopFLES, uopFLTS, uopFEQS,
      uopFADDS, uopFSUBS, uopFMULS, uopFDIVS, uopFSQRTS,
      uopFMADDS, uopFMSUBS, uopFNMSUBS, uopFNMADDS
    )
    val readsFpRs2 = oneOf(
      microCode,
      uopFSW, uopFSGNJS, uopFSGNJNS, uopFSGNJXS, uopFMINS, uopFMAXS, uopFLES, uopFLTS, uopFEQS,
      uopFADDS, uopFSUBS, uopFMULS, uopFDIVS,
      uopFMADDS, uopFMSUBS, uopFNMSUBS, uopFNMADDS
    )
    val readsFpRs3 = oneOf(microCode, uopFMADDS, uopFMSUBS, uopFNMSUBS, uopFNMADDS)
    val writesIntRd = oneOf(
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
    val writesFpRd = oneOf(
      microCode,
      uopFLW, uopFMADDS, uopFMSUBS, uopFNMSUBS, uopFNMADDS,
      uopFADDS, uopFSUBS, uopFMULS, uopFDIVS, uopFSQRTS,
      uopFSGNJS, uopFSGNJNS, uopFSGNJXS, uopFMINS, uopFMAXS,
      uopFCVTSW, uopFCVTSWU, uopFCVTSL, uopFCVTSLU, uopFMVWX
    )
    val isLoad = oneOf(
      microCode,
      uopLB, uopLH, uopLW, uopLBU, uopLHU, uopLWU, uopLD, uopFLW,
      uopAMOSWAPW, uopAMOSWAPD, uopAMOADDW, uopAMOADDD, uopAMOXORW, uopAMOXORD,
      uopAMOANDW, uopAMOANDD, uopAMOORW, uopAMOORD, uopAMOMINW, uopAMOMIND,
      uopAMOMAXW, uopAMOMAXD, uopAMOMINUW, uopAMOMINUD, uopAMOMAXUW, uopAMOMAXUD
    )
    val isStore = oneOf(
      microCode,
      uopSB, uopSH, uopSW, uopSD, uopFSW,
      uopAMOSWAPW, uopAMOSWAPD, uopAMOADDW, uopAMOADDD, uopAMOXORW, uopAMOXORD,
      uopAMOANDW, uopAMOANDD, uopAMOORW, uopAMOORD, uopAMOMINW, uopAMOMIND,
      uopAMOMAXW, uopAMOMAXD, uopAMOMINUW, uopAMOMINUD, uopAMOMAXUW, uopAMOMAXUD
    )
    val isBranch = oneOf(microCode, uopJAL, uopJALR, uopBEQ, uopBNE, uopBLT, uopBGE, uopBLTU, uopBGEU)
    val isControlFlow = isBranch
    val isSerializing = oneOf(microCode, uopFENCE, uopFENCE_I, uopECALL, uopEBREAK, uopSRET, uopMRET, uopSFENCEVMA)
    val immSel = Imm_Select()
    immSel := Imm_Select.N_IMM

    switch(microCode) {
      is(uopADDI, uopSLTI, uopSLTIU, uopXORI, uopORI, uopANDI, uopSLLI, uopSRLI, uopSRAI,
         uopADDIW, uopSLLIW, uopSRLIW, uopSRAIW,
         uopLB, uopLH, uopLW, uopLBU, uopLHU, uopLWU, uopLD, uopFLW,
         uopJALR) {
        immSel := Imm_Select.I_IMM
      }
      is(uopSB, uopSH, uopSW, uopSD, uopFSW) {
        immSel := Imm_Select.S_IMM
      }
      is(uopBEQ, uopBNE, uopBLT, uopBGE, uopBLTU, uopBGEU) {
        immSel := Imm_Select.B_IMM
      }
      is(uopLUI, uopAUIPC) {
        immSel := Imm_Select.U_IMM
      }
      is(uopJAL) {
        immSel := Imm_Select.J_IMM
      }
    }

    val fuMask = Bits(3 bits)
    fuMask := B"001"
    when(isLoad || isStore) {
      fuMask := B"100"
    } elsewhen(isControlFlow) {
      fuMask := B"010"
    }

    props.readsIntRs1 := readsIntRs1
    props.readsIntRs2 := readsIntRs2
    props.readsFpRs1 := readsFpRs1
    props.readsFpRs2 := readsFpRs2
    props.readsFpRs3 := readsFpRs3
    props.writesIntRd := writesIntRd
    props.writesFpRd := writesFpRd
    props.isLoad := isLoad
    props.isStore := isStore
    props.isBranch := isBranch
    props.isControlFlow := isControlFlow
    props.isSerializing := isSerializing
    props.immSel := immSel
    props.fuMask := fuMask

    props
  }
}
