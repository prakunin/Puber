# Device run 4 (conductor), 2026-11-27, after da1c1f89

Emulator host.docker.internal:5555 (sdk_google_atv64_arm64, Android 16, API 36). Lease acquired and released through tools/emulator-resource-lock.sh; emulator started with ssh mac-emu start and stopped after. TV box untouched. No uninstall, no data clear.

Command, five times in a row, from /home/dev/orca/workspaces/Puber/player-hermetic-fixtures:
AGENT_GRADLE_SERIAL=1 make FLAVOR=instrumentation DEVICE=host.docker.internal:5555 GRADLE=./tools/agentw itest TESTS=com.kino.puber.ui.feature.player.vm.PlaybackControllerDeviceTest,com.kino.puber.ui.feature.player.vm.PlaybackControllerNetworkTracksTest,com.kino.puber.ui.feature.player.vm.PlaybackControllerResourcesTest,com.kino.puber.ui.feature.player.vm.PlaybackControllerStaleCallbackTest

Result: exit 0 in all five runs; 19/19 tests pass in each (JUnit XML itest-r4-1..5.xml in this folder).

- PlaybackControllerDeviceTest 5/5, PlaybackControllerNetworkTracksTest 9/9, PlaybackControllerResourcesTest 3/3, PlaybackControllerStaleCallbackTest 2/2.
- AC-5: T15 switchStreamBeforeDelayedFailure_reportsNoFailureForPreviousStream passes and T16 control delayedFailure_reportsStreamFailure_whenStreamIsNotSwitched passes in every run (rounds 2-4, 10 runs): the stale callback is not reproduced; player-callback-gate is not opened.
- History: round 1 13/19 (main-thread reverse DNS, fixed 1837ce91); round 2 isolation assertion and a teardown flake (542b65c5); round 3 flake persisted 2 of 3 runs (root cause: server.close under the dispatch lock, fixed da1c1f89).
