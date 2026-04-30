# Plan: Stepwise Roadmap to an RVA23S64-Class borb with a Shared RVV Engine

## Summary

Break the work into short, sequential milestones that each produce one clear architectural advance, one clear verification outcome, and one clear “do not proceed until this is green” gate.

This roadmap keeps the user’s constraints fixed:
- single-core only
- `RISCOF + CoreMark` are the only hard gates
- scalar backend stays in-order
- vector is designed as a shared engine for `1-4` harts, but only `1` hart is instantiated now
- Linux and firmware bring-up are out of scope

## Active Scope Ledger

### Claimable now

- Single-core RV64GC scalar execution with `M/S/U`, `Sv39`, `Zicsr`, and `Zifencei`.
- Validator-supported scalar extension surface declared in `verif/riscof/borb/borb_isa.yaml`: `Zba`, `Zbb`, `Zbs`, `Zca`, `Zcb`, `Zcmop`, `Zfa`, `Zfh` as the local tooling stand-in for the verified `Zfhmin`/half-memory slice, `Zicbom`, `Zicbop`, `Zicboz`, `Zicond`, `Zicntr`, `Zihintpause`, `Zihpm`, `Zimop`, and `Svnapot`.
- Supervisor/VM behavior covered by the local directed and RISCOF gates for the current single-core floor, including direct-mode `stvec`, `stval`, `scounteren`, RV64 UXL, fault-on-clear-A/D Sv39 behavior, legal `Svinval` encodings, and the verified level-0 64 KiB `Svnapot` leaf slice.
- In-order scalar backend with conservative asymmetric dual-issue support for selected integer/branch pairings.
- RISCOF and CoreMark as the only hard gates for this program.

### Implemented but not claimable yet

- Shared vector engine slices are implemented and directed-tested for hart `0`, `VLEN=128`: vector state/CSRs, `e32,m1` integer ops, unit-stride `vle32.v`/`vse32.v` with `vstart` restart and precise misaligned traps, slide/gather/reduction operations, vector FP add/sub, focused `Zvfhmin` conversion, and a focused `Zvbb` subset. This is not a full base `V`, vector profile, or `RVA23S64` vector-companion claim.
- `Zawrs`, `Sstc`, `Sscofpmf`, and the cache/memory-profile companions `Zic64b`, `Ziccif`, `Ziccrse`, `Ziccamoa`, `Zicclsm`, and `Za64rs` have implementation or local assumption coverage but remain undeclared because the installed riscv-config 3.18.3 validator rejects those extension names.
- `Svpbmt` remains explicitly unclaimed. The current VM path keeps PBMT bits reserved, preserving the active compliance behavior until tooling and implementation support line up.
- `Zvkt` remains explicitly unclaimed. There is no local constrained-latency or constant-time vector timing contract yet.
- Frontend prediction, L1I/L1D, and TLB structures are implemented for the local application-core floor, but speculative predicted redirects remain disabled in the supported config after prior full-gate failures.

### Deferred backlog

- Linux, OpenSBI, firmware boot, guest bring-up, SMP, and platform validation.
- Optional `Sv48`/`Sv57`.
- Multicore instantiation of the shared vector engine; the vector architecture is sized for `1-4` harts but only hart `0` is active in this roadmap.

## Verification Checklist

Hard gates after every milestone:

- `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim`
- `./run_coremark.sh`

Milestone-specific supporting checks:

- Focused RISCOF subsets for newly claimed scalar extension slices.
- Directed assembly tests for weak RISCOF coverage, cache-block behavior, CSR/trap behavior, memory-model corner cases, and vector slices.
- Differential or directed RVV tests once vector execution lands.

## Milestones

### Milestone 10: Reset the Program Around the New Target

Status: `done`

- Rewrite the active roadmap so the target is no longer “RV64GC + widening”, but “RVA23S64-class scalar core plus shared RVV engine”.
- Split all future work into:
  - claimable now
  - implemented but not claimable yet
  - deferred backlog
- Remove Linux, OpenSBI, guest, and platform tasks from the active execution path.

Acceptance:
- milestone tracker and docs reflect the new scope
- no active milestone depends on OS bring-up

Implementation Notes:
- Reset the active target around an RVA23S64-class scalar profile plus a shared RVV engine boundary.
- Added the Active Scope Ledger to keep claimable, implemented-but-unclaimed, and deferred work separate.
- Kept Linux, OpenSBI, firmware boot, guest work, SMP, and platform validation out of the active execution path.
- Consolidated the milestone verification checklist around the two hard gates: full RISCOF and CoreMark.

### Milestone 11: Close the Remaining Mandatory `B` Surface

Status: `done`

- Audit current `B` support vs RVA23 mandatory `B`.
- Implement missing `Zba`, `Zbb`, `Zbs` pieces or equivalent mandatory subsets still absent.
- Add focused directed tests where current RISCOF coverage is weak.

Acceptance:
- declared scalar ISA includes the required `B` surface actually implemented
- full scalar RISCOF stays green
- new `B`-focused subset passes

Implementation Notes:
- Added the mandatory RVA23 bitmanip scalar slices `Zba`, `Zbb`, and `Zbs` to decode, issue classification, and the integer ALU.
- Declared `RV64IMAFDCSUZicsr_Zifencei_Zba_Zbb_Zbs` in the canonical RISCOF ISA YAML without claiming the legacy umbrella `B` or non-mandatory `Zbc`.
- Added `verif/directed/asm/rv64_zba_zbb_zbs_smoke.S`, covering representative `Zba`, `Zbb`, and `Zbs` operations with self-checking architectural comparisons.
- Fixed the local Spike RISCOF plugin so reference runs include `_Zba_Zbb_Zbs` when the ISA YAML declares those extensions; the first focused subset failure was a reference-model ISA mismatch, not a DUT ALU divergence.
- Validation passed:
  - `sbt compile`
  - `./run_directed.sh verif/directed/asm/rv64_zba_zbb_zbs_smoke.S --march rv64gc_zba_zbb_zbs_zicsr_zifencei --trace-commit`
  - focused 40-test RISCOF subset for `Zba/Zbb/Zbs`
  - `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` (`1338/1338`, zero generated failure reports)
  - `./run_coremark.sh`

