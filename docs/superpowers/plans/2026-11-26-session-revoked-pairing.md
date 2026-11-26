# A revoked or banned session sends the user to pairing - plan

**Spec:** `docs/superpowers/specs/2026-11-26-session-revoked-pairing.md`
**Goal:** A KinoPub refusal that means the session is over (401 on a bearer request, 403 `user_banned`) clears the tokens once and re-roots to Auth with the reason; Home and Settings stop offering the mirror for `ApiError.Unauthorized`.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`; full gate: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`. Both run in the worktree `/home/dev/orca/workspaces/Puber/session-revoked-pairing`, never `./gradlew`; set `AGENT_GRADLE_SERIAL=1` when another worktree may build.
- Touched unit tests: `./tools/agentw :app:testDevDebugUnitTest --tests '<FQCN>'`. androidTest sources compile with `./tools/agentw :app:compileDevDebugAndroidTestKotlin`; running them on a device is QA's step (`make DEVICE=<serial> itest`), not the implementer's.
- One task = green fast gate + green tests of the touched code + one commit, message per `One imperative English sentence, no prefixes; no Co-Authored-By line`, never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- No request to a live KinoPub host from any test or script: every test answers from a loopback `HttpServer` or an OkHttp interceptor (AGENTS.md, Runtime And External Systems).
- Out of scope: `session-expiry-delivery` (an application-scope collector for the session clears; this run adds only the pending-end replay of Task 5), `session-logout-interactor` (moving the handler out of `App.kt`), a shared error view (`screen-error-states`), the mirror probe (`EndpointProbe.kt`), `AuthVM`'s existing-session mirror fallback.
- Paths below are under `app/src/main/java/com/kino/puber/` unless they start with `app/`; test paths under `app/src/test/kotlin/com/kino/puber/` unless they start with `app/`.

### Refinements of the spec's design (found while planning)

| Spec says | Plan does | Why |
|---|---|---|
| Only the presented-token check guards off-host refusals (Design, "Which requests may end the session") | Also: `install(Auth)` gets `reAuthorizeOnResponse { it.status == HttpStatusCode.Unauthorized && shouldSendKinoPubToken(it.call.request.url.host) }`, and `requireSuccessfulSession` checks `shouldSendKinoPubToken(host)` before the token | Ktor 3.6.1 `Auth` (`findProvider` in `AuthKt`) picks the single bearer provider for any 401, with or without `WWW-Authenticate`, then refreshes and retries with the bearer attached. Today a GitHub 401 (`getLatestGitHubRelease`, `data/api/KinoPubApiClient.kt:901`) refreshes the KinoPub session and a failed refresh logs the user out, which AC-4 forbids |
| AC-4 test calls `EXTRA_API_BASE_URL` (`getItemCollections`) | AC-4 test calls `getLatestGitHubRelease` (`api.github.com`) | `EXTRA_API_BASE_URL` has the same host as `OAUTH_BASE_URL` (`data/api/config/KinoPubConfig.kt:26-33`, `:88-89`), so `shouldSendKinoPubToken` (`data/api/KinoPubApiClient.kt:1196-1199`) sends the bearer there: it is a KinoPub host. GitHub is the only host the client calls without a bearer |
| `accountBlocked` on `AuthViewState.Content` and `Loading` | On `Content` only | design-ux "States": not shown in `Loading`; AC-6 tests read `Content` |
| Nothing clears Ktor's bearer cache (readiness-1 note 2) | `onSessionExpired` calls `httpClient.clearAuthTokens()` | Re-pairing in the same process is the main path after a session end; without it the next request presents the old token, and the presented-token guard keeps a later refusal from ending the new session until a 401 refresh |
| Settings error from `ApiError.Unauthorized` | `loadDeviceSettings` rethrows an `ApiError.Unauthorized` failure as is instead of wrapping it (`ui/feature/device/settings/vm/DeviceSettingsVM.kt:203`) | Today every failed load becomes `IllegalStateException`, which `DefaultErrorHandler` maps to `Unknown`, so no code says `Unauthorized` |
| Bus without replay accepted (design Risks, "Event dropped with no subscriber") | Task 5: the bus keeps the latest end until a login; `LauncherVM` leaves a pending end to the handler | qa round 1: `AppStartup.start()` (`PuberApp.kt:99`) can end the session before `SessionExpiredHandler` collects; design-architect "Revision 2026-11-26 (qa round 1)" |
| `accountBlocked` on `Content` only (row above) | Task 6: also on `Loading` | qa round 1: Auth stayed in `Loading` (no device code on a worktree build), so the line never showed; same revision |
| A 401 with no stored session refreshes like any other | Task 4: no refresh POST when the stored refresh token is empty | qa round 1 defect 2: seven refresh POSTs with an empty refresh token in 3 s |

---

## Tasks

### Task 1: End the session from `apiCall` with a reason, once

