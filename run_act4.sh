#!/bin/bash
set -e

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/scripts/workspace_env.sh"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

ARCH_TEST_DIR="$ROOT_DIR/verif/act4/riscv-arch-test"
CONFIG_FILE="$ROOT_DIR/verif/act4/borb-rva23s64/test_config.yaml"
WORKDIR="$ROOT_DIR/verif/act4/work"
SIM_DIR="$ROOT_DIR/verif/borb-sim/sim"
SIM_BIN="$ROOT_DIR/verif/borb-sim/build/obj_dir/VSoC"
PROFILE="rva23s64-full"
JOBS=""
SIM_JOBS=""
SIM_THREADS=""
VERILATE_JOBS=""
EXTENSIONS=""
EXCLUDE_EXTENSIONS=""
FAST_ACT=true
BUILD_ONLY=false
RUN_ONLY=false
LIST_ONLY=false
SKIP_GEN=false
SKIP_BUILD=false
MAX_CYCLES="${BORB_ACT4_MAX_CYCLES:-2000000}"

usage() {
  echo "Usage: $0 [OPTIONS]"
  echo "  --profile <name>       Only rva23s64-full is currently supported"
  echo "  --extensions <list>    ACT4 comma-separated extension filter"
  echo "  --exclude <list>       ACT4 comma-separated exclusion filter"
  echo "  --jobs <n>             ACT4 ELF build jobs"
  echo "  --sim-jobs <n>         Borb simulator run jobs"
  echo "  --sim-threads <n>      Verilator runtime threads"
  echo "  --verilate-jobs <n>    Verilator codegen jobs"
  echo "  --max-cycles <n>       Per-ELF simulator cycle budget"
  echo "  --build-only           Build ACT4 ELFs but do not run them"
  echo "  --run-only             Run existing ACT4 ELFs"
  echo "  --list-only            Build plan dry-run without executing tasks"
  echo "  --skip-gen             Skip SpinalHDL generation"
  echo "  --skip-build           Skip Borb simulator build"
  echo "  --fast-act             Disable ACT4 objdump generation"
  echo "  --debug-act            Enable ACT4 debug artifacts"
  echo "  -h, --help             Show this help"
  exit 0
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --profile) PROFILE="$2"; shift 2 ;;
    --extensions) EXTENSIONS="$2"; shift 2 ;;
    --exclude) EXCLUDE_EXTENSIONS="$2"; shift 2 ;;
    --jobs) JOBS="$2"; shift 2 ;;
    --sim-jobs) SIM_JOBS="$2"; shift 2 ;;
    --sim-threads) SIM_THREADS="$2"; shift 2 ;;
    --verilate-jobs) VERILATE_JOBS="$2"; shift 2 ;;
    --max-cycles) MAX_CYCLES="$2"; shift 2 ;;
    --build-only) BUILD_ONLY=true; shift ;;
    --run-only) RUN_ONLY=true; shift ;;
    --list-only) LIST_ONLY=true; shift ;;
    --skip-gen) SKIP_GEN=true; shift ;;
    --skip-build) SKIP_BUILD=true; shift ;;
    --fast-act) FAST_ACT=true; shift ;;
    --debug-act) FAST_ACT=false; shift ;;
    -h|--help) usage ;;
    *) echo "Unknown option: $1"; usage ;;
  esac
done

if [[ "$PROFILE" != "rva23s64-full" ]]; then
  echo "Error: ACT4 wrapper currently supports --profile rva23s64-full"
  exit 2
fi

for value in "$JOBS" "$SIM_JOBS" "$SIM_THREADS" "$VERILATE_JOBS" "$MAX_CYCLES"; do
  if [[ -n "$value" && ! "$value" =~ ^[1-9][0-9]*$ ]]; then
    echo "Error: numeric options must be positive integers"
    exit 2
  fi
done

if [[ -z "$JOBS" ]]; then
  JOBS="$(sysctl -n hw.ncpu 2>/dev/null || echo 1)"
fi

if [[ -d "$ROOT_DIR/oss-cad-suite/bin" ]]; then
  export PATH="$ROOT_DIR/oss-cad-suite/bin:$PATH"
fi
if [[ -x "$ROOT_DIR/verif/toolchains/sail-riscv-0.11/bin/sail_riscv_sim" ]]; then
  export PATH="$ROOT_DIR/verif/toolchains/sail-riscv-0.11/bin:$PATH"
  export ACT_ALLOW_NEWER_SAIL=1
