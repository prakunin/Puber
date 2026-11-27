# Brief: subtitle-und-preferred-fallback

Input: `research.md` (cited as `research.md: <section>`). No brief template exists in the templates folder; usual brief sections. Paths below are under `app/src/main/java/com/kino/puber/ui/feature/player/` unless written in full.

## Request

"An undetermined subtitle row with no selector match no longer falls back to an \"und\" track" (`state.md` title; Log 2026-11-27, source player-audio-und-label judge-1 and review-tests-2.md F1).

## Classification

| Field | Value |
|-------|-------|
| kind | bug |
| size | S under Q1 = A: one goal, 3 files, behaviour-neutral (the value passed to Media3 does not change). Q1 = B or C changes playback track selection, a risk area (`.flow/roles/analyst.md` "Risk areas"), and makes it M |
| tracks | none |
| on-device verification | yes: production source in playback (`vm/PlaybackController.kt`) changes. A dev-APK smoke on the box `192.168.1.110:5555`, `com.kino.puber.stage` only (AC-4) |

## Root cause (confirmed in code; Media3 outcome plausible, from 1.11.0 bytecode, not reproduced)

| Step | Where |
|------|-------|
| With no selector target no override is set; the preferred text language decides | `vm/PlaybackController.kt:894-913`, comment `:899-901` |
| Preferred language is `knownLanguageOrNull(track.language).orEmpty()`, so "und" becomes "" (changed from `track.language` in 8c948972) | `vm/PlaybackController.kt:906`, `model/SubtitleLanguage.kt:14-17` |
| Media3 scores an exact "und" == "und" match 4 but "" matches nothing, so an "und" track no longer shows | research.md: Media3 1.11.0, read from bytecode |
| The selector never matches an "und" row by language (intended, AC-5 of player-audio-und-label) | `vm/SubtitleTrackSelector.kt:58,114`; test `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/SubtitleTrackSelectorTest.kt:109` |
| No test reads the preferred language; the line has no JVM test because it sits inside `PlaybackController` | research.md: Current state in code |

Reach: only an API row with `lang = "und"` (`model/PlayerUIMapper.kt:45`) whose identity match fails; no known caller (research.md: Current state in code). A blank row behaved the same on base.

## Scope (research.md: Options, A)

1. Pure helper in `model/SubtitleLanguage.kt` that returns the preferred text language for a subtitle row: the known language, or "" for blank or "und".
2. `vm/PlaybackController.kt:906` calls it; the comment at `:899-901` says an undetermined or blank row with no target shows no subtitles.
3. Unit tests pin the helper.

## Acceptance criteria

AC-1. Given a subtitle row language of "und", "UND", "und-Latn", "", " " or null, When the preferred text language is computed, Then it is "" (unit, `SubtitleTrackIdentityTest.kt`).

AC-2. Given a row language of "ru", "rus", "en-US" or "ai", When the preferred text language is computed, Then it is the input unchanged (unit).

AC-3. Given `vm/PlaybackController.kt`, When `applySubtitleTrackSelection` sets the preferred text language, Then it passes the helper's result and nothing else (code read at review; the call is the only `setPreferredTextLanguage` in `app/src/main`).

AC-4. Given the dev APK on the box and an item with subtitles, When the user opens Settings, Subtitles and picks a language row, Then that subtitle renders; Given "Off", Then none renders (device, screenshots).

AC-5. Given the change, Then `check.full` from `.flow/config.yaml` passes.

## Plan (Q1 = A)

1. `model/SubtitleLanguage.kt`: after `knownLanguageOrNull` (`:14`) add `internal fun preferredTextLanguage(language: String?): String = knownLanguageOrNull(language).orEmpty()` with a KDoc: "" makes Media3 prefer no track, so an undetermined row without an override shows nothing rather than an arbitrary "und" track.
2. `vm/PlaybackController.kt:906`: replace the expression with `preferredTextLanguage(track.language)`; update the import at `:50` if `knownLanguageOrNull` is no longer used there; reword the comment at `:899-901`.
3. `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/SubtitleTrackIdentityTest.kt`: next to `knownLanguageOrNull_*` (`:78,85`) add tests for AC-1 and AC-2.
4. `./tools/agentw :app:compileDevDebugKotlin`, then `check.full`; AC-4 on the box with install-preserve.

## Risks

- AC-1/AC-2 prove the value passed, not Media3's pick; that rests on the bytecode reading (research.md: Risks).
- Conflicts: `playback-controller-split` (same controller file).

## Open questions

| ID | Question | Recommended answer | Blocks |
|----|----------|--------------------|--------|
| Q1 | For an "und" API row the selector cannot match: subtitles stay off (A), Media3 picks an "und" track (B), or an exact match to a single "und" track (C)? (research.md: Options) | A: consistent with AC-5 of player-audio-und-label and the owner's accepted "an unmatched row may show nothing" (subtitle-upstream-port Q3) | yes (B/C change AC-1, size M) |
| Q2 | Add an instrumented case asserting `preferredTextLanguages` on the emulator? | No: the helper test pins the value | no |
