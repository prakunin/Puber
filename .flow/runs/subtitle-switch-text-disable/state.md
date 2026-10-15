---
slug: subtitle-switch-text-disable
title: "Disable text during switchStream as upstream 7d5addd2 does, proven by instrumented PlaybackController tests on the P6-A fixtures"
stage: idea
size:
tracks: []
kind: improvement
branch:
worktree:
base:
spec:
plan:
rounds: {verify: 0, review: 0, judge: 0}
blocked_by: []
created: 2026-10-15
migrated:
updated: 2026-10-15
---

## Next
Triage with /task:run subtitle-switch-text-disable after subtitle-restore-model lands and upstream-sync-triage P6-A (`:player-test-fixtures`, instrumentation flavor) is on master.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-10-15 | Split from subtitle-restore-model as Part 2 (option C); starts after Part 1 and P6-A | owner |

## Log
- 2026-10-15 created (stage idea) as Part 2 of subtitle-restore-model (owner chose option C, then the split). Scope: switchStream text disable (PlaybackController.kt:501-521, upstream 7d5addd2) and instrumented tests on P6-A fixtures; ACs AC-13..AC-15 in ../subtitle-restore-model/brief.md. Open risk from the analyst: confirm PlayerVM re-sends the subtitle after switchStream, otherwise the disable leaves subtitles off after every quality switch.
