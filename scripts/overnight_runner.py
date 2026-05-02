#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Tuple

ROOT = Path("/Users/mahir/fun/borb")
DEFAULT_STATE = ROOT / "verif/automation/overnight_state.json"
DEFAULT_QUEUE = ROOT / "verif/automation/overnight_queue.json"
DEFAULT_STOP = ROOT / "verif/automation/STOP"
RUNS_DIR = ROOT / "verif/automation/runs"
BUDGET_FILE = ROOT / "verif/borb-sim/cycle_budgets.json"

LEVELS = ["local", "smoke", "medium", "wide"]


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def load_json(path: Path, default: Any) -> Any:
    if not path.exists():
        return default
    return json.loads(path.read_text(encoding="utf-8"))


def save_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def log_event(event_path: Path, kind: str, payload: Dict[str, Any]) -> None:
    row = {"ts": now_iso(), "event": kind}
    row.update(payload)
    with event_path.open("a", encoding="utf-8") as f:
        f.write(json.dumps(row, sort_keys=True) + "\n")


def append_log(log_path: Path, message: str) -> None:
    with log_path.open("a", encoding="utf-8") as f:
        f.write(f"[{now_iso()}] {message}\n")


def ensure_family_state(state: Dict[str, Any], family: Dict[str, Any]) -> Dict[str, Any]:
    fam_states = state.setdefault("families", {})
    name = family["name"]
    if name not in fam_states:
        fam_states[name] = {
            "level": family.get("promotion_level", "local"),
            "pass_streak": 0,
            "failure_streak": 0,
            "completed_jobs": 0,
            "failed_jobs": 0,
            "last_pass_ts": None,
            "last_fail_ts": None,
            "last_failure_kind": None,
            "last_artifact": None,
            "last_summary": None,
        }
    return fam_states[name]


def fingerprint_sources() -> str:
    roots = [
        ROOT / "src/main",
        ROOT / "verif/borb-sim/sim",
        ROOT / "build.sbt",
        ROOT / "project",
        ROOT / "run_act4.sh",
    ]
    h = hashlib.sha256()
    for root in roots:
        if not root.exists():
            continue
        if root.is_file():
            stat = root.stat()
            h.update(str(root).encode("utf-8"))
            h.update(str(int(stat.st_mtime_ns)).encode("utf-8"))
            h.update(str(stat.st_size).encode("utf-8"))
            continue
        for path in sorted(p for p in root.rglob("*") if p.is_file()):
            stat = path.stat()
            h.update(str(path).encode("utf-8"))
            h.update(str(int(stat.st_mtime_ns)).encode("utf-8"))
            h.update(str(stat.st_size).encode("utf-8"))
    return h.hexdigest()


def union_tests(*groups: List[str]) -> List[str]:
    seen = set()
    out: List[str] = []
    for group in groups:
        for test in group:
            if test and test not in seen:
                seen.add(test)
                out.append(test)
    return out


def build_job_spec(family: Dict[str, Any], family_state: Dict[str, Any], args: argparse.Namespace, state: Dict[str, Any]) -> Tuple[str, List[str], List[str]]:
    level = family_state["level"]
    if level == "wide" and (args.skip_wide or state.get("successes_since_wide", 0) < 8):
        level = "medium"

    tests = family.get("tests", [])
    smoke_tests = family.get("smoke_tests", [])
    medium_tests = family.get("medium_tests", [])
    wide_tests = family.get("wide_tests", [])

    run_args = [
        str(ROOT / "run_act4.sh"),
        "--profile", "rva23s64-full",
        "--verilate-jobs", "10",
        "--sim-jobs", "8",
        "--sim-threads", "1",
    ]
    if args.fast:
        run_args += ["--skip-gen", "--skip-build"]

    if family.get("extra_args"):
        run_args += list(family["extra_args"])
    return level, run_args, union_tests(tests, smoke_tests, medium_tests, wide_tests)


def classify_job_result(summary: Dict[str, Any], run_rc: int) -> str:
    counts = summary.get("counts", {})
    if counts.get("build", 0) > 0:
        return "build"
    if counts.get("infra", 0) > 0:
        return "infra"
    if counts.get("timeout", 0) > 0:
        return "timeout"
    if counts.get("mismatch", 0) > 0:
        return "mismatch"
    if counts.get("pass", 0) > 0 and run_rc == 0:
        return "pass"
    return "infra" if run_rc != 0 else "mismatch"


def latest_workdir_summary() -> Tuple[Path | None, Dict[str, Any]]:
    latest_workdir = ROOT / "verif/act4/work/borb-RVA23S64"
    summary = {"counts": {}}
    if latest_workdir and (latest_workdir / "borb_run_summary.json").exists():
        summary = load_json(latest_workdir / "borb_run_summary.json", {"counts": {}})
    return latest_workdir, summary


