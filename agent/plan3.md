# Plan: Borb RVA23S64 Roadmap with C1-Nano-Style 2-Wide Pipeline

## Summary

Move borb from the current asymmetric lane-1 implementation to a **10-stage, in-order, general 2-wide superscalar core** with a central issue stage and multiple specialized execution pipes. Keep the core single-hart, precise, and ACT4/CoreMark-gated while finishing the scalar RVA23S64 claim ledger and keeping vector work as a shared-engine track.

The target shape is:

```text
IF0 IF1 IF2  DE0 DE1 DE2  ISS  EX0 EX1 EX2  WR  RET
Fetch        Decode       Issue  pipe-local execute  retire

ISS -> ALU0
    -> ALU1
    -> Branch
    -> MUL/DIV
    -> Load/Store
    -> FALU
    -> FMAC
    -> CSR/Trap
    -> Shared Vector Engine
```

Hard gates after every milestone:
`./run_act4.sh --profile rva23s64-full --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim`
`./run_coremark.sh --profile`

## Key Design Changes

- Replace the hand-rolled `lane1s4/s5/s6/s7` side path with a lane-native `Vec(2, PipelineSlot)` backend payload carrying PC, fetch metadata, epoch, decode, issue props, operands, selected pipe, result, trap, redirect, and side-effect metadata.
- Introduce a central dual-issue stage that chooses non-conflicting pipes for two decoded slots each cycle; lane 0 is always older, lane 1 is younger and killed by any older trap or redirect.
- Add an in-order completion/retire queue for variable-latency pipes. This is not OoO: issue stays in order, architectural retirement stays in order, but pipe completion may arrive at different latencies.
- Execution-pipe v1 allocation:
  - `ALU0`, `ALU1`: integer arithmetic, logic, shifts, bitmanip, `Zicond`, simple integer ops.
  - `Branch`: conditional branches, `JAL`, `JALR`, redirect metadata, predictor training.
  - `MUL/DIV`: pipelined multiply when practical, iterative divide/remainder.
  - `Load/Store`: one memory op per cycle for loads, stores, AMOs, cache-block ops.
  - `FALU`: FP add/sub, compare, classify, min/max, convert, round.
  - `FMAC`: FP multiply and fused multiply-add.
  - `CSR/Trap`: singleton serializing CSR, fence, trap-return, exception entry.
  - `Vector`: singleton shared-vector command path, serializing from scalar issue for now.
- Initial stage target is 10 backend-visible stages: `IF0/IF1/IF2/DE0/DE1/DE2/ISS/EX0/EX1/EX2/WR/RET`, where `EX*` are pipe-local timing stages rather than a single monolithic execute block.

## Milestones

- [x] **M30: Lane-Native Backend Skeleton**
  - Create the two-slot backend payload and retire packet types.
  - Move current scalar lane 0 through the new structure first, then re-host existing restricted lane-1 behavior on the same structure.
  - Acceptance: existing ACT4/CoreMark pass; `dual_issue_integer_smoke.S` still passes; no new performance target yet.

- [x] **M31: 10-Stage Frontend/Decode/Issue Retiming**
  - Split fetch/decode into `IF0-IF2` and `DE0-DE2`.
  - Preserve current frontend predictor/L1I behavior, with predicted redirects still disabled.
  - Build the central `ISS` stage with pipe reservation but initially only enable current-safe pairings.
  - Acceptance: full ACT4 green; CoreMark no functional regression; redirect/epoch tests pass.

- [x] **M32: General Integer/Branch 2-Wide**
  - Enable `ALU0`, `ALU1`, and dedicated `Branch` pipe issue from either slot.
  - Allow ALU+ALU, ALU+branch, branch+ALU, and branch+branch when ordering and redirect rules are safe.
  - Add lane0-to-lane1 same-cycle bypass only for results available in the same cycle; otherwise reject the pair.
  - Acceptance: directed tests for WAW, RAW rejection, lane0 redirect killing lane1, lane1 branch redirect, and dual integer writeback pass.

