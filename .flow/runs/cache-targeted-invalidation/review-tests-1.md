LENS: tests  ROUND: 1  BASE: 40762767

### F1 [minor] AC-6 is a P0 row, but neither a test nor a device check has confirmed it
- Where: /mnt/data/projects/PetProjets/Puber/.flow/runs/cache-targeted-invalidation/qa-report.md (AC-6 row); spec Test design, Manual checks M1/M2
- Problem: AC-6 has no unit test. Its only check, the manual check M1/M2, is recorded as `NOT CHECKED`. QA saw the mechanism work (one mark at exit, then a details revalidation), but nobody saw the outcome the AC promises: Details showing the saved progress. The unit tests cover only the mark at exit (`backPressed_afterSuccessfulSaves_marksAndRefreshesOnceBeforeBack`). Existing `ContentCacheRepository` tests cover the stale-to-revalidate step.
- Failure scenario: if the exit mark is issued but Details opened from Home still reads the old record (for example, a memory-tier hit wins before the revalidation lands), Details shows the progress from before the session. No test fails and no check catches it.
- Fix: keep AC-6 marked unverified at gate 2, with the documented manual step (an account whose payload carries per-episode progress, or after `resume-progress-anomaly`). Or add one `DetailsVMCachedLoadTest` case: after `markItemDetailsStale`, a Details open revalidates and emits the new progress.

### F2 [minor] Two P0 tests the spec calls red are relabelled guard only in the Implementation record
- Where: /home/user1/orca/workspaces/Puber/cache-targeted-invalidation/docs/superpowers/specs/2026-11-22-cache-targeted-invalidation.md (Traceability: `backPressed_withoutSuccessfulSave_marksAndRefreshesNothing` is P0 red); /home/user1/orca/workspaces/Puber/cache-targeted-invalidation/docs/superpowers/plans/2026-11-22-cache-targeted-invalidation.md (Implementation record, "green on base")
- Problem: the Implementation record says this P0 test passed on the unchanged code and calls it a guard, but the spec's Traceability still says `P0 red`. The test can still fail (setting `detailsStaleOwed` before the save returns would break `verifyMarks(0)`). Its second half cannot fail: a VM that was never started, given one `OnBackPressed`, then checked for 0 marks and 0 refreshes, passes whether the code is right or wrong.
- Failure scenario: the gate reads the spec's Traceability and counts a red-first proof that was never run.
- Fix: change the Traceability row to `P0 guard` and name the mutation it catches (owed flag set before the save returns). Drop the never-started-VM half, or make it assert something that can fail.

### F3 [minor] check.full is not deterministic: one unrelated wall-clock test failed
- Where: /home/user1/orca/workspaces/Puber/cache-targeted-invalidation/app/src/test/kotlin/com/kino/puber/ui/feature/history/vm/HistoryVMTest.kt:609 (`awaitState` polls with `Thread.sleep` until a `System.nanoTime` deadline)
- Problem: I ran `check.full` at e4d5dba2: 2305 tests, 1 failed (`HistoryVMTest.refreshAfterPageError_keepsContentVisible`, timed out at :609). `HistoryVMTest` then passed twice with `--rerun`, once in isolation and once together with `CachedFeedTest`, `com.kino.puber.ui.feature.player.vm.*` and `PlayerInteractorTest`, which all passed too. `:app:detektAll :app:lintDevDebug` passed in a separate run. The diff does not touch HistoryVM, and the failure matches the known `unit-suite-flaky` run.
- Failure scenario: gate 2 or CI hits the same timeout and blames this branch.
- Fix: nothing to change in this run. Add this case to `.flow/runs/unit-suite-flaky/state.md`.

### Unproven
- /home/user1/orca/workspaces/Puber/cache-targeted-invalidation/app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt `flushOwedInvalidation`: the spec says `backPressed_whileBackgroundFlushPending_marksOnceBeforeBack` fails if the flags are cleared after the suspension. It does not: the `backgroundFlushJob?.join()` at exit hides that mutation, and no test covers the "clear before suspension" ordering. This would matter only if a save succeeded while a background mark is still suspended, which needs a resume and a 30 s tracker save landing inside one Room write. There is no evidence that happens.
