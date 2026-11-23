LENS: state-path  ROUND: 5  BASE: e4c3b5aa

### F1 [minor] The catch-up deadline uses the wall clock, so a clock change brings back the round-4 bugs
- Where: app/src/main/java/com/kino/puber/data/repository/WatchStateRepository.kt:48, :188, :250
- Problem: `catchUpEmissionDeadline` uses `clock` (System::currentTimeMillis), the same clock as `observedAt`; the wall clock can jump (NTP after boot). This diff introduced the time dependency.
- Failure scenario (backward jump): no-change catch-up closes at T (deadline T+1000); clock steps back minutes; a walk's first changing chunk is published mid-walk (round-4 item 1 again, unbounded).
- Failure scenario (forward jump): catch-up with a held change closes inside a walk; clock steps forward > 1 s before Room delivers its last write; the emission is held until the walk ends (round-4 tests F1 again).
- Fix: a separate monotonic clock for the window (e.g. `windowClock: () -> Long = SystemClock::elapsedRealtime`), test-drivable; keep `clock` for observedAt/updatedAt.

Checked and holding: held-change catch-up followed by a walk (+2); no-change catch-up without a walk; walk ending inside the deadline; mark inside the deadline; concurrent catch-ups impossible (catchUpMutex).
