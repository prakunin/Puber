LENS: blind  ROUND: 1  BASE: 6b087697

### F1 [minor] A handoff is marked answered before focus is restored, so an interrupted attempt is never retried
- Where: app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllScreenContent.kt, `GridFocusMemory.onHandoff` (about line 235)
- Problem: `answeredHandoffId = requestId` is set before `requestRestoreFocus()` runs, and the boolean that call returns is ignored. `requestRestoreFocus()` returns false when `contentFocusActive()` turns false during its frame waits, or when all `RESTORE_FOCUS_ATTEMPTS` fail. The `LaunchedEffect` is keyed on `contentFocusActive`, so it relaunches when content becomes active again. The latched id is then skipped as "already answered".
- Failure scenario: The rail closes and the handoff id N is latched. Content is active, so `onHandoff(N)` starts waiting for frames. Content flips inactive and then active again (for example a dialog or overlay opens and closes). The first attempt returns false and N stays marked answered. The relaunched effect returns early, so focus is not restored and the user lands wherever the system puts it. This is the shift the change is meant to fix, and it happens only on a rare race.
- Fix: Set `answeredHandoffId = requestId` only when `requestRestoreFocus()` returns true. Also set it on the early-exit path for `inGrid` or no remembered card. Otherwise leave it unset so the relaunch retries.

### Unproven
- ShowAllScreenContent.kt:172 `pendingRequestId` latch never resets - it would matter only if handoff ids can repeat or restart (for example after the handoff holder is recreated). I found no evidence of that in `ContentFocusHandoff.kt`.
- ShowAllScreenContent.kt:170 If the screen is composed while a handoff is already pending, both `reconcile()` (pending entry) and `onHandoff` request restore focus. The two requests target the same card, so the effect is probably harmless.
