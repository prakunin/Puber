# Research: player-audio-track-label-fallback

## 1. Request

Title: "Audio row in player Settings shows \"Track 1\" instead of the track name after a quality switch"

Log (state.md, 2026-11-27): created from player-callback-gate triage-1 #3: qa saw on the box (worktree build) the Audio row read "Track 1" instead of "01. Дубляж (RUS)" after switching quality, and it stayed after a second switch. Reviewers traced it outside that diff: PlaybackController.notifyTracksUpdated labels with format.label ?: format.language ?: "Track N", PlayerVM.onTracksUpdated replaces the API names with the player's list when non-empty, and a manual quality tries hls before hls4 (PlayerInteractor.kt:236,247). First step: repeat Auto -> manual quality on the master build on the box; if master shows the same, keep API names when the player's formats carry no label/language.

## 2. Understanding

The Audio row never shows API names once playback is READY: it shows whatever the HLS manifest calls the audio. Auto plays `url.hls4`, whose master playlist names each audio rendition ("01. Дубляж (RUS)"). A manual quality plays `url.hls` first, a CDN playlist without `EXT-X-MEDIA` audio entries, so the single muxed audio track has no label and no language and the controller names it "Track 1". The defect is on master since the manual hls-first order (`88cdf071`, kept by `2353a735`); it is the same root cause the idea run `manual-quality-hls4` already names. Whether to fix the label (show a better name for what `hls` plays) or the stream (play `hls4` at a capped size so names, all dubbings and manifest subtitles survive) is the decision.

## 3. Current state in code

| Step | Where | What happens |
|------|-------|--------------|
| Initial rows | `ui/feature/player/vm/ContentStateFactory.kt:36-37` | `audioTracks = mapper.mapAudioTracks(resolved.audios)`, index 0, before the player reports |
| API label | `ui/feature/player/model/PlayerUIMapper.kt:21-34` | `"<lang> · <author.title or type.title>"`, lang falls back to hardcoded `"Unknown"`; `language = audio.lang` |
| Auto candidates | `domain/interactor/player/PlayerInteractor.kt:233-240` | first file: `hls4`, `hls`, `http` |
| Manual candidates | `PlayerInteractor.kt:242-251` | file of the picked quality: `hls`, `hls4`, `http`; `hls4` is reached only if `hls` fails (`PlayerVM.kt:473-478`) |
| Surround flag | `PlayerInteractor.kt:253-257` | appends `ac3default=1` to every candidate URL |
| Quality pick | `ui/feature/player/vm/PlayerVM.kt:953-962` -> `switchStreamUrl` `:450-458` -> `PlaybackController.switchStream` | new candidate list from index 0 |
| Report trigger | `ui/feature/player/vm/PlaybackController.kt:244-249` (READY), `:257-262` (`onTracksChanged`) | both call `notifyTracksUpdated` through the per-session gate |
| Player label | `PlaybackController.kt:864-891` | one row per audio `TrackGroup`, from `getTrackFormat(0)`: `format.label ?: format.language ?: "Track ${index + 1}"` (hardcoded English, not in `strings.xml`) |
| Replace rule | `PlayerVM.kt:558-583` | `audioTracks = reportedAudioTracks.ifEmpty { audioTracks }`: any non-empty report replaces the API rows, so API names live only until the first READY |
| Restore | `PlayerVM.kt:606-620`, `vm/AudioTrackPreferenceResolver.kt:11-24,176-220` | once per media: exact label, label without "NN. " prefix, voice type parsed from HLS labels ("03. Многоголосый. … (RUS)") + language, language |
| Selection | `PlaybackController.kt:580-591` | positional: row index = audio group index; out of range is ignored |
| Persist | `PlayerVM.kt:907-934,1955-1972`; `data/repository/PlayerPreferencesRepository.kt:32-48` | an audio or subtitle pick saves the shown row's label and language; null language removes the stored key |
| API model | `data/api/models/Models.kt:302-324` | `Audio(id, index, codec, channels, lang, type{title, shortTitle}, author{title, shortTitle})`; `VideoUrl.hls4` `:594` |

How the label is produced:

