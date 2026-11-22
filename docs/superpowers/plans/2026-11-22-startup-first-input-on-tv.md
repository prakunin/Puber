# Measure and fix the first D-pad response after the splash reveal on a real TV - plan

**Spec:** `docs/superpowers/specs/2026-11-22-startup-first-input-on-tv.md`
**Goal:** Add the reveal and key telemetry (AC-1, AC-2), take the box baseline (AC-3), then write the `startup` line after `ready` and its drain with `ready_req`, add `wait_ms` to `key_lat`, and re-measure on the box (AC-5, AC-6). Tasks 1-6 are phase A and the baseline; Tasks 7-10 are round 3 after the gate-1 delta.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`; full gate: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`. Worktree `/home/user1/orca/workspaces/Puber/startup-first-input-on-tv` never uses `./gradlew`; prefix `AGENT_GRADLE_SERIAL=1` when another worktree may build.
- Touched tests: `./tools/agentw :app:testDevDebugUnitTest --tests '<fqcn>'` (one `--tests` per class).
- One task = green fast gate + green tests of the touched code + one commit, message: one imperative English sentence, no prefixes, never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- Paths: main sources under `app/src/main/java/com/kino/puber/`, tests under `app/src/test/kotlin/com/kino/puber/`; both prefixes are dropped below. Line numbers: Tasks 1-5 at base c506d0bd; Tasks 7-10 at f48beb86 (main and test sources are unchanged from there to bb1af218).
- Test names, rows and red/guard status: spec section Test design, Traceability table. A test named there is written with exactly that name.
- Not edited (AC-9): `core/startup/StartupRevealTest.kt`, `StartupKeyFilterTest.kt`, `StartupSettledTest.kt` (unit) and `app/src/androidTest/kotlin/com/kino/puber/ui/feature/home/component/HomeLaunchFocusSettledTest.kt`; from Task 7 on also `core/startup/StartupImageActivityTest.kt`.
- Device work (Tasks 6, 10) is qa-device only, on box `192.168.1.110:5555` only, package `com.kino.puber.stage` only, build `devBenchmarkRelease` of this worktree; never the emulator, never `com.kino.puber`, never uninstall, clear or downgrade. `cmd package compile` is not authorised (spec Decisions).
- AC-5 and AC-6 are observations recorded by Task 10 on the Task 9 commit; AC-9 is settled by the full gate at Task 9.
- Round-3 red runs: a test marked `red` in the test design's Round 2 table runs red on the tree before its task's code, against signature-only stubs where a symbol is missing (as Task 4 did), and fails on an assertion, not on compilation.

---

## Tasks

### Task 1: Carry the new startup and key_lat fields on the wire

**Covers:** AC-1, AC-2
**Files:**
- Create: `core/startup/RevealObserver.kt` - `enum class RevealCause(val wire: String) { READY("ready"), CAP("cap"), FORCED("forced") }` and `interface RevealObserver { onReady(inFlight: Int); onImagesIdle(); onHeldDrained(); onRevealed(cause: RevealCause, inFlight: Int) }` with a no-op `RevealObserver.None`; KDoc: called inside the gate lock, must not block or call back into the gate.
- Modify: `core/telemetry/TelemetryEvent.kt:130-139` - append to `Startup`: `cause: RevealCause? = null`, `readyMs: Long? = null`, `imagesIdleMs: Long? = null`, `inFlight: Int? = null`, `readyInFlight: Int? = null`, `readyDrainedMs: Long? = null`, `droppedKeys: Int? = null`.
- Modify: `core/telemetry/TelemetryEvent.kt:207-215` - `KeyLat.latMs: Long?`, append `expired: Boolean = false`.
- Modify: `core/telemetry/TelemetryEncoder.kt:67-73` (`putStartup`) - write, each only when non-null: `cause` (`wire`), `ready`, `img_idle`, `ready_drained` (each minus `procStartMs`, as `:70-72`), `in_flight`, `ready_in_flight`, `dropped_keys` (raw counts).
- Modify: `core/telemetry/TelemetryEncoder.kt:114-118` (`putKeyLat`) - `lat_ms` only when `latMs != null`; `expired: true` only when `expired`; `frame_ms` always.
- Test: `core/telemetry/TelemetryEncoderTest.kt` - add `startupWritesRevealCauseAndImageFields` (also asserts `dropped_keys`), `startupOmitsImageFieldsNotReached`, `keyLatExpiredOmitsLatMs`. Guards `startupWritesRevealed` (`:128`), `startupOmitsMarksNotReached` (`:134`), `everyEventTypeEmitsExactlyEnvelopePlusCatalogueFields` (`:73`, samples `:25-69`) unchanged.
- Test (call shape only): `core/telemetry/KeyLatencyMatcherTest.kt:25,48,57,66,74,107` and `core/telemetry/FrameProbeTest.kt:148` - `assertEquals(<n>L, <event>.latMs)`; an `Int` literal against `Long?` boxes to `Integer` and fails.

**Consumes:** `TelemetryEvent.Startup`, `KeyLat` as at base.
**Produces:** `RevealCause`, `RevealObserver`, the new nullable `Startup` fields, `KeyLat(latMs: Long?, expired)`.

- [x] Write the three encoder tests; run them red
- [x] Add `RevealObserver.kt`, the event fields and the encoder branches
- [x] Fix the `latMs` literals; run fast gate and `TelemetryEncoderTest`, `KeyLatencyMatcherTest`, `FrameProbeTest`, `KeyNameTest`; commit "Carry reveal cause, image marks, dropped keys and expired key latency on the telemetry wire"

**Done when:** the four classes pass and `git grep -n '"lat_ms"' app/src/main` shows the guarded write only.

---

### Task 2: Record the reveal, image and dropped-key marks in StartupMarks

