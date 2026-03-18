#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import shlex
import subprocess
import sys
from collections import deque
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence, Set, Tuple


ROOT = Path("/Users/mahir/fun/borb")
RUN_RISCOF = ROOT / "run_riscof.sh"
RISCOF_WORK = ROOT / "verif" / "riscof" / "riscof_work"
RISCOF_SUMMARY = RISCOF_WORK / "borb_run_summary.json"
DEFAULT_BASELINE = ROOT / "verif" / "automation" / "riscof_regression_baseline.json"
DEFAULT_OUT_ROOT = ROOT / "verif" / "automation" / "regression_gate"


@dataclass(frozen=True)
class SuiteSpec:
    name: str
    mode_args: Tuple[str, ...] = ()
    test_patterns: Tuple[str, ...] = ()
    note: str = ""


SUITES: Dict[str, SuiteSpec] = {
    "rv32f.full": SuiteSpec(
        name="rv32f.full",
        mode_args=("--fast-rv32f",),
        note="Full RV32F arch suite",
    ),
    "rv64f.full": SuiteSpec(
        name="rv64f.full",
        mode_args=("--fast-rv64f",),
        note="Full RV64F arch suite",
    ),
    "rv64i.full": SuiteSpec(
        name="rv64i.full",
        mode_args=("--fast-rv64i",),
        note="Full RV64I arch suite",
    ),
    "rv64c.full": SuiteSpec(
        name="rv64c.full",
        test_patterns=("verif/riscof/riscv-arch-test/riscv-test-suite/rv64i_m/C/src/*.S",),
        note="Full RV64C suite",
    ),
    "rv64a.full": SuiteSpec(
        name="rv64a.full",
        test_patterns=("verif/riscof/riscv-arch-test/riscv-test-suite/rv64i_m/A/src/*.S",),
        note="Full RV64A suite",
    ),
    "rv64priv.full": SuiteSpec(
        name="rv64priv.full",
        test_patterns=("verif/riscof/riscv-arch-test/riscv-test-suite/rv64i_m/privilege/src/*.S",),
        note="Full privilege suite",
    ),
    "rv64pmp.full": SuiteSpec(
        name="rv64pmp.full",
        test_patterns=("verif/riscof/riscv-arch-test/riscv-test-suite/rv64i_m/pmp/src/*.S",),
        note="Full PMP suite",
    ),
    "rv64vm_sv39.full": SuiteSpec(
        name="rv64vm_sv39.full",
        test_patterns=("verif/riscof/riscv-arch-test/riscv-test-suite/rv64i_m/vm_sv39/src/*.S",),
        note="Full Sv39 VM suite",
    ),
    "rv64vm_pmp.full": SuiteSpec(
        name="rv64vm_pmp.full",
        test_patterns=("verif/riscof/riscv-arch-test/riscv-test-suite/rv64i_m/vm_pmp/src/*/*.S",),
        note="Full VM+PMP suite",
    ),
}


IMPACT_SUITES: Dict[str, Tuple[str, ...]] = {
    "frontend": ("rv64i.full", "rv64c.full", "rv32f.full"),
    "fpu": ("rv32f.full", "rv64f.full", "rv64i.full"),
    "lsu": (
        "rv64i.full",
        "rv64c.full",
        "rv64a.full",
        "rv64priv.full",
        "rv64pmp.full",
        "rv64vm_sv39.full",
        "rv64vm_pmp.full",
        "rv32f.full",
    ),
    "atomics": ("rv64a.full", "rv64i.full", "rv64priv.full", "rv64pmp.full"),
    "trap_csr": (
        "rv64priv.full",
        "rv64pmp.full",
        "rv64vm_sv39.full",
        "rv64vm_pmp.full",
        "rv64i.full",
        "rv64c.full",
    ),
    "pmp": ("rv64pmp.full", "rv64vm_pmp.full", "rv64priv.full", "rv64i.full"),
    "vm": ("rv64vm_sv39.full", "rv64vm_pmp.full", "rv64priv.full", "rv64i.full", "rv64a.full"),
    "dispatch_core": ("rv32f.full", "rv64i.full", "rv64c.full", "rv64a.full", "rv64priv.full"),
    "global_core": (
        "rv32f.full",
        "rv64f.full",
        "rv64i.full",
        "rv64c.full",
        "rv64a.full",
        "rv64priv.full",
        "rv64pmp.full",
        "rv64vm_sv39.full",
        "rv64vm_pmp.full",
    ),
}


