---
slug: grid-focus-restore-after-back
title: "Other grid screens restored after a root push and Back may focus the first visible card instead of the card that opened Details (ShowAll got a local fix)"
stage: done
size: M
tracks: []
kind: bug
branch: prakunin/grid-focus-restore-after-back
worktree: /home/user1/orca/workspaces/Puber/grid-focus-restore-after-back
base: e9184b79
spec: docs/superpowers/specs/2026-11-11-grid-focus-restore-after-back.md
plan: docs/superpowers/plans/2026-11-11-grid-focus-restore-after-back.md
rounds: {verify: 2, review: 2, judge: 1}
blocked_by: []
created: 2026-10-17
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
| 2026-11-11 | Size M, tracks none, kind bug (analyst recommendation) | owner (standing instruction: recommended) |
| 2026-11-11 | Q1 scope: Collections + CollectionDetail + Show all live-path fallback C; Bookmarks and History in the device baseline only, fixed here only if the baseline shows the symptom | owner (standing instruction: recommended) |
| 2026-11-11 | Q2 fix shape A: per-screen existing helpers | owner (standing instruction: recommended) |
| 2026-11-11 | Q3 Collections: small local id-keyed memory | owner (standing instruction: recommended) |
| 2026-11-11 | Design Q1: amend AC-7; `reconcile_liveGridFocusedCardRemovedAllAttemptsFailingNeverFocusesTheGrid` becomes the AC-7 case (renamed, expects three failed restores then grid); other cases unchanged | owner (standing instruction: recommended) |
| 2026-11-11 | Design Q2: CollectionDetail in-place refresh after Details changes is out of scope; deferred as an idea run | owner (standing instruction: recommended) |
| 2026-11-11 | Gate 1: approve (spec 511590c1: 9 ACs with AC-7 amended, design 9 decisions, test design P0 13 / P1 13 / manual 7) | owner (standing instruction: recommended) |
| 2026-11-11 | Design delta Q4: accept the AC delta after the baseline (AC-1 guard, AC-4 dropped, AC-5 CollectionDetail only, AC-9 split; Collections production change D3 dropped) | owner (standing instruction: recommended) |
| 2026-11-11 | Design delta Q3: finding the call that moves focus into the tab content after a root Back is not in this run (R8 recorded) | owner (standing instruction: recommended) |
| 2026-11-11 | Gate 1 delta approved (spec and plan updated after the baseline) | owner (standing instruction: recommended) |
| 2026-11-11 | Addendum after verify round 1 (D14-D17): D1 is a test-seam fault, test replaced by a host-entry variant; D2 fixed by holding content focus inactive while the context menu is open; no AC change. Open question 5 (Bookmarks same exposure) deferred as idea run bookmarks-context-menu-focus-loss | owner (standing instruction: recommended) |
| 2026-11-11 | Verify cap (2) reached with D3 (host-entry test red on base and HEAD, same as D1 before it; device restores 6/6). Chosen: drop `collectionDetailHostEntryBeforeTheFirstRestoreAttemptFocusesTheFocusedCard` from this run (P0 test dropped: the seam does not reproduce the device and cannot tell R1 from a seam fault); AC-2 rests on `collectionDetailFocusReturnsToTheFocusedCardAfterReentry` + Manual 3; R1 instrumented coverage deferred as idea run collection-detail-early-entry-test | owner (standing instruction: recommended) |
| 2026-11-11 | Gate 2: ship (judge-1 CONCERNS, no must-fix; manual check for the owner: Show all after Back; concerns: R1 early entry without an instrumented test, HistoryScreenContentTest 10/30 failing identically on base, deferred reload-through-Loading focus loss) | owner (standing instruction: recommended) |

