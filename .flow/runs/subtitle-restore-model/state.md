---
slug: subtitle-restore-model
title: "Restore the chosen subtitle across player recreation with upstream's SubtitlePreference keys (7d5addd2, f9c9ff38, a563d6f6)"
stage: done
size: M
tracks: []
kind: improvement
branch: subtitle-restore-model
worktree: /home/user1/orca/workspaces/Puber/subtitle-restore-model
base: f351d988
spec: docs/superpowers/specs/2026-10-15-subtitle-restore-model.md
plan: docs/superpowers/plans/2026-10-15-subtitle-restore-model.md
rounds: {verify: 2, review: 3, judge: 1}
blocked_by: []
created: 2026-10-12
migrated:
updated: 2026-10-15
---

## Next
Nothing; shipped. Follow-ups: subtitle-switch-text-disable (after P6-A), manual-quality-hls4.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Include option C (switchStream text disable + instrumented tests)? | yes | answered | Yes (owner, against the recommendation) |
| Q2 | On a rebuild, live intent before the saved preference? | yes | answered | Yes, intent first (AC-8) |
| Q3 | Device check scope | no | answered | Exit/re-enter on an HLS title with a manifest-only row, then after a quality switch; forced and language-less unit-only |
| Q4 | With option C the run is L: how to sequence Part 2? | yes | answered | Separate run subtitle-switch-text-disable after Part 1 and P6-A |
| Q5 | Ask upstream-sync-triage to prioritise P6-A? | no | answered | Yes |
| Q6 | AC-4 wording (architect Q1) | yes | answered | Reword (recommended) |
| Q7 | Keep saved preference after an empty restore, new AC-16 (architect Q2) | yes | answered | Yes (recommended) |
| Q8 | Label rule in findSameSubtitleTrackIndex (architect Q3) | no | answered | No (recommended) |
| Q9 | AC-11 after a quality switch (conductor) | yes | answered | Reword (recommended) |

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-10-14 | Include option C (switchStream text disable + instrumented tests) | owner |
| 2026-10-14 | Rebuild restore: intent first, then saved | owner |
| 2026-10-14 | Device scope as recommended | owner |
| 2026-10-15 | Split: this run is Part 1 (option B, AC-1..AC-12, M); Part 2 -> run subtitle-switch-text-disable after P6-A | owner |
| 2026-10-15 | Ask upstream-sync-triage to prioritise P6-A | owner |
| 2026-10-15 | Standing instruction: every step on recommended answers, gates and ship included, no questions | owner |
| 2026-10-15 | Q6-Q9 as recommended; AC-4, AC-11 reworded; AC-16 added | owner (standing instruction: recommended) |
| 2026-10-15 | Gate 1: spec approved | owner (standing instruction: recommended) |
| 2026-10-15 | Gate 2: ship | owner (standing instruction: recommended) |

## Log
- 2026-10-12 created (stage idea) as step 2 of option C in subtitle-sideload-merge (owner decision): SubtitlePreference with forced and descriptive-label pref keys, restore across playback recreation, old-to-new pref compatibility. Context: ../subtitle-sideload-merge/research.md
- 2026-10-14 deferred from subtitle-upstream-port review round 2 (triage-2.md #1, owner decision): PlaybackController.prepare() clears pendingSubtitleTrack on every prepare, so rebuilding the same media (buffer preset, Fast DNS) on a stream with no audio group drops the live subtitle pick while the picker still shows the row (VM restore runs only from onTracksUpdated, which needs an audio group). Fix candidates: clear the pending pick only on media change, or re-send the current row after initializePlayer, or decouple the restore from audio groups.
- 2026-10-14 /task:run: research.md (option B recommended) and brief.md (improvement, M, 12 ACs). Owner: include option C, intent-first rebuild, device scope as recommended. Analyst re-sizing with option C. Stage triage.
- 2026-10-15 analyst amended brief with option C: size L, split proposed. Owner: Part 2 to new run subtitle-switch-text-disable (idea) after P6-A; ask upstream-sync-triage to prioritise P6-A (logged there). This run = Part 1, M, AC-1..AC-12. Orca worktree subtitle-restore-model on f351d988. Stage design.
- 2026-10-15 design-architect.md and test-design.md written; spec assembled (9b1dba8a). Owner standing instruction: run every step on recommended answers incl. gates and ship. Gate 1 approved on that basis (AC-4, AC-11 reworded, AC-16 added). Stage plan.
- 2026-10-15 plan (62d24119), readiness-1 NOT READY (AC-11 quality-switch device steps), test-design + plan amended (055d22be), readiness-2 READY. Owner: push and release at the end. Stage impl.
- 2026-10-15 implementer done: b635fa0a..d538e15b (4 tasks), check.full green; failing-first T-2a/T-5b/T-16a red on base. Flaky unrelated suites seen under load (HomeVMIncrementalPublishTest, HistoryVMDeletionSchedulingTest). Conductor: 13 files +821/-131, co-authored 0, check.fast green. Stage verify.
- 2026-10-15 qa-device round 1 (a99cdff9): check.full green 1632 tests; all ACs PASS except AC-11 clause (a) NOT CHECKED (no stream other than Auto offers a manifest-only row); no defects. Review round 1: blind 3, edge 1, tests 4, codex clean. triage-1.md: fix #1-#7, reject #8 (AC-7 contract) and #9 (pre-existing). Stage review.
- 2026-10-15 fix round 1: bccc8161, c81474e8, 630e2074. Round 2: edge none, tests F1 (major test gap), codex minor; qa round 2 (8606de32) PASS, label-chooser NOT CHECKED on device, AC-11 (a) NOT CHECKED; unexplained Off on the first S1E5 open. triage-2.md: fix #1-#2. Round 3 beyond the cap under the owner's standing instruction.
- 2026-10-15 fix round 2: 89c68077 (pick snapshot, per-media reset test), 2e8d4be5. Round 3 edge + tests: no findings. check.full green 1637. Stage judge.
- 2026-10-15 judge-1.md: CONCERNS, no must-fix. Gate 2: ship (standing instruction). Shipped: merged into master as 7becc4d0 (--no-ff); Orca worktree removed.
