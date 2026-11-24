---
slug: cached-item-card-only-fallback
title: "Persist a card-only copy of an item record over the row limit, and let only the owning writer delete a refused key"
# Stages: idea | triage | design | plan | impl | verify | review | judge | handover | shipped | done | blocked | dropped
# dropped = an idea the owner decided not to do (owner only).
stage: idea
# Size: S | M | L (set by triage)
size:
# Tracks (optional): ui, perf
tracks: []
# Kind: bug | feature | improvement | refactor | idea
kind: bug
branch:
worktree:
base:
spec:
plan:
rounds: {verify: 0, review: 0, judge: 0}
# Q-ids or a free reason, set when stage = blocked
blocked_by: []
# Slug of the run a deferred finding came from; empty otherwise
source: cached-payload-row-too-big
created: 2026-11-24
# Set only by a migration from an older orchestrator; lint then skips history checks
migrated:
updated: 2026-11-24
---

## Next
triage with /task:run cached-item-card-only-fallback

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
<!-- Blocks: yes when the answer changes scope or an AC. Status: open / assumed / answered. -->

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- <YYYY-MM-DD> created (stage idea)
- 2026-11-24 created (stage idea) from cached-payload-row-too-big triage-1 T5+T6 (state-path F1, F2): /home/dev/Puber/.flow/runs/cached-payload-row-too-big/triage-1.md, /home/dev/Puber/.flow/runs/cached-payload-row-too-big/review-state-path-1.md. (1) While an oversized details record is in memory, every card merge for that item is refused, so the card is never persisted; after restart lists naming it refetch, offline the card is missing (ContentCacheRepository.kt mergeItems). (2) PersistentPayloadStore.refuse deletes the key's row unconditionally, so a concurrent card write of the same item can be lost for the session (CachedFeed.load vs putAll epochs). Proposed: persist a card-only copy (seasons/videos cleared, detailsUpdatedAt = null) when the merged record is over MAX_ROW_BYTES, and move the refused-key delete under CachedFeed's epoch ownership.
