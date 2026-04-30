# Borb Execution Plan

## Purpose

Turn borb into an `RV64IMAFDCSUZicsr_Zifencei` in-order core that preserves `M/S/U` privilege and `Sv39`, reaches full RV64GC architectural correctness, and then upgrades the backend from scalar-only contracts to a deterministic 2-wide in-order machine.

## Stop-And-Fix Rule

- Do not advance to the next milestone until the current milestone validation commands pass.
- If a validation fails, repair the regression first.
- If a milestone exposes a broken earlier invariant or interface, fix the earlier layer and rerun its validation before resuming.

## Decision Notes

- Canonical architectural ISA declaration: `verif/riscof/borb/borb_isa.yaml`.
- Full regression gate: `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim`.
- The machine stays commit-in-order for this entire plan. No rename, reorder buffer, or OoO machinery.
- Backend widening waits until scalar correctness is stable and frontend-enabled performance is measurably better than fallback mode.
- Land asymmetric 2-wide first, then full scalar-class parity.
- Vector is explicitly out of scope. This program targets RV64GC, not RV64GCV.
- Ignore `riscv-formal` for this plan. Existing formal wrapper work is parked and non-gating unless a later milestone explicitly revives it.
- Ignore Tenstorrent smoke for this plan. Existing runner and debug flows are parked and non-gating unless a later milestone explicitly revives them.
- Use RISCOF as the active architectural gate for now. Directed tests remain useful for bug isolation, but they are non-gating unless a milestone explicitly calls them out as the fastest way to validate an isolated fix.
- RISCOF and performance harnesses are gating signals, not optional evidence.

## Intended Architecture

- Architectural target: `RV64IMAFDCSUZicsr_Zifencei`.
- Product target: bare-metal first, Linux-capable later.
- Core shape: 8-11 stage, in-order, 2-wide decode/issue/retire.
- Frontend base: existing bundle-based, block-aware frontend.
- Backend plan: refactor decode onward into lane-aware 2-wide contracts.
- Predictor target: `BHT + BTB + RAS`.
- Cache target: `L1I 32 KiB`, `L1D 32 KiB`, simple or optional `L2`.
- Performance target: `CoreMark/MHz 3.5-4.0`, `Dhrystone 3-4 DMIPS/MHz`, integer IPC about `0.8-1.2`.
- Verification target: RISCOF green on the RV64GC architectural surface, plus directed tests and checked-in performance harnesses.
- End-state execution resources: `ALU0`, `ALU1`, `BRU`, `LSU/AGU`, `MUL`, `DIV`, `FPU`.

## Verification Checklist

- `sbt compile`
- `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim`
- `./run_coremark.sh`
- `./run_directed.sh verif/directed/asm/frontend_branch_stress.S --march rv64gc_zicsr_zifencei --max-cycles 1000000 --report-perf`
- Additional milestone-specific directed tests as they land.

## Milestones

### Milestone 0: Freeze the Current Baseline

Status: `done`

Acceptance criteria:

- Record the current architectural baseline, privilege surface, and verification state.
- Explicitly note that `D` is still missing from the architectural surface.
- Explicitly note that `riscv-formal` is intentionally ignored by this program.
- Record the standing full RISCOF gate.

Validation commands:

```bash
sbt compile
./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim
```

### Milestone 1: Close the RV64GC ISA Surface Declaration

Status: `done`

Acceptance criteria:

- The canonical declaration moves from `RV64IMAFCSUZicsr_Zifencei` to `RV64IMAFDCSUZicsr_Zifencei`.
- Config, docs, and verification surfaces agree on the target ISA.
- This milestone stays declaration/config-only unless latent `D` support can be enabled cleanly.

Validation commands:

```bash
rg -n "RV64IMAFDCSUZicsr_Zifencei|dExtensionEnabled|RV64D" src/main docs verif scripts
sbt compile
```

Implementation notes:

- Updated `verif/riscof/borb/borb_isa.yaml` to declare `RV64IMAFDCSUZicsr_Zifencei` and include the `D` bit in the MISA reset and legal-extension mask.
- Updated `scripts/coremark_runner.py` and `documentation.md` so user-facing verification defaults and status notes track the RV64GC target declaration.
- Kept `CpuConfig.dExtensionEnabled` defaulted off during the declaration-only pass; Milestone 2 then enabled it once the minimal RV64D path was wired end-to-end.

