# Audio row shows the raw language code "und" for an undetermined-language track

Status: approved
Date: 2026-11-27
Slug: player-audio-und-label
Kind: bug
Size: M
Tracks: none

## Problem / Why

### Request

"Audio row shows the raw language code \"und\" for an undetermined-language track" (`state.md` title, Log 2026-11-27); conductor addition: check subtitle naming for the same issue.

### Classification

| Field | Value |
|-------|-------|
| kind | bug |
| size | M: one goal, about 3 main files, but in risk areas playback (track report, subtitle track matching) and persistence (stored audio preference), `.flow/roles/analyst.md` "Risk areas" |
| tracks | none (row text only, no layout change) |
| on-device verification | yes: player track report and preference save. Instrumented ACs on the emulator `host.docker.internal:5555`; dev-APK regression on the box `192.168.1.110:5555`, `com.kino.puber.stage` only |

### Root cause

Observed on the fixture `player-test-fixtures/src/main/assets/player-fixtures/progressive.mp4` during `manual-quality-hls4` (research.md: Current state); Media3 1.11.0 returns MP4 `mdhd` "und" verbatim and `Format.Builder` keeps it (research.md: External findings).

| Step | Where |
|------|-------|
| Audio row name is `label ?: language`, so an unlabelled "und" track is named "und" | `vm/PlayerAudioTracks.kt:9,12` |
| Row is not a fallback (`isFallbackLabel = name == null` is false), language "und" | `vm/PlayerAudioTracks.kt:13-14` |
| A later audio/subtitle pick saves `audioLang = "und"`, `audioLabel = "und"` over the stored dubbing | `vm/PlayerVM.kt:1966-1977` |
| Subtitle row keeps "und" and it wins over a rendition language | `vm/PlayerTextTracks.kt:46` (`format.language ?: renditionFormat?.language`) |
| No display name for "und", so the labeler shows raw "UND" before a readable label fallback is exhausted | `model/SubtitleLanguage.kt:41-50`, `model/SubtitleLabeler.kt:42-50` |
| "und" matches "und" as a real language in rendition lookup, selector and merger | `model/SubtitleLanguage.kt:8-11`, `vm/PlayerTextTracks.kt:69,87`, `vm/SubtitleTrackSelector.kt:110-116`, `vm/SubtitleTrackMerger.kt:104-105` |

### Scope

1. One helper next to `canonicalSubtitleLanguage` (`model/SubtitleLanguage.kt:13`) that returns null for a blank or "und" language (case-insensitive, and `und-*` tags).
2. Audio: `buildAudioRows` uses it, so an unlabelled "und" row gets the localized fallback label, `isFallbackLabel = true`, `language = ""`; a labelled "und" row keeps its label with `language = ""`.
3. Subtitles: `buildSubtitleRow` and `toPlayerTextTracks` use it (a rendition language can fill in); `sameSubtitleLanguage` is false when either side is "und".

### Risks

- A viewer with "und" already stored keeps it; after the fix no row matches it and restore falls to the default track until the next real pick (`vm/AudioTrackPreferenceResolver.kt:201,210`).
- Two unnamed "und" subtitle tracks lose the language fallback in restore; identity matchers (format id, uri, group/index) still apply.
- Whether KinoPub `http` MP4 files carry "und" audio is unverified; AC-6 rests on the fixture. No API probing; the box shows it only if ordinary playback hits such a file.
- Conflicts: `playback-controller-split`, `subtitle-restore-model` (same controller and subtitle files).

## Acceptance criteria

AC-1. Given audio formats with language "und" (also "UND", "und-Latn") and no label, When `buildAudioRows` runs, Then each row's label is the fallback for its position, `isFallbackLabel` is true and `language` is empty (unit, `PlayerAudioTracksTest.kt`).

AC-2. Given an audio format with label "English" and language "und", When rows are built, Then the label is "English", `isFallbackLabel` is false and `language` is empty (unit).

AC-3. Given a stored audio preference and an "und" unlabelled audio row shown, When the user picks an audio or subtitle row, Then the stored audio label and language are unchanged (unit, `PlayerVMTrackSelectionTest.kt`).

AC-4. Given a text format with language "und" and no readable label, When subtitle rows are labelled, Then the row reads the localized `player_subtitle_unknown` ordinal, not "UND"; Given a rendition with language "rus" for that format, Then the row's language is "rus" (unit, `SubtitleLabelerTest.kt` and a `PlayerTextTracks` test).

AC-5. Given two "und" tracks, When `sameSubtitleLanguage` compares them, Then it is false, and the selector does not pick a candidate by language alone (unit, `SubtitleTrackSelectorTest.kt`).

AC-6. Given the `progressive.mp4` fixture played by `PlaybackController`, When tracks are reported, Then the audio row reads the localized fallback label and not "und" (instrumented on the emulator through `make DEVICE=host.docker.internal:5555 itest`, pattern of `app/src/androidTestInstrumentation/.../PlaybackControllerNetworkTracksTest.kt:169`).

