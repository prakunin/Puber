LENS: edge  ROUND: 1  BASE: 901d820d

No findings.

### Unproven
- `app/src/main/java/com/kino/puber/ui/feature/contentlist/vm/ContentListPagingVM.kt:209` A first page that reports `pagination.current = 0`. The `requests` counter keeps the restore to 9 calls, so it ends at page 9 and the walk carries on from page 10. That is bounded and correct either way, but nothing shows KinoPub ever numbers pages from 0. Evidence that would make it real: a recorded `getItems` or `getItemsByShortcut` reply with `current = 0` for a non-empty list.
- `ContentListPagingVM.kt:225`/`:232` A reply that reports a lower `current` than the page requested. The `maxOf` keeps the cursor moving forward, but the published `deepest.pagination` still carries the lower `current`, so the walk after the restore would re-read pages. In practice both providers rule this out: `fetchFilteredPage` (`ContentListInteractor.kt:147`) and `FreshSectionPager.loadPage` (`FreshSectionPager.kt:56`) answer with the requested page. Evidence that would make it real: a third `loadPage` implementation that does not.
- `ContentListPagingVM.kt:198`/`:210` `restoreTo` is measured in server pages, while `MAX_RESTORED_PAGES` reads as logical pages. A filtered row walked to a server page above 10 (anime off, or hide-watched with read-ahead) comes back at about 10 server pages, which can be only 2 to 5 logical pages. A card focused beyond that is not in the reloaded row. This was already true on base: base re-read the same server pages and reached about the same unique depth, so the diff does not make it worse. Evidence that would make it real: AC-7 run on a row walked well past server page 10, with focus lost on return.

Boundaries I checked that are handled:
- **FreshSectionPager cursor:** it answers `current == requested`, so `maxOf` gives the same requests as base.
- **Cancellation:** it is rethrown.
- **Failure:** nothing is published (AC-6).
- **Empty chain:** it counts raw `next.items` before dedup, and a jump can only end the loop sooner.
- **`total` ≤ `current`:** the loop guard ends the restore.
- **`currentPage` after restore:** set from `deepest.pagination`, the last server page read.
- **Focus id:** first-occurrence dedup keeps every id. The grid restores focus by id with an index fallback (`ShowAllScreenContent.kt:180-210`), and `SectionRow` focuses by id.
- **ShowAll:** it inherits the change without an override.
