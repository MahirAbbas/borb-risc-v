package borb.execute.fpu

import spinal.core._
import borb.dispatch.RegisterFile

case class FpuRegisterFile(
    flen: Int = 64,
    readPorts: Int = 3,
    writePorts: Int = 1
) extends Area {
  require(flen == 32 || flen == 64, "FPU register file supports FLEN=32/64")
  require(readPorts >= 1, "Need at least one FP read port")
  require(writePorts >= 1, "Need at least one FP write port")

  private val initValue =
    if (flen == 64) BigInt("FFFFFFFF00000000", 16) else BigInt(0)

  private val regFile = RegisterFile(
    dataWidth = flen,
    readPorts = readPorts,
    writePorts = writePorts,
    invalidReadZero = true,
    initValue = initValue,
    lastWriteWins = true
  )

  val io = regFile.io
}
