---
slug: seek-blocked-by-skip-prompt
title: D-pad seeking halts when the playhead enters a skip-intro / next-episode section
stage: done
size: M
tracks: [ui]
kind: bug
branch: prakunin/seek-blocked-by-skip-prompt
worktree: /home/user1/orca/workspaces/Puber/seek-blocked-by-skip-prompt
base: 6b087697
spec: docs/superpowers/specs/2026-11-10-seek-blocked-by-skip-prompt.md
plan: docs/superpowers/plans/2026-11-10-seek-blocked-by-skip-prompt.md
rounds: {verify: 2, review: 2, judge: 1}
blocked_by: []
source:
created: 2026-11-10
migrated:
updated: 2026-11-10
---

## Next
Done. Owner manual check: AC-9 hold Right on a real remote through an intro and the credits lead-in.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Skip prompt default focus: X or action? | yes | answered | Action button: OK skips, Back dismisses |
| Q2 | Keep the next-episode prompt returning after a seek that follows a cancel? | no | answered | Yes, keep the tested behaviour |
| Q4 | Pass Up/Down and Play/Pause through the prompt (architect Q1)? | no | answered | Not in this run; follow-up idea |
| Q3 | With the controls up and the seek bar focused, should an appearing prompt take focus? | yes | answered | No, focus stays on the seek bar |

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-10 | Q1: skip prompt focuses its action button (recommended) | owner (standing instruction: recommended) |
| 2026-11-10 | Q2: next-episode prompt keeps returning after a seek following a cancel (recommended) | owner (standing instruction: recommended) |
| 2026-11-10 | Q3: an appearing prompt does not take focus from the seek bar (recommended) | owner (standing instruction: recommended) |
| 2026-11-10 | Q4: Up/Down and Play/Pause pass-through deferred to a follow-up idea | owner (standing instruction: recommended) |
| 2026-11-10 | Gate 1: spec approved (size M, track ui, 9 ACs) | owner (standing instruction: recommended) |
| 2026-11-10 | Gate 2: ship (judge CONCERNS; Concern 1 deferred as prompt-hide-focus-under-controls; AC-9 manual for the owner) | owner (standing instruction: recommended) |
| 2026-11-10 | Land before player-overlays-port; that branch rebases onto this (research recommendation) | owner (standing instruction: recommended) |

## Log
- 2026-11-10 created (stage triage). Owner report (ru): seeking with Left/Right stops when it reaches the "Skip intro" / "Next episode" section; the prompt must be dismissed, and it pops up again on the next seek.
- 2026-11-10 research.md written (option 1 recommended)
- 2026-11-10 triage: bug, M, track ui, 9 ACs; Q1-Q3 answered by standing instruction; worktree created (orca)
- 2026-11-10 design: architect (9 decisions), ux-designer (no stand governs the prompt; no sizes change), test-designer (P0 13, P1 10, manual 5); spec e36b7055; gate 1 approved
- 2026-11-10 plan 1cd4fba6 (5 tasks); readiness-1 READY (advisory: retry focus on false too; schedule Task 3 red run before Task 4)
- 2026-11-10 impl: Tasks 1-5 a90ef78f..35f9866e; Task 3 red run on emulator-5554 (3 real reds, 5 matcher-only, step 4 refuted); check.full green per implementer, check.fast re-run by conductor green
- 2026-11-10 verify round 1 (qa-device): AC-1..AC-8 PASS, AC-9 NOT CHECKED (no intro data in worktree build); 12/12 instrumented green; evidence b6660c74
- 2026-11-10 review round 1: blind 3, edge 0, tests 4, codex 1 confirmed, ux 4; triage-1: fix 8, reject 2, owner manual 1; idea runs prompt-key-passthrough, introdb-imdb-id-prefix
- 2026-11-10 fix round 1 d95aa91d..37d0527c; red run b7854fbb (red-run-b7854fbb.md); verify round 2: AC-1..AC-8 PASS, AC-9 NOT CHECKED, evidence 1d38134a; review round 2: codex clean, edge/tests/ux minors and nits only; triage-2: accept 5
- 2026-11-10 judge-1 CONCERNS (must fix none, manual 1: AC-9 on a real remote); deferred prompt-hide-focus-under-controls; gate 2 ship
- 2026-11-10 squashed to 3 commits 5d91fd66..a2db903a, tree 3660bf5e unchanged
- 2026-11-10 shipped: merge 0712c138 on master; worktrees seek-blocked-by-skip-prompt and seek-red-b7854fbb removed; branches deleted
