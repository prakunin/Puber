# Test design: subtitle-und-preferred-fallback

Paths: tests under `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/`. Bug fix, size S; base 874b049f.

## Risk profile

| ID | Risk | P | I | Score | Handling |
|----|------|---|---|-------|----------|
| R1 | Helper returns the raw "und" or a blank (regression to base behaviour or a wrong normalisation) | 2 | 2 | 4 | Unit tests T1, T2 |
| R2 | Helper strips or alters a known language ("ru", "rus", "en-US", "ai") | 2 | 3 | 6 | Unit test T2 (score 6, tested at unit level where it would fail) |
| R3 | `PlaybackController.kt:906` bypasses the helper (reverts to `track.language`) | 1 | 2 | 2 | Accepted: no JVM test reaches the controller (no Robolectric, brief Q2 = no instrumented case); caught by code read (AC-3) and the AC-4 device smoke only for a visible regression |
| R4 | Media3 outcome for "" differs from the bytecode reading | 1 | 2 | 2 | Accepted: research.md Risks; the value passed is pinned, Media3's pick is not |
| R5 | Playback regression for rows that do have a selector target or a known language | 1 | 3 | 3 | Device smoke M1 |

## Traceability

| AC | Level | Priority | Test | Proves |
|----|-------|----------|------|--------|
| AC-1 | unit | P0 red | `preferredTextLanguage_isEmpty_forUndeterminedAndBlank` in `SubtitleTrackIdentityTest.kt`: inputs "und", "UND", "und-Latn", "", " ", null each return "" | Fails if the helper passes "und" through or returns null/blank text. Red on base: the helper does not exist (compile failure); the base expression's "und" to "" behaviour sits inline in the controller |
| AC-2 | unit | P0 red | `preferredTextLanguage_keepsKnownLanguage` in `SubtitleTrackIdentityTest.kt`: "ru", "rus", "en-US", "ai" return the input unchanged | Fails if the helper lowercases, trims subtags or empties a real language |
| AC-3 | review | P1 | Code read: the only `setPreferredTextLanguage` in `app/src/main` receives `preferredTextLanguage(track.language)` | Fails if the call is reverted to `track.language` or an inline expression; no automated test, see UNTESTABLE |
| AC-4 | device | P1 | Manual M1 | Fails if a language row does not render or "Off" still renders text |
| AC-5 | check | P0 guard | `check.full` from `.flow/config.yaml`, including the existing `SubtitleTrackSelectorTest` `select_doesNotMatchByLanguage_whenBothAreUndetermined` and `knownLanguageOrNull_*` tests | Fails if the selector or `knownLanguageOrNull` changed |

Both P0 tests assert the returned string only. Style: JUnit 5 and the neighbouring `knownLanguageOrNull_*` tests (`SubtitleTrackIdentityTest.kt:78,85`); no fakes needed (pure function, no mocks). Parameterise over the input lists or loop with a message naming the input.

## Bugs

Failing-first test: `preferredTextLanguage_isEmpty_forUndeterminedAndBlank`. On base (`git -C <wt> show 874b049f:app/src/main/java/com/kino/puber/ui/feature/player/model/SubtitleLanguage.kt`) no `preferredTextLanguage` exists, so it fails to compile; after step 1 of the plan it passes. The implementer adds the helper stub-free, so write the test first and run it against the unchanged code to see the compile failure. Note this is a pin: the base value passed to Media3 is already "", the test turns the owner's decision (Q1 = A) into a tested contract.

## Manual checks

M1 (AC-4, box `192.168.1.110:5555`, `com.kino.puber.stage` only, lease, install-preserve of the worktree dev APK, one user's request pace):
1. Open an item that has subtitles and start playback.
2. Open Settings, Subtitles with the D-pad; pick a language row. Expected: that subtitle renders.
3. Reopen Settings, Subtitles; pick "Off". Expected: no subtitle renders.
4. Pick a language row again. Expected: subtitle renders again.
Must not happen: crash, focus lost in the overlay, the picker showing a row while another track renders, text with "Off" selected.

## UNTESTABLE

- AC-3: the controller has no JVM test seam (no Robolectric; instrumented case declined in Q2). Covered by code read at review.