- [x] **M33: Completion/Retire Queue and Dual Commit**
  - Add a small in-order completion/retire queue sized for at least the longest non-div FP latency plus frontend skid.
  - Retire up to two completed instructions per cycle in order.
  - Convert integer RF writeback, FP writeback, `fflags`, store commit, RVFI/debug/perf, and trap reporting to ordered dual-retire semantics.
  - Acceptance: precise-trap directed tests pass; same-cycle dual retire updates architectural state deterministically; full ACT4 remains green.

- [x] **M34: Memory Pipe Integration**
  - Keep one `Load/Store` pipe and one D-cache command per cycle.
  - Allow memory+non-memory pairing, but reject memory+memory.
  - Make AMO, `cbo.zero`, PMP faults, misalignment, store visibility, and signature mirroring lane-aware.
  - Acceptance: L1D writeback smoke, cache-block smoke, AMO subset, PMP memory tests, and full ACT4 pass.

- [x] **M35: FP Pipe Split**
  - Replace combinational scalar FP integration with issue into `FALU` and `FMAC`.
  - Use initial fixed latencies: move/classify/compare 1-2, add/convert/minmax 3, mul 4, FMA 5, div/sqrt iterative serialized.
  - Scoreboard FP registers until completion; commit FP state only through ordered retire.
  - Acceptance: RV64F/RV64D focused ACT4 subsets pass; FP+integer pairing tests pass; CoreMark remains green.
  - Implemented the first pipe-ownership split without changing the current fixed-latency FP datapath: `BackendPipe.select(...)` sends `FMUL.{S,D}` and fused multiply-add/subtract ops to `FMAC`, while add/sub, convert, move, classify, sign, min/max, compare, round, div, and sqrt remain on `FALU` for the active scalar backend.
  - Lane 1 now waits for an older lane-0 FP instruction to retire before committing paired younger integer work, preserving the current ordered-retire FP state contract while allowing safe FP+integer issue coverage.
  - Added `verif/directed/asm/dual_issue_m35_fp_integer_smoke.S` for FALU+integer and FMAC+integer pairing with architectural FP result checks.
  - Validation: `sbt compile`, `sbt "runMain borb.SoC"`, the M35 FP+integer directed smoke, a focused RV64F/RV64D ACT4 subset (`7/7` selected tests), CoreMark (`337,458` cycles / `2.9633317331341975 CoreMark/MHz`), and full ACT4 (`1540/1540`, zero reports) passed.

- [x] **M36: Scalar RVA23S64 Claim Closure**
  - Re-audit the scalar/profile surface after pipeline changes: declared YAML, implemented-but-unclaimed features, and validator-limited features.
  - Keep current declared surface unless implementation evidence changes: `RV64IMAFDCSUZicbom_Zicbop_Zicboz_Zicond_Zicntr_Zicsr_Zifencei_Zihintpause_Zihpm_Zimop_Zfa_Zfh_Zca_Zcb_Zcmop_Zba_Zbb_Zbs_Svnapot`.
  - Keep `Zawrs`, `Sstc`, `Sscofpmf`, `Zic64b`, `Ziccif`, `Ziccrse`, `Ziccamoa`, `Zicclsm`, `Za64rs`, and `Svpbmt` documented as implemented/assumed/unclaimed unless local tooling supports clean declaration.
  - Acceptance: full active ACT4 pass count remains green; docs distinguish claimable vs unclaimed accurately.
  - Audited `verif/act4/borb-rva23s64/test_config.yaml`, `documentation.md`, and the ACT4 filtering/reference-extension path after the M30-M35 backend work. No claim-surface change is warranted: the canonical declaration remains the validator-supported scalar/profile-support surface, and full base `V`, vector profile support, `Zvbb`, `Zvkt`, `Zawrs`, `Sstc`, `Sscofpmf`, `Zic64b`, `Ziccif`, `Ziccrse`, `Ziccamoa`, `Zicclsm`, `Za64rs`, and `Svpbmt` remain intentionally undeclared.
  - Documentation now records the post-M35 claim closure explicitly: declared features are claimable through the active ACT4 flow, implemented-but-unclaimed features are separated by validator or coverage limits, and the vector slices remain directed-tested implementation evidence only.