**Covers:** AC-1, AC-2
**Files:**
- Modify: `core/telemetry/StartupMarks.kt:10-61` - new methods, each stamping `clock.elapsedMs()` at call time and none calling `emit`/`emitWhenComplete`: `markReady(inFlight: Int)` (first wins; stores time and count), `markImagesIdle()` (last wins), `markHeldDrained()` (first wins), `markRevealCause(cause: RevealCause, inFlight: Int)` (first wins; count stored with it), `onKeyDropped()` (`AtomicInteger` increment). Atomics only (no locks). `emit` (`:42-54`) passes them to `Startup`; unreached marks as `null` (`reached()` `:56` pattern, `-1` sentinel for counts); `droppedKeys` always the counter value.
- Test: `core/telemetry/StartupMarksTest.kt` - add `revealCauseAndImageMarksAreCarriedOnTheLine`, `revealCauseKeepsTheFirstValue`, `imagesIdleKeepsTheLastValue`, `imageMarksAreAbsentWhenNotReached`, `newMarksNeverEmitTheLine`, `droppedKeysAreCountedOnTheLine`. Use the class fixture (`:9-11`, `FakeTelemetryClock`).

**Consumes:** Task 1 `RevealCause`, `Startup` fields.
**Produces:** the five `StartupMarks` methods named above.

- [x] Write the six tests; run them red
- [x] Implement the marks
- [x] Fast gate, `StartupMarksTest`, `TelemetryRuntimeTest`; commit "Record reveal cause, ready, image idle, held drain and dropped keys in the startup marks"

**Done when:** `StartupMarksTest` passes with the six new tests.

---

### Task 3: Answer every pending key and stretch key_lat expiry to five seconds

**Covers:** AC-2
**Files:**
- Modify: `core/telemetry/KeyProbe.kt:9` - `EXPIRY_NS = 5_000_000_000L`; add `MAX_PENDING = 16`.
- Modify: `core/telemetry/KeyProbe.kt:71-106` (`KeyLatencyMatcher`) - `AtomicReference<List<Pending>>` (immutable lists, CAS loop). `arm`: unhandled returns (`:82`); a full list returns; otherwise append. `onFrame`: answers every pending key with `dispatchNs <= startNs` and removes them in one CAS; later keys stay. Per answered key: `startNs - dispatchNs > EXPIRY_NS` emits `KeyLat(..., latMs = null, frameMs, expired = true)`; otherwise the existing `latMs` rule and 50 ms floor (`:99-100`). Index loops; no allocation when the list is empty or nothing is answered. Update the class KDoc (`:66-70`). `clear` empties the list.
- Test: `core/telemetry/KeyLatencyMatcherTest.kt` - `eachPendingKeyIsAnsweredByTheFirstFrameAfterIt` replaces `secondDownWhilePendingDoesNotReplaceTheEarliest` (`:70`); `keyAnsweredBetweenOneAndFiveSecondsIsEmitted` replaces `pendingExpiresAfterOneSecondAndLateFrameEmitsNothing` (`:88`); add `frameBetweenTwoPendingKeysAnswersOnlyTheEarlier`, `keyUnansweredPastFiveSecondsIsEmittedExpiredWithoutLat`, `pendingListHoldsAtMostSixteenKeys`. `noFrameMeansNoLine` (`:101`) now expects three lines, `lat` 1101, 601, 100 (AC-2 contract change). Guards `fiftyIsNotSlowAndFiftyOneIs`, `answeredEntryIsConsumedAndNotEmittedTwice`, `unhandledKeyNeverArms`, `frameStartingBeforeDispatchDoesNotAnswer` and `KeyLatencyMatcherConcurrencyTest.armOnOneThreadAnswerOnAnotherNeverDoubleEmits` unchanged.

**Consumes:** Task 1 `KeyLat`.
**Produces:** `KeyLatencyMatcher` with the same `arm`/`onFrame`/`clear` signatures.

- [x] Write the five red tests; run them red
- [x] Implement the pending list
- [x] Fast gate, `KeyLatencyMatcherTest`, `KeyLatencyMatcherConcurrencyTest`, `FrameProbeTest`, `KeyThreadingTest`; commit "Answer every pending key and report keys answered after five seconds as expired"

**Done when:** those classes pass.

---

### Task 4: Stop keys dropped under the splash from arming the latency matcher

**Covers:** AC-2
**Files:**
- Modify: `core/telemetry/KeyProbe.kt:108-119` - `dispatchWithProbe(enabled, dropped: Boolean, superDispatch, probe: (handled: Boolean, dropped: Boolean, dispatchNs: Long) -> Unit)`. A dropped key returns `true` without calling `superDispatch`, also when `enabled` is false; enabled, it is probed with `handled = true, dropped = true`. Non-dropped keys return super's value exactly as now.
- Modify: `core/telemetry/KeyProbe.kt:126-132` - `KeyProbe.onKey(..., handled: Boolean, dropped: Boolean = false)`: bursts unchanged; on `ACTION_DOWN` with `repeat == 0`, a dropped key calls `Telemetry.startupMarks?.onKeyDropped()` and never `arm`.
- Modify: `MainActivity.kt:59-71` - compute `val dropped = coveredKeys.shouldDrop(StartupReveal.isCovered, event.action, event.keyCode)` once (`:44`, `core/startup/StartupKeyFilter.kt:25-33`), call `dispatchWithProbe(Telemetry.isEnabled, dropped, { super.dispatchKeyEvent(event) }) { handled, dropped, ns -> KeyProbe.onKey(..., handled, dropped) }`; keep the comment at `:53-58` accurate.
- Test: `core/telemetry/KeyProbeGateTest.kt` - `droppedKeyDoesNotBlockTheNextHandledKey` first (spec section Bug): `key(ACTION_DOWN, 0, 1_000, dropped = true)`, `key(ACTION_DOWN, 0, 1_100)`, `frame(1_101, 200)`; then `val lats = recorder.events.filterIsInstance<TelemetryEvent.KeyLat>()`, `assertEquals(1, lats.size)` and `assertEquals(201L, lats.single().latMs)` (the handled key: 1_101 + 200 - 1_100; the dropped key would give 301). Then `droppedFirstDownIsCountedOnceAndRepeatsAreNot` (sets `Telemetry.startupMarks` to a `StartupMarks` over `recorder`, reads `droppedKeys` after `onTimeout()`). Helper `key` (`:29-30`) gains `dropped: Boolean = false`, passed to `KeyProbe.onKey`.
- Test: `core/telemetry/DispatchWithProbeTest.kt` - `droppedKeyReturnsTrueWithoutCallingSuper` replaces `aKeyDroppedBySuperIsStillProbed` (`:38`); add `droppedKeyIsProbedAsDropped`; guards `:13`, `:23`, `:46` and `superThatThrowsPropagatesAndProbeIsNotCalled` (`:54`) change call shape only.
- Test (call shape only): `core/telemetry/KeyProbeOffTest.kt:21-35`.

