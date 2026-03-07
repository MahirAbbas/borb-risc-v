#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import shlex
import subprocess
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple


def run_cmd(cmd: Sequence[str], cwd: Optional[Path] = None, capture: bool = False) -> subprocess.CompletedProcess:
    return subprocess.run(
        list(cmd),
        cwd=str(cwd) if cwd else None,
        text=True,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.PIPE if capture else None,
        check=False,
    )


def ensure_tool(name: str) -> None:
    rc = run_cmd(["bash", "-lc", f"command -v {shlex.quote(name)}"])
    if rc.returncode != 0:
        raise RuntimeError(f"missing tool in PATH: {name}")


def nm_symbol(nm_tool: str, elf: Path, symbol: str) -> Optional[int]:
    proc = run_cmd([nm_tool, str(elf)], capture=True)
    if proc.returncode != 0:
        raise RuntimeError(f"{nm_tool} failed for {elf}")
    for line in proc.stdout.splitlines():
        parts = line.split()
        if len(parts) >= 3 and parts[2] == symbol:
            return int(parts[0], 16)
    return None


def read_hex_file(path: Path) -> Optional[int]:
    if not path.exists():
        return None
    txt = path.read_text(encoding="utf-8").strip()
    if not txt:
        return None
    return int(txt, 0)


def read_signature(path: Path) -> List[str]:
    if not path.exists():
        return []
    return [ln.strip().lower() for ln in path.read_text(encoding="utf-8", errors="ignore").splitlines() if ln.strip()]


def first_sig_diff(dut: List[str], golden: List[str]) -> Optional[Dict[str, object]]:
    n = min(len(dut), len(golden))
    for i in range(n):
        if dut[i] != golden[i]:
            return {"line_index": i, "dut": dut[i], "golden": golden[i]}
    if len(dut) != len(golden):
        return {
            "line_index": n,
            "dut": "<eof>" if len(dut) <= n else dut[n],
            "golden": "<eof>" if len(golden) <= n else golden[n],
            "note": f"length mismatch dut={len(dut)} golden={len(golden)}",
        }
    return None


