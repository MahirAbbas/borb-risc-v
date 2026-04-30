# borb Documentation

## What borb is

`borb` is a pipelined RISC-V CPU written in Scala with SpinalHDL. The active execution program now targets an RVA23S64-class scalar core with a shared RVV engine boundary. The current implemented base is a single-core in-order RV64GC-class machine with `M/S/U` privilege support, `Sv39`, architectural compliance via RISCOF, directed assembly testing, and benchmark-driven frontend/backend performance work.

## Local setup

- Work from the repo root: `/Users/mahir/fun/borb`
- The repo uses `scripts/workspace_env.sh` to keep SBT, Ivy, Coursier, and temp state inside `.cache/`.
- Most checked-in runner scripts source that environment automatically.

Basic compile:

```bash
sbt compile
```

Generate SoC Verilog:

```bash
sbt "runMain borb.SoC"
```

## Verification commands

Full architectural gate:

```bash
./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim
```

Directed assembly debug:

```bash
./run_directed.sh verif/directed/asm/<test>.S --march rv64gc_zicsr_zifencei --trace
```

Performance checks:

```bash
./run_coremark.sh
./run_directed.sh verif/directed/asm/frontend_branch_stress.S --march rv64gc_zicsr_zifencei --max-cycles 3000000 --report-perf
```

## Repo structure

- `src/main/`: CPU, SoC, frontend, backend, execute, decode, and vector boundary types.
- `verif/riscof/`: RISCOF integration, ISA declaration, simulator harness, and cycle budgets.
- `verif/directed/`: directed assembly tests and runners.
- `scripts/`: automation helpers, runners, failure report tooling, and performance helpers.
- `docs/`: supporting design and performance notes.
- `agent/`: execution instructions and milestone tracker for the current implementation program.

## Design file format overview

- The main SoC and CPU implementations are authored in Scala/SpinalHDL under `src/main/`.
- Architectural ISA intent for compliance is declared in `verif/riscof/borb/borb_isa.yaml`.

## Current status notes

- The active roadmap is `agent/plan3.md`. It supersedes the older RV64GC-only widening plan and separates claimable features, implemented-but-unclaimed features, and deferred backlog.
- Hard gates for every milestone are full RISCOF and CoreMark:
  - `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim`
  - `./run_coremark.sh`
