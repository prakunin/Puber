---
slug: player-hermetic-fixtures
title: "Add hermetic player test fixtures (synthetic MP4/HLS/VTT, loopback server, network blocker) and check the stale-callback gate"
stage: done
size: M
tracks: []
kind: improvement
branch: prakunin/player-hermetic-fixtures
worktree: /home/dev/orca/workspaces/Puber/player-hermetic-fixtures
base: f53f2144
spec: docs/superpowers/specs/2026-11-27-player-hermetic-fixtures.md
plan: docs/superpowers/plans/2026-11-27-player-hermetic-fixtures.md
rounds: {verify: 5, review: 2, judge: 1}
blocked_by: []
source: upstream-sync-triage
created: 2026-11-26
migrated:
updated: 2026-11-27
---

## Next
none (done)

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Which device may install and run com.kino.puber.instrumentation + test APK via make FLAVOR=instrumentation itest? | yes (AC-4, AC-5) | answered | emulator host.docker.internal:5555 (owner, explicit) |

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-27 | Q2: Part B out of this run, new idea run player-compose-e2e-tests blocked by player-overlays-port | owner (standing instruction: recommended) |
| 2026-11-27 | Q3: take upstream fixture bytes, verify against SHA256SUMS, generator kept as maintainer tool | owner (standing instruction: recommended) |
| 2026-11-27 | codex: required (owner); antigravity on every round (owner); caps lifted (owner) | owner |
| 2026-11-27 | Q1: emulator host.docker.internal:5555 for AC-4/AC-5 instrumented tests | owner (explicit answer) |
| 2026-11-27 | A2-A5 (architect) as recommended: conductor edits main .flow/config.yaml check.full; AC-6 widened to "not ported: <reason>"; no detekt/lint extension; PACKAGE_instrumentation out | owner (standing instruction: recommended) |
| 2026-11-27 | Gate 1: approved | owner (standing instruction: recommended) |
| 2026-11-27 | AC-5 outcome "reproduced": run player-callback-gate opened, switchStreamAfterQueuedFailure committed @Ignore("player-callback-gate") | conductor per spec AC-5 |
| 2026-11-27 | Gate 2: ship (judge-1 CONCERNS, no must-fix) | owner (standing instruction: recommended) |

## Log
- 2026-11-26 created (stage idea) from the upstream (rovkinmax/Puber) re-check: upstream has no app-code commits after b755826c (17 new commits, all `.kent/` tooling), so the open items are the unmerged ports of upstream-sync-triage. Spec with the triage table: `git show upstream-sync-triage:docs/superpowers/specs/2026-10-11-upstream-sync-triage-design.md`. Branch upstream-sync-triage is 31 commits ahead of its base and master is 376 ahead, so cherry-pick into a fresh worktree rather than rebase the branch.
- Port P6 (triage T10), upstream fe6224fb, aa9ffb47, 1d3ec39e, 7c30221e. Its blockers are done (subtitle-upstream-port, subtitle-sideload-merge, subtitle-restore-model). Part A: `player-test-fixtures` module, loopback server, network blocker, `tools/generate-player-test-fixtures.sh`, `docs/testing/player-playback.md`, controller-level instrumented tests run through `make itest` (no Kaspresso, no MockWebServer in our catalog). Gated item: run the logic of upstream's PlaybackControllerCallbackGenerationTest / PlayerVMCallbackGenerationTest against our PlaybackController (930 lines); port `PlaybackCallbackGate` only if the stale callback after a media rebuild reproduces. Part B (Compose E2E and focus tests, PuberVM router rebind) only after player-overlays-port lands. Likely size L: split in triage. Related: playback-controller-split (upstream's policy split as reference).- 2026-11-27 research.md (option A), brief.md (M, 9 ACs, Q1 blocks AC-4/5); Orca worktree /home/dev/orca/workspaces/Puber/player-hermetic-fixtures, branch prakunin/player-hermetic-fixtures, base f53f2144
- 2026-11-27 design-architect.md, test-design.md (P0 13); spec assembled; Q1 answered: emulator; gate 1 approved
- 2026-11-27 plan (6 tasks) written, readiness-1 READY; stage impl
- 2026-11-27 impl done: 6 commits d7858228..5ba6f738, check.fast green, named P0/P1 tests present, app/src/main untouched
- 2026-11-27 verify round 1: qa static gate green; qa declined relayed device consent, conductor ran itest on emulator itself: 13/19 fail (NetworkOnMainThreadException from MockWebServer.url reverse DNS on main; missing required routes). device-run-1.md; fix round to implementer
- 2026-11-27 verify round 2 (device, after 1837ce91): 18/19 and 17/19; T15/T16 pass (not reproduced); isolation test expects path in journal; teardown flake "Gave up waiting for queue to shut down". device-run-2.md; fix round 2
- 2026-11-27 verify round 3 (device, after 542b65c5): 19/19, 18/19, 18/19; teardown flake persists in two different tests. device-run-3.md; fix round 3
- 2026-11-27 verify round 4 (device, after da1c1f89): 5 consecutive runs 19/19; AC-5 not reproduced. device-run-4.md; qa-device to map ACs and run check.full
- 2026-11-27 qa round 2: check.full green, 9/9 AC PASS (AC-4/5 on conductor-run device evidence). Evidence committed, HEAD ce54fc41; stage review
- 2026-11-27 review round 1 at ce54fc41: antigravity clean, codex/blind/edge/tests 1 major (stale probe ordering) + minors; triage-1.md: 11 fix, 1 reject
- 2026-11-27 device run 5 after review fix round 1: queued-failure case reproduces the stale callback (AC-5 reproduced); corrupt-cache recovery not reachable; one incomplete run reported green. Opened idea runs player-callback-gate, player-corrupt-cache-recovery, itest-incomplete-run-green
- 2026-11-27 device run 6 after 73f3b5a4: 3 runs, 20 tests, 0 failures, 1 skipped (player-callback-gate). Review round 2 on ce54fc41..73f3b5a4; qa round 3
- 2026-11-27 qa round 3: check.full green, 9/9 PASS (AC-5 reproduced branch). Review round 2: edge/antigravity clean, 3 minors deferred to player-device-tests-hardening, 1 nit accepted. triage-2.md; stage judge
- 2026-11-27 judge-1 CONCERNS (no must-fix); created player-compose-e2e-tests (Q2 promise); gate 2 ship; squash next
- 2026-11-27 squashed to 4 commits (tree a3d3f05e unchanged), doc Layers row corrected; main .flow/config.yaml check.full now runs :player-test-fixtures:testDebugUnitTest (AC-9, A2)
- 2026-11-27 shipped: merge commit a298faa9 (--no-ff) into master; Orca worktree removed; stage done
