# Test design: startup-first-input-on-tv

Round 2, after the gate-1 delta. Base c506d0bd; phase A f48beb86; box baseline 5fb5de17. ACs: `brief.md#acceptance-criteria` (amended). Design: `design-architect.md` round 2 (Decisions, Changes by layer). Unit tests are JUnit 5 under `app/src/test/kotlin/com/kino/puber/core/`; device rows run through qa-device on the box `192.168.1.110:5555`, `devBenchmarkRelease` (`com.kino.puber.stage`), never the emulator.

Fakes to reuse: `FakeTelemetryClock`, `RecordingRevealObserver`, the `now` clock and `afterFrame` list in `RevealGateTest`, `arm`/`frame` in `KeyLatencyMatcherTest`, `ManualTelemetryExecutor` (`scheduledDelays`, `runScheduled`) with a real `TelemetrySink` in `TelemetryRuntimeTest`. New in `StartupMarksTest`: a recording `schedule` (list of `delayMs` to task, run by the test) next to an immediate one (`{ _, task -> task() }`).

## Risk profile

| # | Risk | P | I | Score | Covered by |
|---|---|---|---|---|---|
| R1 | Phase A or the round-2 `onHeldDrained` change alters when the splash lifts | 2 | 3 | 6 | AC-1 guard rows on `RevealGateTest`, `StartupImageActivityTest` |
| R2 | Reveal cause misreported (`ready`/`cap`/`forced`); AC-5 reads it | 2 | 3 | 6 | AC-1 cause rows |
| R3 | Dropped-key handling changes the `dispatchKeyEvent` return value or still arms the matcher | 2 | 3 | 6 | AC-2 rows on `DispatchWithProbeTest`, `KeyProbeGateTest` |
| R4 | `schedule` or `emit` runs inside the gate lock (blocks, re-enters) | 2 | 3 | 6 | `gateMarksEmitOnlyThroughSchedule` |
| R5 | `am force-stop` before the delayed line is written loses the start | 2 | 3 | 6 | `lineEmitsFiveSecondsAfterRevealWithoutReady` (bound is 5000 ms); Manual check 2 (wait, one line per start) |
| R6 | Line still emitted at the reveal, so `ready` stays absent after a cap reveal (the round-2 finding) | 3 | 2 | 6 | `lineWaitsForReadyAfterACapReveal`, `heldDrained_reportedAfterACapReveal` |
| R7 | Production wiring missing: `StartupReveal` does not forward `onReadyRequested`, or `TelemetryRuntime.kt:163` does not pass `sink::schedule` (both out of unit reach: `StartupRevealTest` frozen by AC-9, factory uses `Process`) | 2 | 2 | 4 | Manual check 2: `ready_req` and post-reveal `ready` present |
| R8 | Pending-list matcher double-emits or answers the wrong key across threads | 2 | 2 | 4 | AC-2 matcher rows, concurrency guard |
| R9 | Marks mix clock bases | 2 | 2 | 4 | `revealCauseAndImageMarksAreCarriedOnTheLine`, `readyRequestedIsCarriedOnTheLine` |
| R10 | Completion and the 5 s bound (or the 60 s fallback) both emit | 1 | 3 | 3 | `completionAndFiveSecondBoundEmitOneLine`, `revealedStartWritesOneLineWhenBoundAndFallbackBothFire` |
| R11 | Box login lost on install | 1 | 3 | 3 | qa-device procedure: lease, `auth-save`, install-preserve |
| R12 | `wait_ms` mixes clocks | 1 | 2 | 2 | Accepted: both `CLOCK_MONOTONIC`; Manual check 3 asserts `0 <= wait_ms <= lat_ms` |
| R13 | Compile state from one set carries into the next | 1 | 2 | 2 | Accepted: filter recorded before every set (Manual check 3) |
| R14 | Encoder change breaks existing `startup`/`key_lat` fields | 1 | 2 | 2 | Accepted: existing encoder guards (AC-9) |

## Traceability

