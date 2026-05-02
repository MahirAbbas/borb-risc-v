#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from pathlib import Path
from typing import Optional, Sequence


def run_cmd(cmd: Sequence[str], *, capture: bool = False) -> subprocess.CompletedProcess:
    return subprocess.run(
        list(cmd),
        text=True,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.PIPE if capture else None,
        check=False,
    )


def nm_symbol(nm_tool: str, elf: Path, symbol: str) -> Optional[int]:
    proc = run_cmd([nm_tool, str(elf)], capture=True)
    if proc.returncode != 0:
        raise RuntimeError(f"{nm_tool} failed for {elf}: {proc.stderr}")
    for line in proc.stdout.splitlines():
        parts = line.split()
        if len(parts) >= 3 and parts[2] == symbol:
            return int(parts[0], 16)
    return None


def read_hex_file(path: Path) -> Optional[int]:
    if not path.exists():
        return None
    text = path.read_text(encoding="utf-8", errors="replace").strip()
    if not text:
        return None
    return int(text, 0)


def main() -> int:
    parser = argparse.ArgumentParser(description="Run one ACT4 ELF on borb-sim and emit an RVCP summary line.")
    parser.add_argument("elf", type=Path)
    parser.add_argument("--sim", default="verif/borb-sim/build/obj_dir/VSoC")
    parser.add_argument("--out-root", default="verif/act4/borb-rva23s64/results")
    parser.add_argument("--max-cycles", type=int, default=int(os.environ.get("BORB_ACT4_MAX_CYCLES", "2000000")))
    parser.add_argument("--xlen", type=int, default=64, choices=(32, 64))
    args = parser.parse_args()

    elf = args.elf.resolve()
    if not elf.exists():
        print(f"ELF not found: {elf}", file=sys.stderr)
        return 1

    sim = Path(args.sim).resolve()
    if not sim.exists():
        print(f"Simulator not found: {sim}", file=sys.stderr)
        return 1

    nm = f"riscv{args.xlen}-unknown-elf-nm"
    test_id = str(elf.with_suffix("").name)
    rel_parts = elf.parts[-4:]
    safe_name = "__".join(part.replace("/", "_") for part in rel_parts).replace(".elf", "")
    out_dir = Path(args.out_root).resolve() / safe_name
    out_dir.mkdir(parents=True, exist_ok=True)

    signature = out_dir / "DUT-borb.signature"
    tohost_txt = out_dir / "tohost.txt"
    perf_json = out_dir / "borb.perf.json"
    stdout_log = out_dir / "sim.stdout.log"
    stderr_log = out_dir / "sim.stderr.log"
    summary_json = out_dir / "summary.json"

    symbols = {
        "tohost": nm_symbol(nm, elf, "tohost"),
        "begin_signature": nm_symbol(nm, elf, "begin_signature"),
        "end_signature": nm_symbol(nm, elf, "end_signature"),
    }
    missing = [name for name, value in symbols.items() if value is None]
    if missing:
        print(f'RVCP-SUMMARY: TEST FAILED - Test File "{test_id}"')
        print(f"Missing required ELF symbols: {', '.join(missing)}", file=sys.stderr)
        return 1

    sim_cmd = [
        str(sim),
        "--elf",
        str(elf),
        "--sig-begin",
        hex(symbols["begin_signature"] or 0),
        "--sig-end",
        hex(symbols["end_signature"] or 0),
        "--tohost",
        hex(symbols["tohost"] or 0),
        "--signature",
        str(signature),
        "--report-tohost",
        str(tohost_txt),
        "--report-perf",
        str(perf_json),
        "--max-cycles",
        str(args.max_cycles),
    ]
    proc = run_cmd(sim_cmd, capture=True)
    stdout_log.write_text(proc.stdout or "", encoding="utf-8")
    stderr_log.write_text(proc.stderr or "", encoding="utf-8")

    tohost = read_hex_file(tohost_txt)
    passed = proc.returncode == 0 and tohost == 1
    status = "PASSED" if passed else "FAILED"
    print(f'RVCP-SUMMARY: TEST {status} - Test File "{test_id}"')

    summary = {
        "status": "PASS" if passed else "FAIL",
        "elf": str(elf),
        "sim_cmd": sim_cmd,
        "sim_returncode": proc.returncode,
        "tohost": None if tohost is None else hex(tohost),
        "symbols": {k: None if v is None else hex(v) for k, v in symbols.items()},
        "signature": str(signature),
        "perf": str(perf_json),
        "stdout": str(stdout_log),
        "stderr": str(stderr_log),
    }
    summary_json.write_text(json.dumps(summary, indent=2), encoding="utf-8")
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())

