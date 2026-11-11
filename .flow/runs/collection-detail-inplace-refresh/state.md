---
slug: collection-detail-inplace-refresh
title: "CollectionDetail reloads through Loading after Details returns content changes, which drops the focused card and the grid scroll"
stage: done
size: M
tracks: []
kind: bug
branch: prakunin/collection-detail-inplace-refresh
worktree: /home/user1/orca/workspaces/Puber/collection-detail-inplace-refresh
base: 0e4449e2
spec: docs/superpowers/specs/2026-11-11-collection-detail-inplace-refresh.md
plan: docs/superpowers/plans/2026-11-11-collection-detail-inplace-refresh.md
rounds: {verify: 2, review: 2, judge: 1}
blocked_by: []
source: grid-focus-restore-after-back
created: 2026-11-11
migrated:
updated: 2026-11-11
---

## Next
nothing (shipped)

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-11 | Size M (analyst; research suggested S: VM state and TV focus are risk areas), tracks none, kind bug | owner (standing instruction: recommended) |
| 2026-11-11 | Q1 refresh shape A: silent refetch while Content is shown | owner (standing instruction: recommended) |
| 2026-11-11 | Q2 heart-revert guard: await pending mutations and keep the current isSaved for ids toggled during the load | owner (standing instruction: recommended) |
| 2026-11-11 | Q3 a failed in-place refresh keeps the stale grid and shows the message | owner (standing instruction: recommended) |
| 2026-11-11 | Gate 1: approve (8 ACs, design 6 decisions, test design P0 6 / P1 9 / manual 4) | owner (standing instruction: recommended) |
| 2026-11-11 | Plan Q1: adopt amendment A1 (clear savedSinceLastPublish when a load starts with no save running; reshape the P2 failed-refresh test; add P1 guard gridToggleBeforeReturn_showsServerSavedValue) | owner (standing instruction: recommended) |
| 2026-11-11 | Addendum after verify round 1: AC-7 neighbour failure is in the shared FocusTargetState.kt helper; Task 3 fixes it there (scope widened to that file; Home, SectionRow, VideoGrid, Bookmarks instrumented regression runs added); no AC change. Stale watched mark is server lag, pre-existing: idea run collection-watched-mark-lag | owner (standing instruction: recommended) |
| 2026-11-11 | Addendum after review round 1: Task 3 changes put behind opt-in `holdPendingFocusTarget` (default false = base behaviour for every other caller); CollectionDetailScreenContent.kt gets one argument (scope widened past the brief's Out list); VM tracks active save ids per item; Task 4. Manual 3 (player return) stays NOT CHECKED: playback writes progress, needs explicit owner authorization | owner (standing instruction: recommended) |
| 2026-11-11 | Gate 2: ship (judge-1 PASS; manual check for the owner: AC-2 player-return clause, needs a playback progress write) | owner (standing instruction: recommended) |

## Log
- 2026-11-11 created (stage idea) from grid-focus-restore-after-back: design Q2 (deferred; design-architect.md risk 2: CollectionDetailVM.kt:69-70,98-102)
- 2026-11-11 merged back from grid-focus-restore-after-back review round 1 (blind F1): reload through Loading after a Details change also drops the restored focus; reproduce it on a device when triaging
- 2026-11-11 /task:run started; base 0e4449e2; researcher spawned
- 2026-11-11 research.md written (option A recommended, S; 3 questions); analyst spawned
- 2026-11-11 brief.md written (M, 8 ACs, 3 questions answered on recommendation)
- 2026-11-11 Orca worktree created; architect design spawned
- 2026-11-11 design-architect.md written (6 decisions, 5 risks, no questions); test-designer spawned
- 2026-11-11 test-design.md written (8 risks, P0 6); spec committed; gate 1 approved; architect plan spawned
- 2026-11-11 plan written (2 tasks, amendment A1 adopted); architect records A1 in design, test-designer updates rows
- 2026-11-11 A1 recorded in design and test design; spec synced, plan committed; readiness judge spawned
- 2026-11-11 readiness-1 NOT READY (4 must-fix: guard test reshaped, wrong citation, AC-2 row, stale 'pending' phrase); architect plan round 2 and test-designer spawned
- 2026-11-11 readiness must-fix applied (plan + test design), spec synced; readiness round 2 spawned
- 2026-11-11 readiness-2 READY; pasted into the plan; implementer spawned
- 2026-11-11 impl: 22aff7ce (VM + unit tests, 7 red on base), cad9f616 (instrumented guards); check.full green (implementer); qa-device verify spawned
- 2026-11-11 verify round 1: check.full pass; AC-1..6, 8 PASS; AC-7 FAIL (neighbour guard times out 3/3; composable unchanged); Manual 3 NOT CHECKED (playback writes progress); observation: 1/18 stale watched mark after Back. Architect diagnosis spawned
- 2026-11-11 architect addendum (Task 3, FocusTargetState.kt); idea run collection-watched-mark-lag; test-designer relabel and implementer Task 3 spawned
- 2026-11-11 Task 3: d1dea378 (FocusTargetState.kt neighbour request retries); check.full green (implementer); qa-device verify round 2 spawned
- 2026-11-11 verify round 2: AC-1..8 PASS (AC-7 5/5 x3); Manual 3 NOT CHECKED (playback write); Bookmarks focus NOT CHECKED (no test, tab off); HomeSectionRemovalFocusTest red pre-existing -> idea run home-section-removal-focus-test-red; evidence committed; review round 1 spawned
- 2026-11-11 review round 1: blind 1 major + 1 minor, edge 1 (dup of blind major), tests 3 minors, codex 1 major + 1 minor (adversarial); triage-1.md: 6 fix, 1 to gate; architect addendum 2 spawned
- 2026-11-11 architect addendum 2 (Task 4, opt-in flag); test-designer and implementer spawned
- 2026-11-11 Task 4: 48ff9085 (opt-in flag, active save ids, guards); check.full green (implementer); qa-device + incremental review (edge, tests, codex) spawned
- 2026-11-11 review round 2: edge none, codex clean, tests 1 minor + 1 nit (accepted); qa review fix: all ACs PASS, guards red on a021e92c and green on HEAD; evidence committed; final-judge spawned
- 2026-11-11 judge-1 PASS (MANUAL 1); gate 2 ship on standing instruction; squash spawned
- 2026-11-11 squashed to 4ce8a72d, 18988650, 46312999 (diff vs 28f986b0: plan only); next: owner runs /task:ship
- 2026-11-11 shipped: merge cb4cf252 into master; worktree removed; branch deleted
