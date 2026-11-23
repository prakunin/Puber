LENS: tests  ROUND: 1  BASE: e4c3b5aa

Suite state: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` in the worktree at a4c19ba4 finished with BUILD SUCCESSFUL in 1m 48s and exit 0. `testDevDebugUnitTest` actually ran; it was not served from cache.

### F1 [minor] `profileStatusReachesMarksOnlyThroughTheTelemetryExecutor` can never fail, and it never guarded R6
- Where: app/src/test/kotlin/com/kino/puber/core/telemetry/TelemetryRuntimeTest.kt:327-344
- Problem: Suppose the listener called `marks.markProfileStatus(code)` directly instead of going through `sink.schedule(0)`. `markProfileStatus` would still only queue `scheduleCheck()` as `schedule(0) { emitWhenComplete() }` on the same `ManualTelemetryExecutor` (StartupMarks.kt:94-99). Both versions behave the same way under this test: the line is missing after `runRepeating()` and present after `runImmediateScheduled()`. The Implementation record calls this test red, but it was red only because the stub never registered the source (callback was null). That is a different failure from the R6 regression the test design says it catches.
- Failure scenario: Change TelemetryRuntime.kt:57 to `profileStatus.onStatus { code -> marks.markProfileStatus(code) }`. The test still passes, so a library-thread write into marks goes unnoticed.
- Fix: Make the test tell the two paths apart. After firing `profileSource.callback`, call `marks.onTimeout()` directly, without running the executor. Then assert that the written `startup` line has no `prof`. A direct call would already have set the status, so the line would carry `prof` and the test would fail.

### F2 [minor] Test design still lists `offStartNeverAsksProfileStatus` as `P0 red`, but the Implementation record shows it green on base and calls it a guard
- Where: /home/dev/Puber/.flow/runs/release-startup-compilation/test-design.md:43; docs/superpowers/plans/2026-11-23-release-startup-compilation.md:241
- Problem: The red run used a stub that never called `onStatus`, so the test passed on the stubbed base. The record calls it a guard, but the traceability row was never changed from `P0 red` to `P0 guard`. The behaviour itself is covered: moving the registration above the `KEY_ENABLED_AT` check makes `asked` equal 1 and the test fails. Only the bookkeeping disagrees. The plan does count "does not compile on base" as red, which partly covers this.
- Failure scenario: Gate 2 reads the row as a proven red-first test, when no red run on the unchanged behaviour was ever recorded.
- Fix: Change the test-design row to `P0 guard`, as was done for `baselineProfileMethodRulesAreHot`. The alternative is a red run against a stub that registers before the switch check.

### F3 [minor] No unit test covers the production wiring in `TelemetryRuntime.install`
- Where: app/src/main/java/com/kino/puber/core/telemetry/TelemetryRuntime.kt:161,172
- Problem: The new runtime tests build `StartupMarks(..., awaitProfileStatus = true)` themselves and inject a fake source. If `install` dropped `awaitProfileStatus = true` or `profileStatus = ProfileVerifierStatusSource`, every unit test would stay green. On the ready path the line would then usually be written before the status arrives, with `prof` missing. The test design says `coldStartStartupLineCarriesProfileStatusFromSource` fails "if `install` builds marks without the code reaching `emit`", which this test cannot detect. The device check M6 does cover it: the qa report shows `prof` on 30 of 30 box starts. So this is recorded as a test-design overclaim, not a gap in coverage.
- Failure scenario: A later refactor of `install` loses `awaitProfileStatus = true`. Unit tests stay green and only a device run shows that `prof` is missing.
- Fix: Correct the "Proves" text of that row to name M6 as the guard for the `install` wiring. Optionally, move the marks construction into a small factory that the test can call.

Coverage: every P0 and P1 row in the test design maps to a test of that name, or to a device or manual check (M1-M8) that the qa report marks PASS. Each P0 unit test fails if its own behaviour breaks; I checked this against the code for StartupMarks, the encoder, the runtime registration, the hot-rule rewrite and the profile content. No assertions were removed and no tests were skipped in this diff. No AC is left without a test or a check.

### Unproven
- app/src/main/java/com/kino/puber/core/telemetry/ProfileStatusSource.kt:24 `future.get()` throwing `CancellationException` or `InterruptedException` is not caught and has no test. This would matter only with evidence that `ProfileVerifier`'s future is ever cancelled.
- app/src/test/kotlin/com/kino/puber/BaselineProfileContentTest.kt:19 `hasRuleFor` matches the prefix anywhere in a line, so the `ui/feature/player/` prefix is already matched by two navigation signatures (`Screens.player`, `ScreensImpl.player`). The more specific `PlayerVM` prefix has no outside matches today, so the coverage check still holds. This would become real if `PlayerVM` started to appear in another class's signatures.
