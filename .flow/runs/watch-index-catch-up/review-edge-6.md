LENS: edge  ROUND: 6  BASE: e4c3b5aa
### F1 [minor] A JVM unit test calls the stubbed `SystemClock.elapsedRealtime` through the default `windowClock`, and its index collector dies without failing the test
- Where: app/src/main/java/com/kino/puber/data/repository/WatchStateRepository.kt:50 (default `windowClock = SystemClock::elapsedRealtime`) and :190; reached from app/src/test/kotlin/com/kino/puber/data/di/WatchStateRepositoryModuleTest.kt:49
- Problem: the module test gets the repository from the real `repositoryModule` (modules.kt:127 passes no windowClock); the snapshot's first emission reaches `windowClock()`, which throws "not mocked" on the JVM (no `isReturnDefaultValues`); the SupervisorJob keeps the test green but kills the version collector. The plan's note "the unit tests never touch the default" is wrong.
- Failure scenario: later assertions on `version` in module-level tests fail for unrelated reasons; default wiring has no JVM coverage.
- Fix: take the clock from Koin with an override in the test module, or set `unitTests.isReturnDefaultValues = true`; correct the plan's note.

Production fine: elapsedRealtime monotonic through deep sleep; NO_DEADLINE = Long.MIN_VALUE; no overflow.
