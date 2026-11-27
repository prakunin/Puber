# Test design: player-audio-und-label

ACs: `brief.md#acceptance-criteria`. Decisions and changed files: `design-architect.md`. Unit tests are under `app/src/test/kotlin/com/kino/puber/ui/feature/player/`, instrumented under `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/`.

## Risk profile

| # | Risk | P | I | Score | Covered by |
|---|---|---|---|---|---|
| R1 | An "und" row is not a fallback, so an audio or subtitle pick overwrites the stored dubbing with `und` (the reported bug) | 3 | 3 | 9 | AC-3 unit, P0 |
| R2 | The helper over-matches (prefix `und` without a separator, canonicalised return value), so a real language changes and audio/subtitle restore breaks | 2 | 3 | 6 | `knownLanguageOrNull_keepsRealLanguagesVerbatim`, existing language guards |
| R3 | Case or tag variants ("UND", "und-Latn") slip through and still show a raw code | 2 | 2 | 4 | AC-1 unit |
| R4 | An "und" subtitle row still matched by language in selector or rendition lookup, picking the wrong track | 2 | 2 | 4 | AC-5 unit |
| R5 | Rendition language does not fill an "und" format; subtitle row stays nameless | 2 | 2 | 4 | AC-4 unit |
| R6 | Media3 on device reports the fixture's language differently from the JVM `Format.Builder`, so the unit tests miss the real path | 1 | 2 | 2 | AC-6 instrumented |
| R7 | Stored `audioLang = "und"` from before the fix no longer matches; restore uses the default track until the next pick | 2 | 1 | 2 | accepted: harmless, cleared by the next real pick |
| R8 | Blank row language now passed to `setPreferredTextLanguage` for former "und" rows (`vm/PlaybackController.kt:905`) | 1 | 2 | 2 | accepted: same path as existing blank rows |
| R9 | Merge conflicts with `playback-controller-split`, `subtitle-restore-model` | 2 | 1 | 2 | accepted: `check.full` after rebase (AC-8) |
| R10 | New function outside the baseline profile | 1 | 1 | 1 | accepted: one string compare per track report |

## Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 red | `PlayerAudioTracksTest.buildAudioRows_undeterminedLanguage_usesFallbackString` | Formats "und", "UND", "und-Latn" without label give "Track 1..3", `isFallbackLabel` true, `language` ""; fails while `buildAudioRows` uses `format.language` raw |
| AC-1 | unit | P1 | `SubtitleTrackIdentityTest.knownLanguageOrNull_treatsUndeterminedAsUnknown` | Null for null, "", " ", "und", "UND", " und ", "und-Latn", "und_US"; fails if a case or tag form is dropped (`Format.Builder` lowercases before `buildAudioRows`, so only this test sees raw variants) |
| AC-1 | unit | P0 guard | `SubtitleTrackIdentityTest.knownLanguageOrNull_keepsRealLanguagesVerbatim` | "ru", "rus", "en-US", "ai", "undefined" come back unchanged; fails on a bare `startsWith("und")` or a canonicalised return (R2) |
| AC-1 | unit | P0 guard | `PlayerAudioTracksTest.buildAudioRows_labelThenLanguage_keepsManifestName` (existing) | Known language rows keep name and language |
| AC-2 | unit | P0 red | `PlayerAudioTracksTest.buildAudioRows_labelledUndeterminedLanguage_keepsLabelWithoutLanguage` | Label "English" + "und" gives label "English", `isFallbackLabel` false, `language` ""; fails while language "und" is kept |
| AC-3 | unit | P0 red | `PlayerVMTrackSelectionTest.selectAudioTrack_undeterminedRowFromPlayer_keepsStoredAudioPreference` | Row built by `buildAudioRows` from an "und" `Format` and reported through `onTracksUpdated`; `SelectAudioTrack(0)` saves `("rus", STORED_AUDIO_LABEL)`; fails on base, which saves `("und", "und")` |
| AC-3 | unit | P0 red | `PlayerVMTrackSelectionTest.selectSubtitle_whileAudioRowIsUndetermined_keepsStoredAudioPreference` | Same row, `SelectSubtitle(1)` keeps the stored audio; fails if only the audio path is guarded |
| AC-3 | unit | P0 guard | `PlayerVMTrackSelectionTest.selectTrack_savesLangToPrefs` (existing) | A real language row is still saved |
| AC-4 | unit | P0 red | `SubtitleLabelerTest.apply_undeterminedLanguageWithoutLabel_readsAsUnknownOrdinal` | Row language "und", no readable label reads "Субтитры 1", not "UND"; fails if `rawLanguageLabel` keeps "und" |
| AC-4 | unit | P0 guard | `SubtitleLabelerTest.unresolvableCodes_fallBackToTheCode_andMissingOnesToAPosition` (existing) | Other unresolvable codes still show the raw code |
| AC-4 | unit | P0 red | `PlayerTextTracksTest.manifestRow_undeterminedFormatLanguage_takesRenditionLanguage` | Format "und" matched by id to a rendition with "ru" (`Format.Builder` normalises "rus" to "ru") gives row language "ru"; fails while `format.language ?: ...` keeps "und" |
| AC-4 | unit | P1 red | `PlayerTextTracksTest.toPlayerTextTracks_undeterminedLanguage_isNull` | Candidate language null for an "und" format; fails if `toPlayerTextTracks` is left out |
| AC-5 | unit | P0 red | `SubtitleTrackIdentityTest.sameSubtitleLanguage_rejectsUndetermined` | `("und","und")`, `("und","UND")`, `("und","ru")` false; fails while `canonicalSubtitleLanguage` equality decides |
| AC-5 | unit | P0 red | `SubtitleTrackSelectorTest.select_doesNotMatchByLanguage_whenBothAreUndetermined` | Row language "und", no identity, single candidate language "und": `select` returns null; fails on base, which matches by language |
| AC-5 | unit | P1 guard | `SubtitleTrackSelectorTest.select_matchesByLanguage_onlyWhenUnambiguous` (existing) | Language matching still works for real codes |
| AC-6 | instrumented | P1 red | `PlaybackControllerNetworkTracksTest.progressiveUndeterminedAudio_reportsLocalizedFallbackRow` | `useMediaRoutes(required = setOf("progressive"))`, prepare `/media/progressive.mp4`; first `PlaybackEvent.Tracks` audio row has label `R.string.player_audio_track_fallback` (1), `isFallbackLabel` true, `language` ""; fails on base with label "und". Run: `make DEVICE=host.docker.internal:5555 itest TESTS=com.kino.puber.ui.feature.player.vm.PlaybackControllerNetworkTracksTest` |
| AC-7 | manual | P1 | Manual check 1 | Named manifest audio and subtitle rows unchanged on a real item |
| AC-8 | unit | P0 guard | `check.full` (`.flow/config.yaml`) | Whole suite, detekt, lint stay green |

