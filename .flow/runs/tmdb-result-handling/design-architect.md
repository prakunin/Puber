# Design: tmdb-result-handling

Scope fixed by the owner (state.md Decisions): option B. Swap `TmdbApiClient`'s
private `apiResult` for the shared `runCatchingCancellable`, and route both
`find/{imdb_id}` lookups through one private `find(imdbId)` that fails on any
non-2xx. No logging anywhere; the privacy guard tests stay unmodified. All
citations below were read in the worktree at base `e569e055`.

## Decisions

| Question | Decision | Why |
|----------|----------|-----|
| Which wrapper wraps the four public methods? | `runCatchingCancellable` from `app/src/main/java/com/kino/puber/core/coroutine/CoroutinesErrorHandler.kt:37`, as an expression body (`= runCatchingCancellable { ... }`) on each method | The helper is what the sibling clients already use (`TheIntroDbApiClient.kt:43`, `IntroDbAppApiClient.kt:43`). It differs from `apiResult` (`TmdbApiClient.kt:70-76`) only in catching `Throwable`, not `Exception`. |
| Where does the shared request live? | `private suspend fun find(imdbId: String): TmdbFindResponse` in `TmdbApiClient`: GET `"${BASE_URL}find/${imdbId.withTmdbPrefix()}"` with `parameter("external_source", "imdb_id")`, then `requireSuccess()`, then `body()`. It throws and is not wrapped. | One request build and one failure policy (AC-7). Wrapping happens once, in each public method, so no `Result` sits inside another `Result` (`docs/architecture/api.md:66-67`). |
| What policy for non-2xx on `findByImdbId`? | `TmdbApiException(status.value)` through the existing `requireSuccess()` (`TmdbApiClient.kt:81-85`), so the result is `Result.failure`. This replaces the `success(null)` at `:63-65`. | Owner decision Q2. It matches `findTvByImdbId` (`:40`) and the tv endpoints (`:46`, `:55`). |
| How do the two lookups differ? | Only in what they project from `TmdbFindResponse`. `findTvByImdbId` takes `tvResults?.firstOrNull()?.id`. `findByImdbId` takes the TV id first, then `movieResults?.firstOrNull()?.id`. | AC-6 keeps today's projections from `:41` and `:67`. |
| Are `getTvDetails` and `getTvSeasonDetails` touched? | Only the wrapper changes (`apiResult` becomes `runCatchingCancellable`). Bodies stay byte-identical. | AC-1 applies to every public suspend method. Nothing else is in scope. |
| What happens to the `CancellationException` import? | Delete `import kotlinx.coroutines.CancellationException` (`TmdbApiClient.kt:19`). Add `import com.kino.puber.core.coroutine.runCatchingCancellable`. | AC-1. The helper itself catches `kotlin.coroutines.cancellation.CancellationException` (`CoroutinesErrorHandler.kt:7`), and on the JVM that is the same class. |
| Does `SkipSegmentService` change? | No production change. `resolveTmdbId` (`SkipSegmentService.kt:56-65`) keeps `.getOrNull()` and saves only a hit. | Owner decision Q1 (no logging). A failure collapses to null, as a 502 does today, so the player outcome is unchanged. |
| Is the AC-8 service test optional? | No, include it. It costs one test method in the existing `SkipSegmentServiceTest` (mockk setup at `:22-47`). Stub `tmdbIdRepository.getTmdbId(IMDB_ID)` to return null and `tmdbApiClient.findByImdbId(IMDB_ID)` to return `Result.failure(TmdbApiException(429))`. Stub `introDbAppClient.getSegments(IMDB_ID, 1, 1)` to return the fallback. Assert that the result equals the fallback, `introDbClient.getSegments` runs 0 times and `tmdbIdRepository.saveTmdbId` runs 0 times. | The setup is cheap, so the brief's "costly" escape clause does not apply. It is the only test that pins the player-side outcome of the contract change. |
| Is there a KDoc on the find methods? | Yes, one line each, stating the contract: "null = TMDB has no match; failure = the request failed (non-2xx, network, decoding)". | The contract is the point of the change. Without a log, it is the only place a reader learns that a failure is not a miss. |
| Is `docs/architecture/api.md` edited? | Yes, one sentence after `:7-8`: the three small clients wrap their calls in `runCatchingCancellable`; TMDB fails on any non-2xx, while the IntroDB clients treat a non-2xx as an empty answer. | After the change this holds for all three clients (`TheIntroDbApiClient.kt:43,49-56`, `IntroDbAppApiClient.kt:43,50-52`). The doc otherwise describes only KinoPub's `apiCall` failure policy (`api.md:33-42`), so the TMDB policy would be undocumented. |

