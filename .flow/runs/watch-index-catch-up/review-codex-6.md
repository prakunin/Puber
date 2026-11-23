CODEX: ran: review, adversarial
CONFIRMED:
- [major] app/src/main/java/com/kino/puber/data/repository/WatchStateRepository.kt:191 - `onIndexChanged` calls `windowClock()` whenever no mark has landed and no catch-up window is open, including the first snapshot emission with `catchUpEmissionDeadline == NO_DEADLINE`. `WatchStateRepositoryModuleTest` (`repositoryFromTheModuleDefersCleanupUntilStartupSettles`) builds the repository through `repositoryModule` with the default `SystemClock::elapsedRealtime`; no `returnDefaultValues`, so the stub throws "not mocked" in the snapshot collector, which dies quietly under the SupervisorJob; the test's cleanup assertions still pass with a dead version observer. Plan claim "the unit tests never touch the default" is false. Not run; inferred. Fix: fake windowClock in the DI test or a JVM-safe default; assert a snapshot change advances `version`.
REJECTED:
- Plain review: no defects.
- Adversarial lifecycle/locking: no regression; `windowClock()` read under `windowLock` as `clock()` was.
VERDICT: changes needed
