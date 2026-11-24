LENS: state-path  ROUND: 5  BASE: b23f7a51
No findings.
Traced: first load (replayed Empty -> Loading -> real Empty), early Refresh/remap, return to screen, hide-watched empty pages, startup telemetry (only Content counts), other screens (SectionState only in contentlist), thread visibility (StateFlow update after each write).
Leftover: KDoc of rememberBrowseEntryFocus still describes the replayed Empty; the delay is now only a fallback.
