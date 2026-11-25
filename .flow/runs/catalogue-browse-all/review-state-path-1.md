LENS: state-path  ROUND: 1  BASE: e4d75215
### F1 [minor] On a cold tab whose first shelf is Empty, launch focus lands on the browse entry instead of the first card
- Where: ContentListScreenContent.kt:465 (canFocus = sectionState !is Loading), BrowseAllEntry.kt:85, ContentListScreenContent.kt:119-122
- Problem: with section 0 Empty the entry is the only focusable node there; the launch Enter search picks it; browseEntryFocused = true makes isTargetRow false for row 1, which was the onRowEmpty target.
- Failure scenario: hide watched on, «Новинки» fully watched -> tab opens on «Все фильмы», empty panel.
- Fix: canFocus = sectionState !is Loading && launchFocusSettled (rememberSaveable flag set on the first focus inside the list).

Dropped after trace: AC-7 return to the entry works in the current code (needs device confirmation); Retry on Error section 0 no worse than base; trailer stop on entry focus; double OK predates the diff.
