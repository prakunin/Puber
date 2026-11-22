---
slug: card-progress-watching-duration
title: "Card progress readers (Item.toWatchStateOrNull, VideoItemUIMapper.toLocalWatchState, WatchStateRepository) take the length from watching.duration, which item payloads leave out"
# Stages: idea | triage | design | plan | impl | verify | review | judge | handover | shipped | done | blocked | dropped
# dropped = an idea the owner decided not to do (owner only).
stage: done
# Size: S | M | L (set by triage)
size: M
# Tracks (optional): ui, perf
tracks: []
# Kind: bug | feature | improvement | refactor | idea
kind: bug
branch: prakunin/card-progress-watching-duration
worktree: /home/user1/orca/workspaces/Puber/card-progress-watching-duration
base: c506d0bd
spec: /home/user1/orca/workspaces/Puber/card-progress-watching-duration/docs/superpowers/specs/2026-11-22-card-progress-watching-duration.md
plan: /home/user1/orca/workspaces/Puber/card-progress-watching-duration/docs/superpowers/plans/2026-11-22-card-progress-watching-duration.md
rounds: {verify: 1, review: 2, judge: 1}
# Q-ids or a free reason, set when stage = blocked
blocked_by: []
# Slug of the run a deferred finding came from; empty otherwise
source: resume-progress-anomaly
created: 2026-11-22
# Set only by a migration from an older orchestrator; lint then skips history checks
migrated:
updated: 2026-11-22
---

## Next
nothing (done); owner manual check AC-7 on the box

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
<!-- Blocks: yes when the answer changes scope or an AC. Status: open / assumed / answered. -->

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-22 | Q1 fix without capturing the real item payload first; AC-7 records field names | owner (standing instruction: recommended) |
| 2026-11-22 | Q2 movie watched verdict from videos[0] in the same shared rule (AC-4) | owner (standing instruction: recommended) |
| 2026-11-22 | Q3 no DAO length guard now; idea run if AC-7 shows a lost length | owner (standing instruction: recommended) |
| 2026-11-22 | Run end to end on recommended answers, gates and ship included | owner (standing instruction: recommended) |
| 2026-11-22 | codex: required on every round | owner |
| 2026-11-22 | caps lifted | owner |
| 2026-11-22 | Device evidence on the TV box 192.168.1.110:5555 only | owner |
| 2026-11-22 | Q4 correct the Item.watching KDoc in the verify fix-up if AC-7 shows no item-level field | owner (standing instruction: recommended) |
| 2026-11-22 | Gate 1: approved (M, no tracks, AC-1..AC-8) | owner (standing instruction: recommended) |
| 2026-11-22 | Gate 2: ship (judge-1 CONCERNS; AC-7 NOT CHECKED left as a manual check for the owner; R5 behaviour pinned, wanted-ness open) | owner (standing instruction: recommended) |

## Log
- 2026-11-22 created (stage idea)- 2026-11-22 deferred from resume-progress-anomaly (design-architect-2.md, Q4, risk 3): WatchingInfo.duration defaults to 0 because KinoPub item payloads carry the length on the video (videos[].duration), not in watching; the Details mapper is fixed in resume-progress-anomaly, the card readers at WatchStateRepository.kt:508 and VideoItemUIMapper.kt:125 may have the same gap
- 2026-11-22 run started, base c506d0bd, stage triage (research first)
- 2026-11-22 research.md and brief.md written (bug, M, no tracks); Q1-Q3 answered on recommendations; stage design
- 2026-11-22 design-architect.md and test-design.md written; spec assembled (7a768c24); gate 1 approved on standing instruction; stage plan
- 2026-11-22 plan written, readiness-1 READY; plan committed; stage impl
- 2026-11-22 impl done: e6e14d44, db292ad0; check.fast green; all test-design names present (check-named-tests.py parsed 0 names, checked by hand); stage verify
- 2026-11-22 verify round 1: check.full green, AC-1..6, AC-8 PASS, AC-7 NOT CHECKED (no movie card on the box; playback writes to the owner account); evidence 92d557be; stage review
- 2026-11-22 review round 1: codex clean, blind/edge no findings, tests F1 major + F2 minor; triage-1 marks both fix (tests only)
- 2026-11-22 fix 41b96bda (tests only); review round 2 (edge, tests, codex incremental) clean; triage-2 no findings
- 2026-11-22 check.full at 41b96bda green (conductor run); stage judge
- 2026-11-22 judge-1 CONCERNS (no must-fix, MANUAL 1); co-authored-by count 0; gate 2 ship on standing instruction
- 2026-11-22 squashed to eb6c9b16 (code+tests), 31695f78 (docs); tree unchanged; check.fast green
- 2026-11-22 shipped: merge 918e8539 into master; worktree removed; stage done
