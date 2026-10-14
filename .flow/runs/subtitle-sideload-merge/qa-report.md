# QA report: subtitle-sideload-merge

## Round 1

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; 1561 tests, 0 failures, 0 errors, 0 skipped; Gradle reported the tasks up to date for this tree, result XML read; named P0 tests present)
DEVICE: emulator-5554 (puber_tv_36), leased and released. APK sha256 6c1580aa9282db0aaf73b94649eea8754776eac962985df20ac028cd41ea98eb, install-preserve outcome `installed`, on-device base.apk sha256 matches.

ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS (unit), device PARTIAL | Unit tests present and green. Device: Outlander HLS shows `Russian`/`English`, pick renders cues. No H1 title, so API-only cannot be told apart from shared. |
| AC-2 | NOT CHECKED on device | No manifest-only language observable (picker shows only the merged result). Unit merger and `isOff` tests pass. |
| AC-3 | PASS | `SubtitleTrackMergerTest` green; device shows one row per language. |
| AC-4 | FAIL on device | Selector unit tests pass, but the selection is lost after a quality switch (Defect 1). |
| AC-5 | NOT CHECKED on device | No progressive title: progressive is only a fallback after an HLS failure. Unit tests pass. |
| AC-6 | FAIL on device | Defect 1. VM unit tests pass but do not cover a report with no text groups after `switchStream`. |
| AC-7 | PASS | Unit tests green; saved `Russian` restored on S1E5 and S2E3. |
| AC-8 | PASS (unit); labels `Russian`, `English`, `Off` seen, wording is the owner's call | `SubtitleLabelerTest` green; no raw ids on screen. |
| AC-9 | NOT CHECKED on device | Evidence method is the Media3 trace D15; no failing-subtitle title turned up. |
| AC-10 | observation only | Psyren S1E1 OK press to first video frame: 2.16 s (first start), then 0.37, 0.45, 0.79 s. Master not built, no comparison. |
| AC-11 | PASS | Gate above; JUnit 5, `FakeResourceProvider` in the new tests. |

TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/subtitle-sideload-merge/docs/superpowers/evidence/2026-10-14-subtitle-sideload-merge-device.md (committed a703075f, 1b48aacd; `mobile-evidence-audit.sh` passed)

DEFECTS:
1. Quality switch on an HLS stream with API subtitles drops the picker rows and the selection.
   Repro: Outlander S1E5 (also S1E3), Quality Auto, Settings, Subtitles, pick `Russian` (cues render). Settings, Quality, `720p` (or `480p`). Wait 5-10 s. Settings root reads `Subtitles Off`; the Subtitles page lists only `Off` (checked) and `Aa Size`. Back on Auto the `Russian`/`English` rows return with `Off` still checked. Expected: `Russian` stays selected and rendering (AC-4, AC-6, D-4). Not seen on S2E3 (rows and selection survived there).
   Likely cause (unverified): after `switchStream` the controller reports a text-less track set while audio groups exist, `SubtitleTrackMerger` then returns only `Off`, `findSameSubtitleTrackIndex` finds nothing, the VM selects `Off`. No failing unit test covers this (a report with empty `subtitleTracks` list after a prior non-empty one). Failing check: device D-4.

UNAVAILABLE: no title for H1 (API-only plus manifest-only language) or P1 (progressive); no failing-subtitle title (D-9). The picker cannot show the source of a row.

MANUAL:
- Owner: confirm Outlander saved subtitle. It was `AI-generated`; the API list no longer offers that row on this build and the saved choice is now `Russian` (picked during D-1). It cannot be restored from the UI.
- Owner: if needed, name or find an HLS title whose manifest has a language the API lacks (D-2), and decide how to exercise progressive (D-5).
- Owner: the quality-switch defect needs a decision before ship.
- Side effects caused and reverted: subtitle size (Medium, verified) and aspect ratio (Auto, verified).

