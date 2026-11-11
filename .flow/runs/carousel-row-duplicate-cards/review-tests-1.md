LENS: tests  ROUND: 1  BASE: 901d820d
### F1 [major] The device checks for AC-7 and AC-8 never ran the refresh, so focus after de-duplication is tested nowhere
- Where: /home/user1/orca/workspaces/Puber/carousel-row-duplicate-cards/docs/superpowers/evidence/2026-11-11-carousel-row-duplicate-cards-device.md (AC-7 and AC-8 sections, "Setup" line)
- Problem: AC-7 and AC-8 say "opens a title, saves or unsaves it, returns". The save or unsave is what calls `coordinator.requestRefresh()` and so runs `restoreLoadedDepth`. The evidence says "No title saved, unsaved or marked watched", and its own Limit line says the run "does not show that the restore ran at depth". Both device rows only show that a normal walk has no duplicates, which was already true on base. The test design marks Manual 1 and 2 as P0 `red`, but no device run on base is recorded. AC-7's focus half ("focus is on the card opened before" after de-duplication shifts indexes, spec line 30) has no unit test either: the new unit tests check ids and request counts, not focus.
- Failure scenario: de-duplication shifts the index of the focused id. Every unit test still passes and the device run as done still reports PASS, because it never reached the changed code.
- Fix: re-run Manual 1 and 2 as written, with a save or unsave before `BACK`, and record whether the reload ran (for example the count of `reload restores` / `loadPage` log lines, not stored raw). Ideally do one run on a base APK first to show red. Or record AC-7's focus part as unverified for the gate.

### F2 [minor] Removing only the `MAX_RESTORED_PAGES` coercion is caught by no test, though the test design lists it as guarded
- Where: /home/user1/orca/workspaces/Puber/carousel-row-duplicate-cards/app/src/main/java/com/kino/puber/ui/feature/contentlist/vm/ContentListPagingVM.kt:198 and SectionVMRefreshDepthTest.kt `refresh_ofARowDeeperThanTheRestoreCeiling_makesAtMostNineRestoreRequests`
- Problem: with `restoreTo = depth` and the counter still there, the ceiling test (depth 12, cursor from 1) still requests pages 2..10, nine calls. The page-zero test requests pages 1..9, ten calls in total, still within `<= 10`. Both pass. The plan's row only claims "counter and coercion both lost", but the brief and test design list "lost coercion" as a guarded mutation, and nothing kills it alone. The two bounds only differ when replies read ahead in a row deeper than 10. Example: depth 12, every reply reports `current = requested + 1`. With coercion the restore requests 2, 4, 6, 8, 10 (5 calls). Without it, it also requests page 12 (6 calls) and restores beyond the 10-page ceiling.
- Failure scenario: someone deletes the `coerceAtMost` because "the counter already bounds it". A deep filtered row then restores more server pages than `MAX_RESTORED_PAGES`, and the suite stays green.
- Fix: add one row (or extend the ceiling test with a read-ahead variant): depth 12, replies `current = requested + 1`, and assert the exact pages after the walk are `{1,2,4,6,8,10}`, page 12 never.

### F3 [nit] The test design's mutation claims don't match what the tests catch
- Where: /mnt/data/projects/PetProjets/Puber/.flow/runs/carousel-row-duplicate-cards/test-design.md rows AC-2 and AC-4 (also mirrored in the plan)
- Problem:
  - **AC-2 row:** it says the test fails "with last-wins (`[1,3,4,2,5]`)". Last-wins via `LinkedHashMap.put` keeps the key's first insertion position, so the order is still `[1,2,3,4,5]` and the test stays green. Only the item object differs, and `mapperFor` maps by id, so the test cannot see it.
  - **AC-4 lower-page row:** "page 2 requested nine times" holds only for the `page = next.pagination.current` mutation. Deleting the `maxOf` line entirely leaves that test green, because the cursor still steps 2..10. That mutation is caught instead by `refresh_afterAPageThatReadAhead_doesNotReRequestAServerPageItAlreadyHolds`, which requests page 4.
- Failure scenario: none at runtime. A reviewer relying on the record would overestimate what each test kills.
- Fix: correct the two claims in the record. Optionally make the AC-2 fixture's duplicate item differ in a mapped field (for example the title) and assert the first occurrence's value.

The other named mutations are each killed by at least one test:
- Swapping `maxOf` for a plain assignment: the lower-page test (page 2 counted 9 or 20 times).
- Deleting the `maxOf` line: the read-ahead tests.
- Losing the counter: the page-zero test (11 calls).
- Counter bound too low: the ceiling test (page 10 missing).
- Dedupe dropped as a list append: the overlap test and the ShowAll retry test.
- Empty-chain break removed: the empty-budget test (5 calls instead of 4).

The red runs on base are recorded for all five P0/P1 red unit tests, and the guard rows are marked `guard`. The ShowAll test polls with real time (`runBlocking`, `withTimeout(2_000)`, `delay(10)`), unlike the rule "virtual time via coroutines-test", but it follows the pattern the file already uses on base. `check.full` (`AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`) is green in the worktree (rc 0, tasks up to date from QA's run).

### Unproven
- SectionVMRefreshDepthTest.kt `retry_ofAGridWithOverlappingPages_publishesEachIdOnce` (ShowAllVMTest.kt): the 2 s wall-clock waits could flake on a loaded CI host. This would be real if any CI timeout failure of the existing ShowAll tests is on record.