def main() -> int:
    ap = argparse.ArgumentParser(description="Compile and run directed RISC-V assembly tests on borb-sim.")
    ap.add_argument("asm", help="Assembly .S file to compile and run")
    ap.add_argument("--name", default="", help="Override test name (default: assembly stem)")
    ap.add_argument("--out-root", default="verif/directed/out", help="Output directory root")
    ap.add_argument("--sim", default="verif/riscof/borb/build/borb-sim", help="Path to borb simulator binary")
    ap.add_argument("--sim-make-dir", default="verif/riscof/borb/sim", help="Directory for simulator Makefile")
    ap.add_argument("--rebuild-sim", action="store_true", help="Rebuild borb-sim before running")
    ap.add_argument("--linker", default="verif/riscof/borb/env/link.ld", help="Linker script path")
    ap.add_argument("--xlen", type=int, default=64, choices=[32, 64], help="XLEN/toolchain width")
    ap.add_argument("--march", default="rv64i_zicsr", help="ISA string passed to GCC")
    ap.add_argument("--mabi", default="lp64", help="ABI string passed to GCC")
    ap.add_argument("--max-cycles", type=int, default=500000, help="Simulation cycle budget")
    ap.add_argument("--trace", action="store_true", help="Emit FST waveform")
    ap.add_argument("--trace-commit", action="store_true", help="Emit per-commit JSON trace")
    ap.add_argument("--golden-signature", default="", help="Optional signature file to diff against")
    args = ap.parse_args()

    asm = Path(args.asm).resolve()
    if not asm.exists():
        raise SystemExit(f"assembly file not found: {asm}")

    sim = Path(args.sim).resolve()
    sim_make_dir = Path(args.sim_make_dir).resolve()
    linker = Path(args.linker).resolve()
    out_root = Path(args.out_root).resolve()
    test_name = args.name if args.name else asm.stem

    gcc = f"riscv{args.xlen}-unknown-elf-gcc"
    nm = f"riscv{args.xlen}-unknown-elf-nm"
    objdump = f"riscv{args.xlen}-unknown-elf-objdump"
    ensure_tool(gcc)
    ensure_tool(nm)
    ensure_tool(objdump)

    if args.rebuild_sim:
        clean = run_cmd(["make", "-C", str(sim_make_dir), "clean"])
        if clean.returncode != 0:
            raise SystemExit("failed to clean simulator")
        mk_cmd = ["make", "-C", str(sim_make_dir)]
        if args.trace:
            mk_cmd.extend(["TRACE=1", "FAST=0"])
        mk = run_cmd(mk_cmd)
        if mk.returncode != 0:
            raise SystemExit("failed to build simulator")

    if not sim.exists():
        raise SystemExit(f"sim binary not found: {sim}")
    if not linker.exists():
        raise SystemExit(f"linker not found: {linker}")

    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    out_dir = out_root / f"{test_name}_{ts}"
    out_dir.mkdir(parents=True, exist_ok=True)

    elf = out_dir / "test.elf"
    dump = out_dir / "test.dump"
    signature = out_dir / "DUT-borb.signature"
    tohost_txt = out_dir / "tohost.txt"
    commit_trace = out_dir / "commit_trace.log"
    fst = out_dir / "wave.fst"

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
        str(linker),
        str(asm),
        "-o",
        str(elf),
    ]
    comp = run_cmd(compile_cmd, capture=True)
    if comp.returncode != 0:
        (out_dir / "compile.stderr.log").write_text(comp.stderr or "", encoding="utf-8")
        (out_dir / "compile.stdout.log").write_text(comp.stdout or "", encoding="utf-8")
        raise SystemExit(f"compile failed; see {out_dir}")

    dis = run_cmd([objdump, "-D", str(elf)], capture=True)
    if dis.returncode == 0:
        dump.write_text(dis.stdout, encoding="utf-8")

    tohost = nm_symbol(nm, elf, "tohost")
    sig_begin = nm_symbol(nm, elf, "begin_signature")
    sig_end = nm_symbol(nm, elf, "end_signature")
    missing = [n for n, v in [("tohost", tohost), ("begin_signature", sig_begin), ("end_signature", sig_end)] if v is None]
    if missing:
        raise SystemExit(f"missing required ELF symbols: {', '.join(missing)}")

    sim_cmd = [
        str(sim),
        "--elf",
        str(elf),
        "--sig-begin",
        hex(sig_begin),
        "--sig-end",
        hex(sig_end),
        "--tohost",
        hex(tohost),
        "--signature",
        str(signature),
        "--report-tohost",
        str(tohost_txt),
        "--max-cycles",
        str(args.max_cycles),
    ]
    if args.trace_commit:
        sim_cmd.extend(["--trace-commit", str(commit_trace)])
    if args.trace:
        sim_cmd.extend(["--fst", str(fst)])

    sim_run = run_cmd(sim_cmd, capture=True)
    (out_dir / "sim.stdout.log").write_text(sim_run.stdout or "", encoding="utf-8")
    (out_dir / "sim.stderr.log").write_text(sim_run.stderr or "", encoding="utf-8")

    tohost_val = read_hex_file(tohost_txt)
    dut_sig = read_signature(signature)

    golden_sig_path = Path(args.golden_signature).resolve() if args.golden_signature else None
    sig_diff = None
    if golden_sig_path and golden_sig_path.exists():
        sig_diff = first_sig_diff(dut_sig, read_signature(golden_sig_path))

    status = "PASS" if sim_run.returncode == 0 and tohost_val == 1 else "FAIL"
    summary = {
        "status": status,
        "asm": str(asm),
        "out_dir": str(out_dir),
        "compile_cmd": compile_cmd,
        "sim_cmd": sim_cmd,
        "sim_returncode": sim_run.returncode,
        "tohost": None if tohost_val is None else hex(tohost_val),
        "elf": str(elf),
        "dump": str(dump),
        "signature": str(signature),
        "commit_trace": str(commit_trace) if args.trace_commit else None,
        "wave": str(fst) if args.trace else None,
        "symbols": {
            "tohost": hex(tohost if tohost is not None else 0),
            "begin_signature": hex(sig_begin if sig_begin is not None else 0),
            "end_signature": hex(sig_end if sig_end is not None else 0),
        },
        "golden_signature": str(golden_sig_path) if golden_sig_path else None,
        "first_signature_divergence": sig_diff,
    }
    (out_dir / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")

    print(json.dumps(summary, indent=2))
    return 0 if status == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
