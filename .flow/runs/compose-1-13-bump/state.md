---
slug: compose-1-13-bump
title: Bump Compose BOM 2026.11.01 (Compose 1.13, material3 1.5) with compileSdk 37.1
# Stages: idea | triage | design | plan | impl | verify | review | judge | handover | shipped | done | blocked | dropped
# dropped = an idea the owner decided not to do (owner only).
stage: idea
# Size: S | M | L (set by triage)
size:
# Tracks (optional): ui, perf
tracks: [ui]
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
source: dependency-patch-bump-nov
created: 2026-11-28
# Set only by a migration from an older orchestrator; lint then skips history checks
migrated:
updated: 2026-11-28
---

## Next
triage with /task:run compose-1-13-bump (after dependency-patch-bump-nov ships); research in .flow/runs/dependency-patch-bump-nov/research.md

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
<!-- Blocks: yes when the answer changes scope or an AC. Status: open / assumed / answered. -->

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-11-28 created (stage idea), split from dependency-patch-bump-nov (brief Q1): needs compileSdk 37.1 (platforms;android-37.1 in box and CI), InputMode.Keyboard in D-pad Compose UI tests, visual review
