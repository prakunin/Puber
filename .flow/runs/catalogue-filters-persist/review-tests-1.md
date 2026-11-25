check.full green at c205c62e (unit tests and detekt up-to-date from 14278faa; production unchanged since). Every P0 row maps to a same-named test.

LENS: tests  ROUND: 1  BASE: ef5987ec
### F1 [minor] AC-5 is tested on a filter applied in the same session, never on a restored one
- Where: app/src/test/kotlin/com/kino/puber/ui/feature/showall/vm/ShowAllVMTest.kt (filterReset_removesTheStoredFilterAndTheReopenedGridReadsCached)
- Problem: AC-5 starts from "a restored filter". The test applies the filter through the panel on a VM built with an empty store, so `restored` is `ContentFilter()`, its super config is the base config and `initialCacheable` is true. The path where `resetFilter` runs on a VM built from a stored filter is never exercised; there `resetFilter` must re-page `base` with `cacheable = true` and clear the entry.
- Failure scenario: someone points `base` at the filtered initial config. After a restart, «Сброс» on a restored grid re-pages the filtered query with `cacheable = true` (catalogue-filters AC-7 cache pollution). Every unit test stays green.
- Fix: write `dramaByYear` to the repository, build a `filterRig` on it, send `FilterReset`, assert `rig.calls` ends with `FirstPageCall(allMovie, cacheable = true)` and `filter_all_movie` is gone.

### F2 [minor] The `animeFilterMode == FollowPreference` condition of the anime skip is not tested
- Where: ShowAllVM.kt (restoreFilter)
- Problem: all three AC-8 tests run on `allMovie` (FollowPreference). `all_4k` is filterable with mode `None`. Deleting the mode condition passes the whole suite.
- Failure scenario: «Аниме» stored on `all_4k` with show-anime off is silently dropped on open although the interactor does not filter anime there.
- Fix: add `restoredAnime_onANonFollowPreferenceGrid_isApplied` on `all4k`, assert first call's `config.genre == "25"`.

### F3 [minor] The instrumented test's final assertion cannot fail; only the Loading-phase assertion discriminates
- Where: app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllFilterFocusTest.kt (coldOpenWithRestoredFilter_focusesTheFirstCardNotReset)
- Problem: only the `assertIsNotFocused` on «Сброс» during Loading guards the gate; the tail reads as if it proves AC-1 focus, and no comment marks which assertion is the guard.
- Fix: comment the Loading-phase assertion as the guard; optionally rename the test.

### Unproven
- ShowAllVM.kt onStart displaySettingsChanges / RetryClicked on a restored grid: no test covers refresh on a restored grid staying `cacheable = false`.
