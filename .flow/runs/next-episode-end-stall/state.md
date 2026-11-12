---
slug: next-episode-end-stall
title: "After the next-episode countdown is cancelled, the end of the episode stalls: no move to the next episode, seek bar at the start, endless buffering"
# Stages: idea | triage | design | plan | impl | verify | review | judge | handover | shipped | done | blocked | dropped
# dropped = an idea the owner decided not to do (owner only).
stage: done
# Size: S | M | L (set by triage)
size: M
# Tracks (optional): ui, perf
tracks: []
# Kind: bug | feature | improvement | refactor | idea
kind: bug
branch: prakunin/next-episode-end-stall
worktree: /home/user1/orca/workspaces/Puber/next-episode-end-stall
base: 4db9c0d4
spec: docs/superpowers/specs/2026-11-12-next-episode-end-stall.md
plan: docs/superpowers/plans/2026-11-12-next-episode-end-stall.md
rounds: {verify: 2, review: 3, judge: 1}
# Q-ids or a free reason, set when stage = blocked
blocked_by: []
# Slug of the run a deferred finding came from; empty otherwise
source:
created: 2026-11-12
# Set only by a migration from an older orchestrator; lint then skips history checks
migrated:
updated: 2026-11-12
---

## Next
nothing; shipped

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | At the end after a cancel: switch at once or show the countdown again? | yes | answered | Switch at once |
| Q2 | If a panel is open when the dismissed episode ends, does closing the panel start the next episode? | yes | answered | Yes, same rule as with no panel (AC-6) |
| Q3 | Include hardening of play() from ENDED (stall recovery, Next focus on ended controls)? | no | answered | No; follow-up run player-ended-play-hardening |
<!-- Blocks: yes when the answer changes scope or an AC. Status: open / assumed / answered. -->

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-12 | Research Q1: Cancel on the next-episode prompt only hides it; the next episode still starts at the end (owner's report states this expectation) | owner |
| 2026-11-12 | Q1: switch at once at the end after a cancel | owner (standing instruction: recommended) |
| 2026-11-12 | Q2: closing a panel on an ended, dismissed episode starts the next episode (AC-6) | owner (standing instruction: recommended) |
| 2026-11-12 | Q3: play()-from-ENDED hardening is a follow-up run, not this one | owner (standing instruction: recommended) |
| 2026-11-12 | Gate 1: approved (size M, no tracks, 8 ACs, P0 10 / P1 5 / manual 4; design risk 7 accepted: no in-player way remains to stay on a finished series episode with a next one) | owner (standing instruction: recommended) |
| 2026-11-12 | Plan Q1: onPanelClosed() falls through to checkEarlyNextEpisode() when no next episode can be played (keeps opener focus) | owner (standing instruction: recommended) |
| 2026-11-12 | Review cap reached with two minor regressions from fix round 1 (triage-2): one more round, recommended because both come from the last fix; no third device round (verify cap; unit tests carry the 1 ms re-check guards) | owner (standing instruction: recommended) |
| 2026-11-12 | Gate 2: ship (judge-1 PASS; manual M1 X button and M3 AC-6 on the device listed) | owner (standing instruction: recommended) |

## Log
- 2026-11-12 created (stage idea). Owner report (repeats): the episode ends, the next-episode countdown prompt appears; the owner cancels it and watches the episode to the end. At the end the player does not move to the next episode: it stays on the current one, the seek bar jumps to the start, buffering shows and nothing plays. Leaving the player and picking the next episode manually works.
- 2026-11-12 research.md (researcher, root cause PlayerVM.kt:1371-1381) and brief.md (analyst: bug, M, no tracks, 8 ACs) written; questions answered by standing instruction; follow-up run player-ended-play-hardening captured; stage triage
- 2026-11-12 /task:run started; Orca worktree created; stage design
- 2026-11-12 design-architect.md and test-design.md written; spec assembled; gate 1 approved by standing instruction; stage plan
- 2026-11-12 plan written (2 tasks) and committed
- 2026-11-12 readiness-1 NOT READY (3 doc items), architect round 2 (aadb323a), readiness-2 READY; stage impl
- 2026-11-12 impl: Task 1 245d698c, Task 2 1239fd91; check.full green (implementer); named tests all present; red runs recorded (7 red, 4 guards green on base); stage verify
- 2026-11-12 qa-device verify round 1: check.full pass; AC-1..5, 7, 8 PASS (AC-5, AC-7 unit only); AC-6 PASS unit, NOT CHECKED on device (M3 timing); M1 X-button path not reached (M2 Back path covers the same cancelCountdown); evidence 3bf1209d; stage review
- 2026-11-12 review round 1: blind, edge, tests, codex (clean); triage-1: #1 release inside the ExoPlayer callback (fix), #2-#4 test gaps (fix)
- 2026-11-12 fix round 1: afb8b9b3 (switch deferred out of the ended callback), a7027fa1 (tests); qa verify round 2 PASS on device for AC-1/2/3/8 (ce2444ea), AC-6 device and M1 NOT CHECKED; review round 2: tests clean, edge + codex two minor regressions (triage-2), one more round
- 2026-11-12 fix round 2: babd220b (closing + panel re-check in the deferral, 2 red-run tests), check.full green; review round 3 (edge, tests, codex) clean (triage-3); stage judge
- 2026-11-12 judge-1 PASS (check.full re-run green at babd220b, merge with master clean); gate 2 ship by standing instruction; stage handover
- 2026-11-12 squashed to 2 commits (3025704c, 8af58be3), tree cdb4efd6 unchanged; backup ref backup/next-episode-end-stall-presquash
- 2026-11-12 shipped: merged into master as b3605125 (--no-ff); Orca worktree removed; branch and backup ref deleted
