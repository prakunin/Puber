I ran the touched checks. `:player-test-fixtures:testDebugUnitTest` passes: 17 tests, 0 failures, including the 3 new `HermeticTestServerTest` cases. `:app:compileInstrumentationDebugAndroidTestKotlin` passes. Gradle reported both tasks as up to date, so the results come from the last build at this HEAD (results timestamped 10:42 today); nothing was re-executed. `tools/tests/test_ci_workflows.py` passes with the new CI step. The instrumented tests were not re-run by me. Device run 6 at 73f3b5a4 recorded 3 of 3 complete runs with 20 tests, 0 failures and 1 skipped (the `@Ignore`d probe). The run `player-callback-gate` exists in `/home/dev/Puber/.flow/runs/`, so AC-5's "reproduced" branch is satisfied.

```
LENS: tests  ROUND: 2  BASE: f53f2144
### F1 [minor] Cache-cleanup assertion dropped when removeResource came back
- Where: app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerResourcesTest.kt:56
- Problem: 7e86a573 removed `harness.cache.removeResource(url)` and `assertTrue(harness.cache.getCachedSpans(url).isEmpty())`. 73f3b5a4 put the `removeResource` call back but not the assertion. Fix round 3 in the plan says nothing about dropping it. The only place that checks cleanup is now `assertCacheOwnershipReleased` at the end. By then the clean scenario has already loaded, so a corrupt span left behind can only show up indirectly, as a clean-run failure.
- Failure scenario: the corrupt span is still locked or held after `releaseAndDrain()`, so `removeResource` leaves it in place. The clean prepare then fails or times out with "Source error". The failure message points at the clean load, not at the cleanup that did not happen, and it reads like the corrupt-cache defect tracked in `player-corrupt-cache-recovery` rather than a fixture-cleanup bug.
- Fix: restore `assertTrue(harness.cache.getCachedSpans(url).isEmpty())` right after `harness.cache.removeResource(url)`.

### F2 [nit] Test name still claims recovery the test no longer asserts
- Where: PlaybackControllerResourcesTest.kt:39 (`emptyCache_loadsNormally_andCorruptOwnedEntryRecoversWithoutPoisoningNextScenario`)
- Problem: the corrupt step now only waits for a terminal outcome (READY or an error). Device run 5 shows the step ends in an error. The name keeps the upstream wording for the port mapping, and `docs/testing/player-playback.md` already says "ported, outcome not asserted". Only someone reading the test name alone would be misled.
- Failure scenario: none at runtime. A reader of a green JUnit report takes it as evidence that corrupt-cache recovery works.
- Fix: optional. Add a KDoc line on the test pointing to `player-corrupt-cache-recovery`, or leave it since the doc row covers it.

### Unproven
- player-test-fixtures/.../HermeticTestServer.kt:516 an error while writing a body is still journaled as `Completed(599)`, even on Truncated/Disconnected routes. If the client aborted before the partial body was fully written, `NetworkTracksTest` would see `Completed(599)` instead of `Truncated(200)` and fail. This becomes real only if a device run shows that outcome; devices runs 6 (3 of 3) are green.
```

Everything else in the incremental diff has a test that would fail if the behaviour broke:
- **Server fixes:** the occurrence-under-lock, nested Range and truncation-outcome fixes each have a JVM test that failed before the fix, as the plan records.
- **Queued-failure probe:** it guards against passing for nothing with `assertNotNull(probe.error)`, and it is `@Ignore`d with the slug as AC-5 requires.
- **Earlier switch case:** it now waits on A's request outcome in the journal and checks the player's current media item, which replaces the `StreamReady.url` check that could not fail.
- **Close-stall test:** it no longer depends on a 300 ms sleep.