**Consumes:** Task 2 `StartupMarks.onKeyDropped`, Task 3 matcher.
**Produces:** `dispatchWithProbe(enabled, dropped, superDispatch, probe)`, `KeyProbe.onKey(..., dropped)`.

- [x] Add the `dropped: Boolean = false` parameter to `KeyProbe.onKey` and `dispatchWithProbe` (probe lambda gains `dropped`) with no behaviour yet: `onKey` still arms a dropped key, `dispatchWithProbe` still calls super; update callers so the fast gate is green
- [x] Write `droppedKeyDoesNotBlockTheNextHandledKey` and the other three red tests; run them red. `droppedKeyDoesNotBlockTheNextHandledKey` must fail on the assertion (two `KeyLat`, 301 and 201), not on compilation; record the failure message in the Red run
- [x] Implement
- [x] Fast gate, `KeyProbeGateTest`, `DispatchWithProbeTest`, `KeyProbeOffTest`, `StartupKeyFilterTest`; commit "Count keys dropped under the splash and keep them out of the key latency matcher"

**Done when:** those classes pass; `StartupKeyFilterTest` is untouched (`git diff --stat` shows no change to it).

---

### Task 5: Track image requests by identity in RevealGate and report its edges

**Covers:** AC-1
**Files:**
- Modify: `core/startup/RevealGate.kt:10-86` - constructor param `observer: RevealObserver = RevealObserver.None`. Replace `imagesBusy` (`:26`) and `onImagesBusy` (`:57`) with an identity set `inFlight` (`Collections.newSetFromMap(IdentityHashMap())`) and `onImageStarted(key: Any)` / `onImageFinished(key: Any)`; a second finish of one key changes nothing. In `markReady` (`:45-50`), when `ready` first takes effect inside `afterFrame`: `held = copy(inFlight)`, `observer.onReady(held.size)`. Covering rule (`:78`) becomes `!(ready && inFlight.isEmpty())`, same effect as base. Observer calls only while `begun && !revealedOnce`: `onImagesIdle()` on each non-empty-to-empty edge of `inFlight`; `onHeldDrained()` once, when a non-empty `held` empties (later requests ignored). At the reveal edge (`:79`): `onRevealed(cause, inFlight.size)`, cause `CAP` from `onCap` (`:52`), `FORCED` from `revealNow` (`:55`), else `READY`. `begin` (`:31-38`) clears `held` and keeps `inFlight`; `reset` (`:65-73`) clears both.
- Modify: `core/startup/StartupImageActivity.kt:16-41` - `StartupImageActivity(private val gate: RevealGate)`: `resolveSizeEnd` -> `gate.onImageStarted(request)`; `onSuccess`/`onError`/`onCancel` -> `gate.onImageFinished(request)`; own set, lock and `reset` removed; KDoc updated.
- Modify: `core/startup/StartupReveal.kt:14-19` - gate built with an observer delegating to `Telemetry.startupMarks?.markReady / markImagesIdle / markHeldDrained / markRevealCause`; `imageActivity = StartupImageActivity(gate)`. `resetForTests` (`:53-58`) drops `imageActivity.reset()`.
- Test: `core/startup/RevealGateTest.kt` - add `revealByReady_reportsCauseReady`, `revealByCap_reportsCauseCapWithInFlightCount`, `revealNow_reportsCauseForced`, `markReady_reportsInFlightCountWhenReadyTakesEffect` (`afterFrame` list as `:57-67`), `imagesIdleEdge_reportedOnlyBeforeTheReveal`, `heldDrained_reportedWhenRequestsInFlightAtReadyAllEnd`, `sameRequestFinishedTwice_doesNotUncoverOthers`, with a recording `RevealObserver`. Guards `:83-136` (`readyWaitsForImagesInFlight` to `beginKeepsImagesBusyFromBeforeIt`) rewritten to `onImageStarted`/`onImageFinished`, same `covered` expectations.
- Test: `core/startup/StartupImageActivityTest.kt` - fixture (`:12-13`) becomes a real `RevealGate(clock = { 0 })` with a recording observer and `StartupImageActivity(gate)`; guards `:18`, `:29`, `:41` assert `gate.covered` after `begin()` + `markReady()` (sized request keeps it covered until its end callback; `onStart` alone does not); `inFlightCountAtRevealCountsDistinctRequests` replaces `busyIsReportedOnlyOnEdges` (`:51`): repeated `resolveSizeEnd` of one request counts once in `onRevealed` after `onCap`.

**Consumes:** Task 1 `RevealObserver`, `RevealCause`; Task 2 marks.
**Produces:** `RevealGate(clock, capMs, afterFrame, observer)`, `onImageStarted`, `onImageFinished`.

