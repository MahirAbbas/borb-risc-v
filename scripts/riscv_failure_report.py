#!/usr/bin/env python3
from __future__ import annotations

import argparse
import configparser
import json
import os
import re
import shlex
import subprocess
import tempfile
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple


def _parse_int(v: Any) -> Optional[int]:
    if v is None:
        return None
    if isinstance(v, bool):
        return int(v)
    if isinstance(v, int):
        return v
    if isinstance(v, str):
        s = v.strip().lower()
        if not s:
            return None
        try:
            return int(s, 0)
        except ValueError:
            return None
    return None


def _hex(v: Optional[int]) -> str:
    return "-" if v is None else hex(v)


def _run_capture(cmd: Sequence[str]) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)


def _best_tool(candidates: Sequence[str]) -> Optional[str]:
    for c in candidates:
        if _run_capture(["bash", "-lc", f"command -v {shlex.quote(c)}"]).returncode == 0:
            return c
    return None


def _write_json_and_md(out_dir: Path, name: str, payload: Dict[str, Any], lines: Sequence[str]) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / f"{name}.json").write_text(json.dumps(payload, indent=2), encoding="utf-8")
    (out_dir / f"{name}.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def _compare_signatures(sig_a: Path, sig_b: Path) -> Optional[Dict[str, Any]]:
    if not sig_a.exists() or not sig_b.exists():
        return None
    a_lines = sig_a.read_text(encoding="utf-8", errors="ignore").splitlines()
    b_lines = sig_b.read_text(encoding="utf-8", errors="ignore").splitlines()
    n = min(len(a_lines), len(b_lines))
    for i in range(n):
        if a_lines[i].strip() != b_lines[i].strip():
            return {
                "line_index": i,
                "dut": a_lines[i].strip(),
                "golden": b_lines[i].strip(),
                "dut_signature": str(sig_a),
                "golden_signature": str(sig_b),
            }
    if len(a_lines) != len(b_lines):
        return {
            "line_index": n,
            "dut": "<eof>" if len(a_lines) <= n else a_lines[n].strip(),
            "golden": "<eof>" if len(b_lines) <= n else b_lines[n].strip(),
            "dut_signature": str(sig_a),
            "golden_signature": str(sig_b),
            "note": f"length differs: dut={len(a_lines)} golden={len(b_lines)}",
        }
    return None


def _norm_trace_entry(raw: Dict[str, Any]) -> Dict[str, Any]:
    out: Dict[str, Any] = {}
    out["i"] = _parse_int(raw.get("i"))
    out["pc"] = _parse_int(raw.get("pc"))
    out["insn"] = _parse_int(raw.get("insn"))
    out["rd"] = _parse_int(raw.get("rd"))
    out["rd_val"] = _parse_int(raw.get("rd_val"))
    out["trap"] = bool(raw.get("trap")) if "trap" in raw else None
    mem = raw.get("mem")
    if isinstance(mem, dict):
        out["mem"] = {
            "we": bool(mem.get("we")) if "we" in mem else None,
            "addr": _parse_int(mem.get("addr")),
            "data": _parse_int(mem.get("data")),
            "size": _parse_int(mem.get("size")),
        }
    else:
        out["mem"] = None
    csr = raw.get("csr")
    if isinstance(csr, dict):
        out["csr"] = {"id": _parse_int(csr.get("id")), "val": _parse_int(csr.get("val"))}
    else:
        out["csr"] = None
    return out


def _load_trace(path: Path) -> List[Dict[str, Any]]:
    entries: List[Dict[str, Any]] = []
    for lineno, line in enumerate(path.read_text(encoding="utf-8", errors="ignore").splitlines(), start=1):
        s = line.strip()
        if not s or s.startswith("#"):
            continue
        obj = json.loads(s)
        if not isinstance(obj, dict):
            raise ValueError(f"{path}:{lineno}: expected json object")
        entries.append(_norm_trace_entry(obj))
    return entries


def _trace_first_divergence(trace_a: Path, trace_b: Path) -> Optional[Dict[str, Any]]:
    a = _load_trace(trace_a)
    b = _load_trace(trace_b)
    keys = ["pc", "insn", "rd", "rd_val", "mem", "csr", "trap"]
    n = min(len(a), len(b))
    for i in range(n):
        diffs = [k for k in keys if a[i].get(k) != b[i].get(k)]
        if diffs:
            return {
                "index": i,
                "fields": diffs,
                "dut": a[i],
                "golden": b[i],
                "dut_trace": str(trace_a),
                "golden_trace": str(trace_b),
            }
    if len(a) != len(b):
        return {
            "index": n,
            "fields": ["length"],
            "dut_len": len(a),
            "golden_len": len(b),
            "dut_trace": str(trace_a),
            "golden_trace": str(trace_b),
        }
    return None


def _objdump_elf(elf: Path, xlen: int, out_path: Path) -> Optional[str]:
    tool = _best_tool([f"riscv{xlen}-unknown-elf-objdump", "riscv64-unknown-elf-objdump", "riscv32-unknown-elf-objdump"])
    if not tool or not elf.exists():
        return None
    proc = _run_capture([tool, "-D", str(elf)])
    if proc.returncode != 0:
        return None
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(proc.stdout, encoding="utf-8")
    return str(out_path)


def _decode_single_insn(insn: Optional[int], xlen: int) -> Optional[str]:
    if insn is None:
        return None
    tool = _best_tool([f"riscv{xlen}-unknown-elf-objdump", "riscv64-unknown-elf-objdump", "riscv32-unknown-elf-objdump"])
    if not tool:
        return None
    with tempfile.TemporaryDirectory(prefix="borb-insn-") as td:
        b = Path(td) / "insn.bin"
        b.write_bytes(int(insn & 0xFFFFFFFF).to_bytes(4, byteorder="little", signed=False))
        proc = _run_capture([tool, "-D", "-b", "binary", f"-m", f"riscv:rv{xlen}", str(b)])
        if proc.returncode != 0:
            return None
        for line in proc.stdout.splitlines():
            if "\t" in line and ":" in line:
                cols = line.split("\t")
                if len(cols) >= 3:
                    return cols[-1].strip()
    return None


def _formal_parse_vcd_first_mismatch(vcd_path: Path) -> Optional[Dict[str, Any]]:
    if not vcd_path.exists():
        return None
    wanted_scope_suffix = ".checker_inst."
    wanted_pairs = [
        ("rvfi_valid", "spec_valid"),
        ("rvfi_pc_wdata", "spec_pc_wdata"),
        ("rvfi_rd_addr", "spec_rd_addr"),
        ("rvfi_rd_wdata", "spec_rd_wdata"),
        ("rvfi_mem_addr", "spec_mem_addr"),
        ("rvfi_mem_wdata", "spec_mem_wdata"),
        ("rvfi_mem_wmask", "spec_mem_wmask"),
        ("rvfi_mem_rmask", "spec_mem_rmask"),
        ("rvfi_trap", "spec_trap"),
        ("rvfi_rs1_addr", "spec_rs1_addr"),
        ("rvfi_rs2_addr", "spec_rs2_addr"),
    ]
    required = {a for a, _ in wanted_pairs} | {b for _, b in wanted_pairs} | {"rvfi_valid", "spec_valid", "rvfi_insn", "rvfi_pc_rdata"}

    scope_stack: List[str] = []
    sym_to_name: Dict[str, str] = {}
    name_to_sym: Dict[str, str] = {}

    with vcd_path.open("r", encoding="utf-8", errors="ignore") as f:
        for line in f:
            s = line.strip()
            if s.startswith("$scope"):
                parts = s.split()
                if len(parts) >= 3:
                    scope_stack.append(parts[2])
                continue
            if s.startswith("$upscope"):
                if scope_stack:
                    scope_stack.pop()
                continue
            if s.startswith("$var"):
                parts = s.split()
                if len(parts) >= 5:
                    sym = parts[3]
                    name = parts[4]
                    full = ".".join(scope_stack + [name])
                    sym_to_name[sym] = full
                    idx = full.find(wanted_scope_suffix)
                    if idx != -1:
                        short = full[idx + len(wanted_scope_suffix) :]
                        if "." not in short and short in required:
                            name_to_sym[short] = sym
                continue
            if s.startswith("$enddefinitions"):
                break

    if "rvfi_valid" not in name_to_sym or "spec_valid" not in name_to_sym:
        return None

    def scalar(v: Optional[str]) -> Optional[int]:
        if v is None:
            return None
        if v in ("0", "1"):
            return int(v)
        if v in ("b0", "b1"):
            return int(v[1])
        return None

    def bits_to_int(v: Optional[str]) -> Optional[int]:
        if v is None:
            return None
        x = v.lower()
        if any(ch in x for ch in ("x", "z")):
            return None
        try:
            return int(x, 2)
        except ValueError:
            return None

    def cmp_value(raw: Optional[str]) -> Any:
        if raw is None:
            return None
        if raw in ("0", "1"):
            return int(raw, 2)
        if raw.startswith("b"):
            val = bits_to_int(raw[1:])
            return val if val is not None else raw
        return raw

    current: Dict[str, str] = {}
    current_time: Optional[int] = None
    first: Optional[Dict[str, Any]] = None
    first_active: Optional[Dict[str, Any]] = None

    def evaluate(ts: int) -> Optional[Dict[str, Any]]:
        rvfi_valid = scalar(current.get(name_to_sym["rvfi_valid"]))
        spec_valid = scalar(current.get(name_to_sym["spec_valid"]))
        if rvfi_valid != 1 and spec_valid != 1:
            return None
        insn_val = cmp_value(current.get(name_to_sym.get("rvfi_insn", "")))
        pc_val = cmp_value(current.get(name_to_sym.get("rvfi_pc_rdata", "")))
        nonlocal first_active
        if first_active is None:
            first_active = {
                "time": ts,
                "pc": _hex(pc_val if isinstance(pc_val, int) else None),
                "insn": _hex(insn_val if isinstance(insn_val, int) else None),
                "insn_int": insn_val if isinstance(insn_val, int) else None,
                "mismatches": [],
                "note": "no direct rvfi/spec field mismatch at first active state",
            }
        mismatches: List[Dict[str, Any]] = []
        for rvfi_name, spec_name in wanted_pairs:
            rvfi_raw = current.get(name_to_sym.get(rvfi_name, ""))
            spec_raw = current.get(name_to_sym.get(spec_name, ""))
            rvfi_val = cmp_value(rvfi_raw)
            spec_val = cmp_value(spec_raw)
            if rvfi_val != spec_val:
                mismatches.append({"field": rvfi_name.replace("rvfi_", ""), "rvfi": _hex(rvfi_val), "spec": _hex(spec_val)})
        if not mismatches:
            return None
        insn_val = cmp_value(current.get(name_to_sym.get("rvfi_insn", "")))
        pc_val = cmp_value(current.get(name_to_sym.get("rvfi_pc_rdata", "")))
        return {
            "time": ts,
            "pc": _hex(pc_val if isinstance(pc_val, int) else None),
            "insn": _hex(insn_val if isinstance(insn_val, int) else None),
            "insn_int": insn_val if isinstance(insn_val, int) else None,
            "mismatches": mismatches,
        }

    with vcd_path.open("r", encoding="utf-8", errors="ignore") as f:
        for line in f:
            s = line.strip()
            if not s or s.startswith("$"):
                continue
            if s.startswith("#"):
                if current_time is not None and first is None:
                    first = evaluate(current_time)
                    if first is not None:
                        break
                current_time = int(s[1:], 10)
                continue
            if s[0] in "01xz":
                current[s[1:]] = s[0]
                continue
            if s[0] == "b":
                parts = s.split()
                if len(parts) == 2:
                    current[parts[1]] = parts[0]
                continue
        if first is None and current_time is not None:
            first = evaluate(current_time)
    return first if first is not None else first_active


def _formal_reports(checks_dir: Path, xlen: int) -> int:
    failed = sorted(p.parent for p in checks_dir.glob("*/FAIL"))
    generated = 0
    for check_dir in failed:
        engine = check_dir / "engine_0"
        log_path = engine / "logfile.txt"
        vcd_path = engine / "trace.vcd"
        report_dir = check_dir / "debug"

        assertions = []
        if log_path.exists():
            for line in log_path.read_text(encoding="utf-8", errors="ignore").splitlines():
                if "Assert failed" in line:
                    assertions.append(line.strip())

        first = _formal_parse_vcd_first_mismatch(vcd_path)
        asm = _decode_single_insn(first.get("insn_int") if first else None, xlen) if first else None
        if first is not None:
            first["insn_disasm"] = asm
            first.pop("insn_int", None)

        payload = {
            "kind": "formal",
            "check": check_dir.name,
            "check_dir": str(check_dir),
            "log_path": str(log_path) if log_path.exists() else None,
            "vcd_path": str(vcd_path) if vcd_path.exists() else None,
            "assertions": assertions,
            "first_divergence": first,
        }
        md = [
            f"# Formal Failure Report: {check_dir.name}",
            "",
            f"- Check dir: `{check_dir}`",
            f"- VCD: `{vcd_path}`",
            f"- Log: `{log_path}`",
            "",
            "## Assertions",
        ]
        if assertions:
            md.extend([f"- {a}" for a in assertions[:10]])
        else:
            md.append("- none found")
        md.extend(["", "## First Divergence (RVFI vs Spec)"])
        if first:
            md.append(f"- time: `{first.get('time')}`")
            md.append(f"- pc: `{first.get('pc')}`")
            md.append(f"- insn: `{first.get('insn')}`")
            md.append(f"- disasm: `{first.get('insn_disasm') or 'n/a'}`")
            for m in first.get("mismatches", []):
                md.append(f"- {m['field']}: rvfi={m['rvfi']} spec={m['spec']}")
        else:
            md.append("- no mismatch decoded from VCD")
        _write_json_and_md(report_dir, "failure_report", payload, md)
        generated += 1
    print(f"[formal] generated reports: {generated}")
    return 0


def _discover_riscof_workdirs(root: Path) -> List[Path]:
    dirs = []
    default = root / "verif" / "riscof" / "riscof_work"
    if default.is_dir():
        dirs.append(default)
    dirs.extend(sorted((root / "verif" / "riscof").glob("riscof_work*"), key=lambda p: p.stat().st_mtime, reverse=True))
    seen = set()
    out = []
    for d in dirs:
        s = str(d.resolve())
        if s not in seen and d.is_dir():
            seen.add(s)
            out.append(d)
    return out


def _riscof_reports(workdir: Optional[Path], xlen: int, rerun: bool) -> int:
    repo_root = Path(__file__).resolve().parents[1]
    workdirs = [workdir] if workdir else _discover_riscof_workdirs(repo_root)
    if not workdirs:
        print("[riscof] no workdir found")
        return 0
    target = workdirs[0]
    generated = 0
    for dut_sig in sorted(target.glob("**/dut/DUT-borb.signature")):
        dut_dir = dut_sig.parent
        test_dir = dut_dir.parent
        ref_sigs = sorted((test_dir / "ref").glob("*.signature"))
        if not ref_sigs:
            continue
        ref_sig = None
        for s in ref_sigs:
            n = s.name.lower()
            if "reference" in n or "spike" in n:
                ref_sig = s
                break
        if ref_sig is None:
            ref_sig = ref_sigs[0]

        sig_div = _compare_signatures(dut_sig, ref_sig)
        if sig_div is None:
            continue

        if rerun:
            dbg_script = repo_root / "verif" / "riscof" / "borb" / "riscof_debug_one.sh"
            if dbg_script.exists():
                _run_capture([str(dbg_script), str(dut_dir), "--xlen", str(xlen)])

        debug_dir = dut_dir / "debug"
        elf = dut_dir / "my.elf"
        dump_path = debug_dir / "dut.dump"
        dump_out = _objdump_elf(elf, xlen, dump_path)

        dut_trace = debug_dir / f"{dut_dir.name}.trace.jsonl"
        ref_trace = debug_dir / f"{dut_dir.name}.ref.trace.jsonl"
        trace_div = _trace_first_divergence(dut_trace, ref_trace) if dut_trace.exists() and ref_trace.exists() else None

        payload = {
            "kind": "riscof",
            "workdir": str(target),
            "test_dir": str(test_dir),
            "dut_signature": str(dut_sig),
            "golden_signature": str(ref_sig),
            "first_signature_divergence": sig_div,
            "dut_trace": str(dut_trace) if dut_trace.exists() else None,
            "golden_trace": str(ref_trace) if ref_trace.exists() else None,
            "first_trace_divergence": trace_div,
            "disassembly": dump_out,
        }
        md = [
            f"# RISCOF Failure Report: {test_dir.name}",
            "",
            f"- Test dir: `{test_dir}`",
            f"- DUT signature: `{dut_sig}`",
            f"- Golden signature: `{ref_sig}`",
            f"- Disassembly: `{dump_out or 'n/a'}`",
            "",
            "## First Signature Divergence",
            f"- line_index: `{sig_div['line_index']}`",
            f"- dut: `{sig_div['dut']}`",
            f"- golden: `{sig_div['golden']}`",
            "",
            "## First Trace Divergence",
        ]
        if trace_div:
            md.append(f"- index: `{trace_div.get('index')}`")
            md.append(f"- fields: `{', '.join(trace_div.get('fields', []))}`")
        else:
            md.append("- no DUT/golden trace pair found in debug dir")
        _write_json_and_md(debug_dir, "failure_report", payload, md)
        generated += 1
    print(f"[riscof] generated reports: {generated} (workdir={target})")
    return 0


def _discover_tenstorrent_summary(repo_root: Path) -> Optional[Path]:
    roots = sorted((repo_root / "verif" / "tenstorrent-riscv-arch-tests" / "out").glob("*/summary.json"), key=lambda p: p.stat().st_mtime, reverse=True)
    return roots[0] if roots else None


def _tenstorrent_reports(summary_path: Optional[Path], xlen: int) -> int:
    repo_root = Path(__file__).resolve().parents[1]
    summary = summary_path or _discover_tenstorrent_summary(repo_root)
    if summary is None or not summary.exists():
        print("[tenstorrent] summary not found")
        return 0
    data = json.loads(summary.read_text(encoding="utf-8"))
    generated = 0
    for r in data.get("results", []):
        status = r.get("status")
        if status == "PASS":
            continue
        tdir = Path(r.get("workdir", ""))
        if not tdir.exists():
            continue
        debug_dir = tdir / "debug"
        elf = tdir / "test.elf"
        dump_out = _objdump_elf(elf, xlen, debug_dir / "test.dump")

        dut_sig = tdir / "DUT-borb.signature"
        golden_sig = None
        for cand in sorted(tdir.glob("*.signature")):
            if cand.name != "DUT-borb.signature":
                golden_sig = cand
                break
        sig_div = _compare_signatures(dut_sig, golden_sig) if golden_sig else None

        dut_trace = None
        golden_trace = None
        for cand in sorted(tdir.glob("*.trace.jsonl")):
            name = cand.name.lower()
            if "ref" in name or "golden" in name:
                golden_trace = cand
            elif "manual" in name or "dut" in name or "invest" in name:
                dut_trace = cand
        trace_div = _trace_first_divergence(dut_trace, golden_trace) if dut_trace and golden_trace else None

        payload = {
            "kind": "tenstorrent",
            "summary": str(summary),
            "test": r.get("name"),
            "status": status,
            "tohost": r.get("tohost"),
            "note": r.get("note"),
            "workdir": str(tdir),
            "disassembly": dump_out,
            "first_signature_divergence": sig_div,
            "first_trace_divergence": trace_div,
            "dut_trace": str(dut_trace) if dut_trace else None,
            "golden_trace": str(golden_trace) if golden_trace else None,
        }
        md = [
            f"# Tenstorrent Failure Report: {r.get('name')}",
            "",
            f"- status: `{status}`",
            f"- tohost: `{r.get('tohost')}`",
            f"- note: `{r.get('note')}`",
            f"- workdir: `{tdir}`",
            f"- disassembly: `{dump_out or 'n/a'}`",
            "",
            "## First Signature Divergence",
        ]
        if sig_div:
            md.append(f"- line_index: `{sig_div.get('line_index')}`")
            md.append(f"- dut: `{sig_div.get('dut')}`")
            md.append(f"- golden: `{sig_div.get('golden')}`")
        else:
            md.append("- no golden signature found")
        md.extend(["", "## First Trace Divergence"])
        if trace_div:
            md.append(f"- index: `{trace_div.get('index')}`")
            md.append(f"- fields: `{', '.join(trace_div.get('fields', []))}`")
        else:
            md.append("- no DUT/golden trace pair found")
        _write_json_and_md(debug_dir, "failure_report", payload, md)
        generated += 1
    print(f"[tenstorrent] generated reports: {generated} (summary={summary})")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description="Generate debugging artifacts for formal/RISCOF/Tenstorrent failures")
    sub = ap.add_subparsers(dest="mode", required=True)

    ap_formal = sub.add_parser("formal", help="Generate reports for riscv-formal failures")
    ap_formal.add_argument("--checks-dir", default="formal/cores/borb/checks")
    ap_formal.add_argument("--xlen", type=int, default=64, choices=[32, 64])

    ap_riscof = sub.add_parser("riscof", help="Generate reports for RISCOF failures")
    ap_riscof.add_argument("--workdir", default="")
    ap_riscof.add_argument("--xlen", type=int, default=64, choices=[32, 64])
    ap_riscof.add_argument("--no-rerun", action="store_true")

    ap_tt = sub.add_parser("tenstorrent", help="Generate reports for Tenstorrent failures")
    ap_tt.add_argument("--summary", default="")
    ap_tt.add_argument("--xlen", type=int, default=64, choices=[32, 64])

    args = ap.parse_args()

    if args.mode == "formal":
        return _formal_reports(Path(args.checks_dir).resolve(), args.xlen)
    if args.mode == "riscof":
        wd = Path(args.workdir).resolve() if args.workdir else None
        return _riscof_reports(wd, args.xlen, rerun=not args.no_rerun)
    if args.mode == "tenstorrent":
        summary = Path(args.summary).resolve() if args.summary else None
        return _tenstorrent_reports(summary, args.xlen)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
