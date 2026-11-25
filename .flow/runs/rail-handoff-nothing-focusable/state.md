---
slug: rail-handoff-nothing-focusable
title: "Back from the open rail over a screen with nothing focusable fails its handoff after 5 s and reopens the rail, so only a tab pick leaves it"
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
source: showall-rail-back-exits-app
created: 2026-11-25
# Set only by a migration from an older orchestrator; lint then skips history checks
migrated:
updated: 2026-11-25
---

## Next
triage with /task:run rail-handoff-nothing-focusable

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
<!-- Blocks: yes when the answer changes scope or an AC. Status: open / assumed / answered. -->

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- <YYYY-MM-DD> created (stage idea)
- 2026-11-25 created (stage idea) from showall-rail-back-exits-app design revision Q1 (/home/dev/Puber/.flow/runs/showall-rail-back-exits-app/design-architect.md): with nothing focusable, Back from the open rail starts a handoff that fails after 5 s and reopens the rail (ContentFocusHandoff.kt:120-137); base behaviour.
