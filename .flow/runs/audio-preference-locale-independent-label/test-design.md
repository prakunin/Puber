# Test design: audio-preference-locale-independent-label

Kind bug, size S, perf track off. Base `6ccf2ebf`. Level `unit` only (JUnit 5, no VM, no fakes needed: `AudioTrackPreferenceResolver` is a pure class with no dependencies; rows built as `AudioTrackUIState(index, label, language, isFallbackLabel)`). File: `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/AudioTrackPreferenceResolverTest.kt`, next to the existing subtitle tests, same style (`resolver = AudioTrackPreferenceResolver()`, `assertEquals`, a small row helper).

## Risk profile

| Risk | P | I | Score | Handling |
|---|---|---|---|---|
| New title matcher beats a real language match, or fires when stored language is known | 2 | 3 | 6 | Test `findAudioTrackIndex_knownStoredLanguage_titleMatchDoesNotApply` (AC-4) |
| Existing matchers regress (order change, the new one inserted third) | 2 | 3 | 6 | Guard tests per matcher, written and green before the change (AC-6) |
| False positive on empty or missing title, or a title absent from all empty-language rows | 2 | 2 | 4 | AC-5 tests assert `NO_MATCH` |
| New matcher takes a row with a known language that shares a title | 2 | 2 | 4 | Case inside the AC-1 test: row 0 `ru · A` and a same-title row with a language must not be picked |
| Title shared by two empty-language rows resolves to first | 3 | 1 | 3 | Accepted by option A; pinned by `findAudioTrackIndex_unknownLanguage_sharedTitle_firstRowWins` (P2) |
| Box restore of a stored Media3 dubbing regresses | 1 | 3 | 3 | Manual check M1 |

## Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 red | `findAudioTrackIndex_unknownLanguageLabelStoredUnderEnglish_matchesRussianRow` | fails if the matcher is missing or ignores a prefix that differs by locale (base returns NO_MATCH) |
| AC-2 | unit | P0 red | `findAudioTrackIndex_unknownLanguageLabelStoredUnderRussian_matchesEnglishRow` | fails if the matcher only handles one direction or hardcodes one prefix |
| AC-3 | unit | P0 red | `findAudioTrackIndex_legacyUnknownLabelWithNullLang_matchesRow`, `findAudioTrackIndex_legacyUndLabelWithUndLang_matchesRow` | fails if legacy released saves (`Unknown · X`, `und · X` with lang `"und"`) are not covered, or `knownLanguageOrNull("und")` is not treated as unknown |
| AC-4 | unit | P0 guard | `findAudioTrackIndex_knownStoredLanguage_titleMatchDoesNotApply` | fails if the title matcher fires for a known stored language (would return 0 instead of 1) |
| AC-5 | unit | P0 guard | `findAudioTrackIndex_noSeparator_noMatch`, `findAudioTrackIndex_emptyTitle_noMatch`, `findAudioTrackIndex_titleAbsentFromEmptyLanguageRows_noMatch` | fails if an empty or absent title matches (for example `"".equals("")` against rows with no separator) |
| AC-6 | unit | P0 guard | `findAudioTrackIndex_exactLabel_wins`, `findAudioTrackIndex_numberedPrefixLabel_matchesNormalized`, `findAudioTrackIndex_voiceTypeAndLanguage_matches`, `findAudioTrackIndex_languageOnly_matches` | fails if an existing matcher changes behaviour or the new one shadows it |
| AC-6 | device | P1 | M1 | fails if restore of a stored Media3 dubbing breaks on the box |
| Risk | unit | P2 | `findAudioTrackIndex_unknownLanguage_sharedTitle_firstRowWins` | documents the accepted first-row rule |

Guard tests (AC-4, AC-5, AC-6) are written first and must be green on the base commit. Table fixtures: the four AC-6 tests choose inputs where only that matcher can hit (for example voice type: stored `Дубляж · ru` against rows with other labels but matching voice type and language), so a removed matcher fails the test.

## Bugs

Failing-first: `findAudioTrackIndex_unknownLanguageLabelStoredUnderEnglish_matchesRussianRow` (AC-1). On base, reading `AudioTrackPreferenceResolver.kt:11-25,178-211` (`git -C <wt> show 6ccf2ebf:app/src/main/java/com/kino/puber/ui/feature/player/vm/AudioTrackPreferenceResolver.kt`): exact fails on the prefix, normalized strips only `NN. `, voice type and language matchers need a language and the row language is `""`, so it returns `NO_MATCH` (-1) rather than 1. All AC-1, AC-2, AC-3 tests share this path and are red on base. Implementer runs each red test on unchanged code first.

## Manual checks

M1 (box `192.168.1.110:5555`, `com.kino.puber.stage` only, dev APK via install-preserve, lease held, no API probing, one user's pace):
1. Open an item with several Media3 dubbings, choose a non-default dubbing in the player, back out.
2. Reopen the same item and start playback.
3. Expected: the chosen dubbing is active after the first track report.
4. Must not happen: default dubbing plays, crash, or a stored preference lost.
The API-rows window is not reachable on the device, so AC-1..AC-5 rest on unit tests alone.

## UNTESTABLE

None. AC-1..AC-5 on-device reproduction is out of reach by design (see M1); the unit level covers them.
