package borb.frontend

import spinal.core._

object RV32I {

  val ADD = M"0000000----------000-----0110011"
  val SUB = M"0100000----------000-----0110011"
  val SLL = M"0000000----------001-----0110011"
  val SLT = M"0000000----------010-----0110011"
  val SLTU = M"0000000----------011-----0110011"
  val XOR = M"0000000----------100-----0110011"
  val SRL = M"0000000----------101-----0110011"
  val SRA = M"0100000----------101-----0110011"
  val OR = M"0000000----------110-----0110011"
  val AND = M"0000000----------111-----0110011"

  val ADDI = M"-----------------000-----0010011"
  val SLLI = M"000000-----------001-----0010011"
  val SLTI = M"-----------------010-----0010011"
  val SLTIU = M"-----------------011-----0010011"
  val XORI = M"-----------------100-----0010011"
  val SRLI = M"000000-----------101-----0010011"
  val SRAI = M"010000-----------101-----0010011"
  val ORI = M"-----------------110-----0010011"
  val ANDI = M"-----------------111-----0010011"

  val LUI = M"-------------------------0110111"
  val AUIPC = M"-------------------------0010111"

  val BEQ = M"-----------------000-----1100011"
  val BNE = M"-----------------001-----1100011"
  val BLT = M"-----------------100-----1100011"
  val BGE = M"-----------------101-----1100011"
  val BLTU = M"-----------------110-----1100011"
  val BGEU = M"-----------------111-----1100011"
  val JALR = M"-----------------000-----1100111"
  val JAL = M"-------------------------1101111"

  val LB = M"-----------------000-----0000011"
  val LH = M"-----------------001-----0000011"
  val LW = M"-----------------010-----0000011"
  val LBU = M"-----------------100-----0000011"
  val LHU = M"-----------------101-----0000011"

  val SB = M"-----------------000-----0100011"
  val SH = M"-----------------001-----0100011"
  val SW = M"-----------------010-----0100011"

  val FENCE = M"-----------------000-----0001111"
  val FENCEI = M"00000000000000000001000000001111"
  val CBO_INVAL = M"000000000000-----010000000001111"
  val CBO_CLEAN = M"000000000001-----010000000001111"
  val CBO_FLUSH = M"000000000010-----010000000001111"
  val CBO_ZERO = M"000000000100-----010000000001111"
  val ECALL = M"00000000000000000000000001110011"
  val EBREAK = M"00000000000100000000000001110011"
  val SRET = M"00010000001000000000000001110011"
  val MRET = M"00110000001000000000000001110011"
  val SFENCE_VMA = M"0001001----------000000001110011"
  val SINVAL_VMA = M"0001011----------000000001110011"
  val SFENCE_W_INVAL = M"00011000000000000000000001110011"
  val SFENCE_INVAL_IR = M"00011000000100000000000001110011"
  val WRS_NTO = M"00000000110100000000000001110011"
  val WRS_STO = M"00000001110100000000000001110011"

  val CSRRW = M"-----------------001-----1110011"
  val CSRRS = M"-----------------010-----1110011"
  val CSRRC = M"-----------------011-----1110011"
  val CSRRWI = M"-----------------101-----1110011"
  val CSRRSI = M"-----------------110-----1110011"
  val CSRRCI = M"-----------------111-----1110011"

}

object RV64I {
  val LWU = M"-----------------110-----0000011"
  val LD = M"-----------------011-----0000011"
  val SD = M"-----------------011-----0100011"

  // val SLLI               = M""
  // val SRLI               = M""
  // val SRAI               = M""

  val ADDIW = M"-----------------000-----0011011"
  val SLLIW = M"000000-----------001-----0011011"
  val SRLIW = M"000000-----------101-----0011011"
  val SRAIW = M"010000-----------101-----0011011"

  val ADDW = M"0000000----------000-----0111011"
  val SUBW = M"0100000----------000-----0111011"

  val SLLW = M"0000000----------001-----0111011"
  val SRLW = M"0000000----------101-----0111011"
  val SRAW = M"0100000----------101-----0111011"
}

object RV32M {
  val MUL = M"0000001----------000-----0110011"
  val MULH = M"0000001----------001-----0110011"
  val MULHSU = M"0000001----------010-----0110011"
  val MULHU = M"0000001----------011-----0110011"
  val DIV = M"0000001----------100-----0110011"
  val DIVU = M"0000001----------101-----0110011"
  val REM = M"0000001----------110-----0110011"
  val REMU = M"0000001----------111-----0110011"
}
object RV64M {
  val MULW = M"0000001----------000-----0111011"
  val DIVW = M"0000001----------100-----0111011"
  val DIVUW = M"0000001----------101-----0111011"
  val REMW = M"0000001----------110-----0111011"
  val REMUW = M"0000001----------111-----0111011"
}

object RV64B {
  val SH1ADD = M"0010000----------010-----0110011"
  val SH2ADD = M"0010000----------100-----0110011"
  val SH3ADD = M"0010000----------110-----0110011"
  val ADD_UW = M"0000100----------000-----0111011"
  val SH1ADD_UW = M"0010000----------010-----0111011"
  val SH2ADD_UW = M"0010000----------100-----0111011"
  val SH3ADD_UW = M"0010000----------110-----0111011"
  val SLLI_UW = M"000010-----------001-----0011011"

  val ANDN = M"0100000----------111-----0110011"
  val ORN = M"0100000----------110-----0110011"
  val XNOR = M"0100000----------100-----0110011"
  val CLZ = M"011000000000-----001-----0010011"
  val CTZ = M"011000000001-----001-----0010011"
  val CPOP = M"011000000010-----001-----0010011"
  val CLZW = M"011000000000-----001-----0011011"
  val CTZW = M"011000000001-----001-----0011011"
  val CPOPW = M"011000000010-----001-----0011011"
  val MAX = M"0000101----------110-----0110011"
  val MAXU = M"0000101----------111-----0110011"
  val MIN = M"0000101----------100-----0110011"
  val MINU = M"0000101----------101-----0110011"
  val SEXTB = M"011000000100-----001-----0010011"
  val SEXTH = M"011000000101-----001-----0010011"
  val ZEXTH = M"000010000000-----100-----0111011"
  val ROL = M"0110000----------001-----0110011"
  val ROR = M"0110000----------101-----0110011"
  val RORI = M"011000-----------101-----0010011"
  val ROLW = M"0110000----------001-----0111011"
  val RORW = M"0110000----------101-----0111011"
  val RORIW = M"0110000----------101-----0011011"
  val ORCB = M"001010000111-----101-----0010011"
  val REV8 = M"011010111000-----101-----0010011"

  val BCLR = M"0100100----------001-----0110011"
  val BCLRI = M"010010-----------001-----0010011"
  val BEXT = M"0100100----------101-----0110011"
  val BEXTI = M"010010-----------101-----0010011"
  val BINV = M"0110100----------001-----0110011"
  val BINVI = M"011010-----------001-----0010011"
  val BSET = M"0010100----------001-----0110011"
  val BSETI = M"001010-----------001-----0010011"
}

object RVZicond {
  val CZERO_EQZ = M"0000111----------101-----0110011"
  val CZERO_NEZ = M"0000111----------111-----0110011"
}

object RVZimop {
  val MOP_R = M"1-00--0111-------100-----1110011"
  val MOP_RR = M"1-00--1----------100-----1110011"
}

