#!/bin/bash
set -e

# Configuration
CONFIG_PATH="/Users/mahir/fun/borb/verif/riscof/config.ini"
SUITE_PATH="/Users/mahir/fun/borb/verif/riscof/riscv-arch-test/riscv-test-suite"
ENV_PATH="/Users/mahir/fun/borb/verif/riscof/riscv-arch-test/riscv-test-suite/env"
SIM_DIR="/Users/mahir/fun/borb/verif/riscof/borb/sim"
BORB_PLUGIN_PY="/Users/mahir/fun/borb/verif/riscof/borb/riscof_borb.py"
SPIKE_PLUGIN_PY="/Users/mahir/fun/borb/verif/riscof/spike/riscof_spike.py"

# Parse arguments
SKIP_GEN=false
SKIP_BUILD=false
SKIP_VALIDATE=false
CLEAN=false
CLEAN_BUILD=true
TESTS=""
REPORT_RERUN=false
SKIP_REPORT=false
FAST_RV64F=false
FAST_RV32F=false
FAST_SIM=false
SIM_JOBS=""

usage() {
  echo "Usage: $0 [OPTIONS]"
  echo ""
  echo "Options:"
  echo "  --skip-gen        Skip SpinalHDL -> Verilog generation (runMain borb.SoC)"
  echo "  --skip-build      Skip Verilator sim build (make -C verif/riscof/borb/sim)"
  echo "  --no-clean-build  Do not clean before Verilator build"
  echo "  --fast-sim        Build simulator in fast mode (FAST=1, TRACE=0)"
  echo "  --sim-jobs <n>    Parallel jobs for simulator build (make -j<n>)"
  echo "  --skip-validate   Skip riscof validateyaml step"
  echo "  --clean           Pass --clean to riscof run"
  echo "  --report-rerun    Re-run failing tests while generating debug reports (slow)"
  echo "  --skip-report     Skip post-run failure report generation"
  echo "  --fast-rv64f      Run only RV64F arch-tests (auto-populates --tests)"
  echo "  --fast-rv32f      Run only RV32F arch-tests (auto-populates --tests)"
  echo "  --tests <list>    Run only selected tests (name fragments, comma-separated)"
  echo "                    Example: --tests add-01.S,addi-01.S"
  echo "  --config <path>   Path to config.ini (default: $CONFIG_PATH)"
  echo "  --suite <path>    Path to riscv-test-suite (default: $SUITE_PATH)"
  echo "  --env <path>      Path to env dir (default: $ENV_PATH)"
  echo "  -h, --help        Show this help message"
  exit 0
}

while [[ $# -gt 0 ]]; do
  case $1 in
    --skip-gen)
      SKIP_GEN=true
      shift
      ;;
    --skip-build)
      SKIP_BUILD=true
      shift
      ;;
    --no-clean-build)
      CLEAN_BUILD=false
      shift
      ;;
    --fast-sim)
      FAST_SIM=true
      shift
      ;;
    --sim-jobs)
      SIM_JOBS="$2"
      shift 2
      ;;
    --skip-validate)
      SKIP_VALIDATE=true
      shift
      ;;
    --clean)
      CLEAN=true
      shift
      ;;
    --report-rerun)
      REPORT_RERUN=true
      shift
      ;;
    --skip-report)
      SKIP_REPORT=true
      shift
      ;;
    --fast-rv64f)
      FAST_RV64F=true
      shift
      ;;
    --fast-rv32f)
      FAST_RV32F=true
      shift
      ;;
    --tests)
      TESTS="$2"
      shift 2
      ;;
    --config)
      CONFIG_PATH="$2"
      shift 2
      ;;
    --suite)
      SUITE_PATH="$2"
      shift 2
      ;;
    --env)
      ENV_PATH="$2"
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

if [[ -n "$SIM_JOBS" && ! "$SIM_JOBS" =~ ^[1-9][0-9]*$ ]]; then
  echo "Error: --sim-jobs expects a positive integer"
  exit 2