## Changes by layer

### API / repository

`app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt`:
- `:19`: remove the `kotlinx.coroutines.CancellationException` import. Add the `com.kino.puber.core.coroutine.runCatchingCancellable` import, keeping the import order the file already uses.
- `:36-42` `findTvByImdbId`: becomes `= runCatchingCancellable { find(imdbId).tvResults?.firstOrNull()?.id }`.
- `:44-48` `getTvDetails` and `:50-57` `getTvSeasonDetails`: `apiResult` becomes `runCatchingCancellable`. The bodies do not change.
- `:59-68` `findByImdbId`: becomes `= runCatchingCancellable { find(imdbId).let { it.tvResults?.firstOrNull()?.id ?: it.movieResults?.firstOrNull()?.id } }`. The `isSuccess()` early return is removed.
- `:70-76` `apiResult`: deleted. The new private `find(imdbId)` takes its place, between the public methods and `withTmdbPrefix` (`:78`).
- `requireSuccess` (`:81-85`), `withTmdbPrefix` (`:78-79`), `forTesting` (`:90`), `createHttpClient` (`:92-107`) and `TmdbApiException` (`:111`): unchanged.
- Public signatures are unchanged (`Result<Int?>`, `Result<TmdbTvDetailsResponse>`, `Result<TmdbSeasonDetailsResponse>`). Callers compile without edits: `EpisodeScheduleRepository.kt:55,65,72` (`getOrThrow`), `SkipSegmentService.kt:62` (`getOrNull`). DI is unchanged (`data/di/modules.kt:72`).
- `isConfigured` (`:33-34`) is unchanged.

Contract change, the only behaviour change visible to callers:

| Call | Before | After |
|------|--------|-------|
| `findByImdbId`, non-2xx | `success(null)` | `failure(TmdbApiException(code))` |
| any method, engine throws `Error` | `Error` escapes | `failure(error)` |
| any method, `CancellationException` | rethrown | rethrown |

`app/src/main/java/com/kino/puber/data/repository/SkipSegmentService.kt`, `EpisodeScheduleRepository.kt`: no change.

### ViewModel state and actions

No change. `EpisodeScheduleVM.kt:43-53` already catches `Throwable` and maps it through `mapper.mapError`. An `Error` that used to escape `apiResult` now arrives as a failed `Result`, `getOrThrow()` rethrows it in `EpisodeScheduleRepository`, and the same catch maps it to the screen's error state.

### Screen and DI module, Compose content, resources

No change.

### Tests

`app/src/test/kotlin/com/kino/puber/data/api/TmdbApiClientTest.kt` (MockEngine helpers `client` `:151-166`, `respondJson` `:168-178`):
- Rewrite `:113-132` `existingFindByImdbId_preservesTvFirstMovieFallbackAndNonSuccessBehavior` as `findByImdbId_prefersTvThenMovieAndFailsOnHttpError`. The first two assertions (`:129-130`) stay. The third (`:131`) becomes: failure, with `TmdbApiException.statusCode == 502` (AC-5, AC-6).
- New `findTvByImdbId_returnsFailureForHttpErrors`: 429 and 502, each giving `TmdbApiException` with that code (AC-4).
- New `findByImdbId_rethrowsCancellation`: copies `scheduleEndpoint_rethrowsCancellation` (`:134-149`) against `findByImdbId` (AC-2).
- New `findLookups_wrapErrorsIntoFailure`: the engine handler throws `NoClassDefFoundError("x")`. Both `findTvByImdbId` and `findByImdbId` return a failure whose exception is that instance, or has it as cause (see Risk 2) (AC-3).
- The tests at `:25`, `:37`, `:46`, `:101` and `:134` stay as they are (AC-6, AC-2).

`app/src/test/kotlin/com/kino/puber/data/repository/SkipSegmentServiceTest.kt`:
- New `skipsPrimaryAndAsksFallback_whenTmdbLookupFails`, as described under Decisions (AC-8). Use `every` for the non-suspend `TmdbIdRepository.getTmdbId` and `saveTmdbId` (`TmdbIdRepository.kt:10,15`). The existing `coEvery` at `:35` also works on non-suspend functions, so mirror it to stay consistent.

Unmodified guards that must stay green (AC-9): `SkipSegmentPrivacyTest.kt` and `EpisodeSchedulePrivacyTest.kt`. The new code adds no `log(`, `Timber.`, `println(` or `bodyAsText`. `SkipSegmentPrivacyTest.kt:42` mocks `findByImdbId` as `success`, so the change does not affect it.

### Docs

`docs/architecture/api.md:7-8`: add one sentence, as described under Decisions. No other architecture doc mentions TMDB error handling.

