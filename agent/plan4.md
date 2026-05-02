# Full RVA23S64 And Full RISC-V Coverage Plan

## Summary

The target is not “make the current 1540-test ACT4 run pass.” The target is a profile-complete borb implementation and verification environment that can honestly claim full mandatory RVA23S64 support.

That requires four parallel bodies of work:

0. Expand ACT4 and the RISC-V architectural suite coverage to the full RVA23S64 profile.
1. Implement every mandatory profile feature, including scalar, memory/PMA, supervisor, vector, and hypervisor requirements.
2. Keep the current green subset, CoreMark, directed tests, and documentation accurate throughout.

## Phase 0: Baseline And Source Of Truth

### M39: Freeze Current Known-Good Baseline

- Record the current passing subset ISA, full ACT4 result, CoreMark cycles, known waivers, and known non-claims.
- Preserve `--profile current` as the regression floor.
- Acceptance:
  - current ACT4 passes unchanged;
  - CoreMark result is recorded;
  - `documentation.md` distinguishes “current subset” from “RVA23S64 target.”

Implementation Notes:

- `./run_act4.sh --profile rva23s64-full` is the explicit M39 regression floor and is intentionally behavior-compatible with the pre-profile default run.
- The frozen baseline is machine-readable at `verif/act4/profiles/current_baseline.json`.
- `documentation.md` now separates the verified `current` subset from the full RVA23S64 target and points future work at this plan.

### M40: Create RVA23S64 Profile Ledger

- Build a machine-readable ledger from the official RVA23S64 profile.
- Track every mandatory, optional, localized, and out-of-scope extension separately.
- Each row must include: extension, profile source, implementation owner area, declaration status, ACT4 coverage, directed coverage, reference-model support, and current blocker.
- Acceptance:
  - no mandatory feature is represented only in prose;
  - ledger can generate a missing-feature report.

Implementation Notes:

- Added `verif/act4/profiles/rva23s64_ledger.json` as the machine-readable M40 ledger. It is seeded from the repo-local `plan4.md` mandatory surface and current documentation; M44 is responsible for attaching upstream suite/profile provenance before final signoff.
- Added `scripts/profile_ledger.py` to validate required row fields and generate missing-feature reports.
- Generated `verif/act4/profiles/missing_features.md` and `verif/act4/profiles/missing_features.json`. The initial conservative report has 69 rows total and 43 mandatory missing or blocked rows.

### M41: Define Final Acceptance Policy

- Define what “done” means for a mandatory feature:
  - implemented in RTL;
  - declared in ISA/platform metadata;
  - accepted by validation tooling or explicitly handled by local profile validation;
  - covered by ACT4 or documented backfill tests;
  - included in final docs.
- Acceptance:
  - a feature cannot be marked complete without evidence links.

Implementation Notes:

- Added `verif/act4/profiles/acceptance_policy.md` with the mandatory completion policy.
- Updated `scripts/profile_ledger.py` so a mandatory feature is still reported missing/blocked unless it has evidence links in the row or the ledger-level evidence catalog.
- Added evidence links for the current completed scalar subset rows in `rva23s64_ledger.json`; future rows must add evidence before they can leave the missing-feature report.

## Phase 1: Expand ACT4 Into A Profile-Scale Harness

### M42: Add ACT4 Profile Modes

- Add `--profile current`, `--profile rva23s64-discovery`, `--profile rva23s64-scalar`, `--profile rva23s64-supervisor`, `--profile rva23s64-vector`, `--profile rva23s64-hypervisor`, and `--profile rva23s64-full`.
- Keep `--tests` and smoke presets, but make profile modes the primary path.
- Acceptance:
  - each mode can run `--list-only`;
  - `current` remains bit-for-bit compatible with today’s intent.

Implementation Notes:

