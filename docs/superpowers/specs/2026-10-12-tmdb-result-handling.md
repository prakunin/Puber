# `runCatchingCancellable` instead of `apiResult`

Status: approved
Date: 2026-10-12
Slug: tmdb-result-handling
Kind: refactor
Size: M
Tracks: none

## Problem / Why

SOLID-13 from the code-quality audit: `TmdbApiClient` uses the shared `runCatchingCancellable` instead of its private `apiResult`, and the two lookups of the TMDB `find/{imdb_id}` endpoint share one request and one failure policy (non-2xx = failed `Result`). Recommended option B of research.md: no new logging.

### Code touched

- `app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt:36-76` (4 `apiResult` uses at `:36,:44,:53,:59`; `apiResult` at `:70-76`; `CancellationException` import at `:19`; `requireSuccess` `:81`).
- `app/src/main/java/com/kino/puber/core/coroutine/CoroutinesErrorHandler.kt:37-45` (helper, unchanged).
- Callers, behaviour must stay: `EpisodeScheduleRepository.kt:55,65,72` (`getOrThrow`), `SkipSegmentService.kt:56-65` (`getOrNull`).
- `app/src/test/kotlin/com/kino/puber/data/api/TmdbApiClientTest.kt:102,114,135`.
- Guards that must stay green and are NOT edited: `SkipSegmentPrivacyTest.kt`, `EpisodeSchedulePrivacyTest.kt` (no `log(`, `Timber.`, `println(` in TMDB/skip-segment sources).
- Docs: `docs/architecture/api.md:7` mentions the TMDB client; check whether a line on its error handling needs an edit.

### Device verification decision

Needed: yes (networking result handling on the schedule screen and in the player; AGENTS.md "Runtime and External Systems"). Limitation: a worktree build has no `.env`, so `BuildConfig.TMDB_READ_ACCESS_TOKEN` is empty, `isConfigured` is false and any live `find` gets 401. The smoke therefore covers: (a) schedule screen opens and shows its missing-credentials state without crash; (b) player on a known series still offers IntroDB.app segments (a 401 now arrives as a failed Result, `getOrNull()` gives null, same as before). A real TMDB success path is covered only by unit tests (MockEngine). Needs a device lease, `adb -s`, install-preserve; no uninstall or data clear. Note: an empty segment list is cached 1 h (`SkipSegmentRepository.kt:21-23`), so pick a series not probed recently or accept the cache.

## Acceptance criteria

- AC-1. Given `TmdbApiClient.kt`, when searched, then it has no `apiResult` and no `kotlinx.coroutines.CancellationException` import, and each public suspend method wraps its work in `runCatchingCancellable` (check: grep + compile).
- AC-2. Given the engine throws `CancellationException`, when any public method is called, then it is rethrown, not wrapped (existing `scheduleEndpoint_rethrowsCancellation` stays green; add the same for `findByImdbId`).
- AC-3. Given the engine throws an `Error` (e.g. `NoClassDefFoundError`), when `findTvByImdbId` or `findByImdbId` is called, then the result is `Result.failure` holding that error (new test).
- AC-4. Given a non-2xx response (429, 502), when `findTvByImdbId` is called, then the result is a failure with `TmdbApiException(statusCode)` (new test; same for 502 on `findByImdbId`).
- AC-5. Given a non-2xx response, when `findByImdbId` is called, then the result is `Result.failure(TmdbApiException(statusCode))`, not `success(null)` (replaces the pin at `TmdbApiClientTest.kt:114`, rewritten deliberately).
- AC-6. Given 2xx responses, when `findByImdbId` is called, then TV id wins, else movie id, else null on empty results; `findTvByImdbId` returns the TV id or null and never falls back to movie (existing tests at `:26,:38` and the first two assertions of `:114` stay green).
- AC-7. Given the code, when read, then both find methods use one private `find(imdbId)` (one request build, one `requireSuccess`) and differ only in projection.
- AC-8. Given TMDB fails for a skip-segment lookup, when `SkipSegmentService` resolves segments with no cached TMDB id, then the player outcome is unchanged: TheIntroDB is skipped, the IntroDB.app fallback is still asked, no TMDB id is saved (new `SkipSegmentServiceTest` case; if the mock setup makes it costly, mark as optional in the plan and cover by AC-5 alone).
- AC-9. Given the full check, when run, then `testDevDebugUnitTest`, `detektAll` and `lintDevDebug` pass, including `SkipSegmentPrivacyTest` and `EpisodeSchedulePrivacyTest` unmodified.
- AC-10. Given the device smoke, when the worktree APK runs, then the episode schedule screen opens to a defined state and a player on a known series still shows skip prompts from IntroDB.app or a cached TMDB id (see device decision).

