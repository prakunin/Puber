LENS: edge  ROUND: 2  BASE: e4c3b5aa

### F1 [minor] When a walk opens inside a catch-up's window, the catch-up's rows can wait for the walk's first chunk
- Where: app/src/main/java/com/kino/puber/data/repository/WatchStateRepository.kt:215-230 (`endSyncWindow`), :174-185 (`onIndexChanged`)
- Problem: windows are counted, not identified; a close leaving `openSyncWindows > 0` is assumed to be the catch-up inside a walk. A walk can open inside a catch-up's window (HistoryVM `requestSync(maxIndexAge = ZERO)` during `refreshSeriesThatLeftTheList`); the catch-up's rows set `syncChangeHeld` (outer), its close pays only `nestedChangeHeld` (false) and sets `ownChangePending`, so its rows go out only with the walk's first emission or end.
- Failure scenario: tab-entry catch-up writes rows; user opens History, a walk starts; the catch-up closes silently; Movies/Series keep old state until the walk's first chunk (seconds to tens of seconds), or the walk's end if parked/failed. Not on cold start.
- Fix: give the catch-up a window flag/token of its own and pay out its held change whatever the open order; or record when the window opened and publish if `syncChangeHeld` was set after it.

Round-1 fixes hold: items 1-2 (late emission via `ownChangePending`; empty nested close no longer pays the walk), 3, 4-5, 7-9.

### Unproven
- :269 a history entry with null/non-numeric `updated` stops the read under `any { <= mark }` and raises the mark past newer entries.
- :298-302 a series whose details always fail holds one of 10 slots forever.
- :232 > 50,000 new entries: cap hit every run, mark never moves.
