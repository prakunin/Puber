# Brief: player-device-tests-hardening

Kind: improvement. Size: S. Tracks: none. Device verification: yes (the changed files are instrumented tests; each AC only shows on a device run). Production sources are not touched.
Research: research.md (sections 3, 6, 7); option A taken. Checked on master 13432572.

Paths below are relative to `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/`.

## Findings

| Item | Test | Why it can pass without its scenario | Where |
|---|---|---|---|
| 1 | `hlsDelayedAndInterruptedSegment_enterBufferingThenRecover` | BUFFERING is set by `prepare()` before any segment request, so the gate opens on the first poll (confirmed by reading) | `PlaybackControllerNetworkTracksTest.kt:330-336` |
| 2 | `hlsPreparation_exposesCodecTracksBeforeTheFirstMediaChunkCompletes` | `master.m3u8` also offers an ungated 360p variant where ABR starts (:121); proof is `activeRequestCount > 0`, which counts any request | `PlaybackControllerNetworkTracksTest.kt:82-110` |
| 3 | `emptyCache_loadsNormally_andCorruptOwnedEntryRecoversWithoutPoisoningNextScenario` | `getCachedSpans(url).isEmpty()` after the second `removeResource` was dropped; no KDoc on the test | `PlaybackControllerResourcesTest.kt:37,57` |
| 4 | `switchStreamAfterQueuedFailure_reportsNoFailureForPreviousStream` | Fixed `Thread.sleep(PARSE_WAIT_MS)` (500 ms) stands in for "A's error is queued"; a slow device misses the precondition (fails loudly at :107-111) | `PlaybackControllerStaleCallbackTest.kt:96-100,192` |

Chunkless preparation is on in production (`app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:781`), so gating segment 0 of the only variant must not block track exposure (research.md: 7, first risk, now resolved).

## Acceptance criteria

"Run on the emulator" means `make FLAVOR=instrumentation DEVICE=host.docker.internal:5555 GRADLE=./tools/agentw itest TESTS=<class>` under a lease.

- AC-1. Given item 1's test, when it runs on the emulator 3 times, then it passes each time, and in the code `gate.countDown()` is reached only after a journal entry with `routeId == "delayed-segment"` and `outcome == null` was observed together with BUFFERING.
- AC-2. Given item 2's test, when it runs on the emulator 3 times, then it passes each time; it prepares `/media/hls/quality-low.m3u8`, gates `video_360_000.ts` and `audio_english_000.ts`, and its tracks condition requires both gated routes to have a pending entry (`outcome == null`) at the moment video and audio tracks are seen.
- AC-3. Given item 2's test with the master path reverted to `/media/hls/master.m3u8` and the gates on the 720p segment (a local mutant, not committed), when it runs on the emulator, then it fails (timeout on the pending-gate condition). The result is recorded in evidence.
- AC-4. Given item 3's test, when it runs on the emulator, then it passes, `assertTrue(harness.cache.getCachedSpans(url).isEmpty())` follows the second `removeResource(url)`, and a KDoc on the test says the corrupt-entry outcome is not asserted and points to run `player-corrupt-cache-recovery`. The test name is unchanged.
- AC-5. Given item 4's test, when read, then it has no fixed `Thread.sleep` between `aOutcome() != null` and `switchStream(B)`; it waits, bounded, on a signal raised on the OkHttp loader thread when stream A's playlist response is closed, with `PARSE_WAIT_MS` used only as the upper bound of that wait.
- AC-6. Given item 4's test, when `PlaybackControllerStaleCallbackTest` runs on the emulator 5 times, then every case passes each time and `probe.error` is non-null in item 4 (precondition held).
- AC-7. Given the worktree, when `check.full` from `.flow/config.yaml` runs, then it passes.

## Risks

- Item 4: whether Media3 1.11.0's playlist loader closes the response body after the `ParserException` (so OkHttp fires `responseBodyEnd`/`callEnd`) is unverified. If no such event fires, fall back to `responseBodyEnd` or `responseHeadersEnd` plus a bounded poll, and record which event was used; never back to an unbounded or fixed sleep.
- Item 4: the step from the loader-thread parse to the main-looper post is not observable from the test, so a short bounded remainder stays; the existing `assertNotNull(probe.error)` keeps a miss loud.
- The EventListener must be scoped to stream A's URL so stream B or other harness traffic cannot raise the signal.

## Open questions

| ID | Question | Recommended | Blocks |
|---|---|---|---|
| Q1 | Item 4: test-only OkHttp signal with a bounded remainder (option A) or a production seam in `PlaybackController` (option B)? | A: no production change in a test-hardening run | no |
| Q2 | Rename item 3's test now that the KDoc says recovery is not asserted? | No: the name maps to upstream in `docs/testing/player-playback.md:87` | no |

## Plan

1. `PlaybackControllerNetworkTracksTest.kt:330-334`: replace the BUFFERING-only condition with one that also requires `harness.server.requestJournal.entries.any { it.routeId == "delayed-segment" && it.outcome == null }`; keep accepting BUFFERING from `stateEvents` or `playbackState()`. Add a private helper `pending(routeId)` next to `gatedSegment` (:425) for reuse in step 2.
2. `PlaybackControllerNetworkTracksTest.kt:82-110`: gate `video_360_000.ts` and `audio_english_000.ts` (route ids kept), prepare `/media/hls/quality-low.m3u8` (route at `PlaybackControllerRoutes.kt:51-55`, `singleVariantMaster` :15-24). Replace both `activeRequestCount > 0` checks with `pending("chunkless-video-gate") && pending("chunkless-audio-gate")`. Run the AC-3 mutant locally and discard it.
3. `PlaybackControllerResourcesTest.kt:36-57`: add the KDoc (move the :53 line comment's content into it), add `assertTrue(harness.cache.getCachedSpans(url).isEmpty())` after :57.
4. `PlayerTestControl.kt:80` `newClient()`: accept an optional `EventListener` (default none); `PlaybackControllerHarness.kt:149-154` passes a harness-owned listener that records closed calls by URL path and exposes a `CountDownLatch`/await per path. `PlaybackControllerStaleCallbackTest.kt:96-100`: replace `Thread.sleep(PARSE_WAIT_MS)` with a bounded await (max `PARSE_WAIT_MS`) on the signal for `STREAM_A_PATH`, failing with `harness.diagnostics()` when it times out. Keep the comment at :94-95 accurate.
5. Device: run `PlaybackControllerNetworkTracksTest` 3x, `PlaybackControllerResourcesTest` 1x, `PlaybackControllerStaleCallbackTest` 5x on the emulator; store the result XMLs in evidence.
