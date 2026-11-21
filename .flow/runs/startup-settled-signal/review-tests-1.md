LENS: tests  ROUND: 1  BASE: 7bcbfd83
### F1 [major] P0 `red` test `restoredActivitySettlesAtOnce` was recorded green on the stub and is not marked `guard`
- Where: app/src/test/kotlin/com/kino/puber/core/startup/StartupSettledTest.kt (`restoredActivitySettlesAtOnce`); docs/superpowers/plans/2026-11-21-startup-settled-signal.md, Implementation record, "Red run"
- Problem: The test design lists AC-2 as `P0 red`. The Implementation record says it was "green on base" because the stub's `await()` returned at once. No red run is recorded for it. The test itself can tell right from wrong: if a restored activity armed the fallback instead of settling, `resumed` would stay false. But the record does not show that it ever failed.
- Failure scenario: AC-2's only P0 test has no proof that it can go red. A regression that makes restored starts wait has no recorded test catching it, which breaks the project's red-first rule for P0 rows.
- Fix: Run it red on a stub that never completes, or one where `restored = true` only arms the fallback, and record that run. Or reclassify the row as `guard` in the test design.

### F2 [minor] `aWarmHeldForStartupTakesNoSlot` cannot fail on the mutation it names
- Where: app/src/test/kotlin/com/kino/puber/domain/interactor/prefetch/DetailsPrefetcherTest.kt (`aWarmHeldForStartupTakesNoSlot`)
- Problem: The second `onFocused(second, 2, …)` cancels `scheduled`, and that job contains `warm(1)`. Suppose `awaitSettled()` were moved after `permits.acquire()`, inside the `try`. The cancelled `warm(1)` gives its permit back in `finally`, and `warm(2)` then gets the only slot either way. The `coEvery { warmItemDetails(1) } coAnswers { release.await() }` stub never runs.
- Failure scenario: Moving the gate after the permit (the regression the row says it catches) leaves the test green. The latch is process-wide, so "a held warm blocks one that is not held" cannot be seen through this setup.
- Fix: Drop the row, or pin the order structurally. For example, record call order from a fake `awaitSettled` against a `Semaphore` probe, or assert that no permit is held: with `maxConcurrent = 1`, a focused card plus a neighbour on the same surface, closed gate, check that the permit is still free.

### F3 [minor] `aSectionArrivingAfterAClearPublishesEvenWhenAStalePublishWasSkipped` relies on a real-time `delay(200)`
- Where: app/src/test/kotlin/com/kino/puber/ui/feature/home/vm/HomeVMIncrementalPublishTest.kt (`withContext(Dispatchers.Default) { delay(200) }` before `releaseMapping.countDown()`)
- Problem: The test only tells right from wrong if the new catalogue's arrival is counted before the stale publish is released. The comment says the 200 ms "gives it the moment it needs". If the count lands after the release, the test passes even with the mutant it targets (the epoch skip writing live `requestedPublishes`). So it can lose its value silently, though it never fails falsely. `.flow/roles/reviewer.md` also says "no sleeps".
- Failure scenario: On a loaded host the arrival is counted after the release, and a regression to `publishedThrough.value = requestedPublishes.value` on the epoch skip stays green.
- Fix: Replace the sleep with a condition, for example spin under a real-time `withTimeout` until `requestedPublishes` has grown past the parked publish's number (a `@VisibleForTesting` read like `awaitPublishesDrained`), then release.

Suite state: I ran check.full in the worktree at HEAD 5686efd0 and it exited 0 (`BUILD SUCCESSFUL`). Test, detekt and lint tasks were all UP-TO-DATE, so the tests were not executed again in my run. qa-report says a `--rerun` at aacf6035 ran 2236 tests with 0 failures; production code is the same at 5686efd0.

Coverage:
- Every P0 row in the test design has a test under its name.
- AC-11 has device evidence.
- AC-12 has the instrumented guard green on the device. Manual 2 was not run because no newer release exists; that is recorded in qa-report.
- No AC is left without a test or a documented check.
