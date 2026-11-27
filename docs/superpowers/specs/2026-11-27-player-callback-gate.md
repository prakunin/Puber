# A stream failure queued before switchStream is attributed to the new stream and the new stream never loads

Status: approved
Date: 2026-11-27
Slug: player-callback-gate
Kind: bug
Size: M
Tracks: none

## Problem / Why

### Request

A stream failure already queued when `PlaybackController.switchStream(B)` runs is reported with B's URL, and B is never loaded. Reproduced by run `player-hermetic-fixtures` AC-5 (`.flow/runs/player-hermetic-fixtures/device-run-5.md`). Acceptance anchor: un-ignore `PlaybackControllerStaleCallbackTest.switchStreamAfterQueuedFailure_reportsNoFailureForPreviousStream` and make it pass. Upstream reference: `PlaybackCallbackGate` in `fe6224fb` (spec D26).

### Root cause

| Defect | Cause | Where | Status |
|---|---|---|---|
| Wrong attribution | one listener for the player's life reads `currentStreamUrl` at dispatch; `switchStream` has already overwritten it with B | `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:260-264`, `:507` | confirmed (device-run-5: `StreamFailure(NEXT_URL)` carries B's URL) |
| B never loads | `switchStream` calls `setMediaItem` + `prepare()` without `stop()`; Media3 1.11.0 `ExoPlayerImpl.prepare()` returns unless the masked state is IDLE, and it is still BUFFERING because A's error has not been delivered; the error then surfaces with the internal IDLE state and nothing re-prepares | `PlaybackController.kt:499-519` | confirmed on device (B never requested); Media3 mechanism plausible (bytecode, research.md: Media3 1.11.0 behaviour) |

Same staleness, not yet observed: a queued READY of A calls `onStreamReady(B)` (`PlaybackController.kt:241`) and resets `streamLinksRefreshAttempted` (`PlayerVM.kt:262-266`); a queued ENDED calls `onPlaybackEnded` (`:235`). Production effect of the reproduced case: `PlayerVM.kt:273-280` accepts the failure as B's and skips candidate B or spends the one links refresh.

### Why M

One goal, few files (`PlaybackController.kt`, a small gate class, a JVM test, the device test, `docs/testing/player-playback.md:11,63`), but it changes playback, a risk area in `.flow/roles/analyst.md`, and the switch semantics every quality pick and `NEXT_URL` fallback goes through (`PlayerVM.kt:456,476,528`).

### Design direction (research.md: Options, A)

- Session token bumped in `prepare` (`:436`) and `switchStream` (`:507`), invalidated in `release` (`:591`); the URL is captured per session, so callbacks carry the session's URL, not the live field.
- A `PlaybackException` that belongs to the previous media is dropped from callbacks and answered with one `player.prepare()` (forwarded now, the player is IDLE). The "belongs to previous media" signal is the design decision: candidates are `PlaybackException.timestampMs` (set on the playback thread when A failed) against the switch time, or "no load started for this session" via `AnalyticsListener.onLoadStarted` (`:274`). Ordering of either against the error update is unverified.
- No `stop()` in `switchStream` (KDoc `:474-483`).

### Risks (analyst)

| Risk | Mitigation |
|---|---|
| The stale-error signal misclassifies: a real B error before any B load is retried once, or a stale A error slips through | one re-prepare per session at most (AC-3); AC-1 three runs |
| The device test is timing-dependent (one main-thread turn, `PARSE_WAIT_MS`) and can pass without reaching the window | its `assertNotNull(probe.error)` precondition stays; body unchanged (AC-1) |
| A worktree build without client secret can drop the box session to pairing (memory: worktree build loses box session) | refresh with a master build and `make DEVICE=192.168.1.110:5555 auth-save` before AC-6 |
| `PlaybackController.kt` is also the target of idea runs `playback-controller-split`, `player-ended-play-hardening` | none in flight; merge order only |
| A media3 bump (run `media3-hagc-removal`) can change the `prepare()` masking this fix relies on | AC-1 is the regression test |

## Acceptance criteria

