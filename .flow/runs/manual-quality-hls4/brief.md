# Brief: manual-quality-hls4

Input: `research.md` (cited as `research.md: <section>`), `baseline-master.md` (master 0823528b on the box). No brief template exists in the templates folder; usual brief sections.

## Request

"Play manual quality from the hls4 stream with a max video size, keeping manifest subtitles and audio names." Folded in: `player-audio-track-label-fallback` (research.md: 1, 8 Q1).

## Classification

| Field | Value |
|-------|-------|
| kind | bug |
| size | M: one goal, risk areas playback and persistence (track preference), crosses interactor, controller track selection and VM |
| tracks | none (no layout change; only row text and the stream behind a quality pick) |
| on-device verification | yes: playback, track selection, preferences; ACs 4-7 show only at runtime. Box `192.168.1.110:5555`, `com.kino.puber.stage` only |

## Root cause (confirmed)

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

## Scope (research.md: 6, option C)

1. Manual candidates: picked file's `hls4`, then `hls`, then `http` (`PlayerInteractor.kt:242`).
2. On an HLS candidate from a manual pick, constrain video to the picked quality (`QualityUIState.width/height`, `PlayerUIModels.kt:59`) through `TrackSelectionParameters`; Auto clears it. Applies on `prepare` (`PlaybackController.kt:402`, reached with the kept manual index from `PlayerVM.kt:436` and the refresh path `:539`) and on `switchStream` (`:526`, which today restores `savedTrackParams` `:530,543`). `StreamCandidate` (`PlayerInteractor.kt:27`) or the `PlaybackControl` calls must carry the cap.
3. Unlabelled audio (still possible on the `hls`/`http` fallback): localized fallback label in `strings.xml` instead of `"Track N"`; a pick shown on such a row does not overwrite the stored audio preference (`PlayerVM.kt:1955`).

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

## Risks

- `hls4` structure is unverified (research.md: 5): one shared multi-variant master or a per-file playlist. The design must work for both; the box shows which (AC-5) at human pace. No API probing.
- API `w`/`h` may be nominal while the stream is letterboxed (`baseline-master.md`: 720p played 1280x534). A cap or a min size built from nominal sizes can exclude every variant (AC-3).
- A cap alone lets ABR drop below the picked quality; whether manual means "exactly" or "at most" is Q1.
- `switchStream` keeps the previous `trackSelectionParameters`: an audio override from the old stream's groups and the old cap can leak into the new stream.
- Positional audio selection (`PlaybackController.kt:580`) and the restore resolver now see 4 groups on manual streams; restore of a saved dubbing must still match (`AudioTrackPreferenceResolver`).
- Conflicts: `playback-controller-split`, `subtitle-restore-model` (same controller/VM lines).

## Open questions

| ID | Question | Recommended answer | Blocks |
|----|----------|--------------------|--------|
| Q1 | Manual quality: pin the picked quality (highest variant not above it, no ABR) or cap only (ABR below it allowed)? | Pin: max size = picked size plus `setForceHighestSupportedBitrate(true)`, so "720p" plays 720p as today | yes (AC-2, AC-5) |
| Q2 | Keep option C's label guard (AC-8, AC-9) in this run? | Yes: the `hls`/`http` fallback still yields unlabelled audio, and a placeholder overwriting the stored dubbing is data loss | yes (scope) |
| Q3 | If the corner-draw glitch (AC-7) shows on the box with the new stream, fix it here? | No: record it and open a separate run; AC-7 then fails only if this change causes it (compare with master) | no |
