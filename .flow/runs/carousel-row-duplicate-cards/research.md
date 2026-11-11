# Research: carousel-row-duplicate-cards

## Request

"While scrolling cards in a carousel, the same card repeats within one row. E.g. the top-series row: scrolling right I can see the same series several times in that row." (owner, translated from Russian)

## Understanding

A catalogue row (the Series tab "Popular" row is the paged one; Home rows do not page) shows the same title more than once after some scrolling. The normal scroll path (walk) is protected: the paginator merges every next page by item id. The unprotected path is the **reload that restores the row's depth** (`restoreLoadedDepth`). It runs whenever a row is refreshed: after a return from a title with content changes, after a save, a display-setting change, or a watch-state change with "hide watched" on. It concatenates pages without de-duplication, and it advances its page cursor by one even when the interactor already read further ahead. With "show anime" off or "hide watched" on, the interactor regularly answers one logical page from two or more server pages, so the restore asks for a server page it already holds and that page's titles appear twice, about one page (25 cards by API default) apart. The restored list then reaches the paginator as a single `Replace`, and `Replace` does not de-duplicate.

## Current state in code

Root cause (deterministic overlap):

| Where | What it does |
|---|---|
| `app/src/main/java/com/kino/puber/ui/feature/contentlist/vm/ContentListPagingVM.kt:104-108` | `refreshFirstPage()` sets `reloadKeepsDepth = true`, so the next first page restores `currentPage` pages. |
| `ContentListPagingVM.kt:165-171` | `publishFirstPage` calls `restoreLoadedDepth` when depth > 1. |
| `ContentListPagingVM.kt:204-206` | Cursor `page` starts at `firstPage.pagination.current` and is incremented by 1 per request, not set from the response (the comment at :202-203 says this is deliberate, to bound the request count). |
| `ContentListPagingVM.kt:208,215` | `interactor.loadPage(config, page)` then `items += next.items`, with no id de-duplication. |
| `ContentListPagingVM.kt:285-286` | The restored list is published through `replace(...)`. |
| `app/src/main/java/com/kino/puber/domain/interactor/contentlist/ContentListInteractor.kt:132-136` | No local filter (anime filter no-op and hide-watched off): one server page per call, `current == requested`. |
| `ContentListInteractor.kt:139-167` | With a filter it keeps fetching server pages until the page is full (`:155`), the server is exhausted, or the hide-watched budget of 5 pages is spent (`:144`). It returns **all** visible items from every page read, with `pagination.current` = the last server page read (`:162`). |
| `app/src/main/java/com/kino/puber/core/paginator/Paginator.kt:214-227` | `Replace` stores `action.items` as is, with no comparator. |

Example trace: filter on, `perpage` 25. The walk loads 1 (server 1+2, current 2), then 3 (server 3+4, current 4). A refresh restores depth 4: page 1 gives server 1+2 (cursor 2), cursor 3 gives server 3+4 (current 4), cursor 4 runs because `4 < total` and gives server 4+5. Server page 4 is in the row twice.

Second, non-deterministic source on the same path: with no filter, the restore still concatenates pages read one after another. If the server order of `popular`/`hot` moves between those reads, a title on a page boundary shows up twice, next to itself (`unverified`: whether and how often KinoPub reorders these lists).

Paths that are already protected:

| Where | Why it does not duplicate |
|---|---|
| `Paginator.kt:370-389` | `NextPage` in `Data`/`LoadingNext` replaces an item in place when the comparator matches, so walk steps de-duplicate, including within the incoming page. |
| `app/src/main/java/com/kino/puber/ui/feature/contentlist/ContentListScreen.kt:71`, `ui/feature/showall/ShowAllScreen.kt:42` | Comparator is `old.id == new.id`. |
| `Paginator.kt:392` | `NextPage` in any other state replaces the list with the page alone. This loses items but does not duplicate them. |
| `app/src/main/java/com/kino/puber/domain/interactor/contentlist/FreshSectionPager.kt:265` | Cartoon/anime "fresh" rows filter through `seenIds`, and logical pages are always `current == requested`. |
| `app/src/main/java/com/kino/puber/domain/interactor/home/HomeInteractor.kt:78-82,173-211` | Home "Popular series" is one page. The filtered path uses `linkedMapOf` by id, and Home rows have no load-more. |

