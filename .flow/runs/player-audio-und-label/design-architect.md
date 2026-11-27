# Design (architect): player-audio-und-label

Paths are under `app/src/main/java/com/kino/puber/ui/feature/player/` unless written in full. ACs: `brief.md#acceptance-criteria`.

## Decisions

| Question | Decision | Why |
|---|---|---|
| Where is "und" recognised? | One helper `knownLanguageOrNull(language: String?): String?` in `model/SubtitleLanguage.kt`, next to `canonicalSubtitleLanguage` (`model/SubtitleLanguage.kt:13`). Null for null, blank, "und" (any case, after trim) and tags starting `und-` or `und_`; otherwise the input unchanged | One rule for audio and subtitles (Q1, option B); returning the input keeps every known value byte-identical |
| Where is it applied? | At the boundary where Media3 formats become rows: `buildAudioRows`, `buildSubtitleRow`, `toPlayerTextTracks`; plus `sameSubtitleLanguage` and the labeler's raw-code fallback for values that arrive from elsewhere (API rows, stored preferences) | Downstream code already treats a blank language as unknown (`vm/SubtitleTrackSelector.kt:55,110`, `vm/AudioTrackPreferenceResolver.kt:109,169`, `vm/SubtitleTrackMerger.kt:41`), so normalising at the boundary needs no change there |
| How is the stored audio preference protected (AC-3)? | No VM change: an unlabelled "und" row now has `isFallbackLabel = true`, which the existing guard `vm/PlayerVM.kt:1966` honours | The guard from `d736f9b9` already covers "Track N" rows |
| Labelled "und" audio row (AC-2) | Label kept, `isFallbackLabel = false`, `language = ""` | A manifest name is a real dubbing name; it is saved as `audioLabel` with `audioLang = null` (`vm/PlayerVM.kt:1969-1972`) |
| Does a rendition language fill an "und" subtitle format? | Yes: `knownLanguageOrNull(format.language) ?: knownLanguageOrNull(renditionFormat?.language).orEmpty()` | AC-4 second clause |
| Change `canonicalSubtitleLanguage("und")` to ""? | No; `sameSubtitleLanguage` checks `knownLanguageOrNull` on both sides first | An empty canonical code would make two "und" values compare equal at `model/SubtitleLanguage.kt:10` |

## Changes by layer

### API / repository

N/A: the API path (`model/PlayerUIMapper.kt:20-33`) is Q2, a separate run.

### ViewModel state and actions

| File | Change |
|---|---|
| `model/SubtitleLanguage.kt:8-11` | Add `knownLanguageOrNull`. `sameSubtitleLanguage` returns false when either side is null after `knownLanguageOrNull` (replaces the `isBlank` check at line 9) |
| `vm/PlayerAudioTracks.kt:9-14` | `val language = knownLanguageOrNull(format.language)`; `name = format.label ?: language`; `language = language.orEmpty()`; `isFallbackLabel = name == null` unchanged |
| `vm/PlayerTextTracks.kt:46` | Row language as in Decisions. `findRendition` (line 69) needs no edit: it goes through `sameSubtitleLanguage` |
| `vm/PlayerTextTracks.kt:87` | `language = knownLanguageOrNull(format.language)` |
| `model/SubtitleLabeler.kt:42-45` | `rawLanguageLabel = knownLanguageOrNull(track.language)?.trim()?.uppercase(Locale.ROOT)`, so "und" falls to `unknownLabel` (line 50) |
| `vm/PlayerVM.kt:1955-1980` | None (guard at line 1966) |
| `vm/PlaybackController.kt:865-887` | None: it already routes formats through `buildAudioRows` (line 872) and `buildSubtitleRows` (line 883) |

### Screen and DI module

N/A: no screen, scope or binding changes.

### Compose content

N/A: row text only; no layout change (brief: tracks none).

### Resources

N/A: `player_audio_track_fallback` and `player_subtitle_unknown` exist (`app/src/main/res/values-en/strings.xml:301-302`).

### Tests

| AC | File | Case |
|---|---|---|
| AC-1, AC-2 | `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerAudioTracksTest.kt` | "und", "UND", "und-Latn" unlabelled; "English" + "und" |
| AC-3 | `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMTrackSelectionTest.kt` | Pattern of `selectAudioTrack_fallbackRow_keepsStoredAudioPreference` (line 105) and `selectSubtitle_whileAudioRowIsFallback_keepsStoredAudioPreference` (line 114), with the reported row produced by `buildAudioRows` from an "und" format rather than hand-built (the helper at line 122 hand-builds) |
| AC-4 | `app/src/test/kotlin/com/kino/puber/ui/feature/player/model/SubtitleLabelerTest.kt`, `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerTextTracksTest.kt` | Row with language "und" reads the unknown ordinal; "und" format + "rus" rendition gives language "rus"; `toPlayerTextTracks` maps "und" to null |
| AC-5 | `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/SubtitleTrackSelectorTest.kt`, `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/SubtitleTrackIdentityTest.kt:62` | `sameSubtitleLanguage("und","und")` false; selector does not pick by language for an "und" row |
| AC-6 | `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerNetworkTracksTest.kt` | Next to `unlabelledAudio_reportsLocalizedFallbackRowMarkedAsFallback` (line 159), routes from `harness.useMediaRoutes(required = setOf("progressive"))` as in `PlaybackControllerDeviceTest.kt:34`, prepare `/media/progressive.mp4` |

## Alternatives rejected

- Audio only (research option A): leaves "UND" subtitle labels and "und" language matches.
- Map "und" inside `canonicalSubtitleLanguage`: makes two "und" values equal in `sameSubtitleLanguage`.
- `DefaultTrackSelector.normalizeUndeterminedLanguageToNull`: protected, not callable from app code (research: External findings).
- Change `SubtitleTrackMerger.orderingLanguage` (`vm/SubtitleTrackMerger.kt:104`): player rows already arrive blank; see Open questions.

## Risks

| Risk | Kind | Where |
|---|---|---|
| A row or stored subtitle preference still holding "und" (API row, value saved before the fix) is now incompatible with every non-blank candidate in the selector, instead of matching an "und" candidate; restore then relies on identity matchers or the preferred text language | correctness, data shape | `vm/SubtitleTrackSelector.kt:55-56`, `vm/AudioTrackPreferenceResolver.kt:153-159` |
| Stored `audioLang = "und"` no longer matches any row; restore falls to the default track until the next real pick | data shape | `vm/AudioTrackPreferenceResolver.kt:201,210` (brief Risks) |
| A blank row language is passed to `setPreferredTextLanguage`; unchanged behaviour for blank rows, now also for former "und" rows | correctness | `vm/PlaybackController.kt:905` |
| Parallel runs `playback-controller-split`, `subtitle-restore-model` edit the same files; merge conflicts in `vm/PlayerTextTracks.kt`, `model/SubtitleLanguage.kt` | rollout | files above |
| New top-level function absent from the baseline profile; one string compare per track report | performance | `app/src/main/generated/baselineProfiles/baseline-prof.txt:37743` |

## Open questions

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| Q3 | Also route `SubtitleTrackMerger.orderingLanguage` (`vm/SubtitleTrackMerger.kt:104-105`) through `knownLanguageOrNull`, so API rows with "und" go to `selectTracksWithoutLanguage` (line 53)? | No: no AC needs it and KinoPub API "und" subtitles are unverified | no |
