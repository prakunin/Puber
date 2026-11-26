# A revoked or banned session sends the user to pairing instead of offering a mirror change

Status: approved
Date: 2026-11-26
Slug: session-revoked-pairing
Kind: bug
Size: M
Tracks: ui

## Problem / Why

### Request
Owner (ru, translated): if the device binding session is gone or was revoked (as with our account ban), the app cannot tell and offers to change the mirror instead of pairing the device again.

### Root cause (confirmed)
Confirmed by code trace plus recorded box evidence (`catalogue-filters-country-year-status/qa-report.md:21`, `:51`; `diagnostics-transport-label/qa-report.md:60`: every call answers `{"status":403,"error":"user_banned"}`, the app stays logged in). Full code map: `research.md: Current state in code`. The chain (paths under `app/src/main/java/com/kino/puber/`):

| Where | Fact |
|---|---|
| `data/api/KinoPubApiClient.kt:149-158` | Bearer auth has no `reAuthorizeOnResponse`; only a 401 runs `refreshTokens` |
| `data/api/KinoPubApiClient.kt:197-246` | Only a failed refresh calls `onSessionExpired` (clear tokens, emit `SessionEvent.Unauthorized`); the app's single session-ending path |
| `data/api/KinoPubApiClient.kt:1022-1038` | `apiCall` maps a non-2xx to an `ApiError` and returns it; no session handling, so a 403 or a 401 after a good refresh ends nothing |
| `data/api/ApiFailures.kt:48-56` | 401 and 403 both become `ApiError.Unauthorized(serverMessage)`; `serverMessage` = body `error` (`user_banned`) |
| `ui/feature/home/vm/HomeVM.kt:178-184`, `:420-432` | Errors land in `HomeViewState.Error`; all sections failing gives `error_generic` |
| `ui/feature/home/component/HomeScreenContent.kt:82-87`, `:129-159` | `ErrorView` always offers Retry and the mirror button (`api_domain_open_action`) |
| `ui/feature/device/settings/DeviceSettingsContent.kt:99`, `ui/feature/device/settings/vm/DeviceSettingsVM.kt:258-264` | Same Retry plus mirror pattern on Settings |
| `data/api/network/EndpointProbe.kt:32-46` | Mirror probe is unauthenticated, so a banned session leaves the mirror "healthy" |
| `core/session/SessionEventBus.kt:7-16`, `ui/feature/root/component/App.kt:68-112` | Event has no reason; handler clears session state and re-roots to Auth on every event |

### Fix direction
`research.md: Options` A: in `apiCall`, a refusal of an authenticated KinoPub request that means "session over" (401; 403 with `error` in an allow-list, today `user_banned`) calls `onSessionExpired` with a reason; `SessionEvent` carries the reason; Auth shows a blocked-account line for the blocked reason; Home and Settings error views drop the mirror button for `ApiError.Unauthorized`. Other 403s stay a plain error.

## Acceptance criteria

| ID | Given | When | Then |
|---|---|---|---|
| AC-1 | a stored session and a KinoPub call answering 403 `{"error":"user_banned"}` | the call runs through `apiCall` | both tokens are cleared and one `SessionEvent` with reason blocked is emitted; the call returns `ApiError.Unauthorized` (client unit test, MockWebServer) |
| AC-2 | a stored session, a 401, a successful refresh, then 401 again on the retried call | the call completes | tokens are cleared and one `SessionEvent` with reason expired is emitted (client unit test) |
| AC-3 | a 403 with any other `error` (e.g. `нет доступа`, `KinoPubApiClientFailureTest.kt:76-86`) | the call runs | tokens stay, no session event; the call still returns `ApiError.Unauthorized` (client unit test) |
| AC-4 | a 401 or 403 from a non-KinoPub host (no bearer sent, `KinoPubApiClient.kt:1196`) | the call runs | no session event and tokens stay (client unit test) |
| AC-5 | Home loading sections in parallel, each answering `user_banned` | the burst finishes | tokens are cleared once and the app lands on Auth once, with no second re-root while Auth is shown (unit test on the emit guard; observation on the box) |
| AC-6 | the session ended with reason blocked | Auth opens | it shows the pairing code and a line saying the KinoPub account is blocked and pairing again on it will not help; reason expired shows no such line (AuthVM unit test; observation on the box) |
| AC-7 | Home or Settings in its error state from an authenticated KinoPub call | the refusal ended the session (401, or 403 `user_banned`), or it did not (`ApiError.Unauthorized` from an unlisted 403), or the error is a network or other HTTP error | a session-ending refusal routes to Auth and neither error view shows the mirror button for it; an `ApiError.Unauthorized` that did not end the session shows Retry, the mirror button and `error_generic`; network and other HTTP errors keep Retry and the mirror button (VM or error-handler unit test; AC-8 covers the route on the box) |
| AC-8 | the box `192.168.1.110:5555` with the banned stage session, `auth-save` taken first | the worktree dev APK is installed with `tools/android-apk-install-preserve` and launched | within the first screen load the app shows Auth with the blocked line, not Home's error with the mirror button; afterwards `auth-restore` brings the saved data back (evidence in `docs/superpowers/evidence/`) |
| AC-9 | the existing failed-refresh guard (`KinoPubApiClientAuthProbeTest.kt:75-102`) | the suite runs | it still passes unchanged in intent: failed refresh -> `Unauthorized`, AUTH telemetry |
| AC-10 | the blocked line on the Auth screen | seen on the TV | it fits the Auth layout in size and placement (owner judges) |