- First READY on Auto: `hls4` master has `EXT-X-MEDIA TYPE=AUDIO` with NAME and LANGUAGE; Media3 maps NAME to `Format.label` (see External findings), so rows read the manifest names, not `mapAudioTracks` output. Restore then matches the saved label.
- After a manual quality: `hls` plays; no audio rendition entries, so ExoPlayer exposes the muxed audio with null label and null language; one row "Track 1" replaces the list. Switching to another manual quality plays another `hls`, so it stays. Back to Auto would return the names (code reading, not observed).
- The same "Track 1" was observed on master's predecessor build in `subtitle-sideload-merge` qa round 2 (`.flow/runs/subtitle-sideload-merge/qa-report.md:64,73`) and explained in its `design-architect-2.md` §1 point 6: `url.hls` carries no `EXT-X-MEDIA` metadata at all, subtitles included.

Side effect found while tracing (from code, not observed): on a manual quality, picking a subtitle or audio row saves `audioLabel = "Track 1"` and removes the saved audio language (`PlayerVM.kt:1966-1968`, `PlayerPreferencesRepository.kt:39-48`). On the next Auto start none of the four resolver rules matches "Track 1" with a null language, so the viewer's saved dubbing is lost for that item.

Tests in the area:

| Test | Covers |
|------|--------|
| `app/src/test/.../player/vm/PlayerVMTrackSelectionTest.kt` (e.g. `tracksUpdated_audioEmptyUpdate_keepsAudioRowsAndIndex` :653) | VM replace/keep rule, restore |
| `app/src/test/.../domain/interactor/player/PlayerInteractorTest.kt:316` `selectStreamCandidates_manual_prefersSelectedQualityHls` | pins the hls-first manual order; a stream-order fix must change it |
| `app/src/androidTestInstrumentation/.../PlaybackControllerNetworkTracksTest.kt` | real controller over loopback HLS with named renditions (`player-test-fixtures/src/main/assets/player-fixtures/hls/master.m3u8`); no fixture without `EXT-X-MEDIA` |
| `AudioTrackPreferenceResolverTest` (unit) | label/voice-type matching |

## 4. Related work

