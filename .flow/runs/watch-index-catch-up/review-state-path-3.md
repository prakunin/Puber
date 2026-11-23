LENS: state-path  ROUND: 3  BASE: e4c3b5aa
### F1 [minor] A catch-up that closes just before a walk opens has its late emission held for the whole walk
- Where: app/src/main/java/com/kino/puber/data/repository/WatchStateRepository.kt:243 (`catchUpEmissionPending = openWalkWindows > 0`); trigger at app/src/main/java/com/kino/puber/ui/feature/main/vm/MainVM.kt:101-104
- Problem: the catch-up owns its late emission only when a walk is already open at its close. With emissions still in flight at close and a walk opening right after (sequential order), they land in `walkChangeHeld` and wait for the walk's end. Not a regression from 6f9a07da (depth-based code behaved the same).
- Failure scenario: cold start, index older than `staleAfter`: MainVM awaits `catchUp` then `syncIfStale` in one coroutine; catch-up closes with nothing held and no walk open; `beginSyncWindow(WALK)` runs before Room emits; the emission is held; grids keep the other device's titles as unwatched until the walk ends.
- Fix: `catchUpEmissionPending = !catchUpChangeHeld || openWalkWindows > 0` (or `true`); test: CATCH_UP begin, emissionsFlowing=false, recordCatchUpPage, CATCH_UP end, WALK begin, emissionsFlowing=true, assert version +1 while the walk is open.
- Trace: begin(CATCH_UP) -> writes (emissions pending) -> end(CATCH_UP) [pending=false, held=false, no tick] -> begin(WALK) -> emission -> walkChangeHeld -> ... -> end(WALK) -> tick.

Checked and holds: walk inside catch-up ending first; catch-up closing inside a walk; user marks; same-kind windows ruled out by mutexes; mid-walk announcement on catch-up close (intended, floored).
