#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import shlex
import subprocess
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Sequence


def normalize_riscv_march(march: str) -> str:
    normalized = march.strip().lower().lstrip(":")
    aliases = {
        "rv64imafcsu_zicsr_zifencei": "rv64imafc_zicsr_zifencei",
        "rv64imafcsuzicsr_zifencei": "rv64imafc_zicsr_zifencei",
        "rv64imafcsu_zifencei_zicsr": "rv64imafc_zicsr_zifencei",
        "rv64imafcsuzifencei_zicsr": "rv64imafc_zicsr_zifencei",
        "rv64imafdcsu_zicsr_zifencei": "rv64imafdc_zicsr_zifencei",
        "rv64imafdcsuzicsr_zifencei": "rv64imafdc_zicsr_zifencei",
        "rv64imafdcsu_zifencei_zicsr": "rv64imafdc_zicsr_zifencei",
        "rv64imafdcsuzifencei_zicsr": "rv64imafdc_zicsr_zifencei",
    }
    return aliases.get(normalized, normalized)


def select_coremark_mode(explicit: str, total_data_size: int) -> str:
    if explicit != "auto":
        return explicit
    if total_data_size == 1200:
        return "profile"
    if total_data_size == 2000:
        return "performance"
    return "validation"


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


def require_files(paths: Sequence[Path]) -> None:
    missing = [str(p) for p in paths if not p.exists()]
    if missing:
        raise SystemExit(f"missing required files:\n  " + "\n  ".join(missing))


def extract_score(stdout_text: str) -> Optional[float]:
    for line in stdout_text.splitlines():
        if "CoreMark 1.0" in line and ":" in line:
            try:
                return float(line.split(":", 1)[1].strip())
            except ValueError:
                continue
    return None


def parse_makefile_threads(sim_make_dir: Path) -> Optional[int]:
    makefile = sim_make_dir / "Makefile"
    if not makefile.exists():
        return None
    for line in makefile.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if stripped.startswith("THREADS ?="):
            value = stripped.split("=", 1)[1].strip()
            try:
                return int(value)
            except ValueError:
                return None
    return None


def parse_built_sim_threads(sim_make_dir: Path) -> Optional[int]:
    generated = sim_make_dir.parent / "build" / "obj_dir" / "VSoC.cpp"
    if not generated.exists():
        return None
    needle = "unsigned VSoC::threads() const { return "
    for line in generated.read_text(encoding="utf-8").splitlines():
        if needle in line:
            value = line.split(needle, 1)[1].split(";", 1)[0].strip()
            try:
                return int(value)
            except ValueError:
                return None
    return None


