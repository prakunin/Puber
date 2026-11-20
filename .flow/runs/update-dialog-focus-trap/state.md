---
slug: update-dialog-focus-trap
title: Update prompt at launch leaves D-pad focus on the screen beneath
stage: done
size: M
tracks: []
kind: bug
branch: prakunin/update-dialog-focus-trap
worktree: /home/user1/orca/workspaces/Puber/update-dialog-focus-trap
base: 0bc3ab0e
spec: /home/user1/orca/workspaces/Puber/update-dialog-focus-trap/docs/superpowers/specs/2026-11-20-update-dialog-focus-trap.md
plan: /home/user1/orca/workspaces/Puber/update-dialog-focus-trap/docs/superpowers/plans/2026-11-20-update-dialog-focus-trap.md
rounds: {verify: 3, review: 2, judge: 1}
blocked_by: []
source:
created: 2026-11-20
migrated:
updated: 2026-11-20
---

## Next
Done. Merged as 3105e87e; release v1.12.3 tag waits for the owner.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-20 | Run end to end on recommended answers, gates included | owner (standing instruction: recommended) |
| 2026-11-20 | Round caps lifted | owner (caps lifted, standing instruction) |
| 2026-11-20 | update-prompt-focus folded into this run as a duplicate | owner (standing instruction: recommended) |
| 2026-11-20 | Q1: D-pad trap goes into TvDialogOverlay for all dialogs, D-pad moves only | owner (standing instruction: recommended) |
| 2026-11-20 | Q2: one uncommitted worktree build with currentVersion lowered (versionCode unchanged) for the launch-race device check, then a smoke on the committed build; Update never pressed | owner (standing instruction: recommended) |
| 2026-11-20 | Q3: the focus gate covers only the update prompt | owner (standing instruction: recommended) |
| 2026-11-20 | Design Q1: Checking state keeps no focus trap; AC-8 starts at the result state | owner (standing instruction: recommended) |
| 2026-11-20 | Gate 1: approved (size M, no tracks) | owner (standing instruction: recommended) |
| 2026-11-20 | Q4 revised: Checking and Downloading get a focus target inside the prompt (triage-1 #1-#2) | conductor (review finding; standing instruction) |
| 2026-11-20 | Gate 2: ship (judge CONCERNS, no must-fix) | owner (standing instruction: recommended) |
| 2026-11-20 | Hold the v1.12.3 tag until this fix is on master | owner |

## Log
- 2026-11-20 created (stage triage)
- 2026-11-20 research.md written; duplicate update-prompt-focus folded in
- 2026-11-20 triage: brief.md, M, kind bug, 10 ACs; stage design
- 2026-11-20 design-architect.md written (8 decisions, 7 risks)
- 2026-11-20 test-design.md written; spec assembled; gate 1 approved; stage plan
- 2026-11-20 plan written (4 tasks); readiness next
- 2026-11-20 readiness-1 READY; stage impl
- 2026-11-20 impl done (4 task commits + record); check.fast green; stage verify
- 2026-11-20 qa round 1 FAIL: AC-1/2/3/4/6/8 on device (prompt over loaded Home not focused; dismiss lands on rail)
- 2026-11-20 fix round 1: handoff respects the gate (b3307eb7), dismiss starts a handoff (59a2ebb8)
- 2026-11-20 qa round 2 PASS (all 10 ACs); stage review
- 2026-11-20 review round 1: codex clean; 3 majors, 5 minors; triage-1: 7 fix, 1 deferred (update-gate-wiring-tests)
- 2026-11-20 review fix round 1: cf8938fb, 0fe2ffd7, 87842639
- 2026-11-20 review round 2: edge clean, 2 minors accepted; qa round 3 PASS; stage judge
- 2026-11-20 judge-1 CONCERNS (no must-fix); gate 2 ship; stage handover
- 2026-11-20 squashed to 3 commits (869b9620, eac8c56d, f1a60d4f), tree unchanged, check.fast green
- 2026-11-20 shipped: merged into master as 3105e87e; worktree removed