fi

if [[ "$FAST_RV64F" = true && "$FAST_RV32F" = true ]]; then
  echo "Error: --fast-rv64f and --fast-rv32f cannot be combined"
  exit 2
fi

if [[ "$FAST_RV64F" = true ]]; then
  if [[ -n "$TESTS" ]]; then
    echo "Error: --fast-rv64f cannot be combined with --tests"
    exit 2
  fi
  RV64F_DIR="$SUITE_PATH/rv64i_m/F/src"
  if [[ ! -d "$RV64F_DIR" ]]; then
    echo "Error: RV64F test directory not found: $RV64F_DIR"
    exit 2
  fi
  TESTS="$(ls "$RV64F_DIR"/*.S 2>/dev/null | xargs -n1 basename | paste -sd, -)"
  if [[ -z "$TESTS" ]]; then
    echo "Error: no RV64F tests found under: $RV64F_DIR"
    exit 2
  fi
  echo "Selected RV64F fast subset ($(echo "$TESTS" | tr ',' '\n' | wc -l | tr -d ' ') tests)"
fi

if [[ "$FAST_RV32F" = true ]]; then
  if [[ -n "$TESTS" ]]; then
    echo "Error: --fast-rv32f cannot be combined with --tests"
    exit 2
  fi
  RV32F_DIR="$SUITE_PATH/rv32i_m/F/src"
  if [[ ! -d "$RV32F_DIR" ]]; then
    echo "Error: RV32F test directory not found: $RV32F_DIR"
    exit 2
  fi
  TESTS="$(ls "$RV32F_DIR"/*.S 2>/dev/null | xargs -n1 basename | paste -sd, -)"
  if [[ -z "$TESTS" ]]; then
    echo "Error: no RV32F tests found under: $RV32F_DIR"
    exit 2
  fi
  echo "Selected RV32F fast subset ($(echo "$TESTS" | tr ',' '\n' | wc -l | tr -d ' ') tests)"
fi

if [[ -d "$(pwd)/oss-cad-suite/bin" ]]; then
  export PATH="$(pwd)/oss-cad-suite/bin:$PATH"
fi

# Set python version for riscof
if command -v pyenv >/dev/null 2>&1; then
  eval "$(pyenv init -)"
  pyenv shell 3.8.18
fi

# Silence known benign GNU as truncation warnings from generated arch tests by
# injecting -Wa,--no-warn into RISCOF plugin gcc templates. This is idempotent.
patch_plugin_asm_warnings() {
  local plugin="$1"
  if [[ ! -f "$plugin" ]]; then
    return
  fi
  python3 - "$plugin" <<'PY'
import sys
from pathlib import Path

p = Path(sys.argv[1])
s = p.read_text(encoding="utf-8")
if "-Wa,--no-warn" in s:
    print(f"[warn-suppress] already present: {p}")
    sys.exit(0)

needle = "-nostdlib -nostartfiles"
if needle not in s:
    print(f"[warn-suppress] pattern not found: {p}", file=sys.stderr)
    sys.exit(0)

s2 = s.replace(needle, needle + " -Wa,--no-warn", 1)
p.write_text(s2, encoding="utf-8")
print(f"[warn-suppress] patched: {p}")
PY
}

patch_plugin_asm_warnings "$BORB_PLUGIN_PY"
patch_plugin_asm_warnings "$SPIKE_PLUGIN_PY"

echo "=== Borb RISCOF Run ==="

if [[ "$SKIP_GEN" = false ]]; then
  echo "[1/3] Compiling SpinalHDL to Verilog (SoC)..."
  sbt "runMain borb.SoC"
else
  echo "[1/3] Skipping Verilog generation"
fi

