---
slug: showall-rail-close-focus-shift
title: "Show all grid: closing the side rail with RIGHT puts focus one card row above the card that was focused when the rail opened (base 35bfbbec too)"
stage: done
size: S
tracks: []
kind: bug
branch: prakunin/showall-rail-close-focus-shift
worktree: /home/user1/orca/workspaces/Puber/showall-rail-close-focus-shift
base: 6b087697
spec: .flow/runs/showall-rail-close-focus-shift/brief.md (size S: brief ACs and Plan are the spec)
plan:
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
| 2026-10-18 | Size S, tracks none, kind bug (analyst recommendation; touches TV focus risk area but one file, no shared contract change) | owner (standing instruction: recommended) |
| 2026-10-18 | Q1 fix shape: A, handoff-aware GridFocusMemory | owner (standing instruction: recommended) |
| 2026-10-18 | Q2 Back-closes-rail and tab re-pick covered in the same fix (AC-3) | owner (standing instruction: recommended) |
| 2026-10-18 | Q3 CollectionDetail grid out of scope, recorded under grid-focus-restore-after-back | owner (standing instruction: recommended) |
| 2026-10-18 | Gate 1: approve (brief ACs 1-9, plan, test-design.md) | owner (standing instruction: recommended) |
| 2026-11-11 | Gate 2: ship (judge-1 CONCERNS: AC-3 Back half NOT CHECKED, unreachable on Show all, same as base; tab re-pick device-only); AC-3 wording left as approved, concern reported to the owner | owner (standing instruction: recommended) |

## Log
- 2026-10-17 created (stage idea) from showall-focus-restore-hardening qa (qa-report.md "Review round 1 fixes" and "Base comparison: rail close focus": 5/5 on HEAD, 3/3 on base; History grid keeps the same card). Sibling of showall-first-entry-rail-focus.
- 2026-10-18 /task:run started; base 6b087697; researcher spawned
- 2026-10-18 research.md written (root cause: GridFocusMemory early return on rail handoff; option A recommended); analyst spawned
- 2026-10-18 brief.md written (S, 9 ACs); decisions taken on recommended answers; test-designer spawned
- 2026-10-18 test-design.md written (8 risks, P0 9); gate 1 approved; Orca worktree created; implementer spawned
- 2026-10-18 impl done: 78b3d64d, 33068672, dda4eb23; check.fast green (conductor); qa-device spawned
- 2026-10-18 verify round 1: qa-device reported UNAVAILABLE device (no emulator running); conductor booted puber_tv_36 (emulator-5554, standard command, no data change); verify re-run
- 2026-10-18 verify round 1 done on emulator-5554: AC-1,2,4-9 PASS; AC-3 Back part NOT CHECKED (Back with rail open pops Show all, same as base); evidence 97be2417; review round 1 spawned
- 2026-11-11 resumed; review round 1 re-spawned (blind, tests, codex); earlier round-1 agents left no output
- 2026-11-11 review round 1 done: blind 1 minor (rejected, unproven), tests 3 (2 fix, 1 carried as NOT CHECKED), codex clean; triage-1.md; implementer fix round 1 spawned
- 2026-11-11 fix round 1: e9595021 (tests only); check-named-tests 0 missing; no Co-Authored-By; qa-device round 2 and lens tests round 2 spawned
- 2026-11-11 qa round 2: check.full pass, ShowAllRailCloseFocusTest 5/5, ShowAllScreenContentFocusTest 5/5 (emulator-5554); review round 2 (tests): 1 major + 1 minor, both comment-only fixes; triage-2.md; implementer fix round 2 spawned
- 2026-11-11 fix round 2: 134c5032 (comments only); stage judge; final-judge spawned
- 2026-11-11 judge-1 CONCERNS (no must-fix, no manual); gate 2 ship on standing instruction; squash before ship
- 2026-11-11 squashed to 95fd3a8e, 995b1dd9, 5a55abfe (tree acfbe3d7 unchanged)
- 2026-11-11 shipped: merge 2438f9c8 into master; worktree removed; branch deleted
