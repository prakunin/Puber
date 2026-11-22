# Verify device evidence: cache-targeted-invalidation (round 3, owner's TV box)

Date 2026-11-22. Head ef2d1c20 (branch prakunin/cache-targeted-invalidation), base 40762767. Device: the owner's TV box 192.168.1.110:5555 only (owner's decision: all before/after values of this run are taken on the box; the emulator was not used). Lease taken and released through tools/emulator-resource-lock.sh. `make DEVICE=192.168.1.110:5555 auth-save` run before the first install. Installs with tools/android-apk-install-preserve (outcome installed, compatible_replace, destructive_action false, same signer 6b2918...). No uninstall, data clear or downgrade. Only com.kino.puber.stage touched. The box was already logged in; no login attempted.

## Builds (on-device base.apk sha256 equals the local APK after every install)

| Build | What | sha256 |
|---|---|---|
| base-shim-0 | 40762767 main sources + markers, NO Serialization ignore (step 0) | 29df7d378853559a4751da9916bcfd003474827b3409976c2156048e53c67830 |
| base-shim-1 | 40762767 + markers + Serialization ignore, saveOk without ep/t text (installed, not measured) | d6e89d1eafc32f5573ee1da6103091768b7d89b75b666c46315aaff74bd697f4 |
| base-shim | 40762767 + markers + Serialization ignore + saveOk ep/t text (measured; equality checked, hash not recorded) | n/a |
| head-shim | ef2d1c20 + the same markers + the same ignore (measured) | 17402e92d3381e7f7758096b231af3b229a9de97c49ec875d46b063db222f5b2 |
| final | clean HEAD ef2d1c20, no shim, left installed | ad4607c524af27ff123b2457737c5bc2b6b4e1eb99340eaf8c26d992f1d57533 |

Note: the measured base-shim APK was installed and its on-device sha256 was checked equal to the local build, but the hash was not written down. The base shim was applied on top of PlayerInteractor, PlayerVM and CachedFeed taken from 40762767 with git checkout and restored to HEAD afterwards.

Shim (uncommitted, reverted; `rg PUBERBASE app/src` prints nothing and `git status` is clean apart from the evidence): PUBERBASE log lines in ContentCacheRepository.markItemDetailsStale, TvHomeSyncCoordinator.refreshNow, ItemDetailsRepository.fetchItem (detailsNetworkLoad), PlayerInteractor.saveWatchingTime (saveOk with episode and position; plus the ApiError.Serialization ignore), PlayerVM drain catch (saveFail), pauseForBackground entry, exitPlayer entry (exitStart), just before router.back (routerBack), two Choreographer frame callbacks after router.back (frame1, frame2). Same lines on both builds. Marker logs only (*-verify-box-base-markers.log, *-verify-box-head-markers.log; times are the box clock).

## Step 0: do watching/marktime saves succeed on the box's account?

Base build with markers, WITHOUT the ignore (build base-shim-0), Odd Taxi episode 3 played about 2 min (after the resume dialog was confirmed with Select):

```
saveFail com.kino.puber.core.error.ApiError$Serialization: Malformed response   (30 s tick)
saveFail com.kino.puber.core.error.ApiError$Serialization: Malformed response   (30 s tick)
exitStart / saveFail (same Serialization) / routerBack / frame1 / frame2
```

No saveOk. So on the box too, marktime returns a body that fails to decode (same as on the emulator): every save is a "failed save" in the app, and with the unmodified base build the per-save mark/refresh never ran. The same ignore shim was therefore applied to both measured builds. This is the resume-progress-anomaly symptom (the server did store positions: the Home card badge and History bars advanced).

## AC-10 (perf): about 29-30 min in front, one next-episode switch, then exit

Title Odd Taxi (id 76651), series, same box, same debug build type, remote input only. Base: episode 4 resumed at 12:16 (t=736 s), window 13:05:39 to exitStart 13:34:51 = 29.2 min, switch ep 4 to 5 at about 13:16:03 (episode-end save t=1361). Head: episode 7 resumed at 5:38, a seek brought it to t=690 s, window 13:45:19 to exitStart 14:15:09 = 29.8 min, switch ep 7 to 8 at about 13:56:30 (t=1361). No stall, no error, no crash on either run.

| Counter in the window | Base 40762767 | Head ef2d1c20 |
|---|---|---|
| successful progress saves | 59 | 61 |
| markItemDetailsStale | 59 | 0 |
| TvHomeSyncCoordinator.refreshNow | 60 | 0 |
| details network loads | 2 | 1 |
| of which at the episode switch | 1 (13:15:54, 9 s before the episode-end save) | 0 |

Base: the first details load (13:14:48) is the prefetch of the next episode 75 s before the end; the second (13:15:54) is at the switch. Head: the one load (13:55:16) is the same prefetch (74 s before the end); none at the switch. Base refreshNow = 59 (one per save) plus 1 extra at 13:14:51 (3 s after the first details load, not 2 s after a save; cause not investigated). Head has 0 refreshNow in the whole window including the episode end (AC-5).

