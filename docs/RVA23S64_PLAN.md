# RVA23S64 Roadmap and Compliance Matrix

Date: 2026-02-24

## Objective
Build a high-performance, high-efficiency in-order CPU that is compliant with the RVA23S64 profile, while maintaining continuous correctness and measurable performance progress.

## Current Snapshot (First Pass)
This snapshot is based on current RTL and active verification status in this repo.

### A. Baseline ISA/Execution
- RV64I integer pipeline: `PARTIAL`
  - RISCOF branch subset (`beq,bne,blt,bltu,bge,bgeu`) currently passes.
  - Privilege-side misaligned branch tests still fail.
- RV64M: `PARTIAL/UNKNOWN`
  - Encodings exist in decode tables, but end-to-end verification status is not yet established.
- RV64A: `MISSING`
  - Encodings exist, but no full execution/atomic memory semantics validation.
- RV64F/RV64D: `MISSING`
  - FPU sources exist, but no profile-level bring-up/validation evidence yet.
- RV64C: `MISSING`
  - No complete compressed ISA execution path validated.
- Zicsr: `PARTIAL`
  - Declared in current RISCOF setup; full CSR behavior and privilege correctness are not complete.
- Zifencei: `PARTIAL/UNKNOWN`
  - Declared in RISCOF ISA string; execution and architectural effects need dedicated validation.

### B. Privileged Architecture (RVA23S64-critical)
- M-mode trap/exception completeness: `PARTIAL`
- S-mode (sstatus/stvec/sepc/scause/stval/sip/sie/satp, delegation): `MISSING`
- SRET/MRET architectural behavior: `PARTIAL/UNKNOWN`
- Interrupt architecture (timer/software/external, delegation, prioritization): `MISSING`
- Virtual memory (Sv39 minimum): `MISSING`
- TLB + page-table walk + page-fault semantics: `MISSING`

### C. Verification/Automation
- RISCOF integration: `IMPLEMENTED`
- riscv-formal integration: `IMPLEMENTED` (selected checks active)
- Tenstorrent riscv-arch-test integration: `PRESENT IN TREE, NOT IN REGRESSION FLOW`
- riscv-dv random stream integration: `MISSING`
- Continuous nightly/per-PR regression with trend reporting: `MISSING`

### D. Performance Infrastructure
- Existing counters: `PARTIAL` (basic stall/branch counters present)
- Stable performance measurement harness and baseline DB: `MISSING`
- Benchmark suite integration (CoreMark/Embench/Dhrystone + memory microbenches): `MISSING`
- Automated perf regression gate (CPI + event deltas): `MISSING`

## Delivery Plan

## Phase 0: Stabilize Correctness Baseline
Goal: eliminate active correctness instability before feature expansion.

Tasks:
- Fix remaining misaligned branch privilege failures.
- Run full RV64I RISCOF and keep a reproducible passing baseline.
- Keep strict formal checks green (`pc_fwd`, `pc_bwd`, `causal`, `unique`, key insn checks).

Exit criteria:
- Full RV64I (current enabled subset) passes in CI.
- Formal baseline suite passes on every merge.

## Phase 1: RVA23S64 Compliance Matrix (Authoritative)
Goal: convert this first-pass matrix into an actionable, spec-traceable checklist.

Tasks:
- Break down RVA23S64 requirements into per-feature checklist items.
- Add columns: `spec reference`, `RTL owner`, `tests`, `status`, `last run`.
- Track as `implemented/partial/missing/tested` with links to evidence.

Exit criteria:
- Every profile requirement has an explicit owner and test path.

## Phase 2: Privileged + VM Bring-up (S-mode Core)
Goal: satisfy the S-mode architectural foundation required by RVA23S64.

Tasks:
- Complete CSR map required for M/S modes.
- Implement robust trap/return/delegation logic.
- Implement `satp`, `sfence.vma`, Sv39 translation, page faults, and access checks.
- Add deterministic directed tests for trap entry/exit, delegation, and fault classes.

Exit criteria:
- S-mode directed tests pass.
- VM translation/fault directed tests pass.

## Phase 3: Extension Completion for Profile
Goal: close remaining ISA/profile gaps.

Tasks:
- Complete and validate `M`, `A`, `F`, `D`, and `C` as required by final RVA23S64 target definition.
- Verify memory-model-sensitive atomic behavior (`LR/SC`, AMOs, ordering expectations).
- Add extension-specific formal checks where applicable.

Exit criteria:
- Extension-specific architectural tests pass.
- No open profile-required extension marked `missing`.

## Phase 4: Verification Expansion and Automation
Goal: prevent regressions and increase bug-finding depth.

Tasks:
- Integrate Tenstorrent riscv-arch-test into automated regression flow.
  - Use `verif/tenstorrent-riscv-arch-tests` as an additional architecture/compliance suite.
  - Build per-suite pass/fail dashboards.
- Integrate `riscv-dv` for randomized instruction stream testing.
- Add nightly long-run regressions with artifact retention (waveforms/signatures on failures).
- Add mandatory PR gates:
  - smoke RISCOF
  - key formal suite
  - directed privilege/VM tests

Exit criteria:
- Per-PR fast gate + nightly deep gate operational.
- Automated report includes trend of failing tests by category.

## Phase 5: Performance Counters and Measurement Stack
Goal: make performance work data-driven.

Tasks:
- Expand counters with clear event taxonomy:
  - retired instructions/cycles
  - frontend stalls
  - branch redirect/flush penalty
  - hazard stalls by cause
  - load/store stalls
  - memory latency buckets
- Standardize counter dump interface and run metadata.
- Create automated performance measurement harness:
  - fixed seeds
  - warmup policy
  - multi-run averaging
  - reproducible report format (CSV/JSON + markdown summary)

Exit criteria:
- Any perf claim is backed by reproducible runs and checked-in reports.

## Phase 6: Benchmark Integration
Goal: optimize against meaningful workloads, not just micro-tests.

Tasks:
- Integrate benchmark suites:
  - CoreMark
  - Embench (recommended)
  - Dhrystone
  - targeted memory microbenches (copy/stream/pointer-chase)
- Add benchmark automation scripts for compile/run/collect/report.
- Add baseline snapshots and threshold-based alerts for regression.

Exit criteria:
- Benchmark suite runs automatically and tracks trend over commits.

## Phase 7: In-order Performance Optimization Program
Goal: maximize perf/efficiency within in-order architecture constraints.

Priority order:
1. Frontend efficiency and branch penalty reduction (predictor/BTB/RAS, redirect latency)
2. Hazard/bypass improvements and load-use penalty cuts
3. Memory subsystem improvements (store buffering, miss handling, cache behavior)
4. Timing closure and power-aware gating (Fmax + perf/W)

Exit criteria:
- CPI breakdown improves on selected benchmark mix.
- Perf/W trend is positive over baseline.

## Tooling Additions (Requested)
These are explicitly in scope and planned:
- Tenstorrent riscv-arch-test integration
- Expanded performance counters + measurement integration
- Benchmark integration and automation
- Additional automated verification/performance tools:
  - riscv-dv random testing
  - extended riscv-formal checks
  - nightly regression orchestration and artifact retention
  - automated performance regression checks

## Immediate Next Actions (Recommended Order)
1. Finish `misalign-*` privilege branch failures.
2. Freeze a full green RV64I baseline.
3. Convert this document into a checked checklist with issue IDs per requirement.
4. Wire Tenstorrent suite into CI/nightly smoke flow.
5. Land perf-counter schema v1 and measurement harness skeleton.
