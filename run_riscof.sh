#!/bin/bash
set -e

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/scripts/workspace_env.sh"

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
CYCLE_BUDGET_MODE="hybrid"
CYCLE_BUDGET_SCALE=""
CYCLE_BUDGET_SLACK=""
CYCLE_BUDGET_FILE="/Users/mahir/fun/borb/verif/riscof/cycle_budgets.json"
SMOKE_RV32F_CORE=false
SMOKE_RV32F_ARITH=false
SMOKE_RV32F_LONGLAT=false
SMOKE_ZB_CORE=false
SMOKE_C_FRONT=false

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
  echo "  --smoke-rv32f-core      Run checked-in RV32F core smoke preset"
  echo "  --smoke-rv32f-arith     Run checked-in RV32F arithmetic smoke preset"
  echo "  --smoke-rv32f-longlat   Run checked-in RV32F long-latency smoke preset"
  echo "  --smoke-zb-core         Run checked-in Zb smoke preset"
  echo "  --smoke-c-front         Run checked-in compressed/frontend smoke preset"
  echo "  --cycle-budget-mode <mode>   off|hybrid|strict (default: $CYCLE_BUDGET_MODE)"
  echo "  --cycle-budget-scale <f>     Override learned-budget scale"
  echo "  --cycle-budget-slack <n>     Override learned-budget slack"
  echo "  --cycle-budget-file <path>   Cycle budget database (default: $CYCLE_BUDGET_FILE)"
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
    --smoke-rv32f-core)
      SMOKE_RV32F_CORE=true
      shift
      ;;
    --smoke-rv32f-arith)
      SMOKE_RV32F_ARITH=true
      shift
      ;;
    --smoke-rv32f-longlat)
      SMOKE_RV32F_LONGLAT=true
      shift
      ;;
    --smoke-zb-core)
      SMOKE_ZB_CORE=true
      shift
      ;;
    --smoke-c-front)
      SMOKE_C_FRONT=true
      shift
      ;;
    --cycle-budget-mode)
      CYCLE_BUDGET_MODE="$2"
      shift 2
      ;;
    --cycle-budget-scale)
      CYCLE_BUDGET_SCALE="$2"
      shift 2
      ;;
    --cycle-budget-slack)
      CYCLE_BUDGET_SLACK="$2"
      shift 2
      ;;
    --cycle-budget-file)
      CYCLE_BUDGET_FILE="$2"
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

if [[ ! "$CYCLE_BUDGET_MODE" =~ ^(off|hybrid|strict)$ ]]; then
  echo "Error: --cycle-budget-mode must be one of: off, hybrid, strict"
  exit 2
fi

if [[ "$FAST_RV64F" = true && "$FAST_RV32F" = true ]]; then
  echo "Error: --fast-rv64f and --fast-rv32f cannot be combined"
  exit 2
fi

SMOKE_COUNT=0
for flag in "$SMOKE_RV32F_CORE" "$SMOKE_RV32F_ARITH" "$SMOKE_RV32F_LONGLAT" "$SMOKE_ZB_CORE" "$SMOKE_C_FRONT"; do
  [[ "$flag" = true ]] && SMOKE_COUNT=$((SMOKE_COUNT + 1))
done
if [[ "$SMOKE_COUNT" -gt 1 ]]; then
  echo "Error: only one --smoke-* preset can be selected"
  exit 2
fi
if [[ "$SMOKE_COUNT" -gt 0 && -n "$TESTS" ]]; then
  echo "Error: --smoke-* cannot be combined with --tests"
  exit 2
fi
if [[ "$SMOKE_COUNT" -gt 0 && ( "$FAST_RV64F" = true || "$FAST_RV32F" = true ) ]]; then
  echo "Error: --smoke-* cannot be combined with --fast-rv64f/--fast-rv32f"
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

if [[ "$SMOKE_COUNT" -gt 0 ]]; then
  PRESET_NAME=""
  if [[ "$SMOKE_RV32F_CORE" = true ]]; then PRESET_NAME="rv32f-core"; fi
  if [[ "$SMOKE_RV32F_ARITH" = true ]]; then PRESET_NAME="rv32f-arith"; fi
  if [[ "$SMOKE_RV32F_LONGLAT" = true ]]; then PRESET_NAME="rv32f-longlat"; fi
  if [[ "$SMOKE_ZB_CORE" = true ]]; then PRESET_NAME="zb-core"; fi
  if [[ "$SMOKE_C_FRONT" = true ]]; then PRESET_NAME="c-front"; fi
  TESTS="$(python3 - "$PRESET_NAME" "/Users/mahir/fun/borb/verif/automation/smoke_presets.json" <<'PY'
import json
import sys

preset = sys.argv[1]
path = sys.argv[2]
with open(path, "r", encoding="utf-8") as f:
    data = json.load(f)
tests = data.get("presets", {}).get(preset, [])
if not tests:
    raise SystemExit(f"Smoke preset not found or empty: {preset}")
print(",".join(tests))
PY
)"
  echo "Selected smoke preset $PRESET_NAME ($(echo "$TESTS" | tr ',' '\n' | wc -l | tr -d ' ') tests)"
