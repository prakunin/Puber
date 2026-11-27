# Research: player-callback-gate

## Request

fix the defect reproduced by run player-hermetic-fixtures AC-5: a stream failure that is already queued when PlaybackController.switchStream(B) runs is attributed to stream B's URL (StreamFailure carries the new URL because PlaybackController reads currentStreamUrl at dispatch time), and stream B is never loaded/requested. Acceptance anchor: un-ignore PlaybackControllerStaleCallbackTest.switchStreamAfterQueuedFailure_reportsNoFailureForPreviousStream (currently @Ignore("player-callback-gate")) and make it pass. Upstream reference fix: PlaybackCallbackGate in upstream commit fe6224fb (spec D26 of upstream-sync-triage; see 'git show upstream-sync-triage:docs/superpowers/specs/2026-10-11-upstream-sync-triage-design.md' and the upstream remote if present).

## Understanding

When stream A has already failed inside ExoPlayer but the error has not yet reached the main thread, `switchStream(B)` sets B's media item and calls `prepare()`. Media3 ignores that `prepare()` because the main-thread view of the player is still BUFFERING, while the internal player is already IDLE from A's error. The error then surfaces after the switch, the single shared listener reports it with B's URL, and nothing ever prepares B. Two defects: wrong attribution, and B never loading. The fix must solve both without breaking the test's own precondition that the player did deliver A's error.

## Current state in code

- `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:175`: one mutable `currentStreamUrl`, overwritten by `prepare` (:436) and `switchStream` (:507), cleared by `release` (:591).
- `PlaybackController.kt:214-271`: one `playerListener` for the player's life, added at :428, removed at :586. Every callback reads controller fields at dispatch time: `onStreamReady(currentStreamUrl)` :241, `onStreamFailure(..., currentStreamUrl)` :260-264, `onError` :267, `onPlaybackEnded` :235.
- `PlaybackController.kt:499-519` `switchStream`: `setMediaItem(item, savedPosition)`, then `prepare()`, `playWhenReady`, `seekStallRecovery.onStreamSwitched`. No `stop()` on purpose: KDoc at :474-483 says `stop()` resets `DefaultLoadControl` and trims the allocator pool (GC pause at switch). Text dates from upstream-era commit 0c01a3eb.
- `PlaybackController.kt:334-372` `streamRecovery()`: `ParserException` maps to `NEXT_URL`; this is the path A's malformed playlist takes.
- `PlaybackController.kt:184-195` `SeekStallRecovery` captures the URL when it schedules (`SeekStallRecovery.kt:36-37`), so its failure is not affected.
- `PlaybackController.kt:698-723`: warm-up player already guards posted work by player identity (`releaseWarmUpPlayer(expected)` :733-738), a small precedent for identity checks.
- `PlayerVM.kt:268-281` `onStreamFailure`: drops a failure whose URL is not the current candidate; otherwise `NEXT_URL` calls `switchToNextStreamCandidate()` (:471-476) or `refreshStreamLinksOnce` (:480). In production the defect therefore skips candidate B (or spends the one links refresh) for a failure that belonged to A. `switchStream` callers: `PlayerVM.kt:456` (quality pick), :476, :528.
- `PlayerVM.kt:139,307,401`: VM already has a `mediaGeneration` guard for its own async prepares, not for controller callbacks.
- Test: `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerStaleCallbackTest.kt:84` `@Ignore`; the case asserts B reaches `StreamReady` (:102-104), that `PlayerProbe` saw A's error (:106-110), no `StreamFailure`/`Error` after the mark and B is the current item (:114-124). `PlayerProbe` is a plain `Player.Listener` added to the same ExoPlayer (`PlaybackControllerHarness.kt:101-115,205-217`).
- Sibling cases in the same class (control, cancelled-load ordering) pass (`.flow/runs/player-hermetic-fixtures/device-run-5.md`); any fix must keep them green.
- `docs/testing/player-playback.md:11,63` already describe the defect as tracked here; they need updating when it is fixed.

### Media3 1.11.0 behaviour (from the AAR in the Gradle cache, `javap -c`)