PATH_IMPACTS: Tuple[Tuple[str, Tuple[str, ...]], ...] = (
    ("src/main/execute/Lsu.scala", ("lsu", "atomics", "pmp", "vm")),
    ("src/main/memory/", ("lsu", "pmp", "vm")),
    ("src/main/backend/TrapCsrBackend.scala", ("trap_csr", "pmp", "vm")),
    ("src/main/backend/", ("trap_csr",)),
    ("src/main/dispatch/", ("dispatch_core",)),
    ("src/main/CPU.scala", ("global_core",)),
    ("src/main/execute/writeBack.scala", ("global_core",)),
    ("src/main/fetch/", ("frontend",)),
    ("src/main/execute/IntAlu.scala", ("dispatch_core",)),
    ("src/main/execute/branch.scala", ("frontend", "dispatch_core")),
    ("src/main/backend/FpBackend.scala", ("fpu", "dispatch_core")),
    ("src/main/formal/RvfiPlugin.scala", ("global_core",)),
)


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    ap = argparse.ArgumentParser(description="Run a regression gate against a known RISCOF baseline.")
    ap.add_argument("--baseline", default=str(DEFAULT_BASELINE), help="Baseline JSON path")
    ap.add_argument("--out-root", default=str(DEFAULT_OUT_ROOT), help="Artifact root")
    ap.add_argument("--list", action="store_true", help="List suites and impacts")
    ap.add_argument("--refresh-baseline", action="store_true", help="Refresh the baseline from the current riscof_work summary")
    ap.add_argument("--impact", action="append", default=[], help="Explicit impact tag to run")
    ap.add_argument("--suite", action="append", default=[], help="Explicit suite to run")
    ap.add_argument("--changed-files", default="", help="Comma-separated changed file paths used for impact inference")
    ap.add_argument("--reuse-existing-build", action="store_true", help="Reuse the existing generated RTL/simulator for the first suite too")
    ap.add_argument("--stream-riscof", action="store_true", help="Stream full run_riscof.sh output instead of capturing to log files")
    ap.add_argument("--tail-lines", type=int, default=40, help="Number of log lines to print on failure when not streaming")
    ap.add_argument("--sim-jobs", type=int, default=8, help="Build jobs passed to run_riscof.sh")
    ap.add_argument("--sim-threads", type=int, default=max(2, min((os.cpu_count() or 4) // 2, 8)), help="Runtime threads passed to run_riscof.sh")
    ap.add_argument("--verilate-jobs", type=int, default=8, help="Verilator jobs passed to run_riscof.sh")
    return ap.parse_args(argv)


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def canonical_test_id(entry: Dict[str, object]) -> str:
    artifact_dir = entry.get("artifact_dir")
    if artifact_dir:
        path = Path(str(artifact_dir)).resolve()
        parts = list(path.parts)
        for idx, part in enumerate(parts):
            if part.startswith("riscof_work"):
                rel_parts = parts[idx + 1 :]
                if rel_parts and rel_parts[-1] == "dut":
                    rel_parts = rel_parts[:-1]
                if rel_parts:
                    return "/".join(rel_parts)
    return str(entry.get("test") or "")


def load_summary(path: Path) -> Dict[str, object]:
    return json.loads(path.read_text(encoding="utf-8"))


def normalize_summary(summary: Dict[str, object]) -> Dict[str, object]:
    tests = {}
    for entry in summary.get("tests", []):
        test_id = canonical_test_id(entry)
        tests[test_id] = {
            "test": entry.get("test"),
            "status": entry.get("status"),
            "cycles": entry.get("cycles"),
            "artifact_dir": entry.get("artifact_dir"),
        }
    return {
        "generated_at": now_iso(),
        "counts": dict(summary.get("counts", {})),
        "tests": tests,
    }


def refresh_baseline(baseline_path: Path) -> int:
    if not RISCOF_SUMMARY.exists():
        print(f"Missing current RISCOF summary: {RISCOF_SUMMARY}", file=sys.stderr)
        return 2
    baseline = normalize_summary(load_summary(RISCOF_SUMMARY))
    baseline_path.parent.mkdir(parents=True, exist_ok=True)
    baseline_path.write_text(json.dumps(baseline, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Wrote baseline: {baseline_path}")
    print(f"Captured tests: {len(baseline['tests'])}")
    return 0


def list_mode() -> int:
    print("Suites:")
    for suite in SUITES.values():
        print(f"  {suite.name}: {suite.note}")
    print("\nImpacts:")
    for impact, suites in sorted(IMPACT_SUITES.items()):
        print(f"  {impact}: {', '.join(suites)}")
    return 0


def infer_changed_files(raw: str) -> List[str]:
    if raw.strip():
        return [item.strip() for item in raw.split(",") if item.strip()]
    proc = subprocess.run(
        ["git", "status", "--porcelain"],
        cwd=str(ROOT),
        text=True,
        capture_output=True,
        check=False,
    )
    changed: List[str] = []
    for line in proc.stdout.splitlines():
        if not line:
            continue
        path = line[3:].strip()
        if path:
            changed.append(path)
    return changed


def infer_impacts(changed_files: Sequence[str]) -> Set[str]:
    impacts: Set[str] = set()
    for path in changed_files:
        matched = False
        for prefix, tags in PATH_IMPACTS:
            if path == prefix or path.startswith(prefix):
                impacts.update(tags)
                matched = True
        if not matched and path.startswith("src/main/"):
            impacts.add("global_core")
    return impacts


def expand_suite(spec: SuiteSpec) -> List[str]:
    if spec.mode_args:
        return list(spec.mode_args)
    tests: List[str] = []
    seen: Set[str] = set()
    for pattern in spec.test_patterns:
        for path in sorted(ROOT.glob(pattern)):
            name = path.name
            if name not in seen:
                tests.append(name)
                seen.add(name)
    if not tests:
        raise RuntimeError(f"Suite {spec.name} resolved to no tests")
    return ["--tests", ",".join(tests)]


def suite_command(spec: SuiteSpec, args: argparse.Namespace, reuse: bool) -> List[str]:
    cmd = [
        str(RUN_RISCOF),
        "--skip-validate",
        "--fast-sim",
        "--sim-jobs",
        str(args.sim_jobs),
        "--sim-threads",
        str(args.sim_threads),
        "--verilate-jobs",
        str(args.verilate_jobs),
    ]
    if reuse:
        cmd += ["--skip-gen", "--skip-build", "--no-clean-build"]
    cmd += expand_suite(spec)
    return cmd


def riscof_env() -> Dict[str, str]:
    env = dict(os.environ)
    path_parts = env.get("PATH", "").split(os.pathsep) if env.get("PATH") else []
    extra = [
        str(Path.home() / ".pyenv" / "bin"),
        str(ROOT / "oss-cad-suite" / "bin"),
    ]
    merged: List[str] = []
    for item in extra + path_parts:
        if item and item not in merged:
            merged.append(item)
    env["PATH"] = os.pathsep.join(merged)
    return env


def copy_summary(out_dir: Path, suite_name: str) -> None:
    if not RISCOF_SUMMARY.exists():
        return
    dst = out_dir / f"{suite_name}.summary.json"
    dst.write_text(RISCOF_SUMMARY.read_text(encoding="utf-8"), encoding="utf-8")


def compare_suite(
    suite_name: str,
    summary: Dict[str, object],
    baseline: Dict[str, object],
) -> Dict[str, object]:
    current_tests = normalize_summary(summary)["tests"]
    baseline_tests = baseline.get("tests", {})
    regressions = []
    improvements = []
    persistent = []
    new_tests = []
    for test_id, current in sorted(current_tests.items()):
        expected = baseline_tests.get(test_id)
        current_status = current.get("status")
        expected_status = expected.get("status") if expected else None
        row = {
            "id": test_id,
            "test": current.get("test"),
            "current": current_status,
            "expected": expected_status,
        }
        if expected is None:
            if current_status != "pass":
                new_tests.append(row)
            continue
        if expected_status == "pass" and current_status != "pass":
            regressions.append(row)
        elif expected_status != "pass" and current_status == "pass":
            improvements.append(row)
        elif expected_status != "pass" and current_status != "pass":
            persistent.append(row)
    return {
        "suite": suite_name,
        "regressions": regressions,
        "improvements": improvements,
        "persistent_failures": persistent,
        "new_unexpected_failures": new_tests,
        "counts": dict(summary.get("counts", {})),
    }


def render_cmd(cmd: Sequence[str]) -> str:
    return " ".join(shlex.quote(part) for part in cmd)


def shell_wrapped_cmd(cmd: Sequence[str]) -> List[str]:
    inner = 'eval "$(pyenv init -)"; pyenv shell 3.8.18; ' + render_cmd(cmd)
    return ["/bin/zsh", "-lc", inner]


def tail_lines(path: Path, limit: int) -> List[str]:
    if limit <= 0 or not path.exists():
        return []
    with path.open("r", encoding="utf-8", errors="replace") as fh:
        return list(deque((line.rstrip("\n") for line in fh), maxlen=limit))


def run_suite_command(
    wrapped_cmd: Sequence[str],
    out_dir: Path,
    suite_name: str,
    stream: bool,
) -> Tuple[subprocess.CompletedProcess[str], Optional[Path], Optional[Path]]:
    if stream:
        proc = subprocess.run(
            wrapped_cmd,
            cwd=str(ROOT),
            text=True,
            check=False,
            env=riscof_env(),
        )
        return proc, None, None

    stdout_path = out_dir / f"{suite_name}.stdout.log"
    stderr_path = out_dir / f"{suite_name}.stderr.log"
    with stdout_path.open("w", encoding="utf-8") as stdout_fh, stderr_path.open("w", encoding="utf-8") as stderr_fh:
        proc = subprocess.run(
            wrapped_cmd,
            cwd=str(ROOT),
            text=True,
            check=False,
            env=riscof_env(),
            stdout=stdout_fh,
            stderr=stderr_fh,
        )
    return proc, stdout_path, stderr_path


def choose_suites(args: argparse.Namespace, inferred_impacts: Iterable[str]) -> List[str]:
    chosen: List[str] = []
    seen: Set[str] = set()
    for suite in args.suite:
        if suite not in SUITES:
            raise SystemExit(f"Unknown suite: {suite}")
        if suite not in seen:
            chosen.append(suite)
            seen.add(suite)
    impact_order = list(args.impact) + [impact for impact in inferred_impacts if impact not in args.impact]
    for impact in impact_order:
        if impact not in IMPACT_SUITES:
            raise SystemExit(f"Unknown impact: {impact}")
        for suite in IMPACT_SUITES[impact]:
            if suite not in seen:
                chosen.append(suite)
                seen.add(suite)
    if not chosen:
        chosen = list(IMPACT_SUITES["global_core"])
    return chosen


def main(argv: Sequence[str]) -> int:
    args = parse_args(argv)
    if args.list:
        return list_mode()

    baseline_path = Path(args.baseline)
    if args.refresh_baseline:
        return refresh_baseline(baseline_path)

    if not baseline_path.exists():
        print(
            f"Missing baseline: {baseline_path}\n"
            f"Run: python3 {ROOT / 'scripts' / 'regression_gate.py'} --refresh-baseline",
            file=sys.stderr,
        )
        return 2

    changed_files = infer_changed_files(args.changed_files)
    inferred_impacts = sorted(infer_impacts(changed_files))
    suite_names = choose_suites(args, inferred_impacts)

    out_dir = Path(args.out_root) / datetime.now().strftime("%Y%m%d_%H%M%S")
    out_dir.mkdir(parents=True, exist_ok=True)
    baseline = json.loads(baseline_path.read_text(encoding="utf-8"))

    run_report = {
        "generated_at": now_iso(),
        "baseline": str(baseline_path),
        "changed_files": changed_files,
        "inferred_impacts": inferred_impacts,
        "suites": [],
    }

    print(f"Artifacts: {out_dir}")
    if changed_files:
        print("Changed files:")
        for path in changed_files:
            print(f"  {path}")
    print(f"Inferred impacts: {', '.join(inferred_impacts) if inferred_impacts else 'none -> global_core'}")
    print(f"Suite plan: {', '.join(suite_names)}")

    any_regressions = False
    for index, suite_name in enumerate(suite_names):
        spec = SUITES[suite_name]
        cmd = suite_command(spec, args, reuse=(index > 0 or args.reuse_existing_build))
        print(f"\n[{index + 1}/{len(suite_names)}] {suite_name}")
        wrapped_cmd = shell_wrapped_cmd(cmd)
        print(f"  cmd: {render_cmd(wrapped_cmd)}")
        if not args.stream_riscof:
            print(f"  logs: {(out_dir / f'{suite_name}.stdout.log')} | {(out_dir / f'{suite_name}.stderr.log')}")
        prev_mtime = RISCOF_SUMMARY.stat().st_mtime_ns if RISCOF_SUMMARY.exists() else None
        proc, stdout_log, stderr_log = run_suite_command(
            wrapped_cmd=wrapped_cmd,
            out_dir=out_dir,
            suite_name=suite_name,
            stream=args.stream_riscof,
        )
        if not RISCOF_SUMMARY.exists():
            print(f"  missing summary after {suite_name}", file=sys.stderr)
            return 2
        new_mtime = RISCOF_SUMMARY.stat().st_mtime_ns
        if prev_mtime is not None and new_mtime == prev_mtime:
            print(f"  summary was not refreshed for {suite_name}; refusing to compare stale results", file=sys.stderr)
            return 2
        summary = load_summary(RISCOF_SUMMARY)
        copy_summary(out_dir, suite_name)
        diff = compare_suite(suite_name, summary, baseline)
        diff["returncode"] = proc.returncode
        run_report["suites"].append(diff)

        regressions = diff["regressions"] + diff["new_unexpected_failures"]
        if proc.returncode != 0 or regressions:
            any_regressions = True

        counts = diff["counts"]
        print(
            "  counts:"
            f" pass={counts.get('pass', 0)}"
            f" timeout={counts.get('timeout', 0)}"
            f" mismatch={counts.get('mismatch', 0)}"
            f" infra={counts.get('infra', 0)}"
            f" build={counts.get('build', 0)}"
        )
        print(f"  regressions: {len(diff['regressions'])}")
        print(f"  new unexpected failures: {len(diff['new_unexpected_failures'])}")
        print(f"  improvements: {len(diff['improvements'])}")
        if proc.returncode != 0:
            print(f"  returncode: {proc.returncode}")
        if diff["regressions"]:
            for row in diff["regressions"][:10]:
                print(f"    REGRESSION {row['id']} expected={row['expected']} current={row['current']}")
        if diff["new_unexpected_failures"]:
            for row in diff["new_unexpected_failures"][:10]:
                print(f"    NEWFAIL {row['id']} expected=<missing> current={row['current']}")
        if diff["improvements"]:
            for row in diff["improvements"][:10]:
                print(f"    IMPROVEMENT {row['id']} expected={row['expected']} current={row['current']}")
        if not args.stream_riscof and (proc.returncode != 0 or regressions):
            stderr_tail = tail_lines(stderr_log, args.tail_lines) if stderr_log else []
            stdout_tail = tail_lines(stdout_log, args.tail_lines) if stdout_log else []
            if stderr_tail:
                print("  stderr tail:")
                for line in stderr_tail:
                    print(f"    {line}")
            elif stdout_tail:
                print("  stdout tail:")
                for line in stdout_tail:
                    print(f"    {line}")

    report_path = out_dir / "report.json"
    report_path.write_text(json.dumps(run_report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"\nReport: {report_path}")

    if any_regressions:
        print("Gate result: FAIL")
        return 1
    print("Gate result: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
