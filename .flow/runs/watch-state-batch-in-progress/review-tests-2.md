LENS: tests  ROUND: 2  BASE: 1beafa9c

No findings.

Notes on the incremental diff (1beafa9c..3318a62e):
- **Does it fix round-1 F1:** yes. `markInProgressInSeparateCommitsEmitsAPartialList` makes the same three `markInProgress` writes as separate commits, with a 200 ms real-time pause between them. It asserts that the emissions include a non-empty proper subset of `{31, 32, 33}`.
  - The pause is real time, not virtual. The writes run inside `withContext(Dispatchers.Default)`, so `delay` there does not use the test scheduler, and Room's InvalidationTracker gets time to re-query between commits.
  - So the control case should see partial lists. If it does, the AC-4 test's `none { … }` assertion is shown to tell an outer transaction apart from separate commits.
- **AC-4 test after the refactor:** it lost nothing. The new `collectWhile` helper is the old inline body moved as is: same collector, same `first.await()` gate, same `SETTLE_MILLIS`. Both assertions are unchanged (`none` partial, `last() == ids`).
- **Test isolation:** each test builds a fresh in-memory database in `@Before`. The control case also uses its own ids (31–33), so it cannot pick up rows from the AC-4 test.
- **Suite state:** I did not run anything in this round. The diff touches only an instrumented test (`app/src/androidTest/kotlin/com/kino/puber/data/db/WatchStateDaoTest.kt`) and two doc files. Running it needs the box lease, and `.flow/runs/watch-state-batch-in-progress/state.md` lists the qa-device re-run of `WatchStateDaoTest` as queued.
  - Until that run is recorded, nothing shows the control case is green on the box. The AC-4 claim depends on it.
  - The plan's Fix round 1 entry (`docs/superpowers/plans/2026-11-28-watch-state-batch-in-progress.md:99`) covers a red result: AC-4 would then count only as a nesting smoke test, and its KDoc would be reworded. The conductor should check the qa result against that entry before gate 2.
- **Spec change:** the new Decisions row only records the worktree `itest` command. It changes no test.
