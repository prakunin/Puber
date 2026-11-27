# Device evidence: manual-quality-hls4

Date 2026-11-27. Round 1.

## Build and device

- Worktree HEAD 006b2a9d, dev debug APK sha256 121882f59a6c7fc5cc86982a1965685bad974e1df62597ad41bd8b0f00ee62f1 (on-device base.apk sha256 matched).
- Device: owner's TV box 192.168.1.110:5555, package com.kino.puber.stage only. Install-preserve outcome `installed`, `compatible_replace`, same signer, no uninstall. Before the install the master build was launched (logged in) and `make auth-save` ran.
- Item as in master baseline: "Квазимодо, душа Парижа" (Audio 4, Subtitles 11), resumed at about 5:30, one item, remote keys only, human pace, no API calls other than the app's own.
- Afterwards the master dev APK (sha256 4354d24d..., on-device match) was put back with install-preserve; it launches logged in (home screen). Stored audio and subtitle choices were returned to RUS / Off. Lease released.
- Shots: `manual-quality-hls4-shots/` (film frames only, no account data).

## Observations (Settings panel rows)

| Step | Audio row | Audio list | Quality | Stream row | Shot |
|------|-----------|-----------|---------|------------|------|
| Auto, start | 01. Дубляж (RUS) | 4 named, RUS checked | Авто | 1920x800 | 1, 2 |
| Auto -> 720p | 01. Дубляж (RUS) | 4 named, RUS checked | 720p | 1280x534 | 3, 4 |
| 720p, subtitles: Русский · вариант 1 | - | - | 720p | - | 5 (cue rendered) |
| 720p -> 480p | 01. Дубляж (RUS) | 4 named, RUS checked | 480p | 720x300 | 6, 7 |
| 480p -> Auto | 01. Дубляж (RUS) | - | Авто | 1920x800 | 8 |
| Auto, pick 02. Дубляж (UKR), then 720p | 02. Дубляж (UKR) | 4 named, UKR checked | 720p | 1280x534 | 9 |
| Leave player, start item again on Auto | 02. Дубляж (UKR) | - | Авто | 1920x800 | - |

Master baseline for the same item: 720p and 480p showed "Track 1" and a one-row list.

## Per AC

| AC | Steps | Expected | Observed | Result |
|----|-------|----------|----------|--------|
| AC-4 (M1) | Auto -> 720p -> 480p, read Audio row and list after each | Manifest name, 4 named tracks | Row "01. Дубляж (RUS)" and the 4-row list after each switch | PASS |
| AC-5 (M2) | Read Stream row after each switch | At most the picked quality, 720p differs from 480p | 720p 1280x534 (same as master), 480p 720x300, Auto 1920x800 again after Auto; no rebuffering noticed, no audio-only | PASS |
| AC-6 (M3) | On 720p open Subtitles, pick a row, watch about 35 s | Rows listed as on Auto, cues render | 11 rows listed (10 visible without scrolling, the rest below); cues rendered in several screenshots; subtitle row persisted over 480p, Auto and 720p | PASS (see note) |
| AC-7 (M4) | Watch after each switch | Picture fills the frame per aspect mode | No native-size corner draw or frozen frame in any screenshot after the switches (shots 3-8) | PASS |
| M5 | Auto, pick UKR, switch to 720p | Record row and dubbing | Row stays "02. Дубляж (UKR)", list of 4, UKR checked. Dubbing heard: not checked (no audio capture) | PASS for the row, audio not heard |
| M6 (AC-9 on device) | Leave and restart the item on Auto | UKR restored | Audio row "02. Дубляж (UKR)" after restart | PASS |

Notes:
- AC-6: the picker shows API rows and manifest rows through one list; the screen does not say which origin a row has. The check shows that the list and cues survive manual quality, not that a given row came from the hls4 manifest.
- R6 (does the hls4 URL change between 720p and 480p) cannot be seen without logs; not recorded.
- M6 does not exercise the fallback-label guard: with hls4 no row was a fallback. That path is covered by unit tests only.

## Instrumented (emulator, by owner decision)

