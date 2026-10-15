LENS: blind  ROUND: 1  BASE: f351d988  HEAD: d538e15b

### F1 [minor] A small change in a subtitle's label between episodes now loses the saved subtitle that matching by language used to find
- Where: app/src/main/java/com/kino/puber/ui/feature/player/vm/AudioTrackPreferenceResolver.kt:44-46, 172-179 (`isCompatibleWith` / `matchesPreferredLabel`)
- Problem: Every rule after the URL rules only looks at "compatible" rows. A row is dropped if its readable label is present and differs from the saved label, even when the language matches, so `subtitleLanguageMatch` never sees it. The old `unambiguousSubtitleLanguageMatch` looked at every row of the language. From the first pick the label is saved (`toSubtitlePreference` writes `readableDescriptiveLabel()`); after that any change in the manifest NAME turns subtitles off.
- Failure scenario: Episode 1: viewer picks NAME="Russian (Full)"; saved lang `rus`, key `subtitle:Russian (Full)`, label "Russian (Full)". Episode 2 has one Russian row NAME="Russian Full". Identity and label rules miss; the language rule finds no candidate (filtered as incompatible). NO_MATCH, subtitles Off. Before this diff the only `rus` row would have been restored.
- Fix: use the label only to choose among several same-language rows, not to filter: compute `candidates` without the label check, keep `subtitleLabelMatch` as the chooser, let `subtitleLanguageMatch` fall back to a single same-language row. If exclusion by label is intended, pin it with a test.

### F2 [minor] The "empty language means Off" rule can never run
- Where: AudioTrackPreferenceResolver.kt:43
- Problem: `if (preferredLang == "" && preferredUrl.isNullOrEmpty()) return indexOfFirst { isOff }` needs a saved language `""`. Nothing writes that: `toSubtitlePreference()` returns `NONE` for Off and `language.takeIf { it.isNotEmpty() }` otherwise; old data stored only non-empty languages. The KDoc ("An empty language with no identity means Off") describes a state the format cannot express; resolver tests passing `""` cover an impossible state.
- Failure scenario: a reader assumes explicit Off is remembered; it is not: `NONE` falls through to "nothing resolves" and the shown row is re-sent; works today only because new media starts on Off.
- Fix: remove the branch and its KDoc sentence, or store explicit Off on purpose end to end.

### F3 [nit] Test checks a saved value that production would already have cleared
- Where: PlayerVMSubtitlePreferenceTest.kt (`savedSubtitleUnresolved_explicitOffAfterUnresolved_clearsStored`, second half)
- Problem: after the explicit Off saves `NONE`, the mock still returns the old `("rus", ".../gone.vtt")`, and the test expects episode 2's audio change to write that old value back. Pins mock wiring, not behaviour; name does not match.
- Failure scenario: a regression where the Off save is skipped and the old value survives still passes the second half.
- Fix: re-stub `getPreferredSubtitlePreference(42)` to `NONE` after the Off (or capture the save) and assert `NONE` passes through; otherwise drop the second half.