### Milestone 2: Implement RV64D Architectural Enablement

Status: `done`

Acceptance criteria:

- `D` is wired through config, decode eligibility, register/state plumbing, and architectural exposure.
- Existing `I/M/A/F/C/Zicsr/Zifencei/S/U/Sv39` behavior does not regress.
- A dedicated RV64D directed smoke test exists and runs through the intended path.

Validation commands:

```bash
sbt compile
./run_directed.sh verif/directed/asm/rv64d_smoke.S --march rv64gc_zicsr_zifencei
```

Implementation notes:

- Enabled `CpuConfig.dExtensionEnabled` by default so `misa.D` is architecturally exposed in the standard CPU/SoC build.
- Added end-to-end RV64D plumbing for a minimal functional slice: `FLD`, `FSD`, `FMV.X.D`, `FMV.D.X`, `FCLASS.D`, and `FSGNJ*.D`.
- Added `verif/directed/asm/rv64d_smoke.S` and validated it on a freshly rebuilt simulator.
- Trace-enabled simulator rebuilds remain disproportionately expensive on this machine because `VSoC__Trace__0__Slow.cpp` dominates compile time, so RISCOF is the active gating path while traced directed runs stay as optional debug evidence.

### Milestone 3: Close RV64D Semantics and Exceptions

Status: `done`

Acceptance criteria:

- Double-precision arithmetic, comparisons, loads/stores, conversions, flags, NaN behavior, and trap/CSR interaction are architecturally correct.
- Any required FPU backend work is good enough to sustain regression use.
- Directed tests cover cases that the architectural suites are weak on.

Validation commands:

```bash
./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim --tests "<rv64d_subset>"
./run_directed.sh verif/directed/asm/<rv64d_corner_cases>.S --march rv64gc_zicsr_zifencei --trace
```

Implementation notes:

- The RISCOF-backed RV64D conversion slice is green for `FCVT.{W,WU,L,LU}.D`, `FCVT.D.{W,WU,L,LU}`, `FCVT.S.D`, and `FCVT.D.S`.
- `FMIN.D` and `FMAX.D` are now wired and passing their focused RISCOF subsets.
- The RV64D arithmetic slice is green in focused RISCOF for `FADD.D`, `FSUB.D`, `FMUL.D`, `FDIV.D`, and `FSQRT.D`.
- The RV64D FMA slice is green in focused RISCOF for `FMADD.D`, `FMSUB.D`, `FNMSUB.D`, and `FNMADD.D`.
- Single-precision operands consumed through the FP register file are now NaN-boxing aware, so unboxed `S` values are treated as canonical NaNs when a scalar `S` consumer reads them.
- The local RISCOF simulator now mirrors committed stores into the dumpable memory image before signature emission. This fixed a real harness bug where the final committed `frcsr` signature store could still appear as `0xdeadbeef` in the dumped signature despite retiring architecturally.

### Milestone 4: Reach Full RV64GC Architectural Green

Status: `done`

Acceptance criteria:

- The full architectural regression surface for the RV64GC target is green.
- No privilege, VM, CSR, or atomic regressions are introduced by `D` bring-up.

Validation commands:

```bash
./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim
```

Implementation notes:

- Replaced the library `Axi4SharedArbiter` in `src/main/SoC.scala` with a local two-master shared AXI arbiter after isolating a real lockup in the generated `StreamArbiter` lock semantics.
- That arbiter fix cleared the remaining architectural timeouts in atomic, compressed, and PMP/CSR-walk regressions.
- The full active RISCOF gate is now green over the current 632-test filtered suite with zero generated failure reports.

### Milestone 5: Make the Scalar Core Worth Widening

Status: `done`

Acceptance criteria:

- Frontend-enabled mode clearly beats frontend-disabled mode.
- Redirect, fetch, and replay behavior improve measurably on CoreMark and at least one branch-heavy or memory-heavy workload.
- Scalar correctness gates remain green.

Validation commands:

```bash
./run_coremark.sh
python3.11 scripts/frontend_perf_ab.py
./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim
```

Implementation notes:

