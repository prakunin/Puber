LENS: edge  ROUND: 2  BASE: 90e69aff

No findings.

Fix round 1f6b03b0..e286d189 (triage rows 2-5) checked against the edge boundaries and .flow/roles/reviewer.md also_consider:
- Cache key (row 2), ContentListInteractor.kt:123-143: with country, year and finished null the key is the same 11 fields in the same order as at 90e69aff, so pre-upgrade pages still hit. A filtered key always carries three extra parts, so it cannot match an unfiltered key; unset fields are "null" in fixed positions, so two filters cannot share a key. Test firstPageCache_withNoBrowseFilter_keepsTheKeyShapeOfBeforeTheFilters checks the pre-upgrade key.
- Year page (row 3), ShowAllFilterPanel.kt:249-273: exactly one row gets first focus in every state (no year: «Любой год»; preset: that row; off-list: the extra row). No duplicate row. Picking another value removes the extra row; plain Column + own `if` block leaves following rows intact; focus stays on the pressed row. The extra row is never in a lazy list.
- Status (row 4): StatusChosen(false) on a series grid now covered (config and draft).
- Country page (row 5): the new assertion uses the merged tree and can fail on its own.

Neither instrumented test (rows 3 and 5) has run on a device; yearPage_aStoredYearOutsideThePresets_isTheCheckedRowAndTakesFocus is compiled only.