- [x] Write the eight red tests; run them red
- [x] Implement gate, forwarder, wiring; rewrite the guards
- [x] Fast gate, then the full gate (end of phase A); commit "Track startup image requests by identity in the reveal gate and report its reveal cause and image edges"

**Done when:** the full gate passes; `StartupRevealTest` unchanged; this commit's sha is recorded as the phase-A commit.

---

### Task 6: Box baseline (AC-3)

**Covers:** AC-3
**Owner:** qa-device, mode baseline, on the phase-A commit (Task 5), not on base.
**Files:**
- Create: `docs/superpowers/evidence/2026-11-22-startup-first-input-on-tv-baseline-device.md`, plus `{run}/baseline.md` (`/mnt/data/projects/PetProjets/Puber/.flow/runs/startup-first-input-on-tv/baseline.md`) with the same numbers.

- [ ] Lease the box, `adb connect 192.168.1.110:5555`, `make DEVICE=192.168.1.110:5555 auth-save` before the first install; install this worktree's `devBenchmarkRelease` APK with `tools/android-apk-install-preserve`; confirm the telemetry session log is on
- [ ] Manual check 1 of the spec, steps 2-4 and 6: at least 10 cold starts (`am force-stop`, `am start -W`), one `KEYCODE_DPAD_DOWN` per start, anchors rotated start by start over launch +3 s, reveal +300 ms, reveal +1000 ms, at least 10 starts per anchor; screenrecord each start; atrace on 3 starts
- [ ] Per start record: `cause`, `ready`, `revealed`, `img_idle`, `in_flight`, `ready_in_flight`, `ready_drained`, first-key `key_lat` per anchor (no line = 50 ms, `expired` = fail), `dropped_keys`, hero drawn at fade end (yes/no)
- [ ] Step 5: one set of 8428c946 launch-to-visible-content, built outside this worktree without `git worktree add` (`git archive 8428c946` into a scratch dir, built with its own `tools/agentw`). Its dev flavor has the same versionCode as the phase-A APK (`versionCode = Versions.DebugVersionCode`, `app/build.gradle.kts:54`, `buildSrc/src/main/kotlin/Versions.kt:22`, 2090000000, unchanged at 8428c946), so install-preserve over it is not a downgrade
- [ ] Reinstall the phase-A APK if the reference set replaced it; release the lease; run `tools/mobile-evidence-audit.sh docs/superpowers/evidence com.kino.puber.stage`; commit the evidence "Add the box baseline of the first key after the splash reveal"

**Done when:** the evidence file has at least 10 start rows with every field above, the audit is clean, and the box still runs `com.kino.puber.stage` logged in.

---

### Task 7: Write the startup line after ready and its drain, or five seconds after the reveal

**Covers:** AC-1 (emission rule, `ready_drained` after the reveal, 5 s bound, 60 s fallback)
**Files:**
- Modify: `core/startup/RevealGate.kt:78-85` (`onImageFinished`) - split the condition: `onImagesIdle()` only when `begun && !revealedOnce && inFlight.isEmpty()` (as now); `onHeldDrained()` when `begun && wasHeld && held.isEmpty()`, also after the reveal. Covering rule (`:105-113`) unchanged.
- Modify: `core/telemetry/StartupMarks.kt:9-18` - constructor param `schedule: (delayMs: Long, task: () -> Unit) -> Unit = { _, task -> task() }` (last; the inline default keeps the other callers, `ui/feature/contentlist/vm/SectionVMTest.kt:201`, `core/ui/PuberVMTelemetryTest.kt:136,153,169,197`, `core/telemetry/KeyProbeGateTest.kt:65`, unchanged); `internal const val LINE_WAIT_AFTER_REVEAL_MS = 5_000L`; `boundPassed: AtomicBoolean`. KDoc states the rule below and that production passes the telemetry executor.
- Modify: `core/telemetry/StartupMarks.kt:36-62` - `markFirstContent`, `markRevealed`, `markReady`, `markHeldDrained`, `markRevealCause` stamp as now, then call `schedule(0) { emitWhenComplete() }`; none calls `emitWhenComplete` or `emit` directly (gate marks run inside the gate lock, `core/startup/RevealObserver.kt:13`). `markReady` schedules after both `readyMs` and `readyInFlight` are set. `markRevealed` also calls `schedule(LINE_WAIT_AFTER_REVEAL_MS) { boundPassed.set(true); emitWhenComplete() }` on its first stamp only. `markImagesIdle`, `onKeyDropped` do not schedule.
- Modify: `core/telemetry/StartupMarks.kt:70-72` (`emitWhenComplete`) - emit when `firstContentMs` and `revealedMs` are reached and one of: `revealCause == FORCED`; `boundPassed`; `readyMs` reached and (`readyInFlight == 0` or `readyDrainedMs` reached). `readyInFlight` still `NOT_REACHED` counts as not complete. `emit` (`:74-93`) stays single-shot; `onTimeout` (`:68`) unchanged.
- Modify: `core/telemetry/TelemetryRuntime.kt:163` - `StartupMarks(Process.getStartElapsedRealtime(), appCreateMs, sink::record, SystemTelemetryClock, sink::schedule)` (`TelemetrySink.kt:128-130` enqueues on the telemetry thread, `TelemetryExecutor.kt:32-34`).
- Test: `core/startup/RevealGateTest.kt` - `heldDrained_reportedAfterACapReveal` first: gate with `RecordingRevealObserver`, `begin()`, `onImageStarted(a)`, `onCap()`, `markReady()`, `onImageFinished(a)`; log is `["revealed:CAP:1", "ready:1", "drained"]` (no `idle`). `imagesIdleEdge_reportedOnlyBeforeTheReveal` (`:204`) and `heldDrained_reportedWhenRequestsInFlightAtReadyAllEnd` (`:220`) unchanged.
- Test: `core/telemetry/StartupMarksTest.kt` - fixture (`:10-12`) keeps `marks` on the inline default; add `queued: MutableList<Pair<Long, () -> Unit>>`, `queuedMarks` built with `schedule = { d, t -> queued += d to t }`, and `runQueued(delayMs: Long? = null)` that removes and runs the queued tasks with that delay (all when null). New, all on `queuedMarks`:
  - `lineWaitsForReadyAfterACapReveal` (write first): `markFirstContent`, `markRevealCause(CAP, 3)`, `markRevealed`, `runQueued(0)`: no line; `markReady(0)`, `runQueued(0)`: one line, `readyMs` set, `cause` CAP.
  - `lineWaitsForHeldDrainWhenReadyHeldRequests`: after the reveal, `markReady(2)`, `runQueued(0)`: no line; `markHeldDrained()`, `runQueued(0)`: one line with `readyDrainedMs`.
  - `forcedRevealEmitsWithoutReady` (guard): `markRevealCause(FORCED, 0)`, `markFirstContent`, `markRevealed`, `runQueued(0)`: one line, `readyMs` null, the 5000 task not run.
  - `lineEmitsFiveSecondsAfterRevealWithoutReady`: `markFirstContent`, `markRevealed`, `runQueued(0)`: no line and `queued` holds one 5000 task; `runQueued(5_000)`: one line with `revealedMs`, `readyMs` null.
  - `gateMarksEmitOnlyThroughSchedule` replaces `newMarksNeverEmitTheLine` (`:142`): `markFirstContent`, `markRevealed`, `markReady(0)`, `markHeldDrained`, `markRevealCause(FORCED, 0)`, `markImagesIdle`, `onKeyDropped` with nothing run: no line; then `runQueued()`: one line.
  - `completionAndFiveSecondBoundEmitOneLine`: `markFirstContent`, `markRevealed`, `markReady(0)`: no line before the queue runs; `runQueued()` (completion and the 5000 task): one line.
  - Guard `oneLineWhenRevealedComesBeforeFirstContent` (`:44`) also calls `marks.markReady(0)` before `markFirstContent`; expected values unchanged. The other guards of the test design row stay as they are.
