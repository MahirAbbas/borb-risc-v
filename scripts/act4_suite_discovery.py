#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Dict, List

import yaml


def load_yaml_keys(path: Path) -> set[str]:
    if not str(path) or not path.exists() or not path.is_file():
        return set()
    data = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    return set(data.keys())


def classify_extension(rel: str) -> str:
    parts = rel.split("/")
    if len(parts) < 3:
        return "unknown"
    family = parts[0]
    if "vm_sv39" in parts:
        return "vm_sv39"
    if "vm_sv48" in parts:
        return "vm_sv48"
    if "vm_sv57" in parts:
        return "vm_sv57"
    if "vm_pmp" in parts:
        return "vm_pmp"
    if "pmp" in parts:
        return "pmp"
    if "privilege" in parts:
        return "privilege"
    try:
        src_idx = parts.index("src")
    except ValueError:
        src_idx = -1
    if src_idx > 1:
        return parts[src_idx - 1]
    return family


def status_for(abs_path: Path, rel: str, full_keys: set[str], filtered_keys: set[str], run_set: str) -> tuple[str, str]:
    if run_set == "all":
        return "selected", "included in full checked-in suite run set"

    abs_s = str(abs_path)
    if abs_s in filtered_keys or rel in filtered_keys:
        return "selected", "active current profile filtered test list"
    if abs_s in full_keys or rel in full_keys:
        name = abs_path.name
        if name == "vm_reserved_svnapot_S_mode.S":
            return "excluded", "current filter excludes negative Svnapot test"
        if "/vm_sv48/" in abs_s or "/vm_sv57/" in abs_s or "/vm_pmp/src/sv48/" in abs_s or "/vm_pmp/src/sv57/" in abs_s:
            return "excluded", "current filter excludes higher-VM tests"
        if "/Zfh/src/" in abs_s:
            return "excluded", "current filter keeps only verified Zfhmin conversion subset"
        return "excluded", "listed by active YAML but filtered by current profile"
    return "unsupported", "not selected by active current ISA/platform YAML"


def markdown(summary: Dict[str, Any], rows: List[Dict[str, Any]]) -> str:
    by_ext = summary["by_extension"]
    out = [
        "# ACT4 Suite Discovery",
        "",
        f"- Suite: `{summary['suite']}`",
        f"- Total checked-in `.S` tests: {summary['total_tests']}",
        "- Status counts: " + ", ".join(f"`{k}`={v}" for k, v in sorted(summary["by_status"].items())),
        "",
        "| Extension/group | Selected | Excluded | Unsupported | Total |",
        "| --- | ---: | ---: | ---: | ---: |",
    ]
    for ext, counts in sorted(by_ext.items()):
        total = sum(counts.values())
        out.append(
            f"| {ext} | {counts.get('selected', 0)} | {counts.get('excluded', 0)} | {counts.get('unsupported', 0)} | {total} |"
        )
    out.extend(["", "## Tests", "", "| Status | Extension/group | Test | Reason |", "| --- | --- | --- | --- |"])
    for row in rows:
        out.append(f"| {row['status']} | {row['extension']} | `{row['path']}` | {row['reason']} |")
    out.append("")
    return "\n".join(out)


def main() -> int:
    ap = argparse.ArgumentParser(description="Discover checked-in ACT4 suite tests and classify profile coverage.")
    ap.add_argument("--suite", default="verif/act4/riscv-arch-test/tests")
    ap.add_argument("--full-testlist", default="")
    ap.add_argument("--filtered-testlist", default="")
    ap.add_argument("--write-json", default="verif/act4/profiles/suite_discovery.json")
    ap.add_argument("--write-md", default="verif/act4/profiles/suite_discovery.md")
    ap.add_argument("--run-set", choices=["current", "all"], default="current")
    args = ap.parse_args()

    suite = Path(args.suite).resolve()
    full_keys = load_yaml_keys(Path(args.full_testlist))
    filtered_keys = load_yaml_keys(Path(args.filtered_testlist))

    rows: List[Dict[str, Any]] = []
    by_status: Counter[str] = Counter()
    by_extension: Dict[str, Counter[str]] = defaultdict(Counter)
    for path in sorted(suite.rglob("*.S")):
        rel = str(path.relative_to(suite))
        ext = classify_extension(rel)
        status, reason = status_for(path, rel, full_keys, filtered_keys, args.run_set)
        row = {
            "path": rel,
            "absolute_path": str(path),
            "extension": ext,
            "status": status,
            "reason": reason,
        }
        rows.append(row)
        by_status[status] += 1
        by_extension[ext][status] += 1

    summary = {
        "suite": str(suite),
        "run_set": args.run_set,
        "total_tests": len(rows),
        "by_status": dict(by_status),
        "by_extension": {k: dict(v) for k, v in by_extension.items()},
    }
    payload = {"summary": summary, "tests": rows}

    json_out = Path(args.write_json)
    json_out.parent.mkdir(parents=True, exist_ok=True)
    json_out.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    md_out = Path(args.write_md)
    md_out.parent.mkdir(parents=True, exist_ok=True)
    md_out.write_text(markdown(summary, rows), encoding="utf-8")

    print(f"discovered {len(rows)} tests")
    print("status: " + ", ".join(f"{k}={v}" for k, v in sorted(by_status.items())))
    print(f"wrote {json_out}")
    print(f"wrote {md_out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
