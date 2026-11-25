---
slug: catalogue-filters-persist
title: "Keep the catalogue filter per tab across restarts"
# Stages: idea | triage | design | plan | impl | verify | review | judge | handover | shipped | done | blocked | dropped
# dropped = an idea the owner decided not to do (owner only).
stage: done
# Size: S | M | L (set by triage)
size: M
# Tracks (optional): ui, perf
tracks: []
# Kind: bug | feature | improvement | refactor | idea
kind: feature
branch: prakunin/catalogue-filters-persist
worktree:
base: ef5987ec
spec: /home/dev/orca/workspaces/Puber/catalogue-filters-persist/docs/superpowers/specs/2026-11-25-catalogue-filters-persist.md
plan: /home/dev/orca/workspaces/Puber/catalogue-filters-persist/docs/superpowers/plans/2026-11-25-catalogue-filters-persist.md
rounds: {verify: 3, review: 1, judge: 1}
# Q-ids or a free reason, set when stage = blocked
blocked_by: []
# Slug of the run a deferred finding came from; empty otherwise
source: catalogue-filters
created: 2026-11-24
# Set only by a migration from an older orchestrator; lint then skips history checks
migrated:
updated: 2026-11-25
---

## Next
nothing (done); merged as e5bfd7d1

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Key the store per browse config id or per TabType? | no | answered | Per config id (recommended) |
| Q2 | Clear stored filters on logout? | no | answered | No, keep per device like other catalogue settings (recommended) |
| Q3 | Stored «Аниме» genre while show-anime is off: drop or skip? | no | answered | Skip on restore, keep stored (recommended) |
<!-- Blocks: yes when the answer changes scope or an AC. Status: open / assumed / answered. -->

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-25 | Run end to end on recommended answers incl. gates; caps lifted; codex and antigravity required every round (skip on limit, do not wait) | owner (standing instruction) |
| 2026-11-25 | Q1 per config id; Q2 keep on logout; Q3 skip anime genre on restore, keep stored; size M, tracks none | owner (standing instruction: recommended) |
| 2026-11-25 | Gate 1: approved (M, tracks none, 9 ACs) | owner (standing instruction: recommended) |
| 2026-11-25 | Gate 2: ship (judge-1 PASS) | owner (standing instruction: recommended) |
| 2026-11-25 | Caps lifted (owner); codex: required (owner) - ran every round; antigravity every round | owner |

## Log
- 2026-11-24 created (stage idea) from the L split of catalogue-filters (brief.md Split; owner standing instruction: recommended): persist the chosen ContentFilter per tab. Part 3 of the catalogue-filters split; independent of part 4.
- 2026-11-25 run started, base ef5987ec; research
- 2026-11-25 research done (option A recommended); triage: M, no tracks, 9 ACs
- 2026-11-25 worktree /home/dev/orca/workspaces/Puber/catalogue-filters-persist on prakunin/catalogue-filters-persist; design
- 2026-11-25 design + test design done; spec assembled; gate 1 approved (standing instruction)
- 2026-11-25 plan written (3 tasks); readiness-1 READY; impl
- 2026-11-25 impl done (46b0ccbd, 9c4975ce, 5db9d299, 14278faa); check.full green; 31/31 named tests present; verify
- 2026-11-25 qa round 1: AC-1..AC-9 PASS; defect 1: coldOpenWithRestoredFilter_focusesTheFirstCardNotReset passes with the gate reverted
- 2026-11-25 fix 121feab7 (itest enters header during Loading); qa round 2 on the itest
- 2026-11-25 qa round 2: itest red without gate, green with it (360c205c); review round 1 on ef5987ec..c205c62e; ux-designer not run: no visual/layout change (canFocus only)
- 2026-11-25 review round 1: codex + antigravity clean; 5 fix (1 focus regression, 4 tests/comment), 2 reject; triage-1.md
- 2026-11-25 fix round 1: b1f0cd09, 87c7be8c, 18d72ea4; review round 2 on c205c62e..18d72ea4
- 2026-11-25 review round 2: codex, antigravity, state-path clean; edge F1 regression from fix round 1 -> fix; triage-2.md
- 2026-11-25 fix round 2: b0cee092; review round 3 on 18d72ea4..b0cee092
- 2026-11-25 review round 3: edge, antigravity clean; state-path + codex: guard also blocks arriving tab -> fix; tests: guard untested -> fix; triage-3.md
- 2026-11-25 fix round 3: d6ec6134; review round 4 on b0cee092..d6ec6134
- 2026-11-25 review round 4: production change holds (edge, state-path); new tests broken (blocker: wrong «Сброс» node; major: handoff refused from Closed); 1 accept; triage-4.md
- 2026-11-25 fix round 4: 3e5248d0 (tests only); review round 5
- 2026-11-25 review round 5: clean (tests, edge, codex, antigravity); triage-5.md; qa round 3
- 2026-11-25 qa round 3 (add1fb05): all ACs PASS, mutations a-d red, classes green, M1-M4 + empty-filter on the box PASS; defect: detekt ComplexCondition
- 2026-11-25 detekt fix 91a90d71; check.full green (BUILD SUCCESSFUL 2m 12s); review round 6
- 2026-11-25 review round 6: clean; triage-6.md; judge
- 2026-11-25 judge-1 PASS; gate 2 ship (standing instruction); squash next
- 2026-11-25 squashed to 4 commits (cdb191ef, 102d6764, 67862289, 7245ecb1), tree unchanged 00c1aaba; ship
- 2026-11-25 shipped: merged as e5bfd7d1 (--no-ff); worktree removed; branch deleted
