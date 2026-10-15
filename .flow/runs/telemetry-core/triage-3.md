# Review triage - round 3 (re-review of round-2 fixes; over the 2-round cap)

Run: telemetry-core   Base: 39903d31   Reviewed head: bbe17d3e (incremental from 0f20e63a)

Sources: review-edge-3.md, review-tests-3.md, review-codex-3.md (ran: review, adversarial); qa-report.md Round 4 (no defects; release retrace verified).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | edge F1 | major | `ui/feature/contentlist/model/SectionState.kt:7-18`, `core/ui/PuberVM.kt:99` | `first_content` never recorded when the startup tab is a content-list tab (Movies, Series, ...): `SectionState.Content/Empty` lack the marker (regression from triage-2 row 2) | Read: marker implemented only by the listed states | fix (owner, standing instruction: bounded extra round) | fixed (1ea95f0f) |
| 2 | codex | minor | `core/telemetry/TelemetrySink.kt:49-57,195-201` | `drainQueue` does not drop events with `t < origin`; a stalled producer can leak one event into the next session | Read | fix (one line) | fixed (1ea95f0f, 9e0dad08) |
| 3 | tests F1 | minor | `TelemetryExportTest.kt:85-102` | Auto-off queue test passes without the queue clear in `expire()` | Read | fix | fixed (1ea95f0f) |
| 4 | edge F2 | minor | `TelemetrySink.kt:204-210, 244-249` | Uptime bound resets per process, so a wrong future `enabled_at` can still keep recording past 7 days across restarts | Read | defer (needs persisted uptime; edge case of a clock wrong in the future at enable time) | new run `telemetry-uptime-autooff` |

## Rejected codex claims

- Adversarial "needs-attention" with a session-generation tag and latch test: not warranted; row 2's one-line origin check closes the gap (codex-reviewer's own grading).

## Next

- Over the review cap: the open major goes to the owner; under the standing instruction the recommended answer is one bounded fix round for rows 1-3, then lens `edge` + `tests` on that diff only, then judge. qa: unit coverage suffices for row 1 (a content-list startup tab cannot be set without changing account prefs on the device).

## Re-check of rows 1-3 (review-edge-4.md, review-tests-4.md)

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 5 | tests F1 (round 4) | minor | `TelemetrySinkTest.kt:99-107, 123-125` | Two sink tests' nav events are now dropped by the pre-origin filter, weakening them (introduced by 1ea95f0f) | Read: FakeTelemetryClock() elapsed 1000 vs nav t=1 | fix (test-only) | fixed (9ce699af) |
