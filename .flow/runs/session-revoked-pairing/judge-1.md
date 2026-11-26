# Gate - session-revoked-pairing - round 1

**Decision:** PASS
**Head:** 8be284f2   **Base:** b53bf631

## Done as asked

`git diff b53bf631...HEAD` matches spec Fix direction and Design: `apiCall` ends the session through `requireSuccessfulSession` (`data/api/KinoPubApiClient.kt`) for a bearer request refused with 401 or 403 `user_banned` (`sessionEndReasonFor`, `data/api/ApiFailures.kt`); `SessionEvent.Unauthorized` carries the reason; Auth shows `auth_account_blocked`; Home and Settings hide the mirror action only for a session-ending refusal (AC-7 as amended, state.md Decisions). `thirdPartyApiCall` and the OAuth calls are untouched. Additions beyond the first design (replaying bus, persisted reason, `SessionEndCleanup`, no refresh without a refresh token) come from the qa round-1 revision and triage-1/2, all recorded in the plan. Deferred runs session-expiry-delivery and session-logout-interactor are not folded in.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `KinoPubApiClientSessionEndTest.aUserBannedRefusalClearsTokensAndEmitsBlocked` | check.full green at 672d3618 (qa-report round 4); test read: asserts Unauthorized, cleared prefs, one Blocked event | PASS |
| AC-2 | `KinoPubApiClientSessionEndTest.aRefusalAfterASuccessfulRefreshEndsTheSessionAsExpired` | check.full | PASS |
| AC-3 | `KinoPubApiClientSessionEndTest.anUnlistedForbiddenAnswerKeepsTheSession` | check.full | PASS |
| AC-4 | `KinoPubApiClientSessionEndTest.aRefusalFromAnotherHostKeepsTheSession` | check.full (guard, green on base) | PASS |
| AC-5 | `aSecondRefusalAfterTheSessionEndedEmitsNothing`, `SessionEndCleanupTest`, `LauncherVMTest.start_leavesAuthRoutingToTheSessionHandler_whenASessionEndIsPending`; M-1 | check.full; `docs/superpowers/evidence/2026-11-26-session-revoked-pairing-device-r4.md` (one stable Auth over 54 s) | PASS |
| AC-6 | `AuthVMTest.onStart_showsAccountBlockedLine_whenSessionEndedBlocked`, `...hidesAccountBlockedLine_whenSessionExpired`, `loading_showsAccountBlockedLine_whenNoCodeArrives`; M-1 | check.full; r4 evidence `r4-12s.png` (loading state only, a worktree build gets no code) | PASS |
| AC-7 (amended) | `HomeVMTest` three AC-7 tests, `DeviceSettingsVMErrorTest` two tests, `DefaultErrorHandlerTest`; `DeviceSettingsContentTest` itest | check.full; itest in `...-device-r2.md` (2 tests, 0 failures) | PASS |
| AC-8 | M-1 on 192.168.1.110:5555, APK 672d3618 sha256 776218d8..., install-preserve, auth-restore before and after | `...-device-r4.md` | PASS |
| AC-9 | `KinoPubApiClientAuthProbeTest.sessionExpiredFailureRecordsAuthErrThenThrowsUnauthorized` (file not in the diff) | check.full | PASS |
| AC-10 | owner judges on the TV; `CodeInfoBlockedPreview` | r2/r4 screenshots (loading state) | NOT CHECKED (manual, section below) |

Sampled: all 22 P0 and fix-round test names in test-design.md exist under `app/src/test`; `tools/mobile-evidence-audit.sh` on the evidence dir: `evidence_audit_status=passed`. check.full not re-run: qa-report (11:57:14) is newer than the last commit 8be284f2 (11:57:09, evidence only); the last code commit 831d6ccf is covered by the run at 672d3618.

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| triage-1 #1-#8 (empty bearer pinned, Launcher race, reason not persisted, replay re-runs clears, unlisted 403 hides mirror, emit-before-clear untested, AC-4 row, missing preview) | blind, edge, state-path, tests, ux, codex round 1 | fixed | fa8b3b23, 42c898dd, 3968eeaa |
| triage-1 #9 refresh outside `sessionLock` | codex C2 | deferred | run `session-refresh-lock-race` exists |
| triage-1 #10 Home Retry-only focus test | ux F2 | deferred | run `home-error-retry-focus-test` exists |
| triage-2 #1-#2 (major) claim before clears; handler untested | edge, state-path, codex, tests round 2 | fixed | 831d6ccf, `SessionEndCleanupTest.aHandlerCancelledMidClearLetsTheReplayClearAgain` |
| triage-2 #3 AC-7 contradicts fix #5 | codex C1 | fixed by AC amendment | 672d3618; state.md Decisions |
| triage-2 #4 preview canvas | ux-2 F1 | fixed | 831d6ccf, `AuthComponent.kt` `spec:width=960dp,height=540dp`; ux-3 no findings |
| triage-3 #1 final handler not run on device | codex-3 | fixed | 8be284f2 (r4 evidence) |
| triage-3 #2 cleanup overlap on replay | state-path-3 F1 | deferred | run `session-end-cleanup-overlap` exists |
| triage-3 #3 throwing clear untested | tests-3 F1 | accepted minor | test-only gap; catch present at `SessionEndCleanup.kt` |
| Rejected: 401 with `user_banned` should show Blocked | codex-1 | rejected, accepted | spec maps 401 to Expired (AC-2); box shows 403 |
| Rejected: `clearAccountPrograms` stalls routing | codex-3 | rejected, accepted | base `App.kt` already awaited it before `newRootScreen` (diff, removed lines) |
| 4 commits carry Co-Authored-By (da172bdf, fa8b3b23, 42c898dd, 3968eeaa), against `commit_style` | judge | conductor squash before ship | state.md Log |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | Lock and Keystore reads only on a refusal and in `isAuthenticated`; no hot-path or recomposition change. Baseline profile entries for `AuthScreen`/`SessionEvent` go stale until regenerated (design Risks) |
| Reliability | Cold start, process death (persisted reason) and burst covered by tests and r3/r4 evidence; residual races deferred to two runs above |
| Maintainability | Follows AGENTS.md: parameterized `AuthScreen` key with `@IgnoredOnParcel`, scoped params, pure content, strings in both locales; clears moved out of the composable; no dead code left (`CODE_UNAUTHORIZED` moved, old `clearWithoutFailing` removed) |
| Security / privacy | Reason stored encrypted via `CryptoPreferenceRepository`; no tokens or account data in code, logs or evidence (audit passed) |

## Manual checks for the owner

1. AC-10: open `CodeInfoBlockedPreview` (`app/src/main/java/com/kino/puber/ui/feature/auth/component/AuthComponent.kt`) and the r4 screenshots `docs/superpowers/evidence/2026-11-26-session-revoked-pairing-r4-12s.png`, `-r4-30s.png`; judge size and placement of the red line in both states.
2. AC-10 on the TV with a code shown (needs a build with the client secret): after the squash and merge, build master, install-preserve on 192.168.1.110:5555 with the banned session restored, cold start, confirm the blocked line above the code and the mirror hint stay inside the screen edges; `make DEVICE=192.168.1.110:5555 auth-restore` afterwards.
3. Mirror dialog on Auth needing two Back presses (`...-device-r2.md` note): compare with a master build; if master does the same, it is pre-existing.

## Must fix

none

## Concerns for the owner

none