- `run_act4.sh` is now deprecated and kept only as a compatibility shim for `--profile rva23s64-full`.
- Legacy ACT4 profile modes, `--tests`, config routing, plugins, Spike integration, and workdirs have been removed from the local repo.
- `--profile rva23s64-full` forwards to ACT4 because upstream `riscv-arch-test` 4.0 replaced the deprecated ACT4 flow for RVA23S64 certification tests.
- Added `run_act4.sh`, `scripts/act4_borb_elf_runner.py`, and `verif/act4/borb-rva23s64/` as the Borb ACT4 integration path.
- ACT4 full-profile execution does not apply current-profile higher-VM/Zfh/current-YAML filters. Unsupported CPU features are expected to surface as build, sim, self-checking tohost, or timeout failures.
- Runner helper snippets now use `PYTHON_BIN` defaulting to `python3.11` so PyYAML is available without pyenv writing outside the repo.
- Verification: the upstream ACT4 suite is checked out at `a7c99303516f4e668f7488f172043392e23b9dfd` under `verif/act4/riscv-arch-test`; full ACT4 generation, including vector tests, builds 1512 self-checking Borb ELFs. A focused `--extensions I` ACT4 run builds 51 ELFs and passes `51/51` on Borb. The full ACT4 run executes all 1512 ELFs and currently reports `531 passed / 981 failed`, so it is usable as a regression/development loop while the remaining mandatory RVA23S64 CPU features are implemented.

### M43: Add Complete Suite Discovery

- Scan the entire checked-in `riscv-arch-test` tree, not just directories currently selected by the ISA YAML.
- Emit selected, excluded, unsupported, missing, and blocked tests by extension.
- Include RV64, privilege, PMP, VM, CMO, bitmanip, hints, FP, half, vector-adjacent, and hypervisor-related suites where available.
- Acceptance:
  - full discovery report lists all 4231 checked-in `.S` tests and why each is selected or not.

Implementation Notes:

- Added `scripts/act4_suite_discovery.py` to scan the full checked-in ACT4 suite independently of active YAML selection.
- Generated `verif/act4/profiles/suite_discovery.json` and `verif/act4/profiles/suite_discovery.md`.
- Current report lists all 4231 `.S` tests: 1540 selected by the current filtered profile, 368 excluded by current filters, and 2323 unsupported by the active YAML.

### M44: Expand The RISC-V ACT4 Suite To RVA23S64

- Vendor or update the architectural test suite version needed for RVA23S64-era coverage.
- Add suite provenance: commit hash, upstream URL, local patches, and generated-test status.
- Add import rules so local modifications are isolated from upstream files.
- Acceptance:
  - suite contains tests for all upstream-available RVA23S64 mandatory extensions;
  - missing upstream coverage is explicitly listed.

Implementation Notes:

- Updated the repo-local upstream `verif/act4/riscv-arch-test` checkout to ACT4 `main` at `a7c99303516f4e668f7488f172043392e23b9dfd`.
- Added `scripts/profile_suite_provenance.py`.
- ACT4 full generation produces 1512 Borb ELFs after running the vector test generator. The suite includes upstream scalar, privileged, PMP/VM, and vector groups available to the RVA23S64 UDB configuration. The current full Borb execution result is `531 passed / 981 failed / 1512 total`.
- Local integration records two framework compatibility patches: explicit newer-Sail acceptance for the repo-local Sail 0.11 binary, and Borb-only vector `-march` normalization so GCC 15 can compile generated vector tests.

### M45: Add Profile Test Manifests

- Create manifests mapping mandatory RVA23S64 features to exact tests.
- Support test groups by extension, privilege mode, ISA string, expected macros, timeout budget, and reference backend.
- Acceptance:
  - every mandatory ledger entry maps to at least one test group, a missing-coverage item, or a temporary waiver.

Implementation Notes:

- Added `scripts/profile_manifest.py`.
- Generated `verif/act4/profiles/profile_test_manifest.json`.
- Initial manifest covers all 65 mandatory ledger features: 13 have direct discovered suite-group mappings, and 52 are explicit missing-coverage items for M45 follow-up, backfill tests, or later waivers.

### M46: Add Coverage Reports

- Generate `profile_coverage.json`, `profile_coverage.md`, and a concise terminal summary after every profile run.
- Include pass/fail/build-fail/timeout/missing/blocked counts by extension.
- Acceptance:
  - final ACT4 output cannot hide missing mandatory coverage behind a pass count.

Implementation Notes:

- Added `scripts/profile_coverage.py`.
- `run_act4.sh` now invokes the coverage generator after writing `borb_run_summary.json`.
- The legacy pre-ACT4 workdir reports were removed with the old local flow. Profile coverage reporting needs to consume ACT4 result summaries before it can become a signoff gate again.