## Round 2

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; 1578 tests, 0 failures, 0 errors, 0 skipped; result XML read; HEAD 7ca85b93)
DEVICE: emulator-5554 (puber_tv_36), leased and released. APK sha256 1d1ec6da84beea2b70c57171e546084292f95a02a92ec8175c5b79a4e3baebdb, install-preserve outcome `installed` (compatible_replace, same signer), on-device base.apk sha256 matches.

ACS (affected ones):
| AC | Status | Evidence |
|----|--------|----------|
| AC-4 | FAIL on device | Row and pick now survive a manual-quality switch (root `Subtitles Russian`, page `Off`/`Russian` checked/`English`) on S1E5 and S1E3. But the selected track does not render: no cues on 720p/480p (see Defect 1). Unit tests green. |
| AC-6 | FAIL on device | Same as AC-4: rows and selection preserved, engine shows no text. The VM now says Russian while nothing is displayed. |
| AC-7 | PASS | Saved `Russian` restored on cold start (S1E5), on back-and-reopen (S1E3, cues 17/24 frames), on next episode (S1E6), S2E3. |
| AC-1 / AC-3 | PASS (unchanged), device PARTIAL as in round 1 | one row per language on S1E5, S1E3, S2E3. |
| AC-2 | NOT CHECKED | no manifest-only language title. |
| AC-5 | NOT CHECKED | manual and auto candidates try `hls` first; progressive is unreachable without an HLS failure. |
| D-1 (pick renders) | PASS on Auto | cues render on Auto in all episodes, also after returning from 720p/480p. |
| D-7 | PASS | Subtitles page opened within 1.5 s of a 720p to 480p switch: focus stayed in the panel, rows present at 0.3, 3 and 10 s, no crash. |
| D-8 | PASS | see AC-7. Next-episode leak case cannot be told from a restore on device (saved value is also Russian); unit tests only. |
| S2E3 "AI-generated" row | observation | not listed; rows `Off`, `Russian`, `English`. No code filters `ai` out, so the API list does not offer it for this episode (inferred; the raw list was not inspected). |

TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/subtitle-sideload-merge/docs/superpowers/evidence/2026-10-14-subtitle-sideload-merge-device.md, section "Round 2" (committed 1b79c661; `mobile-evidence-audit.sh` passed)

DEFECTS:
1. After a switch to a manual quality (720p or 480p) the picker says `Russian` but no subtitle cues are rendered. Defect 1 is fixed only in the VM state; the player has no text to show.
   Repro: Outlander S1E5 (also S1E3; S2E3 weaker), Quality Auto, Settings, Subtitles, `Russian` (cues render; 8/18, 25/29 sampled frames on Auto). Settings, Quality, 720p (or 480p). Wait 8 s. Settings root reads `Subtitles Russian` and the Subtitles page lists `Off`, `Russian` (checked), `English`. Watch a dialogue scene 40 s or longer: 0 cue frames on S1E5 480p (0/32, and 0/27 after picking `Russian` again), none in about 60 frames on S1E5 720p, 0/49 on S1E3 720p, 1/56 on S2E3 720p. Quality back to Auto: cues return without a re-pick (S2E3 18/25, S1E5 25/29).
   Likely cause (not verified in code): the "transient text-less report" assumption in 36da8957 (`PlayerVM.onTracksUpdated`, `textlessReport`) is wrong for manual streams. The text groups never arrive (no side-loaded or manifest text on the `hls` variant after `switchStream`), so rows and pick are held while the player renders nothing. Settings Audio also reads `Track 1` on those streams (named track on Auto), which suggests a different manifest or no manifest-level track data after the switch. Master behaviour on a manual quality was not measured (no base build installed), so whether this predates the change is open.
   Failing check: device D-4 ("stays selected and rendering"). No unit test covers it; the engine side (`PlaybackController.switchStream`, `savedTrackParams`, `applySubtitleTrackSelection` after the switch) is not unit-testable without an ExoPlayer.

