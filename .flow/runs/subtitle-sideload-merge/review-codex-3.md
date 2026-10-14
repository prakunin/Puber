CODEX: ran: review, adversarial (model gpt-6-astra)   RANGE: 7ca85b93..f4b311e0
CONFIRMED:
- [minor] app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:567-572 (with 599-615 and 868-880) - A same-media rebuild (Fast DNS toggle or buffer preset change) clears the retained subtitle intent, only when the intent cannot be found in the current rows. The rebuild resets `subtitleRestoredForCurrentMedia`, so the next report calls `restoreSubtitlePreference()`. Scenario:
  1. The viewer picks Russian, and it is persisted.
  2. A stream that offers only English is reported. The picker shows Off and `intendedSubtitleRow` stays Russian (D11 amended, D19).
  3. The viewer toggles Fast DNS, or changes the buffer preset.
  4. The English-only report arrives; `restoreSubtitlePreference()` cannot resolve Russian and falls back to `content.selectedSubtitleIndex`, the Off row.
  5. `applySubtitleSelection(Off, persist=false)` sets `intendedSubtitleRow = null`.
  6. A later report that contains Russian no longer restores it.
  7. A later explicit audio pick calls `saveTrackPreferences()` and can persist a null subtitle preference.
  The comment at line 602 ("on a rebuild the row is the viewer's live pick, so nothing changes") does not hold for the intent. Fix: on the fallback branch, re-send Off to the controller without touching `intendedSubtitleRow` and without the persist side effect. Add a regression test.
REJECTED:
- Plain review pass: no actionable defects.
VERDICT: changes needed (one minor, narrow edge case)
