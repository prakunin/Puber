# itest-incomplete-run-green device evidence

Build: worktree itest-incomplete-run-green at 7d329c87 (base 63771351), make FLAVOR=instrumentation GRADLE=./tools/agentw.
Device: emulator host.docker.internal:5555 (sdk_google_atv64_arm64 - 16), leased; the owner's box was not used as instructed. Released afterwards.

## Run 1: TESTS=com.kino.puber.ui.feature.player.vm.PlaybackControllerStaleCallbackTest
Expected: exit 0, XML found by the new check.
Observed: make exit 0, BUILD SUCCESSFUL. AGP wrote one file `app/build/outputs/androidTest-results/connected/debug/flavors/instrumentation/TEST-sdk_google_atv64_arm64 - 16.xml` (name with spaces), testsuites tests=4 failures=0. The check accepted it. Result: PASS (confirms the AGP results path).

## Run 2: TESTS=com.kino.puber.NoSuchTest
Expected: zero-test message.
Observed: make exit 2. Gradle itself failed (connectedInstrumentationDebugAndroidTest FAILED, BUILD FAILED), because AGP reports an unknown class as one failing case in the XML (testsuite com.kino.puber.NoSuchTest tests=1 failures=1). The zero-test check did not fire (Gradle status non-zero, so the result scan is skipped; today's behaviour kept). Result: run fails as wanted, via Gradle not the new check. The zero-test branch stays covered only by the fake-Gradle test (AC-3).
