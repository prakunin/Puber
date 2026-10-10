# Pairing code renews when it expires — review

**Spec:** `docs/superpowers/specs/2026-10-10-pairing-code-refresh-design.md`
**Plan:** the `## Plan` section of the spec (quick route)
**Evidence:** `docs/superpowers/evidence/2026-10-10-pairing-code-refresh-device.md`
**Branch:** `pairing-code-refresh` at `b0e47a2a`

<!-- The conductor writes the rounds from the reviewers' returns; the
final-judge writes the Gate. Each finding gets a disposition: fixed (<sha>),
rejected (<why>), or deferred (<board row>). Nothing stays open silently. -->

## Round 1

### code-reviewer

| # | Severity | Where | Finding | Disposition |
|---|---|---|---|---|
| 1 | minor | `KinoPubRepository.kt:54,63` | `interval` is not floored: `interval = 0` with an immediate expiry renews and polls with `delay(0)`, hammering the API | fixed (1b52bc6a) |
| 2 | minor | `KinoPubRepository.kt:30-31` | A failed renewal request ends the flow; in `LoginRequired` the VM only shows a snackbar, so one network blip at renewal leaves the dead code at 00:00 — the original symptom. AC-5 chose this | deferred: board row `pairing-renewal-retry` |
| 3 | minor | `KinoPubRepositoryAuthStateTest.kt:78` | `aPollPastTheDeadline…` never polls past the deadline (wall-clock deadline, virtual `delay`) | duplicate of test #2 |

### test-reviewer

| # | Severity | Where | Finding | Disposition |
|---|---|---|---|---|
| 1 | major | `KinoPubRepositoryAuthStateTest.kt` | No test that an unlisted OAuth string (e.g. `invalid_grant`) still throws; the user's whitelist is unguarded | fixed (7af04f20) |
| 2 | major | `KinoPubRepositoryAuthStateTest.kt` `aPollPastTheDeadlineThatIsRefusedAsExpiredRenews` | Does not simulate a late poll; the AC-2 root-cause path is untested. Same clock mix costs ~1 s real time in `codeRunningOutOnPendingAnswersRequestsANewCode` | fixed (7af04f20) |
| 3 | minor | `pollUntilExpired` guard | The `expiresIn <= 0` delay guard has no test | fixed (7af04f20) |
| 4 | minor | `renewalsAreNeverCloserThanTheInterval` | No assertion on `requestTimes.size` | fixed (7af04f20) |
| 5 | minor | `everyExpiredStringRenewsWithTheNewCode` | Case-insensitive match untested | fixed (7af04f20) |
| 6 | minor | `AuthVMTest.kt:~33` | Import order | fixed (1b52bc6a) |

### codex-reviewer

Ran: review (`gpt-6-astra`; first attempt failed: the global default `gpt-6.1-sol` is refused for a ChatGPT login). No findings.

| # | Severity | Where | Finding | Disposition |
|---|---|---|---|---|

Rejected Codex claims: none.

### ux-designer (visual changes)

Not run: no layout change.

## Round 2

Scope `b0e47a2a..0e509dbd`. qa re-checked after the DI change: evidence
`12c66bc9` (static gate green, smoke on emulator-5554 signed in, DI resolved
for its only consumer `AuthInteractor`; device run does not enter the auth path).

### code-reviewer

Clean. Round-1 #1 fixed; injected clock matches the codebase's `clock`/`nowMillis`
pattern; the Koin change drops only the unused concrete-type binding.

### test-reviewer

Round-1 #1–#6 resolved.

| # | Severity | Where | Finding | Disposition |
|---|---|---|---|---|
| 7 | minor | `KinoPubRepositoryAuthStateTest.kt` `anUnlistedRefusalStillStopsThePollInsteadOfRenewing` | Against the rejected "renew on everything but access_denied" it fails only by hanging until the 60 s `runTest` timeout | fixed (de1f5368) |
| 8 | minor | `KinoPubRepositoryAuthStateTest.kt` `assertRenewalGapAtLeast` | Checks a lower bound only; waiting two intervals would pass | rejected: the requirement is a lower bound |
| 9 | minor | `KinoPubRepositoryAuthStateTest.kt` | No test that a token arriving on the post-deadline poll is still accepted | fixed (de1f5368) |

### codex-reviewer

Not re-run: no findings in round 1.

## Gate

<!-- final-judge, gate mode. -->

