# Review triage - round 2

Run: telemetry-core   Base: 39903d31   Reviewed head: 0f20e63a (incremental from fc5738b1)

Sources: review-edge-2.md, review-tests-2.md, review-codex-2.md (ran: review, adversarial); qa-report.md Round 3 (no defects; T05 PASS on device).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | edge F1, codex | major | `core/telemetry/TelemetryWriter.kt:144-157` | Budget eviction can delete the zip just handed to the share target (regression from round-1 row 10) | Read: zips sorted first in eviction, export sets rotatePending | fix (never evict the newest zip; it is replaced only by the next export) | fixed (12abafde, bbe17d3e) |
| 2 | edge F2 | major | `core/ui/PuberVM.kt:91-98` | `first_content` fires on any first `ready`, incl. Home Loading->Error and root UpdatePromptVM Hidden->Available (regression from round-1 row 4, caused by the conductor's triage direction) | Read: `markFirstContent()` on every first ready | fix (only content states count: explicit content marker on landing-screen content states, error/loading states and root overlay VMs excluded) | fixed (88ae5858) |
| 3 | edge F3 | minor | `TelemetryRuntime.kt:41-47`, `TelemetrySink.kt:191-194, 227-230` | A wrong future `enabled_at` blocks auto-off until real time passes it + 7 days while UI says 7 days left | Read | fix (also expire on monotonic recorded time >= 7 days) | fixed (369226e1) |
| 4 | edge F4 | minor | `TelemetrySink.kt:167-177, 131-151` | Events enqueued between drain and expire leak into the next session with negative `t` | Read | fix (clear queue on expire; drop events older than origin on start) | fixed (369226e1) |
| 5 | tests F1 | minor | `TelemetryExportTest.kt:33-43` | Export fold test cannot fail; dropped-count line on export untested | Read | fix | fixed (27ca119a) |
| 6 | tests F2 | minor | `TelemetrySinkTest.kt:160-182` | `scr` test cannot distinguish per-event owner from lastScr | Read | fix | fixed (27ca119a) |

## Rejected codex claims

- none

## Next

- Implementer fix round (rows 1-6). Then fast check, lens `edge`, lens `tests` and codex-reviewer on the incremental diff from 0f20e63a, and qa-device on AC-1a and AC-9. This is the last review round (cap 2): anything still open goes to the owner.