## Out of scope

- The unified failure is still invisible at runtime (audit's diagnostics goal is not met under B).
- Sibling IntroDB clients keep their "non-2xx = empty list" policy: out of scope, inconsistent but untouched.
- Logging the TMDB lookup failure in `SkipSegmentService` (audit's option C): declined by the owner (Q1); privacy guard tests stay unmodified.

## Design

Scope fixed by the owner (state.md Decisions): option B. Swap `TmdbApiClient`'s
private `apiResult` for the shared `runCatchingCancellable`, and route both
`find/{imdb_id}` lookups through one private `find(imdbId)` that fails on any
non-2xx. No logging anywhere; the privacy guard tests stay unmodified. All
citations below were read in the worktree at base `e569e055`.

### Decisions

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

### Changes by layer

#### API / repository

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

#### ViewModel state and actions

No change. `EpisodeScheduleVM.kt:43-53` already catches `Throwable` and maps it through `mapper.mapError`. An `Error` that used to escape `apiResult` now arrives as a failed `Result`, `getOrThrow()` rethrows it in `EpisodeScheduleRepository`, and the same catch maps it to the screen's error state.

#### Screen and DI module, Compose content, resources

No change.

#### Tests

`app/src/test/kotlin/com/kino/puber/data/api/TmdbApiClientTest.kt` (MockEngine helpers `client` `:151-166`, `respondJson` `:168-178`):
- Rewrite `:113-132` `existingFindByImdbId_preservesTvFirstMovieFallbackAndNonSuccessBehavior` as `findByImdbId_prefersTvThenMovieAndFailsOnHttpError`. The first two assertions (`:129-130`) stay. The third (`:131`) becomes: failure, with `TmdbApiException.statusCode == 502` (AC-5, AC-6).
- New `findTvByImdbId_returnsFailureForHttpErrors`: 429 and 502, each giving `TmdbApiException` with that code (AC-4).
- New `findByImdbId_rethrowsCancellation`: copies `scheduleEndpoint_rethrowsCancellation` (`:134-149`) against `findByImdbId` (AC-2).
- New `findLookups_wrapErrorsIntoFailure`: the engine handler throws `NoClassDefFoundError("x")`. Both `findTvByImdbId` and `findByImdbId` return a failure whose exception is that instance, or has it as cause (see Risk 2) (AC-3).
- The tests at `:25`, `:37`, `:46`, `:101` and `:134` stay as they are (AC-6, AC-2).

`app/src/test/kotlin/com/kino/puber/data/repository/SkipSegmentServiceTest.kt`:
- New `skipsPrimaryAndAsksFallback_whenTmdbLookupFails`, as described under Decisions (AC-8). Use `every` for the non-suspend `TmdbIdRepository.getTmdbId` and `saveTmdbId` (`TmdbIdRepository.kt:10,15`). The existing `coEvery` at `:35` also works on non-suspend functions, so mirror it to stay consistent.

Unmodified guards that must stay green (AC-9): `SkipSegmentPrivacyTest.kt` and `EpisodeSchedulePrivacyTest.kt`. The new code adds no `log(`, `Timber.`, `println(` or `bodyAsText`. `SkipSegmentPrivacyTest.kt:42` mocks `findByImdbId` as `success`, so the change does not affect it.

#### Docs

`docs/architecture/api.md:7-8`: add one sentence, as described under Decisions. No other architecture doc mentions TMDB error handling.

### Alternatives rejected

- Option A (swap only): leaves the split failure policy, which is half of SOLID-13; the owner chose B.
- Option C (log the failure in `SkipSegmentService`): breaks `SkipSegmentPrivacyTest` and risks logging IMDb ids, because the sanitiser has no TMDB rule; the owner chose no logging.
- A public `find` returning `Result<TmdbFindResponse>`, with callers projecting: it pushes TMDB DTO shapes into the repositories and adds a third public method for no AC.
- A sealed `TmdbLookup { Match, NoMatch, Failed }` instead of `Result<Int?>`: it changes caller signatures and tests that the task did not ask for. `Result` with nullable success already separates a miss from a failure.
- Keeping `apiResult` but catching `Throwable`: the shared helper still gets duplicated, which is the finding itself.
- Moving `requireSuccess` into the helper, or into a Ktor `HttpResponseValidator`: it widens the change to the HTTP client config and to `getTvDetails`/`getTvSeasonDetails` error types.

### Risks (highest first)

1. **Correctness / contract (`TmdbApiClient.kt:59-68`, consumer `SkipSegmentService.kt:62`).** `findByImdbId` now fails on non-2xx. The player outcome stays the same only because `resolveTmdbId` uses `getOrNull()`. A future caller that uses `getOrThrow()` would surface a 401 or 429 as an error. That is intended, and the KDoc and AC-8 test pin it. The pinned test at `TmdbApiClientTest.kt:114` is rewritten deliberately; the review should treat that as a contract change, not as a weakened test.
2. **Test fidelity for AC-3 (`TmdbApiClientTest.kt:155`, MockEngine).** It is not verified here whether Ktor 3.5.2 passes an `Error` thrown inside a `MockEngine` handler through unchanged or wraps it. The test should assert that the failure's exception is the thrown instance, or has it as its cause (walk `cause`), not just its class. If Ktor lets the `Error` escape the client pipeline in some other way, the test will show it red, and that is the point of writing it first.
3. **Lifecycle / error semantics (`CoroutinesErrorHandler.kt:37-45`).** Fatal `Error`s (OOM, `StackOverflowError`, `NoClassDefFoundError`) are now wrapped into `Result` for TMDB calls, as they already are for the other 12 helper callers. On the schedule screen the result is an error state rather than a crash (`EpisodeScheduleVM.kt:43-53` already caught `Throwable`). In the player the result is "no TheIntroDB segments". This hides a fatal error in exchange for consistency, and it was accepted with option B.
4. **Data shape / caching (`SkipSegmentRepository.kt:19-27`).** If TMDB fails and IntroDB.app is also empty, the empty merged list is cached for 1 h. This is unchanged by the design (a 502 gave the same null before), but it matters for the device smoke: a series probed recently may show no segments for an hour.
5. **Privacy (`SkipSegmentPrivacyTest.kt:68-84,146-156`, `EpisodeSchedulePrivacyTest.kt:12-23,36-43`).** Any `log(`/`Timber.` added to `TmdbApiClient.kt` fails the guards. The design adds none, and the guards must pass unmodified.
6. **Rollout / verification.** A worktree APK has an empty `TMDB_READ_ACCESS_TOKEN`. On device, `findByImdbId` therefore gets 401, which is now a failed `Result` rather than `success(null)`, with the same downstream outcome. The schedule screen stops at `MissingCredentials` (`EpisodeScheduleRepository.kt:40`) before any TMDB call. The live TMDB success path is covered only by MockEngine tests. The owner accepted this limitation (Q3).
7. **Concurrency / performance.** None new. `runCatchingCancellable` is `inline` with a `crossinline` block, so cancellation and dispatcher behaviour are unchanged (`EpisodeScheduleRepository.kt:45-48` keeps `withContext(workerDispatcher)`).

Resolved from the brief's "unverified": detekt `TooGenericExceptionCaught` is `active: false` (`config/detekt/detekt.yml:287-288`), so removing `apiResult` has no detekt effect either way.

### Affected files

- `app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt` (production, only file)
- `app/src/test/kotlin/com/kino/puber/data/api/TmdbApiClientTest.kt`
- `app/src/test/kotlin/com/kino/puber/data/repository/SkipSegmentServiceTest.kt`
- `docs/architecture/api.md`

### Open questions

None blocking. Recorded for later, outside this task:
- The sibling IntroDB clients keep "non-2xx = empty list" (`TheIntroDbApiClient.kt:49-56`, `IntroDbAppApiClient.kt:50-52`).
- A TMDB failure is still invisible at runtime, so the audit's diagnostics goal is not met under option B. A status-code-only log would need an owner decision and a narrow guard amendment.

## Test design

Base `e569e055`. Levels: `unit` (JUnit 5 on the JVM, Ktor MockEngine, mockk), `static` (grep, compile, detekt, lint), `device` (manual smoke). Owner decisions applied: option B, no logging, privacy guards unmodified, device smoke without a TMDB token accepted (limitation recorded; unit tests carry the TMDB success path).

### 1. Risk profile

| # | Risk | P | I | Score | Covered by |
|---|------|---|---|-------|------------|
| R1 | `findByImdbId` non-2xx stays `success(null)` or the new `find` swallows the status (contract change not delivered) | 2 | 3 | 6 | `TmdbApiClientTest.findByImdbId_prefersTvThenMovieAndFailsOnHttpError`, `findTvByImdbId_returnsFailureForHttpErrors` |
| R2 | `CancellationException` wrapped into `Result` by the new wrapper, breaking structured concurrency | 2 | 3 | 6 | `scheduleEndpoint_rethrowsCancellation` (kept), `findByImdbId_rethrowsCancellation` (new) |
| R3 | Player outcome changes when TMDB fails (TheIntroDB asked, id saved, or fallback skipped) | 2 | 3 | 6 | `SkipSegmentServiceTest.skipsPrimaryAndAsksFallback_whenTmdbLookupFails` |
| R4 | TV-first / movie-fallback projection regresses while unifying `find` (`findTvByImdbId` falling back to movie) | 2 | 3 | 6 | `findByImdbId_prefersTvThenMovieAndFailsOnHttpError`, existing tests at `:25`, `:37`; new assertion for tv-only projection (see AC-6) |
| R5 | `Error` handling: wrapped as designed, but MockEngine or Ktor may wrap or let it escape, so the test proves the wrong thing | 2 | 2 | 4 | `findLookups_wrapErrorsIntoFailure` asserts instance or cause chain |
| R6 | Logging added to TMDB/skip-segment sources (privacy, IMDb id leak) | 1 | 3 | 3 | `SkipSegmentPrivacyTest`, `EpisodeSchedulePrivacyTest` run unmodified |
| R7 | Schedule screen crash path on `Error` now arriving as failure | 1 | 2 | 2 | Accepted; `EpisodeScheduleVM` already catches `Throwable`; device smoke (a) |
| R8 | Empty segment list cached 1 h makes the smoke look like a regression | 2 | 1 | 2 | Accepted; manual check note (pick an unprobed series) |
| R9 | Docs line missing or wrong | 1 | 1 | 1 | Accepted; reviewer reads diff |

Every score >= 6 has a unit test at the level where it would fail.

### 2. Traceability

| AC | Level | Priority | Test | Proves |
|----|-------|----------|------|--------|
| AC-1 | static | P0 | `grep -n "apiResult\|kotlinx.coroutines.CancellationException" app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt` returns nothing; `grep -c runCatchingCancellable` equals 4 (one per public suspend method); `:app:compileDevDebugKotlin` | The private wrapper and import are gone and all four public methods use the shared helper |
| AC-2 | unit | P0 | `TmdbApiClientTest.scheduleEndpoint_rethrowsCancellation` (exists, `:134`, must stay green) and new `TmdbApiClientTest.findByImdbId_rethrowsCancellation` (engine handler throws `CancellationException`; `findByImdbId` throws, not returns failure) | Cancellation is rethrown by the shared helper on the unified find path |
| AC-3 | unit | P0 | new `TmdbApiClientTest.findLookups_wrapErrorsIntoFailure` (handler throws `NoClassDefFoundError("x")`; both `findTvByImdbId` and `findByImdbId` return `isFailure`, and the thrown instance is the exception or found by walking `cause`) | `Error` becomes `Result.failure`; assertion is robust to Ktor wrapping (Risk 2 of design) |
| AC-4 | unit | P0 | new `TmdbApiClientTest.findTvByImdbId_returnsFailureForHttpErrors` (429 and 502, each `TmdbApiException.statusCode` equals the code) | Non-2xx on the TV lookup is a failure with the status |
| AC-5 | unit | P0 | rewritten `TmdbApiClientTest.findByImdbId_prefersTvThenMovieAndFailsOnHttpError` (third call 502 gives `isFailure`, `TmdbApiException.statusCode == 502`, not `success(null)`) | The `success(null)` pin is deliberately replaced by a failure |
| AC-6 | unit | P0 | same rewritten test, first two assertions (TV wins 101; movie 202 on empty tv; `tt` prefix handling); existing `TmdbApiClientTest` tests at `:25`, `:37` for `findTvByImdbId` TV id; add one assertion in `findTvByImdbId_returnsFailureForHttpErrors`'s sibling or in the rewritten test: `findTvByImdbId` with `tv_results:[]`, `movie_results:[{"id":202}]` returns `success(null)` | Projection preserved: no movie fallback for the TV lookup; null on empty |
| AC-7 | static | P1 | Code read at review: one `private suspend fun find(imdbId)`, one `requireSuccess()` call site for find, both lookups call it. Behaviourally pinned by AC-4/5 (both paths fail on the same status) | Single request build and failure policy |
| AC-8 | unit | P0 | new `SkipSegmentServiceTest.skipsPrimaryAndAsksFallback_whenTmdbLookupFails`: stub `tmdbIdRepository.getTmdbId(IMDB_ID)` null (override the `@BeforeEach` stub), `tmdbApiClient.findByImdbId(IMDB_ID)` `Result.failure(TmdbApiException(429))`, `introDbAppClient.getSegments(IMDB_ID,1,1)` success of the fallback list; assert result equals fallback list; `coVerify(exactly = 0)` on `introDbClient.getSegments(any(),any(),any())` and on `tmdbIdRepository.saveTmdbId(any(),any())` | Player outcome unchanged on TMDB failure: primary skipped, fallback asked, no id saved |
| AC-9 | static | P0 | `./tools/agentw :app:testDevDebugUnitTest` (includes `SkipSegmentPrivacyTest`, `EpisodeSchedulePrivacyTest`, files untouched: `git diff --stat` shows neither), `detektAll`, `lintDevDebug` | Full gate green, guards unmodified |
| AC-10 | device | P1 | Manual checks M1 and M2 below | Schedule opens to a defined state and player still shows IntroDB.app skip prompts |

Docs line in `docs/architecture/api.md` has no test; reviewer check, P2.

Test count: new `TmdbApiClientTest` cases 3 (`findTvByImdbId_returnsFailureForHttpErrors`, `findByImdbId_rethrowsCancellation`, `findLookups_wrapErrorsIntoFailure`), 1 rewritten, 1 new `SkipSegmentServiceTest` case. Rules: no mock of `TmdbApiClient` inside its own test (real client over MockEngine); in `SkipSegmentServiceTest` mocking `TmdbApiClient` is fine, it is a collaborator there.

### 3. Failing-first (refactor with a contract change)

Written first and red on `e569e055`. Verified by reading the base code (`git -C <wt> show e569e055:app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt`, lines 59-76):

- `findByImdbId_prefersTvThenMovieAndFailsOnHttpError`: base returns `success(null)` on 502 through the `isSuccess()` early return, so the failure assertion is red.
- `findTvByImdbId_returnsFailureForHttpErrors`: base `findTvByImdbId` already calls `requireSuccess()` (`:40`), so it is expected GREEN on base (characterisation; it guards the unification, not a fix). Do not count it as failing-first.
- `findLookups_wrapErrorsIntoFailure`: base `apiResult` catches `Exception` only, so an `Error` escapes: red on base (unless Ktor wraps it into an `Exception`; if it passes on base, record that and keep the test as a pin).
- `findByImdbId_rethrowsCancellation`: green on base (`apiResult` rethrows); a regression guard.
- `skipsPrimaryAndAsksFallback_whenTmdbLookupFails`: green on base (502 gave null too); it pins the unchanged outcome and must stay green after.

Primary failing-first test: `TmdbApiClientTest.findByImdbId_prefersTvThenMovieAndFailsOnHttpError` (AC-5). Secondary: `findLookups_wrapErrorsIntoFailure`.

Commands: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests '*TmdbApiClientTest' --tests '*SkipSegmentServiceTest'` in the worktree, red before step 2, green after.

### 4. Manual checks (device smoke, AC-10)

Preconditions: device lease via `tools/emulator-resource-lock.sh`, `adb -s <serial>` on every call, worktree dev APK installed with `tools/android-apk-install-preserve`, no uninstall or data clear. Known limitation, recorded as accepted: the worktree build has an empty `TMDB_READ_ACCESS_TOKEN`, so live TMDB success is NOT exercised on device; unit tests cover it.

M1. Episode schedule screen
1. Launch the app, reach the episode schedule screen with D-pad keys (`input keyevent KEYCODE_DPAD_*`, `KEYCODE_DPAD_CENTER`, `KEYCODE_BACK`).
2. Expected: the screen opens to its missing-credentials state (`EpisodeScheduleRepository` stops at `MissingCredentials` before any TMDB call). Back returns to the previous screen with focus restored.
3. Must not happen: crash, ANR, blank screen without a state, focus lost after Back.

M2. Player skip prompts
1. Open a known series episode not probed in the last hour (empty segment lists are cached 1 h) and start playback.
2. Expected: IntroDB.app skip prompt appears at the intro or credits for a series known to have IntroDB.app data, or a cached TMDB id path works; the 401 from TMDB is silent (same as before).
3. Must not happen: crash on playback start, playback blocked, player stuck waiting on segments, a skip prompt that never clears.

M3. Evidence hygiene: run `tools/mobile-evidence-audit.sh <evidence-dir>` before sharing; no tokens or raw logcat in evidence.

Manual: 3 (M1, M2 are AC-10; M3 hygiene).

### 5. UNTESTABLE

- Live TMDB success path on device: untestable without a token (owner accepted; unit tests cover it).
- "Failure is visible at runtime / diagnostics": not delivered under option B, so no test.
- AC-7 "code read" has no automated test; behaviour is pinned through AC-4/AC-5.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Log the TMDB lookup failure in `SkipSegmentService` despite `SkipSegmentPrivacyTest`? | yes | answered | No (option B) |
| Q2 | Scope A (swap only) or B (swap plus one `find` with one failure policy)? | yes | answered | B |
| Q3 | Device smoke without a TMDB token plus unit tests enough? | no | answered | Yes, limitation recorded in evidence |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-10-12 | Q1: no logging in SkipSegmentService; privacy guards untouched (option B) | owner |
| 2026-10-12 | Q2: scope B | owner |
| 2026-10-12 | Size M (full cycle) | owner |
| 2026-10-12 | Q3: device smoke without a TMDB token plus unit tests is enough; limitation recorded | owner |
| 2026-10-12 | Gate 1: spec approved | owner |
