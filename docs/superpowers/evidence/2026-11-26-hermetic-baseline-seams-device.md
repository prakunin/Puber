# hermetic-baseline-seams - device evidence (AC-6)

- Date: 2026-11-26. Head: 23fb886d. Round 2, AC-6 only (manual check 4).
- Device: 192.168.1.110:5555 (owner's TV box, standing authorization), package com.kino.puber.stage only.
- Build: this worktree's :app:assembleDevDebug, version 1.12.7-dev; install-preserve outcome "installed" (compatible_replace, destructive_action false, same signer).
- APK sha256 (build) 95a923cf8df244e55289379b2dace28e5042993742325c6adad689d77d825c80; on-device base.apk sha256 identical.
- auth-save run before the install (data kept under .auth/, not in evidence).

## AC-6: dev APK starts like master

Steps: force-stop; clear crash buffer; `am start -W` MainActivity (LaunchState COLD, TotalTime 9471 ms, Status ok); wait 12 s; screenshot; HOME; relaunch (task brought to front, HOT, 125 ms); wait 8 s; screenshot; check crash buffer.

Expected: Main screen with the navigation rail, no pairing screen, no crash.

Observed: MainActivity resumed, process alive (pid present after relaunch). Main with the full rail is drawn, with the app's "new version 1.12.8" update dialog on top (app-level update prompt, unrelated to this change). No pairing screen. Crash buffer entries for the package: 0. No API calls were driven by the tester.

Screenshots: 2026-11-26-hermetic-baseline-seams-1-cold.png, 2026-11-26-hermetic-baseline-seams-2-relaunch.png

Result: PASS. Note: no master-build screenshot was taken for a side-by-side; "same as master" rests on the expected Main-with-rail result.
