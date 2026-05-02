#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from collections import defaultdict
from pathlib import Path
from typing import Any, Dict, List

import yaml


MANDATORY_RVA23S64 = [
    {"feature": "I", "source": "RVA23S64 mandatory base"},
    {"feature": "M", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "A", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "F", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "D", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "C", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "B", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "Zicsr", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "Zicntr", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "Zihpm", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "Ziccif", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "Ziccrse", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "Ziccamoa", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "Zicclsm", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "Za64rs", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "Zihintpause", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "Zic64b", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "Zicbom", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "Zicbop", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "Zicboz", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "Zfhmin", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "Zkt", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "V", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "Zvfhmin", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "Zvbb", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "Zvkt", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "Zihintntl", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "Zicond", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "Zimop", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "Zcmop", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "Zcb", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "Zfa", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "Zawrs", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "Supm", "source": "RVA23U64 mandatory inherited by RVA23S64"},
    {"feature": "Zifencei", "source": "RVA23S64 mandatory unprivileged"},
    {"feature": "Ss1p13", "source": "RVA23S64 mandatory privileged"},
    {"feature": "Svbare", "source": "RVA23S64 mandatory privileged"},
    {"feature": "Sv39", "source": "RVA23S64 mandatory privileged"},
    {"feature": "Svade", "source": "RVA23S64 mandatory privileged"},
    {"feature": "Ssccptr", "source": "RVA23S64 mandatory privileged"},
    {"feature": "Sstvecd", "source": "RVA23S64 mandatory privileged"},
    {"feature": "Sstvala", "source": "RVA23S64 mandatory privileged"},
    {"feature": "Sscounterenw", "source": "RVA23S64 mandatory privileged"},
    {"feature": "Svpbmt", "source": "RVA23S64 mandatory privileged"},
    {"feature": "Svinval", "source": "RVA23S64 mandatory privileged"},
    {"feature": "Svnapot", "source": "RVA23S64 mandatory privileged"},
    {"feature": "Sstc", "source": "RVA23S64 mandatory privileged"},
    {"feature": "Sscofpmf", "source": "RVA23S64 mandatory privileged"},
    {"feature": "Ssnpm", "source": "RVA23S64 mandatory privileged"},
    {"feature": "Ssu64xl", "source": "RVA23S64 mandatory privileged"},
    {"feature": "Sha", "source": "RVA23S64 mandatory privileged"},
    {"feature": "H", "source": "RVA23S64 Sha bundle"},
    {"feature": "Ssstateen", "source": "RVA23S64 Sha bundle"},
    {"feature": "Shcounterenw", "source": "RVA23S64 Sha bundle"},
    {"feature": "Shvstvala", "source": "RVA23S64 Sha bundle"},
    {"feature": "Shtvala", "source": "RVA23S64 Sha bundle"},
    {"feature": "Shvstvecd", "source": "RVA23S64 Sha bundle"},
    {"feature": "Shvsatpa", "source": "RVA23S64 Sha bundle"},
    {"feature": "Shgatpa", "source": "RVA23S64 Sha bundle"},
]

ALIASES = {
    "B": ["B", "Zba", "Zbb", "Zbs"],
    "Zicsr": ["privilege", "pmp"],
    "Zicntr": ["privilege"],
    "Zihpm": ["privilege"],
    "Zihintpause": ["hints"],
    "Zicbom": ["CMO"],
    "Zicbop": ["CMO"],
    "Zicboz": ["CMO"],
    "Zfa": ["D_Zfa", "F_Zfa"],
    "PMP": ["pmp"],
    "Svbare": ["vm_sv39"],
    "Sv39": ["vm_sv39"],
    "Svade": ["vm_sv39"],
    "Ssccptr": ["vm_sv39", "vm_pmp"],
    "Sstvecd": ["privilege"],
    "Sstvala": ["privilege", "vm_sv39"],
    "Sscounterenw": ["privilege"],
    "Svnapot": ["vm_sv39"],
    "Zfhmin": ["Zfh"],
}


def norm(s: str) -> str:
    return s.lower().replace(".", "").replace("_", "").replace("-", "")


