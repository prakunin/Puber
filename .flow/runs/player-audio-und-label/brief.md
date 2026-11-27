# Brief: player-audio-und-label

Input: `research.md` (cited as `research.md: <section>`). No brief template exists in the templates folder; usual brief sections. Paths below are under `app/src/main/java/com/kino/puber/ui/feature/player/` unless written in full.

## Request

"Audio row shows the raw language code \"und\" for an undetermined-language track" (`state.md` title, Log 2026-11-27); conductor addition: check subtitle naming for the same issue.

## Classification

| Field | Value |
|-------|-------|
| kind | bug |
| size | M: one goal, about 3 main files, but in risk areas playback (track report, subtitle track matching) and persistence (stored audio preference), `.flow/roles/analyst.md` "Risk areas" |
| tracks | none (row text only, no layout change) |
| on-device verification | yes: player track report and preference save. Instrumented ACs on the emulator `host.docker.internal:5555`; dev-APK regression on the box `192.168.1.110:5555`, `com.kino.puber.stage` only |

## Root cause (confirmed)

Observed on the fixture `player-test-fixtures/src/main/assets/player-fixtures/progressive.mp4` during `manual-quality-hls4` (research.md: Current state); Media3 1.11.0 returns MP4 `mdhd` "und" verbatim and `Format.Builder` keeps it (research.md: External findings).

| Step | Where |
|------|-------|
| Audio row name is `label ?: language`, so an unlabelled "und" track is named "und" | `vm/PlayerAudioTracks.kt:9,12` |
| Row is not a fallback (`isFallbackLabel = name == null` is false), language "und" | `vm/PlayerAudioTracks.kt:13-14` |
| A later audio/subtitle pick saves `audioLang = "und"`, `audioLabel = "und"` over the stored dubbing | `vm/PlayerVM.kt:1966-1977` |
| Subtitle row keeps "und" and it wins over a rendition language | `vm/PlayerTextTracks.kt:46` (`format.language ?: renditionFormat?.language`) |
| No display name for "und", so the labeler shows raw "UND" before a readable label fallback is exhausted | `model/SubtitleLanguage.kt:41-50`, `model/SubtitleLabeler.kt:42-50` |
| "und" matches "und" as a real language in rendition lookup, selector and merger | `model/SubtitleLanguage.kt:8-11`, `vm/PlayerTextTracks.kt:69,87`, `vm/SubtitleTrackSelector.kt:110-116`, `vm/SubtitleTrackMerger.kt:104-105` |

## Scope (research.md: Options, B)

1. One helper next to `canonicalSubtitleLanguage` (`model/SubtitleLanguage.kt:13`) that returns null for a blank or "und" language (case-insensitive, and `und-*` tags).
2. Audio: `buildAudioRows` uses it, so an unlabelled "und" row gets the localized fallback label, `isFallbackLabel = true`, `language = ""`; a labelled "und" row keeps its label with `language = ""`.
3. Subtitles: `buildSubtitleRow` and `toPlayerTextTracks` use it (a rendition language can fill in); `sameSubtitleLanguage` is false when either side is "und".

## Acceptance criteria

AC-1. Given audio formats with language "und" (also "UND", "und-Latn") and no label, When `buildAudioRows` runs, Then each row's label is the fallback for its position, `isFallbackLabel` is true and `language` is empty (unit, `PlayerAudioTracksTest.kt`).

AC-2. Given an audio format with label "English" and language "und", When rows are built, Then the label is "English", `isFallbackLabel` is false and `language` is empty (unit).

AC-3. Given a stored audio preference and an "und" unlabelled audio row shown, When the user picks an audio or subtitle row, Then the stored audio label and language are unchanged (unit, `PlayerVMTrackSelectionTest.kt`).

AC-4. Given a text format with language "und" and no readable label, When subtitle rows are labelled, Then the row reads the localized `player_subtitle_unknown` ordinal, not "UND"; Given a rendition with language "rus" for that format, Then the row's language is "rus" (unit, `SubtitleLabelerTest.kt` and a `PlayerTextTracks` test).

AC-5. Given two "und" tracks, When `sameSubtitleLanguage` compares them, Then it is false, and the selector does not pick a candidate by language alone (unit, `SubtitleTrackSelectorTest.kt`).

AC-6. Given the `progressive.mp4` fixture played by `PlaybackController`, When tracks are reported, Then the audio row reads the localized fallback label and not "und" (instrumented on the emulator through `make DEVICE=host.docker.internal:5555 itest`, pattern of `app/src/androidTestInstrumentation/.../PlaybackControllerNetworkTracksTest.kt:169`).

AC-7. Given an item with named audio tracks on the box, When the player Settings Audio list is opened, Then the rows keep their manifest names and subtitle rows keep their language names (device, dev APK, screenshot).

AC-8. Given the change, Then `check.full` from `.flow/config.yaml` passes.

## Risks

- A viewer with "und" already stored keeps it; after the fix no row matches it and restore falls to the default track until the next real pick (`vm/AudioTrackPreferenceResolver.kt:201,210`).
- Two unnamed "und" subtitle tracks lose the language fallback in restore; identity matchers (format id, uri, group/index) still apply.
- Whether KinoPub `http` MP4 files carry "und" audio is unverified; AC-6 rests on the fixture. No API probing; the box shows it only if ordinary playback hits such a file.
- Conflicts: `playback-controller-split`, `subtitle-restore-model` (same controller and subtitle files).

## Open questions

| ID | Question | Recommended answer | Blocks |
|----|----------|--------------------|--------|
| Q1 | Scope: audio only, or audio and subtitles? | Both (option B): same cause, `SubtitleLanguage.kt:22` already half-handles "und" | yes (AC-4, AC-5) |
| Q2 | Include the hardcoded `"Unknown"` for a null API `lang` (`model/PlayerUIMapper.kt:20-33`)? | No: separate idea run, different trigger | no |
