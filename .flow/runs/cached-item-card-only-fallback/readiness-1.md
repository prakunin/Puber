# Readiness 1: cached-item-card-only-fallback

Verdict: **READY**

Spec and plan at worktree HEAD 8e1321e6 (plan untracked); citations checked against `git show 4db1b061:<path>`.

## Checks

| Check | Result |
|---|---|
| Every AC has a task and a Test design row | yes: AC-1..AC-6 and D1 in Tasks 1-4 (plan Coverage), AC-7/AC-8 in Device work, Manual checks 1-2 |
| Citations resolve | yes, all sampled (table below) |
| Build stays green per task | yes: Task 1 red rows compile on base and are `@Disabled`; Task 2 changes the interface together with every implementer (`RoomPersistentPayloadStore`, `FakePayloadStore`, the in-memory stores at `HomeInteractorCacheTest.kt:217`, `ItemDetailsRepositoryTest.kt:265`); the relaxed mocks at `HomeInteractorTest.kt:50,275` need no edit; `androidTest/.../PersistentPayloadStoreDbTest.kt` calls `write` as a statement only |
| Tasks ordered by dependency | yes: fixture -> store/feed ownership -> fallback -> `itemFeed` |
| P0 rows name defect, red/guard, test name | yes; manual P0 rows are AC-7/AC-8 (Manual checks 1-2) |
| Inside Out of scope | yes: no change above `ContentCacheRepository`, no row splitting, no offline device check |
| Blocking open questions | none; Q1-Q3, D1 answered |

## Sampled citations

| Citation | Resolves to |
|---|---|
| `PersistentPayloadStore.kt:29-32` | `write` returning Unit, default `writeAll` |
| `PersistentPayloadStore.kt:99-109,111-126,130-134` | Room `write`, `writeAll`, `refuse` with `dao.delete(key)` at :133 |
| `CachedFeed.kt:46-56` | constructor |
| `CachedFeed.kt:238-250,263` | `load` write, `supersedesWrite` remove, `encodedLength = payload.length` |
| `CachedFeed.kt:396-400,401-432,412,418-424` | `putAll` epochs, `writeAll`, settle loop, supersede delete |
| `CachedFeed.kt:448-458`, `:172-195`, `:654-657` | `epochOf`, `Epoch`, `DefaultJson` (no `encodeDefaults`) |
| `ContentCacheRepository.kt:37,191-232,274-285,293-300,389-403,414-438,491-496,551` | `itemFeed`, `observeItemDetails`, `mergeItems`, `markItemDetailsStale`, `newFeed`, `readItemRecords`/`mergeItemRecord`, `ItemRecord`, `itemKey` |
| `Models.kt:542-547` | `Season(id, number, title, episodes)` |
| `FakePayloadStore.kt:12,36,38-50,62-67` | `refusedKeys`, `onBeforeWrite`, `write`, `writeAll` |
| `PersistentPayloadStoreTest.kt:95-116,120` | the two delete tests to replace; byte-boundary test |
| `detekt.yml:157-161,756-759` | `TooManyFunctions` 25; `ReturnCount` 3; `CachedFeed` has 22 own functions at base |
| `PlayerVM.kt:1986` | `markItemDetailsStale` call |

## Notes for the implementer (not blocking)

| # | Note |
|---|---|
| 1 | `markingAnOversizedDetailsRecordStaleKeepsACardOnlyRow` is `red (compile)` in test-design.md but uses only public API; follow plan Task 4 (plain red before the edit). |
| 2 | `FakePayloadStore.onBeforeWriteAll` (Task 1) is used by no test row; use it or drop it. |
| 3 | The fallback write in `persistLoaded` keeps the base check-then-write window (`CachedFeed.kt:238-243`): a `putAll` landing while the fallback write is suspended is overwritten by the card-only copy. No test covers it; reviewers should confirm it is no wider than the base window. |
| 4 | test-design.md R11 points to "Manual step 2"; the retry is in Manual check 1, step 5. |
