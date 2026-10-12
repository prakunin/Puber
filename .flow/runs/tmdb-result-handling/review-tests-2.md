LENS: tests  ROUND: 2  BASE: c2b93ed0
No findings.

I checked the fix against review-tests-1 F1 and it closes it. The change touches only the test file and the plan doc. Runtime code did not change.

How I checked:
- **Suite run:** `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests '*TmdbApiClientTest'` in the worktree gave BUILD SUCCESSFUL. The test task actually ran (not up-to-date): 9 tests, 0 skipped, 0 failures, 0 errors.
- **Catching only `Exception` in `getTvDetails` or `getTvSeasonDetails`:** in `allCalls_wrapErrorsIntoFailure` (`app/src/test/kotlin/com/kino/puber/data/api/TmdbApiClientTest.kt`), the `NoClassDefFoundError` would escape while `listOf(...)` is being built. The test would then throw and go red. The cause-chain identity check (`===`) is the same sound assertion used for the find methods.
- **Dropping `.also { it.requireSuccess() }` from `getTvSeasonDetails`:** the new 502 assertion in `newScheduleEndpoints_returnFailureForHttpErrorsIncludingRateLimit` would go red either way. If `{}` decodes, the result is a success and `isFailure` fails. If decoding fails, the error is a SerializationException rather than a `TmdbApiException`, and `assertInstanceOf(TmdbApiException)` fails.
- **Dropping it from `getTvDetails`:** the existing 429 assertion in the same test already catches this.
- **Weakened tests:** none. The rename from `findLookups_wrapErrorsIntoFailure` keeps the two original find calls and their assertions. No assertions were removed in `c2b93ed0..7e0b94f3`.

Gaps that remain but are not findings: I only re-ran `TmdbApiClientTest`, not the full check, because the increment is test-only. Round-1 F2 (AC-10 skip prompt on device) is out of scope for this increment; triage sent it to gate 2.
