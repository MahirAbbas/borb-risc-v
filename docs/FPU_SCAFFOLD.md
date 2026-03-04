# FPU Scaffold (Non-Integrated)

This repository now includes a standalone floating-point scaffold intended to be integrated later, without touching the active integer datapath today.

## Files

- `src/main/execute/fpu/FpuTypes.scala`
- `src/main/execute/fpu/FpuRegisterFile.scala`
- `src/main/execute/fpu/FpuCsrFile.scala`
- `src/main/execute/fpu/FpuScaffold.scala`

## Design Intent

- In-order friendly and area-efficient control:
  - Single in-flight FP op in the scaffold controller.
  - Class-based latency model for FP operations.
  - `kill` input for squash/redirect support.
- Integration-ready interfaces:
  - Typed FP issue/result bundles with op class, format, rm, rs/rd, and tag.
  - Dedicated FP register file (`f0..f31`) with configurable port counts.
  - Dedicated FP CSR state (`fflags`, `frm`, `fcsr`) with commit-gated flag updates.
- No CPU integration yet:
  - No decode-table changes.
  - No dispatch/scheduler changes.
  - No writeback/datapath wiring changes.

## Current Behavioral Scope

- This is scaffolding, not IEEE-754-complete execution.
- The controller accepts FP issue traffic and emits timed writeback metadata.
- Data path currently emits placeholder values:
  - `MOVE`/`CONVERT`: pass-through of source operand.
  - Most arithmetic classes: canonical qNaN placeholder.
  - Optional placeholder `fflags` behavior for arithmetic classes.

## Planned Integration Path (Later)

1. Decode FP instructions into `FpuIssue`.
2. Add dispatch route / hazard checks for FP source and destination registers.
3. Feed writeback responses into FP register file write ports.
4. Wire `fflags/frm/fcsr` into CSR map and privilege/trap policy.
5. Replace placeholder datapath behavior with real FP pipelines (add/mul/fma/div/sqrt/convert/compare).
