# Research: player-corrupt-cache-recovery

## Request

"A corrupt cached media container fails playback with Source error instead of recovering from the network" (state.md title). Log: "created (stage idea) from player-hermetic-fixtures: device-run-5.md: corrupt cache entry reaches READY then ExoPlaybackException Source error; FLAG_IGNORE_CACHE_ON_ERROR (PlaybackController.kt:643) covers IO errors only. Whether this matters in real use (cache corruption frequency) is unproven; triage should decide. Test: PlaybackControllerResourcesTest corrupt-entry case, outcome currently not asserted."

## Understanding

When the media cache holds bytes for a stream URL that are not a valid container, the main player reads them and the extractor or playlist parser fails. Media3 does not fall back to the network for that URL, because the cache read itself succeeded. The controller reports `StreamFailure(NEXT_URL)` and the VM moves on to the next candidate URL. The question is whether the player should instead drop the bad entry and reload the same URL from the network, and whether that is worth a production change given how such an entry can get into the cache.

## Current state in code

- `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:672-676`: one `CacheDataSource.Factory` per player, `FLAG_IGNORE_CACHE_ON_ERROR` (now line 675; state.md's `:643` is stale), default cache key factory (no `setCacheKeyFactory` anywhere in `app/src`), so the key is the full URL including its query string.
- `PlaybackController.kt:443` main player: `createDataSourceFactory(writeToCache = false)`; `CacheWriteSink.kt:11-12` returns no sink, so the main player only reads.
- `PlaybackController.kt:702-754` `warmUpNext`: the only writer. It caches the next episode's first candidate (`PlayerVM.kt:1676-1701`, Auto quality, so normally `hls4` HLS) until READY or ENDED. On error (`:739-745`) it releases the warm-up player and leaves whatever it wrote in the cache.
- `PlaybackController.kt:264-288` `onPlayerError` → `streamRecovery()` `:357-397`: `ParserException` (`:367-370`, comment: an expired signed link can return a 200 HTML/JSON page) → `NEXT_URL`. Nothing evicts cache spans on any error.
- `PlaybackCallbackGate.kt:45-57` `routeError`: re-prepares once only for a stale error right after a switch; otherwise reports.
- `PlayerVM.kt:268-281` → `switchToNextStreamCandidate` `:471-478`; when candidates run out, `refreshStreamLinksOnce` `:480-487` refetches item details, then shows `PlayerViewState.Error`.
- `PlayerInteractor.kt:236-243` Auto candidates: `hls4`, `hls`, `http`, deduplicated by URL (`:233`), so each candidate has its own cache key.
- `PlaybackErrorPolicy.kt:32,46`: no load retry and no fallback for `ParserException`.
- `data/di/modules.kt:142-149` `SimpleCache` in `externalCacheDir/media_cache`, LRU evictor at 256 MiB (`MediaCacheTrim.kt:11`). The evictor only runs on writes and the startup trim (`MediaCacheTrim.kt:36-40`) only cuts above the cap, so a bad span stays until warm-up writes push it out.
- Test: `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerResourcesTest.kt:42-75`. It writes a 21-byte span at offset 0 (`:135-147`) and waits for `sawReady || error` (`:59`), then calls `removeResource` itself (`:62`). KDoc `:37-40` points to this run. `docs/testing/player-playback.md:87` marks the case "ported, outcome not asserted".

### Media3 1.11.0 behaviour (bytecode of `media3-datasource-1.11.0.aar` and `media3-exoplayer-1.11.0.aar` in `~/.gradle/caches/modules-2`, read with `javap -c`)

| Fact | Where in the bytecode |
|------|-----------------------|
| `seenCacheError` is set only when a `Throwable` leaves `open`/`read` of the `CacheDataSource` itself while it reads from cache, or when the throwable is a `Cache.CacheException` | `CacheDataSource.handleBeforeThrow` |
| `FLAG_IGNORE_CACHE_ON_ERROR` (value 2) bypasses the cache only after `seenCacheError`, and only in the same `CacheDataSource` instance | `CacheDataSource.shouldIgnoreCacheForRequest` |
| Garbage bytes read fine, and the parse error is thrown by the extractor above the data source, so the flag never fires on a corrupt container | follows from the two rows above |
| `UnrecognizedInputFormatException extends ParserException`, so the fork classifies it as `NEXT_URL` | `androidx.media3.exoplayer.source.UnrecognizedInputFormatException` |
| `CacheDataSink` commits the bytes written so far when it closes, including when a load is cancelled or fails after the body arrived | `CacheDataSink.closeCurrentOutputStream` → `cache.commitFile` |
| `SimpleCache.removeResource(key)` removes every span of the key and is `synchronized` (blocking disk work) | `SimpleCache.removeResource` |

Why the test reaches READY before it fails is `unverified`. A likely cause: only bytes 0-20 come from the cache, the rest comes from upstream, and the failure shows up later in the stream.

### How a bad entry gets into the cache in real use

| Path | Possible | Basis |
|------|----------|-------|
| Warm-up gets a 200 HTML/JSON page instead of media (expired signed link, captive portal) | yes | `OkHttpDataSource` has no content-type predicate set (`PlaybackController.kt:670-671`); the sink writes any 200 body and commits it on close |
| Warm-up gets a 4xx/5xx | no | the upstream open throws before the sink opens |
| Truncated warm-up download | not corrupt | a partial span is valid, and the rest comes from upstream |
| On-disk corruption | possible, rare (`unverified`) | `SimpleCache` keeps no checksum |
| Main playback | no | read-only since b161b061 |

Impact in the app: the poisoned candidate fails with `NEXT_URL` and the VM switches to the next candidate (a different key), so playback recovers, possibly on `hls` instead of `hls4`. A full Error screen needs every candidate poisoned, or the URLs after `refreshStreamLinksOnce` to be identical to the poisoned one. Whether KinoPub stream URLs are stable across details fetches is `unverified` and must not be settled by probing the API.

## Related work

| Item | Relation |
|------|----------|
| run `player-hermetic-fixtures` (device-run-5.md, triage-1.md #9, review-tests-1.md F2, review-tests-2.md, judge-1.md) | source of this run; softened the assertion in 7e86a573, 73f3b5a4, af280467 |
| upstream 1d3ec39e `PlaybackControllerResourcesTest.kt:182-192` | upstream asserts `corrupt.ready.count == 0L \|\| corrupt.error.get() != null`, message "controlled recovery or error". Upstream accepts an error and never proves recovery; the test name promises more than its assertion |
| run `cache-storage-bounds` (done), commit b161b061 | made the main player read-only and warm-up the only writer: the owner decision this run must keep |
| run `cache-layer-rework` (idea) | "video: warm-up only, main player read-only cache"; no corrupt-entry item |
| run `perf-deferred` (idea) | lists P3 next-episode warm-up as deferred |
| run `player-callback-gate` (done) | owns `PlaybackCallbackGate.routeError`, the natural hook for a one-time re-prepare |

Duplicate: none.

## External findings

External findings: off (local research only). The Media3 behaviour above was read from the 1.11.0 artifacts in the Gradle cache.

## Options

| | Option | Size | Pros | Cons |
|---|--------|------|------|------|
| A | **Assert the current controlled outcome; no production change.** Change the corrupt step to require `StreamFailure(NEXT_URL)` for that URL and no READY-without-error, as upstream's intent requires. Rename the KDoc and the doc row to "fails over to the next candidate". Optionally add a VM unit test that `NEXT_URL` advances the candidate. | S | Matches upstream; documents real behaviour; no risk to the read-only cache decision | A bad warm-up entry still costs one failover per playback until it is evicted |
| B | **Do not commit bad warm-up bytes.** On a warm-up `onPlayerError` caused by `ParserException`, remove the keys the warm-up wrote (the stream URL for progressive; for HLS, the playlist and segment keys recorded through a tracking `DataSink.Factory` or a `CacheDataSource.EventListener`), off the main thread. Optionally reject non-media content types at the sink. | M | Closes the only realistic way in; the main player stays read-only | HLS key tracking adds code; does not help with disk corruption or entries already on devices; needs a new instrumented case |
| C | **Evict and retry once in the main player.** On a `ParserException` for the current session, `removeResource` for the current URL (and the HLS keys read from cache), then re-prepare the same URL once before reporting `NEXT_URL`. | M | Recovers from any source of corruption on the same URL; makes the test name true | The main player starts deleting from the shared cache (touches the cache-storage-bounds decision); a real parser failure (expired link page from the network) costs a second load before failover; HLS keys are not the stream URL; retry state in the gate |

Recommended: **A**, plus B if the owner wants the warm-up hole closed. Real corruption needs a 200 non-media body during warm-up, the VM already fails over to a URL with a different key, and upstream asserts only a controlled error.

## Risks

- Option C: removing spans while the warm-up player holds a write lock on the same key. `removeResource` does not check locks (`unverified` for in-flight writes). And `SimpleCache` is `synchronized`, so calling it on the main thread would stall playback.
- Option A: if the corrupt span turns out to make the extractor return READY with no error on some device or fixture, the stricter assertion makes the test flaky. Device run 5 saw an error in both failing runs.
- Any option: changing the test changes the AC wording that `docs/testing/player-playback.md:87` and the player-hermetic-fixtures evidence refer to.

## Open questions

| ID | Question | Recommended answer |
|----|----------|--------------------|
| Q1 | Is a production change wanted, or is a controlled failover to the next candidate the accepted outcome? | Accept the failover (option A); recovery on the same URL is not required |
| Q2 | If yes to a change: fix the writer (B) or the reader (C)? | B: keep the main player read-only, as decided in cache-storage-bounds |

## Sources

- Files: `PlaybackController.kt`, `CacheWriteSink.kt`, `PlaybackCallbackGate.kt`, `PlaybackErrorPolicy.kt`, `PlayerVM.kt`, `domain/interactor/player/PlayerInteractor.kt`, `data/di/modules.kt`, `data/cache/MediaCacheTrim.kt`, `PlaybackControllerResourcesTest.kt`, `docs/testing/player-playback.md`, `docs/superpowers/evidence/2026-11-27-player-hermetic-fixtures-device.md`, `gradle/libs.versions.toml:27` (media3 1.11.0).
- Library: `~/.gradle/caches/modules-2/files-2.1/androidx.media3/media3-datasource/1.11.0/f45b0955.../media3-datasource-1.11.0.aar`, `.../media3-exoplayer/1.11.0/5209960a.../media3-exoplayer-1.11.0.aar` (`javap -c`: CacheDataSource, CacheDataSink, SimpleCache, UnrecognizedInputFormatException).
- Commits: b161b061, 1d3ec39e, cfd88e55, af280467, 7e86a573, 73f3b5a4.
- Runs: player-hermetic-fixtures, cache-storage-bounds, cache-layer-rework, perf-deferred, player-callback-gate.
