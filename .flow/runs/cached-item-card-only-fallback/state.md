---
slug: cached-item-card-only-fallback
title: "Persist a card-only copy of an item record over the row limit, and let only the owning writer delete a refused key"
# Stages: idea | triage | design | plan | impl | verify | review | judge | handover | shipped | done | blocked | dropped
# dropped = an idea the owner decided not to do (owner only).
stage: done
# Size: S | M | L (set by triage)
size: M
# Tracks (optional): ui, perf
tracks: []
# Kind: bug | feature | improvement | refactor | idea
kind: bug
branch: prakunin/cached-item-card-only-fallback
worktree: /home/dev/orca/workspaces/Puber/cached-item-card-only-fallback
base: 4db1b061
spec: /home/dev/orca/workspaces/Puber/cached-item-card-only-fallback/docs/superpowers/specs/2026-11-24-cached-item-card-only-fallback.md
plan: /home/dev/orca/workspaces/Puber/cached-item-card-only-fallback/docs/superpowers/plans/2026-11-24-cached-item-card-only-fallback.md
rounds: {verify: 3, review: 3, judge: 1}
# Q-ids or a free reason, set when stage = blocked
blocked_by: []
# Slug of the run a deferred finding came from; empty otherwise
source: cached-payload-row-too-big
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
| 2026-11-24 | codex: required on every round (at usage limit: skip, do not wait); antigravity on every round | owner (standing instruction) |
| 2026-11-24 | all gates on recommended answers, no questions | owner (standing instruction: recommended) |
| 2026-11-24 | D1 recommended (unit row for markItemDetailsStale); Gate 1 approved as designed (M, AC-1..AC-8) | owner (standing instruction: recommended) |
| 2026-11-24 | Gate 2: ship (judge-1 CONCERNS, no must-fix); squash to <=3 commits before /task:ship | owner (standing instruction: recommended) |
| 2026-11-24 | Q1 fallback = CachedFeed hook supplied by ContentCacheRepository for itemFeed; Q2 card-only shape = season skeletons (episodes = null), videos = null, detailsUpdatedAt = null; Q3 store write returns Boolean, writeAll returns refused keys, store no longer deletes, feed applies fallback / owned delete | owner (standing instruction: recommended) |

## Log
- <YYYY-MM-DD> created (stage idea)
- 2026-11-24 created (stage idea) from cached-payload-row-too-big triage-1 T5+T6 (state-path F1, F2): /home/dev/Puber/.flow/runs/cached-payload-row-too-big/triage-1.md, /home/dev/Puber/.flow/runs/cached-payload-row-too-big/review-state-path-1.md. (1) While an oversized details record is in memory, every card merge for that item is refused, so the card is never persisted; after restart lists naming it refetch, offline the card is missing (ContentCacheRepository.kt mergeItems). (2) PersistentPayloadStore.refuse deletes the key's row unconditionally, so a concurrent card write of the same item can be lost for the session (CachedFeed.load vs putAll epochs). Proposed: persist a card-only copy (seasons/videos cleared, detailsUpdatedAt = null) when the merged record is over MAX_ROW_BYTES, and move the refused-key delete under CachedFeed's epoch ownership.
- 2026-11-24 /task:run started; base 4db1b061
- 2026-11-24 research.md, brief.md: size M, kind bug, no tracks, 8 ACs; Q1-Q3 recommended; worktree /home/dev/orca/workspaces/Puber/cached-item-card-only-fallback
- 2026-11-24 design + test design; spec committed; gate 1 approved (standing); stage plan
- 2026-11-24 plan + readiness-1 READY; stage impl
- 2026-11-24 impl done: bca6c9a1, 2e69f402, 58bc2aa0, 6111240e; check.fast green; named tests 22/22
- 2026-11-24 qa round 1: AC-1..AC-6 PASS, AC-7/AC-8 PASS with deviation (title from search, not 4K grid); evidence b25678c6; stage review
- 2026-11-24 review round 1: triage-1 T1-T3 fix (9ac3dba7), T4 to qa, T5-T8 rejected
- 2026-11-24 review round 2 + qa round 2 (AC-5/7/8 PASS, evidence 2b79669c); triage-2: U1 major (log prefix) fix
- 2026-11-24 review round 3 clean; qa round 3 AC-7/AC-8 PASS (evidence 7aaf6222); check.full exit 0; stage judge
- 2026-11-24 judge-1 CONCERNS (no must-fix); gate 2 ship (standing)
- 2026-11-24 squashed to de4d1bbb, c472e357 (tree unchanged, check.fast green); waiting for the owner's /task:ship
- 2026-11-25 shipped: merge 61a9ba20; worktree removed
