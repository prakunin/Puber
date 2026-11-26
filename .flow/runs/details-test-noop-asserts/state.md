---
slug: details-test-noop-asserts
title: "Replace Kotlin assert in instrumented tests with JUnit assertions (no-op on Android)"
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
source: details-plot-adaptive-width
created: 2026-11-26
migrated:
updated: 2026-11-26
---

## Next
triage with /task:run details-test-noop-asserts

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-11-26 created (stage idea), deferred from details-plot-adaptive-width readiness-1: Kotlin `assert(...)` is a no-op on Android unless assertions are enabled, and nothing in app/build.gradle* or the Makefile enables them, so such checks in instrumented tests can never fail. Found at app/src/androidTest/.../DetailsScreenContentTest.kt:152 (and :192, fixed inside details-plot-adaptive-width). Sweep all of app/src/androidTest for `assert(` and replace with JUnit assertions; consider a detekt/lint rule.