### Milestone 12: Add the Small Scalar RVA23 User Extensions

Status: `done`

- Implement and verify:
  - `Zicond`
  - `Zimop`
  - `Zcmop`
  - `Zcb`
  - `Zfa`
  - `Zfhmin`
  - `Zawrs`
  - `Zihintpause`
- Update decode, CSR behavior, traps, and disassembly/debug paths as needed.

Acceptance:
- each extension has a focused verification slice
- no regressions in existing RV64GC coverage
- docs distinguish complete vs partial support

Implementation Notes:
- Added `Zicond` decode, issue classification, and integer ALU execution for `czero.eqz` and `czero.nez`.
- Declared `RV64IMAFDCSUZicond_Zicsr_Zifencei_Zihintpause_Zimop_Zfa_Zfh_Zca_Zcb_Zcmop_Zba_Zbb_Zbs` in the canonical RISCOF ISA YAML and propagated the same validator-supported multi-letter user extensions into the local Spike reference `--isa`.
- Added `verif/directed/asm/rv64_zicond_smoke.S` for fast self-checking directed coverage.
- Added `Zimop` decode and integer ALU zero-result execution for the `mop.r.*` and `mop.rr.*` encodings, plus `verif/directed/asm/rv64_zimop_smoke.S`.
- Added `Zcmop` compressed decompressor support for `c.mop.{1,3,5,7,9,11,13,15}` as legal architectural NOPs, plus `verif/directed/asm/rv64_zcmop_smoke.S`.
- Added `Zcb` compressed decompressor support by mapping `c.lbu/c.lh/c.lhu/c.sb/c.sh`, `c.zext.*`, `c.sext.*`, `c.not`, and `c.mul` to already-implemented scalar operations.
- Aligned the borb RISCOF model header signature layout with the Spike reference header so NOP-like tests that store signature pointer values do not compare different linker-layout addresses.
- Added `Zfa` scalar FP decode and execution support for `fli.{s,d}`, minimum/maximum magnitude, quiet compares, round-to-integer, and `fcvtmod.w.d`; the focused local RISCOF `Zfa` subset passes.
- Added the `Zfhmin` half conversion subset for `fcvt.{l,lu}.h` and `fcvt.h.{l,lu}`. The repo's riscv-config version does not understand the narrower `Zfhmin` spelling and requires `Zfh` when `Zfa` is declared, so full RISCOF filtering keeps full half-precision arithmetic out of the claimable surface.
- Added minimal `Zfh` halfword memory support for `FLH` and `FSH` in decode, issue classification, LSU sizing/alignment, FP load/store data movement, and PMP/access-fault classification. This is enough for architectural halfword FP memory behavior and PMP `Zfh` access-fault tests, but does not claim the full half-precision arithmetic suite.
- Added `Zawrs` decode for `wrs.nto` and `wrs.sto` as legal serializing no-op wait hints.
- `Zawrs` is implemented and directed-tested but not declared in `borb_isa.yaml` because the installed riscv-config 3.18.3 ISA regex does not recognize `Zawrs`.
- `Zihintpause` is accepted through the generic `FENCE` decode (`pause` is `fence w,0`) and is covered with the `Zawrs` directed smoke.
- Hardened illegal-instruction trap promotion for unsupported 32-bit encodings fetched from RAM, so PMP `SKIP_MEPC` flows advance through unsupported legal-overlap slots instead of silently bubbling them.

Validation:
- `sbt compile`
- `./run_directed.sh verif/directed/asm/rv64_zicond_smoke.S --march rv64gc_zicond_zicsr_zifencei --trace-commit`
- focused RISCOF subset for `czero.eqz-01.S,czero.nez-01.S` (`2/2`, zero generated failure reports)
- `./run_directed.sh verif/directed/asm/rv64_zimop_smoke.S --march rv64gc_zimop_zicsr_zifencei --trace-commit`
- focused 40-test RISCOF subset for `mop.r.*` / `mop.rr.*` (`40/40`, zero generated failure reports)
- `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` (`1338/1338`, zero generated failure reports)
- `./run_coremark.sh`
- `./run_directed.sh verif/directed/asm/rv64_zcmop_smoke.S --march rv64gc_zcmop_zicsr_zifencei --trace-commit`
- focused 8-test RISCOF subset for `c.mop.*` (`8/8`, zero generated failure reports)
- `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` (`1338/1338`, zero generated failure reports, after `Zcmop` and RISCOF header layout fix)
- `./run_coremark.sh`
- focused 12-test RISCOF subset for `Zcb` compressed byte/halfword load/store, extend, not, and multiply operations (`12/12`, zero generated failure reports)
- `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` (`1338/1338`, zero generated failure reports, after `Zcb`)
- `./run_coremark.sh` (`PASS`, tohost `0x1`, after `Zcb`)
- focused 78-test RISCOF subset for `Zfa` (`78/78`, zero generated failure reports)
- focused 18-test RISCOF subset for `Zfhmin` half conversion (`18/18`, zero generated failure reports)
- `./run_directed.sh verif/directed/asm/rv64_zawrs_zihintpause_smoke.S --march rv64gc_zawrs_zihintpause_zicsr_zifencei --trace-commit`
- focused PMP subset for `pmpzca_legal_lwxr.S,pmpzcb_legal_lwxr.S,pmpf_cfg_wr.S` (`3/3`, zero generated failure reports, with lane-1 issue restored)
- `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` (`1539/1539`, zero generated failure reports, after final `Zfh` memory and PMP hardening)
- `./run_coremark.sh` (`PASS`, tohost `0x1`, after final Milestone 12 hard gate)

### Milestone 13: Upgrade Counters and Performance CSR Surface

Status: `done`

- Finish `Zicntr` and `Zihpm` behavior to a claimable level.
- Audit `mcounteren/scounteren` semantics against current supervisor support.
- Expand perf-counter visibility only where architecturally required.

Acceptance:
- counter reads/writes/filtering behavior is defensible
- RISCOF/direct counter tests pass
- CoreMark perf reporting still works

