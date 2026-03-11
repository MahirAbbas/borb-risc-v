#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import threading
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence, Set, TextIO, Tuple


ROOT_DIR = Path(__file__).resolve().parent.parent
RISCOF_WORK_DIR = ROOT_DIR / "verif" / "riscof" / "riscof_work"
RISCOF_SUMMARY_PATH = RISCOF_WORK_DIR / "borb_run_summary.json"
RISCOF_TESTLIST_PATH = RISCOF_WORK_DIR / "test_list.filtered.yaml"
DECLARED_ISA_PATH = ROOT_DIR / "verif" / "riscof" / "borb" / "borb_isa.yaml"
DEFAULT_OUT_ROOT = ROOT_DIR / "verif" / "full_validation" / "out"
STALL_COUNTERS = [
    "stalls_hazard",
    "stalls_fetch",
    "stalls_mem",
    "stalls_backend",
    "stalls_writeback",
    "stalls_commit",
    "stalls_muldiv_busy",
    "stalls_lsu_replay_or_wait",
    "stalls_dispatch_to_src",
    "stalls_src_to_exec",
    "stalls_exec_to_write",
]
TOKEN_ORDER = ["I", "M", "A", "F", "D", "C", "S", "U"]
NAMED_TOKEN_ORDER = ["Zicsr", "Zifencei"]
BANNED_FLAGS = {
    "--skip-gen",
    "--skip-build",
    "--skip-validate",
    "--tests",
    "--fast-rv64f",
    "--fast-rv32f",
    "--fast-rv64i",
    "--smoke-rv32f-core",
    "--smoke-rv32f-arith",
    "--smoke-rv32f-longlat",
    "--smoke-zb-core",
    "--smoke-c-front",
}


@dataclass
class CommandResult:
    returncode: int
    stdout_text: Optional[str] = None
    stderr_text: Optional[str] = None