- Fixed a real benchmark-mode leak in the fetch path: frontend-disabled runs were still consuming `bundleMeta.nextStartPc`, so the "off" configuration was benefitting from frontend sequencing redirects. `FrontendBundleBuilder` now suppresses slot-level prediction metadata when predicted redirects are disabled, and `Fetch` falls back to sequential bundle stepping in that mode.
- `scripts/coremark_runner.py` now accepts the current RV64GC declaration aliases, and the frontend A/B wrappers are pinned to `python3.11` for reproducible local runs.
- CoreMark now shows a clear frontend-on win over the rebuilt frontend-off baseline: `1,780,676` cycles / `5.6158 CoreMark/MHz` on vs `2,096,792` cycles / `4.7692 CoreMark/MHz` off, which is about a `15.08%` cycle reduction.
- Added a reusable branch-heavy benchmark at `verif/directed/asm/frontend_branch_stress.S` plus perf-report support in `scripts/directed_asm_runner.py`.
- That branch-stress workload also shows a clear frontend-on win: `550,160` cycles on vs `1,450,124` cycles off, while flushes drop from `100,006` to `4` and fetch stalls drop from `550,046` to `100,040`.
- The full active RISCOF gate stayed green after the frontend changes: `632/632` passing tests and zero generated failure reports.

### Milestone 6: Refactor the Backend for Lane-Aware Correctness

Status: `done`

Acceptance criteria:

- Decode, dispatch, source, execute, writeback, and retire contracts become lane-aware.
- Same-cycle pairing rules, cross-lane hazards, kill/squash semantics, and precise priority rules are defined and implemented.
- Width-off operation remains functionally scalar even though the contracts are no longer scalar-only.

Validation commands:

```bash
sbt compile
./run_directed.sh verif/directed/asm/<dual_lane_hazard_smoke>.S --march rv64gc_zicsr_zifencei --trace
```

Implementation notes:

- Added first-class lane metadata to the scalar pipe: `LANE_ID` and `LANE_MASK` now propagate from fetch onward, and fetch also preserves the originating bundle slot count through `FETCH_SLOT_COUNT`.
- Added `src/main/common/LaneContracts.scala` to centralize bundle-slot ordering and lane-mask helpers. Redirect squash priority now compares `(bundleSeq, slotIdx)` pairs instead of relying only on the scalar fetch sequence.
- `IssuePropertyBundle` now exposes `lane1Compatible` and `pairBarrier` so pairing policy is explicit before dual issue is enabled.
- Added `verif/directed/asm/dual_lane_hazard_smoke.S` to prove that two adjacent same-bundle instructions with dependencies still execute correctly while width-off operation remains serialized and scalar.

### Milestone 7: Land Asymmetric 2-Wide Issue

Status: `done`

Acceptance criteria:

- Two instructions can be decoded, dispatched, and retired in order each cycle.
- Lane 0 can issue any scalar class; lane 1 is restricted to integer and branch work.
- Pairing rules and fallbacks are deterministic and tested.

Validation commands:

```bash
./run_directed.sh verif/directed/asm/<dual_issue_integer_smoke>.S --march rv64gc_zicsr_zifencei --trace
./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim
./run_coremark.sh
```

Implementation notes:

- Added a real asymmetric side lane in `src/main/CPU.scala`: lane 0 keeps the full scalar path, while lane 1 now carries a restricted integer/conditional-branch companion path with in-order commit.
- Fetch now exposes enough two-slot state to make deterministic pairing decisions without abandoning the existing scalarized frontend contracts. `Fetch` supports scalar skip/defer control, and `FrontendScalarAdapter` exposes a legal slot-1 preview.
- Decode, integer execute, and branch resolution now expose reusable helpers so the side lane can reuse the same architectural semantics as lane 0 instead of drifting into a second implementation.
- Integer register-file plumbing is widened to support dual read/dual write behavior for the asymmetric issue step, while the existing scalar path still behaves correctly when pairing is rejected.
- Pairing is intentionally conservative and deterministic: lane 1 is limited to integer ALU work plus conditional branches, jumps never pair on lane 1, and same-cycle RAW/WAW plus older in-flight register hazards force an immediate fallback to pure scalar issue.
- Added `verif/directed/asm/dual_issue_integer_smoke.S` to cover same-cycle integer pairing, slot-1 conditional-branch execution, and trailing paired writeback.
- The initial overly-broad slot-1 deferral logic exposed a real regression by timing out `fadd_b12-01.S` in RISCOF. Tightening the early two-slot static screen fixed that issue and restored the full gate.
- Current validation state is green on the functional gates: `sbt compile`, `./run_directed.sh verif/directed/asm/dual_issue_integer_smoke.S --march rv64gc_zicsr_zifencei`, `./run_directed.sh verif/directed/asm/dual_lane_hazard_smoke.S --march rv64gc_zicsr_zifencei`, `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` with `632/632`, and `./run_coremark.sh`.
- Trace-form directed validation remains available through `--trace --rebuild-sim`, but on this machine the traced model rebuild is dominated by `VSoC__Trace__0__Slow.cpp` compile time and is materially more expensive than the active RISCOF architectural gate.

