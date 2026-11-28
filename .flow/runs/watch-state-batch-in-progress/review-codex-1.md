CODEX: ran: review, adversarial

CONFIRMED:
- [minor] app/src/androidTest/kotlin/com/kino/puber/data/db/WatchStateDaoTest.kt:192-208 - `markInProgressInOneTransactionEmitsNoPartialList` may not fail if the outer `transactions().run` is removed. The three writes run back-to-back and the test asserts only after a settle delay. Room's invalidation tracker is asynchronous and can coalesce the three writes into one emission, so a regression to three separate commits could still show only the complete list. The "no partial list" assertion would then pass vacuously, which weakens AC-4 as a guard against that regression. I found no mutation check in the QA report. A rollback test would be deterministic: throw after the first `markInProgress` inside `run`, then assert that no row persisted, and check that it fails with the outer transaction removed.
- [minor] /home/dev/orca/workspaces/Puber/watch-state-batch-in-progress/docs/superpowers/specs/2026-11-28-watch-state-batch-in-progress.md:41 - The AC-4 command `make DEVICE=192.168.1.110:5555 itest TESTS=...` uses the Makefile default `GRADLE ?= ./gradlew` (Makefile:23). `guard-bash.sh` refuses `./gradlew` in a worktree, so running the AC as written from the worktree fails. It needs `GRADLE=./tools/agentw`.

REJECTED:
- The 1 s `delay(SETTLE_MILLIS)` in the same test can fail on a slow device even when the batch committed - unproven. It is the same settle pattern the file already uses at line 107, and there is no evidence of slow emissions. The only failure mode is a false fail, not a missed defect.
- The production change in `WatchStateRepository.kt` is wrong or incomplete - Codex found no defect there. The diff adds no screen, DI scope or lifecycle surface. I found nothing in it to challenge on cancellation or leaks.

VERDICT: changes needed (two minor items: test strength for AC-4, and the AC-4 command in the spec; neither blocks the production change)