Notes for the implementer:

- AC-3 tests build a `Format`, so `PlayerVMTrackSelectionTest` needs the `TextUtils` static stub from `PlayerAudioTracksTest.kt:23-32`; scope it to those tests or the class.
- Each `red` row is run on the unchanged code first and must fail on an assertion, not only on compile. Tests that call the new `knownLanguageOrNull` cannot run on base; they are P1 or guard for that reason.

## Failing-first test (bug)

`PlayerVMTrackSelectionTest.selectAudioTrack_undeterminedRowFromPlayer_keepsStoredAudioPreference`. On base `cb7fcd45`, `vm/PlayerAudioTracks.kt:9-14` yields `label = "und"`, `isFallbackLabel = false`, so `vm/PlayerVM.kt:1966-1977` saves `audioLang = "und"`, `audioLabel = "und"` and the `verify` for `("rus", STORED_AUDIO_LABEL)` fails. Written first, together with `PlayerAudioTracksTest.buildAudioRows_undeterminedLanguage_usesFallbackString`.

## Manual checks

Device: dev APK of this worktree, box `192.168.1.110:5555`, `com.kino.puber.stage` only, installed with `tools/android-apk-install-preserve` under a lease; remote input through `adb -s 192.168.1.110:5555 shell input keyevent`.

1. Open an item with several named dubbings and subtitles (HLS), start playback.
2. `KEYCODE_DPAD_CENTER` to show controls, open Settings, then Audio (D-pad).
   - Expected: rows read their manifest names (for example "01. Дубляж (RUS)"), same as on the master build.
   - Must not: a row named "und", "Track N" in place of a manifest name, focus loss, crash.
3. `KEYCODE_BACK`, open Subtitles.
   - Expected: rows read localized language names ("Русский", "Английский"), Off row present.
   - Must not: "UND", "Субтитры N" in place of a known language.
4. Pick another audio row, leave the player (`KEYCODE_BACK`), reopen the same item.
   - Expected: the picked dubbing is restored.
   - Must not: the default track after a named pick.
5. Screenshot steps 2 and 3 into the evidence file; run `tools/mobile-evidence-audit.sh` on the evidence dir.

## Perf

N/A: perf track is off.
