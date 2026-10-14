# subtitle-sideload-merge: device verification, round 1

Date: 2026-10-14. Device: emulator-5554, AVD `puber_tv_36` (Android TV, API 36, 1920x1080), leased and released.
Build: worktree `subtitle-sideload-merge`, HEAD c6ea3789 (base a97ee2b1). `app-dev-debug.apk` `com.kino.puber.stage`
1.9.70-dev, sha256 `6c1580aa9282db0aaf73b94649eea8754776eac962985df20ac028cd41ea98eb`. Installed with
`tools/android-apk-install-preserve` (`installed`, `compatible_replace`, same signer, no data lost); the on-device
`base.apk` has the same sha256. Input: remote key events only. No screenshots are kept (account data).

Static gate: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` BUILD SUCCESSFUL, 1561 tests,
0 failures, 0 errors, 0 skipped (Gradle reported the tasks up to date for this exact tree; the result XML on disk was read).
No crash-buffer entries for the package afterwards.

## Titles (Q6, chosen on the device)

| Role | Title | Finding |
|---|---|---|
| H1 (API-only plus manifest-only language) | none identified | The picker shows only the merged result. The app has no view of which source a row came from, so an API-only or manifest-only language cannot be told apart from a shared one on screen. Not established for any title. |
| H2 (both sources offer a language) | Outlander S1E3, S1E5, S2E3 (API lists Russian, English; picker shows each once) | HLS confirmed (loader thread `ExoPlayer:Loader:HlsSampleStreamWrapper`, adaptive bitrate on Quality Auto). The picker shows `Russian` and `English`, one row each. Whether the manifest also lists them is not observable. |
| P1 (progressive) | none | The app picks `hls4`, then `hls`, then `http` for every quality (`PlayerInteractor.kt:228-246`); progressive is only a fallback after an HLS failure, which cannot be arranged. |
| Psyren S1E1 | HLS, API lists 9 languages | Used for time to first frame only. |

## Checks

| Check | Steps | Expected | Observed | Result |
|---|---|---|---|---|
| D-1 side-loaded API subtitle renders on HLS | Outlander S1E3, settings, Subtitles, pick `Russian` | Language-name row, cues render | Rows `Off`, `Russian`, `English` (language names, no ids). After the pick cues rendered ("и тем, что ты задумала.") | PASS (title is H2, so it proves a side-loaded or manifest row renders on HLS, not API-only specifically) |
| D-2 manifest-only language | no H1 title | n/a | not observed | NOT CHECKED |
| D-3 shared language appears once | Outlander S1E3 and S2E3 | one row per language | `Russian` once, `English` once; no duplicate rows | PASS |
| D-4 selection survives quality switch (AC-4, AC-6, R3) | S1E5, `Russian` checked and rendering, Quality to 720p (also 480p), wait 5-10 s, open Subtitles and Settings root | `Russian` stays checked, cues stay | After 720p the Settings door read `Subtitles Off`; the Subtitles page showed only `Off` (checked) and `Aa Size`, the `Russian` and `English` rows were gone. After 480p the same. Back on Auto the rows returned but `Off` stayed checked. | FAIL (defect 1) |
| D-4 second sample, S2E3 | `Russian` checked, Quality 720p, then Subtitles | same as above | Rows `Off`, `Russian` (checked), `English` still present after the switch | observed once, not reproduced on S2E3 |
| D-5 progressive unchanged | no P1 title | n/a | not observed | NOT CHECKED (unit: `PlayerVMTrackSelectionTest.tracksUpdated_progressive_nullSubtitleTracks_keepsApiRows`, `PlaybackMediaItemSpecTest.progressive_*`) |
| D-6 time to first frame (AC-10) | Psyren S1E1 from the detail page, screen recording at 25 fps, OK press to first non-dark video frame, four runs | no regression against master | 2.16 s (first start after install, cold), then 0.37 s, 0.45 s, 0.79 s | observation only: master was not built, so there is no comparison number |
| D-6 no subtitle fetch before a pick | cold starts above with subtitles Off | no visible extra buffering | none visible | PASS (visual only) |
| D-7 Subtitles page opened early and around a switch | opened Settings within about 3 s of start on Psyren S1E1 and Outlander S1E3; opened Subtitles right after a quality switch | focus stays in the panel, no crash | focus stayed in the panel, Up/Down moved, no crash, no empty page. The page did drop its rows after the switch (defect 1). | PASS for focus, see defect 1 for rows |
| D-8 saved choice restored | Outlander (saved `Russian` after this pass), reopened S1E5 and S2E3 | saved language restored | `Russian` restored and rendered on S1E5 and on S2E3 (the preference is per title, not per episode) | PASS |
| D-9 failing subtitle URL | no such title | n/a | not observed | NOT CHECKED (AC-9 evidence is the Media3 trace D15) |
| Debug "stream source" | Outlander S2E3, Settings, Stream | host only for the video | `Source NL03` | observation |

## Account and app state changes caused

1. Outlander saved subtitle: found as `AI-generated` (per the brief). On this build the API list for Outlander no longer
   offers an `AI-generated` row (rows are `Russian`, `English`), and I picked `Russian` on S1E3, so the saved choice is now
   `Russian`. It cannot be restored to `AI-generated` from the UI. Re-picking was done once more at the end (`Russian`).
2. Psyren S1E1: an explicit `Off` was not changed (no pick was made on Psyren in this round).
3. Subtitle size was changed Medium to Large by an accidental press and cycled back to `Medium` (verified).
4. Aspect ratio was cycled to Stretch/Crop by accident and returned to `Auto` (verified); it is not persisted.
5. Watch progress: short plays of Psyren S1E1 (four starts), Outlander S1E3, S1E5, S2E3; S1E3 and S1E5 positions may
   have been replaced by the short plays (S1E3 showed a resume prompt at 4:03 before it was played). Nothing else was written.

## Defect 1: quality switch on an HLS stream with API subtitles drops the picker rows and the selection

Reproduction: Outlander S1E5 (HLS, Quality Auto), pick `Russian` (cues render), open Settings, Quality, `720p`. After the
switch, Settings shows `Subtitles Off`; the Subtitles page lists only `Off` and `Aa Size`. Switching back to Auto restores
the `Russian` and `English` rows with `Off` checked. Expected (AC-4, AC-6, checklist D-4): `Russian` stays selected and cues
keep rendering. Observed on S1E3 several times (before the clean repro) and on S1E5; not seen on S2E3.
Likely area (not verified in code): `PlayerVM.onTracksUpdated` merges the API rows with a report whose text-group list is
empty (`SubtitleTrackMerger` returns only `Off` when the player reports no tracks), and `notifyTracksUpdated` sends that
report because audio groups exist; the previous row is then not found and the VM selects `Off`. The report with no text
groups right after `switchStream` for a manual quality stream is the thing to inspect.
