---
slug: dependency-prune
title: "Drop `ktor-client-logging`, `coil-video`, `security-crypto`"
stage: dropped
size: S
tracks: []
kind: improvement
branch:
worktree:
base:
spec:
plan:
rounds: {verify: 0, review: 0, judge: 0}
blocked_by: []
created: 2026-10-12
updated: 2026-11-28
---

## Next
From the code-quality-audit report (full scope there): BP-4; chore, S, best practices. Device: yes (release build)

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-28 | Dropped: the scope lives in r8-keep-narrowing | owner |

## Log
- 2026-10-12 created on the board (stage idea)
- 2026-10-12 migrated from the former board file (board stage `idea` -> `idea`); the board row Notes are copied verbatim into ## Next
- 2026-11-23 from release-startup-compilation (Q1): also narrow the broad R8 -keep rules (app/proguard-rules.pro:49-163, absorbing perf-deferred P5) and drop appcompat (themes.xml parent is load-bearing for the splash); needs its own release walk on the box. See .flow/runs/release-startup-compilation/brief.md, Leaving this run
- 2026-11-23 absorbed into r8-keep-narrowing (release-startup-compilation split, owner)
- 2026-11-28 stage dropped (owner): the work continues in r8-keep-narrowing
