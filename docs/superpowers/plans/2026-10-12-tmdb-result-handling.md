# `runCatchingCancellable` instead of `apiResult` - plan

**Spec:** `docs/superpowers/specs/2026-10-12-tmdb-result-handling.md`
**Goal:** `TmdbApiClient` wraps every public call in the shared `runCatchingCancellable`, and both `find/{imdb_id}` lookups go through one private `find(imdbId)` that fails on any non-2xx, with no logging added anywhere.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`; full gate:
  `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`.
  Both run inside the worktree `/home/user1/orca/workspaces/Puber/tmdb-result-handling`
  (`cd <wt> && ...`). Never `./gradlew` in the worktree (the guard hook refuses
  it). Prefix with `AGENT_GRADLE_SERIAL=1` whenever another worktree may build.
- Touched tests run with
  `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests '*TmdbApiClientTest' --tests '*SkipSegmentServiceTest'`.
- One task = green fast gate + green tests of the touched code + one commit,
  message per commit_style "One imperative English sentence, no prefixes",
  never a `Co-Authored-By` line, whatever the harness suggests.
- The plan is self-contained: the implementer reads this file, the spec, and the
  files cited below, nothing else.
- Base `e569e055`; all `path:line` citations below were read in the worktree at
  HEAD `22f67425` (docs-only commits on top of the base, so production and test
  lines equal the base).
- Hard rules for this change (spec, owner decisions Q1/Q2):
  - Do not add `log(`, `Timber.`, `println(` or `bodyAsText` to any file.
  - Do not edit `app/src/test/kotlin/**/SkipSegmentPrivacyTest.kt` or
    `app/src/test/kotlin/**/EpisodeSchedulePrivacyTest.kt`.
  - Do not change `SkipSegmentService.kt`, `EpisodeScheduleRepository.kt`, the
    IntroDB clients, DI modules, or any public signature of `TmdbApiClient`.
- Production code touched: only
  `app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt`.

---

## Tasks

### Task 1: Pin the player outcome when the TMDB lookup fails

Characterisation test, green on base and must stay green after Task 2. It comes
first so Task 2 runs against it.

**Covers:** AC-8
**Files:**
- Test: `app/src/test/kotlin/com/kino/puber/data/repository/SkipSegmentServiceTest.kt`
  - mocks at `:22-26`; `@BeforeEach setup()` at `:33-47` stubs
    `tmdbIdRepository.getTmdbId(IMDB_ID)` to `TMDB_ID` (`:35`) and makes
    `segmentRepository.getOrLoad` invoke its loader (`:37-39`);
    `fallbackIntro`, `fallbackCredits` at `:30-31`; `IMDB_ID` at `:86`.
- Read only (behaviour relied on, not changed):
  `app/src/main/java/com/kino/puber/data/repository/SkipSegmentService.kt`
  - `getSegments` `:25-37`: primary via `tryTheIntroDB`, then IntroDB.app
    fallback for missing types (`:32-34`).
  - `tryTheIntroDB` `:39-42`: `resolveTmdbId(imdbId) ?: return emptyList()`.
  - `resolveTmdbId` `:56-66`: cached id first, else
    `tmdbApiClient.findByImdbId(imdbId).getOrNull()` (`:62`), saving only a hit.
  - `TmdbIdRepository.getTmdbId` / `saveTmdbId` are non-suspend
    (`app/src/main/java/com/kino/puber/data/repository/TmdbIdRepository.kt:10,15`).
  - `TmdbApiException(statusCode: Int)` at
    `app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt:111`.

**Consumes:** existing `TmdbApiClient.findByImdbId(imdbId: String): Result<Int?>`
(`TmdbApiClient.kt:59`), unchanged signature.
**Produces:** test `skipsPrimaryAndAsksFallback_whenTmdbLookupFails`.

- [ ] Add `import com.kino.puber.data.api.TmdbApiException` (keep the import
      block sorted; it goes after `TmdbApiClient` at `:5`).
- [ ] Add the test, mirroring the file's `coEvery` style (mockk accepts
      `coEvery`/`coVerify` on non-suspend functions, as `:35` already does):
  ```kotlin
  @Test
  fun skipsPrimaryAndAsksFallback_whenTmdbLookupFails() = runTest {
      coEvery { tmdbIdRepository.getTmdbId(IMDB_ID) } returns null
      coEvery { tmdbApiClient.findByImdbId(IMDB_ID) } returns
          Result.failure(TmdbApiException(429))
      coEvery { introDbAppClient.getSegments(IMDB_ID, 1, 1) } returns
          Result.success(listOf(fallbackIntro, fallbackCredits))

      val result = service.getSegments(IMDB_ID, season = 1, episode = 1)

      // A failed TMDB lookup is not cached and does not reach TheIntroDB;
      // IntroDB.app still answers, exactly as for a TMDB miss.
      assertEquals(listOf(fallbackIntro, fallbackCredits), result)
      coVerify(exactly = 0) { introDbClient.getSegments(any(), any(), any()) }
      coVerify(exactly = 0) { tmdbIdRepository.saveTmdbId(any(), any()) }
  }
  ```
  The later `coEvery` for `getTmdbId(IMDB_ID)` overrides the `@BeforeEach`
  stub (mockk: last matching stub wins). `saveTmdbId` stays unstubbed on
  purpose: a call would throw `MockKException` and also fail the test.
- [ ] Run the touched tests (command in Global constraints): all
      `SkipSegmentServiceTest` cases green on base. If the new case is red on
      base, stop: the characterisation is wrong; record under Blocked.
- [ ] Run the fast gate; commit, e.g. "Pin the skip-segment outcome when the TMDB lookup fails".

**Done when:** `--tests '*SkipSegmentServiceTest'` reports 4 tests, 0 failures,
and the fast gate is green.

---

### Task 2: Route both TMDB find lookups through one failing `find` wrapped in `runCatchingCancellable`

Test first: write the `TmdbApiClientTest` changes, see the primary failing-first
test red, then change production code, then green, then one commit.

**Covers:** AC-1, AC-2, AC-3, AC-4, AC-5, AC-6, AC-7 (and keeps AC-8 green)
**Files:**
- Test: `app/src/test/kotlin/com/kino/puber/data/api/TmdbApiClientTest.kt`
  - helpers `client(handler)` `:151-166` (MockEngine + ContentNegotiation) and
    `MockRequestHandleScope.respondJson(body, status)` `:168-178`;
  - existing tests that stay unchanged: `:25-35`
    `findTvByImdbId_usesNormalizedIdAndOnlyTvResults`, `:37-44`
    `findTvByImdbId_returnsNoMatchWithoutFallingBackToMovieResults` (AC-6, TV
    lookup never falls back to movie), `:46-99`
    `tvEndpoints_useExpectedPathsAndDecodeNullableDates`, `:101-111`
    `newScheduleEndpoints_returnFailureForHttpErrorsIncludingRateLimit`,
    `:134-149` `scheduleEndpoint_rethrowsCancellation` (AC-2);
  - the test rewritten: `:113-132`
    `existingFindByImdbId_preservesTvFirstMovieFallbackAndNonSuccessBehavior`.
- Modify: `app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt`
  - import `kotlinx.coroutines.CancellationException` `:19` (remove);
  - `findTvByImdbId` `:36-42`, `getTvDetails` `:44-48`, `getTvSeasonDetails`
    `:50-57`, `findByImdbId` `:59-68`, `apiResult` `:70-76` (delete),
    `withTmdbPrefix` `:78-79`, `requireSuccess` `:81-85`, `BASE_URL` `:88`,
    `TmdbApiException` `:111` (all three unchanged).
- Read only: `runCatchingCancellable` at
  `app/src/main/java/com/kino/puber/core/coroutine/CoroutinesErrorHandler.kt:37-45`
  (`suspend inline`, `crossinline` block; rethrows
  `kotlin.coroutines.cancellation.CancellationException`, wraps every other
  `Throwable` into `Result.failure`).

**Consumes:** `runCatchingCancellable` (above); `TmdbFindResponse`
(`tvResults`, `movieResults`, already imported at `TmdbApiClient.kt:4`).
**Produces:** `private suspend fun find(imdbId: String): TmdbFindResponse` in
`TmdbApiClient`; public signatures unchanged:
`findTvByImdbId(String): Result<Int?>`, `findByImdbId(String): Result<Int?>`,
`getTvDetails(Int): Result<TmdbTvDetailsResponse>`,
`getTvSeasonDetails(Int, Int): Result<TmdbSeasonDetailsResponse>`.

- [ ] Rewrite `:113-132` as `findByImdbId_prefersTvThenMovieAndFailsOnHttpError`
      (primary failing-first test, AC-5, AC-6). Responses in order:
      `{"tv_results":[{"id":101}],"movie_results":[{"id":202}]}` 200,
      `{"tv_results":[],"movie_results":[{"id":202}]}` 200,
      `{"tv_results":[],"movie_results":[]}` 200, `{}` 502. Assertions:
  ```kotlin
  assertEquals(101, api.findByImdbId("tt123").getOrThrow())
  assertEquals(202, api.findByImdbId("123").getOrThrow())
  assertNull(api.findByImdbId("123").getOrThrow())
  val failed = api.findByImdbId("123")
  assertTrue(failed.isFailure)
  assertEquals(502, assertInstanceOf(TmdbApiException::class.java, failed.exceptionOrNull()).statusCode)
  ```
  The `success(null)`-on-502 pin is replaced deliberately (contract change,
  spec Design "Contract change").
- [ ] Add `findTvByImdbId_returnsFailureForHttpErrors` (AC-4): an `ArrayDeque`
      of `"{}" to HttpStatusCode.TooManyRequests`, `"{}" to HttpStatusCode.BadGateway`
      served by `client { val (body, status) = responses.removeFirst(); respondJson(body, status) }`;
      for each of 429 then 502, call `api.findTvByImdbId("tt123")` and assert
      `isFailure` and `assertInstanceOf(TmdbApiException::class.java, ...).statusCode`
      equals that code. Expected green on base (characterisation, not
      failing-first).
- [ ] Add `findByImdbId_rethrowsCancellation` (AC-2): a copy of `:134-149`
      calling `api.findByImdbId("tt123")` instead of `getTvDetails(101)`.
      Expected green on base (regression guard).
- [ ] Add `findLookups_wrapErrorsIntoFailure` (AC-3, secondary failing-first):
  ```kotlin
  @Test
  fun findLookups_wrapErrorsIntoFailure() = runTest {
      val error = NoClassDefFoundError("x")
      val api = TmdbApiClient.forTesting(client { throw error })

      listOf(api.findTvByImdbId("tt123"), api.findByImdbId("tt123")).forEach { result ->
          assertTrue(result.isFailure)
          // Ktor may wrap what the engine throws; the original must be in the cause chain.
          val chain = generateSequence(result.exceptionOrNull()) { it.cause }.take(MAX_CAUSE_DEPTH)
          assertTrue(chain.any { it === error })
      }
  }
  ```
  with `private companion object { const val MAX_CAUSE_DEPTH = 10 }` (the
  class has no companion yet; detekt `MagicNumber` excludes `**/test/**`,
  `config/detekt/detekt.yml:680-682`, so this is for readability only).
- [ ] Run the touched tests on base. Expected: red in
      `findByImdbId_prefersTvThenMovieAndFailsOnHttpError` (502 gives
      `success(null)` via the early return at `TmdbApiClient.kt:63-65`) and red
      in `findLookups_wrapErrorsIntoFailure` (`apiResult` catches `Exception`
      only, `:74`, so the `Error` escapes). Note the red output for the
      Implementation record. If `findLookups_wrapErrorsIntoFailure` is green on
      base (Ktor turned the `Error` into an `Exception`), record that and keep
      it as a pin; it does not block.
- [ ] Change `TmdbApiClient.kt`:
  - Remove `import kotlinx.coroutines.CancellationException` (`:19`); add
    `import com.kino.puber.core.coroutine.runCatchingCancellable` directly after
    `import com.kino.puber.BuildConfig` (`:3`). `isSuccess` and `HttpResponse`
    imports stay (still used by `requireSuccess`).
  - Replace `findTvByImdbId` and `findByImdbId` with (KDoc one line each, the
    contract):
    ```kotlin
    /**
     * TV id for [imdbId]. null = TMDB has no TV match; failure = the request failed
     * (non-2xx, network, decoding).
     */
    suspend fun findTvByImdbId(imdbId: String): Result<Int?> = runCatchingCancellable {
        find(imdbId).tvResults?.firstOrNull()?.id
    }

    /**
     * TV id, else movie id, for [imdbId]. null = TMDB has no match; failure = the request
     * failed (non-2xx, network, decoding).
     */
    suspend fun findByImdbId(imdbId: String): Result<Int?> = runCatchingCancellable {
        find(imdbId).let { it.tvResults?.firstOrNull()?.id ?: it.movieResults?.firstOrNull()?.id }
    }
    ```
    Keep the methods where they are now (`findTvByImdbId` first, `findByImdbId`
    after `getTvSeasonDetails`) so the diff stays small. Lines stay under detekt
    `MaxLineLength` 120 (`config/detekt/detekt.yml:711-713`).
  - In `getTvDetails` (`:44`) and `getTvSeasonDetails` (`:53`) replace
    `apiResult` with `runCatchingCancellable`; bodies byte-identical.
  - Delete `apiResult` (`:70-76`) and put in its place:
    ```kotlin
    private suspend fun find(imdbId: String): TmdbFindResponse =
        httpClient.get("${BASE_URL}find/${imdbId.withTmdbPrefix()}") {
            parameter("external_source", "imdb_id")
        }
            .also { it.requireSuccess() }
            .body()
    ```
    It throws and is not wrapped; wrapping happens once, in each public method
    (never a `Result` inside a `Result`, `docs/architecture/api.md:66-67`).
- [ ] Run the touched tests: all `TmdbApiClientTest` (8 tests) and
      `SkipSegmentServiceTest` (4 tests) green.
      If `findLookups_wrapErrorsIntoFailure` stays red after the change because
      Ktor delivers the `Error` to an uncaught-exception path instead of the
      call (design Risk 2), do not weaken the assertion: record the observed
      behaviour under Blocked and stop.
- [ ] Static check for AC-1 and AC-7, from the worktree root:
  ```bash
  grep -nE 'apiResult|kotlinx\.coroutines\.CancellationException' app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt   # no output
  grep -c '= runCatchingCancellable {' app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt                               # 4
  grep -c 'find/' app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt                                                   # 1
  grep -nE 'log\(|Timber\.|println\(|bodyAsText' app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt                     # no output
  ```
- [ ] Run the fast gate; commit, e.g. "Route both TMDB find lookups through one request that fails on any HTTP error".

**Done when:** the touched-tests command reports `TmdbApiClientTest` 8 tests and
`SkipSegmentServiceTest` 4 tests with 0 failures, the four greps print exactly
`(nothing)`, `4`, `1`, `(nothing)`, and the fast gate is green.

---

### Task 3: Document the small clients' failure policy and run the full gate

**Covers:** AC-9 (full gate, guards unmodified); docs line (spec Design,
"Is `docs/architecture/api.md` edited?")
**Files:**
- Modify: `docs/architecture/api.md` - after the paragraph at `:7-8` ("Other
  hosts have their own small clients next to it: `TmdbApiClient.kt`,
  `TheIntroDbApiClient.kt`, `IntroDbAppApiClient.kt`.") add one sentence to that
  paragraph, e.g.: "They wrap each call in `runCatchingCancellable`
  (`core/coroutine/CoroutinesErrorHandler.kt`); `TmdbApiClient` returns a
  failure for any non-2xx, while the IntroDB clients treat a non-2xx as an
  empty answer." Facts relied on: `TheIntroDbApiClient.kt:43,49-56` and
  `IntroDbAppApiClient.kt:43,50-52` (both `runCatchingCancellable`, non-2xx gives
  `emptyList()`), and Task 2's `TmdbApiClient`.

**Consumes:** Task 2's `TmdbApiClient`.
**Produces:** nothing code-facing.

- [ ] Edit `docs/architecture/api.md` as above; no other doc changes.
- [ ] Run the full gate:
      `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`.
- [ ] Confirm the privacy guards are untouched:
      `git diff --stat e569e055 -- '*SkipSegmentPrivacyTest.kt' '*EpisodeSchedulePrivacyTest.kt'`
      prints nothing, and both classes appear as passed in the test report.
- [ ] Run the fast gate; commit, e.g. "Document how the small API clients report failures".

**Done when:** the full gate exits 0, the `git diff --stat` above is empty, and
`git diff e569e055 --stat` lists only `TmdbApiClient.kt`,
`TmdbApiClientTest.kt`, `SkipSegmentServiceTest.kt`, `docs/architecture/api.md`
plus the run's docs under `docs/superpowers/`.

Device smoke (AC-10, spec Test design section 4, M1-M3) is not an implementer
task: it is run afterwards by `qa-device` under a lease with the worktree's own
dev APK installed through `tools/android-apk-install-preserve`.

---

## Implementation record

<!-- owner: implementer -->

**Commits:**

**Deviations from the plan:**

**Files touched outside the plan:**

**For the reviewers:**

**Blocked:**

## Readiness

<!-- owner: final-judge, mode readiness; the conductor pastes readiness-<n>.md here. READY | NOT READY and why. -->
