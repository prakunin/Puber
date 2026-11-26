---
slug: rail-left-focus-nearest-entry
title: "After LEFT from content the side rail focuses the nearest entry, not the selected tab"
stage: idea
size:
tracks: [ui]
kind: bug
branch:
worktree:
base:
spec:
plan:
rounds: {verify: 0, review: 0, judge: 0}
blocked_by: []
source: baseline-generation-hardening
created: 2026-11-26
migrated:
updated: 2026-11-26
---

## Next
triage with /task:run rail-left-focus-nearest-entry (check overlap with side-rail-focus-handoff, showall-rail-close-focus-shift first)

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-11-26 created (stage idea) from baseline-generation-hardening qa M4 on the TV box 192.168.1.110:5555: after opening Series and pressing LEFT from the card row, the rail focuses the entry nearest by position (Settings), not the selected tab; same on base b27d8e40. MainScreenComponent.kt:274-276,291 is meant to restore focus to the selected tab. Evidence: worktree baseline-generation-hardening docs/superpowers/evidence/2026-11-26-baseline-generation-hardening-device.md.
