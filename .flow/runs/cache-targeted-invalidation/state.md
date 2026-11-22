---
slug: cache-targeted-invalidation
title: "Targeted cache invalidation: no markStale on each progress save, TV home refresh coalesced, CachedFeed epoch maps bounded"
# Stages: idea | triage | design | plan | impl | verify | review | judge | handover | shipped | done | blocked | dropped
# dropped = an idea the owner decided not to do (owner only).
stage: done
# Size: S | M | L (set by triage)
size: M
# Tracks (optional): ui, perf
tracks: [perf]
# Kind: bug | feature | improvement | refactor | idea
kind: refactor
branch: prakunin/cache-targeted-invalidation
worktree: /home/user1/orca/workspaces/Puber/cache-targeted-invalidation
base: 40762767
spec: /home/user1/orca/workspaces/Puber/cache-targeted-invalidation/docs/superpowers/specs/2026-11-22-cache-targeted-invalidation.md
plan: /home/user1/orca/workspaces/Puber/cache-targeted-invalidation/docs/superpowers/plans/2026-11-22-cache-targeted-invalidation.md
rounds: {verify: 2, review: 2, judge: 1}
# Q-ids or a free reason, set when stage = blocked
blocked_by: []
# Slug of the run a deferred finding came from; empty otherwise
source: cache-layer-rework
created: 2026-11-21
# Set only by a migration from an older orchestrator; lint then skips history checks
migrated:
updated: 2026-11-22
---

## Next
Done. Follow-ups: resume-progress-anomaly (marktime decode), tv-launcher-channel-logo

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
<!-- Blocks: yes when the answer changes scope or an AC. Status: open / assumed / answered. -->

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-22 | Run end to end on recommended answers, gates and ship included | owner (standing instruction: recommended) |
| 2026-11-22 | codex: required (owner) | owner |
| 2026-11-22 | caps lifted (owner) | owner |
| 2026-11-22 | Q1: mark details stale once at player exit and once at background, each only if a save succeeded since the last mark; process kill in front falls back to the 10 min expiry | owner (standing instruction: recommended) |
| 2026-11-22 | Q2: defer the TV home refresh for Watched during playback to exit and background too | owner (standing instruction: recommended) |
| 2026-11-22 | Gate 1: approve | owner (standing instruction: recommended) |
| 2026-11-22 | Gate 2: ship | owner (standing instruction: recommended) |
| 2026-11-22 | Q3: keep the epoch-map bound (E1) in this run, unit-level evidence for it | owner (standing instruction: recommended) |

## Log
- 2026-11-21 created (stage idea) as split item B4 of cache-layer-rework (owner Q1, 2026-11-21); scope, seed ACs and risks in .flow/runs/cache-layer-rework/brief.md and research.md. Absorbs cached-feed-key-maps. After startup-settled-signal. Progress must still reach the next Details open (e.g. refresh once on player exit).
- 2026-11-22 /task:run started from master 40762767 (cache-decoded-memory-tier merged 3d820f83); researcher spawned
- 2026-11-22 research.md written (option A, 4 questions); analyst spawned
- 2026-11-22 brief.md: M, refactor, perf, 10 ACs, 3 questions answered on recommendation; Orca worktree /home/user1/orca/workspaces/Puber/cache-targeted-invalidation (branch prakunin/cache-targeted-invalidation)
- 2026-11-22 design-architect.md: 12 decisions, 8 risks, no questions (note: qa baseline has temporary PUBERBASE logs in the worktree; must be reverted before impl); test-designer spawned
- 2026-11-22 test-design.md: 13 risks, P0 13, P1 9, P2 1, MANUAL 4; waiting for qa baseline
- 2026-11-22 baseline.md: 7.6 min window, 2.0 marks/min and 2.1 TV home refreshes/min (one per save), switch waited for a details refetch; needed a measurement shim because marktime decode fails (ApiError.Serialization, pre-existing, resume-progress-anomaly). Spec committed 8acfa031 Add the spec and baseline evidence of targeted cache invalidation; gate 1 approved on standing instruction; architect plan spawned
- 2026-11-22 plan written (3 tasks + qa hand-off); readiness judge spawned
- 2026-11-22 readiness-1: READY (note: AC-10 exit time needs a base re-measure with log markers at verify); plan committed 1edfd5b6 Add the targeted cache invalidation plan with its READY verdict; implementer spawned
- 2026-11-22 impl done: 5215bc27, 69e9cdac, c0da5da7; check.fast green (conductor); named tests present except the two the design says are replaced; 0 Co-Authored-By; no PUBERBASE; qa-device verify round 1 spawned
- 2026-11-22 qa-report round 1: check.full green (2305 tests); AC-1..5,7..10 PASS, AC-6 NOT CHECKED (account payload has no per-episode progress, marktime decode issue owned by resume-progress-anomaly); in front 29 min: base 58 marks/59 refreshes, head 0/0; exit median 898 vs 905 ms; evidence e4d5dba2; review round 1 spawned at e4d5dba2
- 2026-11-22 review round 1: codex clean (1 minor = AC-6 gap), edge 0, blind 1, tests 3; triage-1: 3 fix, 1 reject (flake logged in unit-suite-flaky); implementer + test-designer fix spawned
- 2026-11-22 fix round 1: 2eb4cab3 (NonCancellable background flush, AC-6 Details unit test, guard test fixed), a0fa2468 (rename to design names); test-design round 2 (P0 17) synced into spec 525d68cd Sync the targeted invalidation spec with the round 2 test design; check.fast BUILD SUCCESSFUL in 2s; review round 2 (edge, tests, codex on e4d5dba2..525d68cd) + qa round 2 (AC-2/3/4) spawned
- 2026-11-22 review round 2: edge clean, codex clean, tests F1 major (AC-6 row labelled red, is a guard) + F2 minor + F3 nit, all docs; test-design round 3 relabelled; spec synced a5f79b4f Relabel the AC-6 Details test as a guard in the targeted invalidation spec; codex on the docs diff 525d68cd..a5f79b4f spawned
- 2026-11-22 qa round 2: check.full green (2307), AC-2/3/4 PASS on device, triage #1 scenario 4/4 marked; evidence 8329d402; triage-2: 3 fixed (docs), codex rejected PUBERBASE claim (uncommitted shim, reverted); judge spawned at 8329d402
- 2026-11-22 judge-1: CONCERNS (AC-6 NOT CHECKED: marktime decode fails on this account, device evidence from shim build; check.full green at 8329d402, 2307 tests). Co-Authored-By 0. Gate 2: ship on standing instruction; squash spawned (tree 554df0082dcfb73aa15fb3da94771d11a6c03e8c)
- 2026-11-22 squashed to 3 commits (ce32f8a2, b0b59a15, ef2d1c20), tree 554df008 unchanged, check.fast green
- 2026-11-22 owner: all before/after values on the TV box 192.168.1.110:5555, emulator not used. qa-device round 3 on the box: marktime fails to decode on the box account too (shim used on both builds); base 59 marks/60 refreshes, head 0/0 in ~29 min; details loads 2 -> 1 (none at the switch); exit frame2 median 1147 -> 1218 ms (routerBack 188 -> 187 ms), within AC-10; AC-6 and launcher row NOT CHECKED (Details screencap white on box; launcher channel never created: "Failed to add logo", pre-existing in AndroidTvHomePublisher.ensureChannel); evidence 7415b586
- 2026-11-22 shipped: merged into master as 596d0f0e (--no-ff), Orca worktree removed; branch prakunin/cache-targeted-invalidation kept for the owner to delete
