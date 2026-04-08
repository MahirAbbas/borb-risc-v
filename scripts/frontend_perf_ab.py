#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import subprocess
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List


REPO_ROOT = Path(__file__).resolve().parent.parent
HISTORY_PATH = REPO_ROOT / "docs" / "PERFORMANCE_HISTORY.md"


def run_cmd(cmd: List[str], env: Dict[str, str] | None = None, cwd: Path | None = None, capture: bool = False) -> subprocess.CompletedProcess[str]:
    merged_env = os.environ.copy()
    if env:
      merged_env.update(env)
    return subprocess.run(
        cmd,
        cwd=str(cwd or REPO_ROOT),
        env=merged_env,
        text=True,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.PIPE if capture else None,
        check=False,
    )


def ensure_history() -> None:
    if HISTORY_PATH.exists():
        return
    HISTORY_PATH.write_text(
        "# Performance History\n\n"
        "Tracking borb performance over time.\n\n"
        "| Version | Date (UTC) | Benchmark | Frontend | Cycles | Instret | CPI | IPC | CoreMark/MHz | Delta vs off |\n"
        "| --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |\n",
        encoding="utf-8",
    )


def summarize(summary_path: Path) -> Dict[str, Any]:
    data = json.loads(summary_path.read_text(encoding="utf-8"))
    perf = data.get("perf") or {}
    counters = perf.get("counters") or {}
    derived = perf.get("derived") or {}
    return {
        "summary_path": str(summary_path),
        "out_dir": data.get("out_dir"),
        "status": data.get("status"),
        "cycles": int(counters.get("cycles", 0)),
        "instret": int(counters.get("instret", 0)),
        "cpi": float(derived.get("cpi", 0.0)),
        "ipc": float(derived.get("ipc", 0.0)),
        "coremark_score": data.get("coremark_score"),
        "coremark_per_mhz": float(derived.get("coremark_per_mhz", 0.0)),
        "frontend_predicted_redirect": int(counters.get("frontend_predicted_redirect", 0)),
        "frontend_fast_predict_hit": int(counters.get("frontend_fast_predict_hit", 0)),
        "frontend_main_predict_hit": int(counters.get("frontend_main_predict_hit", 0)),
        "frontend_loop_predict_used": int(counters.get("frontend_loop_predict_used", 0)),
        "frontend_loop_predict_hit": int(counters.get("frontend_loop_predict_hit", 0)),
        "frontend_indirect_predict_hit": int(counters.get("frontend_indirect_predict_hit", 0)),
        "frontend_ras_use": int(counters.get("frontend_ras_use", 0)),
        "frontend_ras_repair": int(counters.get("frontend_ras_repair", 0)),
        "frontend_ftq_alloc": int(counters.get("frontend_ftq_alloc", 0)),
        "frontend_ftq_restore": int(counters.get("frontend_ftq_restore", 0)),
        "stalls_fetch": int(counters.get("stalls_fetch", 0)),
        "flushes": int(counters.get("flushes", 0)),
        "branches": int(counters.get("branches", 0)),
    }


def parse_trailing_json(stdout: str) -> Dict[str, Any]:
    start = stdout.find("{")
    if start == -1:
        raise ValueError("no JSON object found in command output")
    return json.loads(stdout[start:])


def append_history(version: str, benchmark: str, off: Dict[str, Any], on: Dict[str, Any]) -> None:
    ensure_history()
    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
    delta_pct = 0.0
    if off["cycles"]:
        delta_pct = ((off["cycles"] - on["cycles"]) / off["cycles"]) * 100.0
    with HISTORY_PATH.open("a", encoding="utf-8") as f:
        for mode, result in [("off", off), ("on", on)]:
            per_row_delta = 0.0 if mode == "off" else delta_pct
            f.write(
                f"| {version} | {now} | {benchmark} | {mode} | {result['cycles']} | {result['instret']} | "
                f"{result['cpi']:.6f} | {result['ipc']:.6f} | {result['coremark_per_mhz']:.6f} | {per_row_delta:.2f}% |\n"
            )


def main() -> int:
    ap = argparse.ArgumentParser(description="Run borb frontend A/B CoreMark comparison and log it.")
    ap.add_argument("--version", default="v0.01")
    ap.add_argument("--name", default="coremark_ab")
    ap.add_argument("--iterations", type=int, default=10)
    ap.add_argument("--max-cycles", type=int, default=5_000_000)
    ap.add_argument("--cpu-hz", type=int, default=100_000_000)
    args = ap.parse_args()

    results: Dict[str, Dict[str, Any]] = {}
    for label, enabled in [("off", "0"), ("on", "1")]:
        env = {"BORB_FRONTEND_ENABLE": enabled}
        run_name = f"{args.name}_{label}"

        gen = run_cmd(["sbt", "runMain borb.SoC"], env=env)
        if gen.returncode != 0:
            return gen.returncode

        build = run_cmd(["make", "-C", "verif/riscof/borb/sim", "clean", "all", "FAST=1", "THREADS=1"], env=env)
        if build.returncode != 0:
            return build.returncode

        coremark = run_cmd(
            [
                "python3",
                "scripts/coremark_runner.py",
                "--name",
                run_name,
                "--iterations",
                str(args.iterations),
                "--max-cycles",
                str(args.max_cycles),
                "--cpu-hz",
                str(args.cpu_hz),
                "--profile",
            ],
            env=env,
            capture=True,
        )
        if coremark.returncode != 0:
            if coremark.stdout:
                print(coremark.stdout)
            if coremark.stderr:
                print(coremark.stderr)
            return coremark.returncode

        if coremark.stdout:
            print(coremark.stdout)
        summary = parse_trailing_json(coremark.stdout)
        results[label] = summarize(Path(summary["out_dir"]) / "summary.json")

    off = results["off"]
    on = results["on"]
    append_history(args.version, "CoreMark", off, on)

    comparison = {
        "version": args.version,
        "benchmark": "CoreMark",
        "frontend_off": off,
        "frontend_on": on,
        "delta": {
            "cycles_pct": ((off["cycles"] - on["cycles"]) / off["cycles"] * 100.0) if off["cycles"] else 0.0,
            "ipc_pct": ((on["ipc"] - off["ipc"]) / off["ipc"] * 100.0) if off["ipc"] else 0.0,
            "coremark_per_mhz_pct": ((on["coremark_per_mhz"] - off["coremark_per_mhz"]) / off["coremark_per_mhz"] * 100.0) if off["coremark_per_mhz"] else 0.0,
        },
        "history_path": str(HISTORY_PATH),
    }
    print(json.dumps(comparison, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
