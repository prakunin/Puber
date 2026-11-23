---
slug: watch-index-catch-up
title: "Watch-state index lags the server across devices: catch up history to a last-updated mark on start/resume and catalogue entry, and on player exit"
stage: done
size: M
tracks: []
kind: bug
branch: prakunin/watch-index-catch-up
worktree: /home/dev/orca/workspaces/Puber/watch-index-catch-up-2
base: e4c3b5aa
spec: docs/superpowers/specs/2026-11-23-watch-index-catch-up.md
plan: docs/superpowers/plans/2026-11-23-watch-index-catch-up.md
rounds: {verify: 2, review: 7, judge: 1}
blocked_by: []
source:
created: 2026-11-22
migrated:
updated: 2026-11-23
---

## Next
Done; merged as 8192db38. Owner: AC-9 manual check on another client (judge-1.md). Follow-up: catchup-session-wipe-race.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Which events run the catch-up, and how often? | no | answered | Main start (after the WatchState startup slot), every resume, Movies/Series tab entry and player exit; at most once a minute except after player exit |
| Q2 | Refetch Details for series that left watching/serials but are unfinished in the index, in this run? | yes | answered | Yes |
| Q3 | Fetch 50 history entries per page instead of 20 in the catch-up and full sync? | no | answered | Yes, 50; the History screen keeps its own page size |

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-22 | Keep a mark of when the index was last updated and fetch history back to that mark, so a rarely switched-on device is current on its next start | owner |
| 2026-11-22 | Catch-up triggers: Main start (WatchState slot), every resume, Movies/Series tab entry, player exit; at most once a minute except player exit | owner |
| 2026-11-22 | Refetch Details for series that left watching/serials while unfinished in the index, in this run | owner |
| 2026-11-22 | Sync reads history at 50 per page; the History screen keeps its own size | owner |
| 2026-11-23 | codex: required (owner); caps lifted (owner); gates on recommended answers | owner (standing instruction: recommended) |
| 2026-11-23 | Q4: AC-5 tab-entry test in MainVMWatchStateSyncTest; Q5: HistoryVM keeps its own catch-up | owner (standing instruction: recommended) |
| 2026-11-23 | Gate 1: approved | owner (standing instruction: recommended) |
| 2026-11-23 | Codex at its usage limit is skipped for the round, not waited for | owner |
| 2026-11-23 | Gate 2: ship (judge PASS; AC-9 manual check on another client left for the owner) | owner (standing instruction: recommended) |
| 2026-11-23 | Box busy (lease held by release-startup-compilation): Tasks 4-6 proceed before Manual 1; AC-8 runs in verify, ordered=false sends Task 4 back to design | conductor |

## Log
- 2026-11-22 created (stage idea). Owner report: Znieczulenie, fully watched, still listed on the Series tab. In stage on box 192.168.1.110:5555 "Hide watched" was off; switched on during triage at the owner's request. Owner pointed out that opening Details fixes only one device, so the fix belongs in the sync triggers.
- 2026-11-22 found in triage: the index is local per install; sync runs on MainVM start/resume gated by 1h since the last complete sync (lastSyncAt stamped only when the walk reaches the end), History screen catch-up (5 min floor), Details open (one item), manual mark, settings rebuild. The player never writes the index; catalogue entry triggers nothing. A series is finished only if its latest history entry is >= 90% (WatchCompletionPolicy) and it is absent from watching/serials.
- 2026-11-22 research.md (option A recommended) and brief.md (bug, M, 10 ACs) written; owner answered Q1-Q3 with the recommended answers; stage triage.
- 2026-11-22 correction from research: the player does write the index when it marks an episode watched (auto in the last 10% or by hand) via a Details re-read; only an exit without a watched mark writes nothing.
- 2026-11-23 /task:run started; worktree created by orca at /home/user1/orca/workspaces/Puber/watch-index-catch-up on base e4c3b5aa; stage design.
- 2026-11-23 design-architect.md and test-design.md written; spec assembled; gate 1 approved; stage plan.
- 2026-11-23 plan written; readiness-1 NOT READY (3 must-fix), plan round 2 and spec aligned; readiness-2 READY; stage impl.
- 2026-11-23 impl tasks 1-3 done (b5a11d80, d0995e44, ab2ef5ea); box leased by another run, tasks 4-6 continue, AC-8 moved to verify.
- 2026-11-23 impl tasks 4-6 done (686eee8b, ac5d2f78, c7df62a8); check.fast and check.full green (one known-flaky HomeVMIncrementalPublishTest failure on the first full run, green on rerun); check-named-tests 0 missing; stage verify.
- 2026-11-23 qa verify round 1 (888f3b6e): AC-1..AC-8, AC-10 PASS (AC-8: pages 1-75 ordered=true); AC-9 NOT CHECKED; box stage session expired mid-run, worktree build cannot refresh (no client secret); make itest also ran on the host emulator (Makefile isolation defect). Review round 1 done, triage-1 written; fix round 1 started.
- 2026-11-23 review rounds 1-6 with fixes (triage-1..6); last fix 210f055e, check.full green. Box stage lost its session (token expired under a worktree build); master build installed with install-preserve and auth-restore tried; owner must re-pair. Deferred: catchup-session-wipe-race. Paused at the owner request (move to Mac mini); handoff written.
- 2026-11-23 resumed on the Mac mini host: Orca worktree watch-index-catch-up-2 created and switched to prakunin/watch-index-catch-up (210f055e); owner: do not wait for Codex at its usage limit (skip it for the round); stage review round 6
- 2026-11-23 review round 7 (aa19fb51..210f055e): edge, tests, state-path no findings; check.full green (2442 tests); codex skipped (usage limit, owner: do not wait); triage-7.md; stage verify, qa-device round 2 spawned
- 2026-11-23 qa-device verify round 2 on the box (master build refreshed the session, auth-save, then worktree APK): AC-2 PASS (device+unit), AC-7 PASS (device observation), AC-8 re-observed (157 ordered=true), AC-4 unit only, AC-9 NOT CHECKED (manual, another client); evidence 6d49f6e5; install-preserve regex failure on apksigner 36 was worked around with a shim (already fixed on master by release-startup-compilation); stage judge
- 2026-11-23 final-judge gate round 1: PASS, must-fix none, MANUAL 1 (AC-9); branch squashed to 3 commits (641b843f, 183c10b1, 0314681d; tree identical to 6d49f6e5; backup ref backup/watch-index-catch-up-pre-squash); Co-Authored-By count 0; gate 2 ship; stage handover
- 2026-11-23 shipped: merged into master as 8192db38 (--no-ff); Orca worktree watch-index-catch-up-2 removed; branch and backup ref kept for the owner
