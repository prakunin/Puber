# Round 1

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; test XML: TmdbApiClientTest 9/9, SkipSegmentServiceTest 4/4, SkipSegmentPrivacyTest 3/3, EpisodeSchedulePrivacyTest 1/1, 0 failures)
DEVICE: unavailable: emulator-5554 leased; dev APK got corrupted on the device (on-device sha256 differs from built APK at same size), app crashes at start with ClassNotFoundException; re-install blocked (signer_unknown). No uninstall attempted. Lease released.

| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | grep for apiResult/kotlinx CancellationException: none; `= runCatchingCancellable {` count 4; compile green |
| AC-2 | PASS | scheduleEndpoint_rethrowsCancellation, findByImdbId_rethrowsCancellation green |
| AC-3 | PASS | findLookups_wrapErrorsIntoFailure green |
| AC-4 | PASS | findTvByImdbId_returnsFailureForHttpErrors green |
| AC-5 | PASS | findByImdbId_prefersTvThenMovieAndFailsOnHttpError green |
| AC-6 | PASS | same test + findTvByImdbId_usesNormalizedIdAndOnlyTvResults, ..._returnsNoMatchWithoutFallingBackToMovieResults green |
| AC-7 | PASS | code read: single private find(imdbId) with one requireSuccess; both lookups use it |
| AC-8 | PASS | SkipSegmentServiceTest.skipsPrimaryAndAsksFallback_whenTmdbLookupFails green |
| AC-9 | PASS | full check green; privacy tests unmodified (git diff empty) |
| AC-10 | NOT CHECKED | device smoke blocked, see above |

TESTS ADDED: none (implementer's tests already cover all testable ACs)
EVIDENCE: /home/user1/orca/workspaces/Puber/tmdb-result-handling/docs/superpowers/evidence/2026-10-12-tmdb-result-handling-device.md (commit f2c7048d; audit passed)
DEFECTS: none in the change
UNAVAILABLE: device smoke (M1, M2): the on-device APK is corrupt and install-preserve is blocked
MANUAL: owner decides how to repair emulator-5554 (reinstall needs authorization for uninstall or a signer-unknown replace), then re-run M1/M2.

# Round 2

CHECK: not re-run (passed in round 1, code unchanged since 0f153791)
DEVICE: emulator-5554 (on-device sha256 equals build 4270beef...472851c; leased and released; no install)

| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | round 1 |
| AC-2 | PASS | round 1 |
| AC-3 | PASS | round 1 |
| AC-4 | PASS | round 1 |
| AC-5 | PASS | round 1 |
| AC-6 | PASS | round 1 |
| AC-7 | PASS | round 1 |
| AC-8 | PASS | round 1 |
| AC-9 | PASS | round 1 |
| AC-10 | PARTIAL | M1 PASS: schedule screen shows "No schedule available / TMDB access isn't set up", no crash, Back works (focus lands on play button, not the calendar button). M2: playback of Outlander S2E3 started after resume and ran ~15 s without crash or stall; IntroDB skip prompt NOT CHECKED (playhead mid-episode, seeking would alter watch progress) |

TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/tmdb-result-handling/docs/superpowers/evidence/2026-10-12-tmdb-result-handling-device.md (audit passed, committed in wt)
DEFECTS: none
UNAVAILABLE: live TMDB success on device (no token, owner decision Q3)
MANUAL: on a series near its intro (start from beginning), confirm the IntroDB.app skip prompt appears; note a short resumed playback was reported to the account's watch progress.
