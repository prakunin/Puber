LENS: edge  ROUND: 3  BASE: f351d988  RANGE: 8606de32..2e8d4be5

No findings.

Read at 2e8d4be5: applySubtitleSelection, saveTrackPreferences, resetMediaSession, onTracksUpdated, restoreSubtitlePreference, selectQuality, buffer/Fast DNS rebuilds, `toSubtitlePreference()`. Every save path writes the right value:
- Explicit pick: snapshot from the row as picked, saved.
- Explicit Off: `toSubtitlePreference()` returns NONE for an Off row (SubtitlePreferenceKey.kt:35-37); snapshot and save are NONE.
- Audio pick after a pick: snapshot saved; a later same-language remap of `intendedSubtitleRow` (PlayerVM.kt:552-557) no longer reaches the save (codex round-2 finding fixed).
- Audio pick without a pick: stored value written back unchanged (AC-16).
- Restore applying a substitute: `applySubtitleSelection(persist = false)` (:623) never changes the snapshot.
- Rebuild with a kept pick (:953/:965): only `subtitleRestoredForCurrentMedia` reset; before tracks arrive the stored value (= the pick) is written; after restore the snapshot; a substitute on a stream lacking the row is not saved.
- AC-11 (b): `selectQuality` resets neither flag; Off shown, intent kept; a later audio pick saves the snapshot, not Off.
- Next media: `resetMediaSession` (:1010-1013) resets the snapshot to NONE with `subtitlePickedThisMedia`; both callers (:1022, :1432) go through it; covered by `pickInFirstMedia_nextMediaSubstituteRestore_audioChangeKeepsStoredPreference`.
