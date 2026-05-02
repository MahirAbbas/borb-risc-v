# RVA23S64 Acceptance Policy

This policy defines when a mandatory profile feature can be marked complete in `rva23s64_ledger.json`.

A mandatory feature is complete only when all of these are true:

- RTL behavior exists in the relevant owner area.
- ISA/platform metadata declares the feature, or a repo-local profile checker accepts it when upstream tooling lacks the name.
- ACT4 covers it, or a directed/local backfill test is explicitly listed.
- The selected reference model supports it, or the ledger names a local backfill/reference route.
- `current_blocker` is `none`.
- The feature has evidence links in either the row's `evidence_links` field or the ledger-level `evidence_links` catalog.

The ledger report tool enforces the evidence rule when computing mandatory missing or blocked rows:

```bash
python3 scripts/profile_ledger.py --write-md verif/act4/profiles/missing_features.md --write-json verif/act4/profiles/missing_features.json
```

Full ACT4 RVA23S64 remains the regression floor, but a pass count is not full-profile evidence unless the ledger row maps that pass to a specific feature and no mandatory coverage is missing.