- Test: `core/telemetry/TelemetryRuntimeTest.kt` - `coldStartResumeBuildsStartupMarksAndSchedulesFallback` (`:243`, guard): marks built with `sink::schedule` (call shape only, still `listOf(60_000L)`). New `startThatNeverRevealsEmitsAtTheSixtySecondFallback` (guard): `prefs.values[key] = clock.wall`, start with marks on `sink::schedule`, `executor.runAll()`, `markFirstContent()`, `runScheduled()`, `runRepeating()`: one `startup` line without `revealed`. New `revealedStartWritesOneLineWhenBoundAndFallbackBothFire`: as above plus `markRevealed()`; `executor.scheduledDelays` contains 60000 and 5000; after `runScheduled()`, `runRepeating()` exactly one `startup` line. `ManualTelemetryExecutor` (`util`, `scheduledDelays`, `runScheduled`) as is.

**Consumes:** `RevealGate`, `StartupMarks`, `TelemetrySink.schedule` as at f48beb86.
**Produces:** `StartupMarks(procStartMs, appCreateMs, record, clock, schedule)`, `LINE_WAIT_AFTER_REVEAL_MS`.

- [x] Stub: add the `schedule` parameter and `LINE_WAIT_AFTER_REVEAL_MS`, both unused; fast gate green
- [x] Write `lineWaitsForReadyAfterACapReveal` and `heldDrained_reportedAfterACapReveal`, then the other new tests; run red: every `red` row fails on an assertion (line written at the reveal; no `drained`; no 5000 delay), the two guards pass; record the failures in the Red run
- [x] Implement the gate split, the rule, the bound and the wiring
- [x] Fast gate, `RevealGateTest`, `StartupMarksTest`, `TelemetryRuntimeTest`, `StartupImageActivityTest`, `PuberVMTelemetryTest`, `SectionVMTest`, `KeyProbeGateTest`; commit "Write the startup line once ready and its held images are in or five seconds after the reveal"

**Done when:** those classes pass and `git grep -n "sink::schedule" app/src/main` lists `TelemetryRuntime.kt`.

---

### Task 8: Record when Home asked for the reveal as ready_req

**Covers:** AC-1 (`ready_req`)
**Files:**
- Modify: `core/startup/RevealObserver.kt:15-27` - `fun onReadyRequested() {}` with a default body and KDoc ("`markReady` was called with the gate begun, before the frame wait; inside the gate lock"); `None` (`:28-33`) inherits it.
- Modify: `core/startup/RevealGate.kt:54-55` - `val markedIn = synchronized(lock) { if (begun) observer.onReadyRequested(); generation }`.
- Modify: `core/startup/StartupReveal.kt:18-34` - the observer overrides `onReadyRequested` with `Telemetry.startupMarks?.markReadyRequested()`.
- Modify: `core/telemetry/StartupMarks.kt` - `readyReqMs: AtomicLong(NOT_REACHED)`; `markReadyRequested()` keeps the first `clock.elapsedMs()`, does not schedule; `emit` passes `readyReqMs = readyReqMs.reached()`.
- Modify: `core/telemetry/TelemetryEvent.kt:131-147` - append `Startup.readyReqMs: Long? = null`.
- Modify: `core/telemetry/TelemetryEncoder.kt:67-80` (`putStartup`) - `event.readyReqMs?.let { put("ready_req", it - event.procStartMs) }`.
- Test: `core/startup/RecordingRevealObserver.kt` - `var readyRequested = 0`, incremented by `onReadyRequested`; not added to `log`, so existing log assertions hold.
- Test: `core/startup/RevealGateTest.kt` - `markReady_reportsReadyRequestedBeforeTheFrameWait`: gate with `afterFrame = { frames += it }` (as `:57-67`) and the recorder; `markReady()` before `begin()`: `readyRequested` 0; `begin()`, `markReady()`: `readyRequested` 1, `frames` not run, `log` empty.
- Test: `core/telemetry/StartupMarksTest.kt` - `readyRequestedIsCarriedOnTheLine` (clock 650, `markReadyRequested()`, `onTimeout()`: `readyReqMs` 650), `readyRequestedKeepsTheFirstValue` (650 then 800: 650); `gateMarksEmitOnlyThroughSchedule` also calls `markReadyRequested()`.
- Test: `core/telemetry/TelemetryEncoderTest.kt` - `startupWritesReadyRequested`: `Startup(2_000, 100, 150, 300, 900, 1_400, readyReqMs = 1_200)` writes `ready_req` 1100; the same line with `readyReqMs = null` has no `ready_req` key. Catalogue guard (`:75`, samples `:33`) unchanged.