object RVV {
  val VSETVLI = M"0----------------111-----1010111"
  val VSETIVLI = M"11---------------111-----1010111"
  val VSETVL = M"1000000----------111-----1010111"
  val VADDVV = M"000000-----------000-----1010111"
  val VADDVI = M"000000-----------011-----1010111"
  val VMVVI = M"010111100000-----011-----1010111"
  val VMVXS = M"0100001-----00000010-----1010111"
  val VLE32 = M"0000001----------110-----0000111"
  val VSE32 = M"0000001----------110-----0100111"
  val VSLIDEUPVI = M"001110-----------011-----1010111"
  val VSLIDEDOWNVI = M"001111-----------011-----1010111"
  val VRGATHERVI = M"001100-----------011-----1010111"
  val VREDSUMVS = M"000000-----------010-----1010111"
  val VFADDVV = M"000000-----------001-----1010111"
  val VFSUBVV = M"000010-----------001-----1010111"
  val VFWCVTFFV = M"0100101-----01100001-----1010111"
  val VFNCVTFFW = M"0100101-----10100001-----1010111"
  val VANDNVV = M"000001-----------000-----1010111"
  val VBREV8V = M"0100101-----01000010-----1010111"
  val VREV8V = M"0100101-----01001010-----1010111"
  val VCLZV = M"0100101-----01100010-----1010111"
  val VCPOPV = M"0100101-----01110010-----1010111"
  val VRORVI = M"010100-----------011-----1010111"
}

object RV32A {
  val LRW = M"00010--00000-----010-----0101111"
  val SCW = M"00011------------010-----0101111"
  val AMOSWAPW = M"00001------------010-----0101111"
  val AMOADDW = M"00000------------010-----0101111"
  val AMOXORW = M"00100------------010-----0101111"
  val AMOANDW = M"01100------------010-----0101111"
  val AMOORW = M"01000------------010-----0101111"
  val AMOMINW = M"10000------------010-----0101111"
  val AMOMAXW = M"10100------------010-----0101111"
  val AMOMINUW = M"11000------------010-----0101111"
  val AMOMAXUW = M"11100------------010-----0101111"
}
object RV64A {
  val LRD = M"00010--00000-----011-----0101111"
  val SCD = M"00011------------011-----0101111"
  val AMOSWAPD = M"00001------------011-----0101111"
  val AMOADDD = M"00000------------011-----0101111"
  val AMOXORD = M"00100------------011-----0101111"
  val AMOANDD = M"01100------------011-----0101111"
  val AMOORD = M"01000------------011-----0101111"
  val AMOMIND = M"10000------------011-----0101111"
  val AMOMAXD = M"10100------------011-----0101111"
  val AMOMINUD = M"11000------------011-----0101111"
  val AMOMAXUD = M"11100------------011-----0101111"

}
object RV32F {
  val FLW = M"-----------------010-----0000111"
  val FSW = M"-----------------010-----0100111"
  val FMADDS = M"-----00------------------1000011"
  val FMSUBS = M"-----00------------------1000111"
  val FNMSUBS = M"-----00------------------1001011"
  val FNMADDS = M"-----00------------------1001111"
  val FADDS = M"0000000------------------1010011"
  val FSUBS = M"0000100------------------1010011"
  val FMULS = M"0001000------------------1010011"
  val FDIVS = M"0001100------------------1010011"
  val FSQRTS = M"010110000000-------------1010011"
  val FCVTWS = M"110000000000-------------1010011"
  val FCVTWUS = M"110000000001-------------1010011"
  val FCVTSW = M"110100000000-------------1010011"
  val FCVTSWU = M"110100000001-------------1010011"
  val FMVXW = M"111000000000-----000-----1010011"
  val FMVWX = M"111100000000-----000-----1010011"
  val FCLASSS = M"111000000000-----001-----1010011"
  val FSGNJS = M"0010000----------000-----1010011"
  val FSGNJNS = M"0010000----------001-----1010011"
  val FSGNJXS = M"0010000----------010-----1010011"
  val FMINS = M"0010100----------000-----1010011"
  val FMAXS = M"0010100----------001-----1010011"
  val FLES = M"1010000----------000-----1010011"
  val FLTS = M"1010000----------001-----1010011"
  val FEQS = M"1010000----------010-----1010011"
}

object RVZfh {
  val FLH = M"-----------------001-----0000111"
  val FSH = M"-----------------001-----0100111"
}

object RV64F {
  val FCVTLS = M"110000000010-------------1010011"
  val FCVTLUS = M"110000000011-------------1010011"
  val FCVTSL = M"110100000010-------------1010011"
  val FCVTSLU = M"110100000011-------------1010011"
}

object RV32D {
  val FLD = M"-----------------011-----0000111"
  val FSD = M"-----------------011-----0100111"
  val FMADDD = M"-----01------------------1000011"
  val FMSUBD = M"-----01------------------1000111"
  val FNMSUBD = M"-----01------------------1001011"
  val FNMADDD = M"-----01------------------1001111"
  val FADDD = M"0000001------------------1010011"
  val FSUBD = M"0000101------------------1010011"
  val FMULD = M"0001001------------------1010011"
  val FDIVD = M"0001101------------------1010011"
  val FSQRTD = M"010110100000-------------1010011"
  val FCVTSD = M"010000000001-------------1010011"
  val FCVTDS = M"010000100000-------------1010011"
  val FCVTWD = M"110000100000-------------1010011"
  val FCVTWUD = M"110000100001-------------1010011"
  val FCVTDW = M"110100100000-------------1010011"
  val FCVTDWU = M"110100100001-------------1010011"
  val FSGNJD = M"0010001----------000-----1010011"
  val FSGNJND = M"0010001----------001-----1010011"
  val FSGNJXD = M"0010001----------010-----1010011"
  val FMIND = M"0010101----------000-----1010011"
  val FMAXD = M"0010101----------001-----1010011"
  val FCLASSD = M"111000100000-----001-----1010011"
  val FLED = M"1010001----------000-----1010011"
  val FLTD = M"1010001----------001-----1010011"
  val FEQD = M"1010001----------010-----1010011"
}
object RV64D {
  val FCVTLD = M"110000100010-------------1010011"
  val FCVTLUD = M"110000100011-------------1010011"
  val FCVTDL = M"110100100010-------------1010011"
  val FCVTDLU = M"110100100011-------------1010011"
  val FMVXD = M"111000100000-----000-----1010011"
  val FMVDX = M"111100100000-----000-----1010011"
}

object RVZfa {
  val FLIS = M"111100000001-----000-----1010011"
  val FLID = M"111100100001-----000-----1010011"
  val FMINMS = M"0010100----------010-----1010011"
  val FMAXMS = M"0010100----------011-----1010011"
  val FMINMD = M"0010101----------010-----1010011"
  val FMAXMD = M"0010101----------011-----1010011"
  val FLEQS = M"1010000----------100-----1010011"
  val FLTQS = M"1010000----------101-----1010011"
  val FLEQD = M"1010001----------100-----1010011"
  val FLTQD = M"1010001----------101-----1010011"
  val FROUNDS = M"010000000100-------------1010011"
  val FROUNDNXS = M"010000000101-------------1010011"
  val FROUNDD = M"010000100100-------------1010011"
  val FROUNDNXD = M"010000100101-------------1010011"
  val FCVTMODWD = M"110000101000-------------1010011"
}

