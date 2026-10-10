# Pairing code renews when it expires

Status: approved
Date: 2026-10-10
Slug: pairing-code-refresh
Kind: bug
Size: S
Route: quick

<!--
Each section has one owner; nobody else edits it. The conductor commits this
file in the task's worktree after every phase.
  analyst        Problem, Acceptance criteria, Out of scope, Plan (quick route)
  architect      Design
  ux-designer    Visual
  test-designer  Test design
  anyone         Open questions (append only; the user answers)
Cite code as [Source: path:line]. A claim with no source is a guess.
-->

## Problem

<!-- analyst. Bug: Repro (steps, build, device), Observed, Expected,
Root cause (confirmed | plausible) with [Source: …]. Feature: who needs what
and why, and what exists today. -->

**Repro.** Dev build 1.9.69-dev on emulator-5554: pairing screen countdown reaches 00:00; the code on screen does not change until the app is restarted. Device repro skipped: the emulator is paired (login not renewable), so the app does not show the pairing screen. Analysis is from code.

**Observed.** Countdown stops at 00:00 and the old, dead code stays on screen. A "failed" snackbar is the only trace.

**Expected.** When the code runs out, the screen shows a fresh code and a restarted countdown, with no restart of the app.

**How the code models the flow.**
- The device-code response carries `expires_in` (code lifetime, seconds) and `interval` (seconds between polls) [Source: app/src/main/java/com/kino/puber/data/api/auth/OAuthModels.kt:11-12].
- `KinoPubRepository.getAuthState()` already has a renew loop: request a code, emit `AuthState.Code`, poll every `interval` seconds until `now + expiresIn` passes, then "loop restarts with new device code" [Source: app/src/main/java/com/kino/puber/data/repository/KinoPubRepository.kt:29-53].
- The countdown is a separate VM timer started per `AuthState.Code`; it counts `expiresIn` down to 0 and ends, changing nothing else [Source: app/src/main/java/com/kino/puber/ui/feature/auth/vm/AuthVM.kt:199-210]. A new `Code` would restart it and replace the shown code [Source: AuthVM.kt:118-128].
- Polling: `getDeviceToken` posts the device `code`; `getDeviceLoginStatus` emits nothing for `authorization_pending` and emits a failure for every other error [Source: app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:1076-1086, 1118-1143]. `pollForToken` rethrows any `ApiError.OAuth` that is not `authorization_pending` [Source: KinoPubRepository.kt:65-74; app/src/main/java/com/kino/puber/core/error/ApiError.kt:50-58].

