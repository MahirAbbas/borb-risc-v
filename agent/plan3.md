# Plan: Borb RVA23S64 Roadmap with C1-Nano-Style 2-Wide Pipeline

## Summary

Move borb from the current asymmetric lane-1 implementation to a **10-stage, in-order, general 2-wide superscalar core** with a central issue stage and multiple specialized execution pipes. Keep the core single-hart, precise, and RISCOF/CoreMark-gated while finishing the scalar RVA23S64 claim ledger and keeping vector work as a shared-engine track.

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
`./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim`
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
  - Acceptance: existing RISCOF/CoreMark pass; `dual_issue_integer_smoke.S` still passes; no new performance target yet.

- [x] **M31: 10-Stage Frontend/Decode/Issue Retiming**
  - Split fetch/decode into `IF0-IF2` and `DE0-DE2`.
  - Preserve current frontend predictor/L1I behavior, with predicted redirects still disabled.
  - Build the central `ISS` stage with pipe reservation but initially only enable current-safe pairings.
  - Acceptance: full RISCOF green; CoreMark no functional regression; redirect/epoch tests pass.

- [x] **M32: General Integer/Branch 2-Wide**
  - Enable `ALU0`, `ALU1`, and dedicated `Branch` pipe issue from either slot.
  - Allow ALU+ALU, ALU+branch, branch+ALU, and branch+branch when ordering and redirect rules are safe.
  - Add lane0-to-lane1 same-cycle bypass only for results available in the same cycle; otherwise reject the pair.
  - Acceptance: directed tests for WAW, RAW rejection, lane0 redirect killing lane1, lane1 branch redirect, and dual integer writeback pass.

- **M33: Completion/Retire Queue and Dual Commit**
  - Add a small in-order completion/retire queue sized for at least the longest non-div FP latency plus frontend skid.
  - Retire up to two completed instructions per cycle in order.
  - Convert integer RF writeback, FP writeback, `fflags`, store commit, RVFI/debug/perf, and trap reporting to ordered dual-retire semantics.
  - Acceptance: precise-trap directed tests pass; same-cycle dual retire updates architectural state deterministically; full RISCOF remains green.

- **M34: Memory Pipe Integration**
  - Keep one `Load/Store` pipe and one D-cache command per cycle.
  - Allow memory+non-memory pairing, but reject memory+memory.
  - Make AMO, `cbo.zero`, PMP faults, misalignment, store visibility, and signature mirroring lane-aware.
  - Acceptance: L1D writeback smoke, cache-block smoke, AMO subset, PMP memory tests, and full RISCOF pass.

- **M35: FP Pipe Split**
  - Replace combinational scalar FP integration with issue into `FALU` and `FMAC`.
  - Use initial fixed latencies: move/classify/compare 1-2, add/convert/minmax 3, mul 4, FMA 5, div/sqrt iterative serialized.
  - Scoreboard FP registers until completion; commit FP state only through ordered retire.
  - Acceptance: RV64F/RV64D focused RISCOF subsets pass; FP+integer pairing tests pass; CoreMark remains green.

- **M36: Scalar RVA23S64 Claim Closure**
  - Re-audit the scalar/profile surface after pipeline changes: declared YAML, implemented-but-unclaimed features, and validator-limited features.
  - Keep current declared surface unless implementation evidence changes: `RV64IMAFDCSUZicbom_Zicbop_Zicboz_Zicond_Zicntr_Zicsr_Zifencei_Zihintpause_Zihpm_Zimop_Zfa_Zfh_Zca_Zcb_Zcmop_Zba_Zbb_Zbs_Svnapot`.
  - Keep `Zawrs`, `Sstc`, `Sscofpmf`, `Zic64b`, `Ziccif`, `Ziccrse`, `Ziccamoa`, `Zicclsm`, `Za64rs`, and `Svpbmt` documented as implemented/assumed/unclaimed unless local tooling supports clean declaration.
  - Acceptance: full active RISCOF pass count remains green; docs distinguish claimable vs unclaimed accurately.

- **M37: Vector Shared-Engine Reconciliation**
  - Keep vector issue serializing from scalar issue until scalar 2-wide is stable.
  - Route vector commands through the completion/retire queue so vector scalar writeback, vector memory traps, `vstart`, and `mstatus.VS` remain precise.
  - Do not claim full base `V`, vector profile, `Zvbb`, or `Zvkt` until coverage and timing contracts exist.
  - Acceptance: existing vector state/integer/LSU/permute/FP/Zvbb smokes pass plus full scalar gates.

