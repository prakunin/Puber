LENS: tests  ROUND: 1  BASE: e4c3b5aa
### F1 [major] The AC-2 tests pass even if the catch-up waits for the parked walk
- Where: app/src/test/kotlin/com/kino/puber/domain/interactor/watchstate/WatchStateSyncInteractorTest.kt (`catchUp_readsFromPageOneWhileAWalkIsParkedBetweenChunks`, `aWalkResumedAfterACatchUpStillWalksFromItsBookmark`)
- Problem: The Test design says this row must fail if the catch-up takes the walk's `mutex`. It only checks that `paced.catchUp()` returns true and that page 1 was read twice. Inside `runTest`, a test body that suspends lets virtual time advance on its own, so the walk's `delay(pauseBetweenChunks)` in `holdBeforeNextChunk` is skipped. The catch-up only checks the mutex with a non-blocking `tryLock` and never blocks on the walk's lock, but nothing in the test pins that down.
- Failure scenario: Change `catchUp` to call `mutex.lock()` (the walk's lock) or to start with `syncMutex.withLock {}`. The test body suspends, `runTest` skips the 20 s pause, and the walk reads pages 16-20 and releases the lock. The catch-up then reads page 1, so the count is 2 and the test passes. That is exactly the R1 defect AC-2 exists to catch ("without waiting for the walk to finish"). The sibling test passes the same way, because page 16 is still read exactly once.
- Fix: In `catchUp_readsFromPageOneWhileAWalkIsParkedBetweenChunks`, record `testScheduler.currentTime` before the call and assert it is unchanged after. Also add `coVerify(exactly = 0) { api.getHistoryData(16, 50) }` right after `catchUp()` returns.

### F2 [major] Two P0 rows labelled `red` were green on the base and are not marked `guard`
- Where: docs/superpowers/specs/2026-11-23-watch-index-catch-up.md, Traceability rows for SI `catchUp_sendsNothingOverAnIndexWithNoMark` (AC-1, R6) and MV `onStart_doesNotCatchUpWhileTheStartupRequestsAreStillOut` (AC-5, R9); plan Implementation record
- Problem: Both are P0 `red` in the Test design, and the Implementation record says both passed on the unchanged or stubbed code. Neither is marked `guard`. Both are negative checks ("nothing called"), so a red run on the base can never show they catch anything. Neither was mutation-checked against the real implementation.
- Failure scenario: The no-mark guard (`historyNewestSeen == 0L -> return false`) or the slot check in `MainVM.syncWatchState` could be written wrong, and the record would still show green-on-base as the only evidence.
- Fix: Mark both rows `P0 guard` in the spec's Test design. Record one mutation run each in the Implementation record: remove the no-mark return, and move the catch-up before `awaitSlot`. Each run should go red.

### F3 [minor] The Task 4 red runs prove only that a stub returned false, not that each assertion catches its defect
- Where: plan Implementation record, Task 4 bullet
- Problem: Most of the 21 "red" results fail on the first `assertTrue(interactor.catchUp())`, not on the assertion that names the defect. The tests themselves look sound by hand reasoning; the gap is in the evidence.
- Fix: For the P0 `red` SI rows on AC-3 and AC-7, record a mutation run against the real `catchUp` in the Implementation record.

Suite state (round 1): check.full at HEAD c7df62a8 exited 0 (unit and detekt UP-TO-DATE from an earlier run at the same HEAD; 253 XML results with no failure; lint ran).

Not yet covered: AC-8 (moved to verify), AC-9 (device), instrumented migration/DAO tests not yet run; `migrate4To5RescalesTheResumePageOfAnUnfinishedWalk` is P0 red with no red run recorded.

### Unproven
- WatchStateRepositoryTest.kt `recordCatchUpPage_rowsCarryTheCurrentGenerationAndSurviveAPrune` - with `DatabaseTransaction.Direct` a generation read outside the transaction cannot be told apart; only a defaulted generation is caught.