- AC-1. Given the instrumentation flavor on the device from Q2, when `make FLAVOR=instrumentation DEVICE=<serial> GRADLE=./tools/agentw itest TESTS=com.kino.puber.ui.feature.player.vm.PlaybackControllerStaleCallbackTest` runs with the `@Ignore` removed and the test body unchanged, then all three cases pass, three consecutive runs, and the result XML lists three executed cases per run.
- AC-2. Given a JUnit 5 test of the gate, when a callback is dispatched for a session older than the current one, or after `release`, then it is not delivered; a callback of the current session is delivered with that session's URL.
- AC-3. Given a JVM test of the stale-error decision, when an error belonging to the previous media arrives in a new session, then no `onStreamFailure`/`onError` is reported and exactly one re-prepare is requested; a second error in the same session is reported as a failure of the new URL.
- AC-4. Given `PlaybackController`, when `onStreamReady`, `onStreamFailure`, `onError` and `onPlaybackEnded` fire, then each goes through the session check and carries the session's URL (code review against `PlaybackController.kt:214-271`).
- AC-5. Given `switchStream`, when read after the change, then it calls no `player.stop()`.
- AC-6. Given the dev APK installed with `tools/android-apk-install-preserve` on the TV box `192.168.1.110:5555` (`com.kino.puber.stage`), when an item is played and the quality is switched twice with the D-pad, then playback resumes at the same position each time with no error screen.
- AC-7. Given `docs/testing/player-playback.md`, when read, then lines 11 and 63 describe the queued-failure case as a passing regression test, not a tracked defect.
- AC-8. Given the worktree, when `check.full` from `.flow/config.yaml` runs, then it passes.

## Out of scope

Not listed separately in the brief. From the brief: no `stop()` in `switchStream` (Q1, option A); no upstream Robolectric tests (Q3); Part B Compose E2E tests belong to run `player-compose-e2e-tests`.

## Design

Base `cec5f7fc`. Paths are relative to the worktree; `PC` = `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt`.

### Decisions

| Question | Decision | Why |
|---|---|---|
| Signal that an error belongs to the previous media (brief: Design direction) | A switch session whose own stream URL has not started loading: `AnalyticsListener.onLoadStarted` (`PC:274`) with `loadEventInfo.dataSpec.uri.toString() == session.streamUrl` marks the session loaded | In the defect B never loads (masked `prepare()` is a no-op), so "B's URL never started loading" holds for every stale error, including one created on the playback thread after the switch call. Load events of A carry A's URL and cannot mark B. `MediaItemSpec.uri` is `stream.url` verbatim (`PlaybackMediaItemFactory.kt:42,60`) |
| Answer to a stale error | Drop it from every callback and call `player.prepare()` once per session; checked before the BehindLiveWindow and AC3 branches (`PC:258-259`) | The player is IDLE with B's item and saved position already set by `setMediaItem(item, savedPosition)` (`PC:506`), so a forwarded prepare loads B there; a stale BehindLiveWindow would otherwise seek B to the live edge |
| Bound on misclassification | At most one re-prepare per session; the next error is reported against the session URL | A real B error before any load of B costs one extra prepare (brief AC-3) |
| Sessions that may re-prepare | Only sessions begun by `switchStream`; `prepare` sessions never | `prepare` releases the old player first (`PC:385`), so no previous media can queue an error into it |
| Gate shape (Q3) | New `PlaybackCallbackGate` with nested `Session(generation, streamUrl, fromSwitch)`; `beginSession`, `invalidate`, `dispatch(session) { streamUrl -> }`, `onLoadStarted(requestedUri)`, `routeError(session, reprepare, report)` | Ports upstream `fe6224fb` `PlaybackCallbackGate` and keeps the stale decision JVM-testable without ExoPlayer (AC-2, AC-3) |
| Callback ownership | The gate holds no `Callback`; `dispatch` runs a lambda that uses the controller's `callback` field (`PC:169`) | Upstream's `setCallback` on the gate would duplicate `PC:374-376`; `notifyPlaybackState`/`notifyTracksUpdated` (`PC:821,861`) keep their current call |
| Locking | None; all gate calls run on the application looper | ExoPlayer is built and called on main (`PC:419`, harness `onMain`); upstream's `synchronized` guards nothing here |
| Listener lifetime | One `Player.Listener` per session: the current `playerListener` (`PC:214-271`) becomes an inner class bound to a `Session`; bound in `prepare` and `switchStream` before `setMediaItem`, removed on the next bind and in `release` | Matches spec D26; a listener removed during a nested switch (VM `onStreamFailure` -> `switchStream` inside dispatch, `PlayerVM.kt:275,476`) receives no more events, and the generation check covers what remains |
| `currentStreamUrl` (`PC:175`) | Stays for `SeekStallRecovery` (`PC:187`) and debug info (`PC:790`); listener callbacks read `session.streamUrl` | Smallest change; those readers want the live URL |
| Switch KDoc | Move the "Deliberately no `stop()`" KDoc from `createMediaSourceFactory` (`PC:473-483`, attached to `PC:484`) to `switchStream` and add one sentence on the stale-error re-prepare | It documents `switchStream` (AC-5) but sits on the wrong function |

