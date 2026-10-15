LENS: tests  ROUND: 3  BASE: f351d988  RANGE: 8606de32..2e8d4be5

No findings. Round-2 F1 closed.
- Suite at 2e8d4be5: BUILD SUCCESSFUL; JUnit XML 1637 tests, 0 skipped/failures/errors; both new tests present.
- Removing the per-media reset of `subtitlePickedThisMedia` (PlayerVM.kt:1011) is caught by `pickInFirstMedia_nextMediaSubstituteRestore_audioChangeKeepsStoredPreference` (the save would be NONE or episode 1's pick, not the stored `("rus", "subtitle:Gone", false, "Russian Old")`).
- Saving the remapped intent instead of the snapshot (:1872) is caught by `pickThenRemapToSameLanguageRow_audioChangeSavesThePick` (would save "English B" instead of "English A").
- Not clearing the snapshot on Off (:895) is caught by `explicitOff_savesNoneAndClearsAllValues` and `savedSubtitleUnresolved_explicitOffAfterUnresolved_clearsStored`.
Mutations reasoned from code, not run (read-only review).
