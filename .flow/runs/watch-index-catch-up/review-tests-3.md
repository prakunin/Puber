LENS: tests  ROUND: 3  BASE: e4c3b5aa

### F1 [minor] Nothing tests which window kind the interactor opens, so swapping WALK and CATCH_UP still leaves every test green
- Where: app/src/main/java/com/kino/puber/domain/interactor/watchstate/WatchStateSyncInteractor.kt:228, :244, :326, :330
- Problem: `WatchStateSyncInteractorTest` uses a relaxed mock repository and never verifies `beginSyncWindow`/`endSyncWindow` kinds; `WatchStateRepositoryTest` calls the repository directly.
- Failure scenario: kinds swapped or WALK on both paths re-creates the AC-9 regression of triage-2 item 1 with all 47 + 76 tests green.
- Fix: `verifyOrder { beginSyncWindow(CATCH_UP) … endSyncWindow(CATCH_UP) }` in a catch-up test, and the same with WALK in a walk test.

### F2 [nit] The new enum sits between `WatchStateSyncCursor` and its KDoc
- Where: WatchStateRepository.kt:592-601
- Fix: move the enum above the cursor's KDoc.

Fix holds: both new tests would be red on the old depth-based code; round-1 window tests only switched to the kind API. Suite: WatchStateRepositoryTest 47, WatchStateSyncInteractorTest 76, 0 failed.

### Unproven
- WatchStateRepository.kt:229-236: no test for a walk closing while a catch-up is still open.