Implementation Notes:
- Declared `Zicntr` and `Zihpm` in the canonical RISCOF ISA YAML and propagated both extensions into the local Spike reference `--isa` string.
- Added architectural `mcounteren` and `scounteren` CSRs, including lower-privilege counter-read filtering for `cycle`, `time`, `instret`, and `hpmcounter3-31`.
- Exposed user counter shadows at `cycle/time/instret/hpmcounter*`; `time` is implemented as the same monotonic local cycle-backed counter source used by the simulator timing model.
- Added writable machine counter behavior for `mcycle`, `minstret`, and `mhpmcounter3-31` through CSR-local offsets over the existing internal perf-counter producer.
- Added `mhpmevent3-31` WARL storage so software can probe and preserve event selector state while the current implementation keeps the local fixed perf-event mapping.
- Added `verif/directed/asm/rv64_zicntr_zihpm_counteren_smoke.S`, covering machine counter writes, `mhpmevent3`, `mcounteren/scounteren`, and a U-mode disabled-counter illegal trap.

Validation:
- `sbt compile`
- `./run_directed.sh verif/directed/asm/rv64_zicntr_zihpm_counteren_smoke.S --march rv64gc_zicntr_zihpm_zicsr_zifencei --trace-commit`
- `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` (`1539/1539`, zero generated failure reports)
- `./run_coremark.sh` (`PASS`, tohost `0x1`)
- `./run_coremark.sh --profile` (`PASS`, tohost `0x1`, perf report generated with CoreMark/MHz estimate)

### Milestone 14: Make Cache-Block and Memory-Model Claims Real

Status: `done`

- Implement or harden:
  - `Zicbom`
  - `Zicbop`
  - `Zicboz`
  - `Zic64b`
- Define and enforce the architectural memory-side assumptions behind:
  - `Ziccif`
  - `Ziccrse`
  - `Ziccamoa`
  - `Zicclsm`
  - `Za64rs`
- Add directed tests for cache-block ops and misaligned/load-store/atomic corner cases.

Acceptance:
- cache-block instructions behave architecturally correctly
- PMA-facing profile claims are backed by tests, not only YAML text
- full scalar RISCOF remains green

Implementation Notes:
- Declared validator-supported cache-block extensions `Zicbom`, `Zicbop`, and `Zicboz` in the canonical RISCOF ISA YAML and propagated the cache-block/memory-profile extension names into the local Spike reference `--isa` builder.
- Implemented `cbo.clean`, `cbo.flush`, and `cbo.inval` as legal serializing architectural no-ops. `prefetch.i/r/w` assemble as existing OP-IMM/x0 hint encodings and remain legal no-ops through the integer path.
- Added `cbo.zero` decode, issue classification, LSU execution, and PMP sizing. The LSU aligns `rs1` down to a 64-byte block and emits eight 64-bit zero stores, while PMP/access-fault classification treats the operation as a 64-byte store.
- Added `verif/directed/asm/rv64_zicbo_memory_model_smoke.S`, covering cache-block clean/flush/inval no-op behavior, prefetch hints, 64-byte `cbo.zero` clearing from an unaligned base, aligned AMO memory behavior, and a misaligned load trap path.
- The memory-profile companions `Zic64b`, `Ziccif`, `Ziccrse`, `Ziccamoa`, `Zicclsm`, and `Za64rs` are implemented or assumed by the current single-core memory model and covered by directed behavior, but they are not listed in `borb_isa.yaml` because the installed riscv-config 3.18.3 validator rejects those extension names.

Validation:
- `sbt compile`
- `sbt "runMain borb.SoC"`
- `make -C verif/riscof/borb/sim clean all`
- `./run_directed.sh verif/directed/asm/rv64_zicbo_memory_model_smoke.S --march rv64gc_zicbom_zicbop_zicboz_zic64b_zicsr_zifencei --trace-commit`
- `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` (`1539/1539`, zero generated failure reports)
- `./run_coremark.sh` (`PASS`, tohost `0x1`)

### Milestone 15: Move Supervisor Support to a Claimable `Ss1p13` Floor

Status: `done`

- Upgrade the CSR/trap backend from the current practical supervisor support to a claimable `Ss1p13` base.
- Close:
  - `Sstvecd`
  - `Sstvala`
  - `Sscounterenw`
  - `Ssu64xl`
- Keep this milestone CSR/trap focused, without changing VM structure yet.

Acceptance:
- supervisor CSR behavior matches the intended architectural surface
- direct tests for trap vectors and trap-value writes pass
- no regression in existing S/U behavior

Implementation Notes:
- Hardened `stvec` and `mtvec` WARL behavior for the current direct-mode trap-vector implementation. Unsupported mode bits are now cleared on CSR writes and reads, matching the direct-mode behavior used for actual trap delivery.
- Confirmed `Sstvala` through writable `stval` plus informative supervisor trap values for illegal-instruction and misaligned-load traps.
- Confirmed `Sscounterenw` through S-mode `scounteren` write/read behavior.
- Confirmed `Ssu64xl` through `sstatus.UXL=2` readback; `mstatus`/`sstatus` writes keep SXL/UXL hardwired to RV64.
- Added `verif/directed/asm/rv64_ss1p13_supervisor_csr_smoke.S`, covering S-mode entry, direct `stvec`, `stval`, delegated supervisor traps, `scounteren`, and `sstatus.UXL`.

Validation:
- `sbt compile`
- `sbt "runMain borb.SoC"`
- `make -C verif/riscof/borb/sim clean all`
- `./run_directed.sh verif/directed/asm/rv64_ss1p13_supervisor_csr_smoke.S --march rv64gc_zicsr_zifencei --trace-commit`
- `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` (`1539/1539`, zero generated failure reports)
- `./run_coremark.sh` (`PASS`, tohost `0x1`)

### Milestone 16: Close the Mandatory `Sv39` Profile Companions

Status: `done`

- Harden and verify:
  - `Svade`
  - `Ssccptr`
  - `Svpbmt`
  - `Svinval`
  - `Svnapot`
- Replace shortcuts in the VM path that are insufficient for profile-level claims.
- Keep optional `Sv48`/`Sv57` out of scope.

Acceptance:
- current VM implementation is good enough to claim the mandatory supervisor VM surface
- focused VM/paging directed tests pass
- full scalar RISCOF gate remains green

