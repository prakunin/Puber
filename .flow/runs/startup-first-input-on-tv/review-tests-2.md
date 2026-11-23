I found no new problems in the round-2 changes (`6eb30d7c..9d454407`). The four touched test classes passed. Round-1 F1 (AC-6 measured at the wrong times) depends on qa re-measuring on the device; this round changed no evidence for it, so it is still open there.

```
LENS: tests  ROUND: 2  BASE: c506d0bd
Suite (incremental only): `./tools/agentw :app:testDevDebugUnitTest` with `--tests` for RevealGateTest, StartupImageActivityUnmeasuredTest, KeyLatencyMatcherConcurrencyTest, TelemetryEncoderTest, StartupMarksTest and KeyProbeGateTest at 9d454407 exited 0. The test task actually ran this time (not UP-TO-DATE). XML for the four touched classes: RevealGateTest 24/0 failures, StartupImageActivityUnmeasuredTest 1/0, KeyLatencyMatcherConcurrencyTest 1/0, TelemetryEncoderTest 24/0; none skipped. No assertion was removed in this diff. The frozen test files are untouched.

Fix-round coverage:
- Row 2: `revealNowBeforeBegin_reportsForcedOnceWithoutCovering` fails on 6eb30d7c. There, `update` reports only when `begun` is true, so the log stays empty; this matches the red run in the plan. The test also checks that a second `revealNow` and a later `markReady` do not report again, that the gate never covers, and that `remainingCapMs` stays 0.
- Row 2 downstream: for a restored start, `MainActivity.kt:88` (`LaunchedEffect(covered)` with covered false from the start) supplies `markRevealed`. StartupMarksTest :181 and :225 already cover a FORCED cause emitting at once.
- Row 3: the encoder key sets now expect `dropped_keys`, so dropping the unconditional `put` fails 3 tests. `StartupMarksTest.droppedKeysAreCountedOnTheLine` keeps the value check.
- Row 4: StartupImageActivityUnmeasuredTest fails if `onStart` forwards to `gate.onImageStarted`: the request stays in flight, so `covered` stays true after `markReady`. The plan records it as a guard (green on base by design), which is allowed.
- Row 5: `waitMs = dispatchNs/NS_PER_MS - eventMs = i` gives each armed key its own id (KeyProbe.kt:116). A second emit of any key now increments `duplicates`, and arms refused at MAX_PENDING no longer hide extra emits.

No findings.

### Unproven
- /home/user1/orca/workspaces/Puber/startup-first-input-on-tv/app/src/main/java/com/kino/puber/core/startup/RevealGate.kt:110 a forced reveal before begin() on a cold start (for example `onUpdatePromptOpenChanged(true)` before `onActivityCreated(false)`) now reports FORCED, and begin() then allows a second report. No test covers this order, and StartupMarks keeps only the first cause. It becomes real if the update prompt can open before `onActivityCreated`.
- /home/user1/orca/workspaces/Puber/startup-first-input-on-tv/app/src/test/kotlin/com/kino/puber/core/telemetry/KeyLatencyMatcherConcurrencyTest.kt:40 `answerer.join(5_000)` does not assert the thread finished, so a stuck answerer would be checked on partial data. This was already there before; it becomes real if this test is ever seen passing with the thread still alive.
```

Files reviewed:
- /home/user1/orca/workspaces/Puber/startup-first-input-on-tv/app/src/main/java/com/kino/puber/core/startup/RevealGate.kt
- /home/user1/orca/workspaces/Puber/startup-first-input-on-tv/app/src/test/kotlin/com/kino/puber/core/startup/RevealGateTest.kt
- /home/user1/orca/workspaces/Puber/startup-first-input-on-tv/app/src/test/kotlin/com/kino/puber/core/startup/StartupImageActivityUnmeasuredTest.kt
- /home/user1/orca/workspaces/Puber/startup-first-input-on-tv/app/src/test/kotlin/com/kino/puber/core/telemetry/KeyLatencyMatcherConcurrencyTest.kt
- /home/user1/orca/workspaces/Puber/startup-first-input-on-tv/app/src/test/kotlin/com/kino/puber/core/telemetry/TelemetryEncoderTest.kt