fi

if [[ -d "$(pwd)/oss-cad-suite/bin" ]]; then
  export PATH="$(pwd)/oss-cad-suite/bin:$PATH"
fi

if [ -n "${PYENV_VERSION:-}" ]; then
  :
elif command -v pyenv >/dev/null 2>&1; then
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
  sbt --batch --no-server --no-share --no-global --sbt-dir "$SBT_GLOBAL_DIR" --sbt-boot "$SBT_BOOT_DIR" --ivy "$IVY_HOME" "runMain borb.SoC"
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

RESOLVED_BUDGET_FILE="$WORK_DIR/resolved_budgets.json"
python3 - "$CYCLE_BUDGET_FILE" "$RESOLVED_BUDGET_FILE" "$TESTS" "$CYCLE_BUDGET_MODE" "$CYCLE_BUDGET_SCALE" "$CYCLE_BUDGET_SLACK" "/Users/mahir/fun/borb/verif/automation/overnight_queue.json" <<'PY'
import json
import os
import sys
from pathlib import Path

budget_file, out_file, tests_csv, mode, scale_override, slack_override, queue_file = sys.argv[1:]
tests = [t.strip() for t in tests_csv.split(",") if t.strip()]
scale_override = float(scale_override) if scale_override else None
slack_override = int(slack_override) if slack_override else None

DEFAULTS = {
    "rv32f.move": {"scale": 4.0, "slack": 500},
    "rv32f.compare": {"scale": 4.0, "slack": 500},
    "rv32f.classify": {"scale": 4.0, "slack": 500},
    "rv32f.convert": {"scale": 6.0, "slack": 2000},
    "rv32f.minmax": {"scale": 6.0, "slack": 2000},
    "rv32f.addsub": {"scale": 6.0, "slack": 2000},
    "rv32f.mul": {"scale": 6.0, "slack": 2000},
    "rv32f.fma": {"scale": 8.0, "slack": 2000},
    "rv32f.divsqrt": {"scale": 20.0, "slack": 10000},
    "zb.core": {"scale": 4.0, "slack": 1000},
    "c.frontend": {"scale": 4.0, "slack": 1000},
    "rv64.base": {"scale": 4.0, "slack": 1000},
    "default": {"scale": 6.0, "slack": 2000},
}
FALLBACK_BUDGETS = {
    "rv32f.move": 200000,
    "rv32f.compare": 200000,
    "rv32f.classify": 200000,
    "rv32f.convert": 200000,
    "rv32f.minmax": 200000,
    "rv32f.addsub": 200000,
    "rv32f.mul": 200000,
    "rv32f.fma": 250000,
    "rv32f.divsqrt": 400000,
    "zb.core": 200000,
    "c.frontend": 200000,
    "rv64.base": 200000,
    "default": 200000,
}

def classify(test_name):
    t = test_name.lower()
    if t.startswith("fmv."):
        return "rv32f.move", "rv32f.move"
    if t.startswith("fclass"):
        return "rv32f.classify", "rv32f.classify"
    if t.startswith("fsgnj"):
        return "rv32f.move", "rv32f.move"
    if t.startswith(("feq", "fle", "flt")):
        return "rv32f.compare", "rv32f.compare"
    if t.startswith("fcvt"):
        return "rv32f.convert", "rv32f.convert"
    if t.startswith(("fmin", "fmax")):
        return "rv32f.minmax", "rv32f.minmax"
    if t.startswith(("fadd", "fsub")):
        return "rv32f.addsub", "rv32f.addsub"
    if t.startswith("fmul"):
        return "rv32f.mul", "rv32f.mul"
    if t.startswith(("fmadd", "fmsub", "fnmadd", "fnmsub")):
        return "rv32f.fma", "rv32f.fma"
    if t.startswith(("fdiv", "fsqrt")):
        return "rv32f.divsqrt", "rv32f.divsqrt"
    if t.startswith("c"):
        return "c.frontend", "c.frontend"
    if t.startswith(("andn", "orn", "xnor", "clz", "ctz", "cpop", "max", "maxu", "min", "minu", "rol", "ror", "rori", "orc.b", "rev8", "sext.b", "sext.h", "zext.h", "bclr", "bclri", "bext", "bexti", "binv", "binvi", "bset", "bseti", "sh1add", "sh2add", "sh3add")):
        return "zb.core", "zb.core"
    return "rv64.base", "default"

queue_map = {}
if Path(queue_file).exists():
    with open(queue_file, "r", encoding="utf-8") as f:
        q = json.load(f) or {}
    for family in q.get("families", []):
        fam_name = family.get("name")
        budget_class = family.get("budget_class", "default")
        for test in family.get("tests", []):
            queue_map[test] = (fam_name, budget_class)

db = {"families": {}, "tests": {}}
if Path(budget_file).exists():
    with open(budget_file, "r", encoding="utf-8") as f:
        db = json.load(f) or db

