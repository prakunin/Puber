LENS: edge  ROUND: 3  BASE: e4c3b5aa

### F1 [minor] The new enum is placed between `WatchStateSyncCursor` and its KDoc
- Where: app/src/main/java/com/kino/puber/data/repository/WatchStateRepository.kt:592-601
- Problem: `enum class SyncWindowKind` and its KDoc sit between the KDoc of `WatchStateSyncCursor` and the class; the cursor's KDoc is now dangling.
- Failure scenario: IDE quick-doc / Dokka on `WatchStateSyncCursor` shows nothing; the text sits above `SyncWindowKind`.
- Fix: move the enum above line 592 or below `WatchStateSyncCursor`.

Fix for triage-2 item 1 holds; traced every open/close order: catch-up before walk (paid at catch-up close, new test), catch-up inside walk (own change only), late last emission (`catchUpEmissionPending`, new test), walk closes while catch-up open (no loss), catch-up alone, empty catch-up (AC-7 holds), user marks unchanged, cancellation (finally), callers pass the right kind. Accepted cost: `catchUpEmissionPending` set inside a walk announces the next walk chunk early (design #8).
