# Research: session-revoked-pairing

## Request

Bug (owner, ru, translated): if the device binding session is gone or was revoked (as with our account ban), the app cannot tell and offers to change the mirror instead of pairing the device again.

## Understanding

The app ends a session (clears tokens, goes to the pairing screen) only when a token refresh fails. A refresh runs only on HTTP 401. A banned or revoked session that answers 403 (the box: `{"status":403,"error":"user_banned"}`), or a 401 that survives a successful refresh, becomes `ApiError.Unauthorized` on the one request; the screen shows "Сессия истекла. Войдите в аккаунт ещё раз." next to Retry and a mirror button, the tokens stay, and no path leads to pairing. The fix has to decide which refusals mean "session over", end the session centrally for those, and stop offering the mirror for them.

## Current state in code

| Where | What it does now |
|---|---|
| `app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:147-158` | Ktor bearer provider; `refreshTokens` -> `refreshBearerTokens`. No `reAuthorizeOnResponse`, so only 401 triggers a refresh (Ktor default). |
| `app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:197-246` | Refresh failure (non-2xx or exception) -> `sessionExpiredFailure` -> `onSessionExpired`: clears both tokens, emits `SessionEvent.Unauthorized`. The only session-ending path in the app. |
| `app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:1022-1038` | `apiCall`: `requireSuccessful()` then `asApiFailure()`; no session handling. Every content call goes through it (`updateDeviceInfo`, `getAccountInfo`, device settings, items...). |
| `app/src/main/java/com/kino/puber/data/api/ApiFailures.kt:48-56` | `httpFailure`: 401 and 403 both -> `ApiError.Unauthorized(serverMessage)`; `serverMessage` is the body's `error` string (`user_banned` for the box). |
| `app/src/main/java/com/kino/puber/core/error/ApiError.kt:37-40` | `Unauthorized` doc says it covers both 401/403 and a dead refresh; one type, no reason field. |
| `app/src/main/java/com/kino/puber/core/error/DefaultErrorHandler.kt:35-38` / `app/src/main/res/values/strings.xml:9` | `Unauthorized` -> `error_unauthorized` "Сессия истекла. Войдите в аккаунт ещё раз." Nothing acts on it. |
| `app/src/main/java/com/kino/puber/core/session/SessionEventBus.kt:7-16` | `SessionEvent.Unauthorized` only; no reason. `MutableSharedFlow(extraBufferCapacity = 1)`, no replay. |
| `app/src/main/java/com/kino/puber/ui/feature/root/component/App.kt:68-112` | `SessionExpiredHandler`: on the event clears launch target, TV-home programs, watch index, content cache, prefetcher, then `newRootScreen(auth)`. |
| `app/src/main/java/com/kino/puber/ui/feature/home/vm/HomeVM.kt:178-184`, `:420-432` | `dispatchError` -> `HomeViewState.Error`; when every section fails with nothing published -> `Error(error_generic)` (what the box showed: "Не удалось выполнить запрос"). |
| `app/src/main/java/com/kino/puber/ui/feature/home/component/HomeScreenContent.kt:82-87`, `:129-159` | `ErrorView`: Retry plus a configure-mirror button, whatever the error type. |
| `app/src/main/java/com/kino/puber/ui/feature/device/settings/vm/DeviceSettingsVM.kt:258-264`, `DeviceSettingsContent.kt:131` | Same pattern: error state with Retry and mirror button (the diagnostics-transport-label box finding). |
| `app/src/main/java/com/kino/puber/ui/feature/auth/vm/AuthVM.kt:56-60`, `:159-187` | In `ExistingSession` mode an error walks alternative built-in mirrors, then shows the manual mirror fallback. Reached only when Auth opens with a token; `LauncherVM.kt:41-56` opens Auth only without a token and `App.kt:94-107` clears tokens before navigating, so this path is not the one the owner saw (inference from the call sites). Still wrong for an `Unauthorized` from `updateDeviceInfo` (`KinoPubRepository.kt:24-28` emits `Success` with a token, then `DeviceInfoRepository.kt:106-109` calls the API). |
| `app/src/main/java/com/kino/puber/data/api/network/EndpointProbe.kt:32-46`, `app/src/main/java/com/kino/puber/domain/interactor/api/ApiDomainInteractor.kt:98-108`, `:126` | Mirror probe is an unauthenticated `items/fresh` GET on a separate OkHttp client; a banned session does not fail it, so auto-resolve reports the mirror healthy. |
| `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackErrorPolicy.kt:12-13` | Player has its own 401/403 constants for CDN stream loads; separate from the API path. |