AC-7. Given an item with named audio tracks on the box, When the player Settings Audio list is opened, Then the rows keep their manifest names and subtitle rows keep their language names (device, dev APK, screenshot).

AC-8. Given the change, Then `check.full` from `.flow/config.yaml` passes.

## Out of scope

Not listed separately in the brief; see Scope above and the answered questions below.

## Design

Paths are under `app/src/main/java/com/kino/puber/ui/feature/player/` unless written in full. ACs: `brief.md#acceptance-criteria`.

### Decisions

| Question | Decision | Why |
|---|---|---|
| Where is "und" recognised? | One helper `knownLanguageOrNull(language: String?): String?` in `model/SubtitleLanguage.kt`, next to `canonicalSubtitleLanguage` (`model/SubtitleLanguage.kt:13`). Null for null, blank, "und" (any case, after trim) and tags starting `und-` or `und_`; otherwise the input unchanged | One rule for audio and subtitles (Q1, option B); returning the input keeps every known value byte-identical |
| Where is it applied? | At the boundary where Media3 formats become rows: `buildAudioRows`, `buildSubtitleRow`, `toPlayerTextTracks`; plus `sameSubtitleLanguage` and the labeler's raw-code fallback for values that arrive from elsewhere (API rows, stored preferences) | Downstream code already treats a blank language as unknown (`vm/SubtitleTrackSelector.kt:55,110`, `vm/AudioTrackPreferenceResolver.kt:109,169`, `vm/SubtitleTrackMerger.kt:41`), so normalising at the boundary needs no change there |
| How is the stored audio preference protected (AC-3)? | No VM change: an unlabelled "und" row now has `isFallbackLabel = true`, which the existing guard `vm/PlayerVM.kt:1966` honours | The guard from `d736f9b9` already covers "Track N" rows |
| Labelled "und" audio row (AC-2) | Label kept, `isFallbackLabel = false`, `language = ""` | A manifest name is a real dubbing name; it is saved as `audioLabel` with `audioLang = null` (`vm/PlayerVM.kt:1969-1972`) |
| Does a rendition language fill an "und" subtitle format? | Yes: `knownLanguageOrNull(format.language) ?: knownLanguageOrNull(renditionFormat?.language).orEmpty()` | AC-4 second clause |
| Change `canonicalSubtitleLanguage("und")` to ""? | No; `sameSubtitleLanguage` checks `knownLanguageOrNull` on both sides first | An empty canonical code would make two "und" values compare equal at `model/SubtitleLanguage.kt:10` |

### Changes by layer

#### API / repository

N/A: the API path (`model/PlayerUIMapper.kt:20-33`) is Q2, a separate run.

#### ViewModel state and actions

| File | Change |
|---|---|
| `model/SubtitleLanguage.kt:8-11` | Add `knownLanguageOrNull`. `sameSubtitleLanguage` returns false when either side is null after `knownLanguageOrNull` (replaces the `isBlank` check at line 9) |
| `vm/PlayerAudioTracks.kt:9-14` | `val language = knownLanguageOrNull(format.language)`; `name = format.label ?: language`; `language = language.orEmpty()`; `isFallbackLabel = name == null` unchanged |
| `vm/PlayerTextTracks.kt:46` | Row language as in Decisions. `findRendition` (line 69) needs no edit: it goes through `sameSubtitleLanguage` |
| `vm/PlayerTextTracks.kt:87` | `language = knownLanguageOrNull(format.language)` |
| `model/SubtitleLabeler.kt:42-45` | `rawLanguageLabel = knownLanguageOrNull(track.language)?.trim()?.uppercase(Locale.ROOT)`, so "und" falls to `unknownLabel` (line 50) |
| `vm/PlayerVM.kt:1955-1980` | None (guard at line 1966) |
| `vm/PlaybackController.kt:865-887` | None: it already routes formats through `buildAudioRows` (line 872) and `buildSubtitleRows` (line 883) |

#### Screen and DI module

N/A: no screen, scope or binding changes.

#### Compose content

N/A: row text only; no layout change (brief: tracks none).

#### Resources

N/A: `player_audio_track_fallback` and `player_subtitle_unknown` exist (`app/src/main/res/values-en/strings.xml:301-302`).

#### Tests

