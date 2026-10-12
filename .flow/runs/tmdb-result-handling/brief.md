# Brief: tmdb-result-handling

Research: `research.md` (sections 3, 6, 7 relied on; code re-checked at `TmdbApiClient.kt:36-76`, `TmdbApiClientTest.kt:114`).

## Classification
- kind: refactor
- size: M. The diff is small (2 main-side files, 1-2 test files), but it changes the failure contract of a networking client, which `.flow/roles/analyst.md` names as a risk area ("networking and API models"), so it is not S by the role's rule. No split needed: one shippable goal. The conductor may treat it as a light M (Plan below is concrete).
- tracks: none

## Goal
SOLID-13 from the code-quality audit: `TmdbApiClient` uses the shared `runCatchingCancellable` instead of its private `apiResult`, and the two lookups of the TMDB `find/{imdb_id}` endpoint share one request and one failure policy (non-2xx = failed `Result`). Recommended option B of research.md: no new logging.

## Code touched
- `app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt:36-76` (4 `apiResult` uses at `:36,:44,:53,:59`; `apiResult` at `:70-76`; `CancellationException` import at `:19`; `requireSuccess` `:81`).
- `app/src/main/java/com/kino/puber/core/coroutine/CoroutinesErrorHandler.kt:37-45` (helper, unchanged).
- Callers, behaviour must stay: `EpisodeScheduleRepository.kt:55,65,72` (`getOrThrow`), `SkipSegmentService.kt:56-65` (`getOrNull`).
- `app/src/test/kotlin/com/kino/puber/data/api/TmdbApiClientTest.kt:102,114,135`.
- Guards that must stay green and are NOT edited: `SkipSegmentPrivacyTest.kt`, `EpisodeSchedulePrivacyTest.kt` (no `log(`, `Timber.`, `println(` in TMDB/skip-segment sources).
- Docs: `docs/architecture/api.md:7` mentions the TMDB client; check whether a line on its error handling needs an edit.

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

## Plan (test-first)
1. `TmdbApiClientTest.kt`: add failing tests for AC-3 (Error wrapped), AC-4 (`findTvByImdbId` 429/502), AC-2 for `findByImdbId`; rewrite the third assertion of `:114` to expect failure with `TmdbApiException(502)` (AC-5), renaming the test accordingly. Run `./tools/agentw :app:testDevDebugUnitTest --tests '*TmdbApiClientTest'` (red).
2. `TmdbApiClient.kt`: add `private suspend fun find(imdbId): TmdbFindResponse` (GET `find/${imdbId.withTmdbPrefix()}` with `external_source=imdb_id`, `requireSuccess()`, `body()`). Rewrite `findTvByImdbId` = `runCatchingCancellable { find(id).tvResults?.firstOrNull()?.id }`; `findByImdbId` = `runCatchingCancellable { find(id).let { it.tvResults?.firstOrNull()?.id ?: it.movieResults?.firstOrNull()?.id } }`; wrap `getTvDetails`, `getTvSeasonDetails` the same way; delete `apiResult` (`:70-76`) and the `CancellationException` import (`:19`). Add the import `com.kino.puber.core.coroutine.runCatchingCancellable`. No `log`/`Timber` anywhere in the file.
3. Optional AC-8: `SkipSegmentServiceTest.kt` case with a mocked `TmdbApiClient.findByImdbId` returning `Result.failure(TmdbApiException(429))`, empty `TmdbIdRepository`: assert IntroDB.app is called, TheIntroDB not, nothing saved.
4. Check `docs/architecture/api.md:7,29-41`; add one sentence that TMDB uses `runCatchingCancellable` and fails on non-2xx only if the doc already describes TMDB errors.
5. Run `fast` then `full` checks via `./tools/agentw` (set `AGENT_GRADLE_SERIAL=1` if another worktree builds).

## Device verification decision
Needed: yes (networking result handling on the schedule screen and in the player; AGENTS.md "Runtime and External Systems"). Limitation: a worktree build has no `.env`, so `BuildConfig.TMDB_READ_ACCESS_TOKEN` is empty, `isConfigured` is false and any live `find` gets 401. The smoke therefore covers: (a) schedule screen opens and shows its missing-credentials state without crash; (b) player on a known series still offers IntroDB.app segments (a 401 now arrives as a failed Result, `getOrNull()` gives null, same as before). A real TMDB success path is covered only by unit tests (MockEngine). Needs a device lease, `adb -s`, install-preserve; no uninstall or data clear. Note: an empty segment list is cached 1 h (`SkipSegmentRepository.kt:21-23`), so pick a series not probed recently or accept the cache.

## Risks
- Wrapping `Error` into `Result` (instead of letting it escape) matches the other 12 callers of the helper, but is a behaviour change for TMDB. `EpisodeScheduleVM.kt:43-53` already catches `Throwable`.
- Rewriting the pinned test at `:114` is an intentional contract change; the player outcome stays the same only because `SkipSegmentService` uses `getOrNull()`.
- Privacy guards forbid logging in these files; any added `log(` fails them (and the sanitiser has no TMDB `find/tt...` rule, so a logged throwable could carry an IMDb id).
- The unified failure is still invisible at runtime (audit's diagnostics goal is not met under B).
- Sibling IntroDB clients keep their "non-2xx = empty list" policy: out of scope, inconsistent but untouched.
- Unverified: whether detekt `TooGenericExceptionCaught` currently passes on `apiResult` only by config; removing it can only help.

## Open questions
1. Log a TMDB lookup failure in the skip-segment path? Recommended: no (option B); the `SkipSegmentPrivacyTest` guard stays intact. If the owner wants it: log only the HTTP status code with a fixed message and narrowly amend the guard (option C, then size M with the privacy risk). Blocks: yes (changes scope and AC-9).
2. Scope B (wrapper swap plus unified `find`) or A (swap only)? Recommended: B, the substance of SOLID-13. Blocks: yes (AC-4/5/7 disappear under A).
3. Accept a device smoke limited by the empty TMDB token (schedule shows missing credentials, player keeps IntroDB.app segments) plus unit tests as sufficient evidence, limitation recorded? Recommended: yes. Blocks: no.
