LENS: edge  ROUND: 1  BASE: 90e69aff

No findings.

I checked each of the seven `also_consider` items in `/home/dev/Puber/.flow/roles/reviewer.md` against the diff, and walked every changed branch. Each boundary below has handling in the code:

- Old store entries: an entry written by the previous version has none of the new fields, so they read as null. A year with a missing or inverted bound reads as null. `finished` is kept only when it is `true`.
- Status on the wrong grid: `StatusChosen` is ignored unless the grid supports status. A stored `finished` is per grid, so it can only come from a series grid.
- Grids that cannot be filtered: `all_cartoon` and `all_anime` have no browse entry, so they never show the panel. `all_4k` has no `type`, so it never shows «Статус».
- Countries list loading, failing and retrying: a second load is not started while one runs. Opening the panel reloads after a failure. Retry is the first focus while the list has failed. When the state changes, the panel's focus effect runs again, so focus goes back to «Все страны» when the Retry row goes away.
- Country page focus: a chosen country that is not in the loaded list opens on «Все страны». Exactly one row holds the first-focus requester in every state. The list scrolls to the selected row when it finishes loading.
- Year stored last year: a range that is no longer in the presets reads correctly as «first–last», and the page opens on «Любой год».
- First-page cache key: it now includes country, year and finished. Keys cannot collide, because `year` contains `-` and never `_`, the separator. Existing cached first pages are simply missed once after the upgrade.
- `getItems` callers: the only caller passes named arguments, so the new parameters cannot shift into the wrong slot.

### Unproven
- `ContentListInteractor.kt:156` (`fetchFilteredPage`): with anime hidden and «Япония» picked, the anime filter walks pages with no limit until it has 50 non-anime items or the server runs out. The first page could take many sequential requests. It becomes real if the box shows country=Japan totals with `isAnime` counts.
- `KinoPubApiClient.kt:648` (`items.orEmpty()`): a `{status}` body with no `items` would become a successful empty list, cached for 30 minutes, with only «Все страны» shown and no error. It becomes real with any recorded `countries` response that has no `items`.
- `ShowAllVM.kt:112` (`Year.now()` at construction): a box that boots with an unsynced clock would get presets of 1970, 1969 and «до 1980» only. It becomes real with evidence that the box ever runs the app before its clock syncs.
- `ShowAllFilterLabels.kt` (`%d` in `show_all_filters_year_decade` and `_range` against `range.first.toString()`): under a locale with non-ASCII digits, decade labels would use local digits while single years stay ASCII. It becomes real if the app has to support a locale other than ru/en.