### Changes by layer

API / repository, screen and DI module, Compose content, resources: N/A, the change is inside the player controller and its tests.

#### ViewModel state and actions (player controller)

| File | Change |
|---|---|
| `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackCallbackGate.kt` (new) | `internal class PlaybackCallbackGate`: `generation: Long`, `current: Session?`. `beginSession(streamUrl, fromSwitch)` bumps the generation and returns the new current session; `invalidate()` bumps it and clears `current`; `dispatch` runs the action with `session.streamUrl` only when `session` is current; `onLoadStarted(uri)` sets `streamLoadStarted` on the current session when `uri == streamUrl`; `routeError`: not current -> nothing, `fromSwitch && !streamLoadStarted && !staleErrorRetried` -> set `staleErrorRetried`, `reprepare()`, else `report(streamUrl)` |
| `PC:214-271` | `playerListener` -> `private inner class SessionListener(val session: PlaybackCallbackGate.Session) : Player.Listener`. ENDED (`:235`) and READY (`:241`) go through `gate.dispatch(session)`, READY with the dispatched URL; `onPlayerError` (`:253-270`) becomes `gate.routeError(session, reprepare = { exoPlayer?.prepare() }, report = { url -> <existing when, with url at :263> })`. Other overrides wrap their bodies in `dispatch` |
| `PC` new private `bindSession(player, streamUrl, fromSwitch)` | Removes the previous `SessionListener`, begins a gate session, adds a new listener; field `sessionListener: SessionListener?` |
| `PC:274-281` `onLoadStarted` | Adds `gate.onLoadStarted(loadEventInfo.dataSpec.uri.toString())` before the existing line |
| `PC:427-430`, `:434-436` `prepare` | `addListener(playerListener)` removed from the builder `apply`; `bindSession(player, stream.url, fromSwitch = false)` before `setMediaItem` at `:435` |
| `PC:499-519` `switchStream` | `bindSession(player, stream.url, fromSwitch = true)` before `setMediaItem` at `:506`; no `stop()` (AC-5); KDoc moved here (Decisions) |
| `PC:581-598` `release` | `removeListener(sessionListener)` replaces `:586`; `gate.invalidate()`; `sessionListener = null` |

#### Tests and docs

| File | Change |
|---|---|
| `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackCallbackGateTest.kt` (new, JUnit 5) | AC-2 and AC-3 against the gate; rows come from `test-design.md` |
| `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerStaleCallbackTest.kt:84`, `:12` | Remove `@Ignore` and its import; body unchanged (AC-1). KDoc `:21-23` reworded from "finding goes to the run" to a regression statement |
| `docs/testing/player-playback.md:11,57,63` | Queued-failure case described as a passing regression test (AC-7); `:57` names the run too |

### Alternatives rejected

| Alternative | Reason |
|---|---|
| `PlaybackException.timestampMs` earlier than the switch time | Misses an A error created on the playback thread after the switch call but before it processes `setMediaItems(B)`; B then never loads |
| Any `onLoadStarted` in the session, URL unchecked | A load start of A already queued at the switch would mark B as loaded and let the stale error through |
| `stop()` before `setMediaItem` (option B) | Owner chose A (Q1): allocator reset on every switch and the test precondition at `PlaybackControllerStaleCallbackTest.kt:106-110` fails |
| Gate only, single shared listener (option C) | The stale error is delivered after the switch to whatever listener is current; neither defect is fixed |
| Gate owns the callback (upstream `setCallback`) | Two holders of one callback |