def tests_from_profile_testlist(path: Path) -> dict[str, list[dict[str, Any]]]:
    if not str(path) or not path.exists() or not path.is_file():
        return {}
    data = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    tests_by_ext: Dict[str, List[Dict[str, Any]]] = defaultdict(list)
    for test_path in data:
        parts = Path(test_path).parts
        ext = ""
        if "riscv-test-suite" in parts:
            idx = parts.index("riscv-test-suite")
            if len(parts) > idx + 2:
                ext = parts[idx + 2]
        if ext:
            tests_by_ext[norm(ext)].append({"path": test_path, "status": "selected"})
    return tests_by_ext


def main() -> int:
    ap = argparse.ArgumentParser(description="Generate RVA23S64 profile test manifest from ledger and suite discovery.")
    ap.add_argument("--ledger", default="verif/act4/profiles/rva23s64_ledger.json")
    ap.add_argument("--discovery", default="verif/act4/profiles/suite_discovery.json")
    ap.add_argument("--profile-testlist", default="")
    ap.add_argument("--write-json", default="verif/act4/profiles/profile_test_manifest.json")
    args = ap.parse_args()

    ledger = json.loads(Path(args.ledger).read_text(encoding="utf-8"))
    discovery = json.loads(Path(args.discovery).read_text(encoding="utf-8")) if Path(args.discovery).exists() else {"tests": []}
    tests_by_ext: Dict[str, List[Dict[str, Any]]] = defaultdict(list)
    for test in discovery.get("tests", []):
        tests_by_ext[norm(test.get("extension", ""))].append(test)
    for key, tests in tests_from_profile_testlist(Path(args.profile_testlist)).items():
        tests_by_ext[key].extend(tests)

    ledger_by_ext = {row["extension"]: row for row in ledger.get("features", [])}
    groups = []
    missing = []
    missing_ledger_entries = []
    for req in MANDATORY_RVA23S64:
        ext = req["feature"]
        feature = ledger_by_ext.get(ext)
        if feature is None:
            missing_ledger_entries.append({
                "feature": ext,
                "profile_source": req["source"],
                "reason": "Mandatory RVA23S64 feature is not represented in rva23s64_ledger.json.",
            })
            feature = {
                "extension": ext,
                "owner_area": "unassigned",
                "requirement": "mandatory",
                "reference_model_support": "unknown",
            }
        candidates = [norm(x) for x in ALIASES.get(ext, [ext])]
        matched = []
        for key in candidates:
            matched.extend(tests_by_ext.get(key, []))
        if matched:
            groups.append({
                "feature": ext,
                "owner_area": feature["owner_area"],
                "profile_requirement": feature["requirement"],
                "profile_source": req["source"],
                "expected_macros": [ext],
                "reference_backend": feature["reference_model_support"],
                "timeout_budget": "profile-default",
                "tests": sorted({t["path"] for t in matched}),
                "statuses": sorted({t["status"] for t in matched}),
            })
        else:
            missing.append({
                "feature": ext,
                "owner_area": feature["owner_area"],
                "profile_source": req["source"],
                "reason": "No selected ACT4 suite group matched this mandatory RVA23S64 feature.",
                "temporary_waiver": False,
                "next_step": "Add a manifest mapping to an existing test, create directed/ACT4 backfill, or add an expiring waiver.",
            })

    manifest = {
        "name": "rva23s64-profile-test-manifest",
        "source_ledger": args.ledger,
        "source_discovery": args.discovery,
        "source_profile_testlist": args.profile_testlist,
        "mandatory_source": "RVA23S64 profile text in agent/plan4.md/user-provided profile excerpt",
        "mandatory_features": MANDATORY_RVA23S64,
        "test_groups": groups,
        "missing_coverage": missing,
        "missing_ledger_entries": missing_ledger_entries,
        "summary": {
            "mandatory_features": len(MANDATORY_RVA23S64),
            "mapped_features": len(groups),
            "missing_coverage": len(missing),
            "missing_ledger_entries": len(missing_ledger_entries),
        },
    }
    out = Path(args.write_json)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(manifest["summary"], indent=2, sort_keys=True))
    print(f"wrote {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