`red` in the phase-A table: failed on c506d0bd, green since f48beb86. `red` in the round-2 table: fails on f48beb86 (behaviour or API absent) and is run there first. `guard`: expected values stay; call shape may change (the `schedule` constructor parameter).

### Phase A (written, names kept)

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 red | `RevealGateTest.revealByReady_reportsCauseReady` | fails if the reveal edge reports no cause or a wrong one when `ready` lifts it |
| AC-1 | unit | P0 red | `RevealGateTest.revealByCap_reportsCauseCapWithInFlightCount` | fails if `onCap` is reported as `ready` or the in-flight count at reveal is wrong |
| AC-1 | unit | P0 red | `RevealGateTest.revealNow_reportsCauseForced` | fails if `revealNow` is not reported as `forced` |
| AC-1 | unit | P0 red | `RevealGateTest.markReady_reportsInFlightCountWhenReadyTakesEffect` | fails if the count is taken at `markReady` instead of after the frame |
| AC-1 | unit | P0 red | `RevealGateTest.imagesIdleEdge_reportedOnlyBeforeTheReveal` | fails if the round-2 change lets post-reveal idle edges reach the observer |
| AC-1 | unit | P1 red | `RevealGateTest.heldDrained_reportedWhenRequestsInFlightAtReadyAllEnd` | fails if `onHeldDrained` fires while a held request is in flight, or a later request delays it |
| AC-1 | unit | P0 red | `RevealGateTest.sameRequestFinishedTwice_doesNotUncoverOthers` | fails if the identity set becomes a counter |
| AC-1 | unit | P0 guard | `RevealGateTest.readyWaitsForImagesInFlight`, `imagesSettlingAfterReadyUncovers`, `capUncoversWhileImagesInFlight`, `revealNowIgnoresContentAndImages`, `imagesBusyAfterRevealDoNotCoverAgain`, `beginKeepsImagesBusyFromBeforeIt`, `remainingCapMs_countsFromBegin` | fails if the covering rule or the 2.5 s cap changes (R1) |
| AC-1 | unit | P0 guard | `StartupImageActivityTest.requestIsInFlightFromResolvedSizeUntilSuccess`, `errorAndCancelEndARequest`, `cancelWithoutResolvedSizeLeavesIdle` | fails if forwarding misses an end callback or treats `onStart` as in flight |
| AC-1 | unit | P1 red | `StartupImageActivityTest.inFlightCountAtRevealCountsDistinctRequests` | fails if repeated callbacks for one request change the count |
| AC-1 | unit | P0 red | `StartupMarksTest.revealCauseAndImageMarksAreCarriedOnTheLine` | fails if `cause`, `readyMs`, `imagesIdleMs`, `inFlight`, `readyInFlight`, `readyDrainedMs` are missing or off the marks clock |
| AC-1 | unit | P1 red | `StartupMarksTest.revealCauseKeepsTheFirstValue`, `imagesIdleKeepsTheLastValue`, `imageMarksAreAbsentWhenNotReached` | fails if first/last-wins or null-when-unreached breaks |
| AC-1 | unit | P0 red | `TelemetryEncoderTest.startupWritesRevealCauseAndImageFields` | fails if wire names `cause`, `ready`, `img_idle`, `in_flight`, `ready_in_flight`, `ready_drained` are absent or not relative to process start |
| AC-1 | unit | P1 red | `TelemetryEncoderTest.startupOmitsImageFieldsNotReached` | fails if null fields are written |
| AC-1 | unit | P1 guard | `TelemetryEncoderTest.startupWritesRevealed`, `startupOmitsMarksNotReached`, `everyEventTypeEmitsExactlyEnvelopePlusCatalogueFields` | fails if existing fields change (R14) |
| AC-2 | unit | P0 red | `KeyLatencyMatcherTest.eachPendingKeyIsAnsweredByTheFirstFrameAfterIt`, `frameBetweenTwoPendingKeysAnswersOnlyTheEarlier` | fails if a second pending key is not armed or a frame answers a key dispatched after its start |
| AC-2 | unit | P0 red | `KeyLatencyMatcherTest.keyAnsweredBetweenOneAndFiveSecondsIsEmitted` | fails if expiry returns to 1 s |
| AC-2 | unit | P0 red | `KeyLatencyMatcherTest.keyUnansweredPastFiveSecondsIsEmittedExpiredWithoutLat` | fails if an expired key is dropped silently or carries `latMs` |
| AC-2 | unit | P2 red | `KeyLatencyMatcherTest.pendingListHoldsAtMostSixteenKeys` | fails if the list grows without bound |
| AC-2 | unit | P0 guard | `KeyLatencyMatcherTest.fiftyIsNotSlowAndFiftyOneIs`, `answeredEntryIsConsumedAndNotEmittedTwice`, `unhandledKeyNeverArms`, `frameStartingBeforeDispatchDoesNotAnswer` | fails if the 50 ms floor or single consumption breaks |
| AC-2 | unit | P0 guard | `KeyLatencyMatcherConcurrencyTest.armOnOneThreadAnswerOnAnotherNeverDoubleEmits` | fails if the pending list double-emits across threads (R8) |
| AC-2 | unit | P0 red | `DispatchWithProbeTest.droppedKeyReturnsTrueWithoutCallingSuper`, `droppedKeyIsProbedAsDropped` | fails if a dropped key reaches super, returns false, or is probed as handled (R3) |
| AC-2 | unit | P0 guard | `DispatchWithProbeTest.offCallsSuperOnceAndNeverTheProbe`, `onReturnsSupersValueAndProbesAfterSuper`, `probeThatThrowsCannotChangeTheReturnValueOrBreakDispatch` | fails if the return value of a non-dropped key changes |
| AC-2 | unit | P0 red | `KeyProbeGateTest.droppedKeyDoesNotBlockTheNextHandledKey` | fails if a dropped key still arms (two lines 301 and 201 instead of one 201) |
| AC-2 | unit | P0 red | `KeyProbeGateTest.droppedFirstDownIsCountedOnceAndRepeatsAreNot` | fails if `dropped_keys` counts repeats or UP, or misses the first DOWN |
| AC-2 | unit | P1 red | `StartupMarksTest.droppedKeysAreCountedOnTheLine` | fails if `droppedKeys` is not carried to the line |
| AC-2 | unit | P1 red | `TelemetryEncoderTest.keyLatExpiredOmitsLatMs` | fails if an expired line writes `lat_ms` or omits `expired` |
| AC-3 | device | P0 | Manual check 1 (done, 5fb5de17) | baseline per start: cause, ready, revealed, per-anchor first-key `key_lat`, dropped keys; 8428c946 launch-to-content set |

