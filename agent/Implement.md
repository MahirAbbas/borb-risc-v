Now implement the entire project end-to-end.

Non-negotiable constraint

* Do not stop after a milestone to ask me questions or wait for confirmation.
* Proceed through every milestone in `plan.md` until the whole project is complete and fully validated.
* Assume you are expected to work autonomously for the full session. Do not stop after one fix or one failed run.
Iterate: reproduce, isolate, patch, validate, repeat.

Execution rules (follow strictly)

* Treat `plan.md` as the source of truth. If anything is ambiguous, make a reasonable decision and record it in `plan.md` before coding.
* Implement deliberately with small, reviewable commits. Avoid bundling unrelated changes.
* After every milestone:

  * run verification commands (lint, typecheck, unit tests, snapshots, and any integration checks)
  * fix all failures immediately
  * add or update tests that cover the milestone’s core behavior
  * commit with a clear message that references the milestone name
* If a bug is discovered at any point:

  * write a failing test that reproduces it
  * fix the bug
  * confirm the test now passes
  * record a short note in `plan.md` under “Implementation Notes”

Validation requirements

* Maintain a “verification checklist” section in `plan.md` that stays accurate as the repo evolves.

Documentation requirements

* Create `documentation.md` and keep it concise and useful. Update it as you implement so it matches reality.

Completion criteria (do not stop until all are true)

* All milestones in `plan.md` are implemented and checked off.
* `documentation.md` is accurate and complete.

Start now by reading `plan.md` and beginning Milestone 10. Continue until everything is finished.