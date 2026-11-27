Round 3

CHECK: AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :player-test-fixtures:testDebugUnitTest :app:detektAll :app:lintDevDebug :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*' :app:detektInstrumentationDebug :app:lintInstrumentationDebug && bash tools/tests/test-baseline-profile-tooling.sh -> pass (exit 0; tooling script all PASS). python3 tools/tests/test_ci_workflows.py -> all PASS.
DEVICE: not used by the tester. Conductor-run on the emulator host.docker.internal:5555 (API 36), the Q1 device: device-run-5.md, device-run-6.md, itest-r5-*.xml, itest-r6-*.xml.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | PlayerTestFixturesTest 7/7, HermeticTestServerTest 10/10 (range 206, journal, HTTPS); `sha256sum -c SHA256SUMS` has 0 non-OK lines |
| AC-2 | PASS | `:app:dependencies` match count for player-test-fixtures or mockwebserver3: devDebug 0, prodRelease 0, instrumentationDebugRuntime 0, instrumentationDebugAndroidTestRuntime 2 |
| AC-3 | PASS | 33 profile unit tests pass (BaselineNetworkBlockerTest 11, EnvironmentTest 3); the only `object : ProxySelector()` is BaselineNetworkBlocker.kt:29 |
| AC-4 | PASS (conductor-run) | itest-r6-1..3: each exit 0, 20 tests, 0 failures, 1 skipped (the @Ignore'd probe case), no testRunFailed; MP4/HLS reach READY, advance, end; isolation test passes. Earlier 5 of 5 at da1c1f89 (itest-r4) |
| AC-5 | PASS (conductor-run), outcome "reproduced" | itest-r5-1,3: switchStreamAfterQueuedFailure_reportsNoFailureForPreviousStream fails: A's failure is reported as StreamFailure carrying B's URL, B never requested. Fail branch matches the AC: run player-callback-gate exists (.flow/runs/player-callback-gate/state.md, stage idea, source player-hermetic-fixtures); the failing case is committed `@Ignore("player-callback-gate: ...")` (PlaybackControllerStaleCallbackTest.kt:84), not deleted; doc names it as reproduced. The cancellation-ordering probe and the control pass in every run |
| AC-6 | PASS | all 20 upstream names are in docs/testing/player-playback.md (grep -F over the test-design table), each "ported" or "not ported: <reason>" |
| AC-7 | PASS | `git diff master --stat -- app/src/main` prints nothing |
| AC-8 | PASS | doc has the make itest command (lines 16-17), device rule (23-28), `sha256sum -c SHA256SUMS` (49), generator marked a maintainer tool outside the gate (52) |
| AC-9 | PASS for the branch part | pr-checks.yml:95 runs :player-test-fixtures:testDebugUnitTest (and compiles the instrumentation test sources), :104 uploads its reports; check.full passes. The main checkout's .flow/config.yaml does not list the task yet (conductor's part at ship) |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/player-hermetic-fixtures/docs/superpowers/evidence/2026-11-27-player-hermetic-fixtures-device.md (updated for rounds 5-6; tools/mobile-evidence-audit.sh passed, 402 files). Not committed.
DEFECTS: none in this branch. The reproduced stale-callback defect is tracked as run player-callback-gate (the probe's result, not a branch defect).
UNAVAILABLE: none
MANUAL: none. Notes: AC-4/AC-5 rest on conductor-run results, not on a tester run. make itest can exit 0 on an incomplete run (device-run-5 run 2); tracked as itest-incomplete-run-green, and the r6 XMLs were checked for test count.
