# Tenstorrent Suite Scaffold (borb)

Date: 2026-02-24

This scaffold wires Tenstorrent `riscv-arch-tests` into a borb-oriented compile/run/report flow.

## What It Does
- Parses Tenstorrent `.list` files.
- Compiles selected tests with local RISC-V GCC.
- Runs each test on `verif/riscof/borb/sim/build/borb-sim`.
- Captures per-test `tohost` and emits a machine-readable summary JSON.

## Default Scope
- Default list: `riscv_tests/bare_metal/machine/paging_bare/rv_i.list`
- Default ISA compile target: `rv64i_zicsr_zifencei`
- Default behavior is RV64-only filtering (`rv64*` names).

## Entry Points
- Wrapper script: `./run_tenstorrent.sh`
- Python runner: `scripts/tenstorrent_borb_runner.py`

## Example Commands
```bash
# Quick smoke: 5 RV64I tests
./run_tenstorrent.sh --limit 5

# Focus one test by regex
./run_tenstorrent.sh --filter '^rv64i_1$'

# Use a different list file
./run_tenstorrent.sh \
  --list riscv_tests/bare_metal/machine/paging_bare/rv_m.list \
  --limit 20
```

## Outputs
- Per-test artifacts under: `verif/tenstorrent-riscv-arch-tests/out/borb/<test_name>/`
- Aggregate summary: `verif/tenstorrent-riscv-arch-tests/out/borb/summary.json`

## Notes / TODO
- This is scaffolding, not final CI policy.
- Current pass/fail uses `tohost == 1` as pass convention.
- As new ISA/extensions land, adjust `--march` and list selection strategy.
- Add waveform auto-capture on failures in the next iteration.
