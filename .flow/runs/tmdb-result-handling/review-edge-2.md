LENS: edge  ROUND: 2  BASE: c2b93ed0
No findings.

Scope: the incremental diff `c2b93ed0..7e0b94f3` changes only tests and the plan note. `app/src/test/kotlin/com/kino/puber/data/api/TmdbApiClientTest.kt` gains a 502 assertion for `getTvSeasonDetails` and now runs the Error-wrapping test over all four public methods. `docs/superpowers/plans/2026-10-12-tmdb-result-handling.md` gets the round-1 fix note. No production code changed, so this increment adds no unhandled runtime boundaries.

Checked against `app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt` at head:
- **Non-2xx:** `getTvSeasonDetails` goes through `requireSuccess()` and then `runCatchingCancellable`, so the new 502 assertion matches the real path.
- **Error wrapping:** all four methods wrap through `runCatchingCancellable`, so the extended `allCalls_wrapErrorsIntoFailure` checks real behaviour and would fail if any one of them stopped wrapping.
- **Test isolation:** the season failure case uses its own `forTesting` client and does not share state with the 429 client in the same test.
- **Order:** `allCalls_wrapErrorsIntoFailure` calls the methods in a fixed order on a stateless `MockEngine` handler, so it does not depend on order.
- **Cancellation:** `scheduleEndpoint_rethrowsCancellation` covers it for `getTvDetails`. `getTvSeasonDetails` uses the same wrapper but has no cancellation test of its own. That gap is not new in this increment and is not a reachable defect.