## Alternatives rejected

- Option A (swap only): leaves the split failure policy, which is half of SOLID-13; the owner chose B.
- Option C (log the failure in `SkipSegmentService`): breaks `SkipSegmentPrivacyTest` and risks logging IMDb ids, because the sanitiser has no TMDB rule; the owner chose no logging.
- A public `find` returning `Result<TmdbFindResponse>`, with callers projecting: it pushes TMDB DTO shapes into the repositories and adds a third public method for no AC.
- A sealed `TmdbLookup { Match, NoMatch, Failed }` instead of `Result<Int?>`: it changes caller signatures and tests that the task did not ask for. `Result` with nullable success already separates a miss from a failure.
- Keeping `apiResult` but catching `Throwable`: the shared helper still gets duplicated, which is the finding itself.
- Moving `requireSuccess` into the helper, or into a Ktor `HttpResponseValidator`: it widens the change to the HTTP client config and to `getTvDetails`/`getTvSeasonDetails` error types.

## Risks (highest first)

1. **Correctness / contract (`TmdbApiClient.kt:59-68`, consumer `SkipSegmentService.kt:62`).** `findByImdbId` now fails on non-2xx. The player outcome stays the same only because `resolveTmdbId` uses `getOrNull()`. A future caller that uses `getOrThrow()` would surface a 401 or 429 as an error. That is intended, and the KDoc and AC-8 test pin it. The pinned test at `TmdbApiClientTest.kt:114` is rewritten deliberately; the review should treat that as a contract change, not as a weakened test.
2. **Test fidelity for AC-3 (`TmdbApiClientTest.kt:155`, MockEngine).** It is not verified here whether Ktor 3.5.2 passes an `Error` thrown inside a `MockEngine` handler through unchanged or wraps it. The test should assert that the failure's exception is the thrown instance, or has it as its cause (walk `cause`), not just its class. If Ktor lets the `Error` escape the client pipeline in some other way, the test will show it red, and that is the point of writing it first.
3. **Lifecycle / error semantics (`CoroutinesErrorHandler.kt:37-45`).** Fatal `Error`s (OOM, `StackOverflowError`, `NoClassDefFoundError`) are now wrapped into `Result` for TMDB calls, as they already are for the other 12 helper callers. On the schedule screen the result is an error state rather than a crash (`EpisodeScheduleVM.kt:43-53` already caught `Throwable`). In the player the result is "no TheIntroDB segments". This hides a fatal error in exchange for consistency, and it was accepted with option B.
4. **Data shape / caching (`SkipSegmentRepository.kt:19-27`).** If TMDB fails and IntroDB.app is also empty, the empty merged list is cached for 1 h. This is unchanged by the design (a 502 gave the same null before), but it matters for the device smoke: a series probed recently may show no segments for an hour.
5. **Privacy (`SkipSegmentPrivacyTest.kt:68-84,146-156`, `EpisodeSchedulePrivacyTest.kt:12-23,36-43`).** Any `log(`/`Timber.` added to `TmdbApiClient.kt` fails the guards. The design adds none, and the guards must pass unmodified.
6. **Rollout / verification.** A worktree APK has an empty `TMDB_READ_ACCESS_TOKEN`. On device, `findByImdbId` therefore gets 401, which is now a failed `Result` rather than `success(null)`, with the same downstream outcome. The schedule screen stops at `MissingCredentials` (`EpisodeScheduleRepository.kt:40`) before any TMDB call. The live TMDB success path is covered only by MockEngine tests. The owner accepted this limitation (Q3).
7. **Concurrency / performance.** None new. `runCatchingCancellable` is `inline` with a `crossinline` block, so cancellation and dispatcher behaviour are unchanged (`EpisodeScheduleRepository.kt:45-48` keeps `withContext(workerDispatcher)`).

Resolved from the brief's "unverified": detekt `TooGenericExceptionCaught` is `active: false` (`config/detekt/detekt.yml:287-288`), so removing `apiResult` has no detekt effect either way.

## Affected files

- `app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt` (production, only file)
- `app/src/test/kotlin/com/kino/puber/data/api/TmdbApiClientTest.kt`
- `app/src/test/kotlin/com/kino/puber/data/repository/SkipSegmentServiceTest.kt`
- `docs/architecture/api.md`

## Open questions

None blocking. Recorded for later, outside this task:
- The sibling IntroDB clients keep "non-2xx = empty list" (`TheIntroDbApiClient.kt:49-56`, `IntroDbAppApiClient.kt:50-52`).
- A TMDB failure is still invisible at runtime, so the audit's diagnostics goal is not met under option B. A status-code-only log would need an owner decision and a narrow guard amendment.
