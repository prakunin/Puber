ANTIGRAVITY: ran
CONFIRMED:
- none
REJECTED:
- agy reported no defects in 18d72ea4..b0cee092. Reviewer check of the F1 fix at `ShowAllScreenContent.kt:190-197` holds: `handoffExpectsNewContent` is true only while `handoffKind == TabPick` (`ModalNavigationDrawer.kt`), so the outgoing tab no longer requests «Сброс» during a rail tab pick; the effect is keyed on the flag; after settle the outgoing screen is torn down; the cold-open `Initial` handoff does not set the flag, so the empty-state focus still happens on first open; matches `ContentFocusHandoffEffect`.
- The rename (`ShowAllFilterFocusTest.kt:157`) changes only the method name.

VERDICT: clean