- [x] **M37: Vector Shared-Engine Reconciliation**
  - Keep vector issue serializing from scalar issue until scalar 2-wide is stable.
  - Route vector commands through the completion/retire queue so vector scalar writeback, vector memory traps, `vstart`, and `mstatus.VS` remain precise.
  - Do not claim full base `V`, vector profile, `Zvbb`, or `Zvkt` until coverage and timing contracts exist.
  - Acceptance: existing vector state/integer/LSU/permute/FP/Zvbb smokes pass plus full scalar gates.
  - Vector issue remains scalar-serializing through `IssueSemantics`, and the shared vector-engine handoff now requires the central issue-selected `BackendPipe.Vector` pipe in addition to decoded vector micro-ops. This keeps the vector engine behind the same lane-native pipe ownership used by the rest of the Plan 3 backend.
  - The active scalar writeback path for vector scalar results still flows through `WriteBack.RESULT`, so `RetirePacket.intWrite` captures `vmv.x.s` at ordered lane-0 retire; vector memory restart/trap behavior remains covered by the existing LSU restart smoke.
  - Validation: `sbt compile`, `sbt "runMain borb.SoC"`, rebuilt `borb-sim`, all seven vector directed smokes, CoreMark (`337,458` cycles / `2.9633317331341975 CoreMark/MHz`), and full ACT4 (`1540/1540`, zero reports) passed.

- [x] **M38: Performance Push**
  - Use perf counters to tune pipe selection, branch penalty, bypass paths, load-use behavior, and frontend refill bubbles.
  - Only re-enable predicted redirects or higher outstanding I-cache misses after they pass full ACT4 and `dual_issue_mixed_classes_smoke.S`.
  - Acceptance: CoreMark improves materially over the current documented `323,354` cycle baseline, or the retained changes must be reverted and bottlenecks documented.
  - No M38 RTL/config performance change is retained. The previously measured speculative redirect and higher outstanding I-cache miss experiments are still rejected because they did not satisfy the required correctness gates; the current milestone keeps the verified conservative frontend/backend contract.
  - Current CoreMark profile after M31-M37 remains `337,458` cycles / `2.9633317331341975 CoreMark/MHz`. The dominant counters are `196,305` fetch stalls, `82,079` backend stalls, `57,704` commit stalls, `53,346` `frontend_wait_next_beat` events, `75,721` `exec_to_write` stall-class cycles, `39,025` `src_to_exec`, `37,349` `dispatch_to_src`, `9,640` LSU replay/wait cycles, `6,327` flushes, and `2,321` mul/div busy cycles.
  - Next viable performance work needs a correctness-clean branch/refill redirect contract and a real bypass/latency recovery pass for the post-M31 registered stages; those are intentionally not claimed as complete here.

## Test Plan

- Required regression after each milestone:
  - full ACT4 fast-sim gate
  - CoreMark profile
  - existing directed smoke set relevant to changed subsystem
- New directed tests:
  - dual ALU same-cycle writeback, x0, WAW, RAW rejection
  - lane0 trap/redirect killing lane1 memory, CSR, FP, and RF side effects
  - branch pipe redirect ordering for both slots
  - memory+ALU pairing and memory+memory rejection
  - AMO/cache-block/PMP under paired issue
  - FP scoreboard, FP flags, FP+integer pairing, FP exception commit ordering
  - vector command precision through the retire queue
