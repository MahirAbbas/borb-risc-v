#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SIM_DIR="$ROOT_DIR/verif/riscof/borb/sim"

SKIP_GEN=false
SKIP_BUILD=false
CLEAN_BUILD=true

usage() {
  cat <<'USAGE'
Usage: ./run_tenstorrent.sh [OPTIONS] [-- <runner args>]

Options:
  --skip-gen        Skip SpinalHDL -> Verilog generation (runMain borb.SoC)
  --skip-build      Skip Verilator sim build
  --no-clean-build  Do not clean before Verilator build
  -h, --help        Show this help

Everything after '--' is forwarded to scripts/tenstorrent_borb_runner.py.
USAGE
}

RUNNER_ARGS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
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
    -h|--help)
      usage
      exit 0
      ;;
    --)
      shift
      RUNNER_ARGS+=("$@")
      break
      ;;
    *)
      RUNNER_ARGS+=("$1")
      shift
      ;;
  esac
done

if [[ "$SKIP_GEN" = false ]]; then
  echo "[1/3] Generating Verilog (sbt runMain borb.SoC)..."
  sbt "runMain borb.SoC"
else
  echo "[1/3] Skipping Verilog generation"
fi

if [[ "$SKIP_BUILD" = false ]]; then
  echo "[2/3] Building borb simulator (make -C $SIM_DIR)..."
  if [[ "$CLEAN_BUILD" = true ]]; then
    make -C "$SIM_DIR" clean
  fi
  make -C "$SIM_DIR"
else
  echo "[2/3] Skipping simulator build"
fi

echo "[3/3] Running Tenstorrent scaffold..."
set +e
python3 "$ROOT_DIR/scripts/tenstorrent_borb_runner.py" "${RUNNER_ARGS[@]}"
RUN_STATUS=$?
set -e

echo "[4/4] Generating failure reports..."
python3 "$ROOT_DIR/scripts/riscv_failure_report.py" tenstorrent || true

exit "$RUN_STATUS"
