CODEX: ran: review (no adversarial pass, size S)
CONFIRMED:
- none
REJECTED:
- [P2] `AudioTrackPreferenceResolver.kt:205-207`: `unknownLanguageTitleMatch` can pick an unrelated Media3 track. The saved manifest label `Stereo · English` has no language, so if that track disappears, the matcher takes `Commentary · English` because the text after " · " is the same. The code path exists. `buildAudioRows` passes `format.label` through unchanged, and the matcher compares only the text after " · " on rows with an empty language. I found no evidence that real manifests produce labels containing " · ". The scenario also needs the saved track to be gone while a different track shares the same suffix. The " · " in the repo comes from the app's own placeholder format, from preview data and from subtitle labels. I rejected it as `unproven`. The fallback only fires after the exact and normalized label matches fail, and only when the saved language is unknown. Without it, the next matchers (voice type plus language, then language only) would run anyway. The worst case is a wrong pick among tracks that already have no language.
VERDICT: clean
