# update-dialog-focus-trap: device evidence

## Round 3 (HEAD a3bcee75, review fix round: focus holder in Checking/Downloading, Details and Player claims gated)

Device: emulator-5554 (AVD puber_tv_36, Android 16 TV image), leased through tools/emulator-resource-lock.sh and released afterwards.
Build: check.full green (BUILD SUCCESSFUL, 46 tasks). Install: tools/android-apk-install-preserve, outcome "installed", on-device base.apk sha256 equal to the built APK for the evidence build and the final committed build.
Evidence build (owner decision Q2): currentVersion 1.12.1 in app/build.gradle.kts, uncommitted, versionCode unchanged; restored with git checkout afterwards, git status clean. The app saw the published v1.12.2 as an update. Update was never pressed. Focus was read from a transient uiautomator dump held in memory (only focused bounds kept); screencaps stayed outside the repository.
Prompt buttons: Update [236,742][399,838], Not now [419,742][599,838]. Home hero [120,0][1920,560], first card [152,644][685,944], Details Watch movie [22,718][251,814].

### Instrumented (make DEVICE=emulator-5554 GRADLE=./tools/agentw itest)
ContentFocusGateTest 3/3, UpdatePromptOverlayFocusTest 7/7 (includes updatePromptKeepsFocusInsideWhenUpdateStartsTheDownload and updatePromptCheckingTakesFocusFromContentBeneath), PlayerLaunchFocusGateTest 2/2. 12 tests, 0 failures.

### Cold starts with an update available (AC-1, AC-3, AC-4, AC-5, AC-6), 3 runs
Run 1 (Back): prompt first seen 6.7 s after launch (network), Update focused at that first dump and after 5 s more. Up/Down/Left: Update; Right: Not now; Right, Up, Down: Not now; Left: Update. Back: prompt gone, hero focused; Down: first card; Right: second card [717,644]. PASS.
Run 2 (AC-5, then Not now): Update focused; after eight D-pad moves in the prompt the full node tree bounds are identical to before (same scroll, no card focused, exactly one focused node). Not now: hero focused; Down: first card; Right: second card. PASS.
Run 3 (Not now): prompt seen at 8.4 s with Update focused, same D-pad matrix; Not now: hero focused, Down first card, Right second card. PASS.

### Settings path (AC-2, AC-8, triage #4)
Rail, Settings, General, row "Check for updates now" [824,444][1800,548], Center: Update focused at the first dump (the check result arrives within about one second), Up/Left/Down stay on Update, Right moves to Not now.
(a) Back on this path (prompt over focused settings content): prompt dismissed, no rail items in the dump (rail closed), focus on settings section General [220,174][740,270]; Down moved to [220,280][740,376], Up back, Right to the first row [824,228][1800,324]. PASS.
AC-8 with Not now: focus on General [220,174][740,270], rail closed, Down/Up move within settings. PASS (not the exact row the check started from).

### (c) Checking state from Settings
Screencap burst right after Center caught the Checking prompt for a fraction of a second (scrim, "Checking for updates", spinner); the next frame is Available. The check finishes faster than a uiautomator dump (about 1 s), so focus inside Checking could not be read on device, and no extra Enter was sent (it could land on Update). Covered by instrumented updatePromptCheckingTakesFocusFromContentBeneath (pass). Device: NOT CHECKED.

### (b) Content link cold start (triage #3)
am start -a VIEW -d puber://content/items/100 (Details, no action=play): Details (movie page) opens beneath the prompt. Update focused from the first poll to 9 s; Up/Down/Left/Right matrix stays on Update/Not now; Not now: focus on the Details first button Watch movie [22,718][251,814] (screencap), prompt gone. Second run: Back dismissed the prompt, focus on Watch movie, Details still open; Right moved to Trailer [271,718]. PASS.
Player target (action=play) not run: it starts playback and writes watch progress to the account (mutation). Covered by PlayerLaunchFocusGateTest (2/2 instrumented). NOT CHECKED on device.

### Final smoke (committed build 1.12.3-dev, no prompt)
Cold start: hero holds focus; Back opens the rail ([8,24][520,120]); second Back returns focus to the hero. As base. PASS.

### Not run
Download state on device (needs Update); covered by instrumented updatePromptKeepsFocusInsideWhenUpdateStartsTheDownload. Bookmarks menu and ApiDomainDialog not re-run (unchanged since round 2).

---

# Round 2 (superseded)

## Round 2 (HEAD cb88f430, fix commits b3307eb7 and 59a2ebb8)

Device: emulator-5554 (AVD puber_tv_36, Android 16 TV image; restarted by the implementer with -read-only, app and login intact), leased through tools/emulator-resource-lock.sh and released afterwards.
Build: check.full green (BUILD SUCCESSFUL). Install: tools/android-apk-install-preserve, outcome "installed", on-device base.apk sha256 equal to the built APK, both for the evidence build and the final committed build.
Evidence build (owner decision Q2): currentVersion 1.12.1 in app/build.gradle.kts, uncommitted, versionCode unchanged; restored afterwards, git status clean. The app saw the published v1.12.2 as an update. Update was never pressed. Focus was read from a transient uiautomator dump held in memory (only the focused node bounds kept); screencaps stayed outside the repository.
Prompt buttons: Update [236,742][399,838], Not now [419,742][599,838]. Home hero [120,0][1920,560], first Continue-watching card [152,644][685,944], rail item [8,24][520,120].

