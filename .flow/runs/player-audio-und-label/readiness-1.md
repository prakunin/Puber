# Readiness 1: player-audio-und-label

Verdict: **READY**

Read: spec and plan at worktree HEAD `3e99f133` (base `cb7fcd45`), `test-design.md`, `state.md`. Paths are under `app/src/main/java/com/kino/puber/ui/feature/player/` unless they start with `app/`, `player-test-fixtures/` or `Makefile`.

## Checks

| Check | Result |
|---|---|
| Every AC has a task and a Test design row | Yes. AC-1, AC-2, AC-3, AC-5, AC-6: Task 1; AC-4, AC-8: Task 2; AC-7: Manual check 1, run by `qa-device` in verify |
| Tasks ordered by dependency, build green after each | Yes. Task 1 adds `knownLanguageOrNull` and its two callers; Task 2 only consumes it. Task 2 red tests fail on Task 1 code: `rawLanguageLabel` and `PlayerTextTracks.kt:46,87` are untouched by Task 1 |
| P0 rows: defect named, `red`/`guard`, test name in backticks | Yes, all P0 rows |
| Red tests can fail on an assertion on base | Yes: `PlayerAudioTracks.kt:9,13-14` keeps "und"; `PlayerVM.kt:1966-1977` then saves ("und", "und"); `SubtitleLanguage.kt:8-11` treats "und" = "und"; `SubtitleLabeler.kt:42-45` yields "UND"; `SubtitleTrackSelector.kt:110-116` matches by language |
| AC-6 fixture really carries "und" | Yes: both `mdhd` boxes of `player-test-fixtures/src/main/assets/player-fixtures/progressive.mp4` decode to "und" (one `soun` track) |
| Out of scope respected | Yes: no edit to `PlayerVM.kt`, `PlaybackController.kt`, `model/PlayerUIMapper.kt:20-33` (Q2), `SubtitleTrackMerger.kt:104-105` (Q3) |
| Blocking questions answered | Q1 answered (option B); Q2, Q3 non-blocking, answered |
| Device rules | Instrumented run on the emulator with the `instrumentation` flavor (package `.instrumentation`, `app/build.gradle.kts:239-241`), so nothing but `com.kino.puber.stage` touches the box; lease and `ssh mac-emu stop` stated |

## Citations sampled

| Citation | Resolves to |
|---|---|
| `vm/PlayerAudioTracks.kt:9,12-14` | `name = format.label ?: format.language`, `language`, `isFallbackLabel = name == null` |
| `vm/PlayerVM.kt:1966` | `keepStoredAudio = audioTrack?.isFallbackLabel == true` |
| `vm/PlayerTextTracks.kt:46,69,87` | row language, `findRendition` language matcher, `toPlayerTextTracks` language |
| `model/SubtitleLanguage.kt:8-11,13,22` | `sameSubtitleLanguage` with `isBlank`, `canonicalSubtitleLanguage`, `it != "und"` |
| `model/SubtitleLabeler.kt:42-45,50` | `rawLanguageLabel`, `unknownLabel(position + 1)` |
| `vm/SubtitleTrackSelector.kt:55-56,106-117` | semantic filter, `matchByLanguage` |
| `vm/PlaybackController.kt:872,905` | `buildAudioRows(...)`, `setPreferredTextLanguage(track.language)` |
| `vm/AudioTrackPreferenceResolver.kt:201,210` | literal `track.language == preferredLang` compares |
| `app/src/test/.../vm/PlayerVMTrackSelectionTest.kt:37,99,104-131` | `STORED_AUDIO_LABEL`, `selectTrack_savesLangToPrefs`, fallback-row tests and helper |
| `app/src/test/.../vm/PlayerAudioTracksTest.kt:23-32,34,40` | `TextUtils` stub, `format`, `rows` |
| `app/src/test/.../vm/SubtitleTrackIdentityTest.kt:62`, `SubtitleTrackSelectorTest.kt:10,89` | `sameSubtitleLanguage_matchesRuAndRus_rejectsBlank`, `selector`, `select_matchesByLanguage_onlyWhenUnambiguous` |
| `app/src/test/.../vm/PlayerTextTracksTest.kt:38,51,56,109,170`, `model/SubtitleLabelerTest.kt:21,71,220,236` | helpers and pattern tests as named |
| `app/src/androidTestInstrumentation/.../PlaybackControllerNetworkTracksTest.kt:159`, `PlaybackControllerDeviceTest.kt:34`, `PlaybackControllerHarness.kt:247` | fallback-row test, `useMediaRoutes(required = setOf("progressive"))`, its definition |
| `app/src/main/res/values-en/strings.xml:301-302` | `player_subtitle_unknown`, `player_audio_track_fallback` |

All resolve.

## Notes (non-blocking)

1. AC-4 says the row language is "rus"; `PlayerTextTracksTest.manifestRow_undeterminedFormatLanguage_takesRenditionLanguage` asserts "ru" because `Format.Builder` normalises it. Amend the AC-4 wording to the normalised code before the gate, or the gate reads the test as proving AC-4 with that normalisation.
2. Spec AC-6 and `test-design.md` give `make DEVICE=host.docker.internal:5555 itest`, which builds the `dev` variant (`Makefile:19,31,138`) and does not run `androidTestInstrumentation`. The plan's command (`FLAVOR=instrumentation GRADLE=./tools/agentw`) is the one to use.
3. `make itest` can exit 0 on an incomplete run (run `itest-incomplete-run-green`). Task 1 "Done when" should read the case count in the JUnit XML under `app/build/outputs/androidTest-results/connected/`, as `docs/superpowers/plans/2026-11-27-player-callback-gate.md:16` does.
