# Verify device evidence: cache-targeted-invalidation (round 2)

Date 2026-11-22. Head a5f79b4f (includes 2eb4cab3, the NonCancellable background flush), base 40762767. Device emulator-5554 (AVD puber_tv_36, API 36 TV); lease taken and released through tools/emulator-resource-lock.sh. Installs with tools/android-apk-install-preserve: outcome installed, compatible_replace, destructive_action false, same signer. No uninstall, data clear or downgrade.

## Builds

| Build | What | APK sha256 (on-device base.apk equal to local) |
|---|---|---|
| head-shim | head + PUBERBASE marker lines (uncommitted, reverted) | 4301b0da46e1792f5652a1433dcca502a0361bbe359f3207626e86ff62b2da74 |
| final | clean head (no shim), left installed | ad4607c524af27ff123b2457737c5bc2b6b4e1eb99340eaf8c26d992f1d57533 |

Shim: marker lines in ContentCacheRepository.markItemDetailsStale, TvHomeSyncCoordinator.refreshNow, ItemDetailsRepository.fetchItem, PlayerInteractor.saveWatchingTime (saveOk, plus the round 1 ignore of ApiError.Serialization so a save counts), PlayerVM save catch, exitPlayer entry, before router.back, and pauseForBackground entry. After the run `git checkout -- app/src`; `rg PUBERBASE app/src` prints nothing. Marker lines only below, no raw logcat. Times are device wall clock.

Title Odd Taxi (id 76651), episodes 2 and 3, driven by D-pad key events and `am start -a VIEW -d puber://content/items/76651/seasons/1/episodes/N?action=play` for the quick reopen.

## AC-2: in front, saves do not mark or refresh

Details to Play, episode 2 from 0:01, 75 s in front (the stream stalls at 1:30 as in round 1, Playback error shown after). Markers from 12:14:35 to 12:15:38: saveOk at 12:15:08.8, saveOk at 12:15:38.4; no markItemDetailsStale, no refreshNow (the refreshNow at 12:14:16 is app start, before playback). 2 saves, 0 marks, 0 refreshes. PASS.

## AC-3: exit

BACK from the player (Playback error screen, owed saves pending):
```
12:16:07.616 exitStart
12:16:07.747 saveOk
12:16:07.748 markItemDetailsStale id=76651
12:16:07.759 routerBack
12:16:07.767 detailsNetworkLoad id=76651
12:16:09.759 refreshNow
```
One mark, mark before routerBack, one refreshNow 2.0 s after (debounce). Details screen shown again after. PASS.

## AC-4: HOME background

Episode 2 playing 50 s, HOME:
```
12:17:12.803 pauseForBackground
12:17:13.240 saveOk
12:17:13.241 markItemDetailsStale id=76651
12:17:15.249 refreshNow
```
Save, then mark in the same millisecond, one refresh 2.0 s later. PASS.

## Triage #1: HOME, then reopen the title quickly (deep link replaces the stack)

The script (run on the device) presses HOME, polls the marker log and fires the VIEW deep link as soon as pauseForBackground appears, about 50-100 ms later, so the final save is still in flight. Each controlled run first waited for a saveOk in front so a mark was owed.

| Run | Link | Markers after pauseForBackground | Result |
|---|---|---|---|
| 1 | episode 2 | saveOk 12:23:17.819, markItemDetailsStale same ms, refreshNow +2.0 s | mark landed |
| 2 | episode 3 | markItemDetailsStale 12:23:50.205 (no saveOk: final save cancelled), refreshNow +2.0 s | mark landed |
| 3 | episode 2 | markItemDetailsStale 12:24:09.328 (no saveOk), refreshNow +2.3 s | mark landed |
| 4 | episode 3 | detailsNetworkLoad by the new screen 12:24:42.022, then markItemDetailsStale 12:24:42.147 (no saveOk), refreshNow +2.0 s | mark landed |

In runs 2 to 4 the new player screen replaced the old one (resume dialog of the other episode shown, new details load) and the final save never reported, yet the owed mark and refresh were issued: the NonCancellable flush works. In run 4 the new screen's details load preceded the mark by 125 ms, so the mark does not always precede the next screen's read; the following revalidation is what the exit path also relies on, and AC-4's wording (mark issued in the background transition) holds.

Not conclusive and not counted: one earlier attempt (12:22:42, episode 3) logged pauseForBackground and no mark or save; a save was not confirmed in front before it, so the mark may not have been owed (the player had been backgrounded 50 s earlier and may have been paused). Attempts with delays 0, 0.4 s and 1.0 s after HOME never reached onStop (the task was brought back before the stop; no pauseForBackground), so they exercised nothing.

## Cleanup

Shim reverted, clean head built and installed (sha above), lease released.
