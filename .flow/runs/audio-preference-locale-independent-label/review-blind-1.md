LENS: blind  ROUND: 1  BASE: 6ccf2ebf

### F1 [minor] Title-only match can pick a row in the wrong language when the saved language is null
- Where: AudioTrackPreferenceResolver.kt:205-208 (`unknownLanguageTitleMatch`)
- Problem: The guard `knownLanguageOrNull(preferredLang) != null` treats a null `preferredLang` as "the saved row had no language". It never checks that the saved label's prefix was the unknown-language placeholder. If `preferredLang` is null and the label is `Русский · X`, the matcher compares only the title `X`. It then accepts any empty-language row whose title is `X`.
- Failure scenario: An older save has label `Русский · Оригинал` and lang null. The new stream has an unknown-language row `Язык не указан · Оригинал` and a known row `ru · Оригинал`. The empty-language row is selected, and it sits ahead of the language-based matchers. The test `legacyUnknownLabelWithNullLang` encodes this leniency. Whether a null-language save can come from a known-language row is not shown in this diff.
- Fix: Also require that the saved prefix is not a known language name. This may be impossible to tell across locales. If so, accept the leniency and document it.

### F2 [nit] The separator constant is duplicated and not tied to the label builder
- Where: AudioTrackPreferenceResolver.kt:246 and PlayerUIMapper.kt:27
- Problem: `" · "` is hard-coded separately in the mapper and in the resolver. If the mapper's format changes, the new matcher silently stops matching. The tests build labels by hand, so they would not catch that.
- Fix: Share one constant, or add a test that builds labels through `PlayerUIMapper`.

### F3 [nit] The matcher can also hit HLS rows built by `buildAudioRows`
- Where: AudioTrackPreferenceResolver.kt:205-208 and PlayerAudioTracks.kt:10-17
- Problem: Rows from `buildAudioRows` have an empty language and a free-form manifest `label`. A manifest name containing " · " is compared on its title only. Such rows could match a saved API-style label with the same trailing title. The scenario is unlikely.

No other findings.
