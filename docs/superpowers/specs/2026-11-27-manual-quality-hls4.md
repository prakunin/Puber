# Play manual quality from the hls4 stream with a max video size, keeping manifest subtitles and audio names

Status: approved
Date: 2026-11-27
Slug: manual-quality-hls4
Kind: bug
Size: M
Tracks: none

## Problem / Why

### Request

"Play manual quality from the hls4 stream with a max video size, keeping manifest subtitles and audio names." Folded in: `player-audio-track-label-fallback` (research.md: 1, 8 Q1).

### Classification

| Field | Value |
|-------|-------|
| kind | bug |
| size | M: one goal, risk areas playback and persistence (track preference), crosses interactor, controller track selection and VM |
| tracks | none (no layout change; only row text and the stream behind a quality pick) |
| on-device verification | yes: playback, track selection, preferences; ACs 4-7 show only at runtime. Box `192.168.1.110:5555`, `com.kino.puber.stage` only |

### Root cause (confirmed)

Observed on master: Auto lists 4 named audio tracks; after Auto -> 720p and 720p -> 480p one row "Track 1" (`baseline-master.md`: Observations, shots 1-6).

| Step | Where |
|------|-------|
| Manual pick builds candidates `hls`, `hls4`, `http` for the picked file; `hls4` only after `hls` fails | `domain/interactor/player/PlayerInteractor.kt:242-251` |
| Pick -> `switchStreamUrl` -> `PlaybackController.switchStream` at candidate 0 | `ui/feature/player/vm/PlayerVM.kt:953`, `:450`; `PlaybackController.kt:526` |
| `url.hls` has no `EXT-X-MEDIA` entries: one muxed audio group, null label and language; no subtitle renditions | `subtitle-sideload-merge/design-architect-2.md` §1 point 6; research.md: 5 (Media3 parser) |
| Controller names it `"Track ${index + 1}"` (hardcoded English) | `ui/feature/player/vm/PlaybackController.kt:873` |
| VM replaces the API rows with any non-empty report | `PlayerVM.kt:558` (`reportedAudioTracks.ifEmpty { audioTracks }`) |
| A later audio/subtitle pick saves `audioLabel = "Track 1"` and clears the stored language (plausible, from code) | `PlayerVM.kt:1955`; research.md: 3 "Side effect" |

Introduced in `88cdf071`, kept by `2353a735` (research.md: 4).

Draw glitch from the idea log (480p/720p drawn at native size top-left over a frozen frame, `subtitle-sideload-merge/qa-report.md:73`): not seen in `baseline-master.md`, not explained. Not a root-cause claim of this run.

### Scope (research.md: 6, option C)

1. Manual candidates: picked file's `hls4`, then `hls`, then `http` (`PlayerInteractor.kt:242`).
2. On an HLS candidate from a manual pick, constrain video to the picked quality (`QualityUIState.width/height`, `PlayerUIModels.kt:59`) through `TrackSelectionParameters`; Auto clears it. Applies on `prepare` (`PlaybackController.kt:402`, reached with the kept manual index from `PlayerVM.kt:436` and the refresh path `:539`) and on `switchStream` (`:526`, which today restores `savedTrackParams` `:530,543`). `StreamCandidate` (`PlayerInteractor.kt:27`) or the `PlaybackControl` calls must carry the cap.
3. Unlabelled audio (still possible on the `hls`/`http` fallback): localized fallback label in `strings.xml` instead of `"Track N"`; a pick shown on such a row does not overwrite the stored audio preference (`PlayerVM.kt:1955`).

### Risks (analyst)

- `hls4` structure is unverified (research.md: 5): one shared multi-variant master or a per-file playlist. The design must work for both; the box shows which (AC-5) at human pace. No API probing.
- API `w`/`h` may be nominal while the stream is letterboxed (`baseline-master.md`: 720p played 1280x534). A cap or a min size built from nominal sizes can exclude every variant (AC-3).
- A cap alone lets ABR drop below the picked quality; whether manual means "exactly" or "at most" is Q1.
- `switchStream` keeps the previous `trackSelectionParameters`: an audio override from the old stream's groups and the old cap can leak into the new stream.
- Positional audio selection (`PlaybackController.kt:580`) and the restore resolver now see 4 groups on manual streams; restore of a saved dubbing must still match (`AudioTrackPreferenceResolver`).
- Conflicts: `playback-controller-split`, `subtitle-restore-model` (same controller/VM lines).

## Acceptance criteria

