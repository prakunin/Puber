---
slug: usage-telemetry-log
title: "Opt-in session log of user actions, timings, requests and errors for performance analysis"
stage: idea          # idea | triage | design | plan | impl | verify | review | judge | handover | shipped | done | blocked
size: L              # S | M | L (set by triage)
tracks: [perf, ui]   # optional: ui, perf
kind: feature        # bug | feature | improvement | refactor | idea
branch:
worktree:
base:
spec:
plan:
rounds: {verify: 0, review: 0, judge: 0}
blocked_by: []       # Q-ids or a free reason, set when stage = blocked
created: 2026-10-15
migrated:
updated: 2026-10-15
---

## Next
Umbrella only: work proceeds in the split runs; start with /task:run telemetry-core.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Who records and how data leaves the device? | no | answered | Owner/testers; adb pull plus a share/export action in Settings. No server upload. (Recommended was adb pull only.) |
| Q2 | May the log hold content identity? | yes | answered | Content ids allowed; titles and search text not (search as length only). Overrides the recommended anonymised allow-list; AC-7 and allow-list change. |
| Q3 | Which builds carry the toggle, how long? | no | answered | All build types incl. prod release, default off, Settings > Developer, auto-off after 7 days, visible indicator. |

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-10-15 | Export: adb pull plus share action; no backend upload | owner |
| 2026-10-15 | Content ids may be logged (departs from PlayerProgressDiagnostics / tmdb-result-handling Q1 for this opt-in log only) | owner |
| 2026-10-15 | Split into 5 runs accepted; titles and search text stay out of the log | owner |
| 2026-10-15 | Toggle in all builds, default off, auto-off after 7 days, visible indicator | owner |

## Log
- 2026-10-15 created (stage idea). Owner request (ru, paraphrased): the app should log its
  work - what was pressed, how long things took to open and what opened, which requests were
  made; all key presses, actions, errors; usage scenarios. Needed temporarily, possibly behind
  a checkbox ("share data to improve the app"). Purpose: analyse real usage to find places to
  optimise speed and memory, above all smoothness and responsiveness. The task must first
  analyse what to collect and in what volume so the log is genuinely useful rather than noise.
- Related (not duplicates): network-diagnostics (done, replaced by media server speed test),
  perf-deferred (idea), memory-and-performance-fixes (done), startup-benchmark-entry (idea).
- 2026-10-15 research.md (option A recommended, no duplicate) and brief.md (feature, L, tracks perf+ui, 21 ACs, 5-run split); owner answered Q1-Q3; stays in stage idea because size L.
- 2026-10-15 owner accepted the split; brief.md amended with owner answers (AC-1a share export, AC-7/AC-17 allow content ids); created telemetry-core, telemetry-frames-keys-memory, telemetry-network, telemetry-player-exit, telemetry-summary-tool.
