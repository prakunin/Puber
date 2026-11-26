# Test design: session-revoked-pairing

Inputs: [brief](brief.md), [design-architect](design-architect.md), [design-ux](design-ux.md). Levels from `.flow/roles/test-designer.md`. Test paths are under `app/src/test/kotlin/com/kino/puber/` unless they start with `app/`.

## Risk profile

| Risk | Source | P | I | Score | Covered by |
|---|---|---|---|---|---|
| Box session loss during AC-8 | brief Risks | 3 | 3 | 9 | Manual M-1 steps 2 and 9 (auth-save before, auth-restore after) |
| Wrongful logout from an unlisted 403 | brief Risks, design Risks | 2 | 3 | 6 | AC-3 test; `sessionEndReasonFor` table test |
| Off-host 401: Ktor `Auth` refreshes the KinoPub session for any 401 and retries with the bearer, so a GitHub 401 can end the session | plan Refinements (`reAuthorizeOnResponse`), `KinoPubApiClient.kt:147-158`, triage-1 #7 | 1 | 3 | 3 | AC-4 guard; Ktor 3.6.1 did not refresh on the unbearered GitHub 401, so the `reAuthorizeOnResponse` host limit is defensive and has no discriminating test |
| Stale bearer after re-pair: Ktor keeps the cached `access`, so the new session sends the old token and its refusals are skipped by the presented-token guard | plan Refinements (`clearAuthTokens`), readiness-1 note 2 | 3 | 2 | 6 | `aRepairAfterTheSessionEndedSendsTheNewToken` |
| Burst: parallel refusals clear tokens and re-root more than once | brief Risks, AC-5 | 3 | 2 | 6 | AC-5 sequential and concurrent tests with a stateful prefs fake; M-1 step 5 |
| 401 after a successful refresh not returned by Ktor as a 401 (`unverified`) | design Risks | 2 | 2 | 4 | AC-2 test settles it |
| Re-pair loop for a banned account | brief Risks | 2 | 2 | 4 | AC-6 line text; M-1 step 6 |
| Mixed failure loses the mirror button | design Decisions (Home) | 2 | 2 | 4 | AC-7 mixed-failure test |
| Home Retry without initial focus once the mirror button is gone | design-ux Home | 2 | 2 | 4 | M-2 |
| OAuth device flow refusal ejects the pairing screen | design Decisions (`apiCall` only) | 1 | 3 | 3 | P1 OAuth test |
| Stale refusal after a new pairing ends the new session | design Decisions (token match) | 1 | 3 | 3 | P2 stale-token test |
| Cold start: the end is emitted by `AppStartup` before `SessionExpiredHandler` collects, the bus has no replay, the Launcher opens Auth without the reason | qa round 1, design-architect Revision | 3 | 3 | 9 | `SessionEventBusTest.aCollectorThatStartsAfterTheEndStillReceivesIt`, `LauncherVMTest.start_leavesAuthRoutingToTheSessionHandler_whenASessionEndIsPending`; M-1 steps 4-5 |
| Refresh POST with no stored refresh token, once per Home section | qa round 1 defect 2 | 3 | 2 | 6 | `aRefusalWithNoStoredSessionSendsNoRefresh` |
| A worktree build never gets a code, so a blocked line drawn only with the code is never seen | qa round 1 | 3 | 2 | 6 | `AuthVMTest.loading_showsAccountBlockedLine_whenNoCodeArrives`; M-1 step 4 |
| A pending end outlives the new login and re-roots the next Activity to Auth | design-architect Revision Risks | 2 | 2 | 4 | `AuthVMTest.authSuccess_clearsThePendingSessionEnd`, `clearingThePendingEndStopsTheReplay` |
| Two equal ends conflated by the `StateFlow` | design-architect Revision Decisions | 1 | 2 | 2 | `twoEqualEndsReachACollectorTwice` (P1) |
| Empty bearer cached after the end: an OAuth call reloads `BearerTokens("","")`, the first call after re-pair forces a refresh that can end the new session | triage-1 #1 | 2 | 3 | 6 | Re-pair test with a device-code call before the save |
| Launcher opens Main over the handler's Auth when the token is read before the clear | triage-1 #2 | 2 | 2 | 4 | LauncherVM token-present test |
| Blocked reason lost on process death; Auth invites a re-pair of the banned account | triage-1 #3, qa round 1 | 3 | 2 | 6 | Persisted-reason tests |
| Replayed end re-runs the clears and wipes a deep-link target accepted after it | triage-1 #4 | 2 | 2 | 4 | Handled-end test |
| Mirror action hidden for an unlisted 403 that kept the session | triage-1 #5, triage-2 #3 (AC-7 amended) | 2 | 2 | 4 | HomeVM and DeviceSettingsVM unlisted-403 tests |
| Handler cancelled mid-clear marks the end handled, so a replay only routes and the ended account's caches survive to the next account | triage-2 #1 | 2 | 3 | 6 | `SessionEndCleanupTest.aHandlerCancelledMidClearLetsTheReplayClearAgain` |
| Clear before emit breaks cold-start routing | triage-1 #6 | 2 | 3 | 6 | Emit-before-clear test |
| Activity recreated on Auth replays the end: one extra re-root to the same screen | design-architect Revision Risks | 1 | 1 | 1 | Accepted: no rotation on a TV |
| Overlap with `session-logout-interactor`, `screen-error-states` | brief Risks | 2 | 1 | 2 | Accepted: merge-order concern, not a test |
| Stale baseline-profile entries | design Risks | 1 | 1 | 1 | Accepted: no correctness impact |

## Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 red | `KinoPubApiClientSessionEndTest.aUserBannedRefusalClearsTokensAndEmitsBlocked` | 403 `{"error":"user_banned"}` on a bearer request returns `ApiError.Unauthorized`, both tokens are null in the fake afterwards, exactly one `SessionEvent.Unauthorized(Blocked)`; fails if `apiCall` keeps `requireSuccessful()` or the reason is `Expired` |
| AC-2 | unit | P0 red | `KinoPubApiClientSessionEndTest.aRefusalAfterASuccessfulRefreshEndsTheSessionAsExpired` | 401 (with `WWW-Authenticate: Bearer`), refresh answered 200 with new tokens by an OkHttp interceptor, retry 401 again: tokens cleared, one `Unauthorized(Expired)`; fails if the 401 after refresh is not mapped |
| AC-3 | unit | P0 guard | `KinoPubApiClientSessionEndTest.anUnlistedForbiddenAnswerKeepsTheSession` | 403 `{"error":"нет доступа"}`: result `ApiError.Unauthorized`, tokens unchanged, no event; fails if 403 ends the session without the allow-list |
| AC-3 | unit | P1 | `data/api/SessionEndReasonForTest.mapsOnlyListedRefusalsToAReason` | Table: 401 -> `Expired`; 403 `user_banned` -> `Blocked`; 403 `нет доступа`, 403 `null`, 403 `USER_BANNED`, 404, 500 -> `null`; fails on any widening of the allow-list |
| AC-4 | unit | P0 guard | `KinoPubApiClientSessionEndTest.aRefusalFromAnotherHostKeepsTheSession` (parameterized 401 and 403 `user_banned`) | `getLatestGitHubRelease` (`api.github.com`, the only host called without a bearer), answered by an OkHttp interceptor: no refresh post, tokens unchanged, no event; green on base, fails if any off-host refusal ends the session; does not discriminate the `reAuthorizeOnResponse` host limit |
| AC-5 | unit | P0 red | `KinoPubApiClientSessionEndTest.aSecondRefusalAfterTheSessionEndedEmitsNothing` | Two sequential calls, both 403 `user_banned`, stateful in-memory `ICryptoPreferenceRepository` fake (not a relaxed mock with a fixed token, design Risks): one event, one `clearAccessToken`; fails if the guard in `onSessionExpired` is removed |
| AC-5 | unit | P1 | `KinoPubApiClientSessionEndTest.aParallelBurstOfRefusalsEndsTheSessionOnce` | Eight concurrent calls answering 403 `user_banned`: `clearAccessToken` counted once on the fake, events counted once; fails if check-and-clear is not under one lock. Count clears on the fake, not only bus events: the bus buffer of 1 can hide a second emit |
| AC-5, AC-6 | unit | P0 red | `KinoPubApiClientSessionEndTest.aRepairAfterTheSessionEndedSendsTheNewToken` | `/v1/items/7` records each `Authorization` header; first call 403 `{"error":"user_banned"}`; the fake's tokens are then set to `new-access` / `new-refresh` and the call repeated (200 `{}`, result not asserted): the second request carries `Bearer new-access`; fails if `onSessionExpired` does not call `httpClient.clearAuthTokens()` (base sends the cached `access`) |
| AC-5 | device | manual | M-1 step 5 | One re-root to Auth on the box |
| AC-6 | unit | P0 red | `AuthVMTest.onStart_showsAccountBlockedLine_whenSessionEndedBlocked` | `AuthScreenParams(SessionEndReason.Blocked)` and an `AuthState.Code`: `AuthViewState.Content.accountBlocked == true` with the code shown; fails if params are not wired into the state |
| AC-6 | unit | P0 guard | `AuthVMTest.onStart_hidesAccountBlockedLine_whenSessionExpired` | `Expired` and `null` reason: `accountBlocked == false`, code shown as today |
| AC-6 | unit | P1 | `ScreensImplTest.auth_carriesTheSessionEndReasonIntoTheScreenKey` (or `AuthScreenTest` where the key lives) | `auth(Blocked).key != auth(null).key`, equal for equal reasons; fails if the key ignores the reason, so a re-root would reuse the old scope and VM |
| AC-6 | device | manual | M-1 step 4 | Blocked line on the box |
| AC-7 | unit | P0 red | `HomeVMTest.allSectionsRefusedUnauthorized_showsErrorWithoutMirrorAction` | Every `interactor.observe*` flow throws a session-ending `ApiError.Unauthorized("user_banned")`: state `HomeViewState.Error`, `showMirrorAction == false`, message `error_generic`; fails if the run does not count session-ending failures. The route to Auth is AC-1, AC-2 and the session handler (M-1) |
| AC-7 | unit | P0 guard | `HomeVMTest.allSectionsRefusedWithoutEndingTheSession_keepMirrorAction` | Every section throws `ApiError.Unauthorized("deny page", endsSession = false)`: `showMirrorAction == true`, message `error_generic`; fails if the view keys on the error type instead of `endsSession` |
| AC-7 | unit | P0 guard | `HomeVMTest.mixedSectionFailures_keepMirrorAction` | One section throws `ApiError.Network`, the rest `Unauthorized`: `showMirrorAction == true`; fails if any single `Unauthorized` hides the button |
| AC-7 | unit | P1 | `HomeVMTest.unauthorizedDispatchError_hidesMirrorAction` | `dispatchError` with code `ErrorEntity.CODE_UNAUTHORIZED` (only a session-ending refusal maps to it) before content: `showMirrorAction == false`; code `Network`: `true` |
| AC-7 | unit | P0 red | `ui/feature/device/settings/vm/DeviceSettingsVMErrorTest.unauthorizedLoadFailure_hidesMirrorAction` | Settings load fails with a session-ending `ApiError.Unauthorized("user_banned")` through `DefaultErrorHandler(FakeResourceProvider())`: `DeviceSettingsState.Error.showMirrorAction == false`; fails if `loadDeviceSettings` still wraps the failure in `IllegalStateException` (`DeviceSettingsVM.kt:203`), which maps to `Unknown` |
| AC-7 | unit | P0 guard | `DeviceSettingsVMErrorTest.refusalThatKeepsTheSession_keepsMirrorActionAndGenericMessage` | Load fails with `ApiError.Unauthorized("deny page", endsSession = false)`: `showMirrorAction == true`, message `error_generic`, not `error_unauthorized`; fails if `DefaultErrorHandler` maps every `Unauthorized` to `CODE_UNAUTHORIZED` |
| AC-7 | unit | P1 | `DeviceSettingsVMErrorTest.networkLoadFailure_keepsMirrorAction` | `ApiError.Network`: `showMirrorAction == true` |
| AC-7 | instrumented | P2 | `app/src/androidTest/kotlin/com/kino/puber/ui/feature/device/settings/DeviceSettingsContentTest.errorWithoutMirrorAction_showsOnlyFocusedRetry` | No node with text `api_domain_open_action`; `SettingsTestTags.ErrorRetry` focused |
| AC-8 | device | manual | M-1 | Live reproduction on the box |
| AC-9 | unit | P0 guard | `KinoPubApiClientAuthProbeTest.sessionExpiredFailureRecordsAuthErrThenThrowsUnauthorized` (existing, unchanged) | Failed refresh still returns `Unauthorized` and one AUTH `Err`; fails if the refresh path loses its telemetry or the new guard swallows it |
| AC-9 | unit | P1 | `KinoPubApiClientSessionEndTest.aFailedRefreshEmitsExpired` | Failed refresh (interceptor 500): tokens cleared, one `Unauthorized(Expired)`; fails if `sessionExpiredFailure` passes the wrong reason or the null-token branch never matches |
| AC-5, AC-8 | unit | P0 red | `KinoPubApiClientSessionEndTest.aRefusalWithNoStoredSessionSendsNoRefresh` | No stored tokens, `/v1/items/7` answers 401 with `WWW-Authenticate: Bearer`: `ApiError.Unauthorized`, `refreshPosts == 0`, no event; fails if `refreshBearerTokens` posts without a refresh token (Task 1 code sends one) |
| AC-5 | unit | P1 | `KinoPubApiClientSessionEndTest.anAccessTokenWithoutARefreshTokenEndsTheSessionWithoutARefresh` | Access stored, refresh null, same server: `refreshPosts == 0`, tokens cleared, events `== [Unauthorized(Expired)]`; fails if the guard skips `sessionExpiredFailure` and the leftover access token stays |
| AC-6, AC-8 | unit | P0 red | `core/session/SessionEventBusTest.aCollectorThatStartsAfterTheEndStillReceivesIt` | `emit(Unauthorized(Blocked))`, then `withTimeoutOrNull(1.seconds) { bus.events.first() }` returns that event; fails on the base `SharedFlow` without replay (`null`) |
| AC-5 | unit | P1 | `SessionEventBusTest.twoEqualEndsReachACollectorTwice` | Unconfined collector in `backgroundScope`, two equal emits: two items; fails if `Pending` is a data class or events are conflated |
| AC-5 | unit | P1 | `SessionEventBusTest.clearingThePendingEndStopsTheReplay` | `emit`, `clearPendingEnd()`: `hasPendingEnd == false`, a new collector gets nothing; fails if the clear does not reset the replayed value |
| AC-5, AC-6 | unit | P0 red | `ui/feature/root/vm/LauncherVMTest.start_leavesAuthRoutingToTheSessionHandler_whenASessionEndIsPending` | No access token, bus with a pending `Unauthorized(Blocked)`, `testOnStart()`: `router.newRootScreen` never called; fails if the Launcher still opens `auth()` without the reason (a second re-root and no blocked line) |
| AC-5 | unit | P0 guard | `LauncherVMTest.start_opensAuthScreenWithoutAccessToken` (existing, empty bus) | `auth()` opened once; fails if the pending check blocks a plain logged-out start |
| AC-5 | unit | P1 | `AuthVMTest.authSuccess_clearsThePendingSessionEnd` | Pending end, `getAuthState()` emits `AuthState.Success`: `bus.hasPendingEnd == false`, `main()` opened; fails if `AuthVM` does not call `clearPendingEnd()` |
| AC-6, AC-8 | unit | P0 red | `AuthVMTest.loading_showsAccountBlockedLine_whenNoCodeArrives` | `Blocked`, not authenticated, `getAuthState()` never emits: state `AuthViewState.Loading` with `accountBlocked == true`; fails if any `Loading` built after start drops the flag |
| AC-6 | unit | P1 | `AuthVMTest.loading_hidesAccountBlockedLine_whenSessionExpired` (parameterized `Expired`, `null`) | `Loading.accountBlocked == false`; fails if the flag ignores the reason |
| AC-10 | device | manual | M-1 step 4 (screenshot for the owner) | Size and placement per `design-ux.md` |
| design | unit | P1 | `KinoPubApiClientSessionEndTest.anOAuthRefusalDuringPairingKeepsThePairingScreen` | Device-code request to `KinoPubConfig.OAUTH_BASE_URL` answered 401 and 403 `user_banned` by an interceptor, no stored tokens: no event; fails if the OAuth calls go through `requireSuccessfulSession` |
| design | unit | P2 | `KinoPubApiClientSessionEndTest.aStaleRefusalAfterRepairingKeepsTheNewSession` | Fake swaps the stored token to a new value while the server holds the response for the old bearer, then 403 `user_banned`: new tokens stay, no event |
| AC-5, AC-6 | unit | P1 | `KinoPubApiClientSessionEndTest.aRepairAfterTheSessionEndedSendsTheNewToken` extended, or a new test (name set by the implementer) | After the end, a device-code request to `KinoPubConfig.OAUTH_BASE_URL` is made before the new tokens are saved; then `/v1/items/7` carries `Bearer new-access`, `refreshPosts == 0`, no event; fails if `loadTokens` returns an empty pair instead of `null` with no stored access token |
| AC-5 | unit | P1 | `LauncherVMTest`, name set by the implementer | Access token stored and a pending end on the bus, `testOnStart()`: `router.newRootScreen` never called; fails if `hasPendingEnd` is checked only in the unauthenticated branch |
| AC-6 | unit | P1 | `KinoPubApiClientSessionEndTest`, name set by the implementer | 403 `user_banned` ends the session: the stateful prefs fake holds the stored reason `Blocked`; fails if the reason is not persisted |
| AC-6 | unit | P1 | `LauncherVMTest`, name set by the implementer | No token, empty bus, stored reason `Blocked`: `auth(Blocked)` opened once; fails if the Launcher opens `auth()` without the stored reason after process death |
| AC-6 | unit | P1 | `AuthVMTest`, name set by the implementer | `AuthState.Success`: the stored reason is cleared; fails if a later logged-out start shows the blocked line for a new account |
| AC-5 | unit | P1 | `core/session/SessionEndCleanupTest.aReplayedEndKeepsALinkAcceptedSinceAndClearsOnce` | A pending end handled, a deep link accepted, the same end delivered again (replay): the clears counted once, the deep-link target still set; fails if every delivery re-runs the clears or the claim is not per end |
| AC-5 | unit | P1 | `SessionEndCleanupTest.aHandlerCancelledMidClearLetsTheReplayClearAgain` | A clear suspends and the handling job is cancelled mid-clear, then the end is replayed: the clears run to the end on the replay; fails if the end is claimed before the clears finish and the claim is kept on cancellation (triage-2 #1) |
| AC-5, AC-6 | unit | P1 | `KinoPubApiClientSessionEndTest`, name set by the implementer | 403 `user_banned`: the prefs fake records `bus.hasPendingEnd` inside `clearAccessToken`, the recorded value is `true`; fails if `onSessionExpired` clears before it emits |

Red tests on new API (`SessionEndReason`, `AuthScreenParams`, `showMirrorAction`, `accountBlocked`, `Loading.accountBlocked`, `LauncherVM`'s bus parameter) fail on the base commit by not compiling; the implementer confirms red by running AC-1 against base `apiCall` with a local stub of the event shape first, or records the compile failure as the red run.

Collectors started with `UnconfinedTestDispatcher` do not read `isAuthenticated` inside the collector (re-entrant `sessionLock`, design-architect Revision Risks).

Fixture for `KinoPubApiClientSessionEndTest`: the `HttpServer` and `client(...)` helpers of `KinoPubApiClientFailureTest`, an OkHttp interceptor for non-loopback hosts as in `KinoPubApiClientAuthProbeTest`, a stateful in-memory `ICryptoPreferenceRepository` defined in the test, events collected from a real `SessionEventBus` in `backgroundScope`. No live KinoPub host.

## Failing-first test

`KinoPubApiClientSessionEndTest.aUserBannedRefusalClearsTokensAndEmitsBlocked`. On base `b53bf631`, `apiCall` (`data/api/KinoPubApiClient.kt:1031`) calls `requireSuccessful()` and returns the failure; `onSessionExpired` (`:242-246`) is reached only from `sessionExpiredFailure` on a failed refresh, and a 403 never triggers a refresh, so the tokens stay and no event is emitted.

## Manual checks

Device: TV box `192.168.1.110:5555`, package `com.kino.puber.stage` only, lease through `tools/emulator-resource-lock.sh`, `adb -s 192.168.1.110:5555` on every call. No scripted API requests; the app's own launch is the only traffic.

### M-1: banned session lands on Auth (AC-5, AC-6, AC-8, AC-10)

| Step | Action | Expected | Must not happen |
|---|---|---|---|
| 1 | Take the lease, `adb connect 192.168.1.110:5555` | Device `device` state | Any adb call without `-s` |
| 2 | `make DEVICE=192.168.1.110:5555 auth-save` | `.auth/com.kino.puber.stage.tar` refreshed, exit 0 | Proceeding without a fresh snapshot |
| 3 | `./tools/agentw :app:assembleDevDebug` in the worktree; `tools/android-apk-install-preserve` the worktree dev APK; launch the stage app (cold start) | Install succeeds with data kept | Uninstall, data clear, downgrade; a main-checkout APK |
| 4 | Wait for the first screen load; screenshot | Auth in its loading state (no code: a worktree build has no client secret) with the blocked line (`auth_account_blocked`) centred above the spinner, `padding(bottom = 160.dp)`, wrapped, not truncated; after 20 s the mirror hint shows as well (Q6), second screenshot | Auth loading without the blocked line; Home spinner or Home error staying; Home cached rows staying |
| 5 | Watch 30 s | Auth reached once; the blocked line stays | Auth flashing or re-rooting again (Launcher and handler both routing); crash |
| 6 | Press `KEYCODE_DPAD_CENTER` long (`adb -s ... shell input keyevent --longpress 23`) | Mirror dialog opens as today; `KEYCODE_BACK` closes it, Auth focus kept | Focus lost; the blocked line focusable |
| 7 | Press `KEYCODE_BACK` on Auth | Same exit behaviour as base | Navigation back to Home |
| 8 | Do not pair the box again | n/a | Completing the device flow on the banned account |
| 9 | Force-stop the stage app; `make DEVICE=192.168.1.110:5555 auth-restore`; release the lease | Exit 0; saved data back | Launching the worktree APK after the restore (it ends the restored session again) |

Evidence goes to `docs/superpowers/evidence/`, the two screenshots of step 4, scanned with `tools/mobile-evidence-audit.sh`; no raw logcat or tokens.

### M-2: Home error with Retry only (AC-7, design-ux Home)

N/A on the box: the banned session ends at once and routes to Auth (M-1), and no unlisted 403 can be produced without an API probe. Covered by the AC-7 unit tests; initial Retry focus is checked in visual review (`ux-designer`).

## Perf

N/A: perf track off.

## UNTESTABLE

None. AC-10 is the owner's judgement on the M-1 step 4 screenshots.