### Exit time, Back to the screen below (exitStart to routerBack, and to frame2 after router.back), ms

Screen below is Home on both builds (all sessions started with a deep link from Home). Exits 2-5 are about 40 s sessions started from the same deep link, Select once to confirm resume, a save at exit each time.

| Build | Exit | routerBack | frame2 |
|---|---|---|---|
| base | 1 (long) | 185 | 995 |
| base | 2 (ep 6 short, resumed 10:05) | 186 | 1451 |
| base | 3 | 189 | 1264 |
| base | 4 | 181 | 1189 |
| base | 5 | 188 | 1124 |
| base | 6 | 199 | 1147 |
| base | 7 (after background, player paused) | 192 | 1110 |
| head | 1 (long) | 226 | 1269 |
| head | 2 (ep 8 short, t 1169-1355 of 1361) | 193 | 1497 |
| head | 3 (ep 8) | 180 | 1403 |
| head | 4 (ep 8) | 214 | 1298 |
| head | 5 (ep 8) | 210 | 1272 |
| head | 6 (after background, ep 8, paused) | 183 | 1218 |
| head | 7 (ep 9 short, mid-episode) | 178 | 1153 |
| head | 8 (ep 9) | 174 | 1128 |
| head | 9 (ep 9) | 206 | 1155 |
| head | 10 (ep 9) | 187 | 1144 |
| head | 11 (ep 9) | 180 | 1094 |

| Set | n | routerBack median | frame2 median | frame2 min-max |
|---|---|---|---|---|
| base, all | 7 | 188 | 1147 | 995-1451 |
| head, first batch (exits 1-6, taken straight after the long run, episode 8 near its end) | 6 | 193 | 1285 | 1218-1497 |
| head, ep 9 batch (exits 7-11, mid-episode) | 5 | 180 | 1144 | 1094-1155 |
| head, all | 11 | 187 | 1218 | 1094-1497 |

Reading: routerBack (the part this run changes: the flush of the owed mark before router.back) is equal, 188 vs 187 ms. frame2 is 1147 (base) vs 1218 (head, all, +71 ms, inside the base spread 995-1451 and the +150 ms pass line = 1297) and 1285 for the first head batch (+138 ms, just inside the line) vs 1144 for the ep 9 batch. The first head batch ran near the end of episode 8 and the base batches mid-episode 6; this was not controlled further, so a position effect cannot be excluded and no claim is made beyond: head all-exit median is below base median + 150 ms, and the part that the change touches (routerBack) did not move.

## AC-4 / background (Home key during playback)

Base, ep 6 resumed, Home at 13:41:34.660: pauseForBackground, saveOk 13:41:34.826, markItemDetailsStale 13:41:34.827, refreshNow 13:41:36.878. (Base already marks on every save, so this is just its normal save plus a refresh.)
Head, ep 8 resumed, Home at 14:20:13.201: pauseForBackground, saveOk 14:20:13.371, markItemDetailsStale 14:20:13.372 (1 ms after the save), refreshNow 14:20:15.404 (+2.0 s). Reopening the app (am start): no marker (no second mark, no second refresh); the Back that followed was an ordinary exit (exitStart 14:20:26.294, saveOk, mark once, routerBack, frame2, refreshNow +2.0 s).

## AC-3: exit

Head, every one of the 11 exits: one saveOk, one markItemDetailsStale logged before routerBack, one refreshNow 1.5-2.1 s after exitStart, one details load by the screen below at the long exit.

## AC-6: Details shows the new progress after exit (head, clean build, no shim)

NOT CHECKED. (1) The Details screen of this box renders as a plain white frame in `adb screencap` (also for an unrelated bookmarked title, Outlander; Home, History and Search render normally), so the visible outcome of AC-6 cannot be captured as a screenshot here; a uiautomator dump of the screen is empty. (2) marktime does not decode on this account (step 0), same with the base build. What was seen on the clean HEAD build: the Home "I'm watching" card for Odd Taxi shows the episode badge 8 and an advanced progress bar after the exit, and the History screen shows the Odd Taxi episode bars (the shim runs had played episodes 3 to 9); both surfaces read from the data the exit invalidates.

## Android TV launcher "Continue watching"

Not observable: no row for the stage app appears on the box's launcher after exit or after Home, and the cause is in the app, not the change: on the clean HEAD build the first reconcile logged `java.io.IOException: Failed to add logo, so channel (ID=269) was not created` from `PreviewChannelHelper.publishChannel` via `AndroidTvHomePublisher.ensureChannel` (AndroidTvHomePublisher.kt:52), so the channel is never created and no program is published. `AndroidTvHomePublisher` and the rest of core/tvhome are unchanged in this run (git diff 40762767..HEAD is empty for them), so this predates the run. A content query of watch_next_program and preview_program as the shell user returned no rows. The launcher screens were looked at but not kept (they show the owner's other apps and recommendations).

## Account effects

Only normal playback (watching/marktime saves for Odd Taxi episodes 3 to 9 on the owner's account, positions advanced; episode ends were reached by playback at the switches 4 to 5 and 7 to 8). No watched toggles, bookmarks or other writes by hand.
