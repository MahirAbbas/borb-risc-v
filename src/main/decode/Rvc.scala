package borb.frontend

import spinal.core._

case class DecompressedInstruction() extends Bundle {
  val inst = Bits(32 bits)
  val illegal = Bool()
}

object RVC {
  private def sext(bits: Bits, width: Int): Bits = {
    val out = Bits(width bits)
    out := bits.resize(width)
    out(width - 1 downto bits.getWidth) := B(width - bits.getWidth bits, default -> bits(bits.getWidth - 1))
    out
  }

  def apply(i: Bits, xlen: Int = 64): DecompressedInstruction = {
    val ret = DecompressedInstruction()
    ret.inst := B"32'h00000013" // Default to NOP
    ret.illegal := False

    val rch = B"01" ## i(9 downto 7)
    val rcl = B"01" ## i(4 downto 2)

    val immCi = sext(i(12) ## i(6 downto 2), 12)
    val immCj = sext(i(12) ## i(8) ## i(10 downto 9) ## i(6) ## i(7) ## i(2) ## i(11) ## i(5 downto 3) ## B"0", 21)
    val immCb = sext(i(12) ## i(6 downto 5) ## i(2) ## i(11 downto 10) ## i(4 downto 3) ## B"0", 13)

    val shamt = (i(12) ## i(6 downto 2)).resize(6 bits)

    val lwImm = B"00000" ## i(5) ## i(12 downto 10) ## i(6) ## B"00"
    val ldImm = B"0000" ## i(6 downto 5) ## i(12 downto 10) ## B"000"
    val swImm = lwImm
    val sdImm = ldImm

    val lwspImm = B"0000" ## i(3 downto 2) ## i(12) ## i(6 downto 4) ## B"00"
    val ldspImm = B"000" ## i(4 downto 2) ## i(12) ## i(6 downto 5) ## B"000"
    val swspImm = B"0000" ## i(8 downto 7) ## i(12 downto 9) ## B"00"
    val sdspImm = B"000" ## i(9 downto 7) ## i(12 downto 10) ## B"000"

    val x0 = B"00000"
    val x1 = B"00001"
    val x2 = B"00010"

    switch(i(1 downto 0) ## i(15 downto 13)) {
      default {
        ret.illegal := True
      }

      // Quadrant 0
      is(B"5'b00000") { // C.ADDI4SPN
        val nzuimm = B"00" ## i(10 downto 7) ## i(12 downto 11) ## i(5) ## i(6) ## B"00"
        ret.inst := nzuimm ## x2 ## B"000" ## rcl ## B"0010011"
        ret.illegal := i(12 downto 5) === 0
      }
      is(B"5'b00010") { // C.LW
        ret.inst := lwImm ## rch ## B"010" ## rcl ## B"0000011"
      }
      is(B"5'b00011") { // C.LD (RV64)
        if (xlen == 64) {
          ret.inst := ldImm ## rch ## B"011" ## rcl ## B"0000011"
        } else {
          ret.illegal := True
        }
      }
      is(B"5'b00110") { // C.SW
        ret.inst := swImm(11 downto 5) ## rcl ## rch ## B"010" ## swImm(4 downto 0) ## B"0100011"
      }
      is(B"5'b00111") { // C.SD (RV64)
        if (xlen == 64) {
          ret.inst := sdImm(11 downto 5) ## rcl ## rch ## B"011" ## sdImm(4 downto 0) ## B"0100011"
        } else {
          ret.illegal := True
        }
      }

      // Quadrant 1
      is(B"5'b01000") { // C.ADDI / C.NOP
        ret.inst := immCi ## i(11 downto 7) ## B"000" ## i(11 downto 7) ## B"0010011"
      }
      is(B"5'b01001") { // C.ADDIW (RV64)
        if (xlen == 64) {
          ret.inst := immCi ## i(11 downto 7) ## B"000" ## i(11 downto 7) ## B"0011011"
          ret.illegal := i(11 downto 7) === 0
        } else {
          ret.illegal := True
        }
      }
      is(B"5'b01010") { // C.LI
        ret.inst := immCi ## x0 ## B"000" ## i(11 downto 7) ## B"0010011"
      }
      is(B"5'b01011") { // C.ADDI16SP / C.LUI
        val addi16spImm = sext(i(12) ## i(4 downto 3) ## i(5) ## i(2) ## i(6) ## B"0000", 12)
        val luiImm = sext(i(12) ## i(6 downto 2) ## B"0000_0000_0000", 32)
        when(i(11 downto 7) === x2) {
          ret.inst := addi16spImm ## x2 ## B"000" ## x2 ## B"0010011"
          // C.ADDI16SP is illegal when nzimm is zero.
          ret.illegal := (i(12) === False) && (i(6 downto 2) === 0)
        } elsewhen(i(11 downto 7) === x0) {
          // Reserved/HINT encoding; execute as NOP.
          ret.inst := B"32'h00000013"
          ret.illegal := False
        } otherwise {
          ret.inst := luiImm(31 downto 12) ## i(11 downto 7) ## B"0110111"
          ret.illegal := (i(12 downto 2) === 0)
        }
      }
      is(B"5'b01100") { // C.SRLI / C.SRAI / C.ANDI / C.SUB/XOR/OR/AND(+W)
        switch(i(11 downto 10)) {
          is(B"2'b00") { // C.SRLI
            ret.inst := B"000000" ## shamt ## rch ## B"101" ## rch ## B"0010011"
            if (xlen == 32) ret.illegal := i(12)
          }
          is(B"2'b01") { // C.SRAI
            ret.inst := B"010000" ## shamt ## rch ## B"101" ## rch ## B"0010011"
            if (xlen == 32) ret.illegal := i(12)
          }
          is(B"2'b10") { // C.ANDI
            ret.inst := immCi ## rch ## B"111" ## rch ## B"0010011"
          }
          default { // C.SUB/C.XOR/C.OR/C.AND (+C.SUBW/C.ADDW on RV64)
            val funct2 = i(6 downto 5)
            when(!i(12)) {
              switch(funct2) {
                is(B"2'b00") { ret.inst := B"0100000" ## rcl ## rch ## B"000" ## rch ## B"0110011" } // SUB
                is(B"2'b01") { ret.inst := B"0000000" ## rcl ## rch ## B"100" ## rch ## B"0110011" } // XOR
                is(B"2'b10") { ret.inst := B"0000000" ## rcl ## rch ## B"110" ## rch ## B"0110011" } // OR
                is(B"2'b11") { ret.inst := B"0000000" ## rcl ## rch ## B"111" ## rch ## B"0110011" } // AND
              }
            } otherwise {
              if (xlen == 64) {
                switch(funct2) {
                  is(B"2'b00") { ret.inst := B"0100000" ## rcl ## rch ## B"000" ## rch ## B"0111011" } // SUBW
                  is(B"2'b01") { ret.inst := B"0000000" ## rcl ## rch ## B"000" ## rch ## B"0111011" } // ADDW
                  default { ret.illegal := True }
                }
              } else {
                ret.illegal := True
              }
            }
          }
        }
      }
      is(B"5'b01101") { // C.J
        ret.inst := immCj(20) ## immCj(10 downto 1) ## immCj(11) ## immCj(19 downto 12) ## x0 ## B"1101111"
      }
      is(B"5'b01110") { // C.BEQZ
        ret.inst := immCb(12) ## immCb(10 downto 5) ## x0 ## rch ## B"000" ## immCb(4 downto 1) ## immCb(11) ## B"1100011"
      }
      is(B"5'b01111") { // C.BNEZ
        ret.inst := immCb(12) ## immCb(10 downto 5) ## x0 ## rch ## B"001" ## immCb(4 downto 1) ## immCb(11) ## B"1100011"
      }

      // Quadrant 2
      is(B"5'b10000") { // C.SLLI
        ret.inst := B"000000" ## shamt ## i(11 downto 7) ## B"001" ## i(11 downto 7) ## B"0010011"
        ret.illegal := i(11 downto 7) === 0
      }
      is(B"5'b10010") { // C.LWSP
        ret.inst := lwspImm ## x2 ## B"010" ## i(11 downto 7) ## B"0000011"
        ret.illegal := i(11 downto 7) === 0
      }
      is(B"5'b10011") { // C.LDSP (RV64)
        if (xlen == 64) {
          ret.inst := ldspImm ## x2 ## B"011" ## i(11 downto 7) ## B"0000011"
          ret.illegal := i(11 downto 7) === 0
        } else {
          ret.illegal := True
        }
      }
      is(B"5'b10100") { // C.JR/C.MV/C.EBREAK/C.JALR/C.ADD
        when(i(12) === False) {
          when(i(6 downto 2) === 0) {
            // C.JR
            ret.inst := B"000000000000" ## i(11 downto 7) ## B"000" ## x0 ## B"1100111"
            ret.illegal := i(11 downto 7) === 0
          } otherwise {
            // C.MV
            ret.inst := B"0000000" ## i(6 downto 2) ## x0 ## B"000" ## i(11 downto 7) ## B"0110011"
          }
        } otherwise {
          when((i(11 downto 7) === 0) && (i(6 downto 2) === 0)) {
            // C.EBREAK
            ret.inst := B"32'h00100073"
          } elsewhen(i(6 downto 2) === 0) {
            // C.JALR
            ret.inst := B"000000000000" ## i(11 downto 7) ## B"000" ## x1 ## B"1100111"
            ret.illegal := i(11 downto 7) === 0
          } otherwise {
            // C.ADD
            ret.inst := B"0000000" ## i(6 downto 2) ## i(11 downto 7) ## B"000" ## i(11 downto 7) ## B"0110011"
          }
        }
      }
      is(B"5'b10110") { // C.SWSP
        ret.inst := swspImm(11 downto 5) ## i(6 downto 2) ## x2 ## B"010" ## swspImm(4 downto 0) ## B"0100011"
      }
      is(B"5'b10111") { // C.SDSP (RV64)
        if (xlen == 64) {
          ret.inst := sdspImm(11 downto 5) ## i(6 downto 2) ## x2 ## B"011" ## sdspImm(4 downto 0) ## B"0100011"
        } else {
          ret.illegal := True
        }
      }
    }

    ret
  }
}
