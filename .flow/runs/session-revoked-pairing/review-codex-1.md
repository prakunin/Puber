CODEX: ran: review, adversarial

CONFIRMED:
- [minor] app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:269 and :1121 - After `clearAuthTokens()`, the device-code and poll requests go to the OAuth host. `sendWithoutRequest` is true for that host (`shouldSendKinoPubToken`, :1245), so Ktor's `loadTokens` re-caches `BearerTokens("","")` from the now-empty prefs. `KinoPubRepository.kt:37` saves the new pair without invalidating that cache.
  - Scenario: session ends, the user pairs again, and the first content call sends an empty bearer.
  - Likely result: the server answers 401, the refresh uses the newly stored refresh token, and the call recovers. This is a wasted round trip, not a lockout. The base code kept the stale old token in the cache, so it is no regression, and the existing re-pair test skips the OAuth calls.
  - Fix: invalidate the Ktor cache when pairing saves its tokens, or make `loadTokens` return null when there is no access token.
- [minor] app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:206-243 - The refresh reads and writes tokens outside `sessionLock`.
  - Scenario: call A gets a 401 and starts a refresh. Call B gets 403 `user_banned` and clears the session. A's refresh then succeeds and saves tokens, so Auth finds a stored token and may skip pairing.
  - The mirror case is a refresh that fails after a new pairing, where `sessionExpiredFailure` clears the new tokens because it only checks that some token is stored.
  - A banned server probably also refuses the refresh, so the narrow ordering is unproven, but the unguarded write is real.
  - Fix: capture the session identity at refresh start and re-check it under `sessionLock` before saving or ending the session.

REJECTED:
- A 401 carrying `user_banned` bypasses the blocked line (medium) - The spec explicitly maps 401 to Expired and reserves Blocked for an allow-listed 403. The recorded box evidence shows 403. The 401-after-refresh path is AC-2, so classifying it earlier would contradict the spec.
- Home and Settings Retry focus on D-pad, recomposition cost, scope leaks (adversarial focus areas) - Codex raised no concrete finding on these, and the diff shows none.

VERDICT: changes needed (minor only; the conductor may accept both as follow-ups)
