LENS: tests  ROUND: 2  BASE: bdd2c792

Suite state: I ran `check.full` (`AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`) at b256e3a5. It passed: BUILD SUCCESSFUL, 1996 unit tests, 0 failures, 0 skipped. NavTelemetryTest ran 18 of 18, including `unbindWhileOffThenEnabledThenBackNamesTheTabRoot` and `unbindWithTelemetryOffRecordsNothing`. The tasks were up to date, so these are the results cached for the current inputs. The instrumented class was not run, because it needs a leased device. The itest change compiles, since lint analysed the androidTest variant, but it has not run green or red on a device in this round.

The round-1 fixes hold:
- **#1:** `unbindWhileOffThenEnabledThenBackNamesTheTabRoot` would fail against the old `unbind`. With telemetry off, the old code set `binding = null`, so Back to the host resolved to `MainScreen`, not the `HomeScreen` the test expects. The test also binds `top = { other }` and `reappearingTop = { home }`, so it would fail if `unbind` froze the live top instead of the reappearing one.
- **#2:** In the AC-6 itest, swapping the `FlowComponent.kt:595-596` lambdas, or using `items.firstOrNull()` for `top`, would make the push `from=WiringHomeScreen`. The new assertion expects `from == WiringChildScreen` and would fail; `first {}` throws if no such PUSH is recorded.
- **#3:** `TabHostTelemetry.resetForTests()` is called in the JVM `tearDown`.
- **#4:** The two-argument `bind` is gone. All callers pass `reappearingTop` explicitly through `bindSame`, and the only production caller is `FlowComponent.kt:593`.

### F1 [minor] The spec's design and Test design still describe the removed telemetry-off gate and the old AC-9 test
- Where: /home/user1/orca/workspaces/Puber/telemetry-tab-host-back-label/docs/superpowers/specs/2026-10-17-telemetry-tab-host-back-label.md:77 (D8), :103, :156 (D11), :172, :220 (R7), :251 (AC-9 row)
- Problem: The increment changed `unbind` to freeze `reappearingTop()` whether or not telemetry is on, and replaced `unbindWithTelemetryOffKeepsNoFrozenTopAndNeverReadsIt`. The spec was not updated; only the plan was. The spec still says:
  - D8: the freeze is gated on `Telemetry.isEnabled`.
  - D11: `reappearingTop` has a default, and the lambda is never invoked when off.
  - :103 and :251: a disabled unbind leaves no frozen top, gives the `MainScreen` fallback, and makes 0 reads.
  - The AC-9 row maps to a test that no longer exists.
- Failure scenario: Gate 2 or a later run checks AC-9 against the Test design. The named test is missing and its assertions (fallback `MainScreen`, 0 reads) are the opposite of what the code and the new test now enforce. That is exactly the behaviour triage #1 fixed. Someone "restoring" the spec's contract would bring the edge F1 bug back.
- Fix: Update D8, D11, :103, :172, R7 and the AC-9 row:
  - The freeze is unconditional and records nothing.
  - AC-9 maps to `unbindWithTelemetryOffRecordsNothing` plus `nothingIsRecordedWhenOff`.
  - Add `unbindWhileOffThenEnabledThenBackNamesTheTabRoot` as the regression row for triage #1.

### F2 [nit] `unbindWithTelemetryOffRecordsNothing` cannot realistically fail
- Where: /home/user1/orca/workspaces/Puber/telemetry-tab-host-back-label/app/src/test/kotlin/com/kino/puber/core/ui/navigation/NavTelemetryTest.kt (`unbindWithTelemetryOffRecordsNothing`)
- Problem: `unbind` has no recording path and never writes `Telemetry.screen`, and `Telemetry.screen` is null after setup anyway. Both assertions pass for the old code, the new code, and any plausible regression. The real AC-9 guard is still `nothingIsRecordedWhenOff`, which covers `recordNav`.
- Failure scenario: someone adds a `recordNav` call inside `unbind` that is guarded by `Telemetry.isEnabled`. The test stays green, which is correct, but it never caught anything to begin with. It only adds count to the suite.
- Fix: Drop the test, or fold its `recorder.events` size check into `unbindWhileOffThenEnabledThenBackNamesTheTabRoot` before the recorder is re-enabled. In the spec, point AC-9 at `nothingIsRecordedWhenOff`.

### F3 [nit] The itest `tearDown` does not reset `TabHostTelemetry`
- Where: /home/user1/orca/workspaces/Puber/telemetry-tab-host-back-label/app/src/androidTest/kotlin/com/kino/puber/core/ui/navigation/component/TabHostTelemetryWiringTest.kt:82-88
- Problem: Triage #3 added `TabHostTelemetry.resetForTests()` only to the JVM test. In the itest, each test's tab navigator is disposed at teardown, which leaves a frozen `Binding` (host plus root screen) in the singleton for the next method. Today this is harmless because every method shows a fresh host, and a binding is keyed by host identity.
- Failure scenario: a future itest reuses a host instance across `showHost()` calls. Its first Back would then read the previous method's frozen root, and only in some test orders.
- Fix: Call `TabHostTelemetry.resetForTests()` in `tearDown` after `Telemetry.resetForTests()`.
