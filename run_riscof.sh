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

usage() {
  echo "Usage: $0 [OPTIONS]"
  echo ""
  echo "Options:"
  echo "  --skip-gen        Skip SpinalHDL -> Verilog generation (runMain borb.SoC)"
  echo "  --skip-build      Skip Verilator sim build (make -C verif/riscof/borb/sim)"
  echo "  --no-clean-build  Do not clean before Verilator build"
  echo "  --skip-validate   Skip riscof validateyaml step"
  echo "  --clean           Pass --clean to riscof run"
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

"${RUN_CMD[@]}"

echo "=== RISCOF complete ==="
