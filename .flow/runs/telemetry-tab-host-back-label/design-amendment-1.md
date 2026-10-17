### Design amendment (review round 1)

Code state: worktree `2899ccd3`. Fixes landed in `572e252a` and `b256e3a5` (triage-1 #1, #3, #4). This amendment replaces the spec text listed under "Superseded". Where the two disagree, this amendment wins.

#### Current contract

| # | Question | Decision | Why |
|---|----------|----------|-----|
| A1 | Is the freeze in `unbind` gated on `Telemetry.isEnabled`? | No. `TabHostTelemetry.unbind` (`app/src/main/java/com/kino/puber/core/ui/navigation/component/NavTelemetry.kt:42-47`) always does the same thing when its handle is still current (`:43`): it reads `handle.reappearingTop()` once (`:44`) and stores a frozen `Binding(handle.host, keep, keep)` (`:45-46`). It never records anything and never writes `Telemetry.screen`. The only telemetry-off gate is the one in `recordNav` (`:64`). The KDoc at `:22-31` states this. | This is review-edge-1 F1 (triage-1 #1). If unbind ran while telemetry was off and simply cleared the binding, then turning telemetry on while the host was covered would make Back resolve to the host class (`Main`). The replay guard at `TabComponent.kt:118` drops the replayed `Open`, so that wrong label would never be corrected. Freezing always costs one snapshot-stack read and one allocation per tab-flow dispose. This is defensive: today no UI path reaches a covered host where the session-log toggle can be flipped. The rail Settings item is a tab, and `MainVM.onSettingsClick` (`ui/feature/main/vm/MainVM.kt:173-174`, a root push of `deviceSettings()`) has no caller. |
| A2 | API shape | `Binding(val host: Any, val top: () -> Any?, val reappearingTop: () -> Any?)` (`NavTelemetry.kt:33`) and `bind(host, top, reappearingTop)` (`:39-40`) have no default for `reappearingTop`. The 2-arg overload is gone (review-blind-1 F3). The only production caller passes both lambdas: `FlowComponent.kt:593-597` (`top = { navigator.lastItemOrNull }`, `reappearingTop = { navigator.items.firstOrNull() }`). JVM tests pass the same lambda twice through `NavTelemetryTest.bindSame` (`app/src/test/kotlin/com/kino/puber/core/ui/navigation/NavTelemetryTest.kt:105-106`). | A default would let a caller freeze the live top without noticing, which is the wrong value under today's Voyager behaviour (D10). With no default, every bind site has to choose explicitly. |
| A3 | Test isolation | `TabHostTelemetry.resetForTests()` (`NavTelemetry.kt:49-52`) clears any binding, frozen or live. `NavTelemetryTest.tearDown` calls it (`NavTelemetryTest.kt:42-44`). | Triage-1 #3. A frozen binding no longer survives into the next test (this narrows R9 for JVM tests). |

#### Superseded

- **D1**: drop "**and** `Telemetry.isEnabled`" and "When telemetry is off it clears the binding as it does today". Per A1, the handle-current check is the only condition.
- **D8**: replaced by A1. The `recordNav` guard (`NavTelemetry.kt:64`) and the unconditional live bind in `FlowComponent.kt:592-598` are unchanged. "with the log off nothing is read" no longer holds. What holds is "nothing is recorded".
- **D10**: drop "gated on `Telemetry.isEnabled` as in D1".
- **D11**: replaced by A2. Drop the `= top` default on `Binding` and `bind`, "The default keeps every existing `bind(host) { ... }` call ... compiling", and "when telemetry is off, it is never invoked".
- **Changes by layer, Tests (`:103`)**: "Unbind with telemetry disabled leaves no frozen top, so a later enabled Back gives the fallback and `handle.top` is not invoked (AC-9)" is reversed. A disabled unbind now freezes the reappearing top, and a later enabled Back names it.
- **Round 2 Tests (`:172`)**: `unbindWithTelemetryOffKeepsNoFrozenTopAndNeverReadsIt` no longer exists. It was replaced by:
  - `unbindWithTelemetryOffRecordsNothing` (`NavTelemetryTest.kt:222`): AC-9 wording.
  - `unbindWhileOffThenEnabledThenBackNamesTheTabRoot` (`:232`): regression for triage-1 #1. It fails if the gate returns (the result falls back to `MainScreen`) or if the live top is frozen (`ScreenScreen`).
  - `tabLocalTopIsFrozenAtUnbind` (`:200`) now binds through `bindSame`, not through a default.
- **R7**: restated as "Telemetry off: `unbind` freezes and holds one screen object even though nothing is logged". Likelihood 1, impact 1. Accepted: it records nothing, the object is replaced at the next bind, and the host-identity check in `topOf` (`NavTelemetry.kt:54`) keeps it inert for a fresh host, as in R5. The mitigation is AC-9 through `nothingIsRecordedWhenOff` (`NavTelemetryTest.kt:78`) and `unbindWithTelemetryOffRecordsNothing`, plus the regression test `unbindWhileOffThenEnabledThenBackNamesTheTabRoot`.
- **AC-9 test row (`:251`)**: the test-designer's amendment owns the exact row. The design-side mapping is: primary guard `nothingIsRecordedWhenOff`, wording check `unbindWithTelemetryOffRecordsNothing`, and the regression row `unbindWhileOffThenEnabledThenBackNamesTheTabRoot` (triage-1 #1).

#### Unchanged

The AC text is unchanged. AC-9 ("no binding or freeze work records anything") still holds. D2-D7, D9, D12-D14 and R1-R6, R8-R12 stand. Note for `tab-stack-lost-on-root-push`: do not reintroduce a `Telemetry.isEnabled` gate in `unbind` (A1).
