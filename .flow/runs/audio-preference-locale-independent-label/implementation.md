# Implementation record: audio-preference-locale-independent-label

Plan tasks 1 and 2 are done. Box smoke M1 (AC-6, device) is left to qa; the implementer role does not touch devices.

## Commits

One commit for both tasks, so no commit has failing tests. See `git log` on the branch.

## Red run

Run on the base resolver, 40 tests, 5 failed:

- red: findAudioTrackIndex_unknownLanguageLabelStoredUnderEnglish_matchesRussianRow - AssertionFailedError, got -1 expected 1
- red: findAudioTrackIndex_unknownLanguageLabelStoredUnderRussian_matchesEnglishRow - AssertionFailedError, got -1 expected 1
- red: findAudioTrackIndex_legacyUnknownLabelWithNullLang_matchesRow - AssertionFailedError, got -1 expected 0
- red: findAudioTrackIndex_legacyUndLabelWithUndLang_matchesRow - AssertionFailedError, got -1 expected 0
- red: findAudioTrackIndex_unknownLanguage_sharedTitle_firstRowWins (P2) - AssertionFailedError, got -1 expected 0
- green on base (guards, as designed): exactLabel_wins, numberedPrefixLabel_matchesNormalized, voiceTypeAndLanguage_matches, languageOnly_matches, knownStoredLanguage_titleMatchDoesNotApply, noSeparator_noMatch, emptyTitle_noMatch, titleAbsentFromEmptyLanguageRows_noMatch

## Deviations

None.

## Check

check.fast and check.full green (BUILD SUCCESSFUL, 117 tasks; tooling shell tests all PASS).

## For reviewers

`unknownLanguageTitleMatch` sits third in the matcher list in `AudioTrackPreferenceResolver.kt`. It runs only when the stored language is unknown, and compares the part after the first " · " against rows with an empty language.

## Fix round 1

| Finding | Fix |
|---|---|
| #2 duplicated separator | `AUDIO_LABEL_SEPARATOR` is one internal constant in `PlayerUIMapper.kt`; the mapper and `AudioTrackPreferenceResolver` both use it. No behaviour change. |
| #4 exactLabel_wins did not isolate exactLabelMatch | Rows `01. X`, `02. X`, stored `02. X` -> 1. With `exactLabelMatch` replaced by `NO_MATCH` locally the test fails (AudioTrackPreferenceResolverTest.kt:22); reverted. |

Check: check.fast, PlayerUIMapperTest, AudioTrackPreferenceResolverTest and :app:detektAll green.
