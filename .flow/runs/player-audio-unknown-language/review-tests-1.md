LENS: tests  ROUND: 1  BASE: 0bcd89c7

I reasoned about the tests against the base mapper and did not rerun them. The implementation record and the QA report both show check.full green, and the red runs recorded for the four P0 tests match what I get on base.

### F1 [minor] No test that the fallback flag stays off for a known language with no title
- Where: /home/dev/orca/workspaces/Puber/player-audio-unknown-language/app/src/test/kotlin/com/kino/puber/ui/feature/player/model/PlayerUIMapperTest.kt (the new `mapAudioTracks_*` tests)
- Problem: The change is `isFallbackLabel = language == null && title == null`. Only the AC-4 test covers the case where both are null. `keepsKnownLanguageAndAuthor` has a title, so it passes even if the rule becomes `language == null || title == null` or `title == null`.
- Failure scenario: With a mutated rule of `title == null`, a plain `Audio(lang = "rus")` row is flagged as a fallback. That row would no longer be saved as an audio preference, and every test still passes.
- Fix: Add a case with `Audio(id = 1, lang = "rus")`. It should expect label "rus", language "rus" and `isFallbackLabel = false`.

### F2 [nit] "und" variants are not exercised
- Where: PlayerUIMapperTest.kt, `mapAudioTracks_blankOrUndLanguageIsTreatedAsUnknown`
- Problem: The test covers only "" and "und". Whitespace-only, "UND" and "und-Latn" are untested. The mapper handles them through the shared `knownLanguageOrNull`, which is tested elsewhere.
- Failure scenario: None reachable from the mapper alone. This only matters if someone swaps `knownLanguageOrNull` for a plain check.
- Fix: Optional. Add " " and "und-Latn" to the list.

### Unproven
- AudioTrackPreferenceResolver.kt:176-191: A stored preference with the old "Unknown · X" label and an empty lang no longer matches, so that title falls back to the player's default track once. The brief accepts this as a narrow, transient risk. No test covers it, and nothing shows it occurs often.
- Every AC has a test or a device check. AC-1 to AC-4 are unit-only because a row with no language can't be reached on the box without probing the API. The P0 red-run record is present, and the guard test is marked as a guard.
