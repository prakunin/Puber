# Test design: player-audio-unknown-language

Kind: bug, size S. Base 0bcd89c7. Level: `unit` (JUnit 5, `PlayerUIMapper(FakeResourceProvider())`, no VM, no mock of the mapper). File: `app/src/test/kotlin/com/kino/puber/ui/feature/player/model/PlayerUIMapperTest.kt`, style `mapAudioTracks_<behaviour>` like the existing `mapSubtitleTracks_*` tests. Expected label strings are built from `resources.getString(R.string.player_audio_language_unknown)`, never a literal.

## Risk profile

| Risk | P | I | Score | Covered by |
|---|---|---|---|---|
| Null or blank/"und" lang still shows English or raw "und" | 3 | 2 | 6 | AC-1, AC-2 tests |
| Row with only type title loses the title or gets wrong prefix | 2 | 2 | 4 | AC-3 test |
| Empty row not flagged `isFallbackLabel`, so it is saved as a preference | 2 | 3 | 6 | AC-4 test |
| Real language row regresses (label/language changed) | 2 | 3 | 6 | AC-5 guard |
| Saved "Unknown · X" preference no longer matches once (brief Risks) | 2 | 1 | 2 | Accepted: narrow, API rows are transient; noted in the brief |
| String missing in ru or en resources, build or lint break | 1 | 2 | 2 | AC-6 check.full (lint/compile) plus grep |

## Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 red | `mapAudioTracks_nullLanguageUsesLocalizedUnknownWithAuthor` | fails on base: label is "Unknown · X", not the resource string; fails if the resource lookup is dropped. Also asserts language == "" and isFallbackLabel false |
| AC-2 | unit | P0 red | `mapAudioTracks_blankOrUndLanguageIsTreatedAsUnknown` (rows for "", "und"; each equals the AC-1 row) | fails on base: "und" shown and stored as language; fails if `knownLanguageOrNull` is not applied |
| AC-3 | unit | P0 red | `mapAudioTracks_nullLanguageFallsBackToTypeTitle` | fails on base ("Unknown · Дубляж"); fails if the type title is not used when author is absent |
| AC-4 | unit | P0 red | `mapAudioTracks_noLanguageAndNoTitlesIsFallbackLabel` (no author/type, and author/type with null titles) | fails on base: label "Unknown", isFallbackLabel false; fails if the flag rule is removed |
| AC-5 | unit | P0 guard | `mapAudioTracks_keepsKnownLanguageAndAuthor` | fails if a real language is localized, rewritten or flagged; label "rus · X", language "rus", isFallbackLabel false |
| AC-6 | check | P1 | `grep -n '"Unknown"' PlayerUIMapper.kt` has no match; both resource files contain `player_audio_language_unknown`; check.full (lint, compile) green | fails if the literal stays or a locale lacks the string. A grep on source is a gate, not behaviour proof; behaviour is AC-1..AC-4 |
| AC-7 | manual | P1 | Manual check 1 | UI regression of the picker on device |

Failing-first: `mapAudioTracks_nullLanguageUsesLocalizedUnknownWithAuthor` (AC-1..AC-4 all red on base; the implementer runs each on unchanged code first, see compile note below).

Compile note: the tests reference `R.string.player_audio_language_unknown`, which does not exist on base. Add the two string entries first (plan step 1), then run the red tests against the old mapper to see the assertion failures, then change the mapper.

## UNTESTABLE

None. A row with no API language is not reachable on device without probing, so AC-1..AC-4 are unit-only.

## Manual checks (AC-7, box 192.168.1.110:5555, worktree dev APK, install-preserve, `com.kino.puber.stage` only)

1. Launch the app with RU UI, open an ordinary item with several dubbings, start playback (D-pad OK).
2. Open the audio picker with the D-pad. Expected: rows read as before ("rus · Author" style or Media3 labels); focus lands on the current track.
3. Move down/up and select another track. Expected: audio switches, playback continues, picker closes.
4. Must not happen: English "Unknown" in any row, crash, focus loss, playback restart.
