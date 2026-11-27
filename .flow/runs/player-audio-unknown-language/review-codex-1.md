CODEX: ran: review (the adversarial pass is not required for size S)
CONFIRMED:
- [minor] /home/dev/orca/workspaces/Puber/player-audio-unknown-language/app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerUIMapper.kt:23-27 - The saved audio label is now a localized display string, so old and new saves no longer match.
  - Scenario: an item's API audio row has lang null and author "X". The old build saved the label "Unknown · X" with an empty language. After the update the row is labelled "Язык не указан · X", and `exactLabelMatch` and `normalizedLabelMatch` miss it.
  - The language fallback cannot rescue it, because `languageMatch` skips an empty language. The same happens for rows where the old label was "und · X" and the language was "und". The saved dub is lost once and the default track plays.
  - The label also changes with the app locale, so a pick saved under one locale will not restore under the other.
  - It is a narrow, one-time regression on rows with an unknown language and a title, and it only applies when the API rows are the ones kept.
  - Fix, if wanted: keep a locale-independent identity for these rows, or have the resolver also accept the legacy "Unknown · " prefix.
REJECTED:
- A blank author or type title hides a usable type title, or leaves a dangling " · " separator and a wrong `isFallbackLabel` - unproven. There is no evidence the API sends blank titles. The old `?.let` code had the same behaviour, so the branch adds no new exposure.
VERDICT: changes needed (one minor finding; the owner may accept it as a one-time label migration cost)
