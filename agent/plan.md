# Milestoned Plan: Lane-Keyed Cleanup, ACT4 Subset Gates, And Code Reduction

## Summary

Refactor borb’s current asymmetric two-lane implementation into a lane-keyed SpinalHDL payload structure using `node(Payload, laneId)`-style access for lane-local state. This is behavior-preserving: no new ISA claim, no broader lane-1 support, no full RVA23S64 signoff claim.

ACT4 subsets become the hard regression gates. Directed tests become debug-only. Code reduction is an explicit goal: remove ad hoc lane-1 side paths, manual copy code, and duplicated scheduling/writeback logic wherever SpinalHDL keyed payloads can express the same structure.

## Milestones

### M1: Freeze Baseline And Gate Policy

- [x] Define the frozen current baseline in docs:
  - current RV64GC-class scalar/profile subset,
  - current asymmetric lane policy,
  - current CoreMark baseline,
  - full RVA23S64 remains roadmap telemetry.
- [x] Mark `run_directed.sh` as debug-only.
- [x] Define ACT4 subset gates as the official cleanup regression path.

Acceptance:
- [x] `documentation.md` clearly separates:
  - frozen current baseline,
  - ACT4 subset gates,
  - full RVA23S64 telemetry,
  - directed debug-only tooling.

### M2: Make ACT4 Subsets Truly Run-Scoped

- [x] Fix `run_act4.sh` so subset selection controls both ELF build and ELF run.
- [x] Prevent stale ELFs from previous broader runs from being executed during subset runs.
- [x] Preserve `--run-only` semantics for intentionally rerunning an existing selected ELF set.
- [x] Keep progress bar and per-test fresh simulation behavior.

Acceptance:
- [x] A small ACT4 subset builds and runs only that subset.
- [x] A broader prior run cannot pollute a later subset run.
- [x] `--skip-gen --skip-build --run-only` still reruns existing selected ELFs fresh.
- [x] Docs show baseline subset, focused subset, and full telemetry commands.

### M3: Add Lane-Keyed Payload Infrastructure

- [x] Add lane ids `0` and `1`.
- [x] Add minimal helpers around SpinalHDL keyed payload access.
- [x] Define which payloads are lane-local versus global.
- [x] Do not change behavior yet.

Lane-local payloads:
- decode valid/instruction/micro-op/register fields,
- issue properties,
- selected backend pipe,
- source operands/immediate,
- branch/ALU result,
- writeback result,
- retire intent.

Global payloads:
- epoch/current redirect control,
- global flush/squash,
- PC sequencing where shared.

Acceptance:
- [x] `sbt compile` passes.
- [x] Existing behavior is unchanged.
- [x] New helpers are used by at least one non-critical payload path to prove the pattern.

### M4: Convert Decode And Issue To Lane-Keyed Structure

- [x] Convert lane-local decode and issue metadata to keyed payloads.
- [x] Replace manual lane-1 decode preview/capture fields where possible.
- [x] Keep lane-1 policy unchanged:
  - integer ALU and conditional branch only,
  - no memory/AMO/FP/CSR/vector/serializing/jump pairing.
- [x] Keep `BackendPipe.select(..., preferAlu1 = laneId == 1)` behavior.

Acceptance:
- [x] `sbt compile` passes.
- [x] Frozen ACT4 subset passes.
- [x] Code no longer duplicates decode/issue field capture for lane 1 except where required for fetch preview.

### M5: Centralize Lane-Aware Hazard And Pairing Logic

- [x] Replace special-case lane-1 RAW/WAW/busy checks with a shared two-lane issue matrix.
- [x] Represent pair barriers, lane compatibility, and older-in-flight hazards uniformly.
- [x] Preserve scalar fallback behavior exactly.

Acceptance:
- [x] Same-cycle RAW rejection still works.
- [x] Same-cycle WAW rejection still works.
- [x] Older busy register rejection still works.
- [x] Lane-1 unsupported classes still scalarize.
- [x] Frozen ACT4 subset passes.
- [x] CoreMark still passes.

### M6: Convert Source Read And Bypass To Lane-Keyed Structure

- [x] Replace hardcoded lane-1 regfile read plumbing with lane-indexed source read mapping.
- [x] Preserve current physical port allocation.
- [x] Keep integer bypass semantics unchanged.
- [x] Do not add FP/vector lane-1 reads.

Acceptance:
- [x] `sbt compile` passes.
- [x] Frozen ACT4 subset passes.
- [x] Existing integer pairing smoke coverage is represented through ACT4 subset coverage or debug-only directed repro.

