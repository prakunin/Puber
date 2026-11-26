# Review triage - round 1

Run: session-revoked-pairing   Base: b53bf631   Reviewed head: da172bdf

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md,
review-codex-1.md (ran: review, adversarial), extras: review-antigravity-1.md (ran, clean), review-state-path-1.md, review-ux-1.md.

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | blind F1, codex C1 | minor | `data/api/KinoPubApiClient.kt:151-155`, `:271` | Auth's OAuth requests reload Ktor's bearer cache with an empty pair after the clear; the first call after re-pairing sends an empty bearer, forces a refresh, and a failed refresh can end the brand-new session | read loadTokens (returns BearerTokens("","") with empty prefs) and sendWithoutRequest for the OAuth host; scenario holds | fix | loadTokens returns null when no access token is stored; re-pair test makes a device-code call before the new tokens are saved |
| 2 | edge F1 | minor | `ui/feature/root/vm/LauncherVM.kt:34-58` | Launcher reads the token outside sessionLock and checks hasPendingEnd only in the unauthenticated branch; it can open Main over the handler's Auth | read onStart/openStartScreen; emit precedes clear, Launcher read is unlocked; window real | fix | return when hasPendingEnd before either branch (or read through the locked isAuthenticated); LauncherVM test with token present and end pending |
| 3 | edge F2 | minor | `core/session/SessionEventBus.kt`, `LauncherVM.kt:55-59` | The blocked reason is in memory only; after process death Auth opens without the blocked line (seen on the box in qa round 1), inviting a re-pair of the banned account | qa round-1 evidence "second launch ... without the blocked line"; bus is a MutableStateFlow only | fix | persist the last end reason in preferences when the session ends, clear it on pairing success; Launcher passes it to auth(reason) when no token is stored |
| 4 | state-path F1 | minor | `ui/feature/root/component/App.kt:94-110`, `SessionEventBus.kt` | Replay of the pending end re-runs every clear on each new handler collection; clearSession() wipes a deep-link target accepted after the end | read handler: clears run on every delivery; MainActivity accepts the intent before setContent; trace holds | fix | run the clears once per end (handled mark on the pending end); a replay only re-roots to auth(reason); test: handled end + accept(uri) keeps the target |
| 5 | blind F2 | minor | `ui/feature/home/vm/HomeVM.kt:436`, `ui/feature/device/settings/vm/DeviceSettingsVM.kt:204` | Mirror action hidden (and Settings says "session expired") for any 401/403, including unlisted 403s that keep the session (e.g. a mirror's deny page) | httpFailure maps every 401/403 to Unauthorized; UI keys on the type; unlisted 403 keeps the session by AC-3 | fix | UI decides on the session-ending condition (sessionEndReasonFor != null), carried on the error (e.g. a reason field on ApiError.Unauthorized); unlisted 403 keeps the mirror action and the generic message |
| 6 | tests F2 | minor | `data/api/KinoPubApiClient.kt:268-273` | Nothing pins "emit before clear", which the cold-start routing depends on | read onSessionExpired and tests: only final state asserted | fix | FakePrefs.clearAccessToken records bus.hasPendingEnd; assert true |
| 7 | tests F1 | minor | `data/api/KinoPubApiClient.kt:163-167`, AC-4 row | AC-4 row claims the test discriminates reAuthorizeOnResponse; the record says it is green on base | Implementation record line confirms green on base | fix | comment that reAuthorizeOnResponse is defensive; test-designer corrects the AC-4 row |
| 8 | ux F1 | minor | `ui/feature/auth/component/AuthComponent.kt:132-154` | Code state with the blocked line has no preview and was never seen | preview calls CodeInfo without accountBlocked | fix | add CodeInfoBlockedPreview (tv_1080p) for the owner's AC-10 check |
| 9 | codex C2 | minor | `data/api/KinoPubApiClient.kt:206-243` | Refresh reads/writes tokens outside sessionLock; a refresh racing an end can restore or clear the wrong session | read refreshBearerTokens; real but needs a 401 and a 403 user_banned in one burst, no evidence the banned server answers 401 | defer | new run session-refresh-lock-race (idea) |
| 10 | ux F2 | nit | `ui/feature/home/component/HomeScreenContent.kt:130-167` | Home error view with Retry only has no compose focus test | Settings has DeviceSettingsContentTest, Home has none | defer | new run home-error-retry-focus-test (idea) |

## Rejected codex claims

- A 401 carrying user_banned should show the blocked line - the spec maps 401 to Expired and reserves Blocked for the allow-listed 403 (AC-1/AC-2); the box evidence shows 403.

## Next

- Fix round: implementer fixes 1-8; then lens edge, codex, antigravity, state-path on the incremental diff, lens tests (tests change), ux-designer (preview), qa on AC-5/6/7/8 (runtime code changes). Unproven lines: 12 (blind 4, edge 4, tests 2, ux 0, state-path 0, codex 0 + 2 overlapping).
