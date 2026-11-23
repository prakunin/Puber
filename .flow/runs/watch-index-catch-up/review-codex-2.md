CODEX: ran: review, adversarial (model gpt-6-astra)
CONFIRMED:
- [minor] app/src/main/java/com/kino/puber/domain/interactor/watchstate/WatchStateSyncInteractor.kt:458-462 (callers 233-241, 384-390) - `sessionSurvived` calls `repository.clear()` on a generation mismatch. The catch-up now runs under its own `catchUpMutex`, separate from the walk `mutex`. Scenario: logout calls `invalidate()` while a catch-up is mid-read; the user re-authenticates and MainVM starts a full walk that writes rows and a cursor; the old catch-up then reaches `sessionSurvived`, sees the old generation, and wipes the new account's rows and cursor. Narrow window (re-login inside one catch-up run); wiping on mismatch was already the design, this branch widened it. Suggested fix: on mismatch return false without clearing, or discard the old run's writes by generation.
- [minor] app/src/main/java/com/kino/puber/data/repository/WatchStateRepository.kt:219-222 and `endSyncWindow` - a nested catch-up window ends right after an earlier emission set `nestedChangeHeld`; that pays one tick and sets `ownChangePending = false`; if Room publishes the catch-up's last write after the close, `onIndexChanged` holds it as `syncChangeHeld` until the surrounding walk ends. A delay, not a loss.
REJECTED:
- AC-9 NOT CHECKED in device evidence (process gap, not a code defect).
- "Await the expected final catch-up state" before releasing the window - fix direction for the second minor, not a separate defect.
VERDICT: clean (two minors only; no blockers or majors; the triage-1 fixes hold)
