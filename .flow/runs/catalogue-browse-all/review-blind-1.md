LENS: blind  ROUND: 1  BASE: e4d75215

### F1 [minor] The browse entry reports focus as restored even when it never got focus
- Where: BrowseAllEntry.kt:62-74; anchorFocusRestored() runs whether landed is true or false (rows notify only after real focus, FocusTargetState.kt:177-180; FlowComponent.kt:419-424).
- Fix: call anchorFocusRestored() only when landed.

### F2 [minor] The entry stays the focus owner when focus moves to a Retry button or a loading row, then pulls focus back up and blocks the cards' initial focus
- Where: ContentListScreenContent.kt:116-122, 366, 447, 463-466
- Problem: browseEntryFocused clears only via onSectionFocused (Content cards); initialFocusEnabled false for every section; the entry's LaunchedEffect re-runs on canFocus / contentFocusActive changes.
- Failure scenario: section 0 errors, entry -> Down -> Retry -> Loading -> Content: focus jumps back to the entry; Down into a loading section 1: its cards never get initial focus.
- Fix: clear browseEntryFocused when focus enters any section row (onFocusChanged(true)), or gate initialFocusEnabled for section 0 only and run the entry restore only on a latched handoff or return.

### F3 [nit] The owner-keeps-focus test hard-codes initialFocusEnabled = false

### Unproven
- TabTypeConfig.kt:13-24 Cartoons and Anime get no entry.
- ContentListScreenContent.kt:547-555 30 dp band only fits at default font scale.