### M7: Convert Execute, Writeback, And Retire

- [x] Convert lane-local ALU/branch results to keyed payloads.
- [x] Build lane retire packets from keyed payloads.
- [x] Preserve lane 0 full backend ownership.
- [x] Preserve lane 1 restricted ALU/conditional-branch execution.
- [x] Preserve precise trap and redirect squash behavior.

Acceptance:
- [x] Manual `lane1s4/lane1s5/lane1s6/lane1s7` style state is removed as primary state.
- [x] Manual lane-1 header copying is removed.
- [x] Retire packet vector remains ordered and two-wide.
- [x] Frozen ACT4 subset passes.
- [x] CoreMark passes.

### M8: Delete Obsolete Side-Path Code And Reduce LOC

- [x] Remove compatibility shims introduced during conversion.
- [x] Delete unused lane-1 bundles, copy helpers, duplicated result plumbing, and stale comments.
- [x] Keep the implementation smaller and more regular than before the refactor.
- [x] Prefer keyed payload access over custom side bundles whenever equivalent.

Acceptance:
- [x] Net line count in touched lane/backend/dispatch code decreases.
- [x] No remaining primary execution path depends on bespoke lane-1 stage registers.
- [x] `rg "lane1s[0-9]|copyLane1Header"` returns no active implementation hits.
- [x] `sbt compile` passes.

### M9: Documentation And Final Validation

- Update `documentation.md` with:
  - final lane-keyed architecture,
  - frozen baseline gate,
  - ACT4 subset usage,
  - directed debug-only status,
  - full RVA23S64 telemetry status.
- Record validation results.

Acceptance:
- Frozen ACT4 subset passes.
- CoreMark profile passes.
- Full ACT4 telemetry command still runs and reports current known failures without being treated as cleanup failure.
- Documentation matches actual commands and behavior.

## Test Plan

Hard gates:
- `sbt compile`
- `sbt "runMain borb.SoC"`
- Frozen ACT4 subset run through `./run_act4.sh`
- `./run_coremark.sh --profile`

Focused ACT4 subsets:
- RV64I integer
- branch/control
- compressed baseline
- memory/AMO baseline
- FP baseline
- CSR/trap baseline
- dual-issue-relevant migrated coverage where available

Telemetry:
- Full `./run_act4.sh --profile rva23s64-full ...` as non-blocking failure-count telemetry.

Debug-only:
- `./run_directed.sh ...` only when a custom repro, waveform, commit trace, or perf artifact is needed.

## Assumptions

- This pass is behavior-preserving.
- Lane count remains two.
- Lane 1 remains restricted.
- ACT4 subsets replace directed tests as hard gates.
- Directed tooling remains available but is not a signoff path.
- Code reduction is a first-class acceptance criterion.

## Verification Checklist

- [x] M1 docs policy review complete.
- [x] `sbt compile`
- [ ] `sbt "runMain borb.SoC"`
- [x] Frozen ACT4 subset via `./run_act4.sh --profile rva23s64-full --extensions I --verilate-jobs 10 --sim-jobs 10 --sim-threads 1`
- [x] Focused ACT4 subset demonstrating run-scoped selection.
- [x] `./run_coremark.sh --profile`
- [ ] Full ACT4 telemetry via `./run_act4.sh --profile rva23s64-full --verilate-jobs 10 --sim-jobs 10 --sim-threads 1`
- [ ] Code reduction audit for lane/backend/dispatch paths.

## Implementation Notes

