# Research: player-audio-unknown-language

## Request

> Audio rows built from the API show a hardcoded English "Unknown" when the language is missing

Log entry (state.md): created from player-audio-und-label Q2 (also manual-quality-hls4 D2): PlayerUIMapper.kt:20-33 (line 24) labels an API audio track with no language as the hardcoded English "Unknown"; move it to strings.xml through ResourceProvider.

Brief addition: check every hardcoded user-visible fallback string in the player UI mapping (PlayerUIMapper and neighbours) and how strings are resolved there.

## Understanding

Before the player reports its own audio tracks, and whenever it reports none, the audio picker shows rows that `PlayerUIMapper.mapAudioTracks` builds from the API `Audio` list. A row whose `lang` is null starts with the literal English "Unknown", so a Russian UI shows "Unknown · <author>". Every other fallback in the mapper resolves through `ResourceProvider`. The fix is to give the missing language a localized string (ru default + en) and cover it with a `FakeResourceProvider` unit test. The audit found no other hardcoded user-visible fallback on a runtime path in the mapper. One neighbour resolves its string through the raw `Context` instead of `ResourceProvider`.

## Current state in code

Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/`.

| Where | What |
|-------|------|
| `ui/feature/player/model/PlayerUIMapper.kt:21-34` | `mapAudioTracks`: label = `audio.lang ?: "Unknown"` (line 24) + `" · author.title"` or `" · type.title"`; `language = audio.lang ?: ""` (line 31); `isFallbackLabel` left false |
| `ui/feature/player/vm/ContentStateFactory.kt:36` | Only caller: `mapper.mapAudioTracks(resolved.audios)` |
| `ui/feature/player/vm/PlayerVM.kt:576` | `reportedAudioTracks.ifEmpty { audioTracks }`: API rows stay only until the player reports audio groups, or when it reports none |
| `ui/feature/player/vm/PlayerAudioTracks.kt:8-18` | Media3 counterpart `buildAudioRows`: `format.label ?: knownLanguageOrNull(language)`, else a localized "Track N" with `isFallbackLabel = true` |
| `ui/feature/player/model/PlayerUIModels.kt:20-26` | `AudioTrackUIState.isFallbackLabel`: a placeholder label must not be stored as a preference |
| `ui/feature/player/vm/PlayerVM.kt:1955-1982` | `saveTrackPreferences` keeps the stored audio label/lang when `isFallbackLabel`; otherwise stores `label` and non-empty `language` |
| `ui/feature/player/vm/AudioTrackPreferenceResolver.kt:11-25,176-211` | Audio restore matches the exact label, then the label without its number prefix, then voice type + language, then language |
| `ui/feature/player/model/SubtitleLanguage.kt:14-17` | `knownLanguageOrNull`: blank or "und" means no language; the API audio path does not use it |
| `ui/feature/player/model/SubtitleLabeler.kt:110-118` | Pattern to reuse: `subtitleLabeler(resources)` reads every fallback (`player_subtitle_unknown` etc.) through `ResourceProvider` |
| `core/system/ResourceProvider.kt:6-15`, `core/system/AndroidResourceProvider.kt:10-22` | `ResourceProvider` re-wraps the context when the in-app language changes, so mapper strings follow it |
| `app/src/test/kotlin/com/kino/puber/util/FakeResourceProvider.kt:8-19` | `getString(id)` = `"string_<id>"`, with args `"string_<id>_<a>_<b>"`; `locale` = ENGLISH |
| `app/src/test/kotlin/com/kino/puber/ui/feature/player/model/PlayerUIMapperTest.kt:12-15` | `PlayerUIMapper(FakeResourceProvider())`; tests cover qualities, time and subtitles; no `mapAudioTracks` test |
| `app/src/main/res/values/player.xml:47-48`, `app/src/main/res/values-en/strings.xml:301-302` | Existing `player_subtitle_unknown` ("Субтитры %1$d" / "Subtitles %1$d") and `player_audio_track_fallback` ("Дорожка %1$d" / "Track %1$d"); no "unknown language" string. `values` is Russian, `values-en` is English |
| `data/api/models/Models.kt:302-310` | `Audio.lang: String?`; whether the API sends a code ("rus") or a name is `unverified` |

### Hardcoded-string audit (player mapping and neighbours)

| Where | String | Runtime? | Finding |
|-------|--------|----------|---------|
| `PlayerUIMapper.kt:24` | `"Unknown"` | yes | The bug |
| `PlayerUIMapper.kt:207-211` | `ASPECT_RATIOS` "Auto"/"Stretch"/"Crop" | no: only `component/PlayerPreview.kt:146,203` | Runtime uses `mapAspectRatios()` (lines 226-230, localized) |
| `PlayerUIMapper.kt:196-205` | `SPEEDS` "0.25x".."2.0x" | yes (`ContentStateFactory.kt:45`) | Numeric, the same in both locales |
| `PlayerUIMapper.kt:64,81` | `"${h}p"` quality fallback | yes | Technical unit |
| `PlayerUIMapper.kt:25-26`, `SubtitleLabeler.kt:84` | `" · "` separator | yes | Punctuation |
| `vm/PlaybackController.kt:873-875` | `context.getString(R.string.player_audio_track_fallback, n)` | yes | Localized, but read through the injected `Context` (`component/PlayerScreen.kt:58`), not `ResourceProvider`. Per `AndroidResourceProvider.kt:15-20` it may keep the start-up language after an in-app switch (`unverified` on device) |
| `component/DebugOverlay.kt:29-35` | "Source", "Video", "Audio" ... | debug overlay | Developer-facing |
| `component/PlayerInfoPanel.kt:67` | `stringResource(R.string.player_info_unknown)` | yes | Correct |
| `component/PlayerPreview.kt:38-178` | Russian sample texts | preview only | No action |

Other API audio rows are also not normalized: a blank or "und" `lang` shows as-is (blank gives a label that starts with " · "), and a non-null `lang` is shown raw, not as a display name. Whether the API sends such values is `unverified`.

## Related work

| Run / commit | Relation |
|--------------|----------|
| player-audio-und-label (done, 53f9b97e) | Source: Q2 deferred this. Added `knownLanguageOrNull` for Media3 rows; spec `docs/superpowers/specs/2026-11-27-player-audio-und-label.md` |
| manual-quality-hls4 | D2 deferred the same item; added `player_audio_track_fallback` and the `isFallbackLabel` guard (judge-1.md:8) |
| player-audio-track-label-fallback (dropped, duplicate of manual-quality-hls4) | Same picker, Media3 path |
| subtitle-und-preferred-fallback (0bcd89c7, base) | Same "und" area, subtitles only |
| 0bf26ee5 | Moved player labels to `ResourceProvider` so they follow the in-app language, but left line 24 as it was |
| 88cdf071 (Release 1.0.0) | Introduced `?: "Unknown"` (`git log -S`) |

Probable duplicate: none.

## External findings

Off (local research only). The brief sets no `research.web`, and `.flow/config.yaml` has no research key.

## Options

| # | Change | Trade-offs | Size |
|---|--------|------------|------|
| A | New string `player_audio_language_unknown` (ru + en) resolved via `resources.getString` at `PlayerUIMapper.kt:24`; pass `lang` through `knownLanguageOrNull` so blank/"und" also falls back; set `isFallbackLabel = true` when the row has neither a language nor an author/type; unit tests in `PlayerUIMapperTest` | Closes the request and the blank/"und" edge case; matches the Media3 path's preference guard. Existing saved "Unknown · X" labels stop matching exactly (see Risks) | S |
| B | As A, but with no language show only the author/type title, and use the existing `player_audio_track_fallback` ("Track N", `isFallbackLabel`) when nothing is known | No new string, and the picker matches Media3 rows ("Дубляж" rather than "Неизвестно · Дубляж"). The row no longer says that the language is missing | S |
| C | A plus `PlaybackController.kt:873-875` through `ResourceProvider` (new constructor dependency, DI at `PlayerScreen.kt:58`) and `ASPECT_RATIOS` preview constants replaced | Every player fallback then follows the in-app language. Touches the controller and DI, which needs a device check of a language switch | S/M |

Recommended: **A**. It is the literal ask, keeps the mapper's pattern (`SubtitleLabeler.kt:110-118`) and needs one string pair and one test file.

## Risks

- Saved preferences: an API row with no language was stored with the label "Unknown · X" (`PlayerVM.kt:1972-1976`) and an empty lang. After the change, `exactLabelMatch` and `normalizedLabelMatch` (`AudioTrackPreferenceResolver.kt:176-191`) no longer match, and with an empty lang `voiceTypeAndLanguageMatch`/`languageMatch` cannot match either. The track falls back to the player's default. API rows only hold until the player reports audio (`PlayerVM.kt:576`), so the impact is narrow.
- A localized label also makes the stored label depend on the UI language at save time. Rows from Media3 already behave this way for "Track N", which is why they use `isFallbackLabel`.
- Device check: the change touches ViewModel state strings, so AGENTS.md asks for on-device verification. Getting an item whose API audio has no `lang` from ordinary use is `unverified`, and probing the API is forbidden. A unit test is the realistic evidence; the device check should be limited to a regression look at the picker.

## Open questions

| ID | Question | Recommended answer |
|----|----------|--------------------|
| Q1 | Wording: "Unknown · X" localized (A) or drop the prefix and fall back to "Track N" (B)? | A, with ru "Язык не указан" / en "Unknown language" |
| Q2 | Also route `PlaybackController.kt:874` through `ResourceProvider` (option C)? | No, separate idea run; it needs DI and a device language-switch check |
| Q3 | Mark a row with neither language nor author/type as `isFallbackLabel`? | Yes, the same rule as `buildAudioRows` |

## Sources

- Code: `ui/feature/player/model/PlayerUIMapper.kt`, `PlayerUIModels.kt`, `SubtitleLabeler.kt`, `SubtitleLanguage.kt`; `ui/feature/player/vm/PlayerAudioTracks.kt`, `PlaybackController.kt`, `PlayerVM.kt`, `ContentStateFactory.kt`, `AudioTrackPreferenceResolver.kt`; `ui/feature/player/component/PlayerScreen.kt`, `PlayerPreview.kt`, `DebugOverlay.kt`, `PlayerInfoPanel.kt`; `core/system/ResourceProvider.kt`, `AndroidResourceProvider.kt`; `data/api/models/Models.kt`; `res/values/player.xml`, `res/values-en/strings.xml`
- Tests: `app/src/test/kotlin/com/kino/puber/util/FakeResourceProvider.kt`, `.../player/model/PlayerUIMapperTest.kt`, `.../player/vm/PlayerVMTrackSelectionTest.kt:167`
- Docs: `docs/architecture/testing.md:52-65`
- Commits: 0bf26ee5, 88cdf071, 53f9b97e, 0bcd89c7
- Runs: player-audio-und-label, manual-quality-hls4, player-audio-track-label-fallback, subtitle-und-preferred-fallback
