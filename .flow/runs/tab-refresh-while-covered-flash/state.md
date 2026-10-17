---
slug: tab-refresh-while-covered-flash
title: "A tab whose content is refreshed while the host is covered (e.g. show-anime toggled from the Settings hotkey) flashes its restored stack on Back before resetting to the root, and the Back telemetry line names the stale screen"
stage: idea
size:
tracks: []
kind: bug
branch:
worktree:
base:
spec:
plan:
rounds: {verify: 0, review: 0, judge: 0}
blocked_by: []
created: 2026-10-17
migrated:
updated: 2026-10-17
---

## Next
triage with /task:run tab-refresh-while-covered-flash

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-10-17 created (stage idea) as a deferred finding of tab-stack-lost-on-root-push review round 1 (review-edge-1.md F1, triage-1.md). Suggested fix: removeState(tab.key) in TabComponent when an Open arrives with a changed contentInstanceKey.
