# Research: audio-preference-locale-independent-label

## Request

"A stored audio preference uses the localized display label, so it misses after an update or a locale change" (`state.md` title). Log entry, verbatim: "created (stage idea) from player-audio-unknown-language triage-1 #1 (blind F1, codex): PlayerVM.saveTrackPreferences stores the row label as audioLabel; for an API row with no language and an author the label is now the localized "Язык не указан · X" / "Unknown language · X". Old saves ("Unknown · X", "und · X") and saves made under the other app locale no longer match in AudioTrackPreferenceResolver (exactLabelMatch, normalizedLabelMatch; languageMatch skips an empty language), so the default track plays once. Options: a locale-independent identity for API rows (id or author/type key), or accept legacy prefixes in the resolver."

## Understanding

The player remembers the viewer's dubbing per item as a display label plus a language. For an API-built audio row with no language, the label now starts with a localized "unknown language" prefix, so the same dubbing is stored as a different string in ru and en and differs from what the released app stored. On restore the label is the only usable key for such a row (its language is empty), so a locale switch or the update makes restore miss and the default track plays. The fix needs restore to recognise that row independently of the prefix.

## Current state in code

- `app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerUIMapper.kt:21-36`: API row label = `knownLanguageOrNull(lang) ?: getString(player_audio_language_unknown)` + ` · ` + author or type title; `language = ""` when unknown; `isFallbackLabel` only when language and title are both missing. The `Audio` id, author id and type id (`data/api/models/Models.kt:302-324`) are not carried into the row.
- `app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerUIModels.kt:20-26`: `AudioTrackUIState(index, label, language, isFallbackLabel)`; no identity field (contrast `SubtitleTrackUIState`, which has url, sourceFile and player ids).
- Strings: `res/values/player.xml:49` "Язык не указан" (default locale is ru), `res/values-en/strings.xml:303` "Unknown language".
- `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1955-1981` `saveTrackPreferences`: stores `audioLang = language.takeIf { isNotEmpty }` and `audioLabel = label`; a row with `isFallbackLabel` keeps the stored pair.
- `app/src/main/java/com/kino/puber/data/repository/PlayerPreferencesRepository.kt:14-59`: SharedPreferences, keys `KEY_AUDIO_LANG_PREFIX + itemId` and `KEY_AUDIO_LABEL_PREFIX + itemId`; a null value removes the key.
- `app/src/main/java/com/kino/puber/ui/feature/player/vm/AudioTrackPreferenceResolver.kt:11-25,176-211`: `findAudioTrackIndex` tries exact label, label without "NN. " prefix, voice type + language (needs a language), language (needs a language). For a stored pair (label "X-prefix · Author", lang null) only the two label matchers can hit.
- `PlayerVM.kt:558-589` `onTracksUpdated`: `audioTracks = reportedAudioTracks.ifEmpty { audioTracks }`; `restoreAudioPreference()` (`:607-620`) runs once on the first report. `PlaybackController.kt:866-888` reports Media3 rows (`vm/PlayerAudioTracks.kt:8-18`: manifest label, else language, else localized "Track N" with `isFallbackLabel`) whenever any audio group exists. So API rows are the shown rows, and thus saved or restored against, only before the first track report or when Media3 reports text groups without audio. Inferred from code; how often this happens on device is unverified.
- Media3 manifest labels (for example "01. Дубляж (RUS)", `PlayerVMTrackSelectionTest.kt:43`) never equal an API label, so an API-label preference did not restore against Media3 rows before `7f1198d6` either (state-path review, `player-audio-unknown-language/review-state-path-1.md`).
- In-app locale switch exists: `core/system/AppLocale.kt:21-60` switches in-process, `data/preferences/AppLanguageRepository.kt`, Device settings `ui/feature/device/settings/vm/DeviceSettingsVM.kt:455-460` (`ChangeAppLanguage`); `res/xml/locales_config.xml` ru, en. Labels already in player state keep the old language until the state is rebuilt (`AppLanguageRepository.kt` KDoc).
- Tests: no unit test calls `findAudioTrackIndex` (`AudioTrackPreferenceResolverTest.kt` covers subtitles only); save behaviour is covered in `PlayerVMTrackSelectionTest.kt`, mapper in `PlayerUIMapperTest.kt`, storage in `PlayerPreferencesRepositoryTest.kt`.

