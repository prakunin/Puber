LENS: tests  ROUND: 1  BASE: 8ffdbca1

### F1 [minor] The "no terminal Error" assertion can pass before a late Error arrives
- Where: `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerResourcesTest.kt:59-77`
- Problem: The test waits for `corrupt.error != null` and for the first `StreamFailure`. It then takes one snapshot of the events and asserts `none { it is PlaybackEvent.Error }`. The brief notes that `routeError` can re-prepare and report again. Nothing waits for the system to settle, so an `Error` posted after the snapshot goes unseen.
- Failure scenario: A regression where the controller emits `StreamFailure(NEXT_URL)` first and a terminal `Error` a few ms later still passes. The VM would then show an error screen instead of failing over, and the test would not catch it.
- Fix: Take the snapshot after `releaseAndDrain()`, because that call drains the callbacks. Alternatively, `awaitCondition` for the player to be idle or released before the `none { Error }` check.

### F2 [nit] The corrupt scenario does not assert that playback never reached READY
- Where: `PlaybackControllerResourcesTest.kt:59`
- Problem: `corrupt.sawReady` is not asserted false. `StreamFailure` plus `error != null` already implies the failure, and the old `sawReady ||` branch is gone. If a probe error and a READY ever coexisted, nothing would flag it.
- Fix: Optionally add `assertFalse(corrupt.sawReady)`.

I did not run `check.full`, because the diff changes only an instrumented test and docs. I judged AC-5 from the diff alone. The emulator results are in the evidence files, which I read but did not re-run.

Coverage:
- **AC-1:** Covered by the new wait and `StreamFailure(NEXT_URL, url)` assertions.
- **AC-2:** The evidence shows three implementer runs and one QA run, all 3/3 passing.
- **AC-3:** The mutant evidence shows a real failure at the new wait (the XML points at line 59). That is a valid substitute for a base-red run, and the test design says so.
- **AC-4:** The KDoc and `docs/testing/player-playback.md:87` both say the corrupt entry fails over with `StreamFailure(NEXT_URL)`.
- **No weakened assertions:** The old `sawReady || error` wait was strengthened, not removed.

### Unproven
- `PlaybackControllerResourcesTest.kt:59` The corrupt span may reach READY with no error on some other device, which would time out the wait. Evidence would be a device run where this wait times out; the brief accepts reporting that as a finding.
