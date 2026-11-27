# Research: player-audio-und-label

## Request

From `state.md` (title, Log):

> Audio row shows the raw language code "und" for an undetermined-language track
>
> 2026-11-27 created (stage idea) from manual-quality-hls4 fix round 1 (implementer note): Media3 reports language "und" for a progressive MP4 audio track; buildAudioRows (PlayerAudioTracks.kt, before that PlaybackController.notifyTracksUpdated on base) uses format.label ?: format.language, so an http fallback stream shows a row named "und" instead of the localized fallback label. Pre-existing on base. Treat "und" (C.LANGUAGE_UNDETERMINED) as no language.

Conductor addition: check subtitle/text track naming for the same "und" issue.

## Understanding

When the player reports an audio track whose container says "language undetermined" (ISO 639-2 `und`) and gives no name, the Audio row in player Settings reads "und" instead of the localized "Track N" fallback. Because the row is not marked as a fallback, picking it also stores `und` as the viewer's audio preference. The subtitle side converts the same code into the label "UND" when a text track has no readable name. The fix is to treat `und` like a missing language wherever player formats become picker rows.

## Current state in code

| Where | What it does with `und` |
|---|---|
| `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerAudioTracks.kt:9` | `name = format.label ?: format.language`: an unlabelled `und` format gets the name "und" |
| `PlayerAudioTracks.kt:13-14` | `language = "und"`, `isFallbackLabel = false` |
| `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:865-887` | `notifyTracksUpdated` passes the first format of each audio group to `buildAudioRows` and reports the rows |
| `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:576` | a non-empty player report replaces the API audio names |
| `PlayerVM.kt:1966-1977` | `saveTrackPreferences` keeps the stored audio only when `isFallbackLabel`; an "und" row saves `audioLang="und"`, `audioLabel="und"` and overwrites the stored dubbing |
| `app/src/main/java/com/kino/puber/ui/feature/player/vm/AudioTrackPreferenceResolver.kt:201,210` | audio restore compares `language ==` literally, so a stored "und" matches any later "und" track |
| `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerTextTracks.kt:46` | subtitle row `language = format.language ?: rendition language`: "und" is kept and wins over a rendition language |
| `PlayerTextTracks.kt:87` | `toPlayerTextTracks` keeps "und" for the selector |
| `app/src/main/java/com/kino/puber/ui/feature/player/model/SubtitleLanguage.kt:13-24` | `canonicalSubtitleLanguage("und")` already skips `isO3Language == "und"` but then returns the input `"und"`; `sameSubtitleLanguage("und","und")` is true |
| `SubtitleLanguage.kt:41-50` | `subtitleLanguageDisplayName("und")` returns null (root locale has no display name) |
| `app/src/main/java/com/kino/puber/ui/feature/player/model/SubtitleLabeler.kt:42-50` | with no display name and no readable label, `rawLanguageLabel` shows "UND" before the localized `player_subtitle_unknown` |
| `app/src/main/java/com/kino/puber/ui/feature/player/vm/SubtitleTrackSelector.kt:55-56,110-116` | "und" is not blank, so it is matched as a real language |
| `app/src/main/java/com/kino/puber/ui/feature/player/vm/SubtitleTrackMerger.kt:104-105` | "und" rows sort as their own language group |
| `app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerUIMapper.kt:20-33` | API audio rows: `audio.lang ?: "Unknown"` (hardcoded English, API path, not `und`) |

Tests: `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerAudioTracksTest.kt` (null label/language, label then language; no `und` case), `SubtitleLabelerTest.kt`, `SubtitleTrackMergerTest.kt`, `SubtitleTrackSelectorTest.kt`, `PlayerVMTrackSelectionTest.kt` (none mention `und`). Instrumented: `app/src/androidTestInstrumentation/.../PlaybackControllerNetworkTracksTest.kt:169` (fallback row on an unlabelled HLS media playlist). Fixture `player-test-fixtures/src/main/assets/player-fixtures/progressive.mp4` is the observed `und` source (`docs/superpowers/plans/2026-11-27-manual-quality-hls4.md:161`).

Reuse: `C.LANGUAGE_UNDETERMINED` (media3-common 1.11.0, value "und"); the `isFallbackLabel` guard from `d736f9b9`; `canonicalSubtitleLanguage` already names `"und"` once.

## Related work

| Item | Relation |
|---|---|
| run `manual-quality-hls4` (handover) | source; added `buildAudioRows`, fallback label and the save guard (`d736f9b9`); its plan records the `und` fixture case |
| run `player-audio-track-label-fallback` (dropped, folded into manual-quality-hls4) | same audio-row naming area |
| runs `subtitle-upstream-port`, `subtitle-restore-model`, `subtitle-sideload-merge` | built `SubtitleLabeler`, `SubtitleLanguage`, selector and merger (`126302bc`, `bd1917a5`, `49afcadf`) |
| run `baseline-player-track-coverage` | track tests area |

