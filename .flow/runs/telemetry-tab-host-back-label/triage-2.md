# Review triage - round 2

Run: telemetry-tab-host-back-label   Base: bdd2c792   Reviewed head: b256e3a5 (increment f5bfec56..b256e3a5)

Sources: review-edge-2.md, review-tests-2.md, review-codex-2.md (ran: review, adversarial; clean). qa on affected ACs (qa-report.md, "Review round 1 fixes"; evidence 2899ccd3): AC-6 itest 6/6, AC-9 PASS, M1 PASS; finding #1 on device NOT CHECKED (no reachable root-pushed screen with the session-log toggle: the rail Settings item is a tab and `MainVM.onSettingsClick` has no UI caller), covered by the JVM test `unbindWhileOffThenEnabledThenBackNamesTheTabRoot`.

Triage-1 rows #1-#4 confirmed fixed by edge, tests and codex (572e252a, b256e3a5).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | edge F1, tests F1 | minor | spec D8, D11, R7, :103, :172, AC-9 test row | The spec still describes the removed telemetry-off gate, the 2-arg overload/default and the deleted AC-9 test | Read the spec lines against `NavTelemetry.kt:42-46` and `NavTelemetryTest`; holds | fix | Documentation only: architect and test-designer write amendments, conductor appends them to the spec; no code change, so no further review round |
| 2 | tests F2 | nit | `NavTelemetryTest.unbindWithTelemetryOffRecordsNothing` | The test cannot realistically fail | holds; it is harmless and documents AC-9's wording, the real guard `nothingIsRecordedWhenOff` stays | reject | Harmless; the spec amendment points AC-9 at `nothingIsRecordedWhenOff` as the primary guard. Review cap reached; not worth a code round |
| 3 | tests F3 | nit | `TabHostTelemetryWiringTest.kt:82-88` | itest tearDown does not reset `TabHostTelemetry` | holds; harmless because every method composes a fresh host (same as accepted R9 for JVM tests) | reject | Harmless under host identity; review cap reached |

## Next

- Spec amendment (docs), then judge.
