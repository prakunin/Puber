LENS: tests  ROUND: 1  BASE: cd16eb47

Suite: `testDevDebugUnitTest` on HEAD 284bd7a0 passed. Gradle reported the task UP-TO-DATE, so it reused results from the same inputs: 1971 tests, 0 failures, 0 errors, 0 skipped. That means the ThreadMXBean allocation tests (K13, F10) really ran. I made no edits and ran no mutation probes: my role rules forbid editing the worktree, so the mutations below are reasoned through, not executed.

Every AC has unit rows plus device evidence (D1-D12 all PASS). The UNTESTABLE items are documented. T3 and T9 were not observed red first, but their key paths are covered by the recorded mutation checks (W1-W4, F12, F13, L5, L6, L9). I checked those tests by hand and they fail when the behaviour breaks.

### F1 [major] `KeyProbe.onKey` gating has no test; C5 does not test what its row says
- Where: app/src/main/java/com/kino/puber/core/telemetry/KeyProbe.kt:127-131; app/src/test/kotlin/com/kino/puber/core/telemetry/KeyLatencyMatcherTest.kt:61
- Problem: spec row C5 (P0) says "DOWN r0 arms, repeats 1..20 do not re-arm". The test calls `matcher.arm` once and sends no repeats. The matcher has no idea of repeats: the filter `action == ACTION_DOWN && repeat == 0` and the pass-through of `handled` live only in `KeyProbe.onKey`. The only test that drives `onKey` is `KeyProbeOffTest`. It uses `handled=true` throughout, and all its arms fall within one second, so the one-slot refusal hides any extra arm. L10 sends `handled=false` but checks only the `key` line.
- Failure scenario: someone drops `&& repeat == 0`, or the `action` check, or passes `true` for `handled`. All tests stay green. On a device, a held DPAD_DOWN re-arms on every repeat once the first answer has consumed the slot, so each repeat (and each UP) writes a `key_lat` measured from the repeat. That fills the 60/min cap with false lines, and unhandled keys get measured too.
- Fix: add a test through `KeyProbe.onKey` (with `resetForTests` in teardown) and a frame after each event:
  - DOWN r0, answered by a fast frame, then r1/r2 and an UP, each followed by a slow frame. Exactly one `KeyLat` comes out, from r0.
  - An unhandled r0 DOWN followed by a slow frame gives no `KeyLat`.

### F2 [minor] Switching the log on while the app is in the foreground: nothing tests that the memory timer starts
- Where: app/src/main/java/com/kino/puber/core/telemetry/TelemetryProbe.kt:75
- Problem: `ActivityProbes.attach` calls `memoryProbe.onForeground(callbacks.isForeground)`. That is the normal path, because the user switches the log on in Settings while the app is in front. L9 asserts `repeatingCount == 0` only when the app is not in the foreground. L12 attaches before any `onActivityStarted`, so its timer comes from `onActivityStarted`, not from `attach`. L7 never checks `mem`.
- Failure scenario: someone deletes line 75. All tests stay green, and after switching on in Settings no periodic `mem` line appears until the app goes to the background and comes back. Only the one-shot device check D4 would show it.
- Fix: in `TelemetryActivityCallbacksTest`, call `onActivityStarted(activity)` while off, then `enabled = true; probes.attach()`. Assert `repeatingPeriods == [60_000]`.

### F3 [minor] Nothing tests that `key` and `mem` lines carry the current screen
- Where: app/src/main/java/com/kino/puber/core/telemetry/KeyProbe.kt:127; app/src/main/java/com/kino/puber/core/telemetry/MemoryProbe.kt:99
- Problem: both probes read `Telemetry.screen` and put it into the event. No test sets the holder and then checks `scr` on a `key` or `mem` line that went through the probe. W2 checks only the writer branch, with a hand-built event. `KeyBurstThroughSinkTest` passes the screen to the machine directly. L10 sets the holder but counts lines only. `MemoryProbeTest` never sets the holder. K5 uses one screen for the whole burst, so it cannot tell "screen at burst open" from "screen at close".
- Failure scenario: someone replaces `Telemetry.screen` with `null` in either probe. Every `key` and `mem` line then gets `scr ""` (spec answer Q2 relies on `scr` to attribute `mem`), and all tests stay green.
- Fix:
  - In L10, assert `scr == "Home"` on the `key` and `frames` lines.
  - In `MemoryProbeTest.foregroundSchedules...`, set `Telemetry.screen = HomeScreen::class.java` and assert `scr == "Home"`.

### F4 [minor] `java_max_kb` and `native_kb` values are never checked
- Where: app/src/main/java/com/kino/puber/core/telemetry/MemoryProbe.kt:101-102; app/src/main/java/com/kino/puber/core/telemetry/TelemetryEncoder.kt (`putMem`); app/src/test/kotlin/com/kino/puber/core/telemetry/MemoryProbeTest.kt:55
- Problem: `RecordingMemorySampler` returns four distinct values (100, 200, 300, 400). M1 asserts only `java_kb == 100` and `pss_kb == 400`. `TelemetryEncoderTest.newTypesWriteTheirValues` covers frames, key and key_lat values but not mem.
- Failure scenario: someone swaps `javaMaxKb` and `nativeKb` in the `Mem(...)` call or in `putMem`. The lines are mislabelled and all tests stay green.
- Fix: in M1, add `assertEquals("200", line.str("java_max_kb"))` and `assertEquals("300", line.str("native_kb"))`.

### F5 [minor] L13 cannot fail for the bug it names
- Where: app/src/test/kotlin/com/kino/puber/core/telemetry/KeyProbeOffTest.kt:42
- Problem: the `RecordingTelemetryRecorder` is installed only after `feed(enabled = false)`, so `record` calls made during the off feed are never counted. Rule 1 and row L13 ask for "record calls on a counting sink are zero". Each press in the feed also ends with an UP, so a machine that wrongly received the off feed would be closed again and leave no trace. The implementation record admits L13 stayed green under the "off branch calls the probe" mutation. K1 is the only real guard.
- Failure scenario: the off branch of `dispatchWithProbe` feeds the probe and the clock-dependent parts happen to line up (as with the `0L` mutation in the record). L13 still passes.
- Fix: install the recorder (with `isEnabled = true`) before `feed(enabled = false)`, assert `recorder.events.isEmpty()` right after the feed, and end the off feed on an open DOWN with no UP.

### F6 [minor] Device evidence says `frames.t` behaves differently from what F8 checks
- Where: docs/superpowers/evidence/2026-10-16-telemetry-frames-keys-memory-device.md:52; app/src/main/java/com/kino/puber/core/telemetry/FrameAggregator.kt:52
- Problem: F8 checks that `t` is back-dated to the end of the window's last frame. The evidence says "`frames.t` is back-dated to the window's first frame by design". On a device that happens only when the clamp to `openedMs` fires. The clamp fires only if `nanoTime - lastEndNs` is large, which would mean `frameStartNanos` does not share the `System.nanoTime` base. The unit tests assume a shared base, so they cannot see this. No device step checks `t` against the time of the last frame.
- Failure scenario: if the clamp fires on every window on hardware, every `frames.t` is the window's open time and the D10 back-dating is lost. Every unit test still passes, and D3 still passes, because it checks only `t >= 0` and order.
- Fix: correct the evidence wording if it is just loose, or check on the device: for a window closed by a screen change, compare `frames.t` with the `t` of the following `nav` line. They should be within one frame, not about 10 s apart. Record the result.
