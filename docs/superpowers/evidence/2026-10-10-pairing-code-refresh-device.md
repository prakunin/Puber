# pairing-code-refresh: verification evidence (2026-10-10)

Build: dev debug, 1.9.69-dev, branch commit 46740a60, built from the worktree
(`app-dev-debug.apk`, sha256 9c8a6ebc...0583). Device: emulator-5554 (AVD
puber_tv_36), lease taken and released.

## Static gate

- `:app:compileDevDebugKotlin`: green.
- `:app:detektAll`, `:app:lintDevDebug`: green.
- `:app:testDevDebugUnitTest --rerun` (1370 tests), four full runs:
  - run 1: 1 failure, `HistoryVMDeletionSchedulingTest.deleteSelectionDuringAutomaticPagination_isQueuedOnceAndRetainsSemanticFocus` (not `HomeVM...`);
  - run 2: "Gradle Test Run FAILED" after 25 s, no failing test name captured;
  - runs 3 and 4: BUILD SUCCESSFUL.
- Flakiness probe: `HomeVMIncrementalPublishTest` alone, 4 reruns: 4 of 4 pass.
  The implementer saw `aFailedRefreshOfOneSectionKeepsTheContentItAlreadyPublished`
  fail in full runs only; I did not see it fail. The branch changes only
  `KinoPubRepositoryAuthStateTest` and `AuthVMTest` under tests; Home and History
  tests are untouched, so the full-run flakiness (a different test failed in my
  run) is order/load dependent and not tied to this change. Not run against a
  master checkout; recorded as observed only.

## Traceability (unit)

`KinoPubRepositoryAuthStateTest`: 9 tests, 0 failures. `AuthVMTest`: 5 tests, 0 failures.

| AC | Test | Result |
|---|---|---|
| AC-1 | `anExpiredCodeRefusalRenewsTheCodeInsteadOfThrowing`, `everyExpiredStringRenewsWithTheNewCode` (loop over the 3 strings; deviation recorded in the spec) | PASS |
| AC-2 | `aPollPastTheDeadlineThatIsRefusedAsExpiredRenews`, `codeRunningOutOnPendingAnswersRequestsANewCode` | PASS |
| AC-3 | `AuthVMTest.secondCode_replacesShownCodeAndRestartsCountdown_whenRenewed` | PASS |
| AC-4 | `aDeniedRefusalStillStopsThePoll` (access_denied throws, `getDeviceLoginCode` exactly once) | PASS |
| AC-5 | `aFailingNewCodeRequestSurfacesAndStopsTheLoop`, `renewalsAreNeverCloserThanTheInterval` | PASS |
| AC-6 | `afterRenewalPollsUseTheNewCode` | PASS |
| AC-7 | device check 1 | NOT CHECKED |

Failing-first on master was not observed by the implementer and not by me.

## Device: regression smoke only

AC-7 is not run by user decision: emulator-5554 is signed in and must stay so.
Needs a signed-out device or the user. The real server expiry string is still
unconfirmed.

Lease: the lock on emulator-5554 was held by a dead pid (1907208, 2745 s old);
`acquire` reclaimed it as stale, I released my token at the end (status: unlocked).

Steps:
1. `tools/android-apk-install-preserve install-preserve --serial emulator-5554 ...`
   (first attempt failed before touching the device: apksigner not on PATH;
   retried with build-tools 37.0.0 on PATH).
   Expected: installed, data preserved. Observed: outcome `installed`,
   classification `compatible_replace`, mode `adb_install_replace_preserve`,
   same signer before and after. PASS.
2. Force-stop, clear logcat buffer, `am start ... MainActivity`, wait 15 s.
   Expected: app starts signed in, home screen, no crash. Observed: process
   alive, `MainActivity` is the top resumed activity; a screenshot (viewed, not
   stored: it shows account content) showed the Home screen with the Continue
   watching and My bookmarks rows, not the pairing screen. PASS.
3. App log for the launch: 0 `FATAL EXCEPTION` / `ANR in` lines for the app
   pid, crash buffer empty. PASS.

No logout, data clear or uninstall was done.
