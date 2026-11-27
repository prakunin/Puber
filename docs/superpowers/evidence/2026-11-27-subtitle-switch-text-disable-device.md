# subtitle-switch-text-disable: emulator verification (round 1)

Build: worktree HEAD f143b7d9 (base 7a9fd580). Tests and docs only; app/src/main unchanged.
Device: emulator host.docker.internal:5555 (AVD puber_tv_36), lease held and released, emulator stopped afterwards.
The owner's box was not used: no production change.

## Instrumented run

Command: make FLAVOR=instrumentation DEVICE=host.docker.internal:5555 GRADLE=./tools/agentw itest TESTS=com.kino.puber.ui.feature.player.vm.PlaybackControllerNetworkTracksTest

Observed: BUILD SUCCESSFUL, result XML tests=16 failures=0 errors=0 skipped=0 (20.2 s).

| AC | Test | Expected | Observed | Result |
|---|---|---|---|---|
| AC-1 | qualitySwitch_missingSubtitleLanguage_staysOffAtReusedCoordinates | No text track selected after the switch, no Spanish VTT request | testcase present in the XML, no failure | PASS |
| AC-2 | qualitySwitch_sameManifestSubtitle_reselectsByIdentity | English text track selected again without another selectSubtitle call | testcase present in the XML, no failure | PASS |

## Unit run (forced rerun)

cleanTestDevDebugUnitTest testDevDebugUnitTest --tests '*PlayerVMTrackSelectionTest': tests=50 failures=0, includes AC-3 and AC-4 tests.