if [[ "$SKIP_BUILD" = false ]]; then
  echo "[2/3] Building Verilator sim..."
  MAKE_BUILD_ARGS=(-C "$SIM_DIR")
  if [[ "$FAST_SIM" = true ]]; then
    MAKE_BUILD_ARGS+=(FAST=1)
    echo "      using fast sim mode (FAST=1, TRACE=0)"
  fi
  if [[ -n "$SIM_JOBS" ]]; then
    MAKE_BUILD_ARGS+=(-j"$SIM_JOBS")
  fi
  if [[ "$CLEAN_BUILD" = true ]]; then
    make -C "$SIM_DIR" clean
  fi
  make "${MAKE_BUILD_ARGS[@]}"
else
  echo "[2/3] Skipping sim build"
fi

if [[ "$SKIP_VALIDATE" = false ]]; then
  echo "[3/3] Validating ISA YAMLs..."
  riscof validateyaml --config="$CONFIG_PATH"
else
  echo "[3/3] Skipping validateyaml"
fi

echo "[4/4] Running RISCOF..."
RUN_CMD=(riscof run --config="$CONFIG_PATH" --suite="$SUITE_PATH" --env="$ENV_PATH")
if [[ "$CLEAN" = true ]]; then
  RUN_CMD+=(--clean)
fi
# Default work-dir used by riscof when --work-dir is not provided.
WORK_DIR="$(dirname "$CONFIG_PATH")/riscof_work"

if [[ -n "$TESTS" ]]; then
  WORK_DIR="$(dirname "$CONFIG_PATH")/riscof_work_subset_$(date +%Y%m%d_%H%M%S)"
  mkdir -p "$WORK_DIR"
  FULL_TESTLIST="$WORK_DIR/test_list.yaml"
  SUBSET_TESTLIST="$WORK_DIR/test_list.subset.yaml"

  if [[ ! -f "$FULL_TESTLIST" ]]; then
    echo "Generating full test list first..."
    riscof testlist --config="$CONFIG_PATH" --suite="$SUITE_PATH" --env="$ENV_PATH" --work-dir="$WORK_DIR"
  fi

  echo "Selecting subset tests: $TESTS"
  python3 - "$FULL_TESTLIST" "$SUBSET_TESTLIST" "$TESTS" <<'PY'
import sys
import yaml
import os
import fnmatch

full, out, tests = sys.argv[1], sys.argv[2], sys.argv[3]
needles = [t.strip() for t in tests.split(",") if t.strip()]

with open(full, "r", encoding="utf-8") as f:
    data = yaml.safe_load(f) or {}

picked = {}
for test_path, meta in data.items():
    base = os.path.basename(test_path)
    matched = False
    for n in needles:
        if "*" in n or "?" in n:
            if fnmatch.fnmatch(base, n) or fnmatch.fnmatch(test_path, n):
                matched = True
                break
        else:
            if base == n or test_path.endswith(n):
                matched = True
                break
    if matched:
        picked[test_path] = meta

if not picked:
    print("No tests matched requested filters:", ", ".join(needles), file=sys.stderr)
    sys.exit(2)

with open(out, "w", encoding="utf-8") as f:
    yaml.safe_dump(picked, f, sort_keys=False)

print(f"Wrote subset testlist: {out} ({len(picked)} tests)")
PY

  RUN_CMD+=(--work-dir="$WORK_DIR")
  RUN_CMD+=(--testfile="$SUBSET_TESTLIST")
fi

set +e
"${RUN_CMD[@]}"
RUN_STATUS=$?
set -e

if [[ "$SKIP_REPORT" = false ]]; then
  echo "Generating RISCOF debug reports..."
  REPORT_ARGS=()
  if [[ -n "${WORK_DIR:-}" ]]; then
    REPORT_ARGS+=(--workdir "$WORK_DIR")
  fi
  if [[ "$REPORT_RERUN" = false ]]; then
    REPORT_ARGS+=(--no-rerun)
  fi
  python3 scripts/riscv_failure_report.py riscof "${REPORT_ARGS[@]}" || true
else
  echo "Skipping RISCOF debug report generation"
fi

echo "=== RISCOF complete ==="
exit "$RUN_STATUS"