def update_family_progress(state: Dict[str, Any], family_state: Dict[str, Any], result: str, level_ran: str, event_path: Path) -> None:
    family_state["last_failure_kind"] = None if result == "pass" else result
    family_state["last_level_ran"] = level_ran
    if result == "pass":
        family_state["completed_jobs"] += 1
        family_state["pass_streak"] += 1
        family_state["failure_streak"] = 0
        family_state["last_pass_ts"] = now_iso()
        if level_ran in ("local", "smoke"):
            state["successes_since_wide"] = state.get("successes_since_wide", 0) + 1
        if level_ran == "local" and family_state["pass_streak"] >= 2 and family_state["level"] == "local":
            family_state["level"] = "smoke"
            family_state["pass_streak"] = 0
            log_event(event_path, "family_promoted", {"family": family_state["name"], "level": "smoke"})
        elif level_ran == "smoke" and family_state["pass_streak"] >= 2 and family_state["level"] == "smoke":
            family_state["level"] = "medium"
            family_state["pass_streak"] = 0
            log_event(event_path, "family_promoted", {"family": family_state["name"], "level": "medium"})
        elif level_ran == "medium" and family_state["level"] == "medium":
            family_state["level"] = "wide"
            family_state["pass_streak"] = 0
            state["successes_since_wide"] = 0
            log_event(event_path, "family_promoted", {"family": family_state["name"], "level": "wide"})
    else:
        family_state["failed_jobs"] += 1
        family_state["failure_streak"] += 1
        family_state["pass_streak"] = 0
        family_state["last_fail_ts"] = now_iso()
        if family_state["failure_streak"] >= 2:
            old_idx = LEVELS.index(family_state["level"])
            if old_idx > 0:
                family_state["level"] = LEVELS[old_idx - 1]
                family_state["failure_streak"] = 0
                log_event(event_path, "family_demoted", {"family": family_state["name"], "level": family_state["level"]})


def maintain_retention() -> None:
    RUNS_DIR.mkdir(parents=True, exist_ok=True)
    sessions = sorted([p for p in RUNS_DIR.iterdir() if p.is_dir() and p.name != "latest"])
    for old in sessions[:-5]:
        shutil.rmtree(old, ignore_errors=True)


def create_latest_link(session_dir: Path) -> None:
    latest = RUNS_DIR / "latest"
    if latest.is_symlink() or latest.is_file():
        latest.unlink()
    elif latest.exists():
        shutil.rmtree(latest, ignore_errors=True)
    latest.symlink_to(session_dir.name)


