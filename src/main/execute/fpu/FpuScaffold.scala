package borb.execute.fpu

import spinal.core._
import spinal.lib._

case class FpuScaffold(cfg: FpuScaffoldConfig = FpuScaffoldConfig()) extends Component {
  val io = new Bundle {
    val issue = slave(Stream(FpuIssue(cfg)))
    val kill = in Bool ()
    val result = master(Flow(FpuResult(cfg)))
    val busy = out Bool ()

    // Optional external access for debug / bring-up.
    val debugFrm = out Bits (3 bits)
    val debugFflags = out Bits (5 bits)
  }

  val regFile = FpuRegisterFile(
    flen = cfg.flen,
    readPorts = cfg.readPorts,
    writePorts = cfg.writePorts
  )
  val csrFile = FpuCsrFile()

  // Keep CSR interface inert in the scaffold.
  csrFile.io.csrReadAddress := 0
  csrFile.io.csrWrite.valid := False
  csrFile.io.csrWrite.payload.assignDontCare()
  csrFile.io.flagsClear := False
  csrFile.io.flagsSetValid := False
  csrFile.io.flagsSetBits := 0
  csrFile.io.commit := False

  // Bind read addresses directly from incoming issue for single-cycle capture on fire.
  regFile.io.reads(0).valid := io.issue.valid
  regFile.io.reads(0).address := io.issue.payload.rs1

  if (cfg.readPorts > 1) {
    regFile.io.reads(1).valid := io.issue.valid
    regFile.io.reads(1).address := io.issue.payload.rs2
  }
  for (i <- 2 until cfg.readPorts) {
    regFile.io.reads(i).valid := io.issue.valid && io.issue.payload.rs3Enable
    regFile.io.reads(i).address := io.issue.payload.rs3
  }

  // Keep all write ports disabled by default; completion writeback is external.
  for (wp <- regFile.io.writes) {
    wp.valid := False
    wp.address := 0
    wp.data := 0
  }

  def maxLatency: Int = Seq(
    cfg.addLatency,
    cfg.mulLatency,
    cfg.divSqrtLatency,
    cfg.fmaLatency,
    cfg.convertLatency,
    cfg.compareLatency,
    cfg.moveLatency
  ).max

  def latencyWidth: Int = log2Up(maxLatency + 1)

  def classLatency(op: FpuOpClass.C): UInt = {
    val lat = UInt(latencyWidth bits)
    lat := U(cfg.moveLatency, latencyWidth bits)
    switch(op) {
      is(FpuOpClass.MOVE) { lat := U(cfg.moveLatency, latencyWidth bits) }
      is(FpuOpClass.ADD_SUB) { lat := U(cfg.addLatency, latencyWidth bits) }
      is(FpuOpClass.MUL) { lat := U(cfg.mulLatency, latencyWidth bits) }
      is(FpuOpClass.DIV_SQRT) { lat := U(cfg.divSqrtLatency, latencyWidth bits) }
      is(FpuOpClass.FMA) { lat := U(cfg.fmaLatency, latencyWidth bits) }
      is(FpuOpClass.CONVERT) { lat := U(cfg.convertLatency, latencyWidth bits) }
      is(FpuOpClass.COMPARE) { lat := U(cfg.compareLatency, latencyWidth bits) }
      is(FpuOpClass.MIN_MAX) { lat := U(cfg.compareLatency, latencyWidth bits) }
      is(FpuOpClass.CLASSIFY) { lat := U(cfg.compareLatency, latencyWidth bits) }
    }
    lat
  }

  val inflight = Reg(Bool()) init False
  val countdown = Reg(UInt(latencyWidth bits)) init 0
  val inflightRd = Reg(UInt(5 bits)) init 0
  val inflightWritesRd = Reg(Bool()) init False
  val inflightWritesFlags = Reg(Bool()) init False
  val inflightTag = Reg(UInt(cfg.issueTagWidth bits)) init 0
  val inflightClass = Reg(FpuOpClass()) init FpuOpClass.MOVE
  val inflightFmt = Reg(FpuFormat()) init FpuFormat.D
  val inflightSrc1 = Reg(Bits(cfg.flen bits)) init 0
  val inflightSrc2 = Reg(Bits(cfg.flen bits)) init 0
  val inflightSrc3 = Reg(Bits(cfg.flen bits)) init 0

  io.issue.ready := !inflight

  when(io.issue.fire) {
    inflight := True
    val lat = classLatency(io.issue.payload.opClass)
    countdown := (lat === 0) ? U(0, latencyWidth bits) | (lat - 1)
    inflightRd := io.issue.payload.rd
    inflightWritesRd := io.issue.payload.writesRd
    inflightWritesFlags := io.issue.payload.writesFlags
    inflightTag := io.issue.payload.tag
    inflightClass := io.issue.payload.opClass
    inflightFmt := io.issue.payload.format
    inflightSrc1 := regFile.io.reads(0).data
    if (cfg.readPorts > 1) inflightSrc2 := regFile.io.reads(1).data else inflightSrc2 := 0
    if (cfg.readPorts > 2) inflightSrc3 := regFile.io.reads(2).data else inflightSrc3 := 0
  }

  val finished = inflight && (countdown === 0)
  val resultFire = finished && !io.kill

  when(inflight && !io.kill && !finished) {
    countdown := countdown - 1
  }

  when(inflight && (finished || io.kill)) {
    inflight := False
  }

  val canonicalQNaN = if (cfg.flen == 64) {
    B(BigInt("7FF8000000000000", 16), cfg.flen bits)
  } else {
    B(BigInt("7FC00000", 16), cfg.flen bits)
  }

  val placeholderData = Bits(cfg.flen bits)
  placeholderData := canonicalQNaN
  switch(inflightClass) {
    is(FpuOpClass.MOVE) { placeholderData := inflightSrc1 }
    is(FpuOpClass.CONVERT) { placeholderData := inflightSrc1 }
    is(FpuOpClass.COMPARE) { placeholderData := B(0, cfg.flen bits) }
    is(FpuOpClass.CLASSIFY) { placeholderData := B(0, cfg.flen bits) }
  }

  // NV is the msb in fflags[4:0] (NV,DZ,OF,UF,NX).
  val placeholderFlags = Bits(5 bits)
  placeholderFlags := 0
  when(
    inflightWritesFlags && (
      inflightClass === FpuOpClass.ADD_SUB ||
        inflightClass === FpuOpClass.MUL ||
        inflightClass === FpuOpClass.DIV_SQRT ||
        inflightClass === FpuOpClass.FMA
    )
  ) {
    placeholderFlags(4) := True
  }

  io.result.valid := resultFire
  io.result.payload.rd := inflightRd
  io.result.payload.data := placeholderData
  io.result.payload.writesRd := inflightWritesRd
  io.result.payload.fflags := placeholderFlags
  io.result.payload.tag := inflightTag

  io.busy := inflight
  io.debugFrm := csrFile.io.frm
  io.debugFflags := csrFile.io.fflags
}
