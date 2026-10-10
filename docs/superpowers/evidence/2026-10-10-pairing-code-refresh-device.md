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

## Re-check after round 1 (head 0e509dbd)

Changes since the first pass: 1b52bc6a (poll interval floored, injectable clock
`nowMillis`, Koin binding changed to an explicit `single<IKinoPubRepository> { ... }`)
and 7af04f20 (tests). Build: dev debug 1.9.69-dev, sha256 6e405abf...9690, built
from the worktree. Device: emulator-5554, lease taken and released (status: unlocked).

### Static gate

- `:app:compileDevDebugKotlin`, `:app:detektAll`, `:app:lintDevDebug`: green.
- `KinoPubRepositoryAuthStateTest`: 12 tests, 0 failures. `AuthVMTest`: 5 tests, 0 failures.
- `:app:testDevDebugUnitTest --rerun`, one full run: 1373 tests, 1 failed:
  `HistoryVMDeletionSchedulingTest > deleteSelectionDuringRefresh_isQueuedOnceAndRetainsSemanticFocus`
  (the known flaky class, board row unit-suite-flaky). No other failure.

### DI resolution (code review)

Grep of `app/src` for `KinoPubRepository`: the only injection site is
`AuthInteractor(private val repository: IKinoPubRepository)`, reached through
`IAuthInteractor` in `AuthVM`. Nothing injects the concrete `KinoPubRepository`
and the only construction is the new binding (`client = get(), cryptoPreferenceRepository = get()`),
the same two dependencies the removed `singleOf` resolved; `nowMillis` has a default.
The binding is `single<IKinoPubRepository>`, which satisfies `IKinoPubRepository`.
The old `bind<IKinoPubRepository>()` also exposed the concrete type; no consumer used it. PASS.

### Device smoke

1. install-preserve (apksigner on PATH): outcome `installed`, `compatible_replace`,
   `adb_install_replace_preserve`, same signer, `destructive_action: false`. PASS.
2. Force-stop, clear logcat, `am start MainActivity`, wait 15 s. Expected: signed-in Home.
   Observed: pid alive, Home with hero, Continue watching and My bookmarks rows (screenshot
   viewed, not stored: account content). PASS.
3. Remote only: DPAD_LEFT, 4x DPAD_DOWN, DPAD_CENTER opened the Movies catalogue.
   Expected: content loaded from the API. Observed: "New releases" row with posters and
   metadata. PASS.
4. App-pid log: 0 lines matching `FATAL EXCEPTION`, `ANR in`, Koin definition-not-found. PASS.

Limit: the repository is used only on the pairing/auth path, which a signed-in
session does not enter, so the device run shows the app starts and the Koin module
loads without error but does not resolve `IKinoPubRepository` itself. The resolution
claim rests on the code review above and the unit tests. AC-7 stays NOT CHECKED
(user decision: emulator-5554 must stay signed in).

No logout, data clear or uninstall was done.