- Linux, OpenSBI, firmware boot, guest bring-up, SMP, and platform validation are not active execution-path tasks for the current roadmap.
- The vector design target has a shared-engine boundary in `src/main/vector/VectorTypes.scala`: `ELEN=64`, `VLEN=128`, four hart contexts, and only hart `0` wired from the current single-core CPU. Vector architectural state, vector CSRs, and several focused execution slices are implemented and directed-tested, but full base `V`, vector profile support, and RVA23 vector companion claims are intentionally not declared.
- The canonical RISCOF ISA declaration now targets `RV64IMAFDCSUZicbom_Zicbop_Zicboz_Zicond_Zicntr_Zicsr_Zifencei_Zihintpause_Zihpm_Zimop_Zfa_Zfh_Zca_Zcb_Zcmop_Zba_Zbb_Zbs_Svnapot`. `Zfh` is declared as a local tooling workaround for the implemented `Zfhmin` conversion subset because the installed riscv-config version does not accept `Zfhmin` and requires `Zfh` for `Zfa`.
- The mandatory RVA23 bitmanip scalar slices `Zba`, `Zbb`, and `Zbs` are implemented in decode, issue classification, and the integer ALU. They are covered by `verif/directed/asm/rv64_zba_zbb_zbs_smoke.S` and a focused 40-test RISCOF subset.
- `Zicond` is implemented for `czero.eqz` and `czero.nez` in decode, issue classification, and the integer ALU. It is covered by `verif/directed/asm/rv64_zicond_smoke.S` and the local `czero.eqz-01.S` / `czero.nez-01.S` RISCOF tests.
- `Zimop` is implemented for the integer may-be-operation encodings as architectural zero-result ALU operations. It is covered by `verif/directed/asm/rv64_zimop_smoke.S` and the full local 40-test `mop.r.*` / `mop.rr.*` RISCOF subset.
- `Zcmop` is implemented in the compressed decompressor for `c.mop.{1,3,5,7,9,11,13,15}` as legal architectural NOPs. It is covered by `verif/directed/asm/rv64_zcmop_smoke.S` and the local 8-test `c.mop.*` RISCOF subset.
- `Zcb` is implemented in the compressed decompressor by expanding `c.lbu/c.lh/c.lhu/c.sb/c.sh`, `c.zext.*`, `c.sext.*`, `c.not`, and `c.mul` to existing scalar operations. It is covered by the local 12-test focused RISCOF subset for those compressed operations.
- `Zfa` is implemented for `fli.{s,d}`, minimum/maximum magnitude, quiet compares, round-to-integer, and `fcvtmod.w.d`. It is covered by the local focused 78-test RISCOF subset.
- `Zfhmin` half conversion support is implemented for `fcvt.{l,lu}.h` and `fcvt.h.{l,lu}`. Minimal `Zfh` halfword memory support is also implemented for `FLH` and `FSH`, including decode, LSU sizing/alignment, FP load/store data movement, and PMP/access-fault classification. Full half-precision arithmetic is still not claimed.
- Illegal-instruction trap handling now promotes unsupported 32-bit encodings fetched from RAM into architectural illegal traps instead of silently bubbling them, which keeps PMP `SKIP_MEPC` legal-overlap flows aligned with the reference model.
- `Zawrs` is implemented for `wrs.nto` and `wrs.sto` as legal serializing no-op wait hints. It is not declared in `borb_isa.yaml` because installed riscv-config 3.18.3 does not recognize `Zawrs` in its ISA regex.
- `Zihintpause` is implemented through the generic `FENCE` decode because `pause` is `fence w,0`.
- `verif/directed/asm/rv64_zawrs_zihintpause_smoke.S` covers `wrs.nto`, `wrs.sto`, and `pause`.
- `Zicntr` and `Zihpm` are now claimable: `cycle/time/instret/hpmcounter3-31` user shadows are exposed, `mcounteren/scounteren` gate lower-privilege reads, machine counters are writable via CSR-local offsets, and `mhpmevent3-31` preserve selector state for software probes. `time` is implemented as a local monotonic cycle-backed counter.
- `verif/directed/asm/rv64_zicntr_zihpm_counteren_smoke.S` covers machine counter writes, `mhpmevent3`, counter-enable CSRs, and disabled U-mode counter traps.
- `Zicbom`, `Zicbop`, and `Zicboz` are implemented. `cbo.clean`, `cbo.flush`, and `cbo.inval` are legal serializing architectural no-ops; `prefetch.i/r/w` remain legal through the existing OP-IMM/x0 hint path; `cbo.zero` aligns the base to a 64-byte block and emits eight 64-bit zero stores through the LSU. PMP/access-fault classification treats `cbo.zero` as a 64-byte store.
- `menvcfg` and `senvcfg` have local WARL storage for the implemented environment configuration bits used by cache-block operation tests, including `CBIE`, `CBCFE`, and `CBZE`.
- `verif/directed/asm/rv64_zicbo_memory_model_smoke.S` covers cache-block no-op behavior, prefetch hints, full 64-byte `cbo.zero` clearing from an unaligned base, aligned AMO behavior, and a misaligned load trap path.
- `Zic64b`, `Ziccif`, `Ziccrse`, `Ziccamoa`, `Zicclsm`, and `Za64rs` are documented as memory-side profile assumptions for the current single-core memory model. They are intentionally not declared in `borb_isa.yaml` because the installed riscv-config 3.18.3 validator rejects those extension names.
- The `Ss1p13` supervisor CSR floor is now locally defensible for the milestone scope: `stvec`/`mtvec` are direct-mode WARL CSRs, `stval` is writable and receives informative trap values, `scounteren` is writable from S-mode, and `sstatus.UXL` is hardwired to RV64. `verif/directed/asm/rv64_ss1p13_supervisor_csr_smoke.S` covers those behaviors through delegated S-mode traps.
- `Svnapot` is implemented for the currently verified Sv39 level-0 64 KiB NAPOT leaf encoding. The VM path accepts `N=1` with `PPN0[3]=1`, rejects other bit-63 encodings as reserved, and composes the physical address with `VA[15:12]` inside the 64 KiB region.
- `Svinval` encodings are legal: `sinval.vma`, `sfence.w.inval`, and `sfence.inval.ir` decode as serializing VM-fence operations with the same privilege and `mstatus.TVM` legality checks as `sfence.vma`. `verif/directed/asm/rv64_svinval_smoke.S` covers those encodings.
- `Svade` behavior is fault-on-clear-A/D in the current VM permission path: translations require `A=1`, and stores require `D=1`. This remains covered by the active Sv39 RISCOF A/D tests.
- `Ssccptr` is covered by the current explicit page-table access path and PMP interaction checks; the active `vm_pmp_on_pte` RISCOF tests remain green.
- `Svpbmt` is not declared because installed riscv-config 3.18.3 does not accept the extension name. Current PTE validation keeps PBMT bits reserved, which preserves the active `vm_reserved_svpbmt_S_mode.S` compliance behavior until the local tooling can claim `Svpbmt`.
- `Sstc` CSR/state behavior is implemented for the local architectural floor: `stimecmp` is writable at CSR `0x14d`, `mip`/`sip` derive STIP from the monotonic cycle-backed `time` source versus `stimecmp`, and `mie`/`mideleg` have architectural storage. This milestone does not add a platform interrupt controller.
- `Sscofpmf` CSR/state behavior is implemented for `hpmcounter3-31`: `mhpmevent[63]` latches architectural counter overflow, `scountovf` is readable at CSR `0xda0`, `mip`/`sip` derive LCOFIP in bit 13 while any overflow latch is set, and `mhpmevent` bits 62/61/60 inhibit counting in M/S/U mode respectively.
- `Sstc` and `Sscofpmf` are not declared in `borb_isa.yaml` because installed riscv-config 3.18.3 does not accept those extension names. They are covered by `verif/directed/asm/rv64_sstc_sscofpmf_smoke.S` and the full scalar gates.
- The data side now has a single-core L1D floor in `src/main/execute/DCache.scala`: 32 KiB, 4-way, 64-byte lines, write-back, write-allocate, dirty victim writeback, line refill, local load-hit response, and store-hit dirty-line updates behind the existing LSU `DataBus`.
- The L1D currently keeps bounded one-miss-at-a-time refill/writeback concurrency to preserve the in-order LSU contract and the existing single-beat SoC RAM/AXI behavior. This is an application-core-style data-cache floor, not a multi-miss non-blocking cache yet.
- `verif/directed/asm/rv64_l1d_writeback_smoke.S` covers 4-way conflict filling, dirty eviction, writeback, refill, and reload visibility. `verif/directed/asm/rv64_zicbo_memory_model_smoke.S` remains the broader memory/cache-block smoke.
- The simulator's dumpable RAM mirror now receives computed AMO store data from the LSU debug/RVFI payload, and committed `cbo.zero` instructions zero the full architectural 64-byte block in the mirror. This keeps RISCOF signatures accurate when dirty cached signature lines have not yet been written back to backing RAM.
- The instruction side now has a 32 KiB L1I floor through `FrontendConfig`: 2 banks, 64 sets, 4 ways, and 64-byte lines. `FrontendBundleBuilder` selects any fetch block within the larger line correctly; this fixed the original two-block assumption that broke VM tests after the line-size increase.
- The scalar frontend now uses the Milestone 20 predictor sizing floor: FTQ depth 32, request queue depth 16, RAS depth 48, 512-entry gshare with 48-bit history, 32-entry loop predictor, 16-entry nano-BTB, 128-entry 4-way FTB, and 64-entry 4-way indirect predictor. Predictor training remains enabled.
- Speculative frontend predicted redirects are currently disabled in the default supported config, and outstanding fetch misses are held at the proven 2-entry depth. The attempted predicted-redirect plus 4-outstanding-miss configuration exposed stale-epoch branch/PMP/VM stalls under full RISCOF, so that performance work is deferred until the redirect/refill interaction is hardened.
- L1I refill matching accepts responses that match the tracked miss slot, line address, and request epoch without additionally requiring the current global epoch. This allows safe late fills across redirect stress while still rejecting responses that do not match a live miss.
- The VM path now has explicit TLB state in `TrapCsrBackend`: 32 ITLB entries, 32 DTLB entries, and 128 shared second-level entries. Entries are tagged by VPN, `satp` root, privilege, access type, and SUM/MXR state, and fills are gated by real frontend misses or real data accesses.
- TLB invalidation covers `satp` writes, tracked page-table writes, `sfence.vma`/Svinval instructions, `sstatus`/`mstatus` writes, and PMP configuration/address writes. The Sv39 walker is still the existing RISCOF-oriented shadow-backed access path rather than a standalone memory-walking FSM.
- The full RISCOF filter excludes `vm_reserved_svnapot_S_mode.S` after `Svnapot` is declared because that test is explicitly for implementations that do not support Svnapot. The wrapper removes stale excluded-test artifacts before failure report generation.
- The local Spike RISCOF plugin now mirrors declared `Zba/Zbb/Zbs/Zicbom/Zicbop/Zicboz/Zicond/Zicntr/Zihpm/Zimop/Zfa/Zfh/Zihintpause/Zca/Zcb/Zcmop/Svnapot` extensions into the reference `--isa` string so extension reference signatures are meaningful.
- The borb RISCOF model header now places model regstate data after `end_signature`, matching the Spike reference layout. This avoids false mismatches for tests such as `Zcmop` that legally store signature pointer values.
- After final Milestone 12 hardening, the full active RISCOF gate remains green at `1539/1539` with zero generated failure reports, and CoreMark still exits with tohost `0x1`.
- After Milestone 13 counter hardening, the full active RISCOF gate remains green at `1539/1539`, CoreMark exits with tohost `0x1`, and `./run_coremark.sh --profile` emits a populated simulator perf report.
- After Milestone 14 cache-block hardening, the full active RISCOF gate remains green at `1539/1539`, CoreMark exits with tohost `0x1`, and the cache-block directed smoke passes.
- After Milestone 15 supervisor CSR hardening, the full active RISCOF gate remains green at `1539/1539`, CoreMark exits with tohost `0x1`, and the supervisor CSR directed smoke passes.
- After Milestone 16 Sv39 companion hardening, the full active RISCOF gate remains green at `1538/1538` with zero generated failure reports, CoreMark exits with tohost `0x1`, and the Svinval directed smoke passes.
- After Milestone 17 timer/counter-overflow hardening, the full active RISCOF gate remains green at `1538/1538` with zero generated failure reports, CoreMark exits with tohost `0x1`, and the Sstc/Sscofpmf directed smoke passes.
- After Milestone 18 L1D bring-up, the full active RISCOF gate remains green at `1538/1538` with zero generated failure reports, CoreMark exits with tohost `0x1`, and the L1D writeback plus cache-block/memory-model directed smokes pass.
- After Milestone 19 L1I/TLB bring-up, the full active RISCOF gate remains green at `1538/1538` with zero generated failure reports, CoreMark exits with tohost `0x1`, and the focused VM subset for A/D, SATP, MXR, and PMP-on-PTE behavior passes.
- After Milestone 20 frontend sizing, the full active RISCOF gate remains green at `1538/1538` with zero generated failure reports, CoreMark exits with tohost `0x1`, and the focused branch/C subset passes. CoreMark profile on the supported conservative frontend is `323,354` cycles, `4.065096` CPI, `0.245997` IPC, and `3.0925858347198427` CoreMark/MHz.
- Milestone 21 adds the dormant shared-vector architecture boundary. `VectorCommand`, `VectorResponse`, `VectorException`, `VectorHartContext`, and `VectorMemReq`/`VectorMemResp` are defined as the scalar/vector contract, and execute-stage OP-V packets are presented to the dormant hart-0 command stream without changing current illegal-instruction trap behavior. The Milestone 21 hard gates passed: full RISCOF `1538/1538` with zero generated failure reports, and CoreMark exited with tohost `0x1`.
- Milestone 22 adds vector CSR and state plumbing without claiming vector execution. `vsetvli`, `vsetivli`, and `vsetvl` now update `vl/vtype/vstart` for the fixed `VLEN=128`, `ELEN=64` floor, mark `mstatus.VS` dirty, and report unsupported `vtype` encodings with `vill` and `vl=0`. `vstart`, `vxsat`, `vxrm`, and `vcsr` are writable CSRs; `vl`, `vtype`, and `vlenb` are read-only through the CSR legality path. The dormant shared engine contains 32 `VLEN=128` vector registers per hart context, with only hart `0` wired to the scalar command boundary.
- `verif/directed/asm/rv64_vector_state_smoke.S` covers vector CSR read/write behavior, `vlenb=16`, all three vector config instructions, `vstart` reset on config writes, `mstatus.VS` dirty visibility through `sstatus`, and `vill` handling for unsupported `vtype`. After Milestone 22, full RISCOF remains green at `1538/1538` with zero generated failure reports and CoreMark exits with tohost `0x1`.
- Milestone 23 adds the first shared-engine integer execution slice for hart `0`: `vmv.v.i`, `vadd.vi`, `vadd.vv`, and `vmv.x.s` for the current `e32,m1`, `VLEN=128` coverage. Vector register writes happen inside the shared vector engine, and `vmv.x.s` returns its scalar result through the scalar writeback boundary. This is not a full base-`V` claim; vector memory, reductions, permutes, broader masks, FP, and complete element-width/LMUL coverage remain future milestones.
- `verif/directed/asm/rv64_vector_integer_smoke.S` covers vector immediate move, vector add immediate, vector add vector, and vector-to-scalar movement. After Milestone 23, both vector directed smokes pass, full RISCOF remains green at `1538/1538` with zero generated failure reports, and CoreMark exits with tohost `0x1`.
- Milestone 24 adds the first vector LSU slice for hart `0`: unit-stride `vle32.v` and `vse32.v` for the current `e32,m1`, `VLEN=128` coverage. Vector memory requests arbitrate onto the existing scalar `DataBus`/`DataSideCache` hierarchy and preserve scalar LSU responses with a dedicated vector bus ID.
- Vector memory execution now consumes `vstart`, preserves inactive earlier load lanes, clears `vstart` on normal completion, and reports precise misaligned vector-memory traps for the current slice. On a vector memory misalignment, `vstart` is written with the faulting element and `mtval/stval` receives the faulting element address.
- `verif/directed/asm/rv64_vector_lsu_smoke.S` covers vector load/add/store correctness. `verif/directed/asm/rv64_vector_lsu_restart_smoke.S` covers nonzero-`vstart` restart behavior and an injected misaligned vector-load trap. After Milestone 24, the vector state, integer, LSU, and restart directed smokes pass; full RISCOF remains green at `1538/1538` with zero generated failure reports; and CoreMark exits with tohost `0x1`.
- Milestone 25 adds the first cross-lane RVV execution slice for hart `0`: `vslideup.vi`, `vslidedown.vi`, `vrgather.vi`, and `vredsum.vs` for the current `e32,m1`, `VLEN=128` coverage. The reduction writes element 0 and leaves the remaining destination elements preserved for the focused slice.
- `verif/directed/asm/rv64_vector_permute_reduce_smoke.S` covers slide-up, slide-down, gather, integer reduction, scalar extraction through `vmv.x.s`, and vector storeback. After Milestone 25, the new permute/reduction smoke plus the vector LSU and integer smokes pass; full RISCOF remains green at `1538/1538` with zero generated failure reports; and CoreMark exits with tohost `0x1`.
- Milestone 26 adds the first vector FP and `Zvfhmin` execution slice for hart `0`. `vfadd.vv` and `vfsub.vv` execute on active `e32,m1` lanes using the existing scalar single-precision FP add/sub helper; `vfwcvt.f.f.v` and `vfncvt.f.f.w` provide focused half/single conversion coverage for `e16,m1`. Vector FP data lives only in the shared vector register file and does not alias scalar FP architectural state.
- `verif/directed/asm/rv64_vector_fp_zvfhmin_smoke.S` covers vector single-precision add/sub, half-to-single widening conversion, single-to-half narrowing conversion, and storeback. After Milestone 26, the vector FP/`Zvfhmin`, integer, LSU, permute/reduction, and state directed smokes pass; full RISCOF remains green at `1538/1538` with zero generated failure reports; and CoreMark exits with tohost `0x1`.
- Milestone 27 adds a focused hart-0 `Zvbb` slice for the current `e32,m1`, `VLEN=128` vector floor: `vandn.vv`, `vbrev8.v`, `vrev8.v`, `vclz.v`, `vcpop.v`, and `vror.vi`. `Zvkt` is explicitly left unclaimed because there is not yet a verified constant-time or constrained-latency vector timing contract.
- `verif/directed/asm/rv64_vector_zvbb_smoke.S` covers vector load, the implemented `Zvbb` operation chain, vector storeback, and unsigned 32-bit result checks. After Milestone 27, the `Zvbb`, vector FP/`Zvfhmin`, permute/reduction, LSU, and integer directed smokes pass; full RISCOF remains green at `1538/1538` with zero generated failure reports; and CoreMark exits with tohost `0x1`.
- Milestone 28 re-audits the claimable surface against tested reality. The canonical ISA declaration remains scalar/profile-support focused and intentionally does not claim full base `V`, vector profile support, `Zvbb`, or `Zvkt`; the implemented vector work is documented as directed-tested slices only.
- Implemented-but-unclaimed surfaces after Milestone 28 are deliberate: `Zawrs`, `Sstc`, `Sscofpmf`, `Zic64b`, `Ziccif`, `Ziccrse`, `Ziccamoa`, `Zicclsm`, `Za64rs`, and `Svpbmt` remain out of `borb_isa.yaml` because of local validator limits and/or incomplete claimability. `Svpbmt` keeps PBMT bits reserved, and `Zvkt` remains deferred.
- `CpuConfig.dExtensionEnabled` is now enabled by default, and the repo has a minimal passing RV64D smoke covering `misa.D`, FP register moves, `FCLASS.D`, `FSGNJ.D`, and `FLD/FSD`.
- The RV64D conversion slice is now passing in RISCOF for `FCVT.{W,WU,L,LU}.D`, `FCVT.D.{W,WU,L,LU}`, `FCVT.S.D`, and `FCVT.D.S`.
- The RV64D min/max slice is now passing in RISCOF for `FMIN.D` and `FMAX.D`.
- The RV64D arithmetic slice is now passing in RISCOF for `FADD.D`, `FSUB.D`, `FMUL.D`, `FDIV.D`, and `FSQRT.D`.
- The RV64D fused multiply-add slice is now passing in RISCOF for `FMADD.D`, `FMSUB.D`, `FNMSUB.D`, and `FNMADD.D`.
- The local RISCOF simulator now mirrors retired stores into the dumpable memory image before writing signature files, which fixes end-of-test signature truncation when a committed store had not yet become visible in the raw RAM dump.
- The SoC shared-memory interconnect now uses a local two-master AXI shared arbiter in `src/main/SoC.scala`, replacing the library `Axi4SharedArbiter` after a real lockup bug in the generated `StreamArbiter` deadlocked store and compressed/PMP regressions.
- The current supported CoreMark baseline is `323,354` cycles / `3.0925858347198427 CoreMark/MHz` with `4.065096` CPI and `0.245997` IPC on `./run_coremark.sh --profile`.
- Milestone 29 did not retain a performance RTL/config change. Early local direct redirects improved CoreMark to `317,222` cycles and a slot-0-only variant improved it to `322,151` cycles, but both failed `dual_issue_mixed_classes_smoke.S`; raising L1I outstanding misses to `4` gave no CoreMark improvement and failed the same smoke.
- Final Milestone 29 bottlenecks are dominated by `196,051` fetch stalls, `73,991` backend stalls, `34,350` commit stalls, `48,227` `frontend_wait_next_beat` events, `6,328` flushes, `0` predicted redirects, `2,321` `muldiv_busy` cycles, and `9,636` `lsu_replay_or_wait` cycles.
- Historical frontend-on/off comparisons still exist in `docs/PERFORMANCE_HISTORY.md`, but borb no longer maintains a supported frontend-disabled mode and the old `scripts/frontend_perf_ab.py` / `scripts/frontend_branch_ab.py` wrappers are now intentionally obsolete.
- The repo still includes a branch-heavy perf microbenchmark at `verif/directed/asm/frontend_branch_stress.S`. On the current supported conservative frontend it requires a `3,000,000` cycle cap and runs in `1,500,169` cycles with `6.000004` CPI, `0.166667` IPC, `0` predicted redirects, `100,006` flushes, and `1,500,154` successful cross-bank dual fetches. The older speculative-redirect result was faster but is not the supported default because it failed full RISCOF.
- `scripts/directed_asm_runner.py` can now emit simulator perf counters with `--report-perf`, which makes directed assembly useful for both bug isolation and lightweight performance A/B checks.
- Backend contracts are now lane-aware even with width-off scalar execution: fetch preserves bundle slot count, pipeline stages carry `LANE_ID`/`LANE_MASK`, redirect squash priority uses bundle-sequence plus slot index, and shared backend packet types (`BackendPipe`, `PipelineSlot`, `RetirePacket`) define the lane-native payload/retire boundary for the Plan 3 widening work.
- Milestone 30 adds the first Plan 3 lane-native backend skeleton: `PipelineSlot(config)` replaces the bespoke lane-1 stage bundle, `BackendPipe` records selected pipe ownership, and `RetirePacket(config)` defines the future ordered-retire handoff. Lane 0 still uses the existing scalar `StageCtrlPipeline`; the restricted lane-1 path is now hosted on the shared payload shape.
- The core now has an asymmetric in-order 2-wide issue step: lane 0 remains the full scalar path, and lane 1 can pair restricted integer ALU work plus conditional branches when the bundle passes deterministic static and dynamic hazard screens.
- Fetch now supports slot-1 preview plus scalar skip/defer controls so same-bundle pairing can be decided without breaking the existing scalarized frontend contracts.
- The integer backend now exposes shared decode/ALU/branch helpers and widened integer register-file ports so lane 1 reuses the same semantics as lane 0 rather than implementing a divergent second path.
- Pairing is intentionally conservative today: jumps do not pair on lane 1, same-cycle RAW/WAW hazards reject pairing immediately, and older in-flight integer busy bits also force scalar fallback.
- The repo now includes `verif/directed/asm/dual_issue_integer_smoke.S` to validate same-cycle integer pairing, slot-1 conditional-branch execution, and paired writeback behavior.
- Same-bundle pairing has mixed older-lane support in the implementation, but `verif/directed/asm/dual_issue_mixed_classes_smoke.S` is a known non-hard-gate residual failure in the final baseline; the narrower `dual_issue_integer_smoke.S` passes.
- The full active RISCOF gate stayed green after asymmetric issue bring-up, including recovery from an early FP regression where over-broad slot-1 deferral initially timed out `fadd_b12-01.S`.
- A checked-in Dhrystone harness does not currently exist under `verif/benchmarks/`, so CoreMark plus directed integer-heavy perf runs are the current measured performance sources until that benchmark prerequisite is added.
- Final validation after Milestone 29 is green: `./run_coremark.sh --profile` exits with tohost `0x1`, and `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` passes `1538/1538` with zero generated failure reports.
- Final validation after Milestone 30 is green: `./run_directed.sh verif/directed/asm/dual_issue_integer_smoke.S --march rv64gc_zicsr_zifencei` passes, `./run_coremark.sh --profile` exits with tohost `0x1` at `323,354` cycles / `3.0925858347198427 CoreMark/MHz`, and `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` passes `1540/1540` with zero generated failure reports.
- `riscv-formal` work exists in the repo, but it is intentionally ignored by the current execution plan.
- Tenstorrent smoke tooling exists in the repo, but it is intentionally ignored by the current execution plan.
- RISCOF is the active architectural gate right now; directed tests are mainly for fast isolation when a focused repro is useful.
- Milestone tracking and implementation notes live in `agent/plan3.md`.

## Troubleshooting

- If SBT writes outside the repo or tmp handling gets noisy, rerun through the checked-in scripts so `scripts/workspace_env.sh` sets the local cache directories.
- If RISCOF tests fail, use `scripts/riscv_failure_report.py` through `./debug_failures.sh --riscof` to get the first signature divergence before changing RTL.
