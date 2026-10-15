LENS: tests  ROUND: 2  BASE: 39903d31 (incremental fc5738b1..0f20e63a)

Suite: targeted testDevDebugUnitTest (core.telemetry.*, PuberVMTelemetryTest, DeviceSettingsVMSessionLogTest, HomeVMTest) at 0f20e63a BUILD SUCCESSFUL; tree clean. Reasoned reverts: rows 1, 8, 9, 10, 2, 5, 3, 4, 6, 7, 12, 15, 16, 17 have tests that fail when the fix is reverted. Row 14 (instrumented T05) still pending on device (qa-device).

### F1 [minor] The "export flushes pending folds" test cannot fail if the new `finishDrain` in export is removed
- Where: app/src/test/kotlin/com/kino/puber/core/telemetry/TelemetryExportTest.kt:33-43; app/src/main/java/com/kino/puber/core/telemetry/TelemetrySink.kt:111
- Problem: The two `IllegalStateException("boom")` are created on different lines, so their fold keys differ and each is written immediately. `TelemetryWriter.export()` already calls `flushFolders(force = true)`; the only thing `finishDrain(force = true)` adds is `emitDropped()`, and no test checks the `meta dropped` line reaches the archive.
- Failure scenario: delete `writer.finishDrain(force = true)` from `exportOnWriter`; both tests stay green; a Share right after a queue overflow has no `dropped` line.
- Fix: one shared Throwable for both errors so they fold; add a case that overflows the queue (> QUEUE_BOUND without a drain), exports, and asserts a `meta` line with `ev=dropped` in the archive.

### F2 [minor] The `scr` checks on `act` and `ready` would still pass if every line just reused the last screen
- Where: app/src/test/kotlin/com/kino/puber/core/telemetry/TelemetrySinkTest.kt:160-182; app/src/main/java/com/kino/puber/core/telemetry/TelemetryWriter.kt:110-115
- Problem: Act/Ready come from HomeVM after Nav(to=Home); owner and lastScr are both "Home", so per-event derivation is indistinguishable from reusing lastScr.
- Failure scenario: make `append` always use `lastScr`; a dialog or nested VM's act/ready gets the previous screen's name; tests stay green.
- Fix: also record an Act or Ready from a VM whose owner is not the last nav's screen; assert its `scr` is that owner and the trailing `meta stop` still says "Home".