def pick_family(queue: Dict[str, Any], state: Dict[str, Any], family_filter: str | None) -> Dict[str, Any] | None:
    families = sorted(queue.get("families", []), key=lambda x: x.get("priority", 1000))
    for family in families:
        if family_filter and family["name"] != family_filter:
            continue
        if family.get("status", "active") != "active":
            continue
        fam_state = ensure_family_state(state, family)
        fam_state["name"] = family["name"]
        deps = family.get("depends_on", [])
        blocked = False
        for dep in deps:
            dep_state = state.get("families", {}).get(dep, {})
            if not dep_state.get("last_pass_ts"):
                blocked = True
                break
        if blocked:
            continue
        if not family.get("tests") and not family.get("medium_mode") and not family.get("wide_mode"):
            continue
        return family
    return None


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--hours", type=float, default=None)
    ap.add_argument("--state-file", default=str(DEFAULT_STATE))
    ap.add_argument("--queue-file", default=str(DEFAULT_QUEUE))
    ap.add_argument("--max-consecutive-infra-failures", type=int, default=3)
    ap.add_argument("--resume", action="store_true")
    ap.add_argument("--fast", action="store_true")
    ap.add_argument("--family", default=None)
    ap.add_argument("--skip-wide", action="store_true")
    ap.add_argument("--stop-file", default=str(DEFAULT_STOP))
    args = ap.parse_args()

    state_path = Path(args.state_file)
    queue_path = Path(args.queue_file)
    stop_path = Path(args.stop_file)
    state = load_json(state_path, {
        "current_family": None,
        "families": {},
        "global": {"last_build_fingerprint": None, "last_successful_build_ts": None},
        "successes_since_wide": 0,
        "consecutive_infra_failures": 0,
        "history": [],
    }) if args.resume else {
        "current_family": None,
        "families": {},
        "global": {"last_build_fingerprint": None, "last_successful_build_ts": None},
        "successes_since_wide": 0,
        "consecutive_infra_failures": 0,
        "history": [],
    }
    queue = load_json(queue_path, {"families": []})

    RUNS_DIR.mkdir(parents=True, exist_ok=True)
    session_dir = RUNS_DIR / datetime.now().strftime("%Y%m%d_%H%M%S")
    session_dir.mkdir(parents=True, exist_ok=True)
    create_latest_link(session_dir)
    maintain_retention()
    event_path = session_dir / "events.jsonl"
    log_path = session_dir / "session.log"
    save_json(session_dir / "session_meta.json", {"started_at": now_iso(), "args": vars(args)})

    deadline = time.monotonic() + args.hours * 3600 if args.hours is not None else None
    budget_mtime = BUDGET_FILE.stat().st_mtime if BUDGET_FILE.exists() else 0

    while True:
        if stop_path.exists():
            append_log(log_path, f"stop file detected: {stop_path}")
            log_event(event_path, "session_stopped", {"reason": "stop_file"})
            break
        if deadline is not None and time.monotonic() >= deadline:
            append_log(log_path, "hour budget exhausted")
            log_event(event_path, "session_stopped", {"reason": "time_budget"})
            break

        family = pick_family(queue, state, args.family)
        if family is None:
            append_log(log_path, "no runnable families remain")
            log_event(event_path, "session_stopped", {"reason": "no_runnable_families"})
            break

        fam_state = ensure_family_state(state, family)
        fam_state["name"] = family["name"]
        level, cmd, _ = build_job_spec(family, fam_state, args, state)

        fingerprint = fingerprint_sources()
        if state["global"].get("last_build_fingerprint") == fingerprint:
            cmd += ["--skip-gen", "--skip-build"]
        state["current_family"] = family["name"]
        state["last_session_dir"] = str(session_dir)
        save_json(state_path, state)

        job_name = f"{family['name']}:{level}"
        append_log(log_path, f"starting {job_name}")
        log_event(event_path, "job_started", {"family": family["name"], "level": level, "cmd": cmd})
        job_dir = session_dir / family["name"].replace("/", "_").replace(".", "_")
        job_dir.mkdir(parents=True, exist_ok=True)
        attempts: List[Dict[str, Any]] = []
        proc = None
        summary = {"counts": {}}
        latest_workdir = None
        result = "infra"
        retry_count = 0

        while True:
            start = time.time()
            proc = subprocess.run(cmd, cwd=str(ROOT), stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, check=False)
            duration = round(time.time() - start, 2)
            latest_workdir, summary = latest_workdir_summary()
            result = classify_job_result(summary, proc.returncode)
            attempt_idx = len(attempts) + 1
            (job_dir / f"{level}.attempt{attempt_idx}.log").write_text(proc.stdout, encoding="utf-8")
            if latest_workdir and (latest_workdir / "borb_run_summary.json").exists():
                shutil.copy2(latest_workdir / "borb_run_summary.json", job_dir / f"{level}.attempt{attempt_idx}.summary.json")
            attempts.append({
                "attempt": attempt_idx,
                "result": result,
                "returncode": proc.returncode,
                "duration_sec": duration,
                "work_dir": str(latest_workdir) if latest_workdir else None,
            })
            if result == "timeout" and retry_count == 0:
                retry_count += 1
                append_log(log_path, f"retrying {job_name} after timeout")
                log_event(event_path, "job_retry", {"family": family["name"], "level": level, "reason": "timeout"})
                continue
            break

        duration = round(sum(a["duration_sec"] for a in attempts), 2)
        latest_attempt = attempts[-1]
        (job_dir / f"{level}.log").write_text(proc.stdout if proc else "", encoding="utf-8")
        if latest_workdir and (latest_workdir / "borb_run_summary.json").exists():
            shutil.copy2(latest_workdir / "borb_run_summary.json", job_dir / f"{level}.summary.json")
        fam_state["last_artifact"] = str(job_dir)
        fam_state["last_summary"] = summary
        update_family_progress(state, fam_state, result, level, event_path)

        if result in ("infra", "build"):
            state["consecutive_infra_failures"] = state.get("consecutive_infra_failures", 0) + 1
            log_event(event_path, "infra_failure", {"family": family["name"], "result": result, "returncode": proc.returncode})
        else:
            state["consecutive_infra_failures"] = 0

        if result not in ("infra", "build"):
            state["global"]["last_build_fingerprint"] = fingerprint
            state["global"]["last_successful_build_ts"] = now_iso()

        new_budget_mtime = BUDGET_FILE.stat().st_mtime if BUDGET_FILE.exists() else budget_mtime
        if new_budget_mtime > budget_mtime:
            log_event(event_path, "timeout_budget_updated", {"budget_file": str(BUDGET_FILE)})
            budget_mtime = new_budget_mtime

        job_record = {
            "ts": now_iso(),
            "family": family["name"],
            "level": level,
            "result": result,
            "duration_sec": duration,
            "returncode": latest_attempt["returncode"],
            "attempts": attempts,
            "artifact_dir": str(job_dir),
            "work_dir": str(latest_workdir) if latest_workdir else None,
        }
        state.setdefault("history", []).append(job_record)
        state["history"] = state["history"][-200:]
        save_json(job_dir / f"{level}.result.json", job_record)
        save_json(state_path, state)
        append_log(log_path, f"finished {job_name} result={result} duration={duration}s")
        log_event(event_path, "job_finished", job_record)

        if state.get("consecutive_infra_failures", 0) >= args.max_consecutive_infra_failures:
            append_log(log_path, "max consecutive infra failures reached")
            log_event(event_path, "session_stopped", {"reason": "infra_failure_limit"})
            break

    state["current_family"] = None
    save_json(session_dir / "final_state.json", state)
    save_json(state_path, state)
    return 0


if __name__ == "__main__":
    sys.exit(main())
