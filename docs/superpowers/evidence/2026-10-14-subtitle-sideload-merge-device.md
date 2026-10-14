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
| H2 (both sources offer a language) | none established. Correction in round 3: Outlander S1E3, S1E5, S2E3 were first read as H2 (picker shows `Russian` and `English`, one row each), but per design delta 2 they are most likely manifest-only titles (no text on the manual-quality streams, text-less report), which is evidence for AC-2, not H2; AC-1 stays open on Outlander. | HLS confirmed (loader thread `ExoPlayer:Loader:HlsSampleStreamWrapper`, adaptive bitrate on Quality Auto). The picker shows `Russian` and `English`, one row each. Whether the manifest also lists them is not observable. |
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

---

# Round 2 (fix round 1 build)

Date: 2026-10-14. Device: emulator-5554 (`puber_tv_36`, API 36 TV), leased and released.
Build: worktree HEAD 7ca85b93 (base a97ee2b1). `app-dev-debug.apk` `com.kino.puber.stage` 1.9.70-dev,
sha256 `1d1ec6da84beea2b70c57171e546084292f95a02a92ec8175c5b79a4e3baebdb`. Installed with
`tools/android-apk-install-preserve` (`installed`, `compatible_replace`, same signer
`6b29181257cb520329553691b7a48c9b1123899950105127b3dd67012c49a83e`, no data lost); the on-device `base.apk` has the same sha256.
Input: remote key events only. Cue presence was measured by sampling screenshots in memory (a black box with white text in the
subtitle strip, never saved); no screenshots, logs or UI dumps are kept.