Triggers and context:

| Where | What |
|---|---|
| `app/src/main/java/com/kino/puber/ui/feature/contentlist/vm/SectionVM.kt:56-87` | Refresh triggers: `SectionRefresh.All`, `ForItem`, `displaySettingsChanges`, watch-state change with hide-watched on. |
| `SectionVM.kt:140` | Save/unsave triggers `ForItem`. |
| `app/src/main/java/com/kino/puber/ui/feature/contentlist/vm/ContentListVM.kt:157-159` | A return from Details/ShowAll with changes calls `requestRefresh()`. |
| `app/src/main/java/com/kino/puber/ui/feature/showall/vm/ShowAllVM.kt:56-77,114,165` | ShowAll extends the same `ContentListPagingVM`, so the "show all" grid has the same bug. |
| `app/src/main/java/com/kino/puber/data/cache/CachedFeed.kt:510` | A forced read marks the stored page stale, so the restore runs on a fresh page 1 (`ContentListPagingVM.kt:127`). Page age is not the cause. |
| `app/src/main/java/com/kino/puber/ui/feature/contentlist/model/TabTypeConfig.kt:25-32,46-63` | Series tab: `popular_serial`, shortcut `popular`, `AnimeFilterMode.FollowPreference`. |
| `app/src/main/java/com/kino/puber/data/preferences/NavigationPreferencesRepository.kt:26-27` | Defaults are `showAnime = true` and `hideWatched = false`. With the defaults, only the drift source applies. The deterministic overlap needs "show anime" off or "hide watched" on. |
| `app/src/main/java/com/kino/puber/ui/feature/contentlist/content/SectionRow.kt:151`, `ui/feature/showall/content/ShowAllScreenContent.kt:114` | Lazy keys are `item.id`. A duplicate id composed twice at once throws "Key ... was already used". Overlap duplicates are about a page apart and are not composed together. Drift duplicates are adjacent and can crash (`unverified` on Compose BOM 2026.08.00, `gradle/libs.versions.toml:7`). |

Tests:

| Where | Coverage |
|---|---|
| `app/src/test/kotlin/com/kino/puber/ui/feature/contentlist/vm/SectionVMRefreshDepthTest.kt:38-39` (and the three cases after it) | Only pages with `current == requested` and disjoint ids. No case where a page jumps ahead or ids overlap. |
| `PaginatorReducerTest` | Does not cover page merges (audit, see Related work). |
| `app/src/test/kotlin/com/kino/puber/domain/interactor/contentlist/ContentListInteractorTest.kt` | Covers the interactor walk. |

## Related work

| Item | Relation |
|---|---|
| Run `paginator-hardening` (idea, M) | Keyed O(n) merge plus characterization tests for `Paginator` (audit PERF-2, BP-6). It touches the same merge code but not `restoreLoadedDepth`. Overlaps if the fix adds de-duplication to `Replace`. |
| Run `content-list-swr-cache` (done); `docs/superpowers/specs/2026-08-17-content-list-swr-cache-design.md`, `docs/superpowers/plans/2026-08-17-content-list-swr-cache.md` | Introduced the cached first page and the two publications per load. |
| Commits `6b75a325`, `81587a1a`, `1c5591a8` (2026-08-17) | First page from cache, walk generation. |
| Commit `1674417d` (2026-08-25) | Introduced `restoreLoadedDepth` / `MAX_RESTORED_PAGES` (`git log -S`). |
| Runs `persistent-content-cache`, `catalogue-single-carousel` (done), `cached-feed-key-maps` (idea) | Same area. None mentions duplicates. |
| `docs/superpowers/evidence/2026-10-12-code-quality-audit-report.md:109,123` | PERF-2 / BP-6: page merge cost and the missing merge tests. |

Duplicate: none.

## External findings

