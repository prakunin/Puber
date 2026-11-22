LENS: edge  ROUND: 3  BASE: a22c03bc
No findings.

What the round 3 diff changes:
- **Constant moved:** `MEDIA_CACHE_SIZE_BYTES` now lives in `MediaCacheTrim.kt`, and `modules.kt` uses it in two places: the cache's least-recently-used evictor (line 123) and the cache trim, through the default value of `mediaCacheTrimAction`. Both still get the same 256 MiB value.
- **Lazy opening kept:** `mediaCacheTrimAction` reads `Lazy.value` only when the sweeper runs the trim, as the old inline lambda did, so the cache is still not opened at DI time.
- **No name clash:** the test file declares its own `private const val MIB`, which is private to that file and does not collide with the one in `MediaCacheTrim.kt`.
- **Annotations:** the `@OptIn` annotations change no runtime behaviour.
- **Collapsed `if`:** the merged `if (!writeToCache && BuildConfig.DEBUG)` in `PlaybackController.kt` means the same as the two nested checks it replaces.
- **Trim edge cases:** a cache that fits, an empty cache and an exact fit are all handled by the early return `remaining <= capBytes` in `spansToEvict`, which this round did not change.

### Unproven
- `/home/user1/orca/workspaces/Puber/cache-storage-bounds/app/src/main/java/com/kino/puber/data/di/modules.kt:114`: no test fails if the DI wiring stops passing `mediaCacheTrimAction(mediaCache)` to the sweeper. The new test covers the function itself, not its wiring. This becomes a finding only with evidence that the wiring has been or could be changed by mistake; it is closer to the tests lens than edge.
