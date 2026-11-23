LENS: edge  ROUND: 1  BASE: e4c3b5aa
### F1 [major] A catch-up that runs inside an open walk window loses its signal: Room publishes after the inner window has already closed
- Where: app/src/main/java/com/kino/puber/data/repository/WatchStateRepository.kt:195 (`endSyncWindow`), app/src/main/java/com/kino/puber/domain/interactor/watchstate/WatchStateSyncInteractor.kt:239 (`runCatchUp` finally)
- Problem: `syncChangeHeld` is set only by `onIndexChanged`, driven by Room's asynchronous `observeAll()`. `runCatchUp` calls `endSyncWindow()` right after its last write; when the window closes `syncChangeHeld` is usually still false. The later emission, with the walk's window open, is held until the walk ends. The unit test passes only because the fake delivers before `endSyncWindow`.
- Failure scenario: a reconcile or resumed first walk is parked; the user resumes or enters Series; the catch-up records X finished elsewhere; Room emits ~10-100 ms after `endSyncWindow`; X stays in the hide-watched grid until the walk ends. Breaks AC-9.
- Fix: release on emission, e.g. nested `endSyncWindow` sets a `releaseNextChange` flag that `onIndexChanged` honours even with a window open; or wait for the snapshot before `endSyncWindow`. Add a test with late emission.

### F2 [minor] A series whose Details refresh fails is never asked about again in the same session
- Where: WatchStateSyncInteractor.kt:281
- Problem/Failure: `detailsCheckedIds += state.itemId` before `itemDetails.refresh`; a timeout leaves X skipped until the process dies.
- Fix: `runCatchingCancellable { itemDetails.refresh(id) }.onSuccess { detailsCheckedIds += id }.onFailure { log(...) }`.

### Unproven
- :250 `readHistoryNewerThan` has no page cap, pause or foreground check; matters for a mark far behind.
- :190 if `runCatchUp` throws (SQLite error), an unfloored rerun note stays set until the next trigger.
- :257 strict `> mark` misses an entry written in the same second as the mark.
- :268 `refreshSeriesThatLeftTheList` reads `snapshot.value` right after its writes; may cost one extra Details request.
