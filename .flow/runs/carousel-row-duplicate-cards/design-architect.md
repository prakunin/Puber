# Design (architect): carousel-row-duplicate-cards

Base 901d820d. Implements research.md Option A within brief.md Scope. File paths are relative to the worktree; `ContentListPagingVM.kt` is `app/src/main/java/com/kino/puber/ui/feature/contentlist/vm/ContentListPagingVM.kt`.

## Decisions

| Question | Decision | Why |
|---|---|---|
| Where does the next restore request start? | `page = maxOf(page, deepest.pagination.current) + 1`, where `page` is the last page requested (starts at `firstPage.pagination.current`). | Skips server pages a filtered answer already read (AC-1). With `current == requested` it is `page + 1`, the same requests as base (AC-3). The `maxOf` makes the cursor strictly increasing even when a reply reports a lower `current` (AC-4). |
| When does the loop stop? | `while (maxOf(page, deepest.pagination.current) < restoreTo && deepest.pagination.current < deepest.pagination.total)`; the empty-chain break at `ContentListPagingVM.kt:217-221` stays as is. | `deepest.current >= restoreTo` ends it (AC-1); the empty chain still counts raw `next.items` (AC-5). |
| How is the request count bounded? | By the strictly increasing cursor itself: requests are at most `restoreTo - firstPage.pagination.current <= MAX_RESTORED_PAGES - 1`. No second counter variable. | The brief asks for a request ceiling independent of the response; a cursor that grows by at least 1 per request and stops at `restoreTo` is that ceiling, and a reply can only lower the count. One variable instead of two keeps the comment at `:202-203` true with one fact to maintain. |
| How are duplicates removed? | Accumulate into a `LinkedHashMap<Int, Item>` with `putIfAbsent(item.id, item)` (first occurrence wins, insertion order kept), return `items = map.values.toList()`. `Item.id` is `Int` (`app/src/main/java/com/kino/puber/data/api/models/Models.kt:47`). | Same idiom as the interactor (`ContentListInteractor.kt:141,153`); covers the overlap source and the reorder (drift) source (AC-2). |
| Which pagination is published? | Unchanged: `deepest.pagination` (`ContentListPagingVM.kt:223`). | `publish` sets `currentPage` from it (`:269`); the walk continues from the last server page actually read. |
| Failure handling | Unchanged: any failed `loadPage` returns `null`, nothing published (`:207-214`). | AC-6. |

## Changes by layer

| Layer | Change |
|---|---|
| API / repository | N/A: `ContentListInteractor.fetchFilteredPage` (`ContentListInteractor.kt:125-168`) and `FreshSectionPager.loadPage` (`FreshSectionPager.kt:22`) are untouched. |
| ViewModel state and actions | `ContentListPagingVM.restoreLoadedDepth` (`ContentListPagingVM.kt:191-224`): cursor and loop condition per Decisions, `items` becomes the id-keyed map, the comment at `:202-203` restated for the new cursor, KDoc at `:173-189` gains one sentence on first-occurrence de-duplication. `MAX_RESTORED_PAGES` (`:399`) and `MAX_EMPTY_PAGE_CHAIN` (`:379`) unchanged. `ShowAllVM` inherits the fix (`app/src/main/java/com/kino/puber/ui/feature/showall/vm/ShowAllVM.kt:32`). |
| Screen and DI module | N/A: no wiring change. |
| Compose content | N/A: lazy keys stay `item.id` (`SectionRow.kt:151`, `ShowAllScreenContent.kt:114`); focus stays keyed by id (`SectionRow.kt:124`). |
| Resources | N/A |
| Tests | New cases in `app/src/test/kotlin/com/kino/puber/ui/feature/contentlist/vm/SectionVMRefreshDepthTest.kt`, reusing `pageOf(vararg ids, current, total)` (`:189`), `mapperFor`, `servesFirstPageFromTheNetwork` from `SectionVMTestFixture` (`SectionVMTest.kt:57,76,132`); the four existing cases (`:32,66,116,154`) unchanged. Rows are the test-designer's. |

## Alternatives rejected

- Separate request counter beside a response-driven cursor: two variables encoding one bound.
- `distinctBy { it.id }` in `mapItems` (research Option C): hides the symptom, re-reads pages.
- De-duplication in `Paginator` `Replace` (Option B): owned by run `paginator-hardening` (state.md Decisions Q1).
- Cursor `deepest.current + 1` without `maxOf`: a reply reporting a lower `current` would re-request the same page until the ceiling.

## Risks

| Risk | Kind | Where | Guard |
|---|---|---|---|
| Focus lands elsewhere after a return because de-duplication shifts indexes, or the focused id was a duplicate's second copy. | Focus and D-pad | `SectionRow.kt:124` (focus by id); `ContentListPagingVM.kt:97-102` | First occurrence keeps the id present; AC-7 on device. |
| Fewer restore requests can leave the restored row shorter than the walked one, where the walk's own page `4` held titles now removed as duplicates. | Data shape | `ContentListPagingVM.kt:195-222` | Every id the walk showed is still read (same server pages, minus re-reads); AC-1 asserts each id once. |
| `currentPage` after a restore no longer equals the walked depth when a reply jumped ahead; the next walk step starts at `deepest.current + 1`. | Correctness | `ContentListPagingVM.kt:151,269` | Intended: that is the next unread server page; AC-1 covers the published pagination. |
| Hide-watched empty pages with `current` far ahead: the jump can end the loop before the empty-chain break. | Correctness | `ContentListPagingVM.kt:217-221`; `ContentListInteractor.kt:144,160-162` | Fewer requests than base, never more; AC-5. |
| `FreshSectionPager` rejects a cursor it never issued. | Lifecycle | `FreshSectionPager.kt:29` | Its pages report `current == requested`, so the cursor equals base; AC-3. |
| Map allocation per restore. | Performance | `ContentListPagingVM.kt:199` | At most `MAX_RESTORED_PAGES` pages of ~25 items; negligible. |

## Open questions

N/A: no AC needs changing.