### Round 2

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 red | `RevealGateTest.markReady_reportsReadyRequestedBeforeTheFrameWait` | observer gets `onReadyRequested` with `afterFrame` not yet run, and none when the gate is not begun; fails if reported after the frame, before `begin`, or not at all |
| AC-1 | unit | P0 red | `RevealGateTest.heldDrained_reportedAfterACapReveal` | begin, one request in flight, `onCap`, `markReady` + frame, request ends: `onHeldDrained` reported; fails if the `!revealedOnce` condition stays (`RevealGate.kt:81-84`) |
| AC-1 | unit | P0 red | `StartupMarksTest.lineWaitsForReadyAfterACapReveal` | first content, reveal with cause `cap`: no line; `markReady(0)` then queued tasks run: one line with `readyMs`; fails if the line is still written at the reveal (R6) |
| AC-1 | unit | P0 red | `StartupMarksTest.lineWaitsForHeldDrainWhenReadyHeldRequests` | `markReady(2)` after the reveal: no line until `markHeldDrained`; line carries `readyDrainedMs`; fails if `ready` alone completes a line with held requests |
| AC-1 | unit | P0 guard | `StartupMarksTest.forcedRevealEmitsWithoutReady` | cause `forced` with first content and reveal: line at once, `readyMs` null; fails if restored/update-prompt starts wait for `ready` or the bound |
| AC-1 | unit | P0 red | `StartupMarksTest.lineEmitsFiveSecondsAfterRevealWithoutReady` | `markRevealed` schedules a 5000 ms task; no line before it runs, one line after with `revealedMs` and null `readyMs`; fails if the bound is missing or not 5000 ms (R5) |
| AC-1 | unit | P0 red | `StartupMarksTest.readyRequestedIsCarriedOnTheLine` | `readyReqMs` stamped by `markReadyRequested` on the marks clock; fails if absent or taken at `markReady` |
| AC-1 | unit | P1 red | `StartupMarksTest.readyRequestedKeepsTheFirstValue` | fails if a later request overwrites the first |
| AC-1 | unit | P0 red | `StartupMarksTest.gateMarksEmitOnlyThroughSchedule` (replaces `newMarksNeverEmitTheLine`) | recording `schedule` not run: `markReady`, `markReadyRequested`, `markHeldDrained`, `markRevealCause`, `markImagesIdle`, `onKeyDropped` record nothing, even with the line complete; fails if any gate-side mark calls `record` on the caller's thread (R4) |
| AC-1 | unit | P1 red | `StartupMarksTest.completionAndFiveSecondBoundEmitOneLine` | completion task and the 5000 ms task both run: one line; fails if the single-shot guard is lost (R10) |
| AC-1 | unit | P1 guard | `StartupMarksTest.oneLineWithAscendingMarksOnFirstContent`, `lineWaitsForRevealedAndCarriesBothMarks`, `oneLineWhenRevealedComesBeforeFirstContent` (now also mark `ready`), `fallbackLineHasNoRevealedWhenItNeverCame`, `fallbackLineAfterSixtySecondsWithMarksSoFar`, `revealedKeepsTheFirstValue`, `appComposedKeepsTheFirstValue` | fails if marks or the fallback line change beyond the emission rule |
| AC-1 | unit | P0 red | `TelemetryEncoderTest.startupWritesReadyRequested` | fails if `ready_req` is absent, not relative to process start, or written when null |
| AC-1 | unit | P0 guard | `TelemetryRuntimeTest.startThatNeverRevealsEmitsAtTheSixtySecondFallback` | marks built on `sink::schedule`, first content only, scheduled tasks run: one `startup` line without `revealed`; fails if the 60 s fallback is dropped or the new rule blocks it |
| AC-1 | unit | P1 guard | `TelemetryRuntimeTest.coldStartResumeBuildsStartupMarksAndSchedulesFallback` | fails if `start` schedules anything but the 60 s fallback |
| AC-1 | unit | P1 red | `TelemetryRuntimeTest.revealedStartWritesOneLineWhenBoundAndFallbackBothFire` | marks on `sink::schedule`, first content and reveal: `scheduledDelays` holds 60000 and 5000, `runScheduled` writes exactly one `startup` line; fails if the bound does not go through the sink or two lines are written (R10) |
| AC-2 | unit | P0 red | `KeyLatencyMatcherTest.keyLatCarriesDispatchWait` | `arm(970, 1_000)`, answering frame: `waitMs` 30; fails if `waitMs` is absent or uses frame time |
| AC-2 | unit | P0 red | `KeyLatencyMatcherTest.expiredKeyLatCarriesDispatchWait` | expired line carries `waitMs`; fails if only latency lines set it |
| AC-2 | unit | P0 red | `TelemetryEncoderTest.keyLatWritesWaitMs` | fails if `wait_ms` is absent on latency or expired lines, or written when null |
| AC-5 | device | P0 | Manual check 2 | per-start `ready_req`, `ready`, `ready_drained` after a cap reveal reach the wire (R6, R7); one line per start (R5) |
| AC-6 | device | P0 | Manual check 3 | as-installed compiler filter, `lat_ms`/`wait_ms` per start, per-anchor median and p95, atrace slices; `0 <= wait_ms <= lat_ms` (R12) |
| AC-6 | device | P2 conditional | Manual check 4 | compiled-state half; NOT CHECKED unless the owner authorises the compile command (`state.md` Decisions) |
| AC-9 | unit | P0 guard | `check.full`: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`; `git -C <wt> diff f48beb86 -- ` on `StartupSettledTest`, `StartupRevealTest`, `StartupKeyFilterTest`, `HomeLaunchFocusSettledTest`, `StartupImageActivityTest` empty | fails if a frozen test changes or the suite, detekt or lint break |

Failing-first order: `StartupMarksTest.lineWaitsForReadyAfterACapReveal` and `RevealGateTest.heldDrained_reportedAfterACapReveal` first, then the other round-2 red rows, each run on f48beb86 before the code.

## Bug

`KeyProbeGateTest.droppedKeyDoesNotBlockTheNextHandledKey` (phase A, written first, green since 4ed47c5f): on c506d0bd a dropped key arrives as `handled = true` (`MainActivity.kt:59-71`, `KeyProbe.kt:81-86`) and arms the matcher.

## Manual checks (qa-device, box `192.168.1.110:5555`)

Lease, `auth-save` before the first install, `tools/android-apk-install-preserve` of the worktree's `devBenchmarkRelease` APK, `adb -s 192.168.1.110:5555` only, `com.kino.puber.stage` only. Checks 2 and 3 use the same set of starts. Evidence in `docs/superpowers/evidence/`, audited with `tools/mobile-evidence-audit.sh`.

| # | AC | Steps | Expected | Must not happen |
|---|---|---|---|---|
| 1 | AC-3 | Done: `docs/superpowers/evidence/2026-11-22-startup-first-input-on-tv-baseline-device.md` (5fb5de17) | Recorded | N/A |
| 2 | AC-5 | 1. Install the round-2 build. 2. Record the compiler filter (`adb -s 192.168.1.110:5555 shell dumpsys package com.kino.puber.stage`, Dexopt state). 3. 12+ cold starts: `am force-stop`, `am start -W`, wait at least 6 s after the visible reveal before the next force-stop. 4. Pull the `startup` lines | Exactly one `startup` line per start with `cause`, `first_content`, `ready_req`, `ready`, `ready_in_flight`, `ready_drained` (when `ready_in_flight` > 0), `revealed`; `ready` present in every start that lands on Home content, including `cap` starts; per start `ready - revealed` and `ready_req - first_content` tabled | A start with no line or two lines; `ready` absent on a Home start; splash stuck past the cap; crash; login lost; `com.kino.puber` touched |
| 3 | AC-6 | Same starts as check 2, no install in between: per start one `input keyevent KEYCODE_DPAD_DOWN` at an AC-3 anchor (launch +3 s, reveal +300 ms, reveal +1000 ms); atrace on 3 starts | Per start `lat_ms` and `wait_ms`; per-anchor median and p95; main-thread slices over 100 ms by name for the 3 atrace starts; `0 <= wait_ms <= lat_ms` on every latency line and `wait_ms >= 0` on `expired` lines | A `key_lat` line without `wait_ms`; `wait_ms > lat_ms`; focus lost or jumping two rows |
| 4 | AC-6 conditional | Only after the owner authorises `cmd package compile -m speed-profile -f com.kino.puber.stage`, run by the conductor: record the filter, repeat check 3 | Same set in the compiled state, filter `speed-profile` recorded | qa runs the compile itself; the half reported as met without authorisation (record NOT CHECKED instead) |

## Perf

| Metric | Source | Baseline | Pass threshold |
|---|---|---|---|
| `ready - revealed`, `ready_req - first_content` per start | `startup` line | N/A in 5fb5de17 (`ready` never written); first values come from Manual check 2 | None (AC-5 observation); set complete: 12+ starts, one line each |
| First-key `lat_ms` and `wait_ms` median, p95 per anchor | `key_lat` lines | `lat_ms` from 5fb5de17 (median about 3.2 s at all anchors); `wait_ms` absent there | None (AC-6 observation); `0 <= wait_ms <= lat_ms` on every line |
| Compiler filter per set | `dumpsys package`, Dexopt state | Not recorded in 5fb5de17 | Recorded before every set |

Same box, same build type, at least 12 cold starts.

UNTESTABLE: none
