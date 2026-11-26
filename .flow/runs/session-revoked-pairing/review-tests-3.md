LENS: tests  ROUND: 3  BASE: b53bf631

I ran the two test classes the incremental diff touches with `./tools/agentw :app:testDevDebugUnitTest --tests …SessionEndCleanupTest --tests …SessionEventBusTest`. `SessionEndCleanupTest` ran 3 tests and `SessionEventBusTest` ran 6. None were skipped and none failed.

What I checked by reverting each behaviour in my head (nothing was edited):
- **`aHandlerCancelledMidClearLetsTheReplayClearAgain` catches the triage-2 #1 regression.** The test runs a cleanup whose clear suspends, then cancels it. If the `finally` that calls `releasePendingEnd()` is removed, the replay sees the end as already claimed and returns at once, so `clears` stays 1 and the test fails. If the deep-link reset only depended on the claim and not on its own `claimLinkReset` flag, it would run again on the replay, `linkResets` would be 2, and the test would fail too.
- **`aReplayedEndKeepsALinkAcceptedSinceAndClearsOnce` closes round-2 tests F1.** If the claim check is dropped, the clears run twice and the deep-link target accepted after the end is wiped. Both assertions fail.
- **The red runs are recorded.** The plan's "Fix round 2" record shows a red run for both P1 tests against a stub `run()` that does nothing. It marks `noEndMeansNoClears` as a guard.
- **The AC-7 amendment needs no test change.** `DefaultErrorHandlerTest.map_keepsTheGenericMessageForARefusalThatLeavesTheSessionAlone` already asserts the amended behaviour. The test-design rows added in the spec match the tests that exist.

### F1 [minor] Nothing tests that one failing clear is logged and the next clears still run
- Where: /home/dev/orca/workspaces/Puber/session-revoked-pairing/app/src/main/java/com/kino/puber/core/session/SessionEndCleanup.kt:24-32
- Problem: The comment in the code says this catch is what keeps the app's only session-end collector alive. Every test passes a single clear that never throws, so the `catch (error: Throwable)` branch and "the next one still runs" are never exercised. This was untestable while it lived inside the composable. The extraction makes a test cheap now.
- Failure scenario: Someone removes the per-clear `try/catch`, or moves it around the whole `forEach`. Then `contentCache.clear()` throws (for example a storage error), the clears after it (`detailsPrefetcher.invalidate()`) are skipped, or the exception escapes `run()` and ends the collector in `App.kt`. Every later `Unauthorized` in that process is then never routed to Auth, and the suite stays green.
- Fix: Add a `SessionEndCleanupTest` case with two clears, the first throwing `IllegalStateException`. Assert that `run()` returns normally and the second clear ran.

### Unproven
- /home/dev/orca/workspaces/Puber/session-revoked-pairing/app/src/main/java/com/kino/puber/ui/feature/root/component/App.kt:77-90 The wiring in `App.kt` has no test: which four clears are passed, in what order, and the route to Auth after `run()`. This gap existed before, in the composable. It only matters if a clear is dropped from the list, and only a Compose test of `SessionExpiredHandler`, or a factory function that builds the list, would catch that.
