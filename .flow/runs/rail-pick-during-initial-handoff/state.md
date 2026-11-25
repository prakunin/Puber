---
slug: rail-pick-during-initial-handoff
title: "A rail pick during the cold-start handoff is refused while the first tab loads; the rail reopens after 5 s"
# Stages: idea | triage | design | plan | impl | verify | review | judge | handover | shipped | done | blocked | dropped
# dropped = an idea the owner decided not to do (owner only).
stage: done
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
triage with /task:run rail-pick-during-initial-handoff

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
<!-- Blocks: yes when the answer changes scope or an AC. Status: open / assumed / answered. -->

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- <YYYY-MM-DD> created (stage idea)
- 2026-11-25 created (stage idea) from showall-rail-back-exits-app triage-4 D6 (/home/dev/Puber/.flow/runs/showall-rail-back-exits-app/review-state-path-4.md F1): beginInitialHandoff starts with expectsNewContent = false, so acceptsPickOf (MainScreenComponent.kt:304-308) refuses Center on a rail item while the first tab loads; after 5 s the rail reopens. Proposed: a handoff kind in DrawerState (Initial / TabPick / Exit), accept picks for kind != Exit && !tab.isSelected; test with startMain(loadingRoot = "first"). Also: the !tab.isSelected clause is untested because the probe never updates the selection (triage-4 D2).
- 2026-11-25 folded into run rail-handoff-nothing-focusable (owner); closed when that run ships
- 2026-11-25 done: fixed and shipped inside rail-handoff-nothing-focusable (merge b1bb0d84)