def cpu_parallelism() -> Tuple[int, int, int]:
    cpu_count = max(1, os.cpu_count() or 1)
    jobs = min(cpu_count, 16)
    threads = max(2, min(max(1, cpu_count // 2), 8))
    return cpu_count, jobs, threads


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    banned_present = []
    i = 0
    while i < len(argv):
        arg = argv[i]
        if arg in BANNED_FLAGS:
            banned_present.append(arg)
            if arg == "--tests":
                i += 2
                continue
        i += 1
    if banned_present:
        raise SystemExit(
            "This suite only supports full runs; banned flags: " + ", ".join(sorted(set(banned_present)))
        )

    _, default_jobs, default_threads = cpu_parallelism()

    ap = argparse.ArgumentParser(description="Run a quiet full RISCOF validation and CoreMark report.")
    ap.add_argument("--sim-jobs", type=int, default=default_jobs, help="Parallel jobs for simulator build")
    ap.add_argument("--sim-threads", type=int, default=default_threads, help="Verilator runtime threads")
    ap.add_argument("--verilate-jobs", type=int, default=default_jobs, help="Parallel Verilator codegen jobs")
    ap.add_argument("--coremark-iterations", type=int, default=50, help="CoreMark iterations")
    ap.add_argument("--coremark-max-cycles", type=int, default=20_000_000, help="CoreMark max cycles")
    ap.add_argument("--coremark-cpu-hz", type=int, default=100_000_000, help="Clock rate used for CoreMark timing")
    ap.add_argument("--coremark-march", default=None, help="Override CoreMark GCC ISA string")
    ap.add_argument("--verbose-riscof", action="store_true", help="Stream live RISCOF output to the console")
    ap.add_argument("--out-root", default=str(DEFAULT_OUT_ROOT), help=argparse.SUPPRESS)
    args = ap.parse_args(argv)

    for name in ("sim_jobs", "sim_threads", "verilate_jobs", "coremark_iterations", "coremark_max_cycles", "coremark_cpu_hz"):
        if getattr(args, name) <= 0:
            raise SystemExit(f"--{name.replace('_', '-')} must be a positive integer")
    return args


def _relay_stream(stream: TextIO, log_file: TextIO, mirror: TextIO) -> None:
    for line in iter(stream.readline, ""):
        log_file.write(line)
        log_file.flush()
        mirror.write(line)
        mirror.flush()
    stream.close()


def run_logged(
    cmd: Sequence[str],
    stdout_path: Path,
    stderr_path: Path,
    *,
    verbose: bool = False,
    capture_output: bool = False,
) -> CommandResult:
    if verbose:
        with stdout_path.open("w", encoding="utf-8") as stdout_file, stderr_path.open("w", encoding="utf-8") as stderr_file:
            proc = subprocess.Popen(
                list(cmd),
                cwd=str(ROOT_DIR),
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )
            assert proc.stdout is not None
            assert proc.stderr is not None
            stdout_parts: List[str] = []
            stderr_parts: List[str] = []

            def tee_stdout() -> None:
                for line in iter(proc.stdout.readline, ""):
                    stdout_file.write(line)
                    stdout_file.flush()
                    sys.stdout.write(line)
                    sys.stdout.flush()
                    if capture_output:
                        stdout_parts.append(line)
                proc.stdout.close()

            def tee_stderr() -> None:
                for line in iter(proc.stderr.readline, ""):
                    stderr_file.write(line)
                    stderr_file.flush()
                    sys.stderr.write(line)
                    sys.stderr.flush()
                    if capture_output:
                        stderr_parts.append(line)
                proc.stderr.close()

            threads = [threading.Thread(target=tee_stdout), threading.Thread(target=tee_stderr)]
            for thread in threads:
                thread.start()
            for thread in threads:
                thread.join()
            return CommandResult(proc.wait(), "".join(stdout_parts) if capture_output else None, "".join(stderr_parts) if capture_output else None)

    if capture_output:
        proc = subprocess.run(list(cmd), cwd=str(ROOT_DIR), text=True, capture_output=True, check=False)
        stdout_path.write_text(proc.stdout or "", encoding="utf-8")
        stderr_path.write_text(proc.stderr or "", encoding="utf-8")
        return CommandResult(proc.returncode, proc.stdout, proc.stderr)

    with stdout_path.open("w", encoding="utf-8") as stdout_file, stderr_path.open("w", encoding="utf-8") as stderr_file:
        proc = subprocess.run(list(cmd), cwd=str(ROOT_DIR), text=True, stdout=stdout_file, stderr=stderr_file, check=False)
    return CommandResult(proc.returncode)


def parse_declared_isa(path: Path) -> str:
    pattern = re.compile(r"^\s*ISA:\s*(\S+)\s*$")
    for line in path.read_text(encoding="utf-8").splitlines():
        match = pattern.match(line)
        if match:
            return match.group(1)
    raise RuntimeError(f"Could not find ISA field in {path}")


def parse_testlist(path: Path) -> Dict[str, Dict[str, str]]:
    entries: Dict[str, Dict[str, str]] = {}
    current_top: Optional[str] = None
    current_work_dir: Optional[str] = None
    current_isa: Optional[str] = None

    def flush() -> None:
        if current_work_dir and current_isa:
            entries[str(Path(current_work_dir).resolve())] = {
                "test_path": current_top or "",
                "isa": current_isa,
                "test": Path(current_top or current_work_dir).name,
            }

    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.rstrip()
        if not line or line.lstrip().startswith("#"):
            continue
        if not line.startswith(" ") and line.endswith(":"):
            flush()
            current_top = line[:-1]
            current_work_dir = None
            current_isa = None
            continue
        if current_top is None:
            continue
        stripped = line.strip()
        if stripped.startswith("work_dir:"):
            current_work_dir = stripped.split(":", 1)[1].strip()
        elif stripped.startswith("isa:"):
            current_isa = stripped.split(":", 1)[1].strip()
    flush()
    if not entries:
        raise RuntimeError(f"Did not parse any test entries from {path}")
    return entries


def tokenize_isa(isa_string: str) -> Set[str]:
    match = re.match(r"^\s*RV(32|64)(.*)$", isa_string.strip(), re.IGNORECASE)
    if not match:
        raise RuntimeError(f"Unrecognized ISA string: {isa_string}")
    tokens: Set[str] = {f"RV{match.group(1)}"}
    rest = match.group(2)
    for canonical in NAMED_TOKEN_ORDER:
        pattern = re.compile(re.escape(canonical), re.IGNORECASE)
        if pattern.search(rest):
            tokens.add(canonical)
            rest = pattern.sub("", rest)
    letters = re.sub(r"[^A-Za-z]", "", rest).upper()
    for token in TOKEN_ORDER:
        if token in letters:
            tokens.add(token)
    return tokens


def render_isa(tokens: Iterable[str]) -> Optional[str]:
    token_set = set(tokens)
    xlen = "RV64" if "RV64" in token_set else "RV32" if "RV32" in token_set else None
    if xlen is None or "I" not in token_set:
        return None
    body = "".join(token for token in TOKEN_ORDER if token in token_set)
    named = [token for token in NAMED_TOKEN_ORDER if token in token_set]
    suffix = "".join(f"_{token}" for token in named)
    return f"{xlen}{body}{suffix}"


def load_riscof_summary(path: Path) -> Dict[str, object]:
    return json.loads(path.read_text(encoding="utf-8"))


def compute_validation(
    declared_isa: str,
    test_entries: Dict[str, Dict[str, str]],
    riscof_summary: Dict[str, object],
) -> Dict[str, object]:
    declared_tokens = tokenize_isa(declared_isa)
    test_status_by_workdir: Dict[str, Dict[str, object]] = {}
    for entry in riscof_summary.get("tests", []):
        artifact_dir = entry.get("artifact_dir")
        if artifact_dir:
            test_status_by_workdir[str(Path(artifact_dir).resolve().parent)] = dict(entry)

    selected_workdirs = sorted(test_entries.keys())
    missing_workdirs = [work_dir for work_dir in selected_workdirs if work_dir not in test_status_by_workdir]
    counts = riscof_summary.get("counts", {})
    clean_pass = (
        not missing_workdirs
        and int(counts.get("build", 0)) == 0
        and int(counts.get("infra", 0)) == 0
        and int(counts.get("mismatch", 0)) == 0
        and int(counts.get("timeout", 0)) == 0
        and int(counts.get("pass", 0)) == len(selected_workdirs)
    )

    token_results: Dict[str, Dict[str, object]] = {}
    for token in sorted(declared_tokens, key=lambda token: (0 if token.startswith("RV") else 1, token)):
        required_workdirs = []
        for work_dir, meta in test_entries.items():
            if token in tokenize_isa(meta["isa"]):
                required_workdirs.append(work_dir)
        if not required_workdirs:
            token_results[token] = {"required_tests": 0, "pass_tests": 0, "validated": False}
            continue
        pass_tests = sum(1 for work_dir in required_workdirs if test_status_by_workdir.get(work_dir, {}).get("status") == "pass")
        token_results[token] = {
            "required_tests": len(required_workdirs),
            "pass_tests": pass_tests,
            "validated": pass_tests == len(required_workdirs),
        }

    validated_tokens = {token for token, meta in token_results.items() if meta["validated"]}
    if "RV64" in declared_tokens:
        validated_tokens.discard("RV32")
    if "RV32" in declared_tokens:
        validated_tokens.discard("RV64")
    rendered = render_isa(validated_tokens) if clean_pass else None
    return {
        "declared_isa": declared_isa,
        "declared_tokens": sorted(declared_tokens),
        "validated_tokens": sorted(validated_tokens),
        "validated_isa": rendered,
        "clean_pass": clean_pass,
        "selected_tests": len(selected_workdirs),
        "missing_workdirs": missing_workdirs,
        "token_results": token_results,
    }


def failing_tests(riscof_summary: Dict[str, object]) -> List[Dict[str, object]]:
    return [entry for entry in riscof_summary.get("tests", []) if entry.get("status") != "pass"]


def choose_coremark_march(validation: Dict[str, object], override: Optional[str]) -> str:
    if override:
        return override
    tokens = set(validation["validated_tokens"])
    xlen = "64" if "RV64" in tokens else "32"
    if "M" in tokens:
        return f"rv{xlen}im_zicsr"
    return f"rv{xlen}i_zicsr"


def json_from_text(text: str) -> Dict[str, object]:
    stripped = text.strip()
    if not stripped:
        raise RuntimeError("Expected JSON output, got empty stdout")
    try:
        return json.loads(stripped)
    except json.JSONDecodeError:
        start = stripped.find("{")
        end = stripped.rfind("}")
        if start == -1 or end == -1 or end <= start:
            raise
        return json.loads(stripped[start : end + 1])


def read_json(path: Path) -> Dict[str, object]:
    return json.loads(path.read_text(encoding="utf-8"))


def pct(part: int, total: int) -> float:
    if total <= 0:
        return 0.0
    return (float(part) * 100.0) / float(total)


def build_stall_breakdown(perf: Dict[str, object]) -> List[Dict[str, object]]:
    counters = perf.get("counters", {})
    cycles = int(counters.get("cycles", 0) or 0)
    breakdown = []
    for counter in STALL_COUNTERS:
        count = int(counters.get(counter, 0) or 0)
        breakdown.append(
            {
                "name": counter,
                "count": count,
                "pct_cycles": pct(count, cycles),
            }
        )
    breakdown.sort(key=lambda entry: (-int(entry["count"]), str(entry["name"])))
    return breakdown


def derive_bottleneck(perf: Dict[str, object], stall_breakdown: List[Dict[str, object]]) -> str:
    counters = perf.get("counters", {})
    cycles = int(counters.get("cycles", 0) or 0)
    if cycles <= 0 or not stall_breakdown:
        return "No cycle data available."

    top = stall_breakdown[0]
    top_name = str(top["name"])
    top_pct = float(top["pct_cycles"])
    frontend_pending = int(counters.get("frontend_pending_req_cycles", 0) or 0)
    frontend_wait_cur = int(counters.get("frontend_wait_cur_beat", 0) or 0)
    frontend_wait_next = int(counters.get("frontend_wait_next_beat", 0) or 0)

    if top_name == "stalls_fetch":
        return (
            f"Frontend-limited: {top_name} is {top_pct:.2f}% of cycles"
            f" (pending_req={pct(frontend_pending, cycles):.2f}%,"
            f" wait_cur={pct(frontend_wait_cur, cycles):.2f}%,"
            f" wait_next={pct(frontend_wait_next, cycles):.2f}%)."
        )
    if top_name in {"stalls_mem", "stalls_lsu_replay_or_wait"}:
        return f"Memory-side pressure dominates: {top_name} is {top_pct:.2f}% of cycles."
    if top_name == "stalls_muldiv_busy":
        return f"Mul/div latency dominates: {top_name} is {top_pct:.2f}% of cycles."
    if top_name in {"stalls_dispatch_to_src", "stalls_src_to_exec", "stalls_exec_to_write", "stalls_writeback", "stalls_commit"}:
        return f"Backend handoff pressure dominates: {top_name} is {top_pct:.2f}% of cycles."
    if top_name == "stalls_backend":
        return f"Backend occupancy dominates: {top_name} is {top_pct:.2f}% of cycles."
    return f"Mixed profile with {top_name} as the top stall source at {top_pct:.2f}% of cycles."


def print_riscof_summary(riscof_summary: Dict[str, object], validation: Dict[str, object]) -> None:
    counts = riscof_summary.get("counts", {})
    selected = int(validation["selected_tests"])
    passed = int(counts.get("pass", 0))
    if validation["clean_pass"]:
        print(f"RISCOF: PASS ({passed}/{selected})")
        print(f"Validated ISA: {validation['validated_isa']} ({selected}/{selected} tests)")
        return

    print(
        "RISCOF: FAIL"
        f" (pass={counts.get('pass', 0)} timeout={counts.get('timeout', 0)}"
        f" mismatch={counts.get('mismatch', 0)} infra={counts.get('infra', 0)} build={counts.get('build', 0)})"
    )
    for entry in failing_tests(riscof_summary)[:10]:
        print(
            f"  {entry.get('status')}: {entry.get('test')}"
            f" cycles={entry.get('cycles')} artifact={entry.get('artifact_dir')}"
        )
    missing = validation.get("missing_workdirs", [])
    if missing:
        print(f"  missing status entries: {len(missing)}")


def print_coremark_summary(coremark_summary: Dict[str, object], perf: Dict[str, object], stall_breakdown: List[Dict[str, object]], bottleneck: str) -> None:
    derived = perf.get("derived", {})
    counters = perf.get("counters", {})
    score = coremark_summary.get("coremark_score")
    if score is None:
        score = derived.get("coremark_score_estimate")
    print(
        "CoreMark:"
        f" score={score}"
        f" coremark/MHz={derived.get('coremark_per_mhz')}"
        f" cycles={counters.get('cycles')}"
        f" instret={counters.get('instret')}"
        f" CPI={derived.get('cpi')}"
        f" IPC={derived.get('ipc')}"
    )
    print("Stalls:")
    for entry in stall_breakdown:
        print(f"  {entry['name']}: {entry['count']} ({entry['pct_cycles']:.2f}% cycles)")
    print(f"Bottleneck: {bottleneck}")


def copy_artifact(src: Path, dst_dir: Path) -> Optional[str]:
    if not src.exists():
        return None
    dst = dst_dir / src.name
    shutil.copy2(src, dst)
    return str(dst)


def ensure_exists(paths: Sequence[Path]) -> None:
    missing = [str(path) for path in paths if not path.exists()]
    if missing:
        raise RuntimeError("Missing required files:\n  " + "\n  ".join(missing))


def main(argv: Sequence[str]) -> int:
    args = parse_args(argv)
    artifact_dir = Path(args.out_root).resolve() / datetime.now().strftime("%Y%m%d_%H%M%S")
    artifact_dir.mkdir(parents=True, exist_ok=True)

    riscof_stdout = artifact_dir / "riscof.stdout.log"
    riscof_stderr = artifact_dir / "riscof.stderr.log"
    coremark_stdout = artifact_dir / "coremark.stdout.log"
    coremark_stderr = artifact_dir / "coremark.stderr.log"
    suite_summary_path = artifact_dir / "suite_summary.json"

    summary: Dict[str, object] = {
        "artifact_dir": str(artifact_dir),
        "logs": {
            "riscof_stdout": str(riscof_stdout),
            "riscof_stderr": str(riscof_stderr),
            "coremark_stdout": str(coremark_stdout),
            "coremark_stderr": str(coremark_stderr),
        },
    }

    print(f"Artifacts: {artifact_dir}")
    print(
        "RISCOF: running full compliance flow"
        f" (fast-sim, sim-jobs={args.sim_jobs}, sim-threads={args.sim_threads}, verilate-jobs={args.verilate_jobs})"
    )

    riscof_cmd = [
        str(ROOT_DIR / "run_riscof.sh"),
        "--fast-sim",
        "--sim-jobs",
        str(args.sim_jobs),
        "--sim-threads",
        str(args.sim_threads),
        "--verilate-jobs",
        str(args.verilate_jobs),
    ]
    riscof_result = run_logged(riscof_cmd, riscof_stdout, riscof_stderr, verbose=args.verbose_riscof, capture_output=False)

    riscof_summary: Optional[Dict[str, object]] = None
    validation: Optional[Dict[str, object]] = None
    if RISCOF_SUMMARY_PATH.exists() and RISCOF_TESTLIST_PATH.exists() and DECLARED_ISA_PATH.exists():
        declared_isa = parse_declared_isa(DECLARED_ISA_PATH)
        test_entries = parse_testlist(RISCOF_TESTLIST_PATH)
        riscof_summary = load_riscof_summary(RISCOF_SUMMARY_PATH)
        validation = compute_validation(declared_isa, test_entries, riscof_summary)
        print_riscof_summary(riscof_summary, validation)
        summary["riscof"] = riscof_summary
        summary["isa"] = validation
    else:
        print("RISCOF: missing summary artifacts after run")
        summary["riscof"] = {"returncode": riscof_result.returncode, "summary_missing": True}
        summary["isa"] = None

    summary["riscof_returncode"] = riscof_result.returncode
    summary["artifacts"] = {
        "riscof_summary": copy_artifact(RISCOF_SUMMARY_PATH, artifact_dir),
        "riscof_testlist": copy_artifact(RISCOF_TESTLIST_PATH, artifact_dir),
        "declared_isa": copy_artifact(DECLARED_ISA_PATH, artifact_dir),
    }

    coremark_section: Optional[Dict[str, object]] = None
    if riscof_result.returncode == 0 and riscof_summary is not None and validation is not None and validation["clean_pass"]:
        print("CoreMark: running benchmark")
        coremark_cmd = [
            str(ROOT_DIR / "run_coremark.sh"),
            "--profile",
            "--iterations",
            str(args.coremark_iterations),
            "--max-cycles",
            str(args.coremark_max_cycles),
            "--cpu-hz",
            str(args.coremark_cpu_hz),
            "--march",
            choose_coremark_march(validation, args.coremark_march),
        ]
        coremark_result = run_logged(coremark_cmd, coremark_stdout, coremark_stderr, capture_output=True)
        summary["coremark_returncode"] = coremark_result.returncode
        if coremark_result.returncode == 0 and coremark_result.stdout_text:
            coremark_summary = json_from_text(coremark_result.stdout_text)
            perf_path = Path(str(coremark_summary.get("perf_report")))
            perf = read_json(perf_path)
            stall_breakdown = build_stall_breakdown(perf)
            bottleneck = derive_bottleneck(perf, stall_breakdown)
            print_coremark_summary(coremark_summary, perf, stall_breakdown, bottleneck)
            coremark_section = {
                "summary": coremark_summary,
                "perf": perf,
                "stall_breakdown": stall_breakdown,
                "bottleneck": bottleneck,
            }
            summary["artifacts"]["coremark_summary"] = copy_artifact(Path(str(coremark_summary["out_dir"])) / "summary.json", artifact_dir)
            summary["artifacts"]["coremark_perf"] = copy_artifact(perf_path, artifact_dir)
        else:
            print(f"CoreMark: FAIL (see {coremark_stdout} and {coremark_stderr})")
            coremark_section = {"status": "failed"}
    else:
        print("CoreMark: skipped because RISCOF did not complete with a clean full pass")
        summary["coremark_returncode"] = None
        coremark_section = {"status": "skipped"}

    summary["coremark"] = coremark_section
    suite_summary_path.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Suite summary: {suite_summary_path}")

    if riscof_result.returncode != 0 or not validation or not validation["clean_pass"]:
        return 1
    if summary.get("coremark_returncode") not in (0, None):
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
