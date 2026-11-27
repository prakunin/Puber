# Research: subtitle-und-preferred-fallback

## Request

"An undetermined subtitle row with no selector match no longer falls back to an \"und\" track" (state.md title). Log: created from player-audio-und-label judge-1 (CONCERNS) and review-tests-2.md F1. `PlaybackController.applySubtitleTrackSelection` now passes `knownLanguageOrNull(track.language).orEmpty()` to `setPreferredTextLanguage`. Media3 1.11.0 `getFormatLanguageScore` returns 4 for an exact "und" match before it normalizes "und", so on base a preferred "und" picked an "und" track when `SubtitleTrackSelector.select` returned null; now no subtitles show. No known caller reaches it (needs an "und" row whose identity match fails). Options: pass `track.language` unchanged when the selector finds no target, or add a test pinning the intended outcome.

## Understanding

When the user picks a subtitle row tagged "und" and the selector cannot tie it to one player track, the app sets no override and leaves the choice to Media3's preferred text language. Before player-audio-und-label (base cb7fcd45) the app sent "und", which Media3 matches exactly against any text track tagged "und"; master sends "", which matches nothing, so the row shows as selected and no text renders. The owner has to choose which outcome is intended and pin it with a test; the line has none today.

## Current state in code

- `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:894-913` `applySubtitleTrackSelection`: clears text overrides, enables text, sets the preferred language at `:906`, sets an override only when the selector returns a target. Comment `:899-901`: with no target, "the preferred language chooses instead".
- `:906` changed from `track.language` in 8c948972 (player-audio-und-label fix d1ed06ad, squashed). When a target exists the override decides and `:906` has no visible effect.
- `vm/SubtitleTrackSelector.kt:42-53` `select`: semantic filter, then format id, source identity (url/file vs prefix-stripped `formatId`/`formatLabel`), group id + index, then language; each reduced with `singleOrNull`.
- `vm/SubtitleTrackSelector.kt:58` an "und" row passes every candidate through the language filter; `:114` `matchByLanguage` never matches an "und" row (AC-5 of player-audio-und-label, test `SubtitleTrackSelectorTest.kt:109` `select_doesNotMatchByLanguage_whenBothAreUndetermined`).
- `model/SubtitleLanguage.kt:14-17` `knownLanguageOrNull`: null for blank and "und" (with subtags).
- Where an "und" row can still come from: only API rows, `model/PlayerUIMapper.kt:45` (`language = sub.lang` unchanged). Player-built rows never carry "und" (`vm/PlayerTextTracks.kt:47`).
- Where an "und" track comes from: side-loads get `SubtitleConfiguration.setLanguage(sub.lang)` (`vm/PlaybackMediaItemFactory.kt:49,67`), which Media3's `DefaultMediaSourceFactory` copies into `Format.language` (javap, `Format$Builder.setLanguage` after `SubtitleConfiguration.language`); embedded or manifest tracks tagged "und". Side-loads carry only `SELECTION_FLAG_FORCED`, never `DEFAULT` (`PlaybackMediaItemFactory.kt:50`).
- Side-load id and label are the url's stable key (`PlaybackMediaItemFactory.kt:46-53`), so `matchBySourceIdentity` normally finds the side-load; the selector returns null for an "und" API row only when two side-loads share a stable key, the forced flag or readable label disagree (subtitle-sideload-merge review-codex-1), or the side-load is not among the current text groups.
- App never sets `selectUndeterminedTextLanguage` or `selectTextByDefault` (grep of `app/src/main`); both default false (javap `TrackSelectionParameters$Builder.<init>` offsets 121-123, 155-157).
- Tests: no test reads `preferredTextLanguages` (review-tests-2 F1; grep confirms). `PlaybackController` has no JVM unit test; no Robolectric in `gradle/libs.versions.toml`. The instrumented `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerNetworkTracksTest.kt` drives a real ExoPlayer (`progressiveSwitch_restoresSelectedSubtitleAndPausedIntent` at `:247` is the closest subtitle case).

### Media3 1.11.0, read from bytecode

Source: `~/.gradle/caches/modules-2/files-2.1/androidx.media3/media3-exoplayer/1.11.0/.../media3-exoplayer-1.11.0.aar` (sha256 29d4827d...) and `media3-common-1.11.0.aar` (sha256 6641b377...), `javap -c -p`. No sources jar is in the cache.

| Step | Bytecode | Result |
|------|----------|--------|
| `TrackSelectionParameters.Builder.setPreferredTextLanguage(s)` | wraps `s` in an array, `setPreferredTextLanguages` → `normalizeLanguageCodes` → `Util.normalizeLanguageCode`; also sets `usePreferredTextLanguagesAndRoleFlagsFromCaptioningManager = false` | list is `["und"]` or `[""]` |
| `Util.normalizeLanguageCode` offsets 15-32 | empty or "und" → keeps the input | "und" stays "und", "" stays "" |
| `DefaultTrackSelector.getFormatLanguageScore(format, pref, selectUndetermined)` offsets 0-19 | `!TextUtils.isEmpty(pref) && pref.equals(format.language)` → 4 | pref "und" vs format "und": 4 |
| same, offsets 20-54 | both normalized via `normalizeUndeterminedLanguageToNull` ("" or "und" → null); if either is null → `selectUndetermined && formatLang == null ? 1 : 0` | pref "" vs anything: 0; pref "und" vs null/""/"ru": 0 |
| `TextTrackInfo.<init>` offsets 286-391 | eligible if `preferredLanguageScore > 0`, or (empty preferred list and role flags / label match), or `isDefault`, or (`isForced` and the selected-audio language score > 0), or `selectTextByDefault` | "und" pref: every "und"-tagged track eligible; "" pref: a non-empty list, so only default-flagged or audio-matching forced tracks |
| `TextTrackInfo.compareTo` | renderer capability, preferred-language index and score, role flags, label, isDefault, isForced... | among several "und" tracks Media3 picks one by these flags, then group order (`unverified` beyond the fields shown) |

