LENS: edge  ROUND: 2  BASE: a021e92c

No findings.

**Flag off is base behaviour.** I compared `FocusTargetState.kt` at `0e4449e2` and `48ff9085`. When `holdPendingFocusTarget = false`, each of the three Task 3 changes falls back to the base code:
- **(a)** The returned target `requestTargetId` is `targetItemId`, as on base.
- **(b)** `ReconcilePublishedItemsEffect` works out the next focused card inside the effect from `focusedItemId.value` with `resolveFocusedItemId`, as on base. The new `resolvedItemId` parameter is passed in but not read.
- **(c)** `requestPending(false, …)` calls `requestAfterComposition`, as on base. `requestUntilFocused` is only reached when the flag is on.

`CollectionDetailScreenContent.kt:64` is the only place that sets the flag. That caller passes `isTargetRow = true` and no `initialFocusedItemId`, which meets both conditions stated in the KDoc.

**Round-1 rows:**
- **Rows 1–3 (focus helper):** the stale pending target, the stale value written back by the effect, and the shared-change scope. All three now apply only to CollectionDetail, where their trigger cannot happen: row 1 needs a row that is not the target row, and row 2 needs an `initialFocusedItemId`. Each one now has a guard test in `VideoGridInitialFocusTest`.
- **Row 4 (VM):** at the start of each load, `loadItems()` drops finished jobs and then keeps only the ids of saves still in flight. So a save of A that has already finished no longer survives just because B's save is pending. Two saves of the same item keep that id while either is still running. If a save finishes before its job is recorded (`saveJobItemIds[job]` is set after `launchMutation` returns), the leftover entry is dropped at the next load and does nothing.
- **Row 5 (test):** the test now fetches items 42 and 43 and checks that only 42 is on screen while the save is pending. If `awaitPendingMutations()` were removed, 43 would appear early and the test would go red.