Version: `gradle/libs.versions.toml:27` `media3 = "1.11.0"` (pinned, see run `media3-hagc-removal`).

| Method | Behaviour | Consequence here |
|--------|-----------|------------------|
| `ExoPlayerImpl.prepare()` | returns unless the masked `playbackInfo.playbackState == STATE_IDLE` | with A's error not yet delivered, `switchStream`'s `prepare()` is a no-op, so B is never prepared |
| `ExoPlayerImpl.handlePlaybackInfo` | subtracts `operationAcks`, applies the internal update only when `pendingOperationAcks == 0` | the error surfaces once the internal player acks B's `setMediaItems`, carrying the internal IDLE + error state |
| `ExoPlayerImpl.stop()` -> `stopInternal(null)` | masks IDLE, adds a pending ack, forwards `internalPlayer.stop()` | after `stop()`, `prepare()` is forwarded, and the queued error update is dropped while acks are pending |
| `ExoPlayerImplInternal.prepareInternal` | `resetInternal(false, false, false, true)` (resets the error) | a forwarded prepare clears A's error internally |

So the upstream ordering `stop(); setMediaSource; prepare` (`fe6224fb:app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackTransitions.kt:31-51`) loads B, but it also means A's error never reaches any listener, `PlayerProbe` included. Inferred from bytecode, not yet observed on a device: `unverified` until a device run.

## Related work

| Item | Relation |
|------|----------|
| run `player-hermetic-fixtures` (source) | AC-5 reproduced on emulator API 36, `device-run-5.md`; `judge-1.md` Concerns 2-3 |
| upstream-sync-triage spec D25/D26, R7 (`git show upstream-sync-triage:docs/superpowers/specs/2026-10-11-upstream-sync-triage-design.md`, lines 321-323, 532) | D26: port `PlaybackCallbackGate`, one listener per media session (begin at `prepare`/`switchStream`, invalidate at `release`), JUnit 5 `PlaybackCallbackGateTest`, probe stays as regression test |
| upstream `fe6224fb` (rovkinmax/Puber) | `PlaybackCallbackGate.kt` (38 lines: lock, generation, `beginSession`/`invalidate`/`dispatch`), `bindCallbackSession` per switch, `switchStream` through `PlaybackTransitions` with `stop()`; Robolectric `PlaybackControllerCallbackGenerationTest` drives captured listeners by hand, so it never exercises Media3 queueing |
| run `playback-controller-split` (idea) | same file; treats upstream policy split incl. `PlaybackCallbackGate` as reference, not port; overlap only |
| run `player-ended-play-hardening` (idea) | same file, `play()`/ended path; no overlap with the switch path |
| run `player-device-tests-hardening`, `player-corrupt-cache-recovery` (idea) | same test harness; independent |
| run `media3-hagc-removal` (idea) | a media3 bump would re-open the bytecode facts above |

Duplicate: none.

## External findings

External findings: off (local research only). Media3 semantics were read from the cached `media3-exoplayer-1.11.0.aar` bytecode instead.

## Options

| | A. Session gate plus re-prepare of a stale error (recommended) | B. Upstream port: `stop()` in `switchStream` plus per-session gate | C. Gate only (D26 literally, no `stop()`) |
|---|---|---|---|
| Change | session token bumped in `prepare`/`switchStream`, invalidated in `release`; URL captured per session; an error that reaches the current session before any load of the new stream started is dropped and answered with one `player.prepare()` (now forwarded, state is IDLE) | `stop()` before `setMediaItem`, listener per session through `PlaybackCallbackGate` | listener per session through `PlaybackCallbackGate` |
| Attribution | fixed | fixed (Media3 drops the error) | not fixed: the error surfaces after the switch, so the new session's listener receives it |
| B loads | yes, after one re-prepare | yes | no (`prepare()` stays a no-op) |
| Test as written | passes: probe sees A's error, callback does not | fails at the `assertNotNull(probe.error)` precondition (:106-110); the test must change | fails |
| Cost | keeps the allocator pool (KDoc :474-483); needs a "new stream has started loading" signal, e.g. `AnalyticsListener.onLoadStarted` (:274) for the session, whose ordering against the error update is `unverified` | allocator reset and GC pause on every quality switch and NEXT_URL fallback; diverges from the fork's stated design | none, and no benefit |
| Size | S (`PlaybackController.kt`, a small gate class, a JVM unit test, the device test un-ignored, `player-playback.md`) | S | S |