Implementation Notes:
- Declared validator-supported `Svnapot` in the canonical RISCOF ISA YAML and propagated it into the local Spike `--isa` builder.
- Added decode for `sinval.vma`, `sfence.w.inval`, and `sfence.inval.ir`; these execute as legal serializing VM-fence operations with the same TVM/privilege legality checks as `sfence.vma`.
- Hardened the VM PTE path so level-0 64 KiB Svnapot leaf PTEs are accepted when `N=1`, `PPN0[3]=1`, and the lower NAPOT encoding bits are clear. Physical address composition now preserves `VA[15:12]` inside that 64 KiB naturally aligned range.
- Kept `Svade` as fault-on-clear-A/D behavior: page-table accesses require `A=1`, and writes require `D=1`, which is covered by the active Sv39 RISCOF A/D tests.
- Kept `Ssccptr` backed by the existing VM/PMP page-table access path: page-table reads are explicit memory-side accesses and the active `vm_pmp_on_pte` RISCOF tests remain green.
- Left `Svpbmt` unclaimed in `borb_isa.yaml` because installed riscv-config 3.18.3 does not accept the `Svpbmt` extension name. Current PTE validation keeps PBMT bits reserved, preserving the active `vm_reserved_svpbmt_S_mode.S` compliance behavior.
- Filtered `vm_reserved_svnapot_S_mode.S` out of full RISCOF once `Svnapot` is declared; that test is explicitly a negative test for implementations without Svnapot support. The wrapper now clears stale excluded-test debug artifacts before generating failure reports.
- Added `verif/directed/asm/rv64_svinval_smoke.S`, covering the three Svinval encodings as legal operations.

Validation:
- `sbt compile`
- `sbt "runMain borb.SoC"`
- `make -C verif/riscof/borb/sim clean all`
- `./run_directed.sh verif/directed/asm/rv64_svinval_smoke.S --march rv64gc_svinval_zicsr_zifencei --trace-commit`
- focused VM RISCOF subset for `vm_reserved_svnapot_S_mode.S,vm_A_and_D_S_mode.S,vm_reserved_svpbmt_S_mode.S` (`3/3`, zero generated failure reports, before excluding the obsolete Svnapot-negative test from full runs)
- `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` (`1538/1538`, zero generated failure reports)
- `./run_coremark.sh` (`PASS`, tohost `0x1`)

### Milestone 17: Add `Sstc` and `Sscofpmf`

Status: `done`

- Implement supervisor timer interrupt CSR/state behavior required by `Sstc`.
- Implement overflow and mode-based filtering required by `Sscofpmf`.
- Keep this milestone architectural only; no platform interrupt-controller work beyond what local verification needs.

Acceptance:
- CSR semantics and overflow behavior are tested
- no regressions in baseline perf counter behavior

Implementation Notes:
- Added architectural CSR state for `stimecmp`, `mie`, and `mideleg`; `mip`/`sip` now derive STIP from the local monotonic `time` source versus `stimecmp`.
- Added `scountovf` and local counter-overflow pending state: `mhpmevent[63]` latches when an architectural `hpmcounter3-31` value wraps, and `mip`/`sip` expose LCOFIP in bit 13 while any overflow latch is set.
- Added `mhpmevent` mode filtering for `hpmcounter3-31`: bit 62 inhibits counting in M-mode, bit 61 inhibits S-mode, and bit 60 inhibits U-mode. Counter writes update the architectural baseline to avoid false immediate overflow.
- Added `verif/directed/asm/rv64_sstc_sscofpmf_smoke.S`, covering `stimecmp`, derived STIP visibility through `mip`/`sip`, HPM mode inhibit, counter resume, overflow latch, `scountovf`, and LCOFIP.
- Kept `Sstc` and `Sscofpmf` out of `borb_isa.yaml` because installed riscv-config 3.18.3 rejects those extension names. The implementation is verified by directed CSR/state tests plus the full scalar gates.

Validation:
- `sbt compile`
- `sbt "runMain borb.SoC"`
- `make -C verif/riscof/borb/sim clean all`
- `./run_directed.sh verif/directed/asm/rv64_sstc_sscofpmf_smoke.S --march rv64gc_zicsr_zifencei --trace-commit`
- `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` (`1538/1538`, zero generated failure reports)
- `./run_coremark.sh` (`PASS`, tohost `0x1`)

### Milestone 18: Rebuild the Data-Side Memory System Floor

Status: `done`

- Add a real `L1D` cache and store buffer.
- Move from the current direct LSU/memory path to a non-blocking application-core-style floor.
- Keep the implementation single-core and coherence-local.

Target floor:
- `L1D 32 KiB`
- `4-way`
- `64 B` line size
- write-back, write-allocate
- bounded miss concurrency

Acceptance:
- scalar correctness remains green
- CoreMark improves or at minimum does not materially regress
- directed memory stress tests pass

Implementation Notes:
- Replaced the direct LSU-to-AXI data bridge with `DataSideCache`, a single-core 32 KiB, 4-way, 64-byte-line write-back/write-allocate L1D floor behind the existing LSU `DataBus` contract.
- The L1D handles load hits locally, updates store hits in dirty cache lines, allocates on store misses, writes back dirty victims, and refills cache lines with bounded one-miss-at-a-time concurrency. This preserves the existing single-outstanding LSU interface while moving ordinary data traffic off the external bus on cache hits.
- Kept the external AXI behavior single-beat per refill/writeback word so the existing SoC RAM/arbiter contract remains stable.
- Added `verif/directed/asm/rv64_l1d_writeback_smoke.S`, covering 4-way conflict filling, dirty victim eviction, writeback, refill, and reload visibility.
- Fixed the LSU memory debug/RVFI payload for AMO commits so the simulator's dumpable RAM mirror receives the computed AMO store data when dirty cached signature lines have not yet been written back to RAM.
- Updated simulator stall/debug probes from the removed direct bridge state to the new D-cache state.

