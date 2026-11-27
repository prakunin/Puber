# Test design: manual-quality-hls4

Base 0823528b. ACs: `brief.md#acceptance-criteria`; design: `design-architect.md`. Unit tests under `app/src/test/kotlin/com/kino/puber/`, instrumented under `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/`, run with `make DEVICE=<serial> itest TESTS=<fqcn>`.

## Risk profile

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

## Traceability

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

## Bug: failing-first test

`PlayerInteractorTest.selectStreamCandidates_manual_prefersSelectedQualityHls4`. On base, `manualStreamCandidates` adds `hls` before `hls4` (`git -C <wt> show 0823528b:app/src/main/java/com/kino/puber/domain/interactor/player/PlayerInteractor.kt`, lines 242-251). Write the order assertion first and run it on unchanged code: it fails on the first element (`1080.m3u8` instead of `1080-hls4.m3u8`). Add the cap assertion after `VideoSizeCap` exists.

## Manual checks

Device: box `192.168.1.110:5555`, `com.kino.puber.stage` only, worktree dev APK via install-preserve, lease held, human pace. Item: the one in `baseline-master.md#item`. Screenshots next to `baseline-master-shots/` for comparison. Remote only (`adb -s 192.168.1.110:5555 shell input keyevent KEYCODE_DPAD_*|KEYCODE_DPAD_CENTER|KEYCODE_BACK`).

| # | Steps | Expected | Must not happen |
|---|-------|----------|-----------------|
| M1 (AC-4) | 1. Start the item on Auto; open player Settings, read Audio row, open the Audio list. 2. Back, Quality -> 720p, wait for playback; read Audio row and list. 3. Quality -> 480p; same reads | After each step the Audio row shows the manifest name and the list holds the 4 named tracks of `baseline-master.md#observations` row 1 | "Track 1" / "Дорожка 1", a one-row list, focus loss in Settings, error or pairing screen |
| M2 (AC-5) | During M1 after 720p and after 480p, read the debug/info overlay resolution and note rebuffering | Resolution at most the picked quality and different between 720p and 480p; record whether the `hls4` URL changed between switches (R6) | Same resolution on both picks; 720p playing the 480p variant (R4); audio-only playback |
| M3 (AC-6) | On 720p open the Subtitles list, pick a manifest row, watch 30 s of dialogue | Manifest subtitle rows listed as on Auto; cues render | Subtitle rows missing on manual quality; picked row with no cues |
| M4 (AC-7) | Watch 10 s after each switch in M1 | Picture fills the frame per the aspect mode | Native-size picture in a corner over a frozen frame; if seen, repeat on the master APK before attributing it (Q3) |
| M5 (R7) | On Auto pick "02. Дубляж (UKR)"; switch to 720p; read Audio row and listen | Record which dubbing plays and which row is checked | Row checked differs from the dubbing heard |
| M6 (AC-9) | After M5 back out, start the item again on Auto | Audio restores the last real pick (UKR) | Default RUS plays because a fallback row was saved |

## Perf

N/A: perf track off.

## UNTESTABLE

None.