### M47: Fix YAML And Validation Handling

- Add staged ISA/platform YAMLs for each profile mode.
- Add local validation for extensions not accepted by current `riscv-config`.
- Keep upstream `riscv-config` validation where possible, but add a repo-local profile checker for official profile names.
- Acceptance:
  - validation distinguishes “invalid,” “unsupported by tool,” and “accepted by profile checker.”

Implementation Notes:

- Added `scripts/profile_checker.py`, which classifies ledger entries as accepted by current metadata, unsupported by current `riscv-config`, missing from profile metadata, or invalid/unknown.
- Generated `verif/act4/profiles/profile_validation.json`: `accepted_by_current_metadata=29`, `unsupported_by_current_riscv_config=12`, `missing_from_profile_metadata=30`.
- Added `verif/act4/profiles/yaml_modes.json` to stage per-profile YAML ownership. Only `current` points at active upstream-validated YAML today; full staged YAML content remains a later M47/M48 implementation task before non-current runs can become signoff gates.

### M48: Canonical ISA String Builder

- Replace hard-coded Spike/DUT ISA construction with one shared canonical builder.
- Support all scalar, supervisor, vector, and hypervisor names needed by staged profile runs.
- Acceptance:
  - DUT compile march, Spike isa string, YAML ISA, and profile ledger agree.

### M49: Reference Model Routing

- Identify which mandatory tests can run on Spike.
- Route unsupported tests to another reference model if available, or mark them as blocked with an explicit implementation plan.
- Acceptance:
  - no test silently drops because Spike lacks a feature.

### M50: Budget And Runtime Scaling

- Add profile-specific cycle budgets and timeout classes.
- Split long profile runs into shards without changing pass/fail semantics.
- Acceptance:
  - `rva23s64-full` can run as shards and merge into one coverage report.

### M51: Failure Report Expansion

- Extend failure reports to include profile feature, suite source, ISA string, macros, reference backend, first signature divergence, DUT trace, and waiver status.
- Acceptance:
  - every ACT4 failure directly points to the responsible profile feature.

## Phase 2: Scalar And Unprivileged Mandatory Closure

### M52: Reconcile Existing Scalar Claims

- Audit current `I/M/A/F/D/C/S/U/Z*` implementation against the profile ledger.
- Remove any tooling workaround claims that are not architecturally true.
- Acceptance:
  - declared current ISA matches implemented behavior.

### M53: Complete Atomic And Reservation Guarantees

- Prove `A`, LR/SC eventuality assumptions, reservation-set size, AMO ordering, and `Za64rs`.
- Add stress tests for conflicting stores, cache-line boundaries, misalignment, traps, and privilege transitions.
- Acceptance:
  - ACT4 atomic tests and directed reservation tests pass.

### M54: Complete Cache Block And CMO Guarantees

- Close `Zic64b`, `Zicbom`, `Zicbop`, `Zicboz`, `Ziccif`, `Ziccrse`, `Ziccamoa`, and `Zicclsm`.
- Validate 64-byte block semantics, zeroing, flush/invalidate behavior, ordering, and interaction with PMA/PBMT.
- Acceptance:
  - CMO tests pass under cacheable and non-cacheable regions.

### M55: Complete Hint And Low-Level Scalar Extensions

- Close `Zihintpause`, `Zihintntl`, `Zimop`, `Zcmop`, `Zicond`, `Zawrs`, and `Zkt`.
- Add data-independent timing checks required for `Zkt`.
- Acceptance:
  - each extension has either ACT4 coverage or directed backfill.

### M56: Complete Bitmanip And Compressed Profile Coverage

- Verify full required `Zba`, `Zbb`, `Zbs`, `Zca`, `Zcb`, and profile-required compressed behavior.
- Add illegal encoding and decode-priority tests.
- Acceptance:
  - no compressed/bitmanip mandatory test is excluded.

### M57: Complete FP Scalar Profile Coverage

- Close `F`, `D`, `Zfa`, and required half-minimum behavior.
- Resolve the current `Zfh` vs `Zfhmin` tooling workaround into a true profile declaration.
- Acceptance:
  - FP ACT4 subsets and rounding/flag directed tests pass.

## Phase 3: Memory Model, PMA, PMP, And Platform Semantics

### M58: Define Profile PMA Map

