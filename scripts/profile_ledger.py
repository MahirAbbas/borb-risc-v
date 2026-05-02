#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Dict, Iterable, List


REQUIRED_FIELDS = [
    "extension",
    "profile_source",
    "requirement",
    "owner_area",
    "declaration_status",
    "act4_coverage",
    "directed_coverage",
    "reference_model_support",
    "current_blocker",
]

COMPLETE_DECLARATIONS = {"declared-current", "declared-profile"}
COMPLETE_COVERAGE = {"covered", "backfill-covered"}
COMPLETE_REFERENCE = {"supported", "local-backfill"}
NO_BLOCKER = {"none"}


def load_ledger(path: Path) -> Dict[str, Any]:
    data = json.loads(path.read_text(encoding="utf-8"))
    rows = data.get("features")
    if not isinstance(rows, list):
        raise SystemExit(f"{path}: expected top-level 'features' list")
    for idx, row in enumerate(rows):
        missing = [field for field in REQUIRED_FIELDS if field not in row]
        if missing:
            raise SystemExit(f"{path}: feature[{idx}] missing fields: {', '.join(missing)}")
    return data


def evidence_for(data: Dict[str, Any], row: Dict[str, Any]) -> List[str]:
    row_links = row.get("evidence_links") or []
    catalog = data.get("evidence_links") or {}
    catalog_links = catalog.get(row.get("extension"), [])
    return list(row_links) + list(catalog_links)


def mandatory_missing(data: Dict[str, Any]) -> List[Dict[str, Any]]:
    missing = []
    for row in data["features"]:
        if row.get("requirement") != "mandatory":
            continue
        declaration_ok = row.get("declaration_status") in COMPLETE_DECLARATIONS
        act4_ok = row.get("act4_coverage") in COMPLETE_COVERAGE
        directed_ok = row.get("directed_coverage") in COMPLETE_COVERAGE
        reference_ok = row.get("reference_model_support") in COMPLETE_REFERENCE
        blocker_ok = row.get("current_blocker") in NO_BLOCKER
        evidence_ok = bool(evidence_for(data, row))
        if not (declaration_ok and (act4_ok or directed_ok) and reference_ok and blocker_ok and evidence_ok):
            missing.append(row)
    return missing


def summarize(rows: List[Dict[str, Any]], missing: List[Dict[str, Any]]) -> str:
    by_requirement = Counter(row["requirement"] for row in rows)
    by_owner = Counter(row["owner_area"] for row in missing)
    lines = [
        "RVA23S64 profile ledger summary",
        f"features: {len(rows)}",
        "requirements: " + ", ".join(f"{k}={v}" for k, v in sorted(by_requirement.items())),
        f"mandatory_missing_or_blocked: {len(missing)}",
    ]
    if by_owner:
        lines.append("missing_by_owner: " + ", ".join(f"{k}={v}" for k, v in sorted(by_owner.items())))
    return "\n".join(lines)


def markdown_report(data: Dict[str, Any], missing: List[Dict[str, Any]]) -> str:
    rows = data["features"]
    by_requirement = Counter(row["requirement"] for row in rows)
    by_owner = defaultdict(list)
    for row in missing:
        by_owner[row["owner_area"]].append(row)

    out = [
        "# RVA23S64 Missing Feature Report",
        "",
        f"- Ledger: `{data.get('name', 'unknown')}`",
        f"- Source basis: {data.get('source_basis', 'unspecified')}",
        f"- Total rows: {len(rows)}",
        "- Requirement counts: " + ", ".join(f"`{k}`={v}" for k, v in sorted(by_requirement.items())),
        f"- Mandatory missing or blocked: {len(missing)}",
        "",
    ]
    if not missing:
        out.extend(["No mandatory gaps found by the current completion policy.", ""])
        return "\n".join(out)

    out.extend(
        [
            "| Extension | Owner | Declaration | ACT4 | Directed | Reference | Blocker |",
            "| --- | --- | --- | --- | --- | --- | --- |",
        ]
    )
    for row in missing:
        out.append(
            "| {extension} | {owner_area} | {declaration_status} | {act4_coverage} | "
            "{directed_coverage} | {reference_model_support} | {current_blocker} |".format(**row)
        )
    out.append("")
    return "\n".join(out)


def main() -> int:
    ap = argparse.ArgumentParser(description="Validate and summarize the RVA23S64 profile ledger.")
    ap.add_argument("--ledger", default="verif/act4/profiles/rva23s64_ledger.json")
    ap.add_argument("--write-md", help="Write missing-feature Markdown report")
    ap.add_argument("--write-json", help="Write missing mandatory rows as JSON")
    args = ap.parse_args()

    ledger_path = Path(args.ledger)
    data = load_ledger(ledger_path)
    rows = data["features"]
    missing = mandatory_missing(data)

    print(summarize(rows, missing))

    if args.write_md:
        out = Path(args.write_md)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(markdown_report(data, missing), encoding="utf-8")
    if args.write_json:
        out = Path(args.write_json)
        out.parent.mkdir(parents=True, exist_ok=True)
        payload = {
            "ledger": str(ledger_path),
            "mandatory_missing_or_blocked": missing,
            "count": len(missing),
        }
        out.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
