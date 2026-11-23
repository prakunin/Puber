LENS: state-path  ROUND: 2  BASE: e4c3b5aa

Round 2 fixes round-1 items 1-3 for the usual order (walk outer, catch-up nested). Page cap, non-advancing stop, `any` stop rule and the details retry bring back no state-path bug. One new minor; no blockers or majors.

### F1 [minor] A walk that opens while a catch-up's window is open now holds the catch-up's change until the walk ends
- Where: app/src/main/java/com/kino/puber/data/repository/WatchStateRepository.kt:183 and :215-229 (`endSyncWindow`, `openSyncWindows > 0` branch)
- Problem: ownership is decided by nesting depth, not by window. With the catch-up outer and a walk inner, the catch-up's emissions go into `syncChangeHeld`; its close (2 -> 1) pays only `nestedChangeHeld` and sets `ownChangePending`; its change waits for the walk's next changing emission or the walk's end. Regression versus round 1 for this ordering.
- Failure scenario: Series tab entry -> catch-up writes, then up to 10 refreshes with the window open -> user opens History -> walk opens (count 2) -> catch-up closes silently -> walk chunks change nothing -> Series tab shows the series unfinished until the walk ends.
- Fix: per-kind held flags (catch-up flavour or token on `beginSyncWindow`); the catch-up's close publishes on its own flag plus `ownChangePending` whatever the open order. Add a repository test opening the catch-up window first.

Checked and dropped: late last emission after a nested close (covered by the 2 s debounce); `ownChangePending` left set after an empty nested close (documented); mark at cap/non-advancing (never lowered); `detailsCheckedIds` updates under `catchUpMutex`.
