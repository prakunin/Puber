LENS: state-path  ROUND: 6  BASE: e4c3b5aa

### F1 [minor] The repository built from the Koin module in a JVM test calls the Android `SystemClock` stub on its first emission, and its index collector dies
- Where: app/src/main/java/com/kino/puber/data/repository/WatchStateRepository.kt:50 and :191; app/src/test/kotlin/com/kino/puber/data/di/WatchStateRepositoryModuleTest.kt:49
- Problem: the module passes no windowClock; `onIndexChanged(emptyMap())` reaches `windowClock()` -> "not mocked" on the JVM; the collector dies under the SupervisorJob; the test passes with a dead collector. Production unaffected. The plan's note "the unit tests never touch the default" is wrong for this test.
- Trace: init -> snapshot.collect -> onIndexChanged(emptyMap()) -> no mark, no catch-up window -> windowClock() -> throws.
- Fix (smallest): check `catchUpEmissionDeadline != NO_DEADLINE && windowClock() <= catchUpEmissionDeadline` (clock read only after a catch-up close); or override the binding in the module test.

Semantics: armed and compared on windowClock; NO_DEADLINE = Long.MIN_VALUE never satisfied; write order unchanged from c4519fa4.