**Consumes:** Task 7 `StartupMarks`.
**Produces:** `RevealObserver.onReadyRequested`, `StartupMarks.markReadyRequested`, `Startup.readyReqMs`, wire field `ready_req`.

- [x] Stub: interface default, `markReadyRequested()` no-op, `readyReqMs` field never set; fast gate green
- [x] Write the four tests; run them red (`readyRequested` 0, `readyReqMs` null, `ready_req` absent)
- [x] Implement
- [x] Fast gate, `RevealGateTest`, `StartupImageActivityTest`, `StartupMarksTest`, `TelemetryEncoderTest`; commit "Record when the landing screen asked for the reveal on the startup line"

**Done when:** those classes pass.

---

### Task 9: Carry the dispatch wait on every key_lat line

**Covers:** AC-2 (`wait_ms`), AC-9
**Files:**
- Modify: `core/telemetry/TelemetryEvent.kt:218-227` - append `KeyLat.waitMs: Long? = null`; KDoc: key event time to `dispatchKeyEvent`, both `CLOCK_MONOTONIC`.
- Modify: `core/telemetry/KeyProbe.kt:114-122` (`answer`) - `val waitMs = p.dispatchNs / NS_PER_MS - p.eventMs`, passed on the expired line (`:117`) and the latency line (`:121`).
- Modify: `core/telemetry/TelemetryEncoder.kt:121-126` (`putKeyLat`) - `event.waitMs?.let { put("wait_ms", it) }`.
- Test: `core/telemetry/KeyLatencyMatcherTest.kt` - `keyLatCarriesDispatchWait`: `arm(970, 1_000)`, `frame(1_010, 60)`: one line, `waitMs` 30, `latMs` 100. `expiredKeyLatCarriesDispatchWait`: `arm(970, 1_000)`, `frame(6_001, 10)`: one expired line, `waitMs` 30.
- Test: `core/telemetry/TelemetryEncoderTest.kt` - `keyLatWritesWaitMs`: a latency line and an expired line with `waitMs = 12` write `wait_ms` 12; with `waitMs = null` no `wait_ms` key. Catalogue sample `:61` unchanged.

**Consumes:** `KeyLatencyMatcher` as at f48beb86.
**Produces:** `KeyLat.waitMs`, wire field `wait_ms`.

- [x] Stub: the `waitMs` field, never set; fast gate green
- [x] Write the three tests; run them red (`waitMs` null, `wait_ms` absent)
- [x] Implement
- [x] Fast gate, `KeyLatencyMatcherTest`, `KeyLatencyMatcherConcurrencyTest`, `TelemetryEncoderTest`, `FrameProbeTest`; then the full gate; commit "Report the wait between a key's event time and its dispatch on every key latency line"

**Done when:** the full gate passes and `git diff --stat f48beb86 -- app/src/test/kotlin/com/kino/puber/core/startup/StartupSettledTest.kt app/src/test/kotlin/com/kino/puber/core/startup/StartupRevealTest.kt app/src/test/kotlin/com/kino/puber/core/startup/StartupKeyFilterTest.kt app/src/test/kotlin/com/kino/puber/core/startup/StartupImageActivityTest.kt app/src/androidTest/kotlin/com/kino/puber/ui/feature/home/component/HomeLaunchFocusSettledTest.kt` is empty.

---

### Task 10: Box re-measure of the startup marks and key wait (AC-5, AC-6)

**Covers:** AC-5, AC-6 (Manual checks 2, 3; check 4 NOT CHECKED)
**Owner:** qa-device on the Task 9 commit, same lease and install rules as Task 6.
**Files:**
- Create: `docs/superpowers/evidence/2026-11-22-startup-first-input-on-tv-remeasure-device.md`.

- [ ] Build `./tools/agentw :app:assembleDevBenchmarkRelease` in the worktree; lease the box, `adb connect 192.168.1.110:5555`, `make DEVICE=192.168.1.110:5555 auth-save`; install with `tools/android-apk-install-preserve`; record the APK and on-device `base.apk` sha256; confirm the session log is on
- [ ] Record the compiler filter: `adb -s 192.168.1.110:5555 shell dumpsys package com.kino.puber.stage`, Dexopt state
- [ ] At least 12 cold starts (`am force-stop`, `am start -W`), one `KEYCODE_DPAD_DOWN` per start at an AC-3 anchor (launch +3 s, reveal +300 ms, reveal +1000 ms, rotated as in Task 6); atrace on 3 starts. Wait at least 11 s after the visible reveal before the next force-stop: the line can be recorded up to 5 s after the reveal (Task 7) and the sink drains every 5 s (`TelemetrySink.kt:260`)
- [ ] Per start: `cause`, `first_content`, `ready_req`, `ready`, `ready_in_flight`, `ready_drained`, `revealed`, `ready - revealed`, `ready_req - first_content`, anchor, `lat_ms` or `expired`, `wait_ms`. Per anchor: median and p95 of `lat_ms` and `wait_ms`. For the 3 atrace starts: main-thread slices over 100 ms by name. Check exactly one `startup` line per start, `ready` on every start that lands on Home content, `0 <= wait_ms <= lat_ms` on every latency line and `wait_ms >= 0` on expired lines
- [ ] Manual check 4: record "NOT CHECKED: `cmd package compile -m speed-profile` not authorised (spec Decisions)"; do not run the compile
- [ ] Release the lease; `tools/mobile-evidence-audit.sh docs/superpowers/evidence com.kino.puber.stage`; commit "Add the box re-measure of the startup marks and key wait after the reveal"

