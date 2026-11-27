LENS: tests  ROUND: 1  BASE: f53f2144

Suite state: I ran `check.full` plus `:player-test-fixtures:testDebugUnitTest` in the worktree at ce54fc41 with `AGENT_GRADLE_SERIAL=1`. It exited 0 and the baseline-profile tooling script passed all its cases. Most Gradle tasks were up to date, because nothing had changed since the tester's run. Every P0 row from T1 to T16 has a test or a gate under its planned name. The Implementation record shows the red runs for T1 to T6. T7, T9, T10 and T12 to T16 are marked `guard`. No test is `@Ignore`d or `@Disabled`, and the diff removes no assertions.

### F1 [minor] The stale-callback probe can only test a load that was cancelled, but the evidence calls it non-vacuous
- Where: app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerStaleCallbackTest.kt:58-79; docs/superpowers/evidence/2026-11-27-player-hermetic-fixtures-device.md (AC-5, "The control passing shows the probe is not vacuous")
- Problem: The gate opens only after `switchStream(B)` has run on the main thread. By then ExoPlayer has already cancelled A's playlist loader, so A's parse error is never produced at all. The window the spec names is different: `onPlayerError` attributes the error to `currentStreamUrl` at `PlaybackController.kt:260-264`, and the stall-handler post can also land late. That window needs an error that is already queued for the app looper when `switchStream` sets `currentStreamUrl`, and this ordering never creates that state. T16 shows that the route fails when there is no switch. It does not show that T15 could ever see a stale error.
- Failure scenario: Suppose `onPlayerError` mislabels an already-queued A failure with B's URL. T15 still passes, because no queued failure ever exists in its run. The evidence would then record "not reproduced" for a defect the test cannot reach.
- Fix: Change the evidence and doc wording to "not reproduced for a load cancelled by switchStream; the queued-error window is not exercised". Or add a case that holds the failure on the playback thread, for example a `Truncated` segment with the gate opened in the same main-thread turn just before `switchStream`, and record which window each case covers.

### F2 [minor] The corrupt-cache port cannot fail on the recovery it is named after
- Where: app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerResourcesTest.kt:49-66
- Problem: The corrupt step accepts either outcome (`corrupt.sawReady || corrupt.error.get() != null`), so a player that fails on the corrupt entry also passes. The "without poisoning next scenario" part is then made true by the test itself: it calls `harness.cache.removeResource(url)` and asserts the spans are empty before the clean run. It never checks what the controller left behind. `docs/testing/player-playback.md` lists the case as plainly "ported".
- Failure scenario: Remove `FLAG_IGNORE_CACHE_ON_ERROR` at `PlaybackController.kt:643`. Playback of a corrupt entry then fails, and the test still passes.
- Fix: Either assert recovery (`awaitReady(corrupt)`, `assertNull(corrupt.error.get())`, and the server journal shows a request for `path` after the corrupt write), or run the clean scenario without removing the resource first. Otherwise mark the row "ported, outcome not asserted" in the doc.

### F3 [minor] AC-9: the main checkout's `check.full` still does not run the module tests
- Where: /home/dev/Puber/.flow/config.yaml:13
- Problem: AC-9 and T11 need both `pr-checks.yml` and `check.full` to run `:player-test-fixtures:testDebugUnitTest`. Only CI does today (`pr-checks.yml:95`). The plan hands the config edit to the conductor at ship time, and qa-report rates AC-9 "PASS for the branch part". Until that edit lands, the project gate never runs T1 to T4 or the close-stall regression test.
- Failure scenario: A later run breaks `HermeticTestServer` range handling. Its `check.full` stays green and only CI catches it.
- Fix: Add `:player-test-fixtures:testDebugUnitTest` to `check.full` in /home/dev/Puber/.flow/config.yaml before gate 2, and grep for it as T11 specifies.

### F4 [nit] The close-stall regression test depends on a 300 ms sleep for its red state
- Where: player-test-fixtures/src/test/kotlin/com/kino/puber/playertestfixtures/server/HermeticTestServerTest.kt (`closeDoesNotStallARequestThatIsStillBeingDispatched`, `Thread.sleep(300)`)
- Problem: Nothing makes sure the closer thread has reached `server.close()` before `proceed.countDown()`. On a slow host the dispatch can finish first, and the test would then pass on the old code that closed under the lock. It can never fail falsely, only lose its red state.
- Fix: Count down a latch right before `server.close()` in the closer thread (or poll `closer.state == WAITING/BLOCKED`) instead of the fixed sleep.

### Unproven
- PlaybackControllerStaleCallbackTest.kt:72 fixed `SETTLE_MS = 3_000` window for the negative assertion. This only matters if a stale callback arrives later than 3 s after B is READY, which would take a retry delay longer than that; nothing in `PlaybackNetworkTuning` shows one for a `ParserException`.