### Cold starts with an update available (AC-1, AC-3, AC-4, AC-5, AC-6), 3 runs
Run 1 (Back): the first poll (<= 1.5 s after launch) already found Update focused; unchanged after 4 s. Up/Down/Left: Update; Right: Not now; Right again, Up, Down: Not now; Left: Update; Right: Not now. Rail closed throughout. Back: prompt gone, focus on the Home hero, rail closed; Down: first card [152,644]. PASS.
Run 2 (Back): Update focused at first poll and after 8 s; Left/Up stay on Update, Right/Down on Not now. Back: hero focused; Down: first card; Right: second card [717,644]. PASS.
Run 3 (Not now): same until Not now focused; Center: prompt gone, focus on the hero; Down: first card; Right: second card. PASS.
AC-5 screencap pair (run 1, before and after eight D-pad moves inside the prompt): the Home rows beneath are at the same scroll position and no card was focused or scrolled; only the auto-advancing hero backdrop differs (the carousel rotates by itself). Screencap C after Back shows the rail closed and the hero focused.

### Prompt opened over Settings (AC-2, AC-8)
Rail, Settings, General, "Check for updates now" focused ([824,444][1800,548]), Center: Update focused on the first dump after the press (under 2.7 s including the dump time; the 1 s bound is the instrumented test). Up/Down/Left kept Update, Right moved to Not now, rail stayed closed. Not now (Center): focus on the settings section list item General [220,174][740,270], rail closed (screencap); Down moved to Playback and Up back. Focus lands on a visible settings item, not on the row the check started from. PASS.

### ApiDomainDialog (AC-9), Settings > Network and device > Change KinoPub mirror
Dialog opened with Save focused. Back closed it and focus returned to the Change KinoPub mirror row. Reopen, Up to the text field, Center: IME shown; Enter (IME Done): IME hidden, focus moved to Save, dialog still open. Reopen field, IME shown, Back: IME hidden, focus on Save; Back again: dialog closed, focus on the mirror row. Mirror unchanged (service-kp.com); nothing was saved. PASS.

### Final smoke (committed build 1.12.3-dev, no prompt)
Cold start: hero holds focus; Back opens the rail (focus [8,24][520,120]); second Back returns focus to the hero. As base. PASS.

### Not run
Bookmarks context menu on device (covered by BookmarksScreenContentFocusTest and TvContextMenuLongPressTest, run on device in round 1). Strict "Home holds focus before the prompt" order on a cold start: the prompt now wins within the first 1.5 s, so the order did not occur; the Settings path covers a prompt over focused content.

---

# Round 1 (superseded; kept for history)


Device: emulator-5554 (AVD puber_tv_36, Android 16, TV image), leased through tools/emulator-resource-lock.sh and released afterwards.
Commit under test: 626040a3 (worktree HEAD). Install: tools/android-apk-install-preserve, outcome "installed", on-device base.apk sha256 equal to the built APK both times.
Evidence build (owner decision Q2): currentVersion lowered to 1.12.1 in app/build.gradle.kts, not committed, versionCode unchanged (2090000000); file restored afterwards, git status clean. The app then saw the published v1.12.2 as an update. Update was never pressed. Focus was read from a transient uiautomator dump held in memory (only the focused node bounds were kept); screencaps stayed outside the repository.

## Instrumented (make DEVICE=emulator-5554 GRADLE=./tools/agentw itest)
Classes: ContentFocusGateTest, UpdatePromptOverlayFocusTest, TvDialogOverlayFocusTrapTest, TvContextMenuLongPressTest, BookmarksScreenContentFocusTest, HistoryScreenContentTest, ShowAllRailCloseFocusTest. 51 tests, 41 pass, 10 fail.
The three new classes (ContentFocusGateTest, UpdatePromptOverlayFocusTest, TvDialogOverlayFocusTrapTest) pass, as do TvContextMenuLongPressTest, BookmarksScreenContentFocusTest and ShowAllRailCloseFocusTest. The 10 failures are all in HistoryScreenContentTest and all are lookups of Russian text (Повторить, История, Подробнее, ...) while the emulator locale is en-US; the file is untouched by this change. They are environmental, not caused by the diff; the focus tests of that class pass.

## M1 launch race (evidence build), 4 cold starts
Prompt appeared about 7.5 s after launch, after Home had loaded and focused (not before Home's hero), so this is the "Home already holds focus" order, not the launch race.
- Run A: focus on a Home card beneath the prompt (bounds [152,84][685,384]) for 30 s after the prompt showed. Update button focused="false". DPAD_RIGHT moved the Home card focus beneath the prompt. FAIL.
- Runs B and D: focus on a Home row card; DPAD_LEFT moved focus to the rail item (bounds [8,24][520,120]) while the prompt was still shown; Back dismissed the prompt and left focus on the rail. FAIL.
- Run C: the prompt did not show within 12 s of dumps; not counted.
Expected: Update focused, D-pad stays in the prompt, Back dismisses with the rail closed and focus on Home. Observed: none of it.

## Prompt opened over Settings (manual check path, same build)
Device settings > Check for updates now (result state Available): Update focused (bounds [236,742][399,838]) within 2.5 s, scrim dim, Up/Left/Right/Down kept focus on Update or Not now, rail did not open. PASS for focus and trap.
Dismiss with Not now (DPAD_CENTER): focus moved to the rail's Settings item (bounds [8,904][520,1000]) and the rail was revealed; not to a visible settings item. FAIL for return of focus.

## Final smoke (committed build 1.12.3-dev, no prompt)
Cold start: hero holds focus; Back opens the rail (focus [8,24][520,120]); second Back returns focus to the hero. As base. PASS.

## Not run
ApiDomainDialog IME Done and Back (M4 step 4): not run on device after the defects above; Bookmarks context menu covered only by the instrumented classes.
