# RVA23S64 Execution Checklist

Date: 2026-02-24

This is the execution tracker for bringing borb to RVA23S64 with strong automation and performance discipline.

## 0. Correctness Baseline
- [x] Fix `bge`/`bgeu` branch-path regressions.
- [x] Resolve remaining `misalign-*` privilege branch failures.
- [x] Freeze a green RV64I baseline run (full selected suite + artifact link).
- [x] Gate merges on RV64I smoke + formal smoke.

## 1. Compliance Matrix (RVA23S64)
- [ ] Add per-requirement matrix row with spec references.
- [ ] Tag each row as `missing/partial/implemented/tested`.
- [ ] Link each row to exact tests and latest evidence artifacts.
- [ ] Assign RTL/test owner per requirement.

## 2. Privileged + VM Foundation
- [ ] Complete M/S CSR behavior required for profile.
- [ ] Complete trap/delegation/return flow (`MRET/SRET`) coverage.
- [ ] Implement Sv39 translation + access/fault semantics.
- [ ] Add deterministic directed test suite for exceptions/VM faults.

## 3. Extension Bring-up
- [x] RV64M completion + compliance evidence.
- [ ] RV64A completion + AMO/LRSC memory-model checks.
- [ ] RV64F/D completion + compliance evidence.
- [ ] RV64C completion + compliance evidence.
- [ ] Additional profile-required Z* extensions completion.

## 4. Tenstorrent riscv-arch-test Integration
- [x] Add borb-specific Tenstorrent scaffold (compile/run/report flow).
- [ ] Add RV64I machine bare paging smoke target to CI/nightly.
- [ ] Add extension-lane selection (`rv_m`, `rv_a`, `rv_f`, `rv_d`, `rv_c`, `rv_z*`).
- [ ] Add privilege/virtualization suites as S-mode/VM features land.
- [ ] Add failure artifact capture (ELF/BIN/disasm/tohost/wave).

## 5. Performance Counters + Measurement
- [ ] Define counter taxonomy v1 (cycles, instret, stall classes, redirects, memory stalls).
- [ ] Implement counter readout interface and stable dump format.
- [ ] Build performance measurement harness with fixed-seed reproducibility.
- [ ] Add multi-run aggregation (mean/p95/stddev) and trend output.
- [ ] Add perf regression threshold checks to CI/nightly.

## 6. Benchmark Integration
- [ ] Integrate CoreMark automation.
- [ ] Integrate Embench automation.
- [ ] Integrate Dhrystone automation.
- [ ] Add memory microbench suite (copy/stream/pointer-chase/latency).
- [ ] Produce consolidated benchmark report per commit window.

## 7. Additional Automated Verification/Performance Tools
- [ ] Integrate `riscv-dv` random testing with seed management.
- [ ] Expand strict riscv-formal property set and nightly depth profile.
- [ ] Add differential lockstep/replay tooling for first-divergence triage.
- [ ] Add waveform auto-capture and triage script on failing tests.
- [ ] Add long-run nightly regression orchestration + dashboard summary.

## 8. Performance Program (In-order)
- [ ] Frontend efficiency workstream (predict/redirect penalty reduction).
- [ ] Hazard/bypass optimization workstream.
- [ ] Memory subsystem workstream (buffering/latency behavior).
- [ ] Timing/perf-per-watt optimization workflow.

## Exit Criteria (Program-Level)
- [ ] RVA23S64 requirements: all rows `implemented + tested`.
- [ ] Verification gates: per-PR fast gate + nightly deep gate green/stable.
- [ ] Performance: CPI/perf-per-watt trends positive on benchmark mix.
