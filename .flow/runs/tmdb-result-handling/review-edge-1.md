LENS: edge  ROUND: 1  BASE: e569e055
No findings.

I found no unhandled boundaries in the diff. These are the boundaries I checked and why each one holds:

- **Cancellation:** `runCatchingCancellable` (/home/user1/orca/workspaces/Puber/tmdb-result-handling/app/src/main/java/com/kino/puber/core/coroutine/) rethrows `CancellationException`, just as the removed `apiResult` did. It uses `kotlin.coroutines.cancellation.CancellationException`, which is the same type as the kotlinx alias. The OkHttp engine reports a timeout as an `IOException`, not as a cancellation, so a timeout comes back as a failure.
- **Throwable vs Exception:** the new helper also catches `Error`. In `EpisodeScheduleRepository`, `getOrThrow()` rethrows it, so behaviour is the same as before. In `SkipSegmentService`, `getOrNull()` now swallows an `Error` (for example an out-of-memory error) and treats it as a TMDB miss. The test `findLookups_wrapErrorsIntoFailure` shows this is intended, and those errors are not realistically reachable from a single small JSON decode. I'm not reporting it.
- **Callers handling the new `findByImdbId` failure:** the only caller is `SkipSegmentService.resolveTmdbId` (line 62). It calls `.getOrNull()`, so a failure becomes null. Then:
  - nothing is saved to the cache;
  - the TheIntroDB step is skipped;
  - IntroDB.app is still asked.

  This matches the old non-2xx `success(null)` path exactly, and `skipsPrimaryAndAsksFallback_whenTmdbLookupFails` covers it. A bad token causes repeated 401s on every call, but that also happened before this change.
- **`findTvByImdbId`:** it already required a 2xx before the change, so moving it onto the shared `find()` changes nothing for `EpisodeScheduleRepository`. There, `getOrThrow()` propagates the failure out of `cache.getOrPut`, so a failure is not cached.
- **Empty or null results:** `tv_results` or `movie_results` that are missing, null or empty fall through `?.firstOrNull()?.id` to null, which counts as a miss. Tests cover the empty-arrays case.
- **Decoding failures:** they come back as a failure in both lookups. Neither caller treats that differently from a non-2xx response.
- **Bad IMDb id:** an empty or malformed id goes through `withTmdbPrefix`, which this diff did not touch.