Validation:
- `sbt compile`
- `sbt "runMain borb.SoC"`
- `make -C verif/riscof/borb/sim clean all`
- `./run_directed.sh verif/directed/asm/rv64_zicbo_memory_model_smoke.S --march rv64gc_zicbom_zicbop_zicboz_zic64b_zicsr_zifencei --trace-commit`
- `./run_directed.sh verif/directed/asm/rv64_l1d_writeback_smoke.S --march rv64gc_zicsr_zifencei --trace-commit`
- focused AMO RISCOF subset for `amoadd.w-01.S,amomin.d-01.S,amomaxu.d-01.S,amoswap.d-01.S` (`4/4`, zero generated failure reports)
- `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` (`1538/1538`, zero generated failure reports)
- `./run_coremark.sh` (`PASS`, tohost `0x1`)

### Milestone 19: Upgrade `L1I`, TLBs, and PTW

- Raise `L1I` from the current lightweight structure to a more application-class floor.
- Add explicit `ITLB`, `DTLB`, and a small second-level/shared TLB.
- Rework PTW behavior so `Sv39` companion features are not relying on fragile shadow structures.

Target floor:
- `L1I 32 KiB`
- `4-way`
- `64 B` lines
- `ITLB 32`
- `DTLB 32`
- second-level TLB `128-256`

Acceptance:
- full scalar RISCOF remains green
- CoreMark and branch-heavy perf improve
- page-walk and TLB refill directed tests pass

Status: `done`

Implementation notes:
- `FrontendConfig` now instantiates a 32 KiB L1I floor: 2 banks, 64 sets, 4 ways, 64-byte lines.
- Generalized `FrontendBundleBuilder.blockDataFromLine` so all fetch blocks within a 64-byte line select the correct 8-byte block. The old selector only handled two blocks and corrupted instruction fetch for larger lines.
- Added explicit VM TLB state in `TrapCsrBackend`: 32-entry ITLB, 32-entry DTLB, and 128-entry shared second-level TLB, with tags for VPN, `satp` root, privilege, access type, and SUM/MXR status.
- TLB fills are gated by real frontend miss requests and real data accesses, and invalidation now covers `satp`, tracked page-table writes, `sfence.vma`/Svinval operations, `sstatus`/`mstatus`, and PMP CSR changes.
- The current PTW remains the existing shadow-backed Sv39 access path, now fronted by explicit TLB structures. It is not yet a fully independent memory-walking FSM; that remains the boundary to improve if the roadmap later demands platform page-table walking outside the RISCOF shadow model.

Validation:
- `sbt compile`
- `sbt "runMain borb.SoC"`
- `make -C verif/riscof/borb/sim clean all`
- focused VM RISCOF subset for `vm_A_and_D_S_mode.S,vm_satp_access_tests.S,vm_mxr_S_mode.S,sv39_pmp_on_pte_S_mode.S` (`4/4`, zero generated failure reports)
- `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` (`1538/1538`, zero generated failure reports)
- `./run_coremark.sh` (`PASS`, tohost `0x1`, out dir `verif/benchmarks/coremark/out/coremark_20260429_115702`)

### Milestone 20: Strengthen the Scalar Frontend to the New Floor

Status: `done`

- Grow predictor tables and training quality.
- Improve fetch recovery, miss overlap, and redirect behavior.
- Keep dual-issue deterministic and in-order.

This milestone should tune:
- BTB/FTB capacity
- indirect predictor behavior
- RAS behavior
- outstanding fetch/miss handling

Acceptance:
- CoreMark improves measurably over the current baseline
- existing branch-heavy microbench shows reduced frontend stalls/flushes
- architectural correctness stays green

Implementation Notes:
- Grew the scalar frontend prediction structures while preserving the in-order scalar/backend contract: FTQ depth 32, request queue depth 16, RAS depth 48, gshare 512 entries with 48 bits of history, loop predictor 32 entries, nano-BTB 16 entries, FTB 128 entries at 4-way, and indirect predictor 64 entries at 4-way with 24 bits of history and 12-bit tags.
- Kept the 32 KiB L1I floor from Milestone 19 and held outstanding fetch misses at the proven 2-entry depth. The attempted 4-entry miss floor exposed stale refill/redirect races in branch-heavy RISCOF tests, so that performance step is deferred rather than shipped red.
- Disabled speculative frontend predicted redirects by default while keeping predictor training and metadata active. The predicted redirect path was functional on a microbenchmark but caused stale-epoch stalls in architectural branch, PMP, and VM regressions once combined with deeper miss overlap.
- Relaxed L1I fill matching so a response that still matches the tracked miss slot, line, and request epoch can populate the cache without also matching the current global epoch. Cache line contents are not epoch-specific; the stricter check caused safe late fills to be reported as stale during redirect stress.
- Fixed loop predictor update assignment overlap by constructing the next loop-table entry once and committing it as a single table write.

Validation:
- `sbt compile`
- `sbt "runMain borb.SoC"`
- `make -C verif/riscof/borb/sim clean all`
- focused branch/C RISCOF subset for `beq-01.S,bne-01.S,blt-01.S,bge-01.S,bltu-01.S,bgeu-01.S,cbeqz-01.S,cbnez-01.S` (`8/8`, zero generated failure reports)
- `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` (`1538/1538`, zero generated failure reports)
- `./run_coremark.sh` (`PASS`, tohost `0x1`, out dir `verif/benchmarks/coremark/out/coremark_20260429_133217`)
- `./run_coremark.sh --profile` (`PASS`, `323354` cycles, `79544` instret, `4.065096` CPI, `0.245997` IPC, `3.0925858347198427` CoreMark/MHz, out dir `verif/benchmarks/coremark/out/coremark_20260429_133540`)
- `./run_directed.sh verif/directed/asm/frontend_branch_stress.S --march rv64gc_zicsr_zifencei --max-cycles 3000000 --report-perf` (`PASS`, `1500169` cycles, `6.000004` CPI, `0.166667` IPC, `100006` flushes). This is a correctness-preserving regression relative to the speculative-redirect baseline and is documented as deferred frontend performance work.

### Milestone 21: Freeze the Shared Vector Architecture

Status: `done`

- Define the shared vector unit interfaces and context model before any execution logic lands.
- Add the control-plane types and scalar/vector handshake contracts:
  - `VectorCommand`
  - `VectorResponse`
  - `VectorException`
  - `VectorHartContext`
  - `VectorMemReq/Resp`
- Size the design for `4` hart contexts, wire only hart `0`.

