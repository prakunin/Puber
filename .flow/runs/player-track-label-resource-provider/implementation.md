# Implementation record: player-track-label-resource-provider

## Commits
| sha | subject |
|---|---|
| a0dc2dd3 | Resolve the player's error and audio fallback strings through ResourceProvider |
| 609a7c43 | Prove the player audio fallback label comes from the injected ResourceProvider |

Plan tasks 1-2 are the first commit, tasks 3-4 the second.

## Red run
- red: PlaybackControllerNetworkTracksTest.unlabelledAudio_usesInjectedResourceProviderForFallbackLabel - `ComparisonFailure: expected:<[injected-fallback-label]> but was:<[Track 1]>` (harness parameter and stub in place, controller unchanged; itest-1.xml).
- guard: the two fallback tests at :166/:182 now assert against `harness.resources`; they pass on the final code.
- P1 `corruptCacheError_usesInjectedResourceProviderMessage` not written: the three `player_error_playback` reads are covered by AC-2 (no `context.getString` remains) and compile; no cheap deterministic scenario reaches :193/:634 with a stub.

## Results
- itest-2.xml: PlaybackControllerNetworkTracksTest 14 tests, 0 failures (emulator host.docker.internal:5555, lease released, emulator stopped).
- check.full: BUILD SUCCESSFUL in 4m 7s, followed by the baseline-profile and itest tooling scripts, all PASS.
- AC-2: `context.getString` no longer appears in PlaybackController.kt.

## Deviations
- P1 error-marker test omitted (above).
- The new test closes the setUp harness and builds its own with the stub, so one test class runs one server at a time.

## For reviewers
- Constructor gained `resources` as the fifth parameter; `PlayerScreen.kt` passes a fifth `get()`. A missing global `ResourceProvider` binding would fail only at runtime: AC-4 on the box (left to qa).
