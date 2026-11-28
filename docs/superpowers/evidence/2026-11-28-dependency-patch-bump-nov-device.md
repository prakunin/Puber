# dependency-patch-bump-nov - device evidence (QA round 1)

Date: 2026-11-28. Head: 876912c9. Package: com.kino.puber.stage only.
Device: owner's TV box 192.168.1.110:5555 (model KM6, Android 10), under a lease (released afterwards).
Build: worktree dev debug APK, sha256 9534f7c7fb168ab45264c0499299759ee2bf335a443c2eacde15d74930abe6f2
(on-device base.apk sha256 identical). install-preserve outcome: installed, compatible_replace, destructive_action false, same signer.
Before the install: make DEVICE=192.168.1.110:5555 auth-save (snapshot under gitignored .auth/).
No scripted API calls: remote keyevents at human pace; one title opened, one playback of about 40 s.

## AC-6 instrumented tests (make DEVICE=... GRADLE=./tools/agentw itest TESTS=<class>)

| Class | Result |
|---|---|
| com.kino.puber.data.db.WatchStateDaoTest | BUILD SUCCESSFUL |
| com.kino.puber.data.db.CachedPayloadDaoTest | BUILD SUCCESSFUL |
| com.kino.puber.data.db.PersistentPayloadStoreDbTest | BUILD SUCCESSFUL |
| com.kino.puber.data.db.PuberDatabaseMigrationTest | BUILD SUCCESSFUL, 6 tests, 0 failures, 0 errors, 0 skipped (result XML) |

## AC-7 manual checks M1-M5

| Step | Expected | Observed | Result |
|---|---|---|---|
| M1 install-preserve, hash compare | install succeeds, hashes match | outcome installed; on-device sha256 equals built APK | PASS |
| M2 launch via am start | Home, logged in | domain check spinner, then Home with hero and rows after about 20 s; no pairing screen | PASS |
| M3 DPAD_DOWN x3, RIGHT x5, CENTER | posters and badges, details with poster and backdrop | posters on every visible card, rating badges, episode badge and watched-eye badge on cards; details screen showed backdrop and metadata; backdrop of a second title also loaded | PASS |
| M4 start playback, wait, BACK | video plays and advances, back returns | two frames about 20 s apart differ (studio logo, then opening scene); BACK returned to details | PASS |
| M5 Device settings, session-log toggle, HOME, relaunch, toggle again | toggle flips each time, description follows | Developer section: ON ("Idyot zapis, ostalos 1 d 7 h") -> OFF (description "7 days" text) -> HOME and relaunch resumed on same screen, still OFF -> ON ("ostalos 6 d 23 h"). Data section showed Room counts (2 marked watched, 8 history entries). | PASS |
| Crash check | none | logcat crash buffer and FATAL EXCEPTION counts for the package: 0 | PASS |

Note: "Share log" (the only place the lifecycle currentStateFlow wait runs, DeviceSettingsContent.kt:213-214) was pressed once; no system chooser appeared on this box, focus stayed on the row. That path is therefore not exercised on the box; toggle and resume behaviour are.
Screenshots were viewed in the QA session and are not committed (they show account titles).