### Risks

| Risk | Area | Code | Mitigation |
|---|---|---|---|
| A real B error arrives on main before B's `onLoadStarted` (ordering of analytics vs playback-info posts is unverified) and is retried once | correctness | `PC:274`, `routeError` | One retry per session, then reported (AC-3) |
| Switch to the same URL as the failing stream (links refresh returning identical URLs, `PlayerVM.kt:510-528`): A's queued load start matches B's URL, the stale error is reported against that URL and B is not re-prepared | correctness | `gate.onLoadStarted` | VM still advances (`PlayerVM.kt:275-277`); same behaviour as today, narrower |
| A re-entrant switch inside a callback leaves events of the old batch for the removed listener | lifecycle | `bindSession`, `PlayerVM.kt:476` | Removed listener is not invoked; `dispatch` generation check |
| Re-prepare after `release` or on a replaced player | lifecycle | `routeError` reprepare lambda | Lambda uses `exoPlayer?`; `routeError` returns for a non-current session, and `release` invalidates |
| Stale error still flips `isPlaying` to false for one turn; `notifyPlaybackState` reports it | correctness (UI) | `PC:215-228` | Transient; B's READY follows. AC-6 on the box |
| Device test passes without reaching the window | rollout | `PlaybackControllerStaleCallbackTest.kt:106-110` | Precondition unchanged; three runs, XML case count (AC-1) |
| A media3 bump changes `prepare()` masking or listener removal semantics | rollout | `gradle/libs.versions.toml:27` | AC-1 is the regression test |
| Per-session listener add/remove on each switch | performance | `bindSession` | One allocation per switch; negligible next to the source swap |

### Open questions

none

## Test design

Base `cec5f7fc`. Levels from `.flow/roles/test-designer.md`; `check` = a gate command. Unit tests in `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackCallbackGateTest.kt` (new, JUnit 5, plain `internal class` as `SeekStallRecoveryPolicyTest`; no ExoPlayer, no dispatcher needed: the gate is synchronous). Instrumented tests in `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/`, run with `AGENT_GRADLE_SERIAL=1 make FLAVOR=instrumentation DEVICE=host.docker.internal:5555 GRADLE=./tools/agentw itest TESTS=<fqcn>` under an emulator lease (state.md Q2).

### Risk profile

| # | Risk (source) | P | I | Score | Covered by |
|---|---|---|---|---|---|
| R1 | Stale A error slips through: B's URL marked loaded by A's queued load start, or the stale check placed after the BehindLiveWindow/AC3 branches (design) | 2 | 3 | 6 | `routeError_reportsNothingAndReprepares_whenLoadStartOfPreviousUrlArrives`, AC-1 test |
| R2 | Queued stale error re-prepared without bound, or a real B error never reported (design: bound) | 2 | 3 | 6 | `routeError_reportsSecondErrorWithSessionUrl_afterStaleRetry` |
| R3 | A `prepare` session treats its first real error as stale and swallows it (design: only switch sessions re-prepare) | 2 | 3 | 6 | `routeError_reportsFirstError_whenSessionComesFromPrepare`, guards `delayedFailure_...`, `hls404AndMalformedPlaylist_...` |
| R4 | Per-session listener rebinding loses the current session's READY/ENDED or tracks (design: listener lifetime) | 2 | 3 | 6 | AC-1 test (awaits B `StreamReady`), guards `hlsSwitch_preservesPositionIntentTracks_andLastStreamWins`, `progressiveMp4_reachesReadyAdvancesAndEnds` |
| R5 | Device test passes without reaching the window (brief, design) | 2 | 3 | 6 | precondition `assertNotNull(probe.error)` unchanged; three runs with XML case count (AC-1) |
| R6 | A media3 bump changes `prepare()` masking (brief, design) | 2 | 3 | 6 | AC-1 test is the regression test |
| R7 | Worktree build drops the box session to pairing (brief) | 3 | 2 | 6 | Manual step 1 |
| R8 | Re-entrant switch inside a callback leaves old events for a removed listener (design) | 2 | 2 | 4 | `dispatch_skipsAction_whenNewerSessionBegan` |
| R9 | Re-prepare after `release` or for a replaced session (design) | 1 | 3 | 3 | `routeError_doesNothing_whenSessionIsNotCurrent`, `dispatch_skipsAction_afterInvalidate` |
| R10 | Stale error flips `isPlaying` for one turn (design) | 3 | 1 | 3 | Manual check AC-6 |
| R11 | Real B error before B's `onLoadStarted` retried once (design) | 2 | 1 | 2 | Accepted: one extra prepare, then reported (R2 test) |
| R12 | Switch to the same URL as the failing stream: stale error reported, no re-prepare (design) | 1 | 2 | 2 | Accepted: today's behaviour, VM advances (`PlayerVM.kt:275-277`) |
| R13 | Listener add/remove per switch (design) | 1 | 1 | 1 | Accepted |

### Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | instrumented | P0 red | `PlaybackControllerStaleCallbackTest.switchStreamAfterQueuedFailure_reportsNoFailureForPreviousStream` (`@Ignore` removed, body unchanged; 3 runs, 3 cases each in the XML) | fails if A's queued error is reported against B or B is never prepared (`StreamReady` never arrives / current item not B) |
| AC-1 | instrumented | P0 guard | `PlaybackControllerStaleCallbackTest.delayedFailure_reportsStreamFailure_whenStreamIsNotSwitched` | fails if the gate swallows a real failure of a `prepare` session or reports it with another URL (asserts A's URL, `NEXT_URL`) |
| AC-1 | instrumented | P0 guard | `PlaybackControllerStaleCallbackTest.switchStreamBeforeDelayedFailure_reportsNoFailureForPreviousStream` | fails if rebinding the listener breaks the cancelled-load ordering |
| AC-2 | unit | P0 red | `PlaybackCallbackGateTest.dispatch_runsActionWithSessionUrl_whenSessionIsCurrent` | fails if the current session's action is dropped or gets a URL other than its own |
| AC-2 | unit | P0 red | `PlaybackCallbackGateTest.dispatch_skipsAction_whenNewerSessionBegan` | fails if the generation check is removed (session A dispatched after `beginSession(B)` runs) |
| AC-2 | unit | P0 red | `PlaybackCallbackGateTest.dispatch_skipsAction_afterInvalidate` | fails if `invalidate` does not end the current session (callback after `release`) |
| AC-3 | unit | P0 red | `PlaybackCallbackGateTest.routeError_reportsNothingAndRepreparesOnce_whenSwitchSessionHasNotLoadedItsUrl` | fails if a stale error is reported, or re-prepare is not called exactly once |
| AC-3 | unit | P0 red | `PlaybackCallbackGateTest.routeError_reportsSecondErrorWithSessionUrl_afterStaleRetry` | fails if the per-session retry flag is missing (second error re-prepared again) or the report carries a URL other than B's |
| AC-3 | unit | P0 red | `PlaybackCallbackGateTest.routeError_reportsNothingAndReprepares_whenLoadStartOfPreviousUrlArrives` | `onLoadStarted(A)` after `beginSession(B, fromSwitch = true)`; fails if `onLoadStarted` ignores the URL |
| AC-3 | unit | P0 red | `PlaybackCallbackGateTest.routeError_reportsWithSessionUrl_whenSwitchSessionLoadedItsUrl` | `onLoadStarted(B)` first; fails if a loaded session still re-prepares (real B error swallowed) |
| AC-3 | unit | P0 red | `PlaybackCallbackGateTest.routeError_reportsFirstError_whenSessionComesFromPrepare` | fails if `fromSwitch` is ignored and a first-play error is retried instead of reported |
| AC-3 | unit | P1 | `PlaybackCallbackGateTest.routeError_doesNothing_whenSessionIsNotCurrent` | fails if a stale session's error reprepares or reports (also after `invalidate`) |
| AC-3 | unit | P2 | `PlaybackCallbackGateTest.routeError_allowsOneRetryPerSession_whenAnotherSwitchBegins` | fails if the retry flag lives on the gate instead of the session |
| AC-4 | instrumented | P1 guard | `PlaybackControllerNetworkTracksTest.hlsSwitch_preservesPositionIntentTracks_andLastStreamWins`, `PlaybackControllerNetworkTracksTest.hls404AndMalformedPlaylist_failWithoutUnboundedRetry`, `PlaybackControllerDeviceTest.progressiveMp4_reachesReadyAdvancesAndEnds` | fails if gated ready, failure or ended callbacks stop arriving or rapid switches lose the last stream |
| AC-4 | manual (review) | P2 | Diff review of `PlaybackController.kt` `SessionListener` | every `onStreamReady`/`onStreamFailure`/`onError`/`onPlaybackEnded` goes through `gate.dispatch`/`routeError` and reads the session URL |
| AC-5 | manual (review) | P2 | Diff review of `switchStream` | no `player.stop()`; KDoc moved onto `switchStream` |
| AC-6 | device | P1 | Manual check 1 | fails if a switch shows the error screen, restarts from 0 or skips a candidate |
| AC-7 | manual (review) | P2 | Read `docs/testing/player-playback.md:11,57,63` | no "tracked"/"`@Ignore`d" wording left for the queued-failure case |
| AC-8 | check | P0 guard | `check.full` from `.flow/config.yaml` in the worktree | fails on any unit, detekt or lint regression, including `PlaybackCallbackGateTest` |

### Failing-first test

`PlaybackControllerStaleCallbackTest.switchStreamAfterQueuedFailure_reportsNoFailureForPreviousStream`: on base it is `@Ignore`d; with the annotation removed it fails (`StreamFailure(NEXT_URL)` with B's URL, B never requested: `.flow/runs/player-hermetic-fixtures/device-run-5.md`; cause `PlaybackController.kt:260-264` at `cec5f7fc` reads `currentStreamUrl`). Run it once on unchanged production code with only the `@Ignore` removed and record the failure before the fix. The `PlaybackCallbackGateTest` rows are red because the class does not exist on base.

### Manual checks

1. Device AC-6, TV box `192.168.1.110:5555`, package `com.kino.puber.stage` only, under a lease.
   1. Before installing the worktree APK: build and install a master dev APK, confirm the app is logged in, run `make DEVICE=192.168.1.110:5555 auth-save` (R7).
   2. Install the worktree dev APK with `tools/android-apk-install-preserve`; launch; confirm the home screen, not pairing.
   3. Open a movie, start playback, let it play 30 s (`KEYCODE_DPAD_CENTER` on Play).
   4. Open the quality menu with the D-pad, pick a different quality (`KEYCODE_DPAD_DOWN`/`KEYCODE_DPAD_CENTER`), note the position before and after.
   5. Repeat step 4 back to the first quality.
   - Expected: each switch resumes within a few seconds of the noted position, playing; focus returns to the player.
   - Must not happen: error screen, restart from 0, stuck buffering, crash, pairing screen, a second skipped quality, any touch of `com.kino.puber`.

### perf track

N/A: perf track is off.

### UNTESTABLE

none

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Option A (no stop(), test body unchanged, one re-prepare for a stale error) or B (upstream stop(), relaxed precondition)? | yes | answered | A (recommended) |
| Q2 | AC-1 instrumentation itest on emulator host.docker.internal:5555 as in player-hermetic-fixtures Q1; dev-APK playback check AC-6 on box 192.168.1.110:5555? | no | answered | yes (recommended) |
| Q3 | PlaybackCallbackGate as a small class with a JUnit 5 test, or inline token? | no | answered | small class + JUnit 5 test, no Robolectric (recommended) |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-27 | codex: required (owner); antigravity on every round (owner); caps lifted (owner) | owner |
| 2026-11-27 | Q1: option A (session gate + one re-prepare, no stop()) | owner (standing instruction: recommended) |
| 2026-11-27 | Q2: AC-1 itest on emulator host.docker.internal:5555, AC-6 dev-APK playback on box 192.168.1.110:5555 | owner (standing instruction: recommended) |
| 2026-11-27 | Q3: PlaybackCallbackGate as a small class with a JUnit 5 test | owner (standing instruction: recommended) |
| 2026-11-27 | Gate 1: approved | owner (standing instruction: recommended) |
