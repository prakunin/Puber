LENS: edge  ROUND: 1  BASE: e4d75215
### F1 [major] Back from the browse grid still has no device check after the fix (AC-7)
- Where: BrowseAllEntry.kt:62-77, SectionRow.kt:96
- Problem: fixes 0a65717f/c484b503 have no device run; the new instrumented test passes isTargetRow/initialFocusEnabled by hand, with no rail, root anchor restore or navigation.
- Fix: run M-3 again on a build at or after c484b503 (cold/warm, with/without Right); if it fails, a test through the root focus restore.

### F2 [minor] A cold tab opens on the entry when section 0 is in Error or Empty
- Where: ContentListScreenContent.kt:460-466 (canFocus = sectionState !is Loading)
- Problem: with section 0 Error/Empty, launch focus enters the entry; browseEntryFocused then blocks isTargetRow and the onRowEmpty handoff.
- Failure scenario: network error on «Новинки» -> tab opens on the entry, not Retry; fully watched «Новинки» -> tab opens on the entry, not «Популярные» card 1.
- Fix: canFocus = sectionState is Content || browseEntryFocused.

### Unproven
- BrowseAllEntry.kt:75 anchorFocusRestored() runs when all requestFocus attempts fail.
- ContentListScreenContent.kt:463 section 0 back to Loading while the entry holds focus drops focus.
