# Design (architect): manual-quality-hls4

Base 0823528b. Paths below are under `app/src/main/java/com/kino/puber/` unless they start with `app/` or `player-test-fixtures/`. ACs: `brief.md#acceptance-criteria`; Q1-Q3 answered in `state.md`.

## Decisions

| Question | Decision | Why |
|----------|----------|-----|
| Where the cap comes from | The interactor builds it from the picked `VideoFile.w/h` and puts it on each HLS `StreamCandidate` of a manual pick; Auto and `PROGRESSIVE` candidates carry `null` | One source for prepare, switch, next-candidate fallback (`PlayerVM.kt:476`) and link refresh (`PlayerVM.kt:528`); no new `PlaybackControl` method, VM call sites unchanged |
| Shape of the cap | `internal data class VideoSizeCap(val width: Int, val height: Int)` next to `StreamCandidate` (`domain/interactor/player/PlayerInteractor.kt:26`); `StreamCandidate.videoCap: VideoSizeCap? = null`. Built only when `h != null`; `w == null` becomes `Int.MAX_VALUE` | Default keeps every existing `StreamCandidate(url, type)` call (tests, `PlaybackControllerHarness.kt:190`) compiling |
| Pin or cap (Q1) | Cap set: `setMaxVideoSize(w, h)` + `setForceHighestSupportedBitrate(true)`; null cap: `clearVideoSizeConstraints()` + `setForceHighestSupportedBitrate(false)` | Owner's answer to Q1; force-highest disables adaptive selection, so the variant does not drift below the pick |
| Where parameters are applied | One pure extension `TrackSelectionParameters.Builder.applyVideoCap(cap: VideoSizeCap?)` in new `ui/feature/player/vm/VideoCapParameters.kt`; called in `prepare` on the selector parameters (`ui/feature/player/vm/PlaybackController.kt:425-432`) and in `switchStream` on `savedTrackParams` before `player.trackSelectionParameters = ...` (`PlaybackController.kt:530,543`) | Unit-testable on the JVM (AC-2); `switchStream` today carries the old cap into the new stream, so it must overwrite it, including back to none on Auto |
| Cap below every variant (AC-3) | `setExceedVideoConstraintsIfNecessary(true)` in `prepare` (`PlaybackController.kt:426`, today `false`, unchanged since `88cdf071` with no rationale) | With no track inside the cap, DefaultTrackSelector then picks the lowest-quality video instead of none; with no cap (Auto) every track is within constraints, so Auto is unaffected |
| Candidate order (AC-1) | `manualStreamCandidates` (`PlayerInteractor.kt:242-251`): `hls4`, `hls`, `http` of the picked file | Root cause; `hls4` carries `EXT-X-MEDIA` audio names and subtitle renditions |
| Fallback label (AC-8) | Move row building out of `notifyTracksUpdated` (`PlaybackController.kt:864-891`, label at `:873`) into pure `buildAudioRows(formats: List<Format>, fallbackLabel: (Int) -> String)` in new `ui/feature/player/vm/PlayerAudioTracks.kt`; label `format.label ?: format.language ?: fallbackLabel(index + 1)`; controller passes `context.getString(R.string.player_audio_track_fallback, n)` | Controller has a `Context`, not a `ResourceProvider` (`PlaybackController.kt:160-165`, DI `ui/feature/player/component/PlayerScreen.kt:58`); a lambda keeps the constructor and the instrumented harness unchanged, and the unit test feeds `FakeResourceProvider::getString` |
| Marking a fallback row | `AudioTrackUIState.isFallbackLabel: Boolean = false` (`ui/feature/player/model/PlayerUIModels.kt:20`), set by `buildAudioRows` | VM needs a fact, not string comparison against a localized text |
| Preference guard (AC-9, Q2) | `saveTrackPreferences` (`ui/feature/player/vm/PlayerVM.kt:1955`): when the shown row `isFallbackLabel`, pass `interactor.getPreferredAudioLang/Label(params.itemId)` (`PlayerInteractor.kt:337,341`) instead of the row's values (`PlayerVM.kt:1967-1968`) | `PlayerPreferencesRepository.saveTrackPreferences` removes a key on `null` (`data/repository/PlayerPreferencesRepository.kt:32-48`); re-passing the stored values keeps them, subtitle saving unchanged |

## Changes by layer

