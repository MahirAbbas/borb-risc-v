# AGENTS

Be aggressive on performance upgrades.
Use `./run_act4.sh --profile rva23s64-full --verilate-jobs 10 --sim-jobs 10 --sim-threads 1` for architectural regression testing. It's better to waste cycles doing regression tests than wasting tokens on regression. 

## Tooling

### `run_act4.sh`
- Purpose: Build and run the ACT4 RVA23S64 architectural flow on borb.
- Path: `/Users/mahir/fun/borb/run_act4.sh`
- Notes:
  - Supports focused extension runs with `--extensions`.

### `waveform-mcp`
- Purpose: Waveform analysis helpers (signal browsing, transitions, WAL queries).
- Path: `/Users/mahir/fun/borb/waveform-mcp`
- Notes:
  - Useful for fast root-cause analysis of failing traces/FSTs.

### `run_tenstorrent.sh`
- Purpose: Build and run Tenstorrent RISC-V arch tests on borb.
- Path: `/Users/mahir/fun/borb/run_tenstorrent.sh`
- Notes:
  - Invokes `scripts/tenstorrent_borb_runner.py`.
  - Auto-generates failure reports after each run.

### `run_formal.sh`
- Purpose: Execute riscv-formal checks for borb.
- Path: `/Users/mahir/fun/borb/run_formal.sh`
- Notes:
  - Supports running all checks, failed-only, or selected check lists.
  - Auto-generates failure reports on formal failures.

### `scripts/riscv_failure_report.py`
- Purpose: Unified failure artifact generator for formal and Tenstorrent.
- Path: `/Users/mahir/fun/borb/scripts/riscv_failure_report.py`
- Modes:
  - `formal`: parses formal failure traces and extracts first RVFI/spec divergence.
  - `tenstorrent`: summarizes non-pass tests with tohost context and disassembly.

### `run_directed.sh` + `scripts/directed_asm_runner.py`
- Purpose: Reusable directed assembly testbench flow for fast bug isolation.
- Paths:
  - `/Users/mahir/fun/borb/run_directed.sh`
  - `/Users/mahir/fun/borb/scripts/directed_asm_runner.py`
- Notes:
  - Compiles `.S` with RISC-V toolchain (`gcc`) using `verif/act4/borb-rva23s64/link.ld`.
  - Extracts `tohost`, `begin_signature`, `end_signature` from ELF (`nm`).
  - Runs borb sim with `--elf`, commit trace, signature dump, and optional FST.
  - Emits per-run artifact directory with `summary.json`, disassembly, traces, and signatures.

### `debug_failures.sh`
- Purpose: One-shot wrapper to generate all failure reports.
- Path: `/Users/mahir/fun/borb/debug_failures.sh`
- Examples:
  - `./debug_failures.sh`
  - `./debug_failures.sh --formal`
  - `./debug_failures.sh --tenstorrent --summary verif/tenstorrent-riscv-arch-tests/out/borb/summary.json`