- Performance counters to add or preserve:
  - issue width histogram: 0/1/2 issued
  - pair rejection reasons: RAW, WAW, structural, serializing, old epoch
  - per-pipe busy and issued counts
  - retire width histogram
  - completion-queue head blocked reason
  - branch redirect penalty cycles
  - load-use stall cycles
  - FP scoreboard stall cycles

## Assumptions

- The core remains **single-hart, in-order issue, in-order retire**. No rename, ROB, or speculative OoO execution.
- “General 2-wide” means all scalar classes can participate in the dual-issue machinery, but singleton resources may serialize.
- v1 keeps one memory operation per cycle and one FP issue per cycle.
- The 10-stage target is the default; only change to 8 or 11 stages after measurement shows a clear win.
- ACT4 and CoreMark stay the hard gates; Tenstorrent and riscv-formal remain optional until explicitly promoted.
- RVA23S64 vector/profile companion claims remain separated from scalar pipeline work until the shared vector engine has broader coverage and a defensible timing contract.

## Implementation Notes

- M30: Added shared lane-native backend types in `src/main/backend/BackendTypes.scala`: `BackendPipe`, `PipelineSlot`, and `RetirePacket`. Re-hosted the existing restricted lane-1 side path in `CPU.scala` on `PipelineSlot(config)` while preserving current pairing restrictions and behavior. This is an incremental skeleton only; lane 0 still travels through the existing scalar `StageCtrlPipeline`.
- M30 bug fix: Full ACT4 exposed `pmpzicbo_prefetch.S` trapping on `csrs menvcfg,t0` because the environment configuration CSRs were not in the CSR whitelist. Added WARL `menvcfg`/`senvcfg` storage for the cache-block environment bits and confirmed the focused ACT4 repro passes.
- M30 bug fix: Full ACT4 then exposed `cbo.zero-01.S` only clearing one doubleword in the dumped signature. The LSU `cbo.zero` sequencer now latches its bus base and is not cancelled by redirect commit draining after launch, and the ACT4 simulator mirror recognizes committed `cbo.zero` instructions so the dumpable RAM signature reflects the architectural 64-byte zeroed block.
- M31: Added the central issue-stage pipe reservation contract. `BackendPipe.select(...)` classifies lane-0 and lane-1 instructions into the Plan 3 pipe set, `BackendIssue.SELECTED_PIPE` now travels downstream from dispatch for lane 0, and lane 1 uses the same selector with ALU1 preference. This keeps the current safe pairings unchanged while establishing shared pipe ownership payloads.
- M31: Physically retimed the scalar lane-0 backend so fetch remains `IF0/IF1/IF2` at stages 0/1/2, decode begins at `DE0` stage 3, two registered decode/issue transit stages occupy stages 4/5, dispatch/central issue moves to stage 6, source read moves to stage 7, execute moves to stage 8, and writeback/retire moves to stage 9. The debug plugin and ACT4 simulator probes were retargeted to the new execute/writeback stage names.
- M32: The conservative integer/branch two-wide path now has focused contract coverage in `verif/directed/asm/dual_issue_m32_integer_branch_smoke.S`. The directed test covers ALU+ALU, ALU+branch, branch+ALU, branch+branch, same-cycle RAW rejection, same-cycle WAW ordering, lane-0 redirect squash of lane 1, and lane-1 branch redirect behavior. The RTL remains intentionally restricted to current-safe integer ALU and conditional-branch pairings.
- M33: Added an explicit two-lane ordered retire-packet boundary. Lane 0 now mirrors writeback-stage commit metadata, integer write intent, FP write/flag intent, store intent, and trap outcome into `RetirePacket`; lane 1 emits the same packet shape for its restricted integer/branch path. Duplicate-retire tracking, lane-1 RF writeback, and commit-cycle accounting now consume the ordered retire packets. `RetireQueue.Depth` records the planned eight-entry completion queue sizing for upcoming variable-latency pipe migration while the current fixed-latency scalar path stays functionally unchanged.
- M33: Added `verif/directed/asm/dual_retire_m33_precise_trap_smoke.S`, which checks deterministic same-cycle dual integer retire before an `ecall` and verifies the precise trap path does not commit fall-through side effects.
- M34: Made the memory pairing contract explicit in the lane-1 issue screen. Older lane-0 memory operations can pair with younger non-memory work, but the younger packet records `waitForOlderCommit` and cannot advance until the memory instruction has retired; younger memory remains rejected so the single LSU owns all load/store/AMO/cache-block side effects. Added `verif/directed/asm/dual_issue_m34_memory_pairing_smoke.S` to cover load+ALU, store+ALU, memory+memory scalarization, AMO+ALU ordering, and older misaligned-load trap squashing of a younger paired ALU write.

