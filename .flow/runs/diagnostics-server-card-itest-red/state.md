---
slug: diagnostics-server-card-itest-red
title: "NetworkDiagnosticsContentFocusTest.serverCard_startsThatServerOnPress fails on the TV box (expected [Start(server=Moscow)], got [])"
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
source: diagnostics-transport-label
created: 2026-11-26
migrated:
updated: 2026-11-26
---

## Next
triage with /task:run diagnostics-server-card-itest-red

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-11-26 created (stage idea), deferred from diagnostics-transport-label triage-1 #8: on 192.168.1.110:5555, `make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.ui.feature.device.diagnostics.NetworkDiagnosticsContentFocusTest` fails serverCard_startsThatServerOnPress (expected [Start(server=Moscow)], got []) on the change and on base 4c398e07 alike (qa rounds 1-2, .flow/runs/diagnostics-transport-label/qa-report.md). Test or production defect not yet known.