| Layer | Change |
|-------|--------|
| API / repository | N/A: `VideoFile.w/h` and `VideoUrl.hls4` exist (`data/api/models/Models.kt`); no repository change |
| Domain (interactor) | `PlayerInteractor.kt:26`: `VideoSizeCap`, `StreamCandidate.videoCap`. `:242-251`: order `hls4`, `hls`, `http`; `videoCap` on both HLS candidates from the picked file (the `?: files.first()` fallback file at `:245` included). `:227-230` (`copy(url=...)`, `distinctBy(url)`) keep the cap unchanged |
| Controller | `PlaybackController.kt:425-432`: `.setExceedVideoConstraintsIfNecessary(true)` and `applyVideoCap(stream.videoCap)` (last before `.build()`, the extension returns the base builder type). `:543`: `savedTrackParams.buildUpon().applyVideoCap(stream.videoCap).build()`. `:871-879`: rows from `buildAudioRows`. `disableAc3AndRetry` (`:630-651`) builds on the current parameters and keeps the cap; no change. New `VideoCapParameters.kt`, `PlayerAudioTracks.kt` |
| ViewModel state and actions | `PlayerVM.kt:1955-1972` guard above. `onTracksUpdated` replace rule (`PlayerVM.kt:576`), `selectQuality` (`:953`), `switchStreamUrl` (`:450-458`) unchanged |
| Screen and DI module | N/A: `PlaybackController` constructor unchanged |
| Compose content | N/A: rows render `AudioTrackUIState.label` as today |
| Resources | `player_audio_track_fallback`: `app/src/main/res/values/player.xml` "Дорожка %1$d", `app/src/main/res/values-en/strings.xml` "Track %1$d" |
| Tests | `app/src/test/kotlin/com/kino/puber/domain/interactor/player/PlayerInteractorTest.kt:316` reordered and renamed, plus cap on manual / none on Auto (`:294`); new `VideoCapParametersTest`, `PlayerAudioTracksTest` (JVM, `Format.Builder` is already used in `PlayerTextTracksTest`); `PlayerVMTrackSelectionTest` for AC-9; AC-3 instrumented in `app/src/androidTestInstrumentation/.../PlaybackControllerNetworkTracksTest.kt` over `player-test-fixtures/src/main/assets/player-fixtures/hls/master.m3u8` (640x360, 1280x720) with a cap of 320x180, expecting the 640x360 variant selected. Exact names and counts belong to `test-design.md` |

## Alternatives rejected

- Label-only fix (research option A): leaves dubbing choice and manifest subtitles lost on manual quality.
- `TrackSelectionOverride` on a chosen video variant after tracks arrive: needs a variant-to-API-quality match and a second selection pass; the size cap does it inside the selector.
- New `PlaybackControl` method `setVideoCap`: every candidate path (`PlayerVM.kt:447,456,476,528`) would have to call it in order with prepare/switch.
- Taking API audio names by position for unlabelled streams: order of muxed tracks against `Audio` list is unverified (research.md: 6, A).
- Skipping the reload when the new candidate URL equals the current one (shared `hls4` master): unknown structure; optimisation, not needed by an AC.

## Risks

| # | Risk | Area | Where |
|---|------|------|-------|
| 1 | API `w/h` slightly larger than the variant's `RESOLUTION` is fine; slightly smaller (e.g. API 1280x534, variant 1280x536) excludes the right variant and force-highest plays the next lower one: "720p" plays 480p. AC-5 on the box settles it; a fix (tolerance) would then be a design change | correctness, data shape | `PlayerInteractor.kt:242-251`, `VideoCapParameters.kt` |
| 2 | `hls4` is either one shared master or per-file; with a per-file single-variant playlist the cap equals the file and is a no-op; with a shared master the same URL is reloaded on a 720p -> 480p switch. Both handled; the box shows which | rollout | `PlaybackController.kt:526-547` |
| 3 | Audio override from the old stream survives `switchStream` (`:530,543`): it applies only if the new stream has an equal `TrackGroup` (same `hls4` master); otherwise default audio plays and the report moves the row index. Restore does not re-run on a quality switch (`tracksRestoredForCurrentMedia`, `PlayerVM.kt:586-589`), so a dubbing picked on `hls4` A may fall back to default on `hls4` B | lifecycle | `PlaybackController.kt:543`, `PlayerVM.kt:576` |
| 4 | `setExceedVideoConstraintsIfNecessary(true)` is global for the session; any future max-size constraint (e.g. viewport limits) would also stop yielding audio-only. Intended | correctness | `PlaybackController.kt:426` |
| 5 | Force-highest without ABR on a slow link: the pinned variant rebuffers where Auto would step down; same as today's single-variant `hls` | performance | `VideoCapParameters.kt` |
| 6 | Warm-up of the next episode (`warmUpNext`, `PlaybackController.kt:700`) ignores the cap and may prefetch another variant; costs bytes, not correctness | performance | `PlaybackController.kt:700-752` |
| 7 | Conflicts with `playback-controller-split` and `subtitle-restore-model` on `prepare`/`switchStream`/`notifyTracksUpdated` | rollout | `PlaybackController.kt:402-547,864-891` |

## Open questions

| ID | Question | Recommended answer | Blocks |
|----|----------|--------------------|--------|
| D1 | Boxes that already stored `audioLabel = "Track 1"` and no language keep a dead preference; clean it up? | No: restore finds nothing and default audio plays; the next real pick overwrites it | no |
| D2 | API rows label a missing language with hardcoded "Unknown" (`ui/feature/player/model/PlayerUIMapper.kt:24`); localize here? | No: separate run; not reached by this bug | no |
