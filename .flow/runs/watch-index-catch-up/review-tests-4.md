LENS: tests  ROUND: 4  BASE: e4c3b5aa

### F1 [minor] The round-3 fix only covers a catch-up that held nothing; a catch-up whose last write emits after its window closes still has that emission held by the walk that opens next
- Where: app/src/main/java/com/kino/puber/data/repository/WatchStateRepository.kt:243
- Problem: with `catchUpChangeHeld` true (an early write emitted inside the window) and no walk open at close, the pending flag stays false; the last write's in-flight emission is held by the walk that opens right after.
- Failure scenario: cold start, stale index; MainVM.kt:101-104 runs catchUp then syncIfStale; `recordFromServer` emits inside the window; close pays one tick; `recordInProgress`/series refresh emission lands after the walk opens and is held until the walk ends. No test covers this ordering.
- Fix: set `catchUpEmissionPending = true` whenever the last catch-up window closes; add a test with a first emission inside the window and a second after, expecting `beforeCatchUp + 2` while the walk is open.

Checked: WatchStateRepositoryTest and WatchStateSyncInteractorTest green at 9910c26e; red run recorded for the new repository test; interactor tests are guards with a kind-swap mutation run; enum move cosmetic.

### Unproven
- :243 no test shows the flag left by an empty catch-up lets at most one walk emission out early and is cleared at walk end.
