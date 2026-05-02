#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Dict, List


def declared_extensions(isa_text: str) -> set[str]:
    m = re.search(r"ISA:\s*([A-Za-z0-9_]+)", isa_text)
    if not m:
        return set()
    isa = m.group(1)
    parts = isa.split("_")
    base = parts[0]
    exts = set(parts[1:])
    for ch in "IMAFDCSU":
        if ch in base:
            exts.add(ch)
    return exts


def main() -> int:
    ap = argparse.ArgumentParser(description="Repo-local RVA23S64 profile validation classifier.")
    ap.add_argument("--ledger", default="verif/act4/profiles/rva23s64_ledger.json")
    ap.add_argument("--isa-yaml", default="")
    ap.add_argument("--write-json", default="verif/act4/profiles/profile_validation.json")
    args = ap.parse_args()

    ledger = json.loads(Path(args.ledger).read_text(encoding="utf-8"))
    declared = set()
    if args.isa_yaml and Path(args.isa_yaml).exists():
        declared = declared_extensions(Path(args.isa_yaml).read_text(encoding="utf-8"))
    rows: List[Dict[str, str]] = []
    counts: Dict[str, int] = {}
    for feature in ledger.get("features", []):
        ext = feature["extension"]
        status = feature["declaration_status"]
        if ext in declared or status in {"declared-current", "declared-profile"}:
            classification = "accepted_by_current_metadata"
        elif status == "validator-unsupported" or status == "tooling-workaround":
            classification = "unsupported_by_current_riscv_config"
        elif status in {"missing", "not-profile-claim", "not-isa"}:
            classification = "missing_from_profile_metadata"
        else:
            classification = "invalid_or_unknown"
        rows.append({
            "extension": ext,
            "requirement": feature["requirement"],
            "declaration_status": status,
            "classification": classification,
            "owner_area": feature["owner_area"],
        })
        counts[classification] = counts.get(classification, 0) + 1

    payload = {"isa_yaml": args.isa_yaml, "declared_extensions": sorted(declared), "counts": counts, "features": rows}
    out = Path(args.write_json)
    out.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print("profile validation: " + ", ".join(f"{k}={v}" for k, v in sorted(counts.items())))
    print(f"wrote {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