resolved = {"mode": mode, "tests": {}}
for test in tests:
    family, budget_class = queue_map.get(test, classify(test))
    if budget_class not in DEFAULTS:
        budget_class = "default"
    defaults = DEFAULTS[budget_class]
    scale = scale_override if scale_override is not None else defaults["scale"]
    slack = slack_override if slack_override is not None else defaults["slack"]
    test_db = db.get("tests", {}).get(test, {})
    family_db = db.get("families", {}).get(budget_class, {})
    budget = test_db.get("budget")
    if budget is None:
        budget = family_db.get("budget")
    if budget is None:
        budget = FALLBACK_BUDGETS.get(budget_class, FALLBACK_BUDGETS["default"])
    if mode == "strict" and budget is None:
        raise SystemExit(f"Strict budget mode requires budget for {test}")
    resolved["tests"][test] = {
        "family": family,
        "budget_class": budget_class,
        "budget": int(budget),
        "scale": scale,
        "slack": slack,
    }

Path(out_file).parent.mkdir(parents=True, exist_ok=True)
with open(out_file, "w", encoding="utf-8") as f:
    json.dump(resolved, f, indent=2, sort_keys=True)
    f.write("\n")
PY

export BORB_CYCLE_BUDGET_MODE="$CYCLE_BUDGET_MODE"
export BORB_CYCLE_BUDGET_FILE="$CYCLE_BUDGET_FILE"
export BORB_RESOLVED_BUDGET_FILE="$RESOLVED_BUDGET_FILE"
export BORB_MAX_CYCLES_DEFAULT="200000"

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

python3 - "$WORK_DIR" "$CYCLE_BUDGET_FILE" "$RESOLVED_BUDGET_FILE" <<'PY'
import json
import sys
from pathlib import Path

work_dir = Path(sys.argv[1])
budget_file = Path(sys.argv[2])
resolved_file = Path(sys.argv[3])

resolved = {"tests": {}}
if resolved_file.exists():
    resolved = json.loads(resolved_file.read_text(encoding="utf-8"))

def compare_lines(a: Path, b: Path):
    if not a.exists() or not b.exists():
        return False
    return a.read_text(encoding="utf-8", errors="ignore").splitlines() == b.read_text(encoding="utf-8", errors="ignore").splitlines()

summary = {"tests": [], "counts": {"pass": 0, "timeout": 0, "mismatch": 0, "infra": 0, "build": 0}}
db = {"families": {}, "tests": {}}
if budget_file.exists():
    db = json.loads(budget_file.read_text(encoding="utf-8")) or db

for status_path in sorted(work_dir.glob("**/dut/borb.status.json")):
    test_dir = status_path.parent.parent
    test_name = status_path.parent.parent.name
    status = json.loads(status_path.read_text(encoding="utf-8"))
    perf_path = status_path.parent / "borb.perf.json"
    perf = json.loads(perf_path.read_text(encoding="utf-8")) if perf_path.exists() else {}
    dut_sig = status_path.parent / "DUT-borb.signature"
    ref_sig = test_dir / "ref" / "Reference-spike.signature"
    timeout = bool(status.get("timeout")) or bool(perf.get("sim", {}).get("timeout"))
    if status.get("failure_kind") == "build":
        final = "build"
    elif timeout:
        final = "timeout"
    elif status.get("sim_exit_code") not in (0, None):
        final = "infra"
    elif compare_lines(dut_sig, ref_sig):
        final = "pass"
    elif dut_sig.exists() and ref_sig.exists():
        final = "mismatch"
    else:
        final = "infra"
    cycles = perf.get("sim", {}).get("cycles_executed")
    budget_info = resolved.get("tests", {}).get(status.get("test"), {})
    entry = {
        "test": status.get("test", test_name),
        "family": budget_info.get("family", status.get("family")),
        "budget_class": budget_info.get("budget_class", status.get("budget_class")),
        "status": final,
        "cycles": cycles,
        "budget_used": status.get("max_cycles"),
        "timeout": timeout,
        "artifact_dir": str(status_path.parent),
    }
    summary["tests"].append(entry)
    summary["counts"][final] = summary["counts"].get(final, 0) + 1
    if final != "pass" or cycles is None:
        continue
    budget_class = entry["budget_class"] or "default"
    family_db = db.setdefault("families", {}).setdefault(budget_class, {})
    test_db = db.setdefault("tests", {}).setdefault(entry["test"], {})
    scale = budget_info.get("scale", 6.0)
    slack = budget_info.get("slack", 2000)
    prev_family = int(family_db.get("max_pass_cycles", 0))
    prev_test = int(test_db.get("max_pass_cycles", 0))
    family_db["max_pass_cycles"] = max(prev_family, int(cycles))
    family_db["budget"] = int(family_db["max_pass_cycles"] * scale + slack)
    family_db["scale"] = scale
    family_db["slack"] = slack
    test_db["max_pass_cycles"] = max(prev_test, int(cycles))
    test_db["budget"] = int(test_db["max_pass_cycles"] * scale + slack)
    test_db["scale"] = scale
    test_db["slack"] = slack
    test_db["budget_class"] = budget_class
    test_db["family"] = entry["family"]

(work_dir / "borb_run_summary.json").write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
budget_file.write_text(json.dumps(db, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY

echo "=== RISCOF complete ==="
exit "$RUN_STATUS"