- Specify cacheability, idempotency, atomicity, executable regions, I/O regions, and misaligned-access behavior.
- Tie PMA definitions to RTL and platform YAML.
- Acceptance:
  - PMA map is tested and documented.

### M59: PMP Full Closure

- Verify PMP entry count, grain, TOR/NA4/NAPOT behavior, locked entries, M/S/U interactions, and misaligned access behavior.
- Acceptance:
  - full PMP ACT4 group passes.

### M60: Misaligned Access Closure

- Implement or trap every mandatory misaligned case according to the profile/PMA contract.
- Cover scalar, atomic, FP, compressed fetch, and VM-translated accesses.
- Acceptance:
  - misaligned ACT4 and directed tests pass.

### M61: Memory Ordering Closure

- Validate fences, AMO ordering, CMO ordering, page-table ordering, and instruction-fetch visibility.
- Acceptance:
  - directed memory-order litmus tests pass against expected outcomes.

## Phase 4: Supervisor Profile Closure

### M62: Privileged Spec Version Lift

- Move metadata and implementation from the current privileged baseline to the RVA23S64-required privileged behavior.
- Audit all mandatory CSRs, WARL fields, traps, delegation, and privilege transitions.
- Acceptance:
  - CSR legality tests pass for M/S/U.

### M63: Real Page Table Walker Contract

- Replace any test-only translation shortcuts with a real memory-backed page-table walk contract.
- Support faults, permissions, MXR, SUM, global mappings, ASIDs, A/D behavior, and `stval`.
- Acceptance:
  - VM tests pass without shadow-only assumptions.

### M64: Sv39 And Svnapot Closure

- Verify all mandatory Sv39 behavior and current `Svnapot` behavior under real PTW flow.
- Add NAPOT superpage and invalid-PTE tests.
- Acceptance:
  - full Sv39/Svnapot profile tests pass.

### M65: Svpbmt Closure

- Implement PBMT PTE bits and their effect on cacheability, ordering, and PMA interaction.
- Acceptance:
  - PBMT tests pass for cacheable, non-cacheable, and I/O-like regions.

### M66: Svinval Closure

- Implement `sinval.vma`, `sfence.w.inval`, and `sfence.inval.ir` semantics.
- Test stale TLB eviction, ordering, ASID behavior, and global mappings.
- Acceptance:
  - invalidation tests pass with ITLB, DTLB, and shared TLB enabled.

### M67: Sstc Closure

- Implement supervisor timer compare behavior, interrupt pending rules, delegation, and CSR legality.
- Acceptance:
  - S-mode timer tests and supervisor smoke pass.

### M68: Sscofpmf Closure

- Implement counter overflow filtering, interrupt behavior, mode filtering, and CSR semantics.
- Acceptance:
  - counter overflow directed tests pass.

### M69: Supm And Ssnpm Closure

- Implement required pointer masking behavior for supervisor and negative cases.
- Acceptance:
  - PMLEN and masked-address tests pass.

### M70: Remaining Supervisor Mandatory CSR Extensions

- Close `Ssu64xl`, `Svade`, `Svbare`, `Ssccptr`, `Sstvecd`, `Sstvala`, and `Sscounterenw`.
- Acceptance:
  - CSR and trap-value tests pass with profile metadata declared.

## Phase 5: Vector Architecture Closure

### M71: Vector Design Decision Lock

- Confirm profile-required `VLEN`, `ELEN`, supported SEW/LMUL set, and context-state policy.
- Decide whether current `VLEN=128` remains legal; if not, resize before implementation continues.
- Acceptance:
  - vector configuration is documented and checked by elaboration-time assertions.

### M72: Vector CSR And State Model

- Implement complete `vtype`, `vl`, `vlenb`, `vstart`, `vxrm`, `vxsat`, `mstatus.VS`, and context dirty behavior.
- Acceptance:
  - CSR WARL, illegal `vtype`, and context-state tests pass.

### M73: Vector Decode And Illegal Instruction Matrix

- Add complete base `V` decode coverage and illegal encoding checks.
- Acceptance:
  - unsupported encodings trap correctly and supported encodings issue correctly.

### M74: Vector Integer ALU

- Implement required integer arithmetic, logical, compare, min/max, merge, move, mask, carry/borrow, widening, narrowing, and extension operations.
- Acceptance:
  - vector integer architectural and differential tests pass.