| Finding | Source |
|---|---|
| `GET /v1/items/{fresh,hot,popular}` take `type`, `page` (documented default 0) and `perpage` (default 25). The docs do not say how ranking is computed or whether it changes between requests. `/v1/items` returns `pagination {total, current, perpage}` and defaults to sort `updated-`. | https://kinoapi.com/api_video.html (fetched 2026-11-11, no version stated) |
| Compose Lazy layouts throw `IllegalArgumentException: Key ... was already used` for duplicate keys. Reports tie it to duplicates composed at the same time during paging (secondary sources). | https://slack-chats.kotlinlang.org/t/2761323/key-was-already-used-if-you-are-using-lazycolumn-row-please- ; https://pluu.github.io/blog/android/2024/11/30/LazyList |

## Options

| Option | Change | Trade-offs | Size |
|---|---|---|---|
| **A (recommended)** | In `restoreLoadedDepth`, take the next request's page from `deepest.pagination.current + 1`, stop when `deepest.pagination.current >= restoreTo`, keep a separate request counter as the ceiling, and de-duplicate the accumulated items by id (first occurrence wins). Add `SectionVMRefreshDepthTest` cases for a page that jumps ahead and for overlapping ids. | Fixes both sources at the only unprotected path, for the section rows and ShowAll. Fewer requests than today. Leaves the `Paginator.Replace` contract alone. | S |
| B | A, plus id de-duplication inside `Paginator` `Replace` (and the `NextPage` `else` branch) for every paged list. | Safety net for History, bookmarks and other `Replace` users. Changes a shared reducer that has no merge tests, so it overlaps `paginator-hardening` and needs device checks on every paged list. | M |
| C | `distinctBy { it.id }` in `ContentListPagingVM.mapItems` only. | Hides the symptom and prevents the key crash, but the restore still re-reads pages, `currentPage` and focus depth stay wrong, and a mapping step takes on list identity. | S |

## Risks

- **Focus restore:** the reload exists so the focused card stays in the list (`ContentListPagingVM.kt:97-102`). Removing duplicates shifts indexes. Focus is keyed by id (`rememberReconciledItemFocus`, `SectionRow.kt:124`), so it should hold, but this needs a device check on the Series tab after a return from Details.
- **Request ceiling:** the comment at `ContentListPagingVM.kt:202-203` requires the request count to stay bounded by `MAX_RESTORED_PAGES` and not be driven by the response. The new cursor must keep that ceiling.
- **Hide-watched budget:** a `loadPage` can return an empty page with `current` several pages ahead (`ContentListInteractor.kt:144,160-162`). The empty-chain stop (`ContentListPagingVM.kt:217-220`) must still work with the new cursor.
- **Device verification needed:** ViewModel state and TV focus. Reproduce with "show anime" off: scroll the Series tab Popular row past 2 or more pages, open a title, change something (watch or save), return, and scroll right.

## Open questions

| ID | Question | Recommended answer |
|---|---|---|
| Q1 | Fix only the catalogue restore path (A), or also add de-duplication to `Paginator.Replace` for every paged list (B)? | A. Leave the reducer-wide guard to `paginator-hardening`, which already owns merge tests for `Paginator`. |
| Q2 | "Top-series row" is assumed to be the Series tab "Popular" row (paged), not Home "Popular series" (one page, no paging). Correct? | Yes. Device repro uses that row with "show anime" off or "hide watched" on. |

## Sources

- Files: those cited above, plus `docs/architecture/paging-and-filters.md`, `app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:248-284`, `app/src/main/java/com/kino/puber/data/cache/ContentCacheRepository.kt:132-147`, `app/src/main/java/com/kino/puber/ui/feature/contentlist/vm/ContentListRefreshCoordinator.kt`, `app/src/main/java/com/kino/puber/core/ui/model/VideoItemUIMapper.kt:25-27`, `app/src/main/java/com/kino/puber/ui/feature/home/component/HomeScreenContent.kt:450`.
- Commits: `1674417d`, `6b75a325`, `81587a1a`, `1c5591a8`, `a1a51cca`.
- Runs: `paginator-hardening`, `content-list-swr-cache`, `persistent-content-cache`, `catalogue-single-carousel`, `cached-feed-key-maps`, `showall-focus-restore-hardening`.
- URLs: https://kinoapi.com/api_video.html, https://kinoapi.com/, https://slack-chats.kotlinlang.org/t/2761323/key-was-already-used-if-you-are-using-lazycolumn-row-please-, https://pluu.github.io/blog/android/2024/11/30/LazyList
