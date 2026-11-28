# Review triage - round 1

Run: watch-state-batch-in-progress   Base: 8c3117ea   Reviewed head: 1beafa9c

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md, review-codex-1.md (ran: review, adversarial), review-state-path-1.md, review-antigravity-1.md (skipped: quota reached, owner: do not wait).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | tests F1, codex 1, blind F1 | minor | `WatchStateDaoTest.kt:186-212` | The AC-4 test cannot be shown to fail: three back-to-back commits without the outer transaction may coalesce into one emission, so it does not prove invalidation waits for the outer commit | Read the test: the writes run with no suspension between them, and no control case exists. AC-4 as written (three writes inside one `run`) is what the test does | fix (cheap, test only) | Add a sibling control case in `WatchStateDaoTest`: the same writes without the outer `run`, with a suspension point between them, asserting that a partial list is seen. If no partial list can be produced, report it and the test's KDoc says it is a nesting smoke test. Re-run `WatchStateDaoTest` on the box |
| 2 | codex 2 | minor | spec AC-4 command | `make ... itest` without `GRADLE=./tools/agentw` fails in a worktree (guard hook) | `Makefile:23` defaults `GRADLE ?= ./gradlew`; the plan and qa already used `GRADLE=./tools/agentw` | reject | The AC wording belongs to the analyst and stays as approved; the working command is in the plan's Global constraints and the qa evidence; recorded in the spec's Decisions |
| 3 | blind F1 (part) | minor | same test | The test does not call `WatchStateRepository.recordInProgress` | AC-4 specifies a `transactions().run` test at DAO level; the repository change is covered by the JVM test `recordInProgress_runsTheWholeListInOneTransaction` (red on base) | reject | Matches the approved AC; the repository batching is proven by AC-1 |

## Rejected codex claims

- The 1 s settle delay can fail on a slow device - same pattern as the existing test at :107, false-fail only (unproven).

## Next

- Fix round: implementer fixes #1; check.fast + androidTest compile; review edge + codex + tests on the incremental diff; qa re-runs `WatchStateDaoTest` on the box (AC-4); unproven lines: 4