Re-run at HEAD 006b2a9d on host.docker.internal:5555 (sdk_google_atv64_arm64): `make FLAVOR=instrumentation DEVICE=host.docker.internal:5555 GRADLE=./tools/agentw itest TESTS=com.kino.puber.ui.feature.player.vm.PlaybackControllerNetworkTracksTest` -> 11 tests, 0 failures, 0 errors, 0 skipped, including `hlsVideoCap_switchStreamReplacesCap` and `hlsVideoCap_belowEveryVariant_stillSelectsVideo`. The impl's itest-1.xml (12:44:39) predates the controller change in 2f89c766 (12:45:24), so it was not relied on.


## Round 2 (fix round 1, HEAD 07aba956)

Date 2026-11-27. Affected ACs: AC-2, AC-3, AC-5 (fix: VideoCapParameters clears the display viewport constraint under a cap, restores it on Auto).

### Build and device

- Worktree HEAD 07aba956, dev debug APK sha256 d5dd1d5edafe81917f42fdf53db3280adf9c7557e2e7bb36cc219845dcf313c3 (on-device base.apk sha256 matched).
- Device: owner's TV box 192.168.1.110:5555, package com.kino.puber.stage only. Before the install the master build (sha256 4354d24d...) was launched, logged in, and `make auth-save` ran. Install-preserve outcome `installed`, `compatible_replace`, same signer, no uninstall.
- Item: "Квазимодо, душа Парижа" opened from History, resumed at about 13:30, one item, remote keys only, human pace, no API calls other than the app's own. No audio or subtitle pick was made, so stored preferences were not touched.
- Afterwards the master dev APK (sha256 4354d24d..., on-device match) was put back with install-preserve; it launches logged in (home screen). Lease released.
- Shots (film frames and player panel only): `manual-quality-hls4-shots/r2-*.png`.

### Observations (Settings panel rows)

The Quality list offers Авто, 1080p, 720p, 480p; the highest manual quality is 1080p.

| Step | Quality | Audio row | Stream row | Shot |
|------|---------|-----------|-----------|------|
| Auto, start | Авто | 01. Дубляж (RUS) | 1920x800 | r2-1 |
| Auto -> 1080p (highest offered) | 1080p | 01. Дубляж (RUS) | 1920x800 (equals Auto) | r2-2, r2-3 |
| 1080p -> 720p | 720p | 01. Дубляж (RUS) | 1280x534 | r2-4 |
| 720p -> Auto | Авто | 01. Дубляж (RUS) | 1920x800 | r2-5 |

### Per AC

| AC | Steps | Expected | Observed | Result |
|----|-------|----------|----------|--------|
| AC-5 | Auto -> 1080p -> 720p -> Auto, read Stream row after each (about 12 s settle) | At most the picked quality, 720p below the 1080p pick, Auto back to full | 1080p pick plays 1920x800 (the same size as Auto, not lower, not audio-only), 720p plays 1280x534 (as in round 1 and master), Auto returns to 1920x800. Picture filled the frame, no corner draw, no rebuffering noticed | PASS |
| AC-2 | Same switches, plus instrumented | Cap applied on switch; Auto lifts it | Device: Auto after 720p returned to 1920x800 (cap lifted). Instrumented `hlsVideoCap_switchStreamReplacesCap` now also asserts viewport cleared under a cap and restored on Auto; itest-fix1-3.xml 12/12 green (emulator, run after the last source edit at 13:20:14) | PASS |
| AC-3 | Instrumented | Cap below every variant still selects video | `hlsVideoCap_belowEveryVariant_stillSelectsVideo` green in itest-fix1-3.xml; now also asserts the 320x180 cap was applied | PASS |

Notes:
- The 1080p pick equals the Auto size on this item, so the Stream row cannot distinguish "pinned to the pick" from "Auto picked the top variant"; it does show the pick is not reduced below the display-sized variant and that the nominal 1080 pick does not exclude the 1920x800 variant.
- Whether the pick's nominal API size is 1920x1080 or 1920x800 is not shown by the UI.
