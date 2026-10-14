---
slug: subtitle-upstream-port
title: "Bring upstream's subtitle fixes into this fork"
stage: done
size: M
tracks: [ui]
kind: improvement
branch: subtitle-player-fixes
worktree: /home/user1/orca/workspaces/Puber/subtitle-player-fixes
base: be8395a4
spec: docs/superpowers/specs/2026-10-14-subtitle-upstream-port.md
plan: docs/superpowers/plans/2026-10-14-subtitle-upstream-port.md
rounds: {verify: 2, review: 2, judge: 1}
blocked_by: []
created: 2026-10-10
migrated: 2026-10-12
updated: 2026-10-14
---

## Next
Nothing; shipped. Follow-ups: subtitle-sideload-merge, subtitle-restore-model.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Rebase: commit in place then rebase, or re-apply on a fresh worktree? | no | answered | Commit in place (without replay_pid*.log), then rebase onto master |
| Q2 | Device re-pass after the rebase? | no | answered | Short smoke (Off default, picker labels, Back) plus unit tests; DEFAULT/FORCED manifest case by unit tests only |
| Q3 | Ship before subtitle-sideload-merge, accepting API-only rows may do nothing on HLS? | yes | answered | Yes, ship first; AC-4 stays as is |
| Q4 | Take D3 (re-apply the picker's current row with persist=false when the saved choice does not resolve)? | yes | answered | Yes, in this run |
| Q5 | Rewrite the two master overlay-autohide tests broken by AC-8, keeping their intent? | no | answered | Yes |
| Q6 | initialize closes any open panel on media change (focus lands on controls after an Episodes switch), as upstream? | no | answered | Yes, as upstream; checked in the smoke |

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-10-13 | Commit the uncommitted work in place and rebase onto master be8395a4 | owner |
| 2026-10-13 | Device re-pass: smoke + unit tests only | owner |
| 2026-10-13 | Ship before subtitle-sideload-merge; AC-4 unchanged | owner |
| 2026-10-14 | Take D3 in this run | owner |
| 2026-10-14 | Rewrite resumeDialog_noHideTimerRunsWhileItIsUp and controlsTimerDuringExit_changesNothing with the same intent | owner |
| 2026-10-14 | Media change closes any panel and focuses the controls, as upstream | owner |
| 2026-10-14 | Gate 1: spec approved | owner |
| 2026-10-14 | Gate 2: ship | owner |
| 2026-10-14 | Defer triage-2 #1 (audio-less rebuild drops the live subtitle pick) to subtitle-restore-model | owner |
| 2026-10-14 | Fix QA defect 1 here: PlayerUIMapper resolves strings and display locale through ResourceProvider (AppLocale), for every player label | owner |

## Log
- 2026-10-10 created on the board (stage review)
- worktree `subtitle-player-fixes` has uncommitted changes; device evidence: docs/superpowers/evidence/2026-10-10-subtitle-upstream-port-device.md (in the worktree)
- 2026-10-12 migrated from the former board file (board stage `review` -> `review`); the board row Notes are copied verbatim into ## Next
- 2026-10-13 resumed with /task:run: analyst mode size -> M, improvement, track ui, 9 ACs (brief.md Sizing note). Stage review on the old board had no spec, plan or commits, so the run restarts at design. Owner answered Q1-Q3. Stage design.
- 2026-10-14 design-architect.md and design-ux-designer.md written; owner answered Q4-Q6. Next: test-designer.
- 2026-10-14 test-design.md written; spec assembled and committed in the worktree (379ffd50); gate 1 approved by owner. Stage plan.
- 2026-10-14 plan written; readiness-1 NOT READY (reorder D8 test rewrite), architect round 2, readiness-2 READY; pasted into the plan. Stage impl.
- 2026-10-14 implementer done: rebased onto be8395a4 without conflicts, 11 commits a82a12c4..a4f0aeeb, check.full green per implementer; conductor checked diff --stat (23 files), co-authored 0, check.fast green. Stage verify.
- 2026-10-14 qa round 1 (qa-report.md): check.full green, 1458 tests; AC-1/3/8/9 PASS, AC-6/7 PASS unit only, AC-2/4 NOT CHECKED, AC-5 FAIL on device (mapper ignores in-app language; pre-existing for all player labels). Owner: fix here.
- 2026-10-14 implementer fix 0bf26ee5 (mapper via ResourceProvider). qa round 2: check.full green (1459 tests), AC-5 PASS on device, AC-1/3/8/9 PASS, AC-6/7 unit only, AC-2/4 NOT CHECKED; evidence 4ae32d90. Stage review.
- 2026-10-14 review round 1: blind, edge, tests, codex (ran), ux-designer (VISUAL: match); triage-1.md: fix #1,#3-#6, defer #2 (playback-controller-split) and #7 (player-overlays-port). Fixes c816bc44..0127f0c3; co-authored 0, check.fast green. Round 2 reviews started.
- 2026-10-14 review round 2: tests clean, edge + codex one minor (audio-less rebuild); qa round 3 PASS (evidence 0973d92b). Review cap reached; owner deferred it to subtitle-restore-model. triage-2.md written. Stage judge.
- 2026-10-14 judge-1.md: CONCERNS, no must-fix. Co-authored 0. Stage handover.
- 2026-10-14 gate 2: ship (owner).
- 2026-10-14 shipped: merged subtitle-player-fixes into master as 50f46021 (--no-ff); Orca worktree removed.
