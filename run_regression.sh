#!/bin/bash
set -e

# Configuration
CORE_DIR="formal/cores/borb"
CHECKS_DIR="$CORE_DIR/checks"

echo "=== Borb CPU Formal Regression ==="

# 1. Regenerate Verilog from SpinalHDL source
echo "[1/3] Compiling SpinalHDL to Verilog..."
sbt "runMain borb.CPU"

# 2. Re-generate formal checks (if needed, or just to be safe)
# echo "[2/3] Generating formal checks..."
# (cd $CORE_DIR && python3 ../../../riscv-formal/checks/genchecks.py)

# 3. Define check groups
ALU_CHECKS="insn_add_ch0 insn_addi_ch0 insn_and_ch0 insn_andi_ch0 insn_auipc_ch0 insn_lui_ch0 insn_or_ch0 insn_ori_ch0 insn_sll_ch0 insn_slli_ch0 insn_slt_ch0 insn_slti_ch0 insn_sltiu_ch0 insn_sltu_ch0 insn_sra_ch0 insn_srai_ch0 insn_srl_ch0 insn_srli_ch0 insn_sub_ch0 insn_xor_ch0 insn_xori_ch0"
BRANCH_CHECKS="insn_beq_ch0 insn_bne_ch0 insn_blt_ch0 insn_bge_ch0 insn_bltu_ch0 insn_bgeu_ch0 insn_jal_ch0 insn_jalr_ch0"

ALL_CHECKS="$ALU_CHECKS $BRANCH_CHECKS"

# 4. Run checks
echo "[3/3] Running Formal Checks..."
echo "Targets: $ALL_CHECKS"

export PATH=$(pwd)/oss-cad-suite/bin:$PATH

# Clean previous results for these specific checks
echo "Cleaning previous check results..."
rm -rf $CHECKS_DIR/insn_*_ch0

# Run make
make -C $CHECKS_DIR -j4 $ALL_CHECKS

echo "=== Regression SUCCESS ==="
