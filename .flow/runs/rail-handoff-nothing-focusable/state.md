---
slug: rail-handoff-nothing-focusable
title: "Back from the open rail over a screen with nothing focusable fails its handoff after 5 s and reopens the rail, so only a tab pick leaves it"
# Stages: idea | triage | design | plan | impl | verify | review | judge | handover | shipped | done | blocked | dropped
# dropped = an idea the owner decided not to do (owner only).
stage: done
# Size: S | M | L (set by triage)
size: M
# Tracks (optional): ui, perf
tracks: []
# Kind: bug | feature | improvement | refactor | idea
kind: bug
branch: prakunin/rail-handoff-nothing-focusable
worktree: /home/dev/orca/workspaces/Puber/rail-handoff-nothing-focusable
base: c9e98129
spec: /home/dev/orca/workspaces/Puber/rail-handoff-nothing-focusable/docs/superpowers/specs/2026-11-25-rail-handoff-nothing-focusable.md
plan: /home/dev/orca/workspaces/Puber/rail-handoff-nothing-focusable/docs/superpowers/plans/2026-11-25-rail-handoff-nothing-focusable.md
rounds: {verify: 2, review: 2, judge: 1}
# Q-ids or a free reason, set when stage = blocked
blocked_by: []
# Slug of the run a deferred finding came from; empty otherwise
source: showall-rail-back-exits-app
created: 2026-11-25
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
| 2026-11-25 | rail-pick-during-initial-handoff folded into this run (same DrawerState handoff code) | owner |
| 2026-11-25 | codex: required on every round (at usage limit: skip, do not wait); antigravity on every round | owner (standing instruction) |
| 2026-11-25 | all gates on recommended answers, no questions | owner (standing instruction: recommended) |
| 2026-11-25 | Q1 a failed Back/Right or cold-start handoff leaves the rail collapsed (root and pushed); Left reveals it. Q2 a second Back during a pending Back handoff with the exit armed leaves the app within the 3 s window. Q3 keep: a tab pick into a never-focusable tab reopens the rail | owner (standing instruction: recommended) |
| 2026-11-25 | R8 (handoffKind == Dismiss in the exit gate) accepted untested: the harness cannot raise a dialog; state-path round 1 traced it as needed and correct | conductor |
| 2026-11-25 | Gate 2: ship (judge-1 CONCERNS, no must-fix); squash to <=3 commits before /task:ship | owner (standing instruction: recommended) |
| 2026-11-25 | D1 re-pick of the selected tab is Dismiss (accepted); Gate 1 approved as designed (M, AC-1..AC-12) | owner (standing instruction: recommended) |

## Log
- <YYYY-MM-DD> created (stage idea)
- 2026-11-25 created (stage idea) from showall-rail-back-exits-app design revision Q1 (/home/dev/Puber/.flow/runs/showall-rail-back-exits-app/design-architect.md): with nothing focusable, Back from the open rail starts a handoff that fails after 5 s and reopens the rail (ContentFocusHandoff.kt:120-137); base behaviour.
- 2026-11-25 /task:run started; base c9e98129; rail-pick-during-initial-handoff folded in (owner)
- 2026-11-25 research.md, brief.md: M, bug, no tracks, 12 ACs; Q1-Q3 recommended; worktree /home/dev/orca/workspaces/Puber/rail-handoff-nothing-focusable
- 2026-11-25 design + test design; spec committed; gate 1 approved (standing); stage plan
- 2026-11-25 plan + readiness-1 READY (R8 uncovered: reviewers); stage impl
- 2026-11-25 impl: dfca2744, b3ed05c5, ad0cc5bf, b539a6cb, a746c00f; check.full green; named tests 26/26; stage verify
- 2026-11-25 qa round 1: all ACs PASS; R red rows red at the named assertions; G 46/46; mutations killed except M9-same-id (unit only); D3 not run (Favourites not empty); evidence fda01538; stage review
- 2026-11-25 review round 1: triage-1 E2/E3 fix, E1/E4-E6 rejected (E4 listed for the owner)
- 2026-11-25 fixes 60705da9 (E2), b163ed42 (E3); HEAD 8f119fa2
- 2026-11-25 review round 2 clean; qa round 2: M9-same-id killed 3/3 (evidence 24c264aa); stage judge
- 2026-11-25 judge-1 CONCERNS (R8 untested, E4 slow cold start, E1 stale profile lines); gate 2 ship (standing)
- 2026-11-25 squashed to 107a4134, 54fc7026, 69dc0b17 (tree unchanged); waiting for the owner's /task:ship
- 2026-11-25 shipped: merge b1bb0d84; worktree removed
