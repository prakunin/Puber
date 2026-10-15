LENS: tests  ROUND: 3 (re-review of round-2 fixes, incremental 0f20e63a..bbe17d3e)  BASE: 39903d31

### F1 [minor] The auto-off queue test still passes when the queue clear in `expire()` is removed
- Where: app/src/test/kotlin/com/kino/puber/core/telemetry/TelemetryExportTest.kt:85-102 (`eventsLeftInTheQueueAtAutoOffNeverReachTheNextSession`); app/src/main/java/com/kino/puber/core/telemetry/TelemetrySink.kt:215 (queue clear in `expire()`)
- Problem: The late `nav` is stamped t = 7 days, then the clock moves 1000 ms before restart, so `dropEventsOlderThanOrigin()` removes it whether or not `expire()` cleared the queue. Row 4's queue clear has no test of its own.
- Failure scenario: delete the queue-clear line; every test stays green; `queueSize` could count toward QUEUE_BOUND while off, or a later origin-filter change regresses silently.
- Fix: assert the queue is empty right after auto-off (test-visible size, or export and check no `nav`), or restart without moving the clock.

All other new or changed tests fail when their fix is reverted (rows 1, 2, 3, 4 origin filter, 5, 6). Suite: targeted core.telemetry.* and PuberVMTelemetryTest BUILD SUCCESSFUL; tree clean.