## Log
- 2026-10-17 created (stage idea) from tab-stack-lost-on-root-push: spec Q5 (owner, standing instruction: recommended); ShowAllScreenContent.kt fix 6b47873d is the reference; rootAnchorCaptureRegistry only covers LazyListState screens.
- 2026-10-17 note from showall-focus-restore-hardening: consider extending the Show all last-resort grid focus (fallback C) to the live path (focused card gone while in the grid, all attempts failed).
- 2026-10-18 scope note from showall-rail-close-focus-shift: the CollectionDetail grid likely also shifts focus one row up when the side rail closes with RIGHT (unverified on device); include it when triaging this run
- 2026-11-11 /task:run started; base e9184b79; researcher spawned
- 2026-11-11 research.md written (6 grids; Collections and CollectionDetail unfixed; option A, M; 5 questions); analyst spawned
- 2026-11-11 brief.md written (M, 9 ACs, 3 questions); decisions on recommended answers
- 2026-11-11 Orca worktree created (prakunin/grid-focus-restore-after-back); architect design spawned
- 2026-11-11 design-architect.md written (9 decisions, 7 risks, 2 questions answered on recommendation); test-designer spawned
- 2026-11-11 deferred idea runs created: collection-detail-inplace-refresh, search-reset-after-details
- 2026-11-11 test-design.md written (9 risks, P0 13); spec assembled and committed 511590c1; gate 1 approved; architect plan spawned
- 2026-11-11 qa-device baseline on the unchanged build spawned in parallel with the plan
- 2026-11-11 plan written (3 tasks + P1 baseline + P2 red checkpoint), committed; readiness judge spawned
- 2026-11-11 readiness-1 READY (3 non-blocking notes), pasted into the plan; stage impl, waiting for the baseline
- 2026-11-11 baseline: CollectionDetail loses focus after Back (from Home and Collections, 3/3); Collections, Bookmarks, History restore on base; rail close stable; evidence committed. Collections fix not symptom-driven: architect asked for a scope delta
- 2026-11-11 architect delta (D10-D13): Collections guard-only; plan updated; analyst and test-designer update brief ACs and test-design.md
- 2026-11-11 brief and test-design.md updated for the delta (P0 11, P1 1); spec re-assembled and committed; gate 1 delta approved; implementer spawned
- 2026-11-11 impl part 1: edbdd0f5 (Task 1, AC-7 red on base recorded); red checkpoint tests written uncommitted; qa-device red checkpoint spawned
- 2026-11-11 red checkpoint: CollectionDetail 3 red (timeouts at the focus waits) + 1 guard green; Collections guard green; implementer part 2 spawned
- 2026-11-11 impl part 2: 7102569d (Collections guard), 5ea2899e (CollectionDetail); check.full green (implementer); check-named-tests 0 missing; Implementation record cites 5e0ed3aa for Task 3 (pre-amend SHA, real 5ea2899e); qa-device verify spawned
- 2026-11-11 verify round 1: check.full pass; AC-1,3,6,7 PASS; AC-8 PASS (Show all manual NOT CHECKED, instrumented covered); AC-2, AC-5, AC-9 FAIL: D1 default-landing test still red, D2 context-menu guard red after the change (readiness note 2). Architect addendum spawned
- 2026-11-11 architect addendum (Task 4); idea run bookmarks-context-menu-focus-loss created; test-designer row update and implementer Task 4 spawned
- 2026-11-11 Task 4: 3e1530f0 (D1 test replaced, D2 menu holds content focus inactive); qa-device verify round 2 spawned
- 2026-11-11 verify round 2: check.full pass; D2 fixed (AC-5 PASS); D3 host-entry test red on HEAD as on base; Manual 3 and 7 PASS 6/6. Cap reached; test dropped and deferred (idea run collection-detail-early-entry-test)
- 2026-11-11 f7d887a7 test dropped; evidence and spec sync committed; review round 1 spawned (blind, edge, tests, codex adversarial; ux-designer not matched: no layout change)
- 2026-11-11 review round 1: blind 1 major (deferred to collection-detail-inplace-refresh) + 1 minor (rejected), edge none, tests 2 minors (fix), codex clean (review + adversarial); triage-1.md; implementer fix spawned
- 2026-11-11 review fix: 4efeed73 (AC-5 test extended with BACK-close neighbour assertion); qa-device + incremental review (tests, edge, codex) spawned
- 2026-11-11 review round 2: tests 1 minor (accepted), codex clean; qa: AC-5 test 3/3 x2, History same 10/30 on base and HEAD; evidence committed; final-judge spawned
- 2026-11-11 judge-1 CONCERNS (MANUAL 1); gate 2 ship on standing instruction; squash spawned
- 2026-11-11 squashed to 1a4109ba, 883e5a0d, 194428c7, a9e21035 (diff vs 5b020264: plan Squash section only); next: owner runs /task:ship
- 2026-11-11 shipped: merge eb67973c into master; worktree removed; branch deleted
