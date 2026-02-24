#!/usr/bin/env python3
import argparse
import json
import os
import re
import shlex
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional


@dataclass
class TestEntry:
    name: str
    test_stem: str
    source: Path
    linker: Path


def run_cmd(cmd: List[str], cwd: Optional[Path] = None, env: Optional[Dict[str, str]] = None) -> None:
    proc = subprocess.run(cmd, cwd=str(cwd) if cwd else None, env=env, text=True)
    if proc.returncode != 0:
        raise RuntimeError(f"Command failed ({proc.returncode}): {' '.join(shlex.quote(c) for c in cmd)}")


def parse_list_file(list_path: Path, tests_root: Path, rv64_only: bool) -> List[TestEntry]:
    out: List[TestEntry] = []
    kv_re = re.compile(r"(\w+)=([^\s]+)")

    with list_path.open("r", encoding="utf-8") as f:
        for raw in f:
            line = raw.strip()
            if not line:
                continue
            kv = dict(kv_re.findall(line))
            name = kv.get("name")
            test = kv.get("test")
            if not name or not test:
                continue
            if rv64_only and not name.startswith("rv64"):
                continue
            stem = tests_root / test
            source = stem.with_suffix(".S")
            linker = stem.with_suffix(".ld")
            if not source.exists() or not linker.exists():
                continue
            out.append(TestEntry(name=name, test_stem=test, source=source, linker=linker))
    return out


def nm_symbol(nm: str, elf: Path, symbol: str) -> Optional[int]:
    out = subprocess.check_output([nm, str(elf)], text=True)
    for line in out.splitlines():
        parts = line.split()
        if len(parts) >= 3 and parts[2] == symbol:
            return int(parts[0], 16)
    return None


def read_tohost_value(path: Path) -> Optional[int]:
    if not path.exists():
        return None
    txt = path.read_text(encoding="utf-8").strip()
    if not txt:
        return None
    return int(txt, 0)


