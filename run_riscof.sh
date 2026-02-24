#!/bin/bash
set -e

# Configuration
CONFIG_PATH="/Users/mahir/fun/borb/verif/riscof/config.ini"
SUITE_PATH="/Users/mahir/fun/borb/verif/riscof/riscv-arch-test/riscv-test-suite"
ENV_PATH="/Users/mahir/fun/borb/verif/riscof/riscv-arch-test/riscv-test-suite/env"
SIM_DIR="/Users/mahir/fun/borb/verif/riscof/borb/sim"

# Parse arguments
SKIP_GEN=false
SKIP_BUILD=false
SKIP_VALIDATE=false
CLEAN=false
CLEAN_BUILD=true
TESTS=""
REPORT_RERUN=false
SKIP_REPORT=false

usage() {
  echo "Usage: $0 [OPTIONS]"
  echo ""
  echo "Options:"
  echo "  --skip-gen        Skip SpinalHDL -> Verilog generation (runMain borb.SoC)"
  echo "  --skip-build      Skip Verilator sim build (make -C verif/riscof/borb/sim)"
  echo "  --no-clean-build  Do not clean before Verilator build"
  echo "  --skip-validate   Skip riscof validateyaml step"
  echo "  --clean           Pass --clean to riscof run"
  echo "  --report-rerun    Re-run failing tests while generating debug reports (slow)"
  echo "  --skip-report     Skip post-run failure report generation"
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

if [[ -d "$(pwd)/oss-cad-suite/bin" ]]; then
  export PATH="$(pwd)/oss-cad-suite/bin:$PATH"
fi

# Set python version for riscof
if command -v pyenv >/dev/null 2>&1; then
  eval "$(pyenv init -)"
  pyenv shell 3.8.18
fi

echo "=== Borb RISCOF Run ==="

if [[ "$SKIP_GEN" = false ]]; then
  echo "[1/3] Compiling SpinalHDL to Verilog (SoC)..."
  sbt "runMain borb.SoC"
else
  echo "[1/3] Skipping Verilog generation"
fi

if [[ "$SKIP_BUILD" = false ]]; then
  echo "[2/3] Building Verilator sim..."
  if [[ "$CLEAN_BUILD" = true ]]; then
    make -C "$SIM_DIR" clean
  fi
  make -C "$SIM_DIR"
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
