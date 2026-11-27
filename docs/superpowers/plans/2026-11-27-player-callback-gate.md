# Queued stream failure before switchStream - plan

**Spec:** `docs/superpowers/specs/2026-11-27-player-callback-gate.md`
**Goal:** Callbacks of `PlaybackController` carry the URL of the session that produced them, and an error left over from the previous stream is dropped and answered with one re-prepare, so stream B loads.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`; full gate: `./tools/agentw testDevDebugUnitTest :player-test-fixtures:testDebugUnitTest :app:detektAll :app:lintDevDebug :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*' :app:detektInstrumentationDebug :app:lintInstrumentationDebug && bash tools/tests/test-baseline-profile-tooling.sh`.
- All commands run in `/home/dev/orca/workspaces/Puber/player-callback-gate`; never `./gradlew` there. Prefix Gradle with `AGENT_GRADLE_SERIAL=1` when another worktree may build.
- One task = green fast gate + green tests of the touched code + one commit, message one imperative English sentence, no prefixes, never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- `PC` = `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt`; `STALE` = `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerStaleCallbackTest.kt`. Line numbers are at base `cec5f7fc`.
- Out of scope: `player.stop()` in `switchStream` (AC-5); upstream Robolectric tests; changes to `PlayerVM.kt`; changes to the body of any `STALE` test.
- Device: instrumented runs only on the emulator `host.docker.internal:5555` under a lease (`tools/emulator-resource-lock.sh acquire|release <resource> ...`; `ssh mac-emu start` then `adb connect host.docker.internal:5555`). Command, called `ITEST` below: `AGENT_GRADLE_SERIAL=1 make FLAVOR=instrumentation DEVICE=host.docker.internal:5555 GRADLE=./tools/agentw itest TESTS=com.kino.puber.ui.feature.player.vm.PlaybackControllerStaleCallbackTest`. `make itest` can exit 0 on an incomplete run: read the case count in the JUnit XML under `app/build/outputs/androidTest-results/connected/`.

---

## Tasks

### Task 1: Add PlaybackCallbackGate with its JUnit 5 test

**Covers:** AC-2, AC-3
**Files:**
- Create: `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackCallbackGate.kt`
- Test: `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackCallbackGateTest.kt` (plain `internal class`, JUnit 5 `org.junit.jupiter.api`, style of `SeekStallRecoveryPolicyTest.kt:1-8`; no ExoPlayer, no dispatcher extension)

**Consumes:** nothing.
**Produces:** (main thread only, no locking)

```kotlin
internal class PlaybackCallbackGate {
    class Session(val generation: Long, val streamUrl: String, val fromSwitch: Boolean) {
        var streamLoadStarted = false; var staleErrorRetried = false  // per session, not on the gate
    }
    fun beginSession(streamUrl: String, fromSwitch: Boolean): Session // bumps generation, becomes current
    fun invalidate()                                                  // bumps generation, current = null
    fun dispatch(session: Session, action: (streamUrl: String) -> Unit) // runs only if session is current
    fun onLoadStarted(requestedUri: String)  // current?.streamLoadStarted = true only when uri == streamUrl
    fun routeError(session: Session, reprepare: () -> Unit, report: (streamUrl: String) -> Unit)
}
```

`routeError`: session not current -> nothing; `fromSwitch && !streamLoadStarted && !staleErrorRetried` -> set `staleErrorRetried`, call `reprepare()`; otherwise `report(session.streamUrl)`. Flag setters may be `internal set`/private if the tests drive them only through `onLoadStarted`/`routeError`.

Tests (names from `test-design.md`, all in `PlaybackCallbackGateTest`):

| Test | AC |
|---|---|
| `dispatch_runsActionWithSessionUrl_whenSessionIsCurrent` | AC-2 |
| `dispatch_skipsAction_whenNewerSessionBegan` | AC-2 |
| `dispatch_skipsAction_afterInvalidate` | AC-2 |
| `routeError_reportsNothingAndRepreparesOnce_whenSwitchSessionHasNotLoadedItsUrl` | AC-3 |
| `routeError_reportsSecondErrorWithSessionUrl_afterStaleRetry` | AC-3 |
| `routeError_reportsNothingAndReprepares_whenLoadStartOfPreviousUrlArrives` (`onLoadStarted(A)` after `beginSession(B, fromSwitch = true)`) | AC-3 |
| `routeError_reportsWithSessionUrl_whenSwitchSessionLoadedItsUrl` (`onLoadStarted(B)` first) | AC-3 |
| `routeError_reportsFirstError_whenSessionComesFromPrepare` | AC-3 |
| `routeError_doesNothing_whenSessionIsNotCurrent` (older session, and after `invalidate`) | AC-3 |
| `routeError_allowsOneRetryPerSession_whenAnotherSwitchBegins` | AC-3 |

