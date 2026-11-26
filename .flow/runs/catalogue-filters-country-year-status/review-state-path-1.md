LENS: state-path  ROUND: 1  BASE: 90e69aff

No findings.

I traced every action the diff touches through `ShowAllVM` and `ShowAllFilterPanel`; none of the sequences ends in a wrong state or loses focus.

- Panel close: `CountryChosen`, `YearChosen` and `StatusChosen` (blocked unless `statusAvailable`) change only the draft. Closing the panel copies the draft into `applied` and the summary. When the filter changed, it writes the store and starts one uncached `changeQuery`. Nothing that runs later writes the draft or `applied` back. `Country` holds only `id` and `title`, so a stored country equals the same country from the list, and closing the panel without a change never reloads the grid.
- Store and cold start: `restoreFilter` runs before the super call, so the first page uses `restored.applyTo(config)` with `initialCacheable = !restored.isActive`. `ContentFilter.isActive` already counts country, year and finished, so a restored filter bypasses the cache. The header summary is right from the first frame through `withApplied(restored)`.
- Reset: it clears `applied` and the draft, removes the store entry and calls `changeQuery(base, cacheable = true)`. `applyTo` only copies the three new fields while the filter is active, so the base query carries none of them.
- Countries loading: `countriesJob` blocks a second load, so pressing Retry twice starts one read. A load that finishes after the panel is closed only sets `rawCountries` and the list state, and the next open reuses them. A load that fails while the panel is closed is retried on the next open.
- Cache: `cacheKey` includes all three new fields. `fetchPage` passes them by name, and grids that use a shortcut keep their own path.
- Focus: at most one row holds `firstFocus` on each page. On the country page with a country chosen and the list still loading, focus goes to «Все страны». When the list arrives, `scrollToItem` runs and, after 100 ms, the panel's effect (keyed on the list state) moves focus to the chosen row. Back from Country, Year or Status returns focus to that door. On Retry, focus leaves the removed Retry row until the effect, keyed on the change to Loading, puts it on «Все страны» 100 ms later. The genre page already behaves the same way.

Files read: ShowAllVM.kt, ShowAllFilterPanel.kt, ShowAllScreenContent.kt, ContentFilterQuery.kt (worktree).
