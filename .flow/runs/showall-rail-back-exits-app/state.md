---
slug: showall-rail-back-exits-app
title: "Back from the expanded side rail in Show all once left the app"
# Stages: idea | triage | design | plan | impl | verify | review | judge | handover | shipped | done | blocked | dropped
# dropped = an idea the owner decided not to do (owner only).
stage: done
# Size: S | M | L (set by triage)
size: M
# Tracks (optional): ui, perf
tracks: []
# Kind: bug | feature | improvement | refactor | idea
kind: bug
branch: prakunin/showall-rail-back-exits-app
worktree: /home/dev/orca/workspaces/Puber/showall-rail-back-exits-app
base: f88f3079
spec: /home/dev/orca/workspaces/Puber/showall-rail-back-exits-app/docs/superpowers/specs/2026-11-25-showall-rail-back-exits-app.md
plan: /home/dev/orca/workspaces/Puber/showall-rail-back-exits-app/docs/superpowers/plans/2026-11-25-showall-rail-back-exits-app.md
rounds: {verify: 7, review: 4, judge: 1}
# Q-ids or a free reason, set when stage = blocked
blocked_by: []
# Slug of the run a deferred finding came from; empty otherwise
source: catalogue-poster-grid
created: 2026-11-24
# Set only by a migration from an older orchestrator; lint then skips history checks
migrated:
updated: 2026-11-25
---

## Next
nothing (done)

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
<!-- Blocks: yes when the answer changes scope or an AC. Status: open / assumed / answered. -->

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-25 | check-named-tests SKIPPED on armedAtRoot_thenPushAndPop_nextBackOpensRailNotExit is a checker false positive (the xit( marker matches "NotExit("); test kept as designed | conductor |
| 2026-11-25 | Q3 decision point D1: the drawer reaches Open after a push with no Left; defect 3 stays, Task 4 goes ahead | conductor (per plan D1 rule) |
| 2026-11-25 | showall-first-entry-rail-focus folded into this run (same Back/rail handler order) | owner |
| 2026-11-25 | codex: required on every round (at usage limit: skip, do not wait); antigravity on every round | owner (standing instruction) |
| 2026-11-25 | all gates on recommended answers, no questions | owner (standing instruction: recommended) |
| 2026-11-25 | Q1 Back with the rail open over a pushed screen closes the rail only; the next Back pops. Q2 exit toast only when Back closes the rail at a tab root; never on a pushed screen; any pop disarms. Q3 if the box check cannot name what moves focus into the rail on Show all entry, split it back to showall-first-entry-rail-focus and ship the Back fix alone | owner (standing instruction: recommended) |
| 2026-11-25 | Gate 2: ship (judge-1 PASS); squash to <=3 commits before /task:ship | owner (standing instruction: recommended) |
| 2026-11-25 | Gate 1: approved as designed (M, Option A, AC-1..AC-11) | owner (standing instruction: recommended) |

## Log
- 2026-11-24 created (stage idea) from catalogue-poster-grid judge-1 concern 5 (qa round 1 observation 5): Back from the expanded rail in Show all once left the app. Not reproduced deliberately; predates the run per qa. The first-entry rail focus part is covered by showall-first-entry-rail-focus.
- 2026-11-24 from catalogue-filters qa round 2: reproducible on the box: Show all grid (Movies «Все фильмы»), Right, Left (rail expands), Back -> the Show all screen pops instead of closing the rail; the same from a card, so not caused by the filter header. Related trace in showall-first-entry-rail-focus (RailOpenBackHandler vs TabBackHandler order).
- 2026-11-25 /task:run started; base f88f3079; showall-first-entry-rail-focus folded in (owner)
- 2026-11-25 research.md, brief.md: M, bug, no tracks, 11 ACs; Q1-Q3 recommended; worktree /home/dev/orca/workspaces/Puber/showall-rail-back-exits-app
- 2026-11-25 baseline.md (box probe), design-architect.md, test-design.md; spec committed; gate 1 approved (standing); stage plan
- 2026-11-25 readiness-1 NOT READY (gate breaks Left on screens with nothing focusable; M7/M8 P0 rows); architect revised design + plan (unhandled-Left listener); design Q1 deferred to rail-handoff-nothing-focusable
- 2026-11-25 readiness-2 READY; plan committed; stage impl
- 2026-11-25 D1/D2 on the box (d1-d2.md): all red rows red on base; 5 MainRailBackTest red rows green at 5a152b4c; guards backAtTabRoot_opensRailThenArmsThenExits (both commits) and leftFromPushedGrid_opensRail (base) red; Q3 -> Task 4
- 2026-11-25 impl done: harness fix 93c4fefd, Task 4 f4ec56aa, renames 8cc8daa0; named tests 34/34 (one checker false positive); stage verify
- 2026-11-25 qa round 1: device AC-1..AC-7, AC-9, AC-10 PASS; AC-8 PASS on 3/4 paths; AC-11 FAIL (6 instrumented red: harness race x4, backAtOtherTabRoot regression, leftWithNothingFocused); playback side effect on owner account (0:04 on an episode) noted
- 2026-11-25 verify fixes: 0511a7c8, d5cd533c (harness waits), 1fd30c7a (production: unhandled-Left guard reads Compose focus), 6379bd8b
- 2026-11-25 qa round 2 (evidence 8d9d19a7): AC-1..AC-10 PASS (some instrumented only), MainRailBackTest 10/10 x2, guards green; AC-11 FAIL: leftWithNothingFocused still red after 1fd30c7a; observation: with screencaps between presses the third Back at Home reopened the rail (likely the 3 s window expired)
- 2026-11-25 fix: test order (2bf56b89), comment (b3933ff8); 1fd30c7a production change turned out not needed for the defect (kept; flag for review)
- 2026-11-25 qa round 3: AC-11 PASS; all ACs PASS except AC-8 partly NOT CHECKED (Left during real Loading, shelf tile) and R11 step 5 NOT CHECKED; evidence c6b25f49; stage review
- 2026-11-25 review round 1: triage-1 A1 major + A2-A7 fix, A8-A9 rejected
- 2026-11-25 review round 1 fixes: ea5eef7a, 00be3dcb, 8d0fb937, 4a962b04, 4b346104, 876ea858 (+plan/spec docs); check.full green; HEAD 773b60f2
- 2026-11-25 review round 2 + qa rounds 4-5: triage-2 B1 resolved by mutations, B2 (focus clear unproven) and B3 (Center on collapsed rail) fix, B4 resolved (6724d512)
- 2026-11-25 review round 3 + qa round 6 (B2/B3 mutations killed, evidence 1c82598a); triage-3: C1 resolved, C2/C3 fix
- 2026-11-25 review round 4 + qa round 7 (evidence 8721db44); triage-4: D1 resolved, D2-D4 accepted, D5/D7 rejected, D6 deferred to rail-pick-during-initial-handoff; stage judge
- 2026-11-25 judge-1 PASS (MANUAL 4); gate 2 ship (standing)
- 2026-11-25 squashed to 1f6bc4b4, 6178ac62, 60b399d1 (tree unchanged, compile green); waiting for the owner's /task:ship
- 2026-11-25 shipped: merge 06413281; worktree removed