- [x] Write `PlaybackCallbackGateTest` first; record the red run (compile failure: class missing) in the Implementation record
- [x] Write `PlaybackCallbackGate` with KDoc stating why it exists (stale callbacks after `switchStream`, one re-prepare per switch session)
- [x] Run the fast gate and `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.ui.feature.player.vm.PlaybackCallbackGateTest'`; commit

**Done when:** the test command above passes with every row of the table executed, and the fast gate is green.

---

### Task 2: Route PlaybackController callbacks through per-session listeners and un-ignore the device test

**Covers:** AC-1, AC-4, AC-5
**Files:**
- Modify: `PC` (symbols below)
- Modify: `STALE` - remove `@Ignore` (`STALE:84`) and its import (`STALE:12`); reword KDoc `STALE:19-23` from "the finding goes to the `player-callback-gate` run" to a regression statement (the case guards the fix). Test bodies unchanged.

**Consumes:** `PlaybackCallbackGate` from Task 1. Existing: `callback` `PC:169`, `currentStreamUrl` `PC:175` (stays for `SeekStallRecovery` `PC:187` and debug `PC:790`), `playerListener` `PC:214-271`, `analyticsListener.onLoadStarted` `PC:274-281`, `recoverBehindLiveWindow` `PC:323`, `streamRecovery` `PC:334`, `prepare` `PC:378` (builder `apply` `PC:427-430`, `setMediaItem` `PC:435`), "Deliberately no `stop()`" KDoc `PC:473-483` (attached to `createMediaSourceFactory` `PC:484`), `switchStream` `PC:499-519` (`setMediaItem` `PC:506`), `release` `PC:581-598` (`removeListener` `PC:586`), `disableAc3AndRetry` `PC:600`, `notifyPlaybackState` `PC:819`, `notifyTracksUpdated` `PC:834`. `MediaItem` URI is `stream.url` verbatim (`PlaybackMediaItemFactory.kt:42,60`).
**Produces:** no public API change; `PlaybackControl` untouched.

Changes in `PC`:

| Where | Change |
|---|---|
| fields near `PC:175` | `private val gate = PlaybackCallbackGate()`, `private var sessionListener: SessionListener? = null` |
| `PC:214-271` | `playerListener` becomes `private inner class SessionListener(val session: PlaybackCallbackGate.Session) : Player.Listener`. Each override body runs inside `gate.dispatch(session) { url -> ... }`; READY calls `callback?.onStreamReady(url)` (was `currentStreamUrl` at `PC:241`); ENDED as at `PC:232-236` inside dispatch. `onPlayerError` computes `cause`/`message`/`recovery` as now, then `gate.routeError(session, reprepare = { exoPlayer?.prepare() }, report = { url -> <the existing when of PC:257-269, streamUrl = url> })`, so the stale check precedes the BehindLiveWindow and AC3 branches |
| new private `bindSession(player: ExoPlayer, streamUrl: String, fromSwitch: Boolean)` | `sessionListener?.let(player::removeListener)`; `val listener = SessionListener(gate.beginSession(streamUrl, fromSwitch))`; `player.addListener(listener)`; `sessionListener = listener` |
| `PC:274-281` | first line: `gate.onLoadStarted(loadEventInfo.dataSpec.uri.toString())` (requested URI, before redirects) |
| `PC:427-430` | drop `addListener(playerListener)`; keep `addAnalyticsListener` |
| `PC:434-435` | `bindSession(player, stream.url, fromSwitch = false)` before `player.setMediaItem` |
| `PC:499-519` | `bindSession(player, stream.url, fromSwitch = true)` before `player.setMediaItem(..., savedPosition)`; no `player.stop()` (AC-5) |
| `PC:473-483` | move the KDoc onto `switchStream` and add one sentence: an error of the previous stream that surfaces after the switch is dropped and answered with one `prepare()`, which loads this stream at the saved position |
| `PC:584-588` | `sessionListener?.let(player::removeListener)` replaces `PC:586`; after the `let`: `gate.invalidate()`, `sessionListener = null` |

