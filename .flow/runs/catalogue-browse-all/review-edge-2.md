LENS: edge  ROUND: 2  BASE: 6c35732d
No findings.
Checked: Empty/Error section 0 launch, ownership clearing from any row content (not shimmer), return from the grid via the entry (Saver keeps isOwner), return via a shelf card, rail handoff while owner, anchorFocusRestored only when landed, shared gating in the scenes.
### Unproven
- BrowseEntryFocus.kt:72-77 Up within ~100 ms of first card focus does not reach the entry.
- BrowseEntryFocus.kt:51-55 + SectionRow.kt:80-84 shimmer -> card focus move without hasFocus toggling would leave firstRowLanded unset (check M-1 on an uncached tab).
- FocusOnLaunchRequester.kt:72-85 launch request held while the rail is open could land on the entry if it fires >100 ms after a row focus.
