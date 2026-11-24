---
slug: cached-payload-row-too-big
title: "A cached_payload row over the 2 MB CursorWindow breaks the 4K «Все» shelf and grid (SQLiteBlobTooBigException)"
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
source: catalogue-browse-all
created: 2026-11-24
# Set only by a migration from an older orchestrator; lint then skips history checks
migrated:
updated: 2026-11-24
---

## Next
triage with /task:run cached-payload-row-too-big

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
<!-- Blocks: yes when the answer changes scope or an AC. Status: open / assumed / answered. -->

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-11-24 created (stage idea) from catalogue-browse-all qa round 1 defect 2: on the TV box the 4K tab's «Все» shelf and the browse grid show «Не удалось выполнить запрос»; Retry fails in ~0.25 s with no network request; logcat: SQLiteBlobTooBigException: Row too big to fit into CursorWindow on SELECT * FROM cached_payload WHERE key IN (...). Paths: data/db/CachedPayloadDao.kt:13-14 (readAll SELECT *), data/repository/PersistentPayloadStore.kt:60-64, data/cache/CachedFeed.kt:360 (peekAll). Config TabTypeConfig.for4kSections unchanged since e4d75215, so it predates catalogue-browse-all. Options: cap payload size on write, or read in pieces; which key is oversized is not confirmed. Clearing app data needs explicit owner authorization.