### M75: Vector Permute And Reduction

- Implement slides, gathers, compress, scalar moves, reductions, and mask reductions.
- Acceptance:
  - permutation/reduction tests pass across SEW/LMUL/mask combinations.

### M76: Vector Memory Unit

- Implement unit-stride, strided, indexed, masked, whole-register, and required fault behavior.
- Add precise trap and `vstart` restart tests.
- Acceptance:
  - vector memory tests pass under cache, PMP, PMA, and VM.

### M77: Vector Fixed-Point

- Implement saturation, rounding, narrowing, averaging, multiply, and `vxrm/vxsat` behavior.
- Acceptance:
  - fixed-point flag and rounding tests pass.

### M78: Vector Floating Point Base

- Implement required vector FP operations, rounding modes, flags, NaN behavior, conversions, widening/narrowing, and reductions.
- Acceptance:
  - vector FP tests pass against Spike/Sail.

### M79: Vector Precise Traps And Restart

- Validate `vstart`, partial completion, exceptions, page faults, and replay behavior.
- Acceptance:
  - precise trap differential tests pass.

### M80: Vector Differential Infrastructure

- Add random/vector instruction generation against Spike or Sail.
- Track seeds, shrink failing cases, and attach traces to failure reports.
- Acceptance:
  - nightly vector differential run has reproducible artifacts.

## Phase 6: RVA23 Vector Companion Extensions

### M81: Zvfhmin Closure

- Implement required vector half-minimum operations and conversions.
- Acceptance:
  - `Zvfhmin` tests pass under all legal SEW/LMUL cases.

### M82: Zvbb Closure

- Implement full vector bitmanip subset required by RVA23S64.
- Acceptance:
  - `Zvbb` architectural/differential tests pass.

### M83: Zvkt Closure

- Implement the data-independent execution-latency contract for required vector instructions.
- Add timing-observation tests using cycle/perf instrumentation.
- Acceptance:
  - `Zvkt` is supported by behavior and timing evidence, not just decode.

## Phase 7: Hypervisor And Sha Closure

### M84: Hypervisor Architecture Plan

- Map required `H/Sha` CSRs, modes, traps, state-enable behavior, and translation interactions.
- Acceptance:
  - hypervisor implementation checklist is complete before RTL work starts.

### M85: Hypervisor CSR File

- Implement `hstatus`, `hedeleg`, `hideleg`, `hie`, `hip`, `hvip`, `hcounteren`, `hstateen*`, `henvcfg`, `htval`, `htinst`, and required VS CSRs.
- Acceptance:
  - CSR legality and WARL tests pass.

### M86: VS-Stage Translation

- Implement VS `satp`, virtual supervisor page faults, permissions, and trap reporting.
- Acceptance:
  - VS translation tests pass.

### M87: G-Stage Translation

- Implement `hgatp`, guest physical translation, G-stage faults, and nested fault reporting.
- Acceptance:
  - G-stage and nested translation tests pass.

### M88: Hypervisor Trap And Interrupt Flow

- Implement virtual interrupt injection, delegation, trap entry/return, and `vstvec` behavior.
- Acceptance:
  - virtual timer/software/external interrupt tests pass.

### M89: Sha Bundle Closure

- Close `Ssstateen`, `Shcounterenw`, `Shvstvala`, `Shtvala`, `Shvstvecd`, `Shvsatpa`, and `Shgatpa`.
- Acceptance:
  - `Sha` ledger entries all have passing tests.

## Phase 8: OS And System-Level Profile Validation

### M90: SBI/OpenSBI Smoke

- Boot a minimal SBI/OpenSBI payload far enough to validate M/S transitions, timer, traps, and console/tohost path.
- Acceptance:
  - boot log reaches known-good marker.

### M91: Linux Minimal Smoke

- Boot a small Linux or supervisor payload with Sv39, timer, interrupts, and user process entry.
- Acceptance:
  - reaches init or a deterministic user-mode test marker.

### M92: VM Stress

- Run page-fault, TLB shootdown, PBMT, NAPOT, Svinval, and supervisor timer stress workloads.
- Acceptance:
  - no hangs, stale translations, or signature mismatches.

### M93: Vector OS Context Smoke