Judged at head `e2db4df1` (the header's `b0e47a2a` is the round-1 scope).

**Decision:** CONCERNS

Done as asked: the diff changes only `ApiError.OAuth` (adds `isCodeExpired`, which matches
the user's whitelist and ignores case), `KinoPubRepository` (adds `pollUntilExpired`, a private
`CodeExpiredException`, an injected `nowMillis`, and an interval floored at 1 s), the Koin
binding needed for the new constructor parameter, two test classes, and docs. Layout, copy, the
API-domain dialog, token refresh and the countdown format are untouched (Out of scope respected).
`AuthVM` is unchanged, as plan step 4 expected. Nothing constructs or injects the concrete
`KinoPubRepository` except the new binding and the test.

### Traceability

| AC | Test / check | Evidence | Status |
|---|---|---|---|
| AC-1 | `KinoPubRepositoryAuthStateTest.anExpiredCodeRefusalRenewsTheCodeInsteadOfThrowing`, `everyExpiredStringRenewsWithTheNewCode` | evidence file (unit table); judge rerun at e2db4df1: 13/13 pass | PASS |
| AC-2 | `aPollPastTheDeadlineThatIsRefusedAsExpiredRenews` (poll at 2 s past a 1 s deadline, virtual clock), `codeRunningOutOnPendingAnswersRequestsANewCode`, `aTokenThatLandsAfterTheDeadlineIsStillAccepted` | same | PASS |
| AC-3 | `AuthVMTest.secondCode_replacesShownCodeAndRestartsCountdown_whenRenewed` | evidence; judge rerun: 5/5 pass | PASS |
| AC-4 | `aDeniedRefusalStillStopsThePoll` (`access_denied`, one code request), `anUnlistedRefusalStillStopsThePollInsteadOfRenewing` (`invalid_grant`, bounded by `take(3)`) | same | PASS |
| AC-5 | `aFailingNewCodeRequestSurfacesAndStopsTheLoop`, `renewalsAreNeverCloserThanTheInterval`, `aCodeThatIsAlreadyExpiredStillWaitsAnIntervalBeforeRenewing`, `aZeroIntervalIsFlooredToOneSecond` | same | PASS |
| AC-6 | `afterRenewalPollsUseTheNewCode` | same | PASS |
| AC-7 | Device check 1 (signed-out TV) | not run: by the user's decision, emulator-5554 stays signed in | NOT VERIFIED |

Failing-first on master was not observed (recorded as a deviation). From reading the code, the
master version throws on `expired_token`, so the test would have failed on master.

### Findings

| Source | # | Severity | Status |
|---|---|---|---|
| code-reviewer R1 | 1 | minor | fixed `1b52bc6a`; the floor is in the diff (`coerceAtLeast(1)`) |
| code-reviewer R1 | 2 | minor | deferred, board row `pairing-renewal-retry` exists on master |
| code-reviewer R1 | 3 | minor | duplicate of test #2, fixed `7af04f20` |
| test-reviewer R1 | 1, 2 | major | fixed `7af04f20`; the tests exist and pass |
| test-reviewer R1 | 3-6 | minor | fixed `7af04f20` / `1b52bc6a` |
| test-reviewer R2 | 7, 9 | minor | fixed `de1f5368` |
| test-reviewer R2 | 8 | minor | rejected with a reason (the requirement is a lower bound); I agree |
| codex-reviewer | - | - | no findings |

No blocker or major finding is still open, and none was dropped without a disposition.

Open point settled (detekt "7 compiler errors"): I reran `:app:detektDevDebugUnitTest` at
`e2db4df1` with detekt debug turned on through an init script. All 7 errors are in files this
branch does not touch, and both files match master: `TabNavigatorChildRetentionTest.kt:184`
(the members `@Parcelize` generates for `RetentionProbeScreen`) and `CachedFeedTest.kt:422-448`
(the `serializer()` that `@Serializable` generates for `Boxed`, plus the type inference that
depends on it). Detekt's analysis does not see code generated by compiler plugins, so these
errors also exist on master and are not caused by this branch. The task succeeds and the warning
does not block.

### Non-functional

| Area | Assessment |
|---|---|
| Performance | Network and timing only, no UI change. The `delay` is never shorter than 1 s, and a pass waits at least one interval even when `expiresIn <= 0`, so renewals cannot spin. Polling allocates one exception per expiry, which is negligible. No change to startup or caches. |
| Reliability | An expired code renews, while a denial or any unknown refusal is still thrown, and so is a failed renewal (AC-5). Cancellation is unchanged (`runCatchingCancellable`), and `CodeExpiredException` is private and caught inside the poll. Known gap, deferred: after a failed renewal request, the old code stays on screen with only a snackbar (`pairing-renewal-retry`). |
| Maintainability | Fits the repository conventions: JUnit 5 and the clock is injected the same way as elsewhere (`nowMillis`). The Koin change is explicit and is needed because `singleOf` would try to resolve the function-typed parameter. It drops only the concrete-type binding, which nothing used. No dead code and no duplicated helper. The inline comment "Code expired → loop restarts" is still accurate. |
| Security / privacy | No secrets in code or evidence. The evidence describes screenshots of account content without storing them and keeps no raw logcat. No logout, data clear or uninstall was done. |
| TV (focus, D-pad, Back) | No composable changed. Focus while the code renews (Device check 2) was not observed, because the signed-in emulator never enters the pairing screen. Same limit as AC-7. |

### Must fix

None.

### Concerns for the user

1. AC-7 is NOT VERIFIED by your choice: no signed-out device has watched the code renew on screen,
   and focus during the renewal is unobserved as well.
2. The expiry whitelist (`expired_token`, `authorization_expired`, `code_expired`) has not been
   checked against the real KinoPub server. If the server sends another string, the code still
   gets renewed when the client deadline passes (AC-2), but an expiry reported by a late poll is
   thrown as an error, which is the original bug. Capture the real string on a signed-out device
   (Device check 4).
3. A failed renewal request still leaves the dead code on screen (deferred to `pairing-renewal-retry`).
4. The full unit suite is flaky on History/Home VM tests this branch does not touch
   (`unit-suite-flaky`). The touched classes pass on their own at head.
5. Detekt's "7 compiler errors" on the unit-test source set already exist on master
   (code generated by compiler plugins that detekt cannot see); they are not a regression.