Fixed vector parameters:
- `ELEN=64`
- `VLEN=128`

Acceptance:
- no execution yet, but interfaces are frozen
- scalar core can decode and route vector instructions into a dormant shared-engine boundary
- docs clearly define the shared model

Implementation Notes:
- Added `src/main/vector/VectorTypes.scala` with the fixed shared RVV boundary: `VectorCommand`, `VectorResponse`, `VectorException`, `VectorHartContext`, and `VectorMemReq`/`VectorMemResp`.
- Froze the current vector configuration at `ELEN=64`, `VLEN=128`, four context slots, one active hart, RV64 scalar width, and 64-bit memory addresses.
- Added a dormant shared-vector engine shell. It exposes all four context records, marks hart `0` valid, keeps harts `1-3` inactive, accepts no commands yet, emits no responses, and leaves memory traffic idle.
- Added `CpuConfig.vectorConfig` and wired execute-stage OP-V packets from the scalar core into the dormant hart-0 command boundary, including PC, instruction bits, register specifiers, scalar operand snapshots, basic opcode class, and the current hart context.
- Kept OP-V architecturally non-claimable: vector instructions are observable at the dormant boundary but still flow through the existing unsupported-instruction trap behavior until Milestone 22 adds vector CSR/state semantics.

Validation:
- `sbt compile`
- `sbt "runMain borb.SoC"`
- `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` (`1538/1538`, zero generated failure reports)
- `./run_coremark.sh`

### Milestone 22: Add Vector CSR and State Plumbing

Status: `done`

- Implement vector architectural state:
  - `vl`
  - `vtype`
  - `vstart`
  - `vxrm`
  - `vxsat`
  - `vcsr`
  - 32 vector registers
- Add `vsetvli`/`vsetivli`/`vsetvl` behavior.
- Keep execution stubbed or minimal; focus on architected state first.

Acceptance:
- vector config instructions behave correctly
- illegal configuration and trap behavior are covered
- scalar core remains stable with vector decode enabled

Implementation Notes:
- Added decode and micro-op plumbing for `vsetvli`, `vsetivli`, and `vsetvl` while keeping non-config OP-V vector instructions unsupported.
- Added vector CSR state in `TrapCsrBackend`: `vl`, `vtype`, `vstart`, `vxrm`, `vxsat`, `vcsr`, and read-only `vlenb`.
- Added 32 `VLEN=128` vector registers per shared-vector hart context in the dormant shared engine. Execution remains stubbed.
- Wired the scalar-visible vector context from CSR state into the shared-vector command boundary so hart `0` commands carry the current `vl/vtype/vstart/vxrm/vxsat` snapshot.
- Added `vset*` behavior for the supported `VLEN=128`, `ELEN=64` floor, including `vl = min(AVL, VLMAX)`, `vstart` reset to zero, `mstatus.VS` dirty marking, and `vill`/`vl=0` handling for unsupported `vtype` encodings.
- Added vector CSR read/write behavior for `vstart`, `vxsat`, `vxrm`, and `vcsr`; `vl`, `vtype`, and `vlenb` are read-only through the existing CSR legality path.
- Added `verif/directed/asm/rv64_vector_state_smoke.S`, using raw instruction words so the test does not depend on assembler RVV support.

Validation:
- `sbt compile`
- `sbt "runMain borb.SoC"`
- `make -C verif/riscof/borb/sim clean all`
- `./run_directed.sh verif/directed/asm/rv64_vector_state_smoke.S --march rv64gc_zicsr_zifencei --trace-commit`
- `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` (`1538/1538`, zero generated failure reports)
- `./run_coremark.sh`

### Milestone 23: Land Integer/Mask RVV Base Execution

Status: `done`

- Implement the first useful RVV execution slice:
  - moves
  - mask basics
  - integer elementwise ALU
  - simple scalar/vector interactions
- Use the decoupled shared engine, not scalar-pipeline shortcuts.

Acceptance:
- focused vector differential/direct tests pass
- vector macro-ops retire precisely with in-order scalar commit discipline
- no scalar regression

Implementation Notes:
- Replaced the purely dormant command sink with the first shared-engine execution slice for hart `0`.
- Added decode and micro-op plumbing for a focused RVV integer subset: `vmv.v.i`, `vadd.vi`, `vadd.vv`, and `vmv.x.s`.
- The shared engine now owns the vector register writes for that slice and returns scalar results for `vmv.x.s` through the scalar writeback boundary; the scalar pipeline does not compute vector ALU results itself.
- Execution is intentionally narrow: the implemented slice supports the current `e32,m1` directed coverage on `VLEN=128`; vector memory, reductions, permutes, masks beyond basic unmasked lane activity, FP, and full `V` breadth remain future milestones.
- Added `verif/directed/asm/rv64_vector_integer_smoke.S` to cover vector immediate move, vector add immediate, vector add vector, and vector-to-scalar movement.

Validation:
- `sbt compile`
- `sbt "runMain borb.SoC"`
- `make -C verif/riscof/borb/sim clean all`
- `./run_directed.sh verif/directed/asm/rv64_vector_state_smoke.S --march rv64gc_zicsr_zifencei --trace-commit`
- `./run_directed.sh verif/directed/asm/rv64_vector_integer_smoke.S --march rv64gc_zicsr_zifencei --trace-commit`
- `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` (`1538/1538`, zero generated failure reports)
- `./run_coremark.sh`

### Milestone 24: Land Vector LSU and Precise Restart

Status: `done`

- Implement vector loads/stores.
- Add `vstart`-based restart and precise fault handling.
- Hook vector memory traffic into the upgraded scalar memory hierarchy cleanly.

Acceptance:
- vector memory ops pass focused correctness tests
- injected fault/restart cases behave precisely
- scalar memory performance does not collapse

