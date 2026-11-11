# Readiness 2: collection-detail-inplace-refresh

Verdict: **READY**

Worktree HEAD 835e55ad, base 0e4449e2, clean tree. Change since round 1 (`git diff 2fbf378e HEAD`): plan and spec docs only. Spec Test design and `test-design.md` traceability rows are identical (`diff` empty).

## Round 1 fix list

| Fix | Status | Evidence |
|---|---|---|
| 1. `refreshAfterPublishedToggle_showsServerSavedValue` catches its mutation | Resolved | Spec `:139`, `test-design.md:33`, plan `:83`. Trace against the plan's `loadItems()` shape: save 43 is active at return 2, so the A1 clear at `:46` of the shape is skipped; without the clear in `publishItems`, 42 stays in the set and fetch 2's false is masked as true, so the test fails. With the clear, 42 shows false. On base (`CollectionDetailVM.kt:69-80,130-138`) the toggle of 42 during `Loading` is a no-op and fetch 2 publishes 42 false: guard passes. Unasserted precondition is stated |
| 2. Handler citation | Resolved | Plan `:30` cites `main/core/coroutine/CoroutinesErrorHandler.kt:10-14`; that range is `DefaultExceptionHandler` returning a `CoroutineExceptionHandler` |
| 3. AC-2 P0 names an automated test | Resolved | Spec `:147`, `test-design.md:41`: P0 guard `collectionDetailFocusStaysOnTheFocusedCardWhenContentIsReplaced`; device half `Manual 1-3` kept as manual by nature (`:148`/`:42`). Plan Task 2 `:102,111` and handoff `:127` cover it |
| 4. Plan line 6 | Resolved | "pending conductor confirmation" gone; A1 recorded as adopted, matching state.md Decisions (2026-11-11, Plan Q1) |

## Regression checks

| Check | Result |
|---|---|
| Every AC has a task and a Test design row | Yes, unchanged from round 1; AC-2 now also in Task 2 |
| Citations resolve (resampled at HEAD) | `CollectionDetailVM.kt:34,37-43,64,69-80,98-102,104-128,130-138,154-166,168-174`; `PuberVM.kt:102-111,113-115`; `CollectionDetailVMTest.kt:45-57,70-82,100-116,118-126,128`: all resolve |
| Build stays green per task, order by dependency | Yes, task bodies unchanged apart from the rows above |
| P0 rows name defect, mark, test | Yes. AC-2 instrumented row names its defect by reference to the AC-7 row (`:142`) |
| Out of scope, blocking questions | Unchanged; none open |

Note (non-blocking): plan Task 2 row `:111` lists the shared test at P1 while the spec holds it at P0 for AC-2; priority does not change the work.
