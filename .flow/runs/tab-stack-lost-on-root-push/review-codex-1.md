CODEX: ran: review, adversarial (model gpt-6-astra, no fallback needed)

CONFIRMED:
- [minor] app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllScreenContent.kt:86-96 - `contentFocusActive` is read once and the `LaunchedEffect(Unit)` retry loop (`RESTORE_FOCUS_ATTEMPTS` frames) never re-checks it. - Scenario: the user presses Back from Details and the grid re-enters with `restoreId != null`. The card is not yet laid out, so the first `requestFocus` fails and the loop retries. The user presses D-pad Left in that window of roughly 3 frames (about 50 ms) and the drawer opens. The next retry then calls `requestFocus` and pulls focus from the rail back into the grid. The window exists only while retries are failing. A successful first request returns at once. Fix: before each `requestFocus`, read the current active flag, for example through `rememberUpdatedState` or `snapshotFlow`. A controlled-clock test is optional, so I did not rate this higher. Codex graded it medium. I regraded it to minor because the window is tiny and the failure is self-correcting, since the user can press Left again.

REJECTED:
- Plain `review`: it reported no actionable regressions, so there is nothing to reject.
- Codex's severity "medium" for the rail race - the path is real, but it needs a Left press within a few frames of re-entry and only while the first focus attempt fails. I regraded it to minor, as above.
- Codex's recommendation to key the effect on `contentFocusActive` - this would not work as written. Re-keying would relaunch the restore whenever the rail closed, which could steal focus unexpectedly. The check belongs inside the loop instead.

Codex raised no finding on the stack-retention change, the telemetry flip, or the leak and logout path.

VERDICT: changes needed (one minor finding, optional to fix; otherwise clean)