**Root cause (plausible, strongly supported by code; the server's exact expiry string is unverified).** The deadline loop is never reached cleanly. The poll is made `interval` seconds after the previous one and the deadline is checked only before the delay (`while (now < deadline) { delay(interval); poll }`), so the last poll can land up to `interval` seconds after the server has expired the code, and in any case server and client clocks disagree by the request latency [Source: KinoPubRepository.kt:34-39]. The server answers an expired code with an OAuth error that is not `authorization_pending`; `pollForToken` treats it as final and throws [Source: KinoPubRepository.kt:60-63, 70]. The throw ends the `channelFlow`, so the renew loop at line 52 is never taken. In `AuthVM`, the exception goes to `dispatchError`, which only shows a snackbar and returns in `LoginRequired` mode [Source: AuthVM.kt:159-163]. The Content state, with the dead code and the timer at 00:00, remains. A unit test pins this: an `expired_token` refusal must throw and not renew [Source: app/src/test/kotlin/com/kino/puber/data/repository/KinoPubRepositoryAuthStateTest.kt:29-38]. That test encodes the defect for the expiry case; it is right only for a refusal like user denial.

**Polling after expiry.** Polling does not continue against an expired code in the intended path (the loop exits at the deadline), but it can make one poll past the deadline (see above), and that poll is what kills the flow.

## Acceptance criteria

<!-- analyst. Numbered, each checkable by a test or an observation on the TV.
- **AC-1** Given …, when …, then …
-->

- **AC-1** Given a signed-out device showing code A, when the server answers a poll with the code-expired refusal, then the repository requests a new device code and emits a new `AuthState.Code` instead of throwing.
- **AC-2** Given the client deadline (`expiresIn`) passes with only `authorization_pending` answers, when the loop ends, then a new code is requested and emitted (existing behaviour, kept).
- **AC-3** Given a new `AuthState.Code` arrives, when `AuthVM` handles it, then the shown code is the new one and the countdown restarts from the new `expiresIn`.
- **AC-4** Given a poll refusal that means the user denied the code (not an expiry), when it arrives, then the flow still stops and the error is reported (not silently renewed).
- **AC-5** Given the new-code request itself fails (network or server), when renewing, then the failure surfaces through the existing error path and the loop does not spin; renewals are never closer together than `interval` seconds.
- **AC-6** Given a code is renewed, when the old code's polling is considered, then no further poll is made with the old `code`.
- **AC-7** Given the pairing screen on a TV with the countdown at 00:00, when a minute passes, then the screen shows a different code and a running countdown (device check; needs a signed-out device, so the user or a dedicated test device runs it) `(user judges)`.

## Out of scope

- The pairing screen layout, copy, and the API-domain dialog.
- Token refresh and any post-login flow.
- Changing the countdown format.
- Logging out or re-pairing the shared emulator.

## Design

<!-- architect. Decisions table, changes by layer, alternatives rejected,
risks. Not on the quick route. -->

## Visual

<!-- ux-designer. Visual and perf routes only when a screen changes. -->

## Test design

Tests live in `KinoPubRepositoryAuthStateTest` (mocked `KinoPubApiClient`, `runTest`, virtual `delay`; the deadline uses `System.currentTimeMillis`, so deadline cases use a tiny `expiresIn` such as 0/1 with `interval` 1 and `take(n)` on the flow so a spinning loop cannot hang the run) and `AuthVMTest` (existing `createVM`, `MainDispatcherExtension`, `FakeResourceProvider`).

### Risks

| Risk | P | I | Score | Mitigation |
|---|---|---|---|---|
| Expired refusal still throws, flow ends, dead code stays (the bug) | 3 | 3 | 9 | `expiredRefusal_emitsSecondCode_...` (fails on master) |
| Renewal broadens too far: `access_denied` or an unknown refusal silently renews and hides a real denial | 2 | 3 | 6 | AC-4 test with `access_denied`; the changed existing test |
| Wrong expiry string (Open question 1): real server string not in the renew set, bug persists on device | 2 | 3 | 6 | Unit test per accepted string (`expired_token`, `authorization_expired`, `code_expired`); residual risk stays open until a real string is captured (device, user) |
| Renew loop spins (new-code request fails, or expiresIn <= 0 skips the `delay`), hammering the API | 2 | 3 | 6 | AC-5 tests: failure of `getDeviceLoginCode` surfaces and stops; consecutive `Code`s are at least `interval` apart in virtual time |
| Old `code` polled after renewal | 1 | 2 | 2 | AC-6 verify; accepted otherwise |
| AuthVM does not restart countdown / replace code on a second `Code` | 2 | 2 | 4 | AC-3 VM test (below 6, but cheap, P1) |
| Late poll past deadline with expiry kills the flow | 2 | 3 | 6 | AC-2 late-poll test |
| Existing test still asserts old behaviour (encodes the defect) | 3 | 2 | 6 | Edit it (see Bug route) |

Highest score 9.

### Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 | `KinoPubRepositoryAuthStateTest.anExpiredCodeRefusalRenewsTheCodeInsteadOfThrowing` (poll answers `OAuth("expired_token")`, then second code request returns a new `userCode`; collect `take(2)`) | `getAuthState` emits two `AuthState.Code`, second with the new code, no exception |
| AC-1 | unit | P1 | same test parameterised over `expired_token`, `authorization_expired`, `code_expired` | every string in the renew set renews (resolution of Open question 1 changes this set only) |
| AC-2 | unit | P0 | `...aPollPastTheDeadlineThatIsRefusedAsExpiredRenews` (`expiresIn` tiny, poll after the deadline gets expired refusal) | late poll renews; the root cause path |
| AC-2 | unit | P1 | `...codeRunningOutOnPendingAnswersRequestsANewCode` (only empty flows from `getDeviceLoginStatus`, `take(2)` Codes) | existing deadline renewal kept |
| AC-3 | unit | P1 | `AuthVMTest.secondCode_replacesShownCodeAndRestartsCountdown_whenRenewed` (fake interactor flow emits Code A then Code B; advance virtual time with `StandardTestDispatcher`; assert state shows B and remaining seconds equals B's `expiresIn` minus elapsed since B, not A's) | VM state replaces code and restarts the timer |
| AC-4 | unit | P0 | `...aDeniedRefusalStillStopsThePoll` (the existing `aTerminalOAuthRefusalStopsThePollInsteadOfRunningOutTheCode`, switched to `access_denied`); also assert code was requested once (`verify(exactly = 1) { client.getDeviceLoginCode() }`) | non-expiry OAuth refusal throws, no renewal |
| AC-5 | unit | P0 | `...aFailingNewCodeRequestSurfacesAndStopsTheLoop` (first code, expired refusal, second `getDeviceLoginCode` returns failure; `assertThrows`, request count == 2) | renewal failure propagates, no spin |
| AC-5 | unit | P0 | `...renewalsAreNeverCloserThanTheInterval` (virtual time via `currentTime` in `runTest`; record time of each `getDeviceLoginCode` call after an immediate expired refusal; difference >= `interval*1000`) | the `interval` delay is kept between renewals |
| AC-6 | unit | P1 | `...afterRenewalPollsUseTheNewCode` (capture `getDeviceLoginStatus` args; after the renewal every arg has the new `code`, none the old) | no poll with the old code |
| AC-7 | device | P1 | Device check 1 | on-screen new code and running countdown |

AC-7 stays unproven by automation. What unit/VM tests cover instead: the repository emits a second `Code` on expiry (AC-1, AC-2); `AuthVM` replaces the shown code and restarts the countdown on it (AC-3). Not covered by any test: the real server's expiry string, the actual Compose rendering, TV focus staying put when the code changes, and end-to-end timing on a signed-out device. `emulator-5554` is paired and must not be signed out, so AC-7 is run by the user or a dedicated signed-out device, and the gate must record it as "not verified by us" otherwise.

### Bug route

Failing-first test: `KinoPubRepositoryAuthStateTest.anExpiredCodeRefusalRenewsTheCodeInsteadOfThrowing`. On master `getAuthState` throws `ApiError.OAuth("expired_token")` after the first `Code`, so the second `Code` never arrives and the test fails; after the fix it passes. Written first.

Existing test to change: `aTerminalOAuthRefusalStopsThePollInsteadOfRunningOutTheCode` currently expects `expired_token` to throw. That encodes the defect. Change its refusal to `access_denied` (assert `error == "access_denied"`), keeping it as the AC-4 test. It must not stay with `expired_token`: it would fail after the fix.

### Device checks

Run only on a signed-out device that is not `emulator-5554` (or by the user); lease via `tools/emulator-resource-lock.sh`, exact serial on every adb call, install with `tools/android-apk-install-preserve`.
1. Open the pairing screen, note code A and the countdown. Do nothing and watch the countdown reach 00:00, then wait up to one minute. Expected: a different code B appears and the countdown restarts from the full duration and runs. Must not happen: code A stays on screen, countdown stuck at 00:00, only a snackbar, a crash.
2. While code B is shown, press D-pad keys (for example DPAD_DOWN, DPAD_CENTER on the focused control, then BACK is not pressed). Expected: focus stays on the same control through the renewal. Must not happen: focus lost or moved to nothing when the code changes.
3. Leave it through two more expiries. Expected: each renewal produces a fresh code, no faster than the polling interval, no log spam of requests. Must not happen: rapid repeated code requests.
4. (If the real string is captured) record the logged refusal string in evidence without raw logcat. Closes Open question 1.

### Baseline (perf route)

n/a, not a perf task.

## Plan

<!-- analyst, quick route only; otherwise see docs/superpowers/plans/. -->

1. `ApiError.OAuth`: add `isCodeExpired` next to `isAuthorizationPending`, true for the server's expired-code error strings (see Open question 1). [Source: app/src/main/java/com/kino/puber/core/error/ApiError.kt:50-58]
2. `KinoPubRepository.pollForToken`: on an expired-code refusal return a signal that ends the inner loop (code expired) rather than throw; other non-pending OAuth refusals still throw. Make the inner loop stop and fall through to the existing "new code" iteration, keeping the `interval` delay so renewals are not tighter than `interval`. [Source: app/src/main/java/com/kino/puber/data/repository/KinoPubRepository.kt:29-74]
3. Tests: change `aTerminalOAuthRefusalStopsThePollInsteadOfRunningOutTheCode` to use a non-expiry refusal (e.g. `access_denied`); add a test that an expired refusal yields a second `AuthState.Code`, and one that a late poll past the deadline with expiry renews. [Source: app/src/test/kotlin/com/kino/puber/data/repository/KinoPubRepositoryAuthStateTest.kt:29-38]
4. `AuthVM` needs no change if AC-3 holds; add a VM test only if one exists for `AuthState.Code` handling. [Source: app/src/main/java/com/kino/puber/ui/feature/auth/vm/AuthVM.kt:118-128, 199-210]

## Open questions

1. Which error string does KinoPub return for an expired device code (`expired_token`, `authorization_expired`, `code_expired`, other)? It is not documented in the repo, and it cannot be observed on emulator-5554 without unpairing. Proposed default: renew on any of those three, and treat `access_denied` and anything else as terminal. Alternatively, invert it: renew on every non-pending refusal except `access_denied`. Which does the user prefer, or can the user capture the real string from a signed-out device (the user must supply that)?

**Answered by the user on 2026-10-10 (gate 1):**

1. Whitelist: renew only on `expired_token`, `authorization_expired`,
   `code_expired`; `access_denied` and any other refusal stay terminal.
2. AC-7 is not run on a device: emulator-5554 stays signed in. It goes to the
   gate as `NOT VERIFIED`, which makes the decision at best `CONCERNS`.

## Implementation record

Commits: see `git log` on the branch (renew on expired code; AuthVM renewal test; extract the poll loop for detekt; this record).

Done: `ApiError.OAuth.isCodeExpired` (whitelist `expired_token`, `authorization_expired`, `code_expired`); `KinoPubRepository.getAuthState` renews the code on an expired refusal through a private `CodeExpiredException` and a new `pollUntilExpired` helper; other non-pending refusals still throw. Tests: `KinoPubRepositoryAuthStateTest` (P0 and P1 per the traceability table; the existing terminal-refusal test now uses `access_denied`), `AuthVMTest.secondCode_replacesShownCodeAndRestartsCountdown_whenRenewed`.

Deviations:
- The "per accepted string" P1 test is a loop over the three strings in one test, not parameterised: `junit-jupiter-params` is not a dependency.
- Extra guard: if `expiresIn <= 0` the inner loop never ran and the renew loop would have spun without delay, so `pollUntilExpired` waits one `interval` in that case (AC-5).
- Failing-first was not observed on master: the first test run hit a compile error in my test, and the fix followed before a clean red run.

Notes for reviewers:
- A full `testDevDebugUnitTest` run failed once, and twice in a row, on `HomeVMIncrementalPublishTest.aFailedRefreshOfOneSectionKeepsTheContentItAlreadyPublished`; it passes alone and passed in the final full run. It touches nothing in this change; treat it as a flaky test.
- AC-7 is `NOT VERIFIED` (emulator stays signed in, per the user's answer). The real server expiry string is still unconfirmed.

Gate: `./tools/agentw --continue testDevDebugUnitTest :app:detektAll :app:lintDevDebug` -> `BUILD SUCCESSFUL in 2m 4s`.

### Review round 1 fixes

- `1b52bc6a`: `interval` floored to one second (`coerceAtLeast(1)`) for every `delay` in the renew/poll loop; `KinoPubRepository` takes `nowMillis: () -> Long` (default `System::currentTimeMillis`, no project clock abstraction existed). `singleOf(::KinoPubRepository)` became an explicit `single<IKinoPubRepository> { KinoPubRepository(client = get(), cryptoPreferenceRepository = get()) }` in `data/di/modules.kt`, because `singleOf` would try to resolve the function-typed parameter. `AuthVMTest` imports reordered.
- `7af04f20`: tests run the deadline and `delay` on `testScheduler.currentTime`; the late-poll and pending tests now assert virtual times (poll at 2 s past a 1 s deadline; renewal at 12 s for a 10 s code polled every 3 s); added the unlisted `invalid_grant` test, the `expiresIn <= 0` gap test, the zero-interval floor test, the `requestTimes.size` assertion, and a mixed-case expiry string.
- Gate: `testDevDebugUnitTest` failed only on `HistoryVMDeletionSchedulingTest.deleteSelectionDuringAutomaticPaginationIsQueuedOnceAndRetainsSemanticFocus` (unrelated, board row unit-suite-flaky); detektAll and lintDevDebug passed.
- Review round 2 (#7, #9), `de1f5368`: the unlisted-refusal test is bounded with `take(3)` so a renewing implementation fails fast, and a new test accepts a token from a poll landing after the deadline.
