# Test design: player-track-label-resource-provider

Kind bug, size S. ACs: `brief.md#Acceptance criteria`. Levels: `instrumented` (emulator, `make FLAVOR=instrumentation DEVICE=host.docker.internal:5555 GRADLE=./tools/agentw itest TESTS=<fqcn>` under a lease), `device` (box), `static` (grep/compile).

## Risk profile
| Risk | P | I | Score | Test |
|---|---|---|---|---|
| Fallback label still read from `Context` (bug stays; stale language after switch) | 3 | 2 | 6 | AC-1 stub-marker test |
| `player_error_playback` (:193, :266, :634) left on `Context` | 2 | 2 | 4 | P1 error-marker test; AC-2 grep |
| DI wiring in `PlayerScreen.kt:58` wrong arity or unresolved `ResourceProvider` (runtime crash only) | 2 | 3 | 6 | AC-4 box run |
| Harness default (`AndroidResourceProvider(targetContext)`) breaks existing fallback tests | 1 | 2 | 2 | AC-3 guard; accepted otherwise |

## Traceability
| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | instrumented | P0 `red` | `PlaybackControllerNetworkTracksTest.unlabelledAudio_usesInjectedResourceProviderForFallbackLabel` (new; stub provider delegates to `AndroidResourceProvider`, overrides only `player_audio_track_fallback` with a marker) | Fails if the controller still reads `context.getString` at :874: label would be the real "Track 1", not the marker |
| AC-1 | instrumented | P1 `red` | `PlaybackControllerResourcesTest.corruptCacheError_usesInjectedResourceProviderMessage` (new; same stub overriding `player_error_playback`; reuse the corrupt-cache scenario of `emptyCache_loadsNormally_andCorruptOwnedEntryRecoversWithoutPoisoningNextScenario`; asserts the `onError` message (`probe.error`) or `StreamFailure` message equals the marker, whichever the scenario emits) | Fails if :193/:634 stay on `context`. If the scenario reaches neither path, drop to P2 and rely on AC-2 |
| AC-2 | static | P1 | `rg -n "context\.getString" app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt` returns nothing; `:app:compileDevDebugKotlin` passes | Fails if any of the four reads remains or the constructor/DI arity is off. Grep is a gate, not a behaviour test |
| AC-3 | instrumented | P0 `guard` | `PlaybackControllerNetworkTracksTest.unlabelledAudio_reportsLocalizedFallbackRowMarkedAsFallback`, `PlaybackControllerNetworkTracksTest.progressiveUndeterminedAudio_reportsLocalizedFallbackRow` (assert against `harness.resources.getString(...)`) | Fails if the default provider yields a different row label or `isFallbackLabel` regresses |
| AC-4 | device | P0 `guard` | Manual check 1 | Fails on `NoBeanDefFoundException`/`InstanceCreationException` or player not starting |

## Bug: failing-first test
`unlabelledAudio_usesInjectedResourceProviderForFallbackLabel`. On base (`git -C <wt> show 8db929b5:app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt`) the controller has no `resources` parameter and reads `context.getString` at :874, so the marker can never appear. The test is compile-red on the base (harness has no `resources` parameter). For an assertion-red, the implementer first adds only the harness parameter and stub, runs the test (fails: label is "Track 1", not the marker), then makes the controller change.

## Manual checks
1. Install the worktree's dev APK on `192.168.1.110:5555` with `tools/android-apk-install-preserve` (`com.kino.puber.stage` only), lease held. Open any item and press OK to play. Expected: player renders and playback starts. Must not happen: crash; logcat (filtered to the package) containing `NoBeanDefFoundException` or `InstanceCreationException`.
2. Open the audio picker (D-pad). Expected: rows list as before; no empty or resource-id labels. Must not happen: change of the stored audio preference or language.

No language switch is driven on the device (stub provider covers the source; `AppLocale` stays untouched).

## UNTESTABLE
None. The real re-wrap after `AppLocale.store` belongs to `AndroidResourceProvider` and is not exercised here (brief Q2).
