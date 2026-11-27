# Brief: audio-preference-locale-independent-label

Kind: bug   Size: S   Tracks: none   Base: 6ccf2ebf
Device verification: yes (playback track restore; box smoke only, see AC-6)
Research: `research.md` (options, related runs, exposure window). Template: no `brief.md` in `/mnt/data/projects/agent-flow/templates/`; structure follows the analyst role.

## Problem

An API audio row with no language is labelled `<unknown-language prefix> · <author or type title>` (`app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerUIMapper.kt:21-36`); the prefix is a localized string (`res/values/player.xml:49` "Язык не указан", `res/values-en/strings.xml:303` "Unknown language"). `PlayerVM.saveTrackPreferences` (`app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1955-1981`) stores that label with `audioLang = null`. Released builds (`v1.12.8`) stored `Unknown · X` or `und · X` with `audioLang = "und"` (`research.md: Related work`).

## Root cause (confirmed, by trace)

`AudioTrackPreferenceResolver.findAudioTrackIndex` (`app/src/main/java/com/kino/puber/ui/feature/player/vm/AudioTrackPreferenceResolver.kt:11-25`), called from `PlayerVM.restoreAudioPreference` (`PlayerVM.kt:607-620`):

| Matcher | Line | Why it misses for a stored `Unknown language · X` against a row `Язык не указан · X` |
|---|---|---|
| `exactLabelMatch` | `:178-184` | prefix differs |
| `normalizedLabelMatch` | `:186-191` | strips only an `NN. ` prefix |
| `voiceTypeAndLanguageMatch` | `:193-203` | stored language null; row language `""` |
| `languageMatch` | `:205-211` | stored null: skipped; stored `"und"`: row language is `""` |

Result: the default track plays. Exposure is limited to restores against API rows (before the first Media3 track report, `research.md: Current state in code`).

## Fix (option A, `research.md: Options`)

Add a resolver matcher, after `normalizedLabelMatch`: when `knownLanguageOrNull(preferredLang)` is null (`app/src/main/java/com/kino/puber/ui/feature/player/model/SubtitleLanguage.kt:14`), take the part of `preferredLabel` after the first ` · `; if non-empty, return the first row whose `language` is empty and whose label has the same part after its first ` · `. Stored format unchanged.

## Acceptance criteria

| ID | Given | When | Then |
|---|---|---|---|
| AC-1 | rows `[ru · A, Язык не указан · X]` (second has language `""`), stored label `Unknown language · X`, lang null | `findAudioTrackIndex` | returns 1 |
| AC-2 | rows `[en · A, Unknown language · X]`, stored `Язык не указан · X`, lang null | `findAudioTrackIndex` | returns 1 |
| AC-3 | rows `[Язык не указан · X]`, stored `Unknown · X` with lang null, and stored `und · X` with lang `"und"` | `findAudioTrackIndex` | returns 0 for each |
| AC-4 | stored lang `"ru"`, label `Unknown · X`, rows `[Язык не указан · X, ru · B]` | `findAudioTrackIndex` | returns 1 (title match does not apply when the stored language is known) |
| AC-5 | stored label with no ` · ` part or an empty title, lang null; or a title absent from all empty-language rows | `findAudioTrackIndex` | returns `NO_MATCH` |
| AC-6 | existing matchers (exact, normalized `NN. `, voice type + language, language) | unit tests added for each | each passes unchanged; check.full green; on the box an item with a stored Media3 dubbing still restores it after reopening the player |

## Risks

- A title shared by two empty-language rows resolves to the first (accepted by option A).
- `findAudioTrackIndex` has no direct tests today (`app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/AudioTrackPreferenceResolverTest.kt` covers subtitles); AC-6 adds them before the change.
- The API-rows window is hard to hit on the device, so AC-1..AC-5 are proven by unit tests only.

## Open questions

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| Q1 | Option A, B (persisted API identity) or C (accept)? | A: resolver-only, no persisted-format change, covers released and cross-locale saves | yes |
| Q2 | Device verification for a resolver-only change? | Unit tests for AC-1..AC-5; box smoke for AC-6 only | no |

## Plan

1. Tests first in `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/AudioTrackPreferenceResolverTest.kt`: cover the four existing matchers of `findAudioTrackIndex` (AC-6), then AC-1..AC-5 (failing).
2. `app/src/main/java/com/kino/puber/ui/feature/player/vm/AudioTrackPreferenceResolver.kt`: add `unknownLanguageTitleMatch(tracks, preferredLabel, preferredLang)` next to `normalizedLabelMatch` (`:186`) and insert it third in the matcher list (`:16-21`); use `knownLanguageOrNull` (import from `ui.feature.player.model`) and a private `String.titleAfterSeparator()` = `substringAfter(" · ", "").trim()` returning null when empty.
3. Run check.fast, then check.full; box smoke for AC-6.