def main() -> int:
    ap = argparse.ArgumentParser(description="Build and run CoreMark on borb simulator.")
    ap.add_argument("--coremark-dir", default="verif/benchmarks/coremark/coremark", help="Path to CoreMark source tree")
    ap.add_argument("--port-dir", default="verif/benchmarks/coremark/port", help="Path to borb CoreMark port files")
    ap.add_argument("--name", default="coremark", help="Run name prefix")
    ap.add_argument("--out-root", default="verif/benchmarks/coremark/out", help="Output directory root")
    ap.add_argument("--sim", default="verif/borb-sim/build/borb-sim", help="Path to borb simulator binary")
    ap.add_argument("--sim-make-dir", default="verif/borb-sim/sim", help="Directory for simulator Makefile")
    ap.add_argument("--rebuild-sim", action="store_true", help="Rebuild borb-sim before running")
    ap.add_argument("--xlen", type=int, default=64, choices=[32, 64], help="XLEN/toolchain width")
    ap.add_argument("--march", default="RV64IMAFDCSUZicsr_Zifencei", help="ISA string passed to GCC")
    ap.add_argument("--mabi", default="lp64", help="ABI string passed to GCC")
    ap.add_argument("--iterations", type=int, default=1, help="CoreMark iteration count")
    ap.add_argument("--cpu-hz", type=int, default=100_000_000, help="Clock frequency used for time conversion")
    ap.add_argument("--total-data-size", type=int, default=1200, help="CoreMark TOTAL_DATA_SIZE")
    ap.add_argument(
        "--coremark-mode",
        choices=["auto", "performance", "validation", "profile"],
        default="auto",
        help="Compile-time CoreMark workload selection. 'auto' follows upstream TOTAL_DATA_SIZE defaults.",
    )
    ap.add_argument("--seed1", type=int, default=None, help="Override CoreMark seed1")
    ap.add_argument("--seed2", type=int, default=None, help="Override CoreMark seed2")
    ap.add_argument("--seed3", type=int, default=None, help="Override CoreMark seed3")
    ap.add_argument("--seed4", type=int, default=None, help="Override CoreMark seed4/iterations seed")
    ap.add_argument("--seed5", type=int, default=None, help="Override CoreMark seed5/exec mask")
    ap.add_argument("--max-cycles", type=int, default=1_000_000, help="Simulation cycle budget")
    ap.add_argument("--trace", action="store_true", help="Emit FST waveform")
    ap.add_argument("--trace-commit", action="store_true", help="Emit per-commit JSON trace")
    ap.add_argument("--profile", action="store_true", help="Enable simulator perf report generation")
    args = ap.parse_args()

    coremark_dir = Path(args.coremark_dir).resolve()
    port_dir = Path(args.port_dir).resolve()
    out_root = Path(args.out_root).resolve()
    sim = Path(args.sim).resolve()
    sim_make_dir = Path(args.sim_make_dir).resolve()
    march = normalize_riscv_march(args.march)
    coremark_mode = select_coremark_mode(args.coremark_mode, args.total_data_size)

    gcc = f"riscv{args.xlen}-unknown-elf-gcc"
    nm = f"riscv{args.xlen}-unknown-elf-nm"
    objdump = f"riscv{args.xlen}-unknown-elf-objdump"
    ensure_tool(gcc)
    ensure_tool(nm)
    ensure_tool(objdump)

    source_files = [
        coremark_dir / "core_list_join.c",
        coremark_dir / "core_main.c",
        coremark_dir / "core_matrix.c",
        coremark_dir / "core_state.c",
        coremark_dir / "core_util.c",
        port_dir / "core_portme.c",
        port_dir / "ee_printf.c",
        port_dir / "runtime.c",
        port_dir / "crt0.S",
        port_dir / "link_coremark.ld",
        port_dir / "core_portme.h",
    ]
    require_files(source_files)

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

    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    out_dir = out_root / f"{args.name}_{ts}"
    out_dir.mkdir(parents=True, exist_ok=True)

    elf = out_dir / "coremark.elf"
    dump = out_dir / "coremark.dump"
    signature = out_dir / "DUT-borb.signature"
    tohost_txt = out_dir / "tohost.txt"
    commit_trace = out_dir / "commit_trace.log"
    perf_json = out_dir / "perf.json"
    fst = out_dir / "wave.fst"

    compile_cmd: List[str] = [
        gcc,
        f"-march={march}",
        f"-mabi={args.mabi}",
        "-O3",
        "-fno-common",
        "-fno-builtin",
        "-fno-exceptions",
        "-ffreestanding",
        "-nostdlib",
        "-nostartfiles",
        "-static",
        "-mcmodel=medany",
        "-g",
        f"-DITERATIONS={args.iterations}",
        f"-DTOTAL_DATA_SIZE={args.total_data_size}",
        f"-DPERFORMANCE_RUN={1 if coremark_mode == 'performance' else 0}",
        f"-DVALIDATION_RUN={1 if coremark_mode == 'validation' else 0}",
        f"-DPROFILE_RUN={1 if coremark_mode == 'profile' else 0}",
        "-DMEM_METHOD=MEM_STATIC",
        "-DSEED_METHOD=SEED_VOLATILE",
        f"-DCOREMARK_CPU_HZ={args.cpu_hz}",
        f"-I{coremark_dir}",
        f"-I{port_dir}",
        "-T",
        str(port_dir / "link_coremark.ld"),
    ]
    for macro, value in [
        ("COREMARK_SEED1", args.seed1),
        ("COREMARK_SEED2", args.seed2),
        ("COREMARK_SEED3", args.seed3),
        ("COREMARK_SEED4", args.seed4),
        ("COREMARK_SEED5", args.seed5),
    ]:
        if value is not None:
            compile_cmd.append(f"-D{macro}={value}")
    compile_cmd.extend(str(p) for p in source_files[:-2])
    compile_cmd.extend(["-o", str(elf)])

    comp = run_cmd(compile_cmd, capture=True)
    if comp.returncode != 0:
        (out_dir / "compile.stdout.log").write_text(comp.stdout or "", encoding="utf-8")
        (out_dir / "compile.stderr.log").write_text(comp.stderr or "", encoding="utf-8")
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

    sim_cmd: List[str] = [
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
    if args.profile:
        sim_cmd.extend(["--report-perf", str(perf_json)])

    sim_run = run_cmd(sim_cmd, capture=True)
    (out_dir / "sim.stdout.log").write_text(sim_run.stdout or "", encoding="utf-8")
    (out_dir / "sim.stderr.log").write_text(sim_run.stderr or "", encoding="utf-8")

    tohost_val = read_hex_file(tohost_txt)
    score = extract_score(sim_run.stdout or "")

    perf_data: Optional[Dict[str, object]] = None
    if perf_json.exists():
        perf_data = json.loads(perf_json.read_text(encoding="utf-8"))
        # Benchmark-specific derived metrics (kept in runner so generic sim stays unchanged).
        derived = perf_data.setdefault("derived", {})
        cycles = perf_data.get("counters", {}).get("cycles", 0)
        try:
            cycles_int = int(cycles)
        except (TypeError, ValueError):
            cycles_int = 0
        if cycles_int > 0:
            coremark_score_est = (args.iterations * args.cpu_hz) / float(cycles_int)
            coremark_per_mhz_est = coremark_score_est / (args.cpu_hz / 1_000_000.0)
            derived["coremark_score_estimate"] = coremark_score_est
            derived["coremark_per_mhz_estimate"] = coremark_per_mhz_est
            if score is None:
                score = coremark_score_est
            if score is not None:
                derived["coremark_per_mhz"] = score / (args.cpu_hz / 1_000_000.0)
            else:
                derived["coremark_per_mhz"] = coremark_per_mhz_est
        perf_json.write_text(json.dumps(perf_data, indent=2), encoding="utf-8")

    status = "PASS" if sim_run.returncode == 0 and tohost_val == 1 else "FAIL"
    configured_threads = parse_makefile_threads(sim_make_dir)
    built_threads = parse_built_sim_threads(sim_make_dir)
    summary = {
        "status": status,
        "out_dir": str(out_dir),
        "coremark_dir": str(coremark_dir),
        "coremark_mode": coremark_mode,
        "compile_cmd": compile_cmd,
        "sim_cmd": sim_cmd,
        "sim_returncode": sim_run.returncode,
        "tohost": None if tohost_val is None else hex(tohost_val),
        "coremark_score": score,
        "simulator_threads": {
            "configured_default": configured_threads,
            "built_model": built_threads,
        },
        "elf": str(elf),
        "dump": str(dump),
        "signature": str(signature),
        "commit_trace": str(commit_trace) if args.trace_commit else None,
        "perf_report": str(perf_json) if args.profile else None,
        "wave": str(fst) if args.trace else None,
        "symbols": {
            "tohost": hex(tohost if tohost is not None else 0),
            "begin_signature": hex(sig_begin if sig_begin is not None else 0),
            "end_signature": hex(sig_end if sig_end is not None else 0),
        },
        "perf": perf_data,
    }
    (out_dir / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")

    print(json.dumps(summary, indent=2))
    return 0 if status == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