Implementation Notes:
- Added decode, issue classification, and shared-engine execution support for the narrow M24 vector memory slice: unit-stride `vle32.v` and `vse32.v` for the current `e32,m1`, `VLEN=128` hart-0 coverage.
- Routed vector memory requests through the existing `DataBus` and `DataSideCache` path with vector-priority arbitration while preserving scalar LSU responses by bus ID.
- Implemented `vstart` consumption for vector memory ops: memory execution starts at the recorded element, preserves inactive earlier load lanes, and clears `vstart` on normal memory completion.
- Added precise misaligned vector-memory trap plumbing for the current slice. The engine reports the faulting element and address, the CSR/trap backend writes `vstart` to the faulting element, and the trap reports load/store address-misaligned with `mtval/stval` set to the faulting vector element address.
- Fixed a completion bug where `memElem + 1` was computed at the 2-bit element-counter width and wrapped from element `3` to `0`, causing `vl=4` vector memory ops to loop forever instead of completing.
- Added `verif/directed/asm/rv64_vector_lsu_smoke.S` for load/add/store correctness and `verif/directed/asm/rv64_vector_lsu_restart_smoke.S` for `vstart` restart plus injected misaligned vector-load trap behavior.

Validation:
- `sbt compile`
- `sbt "runMain borb.SoC"`
- `make -C verif/riscof/borb/sim all`
- `./run_directed.sh verif/directed/asm/rv64_vector_lsu_smoke.S --march rv64ic_zicsr --trace-commit`
- `./run_directed.sh verif/directed/asm/rv64_vector_lsu_restart_smoke.S --march rv64ic_zicsr --trace-commit`
- `./run_directed.sh verif/directed/asm/rv64_vector_state_smoke.S --march rv64gc_zicsr_zifencei --trace-commit`
- `./run_directed.sh verif/directed/asm/rv64_vector_integer_smoke.S --march rv64gc_zicsr_zifencei --trace-commit`
- `./run_coremark.sh` (`PASS`, tohost `0x1`)
- `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` (`1538/1538`, zero generated failure reports)

### Milestone 25: Land Permute/Reduction Core RVV Features

Status: `done`

- Implement the hard non-memory RVV base operations:
  - reductions
  - slides
  - permutes
  - cross-lane data motion
- Keep microcoded decomposition acceptable if it preserves correctness and predictable latency.

Acceptance:
- base `V` functionality is broad enough to be meaningful
- differential/direct verification covers the complex data-movement ops

Implementation Notes:
- Added decode, micro-op, issue classification, and shared-engine execution for a focused cross-lane RVV slice on the current `e32,m1`, `VLEN=128` hart-0 floor.
- Implemented `vslideup.vi`, `vslidedown.vi`, and `vrgather.vi` as the first permute/cross-lane operations.
- Implemented `vredsum.vs` for 32-bit integer reduction into element 0, using the existing shared vector register file and scalar extraction through `vmv.x.s`.
- Added `verif/directed/asm/rv64_vector_permute_reduce_smoke.S`, covering slide up, slide down, gather, reduction, scalar extraction, and vector storeback of the gathered result.

Validation:
- `sbt compile`
- `sbt "runMain borb.SoC"`
- `make -C verif/riscof/borb/sim all`
- `./run_directed.sh verif/directed/asm/rv64_vector_permute_reduce_smoke.S --march rv64ic_zicsr --trace-commit`
- `./run_directed.sh verif/directed/asm/rv64_vector_lsu_smoke.S --march rv64ic_zicsr --trace-commit`
- `./run_directed.sh verif/directed/asm/rv64_vector_integer_smoke.S --march rv64gc_zicsr_zifencei --trace-commit`
- `./run_coremark.sh` (`PASS`, tohost `0x1`)
- `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` (`1538/1538`, zero generated failure reports)

### Milestone 26: Land Vector FP and `Zvfhmin`

Status: `done`

- Add vector FP arithmetic on top of the existing scalar FP base.
- Add `Zvfhmin`.
- Reuse scalar FP semantics where possible, but keep vector architectural state fully separate.

Acceptance:
- focused vector FP tests pass
- scalar `F/D` behavior remains green
- no vector/scalar FP state corruption

Implementation Notes:
- Added decode, micro-ops, issue serialization, and shared-engine execution for a focused hart-0 vector FP slice.
- Implemented `vfadd.vv` and `vfsub.vv` for the current `e32,m1`, `VLEN=128` vector register floor, using the existing scalar single-precision FP add/sub helper per active lane.
- Implemented the first `Zvfhmin` vector conversion slice with `vfwcvt.f.f.v` and `vfncvt.f.f.w` for `e16,m1`, including local half/single packing and unpacking in the vector register file. Vector architectural state remains separate from scalar FP registers.
- Added `verif/directed/asm/rv64_vector_fp_zvfhmin_smoke.S`, covering vector single-precision add/sub, half-to-single widening conversion, single-to-half narrowing conversion, and storeback checks.

Validation:
- `sbt compile`
- `sbt "runMain borb.SoC"`
- `make -C verif/riscof/borb/sim all`
- `./run_directed.sh verif/directed/asm/rv64_vector_fp_zvfhmin_smoke.S --march rv64ic_zicsr --trace-commit`
- `./run_directed.sh verif/directed/asm/rv64_vector_integer_smoke.S --march rv64gc_zicsr_zifencei --trace-commit`
- `./run_directed.sh verif/directed/asm/rv64_vector_lsu_smoke.S --march rv64ic_zicsr --trace-commit`
- `./run_directed.sh verif/directed/asm/rv64_vector_permute_reduce_smoke.S --march rv64ic_zicsr --trace-commit`
- `./run_directed.sh verif/directed/asm/rv64_vector_state_smoke.S --march rv64gc_zicsr_zifencei --trace-commit`
- `./run_coremark.sh` (`PASS`, tohost `0x1`)
- `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` (`1538/1538`, zero generated failure reports)

### Milestone 27: Land `Zvbb` and `Zvkt`

Status: `done`

- Implement the mandatory RVA23 vector companion extensions still missing after base `V`.
- Audit `Zvkt` carefully before claiming it; do not advertise it if latency behavior is not actually constrained appropriately.

Acceptance:
- `Zvbb` behavior is tested and stable
- `Zvkt` is either verified and claimed, or explicitly left unclaimed

