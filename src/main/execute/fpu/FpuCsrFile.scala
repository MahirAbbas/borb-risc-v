package borb.execute.fpu

import spinal.core._
import spinal.lib._

case class FpuCsrFile() extends Component {
  val io = new Bundle {
    val csrReadAddress = in UInt (12 bits)
    val csrReadData = out Bits (64 bits)
    val csrWrite = slave(Flow(FpuCsrWriteCmd()))

    // Flags update should be commit-gated by integration logic.
    val flagsSetValid = in Bool ()
    val flagsSetBits = in Bits (5 bits)
    val flagsClear = in Bool ()
    val commit = in Bool ()

    val frm = out Bits (3 bits)
    val fflags = out Bits (5 bits)
    val fcsr = out Bits (8 bits)
  }

  val CSR_FFLAGS = U(0x001, 12 bits)
  val CSR_FRM = U(0x002, 12 bits)
  val CSR_FCSR = U(0x003, 12 bits)

  val fflagsReg = Reg(Bits(5 bits)) init 0
  val frmReg = Reg(Bits(3 bits)) init FpuRm.RNE

  def legalFrm(x: Bits): Bool = FpuRm.isLegal(x)

  when(io.flagsClear) {
    fflagsReg := 0
  }

  when(io.flagsSetValid && io.commit) {
    fflagsReg := fflagsReg | io.flagsSetBits
  }

  when(io.csrWrite.valid) {
    switch(io.csrWrite.payload.address) {
      is(CSR_FFLAGS) {
        fflagsReg := io.csrWrite.payload.data(4 downto 0)
      }
      is(CSR_FRM) {
        when(legalFrm(io.csrWrite.payload.data(2 downto 0))) {
          frmReg := io.csrWrite.payload.data(2 downto 0)
        }
      }
      is(CSR_FCSR) {
        fflagsReg := io.csrWrite.payload.data(4 downto 0)
        when(legalFrm(io.csrWrite.payload.data(7 downto 5))) {
          frmReg := io.csrWrite.payload.data(7 downto 5)
        }
      }
    }
  }

  io.csrReadData := B(0, 64 bits)
  switch(io.csrReadAddress) {
    is(CSR_FFLAGS) { io.csrReadData := B(0, 59 bits) ## fflagsReg }
    is(CSR_FRM) { io.csrReadData := B(0, 61 bits) ## frmReg }
    is(CSR_FCSR) { io.csrReadData := B(0, 56 bits) ## frmReg ## fflagsReg }
  }

  io.frm := frmReg
  io.fflags := fflagsReg
  io.fcsr := frmReg ## fflagsReg
}
