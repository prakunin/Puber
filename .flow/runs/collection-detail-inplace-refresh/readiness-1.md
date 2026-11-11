# Readiness 1: collection-detail-inplace-refresh

Verdict: **NOT READY**

Worktree HEAD 2fbf378e, base 0e4449e2. Spec and `test-design.md` match (only heading levels differ).

## Checks

| Check | Result |
|---|---|
| Every AC has a task and a Test design row | Yes. AC-1, 3-6, 8: Task 1. AC-7: Task 2. AC-2: Verification handoff, Manual 1-3 |
| Citations resolve | 17 sampled, 16 resolve; see table below |
| Build stays green per task | Yes. Task 1 commits only after the unit class, the fast gate and detekt pass; red runs stay uncommitted. Task 2 adds tests only |
| Task order follows dependencies | Yes. Task 2 does not depend on Task 1 |
| P0 rows name a defect, a mark and a test | AC-1, AC-4, AC-5 and both AC-6 P0 rows: yes. AC-2 P0 row names `Manual 1-3`, not an automated test (fix 3) |
| Red rows are red on base | Checked by trace against `CollectionDetailVM.kt:69-80,98-102` at base: AC-1, AC-4 (`Error`), AC-5 (id 1 wins), AC-6 SettledFirst/SettlesAfterFetch, P2 failed-refresh: all fail on base |
| Guard rows pass on base, and their mutation is caught | `refreshAfterPublishedToggle_showsServerSavedValue` fails both checks (fix 1). The other guards pass both |
| Plan stays inside Out of scope | Yes. Production change only in `CollectionDetailVM.kt`; `launchMutation`/`awaitPendingMutations` are not edited; `CollectionDetailScreenContent.kt` unchanged |
| Blocking open questions | None open; Q1-Q3 and plan Q1 (A1) answered in state.md Decisions |

## Citations sampled

| Citation | Claimed | Found |
|---|---|---|
| `CollectionDetailVM.kt:69-80` | `loadItems()` publishes `Loading`, unsequenced `launch` | Yes |
| `CollectionDetailVM.kt:98-102` | `onReturnedContentChanges` | Yes |
| `CollectionDetailVM.kt:154-174` | `launchMutation`, `awaitPendingMutations` | Yes |
| `CollectionDetailVM.kt:37-43` | `dispatchError` shows a message while `Content` is shown | Yes |
| `CollectionDetailScreenContent.kt:46-52,59-65,81,82` | `when (state)`, `rememberReconciledItemFocus`, `LazyVerticalGrid`, `GridCells.Fixed(3)` | Yes |
| `FocusTargetState.kt:50-55` | `rememberSaveable(rowKey)` | Yes (`:51`) |
| `FocusTargetResolver.kt:10-23` | removed card hands focus to the card at its index | Yes |
| `PuberVM.kt:59-76,102-115` | `testStateValue/testStateFlow/testMessageValue`; `launch` with the exception handler | Yes |
| `main/core/coroutine/handler.kt:10-14` | `CoroutineExceptionHandler` that never sees a cancellation | **No**: the file has 8 lines; the handler is `main/core/coroutine/CoroutinesErrorHandler.kt:10-14` (fix 2) |
| `FavoriteVM.kt:28,42-43` | `loadDataJob` cancel-then-launch | Yes |
| `HomeVM.kt:639-660` | `applySavedOverrides` | Yes |
| `CollectionInteractor.kt:25-29` | `getCollectionItems` | Yes |
| `VideoItemUIMapper.kt:25` | `mapShortItemList` | Yes |
| `Models.kt:64` | `inWatchlist` | Yes |
| `SectionVMTest.kt:693-695` | unconfined state recorder | Yes |
| `HistoryVMStoredFirstPageTest.kt:87-90` | `proceedInvoke` stub that invokes the action | Yes |
| `detekt.yml:113-115,157-159,756-759` | `LongMethod`/`TooManyFunctions` exclude tests; `ReturnCount` max 3 | Yes |
| `CollectionDetailScreenContentFocusTest.kt:34-36,60-61,96-107,120-130,132-138` | `items` (24), state swap, `Detail`, helpers, constants | Yes |

## Fix list

1. `refreshAfterPublishedToggle_showsServerSavedValue` (spec Test design, `test-design.md`, plan Task 1 row) no longer catches its named mutation. With A1, `loadItems()` clears `savedSinceLastPublish` at the second return because no save is active, so deleting the clear in `publishItems` leaves the test green. The surviving defect: a settled toggle on 42 stays in the set, a save on another card is pending when the next return starts, and the next fetch's `isSaved` for 42 is masked. Reshape the row so a save on a second card (for example 43) is still pending when return 2 starts. Also say that the "published 42 true" precondition is not asserted, so the guard passes on base (on base that publish shows false).
2. Plan Task 1 Files: replace `main/core/coroutine/handler.kt:10-14` with `main/core/coroutine/CoroutinesErrorHandler.kt:10-14`.
3. AC-2 P0 row (spec Test design, `test-design.md`): name the automated half (`collectionDetailFocusStaysOnTheFocusedCardWhenContentIsReplaced`) next to `Manual 1-3`, or mark the row manual by nature.
4. Plan line 6: drop "pending conductor confirmation". A1 is adopted (state.md Decisions; spec Amendment A1).