| AC | File | Case |
|---|---|---|
| AC-1, AC-2 | `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerAudioTracksTest.kt` | "und", "UND", "und-Latn" unlabelled; "English" + "und" |
| AC-3 | `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMTrackSelectionTest.kt` | Pattern of `selectAudioTrack_fallbackRow_keepsStoredAudioPreference` (line 105) and `selectSubtitle_whileAudioRowIsFallback_keepsStoredAudioPreference` (line 114), with the reported row produced by `buildAudioRows` from an "und" format rather than hand-built (the helper at line 122 hand-builds) |
| AC-4 | `app/src/test/kotlin/com/kino/puber/ui/feature/player/model/SubtitleLabelerTest.kt`, `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerTextTracksTest.kt` | Row with language "und" reads the unknown ordinal; "und" format + "rus" rendition gives language "rus"; `toPlayerTextTracks` maps "und" to null |
| AC-5 | `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/SubtitleTrackSelectorTest.kt`, `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/SubtitleTrackIdentityTest.kt:62` | `sameSubtitleLanguage("und","und")` false; selector does not pick by language for an "und" row |
| AC-6 | `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerNetworkTracksTest.kt` | Next to `unlabelledAudio_reportsLocalizedFallbackRowMarkedAsFallback` (line 159), routes from `harness.useMediaRoutes(required = setOf("progressive"))` as in `PlaybackControllerDeviceTest.kt:34`, prepare `/media/progressive.mp4` |

### Alternatives rejected

- Audio only (research option A): leaves "UND" subtitle labels and "und" language matches.
- Map "und" inside `canonicalSubtitleLanguage`: makes two "und" values equal in `sameSubtitleLanguage`.
- `DefaultTrackSelector.normalizeUndeterminedLanguageToNull`: protected, not callable from app code (research: External findings).
- Change `SubtitleTrackMerger.orderingLanguage` (`vm/SubtitleTrackMerger.kt:104`): player rows already arrive blank; see Open questions.

### Risks

| Risk | Kind | Where |
|---|---|---|
| A row or stored subtitle preference still holding "und" (API row, value saved before the fix) is now incompatible with every non-blank candidate in the selector, instead of matching an "und" candidate; restore then relies on identity matchers or the preferred text language | correctness, data shape | `vm/SubtitleTrackSelector.kt:55-56`, `vm/AudioTrackPreferenceResolver.kt:153-159` |
| Stored `audioLang = "und"` no longer matches any row; restore falls to the default track until the next real pick | data shape | `vm/AudioTrackPreferenceResolver.kt:201,210` (brief Risks) |
| A blank row language is passed to `setPreferredTextLanguage`; unchanged behaviour for blank rows, now also for former "und" rows | correctness | `vm/PlaybackController.kt:905` |
| Parallel runs `playback-controller-split`, `subtitle-restore-model` edit the same files; merge conflicts in `vm/PlayerTextTracks.kt`, `model/SubtitleLanguage.kt` | rollout | files above |
| New top-level function absent from the baseline profile; one string compare per track report | performance | `app/src/main/generated/baselineProfiles/baseline-prof.txt:37743` |

### Open questions

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| Q3 | Also route `SubtitleTrackMerger.orderingLanguage` (`vm/SubtitleTrackMerger.kt:104-105`) through `knownLanguageOrNull`, so API rows with "und" go to `selectTracksWithoutLanguage` (line 53)? | No: no AC needs it and KinoPub API "und" subtitles are unverified | no |

## Test design

ACs: `brief.md#acceptance-criteria`. Decisions and changed files: `design-architect.md`. Unit tests are under `app/src/test/kotlin/com/kino/puber/ui/feature/player/`, instrumented under `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/`.

### Risk profile

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

### Traceability

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

### Failing-first test (bug)

`PlayerVMTrackSelectionTest.selectAudioTrack_undeterminedRowFromPlayer_keepsStoredAudioPreference`. On base `cb7fcd45`, `vm/PlayerAudioTracks.kt:9-14` yields `label = "und"`, `isFallbackLabel = false`, so `vm/PlayerVM.kt:1966-1977` saves `audioLang = "und"`, `audioLabel = "und"` and the `verify` for `("rus", STORED_AUDIO_LABEL)` fails. Written first, together with `PlayerAudioTracksTest.buildAudioRows_undeterminedLanguage_usesFallbackString`.

### Manual checks

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

### Perf

N/A: perf track is off.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Fix audio only, or audio and subtitles? | yes | answered | both, option B (recommended) |
| Q2 | Also replace the hardcoded "Unknown" API audio language (PlayerUIMapper.kt:20-33)? | no | answered | no, separate idea run player-audio-unknown-language (recommended) |
| Q3 | Route SubtitleTrackMerger.orderingLanguage through knownLanguageOrNull too? | no | answered | no (recommended) |
<!-- Blocks: yes when the answer changes scope or an AC. Status: open / assumed / answered. -->

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-27 | codex: required (owner); antigravity on every round (owner); caps lifted (owner) | owner |
| 2026-11-27 | Q1: audio and subtitles (option B) | owner (standing instruction: recommended) |
| 2026-11-27 | Q2: "Unknown" localization -> idea run player-audio-unknown-language | owner (standing instruction: recommended) |
| 2026-11-27 | Q3: orderingLanguage unchanged | owner (standing instruction: recommended) |
| 2026-11-27 | Gate 1: approved | owner (standing instruction: recommended) |
