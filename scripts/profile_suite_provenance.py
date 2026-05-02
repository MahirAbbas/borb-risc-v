#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import subprocess
from pathlib import Path
from typing import Any, Dict, List


def git_out(args: List[str], cwd: Path) -> str:
    proc = subprocess.run(["git", *args], cwd=str(cwd), text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    return proc.stdout.strip() if proc.returncode == 0 else ""


def main() -> int:
    ap = argparse.ArgumentParser(description="Record checked-in ACT4 suite provenance and mandatory coverage gaps.")
    ap.add_argument("--suite-root", default="verif/act4/riscv-arch-test")
    ap.add_argument("--ledger", default="verif/act4/profiles/rva23s64_ledger.json")
    ap.add_argument("--discovery", default="verif/act4/profiles/suite_discovery.json")
    ap.add_argument("--write-json", default="verif/act4/profiles/suite_provenance.json")
    ap.add_argument("--write-missing", default="verif/act4/profiles/missing_upstream_coverage.json")
    args = ap.parse_args()

    suite_root = Path(args.suite_root)
    ledger = json.loads(Path(args.ledger).read_text(encoding="utf-8"))
    discovery = json.loads(Path(args.discovery).read_text(encoding="utf-8"))
    tests = discovery.get("tests", [])

    remote = git_out(["remote", "get-url", "origin"], suite_root)
    commit = git_out(["rev-parse", "HEAD"], suite_root)
    dirty = bool(git_out(["status", "--short"], suite_root))
    provenance = {
        "suite_root": str(suite_root),
        "upstream_url": remote,
        "commit": commit,
        "dirty": dirty,
        "total_checked_in_asm_tests": discovery.get("summary", {}).get("total_tests"),
        "local_patch_policy": "Do not edit upstream suite files in-place for profile work; add local generated/backfill tests outside the upstream tree or record patches explicitly here.",
        "generated_test_status": "No M44-generated upstream replacement tests are present; local directed tests live under verif/directed/asm.",
        "network_update_status": "Not attempted: current execution constraints require using only files already inside this repo.",
    }

    by_ext = {}
    for test in tests:
        key = test.get("extension", "").lower()
        by_ext.setdefault(key, 0)
        by_ext[key] += 1

    missing = []
    for row in ledger.get("features", []):
        if row.get("requirement") != "mandatory":
            continue
        ext = row.get("extension", "")
        key = ext.lower()
        candidates = {key, key.replace(".", ""), key.replace("_", "-")}
        has_any = any(by_ext.get(candidate, 0) > 0 for candidate in candidates)
        if not has_any:
            missing.append({
                "extension": ext,
                "owner_area": row.get("owner_area"),
                "profile_source": row.get("profile_source"),
                "reason": "No directly named checked-in riscv-arch-test group was found by local discovery.",
                "next_step": "M45 must map to an existing group, add a local backfill manifest entry, or keep a waiver/blocker.",
            })

    Path(args.write_json).write_text(json.dumps(provenance, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    Path(args.write_missing).write_text(json.dumps({"count": len(missing), "missing_upstream_coverage": missing}, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"suite commit: {commit}")
    print(f"checked-in asm tests: {provenance['total_checked_in_asm_tests']}")
    print(f"mandatory entries without direct suite group: {len(missing)}")
    print(f"wrote {args.write_json}")
    print(f"wrote {args.write_missing}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
