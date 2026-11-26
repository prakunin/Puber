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
