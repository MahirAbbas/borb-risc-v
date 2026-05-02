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

- Add lane ids `0` and `1`.
- Add minimal helpers around SpinalHDL keyed payload access.
- Define which payloads are lane-local versus global.
- Do not change behavior yet.

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
- `sbt compile` passes.
- Existing behavior is unchanged.
- New helpers are used by at least one non-critical payload path to prove the pattern.

### M4: Convert Decode And Issue To Lane-Keyed Structure

- Convert lane-local decode and issue metadata to keyed payloads.
- Replace manual lane-1 decode preview/capture fields where possible.
- Keep lane-1 policy unchanged:
  - integer ALU and conditional branch only,
  - no memory/AMO/FP/CSR/vector/serializing/jump pairing.
- Keep `BackendPipe.select(..., preferAlu1 = laneId == 1)` behavior.

Acceptance:
- `sbt compile` passes.
- Frozen ACT4 subset passes.
- Code no longer duplicates decode/issue field capture for lane 1 except where required for fetch preview.

### M5: Centralize Lane-Aware Hazard And Pairing Logic

- Replace special-case lane-1 RAW/WAW/busy checks with a shared two-lane issue matrix.
- Represent pair barriers, lane compatibility, and older-in-flight hazards uniformly.
- Preserve scalar fallback behavior exactly.

Acceptance:
- Same-cycle RAW rejection still works.
- Same-cycle WAW rejection still works.
- Older busy register rejection still works.
- Lane-1 unsupported classes still scalarize.
- Frozen ACT4 subset passes.
- CoreMark still passes.

### M6: Convert Source Read And Bypass To Lane-Keyed Structure

- Replace hardcoded lane-1 regfile read plumbing with lane-indexed source read mapping.
- Preserve current physical port allocation.
- Keep integer bypass semantics unchanged.
- Do not add FP/vector lane-1 reads.

Acceptance:
- `sbt compile` passes.
- Frozen ACT4 subset passes.
- Existing integer pairing smoke coverage is represented through ACT4 subset coverage or debug-only directed repro.

### M7: Convert Execute, Writeback, And Retire

- Convert lane-local ALU/branch results to keyed payloads.
- Build lane retire packets from keyed payloads.
- Preserve lane 0 full backend ownership.
- Preserve lane 1 restricted ALU/conditional-branch execution.
- Preserve precise trap and redirect squash behavior.

Acceptance:
- Manual `lane1s4/lane1s5/lane1s6/lane1s7` style state is removed as primary state.
- Manual lane-1 header copying is removed.
- Retire packet vector remains ordered and two-wide.
- Frozen ACT4 subset passes.
- CoreMark passes.

### M8: Delete Obsolete Side-Path Code And Reduce LOC

- Remove compatibility shims introduced during conversion.
- Delete unused lane-1 bundles, copy helpers, duplicated result plumbing, and stale comments.
- Keep the implementation smaller and more regular than before the refactor.
- Prefer keyed payload access over custom side bundles whenever equivalent.

Acceptance:
- Net line count in touched lane/backend/dispatch code decreases.
- No remaining primary execution path depends on bespoke lane-1 stage registers.
- `rg "lane1s[0-9]|copyLane1Header"` returns no active implementation hits.
- `sbt compile` passes.

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
- [ ] `sbt compile`
- [ ] `sbt "runMain borb.SoC"`
- [x] Frozen ACT4 subset via `./run_act4.sh --profile rva23s64-full --extensions I --verilate-jobs 10 --sim-jobs 10 --sim-threads 1`
- [x] Focused ACT4 subset demonstrating run-scoped selection.
- [ ] `./run_coremark.sh --profile`
- [ ] Full ACT4 telemetry via `./run_act4.sh --profile rva23s64-full --verilate-jobs 10 --sim-jobs 10 --sim-threads 1`
- [ ] Code reduction audit for lane/backend/dispatch paths.

## Implementation Notes

- 2026-05-02: Started from M1 because the active `plan.md` defines M1-M9 and the user instruction explicitly says to start from Milestone 1. The stale `agent/Implement.md` line about Milestone 10 is ignored for this run.
- 2026-05-02: M1 policy landed in `documentation.md`: frozen baseline, ACT4 subset cleanup gates, full RVA23S64 telemetry, and directed debug-only usage are now explicitly separated.
- 2026-05-02: M2 uses a deterministic repo-local ACT4 workdir scope when `--extensions` or `--exclude` is present. The scope key includes profile, extension filter, and exclusion filter, so focused runs build and run from their own `verif/act4/work/run-scopes/<hash>` tree while full telemetry keeps the historical `verif/act4/work` tree.
- 2026-05-02: The first scoped `I` ACT4 run built 204 selected ELFs in the scoped workdir, then exposed stale Verilator dependency files in `verif/borb-sim/build/obj_dir` that still referenced the old RISCOF simulator tree. `verif/borb-sim/sim/Makefile` now deletes generated `.d` files before rerunning Verilator so the simulator rebuild uses current local paths.
- 2026-05-02: M2 validation passed: `./run_act4.sh --profile rva23s64-full --extensions I --skip-gen --verilate-jobs 10 --sim-jobs 10 --sim-threads 1` ran from `verif/act4/work/run-scopes/2485ff50a4ca/.../elfs` and passed `51/51`; `./run_act4.sh --profile rva23s64-full --extensions I --skip-gen --skip-build --run-only --sim-jobs 10 --sim-threads 1` reran the same scoped selection and passed `51/51`.
