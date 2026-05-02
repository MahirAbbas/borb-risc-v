# CoreMark on borb

This directory contains a bare-metal CoreMark integration for borb simulator.

## Prerequisites

1. RISC-V GCC toolchain in `PATH` (`riscv64-unknown-elf-gcc`, `nm`, `objdump` or rv32 variants).
2. CoreMark source checkout at `verif/benchmarks/coremark/coremark` (or pass `--coremark-dir`).
3. Built borb simulator (`verif/borb-sim/build/obj_dir/VSoC`), or run with `--rebuild-sim`.

Example setup:

```bash
git clone https://github.com/eembc/coremark.git verif/benchmarks/coremark/coremark
```

## Running

```bash
./run_coremark.sh --iterations 50
```

Useful options:

- `--profile`: emit `perf.json` with hardware counters and derived CPI/IPC metrics.
- `--trace`: emit waveform FST.
- `--trace-commit`: emit per-commit JSON trace. Leave this off for speed.
- `--rebuild-sim`: rebuild simulator before run.
- `--march`, `--mabi`, `--xlen`: toolchain ISA/ABI selection.

Outputs are written under `verif/benchmarks/coremark/out/<run>_<timestamp>/`.

## Profiling behavior

Performance profiling is **opt-in**. No perf report is generated unless `--profile` is passed.
This keeps ACT4 and other existing flows unaffected.