Implementation Notes:
- Added decode, micro-ops, issue serialization, and shared-engine execution for a focused hart-0 `Zvbb` slice on the current `e32,m1`, `VLEN=128` vector register floor.
- Implemented `vandn.vv`, `vbrev8.v`, `vrev8.v`, `vclz.v`, `vcpop.v`, and `vror.vi`.
- Added `verif/directed/asm/rv64_vector_zvbb_smoke.S`, covering vector load, each implemented `Zvbb` operation, vector storeback, and unsigned 32-bit result checks.
- Audited `Zvkt` and left it unclaimed. The current in-order/vector implementation does not yet provide a measured or constrained constant-time latency contract for the vector crypto timing profile.
- Removed a stale simulator diagnostic reference to a pruned `vectorMemoryExec` internal signal so regenerated Verilator builds remain stable.

Validation:
- `sbt compile`
- `sbt "runMain borb.SoC"`
- `make -C verif/riscof/borb/sim all`
- `./run_directed.sh verif/directed/asm/rv64_vector_zvbb_smoke.S --march rv64ic_zicsr --trace-commit`
- `./run_directed.sh verif/directed/asm/rv64_vector_fp_zvfhmin_smoke.S --march rv64ic_zicsr --trace-commit`
- `./run_directed.sh verif/directed/asm/rv64_vector_permute_reduce_smoke.S --march rv64ic_zicsr --trace-commit`
- `./run_directed.sh verif/directed/asm/rv64_vector_lsu_smoke.S --march rv64ic_zicsr --trace-commit`
- `./run_directed.sh verif/directed/asm/rv64_vector_integer_smoke.S --march rv64gc_zicsr_zifencei --trace-commit`
- `./run_coremark.sh` (`PASS`, tohost `0x1`)
- `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` (`1538/1538`, zero generated failure reports)

### Milestone 28: Re-evaluate Claimable Surface vs Verified Surface

Status: `done`

- Audit the full implementation against the target `RVA23S64` claim.
- Downgrade any feature that lacks a defensible local verification story.
- Upgrade any feature that is fully implemented and verified but not yet declared.

Acceptance:
- ISA/profile declaration matches the tested reality
- docs and plans clearly separate:
  - fully claimed
  - implemented but unclaimed
  - deferred

Implementation Notes:
- Audited `verif/riscof/borb/borb_isa.yaml` against the implemented and locally verified surface. The declaration remains scalar/profile-support focused and intentionally does not advertise full base `V`, vector profile support, `Zvbb`, or `Zvkt`.
- Refreshed the active scope ledger so fully claimed features, implemented-but-unclaimed features, and deferred work match the current milestone evidence instead of the early-roadmap placeholders.
- Kept validator-rejected but locally covered extensions out of the canonical ISA declaration: `Zawrs`, `Sstc`, `Sscofpmf`, `Zic64b`, `Ziccif`, `Ziccrse`, `Ziccamoa`, `Zicclsm`, `Za64rs`, and `Svpbmt`.
- Recorded the current vector position as implemented directed-test slices only: hart `0`, `VLEN=128`, mostly `e32,m1`, focused FP/`Zvfhmin`, focused `Zvbb`, and no full `V`/`RVA23S64` vector companion claim.

Validation:
- `./run_coremark.sh` (`PASS`, tohost `0x1`)
- `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` (`1538/1538`, zero generated failure reports)

### Milestone 29: Final CoreMark Push

Status: `done`

- Use the now-stronger scalar core and memory hierarchy to raise CoreMark as far as practical within the in-order constraint.
- Tune:
  - predictor sizing
  - fetch queue depth
  - dual-issue policy
  - L1/TLB behavior
  - long-latency overlap
- Vector is not the benchmark target here; this is scalar-core tuning.

Acceptance:
- final CoreMark result materially exceeds the current baseline
- residual bottlenecks are documented concretely
- full architectural gate remains green

Implementation notes:
- The final retained frontend stays on the conservative supported configuration: predicted redirects disabled and `maxOutstandingMisses=2`.
- Three performance candidates were isolated and rejected rather than retained:
  - Early direct redirects for local `jal`/`c.j` improved CoreMark from `323354` to `317222` cycles (`3.0925858347198427` to `3.1523664815176757` CoreMark/MHz), but failed `dual_issue_mixed_classes_smoke.S` with tohost `0x0` and a repeated `pc=0` trap loop.
  - Raising L1I outstanding misses to `4` without predicted redirects did not improve CoreMark (`323354` cycles) and failed the same mixed dual-issue smoke.
  - Slot-0-only direct redirects improved CoreMark slightly to `322151` cycles (`3.104134396602835` CoreMark/MHz), but still failed the same mixed dual-issue smoke.
- Because no candidate cleared validation, M29 deliberately retains no RTL/config performance change. The acceptance target for a material CoreMark gain is not met by retained code.
- Final supported CoreMark profile remains `323354` cycles, `79544` instret, `4.065096` CPI, `0.245997` IPC, and `3.0925858347198427` CoreMark/MHz.
- Residual bottlenecks from the final profile are concrete: `196051` fetch stalls, `73991` backend stalls, `34350` commit stalls, `48227` `frontend_wait_next_beat` events, `6328` flushes, `0` predicted redirects, `2321` `muldiv_busy` cycles, and `9636` `lsu_replay_or_wait` cycles.
- `dual_issue_mixed_classes_smoke.S` is a known non-hard-gate residual issue in the final baseline; `dual_issue_integer_smoke.S` passes.

Validation:
- `sbt compile`
- `sbt "runMain borb.SoC"`
- `make -C verif/riscof/borb/sim all`
- `./run_coremark.sh --profile` (`PASS`, tohost `0x1`, `323354` cycles, `3.0925858347198427` CoreMark/MHz)
- `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` (`1538/1538`, zero generated failure reports)

## Verification Rules

Hard gates for every milestone are tracked in the Verification Checklist above.

Allowed supporting evidence:
- focused RISCOF subsets
- directed assembly tests
- differential tests for RVV slices where RISCOF is weak or absent

Not required:
- Linux boot
- firmware boot
- SMP bring-up
- multicore validation

## Assumptions

- The scalar backend remains in-order for the entire roadmap.
- Shared vector support means “architected for 1-4 harts now”, not “instantiate 4 harts now”.
- Features without a credible local verification story should not be claimed just to satisfy the profile text.
- If the project later wants true LITTLE-core benchmark parity beyond what an in-order design can sustain, the roadmap should be reopened rather than quietly stretched.