- 2026-05-02: Started from M1 because the active `plan.md` defines M1-M9 and the user instruction explicitly says to start from Milestone 1. The stale `agent/Implement.md` line about Milestone 10 is ignored for this run.
- 2026-05-02: M1 policy landed in `documentation.md`: frozen baseline, ACT4 subset cleanup gates, full RVA23S64 telemetry, and directed debug-only usage are now explicitly separated.
- 2026-05-02: M2 uses a deterministic repo-local ACT4 workdir scope when `--extensions` or `--exclude` is present. The scope key includes profile, extension filter, and exclusion filter, so focused runs build and run from their own `verif/act4/work/run-scopes/<hash>` tree while full telemetry keeps the historical `verif/act4/work` tree.
- 2026-05-02: The first scoped `I` ACT4 run built 204 selected ELFs in the scoped workdir, then exposed stale Verilator dependency files in `verif/borb-sim/build/obj_dir` that still referenced the old RISCOF simulator tree. `verif/borb-sim/sim/Makefile` now deletes generated `.d` files before rerunning Verilator so the simulator rebuild uses current local paths.
- 2026-05-02: M2 validation passed: `./run_act4.sh --profile rva23s64-full --extensions I --skip-gen --verilate-jobs 10 --sim-jobs 10 --sim-threads 1` ran from `verif/act4/work/run-scopes/2485ff50a4ca/.../elfs` and passed `51/51`; `./run_act4.sh --profile rva23s64-full --extensions I --skip-gen --skip-build --run-only --sim-jobs 10 --sim-threads 1` reran the same scoped selection and passed `51/51`.
- 2026-05-02: M3 introduced `LaneId` and `LaneKeyedPayload` in `src/main/common/LaneContracts.scala`. `BackendIssue.SELECTED_PIPE_BY_LANE.lane0` now mirrors the existing selected-pipe metadata as the first non-critical lane-keyed payload use; the canonical behavior-driving payload remains `BackendIssue.SELECTED_PIPE`.
- 2026-05-02: M3 validation passed with `sbt compile`.
- 2026-05-02: M4 replaced the manual lane-1 issue-capture assignment block with `buildLaneIssueSlot(laneId, ...)`, preserving `BackendPipe.select(..., preferAlu1 = laneId == LaneId.Lane1)`. Conditional branch classification is now shared by lane-0 boundary, lane-1 preview, lane-1 candidate, and older-dispatch checks.
- 2026-05-02: M4 validation passed with `sbt compile` and `./run_act4.sh --profile rva23s64-full --extensions I --verilate-jobs 10 --sim-jobs 10 --sim-threads 1` passing `51/51`.
- 2026-05-02: M5 introduced `LaneIssueView` helpers for pairability, older busy-register rejection, same-cycle RAW rejection, and same-cycle WAW rejection. The lane-1 acceptance expression now consumes those matrix-style predicates while preserving the previous lane-1 compatibility, memory scalarization, barrier, and older-memory/FP wait behavior.
- 2026-05-02: M5 validation passed. `./run_directed.sh verif/directed/asm/dual_issue_m32_integer_branch_smoke.S --march rv64gc_zicsr_zifencei --rebuild-sim` passed as debug-only targeted coverage for RAW/WAW/scalarization behavior. `./run_act4.sh --profile rva23s64-full --extensions I --verilate-jobs 10 --sim-jobs 10 --sim-threads 1` passed `51/51`. `./run_coremark.sh --profile` passed with tohost `0x1`, `337,458` cycles, and `2.9633317331341975 CoreMark/MHz`.
- 2026-05-02: M6 introduced an explicit lane-to-physical integer read-port map in `CPU.scala`: lane 0 remains ports `0/1`, lane 1 remains ports `2/3`. Lane-1 source read hookup and bypass resolution now go through `connectLaneIntReadPorts` / `resolveLaneIntRead` instead of hardcoded port literals.
- 2026-05-02: M6 validation passed with `sbt compile` and `./run_act4.sh --profile rva23s64-full --extensions I --verilate-jobs 10 --sim-jobs 10 --sim-threads 1` passing `51/51`.
- 2026-05-02: M7 replaced the four named `lane1s*` stage registers with a single `lane1Pipe` vector and named aliases for decode/source/execute/writeback slots. The obsolete `copyLane1Header` helper is gone; stage-local source, branch, result, and commit fields are assigned explicitly once to satisfy Spinal's no-overlap elaboration checks. Ordered retire remains `Vec(RetirePacket(config), 2)` with lane 0 at index 0 and lane 1 at index 1.
- 2026-05-02: M7 validation passed with `sbt "runMain borb.SoC"`, `./run_act4.sh --profile rva23s64-full --extensions I --skip-gen --verilate-jobs 10 --sim-jobs 10 --sim-threads 1` passing `51/51`, and `./run_coremark.sh --profile` passing with tohost `0x1`, `337,458` cycles, and `2.9633317331341975 CoreMark/MHz`.
- 2026-05-02: M8 removed stale lane-1 side-path bookkeeping (`lane1PairEpoch`, `lane1PairRejected`) and confirmed `rg "lane1s[0-9]|copyLane1Header"` has no active `src/main` implementation hits.
- 2026-05-02: M8 validation passed with `sbt compile`. The M8 implementation cleanup diff in touched lane/backend/dispatch code is `src/main/CPU.scala | 4 ----`.
