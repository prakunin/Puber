LENS: edge  ROUND: 4  BASE: 109b7b0b
No findings.
Checked: replayed Empty cancelled by Loading; Loading -> all-Empty gives the entry focus; rail open; hero present/loading with all Empty; Content -> all-Empty later; Saver; Error mixes; tests drive real rememberBrowseEntryFocus.
### Unproven
- BrowseEntryFocus.kt:96-100 a >=100 ms main-thread stall between replayed Empty and Loading could still settle (needs a trace).
- BrowseEntryFocus.kt:73-76 all sections turning Empty while the content-list context menu is open.
