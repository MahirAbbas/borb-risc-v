# Full Spinal Pipeline Migration And Exact RV64IMAFDCSU ACT4 Gate

## Summary

Migrate borb from the transitional asymmetric lane model to a lane-native SpinalHDL pipeline contract. Lane-local backend data must be addressed through Spinal's keyed payload API with stable lane keys, while global controls such as speculation epoch and redirect state remain single-copy.

The hard architectural signoff gate for this migration is:

```bash
./run_act4.sh --profile rv64imafdcsu-profile --verilate-jobs 10 --sim-jobs 10 --sim-threads 1
```

`rva23s64-full` is broader roadmap telemetry only.

## Milestones

### M1: Freeze And Audit

- [ ] Record current `rv64imafdcsu-profile` failures by class.
- [x] Replace this checklist with the exact-profile migration plan.
- [x] Confirm the existing custom lane-1 primary path and helper payloads.
- [ ] Establish green smoke gates:
  - `sbt compile`
  - `sbt "runMain borb.SoC"`
  - `./run_coremark.sh --profile`
  - focused `rv64imafdcsu-profile` ACT4 subsets

### M2: Define Keyed Spinal Lane API

- [x] Add stable lane keys accepted by Spinal's keyed payload API.
- [x] Remove helper-only `LaneKeyedPayload` infrastructure.
- [x] Compile direct `stage(payload, LaneKey.Lane*)` access.
- [x] Define required lane-local CPU call-site payloads with keyed access:
  - `Decoder.RD_ADDR`
  - `Decoder.RS1_ADDR`
  - `Decoder.RS2_ADDR`
  - `IssueSemantics.PROPS`
  - `BackendIssue.SELECTED_PIPE`
  - `SrcPlugin.RS1`
  - `SrcPlugin.RS2`
  - `WriteBack.RESULT`
  - `COMMIT`
  - retire intent

### M3: Move Lane 1 Into Spinal Stages

- [x] Remove the obsolete `lane1Pipe` symbol and the old named lane-1 stage aliases.
- [x] Delete the explicit lane-1 stage-register storage and manual kill/advance path.
- [x] Delete lane-1 source/execute/writeback next-slot plumbing and lane-1 regfile read hookup.
- [ ] Reintroduce lane-1 pairing only through keyed Spinal pipeline payloads.
- [x] Remove obsolete primary-path symbol names targeted by regression search.
- [ ] Split the lane-1 keyed slot bundle into lane-local keyed fields where practical.
- [ ] Preserve current lane-1 integer ALU and conditional-branch-only policy.

### M4: Unify Hazard, Source, Execute, And Retire

- [ ] Make pairability, RAW/WAW, busy-register checks, source port assignment, bypass resolution, branch redirect priority, and retire packet creation consume keyed lane payloads.
- [ ] Keep lane 0 as the full scalar backend owner.
- [ ] Keep memory, AMO, CSR/trap, FP, vector, serializing, and jump work scalarized to lane 0.
- [ ] Preserve ordered retire: lane 0 before lane 1.

### M5: Make The Exact ISA Gate Hard

- [ ] Run:
  ```bash
  ./run_act4.sh --profile rv64imafdcsu-profile --verilate-jobs 10 --sim-jobs 10 --sim-threads 1
  ```
- [ ] Root-cause and fix all failures under this profile.
- [ ] Track immediate fix areas:
  - FP fused/convert edge semantics
  - misaligned memory/trap progress

### M6: Documentation And Cleanup

- [ ] Update `documentation.md` for the fully keyed Spinal lane architecture and exact ACT4 gate.
- [ ] Keep `rva23s64-full` documented as telemetry only.
- [ ] Keep directed tests documented as debug tools, not signoff.
- [ ] Ensure regression searches prove no obsolete lane-1 primary-path state remains.

## Regression Searches

```bash
rg "lane1Pipe|lane1DecodeSlot|lane1SrcSlot|lane1ExecSlot|lane1WbSlot|initLaneSlotFrom|copyLane1Header|lane1s[0-9]" src/main
rg "SELECTED_PIPE_BY_LANE|LaneKeyedPayload" src/main
rg "\([^,]+, LaneKey\.Lane[01]\)" src/main
```

Required result: no obsolete lane-1 primary-path state; lane-local values use keyed Spinal payload access.

## Validation Log

- 2026-05-02: Direct `ctrl.up(payload, LaneKey.Lane1)` keyed payload access compiles.
- 2026-05-02: Removed `LaneKeyedPayload` and `BackendIssue.SELECTED_PIPE_BY_LANE`.
- 2026-05-02: Tried hosting lane-1 state directly as a keyed Spinal `setAsReg` payload across dispatch/source/execute/writeback. It compiled and elaborated but regressed CoreMark into a trap loop, so that storage move was backed out while keeping the keyed call-site contract.
- 2026-05-02: Removed the old `lane1Pipe`/`lane1DecodeSlot`/`lane1SrcSlot`/`lane1ExecSlot`/`lane1WbSlot` names and mirrored lane-1 state into keyed Spinal payloads. Remaining explicit lane-1 storage is tracked as unfinished M3 work.
- 2026-05-02: `sbt compile` passed after the initial keyed-call-site migration.
- 2026-05-02: Added lane-keyed CPU call-site mirrors for decode register addresses, issue props, selected pipe, integer sources, writeback result, and commit intent.
- 2026-05-02: `sbt "runMain borb.SoC"` passed after keyed payload elaboration.
- 2026-05-02: `./run_coremark.sh --profile --rebuild-sim` passed with tohost `0x1`, `337,458` cycles, and `2.9633317331341975 CoreMark/MHz`.
- 2026-05-02: Exact ACT4 `rv64imafdcsu-profile` run built/elaborated but aborted after a 300 second harness timeout. The first reported timeout was `priv/ExceptionsS/ExceptionsS-00.elf`; a parallel orphan was also observed on `rv64i/Zfh/Zfh-fmadd.h-03.elf`. Orphan simulator processes were stopped.
- 2026-05-02: Deleted the remaining custom lane-1 execution path from `CPU.scala`: no `laneOneStages`, lane-1 next-slot records, manual lane-1 kill/advance, lane-1 redirect muxing, lane-1 retire packet, or lane-1 regfile read plumbing remain. Integer regfile read ports 2/3 are explicitly idled in `SrcPlugin`.
- 2026-05-02: Post-deletion `sbt compile` and `sbt "runMain borb.SoC"` passed. Lane-1 pairing is intentionally absent until it is rebuilt through keyed Spinal pipeline payloads.
- 2026-05-02: Exact ACT4 `rv64imafdcsu-profile` was rerun after deletion. It progressed past the earlier `ExceptionsS-00` 300s harness-timeout point and reached 190/748 before being stopped because the gate was already non-green. Observed failures included privilege/Sv/PMP/Zicbo/Zicntr/Zaamo classes and later FP D-class tests were still in flight. No ACT4 or simulator worker processes were left running.