**Done when:** the evidence file has at least 12 start rows with every field above, the per-anchor table, the 3 atrace slice lists and the NOT CHECKED line; the audit is clean; the box still runs `com.kino.puber.stage` logged in.

---

## Implementation record

<!-- owner: implementer -->

**Commits** (phase A, Tasks 1-5; Task 5 is the phase-A commit, the last one below, sha in the return block):

- 4ed47c5f Count keys dropped under the splash and keep them out of the key latency matcher
- be72f6cf Answer every pending key and report keys answered after five seconds as expired
- 4fd86f1c Record reveal cause, ready, image idle, held drain and dropped keys in the startup marks
- 93e215a2 Carry reveal cause, image marks, dropped keys and expired key latency on the telemetry wire
- ede5686b Record the plan readiness verdict
- 740d780f Add the plan and amend the spec for the grace reveal cause
- f4e982e8 Add the spec of the first-input-after-reveal startup work
- Task 5: Track startup image requests by identity in the reveal gate and report its reveal cause and image edges

**Red run** (P0 `red` tests on the unchanged code; the minimal stub where a symbol was missing):

- red: `TelemetryEncoderTest.startupWritesRevealCauseAndImageFields` - NoSuchElementException at `json.getValue("cause")` (field not written)
- red: `TelemetryEncoderTest.startupOmitsImageFieldsNotReached` / `keyLatExpiredOmitsLatMs` - key set assertions fail (`cause` absent; `lat_ms` written, `expired` absent)
- red: `StartupMarksTest.revealCauseAndImageMarksAreCarriedOnTheLine`, `revealCauseKeepsTheFirstValue`, `imagesIdleKeepsTheLastValue`, `droppedKeysAreCountedOnTheLine` - assertion failures against no-op stubs (null cause / 0 dropped)
- green on base: `StartupMarksTest.imageMarksAreAbsentWhenNotReached`, `newMarksNeverEmitTheLine` - a no-op stub already satisfies them; they only guard the real implementation
- red: `KeyLatencyMatcherTest.eachPendingKeyIsAnsweredByTheFirstFrameAfterIt`, `frameBetweenTwoPendingKeysAnswersOnlyTheEarlier`, `keyAnsweredBetweenOneAndFiveSecondsIsEmitted`, `keyUnansweredPastFiveSecondsIsEmittedExpiredWithoutLat`, `pendingListHoldsAtMostSixteenKeys`, `noFrameMeansNoLine` (one line instead of three) - assertion failures on the single-slot matcher
- red: `DispatchWithProbeTest.droppedKeyReturnsTrueWithoutCallingSuper` (super called) and `droppedKeyIsProbedAsDropped` (probe saw handled=false), against the signature-only stub
- red: `KeyProbeGateTest.droppedKeyDoesNotBlockTheNextHandledKey` - KeyProbeGateTest.kt:59 `assertEquals(1, lats.size)`, two KeyLat lines instead of one (dropped key armed, 301 and 201); `droppedFirstDownIsCountedOnceAndRepeatsAreNot` - `droppedKeys` 0 instead of 1
- red (stub: `RevealGate` observer parameter accepted and ignored, `onImageStarted/Finished` driving the old boolean): `RevealGateTest.revealByReady_reportsCauseReady`, `revealByCap_reportsCauseCapWithInFlightCount`, `revealNow_reportsCauseForced`, `markReady_reportsInFlightCountWhenReadyTakesEffect`, `imagesIdleEdge_reportedOnlyBeforeTheReveal`, `heldDrained_reportedWhenRequestsInFlightAtReadyAllEnd` (empty observer log), `sameRequestFinishedTwice_doesNotUncoverOthers` (RevealGateTest.kt:156 `assertTrue(covered)`)
- red: `StartupImageActivityTest.inFlightCountAtRevealCountsDistinctRequests` - empty observer log (the reported-count contract); `errorAndCancelEndARequest` also failed on the boolean stub, a guard that the identity set restores

**Deviations from the plan:**

- Task 5 commit also carries two line-length fixes (`MainActivity.kt`, `KeyProbeGateTest.kt`) that detekt reported at the full gate, belonging to Task 4 code.
- `KeyProbeOffTest.offDispatchNeverTouchesTheMachineOrMatcher` (Task 4, listed as call shape only): its control now expects 11 `KeyLat` lines, one per armed press, because Task 3's pending list answers every key; the off-path assertion is unchanged.
- `RevealGate`: `forced` became `forcedCause: RevealCause?` (first of `onCap` / `revealNow` wins, which is what the cause reports); `onReady` is reported whenever ready first takes effect after `begin`, also after the reveal.

**Files touched outside the plan:** `app/src/test/kotlin/com/kino/puber/core/startup/RecordingRevealObserver.kt` (shared test double of both gate test classes).

**For the reviewers:**

- `RevealGate.update` reports `onRevealed` once, at the first uncover edge; `onImagesIdle` / `onHeldDrained` fire from `onImageFinished` only while `begun && !revealedOnce`, before the reveal edge they may cause.
- Covering rule is unchanged (`ready && inFlight.isEmpty()`); `held` is only read for `onHeldDrained` in phase A.
- `dispatchWithProbe` now never calls super for a dropped key, with the probe on or off; `MainActivity` evaluates `CoveredKeyFilter.shouldDrop` once before it.

