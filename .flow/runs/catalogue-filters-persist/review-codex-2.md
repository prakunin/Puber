CODEX: ran: review, adversarial

CONFIRMED:
- none

REJECTED:
- [medium] `ShowAllFilterFocusTest.kt:156-169`: the panel-apply-to-empty test waits for Filters to regain focus before applying, so it misses a fast empty result during the close; round-two focus tests only compiled. Not a shipped-code defect: `headerHoldsFocus` latches true the first time the header has focus (`ShowAllScreenContent.kt:113`); the panel opens only from «Фильтры», so the latch is true before any empty result; `ShowAllEmpty` never requests Reset on the panel path whatever the close timing. No failing sequence given. Device run pending is stated in the plan. Rejected as `unproven`.

Plain review: no defects; restored filter applied before the first paging request, filtered requests bypass the first-page cache, reset clears the entry. Incremental diff c205c62e..18d72ea4 matches the plan's round 1 fixes table.

Note for the conductor: qa-device should run `coldOpenWithRestoredFilter_thatPagesEmpty_focusesTheEmptyStateReset` and `panelApplyThatPagesEmpty_keepsFocusOnFiltersNotReset` on the round-two APK.

VERDICT: clean
