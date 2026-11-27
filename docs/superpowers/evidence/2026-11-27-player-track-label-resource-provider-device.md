# player-track-label-resource-provider device evidence

Date: 2026-11-27. Device: owner's TV box 192.168.1.110:5555, package com.kino.puber.stage only.
Build: worktree dev debug APK, sha256 7f25f0f90e3cc30acd6ca7745450b8fb530c84a80588887d392192545396b3ff (commits a0dc2dd3, 609a7c43 on base 8db929b5), installed with install-preserve (outcome installed, compatible_replace, destructive_action false). On-device sha256 of base.apk matched after install.
Before: master dev APK (sha256 4354d24d...c128) was installed and logged in; app launched once and make auth-save run.
After: master dev APK put back with install-preserve; on-device sha256 equals 4354d24d...c128. Lease acquired and released. App language not touched.

## AC-4 (player starts, no Koin resolution crash)
Steps (human pace, D-pad only): launch app; Search; type "кваз" (one search result); open "Квазимодо, душа Парижа"; Watch film; answer the resume prompt with Continue; wait for playback.
Expected: player screen opens, playback starts, no NoBeanDefFoundException / InstanceCreationException.
Observed: resume prompt rendered by the player screen, then playback ran (position advanced 30:22 -> 31:31 on the progress bar). App process stayed the same pid and MainActivity stayed resumed through the run. Logcat (cleared before launch, read once after) counts: NoBeanDefFoundException 0, InstanceCreationException 0, FATAL EXCEPTION 0.
Result: PASS.

## Manual check 2 (audio picker)
Steps: controls (Down), Right to settings, OK; Audio row, OK.
Observed: Audio list shows four named rows ("01. Дубляж. Видеофильм (RUS)" checked, "02. Дубляж. LeDoyen (UKR)", "03. Оригинал (FRE)", "04. Дубляж. Видеофильм (RUS) AC3"); no empty rows or resource ids. Nothing selected, so the stored audio preference is unchanged. Exited with Back.
Note: these titles come from the API, so the "Track N" fallback string path is not exercised on the device; that is covered by the instrumented test.
Result: PASS.
