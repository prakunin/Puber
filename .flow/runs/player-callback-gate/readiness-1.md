# Readiness 1: player-callback-gate

Verdict: **READY**

Checked: spec `docs/superpowers/specs/2026-11-27-player-callback-gate.md`, plan `docs/superpowers/plans/2026-11-27-player-callback-gate.md` (worktree HEAD `14549a92`; diff from base `cec5f7fc` is these two docs only), `test-design.md`, `state.md`.

## AC coverage

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

## Citations sampled (at `cec5f7fc`)

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

## Order and build

Task 1 adds an unused-in-main `internal` class plus its test (build green); Task 2 consumes it, the red run edits only the test source; Task 3 is docs plus `check.full`. Dependency order holds.

## P0 rows

Every P0 row is marked `red` or `guard`, names the defect it catches, and names its test in backticks as it will appear in code.

## Scope and questions

Plan stays inside Out of scope (no `stop()`, no Robolectric, no `PlayerVM.kt`, no `STALE` body change). Q1 (blocking) answered A; Q2, Q3 answered.

## Notes for implementation (not blocking)

| # | Note |
|---|---|
| 1 | Task 3 grep also matches `docs/testing/player-playback.md:41` ("tracked as run `itest-incomplete-run-green`"); that line is not about the queued-failure case and stays. |
| 2 | Spec cites the `STALE` KDoc as `:21-23`, plan as `:19-23`; the reworded sentence spans `:19-23`. |