object RV64Zfh {
  val FCVTLH = M"110001000010-------------1010011"
  val FCVTLUH = M"110001000011-------------1010011"
  val FCVTHL = M"110101000010-------------1010011"
  val FCVTHLU = M"110101000011-------------1010011"
}

object DecodeTable {
  import RV32I._
  import RV64I._
  import RV32M._
  import RV64M._
  import RV64B._
  import RVZicond._
  import RVZimop._
  import RVV._
  import RV32A._
  import RV64A._
  import RV32F._
  import RV64F._
  import RV32D._
  import RV64D._
  import RVZfh._
  import RVZfa._
  import RV64Zfh._
  import REGFILE._
  import Imm_Select._
  import YESNO._
  import borb.common.MicroCode._

  //                                                                                  frs3_en
  //         valid?                                                                   |    imm sel         is_br
  //            | is fp inst?                                                         |    |      micro-code |    uses_ldq
  //            | |                                     rs1 regtype                   |    |        |        | is_w|  uses_stq        is unique? (clear pipeline for it)
  //            | |                                          |           rs2 type     |    |        |        |  |  |  |  is_amo       |  flush on commit
  //            | |       func unit                          |               |        |    |        |        |  |  |  |  |            |  |  csr cmd
  //            | |       |                                  |               |        |    |        |        |  |  |  |  |            |  |  |      fcn_dw                      swap12         fma
  //            | |       |                  dst             |               |        |    |        |        |  |  |  |  |  mem       |  |  |      |       fcn_op              | swap32       | div
  //            | |       |                  regtype         |               |        |    |        |        |  |  |  |  |  cmd       |  |  |      |       |                   | | typeTagIn  | | sqrt
  //            | |       |                  |               |               |        |    |        |        |  |  |  |  |  |         |  |  |      |       |        ldst       | | | typeTagOut | | wflags
  //            | |       |                  |               |               |        |    |        |        |  |  |  |  |  |         |  |  |      |       |        | wen      | | | | from_int | | |
  //            | |       |                  |               |               |        |    |        |        |  |  |  |  |  |         |  |  |      |       |        | | ren1   | | | | | to_int | | |
  //            | |       |                  |               |               |        |    |        |        |  |  |  |  |  |         |  |  |      |       |        | | | ren2 | | | | | | fast | | |
  //            | |       |                  |               |               |        |    |        |        |  |  |  |  |  |         |  |  |      |       |        | | | | ren3 | | | | | |  | | | |
  //            | |       |                  |               |               |        |    |        |        |  |  |  |  |  |         |  |  |      |       |        | | | | |  | | | | | | |  | | | |
  //       List(N,N, ExecutionUnit.FPU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT,  N, I_IMM, uopADD,   N, N  X, X, X, M_X,      N, X, CSR.X, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X)
  val nop = Seq( N, N, ExecutionUnitEnum.NA, RDTYPE.RD_NA, RSTYPE.RS_NA, RSTYPE.RS_NA, N, N_IMM, uopNOP, N, N, N, N)

