LENS: state-path  ROUND: 1  BASE: cb7fcd45

### F1 [minor] An API subtitle row whose language is "und" can no longer reach its own track: the identity matchers never see it, so picking it leaves subtitles off
- Where: `/home/dev/orca/workspaces/Puber/player-audio-und-label/app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerTextTracks.kt:88` (candidates now carry `language = null` for "und"), together with `/home/dev/orca/workspaces/Puber/player-audio-und-label/app/src/main/java/com/kino/puber/ui/feature/player/vm/SubtitleTrackSelector.kt:55-56`. `SubtitleTrackSelector.kt` was not changed by the diff.
- Problem: the diff takes "und" out of one side only, the player candidates (`toPlayerTextTracks`) and the player-built rows. An API row is still built from `sub.lang` as it is (`PlayerUIMapper.kt:45`), so it keeps "und". In `isSemanticallyCompatibleWith`, `track.language` is "und", which is not blank, so a candidate passes only if `sameSubtitleLanguage(candidate.language, "und")` is true. That is now always false: a null candidate language makes the `?.let` false, and a non-null one fails `knownLanguageOrNull`. So `compatibleCandidates` is empty, and `matchByFormatId`, `matchBySourceIdentity` and `matchByTrackGroupId` all run on an empty list. The spec's Risks row says restore "then relies on identity matchers". It cannot: the identity matchers only see candidates that already passed the semantic filter. Before the change the side-load's own Format carried "und", `sameSubtitleLanguage("und","und")` was true, and the stable-key id matched.
- Failure scenario: a progressive stream, so `SubtitleReportKind.API_ROWS` and the picker rows are the API rows. KinoPub returns a subtitle with `lang = "und"`. The user picks that row, the call chain sets no override, and `setPreferredTextLanguage("und")` does not select a text track either. Media3 turns "und" into null, and with `selectUndeterminedTextLanguage = false` an undetermined track scores 0. Subtitles stay off while the picker shows the row as selected. The stored preference still restores the row through `exactSubtitleUrlMatch`, which runs over every track, so the same dead pick happens again on the next start. Rated minor because the spec (Q3) leaves it unverified whether the KinoPub API ever returns "und".
- Fix: make the selector treat an undetermined row language like a blank one. In `isSemanticallyCompatibleWith`, use `knownLanguageOrNull(track.language) == null ||` in place of `track.language.isBlank() ||`, and pass `knownLanguageOrNull(track.language).orEmpty()` to `setPreferredTextLanguage` at `PlaybackController.kt:905`. Another option is to normalise `sub.lang` through `knownLanguageOrNull` in `PlayerUIMapper.mapSubtitleTracks`. Add a selector test: a row with "und" and a url, and a candidate with `language = null` whose `formatId` is that row's stable key, must return that candidate.
- Trace:
  1. `SelectSubtitle(i)` → `PlayerVM.applySubtitleSelection`, which writes `selectedSubtitleIndex = i`, `intendedSubtitleRow = row` and `pickedSubtitlePreference = {lang "und", url, ...}`, then calls `saveTrackPreferences`.
  2. → `PlaybackController.selectSubtitle(row)` → `applySubtitleTrackSelection`.
  3. → `toPlayerTextTracks()`: the side-load candidate has `language = null` (new).
  4. → `select`: `compatibleCandidates` is empty, so the result is null.
  5. → the builder clears the text overrides and calls `setPreferredTextLanguage("und")` with no override, so no text renders.
  6. Next start → `restoreSubtitlePreference` → `exactSubtitleUrlMatch` finds the row → `applySubtitleSelection(persist = false)` → the same empty result.

I traced the other paths and they hold:
- Audio pick on an unlabelled "und" row: `isFallbackLabel = true`, so the stored language and label are kept.
- Audio pick on a labelled "und" row: saves `audioLang = null` and the label. The restore finds the row with `exactLabelMatch`.
- Subtitle pick while the selected audio row is a fallback: the stored audio is kept.
- Subtitle pick on an HLS row that was "und": `toSubtitlePreference` stores `language = null`, not `""`, so the Off shortcut in `findSubtitleTrackIndex` cannot fire. The url or group key restores the row, and the selector treats the blank row as compatible.
- A subtitle preference saved before the fix with "und": blank rows pass `matchesPreferredLanguage`, so the identity or url matchers still restore it.
- A stored `audioLang = "und"` from before the fix now restores to the default track. The spec already accepts this.

The diff changes no UI, so there is no focus path to check.
