# Readiness 2 - tmdb-result-handling

Mode: readiness, round 2. Judged at worktree HEAD `9e1f3557` (base `e569e055`).
The revision since round 1 is `git diff 5c36f19b 9e1f3557`.

**Verdict: READY**

## Round 1 must-fix

1. The `TmdbApiClientTest` count was wrong (8). **Resolved.** Both places now say 9:
   plan `:231` ("Run the touched tests") and `:246` ("Done when"). I recounted
   from the code: `TmdbApiClientTest.kt` has 6 `@Test` today (`:25`, `:37`, `:46`,
   `:101`, `:113`, `:134`). Task 2 rewrites one of them (`:113-132` ->
   `findByImdbId_prefersTvThenMovieAndFailsOnHttpError`) and adds three
   (`findTvByImdbId_returnsFailureForHttpErrors`, `findByImdbId_rethrowsCancellation`,
   `findLookups_wrapErrorsIntoFailure`). 6 + 3 = 9.
   `SkipSegmentServiceTest` has 3 `@Test` today, and Task 1 adds
   `skipsPrimaryAndAsksFallback_whenTmdbLookupFails`, so the stated 4 is correct.

## Regression check

- The revision changes 2 lines in the plan and nothing else: no spec, code or
  test file is touched. `e569e055..9e1f3557` touches only the spec and the plan.
  The worktree is clean.
- No other place in the plan, the spec or `test-design.md` still says 8.
- I re-opened these citations in the code and they still resolve:
  `TmdbApiClient.kt:19` (CancellationException import), `:59-68` `findByImdbId`
  with the non-2xx early return, `:70-76` `apiResult`;
  `SkipSegmentService.kt:56-66` `resolveTmdbId` with `getOrNull()` at `:62`;
  `docs/architecture/api.md:66-67` (never nest `Result`).
- The round 1 findings on AC coverage, task order (no red commit), Out of scope,
  and the answered questions Q1-Q3 still apply unchanged.

## Must fix

None.

## Notes (carried from round 1, not blocking)

- The AC-1 grep: use the plan's `grep -c '= runCatchingCancellable {'` = 4. The
  spec's broader grep returns 5 once the new import is added.
- `findLookups_wrapErrorsIntoFailure` may already pass on base. AC-5 is the test
  that has to fail first and then pass.
