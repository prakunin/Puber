---
slug: itest-incomplete-run-green
title: "make itest exits 0 when the instrumentation run fails to complete"
stage: done
size: S
tracks: []
kind: bug
branch: prakunin/itest-incomplete-run-green
worktree: /home/dev/orca/workspaces/Puber/itest-incomplete-run-green
base: 63771351
spec:
plan:
rounds: {verify: 1, review: 1, judge: 1}
blocked_by: []
source: player-hermetic-fixtures
created: 2026-11-27
migrated:
updated: 2026-11-27
---

## Next
none (done)

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Fail a completed run that ran 0 tests? | no | answered | yes, own message (recommended) |
| Q2 | Same check for make baseline-profile? | no | answered | no (recommended) |

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-27 | codex: required (owner); antigravity on every round (owner; skip while quota exhausted); caps lifted (owner) | owner |
| 2026-11-27 | Q1 fail zero-test runs; Q2 baseline-profile out of scope | owner (standing instruction: recommended) |
| 2026-11-27 | Gate 1: approved (S: brief ACs and Plan are the spec and plan) | owner (standing instruction: recommended) |
| 2026-11-27 | Gate 2: ship (judge-1 PASS; conductor adds the tooling test to check.full at ship) | owner (standing instruction: recommended) |

## Log
- 2026-11-27 created (stage idea) from player-hermetic-fixtures: device-run-5.md run 2: connectedInstrumentationDebugAndroidTest finished in 3 s with exit 0 while the JUnit XML holds only "testRunFailed: Test run failed to complete. No test results". make itest (Makefile:133-141) trusts the Gradle exit code; it should fail on an incomplete run (e.g. check the XML for testRunFailed or a zero test count).
- 2026-11-27 preflight: base 63771351; stage triage
- 2026-11-27 research.md (option A, S)
- 2026-11-27 brief.md (bug, S, 8 ACs); stage design
- 2026-11-27 test-design.md (P0 6, P1 1, P2 1, manual 1); gate 1 approved (standing instruction); Orca worktree /home/dev/orca/workspaces/Puber/itest-incomplete-run-green; stage impl
- 2026-11-27 impl done: 0d9212c6, ea626208, 7d329c87; new tooling test green (conductor); check.full edit deferred to ship (script exists only on the branch); stage verify
- 2026-11-27 qa-report round 1: check.full + tooling test pass, AC-1..AC-8 PASS; real emulator run: XML path confirmed, unknown TESTS class fails in Gradle itself (AC-3 branch fake-only); stage review
- 2026-11-27 review round 1 (blind, tests, codex; antigravity skipped: quota): triage-1: 2 cheap fixes, check.full wiring at ship
- 2026-11-27 fix: 345e30c4 (head 345e30c4); tooling test 7/7 (conductor); stage judge
- 2026-11-27 judge-1 PASS; gate 2 ship (standing instruction); stage handover
- 2026-11-27 squashed to 3 commits 365b02fa..9e6db1a8 (tree unchanged)
- 2026-11-27 shipped: merge commit ad4bd78f (--no-ff) into master; check.full in main .flow/config.yaml now runs tools/tests/test-itest-tooling.sh (uncommitted, like the rest of the owner's config edits); Orca worktree removed; stage done