AC-1. Given a file with `hls4`, `hls` and `http`, When candidates are built for manual quality N, Then the order is that file's `hls4`, `hls`, `http` (unit: `PlayerInteractorTest.kt:316` changed). Auto order unchanged (`:294`).

AC-2. Given manual quality with width W and height H, When the HLS stream is prepared or switched to, Then the track selector parameters cap video at W x H; When the user goes back to Auto, Then no cap remains (unit on the controller's parameter builder, or instrumented over the loopback multi-variant fixture `player-test-fixtures/.../hls/master.m3u8`).

AC-3. Given a manual cap below every variant in the manifest, When the stream plays, Then a video track is still selected (no audio-only playback) (instrumented or unit on the selection rule; `setExceedVideoConstraintsIfNecessary(false)` at `PlaybackController.kt:426` makes this a real case).

AC-4. Given the box item from `baseline-master.md` (4 audio tracks), When the user switches Auto -> 720p -> 480p, Then the Audio row keeps the manifest name and the Audio list keeps all 4 named tracks after each switch (device, screenshots like `baseline-master-shots/3-6`).

AC-5. Given the same switches, When the debug/info overlay is read, Then the video resolution is at most the picked quality and differs between 720p and 480p (device; `PlaybackController.kt` `getDebugInfo().videoResolution`).

AC-6. Given an item whose `hls4` manifest has subtitle renditions, When the user picks a manual quality, Then the manifest subtitle rows stay in the picker and a picked row renders cues (device).

AC-7. Given a manual quality switch on the box, When the new stream starts, Then the picture fills the frame per the aspect mode, with no native-size draw in a corner over a frozen frame (device observation).

AC-8. Given a report whose audio groups carry no label and no language, When rows are built, Then each row reads the localized fallback string, not `"Track N"` (unit, `FakeResourceProvider`).

AC-9. Given a stored audio preference (label and language) for an item, When the user picks an audio or subtitle row while the shown audio row is a fallback, Then the stored audio label and language are unchanged (unit, `PlayerVMTrackSelectionTest`).

AC-10. Given the change, Then `check.full` from `.flow/config.yaml` passes.

## Out of scope

Not listed separately in the brief; see Scope above and Q3, D1, D2 below (corner-draw glitch, cleanup of saved "Track 1" labels, "Unknown" language localization stay out).

## Design

Base 0823528b. Paths below are under `app/src/main/java/com/kino/puber/` unless they start with `app/` or `player-test-fixtures/`. ACs: `brief.md#acceptance-criteria`; Q1-Q3 answered in `state.md`.

### Decisions

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

### Changes by layer

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

### Alternatives rejected

- Label-only fix (research option A): leaves dubbing choice and manifest subtitles lost on manual quality.
- `TrackSelectionOverride` on a chosen video variant after tracks arrive: needs a variant-to-API-quality match and a second selection pass; the size cap does it inside the selector.
- New `PlaybackControl` method `setVideoCap`: every candidate path (`PlayerVM.kt:447,456,476,528`) would have to call it in order with prepare/switch.
- Taking API audio names by position for unlabelled streams: order of muxed tracks against `Audio` list is unverified (research.md: 6, A).
- Skipping the reload when the new candidate URL equals the current one (shared `hls4` master): unknown structure; optimisation, not needed by an AC.

### Risks

| # | Risk | Area | Where |
|---|------|------|-------|
| 1 | API `w/h` slightly larger than the variant's `RESOLUTION` is fine; slightly smaller (e.g. API 1280x534, variant 1280x536) excludes the right variant and force-highest plays the next lower one: "720p" plays 480p. AC-5 on the box settles it; a fix (tolerance) would then be a design change | correctness, data shape | `PlayerInteractor.kt:242-251`, `VideoCapParameters.kt` |
| 2 | `hls4` is either one shared master or per-file; with a per-file single-variant playlist the cap equals the file and is a no-op; with a shared master the same URL is reloaded on a 720p -> 480p switch. Both handled; the box shows which | rollout | `PlaybackController.kt:526-547` |
| 3 | Audio override from the old stream survives `switchStream` (`:530,543`): it applies only if the new stream has an equal `TrackGroup` (same `hls4` master); otherwise default audio plays and the report moves the row index. Restore does not re-run on a quality switch (`tracksRestoredForCurrentMedia`, `PlayerVM.kt:586-589`), so a dubbing picked on `hls4` A may fall back to default on `hls4` B | lifecycle | `PlaybackController.kt:543`, `PlayerVM.kt:576` |
| 4 | `setExceedVideoConstraintsIfNecessary(true)` is global for the session; any future max-size constraint (e.g. viewport limits) would also stop yielding audio-only. Intended | correctness | `PlaybackController.kt:426` |
| 5 | Force-highest without ABR on a slow link: the pinned variant rebuffers where Auto would step down; same as today's single-variant `hls` | performance | `VideoCapParameters.kt` |
| 6 | Warm-up of the next episode (`warmUpNext`, `PlaybackController.kt:700`) ignores the cap and may prefetch another variant; costs bytes, not correctness | performance | `PlaybackController.kt:700-752` |
| 7 | Conflicts with `playback-controller-split` and `subtitle-restore-model` on `prepare`/`switchStream`/`notifyTracksUpdated` | rollout | `PlaybackController.kt:402-547,864-891` |

### Open questions

| ID | Question | Recommended answer | Blocks |
|----|----------|--------------------|--------|
| D1 | Boxes that already stored `audioLabel = "Track 1"` and no language keep a dead preference; clean it up? | No: restore finds nothing and default audio plays; the next real pick overwrites it | no |
| D2 | API rows label a missing language with hardcoded "Unknown" (`ui/feature/player/model/PlayerUIMapper.kt:24`); localize here? | No: separate run; not reached by this bug | no |

## Test design

Base 0823528b. ACs: `brief.md#acceptance-criteria`; design: `design-architect.md`. Unit tests under `app/src/test/kotlin/com/kino/puber/`, instrumented under `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/`, run with `make DEVICE=<serial> itest TESTS=<fqcn>`.

### Risk profile

| # | Risk | P | I | Score | Covered by |
|---|------|---|---|-------|------------|
| R1 | `switchStream` restores the old `trackSelectionParameters` (`PlaybackController.kt:530,543`): the 720p cap leaks into 480p, or a manual cap into Auto | 3 | 3 | 9 | `hlsVideoCap_switchStreamReplacesCap` (instrumented), `applyVideoCap_null_clearsEarlierCap` |
| R2 | A pick while the audio row is a fallback overwrites the stored dubbing (`PlayerVM.kt:1955-1968`, null language removes the key) | 3 | 3 | 9 | AC-9 unit rows |
| R3 | Cap below every variant yields audio-only playback (`setExceedVideoConstraintsIfNecessary(false)`, `PlaybackController.kt:426`) | 2 | 3 | 6 | `hlsVideoCap_belowEveryVariant_stillSelectsVideo` (instrumented) |
| R4 | API `w/h` nominal vs variant `RESOLUTION` (letterboxed 1280x534): "720p" plays the next lower variant (design risk 1) | 2 | 3 | 6 | M2 (device; unit cannot know real manifests) |
| R5 | Cap dropped by the URL rewrite / `distinctBy` step (`PlayerInteractor.kt:227-230`) | 2 | 3 | 6 | `selectStreamCandidates_manualWithSurround_keepsVideoCap` |
| R6 | `hls4` structure unknown (shared master or per-file) | 2 | 2 | 4 | M1, M2 record which |
| R7 | Old stream's audio override applies to the new `hls4` stream or is dropped; restore does not re-run on a quality switch (design risk 3) | 2 | 2 | 4 | M5 |
| R8 | Force-highest without ABR rebuffers on a slow link (design risk 5) | 2 | 2 | 4 | M2 notes rebuffering |
| R9 | Merge conflicts with `playback-controller-split`, `subtitle-restore-model` | 2 | 2 | 4 | AC-10 after rebase |
| R10 | Restore resolver sees 4 groups on manual streams | 1 | 2 | 2 | Accepted: `AudioTrackPreferenceResolverTest` stays green |
| R11 | Exceed-constraints flag is session-global (design risk 4) | 1 | 2 | 2 | Accepted: intended |
| R12 | `warmUpNext` ignores the cap (design risk 6) | 1 | 1 | 1 | Accepted: bytes only |

### Traceability

| AC | Level | Priority | Test | Proves |
|----|-------|----------|------|--------|
| AC-1 | unit | P0 red | `PlayerInteractorTest.selectStreamCandidates_manual_prefersSelectedQualityHls4` (replaces `..._prefersSelectedQualityHls`) | Picked file's `hls4`, `hls`, `http` in that order, both HLS carry `VideoSizeCap(w, h)`, `http` none; fails if the order stays `hls` first or the cap is missing |
| AC-1 | unit | P0 guard | `PlayerInteractorTest.selectStreamCandidates_auto_returnsEveryProtocolInFallbackOrder` | Auto order unchanged and no cap (data-class equality with default `null`); fails if Auto gets a cap |
| AC-1 | unit | P1 red | `PlayerInteractorTest.selectStreamCandidates_manualWithSurround_keepsVideoCap` | Fails if the `ac3default` rewrite or `distinctBy` drops the cap (R5) |
| AC-1 | unit | P1 red | `PlayerInteractorTest.selectStreamCandidates_manualFileWithoutWidth_capsHeightOnly` | `w == null` gives width `Int.MAX_VALUE`; fails if a missing width drops the cap or caps width at 0 |
| AC-1 | unit | P2 red | `PlayerInteractorTest.selectStreamCandidates_manualFileWithoutHeight_hasNoCap` | `h == null` gives no cap |
| AC-1 | unit | P2 red | `PlayerInteractorTest.selectStreamCandidates_manualIndexOutOfRange_capsFallbackFile` | The `?: files.first()` file gets its own cap |
| AC-2 | unit | P0 red | `VideoCapParametersTest.applyVideoCap_cap_setsMaxSizeAndForcesHighestBitrate` | `maxVideoWidth/Height` = cap and `forceHighestSupportedBitrate` true; fails if pin (Q1) is reduced to a cap |
| AC-2 | unit | P0 red | `VideoCapParametersTest.applyVideoCap_null_clearsEarlierCap` | Builder from parameters that held a cap: max size back to `Int.MAX_VALUE`, force-highest false; fails if Auto keeps the manual pin |
| AC-2 | unit | P1 red | `VideoCapParametersTest.applyVideoCap_keepsUnrelatedParameters` | Text disabled and preferred audio language survive; fails if the extension rebuilds from defaults |
| AC-2 | instrumented | P0 red | `PlaybackControllerNetworkTracksTest.hlsVideoCap_switchStreamReplacesCap` | Over `hls/master.m3u8`: prepare with 640x360 selects the 360 variant; `switchStream` with 1280x720 selects 720; `switchStream` with no cap leaves the player's parameters unbounded and force-highest off; fails if `switchStream` restores the saved parameters unchanged (R1) or `prepare` ignores the cap |
| AC-3 | instrumented | P0 red | `PlaybackControllerNetworkTracksTest.hlsVideoCap_belowEveryVariant_stillSelectsVideo` | Cap 320x180: READY with a selected video track of 640x360; fails if `setExceedVideoConstraintsIfNecessary(false)` stays |
| AC-3 | instrumented | P1 guard | `PlaybackControllerNetworkTracksTest.hlsTracks_selectsAudioAndSubtitle_observesCueAndDisablesText` | Uncapped HLS still selects audio, text and cues after the selector change |
| AC-4 | device | P0 | M1 | Fails if a manual switch collapses the Audio list to one unnamed row |
| AC-5 | device | P0 | M2 | Fails if the cap is not applied or a nominal size excludes the picked variant (R4) |
| AC-6 | device | P0 | M3 | Fails if manifest subtitle rows vanish on manual quality |
| AC-7 | device | P1 | M4 | Fails if the change introduces the corner draw (compare with master, Q3) |
| AC-8 | unit | P0 red | `PlayerAudioTracksTest.buildAudioRows_unlabelledGroup_usesFallbackString` | Format with no label and no language: label is the `FakeResourceProvider` string for `index + 1`, `isFallbackLabel` true; fails if `"Track N"` stays |
| AC-8 | unit | P1 red | `PlayerAudioTracksTest.buildAudioRows_labelThenLanguage_keepsManifestName` | Label wins over language, language over fallback, `isFallbackLabel` false, `index` follows group order |
| AC-9 | unit | P0 red | `PlayerVMTrackSelectionTest.selectSubtitle_whileAudioRowIsFallback_keepsStoredAudioPreference` | Stored `"rus"` / `"01. Дубляж (RUS)"`, tracks report one fallback row, subtitle pick: `saveTrackPreferences(42, "rus", "01. Дубляж (RUS)", any())`; fails if the guard is removed (base saves null language and the fallback label) |
| AC-9 | unit | P0 red | `PlayerVMTrackSelectionTest.selectAudioTrack_fallbackRow_keepsStoredAudioPreference` | Same for `PlayerAction.SelectAudioTrack` on the fallback row |
| AC-9 | unit | P0 guard | `PlayerVMTrackSelectionTest.selectTrack_savesLangToPrefs` | A labelled row is still saved; fails if the guard applies to every row |
| AC-10 | command | P0 | check.full from `.flow/config.yaml` | Exit 0 |

Notes for the implementer:

- Instrumented tests need `harness.stream(path, videoCap)` / `harness.prepare(..., videoCap)` and a read of the selected video format and the player's `trackSelectionParameters` in `PlaybackControllerReads.kt`, following `selectedAudioLanguage()`.
- `VideoCapParametersTest` builds from `TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT.buildUpon()` (JVM, no `Context`).
- Red tests that reference new API do not compile on base; the bug test below must fail on an assertion.

### Bug: failing-first test

`PlayerInteractorTest.selectStreamCandidates_manual_prefersSelectedQualityHls4`. On base, `manualStreamCandidates` adds `hls` before `hls4` (`git -C <wt> show 0823528b:app/src/main/java/com/kino/puber/domain/interactor/player/PlayerInteractor.kt`, lines 242-251). Write the order assertion first and run it on unchanged code: it fails on the first element (`1080.m3u8` instead of `1080-hls4.m3u8`). Add the cap assertion after `VideoSizeCap` exists.

### Manual checks

Device: box `192.168.1.110:5555`, `com.kino.puber.stage` only, worktree dev APK via install-preserve, lease held, human pace. Item: the one in `baseline-master.md#item`. Screenshots next to `baseline-master-shots/` for comparison. Remote only (`adb -s 192.168.1.110:5555 shell input keyevent KEYCODE_DPAD_*|KEYCODE_DPAD_CENTER|KEYCODE_BACK`).

| # | Steps | Expected | Must not happen |
|---|-------|----------|-----------------|
| M1 (AC-4) | 1. Start the item on Auto; open player Settings, read Audio row, open the Audio list. 2. Back, Quality -> 720p, wait for playback; read Audio row and list. 3. Quality -> 480p; same reads | After each step the Audio row shows the manifest name and the list holds the 4 named tracks of `baseline-master.md#observations` row 1 | "Track 1" / "Дорожка 1", a one-row list, focus loss in Settings, error or pairing screen |
| M2 (AC-5) | During M1 after 720p and after 480p, read the debug/info overlay resolution and note rebuffering | Resolution at most the picked quality and different between 720p and 480p; record whether the `hls4` URL changed between switches (R6) | Same resolution on both picks; 720p playing the 480p variant (R4); audio-only playback |
| M3 (AC-6) | On 720p open the Subtitles list, pick a manifest row, watch 30 s of dialogue | Manifest subtitle rows listed as on Auto; cues render | Subtitle rows missing on manual quality; picked row with no cues |
| M4 (AC-7) | Watch 10 s after each switch in M1 | Picture fills the frame per the aspect mode | Native-size picture in a corner over a frozen frame; if seen, repeat on the master APK before attributing it (Q3) |
| M5 (R7) | On Auto pick "02. Дубляж (UKR)"; switch to 720p; read Audio row and listen | Record which dubbing plays and which row is checked | Row checked differs from the dubbing heard |
| M6 (AC-9) | After M5 back out, start the item again on Auto | Audio restores the last real pick (UKR) | Default RUS plays because a fallback row was saved |

### Perf

N/A: perf track off.

### UNTESTABLE

None.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Manual quality: pin the picked quality or only cap it? | yes | answered | pin: max size = picked size + setForceHighestSupportedBitrate(true) (recommended) |
| Q2 | Keep option C label guard (localized fallback label, no placeholder audio preference saved) in this run? | yes | answered | yes (recommended) |
| Q3 | If the corner-draw glitch shows with the new stream, fix it here? | no | answered | no: record it, separate run unless this change causes it (recommended) |
| D1 | Clean up "Track 1" audio labels already saved on devices? | no | answered | no (recommended) |
| D2 | Localize the hardcoded "Unknown" language in API audio rows (PlayerUIMapper.kt:24)? | no | answered | no, separate run (recommended) |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-27 | codex: required (owner); antigravity on every round (owner); caps lifted (owner) | owner |
| 2026-11-27 | Q1: pin the picked quality | owner (standing instruction: recommended) |
| 2026-11-27 | Q2: label guard in this run | owner (standing instruction: recommended) |
| 2026-11-27 | Q3: corner-draw glitch out of scope unless caused here | owner (standing instruction: recommended) |
| 2026-11-27 | D1: no cleanup of saved "Track 1" labels; D2: "Unknown" localization out of scope | owner (standing instruction: recommended) |
| 2026-11-27 | Instrumented tests (AC-2, AC-3) run on the emulator host.docker.internal:5555 (com.kino.puber.instrumentation is not allowed on the box), as in player-hermetic-fixtures Q1; dev-APK device checks M1-M6 on the box | owner (standing instruction: recommended) |
| 2026-11-27 | Gate 1: approved | owner (standing instruction: recommended) |
