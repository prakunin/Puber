# Readiness 1 - tmdb-result-handling

Mode: readiness, round 1. Judged against spec and plan at worktree HEAD `5c36f19b`
(base `e569e055`); every citation below was opened in the worktree code.

**Verdict: NOT READY** (one must-fix: a wrong test count in a "Done when" criterion)

## AC coverage

| AC | Plan task | Test design row / check | Status |
|----|-----------|-------------------------|--------|
| AC-1 | Task 2 (import swap, wrapper swap, `apiResult` delete) | static greps in Task 2 + fast gate | covered |
| AC-2 | Task 2 `findByImdbId_rethrowsCancellation`; existing `scheduleEndpoint_rethrowsCancellation` kept | unit | covered |
| AC-3 | Task 2 `findLookups_wrapErrorsIntoFailure` (cause-chain assertion, stop-on-Blocked if Ktor misroutes) | unit | covered |
| AC-4 | Task 2 `findTvByImdbId_returnsFailureForHttpErrors` (429, 502); 502 on `findByImdbId` in the rewritten test | unit | covered |
| AC-5 | Task 2 rewritten `findByImdbId_prefersTvThenMovieAndFailsOnHttpError` | unit, primary failing-first | covered |
| AC-6 | Task 2 rewritten test (TV wins, movie fallback, null on empty) + existing `:26`, `:38` kept | unit | covered |
| AC-7 | Task 2 private `find(imdbId)`; greps `find/` = 1 | static / review read | covered |
| AC-8 | Task 1 `skipsPrimaryAndAsksFallback_whenTmdbLookupFails` (not optional) | unit, characterisation | covered |
| AC-9 | Task 3 full gate + `git diff --stat` on both privacy guards | static | covered |
| AC-10 | Explicitly handed to `qa-device` after implementation (spec M1-M3) | device | covered (out of implementer scope, stated) |

## Citations sampled (all resolve)

- `TmdbApiClient.kt:19` - `import kotlinx.coroutines.CancellationException`. OK.
- `TmdbApiClient.kt:36-42` `findTvByImdbId`, `:44` `getTvDetails`, `:53` `getTvSeasonDetails` wrapper, `:59-68` `findByImdbId` with the `isSuccess()` early return at `:63-65`, `:70-76` `apiResult` catching `Exception` at `:74`, `:78-79` `withTmdbPrefix`, `:81-85` `requireSuccess`, `:88` `BASE_URL`, `:90` `forTesting`, `:111` `TmdbApiException`. All OK.
- `CoroutinesErrorHandler.kt:37-45` `runCatchingCancellable` (`suspend inline`, `crossinline`, catches `Throwable`); `:7` imports `kotlin.coroutines.cancellation.CancellationException`. OK.
- `SkipSegmentService.kt:25-37` `getSegments`, `:32-34` fallback, `:39-42` `tryTheIntroDB`, `:56-66` `resolveTmdbId` with `getOrNull()` at `:62`. OK.
- `TmdbIdRepository.kt:10,15` non-suspend `getTmdbId` / `saveTmdbId`. OK.
- `SkipSegmentServiceTest.kt:22-26` mocks, `:30-31` fallbacks, `:33-47` setup, `:35` `coEvery` on `getTmdbId`, `:86` `IMDB_ID`. OK.
- `TmdbApiClientTest.kt:113-132` the pinned test (assertNull on 502 at `:131`), `:134-149` cancellation test, `:151-166` `client`, `:168-178` `respondJson`. OK.
- `EpisodeScheduleRepository.kt:40` `MissingCredentials`, `:55,65,72` `getOrThrow`. OK.
- `TheIntroDbApiClient.kt:43,49-56`, `IntroDbAppApiClient.kt:43,50-52` (helper + non-2xx = empty). OK.
- `data/di/modules.kt:72` `singleOf(::TmdbApiClient)`. OK.
- `docs/architecture/api.md:7-8` paragraph, `:66-67` "Never wrap a Result in another Result". OK.
- `config/detekt/detekt.yml:287-288` `TooGenericExceptionCaught active: false`, `:680-682` `MagicNumber` excludes test, `:711-713` `MaxLineLength 120`. OK.

## Order and build

- Task 1 is a characterisation test, green on base, committed green.
- Task 2 is test-first inside one commit: red tests written, production changed, green, then commit; no red commit.
- Task 3 docs + full gate. Dependency order is correct; no task leaves the build red.
- Line lengths of the proposed code checked (longest ~110 chars). Import placement after `com.kino.puber.BuildConfig` is lexically correct.

## Scope and questions

Plan stays inside Out of scope: no logging, privacy guards untouched, IntroDB clients and `SkipSegmentService` unchanged, public signatures unchanged. Q1-Q3 answered by the owner; no blocking open question.

## Must fix

1. Task 2 states `TmdbApiClientTest` will have **8** tests (in the "Run the touched tests" step and in "Done when"). The file has 6 tests today (`:26`, `:38`, `:47`, `:102`, `:114`, `:135`); the plan rewrites one and adds three, so the result is **9**. Change both occurrences to 9. As written the criterion is unreachable, and a count of 8 would also pass silently if one of the new tests were dropped.

## Notes (not blocking)

- The spec's Test design row AC-1 says `grep -c runCatchingCancellable` equals 4; with the new import line it is 5. The plan's narrower `grep -c '= runCatchingCancellable {'` = 4 is correct; QA and the gate should use the plan's grep, not the spec's.
- `findLookups_wrapErrorsIntoFailure` may be green on base if Ktor converts the `Error`; the plan already records that as a pin, so the primary failing-first test (AC-5) carries the red-first proof.