  val X_table: Seq[(MaskedLiteral, Seq[Any])] = Seq(
    //                                                             frs3_en
    //               is val inst?                                  |  imm sel
    //               |  is fp inst?                                |  |     uses_ldq
    //               |  |                          rs1 regtype     |  |     |  uses_stq        is unique? (clear pipeline for it)
    //               |  |                          |       rs2 type|  |     |  |  is_amo       |  flush on commit
    //               |  |  func unit               |       |       |  |     |  |  |            |  |  csr cmd
    //               |  |  |                       |       |       |  |     |  |  |            |  |  |      fcn_dw                      swap12         fma
    //               |  |  |               dst     |       |       |  |     |  |  |  mem       |  |  |      |       fcn_op              | swap32       | div
    //               |  |  |               regtype |       |       |  |     |  |  |  cmd       |  |  |      |       |                   | | typeTagIn  | | sqrt
    //               |  |  |               |       |       |       |  |     |  |  |  |         |  |  |      |       |        ldst       | | | typeTagOut | | wflags
    //               |  |  |               |       |       |       |  |     |  |  |  |         |  |  |      |       |        | wen      | | | | from_int | | |
    //               |  |  |               |       |       |       |  |     |  |  |  |         |  |  |      |       |        | | ren1   | | | | | to_int | | |
    //               |  |  |               |       |       |       |  |     |  |  |  |         |  |  |      |       |        | | | ren2 | | | | | | fast | | |
    //               |  |  |               |       |       |       |  |     |  |  |  |         |  |  |      |       |        | | | | ren3 | | | | | |  | | | |
    //               |  |  |               |       |       |       |  |     |  |  |  |         |  |  |      |       |        | | | | |  | | | | | | |  | | | |
    //                    List(N, N, DC(FC_SZ)     , RT_X  , DC(2) , DC(2) , X, IS_N, X, X, X, M_X,      N, X, CSR.X, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X)

              //                                                                                  frs3_en
              //         valid?                                                                   |    imm sel         is_br
              //            | is fp inst?                                                         |    |      micro-code |    uses_ldq
              //            | |                                     rs1 regtype                   |    |        |        | is_w|  uses_stq        is unique? (clear pipeline for it)
              //            | |                                          |           rs2 type     |    |        |        |  |  |  |  is_amo       |  flush on commit
              //            | |       func unit                          |               |        |    |        |        |  |  |  |  |            |  |  csr cmd
              //            | |       |                                  |               |        |    |        |        |  |  |  |  |            |  |  |      fcn_dw                      swap12         fma
              //            | |       |                  dst             |               |        |    |        |        |  |  |  |  |  mem       |  |  |      |       fcn_op              | swap32       | div
              //            | |       |                  regtype         |               |        |    |        |        |  |  |  |  |  cmd       |  |  |      |       |                   | | typeTagIn  | | sqrt
              //            | |       |                  |               |               |        |    |        |        |  |  |  |  |  |         |  |  |      |       |        ldst       | | | typeTagOut | | wflags
              //            | |       |                  |               |               |        |    |        |        |  |  |  |  |  |         |  |  |      |       |        | wen      | | | | from_int | | |
              //            | |       |                  |               |               |        |    |        |        |  |  |  |  |  |         |  |  |      |       |        | | ren1   | | | | | to_int | | |
              //            | |       |                  |               |               |        |    |        |        |  |  |  |  |  |         |  |  |      |       |        | | | ren2 | | | | | | fast | | |
              //            | |       |                  |               |               |        |    |        |        |  |  |  |  |  |         |  |  |      |       |        | | | | ren3 | | | | | |  | | | |
              //            | |       |                  |               |               |        |    |        |        |  |  |  |  |  |         |  |  |      |       |        | | | | |  | | | | | | |  | | | |
    //                 List(N,N, ExecutionUnit.FPU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT,  N, I_IMM, uopADD,      N, N  X, X, X, M_X,      N, X, CSR.X, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X)

  SLLI            -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED,  N, I_IMM, uopSLLI, N, N, N, N),
  SRLI            -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED,  N, I_IMM, uopSRLI, N, N, N, N),
  SRAI            -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED,  N, I_IMM, uopSRAI, N, N, N, N),
  ADDIW           -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED,  N, I_IMM,uopADDIW, N, Y, N, N),
  SLLIW           -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED,  N, I_IMM,uopSLLIW, N, Y, N, N),
  SRAIW           -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED,  N, I_IMM,uopSRAIW, N, Y, N, N),
  SRLIW           -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED,  N, I_IMM,uopSRLIW, N, Y, N, N),
  ADDW            -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM, uopADDW, N, Y, N, N),
  SUBW            -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM, uopSUBW, N, Y, N, N),
  SLLW            -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM, uopSLLW, N, Y, N, N),
  SRAW            -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM, uopSRAW, N, Y, N, N),
  SRLW            -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM, uopSRLW, N, Y, N, N),

  LD              -> List(Y, N, ExecutionUnitEnum.AGU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED , N, I_IMM , uopLD , N, N, N, N),
  LWU             -> List(Y, N, ExecutionUnitEnum.AGU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED , N, I_IMM , uopLWU, N, N, N, N),
  LW              -> List(Y, N, ExecutionUnitEnum.AGU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED , N, I_IMM , uopLW , N, N, N, N),
  LH              -> List(Y, N, ExecutionUnitEnum.AGU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED , N, I_IMM , uopLH , N, N, N, N),
  LHU             -> List(Y, N, ExecutionUnitEnum.AGU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED , N, I_IMM , uopLHU, N, N, N, N),
  LB              -> List(Y, N, ExecutionUnitEnum.AGU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED , N, I_IMM , uopLB , N, N, N, N),
  LBU             -> List(Y, N, ExecutionUnitEnum.AGU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED , N, I_IMM , uopLBU, N, N, N, N),
  VSETVLI         -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED,  N, N_IMM, uopVSETVLI, N, N, N, N),
  VSETIVLI        -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.IMMED, RSTYPE.IMMED,  N, N_IMM, uopVSETIVLI, N, N, N, N),
  VSETVL          -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM, uopVSETVL, N, N, N, N),
  VADDVV          -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_VEC, RSTYPE.RS_VEC, RSTYPE.RS_VEC, N, N_IMM, uopVADDVV, N, N, N, N),
  VADDVI          -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_VEC, RSTYPE.RS_VEC, RSTYPE.IMMED,  N, N_IMM, uopVADDVI, N, N, N, N),
  VMVVI           -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_VEC, RSTYPE.IMMED, RSTYPE.IMMED,  N, N_IMM, uopVMVVI, N, N, N, N),
  VMVXS           -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_VEC, RSTYPE.IMMED,  N, N_IMM, uopVMVXS, N, N, N, N),
  VLE32           -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_VEC, RSTYPE.RS_INT, RSTYPE.IMMED,  N, N_IMM, uopVLE32, N, N, N, N),
  VSE32           -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_NA,  RSTYPE.RS_INT, RSTYPE.RS_VEC, N, N_IMM, uopVSE32, N, N, N, N),
  VSLIDEUPVI      -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_VEC, RSTYPE.RS_VEC, RSTYPE.IMMED,  N, N_IMM, uopVSLIDEUPVI, N, N, N, N),
  VSLIDEDOWNVI    -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_VEC, RSTYPE.RS_VEC, RSTYPE.IMMED,  N, N_IMM, uopVSLIDEDOWNVI, N, N, N, N),
  VRGATHERVI      -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_VEC, RSTYPE.RS_VEC, RSTYPE.IMMED,  N, N_IMM, uopVRGATHERVI, N, N, N, N),
  VREDSUMVS       -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_VEC, RSTYPE.RS_VEC, RSTYPE.RS_VEC, N, N_IMM, uopVREDSUMVS, N, N, N, N),
  VFADDVV         -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_VEC, RSTYPE.RS_VEC, RSTYPE.RS_VEC, N, N_IMM, uopVFADDVV, N, N, N, N),
  VFSUBVV         -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_VEC, RSTYPE.RS_VEC, RSTYPE.RS_VEC, N, N_IMM, uopVFSUBVV, N, N, N, N),
  VFWCVTFFV       -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_VEC, RSTYPE.RS_VEC, RSTYPE.IMMED,  N, N_IMM, uopVFWCVTFFV, N, N, N, N),
  VFNCVTFFW       -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_VEC, RSTYPE.RS_VEC, RSTYPE.IMMED,  N, N_IMM, uopVFNCVTFFW, N, N, N, N),
  VANDNVV         -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_VEC, RSTYPE.RS_VEC, RSTYPE.RS_VEC, N, N_IMM, uopVANDNVV, N, N, N, N),
  VBREV8V         -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_VEC, RSTYPE.RS_VEC, RSTYPE.IMMED,  N, N_IMM, uopVBREV8V, N, N, N, N),
  VREV8V          -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_VEC, RSTYPE.RS_VEC, RSTYPE.IMMED,  N, N_IMM, uopVREV8V, N, N, N, N),
  VCLZV           -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_VEC, RSTYPE.RS_VEC, RSTYPE.IMMED,  N, N_IMM, uopVCLZV, N, N, N, N),
  VCPOPV          -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_VEC, RSTYPE.RS_VEC, RSTYPE.IMMED,  N, N_IMM, uopVCPOPV, N, N, N, N),
  VRORVI          -> List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_VEC, RSTYPE.RS_VEC, RSTYPE.IMMED,  N, N_IMM, uopVRORVI, N, N, N, N),

  // Store Instructions (dispatch to AGU/LSU, RS1=base, RS2=store data, S-type imm)
  SB              -> List( Y, N, ExecutionUnitEnum.AGU, RDTYPE.RD_NA , RSTYPE.RS_INT, RSTYPE.RS_INT, N, S_IMM, uopSB, N, N, N, Y),
  SH              -> List( Y, N, ExecutionUnitEnum.AGU, RDTYPE.RD_NA , RSTYPE.RS_INT, RSTYPE.RS_INT, N, S_IMM, uopSH, N, N, N, Y),
  SW              -> List( Y, N, ExecutionUnitEnum.AGU, RDTYPE.RD_NA , RSTYPE.RS_INT, RSTYPE.RS_INT, N, S_IMM, uopSW, N, N, N, Y),
  SD              -> List( Y, N, ExecutionUnitEnum.AGU, RDTYPE.RD_NA , RSTYPE.RS_INT, RSTYPE.RS_INT, N, S_IMM, uopSD, N, N, N, Y),
  FLH             -> List( Y, Y, ExecutionUnitEnum.AGU, RDTYPE.RD_NA , RSTYPE.RS_INT, RSTYPE.IMMED,  N, I_IMM, uopFLH, N, N, N, N),
  FLW             -> List( Y, Y, ExecutionUnitEnum.AGU, RDTYPE.RD_NA , RSTYPE.RS_INT, RSTYPE.IMMED,  N, I_IMM, uopFLW, N, N, N, N),
  FLD             -> List( Y, Y, ExecutionUnitEnum.AGU, RDTYPE.RD_NA , RSTYPE.RS_INT, RSTYPE.IMMED,  N, I_IMM, uopFLD, N, N, N, N),
  FSH             -> List( Y, Y, ExecutionUnitEnum.AGU, RDTYPE.RD_NA , RSTYPE.RS_INT, RSTYPE.RS_NA,  N, S_IMM, uopFSH, N, N, N, Y),
  FSW             -> List( Y, Y, ExecutionUnitEnum.AGU, RDTYPE.RD_NA , RSTYPE.RS_INT, RSTYPE.RS_NA,  N, S_IMM, uopFSW, N, N, N, Y),
  FSD             -> List( Y, Y, ExecutionUnitEnum.AGU, RDTYPE.RD_NA , RSTYPE.RS_INT, RSTYPE.RS_NA,  N, S_IMM, uopFSD, N, N, N, Y),
  FMADDS          -> List( Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , Y, N_IMM , uopFMADDS, N, N, N, N),
  FMSUBS          -> List( Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , Y, N_IMM , uopFMSUBS, N, N, N, N),
  FNMSUBS         -> List( Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , Y, N_IMM , uopFNMSUBS, N, N, N, N),
  FNMADDS         -> List( Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , Y, N_IMM , uopFNMADDS, N, N, N, N),
  FMADDD          -> List( Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , Y, N_IMM , uopFMADDD, N, N, N, N),
  FMSUBD          -> List( Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , Y, N_IMM , uopFMSUBD, N, N, N, N),
  FNMSUBD         -> List( Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , Y, N_IMM , uopFNMSUBD, N, N, N, N),
  FNMADDD         -> List( Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , Y, N_IMM , uopFNMADDD, N, N, N, N),
  FADDS           -> List( Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFADDS, N, N, N, N),
  FSUBS           -> List( Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFSUBS, N, N, N, N),
  FMULS           -> List( Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFMULS, N, N, N, N),
  FADDD           -> List( Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFADDD, N, N, N, N),
  FSUBD           -> List( Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFSUBD, N, N, N, N),
  FMULD           -> List( Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFMULD, N, N, N, N),

  LUI        ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.IMMED , N, U_IMM , uopLUI , N, N, N, N),

  ADDI       ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED , N, I_IMM , uopADDI , N, N, N, N),
  ANDI       ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED , N, I_IMM , uopANDI , N, N, N, N),
  ORI        ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED , N, I_IMM , uopORI  , N, N, N, N),
  XORI       ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED , N, I_IMM , uopXORI , N, N, N, N),
  SLTI       ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED , N, I_IMM , uopSLTI , N, N, N, N),
  SLTIU      ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED , N, I_IMM , uopSLTIU, N, N, N, N),
                                               
  SLL        ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopSLL , N, N, N, N),
  ADD        ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopADD , N, N, N, N),
  SUB        ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopSUB , N, N, N, N),
  SLT        ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopSLT , N, N, N, N),
  SLTU       ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopSLTU, N, N, N, N),
  AND        ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopAND , N, N, N, N),
  OR         ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopOR  , N, N, N, N),
  XOR        ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopXOR , N, N, N, N),
  SRA        ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopSRA , N, N, N, N),
  SRL        ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopSRL , N, N, N, N),
  MUL        ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopMUL , N, N, N, N),
  MULH       ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopMULH, N, N, N, N),
  MULHSU     ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopMULHSU, N, N, N, N),
  MULHU      ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopMULHU, N, N, N, N),
  DIV        ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopDIV , N, N, N, N),
  DIVU       ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopDIVU, N, N, N, N),
  REM        ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopREM , N, N, N, N),
  REMU       ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopREMU, N, N, N, N),
  MULW       ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopMULW, N, Y, N, N),
  DIVW       ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopDIVW, N, Y, N, N),
  DIVUW      ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopDIVUW, N, Y, N, N),
  REMW       ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopREMW, N, Y, N, N),
  REMUW      ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopREMUW, N, Y, N, N),
  SH1ADD     ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopSH1ADD, N, N, N, N),
  SH2ADD     ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopSH2ADD, N, N, N, N),
  SH3ADD     ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopSH3ADD, N, N, N, N),
  ADD_UW     ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopADD_UW, N, Y, N, N),
  SH1ADD_UW  ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopSH1ADD_UW, N, Y, N, N),
  SH2ADD_UW  ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopSH2ADD_UW, N, Y, N, N),
  SH3ADD_UW  ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopSH3ADD_UW, N, Y, N, N),
  SLLI_UW    ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED, N, I_IMM , uopSLLI_UW, N, Y, N, N),
  ANDN       ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopANDN, N, N, N, N),
  ORN        ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopORN, N, N, N, N),
  XNOR       ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopXNOR, N, N, N, N),
  CLZ        ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED, N, I_IMM , uopCLZ, N, N, N, N),
  CTZ        ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED, N, I_IMM , uopCTZ, N, N, N, N),
  CPOP       ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED, N, I_IMM , uopCPOP, N, N, N, N),
  CLZW       ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED, N, I_IMM , uopCLZW, N, Y, N, N),
  CTZW       ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED, N, I_IMM , uopCTZW, N, Y, N, N),
  CPOPW      ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED, N, I_IMM , uopCPOPW, N, Y, N, N),
  MAX        ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopMAX, N, N, N, N),
  MAXU       ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopMAXU, N, N, N, N),
  MIN        ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopMIN, N, N, N, N),
  MINU       ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopMINU, N, N, N, N),
  SEXTB      ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED, N, I_IMM , uopSEXTB, N, N, N, N),
  SEXTH      ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED, N, I_IMM , uopSEXTH, N, N, N, N),
  ZEXTH      ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopZEXTH, N, N, N, N),
  ROL        ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopROL, N, N, N, N),
  ROR        ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopROR, N, N, N, N),
  RORI       ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED, N, I_IMM , uopRORI, N, N, N, N),
  ROLW       ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopROLW, N, Y, N, N),
  RORW       ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopRORW, N, Y, N, N),
  RORIW      ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED, N, I_IMM , uopRORIW, N, Y, N, N),
  ORCB       ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED, N, I_IMM , uopORCB, N, N, N, N),
  REV8       ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED, N, I_IMM , uopREV8, N, N, N, N),
  BCLR       ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopBCLR, N, N, N, N),
  BCLRI      ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED, N, I_IMM , uopBCLRI, N, N, N, N),
  BEXT       ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopBEXT, N, N, N, N),
  BEXTI      ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED, N, I_IMM , uopBEXTI, N, N, N, N),
  BINV       ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopBINV, N, N, N, N),
  BINVI      ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED, N, I_IMM , uopBINVI, N, N, N, N),
  BSET       ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopBSET, N, N, N, N),
  BSETI      ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.IMMED, N, I_IMM , uopBSETI, N, N, N, N),
  CZERO_EQZ  ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopCZERO_EQZ, N, N, N, N),
  CZERO_NEZ  ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopCZERO_NEZ, N, N, N, N),
  MOP_R      ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopMOP_R, N, N, N, N),
  MOP_RR     ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopMOP_RR, N, N, N, N),
  AMOADDW    ->        List(Y, N, ExecutionUnitEnum.AGU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopAMOADDW, N, N, N, Y),
  AMOADDD    ->        List(Y, N, ExecutionUnitEnum.AGU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopAMOADDD, N, N, N, Y),
  AMOSWAPW   ->        List(Y, N, ExecutionUnitEnum.AGU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopAMOSWAPW, N, N, N, Y),
  AMOSWAPD   ->        List(Y, N, ExecutionUnitEnum.AGU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopAMOSWAPD, N, N, N, Y),
  AMOXORW    ->        List(Y, N, ExecutionUnitEnum.AGU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopAMOXORW, N, N, N, Y),
  AMOXORD    ->        List(Y, N, ExecutionUnitEnum.AGU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopAMOXORD, N, N, N, Y),
  AMOANDW    ->        List(Y, N, ExecutionUnitEnum.AGU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopAMOANDW, N, N, N, Y),
  AMOANDD    ->        List(Y, N, ExecutionUnitEnum.AGU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopAMOANDD, N, N, N, Y),
  AMOORW     ->        List(Y, N, ExecutionUnitEnum.AGU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopAMOORW, N, N, N, Y),
  AMOORD     ->        List(Y, N, ExecutionUnitEnum.AGU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopAMOORD, N, N, N, Y),
  AMOMINW    ->        List(Y, N, ExecutionUnitEnum.AGU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopAMOMINW, N, N, N, Y),
  AMOMIND    ->        List(Y, N, ExecutionUnitEnum.AGU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopAMOMIND, N, N, N, Y),
  AMOMAXW    ->        List(Y, N, ExecutionUnitEnum.AGU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopAMOMAXW, N, N, N, Y),
  AMOMAXD    ->        List(Y, N, ExecutionUnitEnum.AGU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopAMOMAXD, N, N, N, Y),
  AMOMINUW   ->        List(Y, N, ExecutionUnitEnum.AGU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopAMOMINUW, N, N, N, Y),
  AMOMINUD   ->        List(Y, N, ExecutionUnitEnum.AGU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopAMOMINUD, N, N, N, Y),
  AMOMAXUW   ->        List(Y, N, ExecutionUnitEnum.AGU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopAMOMAXUW, N, N, N, Y),
  AMOMAXUD   ->        List(Y, N, ExecutionUnitEnum.AGU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopAMOMAXUD, N, N, N, Y),
                                                                                                                              
  AUIPC      ->        List(Y, N, ExecutionUnitEnum.ALU,  RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.IMMED , N, U_IMM , uopAUIPC,N, N, N, N),
  JAL        ->        List(Y, N, ExecutionUnitEnum.BR,  RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.RS_NA , N, J_IMM , uopJAL , Y, N, N, N),
  JALR       ->        List(Y, N, ExecutionUnitEnum.BR,  RDTYPE.RD_INT, RSTYPE.RS_INT , RSTYPE.RS_NA , N, I_IMM , uopJALR, Y, N, N, N),

  BEQ        ->        List(Y, N, ExecutionUnitEnum.BR,  RDTYPE.RD_NA , RSTYPE.RS_INT , RSTYPE.RS_INT , N, B_IMM , uopBEQ , Y, N, N, N),
  BNE        ->        List(Y, N, ExecutionUnitEnum.BR,  RDTYPE.RD_NA , RSTYPE.RS_INT , RSTYPE.RS_INT , N, B_IMM , uopBNE , Y, N, N, N),
  BGE        ->        List(Y, N, ExecutionUnitEnum.BR,  RDTYPE.RD_NA , RSTYPE.RS_INT , RSTYPE.RS_INT , N, B_IMM , uopBGE , Y, N, N, N),
  BGEU       ->        List(Y, N, ExecutionUnitEnum.BR,  RDTYPE.RD_NA , RSTYPE.RS_INT , RSTYPE.RS_INT , N, B_IMM , uopBGEU, Y, N, N, N),
  BLT        ->        List(Y, N, ExecutionUnitEnum.BR,  RDTYPE.RD_NA , RSTYPE.RS_INT , RSTYPE.RS_INT , N, B_IMM , uopBLT , Y, N, N, N),
  BLTU       ->        List(Y, N, ExecutionUnitEnum.BR,  RDTYPE.RD_NA , RSTYPE.RS_INT , RSTYPE.RS_INT , N, B_IMM , uopBLTU, Y, N, N, N),

  CSRRW      ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_NA , N, N_IMM , uopCSRRW , N, N, N, N),
  CSRRS      ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_NA , N, N_IMM , uopCSRRS , N, N, N, N),
  CSRRC      ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_INT, RSTYPE.RS_NA , N, N_IMM , uopCSRRC , N, N, N, N),
  CSRRWI     ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopCSRRWI, N, N, N, N),
  CSRRSI     ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopCSRRSI, N, N, N, N),
  CSRRCI     ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopCSRRCI, N, N, N, N),
  FENCE      ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFENCE , N, N, N, N),
  FENCEI     ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFENCE_I, N, N, N, N),
  CBO_INVAL  ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_INT, RSTYPE.RS_NA , N, N_IMM , uopFENCE , N, N, N, N),
  CBO_CLEAN  ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_INT, RSTYPE.RS_NA , N, N_IMM , uopFENCE , N, N, N, N),
  CBO_FLUSH  ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_INT, RSTYPE.RS_NA , N, N_IMM , uopFENCE , N, N, N, N),
  CBO_ZERO   ->        List(Y, N, ExecutionUnitEnum.AGU, RDTYPE.RD_NA , RSTYPE.RS_INT, RSTYPE.RS_NA , N, N_IMM , uopCBOZERO, N, N, N, Y),
  WRS_NTO    ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFENCE , N, N, N, N),
  WRS_STO    ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFENCE , N, N, N, N),
  ECALL      ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopECALL , N, N, N, N),
  EBREAK     ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopEBREAK, N, N, N, N),
  SRET       ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopSRET   , N, N, N, N),
  MRET       ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopMRET   , N, N, N, N),
  SFENCE_VMA ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopSFENCEVMA   , N, N, N, N),
  SINVAL_VMA ->        List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_INT, RSTYPE.RS_INT, N, N_IMM , uopSFENCEVMA   , N, N, N, N),
  SFENCE_W_INVAL ->    List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopSFENCEVMA   , N, N, N, N),
  SFENCE_INVAL_IR ->   List(Y, N, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopSFENCEVMA   , N, N, N, N),
  FCVTLS     ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFCVTLS, N, N, N, N),
  FCVTLUS    ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFCVTLUS, N, N, N, N),
  FCVTSL     ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_INT, RSTYPE.RS_NA , N, N_IMM , uopFCVTSL, N, N, N, N),
  FCVTSLU    ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_INT, RSTYPE.RS_NA , N, N_IMM , uopFCVTSLU, N, N, N, N),
  FCVTWS     ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFCVTWS, N, N, N, N),
  FCVTWUS    ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFCVTWUS, N, N, N, N),
  FCVTSW     ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_INT, RSTYPE.RS_NA , N, N_IMM , uopFCVTSW, N, N, N, N),
  FCVTSWU    ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_INT, RSTYPE.RS_NA , N, N_IMM , uopFCVTSWU, N, N, N, N),
  FCVTWD     ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFCVTWD, N, N, N, N),
  FCVTWUD    ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFCVTWUD, N, N, N, N),
  FCVTLD     ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFCVTLD, N, N, N, N),
  FCVTLUD    ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFCVTLUD, N, N, N, N),
  FCVTDW     ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_INT, RSTYPE.RS_NA , N, N_IMM , uopFCVTDW, N, N, N, N),
  FCVTDWU    ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_INT, RSTYPE.RS_NA , N, N_IMM , uopFCVTDWU, N, N, N, N),
  FCVTDL     ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_INT, RSTYPE.RS_NA , N, N_IMM , uopFCVTDL, N, N, N, N),
  FCVTDLU    ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_INT, RSTYPE.RS_NA , N, N_IMM , uopFCVTDLU, N, N, N, N),
  FCVTSD     ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFCVTSD, N, N, N, N),
  FCVTDS     ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFCVTDS, N, N, N, N),
  FCVTLH     ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFCVTLH, N, N, N, N),
  FCVTLUH    ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFCVTLUH, N, N, N, N),
  FCVTHL     ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_INT, RSTYPE.RS_NA , N, N_IMM , uopFCVTHL, N, N, N, N),
  FCVTHLU    ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_INT, RSTYPE.RS_NA , N, N_IMM , uopFCVTHLU, N, N, N, N),
  FMVXW      ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFMVXW, N, N, N, N),
  FMVWX      ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_INT, RSTYPE.RS_NA , N, N_IMM , uopFMVWX, N, N, N, N),
  FMVXD      ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFMVXD, N, N, N, N),
  FMVDX      ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_INT, RSTYPE.RS_NA , N, N_IMM , uopFMVDX, N, N, N, N),
  FDIVS      ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFDIVS, N, N, N, N),
  FSQRTS     ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFSQRTS, N, N, N, N),
  FDIVD      ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFDIVD, N, N, N, N),
  FSQRTD     ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFSQRTD, N, N, N, N),
  FCLASSS    ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFCLASSS, N, N, N, N),
  FCLASSD    ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFCLASSD, N, N, N, N),
  FSGNJS     ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFSGNJS, N, N, N, N),
  FSGNJNS    ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFSGNJNS, N, N, N, N),
  FSGNJXS    ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFSGNJXS, N, N, N, N),
  FSGNJD     ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFSGNJD, N, N, N, N),
  FSGNJND    ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFSGNJND, N, N, N, N),
  FSGNJXD    ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFSGNJXD, N, N, N, N),
  FMINS      ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFMINS, N, N, N, N),
  FMAXS      ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFMAXS, N, N, N, N),
  FMINMS     ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFMINMS, N, N, N, N),
  FMAXMS     ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFMAXMS, N, N, N, N),
  FMIND      ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFMIND, N, N, N, N),
  FMAXD      ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFMAXD, N, N, N, N),
  FMINMD     ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFMINMD, N, N, N, N),
  FMAXMD     ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFMAXMD, N, N, N, N),
  FLES       ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFLES, N, N, N, N),
  FLTS       ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFLTS, N, N, N, N),
  FEQS       ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFEQS, N, N, N, N),
  FLEQS      ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFLEQS, N, N, N, N),
  FLTQS      ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFLTQS, N, N, N, N),
  FLED       ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFLED, N, N, N, N),
  FLTD       ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFLTD, N, N, N, N),
  FEQD       ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFEQD, N, N, N, N),
  FLEQD      ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFLEQD, N, N, N, N),
  FLTQD      ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFLTQD, N, N, N, N),
  FLIS       ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFLIS, N, N, N, N),
  FLID       ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFLID, N, N, N, N),
  FROUNDS    ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFROUNDS, N, N, N, N),
  FROUNDNXS  ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFROUNDNXS, N, N, N, N),
  FROUNDD    ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFROUNDD, N, N, N, N),
  FROUNDNXD  ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_NA , RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFROUNDNXD, N, N, N, N),
  FCVTMODWD  ->        List(Y, Y, ExecutionUnitEnum.ALU, RDTYPE.RD_INT, RSTYPE.RS_NA , RSTYPE.RS_NA , N, N_IMM , uopFCVTMODWD, N, N, N, N),
                                                                                                                              
