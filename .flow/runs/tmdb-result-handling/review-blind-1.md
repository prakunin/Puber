LENS: blind  ROUND: 1  BASE: e569e055
### F1 [minor] Lookups now turn JVM `Error`s into ordinary failures, and a test locks that in
- Where: app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt:40,44,53,63 (via `runCatchingCancellable`, which catches `Throwable`); app/src/test/kotlin/com/kino/puber/data/api/TmdbApiClientTest.kt `findLookups_wrapErrorsIntoFailure`
- Problem: The removed `apiResult` caught only `Exception`. `runCatchingCancellable` catches `Throwable`, so `OutOfMemoryError`, `StackOverflowError` and `NoClassDefFoundError` now come back as `Result.failure` from all four TMDB methods. The diff does not mention this change. The new test asserts that a `NoClassDefFoundError` is wrapped, which makes that behaviour part of the contract.
- Failure scenario: A broken class path, or an OOM while decoding a large TMDB body, inside `findByImdbId`. `SkipSegmentService.resolveTmdbId` calls `.getOrNull()` on the result, so the fatal error is dropped without any log and the app continues as if TMDB had simply had no match. Before this diff the error reached the coroutine exception handler. `EpisodeScheduleRepository` is unaffected because it calls `getOrThrow()`.
- Fix: Keep catching `Exception` in the TMDB client: either keep a local helper, or use a `runCatchingCancellable` variant that rethrows `Error`. Then change `findLookups_wrapErrorsIntoFailure` to throw an `IOException`, so it checks that I/O errors are wrapped rather than that fatal errors are.

### F2 [nit] Cancellation test does not cover anything the change affects
- Where: app/src/test/kotlin/com/kino/puber/data/api/TmdbApiClientTest.kt `findByImdbId_rethrowsCancellation`
- Problem: The old `apiResult` already rethrew `CancellationException`, so this test passes on the base commit as well. It also only covers `findByImdbId`. It is not proof that the refactor changed anything for cancellation.
- Failure scenario: None at runtime. Someone reading the suite could think this test guards new behaviour, but it would not have failed before the change either.
- Fix: Keep it as a regression guard, or run the same check over all four public methods so it covers the shared helper and not just one call site.

I checked these and dropped them:
- `findByImdbId` now fails on non-2xx instead of returning `success(null)`. Its only caller, `SkipSegmentService.resolveTmdbId`, uses `getOrNull()`, so the result is the same and nothing is cached.
- `findTvByImdbId` failures are now thrown through `getOrThrow` inside `cache.getOrPut`, so they are not cached as `NoMatch`. That looks intended.
- No imports were left unused: `isSuccess` is still used by `requireSuccess`.
- The `find()` helper sends the same request as the two blocks it replaced (path, `external_source` parameter and prefix handling).
