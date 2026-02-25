package borb.execute.fpu

import spinal.core._

object FpuFormat extends SpinalEnum {
  val S, D = newElement()
}

object FpuOpClass extends SpinalEnum {
  val MOVE = newElement()
  val ADD_SUB = newElement()
  val MUL = newElement()
  val DIV_SQRT = newElement()
  val FMA = newElement()
  val CONVERT = newElement()
  val COMPARE = newElement()
  val MIN_MAX = newElement()
  val CLASSIFY = newElement()
}

object FpuRm {
  val RNE = B"3'b000"
  val RTZ = B"3'b001"
  val RDN = B"3'b010"
  val RUP = B"3'b011"
  val RMM = B"3'b100"
  val DYN = B"3'b111"

  def isLegal(rm: Bits): Bool = {
    val ok = Bool()
    ok := False
    switch(rm) {
      is(RNE) { ok := True }
      is(RTZ) { ok := True }
      is(RDN) { ok := True }
      is(RUP) { ok := True }
      is(RMM) { ok := True }
      is(DYN) { ok := True }
    }
    ok
  }
}

case class FpuScaffoldConfig(
    flen: Int = 64,
    issueTagWidth: Int = 4,
    readPorts: Int = 3,
    writePorts: Int = 1,
    addLatency: Int = 3,
    mulLatency: Int = 4,
    divSqrtLatency: Int = 16,
    fmaLatency: Int = 5,
    convertLatency: Int = 3,
    compareLatency: Int = 2,
    moveLatency: Int = 1
) {
  require(flen == 32 || flen == 64, "FPU scaffold only supports FLEN=32 or FLEN=64")
  require(issueTagWidth >= 1, "issueTagWidth must be >= 1")
  require(readPorts >= 1, "readPorts must be >= 1")
  require(writePorts >= 1, "writePorts must be >= 1")
  require(addLatency >= 1, "addLatency must be >= 1")
  require(mulLatency >= 1, "mulLatency must be >= 1")
  require(divSqrtLatency >= 1, "divSqrtLatency must be >= 1")
  require(fmaLatency >= 1, "fmaLatency must be >= 1")
  require(convertLatency >= 1, "convertLatency must be >= 1")
  require(compareLatency >= 1, "compareLatency must be >= 1")
  require(moveLatency >= 1, "moveLatency must be >= 1")
}

case class FpuIssue(cfg: FpuScaffoldConfig) extends Bundle {
  val opClass = FpuOpClass()
  val format = FpuFormat()
  val rm = Bits(3 bits)

  val rs1 = UInt(5 bits)
  val rs2 = UInt(5 bits)
  val rs3 = UInt(5 bits)
  val rs3Enable = Bool()
  val rd = UInt(5 bits)

  val writesRd = Bool()
  val writesFlags = Bool()
  val tag = UInt(cfg.issueTagWidth bits)
}

case class FpuResult(cfg: FpuScaffoldConfig) extends Bundle {
  val rd = UInt(5 bits)
  val data = Bits(cfg.flen bits)
  val writesRd = Bool()
  val fflags = Bits(5 bits)
  val tag = UInt(cfg.issueTagWidth bits)
}

case class FpuCsrWriteCmd() extends Bundle {
  val address = UInt(12 bits)
  val data = Bits(64 bits)
}