Tests around it:

| Test | Covers |
|---|---|
| `app/src/test/kotlin/com/kino/puber/data/api/KinoPubApiClientFailureTest.kt:76-86` | 403 with `{"error":"нет доступа"}` -> `ApiError.Unauthorized` (pins today's 403 mapping). |
| `app/src/test/kotlin/com/kino/puber/data/api/KinoPubApiClientAuthProbeTest.kt:75-102` | Failed refresh -> `Unauthorized` and auth telemetry; uses a 401 MockWebServer answer. Guard named in `docs/superpowers/specs/2026-11-12-dependency-update-stable.md` AC-4. |
| `app/src/test/kotlin/com/kino/puber/ui/feature/auth/vm/AuthVMTest.kt` | AuthVM modes and mirror fallback. |
| `app/src/test/kotlin/com/kino/puber/ui/feature/home/vm/HomeVMTest.kt` | Home error states. |

Commits: `ecb0f990` (typed API errors, introduced 401/403 -> `Unauthorized`), `97a0fca2` (auth telemetry in `sessionExpiredFailure`), `93a06ca2` (shared mirror dialog mapper).

## Related work

| Run / doc | Relation |
|---|---|
| `session-expiry-delivery` (idea) | RW-6: move session clears to an application-scope collector; the bus drops events with no subscriber. Same bus and handler; does not detect 403 or post-refresh 401. Not a duplicate. |
| `session-logout-interactor` (idea) | SOLID-11: `SessionTerminator` out of `App.kt`. Same handler; a reason-carrying event would touch it. Not a duplicate. |
| `catchup-session-wipe-race` (idea) | Watch-index wipe across logout/re-login; becomes more reachable once more refusals end the session. Not a duplicate. |
| `screen-error-states` (idea) | Shared Home/Settings error views (BP-8, BP-9); the place a "no mirror button for auth errors" rule would live if it lands first. |
| `network-timeouts-failover` (idea) | Mirror failover speed; unrelated except the shared error view. |
| `diagnostics-transport-label` | `qa-report.md:60`: Settings returns `Unauthorized: user_banned`, app stays logged in. |
| `catalogue-filters-country-year-status` | `qa-report.md:21`, `:51`: every box call returns `{"status":403,"error":"user_banned"}`; Home shows cached rows, then "Не удалось выполнить запрос" with Повторить. |
| `docs/superpowers/evidence/2026-10-12-code-quality-audit-report.md:68`, `:97` | RW-6 and SOLID-11 text. |

Probable duplicate: none.

## External findings

| Finding | Source |
|---|---|
| KinoPub errors page documents one example only: 401 with `name: Unauthorized`, "You are requesting with an invalid credential.", `status: 401`. No 403, ban, or per-resource refusal is documented. | https://kinoapi.com/errors.html (API 1.3 docs, fetched 2026-11-26) |
| Auth page: access token `expires_in` 3600; refresh token valid 30 days, older tokens invalid after a refresh; every device-flow error except `authorization_pending` is critical. Refresh documented at `/oauth2/token`; the app posts to `/oauth2/device` (`KinoPubApiClient.kt:202`) and works, so the docs are not exact here. | https://kinoapi.com/authentication.html |
| Removing the current device "равносильно логауту"; nothing on what the API answers afterwards. | https://kinoapi.com/api_device.html |
| Video and user pages document no 403 or subscription refusal for content or media links; subscription is a field of `/v1/user`. | https://kinoapi.com/api_video.html, https://kinoapi.com/user.html |
| Ktor bearer: `refreshTokens` runs only after a 401; concurrent 401s share one refresh; nothing said about a retry that is 401 again (`unverified`: Ktor then returns that response, which matches the conductor's note but was not read in source). | https://ktor.io/docs/client-bearer-auth.html (docs for 3.6.2; project uses 3.6.1, `gradle/libs.versions.toml:15`) |
| `AuthConfig.reAuthorizeOnResponse(block: suspend (HttpResponse) -> Boolean)` / `isUnauthorizedResponse` decide which responses trigger refresh; tied to KTOR-7644 (3.1). Default and since-version not stated on the page. | https://api.ktor.io/ktor-client-auth/io.ktor.client.plugins.auth/-auth-config/index.html, https://ktor.io/changelog/3.1/ |

Not settled by any public source: whether KinoPub uses 403 for per-resource refusals, which `error` strings mean a revoked device, and whether a refresh succeeds for a banned account. The box stayed logged in from 2026-11-25 to 2026-11-26 past the 3600 s token life, which suggests the API answers 403 before any 401 for a banned account, or that refresh still succeeds (`unverified`; settle only from requests the app makes in ordinary use, never by probing).

## Options

| # | Option | Trade-offs | Size |
|---|---|---|---|
| A (recommended) | Decide in the client. In `apiCall`, for a request to the KinoPub host that carried a bearer: a 401 (only reachable after refresh succeeded) or a 403 whose `error` is in a known session-ending set (`user_banned`; others added only when seen) calls `onSessionExpired` with a reason. `SessionEvent` gains a reason (expired / blocked); the Auth screen shows a "account blocked" line for blocked. Home and Settings error views hide the mirror button for `Unauthorized`. | One place, every screen benefits, keeps per-resource 403s non-fatal. Allow-list needs maintenance; concurrent failing calls emit several events (handler is idempotent). Touches auth (risk area) and UI. | M |
| B | Map only, no logout: split `ApiError.Unauthorized` into `SessionEnded` and `Forbidden`; screens get a "pair again" button that runs an explicit logout. | No automatic token wipe, the user decides. Every screen with an error view must be wired; a cached Home still hides the problem; the owner asked for the app to tell by itself. | M |
| C | `reAuthorizeOnResponse { status in 401, 403 }` so 403 triggers a refresh and the existing refresh-failure path ends the session. | Smallest diff. Ends nothing if refresh succeeds for a banned account (box evidence hints it may); turns every per-resource 403 into a token rotation; a 401 after a good refresh is still not handled. | S |

## Risks

- **Wrongful logout.** A 403 that is per-resource (unknown for KinoPub) treated as session end wipes a pairing the user cannot always restore (a worktree build has no client secret). The allow-list in A limits this; a blanket "403 ends session" does not.
- **Re-pair loop for a banned account.** Pairing a banned account may succeed and the next call answers `user_banned` again, sending the user back to pairing each time. The blocked message has to say re-pairing will not help on that account (`unverified` whether the device flow works for a banned account).
- **Device verification destroys the box session.** The box account answers `user_banned` now, so a build with this fix clears `com.kino.puber.stage` tokens on first call. `make DEVICE=192.168.1.110:5555 auth-save` exists (`.auth/com.kino.puber.stage.tar`, saved in diagnostics-transport-label); `auth-restore` works while the app stays installed.
- **Event drop.** `SessionEventBus` drops an event with no subscriber (RW-6); tokens are still cleared, so the next launch lands on Auth, but the session clears are skipped.
- **Concurrent callers.** Home fetches sections in parallel; several `Unauthorized` in one burst must not run the clears and navigation repeatedly in a harmful way (today's handler re-roots to Auth each time).
- **Test churn.** `KinoPubApiClientFailureTest.kt:76-86` pins the 403 mapping and the auth-probe tests guard the refresh path (dependency-update-stable AC-4).

## Open questions

| ID | Question | Recommended answer |
|---|---|---|
| Q1 | Which refusals end the session? | 401 on any authenticated KinoPub call (the bearer layer already tried a refresh); 403 only when `error` is `user_banned`; other 403s stay a plain error with no mirror offer. Extend the list only from errors seen in ordinary use. |
| Q2 | Should a blocked account get its own message on the pairing screen? | Yes: a distinct string saying the account is blocked by KinoPub and pairing again on it will not help; generic expiry keeps `error_unauthorized`. |
| Q3 | Device check on the box would clear the stage app's (already banned) tokens. Allowed? | Yes, once: `auth-save` before, observe the pairing screen, `auth-restore` after; no uninstall or data clear. Needs the owner's explicit yes since it ends the box session. |
| Q4 | Fold in `session-expiry-delivery` / `session-logout-interactor`? | No; keep this run to detection and UI, reuse `onSessionExpired` and the existing handler. |

## Sources

- Code: `app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt`, `app/src/main/java/com/kino/puber/data/api/ApiFailures.kt`, `app/src/main/java/com/kino/puber/core/error/ApiError.kt`, `app/src/main/java/com/kino/puber/core/error/DefaultErrorHandler.kt`, `app/src/main/java/com/kino/puber/core/session/SessionEventBus.kt`, `app/src/main/java/com/kino/puber/ui/feature/root/component/App.kt`, `app/src/main/java/com/kino/puber/ui/feature/root/vm/LauncherVM.kt`, `app/src/main/java/com/kino/puber/ui/feature/auth/vm/AuthVM.kt`, `app/src/main/java/com/kino/puber/ui/feature/home/vm/HomeVM.kt`, `app/src/main/java/com/kino/puber/ui/feature/home/component/HomeScreenContent.kt`, `app/src/main/java/com/kino/puber/ui/feature/device/settings/vm/DeviceSettingsVM.kt`, `app/src/main/java/com/kino/puber/data/repository/KinoPubRepository.kt`, `app/src/main/java/com/kino/puber/data/repository/DeviceInfoRepository.kt`, `app/src/main/java/com/kino/puber/data/api/network/EndpointProbe.kt`, `app/src/main/java/com/kino/puber/domain/interactor/api/ApiDomainInteractor.kt`, `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackErrorPolicy.kt`, `app/src/main/res/values/strings.xml`, `gradle/libs.versions.toml`
- Tests: `KinoPubApiClientFailureTest.kt`, `KinoPubApiClientAuthProbeTest.kt`, `AuthVMTest.kt`, `HomeVMTest.kt`
- Commits: `ecb0f990`, `97a0fca2`, `93a06ca2`
- Runs: `session-expiry-delivery`, `session-logout-interactor`, `catchup-session-wipe-race`, `screen-error-states`, `network-timeouts-failover`, `diagnostics-transport-label`, `catalogue-filters-country-year-status`, `dependency-update-stable`
- Docs: `docs/superpowers/evidence/2026-10-12-code-quality-audit-report.md`, `docs/superpowers/specs/2026-11-12-dependency-update-stable.md`
- URLs: https://kinoapi.com/, https://kinoapi.com/errors.html, https://kinoapi.com/authentication.html, https://kinoapi.com/api_device.html, https://kinoapi.com/api_video.html, https://kinoapi.com/user.html, https://ktor.io/docs/client-bearer-auth.html, https://api.ktor.io/ktor-client-auth/io.ktor.client.plugins.auth/-auth-config/index.html, https://ktor.io/changelog/3.1/
