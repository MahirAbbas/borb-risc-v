# Tenstorrent Suite Scaffold (borb)

Date: 2026-02-24

This scaffold wires Tenstorrent `riscv-arch-tests` into a borb-oriented compile/run/report flow.

## What It Does
- Parses Tenstorrent `.list` files.
- Compiles selected tests with local RISC-V GCC.
- Runs each test on `verif/riscof/borb/build/borb-sim`.
- Captures per-test `tohost` and emits a machine-readable summary JSON.
- Relocates `.io_htif` to `0x807ff000` at link time to avoid false `tohost` aliasing in small wrapped-RAM simulation.

## Default Scope
- Default list: `riscv_tests/bare_metal/machine/paging_bare/rv_i.list`
- Default ISA compile target: `rv64gcv_zicsr_zifencei`
- Default behavior is strict RV64I-only filtering (`rv64i*` names).
- Use `--allow-non-rv64i` only when intentionally broadening beyond RV64I.
- Default behavior applies a single-hart startup patch that bypasses Tenstorrent AMO lock/wait in `tohost_try_lock` to avoid dead-loop timeouts on current bring-up.
- Use `--no-single-hart-patch` to disable this and run the unmodified startup code.

Note: the compile `--march` default is broader than RV64I because current Tenstorrent generated startup code assumes IMFV-style initialization. The test *selection* remains RV64I-only by name.
Default cycle budget is set higher for this suite's long startup/synchronization loops.

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
