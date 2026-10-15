# Review triage - round 1

Run: telemetry-core   Base: 39903d31   Reviewed head: fc5738b1

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md,
review-codex-1.md (ran: review, adversarial), review-ux-designer-1.md (no findings, VISUAL: match).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | codex, edge F2, blind F6 | major | `core/telemetry/TelemetrySink.kt:100-101` | Share does not drain the queue first; the last ~5 s of events (incl. the problem being reported) miss the zip; spec D8 says drain on export | Read: `export` only calls `writer.export()` | fix | fixed (d962b687) |
| 2 | edge F1 | major | `core/telemetry/EventFolder.kt:62-67` | ErrFolder key ignores `src`; an uncaught crash within 60 s of a same-key logged error is folded and written as `src=log` | Read: key = type + first frame, `src` from the first entry | fix (crash never folded) | fixed (1b740792) |
| 3 | blind F1, edge F5 | major | `core/telemetry/ErrorScrubber.kt:28-38`; `SensitiveRequestLogPolicy.kt:118` | `et` and frames keep only the tail of the class name: in release they are obfuscated and not retraceable, and `et` differs between `src=log` and `src=auth` for the same class | Read: `substringAfterLast('.')` on `javaClass.name` vs `qualifiedName` | fix (write the full binary class name in `et` and frames, one normalisation for every path, so `retrace` with mapping.txt works) | fixed (1b740792) |
| 4 | blind F2 | major | `ui/feature/home/vm/HomeVM.kt:72`, `core/ui/PuberVM.kt:103` | `first_content` is Home-only: wrong or missing when the startup tab is not Home or the cold start lands on Auth | Read: only `HomeViewState.Content` marks it | fix (mark on the first `ready` of the landing screen; never record a Home ready after another screen came first) | fixed (72af6ac6) |
| 5 | edge F4 | minor | `core/telemetry/TelemetryWriter.kt:88-101` | Uncaught crash is subject to the 20/min `err` cap and can be dropped in an error storm | Read: cap applies to every Err | fix (exempt `UNCAUGHT`) | fixed (1b740792) |
| 6 | edge F3 | minor | `TelemetryRuntime.kt:84`, `DeviceSettingsVM` toggle | In `Unavailable` the toggle can only re-enable; the pref is never cleared and recording resumes silently on a later cold start | Scenario holds per reviewer trace | fix | fixed (279b281c) |
| 7 | blind F4 | minor | `TelemetryRuntime.kt:42-45` | A pre-NTP boot clock clamps and persists `enabled_at`, then auto-off fires once time syncs | Read: clamped value is saved | fix (clamp in memory only) | fixed (d962b687) |
| 8 | blind F5 | minor | `TelemetrySink.kt:101`, `TelemetryExecutor.kt:29` | Non-IO throwable in export is swallowed; Share stays "preparing" forever | Read: executor `runCatching`, export catches IOException only | fix (catch Throwable -> `ShareResult.Failed`) | fixed (d962b687) |
| 9 | blind F7 | minor | `TelemetrySink.kt:34-38` | `sid` and `t` origin not reset on re-enable; a Share before first enable fixes the origin | Read: encoder created once per process | fix (new sid and origin on every start) | fixed (d962b687) |
| 10 | blind F8, codex minor, edge F6 | minor | `TelemetryWriter.kt:147-156`, `TelemetryStore.kt:43-49` | Stale zip counts against the 20 MB budget and evicts logs; orphan `.zip.tmp` never cleaned and outside the budget | Read: budget deletes only `.jsonl`, `list()` hides `.tmp` | fix (delete stale zips/`.tmp` first; budget includes them) | fixed (d962b687) |
| 11 | blind F3 | minor | `core/ui/PuberVM.kt:97-101` | No `ready` for data-class-state VMs (Settings, Main, ContentList, NetworkDiagnostics) | Known: accepted for S1 by decision (architect Q2, 2026-10-15) | defer | new run `telemetry-ready-data-states` |
| 12 | tests F1 | minor | `DeviceSettingsVMSessionLogTest.kt:158-169` | Stale snackbar makes the `ShareResult.Failed` assert vacuous | Read | fix | fixed (279b281c) |
| 13 | tests F2 | minor | telemetry tests | No sink-level test for act folding/cap/flush-on-stop and the `scr` envelope | Grep: no `scr` value assert | fix | fixed (b07f77d4) |
| 14 | tests F3 | minor | `androidTest/.../DeviceSettingsContentFocusTest.kt:394-409` | Instrumented T05 exists but was never run; qa-report says it is missing | Read | fix (qa runs it via `make DEVICE=<serial> itest TESTS=...`) | fixed (b07f77d4 (device run pending)) |
| 15 | tests F4 | minor | `ProguardRulesTest.kt:16-24`, `TelemetryNames.kt:105-108` | Round-2 name fix has no guard: Tab keep rule not in the tripwire; `plain()` reverting to `simpleName` stays green | Read | fix | fixed (b07f77d4) |
| 16 | tests F5 | minor | `ManualTelemetryExecutor.kt:71`, `TelemetryExecutor.kt:46` | Writer-thread guard only tested via a fake that ignores its argument | Read | fix (one real-thread test) | fixed (b07f77d4) |
| 17 | tests F6 | nit | `PuberVMTelemetryTest.kt:142`, `StartupMarksTest.kt:26` | Two asserts cannot fail | Read | fix (cheap) | fixed (72af6ac6, b07f77d4) |

## Rejected codex claims

- none (codex reported two findings; both hold, see rows 1 and 10)

## Next

- Implementer fix round for rows 1-10 and 12-17; then fast check, lens `edge` and codex-reviewer on the incremental diff from fc5738b1, lens `tests` (tests change), and qa-device on the affected ACs (AC-1a export, AC-4, AC-9 first_content, AC-10 err) plus running instrumented T05. Row 11 becomes run `telemetry-ready-data-states`.