UNAVAILABLE: no title for H1 (manifest-only language) or P1 (progressive); no failing-subtitle title (D-9).

MANUAL:
- Owner: decide whether "row stays but nothing renders on manual quality" blocks the ship, and whether master behaves the same on a manual quality (build master and repeat the repro: Quality 720p on S1E5 with `Russian` picked).
- Owner: Outlander saved subtitle is `Russian` (was `AI-generated` before round 1); S2E3 does not list an AI row now.
- Side effects: watch progress of Outlander S1E3 to S1E6 and S2E3 may have moved (short plays); everything else was returned (Quality Auto, subtitle size Medium, aspect Auto).
- Observations to look at, not ACs: Audio label `Track 1` on manual quality; after the switch the video surface at 480p drew the new stream at native size in the top-left corner over a frozen frame.

## Round 3

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; 1590 tests, 0 failures, 0 errors, 0 skipped; result XML read; HEAD f4b311e0)
DEVICE: emulator-5554 (puber_tv_36), leased and released. APK sha256 62b37f060d683800dc0dd1ab26377fc167e7a2275219231cb102268f515a5919, install-preserve outcome `installed` (compatible_replace, same signer), on-device base.apk sha256 matches.

ACS (affected ones):
| AC | Status | Evidence |
|----|--------|----------|
| AC-4 / AC-6 / D-4 (D17, D19) | PASS | S1E5 and S1E3: Auto, `Russian` renders; 720p after 8 s: root `Subtitles Off`, page only `Off` (checked), no cues (0/35 on S1E3); back to Auto without a re-pick: `Russian` checked again, cues render (31/35 on S1E3). |
| AC-7 / D-8 | PASS | Saved `Russian` restored after exit and reopen on S1E5 and S1E3 (`Off` not persisted by the collapse); S1E5 to S1E6 shows `Russian` with cues. Leak case: unit tests only. |
| D-7 | PASS (focus) | Subtitles page opened about 1.2 s after a 720p pick and around the 3 to 1 row collapse: focus stayed in the panel, no crash. Observation: BACK/UP/OK sent within about 0.9 s after the 720p pick were not applied in two attempts (not compared with master). |
| AC-1 | PASS on Psyren S1E1 (API-only list), still open for Outlander | On 720p the picker keeps nine language rows (no variants, so no manifest text) and `Chinese` rendered cues (12/34 frames): side-loaded API subtitles render on a manual-quality HLS stream. Outlander S1E5 own API count cannot be read: the detail page `Subtitles: 5` is a series total (`DetailsScreenUIMapper.subtitleCount()`), Psyren shows 9. |
| AC-2 | observation, no change in status | Outlander is most likely manifest-only (per delta); evidence file corrected (earlier "H2" reading withdrawn). |
| AC-3, AC-5, AC-9 | unchanged | AC-5 and AC-9 still NOT CHECKED on device. |

TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/subtitle-sideload-merge/docs/superpowers/evidence/2026-10-14-subtitle-sideload-merge-device.md, section "Round 3" (committed in the worktree; `mobile-evidence-audit.sh` passed)

DEFECTS: none open from this round. Defect 1 of round 2 (no cues after a manual quality switch) is resolved by D17/D19: the picker no longer claims a language while nothing renders.

UNAVAILABLE: no title for P1 (progressive) and no failing-subtitle title (D-9).

MANUAL:
- Owner: Outlander on a manual quality shows no subtitle at all (picker only `Off`). If that is not acceptable, side-loading the API subtitles for Outlander on manual qualities is a separate task (Psyren shows the API rows work there).
- Account state changes: stray Compostelle start (backed out), watch progress of Outlander S1E3/S1E5/S1E6, Psyren S1E1; Psyren saved subtitle was changed to `Chinese` and returned to `Off`.
- Observation: keys pressed within about 0.9 s after a manual quality pick are dropped; check against master if it matters.
