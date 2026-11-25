---
slug: browse-entry-owner-without-entry
title: "The all-Empty settle marks the browse entry owner on tabs without an entry (Cartoons, Anime)"
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
triage with /task:run browse-entry-owner-without-entry

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
<!-- Blocks: yes when the answer changes scope or an AC. Status: open / assumed / answered. -->

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-11-24 created (stage idea) from the post-merge Codex review of catalogue-browse-all: BrowseEntryFocus.kt:92-96 settleLaunchWhenEveryEmpty() sets isOwner = true even when browseEntry is null; with every shelf Empty >= 100 ms and focus outside the list (rail), the saved ownership disables isTargetRow/initialFocusEnabled (ContentListScreenContent.kt:364, :448) for every row until a row gains focus. Minor; fix: skip the grab when there is no entry.
