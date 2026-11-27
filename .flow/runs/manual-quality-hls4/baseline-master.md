# Baseline on master: player Settings Audio row

Date 2026-11-27. Master at 0823528b (main checkout, no worktree changes). Device: owner's TV box 192.168.1.110:5555, package com.kino.puber.stage only.

## Build

Master dev APK built in /home/dev/Puber with `./gradlew :app:assembleDevDebug`, sha256 4354d24dda6ce6a7034f5dde1fdb9e2b3a03730ea2c1decdfe8a15c6f632c128. The box had 64b7d48f... (the earlier master build), so I ran install-preserve (outcome `installed`, `compatible_replace`, no uninstall, signer 6b29181257cb... unchanged). On-device base.apk sha256 matched the build. Force-stop and launch: home screen, logged in, no pairing screen.

## Item

Home rail "Горячие" first card: "Квазимодо, душа Парижа / Quasimodo, l'âme de Paris" (movie, 2026; detail page shows "Дорожек: 4 · Субтитры: 11"). Watch started from the beginning area of the movie. Human pace, one item, no API calls other than the app's own.

## Observations

| State | Audio row in Settings | Tracks in Audio list | Quality / stream | Screenshot |
|---|---|---|---|---|
| First start (Quality Auto) | "01. Дубляж (RUS)" | 4: "01. Дубляж (RUS)" (checked), "02. Дубляж (UKR)", "03. Оригинал (FRE)", "04. Дубляж (RUS) AC3" | Авто, 1920x800 | 1, 2 |
| After switch Auto -> 720p | "Track 1" | 1: "Track 1" (checked) | 720p, 1280x534 | 3, 4 |
| After second manual switch 720p -> 480p | "Track 1" | 1: "Track 1" (checked) | 480p, 720x300 | 5, 6 |

Notes:
- The second switch went to 480p, not 1080p (my D-pad step landed on 480p; the list screenshot shows the check on 480p). It is still a second manual switch from a manual quality.
- Playback kept running after each switch; no error screen, no pairing screen.
- The label change reproduces on master: the API name is shown at first start, "Track 1" after the first manual switch, unchanged after the second, and the list collapses from 4 entries to 1.

Screenshots (no account data): /home/dev/Puber/.flow/runs/player-audio-track-label-fallback/baseline-master-shots/
1-settings-first-start.png, 2-audio-list-first-start.png, 3-settings-after-720p.png, 4-audio-list-after-720p.png, 5-settings-after-480p.png, 6-audio-list-after-480p.png

## Device state

Backed out of the player and force-stopped the app. Master dev APK (4354d24d...) left installed on the box. Lease released (). com.kino.puber was not touched.
