# Brief: player-callback-gate

Kind: bug. Size: M. Tracks: none. Device verification: yes (playback and ViewModel-facing callbacks; the AC is an instrumented device test).
Research: research.md (all sections); option A recommended there and here. Checked on base cec5f7fc.

## Request

A stream failure already queued when `PlaybackController.switchStream(B)` runs is reported with B's URL, and B is never loaded. Reproduced by run `player-hermetic-fixtures` AC-5 (`.flow/runs/player-hermetic-fixtures/device-run-5.md`). Acceptance anchor: un-ignore `PlaybackControllerStaleCallbackTest.switchStreamAfterQueuedFailure_reportsNoFailureForPreviousStream` and make it pass. Upstream reference: `PlaybackCallbackGate` in `fe6224fb` (spec D26).

## Root cause

| Defect | Cause | Where | Status |
|---|---|---|---|
| Wrong attribution | one listener for the player's life reads `currentStreamUrl` at dispatch; `switchStream` has already overwritten it with B | `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:260-264`, `:507` | confirmed (device-run-5: `StreamFailure(NEXT_URL)` carries B's URL) |
| B never loads | `switchStream` calls `setMediaItem` + `prepare()` without `stop()`; Media3 1.11.0 `ExoPlayerImpl.prepare()` returns unless the masked state is IDLE, and it is still BUFFERING because A's error has not been delivered; the error then surfaces with the internal IDLE state and nothing re-prepares | `PlaybackController.kt:499-519` | confirmed on device (B never requested); Media3 mechanism plausible (bytecode, research.md: Media3 1.11.0 behaviour) |

Same staleness, not yet observed: a queued READY of A calls `onStreamReady(B)` (`PlaybackController.kt:241`) and resets `streamLinksRefreshAttempted` (`PlayerVM.kt:262-266`); a queued ENDED calls `onPlaybackEnded` (`:235`). Production effect of the reproduced case: `PlayerVM.kt:273-280` accepts the failure as B's and skips candidate B or spends the one links refresh.

## Why M

One goal, few files (`PlaybackController.kt`, a small gate class, a JVM test, the device test, `docs/testing/player-playback.md:11,63`), but it changes playback, a risk area in `.flow/roles/analyst.md`, and the switch semantics every quality pick and `NEXT_URL` fallback goes through (`PlayerVM.kt:456,476,528`).

## Design direction (research.md: Options, A)

- Session token bumped in `prepare` (`:436`) and `switchStream` (`:507`), invalidated in `release` (`:591`); the URL is captured per session, so callbacks carry the session's URL, not the live field.
- A `PlaybackException` that belongs to the previous media is dropped from callbacks and answered with one `player.prepare()` (forwarded now, the player is IDLE). The "belongs to previous media" signal is the design decision: candidates are `PlaybackException.timestampMs` (set on the playback thread when A failed) against the switch time, or "no load started for this session" via `AnalyticsListener.onLoadStarted` (`:274`). Ordering of either against the error update is unverified.
- No `stop()` in `switchStream` (KDoc `:474-483`).

## Acceptance criteria

- AC-1. Given the instrumentation flavor on the device from Q2, when `make FLAVOR=instrumentation DEVICE=<serial> GRADLE=./tools/agentw itest TESTS=com.kino.puber.ui.feature.player.vm.PlaybackControllerStaleCallbackTest` runs with the `@Ignore` removed and the test body unchanged, then all three cases pass, three consecutive runs, and the result XML lists three executed cases per run.
- AC-2. Given a JUnit 5 test of the gate, when a callback is dispatched for a session older than the current one, or after `release`, then it is not delivered; a callback of the current session is delivered with that session's URL.
- AC-3. Given a JVM test of the stale-error decision, when an error belonging to the previous media arrives in a new session, then no `onStreamFailure`/`onError` is reported and exactly one re-prepare is requested; a second error in the same session is reported as a failure of the new URL.
- AC-4. Given `PlaybackController`, when `onStreamReady`, `onStreamFailure`, `onError` and `onPlaybackEnded` fire, then each goes through the session check and carries the session's URL (code review against `PlaybackController.kt:214-271`).
- AC-5. Given `switchStream`, when read after the change, then it calls no `player.stop()`.
- AC-6. Given the dev APK installed with `tools/android-apk-install-preserve` on the TV box `192.168.1.110:5555` (`com.kino.puber.stage`), when an item is played and the quality is switched twice with the D-pad, then playback resumes at the same position each time with no error screen.
- AC-7. Given `docs/testing/player-playback.md`, when read, then lines 11 and 63 describe the queued-failure case as a passing regression test, not a tracked defect.
- AC-8. Given the worktree, when `check.full` from `.flow/config.yaml` runs, then it passes.

## Risks

| Risk | Mitigation |
|---|---|
| The stale-error signal misclassifies: a real B error before any B load is retried once, or a stale A error slips through | one re-prepare per session at most (AC-3); AC-1 three runs |
| The device test is timing-dependent (one main-thread turn, `PARSE_WAIT_MS`) and can pass without reaching the window | its `assertNotNull(probe.error)` precondition stays; body unchanged (AC-1) |
| A worktree build without client secret can drop the box session to pairing (memory: worktree build loses box session) | refresh with a master build and `make DEVICE=192.168.1.110:5555 auth-save` before AC-6 |
| `PlaybackController.kt` is also the target of idea runs `playback-controller-split`, `player-ended-play-hardening` | none in flight; merge order only |
| A media3 bump (run `media3-hagc-removal`) can change the `prepare()` masking this fix relies on | AC-1 is the regression test |

## Open questions

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| Q1 | Option A (no `stop()`, test unchanged, stale error answered by one re-prepare) or B (upstream `stop()` in `switchStream`, then relax the test's "probe saw A's error" precondition)? | A | yes |
| Q2 | Where does AC-1 run: the emulator `host.docker.internal:5555` as in `player-hermetic-fixtures` Q1 (the box allows only `com.kino.puber.stage`, the test installs `com.kino.puber.instrumentation`)? | yes, emulator for AC-1; box with the dev APK for AC-6 | no |
| Q3 | Port `PlaybackCallbackGate` as a small class with a JUnit 5 test, or keep the token inline in the controller? | small class plus JUnit 5 test (spec D26); no upstream Robolectric tests | no |
