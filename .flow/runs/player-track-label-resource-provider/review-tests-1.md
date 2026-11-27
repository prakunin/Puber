LENS: tests  ROUND: 1  BASE: 8db929b5

### F1 [minor] Three of the four rerouted strings have no behavioural test
- Where: `/home/dev/orca/workspaces/Puber/player-track-label-resource-provider/app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:195, :268, :636`. These are the `player_error_playback` reads in `onStalled`, `onPlayerError` and `disableAc3AndRetry`. The test is also missing from `PlaybackControllerResourcesTest.kt`.
- Problem: `test-design.md` lists the P1 test `PlaybackControllerResourcesTest.corruptCacheError_usesInjectedResourceProviderMessage`. It was not written. `implementation.md` records it as omitted, with the reason "no cheap deterministic scenario". The design said to downgrade to P2 and rely on AC-2 if the scenario reaches neither path. That downgrade is recorded only in the implementation record. `test-design.md` still lists it as P1 `red`.
- Failure scenario: someone reverts one of the three error reads to `context.getString(...)`. The code still compiles, because `context` stays for Media3. No test fails, and the stale-language error message comes back. AC-2 is a grep done by hand, not an automated test.
- Fix: either add a small unit-level or instrumented test that drives `onPlayerError` with an error whose `localizedMessage` is null. It would assert that the `onError` or `onStreamFailure` message equals a stub marker. Or update `test-design.md` to record the P1 to P2 downgrade and state that AC-2 is gated by a grep. Better still, add a grep step to `check.full`.

### Checked and fine
- **AC-1 (P0 `red`):** `unlabelledAudio_usesInjectedResourceProviderForFallbackLabel` fails on the base commit. The base has no `resources` constructor parameter, so it would not compile. The recorded red run shows `expected:<[injected-fallback-label]> but was:<[Track 1]>`. That is a genuine assertion failure with the harness parameter in place and the controller unchanged. The stub overrides only the fallback resource id, so the test would fail if the `:874` read went back to `context`. It also asserts `isFallbackLabel`.
- **AC-3 (P0 `guard`):** the two existing fallback tests now compare against `harness.resources`. They are documented as guard tests, not red. They pass in `itest-2.xml` (14 tests, 0 failures).
- **AC-4:** the device evidence file covers it. It records a PASS, with zero `NoBeanDefFoundException` and zero `InstanceCreationException`.
- **AC-2:** `grep context.getString` on `PlaybackController.kt` returns nothing.
- **Harness handling in the new test:** it closes the harness made in `setUp` and builds its own. The `@After` hook closes whichever harness `harness` holds at that point, so there is no leak.
- **`resources.getString(R.string.player_audio_track_fallback, 1)` in the existing tests:** this default-provider assertion is weaker than a locale-switch test. That matches the brief's Q2.

I did not re-run the instrumented tests. I relied on the recorded results in `itest-1.xml` and `itest-2.xml`.

### Unproven
- `PlayerScreen.kt:58` Koin arity and resolution of the global `ResourceProvider`: it is covered by the AC-4 device run. No unit-level DI test exists. Evidence of a regression would be a DI refactor that moves the `ResourceProvider` binding out of the global scope.
