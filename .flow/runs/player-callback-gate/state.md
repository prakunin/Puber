---
slug: player-callback-gate
title: "A stream failure queued before switchStream is attributed to the new stream and the new stream never loads"
stage: done
size: M
tracks: []
kind: bug
branch: prakunin/player-callback-gate
worktree: /home/dev/orca/workspaces/Puber/player-callback-gate
base: cec5f7fc
spec: docs/superpowers/specs/2026-11-27-player-callback-gate.md
plan: docs/superpowers/plans/2026-11-27-player-callback-gate.md
rounds: {verify: 1, review: 2, judge: 1}
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
| Q1 | Option A (no stop(), test body unchanged, one re-prepare for a stale error) or B (upstream stop(), relaxed precondition)? | yes | answered | A (recommended) |
| Q2 | AC-1 instrumentation itest on emulator host.docker.internal:5555 as in player-hermetic-fixtures Q1; dev-APK playback check AC-6 on box 192.168.1.110:5555? | no | answered | yes (recommended) |
| Q3 | PlaybackCallbackGate as a small class with a JUnit 5 test, or inline token? | no | answered | small class + JUnit 5 test, no Robolectric (recommended) |

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-27 | codex: required (owner); antigravity on every round (owner); caps lifted (owner) | owner |
| 2026-11-27 | Q1: option A (session gate + one re-prepare, no stop()) | owner (standing instruction: recommended) |
| 2026-11-27 | Q2: AC-1 itest on emulator host.docker.internal:5555, AC-6 dev-APK playback on box 192.168.1.110:5555 | owner (standing instruction: recommended) |
| 2026-11-27 | Q3: PlaybackCallbackGate as a small class with a JUnit 5 test | owner (standing instruction: recommended) |
| 2026-11-27 | Gate 1: approved | owner (standing instruction: recommended) |
| 2026-11-27 | Gate 2: ship (judge-1 PASS) | owner (standing instruction: recommended) |

## Log
- 2026-11-27 created (stage idea) from player-hermetic-fixtures: AC-5 reproduced on the emulator (API 36), see .flow/runs/player-hermetic-fixtures/device-run-5.md. PlaybackControllerStaleCallbackTest.switchStreamAfterQueuedFailure_reportsNoFailureForPreviousStream (committed @Ignore("player-callback-gate")) fails: StreamFailure(NEXT_URL) carries B's URL (PlaybackController.kt:260-264 reads currentStreamUrl at dispatch) and B is never requested. Upstream fix reference: PlaybackCallbackGate in fe6224fb (spec D26). Un-ignore the test as the AC.
- 2026-11-27 preflight: base cec5f7fc; stage triage
- 2026-11-27 research.md written (3 options, recommended A: session gate + one re-prepare, no stop())
- 2026-11-27 brief.md (bug, M, 8 ACs, 3 questions answered on recommended); stage design
- 2026-11-27 Orca worktree /home/dev/orca/workspaces/Puber/player-callback-gate, branch prakunin/player-callback-gate, base cec5f7fc
- 2026-11-27 design-architect.md (10 decisions, 8 risks, no questions)
- 2026-11-27 test-design.md (P0 12, P1 3, P2 4, manual 1); spec assembled
- 2026-11-27 gate 1 approved (standing instruction); stage plan
- 2026-11-27 plan (3 tasks) written
- 2026-11-27 readiness-1 READY; stage impl
- 2026-11-27 impl done: a569fdb4, d1c0090b, ed00b902; check.fast green (conductor), red run recorded, itest 3x3/0 fail + guards 14/0; stage verify
- 2026-11-27 qa-report round 1: check.full pass, AC-1..AC-8 PASS; observation: audio row label "Track 1" after quality switch (not compared with master); stage review
- 2026-11-27 review round 1: 6 lenses, triage-1: 1 fix (minor, test), 1 deferred to player-device-tests-hardening, 1 new idea run player-audio-track-label-fallback
- 2026-11-27 fix round 1: 6ec6e015 (test only); itest 3x4/0, mutation run red
- 2026-11-27 review round 2 (edge, tests, codex, antigravity): clean; check.full after fix: exit 0
- 2026-11-27 stage judge
- 2026-11-27 judge-1 PASS; gate 2 ship (standing instruction); stage handover
- 2026-11-27 squashed to 4 commits d02406e2..ee676e13 (tree 2a99b9b3 unchanged)
- 2026-11-27 shipped: merge commit 20cbb65c (--no-ff) into master; Orca worktree removed; stage done
