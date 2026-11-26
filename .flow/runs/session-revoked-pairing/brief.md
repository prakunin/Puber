# Brief: session-revoked-pairing

Kind: bug   Size: M   Tracks: ui   Device verification: yes (auth and session, networking, ViewModel state, TV error views)

## Request

Owner (ru, translated): if the device binding session is gone or was revoked (as with our account ban), the app cannot tell and offers to change the mirror instead of pairing the device again.

## Root cause (confirmed)

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

## Fix direction

`research.md: Options` A: in `apiCall`, a refusal of an authenticated KinoPub request that means "session over" (401; 403 with `error` in an allow-list, today `user_banned`) calls `onSessionExpired` with a reason; `SessionEvent` carries the reason; Auth shows a blocked-account line for the blocked reason; Home and Settings error views drop the mirror button for `ApiError.Unauthorized`. Other 403s stay a plain error.

## Size

M: one goal, but it touches auth and session, networking, ViewModel state and screen wiring (risk areas in `.flow/roles/analyst.md`). Production files: `KinoPubApiClient.kt`, `ApiFailures.kt` or `ApiError.kt`, `SessionEventBus.kt`, `App.kt`, Auth VM and content, Home and Settings content, `strings.xml`; tests listed in `research.md: Current state in code` (tests table).

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

## Risks

- **Wrongful logout.** A per-resource 403 treated as session end wipes a pairing a worktree build cannot redo; the allow-list (AC-3) is the guard. No public source documents KinoPub's 403 uses (`research.md: External findings`).
- **Re-pair loop.** A banned account may pair again and be ejected on the next call; AC-6 text must say so. Whether the device flow works for a banned account is unverified and must not be probed.
- **Box session loss.** AC-8 ends the stage app's session by design; `make DEVICE=192.168.1.110:5555 auth-save` before and `auth-restore` after, only `com.kino.puber.stage`, no uninstall or data clear.
- **Event drop and repeats.** The bus has no replay (`SessionEventBus.kt:12`, run `session-expiry-delivery`); tokens are cleared regardless, and the emit must be guarded so a parallel burst (AC-5) does not re-root repeatedly.
- **Overlap.** `session-logout-interactor` and `screen-error-states` edit the same handler and error views; land this first or rebase.

## Open questions

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| Q1 | Which refusals end the session | Any 401 that reaches `apiCall` on a bearer request; 403 only for `error = user_banned`; extend the list only from errors seen in ordinary use | yes |
| Q2 | A distinct blocked-account message on Auth | Yes, own string; plain expiry keeps `error_unauthorized` | yes |
| Q3 | Fold in `session-expiry-delivery` / `session-logout-interactor` | No; reuse `onSessionExpired` and the existing handler | no |