No run covers `und`. DUPLICATE_OF: none.

## External findings

| Finding | Source |
|---|---|
| `C.LANGUAGE_UNDETERMINED = "und"`, "an undetermined language as an ISO 639-2 language code"; also confirmed in the project's media3-common 1.11.0 jar (`javap`) | https://raw.githubusercontent.com/androidx/media/1.11.0/libraries/common/src/main/java/androidx/media3/common/C.java |
| MP4 `mdhd` language is decoded from the packed code and returned as "und" verbatim; null only for out-of-range characters | https://raw.githubusercontent.com/androidx/media/1.11.0/libraries/extractor/src/main/java/androidx/media3/extractor/mp4/BoxParser.java |
| `Util.normalizeLanguageCode` (applied by `Format.Builder`) keeps "und"; it does not map it to null | https://raw.githubusercontent.com/androidx/media/1.11.0/libraries/common/src/main/java/androidx/media3/common/util/Util.java |
| `DefaultTrackSelector` has `protected static normalizeUndeterminedLanguageToNull(String)` (from the 1.11.0 api jar via `javap`); being protected it is not a usable helper for app code. `selectUndeterminedTextLanguage` exists in its Parameters | https://raw.githubusercontent.com/androidx/media/1.11.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/trackselection/DefaultTrackSelector.java |

Media3 version: `gradle/libs.versions.toml:27` (`media3 = "1.11.0"`).

## Options

| # | Option | Trade-offs | Size |
|---|---|---|---|
| A | **Audio only.** In `buildAudioRows` treat a blank or `und` language as null: name falls to "Track N", `isFallbackLabel = true`, `language = ""`. | Fixes the reported row and the preference overwrite through the existing guard. Leaves "UND" on subtitles and `und` matching in selector/merger. | S |
| B | **Shared helper, both pickers.** One `knownLanguageOrNull()` (blank or `und`, case-insensitive, also `und-*` tags, unverified whether Media3 ever emits those) next to `canonicalSubtitleLanguage`; use it in `buildAudioRows`, `buildSubtitleRow` (so a rendition language can fill in), `toPlayerTextTracks`, and make `sameSubtitleLanguage` false for `und`. Labeler then falls to the readable label or "Subtitles N". | One rule for both pickers; removes "UND" and false language matches. Touches subtitle matching, so the subtitle tests need `und` cases. | S (about 3 main files plus tests) |
| C | B, plus replace the hardcoded `"Unknown"` in `PlayerUIMapper.mapAudioTracks` with a string resource. | Clears the last raw placeholder in audio naming; different trigger (API null `lang`). | S |

Recommended: **B**. Same cause on both pickers, the subtitle rule already half-knows `und` (`SubtitleLanguage.kt:22`), and the cost over A is a few tests.

## Risks

- Audio restore (`AudioTrackPreferenceResolver.kt:201,210`): a viewer who already has `audioLang="und"`/`audioLabel="und"` stored keeps it; after the fix no row matches it, so restore falls to the default track. Harmless, but the stale value stays until the next real pick.
- Subtitle restore: making `sameSubtitleLanguage` false for `und` removes the language fallback for two unnamed `und` tracks; the identity matchers (format id, uri, group/index) still apply.
- Whether KinoPub's real `http` MP4 files carry `und` audio or any embedded text track is unverified; the observation comes from the test fixture. Settle from the fixture and ordinary box playback, never by probing the API.
- Touches the player track report: needs on-device verification per AGENTS.md (box, `com.kino.puber.stage`).

## Open questions

| ID | Question | Recommended answer |
|---|---|---|
| Q1 | Scope: audio only, or audio and subtitles? | Both (option B). |
| Q2 | Should an `und` audio row count as a fallback for saving the preference? | Yes: `isFallbackLabel = true`, so the stored dubbing is kept. |
| Q3 | Include the hardcoded `"Unknown"` in `PlayerUIMapper` (option C)? | No; separate idea run, it is an API-path string, not `und`. |

## Sources

- Files: those in the table under Current state; `gradle/libs.versions.toml`; `docs/superpowers/plans/2026-11-27-manual-quality-hls4.md`.
- Commits: `d736f9b9`, `126302bc`, `bd1917a5`, `49afcadf`, `cb7fcd45` (base).
- Runs: `manual-quality-hls4` (state, research, reviews), `player-audio-track-label-fallback`, `subtitle-upstream-port`, `subtitle-restore-model`, `subtitle-sideload-merge`.
- URLs: listed under External findings (fetched 2026-11-27, tag 1.11.0).