**Covers:** AC-1, AC-2, AC-3, AC-4, AC-5 (unit), AC-9
**Files:**
- Create: `core/session/SessionEndReason.kt` - `enum class SessionEndReason { Expired, Blocked }`
- Modify: `core/session/SessionEventBus.kt:8` - `data object Unauthorized` becomes `data class Unauthorized(val reason: SessionEndReason) : SessionEvent`
- Modify: `data/api/ApiFailures.kt` - next to `httpFailure` (`:48-56`) add `internal val SESSION_ENDING_FORBIDDEN_ERRORS = setOf("user_banned")` and `internal fun sessionEndReasonFor(statusCode: Int, serverMessage: String?): SessionEndReason?`: 401 -> `Expired`; 403 with `serverMessage in SESSION_ENDING_FORBIDDEN_ERRORS` (exact, case-sensitive) -> `Blocked`; else `null`. `requireSuccessful` (`:33-38`) unchanged; `parseApiErrorMessage` (`:70-75`) is reused for `serverMessage`
- Modify: `data/api/KinoPubApiClient.kt`
  - `:147-159` `install(Auth)`: add `reAuthorizeOnResponse { response -> response.status == HttpStatusCode.Unauthorized && shouldSendKinoPubToken(response.call.request.url.host) }` (`shouldSendKinoPubToken` at `:1196-1199`)
  - `:232-237` `sessionExpiredFailure`: call `onSessionExpired(SessionEndReason.Expired, presentedToken = null)`; telemetry line `:235` unchanged
  - `:242-246` `onSessionExpired(reason: SessionEndReason, presentedToken: String?)` under `synchronized(sessionLock)` (`private val sessionLock = Any()`): with a token, act only if `cryptoPreferenceRepository.getAccessToken() == presentedToken`; with `null`, act only if the stored access or refresh token is non-empty. Acting = `clearAccessToken()`, `clearRefreshToken()`, `httpClient.clearAuthTokens()` (import `io.ktor.client.plugins.auth.clearAuthTokens`; drops Ktor's cached `BearerTokens` so the next request reloads them from `loadTokens` at `:149-153`), `sessionEventBus.emit(SessionEvent.Unauthorized(reason))`. Nothing suspends inside the lock: in Ktor 3.6.1 `clearAuthTokens` and `BearerAuthProvider.clearToken()` are non-suspend (`AuthTokenHolder.clearToken` nulls the cache under `tryLock`, or launches the clear in `GlobalScope` when a refresh holds the mutex; the cache then reloads from storage, which is already cleared or holds the new pairing)
  - New `@PublishedApi internal suspend fun HttpResponse.requireSuccessfulSession(): HttpResponse`: on success return `this`; else read the body once (`runCatchingCancellable { bodyAsText() }.getOrNull()` as in `ApiFailures.kt:36`), build `httpFailure(status.value, body)`, take `serverMessage` from it (`ApiError.Unauthorized.serverMessage`, `core/error/ApiError.kt:37-40`), and when `sessionEndReasonFor` is non-null, the host passes `shouldSendKinoPubToken(call.request.url.host)` and the presented token (`call.request.headers[HttpHeaders.Authorization]` minus the `Bearer ` prefix) is non-empty, call `onSessionExpired(reason, token)`; then throw the failure
  - `:1031` `apiCall`: `block().requireSuccessfulSession()`. `thirdPartyApiCall` (`:1054`) and the OAuth calls (`:1071-1103`) keep their paths
- Modify: `ui/feature/root/component/App.kt:94` - `is SessionEvent.Unauthorized ->`; body unchanged (`auth()` gains the reason in Task 2)
- Test (new): `data/api/SessionEndReasonForTest.kt`
- Test (new): `data/api/KinoPubApiClientSessionEndTest.kt`
- Test (unchanged, must stay green): `data/api/KinoPubApiClientAuthProbeTest.kt:75-85`, `data/api/KinoPubApiClientFailureTest.kt:76-88`

**Consumes:** `ICryptoPreferenceRepository` (`data/repository/ICryptoPreferenceRepository.kt:3-17`), `SessionEventBus` (`core/session/SessionEventBus.kt:11-18`), `TokenResponse` JSON names `access_token`, `refresh_token`, `expires_in` (`data/api/auth/OAuthModels.kt:16-21`).
**Produces:** `SessionEndReason`; `SessionEvent.Unauthorized(reason)`; `sessionEndReasonFor(Int, String?)`.

Fixture for `KinoPubApiClientSessionEndTest`: `setUpAndroidLogging` and the `client(...)` / `withServer(...)` / `respond(...)` helpers copied from `KinoPubApiClientFailureTest.kt:38-55`, `:179-231` (`Base64.decode` mocked, so `OAUTH_BASE_URL` is `https://api.example.test/oauth2/`); an OkHttp interceptor as in `KinoPubApiClientAuthProbeTest.kt:133-146` answering every non-`127.0.0.1` host per test and counting refresh posts (`grant_type=refresh_token`); a stateful in-memory `ICryptoPreferenceRepository` defined in the test (tokens in `AtomicReference`, `clearAccessToken` counted in an `AtomicInteger`; a relaxed mock with a fixed token hides the guard); a real `SessionEventBus` collected into a list from `backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))` before the first call. Stored tokens start as `access` / `refresh` unless the row says otherwise.

| Test | Server / interceptor | Asserts |
|---|---|---|
| `aUserBannedRefusalClearsTokensAndEmitsBlocked` (P0 red, AC-1) | `/v1/items/7` 403 `{"error":"user_banned"}` | result `ApiError.Unauthorized`; both tokens `null`; events `== [Unauthorized(Blocked)]` |
| `aRefusalAfterASuccessfulRefreshEndsTheSessionAsExpired` (P0 red, AC-2) | `/v1/items/7` always 401 with `WWW-Authenticate: Bearer`; refresh answered 200 `{"access_token":"new-access","refresh_token":"new-refresh","expires_in":3600}` | one refresh; tokens `null`; events `== [Unauthorized(Expired)]` |
| `anUnlistedForbiddenAnswerKeepsTheSession` (P0 guard, AC-3) | 403 `{"error":"нет доступа"}` | result `ApiError.Unauthorized`; tokens unchanged; no event |
| `aRefusalFromAnotherHostKeepsTheSession` (P0 guard, AC-4; `@ParameterizedTest` over 401 with `WWW-Authenticate: Bearer` and 403 `user_banned`) | `getLatestGitHubRelease("o", "r")`; interceptor answers `api.github.com` with the case, the refresh with 500 | tokens unchanged; no event; zero refresh posts. The 401 case is red on base (Ktor refreshes, the 500 clears the tokens) |
| `aSecondRefusalAfterTheSessionEndedEmitsNothing` (P0 red, AC-5) | two sequential calls, both 403 `user_banned` | one event; `clearAccessToken` count 1 |
| `aParallelBurstOfRefusalsEndsTheSessionOnce` (P1, AC-5) | eight calls, 403 `user_banned`, run with `async` inside `withContext(Dispatchers.IO)` so the refusals really overlap | `clearAccessToken` count 1 (count on the fake: the bus buffer of 1 can hide a second emit); one event |
| `aFailedRefreshEmitsExpired` (P1, AC-9) | 401 with `WWW-Authenticate: Bearer`; refresh 500 | tokens `null`; events `== [Unauthorized(Expired)]` |
| `anOAuthRefusalDuringPairingKeepsThePairingScreen` (P1, design; parameterized 401 and 403 `user_banned`) | no stored tokens; `getDeviceCode()`; interceptor answers `grant_type=device_code` with the case, anything else with 500 | no event |
| `aRepairAfterTheSessionEndedSendsTheNewToken` (P0 red, design: re-pairing in the same process) | `/v1/items/7` records each `Authorization` header; first answer 403 `user_banned`; the test then sets the fake's tokens to `new-access` / `new-refresh` (as the pairing flow does) and calls again, answered 200 `{}` (the second result is not asserted) | second request carries `Bearer new-access`. Red on base: Ktor's cached `access` is sent |
| `aStaleRefusalAfterRepairingKeepsTheNewSession` (P2, design) | the server handler sets the fake's tokens to `new-access` / `new-refresh` before answering 403 `user_banned` to the request sent with `Bearer access` | tokens are `new-access` / `new-refresh`; no event |

`SessionEndReasonForTest.mapsOnlyListedRefusalsToAReason` (P1, AC-3): `@ParameterizedTest` table - 401 any message -> `Expired`; 403 `user_banned` -> `Blocked`; 403 `нет доступа`, 403 `null`, 403 `USER_BANNED`, 404 `user_banned`, 500 `null` -> `null`.

- [x] Write `aUserBannedRefusalClearsTokensAndEmitsBlocked` first against the base `apiCall` with a local stub of `SessionEndReason` / `Unauthorized(reason)`; record the red run (or the compile failure) in the Implementation record
- [x] Add `SessionEndReason`, the event shape and `App.kt:94`
- [x] Add `sessionEndReasonFor` and its test
- [x] Add `reAuthorizeOnResponse`, `onSessionExpired` guard, `requireSuccessfulSession`, switch `apiCall`
- [x] Add `httpClient.clearAuthTokens()` to `onSessionExpired`
- [x] Write the remaining `KinoPubApiClientSessionEndTest` rows
- [x] Run the fast gate and `--tests 'com.kino.puber.data.api.*'`; commit

**Done when:** `./tools/agentw :app:compileDevDebugKotlin` passes and `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.data.api.*'` is green, including the unchanged `KinoPubApiClientAuthProbeTest` and `KinoPubApiClientFailureTest`.

---

### Task 2: Carry the reason to Auth and show the blocked line

**Covers:** AC-6, AC-10 (layout per design-ux), AC-5 (re-root identity)
**Files:**
- Create: `ui/feature/auth/model/AuthScreenParams.kt` - `@Parcelize data class AuthScreenParams(val sessionEndReason: SessionEndReason? = null) : Parcelable`
- Modify: `core/ui/navigation/Screens.kt:9` - `fun auth(sessionEndReason: SessionEndReason? = null): PuberScreen`
- Modify: `ui/ScreensImpl.kt:37-39` - `override fun auth(sessionEndReason: SessionEndReason?) = AuthScreen(AuthScreenParams(sessionEndReason))`
- Modify: `ui/feature/auth/component/AuthScreen.kt:15-23` - constructor `private val params: AuthScreenParams = AuthScreenParams()`; `@IgnoredOnParcel override val key: ScreenKey = params.sessionEndReason?.let { "AuthScreen_${it.name}" } ?: "AuthScreen"` (null keeps today's key, `core/ui/navigation/PuberScreen.kt:8-9`); `scoped { params }` in `buildModule`; pattern: `ui/feature/search/SearchScreen.kt:27-46`
- Modify: `ui/feature/auth/model/AuthViewState.kt:17-22` - `Content` gains `val accountBlocked: Boolean = false`
- Modify: `ui/feature/auth/vm/AuthVM.kt:30-38` - constructor takes `private val params: AuthScreenParams`; the `AuthViewState.Content(...)` built at `:121-125` sets `accountBlocked = params.sessionEndReason == SessionEndReason.Blocked`
- Modify: `ui/feature/auth/component/AuthComponent.kt:68-72`, `:118-137` - pass `state.accountBlocked`; `CodeInfo(code, url, timeLeft, accountBlocked: Boolean = false)`: when true, first item is `Text(stringResource(R.string.auth_account_blocked), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 560.dp))` (`androidx.tv.material3.MaterialTheme`) followed by `Spacer(20.dp)`; not focusable; existing 20/16/20 dp gaps unchanged; preview `:155-159` unchanged
- Modify: `ui/feature/root/component/App.kt:107` - `router.newRootScreen(router.screens.auth(event.reason))`
- Modify: `app/src/main/res/values/strings.xml` - `auth_account_blocked`: `Аккаунт KinoPub заблокирован. Повторная привязка этого устройства к тому же аккаунту не поможет.`
- Modify: `app/src/main/res/values-en/strings.xml` - `auth_account_blocked`: `The KinoPub account is blocked. Pairing this device again with the same account will not help.`
- Modify (compile only): `app/src/androidTest/kotlin/com/kino/puber/core/ui/navigation/component/FlowComponentRemoteHotkeyTest.kt:101`, `app/src/androidTest/kotlin/com/kino/puber/ui/feature/episodeschedule/component/EpisodeSchedulePushBackFocusTest.kt:121` - `override fun auth(sessionEndReason: SessionEndReason?): PuberScreen = unsupported()`
- Unchanged callers: `ui/feature/root/vm/LauncherVM.kt:55` (`auth()`), `app/src/test/kotlin/com/kino/puber/ui/feature/root/vm/LauncherVMTest.kt:115` mock (`auth()` matches `auth(null)`)
- Test: `ui/feature/auth/vm/AuthVMTest.kt` - `createVM` (`:52-68`) gains `params: AuthScreenParams = AuthScreenParams()`
- Test: `ui/ScreensImplTest.kt`

**Consumes:** `SessionEndReason`, `SessionEvent.Unauthorized(reason)` (Task 1).
**Produces:** `Screens.auth(SessionEndReason?)`, `AuthScreenParams`, `AuthViewState.Content.accountBlocked`, string `auth_account_blocked`.

| Test | Setup | Asserts |
|---|---|---|
| `AuthVMTest.onStart_showsAccountBlockedLine_whenSessionEndedBlocked` (P0 red) | `isAuthenticated() == false`, `getAuthState()` emits `AuthState.Code` as at `AuthVMTest.kt:72-79`; `AuthScreenParams(SessionEndReason.Blocked)` | state `Content`, `accountBlocked == true`, `code == "123456"` |
| `AuthVMTest.onStart_hidesAccountBlockedLine_whenSessionExpired` (P0 guard; `@ParameterizedTest` over `Expired` and `null`) | same | `accountBlocked == false`, code shown |
| `ScreensImplTest.auth_carriesTheSessionEndReasonIntoTheScreenKey` (P1) | `ScreensImpl.auth(...)` | `auth(Blocked).key != auth(null).key`; `auth(Blocked).key == auth(Blocked).key`; `auth(null).key == "AuthScreen"` |

- [x] Write `onStart_showsAccountBlockedLine_whenSessionEndedBlocked` first; record the red run (compile failure on `AuthScreenParams`)
- [x] Navigation, screen, VM, state, content, strings, `App.kt:107`, androidTest fakes
- [x] Run the fast gate, `:app:compileDevDebugAndroidTestKotlin`, `--tests 'com.kino.puber.ui.feature.auth.*' --tests 'com.kino.puber.ui.ScreensImplTest' --tests 'com.kino.puber.ui.feature.root.*'`; commit

**Done when:** the fast gate and `./tools/agentw :app:compileDevDebugAndroidTestKotlin` pass and the three test filters above are green.

---

### Task 3: Drop the mirror button for `Unauthorized` on Home and Settings

**Covers:** AC-7
**Files:**
- Modify: `core/error/ErrorEntity.kt:3-6` - `companion object { const val CODE_UNAUTHORIZED = "Unauthorized" }`
- Modify: `core/error/DefaultErrorHandler.kt:37`, `:74` - use `ErrorEntity.CODE_UNAUTHORIZED`; drop the private constant
- Modify: `ui/feature/home/model/HomeViewState.kt:35-38` - `Error` gains `val showMirrorAction: Boolean = true`
- Modify: `ui/feature/home/vm/HomeVM.kt`
  - `:178-184` `dispatchError`: `HomeViewState.Error(error.message, showMirrorAction = error.code != ErrorEntity.CODE_UNAUTHORIZED, apiDomainDialog = ...)`
  - `:572-575` `LoadRun`: `var unauthorizedFailures = 0`
  - `:367-369` and `:404-405` catch blocks: `if (error is ApiError.Unauthorized) run.unauthorizedFailures += 1` before `log`
  - `:425-430` `onSectionFinished`: `showMirrorAction = run.unauthorizedFailures < TOTAL_SECTIONS` (`TOTAL_SECTIONS` at `:71`); message stays `error_generic` (Q4). The auto-resolve error at `:303-308` keeps the default `true`
- Modify: `ui/feature/home/component/HomeScreenContent.kt:83-87`, `:128-159` - `ErrorView` gains `showMirrorAction: Boolean`; the 8 dp spacer and mirror `TvSafeButton` only when true; Retry gets `Modifier.focusRequester(rememberFocusRequesterOnLaunch())` (`core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:61`; `TvSafeButton` takes `modifier`, `core/ui/uikit/component/TvSafeButton.kt:43-52`) so the single button holds initial focus (design-ux Home)
- Modify: `ui/feature/device/settings/model/DeviceSettingsViewState.kt:21` - `data class Error(val error: String, val showMirrorAction: Boolean = true)`
- Modify: `ui/feature/device/settings/vm/DeviceSettingsVM.kt`
  - `:203`: `throw currentDevice.exceptionOrNull() as? ApiError.Unauthorized ?: IllegalStateException(currentDevice.exceptionOrNull())`
  - `:258-264` `dispatchError`: `DeviceSettingsState.Error(error.message, showMirrorAction = error.code != ErrorEntity.CODE_UNAUTHORIZED)`
- Modify: `ui/feature/device/settings/DeviceSettingsContent.kt:96-100`, `:130-177` - `ErrorView` gains `showMirrorAction`; the mirror `Button` (`:171-173`) only when true; Retry keeps its focus requester and `SettingsTestTags.ErrorRetry`
- Test: `ui/feature/home/vm/HomeVMTest.kt` (fixture `setup` `:71-100`, `createVM` `:475-490`)
- Test (new): `ui/feature/device/settings/vm/DeviceSettingsVMErrorTest.kt` (fixture: mocks and `createVM` copied from `DeviceSettingsVMMirrorNavigationTest.kt:47-57`, `:159-186`, with `errorHandler = DefaultErrorHandler(FakeResourceProvider())`)
- Test: `app/src/androidTest/kotlin/com/kino/puber/ui/feature/device/settings/DeviceSettingsContentTest.kt`

**Consumes:** `ApiError.Unauthorized` (`core/error/ApiError.kt:37-40`), `DefaultErrorHandler` mapping (`core/error/DefaultErrorHandler.kt:35-38`).
**Produces:** `ErrorEntity.CODE_UNAUTHORIZED`, `HomeViewState.Error.showMirrorAction`, `DeviceSettingsState.Error.showMirrorAction`.

| Test | Setup | Asserts |
|---|---|---|
| `HomeVMTest.allSectionsRefusedUnauthorized_showsErrorWithoutMirrorAction` (P0 red) | every `interactor.observe*` (`HomeVMTest.kt:87-94`) returns `flow { throw ApiError.Unauthorized("user_banned") }` | state `HomeViewState.Error`, `showMirrorAction == false`, `message == FakeResourceProvider` text of `R.string.error_generic` |
| `HomeVMTest.mixedSectionFailures_keepMirrorAction` (P0 guard) | `observeHotItems` throws `ApiError.Network()`, the rest `Unauthorized` | `showMirrorAction == true` |
| `HomeVMTest.unauthorizedDispatchError_hidesMirrorAction` (P1; parameterized: code `ErrorEntity.CODE_UNAUTHORIZED` -> `false`, `"Network"` -> `true`) | `apiDomainInteractor.autoResolveWorkingDomain()` throws; `errorHandler.proceedInvoke(any(), any())` answers with `ErrorEntity("x", code)` as in `AuthVMTest.kt:110-117` | state `Error` with the expected `showMirrorAction` |
| `DeviceSettingsVMErrorTest.unauthorizedLoadFailure_hidesMirrorAction` (P0 red) | `deviceSettingInteractor.getCurrentDeviceSettings()` returns `flowOf(Result.failure(ApiError.Unauthorized("user_banned")))`; `testOnStart()` | `state` is `DeviceSettingsState.Error` with `showMirrorAction == false` |
| `DeviceSettingsVMErrorTest.networkLoadFailure_keepsMirrorAction` (P1) | same with `ApiError.Network()` | `showMirrorAction == true` |
| `DeviceSettingsContentTest.errorWithoutMirrorAction_showsOnlyFocusedRetry` (P2, instrumented) | `DeviceSettingsContent(state = DeviceSettingsState.Error("x", showMirrorAction = false), apiDomain = ...)` as at `:34-50` | no node with the target context's `R.string.api_domain_open_action` text; `onNodeWithTag(SettingsTestTags.ErrorRetry).assertIsFocused()` after `waitUntil` |

- [x] Write `allSectionsRefusedUnauthorized_showsErrorWithoutMirrorAction` and `unauthorizedLoadFailure_hidesMirrorAction` first; record the red run
- [x] `ErrorEntity` constant, Home state/VM/content, Settings state/VM/content
- [x] Write the remaining rows
- [x] Run the fast gate, `:app:compileDevDebugAndroidTestKotlin`, `--tests 'com.kino.puber.ui.feature.home.*' --tests 'com.kino.puber.ui.feature.device.settings.*' --tests 'com.kino.puber.core.error.*'`, then the full gate; commit

**Done when:** the fast gate, `./tools/agentw :app:compileDevDebugAndroidTestKotlin` and the full gate `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` pass. Device checks M-1 (AC-5, AC-6, AC-8, AC-10) and the instrumented row belong to QA (`qa-device`), per the spec's Manual checks.


---

### Task 4: Skip the refresh when no refresh token is stored

Line numbers in Tasks 4-6 are those of the code after Task 3 (worktree HEAD `e8c81571`).

**Covers:** qa round 1 defect 2; supports AC-5 and AC-8 (device)
**Files:**
- Modify: `data/api/KinoPubApiClient.kt:206-208` `refreshBearerTokens`: right after `val refreshToken = ...` (`:207`), `if (refreshToken.isEmpty()) sessionExpiredFailure()` before `log("Attempting to refresh access token")` and the POST. `sessionExpiredFailure` (`:241-246`) ends a leftover access token through `onSessionExpired(Expired, presentedToken = null)` (acts only while a token is stored, `:257-265`) and keeps its AUTH telemetry
- Test: `data/api/KinoPubApiClientSessionEndTest.kt` (fixture: `env(cacheDir, access, refresh)` at `:320`, `FakePrefs` `:235-250`, `refreshPosts` counted at `:281`, `:296`; `respondUnauthorized` at `:365`)
- Test (unchanged, must stay green): `data/api/KinoPubApiClientAuthProbeTest.kt:75-85` (stores `dead-refresh`, `:127`), `KinoPubApiClientSessionEndTest.aFailedRefreshEmitsExpired` (`:176-184`)

**Consumes:** `sessionExpiredFailure`, `onSessionExpired` (Task 1).
**Produces:** no refresh request without a stored refresh token.

| Test | Setup | Asserts |
|---|---|---|
| `KinoPubApiClientSessionEndTest.aRefusalWithNoStoredSessionSendsNoRefresh` (P0 red) | `env(cacheDir, access = null, refresh = null)`; `/v1/items/7` answers 401 with `WWW-Authenticate: Bearer` | result `ApiError.Unauthorized`; `refreshPosts == 0`; no event. Red on the Task 1 code: one refresh POST |
| `KinoPubApiClientSessionEndTest.anAccessTokenWithoutARefreshTokenEndsTheSessionWithoutARefresh` (P1) | `env(cacheDir, access = "access", refresh = null)`; same server | `refreshPosts == 0`; tokens cleared; events `== [Unauthorized(Expired)]` |

- [x] Write `aRefusalWithNoStoredSessionSendsNoRefresh` first; record the red run
- [x] The guard in `refreshBearerTokens`; the P1 row
- [x] Run the fast gate and `--tests 'com.kino.puber.data.api.*'`; commit

**Done when:** `./tools/agentw :app:compileDevDebugKotlin` passes and `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.data.api.*'` is green.

---

### Task 5: Deliver a session end that happens before the handler collects

**Covers:** AC-5, AC-6, AC-8 (cold start: qa round 1 defect 1)
**Files:**
- Modify: `core/session/SessionEventBus.kt:11-18`:
  - `private class Pending(val event: SessionEvent)` (not a data class: two equal ends must be two `StateFlow` values)
  - `private val pending = MutableStateFlow<Pending?>(null)`
  - `val events: Flow<SessionEvent> = pending.filterNotNull().map { it.event }` (a collector that starts late, or anew, gets the latest end)
  - `val hasPendingEnd: Boolean get() = pending.value != null`
  - `fun emit(event: SessionEvent) { pending.value = Pending(event) }`
  - `fun clearPendingEnd() { pending.value = null }`
  - `SessionEvent` (`:7-9`) unchanged
- Modify: `data/api/KinoPubApiClient.kt`
  - `:257-272` `onSessionExpired`: `sessionEventBus.emit(...)` (`:270`) moves to the top of the acting branch, before `clearAccessToken()` (`:266`); the rest unchanged and still under `sessionLock`. So a reader that finds no token also finds the end pending
  - `:248-249` `isAuthenticated()`: body wrapped in `synchronized(sessionLock)`, so `AuthVM.startAuth` (`ui/feature/auth/vm/AuthVM.kt:59`, through `data/repository/KinoPubRepository.kt:22`) cannot read the token between the emit and the clear
- Modify: `ui/feature/root/vm/LauncherVM.kt`
  - `:13-19` constructor: `private val sessionEventBus: SessionEventBus` after `appStartup` (`:17`), before `ioDispatcher` (`:18`)
  - `:53-55` not-authenticated branch: keep `contentLaunchCoordinator.waitForAuthentication()`; then `if (sessionEventBus.hasPendingEnd) return` (the handler in `App.kt` routes to Auth with the reason and runs the session clears); else `router.newRootScreen(router.screens.auth())` as today
- Modify: `ui/feature/root/component/LauncherScreen.kt:22` - `LauncherVM(get(), get(), get(), get(), get())`
- Modify: `ui/feature/auth/vm/AuthVM.kt`
  - `:32-41` constructor: `private val sessionEventBus: SessionEventBus` (Koin `viewModelOf(::AuthVM)`, `ui/feature/auth/component/AuthScreen.kt:31`, resolves the single at `data/di/modules.kt:66`)
  - `:134` `AuthState.Success ->`: first statement `sessionEventBus.clearPendingEnd()`
- Modify: `ui/feature/root/component/App.kt:78-92` - comment only: the collect also receives an end emitted before it started (or before this composition), so the cold-start warm-up (`core/startup/AppStartup.kt:59-67`) is handled. Body unchanged
- Test (new): `core/session/SessionEventBusTest.kt` (path `app/src/test/kotlin/com/kino/puber/core/session/`)
- Test: `ui/feature/root/vm/LauncherVMTest.kt` - every constructor call (`:43`, `:62`, `:82`, `:102`, `:116`, `:135`, `:149`) passes a `SessionEventBus()` before the dispatcher
- Test: `ui/feature/auth/vm/AuthVMTest.kt` - `createVM` (`:58-76`) gains `sessionEventBus: SessionEventBus = SessionEventBus()` and passes it
- Test (unchanged, must stay green): `data/api/KinoPubApiClientSessionEndTest.kt` (collects `bus.events` before the first call, `:280-287`)

**Consumes:** `SessionEvent.Unauthorized(reason)`, `Screens.auth(reason)` (Tasks 1-2).
**Produces:** `SessionEventBus.hasPendingEnd`, `SessionEventBus.clearPendingEnd()`, replaying `events`; `LauncherVM(router, prefs, coordinator, startup, sessionEventBus, ioDispatcher)`; `AuthVM(..., params, sessionEventBus)`.

| Test | Setup | Asserts |
|---|---|---|
| `SessionEventBusTest.aCollectorThatStartsAfterTheEndStillReceivesIt` (P0 red) | `emit(Unauthorized(Blocked))`, then `withTimeoutOrNull(1.seconds) { bus.events.first() }` in `runTest` | `== Unauthorized(Blocked)`. Red on base: `null` (no replay) |
| `SessionEventBusTest.twoEqualEndsReachACollectorTwice` (P1) | collector on `backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))` into a list; `emit(Unauthorized(Blocked))` twice | list has two items |
| `SessionEventBusTest.clearingThePendingEndStopsTheReplay` (P1) | `emit`, `clearPendingEnd()`, then a new collector as in the P0 row | `hasPendingEnd == false`; `withTimeoutOrNull` returns `null` |
| `LauncherVMTest.start_leavesAuthRoutingToTheSessionHandler_whenASessionEndIsPending` (P0 red) | `preferences(accessToken = null)`; a bus with `emit(Unauthorized(Blocked))`; `testOnStart()`, `advanceUntilIdle()` | `verify(exactly = 0) { router.newRootScreen(*anyVararg()) }`. Red on base: `auth()` opened |
| `LauncherVMTest.start_opensAuthScreenWithoutAccessToken` (existing `:112`, P0 guard) | empty bus | `auth()` opened once, as today |
| `AuthVMTest.authSuccess_clearsThePendingSessionEnd` (P1) | bus with a pending end; `isAuthenticated() == true`, `getAuthState()` returns `flowOf(AuthState.Success)` as at `AuthVMTest.kt:137-149` | `bus.hasPendingEnd == false`; `main()` opened |

- [x] Write `aCollectorThatStartsAfterTheEndStillReceivesIt` and `start_leavesAuthRoutingToTheSessionHandler_whenASessionEndIsPending` first; record the red runs
- [x] Bus, client order and lock, `LauncherVM` and its Koin line, `AuthVM`, `App.kt` comment, test fixtures
- [x] Write the remaining rows
- [x] Run the fast gate and `--tests 'com.kino.puber.core.session.*' --tests 'com.kino.puber.ui.feature.root.*' --tests 'com.kino.puber.ui.feature.auth.*' --tests 'com.kino.puber.data.api.*'`; commit

**Done when:** the fast gate passes and the four test filters above are green.

---

### Task 6: Show the blocked line while Auth has no code

**Covers:** AC-6, AC-8, AC-10 (box: a worktree build gets no device code, qa round 1)
**Files:**
- Modify: `ui/feature/auth/model/AuthViewState.kt:10-14` - `Loading` gains `val accountBlocked: Boolean = false`
- Modify: `ui/feature/auth/vm/AuthVM.kt`
  - one private property `private val accountBlocked: Boolean get() = params.sessionEndReason == SessionEndReason.Blocked`, used by the `Content` at `:127`
  - every `AuthViewState.Loading(...)` built at `:66`, `:85`, `:96`, `:172`, `:195` passes `accountBlocked = accountBlocked`. `initialViewState` (`:43`) stays as is: `PuberVM` reads it in its constructor (`core/ui/PuberVM.kt:42-44`), before `params` is set, and `onStart` replaces it at once. The hint copy at `:219` keeps the flag
- Modify: `ui/feature/auth/component/AuthComponent.kt`
  - `:127-134`: the blocked `Text` moves into `@Composable private fun AccountBlockedLine(modifier: Modifier = Modifier)` (same string, `bodyLarge`, `colorScheme.error`, `TextAlign.Center`, `widthIn(max = 560.dp)` then `modifier`); `CodeInfo` calls it, its 20 dp spacer stays
  - `:77-80` passes `accountBlocked = state.accountBlocked`; `LoadingInfo` (`:97-120`) gains `accountBlocked: Boolean` and, when true, draws `AccountBlockedLine(Modifier.align(Alignment.Center).padding(bottom = 160.dp))`, mirroring the status message's `top = 160.dp` (`:108`); placement per ux-designer (design-architect Q7)
- Test: `ui/feature/auth/vm/AuthVMTest.kt` (`createVM` as changed in Task 5)

**Consumes:** `AuthScreenParams` (Task 2).
**Produces:** `AuthViewState.Loading.accountBlocked`.

| Test | Setup | Asserts |
|---|---|---|
| `AuthVMTest.loading_showsAccountBlockedLine_whenNoCodeArrives` (P0 red) | `AuthScreenParams(SessionEndReason.Blocked)`; `isAuthenticated() == false`; `getAuthState()` returns a flow that never emits (`flow { awaitCancellation() }`) | state `AuthViewState.Loading` with `accountBlocked == true`. Red on base: no such field (compile failure) |
| `AuthVMTest.loading_hidesAccountBlockedLine_whenSessionExpired` (P1; `@ParameterizedTest` over `Expired` and `null`) | same | `accountBlocked == false` |

- [x] Write `loading_showsAccountBlockedLine_whenNoCodeArrives` first; record the red run
- [x] State, VM, content
- [x] Run the fast gate, `--tests 'com.kino.puber.ui.feature.auth.*'`, then the full gate; commit

**Done when:** the fast gate and `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` pass. M-1 (QA, `qa-device`) then expects on a worktree build: Auth in `Loading` with the blocked line once, no second re-root.

---

## Implementation record

<!-- owner: implementer -->

**Commits:** Task 1 `7349f238`, Task 2 `a76c87ba`, Task 3 `d3d3f6b3`, Task 4 `5ad691e2`, Task 5 `e679abbf`, Task 6 (this record is committed with it).

**Red run** (P0 `red` tests on the unchanged code; Task 1 ran against base `apiCall` with only the `SessionEndReason` / `Unauthorized(reason)` types stubbed in):

- red: aUserBannedRefusalClearsTokensAndEmitsBlocked - expected: <null> but was: <access> (tokens not cleared)
- red: aRefusalAfterASuccessfulRefreshEndsTheSessionAsExpired - expected: <null> but was: <new-access> (retried 401 ends nothing)
- red: aSecondRefusalAfterTheSessionEndedEmitsNothing - expected: <1> but was: <0> (no clear, no event)
- red: aRepairAfterTheSessionEndedSendsTheNewToken - expected: [Bearer access, Bearer new-access] but was: [Bearer access, Bearer access]
- red: aParallelBurstOfRefusalsEndsTheSessionOnce (P1) - expected: <1> but was: <0>
- red: anOAuthRefusalDuringPairingKeepsThePairingScreen[401] (P1) - base emits an event from the failed refresh with no stored tokens
- green on base: aRefusalFromAnotherHostKeepsTheSession (401 and 403) - guard row; Ktor 3.6.1 did not refresh on the GitHub 401 in the test, so the plan's premise (red on base) does not hold; `reAuthorizeOnResponse` is added as the plan says
- green on base: anUnlistedForbiddenAnswerKeepsTheSession, aFailedRefreshEmitsExpired, aStaleRefusalAfterRepairingKeepsTheNewSession - guard / P1-P2 rows
- red (compile failure, new API): AuthVMTest.onStart_showsAccountBlockedLine_whenSessionEndedBlocked - unresolved `AuthScreenParams`, no `params` parameter
- red (revision, Tasks 4-6; stubs only where the API is new):
  - red: aRefusalWithNoStoredSessionSendsNoRefresh - refreshPosts expected 0 but was 1
  - red: anAccessTokenWithoutARefreshTokenEndsTheSessionWithoutARefresh - refreshPosts expected 0 but was 1
  - red: SessionEventBusTest.aCollectorThatStartsAfterTheEndStillReceivesIt - expected the event but was null (stub bus, no replay)
  - red: LauncherVMTest.start_leavesAuthRoutingToTheSessionHandler_whenASessionEndIsPending - `router.newRootScreen` called (auth opened)
  - red: AuthVMTest.loading_showsAccountBlockedLine_whenNoCodeArrives - accountBlocked false (stub field)
  - green on stub: twoEqualEndsReachACollectorTwice, clearingThePendingEndStopsTheReplay, authSuccess_clearsThePendingSessionEnd (the last two pass trivially on a stub `hasPendingEnd == false`; they discriminate once the bus is real), loading_hidesAccountBlockedLine_whenSessionExpired (guard)
- red (compile failure, new API): HomeVMTest.allSectionsRefusedUnauthorized_showsErrorWithoutMirrorAction, DeviceSettingsVMErrorTest.unauthorizedLoadFailure_hidesMirrorAction - unresolved `showMirrorAction`

**Deviations from the plan:**

- `reAuthorizeOnResponse` is declared on `AuthConfig` (directly in `install(Auth)`), not inside `bearer { }`: it is not a member of the bearer config.
- `ScreenKey` is Voyager's (`cafe.adriel.voyager.core.screen.ScreenKey`), not a project type.
- The test response helper sets `Content-Type` as a header: OkHttp's `toResponseBody(mediaType)` is not copied into the headers Ktor reads, and without it the refresh body fails to deserialize (the first AC-2 run passed on base for that wrong reason).
- `HttpServer` in `KinoPubApiClientSessionEndTest` gets a thread pool so the burst test overlaps.

- Tasks 4-6: the `LauncherVMTest` constructor calls are wrapped over several lines (detekt MaxLineLength), in the Task 6 commit.

**Files touched outside the plan:** none.

**For the reviewers:**

- `onSessionExpired` holds `synchronized(sessionLock)` across two blocking prefs reads and the clears; `clearAuthTokens` and `emit` do not suspend.
- A 401 on a request whose bearer was empty (no tokens) never ends the session; the presented token must be non-empty.
- Ru string `auth_account_blocked` is in `values/strings.xml` as planned, although the ru `auth_mirror_content_hint` sits in `values/api_domain_strings.xml`.
- The instrumented row `DeviceSettingsContentTest.errorWithoutMirrorAction_showsOnlyFocusedRetry` compiles but was not run (QA, device).
- Tasks 4-6: `onSessionExpired` emits before it clears and `isAuthenticated()` takes `sessionLock`; the bus replays the latest end until `AuthVM` sees a pairing succeed (`App` re-routes to Auth on a re-composition while an end is still pending).
- Home's Retry now takes initial focus through `rememberFocusRequesterOnLaunch`.

**Blocked:** none.

### Fix round 1

Triage `triage-1.md`, items 1-8. Red run on the unchanged behaviour (new API stubbed: `ApiError.Unauthorized.endsSession` default true, `SessionEventBus.claimPendingEnd` returns true, the three preference methods exist):

- red: KinoPubApiClientSessionEndTest.aPairingCallBeforeTheNewTokensAreSavedDoesNotPinAnEmptyBearer - assertion failed at the request-header / refresh-count check (item 1)
- red: LauncherVMTest.start_leavesRoutingToTheSessionHandler_whenATokenIsStillReadableAndAnEndIsPending - `newRootScreen` called (item 2)
- red: LauncherVMTest.start_opensAuthWithThePersistedReason_afterTheProcessDiedPastASessionEnd - `auth(Blocked)` not routed (item 3)
- red: KinoPubApiClientSessionEndTest.theEndReasonIsPersistedWhenTheSessionEnds - reason null (item 3)
- red: KinoPubRepositoryAuthStateTest.aSuccessfulPairingClearsThePersistedSessionEndReason - `clearSessionEndReason` not called (item 3)
- red: SessionEventBusTest.anEndCanBeClaimedOnlyOnce, nothingIsClaimedWithoutAnEnd - stub claims always (item 4)
- red: KinoPubApiClientSessionEndTest.anUnlistedForbiddenAnswerSaysItKeepsTheSession, DefaultErrorHandlerTest.map_keepsTheGenericMessageForARefusalThatLeavesTheSessionAlone, HomeVMTest.allSectionsRefusedWithoutEndingTheSession_keepMirrorAction, DeviceSettingsVMErrorTest.refusalThatKeepsTheSession_keepsMirrorActionAndGenericMessage - flag/branch absent (item 5)
- green on base: KinoPubApiClientSessionEndTest.theEndIsPendingBeforeTheTokenIsCleared (item 6) - `onSessionExpired` already emits first; the test pins it (guard)
- green on stub: SessionEventBusTest.aNewEndCanBeClaimedAgain, LauncherVMTest.start_opensPlainAuth_whenThePersistedReasonIsUnknown, KinoPubApiClientSessionEndTest.aRefusalThatEndsTheSessionSaysSo - guards for the real implementation
- Item 7 (comment) and item 8 (preview) have no test.

Deviations and notes:

- `ApiError.Unauthorized.endsSession` (default true) carries the session-ending decision; `httpFailure` sets it from `sessionEndReasonFor`. An unlisted 403 maps to the generic message with code `Refused`, so Home and Settings keep their mirror action.
- The end reason is persisted in `ICryptoPreferenceRepository` (`saveSessionEndReason` / `getSessionEndReason` / `clearSessionEndReason`) from `onSessionExpired`, failure-tolerant, and cleared in `KinoPubRepository.getAuthState` when a pairing saves tokens.
- `SessionEventBus.claimPendingEnd()` gives the handler's clears once per end; the `App` handler itself has no unit test (composable); the claim is covered at the bus.
- Item 7: the AC-4 row in the test design still says the test discriminates `reAuthorizeOnResponse`; the test-designer corrects it (not edited here).
- Item 8: `CodeInfoBlockedPreview` for the owner's AC-10 check.

## Readiness

# Readiness 1: session-revoked-pairing

Verdict: **READY**

Inputs: spec `docs/superpowers/specs/2026-11-26-session-revoked-pairing.md`, plan `docs/superpowers/plans/2026-11-26-session-revoked-pairing.md` (worktree HEAD `de3634e9`, code identical to base `b53bf631`), `test-design.md`.

## AC coverage

| AC | Plan task | Test design row |
|---|---|---|
| AC-1 | Task 1 | `KinoPubApiClientSessionEndTest.aUserBannedRefusalClearsTokensAndEmitsBlocked` (P0 red) |
| AC-2 | Task 1 | `aRefusalAfterASuccessfulRefreshEndsTheSessionAsExpired` (P0 red) |
| AC-3 | Task 1 | `anUnlistedForbiddenAnswerKeepsTheSession` (P0 guard), `SessionEndReasonForTest` (P1) |
| AC-4 | Task 1 | `aRefusalFromAnotherHostKeepsTheSession` (P0 guard) |
| AC-5 | Task 1, Task 2 (screen key) | `aSecondRefusalAfterTheSessionEndedEmitsNothing` (P0 red), burst P1, M-1 step 5 |
| AC-6 | Task 2 | `AuthVMTest.onStart_showsAccountBlockedLine_whenSessionEndedBlocked` (P0 red), `..._hidesAccountBlockedLine_whenSessionExpired` (P0 guard), M-1 step 4 |
| AC-7 | Task 3 | `HomeVMTest.allSectionsRefusedUnauthorized_showsErrorWithoutMirrorAction` (P0 red), `mixedSectionFailures_keepMirrorAction` (P0 guard), `DeviceSettingsVMErrorTest.unauthorizedLoadFailure_hidesMirrorAction` (P0 red) |
| AC-8 | QA (M-1) | M-1 |
| AC-9 | Task 1 | `KinoPubApiClientAuthProbeTest.sessionExpiredFailureRecordsAuthErrThenThrowsUnauthorized` (existing, P0 guard, at `KinoPubApiClientAuthProbeTest.kt:75`) |
| AC-10 | Task 2 (layout), QA | M-1 step 4 screenshot, owner |

Every P0 row names the defect it catches, is marked `red` or `guard`, and names its test in backticks.

## Citations sampled (all resolve in the worktree)

| Citation | Resolves to |
|---|---|
| `data/api/KinoPubApiClient.kt:1031` | `block().requireSuccessful()` in `apiCall` |
| `data/api/KinoPubApiClient.kt:232-237`, `:242-246` | `sessionExpiredFailure`, `onSessionExpired` |
| `data/api/KinoPubApiClient.kt:901`, `:1196-1199` | `getLatestGitHubRelease` via `thirdPartyApiCall`; `shouldSendKinoPubToken` |
| `data/api/config/KinoPubConfig.kt:26-33` | `oauthBaseUrl` and `extraBaseUrl` share `apiHost` (plan refinement for AC-4 holds) |
| `data/api/ApiFailures.kt:48-56` | `httpFailure`: 401/403 -> `ApiError.Unauthorized(serverMessage)` |
| `ui/feature/device/settings/vm/DeviceSettingsVM.kt:203` | `throw IllegalStateException(currentDevice.exceptionOrNull())` |
| `ui/feature/home/vm/HomeVM.kt:71`, `:367-369`, `:404-405`, `:572-575` | `TOTAL_SECTIONS = 8`, both catch blocks, `LoadRun` |
| `core/error/DefaultErrorHandler.kt:74` | private `CODE_UNAUTHORIZED` |
| `ui/feature/root/component/App.kt:94`, `:107` | `SessionEvent.Unauthorized ->`, `router.screens.auth()` |
| `ui/feature/search/SearchScreen.kt:27-46` | params + `@IgnoredOnParcel` key + `scoped { params }` pattern |
| `FlowComponentRemoteHotkeyTest.kt:101`, `EpisodeSchedulePushBackFocusTest.kt:121` | the only other `Screens.auth()` overrides |

Ktor 3.6.1 `AuthConfig` has `reAuthorizeOnResponse` (checked in `ktor-client-auth-jvm-3.6.1.jar`).

## Build order

Task 1 keeps the build green: the only `SessionEvent.Unauthorized` uses are `App.kt:94` and `KinoPubApiClient.kt:245`, both edited; no test references the event. Task 2 changes `Screens.auth` with a default argument; all implementers are listed, `AuthScreen()` in `AppRemoteHotkeyHandlerTest.kt:111` keeps compiling. Task 3 depends on neither. Order follows the dependencies.

## Scope and questions

Plan's out-of-scope list matches spec Decision Q3; no task touches `EndpointProbe.kt`, the bus, or `AuthVM`'s mirror fallback. Q1-Q5 and plan questions 1-3 are answered in spec Decisions.

## Notes for the implementer (non-blocking)

| Note | Where |
|---|---|
| Spec Test design AC-4 row still names `EXTRA_API_BASE_URL`; plan and `test-design.md` use `getLatestGitHubRelease` (spec Decisions row 2). Follow the plan | spec Traceability AC-4 |
| Ktor's bearer provider caches tokens and nothing clears them (no `clearToken` in `app/src/main`); after a re-pair in the same process the presented token can differ from the stored one, so the stale-token guard leaves a later refusal non-ending until a 401 refresh. Pre-existing; record in "For the reviewers" if a test shows it | `KinoPubApiClient.kt:147-158` |

### Fix round 2

Triage `triage-2.md`, items 1, 2, 4 (3 is a spec amendment; code and `DefaultErrorHandlerTest.map_keepsTheGenericMessageForARefusalThatLeavesTheSessionAlone` already match the amended AC-7, no change).

| Item | Change |
|---|---|
| 1, 2 | `core/session/SessionEndCleanup` holds the clears; `App.kt` calls `run()`. The claim is released when the run did not finish (cancellation), so a replay clears again; the deep-link reset has its own once-per-end flag (`SessionEventBus.claimLinkReset`) |
| 4 | `CodeInfoBlockedPreview` at `spec:width=960dp,height=540dp,dpi=320` inside `PuberTheme` and a centring `Box` on the theme background |

Red run (stub `SessionEndCleanup.run` doing nothing): `red: aReplayedEndKeepsALinkAcceptedSinceAndClearsOnce - AssertionFailedError (SessionEndCleanupTest.kt:32, clears not run)`; `red: aHandlerCancelledMidClearLetsTheReplayClearAgain - AssertionFailedError (SessionEndCleanupTest.kt:46)`; `green on base: noEndMeansNoClears - guard`.

check.full green (unit tests, detektAll, lintDevDebug).