| Run / doc | Relation |
|-----------|----------|
| `manual-quality-hls4` (idea, 2026-10-14, `f351d988`) | **Probable duplicate.** Same root cause: manual quality plays `url.hls` with "no audio names (\"Track 1\")" and no manifest subtitles; proposes `hls4` with a max video size. Also records a 480p/720p draw glitch (new stream at native size in the top-left over a frozen frame). |
| `subtitle-sideload-merge` | First device sighting of "Track 1" on manual quality; design-architect-2 §1 and Q5 (recommended the separate run above) |
| `player-callback-gate` | Source of this run (triage-1 #3); reviews edge-1, tests-1, codex-1, antigravity-1, state-path-1 all trace it outside that diff; evidence `docs/superpowers/evidence/2026-11-27-player-callback-gate-device.md:43` |
| `baseline-player-track-coverage` | Touches `AudioTrackPreferenceResolver` only for baseline-profile coverage; no overlap in behaviour |
| `baseline-master.md` (this run, in progress) | master observation on the box; decides whether master matches the worktree sighting |

Commits: `88cdf071` (Release 1.0.0) introduced the "Track N" fallback and the hls-first manual order; `2353a735` (stream candidate fallback) kept the order; `49afcadf` added the `ifEmpty` keep rule for text-only reports.

## 5. External findings

| Finding | Source |
|---------|--------|
| Media3 1.11.0 `HlsPlaylistParser`: `EXT-X-MEDIA` NAME is required and becomes `Format.label` (id `GROUP-ID:NAME`); LANGUAGE is optional and becomes `Format.language`. Without audio media tags there is no labelled audio format, which matches the null label/language on `hls`. Project uses media3 1.11.0 (`gradle/libs.versions.toml:27`). | https://raw.githubusercontent.com/androidx/media/1.11.0/libraries/exoplayer_hls/src/main/java/androidx/media3/exoplayer/hls/playlist/HlsPlaylistParser.java |
| Community KinoPub client docs: `hls` = "CDN HLS master", `hls4` = "API-hosted HLS master (all qualities/audios/subs)"; `Audio.author` is an object and null on part of the tracks. Not official. | https://pkg.go.dev/github.com/niazlv/kinopub-downloader/pkg/kinopub |
| kodi.kino.pub maintainer: audio track choice works only with `http` and `hls4` (2022-02-13), "only for hls4" (2025-09-20); a user reports `hls2`/`hls` show video without audio choice. | https://github.com/quarckster/kodi.kino.pub/discussions/219 |

No official KinoPub API documentation was found. Unverified: whether each file's `hls4` is one shared master or per-quality, and whether `hls` muxes one audio track or several.

## 6. Options

| # | Option | Trade-offs | Size |
|---|--------|------------|------|
| A | **Label fallback in the VM.** When every reported audio format has no label and no language, do not show "Track N": if the report count equals the API count, take API labels by position; otherwise keep one row named from the API track the stream plays (unknown) or a localized "Default audio". Move "Track N" to `strings.xml`. Do not persist a placeholder label (`saveTrackPreferences` skips audio when the shown row is a fallback). | Small, unit-testable, fixes the text and the preference loss. Does not bring back dubbing choice or manifest subtitles on manual quality; the API-by-position mapping is a guess when counts match (order of muxed tracks vs `audios` is unverified). | S |
| B | **Play `hls4` for manual quality** (= `manual-quality-hls4`): manual candidates `hls4`, `hls`, `http`; enforce the pick with `TrackSelectionParameters.setMaxVideoSize`/video override on the hls4 variants, then the existing names, dubbing list and manifest subtitles stay. | Fixes the cause and the subtitle loss too. Crosses interactor, controller track selection and the quality UI (selected-quality semantics, variant-to-API-quality mapping); needs device checks for the draw glitch and for whether `hls4` is per-file. Changes the pinned `PlayerInteractorTest` order. | M |
| C | B, plus A's guard (no placeholder persisted, localized fallback) for streams that still arrive unlabelled (`hls` fallback after an `hls4` failure, `http`). | Most complete; larger review surface. | M |

Recommended: **C, delivered by folding this run into `manual-quality-hls4`** if `baseline-master.md` confirms master shows "Track 1" after Auto -> manual; A alone only if the owner wants a quick fix now and B later.

## 7. Risks

- Preference loss: today a pick on a manual quality overwrites the saved dubbing (§3); any option must cover it with a unit test.
- Positional selection (`PlaybackController.kt:580-591`): rows taken from the API while the player has fewer groups make extra rows no-ops.
- Option B: `hls4` at a capped size may still adapt below the cap; the 480p/720p native-size draw glitch from `manual-quality-hls4` may be stream-related and must be checked on the box.
- No loopback fixture has an unlabelled HLS audio stream; a controller-level test needs one added to `player-test-fixtures`.
- No API probing: `hls` vs `hls4` contents can only be confirmed by ordinary playback on the box at human pace.

## 8. Open questions

| ID | Question | Recommended answer |
|----|----------|--------------------|
| Q1 | Fold this run into `manual-quality-hls4` (or drop this one and run that), or keep two runs? | Fold: one run, option C, this slug's sighting and `baseline-master.md` as its evidence. |
| Q2 | If only the label is fixed (A), what should the single unlabelled row read? | A localized "Default audio" (new string), never the API name of a track we cannot identify. |
| Q3 | Should a pick made on an unlabelled stream ever persist the audio preference? | No: skip audio in `saveTrackPreferences` when the shown row is a fallback; keep the stored dubbing. |

## 9. Sources

- Code: `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt`, `PlayerVM.kt`, `ContentStateFactory.kt`, `AudioTrackPreferenceResolver.kt`, `ui/feature/player/model/PlayerUIMapper.kt`, `PlayerUIModels.kt`, `domain/interactor/player/PlayerInteractor.kt`, `data/api/models/Models.kt`, `data/repository/PlayerPreferencesRepository.kt`, `gradle/libs.versions.toml`, `player-test-fixtures/src/main/assets/player-fixtures/hls/master.m3u8`
- Tests: `PlayerVMTrackSelectionTest.kt`, `PlayerInteractorTest.kt`, `PlaybackControllerNetworkTracksTest.kt`
- Commits: `88cdf071`, `2353a735`, `49afcadf`, `f351d988`
- Runs: `player-callback-gate` (triage-1, qa-report, review-edge-1, review-tests-1, review-codex-1, review-antigravity-1, review-state-path-1), `manual-quality-hls4`, `subtitle-sideload-merge` (design-architect-2, qa-report), `baseline-player-track-coverage`
- Docs: `docs/superpowers/evidence/2026-11-27-player-callback-gate-device.md`, `docs/superpowers/specs/2026-10-14-subtitle-sideload-merge.md`, `docs/testing/player-playback.md`
- URLs: listed in §5
