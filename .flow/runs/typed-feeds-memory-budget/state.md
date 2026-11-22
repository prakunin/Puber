---
slug: typed-feeds-memory-budget
title: "Typed content feeds share one memory budget instead of 16Mi encoded chars per feed and per payload key"
# Stages: idea | triage | design | plan | impl | verify | review | judge | handover | shipped | done | blocked | dropped
# dropped = an idea the owner decided not to do (owner only).
stage: idea
# Size: S | M | L (set by triage)
size:
# Tracks (optional): ui, perf
tracks: []
# Kind: bug | feature | improvement | refactor | idea
kind: improvement
branch:
worktree:
base:
spec:
plan:
rounds: {verify: 0, review: 0, judge: 0}
# Q-ids or a free reason, set when stage = blocked
blocked_by: []
# Slug of the run a deferred finding came from; empty otherwise
source: cache-decoded-memory-tier
created: 2026-11-22
# Set only by a migration from an older orchestrator; lint then skips history checks
migrated:
updated: 2026-11-22
---

## Next
triage with /task:run typed-feeds-memory-budget (after cache-decoded-memory-tier ships)

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
<!-- Blocks: yes when the answer changes scope or an AC. Status: open / assumed / answered. -->

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-11-22 created (stage idea) from cache-decoded-memory-tier review round 1, triage-1.md #1 (blind F1): base held one 16 MiB byte budget (8Mi chars) for the content memory tier; the typed tier gives 16Mi encoded chars to each of item, list, page, history and every payload key feed, and weighs JSON length not decoded heap. Device heap showed no growth (cache-decoded-memory-tier qa-report AC-10); a long-series browsing session was not measured.