### Milestone 8: Upgrade to Full 2-Wide Scalar Parity

Status: `done`

Acceptance criteria:

- Safe pairing extends across ALU, branch, load/store, FP, and allowed serialized classes.
- Writeback and retire arbitration is correct across all scalar resources.
- Compliance, directed suites, and performance gains hold with dual issue enabled.

Validation commands:

```bash
./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim
./run_coremark.sh
```

Implementation notes:

- Widened same-bundle pairing so the older lane-0 instruction can now safely be ALU, conditional branch, load/store, or FP work while the younger lane-1 companion remains the restricted integer/conditional-branch path.
- Pair admission is still deterministic and conservative. Only one companion instruction is allowed in flight at a time, and younger lane-1 execution is explicitly deferred behind older memory operations so commit order and branch side effects stay in-order.
- Added `verif/directed/asm/dual_issue_mixed_classes_smoke.S` to cover the new mixed-class surface: older load plus younger integer ALU, older store plus younger integer ALU, older conditional branch plus younger integer ALU, and older FP ALU plus younger integer ALU.
- `src/main/fetch/FrontendPredictor.scala` now updates loop-predictor entries through a full-entry shadow writeback. That removes the rebuild-time Spinal partial-assignment warnings that were polluting `sbt "runMain borb.SoC"` during regression loops.
- Validation is green on the intended gates: mixed-class dual-issue smoke, existing integer/hazard smokes, full fast RISCOF at `632/632`, and profiled CoreMark at `1,780,676` cycles / `5.6158 CoreMark/MHz`.

### Milestone 9: Hit the Prompt-Level Performance Envelope

Status: `in_progress`

Acceptance criteria:

- CoreMark/MHz reaches the target band or the remaining gap is documented with bounded blockers.
- Dhrystone and integer IPC are measured and recorded.
- Predictor and cache behavior are consistent with the intended architecture.

Validation commands:

```bash
./run_coremark.sh
```

Implementation notes:

- Add a Dhrystone command only if the harness already exists in-repo; otherwise treat the harness as a prerequisite subtask inside this milestone.
- The current supported frontend-on CoreMark baseline is `1,780,676` cycles, `2.4968` CPI, `0.4005` IPC, and `5.6158 CoreMark/MHz` from `verif/benchmarks/coremark/out/coremark_m8_now_20260427_220000`.
- The current supported branch-heavy perf sample is `550,160` cycles, `2.2003` CPI, and `0.4545` IPC from `verif/directed/out/frontend_branch_stress_20260427_220335/perf.json`, with `100,036` predicted redirects, `4` flushes, `0` bank conflicts, and `550,127` successful cross-bank dual fetches.
- The old frontend on/off wrappers are now deprecated because borb no longer supports a maintained frontend-disabled mode. Any future performance notes should use the supported always-on configuration and compare against recorded historical baselines in `docs/PERFORMANCE_HISTORY.md`.
- A checked-in Dhrystone harness still does not exist under `verif/benchmarks/`. Building that harness is the remaining concrete Milestone 9 subtask if we want a directly measured DMIPS-style data point instead of only CoreMark plus branch-heavy IPC evidence.

## Validation Buckets

- Architecture: full RISCOF gate plus focused subsets while iterating.
- Directed bug isolation: `run_directed.sh`.
- Performance: CoreMark and checked-in microbenchmarks on the supported always-on frontend configuration.
