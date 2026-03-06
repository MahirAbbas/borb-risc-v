#!/bin/bash
set -e

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/scripts/workspace_env.sh"

# Configuration
CORE_DIR="formal/cores/borb"
CHECKS_DIR="$CORE_DIR/checks"

# Parse arguments
FAILED_ONLY=false
SKIP_COMPILE=false
CHECKS_OVERRIDE=""

usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --failed-only    Only run tests that failed in the previous run"
    echo "  --skip-compile   Skip SpinalHDL -> Verilog generation (sbt runMain borb.CPU)"
    echo "  --checks <list>  Run only these checks (space or comma-separated)"
    echo "  -h, --help       Show this help message"
    exit 0
}

while [[ $# -gt 0 ]]; do
    case $1 in
        --failed-only)
            FAILED_ONLY=true
            shift
            ;;
        --skip-compile)
            SKIP_COMPILE=true
            shift
            ;;
        --checks)
            CHECKS_OVERRIDE="$2"
            shift 2
            ;;
        -h|--help)
            usage
            ;;
        *)
            echo "Unknown option: $1"
            usage
            ;;
    esac
done

echo "=== Borb CPU Formal Regression ==="

# 1. Regenerate Verilog from SpinalHDL source
if [ "$SKIP_COMPILE" = true ]; then
    echo "[1/3] Skipping SpinalHDL compile"
else
    echo "[1/3] Compiling SpinalHDL to Verilog..."
    sbt --batch --no-server --no-share --no-global --sbt-dir "$SBT_GLOBAL_DIR" --sbt-boot "$SBT_BOOT_DIR" --ivy "$IVY_HOME" "runMain borb.CPU"
fi

# 2. Re-generate formal checks (if needed, or just to be safe)
echo "[2/3] Generating formal checks..."
(cd $CORE_DIR && python3 ../../../riscv-formal/checks/genchecks.py)

# 3. Define check groups
ALU_CHECKS="insn_add_ch0 insn_addi_ch0 insn_and_ch0 insn_andi_ch0 insn_auipc_ch0 insn_lui_ch0 insn_or_ch0 insn_ori_ch0 insn_sll_ch0 insn_slli_ch0 insn_slt_ch0 insn_slti_ch0 insn_sltiu_ch0 insn_sltu_ch0 insn_sra_ch0 insn_srai_ch0 insn_srl_ch0 insn_srli_ch0 insn_sub_ch0 insn_xor_ch0 insn_xori_ch0 insn_addiw_ch0 insn_addw_ch0 insn_subw_ch0 insn_sllw_ch0 insn_srlw_ch0 insn_sraw_ch0 insn_slliw_ch0 insn_srliw_ch0 insn_sraiw_ch0 insn_mul_ch0 insn_mulh_ch0 insn_mulhsu_ch0 insn_mulhu_ch0 insn_div_ch0 insn_divu_ch0 insn_rem_ch0 insn_remu_ch0 insn_mulw_ch0 insn_divw_ch0 insn_divuw_ch0 insn_remw_ch0 insn_remuw_ch0"
BRANCH_CHECKS="insn_beq_ch0 insn_bne_ch0 insn_blt_ch0 insn_bge_ch0 insn_bltu_ch0 insn_bgeu_ch0 insn_jal_ch0 insn_jalr_ch0"
STORE_CHECKS="insn_sb_ch0 insn_sh_ch0 insn_sw_ch0 insn_sd_ch0"
LOAD_CHECKS="insn_lb_ch0 insn_lbu_ch0 insn_ld_ch0 insn_lh_ch0 insn_lhu_ch0 insn_lwu_ch0 insn_lw_ch0 "

CONSISTENCY_CHECKS="reg_ch0 pc_fwd_ch0 pc_bwd_ch0 unique_ch0 causal_ch0 liveness_ch0"

ALL_CHECKS="$ALU_CHECKS $BRANCH_CHECKS $STORE_CHECKS $LOAD_CHECKS $CONSISTENCY_CHECKS"

# Determine which checks to run
if [ -n "$CHECKS_OVERRIDE" ]; then
    CHECKS_TO_RUN=$(echo "$CHECKS_OVERRIDE" | tr ',' ' ')
    echo "[3/3] Running user-selected checks..."
elif [ "$FAILED_ONLY" = true ]; then
    # Find previously failed tests
    PREV_FAILED=$(find $CHECKS_DIR -name "FAIL" 2>/dev/null | while read f; do basename $(dirname "$f"); done | sort -u | tr '\n' ' ')
    if [ -z "$PREV_FAILED" ]; then
        echo "No previously failed tests found. Nothing to run."
        exit 0
    fi
    CHECKS_TO_RUN="$PREV_FAILED"
    echo "[3/3] Running previously failed checks only..."
else
    CHECKS_TO_RUN="$ALL_CHECKS"
    echo "[3/3] Running Formal Checks..."
fi

echo "Targets: $CHECKS_TO_RUN"

export PATH=$(pwd)/oss-cad-suite/bin:$PATH

# Clean previous results for checks we're about to run
echo "Cleaning previous check results..."
for check in $CHECKS_TO_RUN; do
    rm -rf "$CHECKS_DIR/$check"
done

# Run make (ignore errors to let all run)
make -C $CHECKS_DIR -j10 $CHECKS_TO_RUN

# Convert generated counterexample traces to FST for faster viewing.
if command -v vcd2fst >/dev/null 2>&1; then
    find "$CHECKS_DIR" -path "*/engine_0/trace.vcd" -type f | while read -r vcd; do
        fst="${vcd%.vcd}.fst"
        vcd2fst "$vcd" "$fst" >/dev/null 2>&1 || true
    done
fi

echo "=== Regression Results ==="
FAILED_TESTS=$(find $CHECKS_DIR -name "FAIL" | sort)

if [ -z "$FAILED_TESTS" ]; then
    echo "SUCCESS: All tests passed."
else
    echo "FAILURE: The following tests failed:"
    for test in $FAILED_TESTS; do
        # Extract test name (e.g. formal/cores/borb/checks/insn_jal_ch0/FAIL -> insn_jal_ch0)
        echo "  - $(basename $(dirname $test))"
    done
    echo "Generating formal debug reports..."
    python3 scripts/riscv_failure_report.py formal --checks-dir "$CHECKS_DIR" || true
    exit 1
fi