Outcome for an "und" API row with no selector target:

| Text tracks present | Base (`"und"`) | Master (`""`) |
|---------------------|----------------|----------------|
| one track tagged "und" | that track shows | nothing shows (unless the track is DEFAULT or a forced track matching the audio language) |
| several tracks tagged "und" | Media3's best-ranked "und" track, possibly not the one picked | nothing shows |
| no "und" track | nothing (same rule as master) | nothing |

A blank row behaved like master on base too: `setPreferredTextLanguage("")` scores 0 everywhere.

## Related work

| Item | Relation |
|------|----------|
| player-audio-und-label (done, merge 53f9b97e) | Source run. judge-1.md Concerns 1 corrects triage-2's "behaviour-neutral" rationale; review-tests-2.md F1; review-state-path-1.md F1 proposed the `:906` change; spec `docs/superpowers/specs/2026-11-27-player-audio-und-label.md` AC-5 (`:61`), risk R8 (`:166`, accepted as "same path as blank rows"). |
| subtitle-upstream-port | Spec `docs/superpowers/specs/2026-10-14-subtitle-upstream-port.md` AC-4 (`:42`), D2 (`:69`): no positional match; with no match only the preferred language applies. Owner accepted that an unmatched row may show nothing (Q3, `:17`, `:146`). |
| subtitle-sideload-merge (done) | review-codex-1.md: a forced-flag disagreement makes the selector return null and the preferred-language fallback play a different track. |
| player-audio-unknown-language (idea) | Same area (API language mapping, `PlayerUIMapper.kt`), different symptom. |

Duplicate: none.

## External findings

Off (local research only). Media3 behaviour is taken from the 1.11.0 bytecode above, as the brief asks.

## Options

| Option | Change | Trade-offs | Size |
|--------|--------|-----------|------|
| A. Keep "", pin it (recommended) | Extract the preferred-language choice at `PlaybackController.kt:906` into a pure helper next to `knownLanguageOrNull`; JVM tests for "und", "", "ru"; reword the comment at `:899-901` to say an undetermined row with no target shows nothing. | Consistent with AC-5 (no pick by "und" alone) and with blank rows; matches the owner's accepted "an unmatched row may show nothing" (subtitle-upstream-port Q3). Loses the base fallback for a single "und" track. | S |
| B. Restore "und", pin it | Revert `:906` to `track.language` (or have the helper return the raw "und"); same helper tests. | Shows the only "und" track when identity fails. With several "und" tracks Media3 picks by flags and order, the guess AC-5 removed from the selector; the picker may show one row while another track plays. | S |
| C. Deterministic unique-"und" match | In the selector, after the identity rules, accept a single candidate whose raw format language is "und" for an "und" row and set an exact override; keep "" at `:906`. | Keeps the useful single-track case without Media3's ambiguous pick; needs the raw language on `PlayerTextTrack` (now nulled at `PlayerTextTracks.kt:88`) and changes the AC-5 test `SubtitleTrackSelectorTest.kt:109`. | S |

Recommended: A. The path has no known caller, both alternatives re-add a language-only guess, and A turns the judge's concern into a tested decision.

## Risks

- The helper test proves the value passed, not what Media3 then selects; Media3's outcome rests on the bytecode reading above. Proving it at runtime needs an instrumented case in `PlaybackControllerNetworkTracksTest` on the box (`make DEVICE=192.168.1.110:5555 itest`).
- Option B or C affects playback selection, so it needs on-device verification under AGENTS.md; option A changes no runtime value and only extracts a helper.
- Whether the KinoPub API ever returns `lang = "und"` is unknown (player-audio-und-label spec Q3); settle it only from requests seen at human pace, never by probing.

## Open questions

| ID | Question | Recommended answer |
|----|----------|--------------------|
| Q1 | For an "und" API row the selector cannot match, should subtitles stay off (A), let Media3 pick an "und" track (B), or match a single "und" track exactly (C)? | A: stay off, pinned by a helper unit test. |
| Q2 | Add an instrumented case asserting `preferredTextLanguages` on the box as well? | No: the helper unit test pins the value; the Media3 outcome is settled from bytecode. |

## Sources

- Files: `vm/PlaybackController.kt:894-913`, `vm/SubtitleTrackSelector.kt`, `model/SubtitleLanguage.kt`, `vm/PlayerTextTracks.kt:36-93`, `vm/PlaybackMediaItemFactory.kt:40-75`, `model/PlayerUIMapper.kt:45`, `SubtitleTrackSelectorTest.kt:109,186`, `PlaybackControllerNetworkTracksTest.kt` (all under `app/src/...` of /home/dev/Puber).
- Commits: 8c948972 (the `:906` change), c6ea3789, f1be246e (earlier history of the function).
- Runs: player-audio-und-label (judge-1, review-tests-2, review-state-path-1/2, triage-2, state), subtitle-sideload-merge (research, review-codex-1), subtitle-upstream-port spec, player-audio-unknown-language state.
- Media3 1.11.0 bytecode: `DefaultTrackSelector.getFormatLanguageScore`, `normalizeUndeterminedLanguageToNull`, `DefaultTrackSelector$TextTrackInfo.<init>`/`compareTo`, `TrackSelectionParameters$Builder.<init>`/`setPreferredTextLanguage(s)`/`normalizeLanguageCodes`, `Util.normalizeLanguageCode`, `DefaultMediaSourceFactory` (version from `gradle/libs.versions.toml:27`).
