# Device evidence: marktime decode fix (resume-progress-anomaly), round 1

Build: HEAD 37c5c7af, dev debug APK, version 1.12.5-dev, sha256 250f9ea9978f1fdcbb5689d52f8bc14b6d851bdd37b61d6be27a13fe7c6ba37d. No shim. The on-device base.apk sha256 equals the local APK after install and again at the end of the run.
Device: owner's TV box 192.168.1.110:5555, package com.kino.puber.stage only. Lease held and released. Install: install-preserve, outcome installed, classification compatible_replace, no destructive action. auth-save taken before the install.
Method: D-pad only (adb input keyevent). Log evidence is counts of filtered lines (no raw logcat). The player surface and, on this box, screencap of the player come out as a white frame; text on screen was read through uiautomator dump filtered to the resume dialog and Details text lines.
Seek: about 30-40 s per DPAD_RIGHT press during playback.

## Step 1: movie (AC-5, AC-6)

Steps: logcat -c; Movies list, open a movie not played before; Details showed "Not started - 1 h 55 min". Play, 52 DPAD_RIGHT seeks, 45 s wait (more than one 30 s save tick), BACK to Details.
- grep -cF "Playback progress save failed": 0
- marktime responses with HTTP 200: 5; with any other status: 0
- Back on Details (screenshot and uiautomator): focus kept on Play, but the progress line still read "Not started - 1 h 55 min", also after leaving Details and opening it again from the list about 2 minutes later.
- Play again from Details: resume dialog "Continue from 28:23?" with Restart / Continue buttons. PASS for the dialog.
- Result: AC-6 PASS (movie). AC-5 half 1 (no failure lines) PASS; AC-5 half 2 (Details shows saved progress) FAIL: the label did not change although the dialog read the new position from the same item.

## Step 2: series episode (Q3)

Steps: logcat -c; Series list, open a series not played before; Play (episode 1), 16 DPAD_RIGHT seeks, 45 s, BACK.
- failure-line count: 0; marktime HTTP 200: 2; other: 0
- Details after BACK: resume line "Next - season 1, episode 1" (no time left shown), unchanged from before playing.
- Play again: resume dialog "Continue from 10:27?".
- Result: episode resume dialog PASS; Details progress line FAIL, same as the movie. The movie fails the same way, so this is not an episode-only failure.

## Step 3: from Home (AC-7)

Steps: logcat -c; on Home long-press DPAD_CENTER on a card in the "Watching" row (a series), context menu entry "Watch series" (Player over Home, no Details below). The resume dialog first offered 7:46. Continue, 16 DPAD_RIGHT seeks, 45 s, BACK to Home.
- failure-line count: 0; marktime HTTP 200: 3; other: 0
- Opened its Details from Home: progress line "Next - season 1, episode 8" (no time left shown).
- Play: resume dialog "Continue from 11:49?" (was 7:46 before this session).
- Result: resume dialog PASS (position moved 7:46 to 11:49); Details progress line FAIL.

## Totals

Failure lines across the whole run (grep -cF over the final logcat, taken before the last step ended): 0.
Details progress screenshot: not white here; Details rendered normally in screencap on this run, and the progress line was also read with uiautomator. The player screen was white (video surface).

## Finding for a new run (not fixed here)

The resume dialog reads the new position (PlayerInteractor.kt:101/125: videos[].watching.time) while the Details resume line (DetailsScreenUIMapper.kt buildMovieResumeLine / buildSeriesResumeLine) stays "Not started" / "Next - ...". The movie line uses item.watching first (item.watching ?: videos.first().watching) and needs time > 0 and duration > 0; the series line reads the first unwatched episode's watching. Which field is empty on the box payload was not observed (no response bodies were captured). Hypothesis only.


## Round 2 (HEAD c185d417: Details resume line and episode bar read the media length)