//
//  SFENCE_VMA          ->List(Y, N, fc2oh(FC_CSR) , RT_X  , RT_FIX, RT_FIX, N, IS_N, N, N, N,M_SFENCE , Y, Y, CSR.R, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
//  ECALL              -> List(Y, N, fc2oh(FC_CSR) , RT_X  , RT_X  , RT_X  , N, IS_I, N, N, N, M_X     , Y, Y, CSR.I, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
//  EBREAK             -> List(Y, N, fc2oh(FC_CSR) , RT_X  , RT_X  , RT_X  , N, IS_I, N, N, N, M_X     , Y, Y, CSR.I, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
//  SRET               -> List(Y, N, fc2oh(FC_CSR) , RT_X  , RT_X  , RT_X  , N, IS_I, N, N, N, M_X     , Y, Y, CSR.I, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
//  MRET               -> List(Y, N, fc2oh(FC_CSR) , RT_X  , RT_X  , RT_X  , N, IS_I, N, N, N, M_X     , Y, Y, CSR.I, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
//  DRET               -> List(Y, N, fc2oh(FC_CSR) , RT_X  , RT_X  , RT_X  , N, IS_I, N, N, N, M_X     , Y, Y, CSR.I, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
//
//  WFI                -> List(Y, N, fc2oh(FC_CSR) , RT_X  , RT_X  , RT_X  , N, IS_I, N, N, N, M_X     , Y, Y, CSR.I, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
//
//  FENCE_I            -> List(Y, N, 0.U(FC_SZ.W)  , RT_X  , RT_X  , RT_X  , N, IS_N, N, N, N, M_X     , Y, Y, CSR.N, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
//  FENCE              -> List(Y, N, 0.U(FC_SZ.W)  , RT_X  , RT_X  , RT_X  , N, IS_N, N, Y, N, M_X     , Y, Y, CSR.N, DW_XPR, FN_ADD , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
//
//

  )
}

