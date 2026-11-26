# Review edge, round 1

LENS: edge  ROUND: 1  BASE: 5cd3e37e

### F1 [minor] Late requests after the last iteration of each journey are never checked
- Where: BaselineProfileGenerator.kt:29-32 (@Before resetMock), BaselineMockBackend.kt:56-62 (resetTest), BaselineProfileTestControl.kt:48-60 (checkIteration)
- Problem: after the final iteration's checkIteration the library dumps the profile while the app runs and nothing verifies again; the next test's @Before (RESET_JOURNAL, backend.resetTest()) erases the journal lines and unknownRequests before anyone reads them; after the last test nothing reads them.
- Failure scenario: background work (watch-state sync walk, DetailsPrefetcher, TvHomeSyncCoordinator) after the 500 ms quiescence window in the last iteration of a journey; the egress is refused but AC-2's "journal empty" and "every request reached the mock" are unproven for 4 of 37 iterations.
- Fix: in resetMock(), read VERIFY and backend.verify().unknownRequests before resetting and fail if non-empty (skip on the first test); same check in @AfterClass before backend.close().

### F2 [nit] Spec D14 and D7 still describe the old required routes and certificate validity
- Where: spec :98 (D14), D7, R15; BaselineMockBackend.kt:144; tools/make-baseline-mock-cert.sh:19-20
- Problem: only the plan record mentions the drift; both drifts are correct on merit (genres requested only by ShowAllVM; fixed notBefore 2020 fixes the emulator clock lag; docs/release.md already states it).
- Fix: D14 -> "Rail: + history, items" with the reason; D7/R15 -> "fixed validity 2020-01-01 to 2051-01-01".

Handled: egress (all clients OkHttp built after configureRuntime, no direct fallback, unresolved dead proxy), DoH off when pinned, NSC (no cleartext, only the mock root), per-iteration reset (force-stop, include-stopped-packages, caches and databases cleared), journal locking, receiver DUMP permission and fail-on-crash, mock route order, leaf SAN 127.0.0.1, 24 s stream vs 5 s dwell, profile filter drops only instrumentation code.

### Unproven
- BaselineNetworkBlocker.kt:28-34: if journal.record throws (storage full), the request is refused but not journaled.
- BaselineProfileGenerator.kt:213-215: @AfterClass stopBackend throws UninitializedPropertyAccessException when createBackend failed.
- BaselineInstrumentationEnvironment.kt:37-50: CLEAR does not reset SharedPreferences or filesDir.
