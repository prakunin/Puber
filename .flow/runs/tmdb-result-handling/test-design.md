# Test design: tmdb-result-handling

Base `e569e055`. Levels: `unit` (JUnit 5 on the JVM, Ktor MockEngine, mockk), `static` (grep, compile, detekt, lint), `device` (manual smoke). Owner decisions applied: option B, no logging, privacy guards unmodified, device smoke without a TMDB token accepted (limitation recorded; unit tests carry the TMDB success path).

## 1. Risk profile

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

## 2. Traceability

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

## 3. Failing-first (refactor with a contract change)

Written first and red on `e569e055`. Verified by reading the base code (`git -C <wt> show e569e055:app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt`, lines 59-76):

- `findByImdbId_prefersTvThenMovieAndFailsOnHttpError`: base returns `success(null)` on 502 through the `isSuccess()` early return, so the failure assertion is red.
- `findTvByImdbId_returnsFailureForHttpErrors`: base `findTvByImdbId` already calls `requireSuccess()` (`:40`), so it is expected GREEN on base (characterisation; it guards the unification, not a fix). Do not count it as failing-first.
- `findLookups_wrapErrorsIntoFailure`: base `apiResult` catches `Exception` only, so an `Error` escapes: red on base (unless Ktor wraps it into an `Exception`; if it passes on base, record that and keep the test as a pin).
- `findByImdbId_rethrowsCancellation`: green on base (`apiResult` rethrows); a regression guard.
- `skipsPrimaryAndAsksFallback_whenTmdbLookupFails`: green on base (502 gave null too); it pins the unchanged outcome and must stay green after.

Primary failing-first test: `TmdbApiClientTest.findByImdbId_prefersTvThenMovieAndFailsOnHttpError` (AC-5). Secondary: `findLookups_wrapErrorsIntoFailure`.

Commands: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests '*TmdbApiClientTest' --tests '*SkipSegmentServiceTest'` in the worktree, red before step 2, green after.

## 4. Manual checks (device smoke, AC-10)

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

## 5. UNTESTABLE

- Live TMDB success path on device: untestable without a token (owner accepted; unit tests cover it).
- "Failure is visible at runtime / diagnostics": not delivered under option B, so no test.
- AC-7 "code read" has no automated test; behaviour is pinned through AC-4/AC-5.