**Round 3 (Tasks 7-9)** - commits: Task 7 `1f33a6e1`, Task 8 `ada007cf`, Task 9 below. The first full-gate run failed in `HomeVMIncrementalPublishTest` (`UncaughtExceptionsBeforeTest`, 2 failures, a flake of the known unit-suite-flaky kind); the rerun was green.

Red run (against signature-only stubs: `schedule` parameter and `LINE_WAIT_AFTER_REVEAL_MS` unused; `markReadyRequested` no-op, `readyReqMs` never set; `waitMs` never set):

- red: `RevealGateTest.heldDrained_reportedAfterACapReveal` - RevealGateTest.kt:251 assertEquals, no `drained` after the cap reveal
- red: `StartupMarksTest.lineWaitsForReadyAfterACapReveal` (:177), `lineWaitsForHeldDrainWhenReadyHeldRequests` (:195), `lineEmitsFiveSecondsAfterRevealWithoutReady` (:220), `gateMarksEmitOnlyThroughSchedule` (:165), `completionAndFiveSecondBoundEmitOneLine` (:235) - line written at the reveal, no 5000 task queued
- red: `TelemetryRuntimeTest.revealedStartWritesOneLineWhenBoundAndFallbackBothFire` - TelemetryRuntimeTest.kt:278, no 5000 delay scheduled
- green on base (guards): `TelemetryRuntimeTest.startThatNeverRevealsEmitsAtTheSixtySecondFallback`; `StartupMarksTest.forcedRevealEmitsWithoutReady` (line already emitted at the reveal; its first version asserted the queued 5000 task and failed on the stub, now asserts one line after running it)
- red: `RevealGateTest.markReady_reportsReadyRequestedBeforeTheFrameWait` (readyRequested 0 instead of 1), `StartupMarksTest.readyRequestedIsCarriedOnTheLine` (:161), `readyRequestedKeepsTheFirstValue` (:171), `TelemetryEncoderTest.startupWritesReadyRequested` (`ready_req` absent)
- red: `KeyLatencyMatcherTest.keyLatCarriesDispatchWait` (:172), `expiredKeyLatCarriesDispatchWait` (:182), `TelemetryEncoderTest.keyLatWritesWaitMs` (`wait_ms` absent)

Deviations (Tasks 7-9):

- `onImageFinished` reports `onHeldDrained` after the reveal and `onImagesIdle` only before it, as planned; `emitWhenComplete` also requires `readyInFlight` reached, so a ready mark half-written by a racing thread counts as not complete.
- `TelemetryRuntimeTest` new tests read `marks` through a local `started` to avoid a redundant `!!`.

**Blocked:** none.

### Fix round 1

Triage rows 2-5 (row 1 is qa's, row 6 rejected). Frozen classes untouched (AC-9).

| Row | Fix | Commit |
|---|---|---|
| 2 | `RevealGate.update` reports a forced reveal once when the gate never began; red: `RevealGateTest.revealNowBeforeBegin_reportsForcedOnceWithoutCovering` - expected [revealed:FORCED:0], was empty | 9eec4d24 and the follow-up that splits the condition for detekt |
| 3 | `Startup.droppedKeys` is `Int = 0`, always encoded; encoder tests expect `dropped_keys` | 9fe426e0 |
| 4 | New `StartupImageActivityUnmeasuredTest` (guard, green on base by design) | 874594d5 |
| 5 | `KeyLatencyMatcherConcurrencyTest` keys `waitMs == i`, fails on a duplicate emit | 874594d5 |

## Readiness

Verdict: **READY**

Base c506d0bd; phase A f48beb86; spec and plan at f368c8b4 (worktree clean); test design round 2 (`.flow/runs/startup-first-input-on-tv/test-design.md`).

### Readiness-3 fix list

| # | Status | Evidence |
|---|---|---|
| 1 | Resolved | Spec `## Test design` at `:159` (Risk profile `:165`, Traceability `:184`, Bug `:250`, Manual checks `:254`, Perf `:265`). `diff` against test-design.md differs only in heading levels and one added line linking the baseline (5fb5de17). Plan Global constraints and Task 10 references now resolve |
| 2 | Resolved | AC-9 in spec `:40` and brief `:43` states "unchanged since `f48beb86`"; spec AC-9 row `:246`, plan `:16` and Task 9 Done when use the same reference |

### Regression checks

| Check | Result |
|---|---|
| Code unchanged since round 3 | `git diff --stat f48beb86 HEAD -- app` empty; f368c8b4 touches docs only |
| Citations re-sampled at HEAD | `StartupMarks.kt:9-18` (constructor), `RevealGate.kt:54-55` (`markedIn`), `KeyProbe.kt:114-122` (`answer`), `TelemetryRuntime.kt:163` (`StartupMarks(...)`), `TelemetrySink.kt:128-130` (`schedule`), `StartupMarksTest.kt:44`, `:142` resolve |
| Round-2 rows to plan tasks | Every new test name of spec Round 2 appears in Tasks 7-9; the AC-1 P1 guard names not in the plan (`lineWaitsForRevealedAndCarriesBothMarks`, `fallbackLineHasNoRevealedWhenItNeverCame`, `fallbackLineAfterSixtySecondsWithMarksSoFar`, `revealedKeepsTheFirstValue`, `appComposedKeepsTheFirstValue`) exist in `StartupMarksTest.kt` and are covered by Task 7 "other guards stay as they are" |
| Build order, P0 rows, scope | Unchanged from readiness-3 Checks; still pass |

### Note

- Spec AC delta row `:88` still lists the frozen classes as "unchanged" without the `f48beb86` reference; AC-9 `:40` governs.