def main() -> int:
    ap = argparse.ArgumentParser(description="Scaffold runner: Tenstorrent riscv-arch-tests on borb DUT")
    ap.add_argument("--tests-root", default="verif/tenstorrent-riscv-arch-tests", help="Tenstorrent repo root")
    ap.add_argument(
        "--list",
        action="append",
        default=[],
        help=(
            "List file relative to tests-root; can repeat. "
            "Default: riscv_tests/bare_metal/machine/paging_bare/rv_i.list"
        ),
    )
    ap.add_argument("--workdir", default="verif/tenstorrent-riscv-arch-tests/out/borb", help="Output root")
    ap.add_argument("--dut", default="verif/riscof/borb/sim/build/borb-sim", help="Path to borb sim executable")
    ap.add_argument("--xlen", type=int, default=64, choices=[32, 64])
    ap.add_argument("--march", default="rv64i_zicsr_zifencei", help="Compile march (RV64I-safe default)")
    ap.add_argument("--mabi", default="lp64", help="Compile ABI")
    ap.add_argument("--max-cycles", type=int, default=200000)
    ap.add_argument("--limit", type=int, default=0, help="Limit number of tests (0=all)")
    ap.add_argument("--filter", default="", help="Regex filter on test name")
    ap.add_argument("--rv64-only", action="store_true", default=True, help="Run only rv64* tests")
    ap.add_argument("--allow-rv32", action="store_true", help="Allow rv32* entries from list")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    tests_root = Path(args.tests_root).resolve()
    work_root = Path(args.workdir).resolve()
    dut = Path(args.dut).resolve()

    if not tests_root.exists():
        print(f"tests root not found: {tests_root}", file=sys.stderr)
        return 2
    if not args.dry_run and not dut.exists():
        print(f"dut sim not found: {dut}", file=sys.stderr)
        return 2

    list_paths = args.list or ["riscv_tests/bare_metal/machine/paging_bare/rv_i.list"]
    list_files = [tests_root / p for p in list_paths]
    for lp in list_files:
        if not lp.exists():
            print(f"list not found: {lp}", file=sys.stderr)
            return 2

    rv64_only = (not args.allow_rv32) and args.rv64_only
    entries: List[TestEntry] = []
    for lp in list_files:
        entries.extend(parse_list_file(lp, tests_root, rv64_only=rv64_only))

    if args.filter:
        rex = re.compile(args.filter)
        entries = [e for e in entries if rex.search(e.name)]

    if args.limit > 0:
        entries = entries[: args.limit]

    if not entries:
        print("no tests selected", file=sys.stderr)
        return 2

    gcc = f"riscv{args.xlen}-unknown-elf-gcc"
    objcopy = f"riscv{args.xlen}-unknown-elf-objcopy"
    nm = f"riscv{args.xlen}-unknown-elf-nm"

    if not args.dry_run:
        for tool in [gcc, objcopy, nm]:
            if subprocess.run(["bash", "-lc", f"command -v {shlex.quote(tool)}"], text=True).returncode != 0:
                print(f"missing tool in PATH: {tool}", file=sys.stderr)
                return 2

    work_root.mkdir(parents=True, exist_ok=True)
    summary = {
        "tests_root": str(tests_root),
        "list_files": [str(p) for p in list_files],
        "dut": str(dut),
        "march": args.march,
        "mabi": args.mabi,
        "max_cycles": args.max_cycles,
        "results": [],
    }

    print(f"selected {len(entries)} tests")
    for i, e in enumerate(entries, start=1):
        tdir = work_root / e.name
        tdir.mkdir(parents=True, exist_ok=True)

        elf = tdir / "test.elf"
        binary = tdir / "test.bin"
        tohost_file = tdir / "tohost.txt"
        sig = tdir / "DUT-borb.signature"

        compile_cmd = [
            gcc,
            f"-march={args.march}",
            f"-mabi={args.mabi}",
            "-static",
            "-mcmodel=medany",
            "-fvisibility=hidden",
            "-nostdlib",
            "-nostartfiles",
            "-g",
            "-T",
            str(e.linker),
            str(e.source),
            "-o",
            str(elf),
        ]

        result = {
            "index": i,
            "name": e.name,
            "test": e.test_stem,
            "status": "PASS",
            "tohost": None,
            "note": "",
            "workdir": str(tdir),
        }

        try:
            print(f"[{i}/{len(entries)}] {e.name}")
            if args.dry_run:
                print("  DRYRUN", " ".join(shlex.quote(x) for x in compile_cmd))
                summary["results"].append(result)
                continue

            run_cmd(compile_cmd)
            run_cmd([objcopy, "-O", "binary", str(elf), str(binary)])

            tohost = nm_symbol(nm, elf, "tohost")
            if tohost is None:
                raise RuntimeError("symbol not found: tohost")

            begin_sig = nm_symbol(nm, elf, "begin_signature")
            end_sig = nm_symbol(nm, elf, "end_signature")
            # Tenstorrent tests are generally self-checking via tohost. Keep borb-sim
            # happy when signature symbols are absent by passing a zero-length range.
            if begin_sig is None or end_sig is None or begin_sig == 0 or end_sig == 0:
                begin_sig = tohost
                end_sig = tohost

            sim_cmd = [
                str(dut),
                "--bin",
                str(binary),
                "--sig-begin",
                hex(begin_sig),
                "--sig-end",
                hex(end_sig),
                "--tohost",
                hex(tohost),
                "--signature",
                str(sig),
                "--max-cycles",
                str(args.max_cycles),
                "--report-tohost",
                str(tohost_file),
            ]
            run_cmd(sim_cmd)

            tohost_val = read_tohost_value(tohost_file)
            result["tohost"] = None if tohost_val is None else hex(tohost_val)
            if tohost_val is None:
                result["status"] = "UNKNOWN"
                result["note"] = "missing tohost report"
            elif tohost_val == 1:
                result["status"] = "PASS"
            elif tohost_val == 0:
                result["status"] = "TIMEOUT_OR_NO_SIGNAL"
            else:
                result["status"] = "FAIL"
                result["note"] = "non-pass tohost"

        except Exception as ex:
            result["status"] = "ERROR"
            result["note"] = str(ex)

        summary["results"].append(result)

    summary_path = work_root / "summary.json"
    summary_path.write_text(json.dumps(summary, indent=2), encoding="utf-8")

    total = len(summary["results"])
    counts: Dict[str, int] = {}
    for r in summary["results"]:
        counts[r["status"]] = counts.get(r["status"], 0) + 1

    print("summary:")
    for k in sorted(counts.keys()):
        print(f"  {k}: {counts[k]}")
    print(f"  total: {total}")
    print(f"  summary_file: {summary_path}")

    return 0 if counts.get("ERROR", 0) == 0 and counts.get("FAIL", 0) == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
