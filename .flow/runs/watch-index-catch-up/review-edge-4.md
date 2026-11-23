LENS: edge  ROUND: 4  BASE: e4c3b5aa

Round-3 fix holds (late emission owned when a walk opens right after; cold-start case covered; the new interactor tests fail on swapped kinds; enum move cosmetic).

### F1 [minor] After a catch-up that found nothing, the pending flag stays set until some later walk, which then announces its first chunk early
- Where: app/src/main/java/com/kino/puber/data/repository/WatchStateRepository.kt:243 (with :176, :181-184, :207-213)
- Problem: `catchUpEmissionPending = !catchUpChangeHeld || openWalkWindows > 0` sets the flag after every no-change catch-up; a StateFlow snapshot emits nothing for unchanged rows, `beginSyncWindow(WALK)` and the mark branch leave it set, so it lasts until the next non-mark emission or a walk end.
- Failure scenario: a no-change catch-up on resume sets the flag; hours later a walk starts; its first chunk emission goes out on its own; open screens re-page once mid-walk. One extra refresh per walk, not wrong data.
- Fix: store a deadline (close + SETTLE_DELAY or a few hundred ms) instead of a boolean; clear it in the `aMarkLanded` branch.

### Unproven
- :243 a late emission of a catch-up's last write, closing with something held just before a cold-start walk, held for the walk; visible only if Room lags the close by more than SETTLE_DELAY.