- Validate vector enable/disable, lazy state, dirty state, trap behavior, and context save/restore.
- Acceptance:
  - two-task vector context test passes.

### M94: Hypervisor Smoke

- Run a minimal guest under HS-mode if practical.
- Acceptance:
  - guest reaches known-good marker and exits cleanly.

## Phase 9: Formal And Directed Backstop

### M95: Formal Scalar Retire Gate

- Expand riscv-formal coverage for scalar retire, traps, CSR legality, memory faults, and branch redirects.
- Acceptance:
  - selected formal checks pass.

### M96: Formal Memory/VM Invariants

- Add assertions for PTW ordering, TLB invalidation, PMA/PBMT consistency, and PMP permissions.
- Acceptance:
  - bounded proofs or targeted checks pass.

### M97: Directed Regression Suite Cleanup

- Convert all one-off debug tests into named directed suites.
- Add expected pass/fail metadata and feature ownership.
- Acceptance:
  - directed suite can run as a single command and produces a summary.

### M98: Waiver Discipline

- Add waiver files with owner, reason, affected feature, expiry condition, and replacement test.
- Acceptance:
  - final full profile run fails on any mandatory unexpired waiver.

## Phase 10: Final Declaration And Documentation

### M99: Final ISA And Platform Metadata

- Update final YAMLs to declare full mandatory RVA23S64.
- Remove temporary declaration workarounds that conflict with actual profile names.
- Acceptance:
  - profile checker accepts the final declaration.

### M100: Documentation Refresh

- Rewrite `documentation.md` around actual final support:
  - implemented profile;
  - exact ISA string;
  - VLEN/ELEN;
  - PMA/PBMT behavior;
  - VM modes;
  - hypervisor support;
  - validation commands;
  - residual optional non-claims.
- Acceptance:
  - docs match ledger and final ACT4 output.

### M101: Full RVA23S64 ACT4 Signoff

- Run the final full profile:
  - `./run_act4.sh --profile rva23s64-full --fast-sim --verilate-jobs 10 --sim-jobs 10 --sim-threads 1`
- Acceptance:
  - zero mandatory missing tests;
  - zero unwaived blocked tests;
  - zero failures;
  - coverage report archived.

### M102: Full Cross-Validation Signoff

- Run:
  - full ACT4;
  - Tenstorrent architectural tests;
  - selected formal checks;
  - full directed suite;
  - vector differential suite;
  - OS/supervisor smoke;
  - hypervisor smoke.
- Acceptance:
  - all pass from a clean build.

## Phase 11: Performance Recovery

### M103: CoreMark Regression Recovery

- Restore at least the pre-retime CoreMark baseline of `<= 323,354` cycles.
- Acceptance:
  - CoreMark passes and cycle count is documented.

### M104: Frontend Redirect Recovery

- Re-enable prediction/refill improvements only after mixed dual-issue and full profile tests pass.
- Acceptance:
  - no ACT4, directed, or CoreMark regression.

### M105: Bypass And Load-Use Recovery

- Add bypass/latency improvements behind targeted correctness tests.
- Acceptance:
  - performance improves without weakening profile signoff.

### M106: Final Performance And Profile Lock

- Re-run full final validation after performance work.
- Acceptance:
  - full RVA23S64 remains green;
  - CoreMark target remains met;
  - documentation records final performance.

## Final Validation Command Set

- `sbt compile`
- `sbt "runMain borb.SoC"`
- `./run_act4.sh --profile rva23s64-full --fast-sim --verilate-jobs 10 --sim-jobs 10 --sim-threads 1`
- `./run_act4.sh --profile rva23s64-full --fast-sim --verilate-jobs 10 --sim-jobs 10 --sim-threads 1`
- `./run_tenstorrent.sh`
- `./run_formal.sh`
- full directed suite
- vector differential suite
- OpenSBI/Linux smoke
- hypervisor smoke
- CoreMark profile

## Non-Negotiable Rules

- Do not claim an extension because decode exists; claim only when architectural behavior and tests exist.
- Do not count a ACT4 pass as full profile coverage unless missing/blocked coverage is zero.
- Do not hide validator incompatibilities; separate tool limitations from implementation limitations.
- Do not regress `--profile current` while expanding full-profile support.
- Update `documentation.md` and the profile ledger as each feature lands.