AC-7 amended 2026-11-26 (`triage-2.md` #3): an `ApiError.Unauthorized` that did not end the session keeps the mirror action; the original text dropped it.

## Out of scope

The analyst listed none separately; the deferred related runs session-expiry-delivery and session-logout-interactor stay out of this run (Decision Q3).

### Risks (analyst)
- **Wrongful logout.** A per-resource 403 treated as session end wipes a pairing a worktree build cannot redo; the allow-list (AC-3) is the guard. No public source documents KinoPub's 403 uses (`research.md: External findings`).
- **Re-pair loop.** A banned account may pair again and be ejected on the next call; AC-6 text must say so. Whether the device flow works for a banned account is unverified and must not be probed.
- **Box session loss.** AC-8 ends the stage app's session by design; `make DEVICE=192.168.1.110:5555 auth-save` before and `auth-restore` after, only `com.kino.puber.stage`, no uninstall or data clear.
- **Event drop and repeats.** The bus has no replay (`SessionEventBus.kt:12`, run `session-expiry-delivery`); tokens are cleared regardless, and the emit must be guarded so a parallel burst (AC-5) does not re-root repeatedly.
- **Overlap.** `session-logout-interactor` and `screen-error-states` edit the same handler and error views; land this first or rebase.

## Design

### Architect
Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/`.

### Decisions

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

### Changes by layer

#### API / repository

| File | Change |
|---|---|
| `core/session/SessionEventBus.kt:7-9` | `SessionEndReason` enum (same package, own file or here); `Unauthorized` carries `reason` |
| `data/api/ApiFailures.kt:48` | Add `sessionEndReasonFor` and the allow-list constant next to `httpFailure`; `requireSuccessful` (`:34`) unchanged for `thirdPartyApiCall` |
| `data/api/KinoPubApiClient.kt:1031` | `block().requireSuccessfulSession()`; the new function reads the body once, builds `httpFailure`, asks `sessionEndReasonFor`, calls `onSessionExpired(reason, presentedBearer)` when non-null, then throws the failure |
| `data/api/KinoPubApiClient.kt:232-246` | `sessionExpiredFailure` calls `onSessionExpired(SessionEndReason.Expired, presentedToken = null)`; `onSessionExpired` gets the lock and guard above; AUTH telemetry (`:235`) unchanged (AC-9) |

#### ViewModel state and actions

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

#### Screen and DI module

| File | Change |
|---|---|
| `core/ui/navigation/Screens.kt:9` | `fun auth(sessionEndReason: SessionEndReason? = null): PuberScreen` |
| `ui/ScreensImpl.kt:37-39` | `AuthScreen(AuthScreenParams(sessionEndReason))` |
| `ui/feature/auth/component/AuthScreen.kt:16-23` | `params` constructor property, `@IgnoredOnParcel` key, `scoped { params }` |
| `ui/feature/root/component/App.kt:92-108` | `is SessionEvent.Unauthorized ->`; `router.screens.auth(event.reason)` |
| `ui/feature/root/vm/LauncherVM.kt:55` | Unchanged call `auth()` (default `null`) |
| `app/src/androidTest/kotlin/com/kino/puber/core/ui/navigation/component/FlowComponentRemoteHotkeyTest.kt:101`, `app/src/androidTest/kotlin/com/kino/puber/ui/feature/episodeschedule/component/EpisodeSchedulePushBackFocusTest.kt:121` | Fake overrides take the new parameter (compile only) |

#### Compose content

| File | Change |
|---|---|
| `ui/feature/auth/component/AuthComponent.kt:68`, `:119` | `CodeInfo` gains `accountBlocked: Boolean`; draws `auth_account_blocked` per `design-ux.md` |
| `ui/feature/home/component/HomeScreenContent.kt:83-87`, `:129-159` | `ErrorView` gains `showMirrorAction`; mirror `TvSafeButton` and its spacer only when true |
| `ui/feature/device/settings/DeviceSettingsContent.kt:96-100`, `:131-177` | Same; mirror `Button` only when true |

#### Resources

| File | Change |
|---|---|
| `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml` | `auth_account_blocked` (ru default, en): the KinoPub account is blocked; pairing this device again with the same account will not help. Wording from `design-ux.md` if it gives one |

### Alternatives rejected

| Alternative | Reason |
|---|---|
| `reAuthorizeOnResponse` on 403 (research option C) | Refresh may succeed for a banned account; rotates tokens on every per-resource 403; misses 401 after a good refresh |
| Split `ApiError.Unauthorized` into two types (option B) | No automatic logout; owner asked the app to tell by itself |
| Reason held in an in-memory singleton read by `AuthVM` | Lost on process death; the event already carries it |
| Persisting the reason in `ICryptoPreferenceRepository` | New storage and clear-on-login rules for one line of text; not asked |
| Emit guard in `SessionExpiredHandler` (`App.kt:68-112`) | Tokens would still be cleared once per burst caller; the client owns the session |
| Hide mirror when any section failed `Unauthorized` | A mixed network failure would lose the one action that fixes it |

### Risks

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

### Open questions

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| Q4 | Home's error text when every section was refused with `Unauthorized` but the session did not end | Keep `error_generic`; `error_unauthorized` ("session expired") would be wrong for a non-session 403 | no |


### Revision 2026-11-26 (qa round 1)

Inputs: [qa-report.md](qa-report.md) round 1, evidence `docs/superpowers/evidence/2026-11-26-session-revoked-pairing-device.md` and its two screenshots.

#### Evidence reading

| Fact | Source |
|---|---|
| The "stuck spinner" screenshot is Auth's `LoadingInfo`, not Home: `auth_mirror_loading_hint` is drawn only at `ui/feature/auth/component/AuthComponent.kt:113` | `2026-11-26-session-revoked-pairing-stuck-spinner-70s.png` |
| The blocked line is drawn only in `CodeInfo` (`AuthComponent.kt:123-136`), that is only in `AuthViewState.Content` | plan Refinements, row "accountBlocked on Content only" |
| A worktree build gets no device code (400, no client secret); `AuthVM.dispatchError` returns early in `LoginRequired` (`ui/feature/auth/vm/AuthVM.kt:163-167`), so Auth stays in `Loading` and the hint appears after 20 s (`AuthVM.kt:216-220`) | qa-report `UNAVAILABLE` note |
| So the first launch reached Auth in `Loading`, where no blocked line exists; the box does not show whether the event was dropped. The 401/refresh burst is Home loading before the re-root | the two facts above |
| The drop is still possible in code: `AppStartup.start()` runs from `PuberApp.kt:99`, before any Activity composes `SessionExpiredHandler` (`ui/feature/root/component/App.kt:69-110`), and the bus has no replay (`core/session/SessionEventBus.kt:12`) | design Risks, row "Event dropped with no subscriber" |

#### Decisions

| Question | Decision | Why |
|---|---|---|
| A session end that happens before the handler collects | `SessionEventBus` keeps the latest end not yet followed by a login in a `MutableStateFlow<Pending?>`; `Pending` is a plain (non-data) class wrapping the event, so two equal ends are two values. `events = pending.filterNotNull().map { it.event }` replays it to a late or new collector; adds `hasPendingEnd` and `clearPendingEnd()`. `emit`, `events` and `SessionEvent` keep their signatures | Smallest change that survives a cold start; `App.kt` and the Task 1 tests keep compiling |
| Who routes a pending end | Only `SessionExpiredHandler` (body unchanged). `LauncherVM` with no token and `hasPendingEnd` routes nothing | One router per end (AC-5); the handler carries the reason (AC-6) and runs the session clears |
| Order inside `onSessionExpired` | Emit before clearing the tokens, still under `sessionLock`; `isAuthenticated()` (`data/api/KinoPubApiClient.kt:248-249`) reads under `sessionLock` | Launcher: "no token" implies "end pending". `AuthVM.startAuth` reads `isAuthenticated` (`AuthVM.kt:59`, via `KinoPubRepository.kt:22`) and must never see the old token |
| When a pending end stops being news | `AuthVM` calls `clearPendingEnd()` on `AuthState.Success` (`AuthVM.kt:134`) before routing to Main | A later handler subscription (Activity recreated) would otherwise replay an end of a session already replaced |
| Blocked line while Auth has no code | `AuthViewState.Loading` gains `accountBlocked`; `LoadingInfo` draws the same line. Reverses the plan refinement "on `Content` only" | A banned account may get no code; a worktree build never gets one, so without it AC-8 cannot be observed on the box |
| Refresh with no stored refresh token (defect 2) | `refreshBearerTokens` (`KinoPubApiClient.kt:206-208`) calls `sessionExpiredFailure()` before the POST when the stored refresh token is empty | No pointless refresh POST per section; a leftover access token still ends the session through the null-token branch of `onSessionExpired`; AC-9's test stores `dead-refresh` (`KinoPubApiClientAuthProbeTest.kt:127`) and is unchanged |
| Home reaching a final state | No Home change: without the refresh POST each section fails fast with `Unauthorized` and `onSectionFinished` (`ui/feature/home/vm/HomeVM.kt:427-440`) publishes `Error`; the replayed end then re-roots to Auth | The "Home spinner" was Auth's `Loading` (Evidence reading) |

#### Changes by layer

##### API / repository

| File | Change |
|---|---|
| `core/session/SessionEventBus.kt:11-18` | Pending-end `MutableStateFlow`, replaying `events`, `hasPendingEnd`, `clearPendingEnd()` |
| `data/api/KinoPubApiClient.kt:206-208` | Empty stored refresh token: `sessionExpiredFailure()` before `log` and the POST |
| `data/api/KinoPubApiClient.kt:248-249`, `:257-272` | `isAuthenticated` under `sessionLock`; `sessionEventBus.emit` (`:270`) moves before the clears (`:266`) |

##### ViewModel state and actions

| File | Change |
|---|---|
| `ui/feature/root/vm/LauncherVM.kt:13-19`, `:53-55` | Takes `SessionEventBus`; no token and a pending end: `waitForAuthentication()` and no route |
| `ui/feature/auth/vm/AuthVM.kt:32-41`, `:134` | Takes `SessionEventBus`; `clearPendingEnd()` on `Success` |
| `ui/feature/auth/vm/AuthVM.kt:66`, `:85`, `:96`, `:172`, `:195`, `:127` | Every `Loading` built after start and the `Content` carry `accountBlocked` |
| `ui/feature/auth/model/AuthViewState.kt:10-14` | `Loading` gains `accountBlocked: Boolean = false` |

##### Screen and DI module

| File | Change |
|---|---|
| `ui/feature/root/component/LauncherScreen.kt:22` | Fifth `get()` for the bus |
| `ui/feature/auth/component/AuthScreen.kt:31` | Unchanged: `viewModelOf(::AuthVM)` resolves the bus single (`data/di/modules.kt:66`) |
| `ui/feature/root/component/App.kt:78-92` | Comment only: the collect now also receives an end emitted before it started |

##### Compose content

| File | Change |
|---|---|
| `ui/feature/auth/component/AuthComponent.kt:77-80`, `:97-120`, `:127-136` | Blocked line extracted to one private composable, drawn by `CodeInfo` as today and by `LoadingInfo` above the spinner (placement: ux-designer, Q7) |

##### Resources

N/A: reuses `auth_account_blocked`.

#### Alternatives rejected

| Alternative | Reason |
|---|---|
| `replay = 1` on the `SharedFlow` | Needs the same clear on login (`resetReplayCache`) and still conflates nothing: no simpler |
| `LauncherVM` routes `auth(pendingReason)` itself | Races the handler, whose clears suspend before its re-root: two re-roots (AC-5) |
| The handler consumes the pending end | On an Activity recreated without a re-pair, the Launcher yields and nobody routes |
| Persisting the reason | Still rejected (Decisions above, base design); after process death Auth opens without the line |
| Application-scope collector for the clears | That is `session-expiry-delivery` (Q3) |

#### Risks

| Risk | Where | Note |
|---|---|---|
| Activity recreated on Auth after an end, no re-pair: the new subscription replays the end and re-roots to an Auth with the same key; the code request restarts | `App.kt:92`, `SessionEventBus` | Rare on a TV (no rotation); one extra re-root, same screen |
| A same-thread unconfined collector would run inside `sessionLock` before the clears and read the old token (the lock is re-entrant) | `KinoPubApiClient.kt:257-272` | The handler collects on the main dispatcher, which dispatches; tests that collect unconfined must not read `isAuthenticated` inside the collector |
| A login path other than `AuthVM` leaves the pending end set; the next Activity would re-root to Auth | `AuthVM.kt:134` | Only `AuthVM` handles `AuthState.Success` today |
| `isAuthenticated` now waits for `sessionLock`, sometimes on the main thread | `KinoPubApiClient.kt:248` | Lock holds only prefs reads and clears, as in Task 1 |
| Blocked line and the 20 s mirror hint shown together in `Loading` | `AuthComponent.kt:111-118` | Q6 |

#### Open questions

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| Q6 | Hide Auth's 20 s mirror hint when the reason is blocked? | No: a device-code failure can still be a mirror problem | no |
| Q7 | Placement of the blocked line in Auth `Loading` (ux-designer) | Same style as in `CodeInfo`, centred, `padding(bottom = 160.dp)` mirroring the status message's `top = 160.dp` (`AuthComponent.kt:108`) | no |

### UX (ux-designer)
**No `docs/design/` stand governs this change.** Stands exist for `catalogue`, `content`, `player-controls`, `showall`; none covers Auth, the Home error view or the Settings error view. Per `.flow/config.yaml` `escalate_also`, the conductor runs `designing-a-screen` with the owner for the Auth blocked line before planning, or the owner accepts the proposed numbers below in place of a stand (AC-10 is the owner's call either way). The Home and Settings deltas only remove a button and need no stand.

### Auth: blocked-account line (AC-6, AC-10)

Current layout, `ui/feature/auth/component/AuthComponent.kt:117-134` (`CodeInfo`, centred column): code 32 sp, 20 dp, QR 200 dp, 20 dp, url, 16 dp, time left, 20 dp, mirror hint. Body lines use the default `Text` style (theme `bodyLarge` 16 sp, `core/ui/uikit/theme/Type.kt:11-14`).

| Item | Proposed | Status |
|---|---|---|
| Where | First item of `CodeInfo`, above the code: it is the reason the screen is shown | open (owner) |
| Gap to the code | 20 dp (the column's existing rhythm) | open (owner) |
| Type | `bodyLarge` 16 sp, same as the sibling lines | proposed |
| Colour | `colorScheme.error` for the text only; no container, no icon | open (owner) |
| Width | max 560 dp, centred, `TextAlign.Center`, wraps; never truncated | open (owner) |
| States | Shown only in `AuthViewState.Content` when the reason is blocked; not in `Loading`; reason expired shows nothing and the column keeps today's geometry | fixed by AC-6 |
| Text | Own string (Q2); must say the KinoPub account is blocked and that pairing again will not help | fixed by brief Q2, wording for the owner |

Focus: the line is not focusable. Auth keeps its single focus target, the root `Box` (`AuthComponent.kt:51-63`), so long-press OK still opens the mirror dialog; no new focus order.

### Home error view (AC-7)

`ui/feature/home/component/HomeScreenContent.kt:129-159`: message, 24 dp, Retry (`TvSafeButton` primary, 48 dp), 8 dp, mirror (`TvSafeButton`).

- For `ApiError.Unauthorized`: message, 24 dp, Retry only. Drop the 8 dp spacer with the button, so Retry sits where it sits today.
- Network and other HTTP errors: unchanged.
- No "pair again" button: an `Unauthorized` that reaches this view did not end the session (brief AC-3), so pairing is not the remedy and a pairing action would wipe a session a worktree build cannot restore.
- Focus: Retry is the only focusable; it must hold initial focus when the view appears (today the view has no focus requester; with one button it must not depend on default focus search). Focus visibility: `TvSafeButton` focused state, unchanged.

### Settings error view (AC-7)

`ui/feature/device/settings/DeviceSettingsContent.kt:131-176`: title, message, 20 dp, `Row` spaced 12 dp of Retry + mirror (tv-material3 `Button`).

- For `ApiError.Unauthorized`: the row holds Retry only, centred; title, message and the 20 dp gap unchanged.
- Focus: Retry keeps `rememberFocusRequesterOnLaunch` and `SettingsTestTags.ErrorRetry`; nothing new.

### Shared constants

- Both `ErrorView`s are private per screen and share no constant; parameterise each with a boolean (e.g. `showMirrorAction`) decided in the VM state, not by inspecting the error type in the composable. This is the seam `screen-error-states` will fold into a shared view.
- `TvSafeButton` `ButtonHeight` 48 dp and `ButtonCornerRadius` 24 dp (`core/ui/uikit/component/TvSafeButton.kt:39-40`) are shared app-wide: do not retune.
- `CodeInfo` spacings are used by the preview and the live screen only; adding the line must not change the existing 20/16/20 dp gaps.

## Test design

Inputs: [brief](brief.md), [design-architect](design-architect.md), [design-ux](design-ux.md). Levels from `.flow/roles/test-designer.md`. Test paths are under `app/src/test/kotlin/com/kino/puber/` unless they start with `app/`.

### Risk profile

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

### Traceability

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

### Failing-first test

`KinoPubApiClientSessionEndTest.aUserBannedRefusalClearsTokensAndEmitsBlocked`. On base `b53bf631`, `apiCall` (`data/api/KinoPubApiClient.kt:1031`) calls `requireSuccessful()` and returns the failure; `onSessionExpired` (`:242-246`) is reached only from `sessionExpiredFailure` on a failed refresh, and a 403 never triggers a refresh, so the tokens stay and no event is emitted.

### Manual checks

Device: TV box `192.168.1.110:5555`, package `com.kino.puber.stage` only, lease through `tools/emulator-resource-lock.sh`, `adb -s 192.168.1.110:5555` on every call. No scripted API requests; the app's own launch is the only traffic.

#### M-1: banned session lands on Auth (AC-5, AC-6, AC-8, AC-10)

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

#### M-2: Home error with Retry only (AC-7, design-ux Home)

N/A on the box: the banned session ends at once and routes to Auth (M-1), and no unlisted 403 can be produced without an API probe. Covered by the AC-7 unit tests; initial Retry focus is checked in visual review (`ux-designer`).

### Perf

N/A: perf track off.

### UNTESTABLE

None. AC-10 is the owner's judgement on the M-1 step 4 screenshots.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Which refusals end the session? | yes | answered | Any 401 that reaches apiCall on a bearer request; 403 only for error=user_banned; extend the list only from errors seen in ordinary use |
| Q2 | Distinct blocked-account message on Auth? | yes | answered | Yes, own string; plain expiry keeps error_unauthorized |
| Q3 | Fold in session-expiry-delivery / session-logout-interactor? | no | answered | No; reuse onSessionExpired and the existing handler |
| Q4 | Home error text when every section is refused with Unauthorized but the session did not end? | no | answered | Keep error_generic |
| Q6 | Hide Auth's 20 s mirror hint when blocked? | no | answered | No |
| Q7 | Where does the blocked line go while Auth loads? | no | answered | Same style, centred, padding(bottom = 160.dp) |
| Q5 | No docs/design stand governs the pairing screen or the error views | no | answered | Accept the ux-designer's proposed numbers in place of a stand; the owner may retune later with designing-a-screen |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-26 | Q1-Q5 answered on the recommended answers | owner (standing instruction: run /task:run on recommended answers) |
| 2026-11-26 | Plan questions 1-3 accepted on recommendations: AC-4 test calls getLatestGitHubRelease; reAuthorizeOnResponse limited to KinoPub hosts; Settings shows error_unauthorized for a non-ending Unauthorized, Home keeps error_generic (screen-error-states may unify) | owner (standing instruction) |
| 2026-11-26 | Readiness note 2 taken in: onSessionExpired clears the Ktor bearer cache (clearAuthTokens) so an in-process re-pair sends the new token | conductor |
| 2026-11-26 | AC-7 amended (triage-2 #3): an Unauthorized that did not end the session keeps the mirror action and error_generic | owner (standing instruction; reported at gate 2) |
| 2026-11-26 | Gate 1: approved as specified (size M, track ui) | owner (standing instruction) |