Static gate: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` BUILD SUCCESSFUL (tasks up to date for this
tree); result XML read: 1578 tests, 0 failures, 0 errors, 0 skipped.

## Checks

| Check | Steps | Expected | Observed | Result |
|---|---|---|---|---|
| D-8 restore on open (AC-7) | cold start, Outlander S1E5 (saved `Russian`), Continue | `Russian` selected and rendering | Settings door `Subtitles Russian`, cues rendered | PASS |
| D-8 back-and-reopen | back to the detail page, reopen S1E3 | `Russian` restored | `Subtitles Russian`, cues in 17 of 24 sampled frames | PASS |
| Next episode | S1E5 to S1E6 with Next | pick on the new media comes from the saved choice | `Russian` selected, cues in 5 of 17 frames. The saved choice is also `Russian`, so a leaked pick cannot be told from a restore on the device | PASS (consistent), leak case covered by unit tests only |
| D-4 rows and pick after a switch (AC-4, AC-6), S1E5 | Auto, `Russian` checked, Quality 720p, wait 8 s, Settings root and Subtitles page; then 480p | rows and `Russian` stay | after 720p and after 480p: root reads `Subtitles Russian`, page lists `Off`, `Russian` (checked), `English`, `Aa Size`. Round 1 showed only `Off`. | PASS (row and pick) |
| D-4 cues still render after a switch (D-4 "and rendering"), S1E5 | same, then sample the subtitle strip for 40 to 150 s during dialogue | cues keep rendering | 720p: no cue in about 60 sampled frames plus visual checks across 4:28 to 7:00 and 3:22 to 3:50. 480p: 0 of 32 and 0 of 27 frames (the second run after re-picking `Russian` at 480p). Auto at the same episode: 8 of 18, 25 of 29, 3 of 19 frames. | FAIL |
| D-4 cues after a switch, S1E3 | Reopen, Auto: 17 of 24 frames with cues. Quality 720p, wait 8 s: root `Subtitles Russian`, Audio `Track 1` | cues keep rendering | 0 of 49 frames with cues over 40 s | FAIL |
| D-4 cues after a switch, S2E3 | Auto: 5 of 21 frames. Quality 720p | cues keep rendering | 1 of 56 frames; back to Auto without a re-pick: 18 of 25 frames (cues return) | FAIL (weaker sample than S1E5/S1E3) |
| D-7 Subtitles page during a quality switch | 720p to 480p, then BACK, Up, OK within about 1.5 s; screenshots at 0.3 s, 3 s, 10 s | focus stays in the panel, rows present | Subtitles page open at all three times, `Russian` checked, rows `Off`, `Russian`, `English`, `Aa Size`, focus in the panel, no crash | PASS |
| D-1 quick re-check | `Russian` rendering on Auto after each return from 720p/480p | cues render | rendering on Auto in all three episodes | PASS |
| S2E3 AI row | S2E3, Settings, Subtitles | whether `AI-generated` is listed | rows are `Off`, `Russian`, `English`; no `AI-generated`. No code filters the `ai` language out (`SubtitleLabeler` only labels it), so the API list does not offer it for this episode. Inferred, the raw list was not inspected. | observation |
| D-2 manifest-only language (AC-2) | no H1 title | n/a | not observed | NOT CHECKED |
| D-5 progressive (AC-5) | manual qualities still try `hls` first (`PlayerInteractor.kt:233-240`), so no progressive stream is reachable | n/a | not observed | NOT CHECKED |

## Observations that are not ACs

1. Manual quality: Settings Audio reads `Track 1` instead of the named track (`01. Многоголосый. NewStudio (RUS)`) on 720p and 480p; Auto shows the name.
2. At 480p (and once at 720p) the video surface showed the new stream at its native size in the top-left corner over a frozen frame
   of the previous size (video render artefact after the switch). Not compared with the base build.
3. Pressing Previous restarts the item and resets Quality to Auto (not persisted).

## Account and app state changes caused (round 2)

1. Watch progress: short plays of Outlander S1E5 (twice, resumed at 2:19), S1E6 (next episode), S1E3 (resumed at 4:21), S1E4 (opened by mistake), S2E3 (resumed at 2:04).
   The saved positions of those episodes may have moved.
2. Saved subtitle for Outlander: `Russian` re-picked once on S1E5 at 480p; the saved value stays `Russian`.
3. Quality is not persisted and was returned to Auto. The player info overlay was toggled once by a stray key and closed with the player.
4. Subtitle size, aspect ratio untouched (`Medium`, `Auto`).

---

# Round 3 (fix round 2 build)

Date: 2026-10-14. Device: emulator-5554 (`puber_tv_36`, API 36 TV), leased and released.
Build: worktree HEAD f4b311e0 (base a97ee2b1). `app-dev-debug.apk` `com.kino.puber.stage` 1.9.70-dev, versionCode 2090000000,
sha256 `62b37f060d683800dc0dd1ab26377fc167e7a2275219231cb102268f515a5919`. Installed with `tools/android-apk-install-preserve`
(`installed`, `compatible_replace`, same signer `6b29181257cb520329553691b7a48c9b1123899950105127b3dd67012c49a83e`, no data lost);
the on-device `base.apk` has the same sha256. Input: remote key events only. Cue presence was measured by sampling screenshots in
memory (a black box with white text in the subtitle strip, never saved to the repo); no screenshots, logs or UI dumps are kept in the repo.

Static gate: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` BUILD SUCCESSFUL (tasks up to date for this tree);
result XML read: 1590 tests, 0 failures, 0 errors, 0 skipped.

Expected (D17, D19): on a stream with no text tracks the picker shows only `Off`, no language row checked while nothing renders; the
viewer's pick is remembered and re-applied when a stream with that row returns; `Off` is not persisted by the collapse.

## Checks