Build: HEAD c185d417, clean tree, dev debug APK 1.12.5-dev, sha256 32e29a8354b2dc966492419e94eab05445c9d141d51f448117fc4ba0e07e343b. No shim. On-device base.apk sha256 equals the local APK after install and again after the run; the box is left on it.
Device: owner's TV box 192.168.1.110:5555, package com.kino.puber.stage only. Lease held and released. auth-save taken before the install. Install: install-preserve, outcome installed, classification compatible_replace, no destructive action.
Method as in round 1: D-pad only; log evidence is counts of filtered lines (marktime responses "<-- 200", "Playback progress save failed"), no raw logcat; Details and dialog text read with uiautomator filtered to the progress line and the resume dialog. The UI language of the box was Russian this time; strings are given in English with the Russian original in quotes.
Seek: one DPAD_RIGHT press moved about 20-40 s, depending on the title.

### Step 1: movie (AC-5, AC-6)

Steps: logcat -c; Movies list, a movie not opened before. Details before: "Not started - 2 h 3 min" ("Не начат · 2 ч 3 мин"). Play, 30 DPAD_RIGHT seeks, 45 s wait, BACK.
- "Playback progress save failed" lines: 0. marktime responses: 4, all HTTP 200.
- Details right after BACK (screenshot and uiautomator): "Stopped - 1 h 54 min left" ("Остановились · осталось 1 ч 54 мин"). Focus stayed on Play. This is the line that read "Not started" in round 1.
- Play again: resume dialog "Continue from 8:58?" with Restart / Continue. 8:58 played of 2 h 3 min equals 1 h 54 min left (consistent).
- Result: AC-5 PASS (0 failures, Details shows the progress), AC-6 PASS.

### Step 2: series episode (AC-5, AC-6, Q3)

Steps: logcat -c; Series list, a series already holding one earlier session. Details before: "Stopped at season 1, episode 1 - 20 min left" ("Остановились на 1 сезоне, 1 серии · осталось 20 мин"), which is the round-1 position of 10:27 now rendered (round 1 read "Next - season 1, episode 1" for it). Play: dialog "Continue from 10:27?". The focus handling of the dialog was not checked and the session started from the beginning (the position afterwards was 4:38, so Restart was taken, not Continue). 16 DPAD_RIGHT seeks, 45 s, BACK.
- failure lines: 0. marktime responses: 3, all HTTP 200.
- Details after BACK: "Stopped at season 1, episode 1 - 26 min left" ("осталось 26 мин"). It changed from 20 min to 26 min as the saved position moved from 10:27 to 4:38.
- Play again: dialog "Continue from 4:38?" (matches the new 26 min left).
- Result: AC-5 PASS for an episode (the line carries the time left, which round 1 lacked), AC-6 PASS. The change was a restart, not a forward move; both directions show the Details line follows the saved position.

### Step 3: from Home (AC-7)

Steps: app restarted to land on Home; logcat -c; long-press DPAD_CENTER on the "Odd Taxi" card in the "Watching" row; menu "Watch series" (the destructive "Remove from Watching" entry was not touched). The Player opened over Home. Dialog "Continue from 4:36?" (season 1, episode 2). Chosen: the default button, then 16 DPAD_RIGHT seeks, 45 s, BACK to Home.
- failure lines: 0. marktime responses: 3, all HTTP 200.
- Opened its Details from Home: "Stopped at season 1, episode 2 - 11 min left" ("Остановились на 1 сезоне, 2 серии · осталось 11 мин").
- Play: dialog "Continue from 12:48?" (4:36 before this session); marktime responses after this step: 4, all 200; failure lines 0.
- Result: AC-7 PASS.

### Not checked

The episode-card progress bar on the series Details was not looked at: the Details screen comes out as a white frame in screencap on this box for series and the uiautomator dump carries text, not bar fill. The bar is covered by DetailsScreenUIMapperTest "an episode card fills its bar from the episode length when the position carries none" (green, 25 tests in the class, 0 failures).

### Totals

Failure lines across steps 1-3: 0. Non-200 marktime responses: 0. Crash: none. Focus after BACK: kept (step 1 checked on screenshot).
