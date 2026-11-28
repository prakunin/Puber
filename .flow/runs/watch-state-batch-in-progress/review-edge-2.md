LENS: edge  ROUND: 2  BASE: 1beafa9c

No findings.

### Unproven
- app/src/androidTest/kotlin/com/kino/puber/data/db/WatchStateDaoTest.kt:211: the control case `markInProgressInSeparateCommitsEmitsAPartialList` waits `PAUSE_MILLIS = 200L` between commits. It only works if Room's invalidation tracker refreshes and re-queries `observeAll` within those 200 ms. If it is slower, the writes merge into one emission and the test goes red, which would look as if the control failed. This becomes a real finding if a box run of `make DEVICE=192.168.1.110:5555 GRADLE=./tools/agentw itest TESTS=com.kino.puber.data.db.WatchStateDaoTest` shows the control case red, or green only some of the time. The plan's fix-round note already says what to do if it is red: change the AC-4 test's KDoc to call it a nesting smoke test.
