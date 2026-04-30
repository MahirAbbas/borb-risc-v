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
  val lane1Compatible = Bool()
  val pairBarrier = Bool()
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
      uopLB, uopLH, uopLW, uopLBU, uopLHU, uopLWU, uopLD, uopFLH, uopFLW, uopFLD,
      uopSB, uopSH, uopSW, uopSD, uopFSH, uopFSW, uopFSD, uopCBOZERO,
      uopADD, uopSUB, uopSLL, uopSLT, uopSLTU, uopXOR, uopSRL, uopSRA, uopOR, uopAND,
      uopADDW, uopSUBW, uopSLLW, uopSRLW, uopSRAW,
      uopSH1ADD, uopSH2ADD, uopSH3ADD, uopADD_UW, uopSH1ADD_UW, uopSH2ADD_UW, uopSH3ADD_UW, uopSLLI_UW,
      uopANDN, uopORN, uopXNOR, uopCLZ, uopCTZ, uopCPOP, uopCLZW, uopCTZW, uopCPOPW,
      uopMAX, uopMAXU, uopMIN, uopMINU, uopSEXTB, uopSEXTH, uopZEXTH,
      uopROL, uopROR, uopRORI, uopROLW, uopRORW, uopRORIW, uopORCB, uopREV8,
      uopBCLR, uopBCLRI, uopBEXT, uopBEXTI, uopBINV, uopBINVI, uopBSET, uopBSETI,
      uopCZERO_EQZ, uopCZERO_NEZ,
      uopMUL, uopMULH, uopMULHSU, uopMULHU, uopDIV, uopDIVU, uopREM, uopREMU,
      uopMULW, uopDIVW, uopDIVUW, uopREMW, uopREMUW,
      uopAMOSWAPW, uopAMOSWAPD, uopAMOADDW, uopAMOADDD, uopAMOXORW, uopAMOXORD,
      uopAMOANDW, uopAMOANDD, uopAMOORW, uopAMOORD, uopAMOMINW, uopAMOMIND,
      uopAMOMAXW, uopAMOMAXD, uopAMOMINUW, uopAMOMINUD, uopAMOMAXUW, uopAMOMAXUD,
      uopJALR, uopBEQ, uopBNE, uopBLT, uopBGE, uopBLTU, uopBGEU,
      uopCSRRW, uopCSRRS, uopCSRRC, uopSFENCEVMA,
      uopVSETVLI, uopVSETVL, uopVLE32, uopVSE32,
      uopFCVTSW, uopFCVTSWU, uopFCVTSL, uopFCVTSLU, uopFCVTDW, uopFCVTDWU, uopFCVTDL, uopFCVTDLU, uopFCVTHL, uopFCVTHLU, uopFMVWX, uopFMVDX
    )
    val readsIntRs2 = oneOf(
      microCode,
      uopSB, uopSH, uopSW, uopSD,
      uopADD, uopSUB, uopSLL, uopSLT, uopSLTU, uopXOR, uopSRL, uopSRA, uopOR, uopAND,
      uopADDW, uopSUBW, uopSLLW, uopSRLW, uopSRAW,
      uopSH1ADD, uopSH2ADD, uopSH3ADD, uopADD_UW, uopSH1ADD_UW, uopSH2ADD_UW, uopSH3ADD_UW,
      uopANDN, uopORN, uopXNOR, uopMAX, uopMAXU, uopMIN, uopMINU, uopZEXTH,
      uopROL, uopROR, uopROLW, uopRORW,
      uopBCLR, uopBEXT, uopBINV, uopBSET,
      uopCZERO_EQZ, uopCZERO_NEZ,
      uopMUL, uopMULH, uopMULHSU, uopMULHU, uopDIV, uopDIVU, uopREM, uopREMU,
      uopMULW, uopDIVW, uopDIVUW, uopREMW, uopREMUW,
      uopAMOSWAPW, uopAMOSWAPD, uopAMOADDW, uopAMOADDD, uopAMOXORW, uopAMOXORD,
      uopAMOANDW, uopAMOANDD, uopAMOORW, uopAMOORD, uopAMOMINW, uopAMOMIND,
      uopAMOMAXW, uopAMOMAXD, uopAMOMINUW, uopAMOMINUD, uopAMOMAXUW, uopAMOMAXUD,
      uopBEQ, uopBNE, uopBLT, uopBGE, uopBLTU, uopBGEU, uopSFENCEVMA,
      uopVSETVL
    )
    val readsFpRs1 = oneOf(
      microCode,
      uopFCVTLS, uopFCVTLUS, uopFCVTWS, uopFCVTWUS, uopFCVTLD, uopFCVTLUD, uopFCVTWD, uopFCVTWUD, uopFCVTMODWD, uopFCVTLH, uopFCVTLUH, uopFCVTSD, uopFMVXW, uopFMVXD, uopFCLASSS, uopFCLASSD,
      uopFSGNJS, uopFSGNJNS, uopFSGNJXS, uopFSGNJD, uopFSGNJND, uopFSGNJXD, uopFMINS, uopFMAXS, uopFMINMS, uopFMAXMS, uopFMIND, uopFMAXD, uopFMINMD, uopFMAXMD,
      uopFLES, uopFLTS, uopFEQS, uopFLEQS, uopFLTQS, uopFLED, uopFLTD, uopFEQD, uopFLEQD, uopFLTQD,
      uopFROUNDS, uopFROUNDNXS, uopFROUNDD, uopFROUNDNXD,
      uopFADDS, uopFSUBS, uopFMULS, uopFDIVS, uopFSQRTS,
      uopFADDD, uopFSUBD, uopFMULD, uopFDIVD, uopFSQRTD,
      uopFMADDS, uopFMSUBS, uopFNMSUBS, uopFNMADDS,
      uopFMADDD, uopFMSUBD, uopFNMSUBD, uopFNMADDD
    )
    val readsFpRs2 = oneOf(
      microCode,
      uopFSH, uopFSW, uopFSD, uopFSGNJS, uopFSGNJNS, uopFSGNJXS, uopFSGNJD, uopFSGNJND, uopFSGNJXD,
      uopFMINS, uopFMAXS, uopFMINMS, uopFMAXMS, uopFMIND, uopFMAXD, uopFMINMD, uopFMAXMD,
      uopFLES, uopFLTS, uopFEQS, uopFLEQS, uopFLTQS, uopFLED, uopFLTD, uopFEQD, uopFLEQD, uopFLTQD,
      uopFADDS, uopFSUBS, uopFMULS, uopFDIVS,
      uopFADDD, uopFSUBD, uopFMULD, uopFDIVD,
      uopFMADDS, uopFMSUBS, uopFNMSUBS, uopFNMADDS,
      uopFMADDD, uopFMSUBD, uopFNMSUBD, uopFNMADDD
    )
    val readsFpRs3 = oneOf(microCode, uopFMADDS, uopFMSUBS, uopFNMSUBS, uopFNMADDS, uopFMADDD, uopFMSUBD, uopFNMSUBD, uopFNMADDD)
    val writesIntRd = oneOf(
      microCode,
      uopLUI, uopAUIPC, uopJAL, uopJALR,
      uopLB, uopLH, uopLW, uopLBU, uopLHU, uopLWU, uopLD,
      uopADDI, uopSLTI, uopSLTIU, uopXORI, uopORI, uopANDI, uopSLLI, uopSRLI, uopSRAI,
      uopADD, uopSUB, uopSLL, uopSLT, uopSLTU, uopXOR, uopSRL, uopSRA, uopOR, uopAND,
      uopADDIW, uopSLLIW, uopSRLIW, uopSRAIW, uopADDW, uopSUBW, uopSLLW, uopSRLW, uopSRAW,
      uopSH1ADD, uopSH2ADD, uopSH3ADD, uopADD_UW, uopSH1ADD_UW, uopSH2ADD_UW, uopSH3ADD_UW, uopSLLI_UW,
      uopANDN, uopORN, uopXNOR, uopCLZ, uopCTZ, uopCPOP, uopCLZW, uopCTZW, uopCPOPW,
      uopMAX, uopMAXU, uopMIN, uopMINU, uopSEXTB, uopSEXTH, uopZEXTH,
      uopROL, uopROR, uopRORI, uopROLW, uopRORW, uopRORIW, uopORCB, uopREV8,
      uopBCLR, uopBCLRI, uopBEXT, uopBEXTI, uopBINV, uopBINVI, uopBSET, uopBSETI,
      uopCZERO_EQZ, uopCZERO_NEZ,
      uopMOP_R, uopMOP_RR,
      uopMUL, uopMULH, uopMULHSU, uopMULHU, uopDIV, uopDIVU, uopREM, uopREMU,
      uopMULW, uopDIVW, uopDIVUW, uopREMW, uopREMUW,
      uopAMOSWAPW, uopAMOSWAPD, uopAMOADDW, uopAMOADDD, uopAMOXORW, uopAMOXORD,
      uopAMOANDW, uopAMOANDD, uopAMOORW, uopAMOORD, uopAMOMINW, uopAMOMIND,
      uopAMOMAXW, uopAMOMAXD, uopAMOMINUW, uopAMOMINUD, uopAMOMAXUW, uopAMOMAXUD,
      uopCSRRW, uopCSRRS, uopCSRRC, uopCSRRWI, uopCSRRSI, uopCSRRCI,
      uopVSETVLI, uopVSETIVLI, uopVSETVL, uopVMVXS,
      uopFCVTLS, uopFCVTLUS, uopFCVTWS, uopFCVTWUS, uopFCVTLD, uopFCVTLUD, uopFCVTWD, uopFCVTWUD, uopFCVTMODWD, uopFCVTLH, uopFCVTLUH,
      uopFMVXW, uopFMVXD, uopFCLASSS, uopFCLASSD, uopFLES, uopFLTS, uopFEQS, uopFLEQS, uopFLTQS, uopFLED, uopFLTD, uopFEQD, uopFLEQD, uopFLTQD
    )
    val writesFpRd = oneOf(
      microCode,
      uopFLH, uopFLW, uopFLD, uopFMADDS, uopFMSUBS, uopFNMSUBS, uopFNMADDS, uopFMADDD, uopFMSUBD, uopFNMSUBD, uopFNMADDD,
      uopFADDS, uopFSUBS, uopFMULS, uopFDIVS, uopFSQRTS,
      uopFADDD, uopFSUBD, uopFMULD, uopFDIVD, uopFSQRTD,
      uopFSGNJS, uopFSGNJNS, uopFSGNJXS, uopFSGNJD, uopFSGNJND, uopFSGNJXD,
      uopFMINS, uopFMAXS, uopFMINMS, uopFMAXMS, uopFMIND, uopFMAXD, uopFMINMD, uopFMAXMD,
      uopFCVTSW, uopFCVTSWU, uopFCVTSL, uopFCVTSLU, uopFCVTDW, uopFCVTDWU, uopFCVTDL, uopFCVTDLU, uopFCVTHL, uopFCVTHLU, uopFCVTDS, uopFMVWX, uopFMVDX,
      uopFLIS, uopFLID, uopFROUNDS, uopFROUNDNXS, uopFROUNDD, uopFROUNDNXD
    )
    val isLoad = oneOf(
      microCode,
      uopLB, uopLH, uopLW, uopLBU, uopLHU, uopLWU, uopLD, uopFLH, uopFLW, uopFLD,
      uopAMOSWAPW, uopAMOSWAPD, uopAMOADDW, uopAMOADDD, uopAMOXORW, uopAMOXORD,
      uopAMOANDW, uopAMOANDD, uopAMOORW, uopAMOORD, uopAMOMINW, uopAMOMIND,
      uopAMOMAXW, uopAMOMAXD, uopAMOMINUW, uopAMOMINUD, uopAMOMAXUW, uopAMOMAXUD
    )
    val isStore = oneOf(
      microCode,
      uopSB, uopSH, uopSW, uopSD, uopFSH, uopFSW, uopFSD, uopCBOZERO,
      uopAMOSWAPW, uopAMOSWAPD, uopAMOADDW, uopAMOADDD, uopAMOXORW, uopAMOXORD,
      uopAMOANDW, uopAMOANDD, uopAMOORW, uopAMOORD, uopAMOMINW, uopAMOMIND,
      uopAMOMAXW, uopAMOMAXD, uopAMOMINUW, uopAMOMINUD, uopAMOMAXUW, uopAMOMAXUD
    )
    val isBranch = oneOf(microCode, uopJAL, uopJALR, uopBEQ, uopBNE, uopBLT, uopBGE, uopBLTU, uopBGEU)
    val isControlFlow = isBranch
    val isSerializing = oneOf(microCode, uopFENCE, uopFENCE_I, uopCBOZERO, uopECALL, uopEBREAK, uopSRET, uopMRET, uopSFENCEVMA, uopVSETVLI, uopVSETIVLI, uopVSETVL, uopVADDVV, uopVADDVI, uopVMVVI, uopVMVXS, uopVLE32, uopVSE32, uopVSLIDEUPVI, uopVSLIDEDOWNVI, uopVRGATHERVI, uopVREDSUMVS, uopVFADDVV, uopVFSUBVV, uopVFWCVTFFV, uopVFNCVTFFW, uopVANDNVV, uopVBREV8V, uopVREV8V, uopVCLZV, uopVCPOPV, uopVRORVI)
    val lane1Compatible =
      !isSerializing &&
      !isLoad &&
      !isStore &&
      !readsFpRs1 &&
      !readsFpRs2 &&
      !readsFpRs3 &&
      !writesFpRd
    val pairBarrier = isSerializing
    val immSel = Imm_Select()
    immSel := Imm_Select.N_IMM

    switch(microCode) {
      is(uopADDI, uopSLTI, uopSLTIU, uopXORI, uopORI, uopANDI, uopSLLI, uopSRLI, uopSRAI,
         uopADDIW, uopSLLIW, uopSRLIW, uopSRAIW,
         uopSLLI_UW, uopCLZ, uopCTZ, uopCPOP, uopCLZW, uopCTZW, uopCPOPW,
         uopSEXTB, uopSEXTH, uopRORI, uopRORIW, uopORCB, uopREV8,
         uopBCLRI, uopBEXTI, uopBINVI, uopBSETI,
         uopLB, uopLH, uopLW, uopLBU, uopLHU, uopLWU, uopLD, uopFLH, uopFLW, uopFLD,
         uopJALR) {
        immSel := Imm_Select.I_IMM
      }
      is(uopSB, uopSH, uopSW, uopSD, uopFSH, uopFSW, uopFSD) {
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
    props.lane1Compatible := lane1Compatible
    props.pairBarrier := pairBarrier
    props.immSel := immSel
    props.fuMask := fuMask

    props
  }
}
