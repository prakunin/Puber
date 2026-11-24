LENS: edge  ROUND: 5  BASE: b23f7a51
No findings.
### Unproven
- SectionVM.kt:54 storeLeftItsInitialState is a plain var written on the store thread and read on main in render replays; a stale false could keep an all-Empty row on a shimmer until the next render (same pattern as Paginator.Store.state).
Regression check: one SectionVM caller (ContentListScreen.kt:71); SectionState readers only see a shimmer instead of a hidden row on the replayed frame; init always restarts so no row stays Loading; Show all VM unaffected.
