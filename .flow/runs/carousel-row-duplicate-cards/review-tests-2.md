LENS: tests  ROUND: 2  BASE: afc1e706

No findings.

I did not edit the worktree, so I did not apply the mutation myself. I checked it by tracing the code, and the plan records the implementer's red run.

**`refresh_ofADeepRowWhosePagesReadAhead_stopsAtTheRestoreCeiling` does kill the dropped coercion.**
- **Depth after the walk:** each reply after page 1 reports `current = requested + 1`, so the walk asks for pages 2, 4, …, 22. The row then holds `currentPage = 23` (`ContentListPagingVM.kt:278`), and the restore depth is 23.
- **With `coerceAtMost(MAX_RESTORED_PAGES)`:** `restoreTo = 10`, so the loop at `ContentListPagingVM.kt:210` asks for 2, 4, 6, 8 and 10. After page 10 the cursor is 11, which stops the loop. That is exactly `[2,4,6,8,10]`.
- **Without the coercion:** `restoreTo = 23`, and the request counter (9) is now the only bound. The loop asks for 2, 4, …, 18, so the assertion fails at page 12.
- **The ceiling it guards:** `failAfterCalls = 20` is never reached; at most 10 calls are made.
- **Recorded red run:** the plan's "Fix round 1" entry says the test fails with the coercion removed and is the only one of the 12 tests in the class to fail. That matches the trace.

**The ShowAll polling no longer races.** `mapped` is now a `CopyOnWriteArrayList`. `mapped += …` resolves to `add`, and the `none {}` / `first {}` polls read a snapshot, so they cannot throw `ConcurrentModificationException` while the mapper appends from `Dispatchers.Default`. `page2Calls` is an `AtomicInteger`.

**Suite state:** I ran `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests '*SectionVMRefreshDepthTest' --tests '*ShowAllVMTest'`. The build succeeded: `SectionVMRefreshDepthTest` passed 12 of 12 (including the new test), and `ShowAllVMTest` passed 10 of 10, with nothing skipped. `git status` is still clean.

### Unproven
- `/home/user1/orca/workspaces/Puber/carousel-row-duplicate-cards/app/src/test/kotlin/com/kino/puber/ui/feature/showall/vm/ShowAllVMTest.kt:309` The final `shownIds(vm)` assert can read the grid state from before the retry. That state is already `[1,2,3,4,5]`, so the assert proves nothing new; the `restored` assert carries the test. This would matter only if the pre-retry grid could ever hold a duplicate, which the paginator's id equality prevents.