- [x] Red run (before any `PC` edit): remove only `@Ignore` and its import, take the emulator lease, run `ITEST` once, record the failing assertion and XML case count in the Implementation record (expected: `StreamFailure(NEXT_URL)` with B's URL or "stream B is ready" timeout); release the lease
- [x] Apply the `PC` changes and the `STALE` KDoc rewording
- [x] `grep -n "currentStreamUrl" PC` shows only the field, its writes, `PC:187` and `PC:790` readers; `grep -n "stop()" PC` shows no hit inside `switchStream`
- [x] Run the fast gate, `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.ui.feature.player.vm.*'` and `./tools/agentw :app:compileInstrumentationDebugAndroidTestKotlin`
- [x] Under the emulator lease run `ITEST` three times, then once with `TESTS=com.kino.puber.ui.feature.player.vm.PlaybackControllerNetworkTracksTest,com.kino.puber.ui.feature.player.vm.PlaybackControllerDeviceTest` (AC-4 guards); record pass counts from the XML; release the lease and `ssh mac-emu stop` if `tools/emulator-resource-lock.sh status` shows no other lease
- [x] Commit

**Done when:** three `ITEST` runs each list three executed, passing cases in the XML; the guard classes pass; the fast gate and the instrumentation compile are green.

---

### Task 3: Describe the queued-failure case as a regression test and run the full gate

**Covers:** AC-7, AC-8
**Files:**
- Modify: `docs/testing/player-playback.md:11` - drop "is still reported against the new stream ... tracked in `player-callback-gate`"; state that a failure queued before `switchStream` is neither reported against the new stream nor stops it loading
- Modify: `docs/testing/player-playback.md:56-57` - the probe is a regression guard fixed by run `player-callback-gate` (`PlaybackCallbackGate`), not a "reproduced" result
- Modify: `docs/testing/player-playback.md:63` - replace "Reproduces the defect ... `@Ignore`d until run `player-callback-gate`" with: passes; guards the stale-error re-prepare in `PlaybackController.switchStream`

**Consumes:** Tasks 1-2.
**Produces:** nothing.

- [x] Edit the three places
- [x] `grep -n "tracked\|@Ignore\|Reproduces" docs/testing/player-playback.md` returns no line about the queued-failure case
- [x] Run the full gate; commit

**Done when:** the grep shows no stale wording and the full gate exits 0.

AC-6 (TV box quality switch) is verification work for `qa-device` per the spec's Manual checks; no task here.

---

## Implementation record

<!-- owner: implementer -->

**Commits:**

- Task 1: `a569fdb4` Add PlaybackCallbackGate ...
- Task 2: Route PlaybackController callbacks through per-session listeners ...
- Task 3: Document the queued-failure case as a regression test (sha in git log)

**Red run** (P0 `red` tests on the unchanged code):

- red: PlaybackCallbackGateTest (all 10 rows) - compile failure, class missing on base; ran green after Task 1 (10 tests, 0 failures)
- red: switchStreamAfterQueuedFailure_reportsNoFailureForPreviousStream - `stream B is ready: condition stayed false for 15000 ms`, events end with StreamFailure(NEXT_URL, url=progressive.mp4); XML 3 cases, 1 failure (`.flow/runs/player-callback-gate/itest-red.xml`)
- after fix: 3 runs of PlaybackControllerStaleCallbackTest, each 3 cases, 0 failures (itest-1..3.xml); guards NetworkTracksTest 9 + DeviceTest 5, 0 failures (itest-guards.xml). Emulator host.docker.internal:5555, lease released, no other lease; emulator not stopped by me beyond status check.

- fix round 1: new case `switchStreamToFailingStream_reportsOneFailureWithoutRePrepare` (healthy A, switch to malformed B: one StreamFailure with B's URL, one request to B). 3 runs of PlaybackControllerStaleCallbackTest, each 4 cases, 0 failures (`itest-fix1-1..3.xml`). With the `gate.onLoadStarted` line removed locally the class fails 1 of 4 cases (`itest-fix1-mut.xml`); experiment reverted, not committed.

**Deviations from the plan:** none

**Files touched outside the plan:**

**For the reviewers:** error gating now precedes the BehindLiveWindow and AC3 branches (both only run for a current session); `analyticsListener.onLoadStarted` compares `dataSpec.uri` (requested URI) with the stream URL. AC-6 (TV box) is left to qa-device. check.full green (EXIT=0, BUILD SUCCESSFUL, baseline-profile tooling tests PASS).

**Blocked:**

## Readiness

Verdict: **READY**

Checked: spec `docs/superpowers/specs/2026-11-27-player-callback-gate.md`, plan `docs/superpowers/plans/2026-11-27-player-callback-gate.md` (worktree HEAD `14549a92`; diff from base `cec5f7fc` is these two docs only), `test-design.md`, `state.md`.

### AC coverage

| AC | Plan task | Test design row |
|---|---|---|
| AC-1 | Task 2 (red run, 3x `ITEST` with XML count) | P0 red `switchStreamAfterQueuedFailure_...`, P0 guards `delayedFailure_...`, `switchStreamBeforeDelayedFailure_...` |
| AC-2 | Task 1 | P0 red `dispatch_*` (3 rows) |
| AC-3 | Task 1 | P0 red `routeError_*` (5 rows), P1, P2 |
| AC-4 | Task 2 | P1 guard device tests, P2 diff review |
| AC-5 | Task 2 (grep step) | P2 diff review |
| AC-6 | none; plan assigns it to `qa-device` per spec Manual check 1 (verification-only AC) | P1 device, Manual check 1 |
| AC-7 | Task 3 | P2 read |
| AC-8 | Task 3 | P0 guard `check.full` |

### Citations sampled (at `cec5f7fc`)

| Citation | Resolves to |
|---|---|
| `PlaybackController.kt:175` | `private var currentStreamUrl` |
| `PlaybackController.kt:214-271` | `playerListener`; `:241` `onStreamReady(currentStreamUrl...)`, `:260-264` `onStreamFailure(... currentStreamUrl)`, `:235` `onPlaybackEnded` |
| `PlaybackController.kt:274-281` | `analyticsListener.onLoadStarted` |
| `PlaybackController.kt:427-430`, `:435` | builder `apply { addListener(playerListener) ... }`, `setMediaItem` in `prepare` |
| `PlaybackController.kt:473-484` | "Deliberately no `stop()`" KDoc attached to `createMediaSourceFactory` |
| `PlaybackController.kt:499-519`, `:506` | `switchStream`, `setMediaItem(..., savedPosition)`, no `stop()` |
| `PlaybackController.kt:581-598`, `:586` | `release`, `removeListener(playerListener)` |
| `PlaybackController.kt:187`, `:790` | `SeekStallRecovery` URL reader, debug `streamSource` reader |
| `PlaybackControllerStaleCallbackTest.kt:12`, `:84`, `:106-110` | `import org.junit.Ignore`, `@Ignore("player-callback-gate...")`, `assertNotNull(... probe.error.get())` |
| `PlayerVM.kt:262-266`, `:273-280`, `:456`, `:476` | `onStreamReady`, `onStreamFailure` URL check and `NEXT_URL` branch, `switchStream` callers |
| `PlaybackMediaItemFactory.kt:42` | `uri = stream.url` |
| `SeekStallRecoveryPolicyTest.kt:1-8` | plain `internal class`, JUnit 5 imports |
| `gradle/libs.versions.toml:27` | `media3 = "1.11.0"` |
| Guard tests | `progressiveMp4_reachesReadyAdvancesAndEnds` (DeviceTest:33), `hlsSwitch_...LastStreamWins` (:110), `hls404AndMalformedPlaylist_...` (:209), `delayedFailure_...` (Stale:49), `switchStreamBeforeDelayedFailure_...` (Stale:66) exist |

### Order and build

Task 1 adds an unused-in-main `internal` class plus its test (build green); Task 2 consumes it, the red run edits only the test source; Task 3 is docs plus `check.full`. Dependency order holds.

### P0 rows

Every P0 row is marked `red` or `guard`, names the defect it catches, and names its test in backticks as it will appear in code.

### Scope and questions

Plan stays inside Out of scope (no `stop()`, no Robolectric, no `PlayerVM.kt`, no `STALE` body change). Q1 (blocking) answered A; Q2, Q3 answered.

### Notes for implementation (not blocking)

| # | Note |
|---|---|
| 1 | Task 3 grep also matches `docs/testing/player-playback.md:41` ("tracked as run `itest-incomplete-run-green`"); that line is not about the queued-failure case and stays. |
| 2 | Spec cites the `STALE` KDoc as `:21-23`, plan as `:19-23`; the reworded sentence spans `:19-23`. |

