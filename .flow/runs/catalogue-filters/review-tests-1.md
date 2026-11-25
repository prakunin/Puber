LENS: tests  ROUND: 1  BASE: b7a7d99e
Suite: check.full --rerun-tasks green at 027ed8d3 (2537 unit tests).
### F1 [major] P0 newListLanding_whilePanelOpen_keepsFocusInThePanel cannot fail without ContentFocusGate (no LocalScreenKey in the test host); no device check covers it. Fix: provide LocalScreenKey in the host and re-run the mutation, or add a Manual 3 step (apply, OK on Фильтры at once, list lands, focus stays in the panel).
### F2 [major] Four P0 red tests were green on stub/base (applyTo_defaultFilter_returnsTheBaseConfig, isFilterable_falseForShelvesAnd4kTypeGrids, filterPanelClosed_withoutAChange_doesNotRepage, observeFirstPage_afterAFilteredRead_theUnfilteredPageStillComesFromTheStore). Fix: mark P0 guard, or record a red run against the naive mutation.
### F3 [minor] AC-8 first-page and walk-step tests pass with the generation checks removed (cancellation does the work). Fix: correct the spec rows; optional direct test.
### F4 [minor] Manual 1 (Все tile route), Manual 5 player leg, Manual 6 Сброс from filtered-empty not run; repeat Manual 6 on Movies/Series.
### Unproven
- TvSidePanel.kt player panel after the lift unchecked on device.