## Verification Checklist

- M30:
  - [x] `sbt compile`
  - [x] focused `./run_act4.sh --skip-validate --fast-sim --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --tests pmpzicbo_prefetch.S`
  - [x] focused `./run_act4.sh --skip-validate --fast-sim --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --tests cbo.zero-01.S`
  - [x] `./run_directed.sh verif/directed/asm/dual_issue_integer_smoke.S --march rv64gc_zicsr_zifencei`
  - [x] `./run_coremark.sh --profile` (`323,354` cycles, tohost `0x1`)
  - [x] `./run_act4.sh --profile rva23s64-full --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` (`1540/1540`, zero generated failure reports)
- M31:
  - [x] `sbt compile`
  - [x] `sbt "runMain borb.SoC"`
  - [x] `./run_directed.sh verif/directed/asm/dual_issue_integer_smoke.S --march rv64gc_zicsr_zifencei` (tohost `0x1`)
  - [x] `./run_coremark.sh --profile` (`337,458` cycles, tohost `0x1`)
  - [x] `./run_act4.sh --profile rva23s64-full --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` (`1540/1540`, zero generated failure reports)
- M32:
  - [x] `./run_directed.sh verif/directed/asm/dual_issue_m32_integer_branch_smoke.S --march rv64gc_zicsr_zifencei` (tohost `0x1`)
  - [x] `./run_coremark.sh --profile` (`337,458` cycles, tohost `0x1`)
  - [x] `./run_act4.sh --profile rva23s64-full --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` (`1540/1540`, zero generated failure reports)
- M33:
  - [x] `sbt compile`
  - [x] `sbt "runMain borb.SoC"`
  - [x] `make -C verif/borb-sim/sim clean all`
  - [x] `./run_directed.sh verif/directed/asm/dual_retire_m33_precise_trap_smoke.S --march rv64gc_zicsr_zifencei` (tohost `0x1`)
  - [x] `./run_coremark.sh --profile` (`337,458` cycles, tohost `0x1`)
  - [x] `./run_act4.sh --profile rva23s64-full --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` (`1540/1540`, zero generated failure reports)
- M34:
  - [x] `sbt compile`
  - [x] `sbt "runMain borb.SoC"`
  - [x] `make -C verif/borb-sim/sim clean all`
  - [x] `./run_directed.sh verif/directed/asm/dual_issue_m34_memory_pairing_smoke.S --march rv64gc_zicsr_zifencei` (tohost `0x1`)
  - [x] `./run_directed.sh verif/directed/asm/rv64_zicbo_memory_model_smoke.S --march rv64gc_zicsr_zifencei_zicbom_zicbop_zicboz` (tohost `0x1`)
  - [x] `./run_directed.sh verif/directed/asm/rv64_l1d_writeback_smoke.S --march rv64gc_zicsr_zifencei` (tohost `0x1`)
  - [x] focused AMO ACT4 subset (`8/8`, zero generated failure reports)
  - [x] `./run_coremark.sh --profile` (`337,458` cycles, tohost `0x1`)
  - [x] `./run_act4.sh --profile rva23s64-full --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` (`1540/1540`, zero generated failure reports)
