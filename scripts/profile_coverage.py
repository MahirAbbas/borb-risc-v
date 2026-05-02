#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Dict, List


def load_json(path: Path, default: Any) -> Any:
    if not path.exists():
        return default
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> int:
    ap = argparse.ArgumentParser(description="Generate historical profile coverage summary from manifest data.")
    ap.add_argument("--profile", default="current")
    ap.add_argument("--workdir", default="verif/act4/work/borb-RVA23S64")
    ap.add_argument("--manifest", default="verif/act4/profiles/profile_test_manifest.json")
    ap.add_argument("--missing", default="verif/act4/profiles/missing_features.json")
    ap.add_argument("--out-json", help="Output JSON path")
    ap.add_argument("--out-md", help="Output Markdown path")
    ap.add_argument("--fail-on-missing", action="store_true", help="Exit nonzero if mandatory profile coverage is missing or blocked")
    args = ap.parse_args()

    workdir = Path(args.workdir)
    run_summary = load_json(workdir / "borb_run_summary.json", {"counts": {}, "tests": []})
    manifest = load_json(Path(args.manifest), {"test_groups": [], "missing_coverage": []})
    missing_features = load_json(Path(args.missing), {"mandatory_missing_or_blocked": []})
    missing_ledger = manifest.get("missing_ledger_entries", [])

    ran_tests = bool(run_summary.get("tests"))
    test_status = {t.get("test"): t.get("status") for t in run_summary.get("tests", [])}
    by_feature: Dict[str, Counter[str]] = defaultdict(Counter)
    for group in manifest.get("test_groups", []):
        feature = group["feature"]
        for test_path in group.get("tests", []):
            test_name = Path(test_path).name
            status = test_status.get(test_name, "missing" if ran_tests else "selected")
            by_feature[feature][status] += 1

    for item in manifest.get("missing_coverage", []):
        by_feature[item["feature"]]["missing"] += 1
    for item in missing_ledger:
        by_feature[item["feature"]]["missing_ledger"] += 1
    for item in missing_features.get("mandatory_missing_or_blocked", []):
        by_feature[item["extension"]]["blocked"] += 1

    feature_rows = []
    totals = Counter()
    for feature, counts in sorted(by_feature.items()):
        row = {"feature": feature, **dict(counts)}
        feature_rows.append(row)
        totals.update(counts)

    payload = {
        "profile": args.profile,
        "workdir": str(workdir),
        "legacy_counts": run_summary.get("counts", {}),
        "coverage_counts": dict(totals),
        "mandatory_summary": manifest.get("summary", {}),
        "missing_ledger_entries": missing_ledger,
        "features": feature_rows,
    }

    out_json = Path(args.out_json or (workdir / "profile_coverage.json"))
    out_md = Path(args.out_md or (workdir / "profile_coverage.md"))
    out_json.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    lines = [
        f"# Profile Coverage: {args.profile}",
        "",
        f"- Workdir: `{workdir}`",
        "- Legacy counts: " + ", ".join(f"`{k}`={v}" for k, v in sorted(payload["legacy_counts"].items())),
        "- Mandatory summary: " + ", ".join(f"`{k}`={v}" for k, v in sorted(payload["mandatory_summary"].items())),
        "- Coverage counts: " + ", ".join(f"`{k}`={v}" for k, v in sorted(payload["coverage_counts"].items())),
        "",
        "| Feature | Pass | Missing | Missing Ledger | Blocked | Other |",
        "| --- | ---: | ---: | ---: | ---: | ---: |",
    ]
    for row in feature_rows:
        passed = row.get("pass", 0)
        missing = row.get("missing", 0)
        missing_ledger_count = row.get("missing_ledger", 0)
        blocked = row.get("blocked", 0)
        other = sum(v for k, v in row.items() if k not in {"feature", "pass", "missing", "missing_ledger", "blocked"})
        lines.append(f"| {row['feature']} | {passed} | {missing} | {missing_ledger_count} | {blocked} | {other} |")
    out_md.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("Profile coverage: " + ", ".join(f"{k}={v}" for k, v in sorted(totals.items())))
    print(f"Wrote {out_json}")
    print(f"Wrote {out_md}")
    if args.fail_on_missing and (totals.get("missing", 0) or totals.get("missing_ledger", 0) or totals.get("blocked", 0)):
        print("Profile coverage gate failed: mandatory RVA23S64 coverage is incomplete.")
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
