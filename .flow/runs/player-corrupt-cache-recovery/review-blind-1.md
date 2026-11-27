LENS: blind  ROUND: 1  BASE: 8ffdbca1

The diff has no production code. It changes only the assertions in `PlaybackControllerResourcesTest.emptyCache_loadsNormally_andCorruptOwnedEntryRecoversWithoutPoisoningNextScenario`. I did not run it. It is an instrumented device test, and I have read-only access here.

### F1 [minor] The "no terminal Error" assertion can pass before a late Error arrives
- Where: `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerResourcesTest.kt:58-77`
- Problem: The test waits for `StreamFailure`, then takes one snapshot with `harness.events.since(mark)`. It checks `none { it is PlaybackEvent.Error }` on that snapshot only. The harness records events from callbacks, so an `Error` delivered after the snapshot goes unseen. `releaseAndDrain()` follows, but its events are never asserted.
- Failure scenario: A regression makes the controller send `StreamFailure(NEXT_URL)` and then also a terminal `Error` a moment later. The test still passes.
- Fix: Assert on `harness.events.since(mark)` again after `releaseAndDrain()`, or re-check for `Error` once the drain has finished.

### F2 [nit] The test name still says "recovers"
- Where: `PlaybackControllerResourcesTest.kt:43`
- Problem: The name `...andCorruptOwnedEntryRecoversWithoutPoisoningNextScenario` is now wrong. The test asserts failover with `NEXT_URL`, and the player errors out (`corrupt.error != null`). It does not recover.
- Fix: Rename the test to describe the failover behaviour. For example, `corruptOwnedEntryFailsOverToNextUrl_andDoesNotPoisonNextScenario`.
