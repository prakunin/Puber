LENS: tests  ROUND: 4 (bounded re-check of 4ece367c..9e0dad08)  BASE: 39903d31

### F1 [minor] Two older sink tests now record events that the new pre-origin filter throws away, so their nav path no longer runs
- Where: app/src/test/kotlin/com/kino/puber/core/telemetry/TelemetrySinkTest.kt:99-107 (`encodingAndFileIoRunOnlyOnWriterThread`) and :123-125 (`recordReturnsWhileStoreIsBlocked`); `nav()` helper at :21 uses `t = 1`.
- Problem: these tests still use `FakeTelemetryClock()` (elapsed 1000, origin 1000) and record `nav()` with `t = 1`; since 1ea95f0f `drainQueue` drops them. Both pass only on session/meta lines, so writer-thread encoding and store blocking are no longer checked for a producer event. Weakened by this diff; suite still green.
- Failure scenario: a change that encodes or writes a recorded event off the writer thread, or blocks it, passes both tests.
- Fix: build both sinks on `FakeTelemetryClock(elapsed = 0)` (or `nav().copy(t = clock.elapsed)`) and assert a `nav` line is written in the first test.

Rows 1-3 checked: row 3 test now depends only on the queue clear; row 2 test depends only on the drainQueue check; row 1 tests fail if the marker is removed from SectionState.Content; `sectionLoadingEmptyAndErrorAreNotMarked` guards Empty. Fixture change weakened no other assertion. Targeted suite BUILD SUCCESSFUL; tree clean.