## Related work

| Run / commit | Relation |
|---|---|
| `player-audio-unknown-language` (done, merge `0f654bc6`, commit `7f1198d6`) | Introduced the localized prefix; deferred this as triage-1 #1; the one-time miss is an accepted brief risk there |
| `player-audio-und-label` (done, merge `53f9b97e`) | `knownLanguageOrNull` treats "und" as no language; spec `docs/superpowers/specs/2026-11-27-player-audio-und-label.md` |
| `manual-quality-hls4` | Added the `isFallbackLabel` guard against storing "Track N" (spec D1: a dead stored preference is left to be overwritten) |
| `player-track-label-resource-provider` (idea) | Routes the Media3 "Track N" string through ResourceProvider; same files, no overlap in stored labels |
| `locale-switch-dead-row` (idea) | Confirms the runtime locale switch; unrelated defect (unclickable row) |
| Release tags | `v1.12.8` (latest) contains neither `7f1198d6` nor `53f9b97e` (`git tag --contains` empty): released builds store "Unknown · X" or "und · X" (with `audioLang = "und"`) for these rows |

Duplicate: none.

## External findings

External findings: off (local research only; `research.web` not set in the brief).

## Options

| Option | What | Trade-offs | Size |
|---|---|---|---|
| A (recommended) | Resolver-only: add a matcher after the label matchers that, for a stored pair whose language is not a known language (`knownLanguageOrNull` null, so null, "", "und"), compares the part after the first " · " with the same part of rows whose `language` is empty | No stored-format change, covers released saves, both locales and future prefix wording; a title shared by two unknown-language rows resolves to the first; label stays a display string | S (resolver + tests) |
| B | Locale-independent identity: add a preference key to `AudioTrackUIState` from the API (`author.id` or `type.id`, or `Audio.id`), store it instead of the label for API rows, match it first; keep A-style fallback for legacy saves | Robust to any label change; changes what is persisted and the row model, touches mapper, VM save, resolver, repository semantics; API ids' stability across sessions unverified | M (persistence risk area) |
| C | Accept: document the one-time miss, change nothing | Zero cost; exposure narrow (API rows shown only before the first track report, see Current state); a locale switch keeps causing misses for affected items | none |

## Risks

- Option A must not let a title-only match beat a real language match: it applies only when the stored language is unknown, and only to rows with empty language.
- `findAudioTrackIndex` has no direct tests; any change should add them first (exact, normalized, voice type, language, and the new case for ru, en, "Unknown", "und").
- Restore runs once, against Media3 rows in the usual case; a fix covers only the API-rows window, so device evidence of a real miss is hard to produce (unverified how often it occurs).

## Open questions

| ID | Question | Recommended answer |
|---|---|---|
| Q1 | Option A, B or C? | A: resolver-only, no persisted-format change, covers released and cross-locale saves |
| Q2 | Device verification for a resolver-only change, given the API-rows window is hard to reach on the box? | Unit tests only, plus a box smoke that an item with a stored Media3 dubbing still restores |

## Sources

- Files: those cited above; `.flow/roles/analyst.md:18-25` (risk areas).
- Commits: `7f1198d6`, `0f654bc6`, `53f9b97e`, `88cdf071`, tag `v1.12.8` (`df3e4b02`).
- Runs: `player-audio-unknown-language` (state, triage-1, review-state-path-1), `player-audio-und-label`, `manual-quality-hls4` (spec), `player-track-label-resource-provider`, `locale-switch-dead-row`.
- Specs: `docs/superpowers/specs/2026-11-27-player-audio-und-label.md`, `docs/superpowers/specs/2026-11-27-manual-quality-hls4.md`.
