# Implementation record: subtitle-switch-text-disable

Base 7a9fd580. Tests and docs only; `app/src/main` unchanged. Plan = brief Plan, all four tasks done (one commit holds tasks 1-4; the second is the detekt `LargeClass` suppression the class needed after the two unit tests).

## Commits

```
f143b7d9 Allow the track selection test class to exceed the detekt size limit
bb8a704b Pin the fork's subtitle carry-over on a quality switch with instrumented and unit tests
 .../vm/PlaybackControllerNetworkTracksTest.kt      | 92 ++++++++++++++++++++++
 .../feature/player/vm/PlaybackControllerReads.kt   |  7 ++
 .../feature/player/vm/PlaybackControllerRoutes.kt  | 46 +++++++++++
 .../player/vm/PlayerVMTrackSelectionTest.kt        | 31 ++++++++
 docs/testing/player-playback.md                    | 15 ++++
 5 files changed, 191 insertions(+)```

## Red run

No failing-first test (test design: option B, every test is `guard`, green on base by construction).

## Mutation checks (applied locally, reverted, never committed)

| Test | Mutation | Result |
|---|---|---|
| `qualitySwitch_missingSubtitleLanguage_staysOffAtReusedCoordinates` | `SubtitleTrackSelector.select` returns `candidates.firstOrNull()` (positional) | fails (only this test) |
| `qualitySwitch_sameManifestSubtitle_reselectsByIdentity` | `switchStream` clears `pendingSubtitleTrack`, text overrides and preferred language | fails (also `hlsSwitch_preservesPositionIntentTracks_andLastStreamWins`, `progressiveSwitch_restoresSelectedSubtitleAndPausedIntent`) |
| `selectQuality_withSubtitleRowMissingFromNewReport_sendsOffRow` | `PlayerVM.onTracksUpdated` drops the final `playbackController.selectSubtitle(row)` | fails (plus 7 existing tests) |
| `selectQuality_withIdenticalRows_keepsSubtitleWithoutResending` | the re-send condition loses `row != previousRow` | fails (plus 4 existing tests) |

## Instrumented runs (emulator host.docker.internal:5555, lease held, released; emulator stopped)

`make FLAVOR=instrumentation DEVICE=host.docker.internal:5555 GRADLE=./tools/agentw itest TESTS=com.kino.puber.ui.feature.player.vm.PlaybackControllerNetworkTracksTest`: three runs on final code, 16 tests, 0 failures each (`itest-1.xml`, `itest-2.xml`, `itest-3.xml`).

## Check

`check.full` exit 0 (unit tests, detekt, lint, profile and itest tooling scripts, all PASS).

## Deviations

- Instrumented tests pass `subtitles = null` and build the master with the new `subtitleVariantMaster` and `manifestSubtitleRoutes` helpers; `textTrackLanguages()` added to `PlaybackControllerReads.kt` so the tests wait for the new source's text group before asserting (an assertion of "no selected text track" would otherwise pass vacuously).
- `@Suppress("LargeClass")` on `PlayerVMTrackSelectionTest` (repo precedent: five other test classes) instead of splitting the shared private helpers.
- Unit test names differ slightly from the test design (`selectQuality_withSubtitleRowMissingFromNewReport_sendsOffRow`, `selectQuality_withIdenticalRows_keepsSubtitleWithoutResending`); the docs list the final names.

## Reviewers

Check the Spanish-VTT absence assertion (journal path `/media/hls/sub_es.vtt`) and that the docs section matches the pinned semantics.

## Fix round 1

| # | Result |
|---|---|
| 3 | fixed: `qualitySwitch_missingSubtitleLanguage_staysOffAtReusedCoordinates` samples both negative checks every 100 ms over a 1 s settle window (`NEGATIVE_SETTLE_MS`, `NEGATIVE_POLL_MS`) before the unknown-request check |
| 4 | fixed: test renamed `qualitySwitch_sameManifestSubtitle_staysSelected`, comment claims only that the same English subtitle stays selected; docs and device evidence updated. Mutation row above still applies to the renamed test |

Checks: `:app:compileDevDebugKotlin`, `:app:compileInstrumentationDebugAndroidTestKotlin`, `:app:detektInstrumentationDebug` BUILD SUCCESSFUL. `PlaybackControllerNetworkTracksTest` on the emulator under lease, two runs, 16 tests, 0 failures each (`itest-fix1-1.xml`, `itest-fix1-2.xml`); lease released, emulator stopped. The first run's XML was overwritten by the second make invocation, so `itest-fix1-1.xml` is the XML of the second of three executions and `itest-fix1-2.xml` of the third.
