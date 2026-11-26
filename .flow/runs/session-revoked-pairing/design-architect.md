# Design (architect): session-revoked-pairing

Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/`.

## Decisions

| Question | Decision | Why |
|---|---|---|
| Where a refusal ends the session | A new `@PublishedApi internal suspend fun HttpResponse.requireSuccessfulSession()` in `KinoPubApiClient` replaces `requireSuccessful()` in `apiCall` only (`data/api/KinoPubApiClient.kt:1031`); `thirdPartyApiCall` (`:1054`) and the OAuth calls (`:1071-1103`, own `handleApiResponse`) are untouched | `apiCall` is the one path every content call takes; the OAuth device flow must never eject the pairing screen |
| Which refusals end it (Q1) | Pure `internal fun sessionEndReasonFor(statusCode: Int, serverMessage: String?): SessionEndReason?` in `data/api/ApiFailures.kt`: 401 -> `Expired`; 403 with `serverMessage` in `SESSION_ENDING_FORBIDDEN_ERRORS = setOf("user_banned")` -> `Blocked`; else `null` | Allow-list from brief Q1; pure function is unit-testable without a server |
| What `apiCall` returns | Unchanged: `httpFailure` (`data/api/ApiFailures.kt:48-56`) still maps 401/403 to `ApiError.Unauthorized(serverMessage)` | AC-1..AC-3 keep the return type; `KinoPubApiClientFailureTest.kt:76-86` stays valid |
| Which requests may end the session | Only a request whose `Authorization` header carried a non-empty bearer equal to the stored access token at the moment of the refusal (`response.call.request.headers[HttpHeaders.Authorization]`) | Covers AC-4 (no bearer off-host, `KinoPubApiClient.kt:1196-1199`), the unauthenticated empty bearer from `loadTokens` (`:149-153`), the parallel burst (AC-5) and a stale response landing after a new pairing |
| Emit guard (AC-5) | `onSessionExpired` (`KinoPubApiClient.kt:242-246`) becomes `onSessionExpired(reason: SessionEndReason, presentedToken: String?)` running under `synchronized(sessionLock)`: with a token, it clears and emits only if the stored access token equals it; with `null` (refresh path, `:232-237`), only if a stored access or refresh token is non-empty. First caller clears, later callers see no match and do nothing | One check-and-clear in one place; no suspension inside the lock (`ICryptoPreferenceRepository` getters are blocking, `SessionEventBus.emit` uses `tryEmit`) |
| Event shape | `SessionEvent.Unauthorized` (`core/session/SessionEventBus.kt:8`) becomes `data class Unauthorized(val reason: SessionEndReason)`; new `enum class SessionEndReason { Expired, Blocked }` in `core/session/`, `@Parcelize`-able as a plain enum | Brief fix direction; one reason source |
| How Auth learns the reason | Screen parameter: `Screens.auth(sessionEndReason: SessionEndReason? = null)` (`core/ui/navigation/Screens.kt:9`), `AuthScreen(params: AuthScreenParams = AuthScreenParams())` with `@IgnoredOnParcel override val key = "AuthScreen_" + reason`, `scoped { params }` in `buildModule`, `AuthVM` takes `AuthScreenParams` | Parcelable, survives process death; key changes with reason so a re-root from `AuthScreen` to blocked `AuthScreen` gets a fresh scope and VM; matches `SearchScreen` (`ui/feature/search/SearchScreen.kt:28-41`) |
| Blocked line on Auth (Q2) | `AuthViewState.Content` and `Loading` gain `accountBlocked: Boolean = false`, set from params; `CodeInfo` shows new string `auth_account_blocked` when true; placement and size from `design-ux.md` | AC-6, AC-10; expiry keeps `error_unauthorized` |
| Mirror button off for `Unauthorized` (AC-7) | `ErrorEntity` (`core/error/ErrorEntity.kt:3`) gains `companion object { const val CODE_UNAUTHORIZED = "Unauthorized" }`, used by `DefaultErrorHandler` (`core/error/DefaultErrorHandler.kt:74`); Home and Settings error states gain `showMirrorAction: Boolean` | The VM decides from the type code, content stays pure |
| Home's all-sections-failed error | `LoadRun` (`ui/feature/home/vm/HomeVM.kt:572-575`) gains `unauthorizedFailures`, incremented in `collectSection` / `collectCollections` catch blocks (`:367-369`, `:404-405`) when `error is ApiError.Unauthorized`; `onSectionFinished` (`:420-431`) sets `showMirrorAction = run.unauthorizedFailures < TOTAL_SECTIONS`; message stays `error_generic` | A mixed failure (some network) keeps the mirror; only an all-refused load hides it |

## Changes by layer

### API / repository

| File | Change |
|---|---|
| `core/session/SessionEventBus.kt:7-9` | `SessionEndReason` enum (same package, own file or here); `Unauthorized` carries `reason` |
| `data/api/ApiFailures.kt:48` | Add `sessionEndReasonFor` and the allow-list constant next to `httpFailure`; `requireSuccessful` (`:34`) unchanged for `thirdPartyApiCall` |
| `data/api/KinoPubApiClient.kt:1031` | `block().requireSuccessfulSession()`; the new function reads the body once, builds `httpFailure`, asks `sessionEndReasonFor`, calls `onSessionExpired(reason, presentedBearer)` when non-null, then throws the failure |
| `data/api/KinoPubApiClient.kt:232-246` | `sessionExpiredFailure` calls `onSessionExpired(SessionEndReason.Expired, presentedToken = null)`; `onSessionExpired` gets the lock and guard above; AUTH telemetry (`:235`) unchanged (AC-9) |

### ViewModel state and actions

| File | Change |
|---|---|
| `ui/feature/auth/vm/AuthVM.kt:30` | Constructor takes `AuthScreenParams`; every `AuthViewState.Content` / `Loading` it builds (`:118` and the `Loading(...)` sites) carries `accountBlocked = params.sessionEndReason == SessionEndReason.Blocked` |
| `ui/feature/auth/model/AuthViewState.kt:17` | `accountBlocked: Boolean = false` on `Content` and `Loading` |
| `ui/feature/auth/model/` (new `AuthScreenParams.kt`) | `@Parcelize data class AuthScreenParams(val sessionEndReason: SessionEndReason? = null) : Parcelable` |
| `ui/feature/home/vm/HomeVM.kt:178-184` | `dispatchError` sets `showMirrorAction = error.code != ErrorEntity.CODE_UNAUTHORIZED` |
| `ui/feature/home/vm/HomeVM.kt:367-369`, `:404-405`, `:420-431`, `:572-575` | Count `Unauthorized` failures per run; error state built from the count |
| `ui/feature/home/model/HomeViewState.kt:35` | `Error` gains `showMirrorAction: Boolean = true` |
| `ui/feature/device/settings/vm/DeviceSettingsVM.kt:258-264` | `Error(error.message, showMirrorAction = error.code != ErrorEntity.CODE_UNAUTHORIZED)` |
| `ui/feature/device/settings/model/DeviceSettingsViewState.kt:21` | `Error` gains `showMirrorAction: Boolean = true` |
| `core/error/ErrorEntity.kt:3`, `core/error/DefaultErrorHandler.kt:74` | Constant moves to `ErrorEntity`; handler references it |

### Screen and DI module

| File | Change |
|---|---|
| `core/ui/navigation/Screens.kt:9` | `fun auth(sessionEndReason: SessionEndReason? = null): PuberScreen` |
| `ui/ScreensImpl.kt:37-39` | `AuthScreen(AuthScreenParams(sessionEndReason))` |
| `ui/feature/auth/component/AuthScreen.kt:16-23` | `params` constructor property, `@IgnoredOnParcel` key, `scoped { params }` |
| `ui/feature/root/component/App.kt:92-108` | `is SessionEvent.Unauthorized ->`; `router.screens.auth(event.reason)` |
| `ui/feature/root/vm/LauncherVM.kt:55` | Unchanged call `auth()` (default `null`) |
| `app/src/androidTest/kotlin/com/kino/puber/core/ui/navigation/component/FlowComponentRemoteHotkeyTest.kt:101`, `app/src/androidTest/kotlin/com/kino/puber/ui/feature/episodeschedule/component/EpisodeSchedulePushBackFocusTest.kt:121` | Fake overrides take the new parameter (compile only) |

### Compose content

| File | Change |
|---|---|
| `ui/feature/auth/component/AuthComponent.kt:68`, `:119` | `CodeInfo` gains `accountBlocked: Boolean`; draws `auth_account_blocked` per `design-ux.md` |
| `ui/feature/home/component/HomeScreenContent.kt:83-87`, `:129-159` | `ErrorView` gains `showMirrorAction`; mirror `TvSafeButton` and its spacer only when true |
| `ui/feature/device/settings/DeviceSettingsContent.kt:96-100`, `:131-177` | Same; mirror `Button` only when true |

### Resources

| File | Change |
|---|---|
| `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml` | `auth_account_blocked` (ru default, en): the KinoPub account is blocked; pairing this device again with the same account will not help. Wording from `design-ux.md` if it gives one |

## Alternatives rejected

| Alternative | Reason |
|---|---|
| `reAuthorizeOnResponse` on 403 (research option C) | Refresh may succeed for a banned account; rotates tokens on every per-resource 403; misses 401 after a good refresh |
| Split `ApiError.Unauthorized` into two types (option B) | No automatic logout; owner asked the app to tell by itself |
| Reason held in an in-memory singleton read by `AuthVM` | Lost on process death; the event already carries it |
| Persisting the reason in `ICryptoPreferenceRepository` | New storage and clear-on-login rules for one line of text; not asked |
| Emit guard in `SessionExpiredHandler` (`App.kt:68-112`) | Tokens would still be cleared once per burst caller; the client owns the session |
| Hide mirror when any section failed `Unauthorized` | A mixed network failure would lose the one action that fixes it |

## Risks

| Risk | Where | Note |
|---|---|---|
| Wrongful logout from an unlisted 403 or a 401 the server sends for a per-resource reason | `ApiFailures.kt` `sessionEndReasonFor` | A 401 on a bearer request ends the session per Q1; a worktree build cannot re-pair. Allow-list limited to `user_banned` (AC-3) |
| Ktor behaviour on a 401 after a successful refresh is `unverified` (research: Ktor docs) | `KinoPubApiClient.kt:147-158` | If Ktor refreshes again instead of returning the 401, AC-2 fails; AC-2's MockWebServer test settles it |
| Burst guard proven only with stateful token storage | AC-5 test | A relaxed `mockk` returning a fixed `getAccessToken()` (as in `KinoPubApiClientAuthProbeTest.kt:125-127`) makes every caller match; the AC-5 test needs a fake that clears |
| Lock and main thread | `onSessionExpired` | `apiCall` runs in callers' context (often Main); the lock holds only blocking pref reads and clears, same cost as today's `onSessionExpired` |
| Event dropped with no subscriber | `core/session/SessionEventBus.kt:12` | Tokens are cleared regardless; next launch opens Auth through `LauncherVM.kt:55` without the blocked line. Accepted per Q3 |
| Re-pair loop for a banned account | `AuthVM.kt` after `AuthState.Success`: `setDeviceInformation` is an `apiCall` | Pairing again ends the new session on the first call and returns to blocked Auth; the AC-6 line says so |
| Scope and focus on re-root | `AuthScreen` key | Reason in the key gives a fresh DI scope; Auth's focus is the root `Box` (`AuthComponent.kt`), unchanged |
| Box session loss | AC-8 | Ends the stage session by design; `auth-save` before, `auth-restore` after |
| Baseline profile | `app/src/main/generated/baselineProfiles/` | `SessionEvent$Unauthorized` and `AuthScreen` constructor signatures change; profile entries go stale until regenerated, no correctness impact |

## Open questions

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| Q4 | Home's error text when every section was refused with `Unauthorized` but the session did not end | Keep `error_generic`; `error_unauthorized` ("session expired") would be wrong for a non-session 403 | no |


## Revision 2026-11-26 (qa round 1)

Inputs: [qa-report.md](qa-report.md) round 1, evidence `docs/superpowers/evidence/2026-11-26-session-revoked-pairing-device.md` and its two screenshots.

### Evidence reading

| Fact | Source |
|---|---|
| The "stuck spinner" screenshot is Auth's `LoadingInfo`, not Home: `auth_mirror_loading_hint` is drawn only at `ui/feature/auth/component/AuthComponent.kt:113` | `2026-11-26-session-revoked-pairing-stuck-spinner-70s.png` |
| The blocked line is drawn only in `CodeInfo` (`AuthComponent.kt:123-136`), that is only in `AuthViewState.Content` | plan Refinements, row "accountBlocked on Content only" |
| A worktree build gets no device code (400, no client secret); `AuthVM.dispatchError` returns early in `LoginRequired` (`ui/feature/auth/vm/AuthVM.kt:163-167`), so Auth stays in `Loading` and the hint appears after 20 s (`AuthVM.kt:216-220`) | qa-report `UNAVAILABLE` note |
| So the first launch reached Auth in `Loading`, where no blocked line exists; the box does not show whether the event was dropped. The 401/refresh burst is Home loading before the re-root | the two facts above |
| The drop is still possible in code: `AppStartup.start()` runs from `PuberApp.kt:99`, before any Activity composes `SessionExpiredHandler` (`ui/feature/root/component/App.kt:69-110`), and the bus has no replay (`core/session/SessionEventBus.kt:12`) | design Risks, row "Event dropped with no subscriber" |

### Decisions

| Question | Decision | Why |
|---|---|---|
| A session end that happens before the handler collects | `SessionEventBus` keeps the latest end not yet followed by a login in a `MutableStateFlow<Pending?>`; `Pending` is a plain (non-data) class wrapping the event, so two equal ends are two values. `events = pending.filterNotNull().map { it.event }` replays it to a late or new collector; adds `hasPendingEnd` and `clearPendingEnd()`. `emit`, `events` and `SessionEvent` keep their signatures | Smallest change that survives a cold start; `App.kt` and the Task 1 tests keep compiling |
| Who routes a pending end | Only `SessionExpiredHandler` (body unchanged). `LauncherVM` with no token and `hasPendingEnd` routes nothing | One router per end (AC-5); the handler carries the reason (AC-6) and runs the session clears |
| Order inside `onSessionExpired` | Emit before clearing the tokens, still under `sessionLock`; `isAuthenticated()` (`data/api/KinoPubApiClient.kt:248-249`) reads under `sessionLock` | Launcher: "no token" implies "end pending". `AuthVM.startAuth` reads `isAuthenticated` (`AuthVM.kt:59`, via `KinoPubRepository.kt:22`) and must never see the old token |
| When a pending end stops being news | `AuthVM` calls `clearPendingEnd()` on `AuthState.Success` (`AuthVM.kt:134`) before routing to Main | A later handler subscription (Activity recreated) would otherwise replay an end of a session already replaced |
| Blocked line while Auth has no code | `AuthViewState.Loading` gains `accountBlocked`; `LoadingInfo` draws the same line. Reverses the plan refinement "on `Content` only" | A banned account may get no code; a worktree build never gets one, so without it AC-8 cannot be observed on the box |
| Refresh with no stored refresh token (defect 2) | `refreshBearerTokens` (`KinoPubApiClient.kt:206-208`) calls `sessionExpiredFailure()` before the POST when the stored refresh token is empty | No pointless refresh POST per section; a leftover access token still ends the session through the null-token branch of `onSessionExpired`; AC-9's test stores `dead-refresh` (`KinoPubApiClientAuthProbeTest.kt:127`) and is unchanged |
| Home reaching a final state | No Home change: without the refresh POST each section fails fast with `Unauthorized` and `onSectionFinished` (`ui/feature/home/vm/HomeVM.kt:427-440`) publishes `Error`; the replayed end then re-roots to Auth | The "Home spinner" was Auth's `Loading` (Evidence reading) |

### Changes by layer

#### API / repository

| File | Change |
|---|---|
| `core/session/SessionEventBus.kt:11-18` | Pending-end `MutableStateFlow`, replaying `events`, `hasPendingEnd`, `clearPendingEnd()` |
| `data/api/KinoPubApiClient.kt:206-208` | Empty stored refresh token: `sessionExpiredFailure()` before `log` and the POST |
| `data/api/KinoPubApiClient.kt:248-249`, `:257-272` | `isAuthenticated` under `sessionLock`; `sessionEventBus.emit` (`:270`) moves before the clears (`:266`) |

#### ViewModel state and actions

| File | Change |
|---|---|
| `ui/feature/root/vm/LauncherVM.kt:13-19`, `:53-55` | Takes `SessionEventBus`; no token and a pending end: `waitForAuthentication()` and no route |
| `ui/feature/auth/vm/AuthVM.kt:32-41`, `:134` | Takes `SessionEventBus`; `clearPendingEnd()` on `Success` |
| `ui/feature/auth/vm/AuthVM.kt:66`, `:85`, `:96`, `:172`, `:195`, `:127` | Every `Loading` built after start and the `Content` carry `accountBlocked` |
| `ui/feature/auth/model/AuthViewState.kt:10-14` | `Loading` gains `accountBlocked: Boolean = false` |

#### Screen and DI module

| File | Change |
|---|---|
| `ui/feature/root/component/LauncherScreen.kt:22` | Fifth `get()` for the bus |
| `ui/feature/auth/component/AuthScreen.kt:31` | Unchanged: `viewModelOf(::AuthVM)` resolves the bus single (`data/di/modules.kt:66`) |
| `ui/feature/root/component/App.kt:78-92` | Comment only: the collect now also receives an end emitted before it started |

#### Compose content

| File | Change |
|---|---|
| `ui/feature/auth/component/AuthComponent.kt:77-80`, `:97-120`, `:127-136` | Blocked line extracted to one private composable, drawn by `CodeInfo` as today and by `LoadingInfo` above the spinner (placement: ux-designer, Q7) |

#### Resources

N/A: reuses `auth_account_blocked`.

### Alternatives rejected

| Alternative | Reason |
|---|---|
| `replay = 1` on the `SharedFlow` | Needs the same clear on login (`resetReplayCache`) and still conflates nothing: no simpler |
| `LauncherVM` routes `auth(pendingReason)` itself | Races the handler, whose clears suspend before its re-root: two re-roots (AC-5) |
| The handler consumes the pending end | On an Activity recreated without a re-pair, the Launcher yields and nobody routes |
| Persisting the reason | Still rejected (Decisions above, base design); after process death Auth opens without the line |
| Application-scope collector for the clears | That is `session-expiry-delivery` (Q3) |

### Risks

| Risk | Where | Note |
|---|---|---|
| Activity recreated on Auth after an end, no re-pair: the new subscription replays the end and re-roots to an Auth with the same key; the code request restarts | `App.kt:92`, `SessionEventBus` | Rare on a TV (no rotation); one extra re-root, same screen |
| A same-thread unconfined collector would run inside `sessionLock` before the clears and read the old token (the lock is re-entrant) | `KinoPubApiClient.kt:257-272` | The handler collects on the main dispatcher, which dispatches; tests that collect unconfined must not read `isAuthenticated` inside the collector |
| A login path other than `AuthVM` leaves the pending end set; the next Activity would re-root to Auth | `AuthVM.kt:134` | Only `AuthVM` handles `AuthState.Success` today |
| `isAuthenticated` now waits for `sessionLock`, sometimes on the main thread | `KinoPubApiClient.kt:248` | Lock holds only prefs reads and clears, as in Task 1 |
| Blocked line and the 20 s mirror hint shown together in `Loading` | `AuthComponent.kt:111-118` | Q6 |

### Open questions

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| Q6 | Hide Auth's 20 s mirror hint when the reason is blocked? | No: a device-code failure can still be a mirror problem | no |
| Q7 | Placement of the blocked line in Auth `Loading` (ux-designer) | Same style as in `CodeInfo`, centred, `padding(bottom = 160.dp)` mirroring the status message's `top = 160.dp` (`AuthComponent.kt:108`) | no |
