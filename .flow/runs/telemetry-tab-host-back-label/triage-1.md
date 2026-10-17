# Review triage - round 1

Run: telemetry-tab-host-back-label   Base: bdd2c792   Reviewed head: f5bfec56

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md, review-codex-1.md (ran: review, adversarial; clean). qa round 2: all ACs PASS.

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | edge F1 | major | `NavTelemetry.kt:41-49`, `TabComponent.kt:118` | Telemetry enabled while the host is covered (Device Settings is a root push from the rail): `unbind` cleared the binding, Back records `Main`, and the replay guard removes the old correction, so `Main` lasts until the next navigation | Read `MainVM.kt:173-174` (`router.navigateTo(screens.deviceSettings())`, root push) and `unbind`'s `Telemetry.isEnabled` branch; holds | fix | Freeze `reappearingTop()` regardless of `Telemetry.isEnabled` (records nothing, AC-9 wording holds); rework `unbindWithTelemetryOffKeepsNoFrozenTopAndNeverReadsIt` to "records nothing while off"; add JVM case unbind-while-off, enable, Back -> Home |
| 2 | tests F1 | minor | `TabHostTelemetryWiringTest.kt` AC-6 test, `FlowComponent.kt:593-597` | Live `top` wiring untested: swapping the lambdas leaves every test green | Read the itest: `mark()` after the child Details click; every other push is from the tab root; holds | fix | Assert the PUSH from the tab-local screen (`from == WiringChildScreen`, `to == WiringDetailsScreen`) in the AC-6 itest |
| 3 | tests F2 | nit | `NavTelemetryTest.kt:42-46` | Frozen bindings from direct `bind` calls survive tearDown | Read tearDown; holds (harmless today via fresh hosts) | fix | `TabHostTelemetry.resetForTests()` called in tearDown |
| 4 | blind F3 | nit | `NavTelemetry.kt:35` | The 2-arg `bind` overload is test-only and freezes the live top, unlike production | grep: only `FlowComponent.kt:593` in main, uses the 3-arg form; holds | fix | Remove the 2-arg overload; tests pass `reappearingTop` explicitly |
| 5 | blind F1 | minor | `NavTelemetry.kt:41-49` | Tab changed while covered (menu/content prefs): Back names the old tab root, then the replayed `Open` (a new instance) records `nav tab old->new` | Read `MainVM.kt:55-60`, `TabComponent.kt:118`; holds, and is design risk 3, accepted at gate 1: the TAB line follows in the same recomposition and corrects the label | reject | Accepted design risk R3 (self-corrects immediately; base logged `Main` there, equally wrong). Needs a settings change during a covered host |
| 6 | blind F2 | nit | `NavTelemetry.kt:43-46` | A frozen binding keeps a dead host and one screen reachable until the next bind | holds; it is design risk 5, accepted at gate 1 (one `MainScreen` and one parcelable screen, replaced at the next bind; never read via the host check) | reject | Accepted design risk R5 |

Codex: no findings (both passes).

## Next

- Implementer fix mode for #1-#4, then check.fast, lens edge + tests + codex on the increment, qa-device on AC-6/AC-9 (itest class + JVM).
