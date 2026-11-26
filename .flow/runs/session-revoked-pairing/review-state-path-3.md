I found one issue in the round 3 code change (831d6ccf), and it needs a narrow timing to happen. Everything else in that change holds up.

```
LENS: state-path  ROUND: 3  BASE: b53bf631
### F1 [minor] A cancelled cleanup gives its claim back only after its clear returns, so a replay that starts first skips the clears for good
- Where: /home/dev/orca/workspaces/Puber/session-revoked-pairing/app/src/main/java/com/kino/puber/core/session/SessionEndCleanup.kt:18 and :36; /home/dev/orca/workspaces/Puber/session-revoked-pairing/app/src/main/java/com/kino/puber/core/session/SessionEventBus.kt:38-44
- Problem: The claim stays taken while its holder is cancelled but has not yet reached `finally`. That holds when the clear running at that moment takes time to return, for example `ContentCacheRepository.clear()` -> `store.clear()` doing storage work. A replay that calls `claimPendingEnd()` in that window gets `false` and returns at once. It goes on to `router.newRootScreen(auth)` without clearing anything. The holder's `releasePendingEnd()` then frees the claim, but nothing runs `run()` again. The next chance is a new emission or the next activity recreation. After a pairing succeeds, `AuthVM` calls `clearPendingEnd()`, so the release is lost. The clears that had not run yet (`contentCache.clear` and/or `detailsPrefetcher.invalidate`) never run for this end. That is the case 831d6ccf set out to close; its KDoc says "an ended account's caches cannot survive to the next account".
- Failure scenario: A session ends. `SessionExpiredHandler` A claims the end and is inside `contentCache.clear()`. The activity is recreated (a config change on a `singleTask` activity disposes the old composition), which cancels A, but A's clear has not returned. The new composition's handler B collects the replayed end, `claimPendingEnd()` returns false, and B routes to Auth with no clears. A's clear returns, A skips `detailsPrefetcher.invalidate()` because it is cancelled, and A releases the claim. The user pairs, `clearPendingEnd()` runs, and the next account starts with `DetailsPrefetcher` still holding the previous account's state, and possibly a partly cleared cache.
- Fix: Do not let a replay's success depend on a cancelled holder having released first. Two options:
  - Keep a `done` flag on `Pending`, set only once all clears finish. `run()` clears whenever `done` is false. Overlapping runs are acceptable because every clear can safely run twice. Keep `claimLinkReset` as the once-per-end guard.
  - Put a `Mutex` in `SessionEventBus` (a singleton, unlike the `remember`ed cleanup) around the claim and the clears, so B waits for A's `finally` before it tries to claim.
- Trace:
  1. A: `claimPendingEnd()` sets `claimed` to true.
  2. A: `claimLinkReset()` succeeds and `clearSession()` runs.
  3. A: `clearAccountPrograms()` and `invalidate()` run.
  4. A: `contentCache.clear()` suspends.
  5. A's composition is disposed and A is cancelled; the clear has not returned.
  6. B: `events` replays the same `Pending`; `claimPendingEnd()` returns false, so `run()` returns.
  7. B: `newRootScreen(auth)`.
  8. A: the clear returns, and the next `clear()` call (`detailsPrefetcher.invalidate()`) throws `CancellationException`.
  9. A: `finally` calls `releasePendingEnd()`, setting `claimed` to false, and no handler runs again.
  10. Auth succeeds and `clearPendingEnd()` drops the release.

  The new test `aHandlerCancelledMidClearLetsTheReplayClearAgain` does not cover this. Its clear waits on a `CompletableDeferred`, which returns the moment it is cancelled, so the release always comes before the replay.
```

What I checked and found safe in this round:
- **Deep-link reset:** `claimLinkReset` happens once per end, and nothing suspends between the claim and `resetDeepLink()`. A replay therefore cannot wipe a link accepted since.
- **Routing:** it runs unconditionally after `cleanup.run()`, so a skipped cleanup never leaves the user off the Auth screen.
- **A second end during a clear:** `collect` handles events in order, so the second end is claimed and cleared after the first.
- **`releasePendingEnd` landing on a newer end:** it reads `pending.value`, so it may set a newer end's flag to false. That flag is false at that point anyway, so nothing changes.
- **The `AuthComponent` change:** it only touches a preview and has no effect on state.
