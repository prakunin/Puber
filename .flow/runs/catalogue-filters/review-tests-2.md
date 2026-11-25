LENS: tests  ROUND: 2  BASE: 027ed8d3
### F1 [major] No test or manual check would catch a regression of the headerHoldsFocus rail fix: railOpenedFromFilters_closesWithFocusBackOnFilters was green on base; Manual 2 never closes the rail. Fix: extend Manual 2 (close the rail with Right/Back, expect Фильтры); spec row P0 guard with Manual 2 as carrier.
### F2 [minor] Spec test table out of date: filterChange_marksTheNewListWithAnotherQueryGeneration listed P1 red (green on base); resetWithACachedBasePage_movesFocusFromFiltersToTheFirstCard, followPreference_whenAnimeIsShownAndWatchedHidden_keepsUnwatchedAnime, railOpenedFromFilters_closesWithFocusBackOnFilters missing.
Suite: touched unit tests green; itest-r2 XML 14/9/8.
### Unproven
- queryGeneration read at render time, not publish time.
