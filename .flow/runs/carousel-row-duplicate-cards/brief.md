# Brief: carousel-row-duplicate-cards

Base 901d820d. Analyst, 2026-11-11. Builds on `research.md` (cited as research.md: section). No brief template exists in the plugin templates; this follows the analyst job layout.

kind: bug. size: M. tracks: none. Device verification: **yes** (ViewModel state, TV focus restore after a reload).

## Request
A paged catalogue row shows the same title several times while scrolling right. Owner decisions (state.md Decisions): the row is the Series tab "Popular" row; fix only the `restoreLoadedDepth` path.

## Root cause (confirmed by code trace; not reproduced on a device)
1. A refresh (`ContentListPagingVM.kt:104-108`) restores the row to `currentPage`, which `publish` sets from the last response's `pagination.current` (`ContentListPagingVM.kt:269`), i.e. a server page number.
2. With "show anime" off on a `FollowPreference` row, or "hide watched" on, `fetchFilteredPage` reads several server pages per call and answers with `pagination.current` = the last server page read (`ContentListInteractor.kt:131-167`).
3. `restoreLoadedDepth` advances its own cursor by 1 per request (`ContentListPagingVM.kt:204-208`), ignoring that the previous answer already reached further. The next request starts at a server page it already holds.
4. It appends with no id check (`ContentListPagingVM.kt:215`) and publishes through `replace` (`ContentListPagingVM.kt:285-286`); `Paginator` `Replace` stores the list as is (`Paginator.kt:214-227`).

Trace (perpage 25, half the titles filtered): walk gives page 1 = server 1+2 (current 2), page 3 = server 3+4 (current 4). Refresh, depth 4: cursor 2, request 3 gives server 3+4 (current 4); `4 < total` so request 4 gives server 4+5. Server page 4 is in the row twice, about one page apart.

Second source on the same path, `plausible`: with no filter, a reorder of `popular` between consecutive reads puts a boundary title twice, adjacently (research.md: Current state in code). Adjacent equal lazy keys (`SectionRow.kt:151`, `ShowAllScreenContent.kt:114`) can throw "Key ... was already used" (`unverified`).

The walk path is not affected: `NextPage` merges by id (`Paginator.kt:370-389`). ShowAll extends `ContentListPagingVM` (`ShowAllVM.kt:56-77`) and has the same bug.

## Scope
In: `restoreLoadedDepth` (`ContentListPagingVM.kt:192-225`) takes each next request from `deepest.pagination.current + 1`, stops once `deepest.pagination.current >= restoreTo`, keeps a separate request counter bounded by `MAX_RESTORED_PAGES` (`:399`), and de-duplicates the accumulated items by id, first occurrence wins (research.md: Options A). Unit tests in `SectionVMRefreshDepthTest.kt`.
Out: de-duplication in `Paginator` `Replace` / `NextPage` (run `paginator-hardening`); Home rows.

## Acceptance criteria
- AC-1. Given a row whose `loadPage` answers page 1 with `current = 2` and page 3 with `current = 4` (total > 4), walked to `currentPage = 4`, When a refresh runs, Then `loadPage(page = 4)` is not requested and the published row holds each id once. (unit, red on base)
- AC-2. Given the restore reads pages whose items overlap by id, When the restored list is published, Then each id appears once, in the order of first occurrence. (unit, red on base)
- AC-3. Given pages with `current == requested` and disjoint ids, When a refresh runs, Then the requests and the published list equal base (the four existing `SectionVMRefreshDepthTest` cases stay green unchanged).
- AC-4. Given a row deeper than `MAX_RESTORED_PAGES`, or answers that report `current` lower than requested, When a refresh runs, Then the restore makes at most `MAX_RESTORED_PAGES - 1` `loadPage` calls. (unit)
- AC-5. Given "hide watched" on and a restore page that comes back empty with `current` several pages ahead, When the empty chain exceeds `MAX_EMPTY_PAGE_CHAIN` (`:379`), Then the restore stops and publishes what it has, as on base. (unit)
- AC-6. Given a later restore page fails, When a refresh runs, Then nothing is published and the row stays as it was (existing `refresh_whoseLaterPageFails_leavesTheRowAsItWas` green).
- AC-7. Given the Series tab with "show anime" off, the Popular row scrolled past at least 3 logical pages, When the user opens a title, saves or unsaves it, returns, and scrolls right to the row's end of loaded pages, Then no title appears twice in the row and focus is on the card opened before. (device, D-pad)
- AC-8. Given the same settings, When the same steps run in the Popular "show all" grid, Then no title appears twice. (device)

## Risks
- Risk area "ViewModel state and screen wiring", hence M rather than the research's S: the change is one function, but it decides the list a refresh publishes and the `currentPage` the walk continues from.
- Focus restore after a return depends on the focused id surviving the reload (`ContentListPagingVM.kt:97-102`, focus keyed by id at `SectionRow.kt:124`). De-duplication shifts indexes; AC-7 checks it.
- The request ceiling comment (`ContentListPagingVM.kt:202-203`) must stay true: the response may decide where to read, never how many reads.
- `FreshSectionPager` refuses a cursor it never issued (`ContentListInteractor.kt:60-62`); its pages have `current == requested` (research.md: Current state in code), so the new cursor sends the same requests. AC-3 guards it.

## Open questions
N/A: Q1 and Q2 are answered in state.md Decisions.
