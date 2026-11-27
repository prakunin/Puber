# Research: player-device-tests-hardening

## 1. Request

From `state.md` (title and Log items 1-4):

> Tighten three player device tests that can pass without exercising their scenario
>
> (1) hlsDelayedAndInterruptedSegment_enterBufferingThenRecover opens the gate on the first BUFFERING, which prepare() reaches before the segment is requested (PlaybackControllerNetworkTracksTest.kt:250-257): wait for delayed-segment to be pending first; (2) hlsPreparation_exposesCodecTracksBeforeTheFirstMediaChunkCompletes gates only the 720p/english segments while the master also offers ungated 360p (:80-107): single-variant master and require the gated routes pending when tracks appear; (3) PlaybackControllerResourcesTest.kt:56 lost assertTrue(cache.getCachedSpans(url).isEmpty()) after removeResource; restore it and add a KDoc pointer to player-corrupt-cache-recovery on the test.
>
> item 4 from player-callback-gate triage-1 #2 (codex): switchStreamAfterQueuedFailure_reportsNoFailureForPreviousStream (PlaybackControllerStaleCallbackTest.kt:83-84) waits a fixed Thread.sleep(PARSE_WAIT_MS) for A's error to be queued; replace with a bounded wait on a playback-thread signal so a slow device cannot fail the probe precondition.

## 2. Understanding

Four edits to instrumented tests only (`app/src/androidTestInstrumentation`), no production code: two `PlaybackControllerNetworkTracksTest` cases must prove the gated request is actually pending before they act on it, one `PlaybackControllerResourcesTest` assertion comes back, and the stale-callback case swaps a fixed 500 ms sleep for a wait on a real signal. All four still apply on master 13432572; line numbers have moved. Item 4 has no ready-made playback-thread signal in the harness, so it is the only item with a design choice.

## 3. Current state in code

Paths below are relative to `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/` unless given in full.