// def F_table: Seq[(BitPat, List[BitPat])] = Seq(
//   FLW                -> List(Y, Y, fc2oh(FC_AGEN), RT_FLT, RT_FIX, RT_X  , N, IS_I, Y, N, N, M_XRD   , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
//   FLD                -> List(Y, Y, fc2oh(FC_AGEN), RT_FLT, RT_FIX, RT_X  , N, IS_I, Y, N, N, M_XRD   , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
//   FSW                -> List(Y, Y, FCOH_F2IMEM   , RT_X  , RT_FIX, RT_FLT, N, IS_S, N, Y, N, M_XWR   , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X), // sort of a lie; broken into two micro-ops
//   FSD                -> List(Y, Y, FCOH_F2IMEM   , RT_X  , RT_FIX, RT_FLT, N, IS_S, N, Y, N, M_XWR   , N, N, CSR.N, DW_X  , FN_X   , X,X,X,X,X, X,X,X,X,X,X,X, X,X,X,X),
//
//   FCLASS_S           -> List(Y, Y, fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,N, N,X,S,S,N,Y,N, N,N,N,N),
//   FCLASS_D           -> List(Y, Y, fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,N, N,X,D,D,N,Y,N, N,N,N,N),
//
//   FMV_W_X            -> List(Y, Y, fc2oh(FC_I2F) , RT_FLT, RT_FIX, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,N,N,N, X,X,S,D,Y,N,N, N,N,N,N),
//   FMV_D_X            -> List(Y, Y, fc2oh(FC_I2F) , RT_FLT, RT_FIX, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,N,N,N, X,X,D,D,Y,N,N, N,N,N,N),
//   FMV_X_W            -> List(Y, Y, fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,N, N,X,D,S,N,Y,N, N,N,N,N),
//   FMV_X_D            -> List(Y, Y, fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,N, N,X,D,D,N,Y,N, N,N,N,N),
//
//   FSGNJ_S            -> List(Y, Y, fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,S,S,N,N,Y, N,N,N,N),
//   FSGNJ_D            -> List(Y, Y, fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,D,D,N,N,Y, N,N,N,N),
//   FSGNJX_S           -> List(Y, Y, fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,S,S,N,N,Y, N,N,N,N),
//   FSGNJX_D           -> List(Y, Y, fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,D,D,N,N,Y, N,N,N,N),
//   FSGNJN_S           -> List(Y, Y, fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,S,S,N,N,Y, N,N,N,N),
//   FSGNJN_D           -> List(Y, Y, fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,D,D,N,N,Y, N,N,N,N),
//
//   // FP to FP
//   FCVT_S_D           -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,N, N,X,D,S,N,N,Y, N,N,N,Y),
//   FCVT_D_S           -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,N, N,X,S,D,N,N,Y, N,N,N,Y),
//
//   // Int to FP
//   FCVT_S_W           -> List(Y, Y,fc2oh(FC_I2F) , RT_FLT, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,N,N,N, X,X,S,S,Y,N,N, N,N,N,Y),
//   FCVT_S_WU          -> List(Y, Y,fc2oh(FC_I2F) , RT_FLT, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,N,N,N, X,X,S,S,Y,N,N, N,N,N,Y),
//   FCVT_S_L           -> List(Y, Y,fc2oh(FC_I2F) , RT_FLT, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,N,N,N, X,X,S,S,Y,N,N, N,N,N,Y),
//   FCVT_S_LU          -> List(Y, Y,fc2oh(FC_I2F) , RT_FLT, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,N,N,N, X,X,S,S,Y,N,N, N,N,N,Y),
//
//   FCVT_D_W           -> List(Y, Y,fc2oh(FC_I2F) , RT_FLT, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,N,N,N, X,X,D,D,Y,N,N, N,N,N,Y),
//   FCVT_D_WU          -> List(Y, Y,fc2oh(FC_I2F) , RT_FLT, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,N,N,N, X,X,D,D,Y,N,N, N,N,N,Y),
//   FCVT_D_L           -> List(Y, Y,fc2oh(FC_I2F) , RT_FLT, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,N,N,N, X,X,D,D,Y,N,N, N,N,N,Y),
//   FCVT_D_LU          -> List(Y, Y,fc2oh(FC_I2F) , RT_FLT, RT_FIX, RT_X  , N, IS_I, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,N,N,N, X,X,D,D,Y,N,N, N,N,N,Y),
//
//   // FP to Int
//   FCVT_W_S           -> List(Y, Y,fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,N, N,X,S,S,N,Y,N, N,N,N,Y),
//   FCVT_WU_S          -> List(Y, Y,fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,N, N,X,S,S,N,Y,N, N,N,N,Y),
//   FCVT_L_S           -> List(Y, Y,fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,N, N,X,S,S,N,Y,N, N,N,N,Y),
//   FCVT_LU_S          -> List(Y, Y,fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,N, N,X,S,S,N,Y,N, N,N,N,Y),
//
//   FCVT_W_D           -> List(Y, Y,fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,N, N,X,D,D,N,Y,N, N,N,N,Y),
//   FCVT_WU_D          -> List(Y, Y,fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,N, N,X,D,D,N,Y,N, N,N,N,Y),
//   FCVT_L_D           -> List(Y, Y,fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,N, N,X,D,D,N,Y,N, N,N,N,Y),
//   FCVT_LU_D          -> List(Y, Y,fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,N, N,X,D,D,N,Y,N, N,N,N,Y),
//
//
//   FEQ_S              -> List(Y, Y, fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,S,S,N,Y,N, N,N,N,Y),
//   FLT_S              -> List(Y, Y, fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,S,S,N,Y,N, N,N,N,Y),
//   FLE_S              -> List(Y, Y, fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,S,S,N,Y,N, N,N,N,Y),
//
//   FEQ_D              -> List(Y, Y, fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,D,D,N,Y,N, N,N,N,Y),
//   FLT_D              -> List(Y, Y, fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,D,D,N,Y,N, N,N,N,Y),
//   FLE_D              -> List(Y, Y, fc2oh(FC_F2I) , RT_FIX, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,D,D,N,Y,N, N,N,N,Y),
//
//   FMIN_S             -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,S,S,N,N,Y, N,N,N,Y),
//   FMAX_S             -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,S,S,N,N,Y, N,N,N,Y),
//   FMIN_D             -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,D,D,N,N,Y, N,N,N,Y),
//   FMAX_D             -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,N, N,N,D,D,N,N,Y, N,N,N,Y),
//
//   FADD_S             -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_00  ,X,X,Y,Y,N, N,Y,S,S,N,N,N, Y,N,N,Y),
//   FSUB_S             -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_01  ,X,X,Y,Y,N, N,Y,S,S,N,N,N, Y,N,N,Y),
//   FMUL_S             -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_00  ,X,X,Y,Y,N, N,N,S,S,N,N,N, Y,N,N,Y),
//   FADD_D             -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_00  ,X,X,Y,Y,N, N,Y,D,D,N,N,N, Y,N,N,Y),
//   FSUB_D             -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_01  ,X,X,Y,Y,N, N,Y,D,D,N,N,N, Y,N,N,Y),
//   FMUL_D             -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_00  ,X,X,Y,Y,N, N,N,D,D,N,N,N, Y,N,N,Y),
//
//   FMADD_S            -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, Y, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_00  ,X,X,Y,Y,Y, N,N,S,S,N,N,N, Y,N,N,Y),
//   FMSUB_S            -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, Y, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_01  ,X,X,Y,Y,Y, N,N,S,S,N,N,N, Y,N,N,Y),
//   FNMADD_S           -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, Y, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_11  ,X,X,Y,Y,Y, N,N,S,S,N,N,N, Y,N,N,Y),
//   FNMSUB_S           -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, Y, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_10  ,X,X,Y,Y,Y, N,N,S,S,N,N,N, Y,N,N,Y),
//   FMADD_D            -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, Y, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_00  ,X,X,Y,Y,Y, N,N,D,D,N,N,N, Y,N,N,Y),
//   FMSUB_D            -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, Y, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_01  ,X,X,Y,Y,Y, N,N,D,D,N,N,N, Y,N,N,Y),
//   FNMADD_D           -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, Y, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_11  ,X,X,Y,Y,Y, N,N,D,D,N,N,N, Y,N,N,Y),
//   FNMSUB_D           -> List(Y, Y,fc2oh(FC_FPU) , RT_FLT, RT_FLT, RT_FLT, Y, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_10  ,X,X,Y,Y,Y, N,N,D,D,N,N,N, Y,N,N,Y)
// )
// def FDivSqrt_table: Seq[(BitPat, List[BitPat])] = Seq(
//   FDIV_S             -> List(Y, Y, fc2oh(FC_FDV) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,X, X,X,S,S,X,X,X, X,Y,N,Y),
//   FDIV_D             -> List(Y, Y, fc2oh(FC_FDV) , RT_FLT, RT_FLT, RT_FLT, N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,Y,X, X,X,D,D,X,X,X, X,Y,N,Y),
//   FSQRT_S            -> List(Y, Y, fc2oh(FC_FDV) , RT_FLT, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,X, X,X,S,S,X,X,X, X,N,Y,Y),
//   FSQRT_D            -> List(Y, Y, fc2oh(FC_FDV) , RT_FLT, RT_FLT, RT_X  , N, IS_N, N, N, N, M_X     , N, N, CSR.N, DW_X  , FN_X   ,X,X,Y,N,X, X,X,D,D,X,X,X, X,N,Y,Y),
// )
//
//
