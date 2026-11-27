Round 1

CHECK: check.full (./tools/agentw testDevDebugUnitTest :player-test-fixtures:testDebugUnitTest :app:detektAll :app:lintDevDebug :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*' :app:detektInstrumentationDebug :app:lintInstrumentationDebug && bash tools/tests/test-baseline-profile-tooling.sh) -> pass (exit 0, BUILD SUCCESSFUL, all baseline-profile tooling tests PASS; most Gradle tasks were UP-TO-DATE from the implementation run, PlaybackCallbackGateTest result XML shows 10 tests, 0 failures, 0 skipped)
DEVICE: 192.168.1.110:5555 (AC-6, com.kino.puber.stage only); host.docker.internal:5555 emulator (AC-1, AC-4 guards, per spec Q2)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | Re-run by QA on the emulator, 3 consecutive runs, @Ignore removed, test body unchanged (git diff: import, annotation, KDoc only). Each XML: tests=3 failures=0 errors=0 skipped=0 with the three named cases incl. switchStreamAfterQueuedFailure_reportsNoFailureForPreviousStream. Impl run also has itest-red.xml (1 failure before the fix) |
| AC-2 | PASS | PlaybackCallbackGateTest: dispatch_runsActionWithSessionUrl_whenSessionIsCurrent, dispatch_skipsAction_whenNewerSessionBegan, dispatch_skipsAction_afterInvalidate pass (10/10 in the class, run in check.full) |
| AC-3 | PASS | routeError_reportsNothingAndRepreparesOnce_whenSwitchSessionHasNotLoadedItsUrl, routeError_reportsSecondErrorWithSessionUrl_afterStaleRetry, routeError_reportsNothingAndReprepares_whenLoadStartOfPreviousUrlArrives, routeError_reportsWithSessionUrl_whenSwitchSessionLoadedItsUrl, routeError_reportsFirstError_whenSessionComesFromPrepare, routeError_doesNothing_whenSessionIsNotCurrent, routeError_allowsOneRetryPerSession_whenAnotherSwitchBegins |
| AC-4 | PASS | Code review of PlaybackController.kt diff: SessionListener (per session) sends ENDED, READY (with the dispatched URL), tracks, play state through gate.dispatch; onPlayerError through gate.routeError with report(url) using the session URL; no listener path reads currentStreamUrl. Guards on the emulator: PlaybackControllerResourcesTest 3/0 (XML), NetworkTracksTest and DeviceTest make exit 0 (XML not kept; impl itest-guards.xml has 14 cases, 0 failures) |
| AC-5 | PASS | switchStream has no stop(); the only player.stop() left is in disableAc3AndRetry (line ~641), unchanged. KDoc moved onto switchStream |
| AC-6 | PASS | Box, worktree dev APK (sha256 f9be4459... verified on device). Movie played; switch 1 to 720p: resumed at 1:46 vs running position 1:48, stream 1280x534, playing; switch 2 to 1080p: 2:18 vs 2:20, stream 1920x800, playing. No error screen, no restart, no crash, no pairing. Master dev APK restored (sha256 64b7d48f... verified), logged in |
| AC-7 | PASS | docs/testing/player-playback.md lines 11 and 63 now say the queued failure is neither reported nor blocks loading, and the case "Passes; guards the stale-error re-prepare"; no "tracked"/@Ignore wording left (diff read) |
| AC-8 | PASS | check.full green, see CHECK |
TESTS ADDED: none (implementation already added PlaybackCallbackGateTest; no gaps found for testable ACs)
EVIDENCE: /home/dev/orca/workspaces/Puber/player-callback-gate/docs/superpowers/evidence/2026-11-27-player-callback-gate-device.md (audit passed; not committed, the conductor commits)
DEFECTS: none
UNAVAILABLE: none
MANUAL: none required. Optional observation for the owner or reviewer: after the first quality switch the Audio row in Settings read "Track 1" instead of "01. Дубляж (RUS)" and stayed so after the second switch. I did not compare against a master build, so it may be pre-existing (HLS track labels of the switched stream); if it is a regression it would come from the per-session listener's onTracksChanged path. Check by repeating one quality switch on the master APK now on the box.
