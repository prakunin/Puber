LENS: tests  ROUND: 2  BASE: a22c03bc

Incremental range dfad8b7c..a8fdd8d1. I ran the unit tests this diff touches in the worktree with `./tools/agentw :app:testDevDebugUnitTest`. All passed: `CachedPayloadSweeperTest` 11/11, `MediaCacheTrimTest` 3/3, `CacheWriteSinkTest` 2/2, and `PlayerVMNextEpisodePrefetchTest` 3/3 as a guard.

### F1 [major] The media cache trim and its DI wiring have no test that can fail, and the device evidence was recorded before they existed
- Where: app/src/main/java/com/kino/puber/data/cache/MediaCacheTrim.kt:27-30, app/src/main/java/com/kino/puber/data/di/modules.kt:111-118
- Problem: `MediaCacheTrimTest` covers only the pure `spansToEvict` function. Nothing tests `trimMediaCache(cache, capBytes)`, which collects `cache.keys` and `getCachedSpans` and calls `removeSpan`. Nothing tests that `modules.kt` passes the trim lambda with `MEDIA_CACHE_SIZE_BYTES` to the sweeper either. `CachedPayloadSweeperTest` injects its own lambda, and the constructor default is `{}`. The device evidence (docs/superpowers/evidence/2026-11-21-cache-storage-bounds-device.md, last committed in dfad8b7c) was recorded on a build that predates commit 9c5c5db9. So the new startup and persistence path, which runs `removeSpan` on a live `SimpleCache`, has neither a unit test nor a device run. AGENTS.md requires device verification for changes to startup and persistence.
- Failure scenario: someone deletes the `trimMediaCache = ...` argument in `modules.kt`, or passes the wrong cap, or `trimMediaCache` stops calling `removeSpan` for some key. The trim then silently does nothing or evicts the wrong amount, and the whole suite stays green. A runtime fault, such as `removeSpan` hitting a span that a player is reading, would be caught by `runCatchingCancellable` and only logged, so no current evidence would show it.
- Fix: add a unit test for `trimMediaCache` with a mocked `Cache`: two keys whose spans total more than the cap, then verify that `removeSpan` is called for exactly the least recently touched spans and for nothing when the cache fits. Then re-run the AC-3 device row (M3) on an APK that contains 9c5c5db9, with a cold start, a wait past the startup delay, and `du` before any warm-up, and record it in the evidence file.

### F2 [minor] `CacheWriteSinkTest` tests the helper, not how `PlaybackController` calls it
- Where: app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/CacheWriteSinkTest.kt:13-21; call sites PlaybackController.kt:416 (`writeToCache = false`) and the `warmUpNext` call (`writeToCache = true`)
- Problem: the test checks that `cacheWriteSink(cache, false)` returns null, which mirrors the one-line implementation. Design risk D2 is the main player factory still writing. No test fails if `prepare` passes `writeToCache = true`, or if `warmUpNext` passes `false`. Device rows M1 and M2 cover AC-1 and AC-2, so no AC is left untested, but the unit test adds almost no protection against the regression it is named after.
- Fix: either assert the flag through the controller's data source creation, for example with a seam that records the `writeToCache` value each player gets, or rename the test so it does not imply it guards AC-1.

### F3 [minor] `theMediaCacheIsTrimmedOnceAcrossSweeps` sees one fewer ON_STOP than it appears to, and never checks that sweeps ran
- Where: app/src/test/kotlin/com/kino/puber/data/cache/CachedPayloadSweeperTest.kt:81-93
- Problem: `onEnteredForeground()` and `onLeftForeground()` are called back to back with no `runCurrent()` between them. `AppForegroundState` is a `MutableStateFlow`, so true then false collapses into no change and the third sweep never happens. The test passes because the startup sweep and the first ON_STOP still make two sweeps. It also never asserts the sweep count, so it relies on other tests to prove that more than one sweep happened.
- Fix: add `runCurrent()` between lines 88 and 89, as `eachOnStopRunsOneMoreSweep` does, and assert the store's sweep count (3) next to `trims == 1`.

### Unproven
- plan:182: the new tests are recorded with "no red run on base". The test design does not list them as P0, so the red-run rule does not apply. It would become a finding only if the conductor counts `trimMediaCache` as part of AC-3's P0.
