# Regression Gating

## Problem

Targeted fixes have been validated against too-narrow subsets, then landed with regressions in adjacent domains.

In borb, this is especially common for changes in:

- LSU / AMO / load-store alignment
- trap / CSR / exception redirect
- dispatch / writeback / epoch / stall plumbing
- fetch / compressed decode / frontend sequencing

Those paths are shared by many architectural families, so a fix that only reruns the original failing test is not credible verification.

## Policy

The gate is designed to answer one question:

`Did this change introduce any new regressions relative to the current known baseline?`

That is different from asking whether the whole core is green. The current tree already has known failing areas, so the gate compares against a checked-in baseline and blocks only on:

- a previously passing test now failing
- a test missing from the baseline that now fails in a selected suite
- infrastructure/build failures in the selected suite

It also reports improvements separately so the baseline can be updated intentionally after review.

## Workflow

1. Refresh the baseline from a reviewed full RISCOF run:

```bash
python3 /Users/mahir/fun/borb/scripts/regression_gate.py --refresh-baseline
```

2. Before claiming a fix is verified, run the gate for the changed area:

```bash
python3 /Users/mahir/fun/borb/scripts/regression_gate.py \
  --changed-files src/main/execute/Lsu.scala,src/main/backend/TrapCsrBackend.scala
```

3. For ambiguous or wide changes, force the broad matrix:

```bash
python3 /Users/mahir/fun/borb/scripts/regression_gate.py --impact global_core
```

4. Only after the gate is clean should a fix be treated as regression-safe.

## Impact Mapping

The gate infers an impact class from changed files and expands that to a suite matrix.

Examples:

- `Lsu.scala`, `src/main/memory/*`
  - `rv64i.full`
  - `rv64c.full`
  - `rv64a.full`
  - `rv64priv.full`
  - `rv64pmp.full`
  - `rv64vm_sv39.full`
  - `rv64vm_pmp.full`
  - `rv32f.full`

- `TrapCsrBackend.scala`
  - `rv64priv.full`
  - `rv64pmp.full`
  - `rv64vm_sv39.full`
  - `rv64vm_pmp.full`
  - `rv64i.full`
  - `rv64c.full`

- `dispatch/*`, `CPU.scala`, `writeBack.scala`
  - `global_core`
  - this is intentionally broad

## Why This Is Better

The old pattern was:

- reproduce one failure
- patch RTL
- rerun one or two focused tests

The new pattern is:

- classify the shared subsystem touched by the fix
- rerun the entire affected matrix
- compare against a baseline of expected pass/fail
- fail closed on any newly-broken test

This spends more CPU, but it is the right tradeoff for borb.

## Recommended Discipline

- Do not say "fixed" after a single directed test.
- Do not refresh the baseline automatically after a run with regressions.
- Refresh the baseline only after reviewing improvements and confirming they are intended.
- For risky core-path changes, still run the full overnight or full-validation flow after the gate passes.
