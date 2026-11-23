LENS: state-path  ROUND: 1  BASE: e4c3b5aa

### F1 [major] A catch-up that runs inside a walk's window closes its window before Room delivers its writes, so its change waits for the walk to end
- Where: app/src/main/java/com/kino/puber/data/repository/WatchStateRepository.kt:195-203 (`endSyncWindow`), with WatchStateSyncInteractor.kt (`runCatchUp`, the `finally { repository.endSyncWindow() }` right after `refreshSeriesThatLeftTheList`)
- Problem: Only `onIndexChanged` sets `syncChangeHeld`, and it runs off `snapshot` (Room `dao.observeAll()`), delivered after commit. `endSyncWindow` can only pay out a change already held when it closes. With a walk's window still open, the catch-up's update reaches `onIndexChanged` after the catch-up's window closed and is held until the walk ends. The red test `syncWindow_publishesACatchUpsChangeWhileAWalksWindowIsStillOpen` passes only because of `UnconfinedTestDispatcher` with a `MutableStateFlow` fake DAO.
- Failure scenario: "Hide watched" on, last complete sync > 1 h old; resume starts both a walk and a catch-up; the catch-up refreshes series X (finished), writes, closes its window; Room's update arrives with the walk's window open and is held; the Series grid keeps X until the walk ends minutes later. Breaks AC-9.
- Fix: close the catch-up's window only after the snapshot shows its writes (await a snapshot emission / compare to a target), or register expected rows like `expectUserMark` so the update goes out whatever window is open. Add a test using the fake's `emissionsFlowing` knob or a non-eager scheduler.
- Trace: syncIfStale → beginSyncWindow (1) → catchUp → beginSyncWindow (2) → refresh(X) → recordFromServer (update queued) → endSyncWindow (1; nothing held) → update arrives, open > 0 → held → walk ends → published.

### F2 [minor] A nested catch-up's close pays out the walk's held chunk changes partway through the walk
- Where: WatchStateRepository.kt:195-203
- Problem: `syncChangeHeld` is shared by all windows; closing the nested catch-up window publishes the walk's held changes even when the catch-up changed nothing, against AC-7 and the one-tick-per-walk purpose.
- Failure scenario: a walk flushed a changing chunk (held); Series tab entry triggers a catch-up with nothing new; its close publishes the walk's tick; with "Hide watched" on the grid re-pages under focus mid-walk, repeatable each minute and on every player exit.
- Fix: pay out only what the closing window earned (per-window held state); combine with F1.

### F3 [minor] A failed Details refresh stops that series from being checked again until the process dies
- Where: WatchStateSyncInteractor.kt `refreshSeriesThatLeftTheList` (`detailsCheckedIds += state.itemId` before `runCatchingCancellable { itemDetails.refresh(...) }`)
- Problem/Failure: a timeout on X's refresh leaves X marked checked; later catch-ups skip it; X stays visible under "Hide watched" until reconcile, Details open or restart.
- Fix: add the id only after `refresh` succeeds.
