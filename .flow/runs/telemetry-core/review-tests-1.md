LENS: tests  ROUND: 1  BASE: 39903d31

Suite state: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` gave BUILD SUCCESSFUL at HEAD fc5738b1. The test task was UP-TO-DATE, so its inputs match HEAD. Result XMLs show 1742 tests, 0 failures, 0 errors, 0 skipped; detekt and lint are clean. Every P0 row in the Test design has a test under its name or an equivalent. JUnit runs one test at a time in this project. Every test that installs something global (Telemetry recorder, Timber tree, default uncaught handler) resets it in `@AfterEach`, and no `Thread.sleep` was added. Per reviewer rules the worktree was not mutated; the mutation outcomes below are reasoned from the code.

### F1 [minor] `ShareResult.Failed` branch can break without any test failing
- Where: app/src/test/kotlin/com/kino/puber/ui/feature/device/settings/vm/DeviceSettingsVMSessionLogTest.kt:158-169
- Problem: `showMessage` sets `snackBarMessageFlow.value` and nothing clears it between steps. The "chooser refused" step (`shareFile` returns false) leaves `settings_share_log_failed` in `testMessageValue`. The next step, `ShareResult.Failed`, then asserts the same string, which is already there.
- Failure scenario: change `ShareResult.Failed ->` in `DeviceSettingsVM.shareSessionLog` (vm/DeviceSettingsVM.kt:127-128) to show nothing or the wrong text. The stale message still matches and T12 stays green.
- Fix: clear the message before each step (`vm.onAction(CommonAction.SnackBarDismissed)` and `assertNull(vm.message())`), or give the Failed case its own fresh VM.

### F2 [minor] No sink-level test sends `act` or `ready` through the writer or checks the `scr` envelope value
- Where: app/src/main/java/com/kino/puber/core/telemetry/TelemetryWriter.kt:18 (default `folders`), :112-129 (`scr` derivation); tests under app/src/test/kotlin/com/kino/puber/core/telemetry/
- Problem: `ActFolder` is tested only in isolation; no `SinkHarness` test records an `Act` or `Ready` event; nothing tests that the writer folds acts, applies the `act` cap of 240 after folding, or flushes a pending fold on stop; no assertion reads the `scr` value.
- Failure scenario: drop `ActFolder()` from the writer's default list, make `append` always pass `lastScr`, or skip `finishDrain(force = true)` in `stopOnWriter`. Every unit test stays green. Only the device scenario (D5/D6) would catch it.
- Fix: one `SinkHarness` test that records `Nav(to=Home)`, three identical `Act`s within 1 s and one `Ready`, then stops. Assert one `act` line with `n=3`, `scr == "Home"` on the nav, act and ready lines, and `scr == "Home"` on the trailing `meta stop`.

### F3 [minor] Instrumented T05 exists but was never run, and the QA report says it is missing
- Where: app/src/androidTest/kotlin/com/kino/puber/ui/feature/device/settings/DeviceSettingsContentFocusTest.kt:394-409; .flow/runs/telemetry-core/qa-report.md:9,23; docs/superpowers/evidence/2026-10-15-telemetry-core-device.md:154
- Problem: `developerSectionRowsAreFocusableAndLeftReturnsToSectionList` was added in eb1c7974 and compiles. QA looked for the `session-log`/`share-log` keys, missed it, recorded "T05 does not exist" and never ran `make DEVICE=<serial> itest TESTS=...DeviceSettingsContentFocusTest`. It also omits the spec's "seven sections fit without scrolling" check.
- Failure scenario: a focus regression on the disabled Share row (`focusableWhenDisabled`) ships; the added test would catch it, but nobody runs it.
- Fix: run the class through `make DEVICE=<serial> itest TESTS=com.kino.puber.ui.feature.device.settings.DeviceSettingsContentFocusTest` and correct the qa-report and evidence lines.

### F4 [minor] The round-2 name fix has no unit guard, and the proguard tripwire was not updated
- Where: app/src/test/kotlin/com/kino/puber/core/telemetry/ProguardRulesTest.kt:16-24; app/src/main/java/com/kino/puber/core/telemetry/TelemetryNames.kt:105-108
- Problem: Commit 4d786240 added `-keepnames class * implements cafe.adriel.voyager.navigator.tab.Tab`; T45 does not list that rule. `simpleNameIsDerivedFromTheBinaryName` tests only the static helper; on the JVM `Class.simpleName` of nested classes is already the bare name, so reverting `plain()` to `cls.simpleName` also stays green.
- Failure scenario: remove the Tab rule, or switch `plain()` back to `simpleName`. The suite passes and release logs `pm2` / `CommonAction$ItemSelected` again.
- Fix: add the Tab rule to the T45 list. Make `plain()` take the binary name through a small seam, or test `names.plain` with a class whose `simpleName` differs from its binary-name tail. Otherwise state in the test that the device check (D5) is the only guard.

### F5 [minor] T58 tests the "skip flush on the writer thread" guard only through a fake that ignores its thread argument
- Where: app/src/test/kotlin/com/kino/puber/util/ManualTelemetryExecutor.kt:71; app/src/test/kotlin/com/kino/puber/core/telemetry/TelemetryUncaughtHandlerTest.kt:142-152; app/src/main/java/com/kino/puber/core/telemetry/TelemetryExecutor.kt:46
- Problem: `ManualTelemetryExecutor.isWriterThread(thread)` returns `running` whatever thread it is given. The real `ThreadTelemetryExecutor.isWriterThread` is never called with the writer thread in any test.
- Failure scenario: `writerThread` is never assigned, or the comparison is wrong. A crash on `puber-telemetry` then waits the full 1.5 s for itself before the previous handler runs, and T58 stays green.
- Fix: one real-thread case on a `ThreadTelemetryExecutor`: `execute { result = isWriterThread() }` with a latch, assert true, and assert false from the test thread.

### F6 [nit] Some asserts cannot fail
- Where: app/src/test/kotlin/com/kino/puber/core/ui/PuberVMTelemetryTest.kt:142; app/src/test/kotlin/com/kino/puber/core/telemetry/StartupMarksTest.kt:26
- Problem: `assertNull(vm.testMessageValue)` in T52 holds because no message was ever set. The ascending-marks `assertTrue` in T47 only re-checks the order the test chose for its inputs.
- Failure scenario: base `handleAction` stops calling `cleanMessage()`. T16 and T52 stay green.
- Fix: set a message, send `CommonAction.SnackBarDismissed`, then assert null. Drop the ascending check in T47.