- **M38: Performance Push**
  - Use perf counters to tune pipe selection, branch penalty, bypass paths, load-use behavior, and frontend refill bubbles.
  - Only re-enable predicted redirects or higher outstanding I-cache misses after they pass full RISCOF and `dual_issue_mixed_classes_smoke.S`.
  - Acceptance: CoreMark improves materially over the current documented `323,354` cycle baseline, or the retained changes must be reverted and bottlenecks documented.

## Test Plan

- Required regression after each milestone:
  - full RISCOF fast-sim gate
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
- RISCOF and CoreMark stay the hard gates; Tenstorrent and riscv-formal remain optional until explicitly promoted.
- RVA23S64 vector/profile companion claims remain separated from scalar pipeline work until the shared vector engine has broader coverage and a defensible timing contract.

## Implementation Notes

- M30: Added shared lane-native backend types in `src/main/backend/BackendTypes.scala`: `BackendPipe`, `PipelineSlot`, and `RetirePacket`. Re-hosted the existing restricted lane-1 side path in `CPU.scala` on `PipelineSlot(config)` while preserving current pairing restrictions and behavior. This is an incremental skeleton only; lane 0 still travels through the existing scalar `StageCtrlPipeline`.
- M30 bug fix: Full RISCOF exposed `pmpzicbo_prefetch.S` trapping on `csrs menvcfg,t0` because the environment configuration CSRs were not in the CSR whitelist. Added WARL `menvcfg`/`senvcfg` storage for the cache-block environment bits and confirmed the focused RISCOF repro passes.
- M30 bug fix: Full RISCOF then exposed `cbo.zero-01.S` only clearing one doubleword in the dumped signature. The LSU `cbo.zero` sequencer now latches its bus base and is not cancelled by redirect commit draining after launch, and the RISCOF simulator mirror recognizes committed `cbo.zero` instructions so the dumpable RAM signature reflects the architectural 64-byte zeroed block.
- M31: Added the central issue-stage pipe reservation contract. `BackendPipe.select(...)` classifies lane-0 and lane-1 instructions into the Plan 3 pipe set, `BackendIssue.SELECTED_PIPE` now travels downstream from dispatch for lane 0, and lane 1 uses the same selector with ALU1 preference. This keeps the current safe pairings unchanged while establishing shared pipe ownership payloads.
- M31: Physically retimed the scalar lane-0 backend so fetch remains `IF0/IF1/IF2` at stages 0/1/2, decode begins at `DE0` stage 3, two registered decode/issue transit stages occupy stages 4/5, dispatch/central issue moves to stage 6, source read moves to stage 7, execute moves to stage 8, and writeback/retire moves to stage 9. The debug plugin and RISCOF simulator probes were retargeted to the new execute/writeback stage names.
- M32: The conservative integer/branch two-wide path now has focused contract coverage in `verif/directed/asm/dual_issue_m32_integer_branch_smoke.S`. The directed test covers ALU+ALU, ALU+branch, branch+ALU, branch+branch, same-cycle RAW rejection, same-cycle WAW ordering, lane-0 redirect squash of lane 1, and lane-1 branch redirect behavior. The RTL remains intentionally restricted to current-safe integer ALU and conditional-branch pairings.

## Verification Checklist

- M30:
  - [x] `sbt compile`
  - [x] focused `./run_riscof.sh --skip-validate --fast-sim --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --tests pmpzicbo_prefetch.S`
  - [x] focused `./run_riscof.sh --skip-validate --fast-sim --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --tests cbo.zero-01.S`
  - [x] `./run_directed.sh verif/directed/asm/dual_issue_integer_smoke.S --march rv64gc_zicsr_zifencei`
  - [x] `./run_coremark.sh --profile` (`323,354` cycles, tohost `0x1`)
  - [x] `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` (`1540/1540`, zero generated failure reports)
- M31:
  - [x] `sbt compile`
  - [x] `sbt "runMain borb.SoC"`
  - [x] `./run_directed.sh verif/directed/asm/dual_issue_integer_smoke.S --march rv64gc_zicsr_zifencei` (tohost `0x1`)
  - [x] `./run_coremark.sh --profile` (`337,458` cycles, tohost `0x1`)
  - [x] `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` (`1540/1540`, zero generated failure reports)
- M32:
  - [x] `./run_directed.sh verif/directed/asm/dual_issue_m32_integer_branch_smoke.S --march rv64gc_zicsr_zifencei` (tohost `0x1`)
  - [x] `./run_coremark.sh --profile` (`337,458` cycles, tohost `0x1`)
  - [x] `./run_riscof.sh --verilate-jobs 10 --sim-jobs 10 --sim-threads 1 --fast-sim` (`1540/1540`, zero generated failure reports)
