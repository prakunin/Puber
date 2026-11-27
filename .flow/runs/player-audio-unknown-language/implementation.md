# Implementation record: player-audio-unknown-language

## Commits

| Task | Change |
|---|---|
| 1 Strings | `player_audio_language_unknown` in `values/player.xml` ("Язык не указан") and `values-en/strings.xml` ("Unknown language") |
| 2+3 Mapper and tests | `PlayerUIMapper.mapAudioTracks` uses `knownLanguageOrNull`, a localized unknown prefix, type-title fallback, `isFallbackLabel`; five `mapAudioTracks_*` tests |

## Red run (strings added, old mapper)

- red: mapAudioTracks_nullLanguageUsesLocalizedUnknownWithAuthor - AssertionFailedError (label "Unknown · X")
- red: mapAudioTracks_blankOrUndLanguageIsTreatedAsUnknown - AssertionFailedError (PlayerUIMapperTest.kt:156)
- red: mapAudioTracks_nullLanguageFallsBackToTypeTitle - AssertionFailedError (PlayerUIMapperTest.kt:164)
- red: mapAudioTracks_noLanguageAndNoTitlesIsFallbackLabel - AssertionFailedError (PlayerUIMapperTest.kt:176)
- guard (green on base, as designed): mapAudioTracks_keepsKnownLanguageAndAuthor

## Checks

PlayerUIMapperTest 12/12 green after the change. check.full: BUILD SUCCESSFUL, baseline-profile tooling tests PASS. AC-6: no `"Unknown"` in the mapper.

## Deviations

None. AC-7 (device) is left to qa.

## For reviewers

Saved no-language audio preferences stop matching once (brief Risks).

## Fix round 1

- #2 fixed (d914810b): `mapAudioTracks_knownLanguageWithoutTitleIsNotFallbackLabel` (lang "rus", no titles, isFallbackLabel false). Local mutation `isFallbackLabel = title == null` fails it (PlayerUIMapperTest.kt:192); reverted, not committed.
- #3 fixed (d914810b): the undetermined list now also covers " ", "UND", "und-Latn".
- Checks: PlayerUIMapperTest green, :app:compileDevDebugKotlin and :app:detektAll BUILD SUCCESSFUL.