| Check | Steps | Expected | Observed | Result |
|---|---|---|---|---|
| 1. Outlander S1E5 collapse and return | Auto, saved `Russian` (page: `Off`, `Russian` checked, `English`), pick `Russian` again, Quality 720p, wait 8 s, Settings root and Subtitles page; then Quality Auto without a re-pick | 720p: only `Off` (checked) and no cues; Auto: `Russian` checked again, cues render | 720p: root `Subtitles Off`, Audio `Track 1`, page `Off` (checked) and `Aa Size` only, no cue in the frame. Back on Auto after 8 s: root `Subtitles Russian`, page `Off`, `Russian` (checked), `English`, cue rendered ("Это был долгий день.") with no re-pick | PASS |
| 1b. Reopen S1E5 | BACK to the detail page, reopen S1E5, Continue, Settings | saved `Russian` restored (`Off` not persisted) | root `Subtitles Russian`, cue rendered | PASS |
| 2. Same on S1E3 | reopen S1E3 (Continue at 6:02), page `Off`/`Russian` checked/`English`, cue; pick `Russian`, 720p, wait 8 s; sample 30 s | 720p: only `Off`, no cues; Auto: `Russian` returns, cues | 720p: root `Subtitles Off`, page `Off` only, 0 of 35 sampled frames with a cue. Auto without a re-pick: root `Subtitles Russian`, 31 of 35 frames with a cue. Reopen S1E3 after exit: `Subtitles Russian`, cue rendered | PASS |
| 3. Next episode (AC-7, D-8) | S1E5, Next | pick applies on S1E6, restore after reopen | S1E6: root `Subtitles Russian`, cues rendered at 2:20, 2:55, 3:01. Restore after back-and-reopen was seen on S1E5 and S1E3 (checks 1b, 2). The leak case (a pick leaking into the next episode) cannot be told from a restore on the device, the saved value is also `Russian` | PASS (consistent), leak case unit tests only |
| 4. D-7 focus during a switch | Auto to 720p (rows 3 to 1), open the Subtitles page right after the pick | focus stays in the panel, no crash | Attempt with 0.6 s key spacing: the Subtitles page was open about 1.2 s after the pick (switch overlay `Render 0.8 fps`), only `Off` (focused, checked) and `Aa Size`, focus stayed in the panel at 0.2, 3 and 10 s. Attempt with a combo of keys: the Settings root was seen with `Subtitles Russian` and then `Subtitles Off` within 1 s (the collapse), `Quality` row stayed focused. No crash entries for the package afterwards. Keys sent within about 0.9 s after selecting 720p (BACK, UP, OK) were not applied in two attempts (Quality page stayed); not compared with master | PASS (focus), observation on dropped keys |
| 5. Manual-quality probe, Psyren S1E1 (AC-1) | Auto: Subtitles page lists manifest and API rows with variants (`Spanish · variant 1/2`, `SPA #01 Forced · forced`, `Russian · variant 1/2`, `Portuguese`, `Polish` ...). Quality 720p, wait 8 s | language rows remain on the manual-quality stream; side-loaded rows render | After 720p the page lists `Off` (checked), `Chinese`, `Russian`, `Portuguese`, `Polish`, `Italian`, `French`, `Spanish`, `German`, `English` (nine languages, one row each, no variants), `Aa Size`. Picking the first language row (`Chinese`) rendered cues (12 of 34 sampled frames, Chinese text in the strip). The manual stream has no manifest text (no variants), so these are the side-loaded API subtitles | PASS (AC-1 on a title with an API-only list) |
| API count for Outlander S1E5 | Detail page facts line | per-episode count | The series page shows `Subtitles: 5`. `DetailsScreenUIMapper.subtitleCount()` sums the subtitles of every episode in the loaded seasons, so it is a series total, not S1E5's own count. The picker shows two rows (`Russian`, `English`) on Auto. Psyren shows `Subtitles: 9` (consistent with the 9 languages) | observation only |

## Account and app state changes caused (round 3)

1. Playback of Compostelle (Home hero card) was started by a stray OK press and backed out within about 3 s; its watch progress may have moved.
2. Watch progress: short plays of Outlander S1E5 (resumed at 9:09), S1E6 (next episode, to about 3:00), S1E3 (resumed at 6:02, again later to about 9:00), Psyren S1E1 (resumed at 5:37).
3. Outlander saved subtitle: re-picked `Russian` (S1E5, S1E3), saved value stays `Russian`.
4. Psyren saved subtitle: was `Off`; `Chinese` was picked for the probe and `Off` was picked again at the end (verified on the page). Net value `Off`.
5. Quality is not persisted. Subtitle size (`Medium`) and aspect ratio (`Auto`) untouched. The stream info overlay appears after a quality switch by itself.
