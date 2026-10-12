LENS: tests  ROUND: 1  BASE: e569e055
### F1 [minor] The new Error-wrapping on getTvDetails and getTvSeasonDetails has no test
- Where: app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt:44-57 (wrapper swap); app/src/test/kotlin/com/kino/puber/data/api/TmdbApiClientTest.kt:180-189 (find methods only)
- Problem: The spec's contract table says "any method, engine throws Error -> failure(error)". Moving to `runCatchingCancellable` changes this for the two schedule endpoints as well, but `findLookups_wrapErrorsIntoFailure` only tests `findTvByImdbId` and `findByImdbId`. `getTvSeasonDetails` also has no failure test of any kind (non-2xx, cancellation or Error). Its only test is the 2xx decode in `tvEndpoints_useExpectedPathsAndDecodeNullableDates`.
- Failure scenario: someone later puts a local `catch (e: Exception)` wrapper back on `getTvDetails` or `getTvSeasonDetails`, or drops `.also { it.requireSuccess() }` from `getTvSeasonDetails`. An Error, or a 502 decoded from `{}`, then escapes or becomes a success, and every TmdbApiClientTest stays green. AC-3 names only the find methods, so this is a coverage gap, not an AC breach.
- Fix: add `getTvDetails(101)` and `getTvSeasonDetails(101, 2)` to the list in `findLookups_wrapErrorsIntoFailure` (rename it to e.g. `allCalls_wrapErrorsIntoFailure`). Add one 502 assertion for `getTvSeasonDetails` next to `newScheduleEndpoints_returnFailureForHttpErrorsIncludingRateLimit`.

### F2 [minor] AC-10 M2 is only partly checked: the IntroDB.app skip prompt was never seen on device
- Where: /mnt/data/projects/PetProjets/Puber/.flow/runs/tmdb-result-handling/qa-report.md (Round 2, AC-10 PARTIAL); docs/superpowers/evidence/2026-10-12-tmdb-result-handling-device.md
- Problem: AC-10 requires that "a player on a known series still shows skip prompts from IntroDB.app or a cached TMDB id". Round 2 only showed that playback started and ran about 15 s without a crash. The skip prompt is marked NOT CHECKED. The service-level outcome is pinned by the unit test `SkipSegmentServiceTest.skipsPrimaryAndAsksFallback_whenTmdbLookupFails`, so the risk is low. Still, the device half of the AC has no evidence.
- Failure scenario: a runtime-only problem in the 401 -> failure -> getOrNull -> IntroDB.app path (wiring, cache state) would not be seen, because no one watched a prompt appear.
- Fix: run M2 from the start of a series not probed in the last hour and record whether the prompt appears. Or have the owner explicitly accept AC-10 as partial at gate 2.

Notes (verified, no finding):
- Suite state: I ran `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests '*TmdbApiClientTest' --tests '*SkipSegmentServiceTest' --tests '*PrivacyTest'` in the worktree: BUILD SUCCESSFUL. TmdbApiClientTest 9/9, SkipSegmentServiceTest 4/4, SkipSegmentPrivacyTest 3/3, EpisodeSchedulePrivacyTest 1/1, 0 skipped, 0 failures. QA round 1 reports the full check (tests, detektAll, lintDevDebug) green. Neither privacy test is in the diff.
- Mutation reasoning, every case caught:
  - Removing `requireSuccess` from `find` makes `{}` on 502 decode to `success(null)`. The 502 assertions in `findByImdbId_prefersTvThenMovieAndFailsOnHttpError` and `findTvByImdbId_returnsFailureForHttpErrors` go red.
  - Dropping the movie fallback breaks the 202 assertion. Adding a movie fallback to `findTvByImdbId` breaks the test at `:38`.
  - Swapping in `runCatching` makes `thrown == null`, so `findByImdbId_rethrowsCancellation` goes red.
  - Catching only `Exception` lets the Error escape, so `findLookups_wrapErrorsIntoFailure` goes red. The implementation record shows it red on base with `NoClassDefFoundError`, and its identity check on the cause chain is sound.
  - `getOrThrow` in `SkipSegmentService.resolveTmdbId` makes the AC-8 test throw. Calling `saveTmdbId` or TheIntroDB hits a strict mockk, which throws and is also verified 0 times.
- Rewriting the old `success(null)` pin is a deliberate contract change (AC-5), not a weakened test. The first two assertions are kept and an empty-results -> null case was added.
- AC-1 and AC-7 are static by design. The code read confirms one private `find` with a single `requireSuccess`.