fi
export XDG_CACHE_HOME="$ROOT_DIR/verif/act4/cache"
export ACT_BORB_FORCE_RVA23_MARCH=1
if [[ -f /opt/homebrew/lib/libz3.dylib ]]; then
  mkdir -p "$XDG_CACHE_HOME/udb/z3/z3-4.16.0/arm64"
  ln -sf /opt/homebrew/lib/libz3.dylib "$XDG_CACHE_HOME/udb/z3/z3-4.16.0/arm64/libz3.so"
  ln -sf /opt/homebrew/lib/libz3.dylib "$XDG_CACHE_HOME/udb/z3/z3-4.16.0/arm64/libz3.so.4.8"
fi

echo "=== Borb ACT4 Run ==="
echo "Profile: $PROFILE"
echo "Suite: $ARCH_TEST_DIR"
echo "Config: $CONFIG_FILE"

RUN_WORKDIR="$WORKDIR"
if [[ -n "$EXTENSIONS" || -n "$EXCLUDE_EXTENSIONS" ]]; then
  scope_key="profile=$PROFILE extensions=$EXTENSIONS exclude=$EXCLUDE_EXTENSIONS"
  scope_hash="$(printf '%s' "$scope_key" | shasum -a 256 | awk '{print substr($1, 1, 12)}')"
  RUN_WORKDIR="$WORKDIR/run-scopes/$scope_hash"
  echo "ACT4 scope: $scope_key"
  echo "Scoped workdir: $RUN_WORKDIR"
fi

if [[ "$RUN_ONLY" != true ]]; then
  if [[ "$SKIP_GEN" != true ]]; then
    echo "[1/3] Compiling SpinalHDL to Verilog (SoC)..."
    "$ROOT_DIR/scripts/run_borb_main.sh" borb.SoC
  fi

  echo "[2/3] Building ACT4 ELFs..."
  if [[ -z "$EXTENSIONS" ]]; then
    vector_args=(-C "$ARCH_TEST_DIR" "WORKDIR=$RUN_WORKDIR")
    if [[ -n "$JOBS" ]]; then vector_args+=("-j$JOBS" "JOBS=$JOBS"); fi
    make "${vector_args[@]}" vector-tests
  fi
  make_args=(
    -C "$ARCH_TEST_DIR"
    "CONFIG_FILES=$CONFIG_FILE"
    "WORKDIR=$RUN_WORKDIR"
    "EXCLUDE_EXTENSIONS=$EXCLUDE_EXTENSIONS"
  )
  if [[ -n "$JOBS" ]]; then make_args+=("-j$JOBS" "JOBS=$JOBS"); fi
  if [[ -n "$EXTENSIONS" ]]; then make_args+=("EXTENSIONS=$EXTENSIONS"); fi
  if [[ "$FAST_ACT" = true ]]; then make_args+=("FAST=True"); else make_args+=("DEBUG=True"); fi
  if [[ "$LIST_ONLY" = true ]]; then make_args+=("elfs" "-n"); else make_args+=("elfs"); fi
  make "${make_args[@]}"
fi

if [[ "$LIST_ONLY" = true || "$BUILD_ONLY" = true ]]; then
  exit 0
fi

if [[ "$SKIP_BUILD" != true ]]; then
  echo "[3/3] Building Borb simulator..."
  sim_make=(make -C "$SIM_DIR")
  if [[ -n "$SIM_JOBS" ]]; then sim_make+=("-j$SIM_JOBS"); fi
  sim_make+=("FAST=1")
  if [[ -n "$SIM_THREADS" ]]; then sim_make+=("THREADS=$SIM_THREADS"); fi
  if [[ -n "$VERILATE_JOBS" ]]; then sim_make+=("VERILATE_JOBS=$VERILATE_JOBS"); fi
  "${sim_make[@]}"
fi

ELF_DIR="$RUN_WORKDIR/borb-RVA23S64/elfs"
if [[ ! -d "$ELF_DIR" ]]; then
  echo "Error: ACT4 ELF directory not found: $ELF_DIR"
  if [[ "$RUN_ONLY" = true && ( -n "$EXTENSIONS" || -n "$EXCLUDE_EXTENSIONS" ) ]]; then
    echo "Hint: build this scoped ACT4 selection once without --run-only before rerunning it."
  fi
  exit 1
fi

RUNNER_CMD="python3.11 $ROOT_DIR/scripts/act4_borb_elf_runner.py --sim $SIM_BIN --out-root $ROOT_DIR/verif/act4/borb-rva23s64/results --max-cycles $MAX_CYCLES"
run_args=(python3.11 "$ARCH_TEST_DIR/run_tests.py")
if [[ -n "$SIM_JOBS" ]]; then run_args+=("-j" "$SIM_JOBS"); fi
run_args+=("$RUNNER_CMD" "$ELF_DIR")

echo "[4/4] Running ACT4 ELFs on Borb..."
"${run_args[@]}"
