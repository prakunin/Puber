LENS: tests  ROUND: 2  BASE: 40762767

The two test classes touched by the incremental diff (`PlayerVMInvalidationTest`, `DetailsVMCachedLoadTest`) pass at 525d68cd: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests ...PlayerVMInvalidationTest --tests ...DetailsVMCachedLoadTest`, BUILD SUCCESSFUL. I did not run the full check, as round 2 requires.

Two of the triage-1 fixes hold up. For #1, `clearedDuringBackgroundFlush_stillMarksAndRefreshes` keeps the held save running through the cancel with `NonCancellable`. The plan records that it fails without the wrap, which amounts to the red on e4d5dba2. It checks `exactly = 1` on the mark and the refresh, so a double flush would fail too. For #3, `backPressed_withoutSuccessfulSave_marksAndRefreshesNothing` is relabelled P0 guard, the never-started half is removed, and a `router.back` check was added.

### F1 [major] The new AC-6 test is labelled "P0 red" but only exercises code that already exists on base
- Where: docs/superpowers/specs/2026-11-22-cache-targeted-invalidation.md (Traceability row AC-6 unit, `DetailsVMCachedLoadTest.afterMarkItemDetailsStale_detailsOpenRevalidatesAndShowsNewProgress`); app/src/test/kotlin/com/kino/puber/ui/feature/details/vm/DetailsVMCachedLoadTest.kt:231
- Problem: The spec defines "Red" as "fails on 40762767". The test only uses `ItemDetailsRepository.markStale`, `ContentCacheRepository(store, clock, decodeDispatcher)` and the existing `FakePayloadStore` API. All of these are on base unchanged (checked with `git show 40762767:.../ItemDetailsRepository.kt`). Nothing the branch adds is needed for the test to compile or pass, so it should be green on base. The plan's fix-round record gives only a mutation proof ("Without the `markStale` line it fails") and no red run on base.
- Failure scenario: The traceability table claims a red-first P0 proof for AC-6. That proof cannot exist, so the reader overestimates what the test shows. The test is a guard on the repository's stale-then-revalidate path. It is not proof that the change works.
- Fix: Relabel the row "P0 guard" and name its mutation (drop `markStale`, or a stale mark the memory tier ignores). Alternatively, record an actual run on 40762767 if it really fails there.

### F2 [minor] The AC-6 row describes a different test from the one written
- Where: docs/superpowers/specs/2026-11-22-cache-targeted-invalidation.md (same AC-6 row); DetailsVMCachedLoadTest.kt:233-251
- Problem: The row says the test runs a "real `PlayerInteractor.markItemDetailsStale(42)` on that repository" and that the "last item mapped carries the new progress". The test calls `repository.markStale(42)` directly. Its `before`/`after` items differ only in `title`, and the test name still says "ShowsNewProgress". The interactor delegation is covered separately (`PlayerInteractorTest.markItemDetailsStale_marksTheRepositoryEntryStale`, line 664), so no behaviour is left without a test. The mismatch is in the description.
- Failure scenario: A reader of the traceability table assumes a progress-carrying item flows end to end through the interactor. Neither the interactor nor any progress field is exercised.
- Fix: Change the row to match the test (repository `markStale`, items told apart by a field). Or vary a watch-progress field instead of `title` and call the real interactor, as the row says.

### F3 [nit] The cleared-VM test does less than its row says
- Where: app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMInvalidationTest.kt:139; spec row `clearedDuringBackgroundFlush_stillMarksAndRefreshes`
- Problem: The row says the VM is "cleared as the `ViewModelStore` does (scope cancel and `onCleared`)". The test only calls `testCancelScope()`. `PlayerVM.onCleared` (PlayerVM.kt:2041) does not touch the flush, so the outcome is unchanged.
- Fix: Change the row to "scope cancel", or also call `onCleared` through a test hook.

No findings.

### Unproven
- PlayerVM.kt:1505 The flush runs in `withContext(NonCancellable)` after `previous?.join()`. If `previous` is a flush that hangs (for example `markItemDetailsStale` never returns), the coroutine now outlives the VM with no bound. This only matters if the store or coordinator call can block indefinitely, and nothing suggests it can.
