---
slug: player-track-label-resource-provider
title: "The player fallback track label is read from the raw Context instead of ResourceProvider"
# Stages: idea | triage | design | plan | impl | verify | review | judge | handover | shipped | done | blocked | dropped
# dropped = an idea the owner decided not to do (owner only).
stage: done
# Size: S | M | L (set by triage)
size: S
# Tracks (optional): ui, perf
tracks: []
# Kind: bug | feature | improvement | refactor | idea
kind: bug
branch: prakunin/player-track-label-resource-provider
worktree: /home/dev/orca/workspaces/Puber/player-track-label-resource-provider
base: 8db929b5
spec:
plan:
rounds: {verify: 1, review: 1, judge: 1}
# Q-ids or a free reason, set when stage = blocked
blocked_by: []
# Slug of the run a deferred finding came from; empty otherwise
source: player-audio-unknown-language
created: 2026-11-27
# Set only by a migration from an older orchestrator; lint then skips history checks
migrated:
updated: 2026-11-27
---

## Next
none (done)

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Also route the three player_error_playback reads through ResourceProvider? | no | answered | yes (recommended) |
| Q2 | Prove with a stub ResourceProvider in an instrumented test instead of a real AppLocale switch? | no | answered | yes (recommended) |
<!-- Blocks: yes when the answer changes scope or an AC. Status: open / assumed / answered. -->

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-27 | codex: required (owner); antigravity on every round (owner; skip while quota exhausted); caps lifted (owner) | owner |
| 2026-11-27 | Size S kept as the analyst proposed (string source swap only; project rule would say M) | owner (standing instruction: recommended) |
| 2026-11-27 | Q1 route all four strings; Q2 stub ResourceProvider test, no device locale switch; instrumented runs on the emulator | owner (standing instruction: recommended) |
| 2026-11-27 | Gate 1: approved (S: brief ACs and Plan are the spec and plan) | owner (standing instruction: recommended) |
| 2026-11-27 | P1 test corruptCacheError_usesInjectedResourceProviderMessage dropped (downgraded to P2 per test-design open point): no deterministic path reaches the null-message fallback; AC-2 grep is the gate | owner (standing instruction: recommended) |
| 2026-11-27 | Gate 2: ship (judge-1 PASS) | owner (standing instruction: recommended) |

## Log
- 2026-11-27 created (stage idea) from player-audio-unknown-language Q2: PlaybackController.kt:873-875 gets the "Track N" fallback label from Context.getString, not ResourceProvider (AGENTS.md: non-composable code uses ResourceProvider); it may keep the old language after an in-app language switch (unverified on a device).
- 2026-11-27 preflight: base 8db929b5; stage triage
- 2026-11-27 research.md (option A recommended, S)
- 2026-11-27 brief.md (bug, S, 4 ACs); stage design
- 2026-11-27 test-design.md (P0 3, P1 2, manual 2); gate 1 approved (standing instruction); Orca worktree /home/dev/orca/workspaces/Puber/player-track-label-resource-provider; stage impl
- 2026-11-27 impl: a0dc2dd3, 609a7c43; red run recorded; NetworkTracks 14/0 on emulator; check.full pass; P1 error-message test not written (no deterministic path); stage verify
- 2026-11-27 qa-report round 1: check.full pass, AC-1..AC-4 PASS (box: player starts, no Koin errors); stage review
- 2026-11-27 review round 1 (blind, tests, codex; antigravity skipped: quota): triage-1: no fixes, 1 rejected with the drop recorded; stage judge
- 2026-11-27 judge-1 PASS; 3 commits, no squash needed; gate 2 ship (standing instruction); stage handover
- 2026-11-27 shipped: merge commit dba973f5 (--no-ff) into master; Orca worktree removed; stage done