Recommended: A. It fixes both defects, keeps the test unchanged as the AC, and keeps the no-`stop()` switch. The worst case for a real B failure that arrives before any B load is one extra prepare, after which it is reported.

## Risks

| Risk | Mitigation |
|------|------------|
| Option A's "B has started loading" signal arrives after the stale error, or a real B error comes with no load event, so a real error is retried once or a stale one slips through | limit to one re-prepare per session; JVM unit test of the decision; device run of all three stale-callback cases three times, as in run `player-hermetic-fixtures` |
| Other URL-bearing callbacks have the same staleness: a queued READY for A reports `onStreamReady(B)` and resets `streamLinksRefreshAttempted` (`PlayerVM.kt:262-266`); a queued ENDED for A calls `onPlaybackEnded` after a switch | route `onStreamReady` and `onPlaybackEnded` through the same session check |
| Main-thread only: the gate needs no lock (all `Player.Listener` calls come on the application looper); upstream's `synchronized` is unnecessary but harmless | keep it unsynchronized and main-thread-asserted, or port as is |
| Production path is ViewModel state and playback; AGENTS.md requires on-device verification | `make DEVICE=192.168.1.110:5555 itest TESTS=...PlaybackControllerStaleCallbackTest` on the TV box (memory: box-only device work); check the XML case count (run `itest-incomplete-run-green`) |
| `PlaybackController.kt` is also touched by idea runs `playback-controller-split` and `player-ended-play-hardening` | none in flight; merge order only |

## Open questions

| ID | Question | Recommended answer |
|----|----------|--------------------|
| Q1 | Option A (keep the test unchanged, no `stop()`) or option B (upstream `stop()`, then relax the test's precondition that the probe saw A's error)? | A |
| Q2 | Port `PlaybackCallbackGate` as a class with a JUnit 5 unit test (spec D26), or keep the session token inline in the controller? | port it as a small class with a JUnit 5 test; skip upstream's Robolectric tests (spec D25) |
| Q3 | Gate only the failure and error callbacks, or also `onStreamReady`, `onPlaybackEnded` and track updates? | all callbacks that carry a URL or end the session: failure, error, ready, ended; snapshots and tracks read the live player and can stay ungated |

## Sources

- Files: `AGENTS.md`; `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt`, `PlayerVM.kt`, `SeekStallRecovery.kt`; `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerStaleCallbackTest.kt`, `PlaybackControllerHarness.kt`; `docs/testing/player-playback.md`; `gradle/libs.versions.toml`.
- Media3: `~/.gradle/caches/modules-2/files-2.1/androidx.media3/media3-exoplayer/1.11.0/.../media3-exoplayer-1.11.0.aar`, `ExoPlayerImpl.prepare`, `stop`, `stopInternal`, `handlePlaybackInfo`; `ExoPlayerImplInternal.prepareInternal`, `stopInternal` (javap).
- Commits: `fe6224fb` (upstream: `PlaybackCallbackGate.kt`, `PlaybackController.kt`, `PlaybackTransitions.kt`, `PlaybackControllerCallbackGenerationTest.kt`), `cfd88e55`, `0c01a3eb`, base `cec5f7fc`.
- Spec: branch `upstream-sync-triage`, `docs/superpowers/specs/2026-10-11-upstream-sync-triage-design.md` D24-D27, R7.
- Runs: `player-callback-gate`, `player-hermetic-fixtures` (`device-run-5.md`, `judge-1.md`), `playback-controller-split`, `player-ended-play-hardening`, `player-device-tests-hardening`, `player-corrupt-cache-recovery`, `media3-hagc-removal`, `itest-incomplete-run-green`.