| Item | Where on master | What the code does now | Still applies |
|---|---|---|---|
| 1 | `PlaybackControllerNetworkTracksTest.kt:322-338` | Gates `video_360_000.ts` (route `delayed-segment`, :327), prepares `quality-low.m3u8`, waits until `STATE_BUFFERING in stateEvents \|\| playbackState() == STATE_BUFFERING` (:331-334), then `gate.countDown()` (:336). `Player.prepare()` sets BUFFERING synchronously in the same main-thread turn as `harness.prepare` (`PlaybackControllerHarness.kt:203-221`, `PlaybackController.kt:467`), so the condition is true on the first poll, before any segment request. | yes, vacuous by construction |
| 2 | `PlaybackControllerNetworkTracksTest.kt:82-110` | Gates `video_720_000.ts` and `audio_english_000.ts` (:88-89) on `/media/hls/master.m3u8`, which lists 360p and 720p (`player-test-fixtures/src/main/assets/player-fixtures/hls/master.m3u8`). Proof is `activeRequestCount > 0` (:96, :99), which counts any request. The same file says ABR starts on the 360 variant without a cap (:121), so the 720p gate is likely never reached and 360p segments load ungated. | yes |
| 3 | `PlaybackControllerResourcesTest.kt:57` | `harness.cache.removeResource(url)` with no `getCachedSpans(url).isEmpty()` after it. A line comment at :53 already names `player-corrupt-cache-recovery`; there is no KDoc on the test (:36-37). The test name still says "Recovers" (hermetic triage-2 #4). | yes |
| 4 | `PlaybackControllerStaleCallbackTest.kt:87-113` | Inside one `onMain` block: `gate.countDown()`, `awaitOnMain { aOutcome() != null }` (server-side completion only), `Thread.sleep(PARSE_WAIT_MS)` (:99, 500 ms at :192), then `switchStream(B)`. Precondition `assertNotNull(probe.error)` with a diagnostic message (:107-111) makes a miss fail loudly, not pass. Not `@Ignore`d. | yes |

Helpers worth reusing:

- `HermeticRequestJournal`: `RequestEntry.routeId` and `outcome == null` mark a pending request; `matchedCount(routeId)` and `Snapshot.matchedRoutes` exist (`player-test-fixtures/src/main/kotlin/com/kino/puber/playertestfixtures/server/HermeticRequestJournal.kt:34-70,98-106`). `PlayerTestControl` exposes only `requestJournal` (snapshot) and `activeRequestCount` (`PlayerTestControl.kt:37-41`).
- `singleVariantMaster(playlist)` (`PlaybackControllerRoutes.kt:15-24`) and the `quality-low` route (`PlaybackControllerRoutes.kt:51-55`, 360p only) give a single-variant master for item 2 without new fixtures. Note: `singleVariantMaster` hardcodes `RESOLUTION=640x360` whatever playlist it names.
- `gatedSegment(id, filename, gate)` (`PlaybackControllerNetworkTracksTest.kt:425-434`).
- `awaitCacheWriteLockRelease` / `assertCacheOwnershipReleased` (`PlaybackControllerResourcesTest.kt:142-167`) already pair `removeResource` with `getCachedSpans(key).isEmpty()`.

Item 4 signal options found in code:

- The test owns the `OkHttpClient` the controller uses (`PlaybackControllerHarness.kt:149-154`, `server.newClient()`), so an OkHttp `EventListener` on that client is a test-only hook on the loader thread (body read end / call end). Whether the HLS playlist loader closes the stream after parsing, and so fires call end after the `ParserException`, is `unverified` (Media3 1.11.0 internals, not read).
- The load-error policy (`PlaybackErrorPolicy`, `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackErrorPolicy.kt:28-36`) is called for the error, on the playback thread (`unverified`), but it is built inside `PlaybackController` (`PlaybackController.kt:510,725,782`) with no injection point: using it needs a production seam.
- Player and analytics listeners are delivered on the main looper, which the test body blocks, so they cannot serve as the signal (`unverified` for `AnalyticsListener`; `Player.Listener` per Media3 threading model).

Recent commits on these files: `992665a9` (callback gate, added the stale-callback cases), `b314cd0f`, `8c948972` (2026-11-27).

## 4. Related work

| Run / doc | Relation |
|---|---|
| `player-hermetic-fixtures` (done) | Source of items 1-3: `triage-2.md` #1-#4, `review-codex-2.md`, `review-tests-2.md` (F1: `7e86a573` dropped the assertion, `73f3b5a4` restored only `removeResource`). |
| `player-callback-gate` (done) | Source of item 4: `triage-1.md` #2, `review-codex-1.md`. Its spec kept the test body unchanged (`docs/superpowers/specs/2026-11-27-player-callback-gate.md:40`, AC-1); that run is closed, so the constraint no longer binds. Device history: 7 green runs at ~1.85 s each, red only on the base and mutant builds (`itest-*.xml`). |
| `player-corrupt-cache-recovery` (idea) | Target of the KDoc pointer in item 3; owns the corrupt-entry outcome. |
| `docs/testing/player-playback.md:78,84,87` | Port-mapping rows for the three tests; row 87 already reads "outcome not asserted". |

No duplicate.

## 5. External findings

External findings: off (local research only).

## 6. Options

| Option | Item 4 approach | Items 1-3 | Trade-offs | Size |
|---|---|---|---|---|
| A (recommended) | Test-only: OkHttp `EventListener` on the harness client signals the end of A's response on the loader thread; then a bounded poll (no fixed sleep) until A's error is queued, with `PARSE_WAIT_MS` kept only as the upper bound. | Item 1: wait for a `delayed-segment` entry with `outcome == null` while BUFFERING, then open the gate. Item 2: `quality-low.m3u8` (single 360p variant), gate `video_360_000.ts` and `audio_english_000.ts`, require both routes pending when tracks appear. Item 3: restore the assertion, add KDoc. | No production change. The signal is closer to the parse than the server-side `aOutcome`, but the step from loader-thread parse to the main-looper post is not observable from the test, so a short bounded wait remains. | S |
| B | Production seam: optional constructor hook in `PlaybackController` (e.g. a load-error observer or injectable `LoadErrorHandlingPolicy`) called on the playback thread; the test waits on it. | Same as A. | Signal is on the playback thread, closer to the real event. Changes a production constructor and needs device verification of playback; a test seam in a production class. | M |
| C | Keep the sleep, raise `PARSE_WAIT_MS` and retry the scenario once when the precondition misses. | Same as A. | Smallest diff; still timing-based; a retry can hide a real regression in how errors are delivered. | S |

## 7. Risks

- Item 2: with chunkless preparation off, gating segment 0 of the only variant blocks preparation and the test times out instead of passing; that is the intended failure, but the default of `HlsMediaSource.Factory.setAllowChunklessPreparation` in Media3 1.11.0 is `unverified` here.
- Item 1: gating the only 360p segment keeps BUFFERING until the gate opens; if the player reaches BUFFERING only after the request starts, the new order of conditions must still accept both.
- Item 4 option A: the remaining bounded wait can still miss on a very slow device; the existing `assertNotNull(probe.error)` keeps that a loud failure.
- Device verification: instrumented tests only, on the emulator under a lease via `make ... itest TESTS=<class>` (brief). Each changed case should run repeatedly (the hermetic run used 3x) to show it is not flaky.

## 8. Open questions

| ID | Question | Recommended answer |
|---|---|---|
| Q1 | Item 4: accept a test-only signal with a bounded remainder (A), or add a production seam (B)? | A: no production code changes in a test-hardening run; the precondition assertion already makes a miss loud. |
| Q2 | Item 3: rename `emptyCache_loadsNormally_andCorruptOwnedEntryRecoversWithoutPoisoningNextScenario` now that the KDoc says recovery is not asserted? | No: keep the name for the upstream port mapping (`docs/testing/player-playback.md:87`, hermetic triage-2 #4); the KDoc carries the caveat. |

## 9. Sources

- `.flow/runs/player-device-tests-hardening/state.md`
- `.flow/runs/player-hermetic-fixtures/triage-2.md`, `review-codex-2.md`, `review-tests-2.md`, `itest-r6-*.xml`
- `.flow/runs/player-callback-gate/triage-1.md`, `review-codex-1.md`, `itest-*.xml`
- `.flow/runs/player-corrupt-cache-recovery/state.md`
- `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerNetworkTracksTest.kt`, `PlaybackControllerResourcesTest.kt`, `PlaybackControllerStaleCallbackTest.kt`, `PlaybackControllerHarness.kt`, `PlaybackControllerRoutes.kt`, `PlayerTestControl.kt`
- `player-test-fixtures/src/main/kotlin/com/kino/puber/playertestfixtures/server/HermeticRequestJournal.kt`, `player-test-fixtures/src/main/assets/player-fixtures/hls/master.m3u8`
- `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt`, `PlaybackErrorPolicy.kt`
- `gradle/libs.versions.toml:27` (media3 1.11.0)
- `docs/testing/player-playback.md`, `docs/superpowers/specs/2026-11-27-player-callback-gate.md`, `docs/superpowers/plans/2026-11-27-player-hermetic-fixtures.md`
- Commits `992665a9`, `b314cd0f`, `8c948972`, `cfd88e55`, `fe6224fb`
