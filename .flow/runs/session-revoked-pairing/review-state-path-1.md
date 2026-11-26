LENS: state-path  ROUND: 1  BASE: b53bf631

### F1 [minor] A stored session end runs again whenever the session handler starts collecting, and wipes a deep-link target that arrived after the end
- Where: /home/dev/orca/workspaces/Puber/session-revoked-pairing/app/src/main/java/com/kino/puber/core/session/SessionEventBus.kt:19-31, /home/dev/orca/workspaces/Puber/session-revoked-pairing/app/src/main/java/com/kino/puber/ui/feature/root/component/App.kt:94-109
- Problem: The bus is now a `MutableStateFlow` that keeps the last end until `AuthVM` sees `AuthState.Success`. Any new collection gets that end again. This includes `SessionExpiredHandler`'s `LaunchedEffect(Unit)` on every new `App()` composition, i.e. every warm relaunch or activity recreation. Each time, the handler runs every clear again, including `contentLaunchCoordinator.clearSession()`, and then `router.newRootScreen(auth(reason))`. Those clears were meant to run once, at the moment the session ended. Run again later, `clearSession()` deletes a `pendingTarget` that `MainActivity.onCreate` accepted after the end (`MainActivity.kt:74` calls `acceptContentIntent` before `setContent`). On base the event was not replayed, so a logged-out deep link survived until pairing through `waitForAuthentication` / `consumeAfterAuthentication`.
- Failure scenario: The session ends with Blocked or Expired, and the app shows Auth. The user presses Back and the activity finishes, but the process stays alive with the end still pending. The user then opens a content deep link, for example from search or a channel row. `onCreate` calls `accept(uri)`, so `pendingTarget = target`. `setContent` composes the handler, which receives the old end and calls `clearSession()`, so `pendingTarget = null`. Then `newRootScreen(auth(reason))` runs. The user pairs, `consumeAfterAuthentication()` returns null, and the app opens Main instead of the content the link asked for. In the same way, an activity recreation while Auth shows a code replaces the restored Auth with a new root.
- Trace: `onSessionExpired` -> `pending = Pending(Unauthorized(r))` (stays) -> activity finishes, process alive -> `onCreate`: `coordinator.accept(uri)` sets `pendingTarget` -> `LauncherVM.openStartScreen(false)`: `waitForAuthentication()`, `hasPendingEnd` is true so it returns -> handler collect gets the stored end again -> `clearSession()` sets `pendingTarget = null` (RESETS the deep link) -> `newRootScreen(auth(r))` -> pairing -> `AuthVM` Success: `clearPendingEnd()`, `consumeAfterAuthentication()` returns null -> `newRootScreen(main())`.
- Fix: Keep the stored end only for routing, and run the clears once per end. Two ways:
  - Have the handler remember the last `Pending` it handled, for example by comparing identity in a retained holder or marking the `Pending` as consumed, and on a repeat delivery only re-root.
  - Or have `SessionEventBus` hold a "handled" flag. The handler sets it after the clears. While the flag is set, the handler only does `newRootScreen(auth(reason))` and skips `contentLaunchCoordinator.clearSession()` and the cache clears.

  Add a `LauncherVM`/handler test: a pending end that was already handled, then `accept(uri)`, then pairing; the target must open.

No other sequence survived checking. Each was ruled out as follows:
- **Burst of parallel refusals:** the access-token comparison and the clear happen together under `sessionLock`, so only the first refusal ends the session.
- **Stale refusal after a new pairing:** the bearer the request carried no longer matches the stored token, so it is ignored.
- **Refresh with no stored tokens:** this path now emits nothing.
- **Cold-start ordering:** `onSessionExpired` emits before it clears the tokens, so a `LauncherVM` that finds no token also finds `hasPendingEnd`.
- **Launcher ordering:** a Launcher-to-Main re-root overwriting the handler's Auth re-root would need the handler's suspending clears to finish inside one main-thread dispatch, which is not a realistic window.
- **`HomeVM` refusal counting:** `unauthorizedFailures` is only changed on the VM scope, and `CachedFeed` rethrows the raw `ApiError.Unauthorized`.
- **Focus on Home and Settings errors:** Retry keeps its launch `FocusRequester`, and removing the mirror button leaves no requester pointing at a node that is gone.
- **Auth blocked line:** it is a plain `Text` and cannot take focus.
