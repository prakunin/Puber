# player-overlay-autohide: device evidence

Date: 2026-10-10
Build: worktree `player-overlay-autohide` at f00f1712, `app-dev-debug.apk` (dev debug, 1.9.69-dev), installed with `tools/android-apk-install-preserve` (outcome `installed`, `compatible_replace`, no data touched).
Device: emulator-5554 (AVD puber_tv_36, API 36), paired, lease taken and released. Every adb call used `-s emulator-5554`.
Title used: a series with 4 episodes in season 2 (played from the details screen, resume dialog answered "Continue").

Method: key events through `adb shell input keyevent`; the screen was recorded with `screenrecord` and read in 0.5 to 1 s frames. The bar was timed by detecting the title text. Panel times are read from frames, resolution about 0.5 s. No screenshots, recordings or account data are stored here.

## Static gate

- `:app:compileDevDebugKotlin`: BUILD SUCCESSFUL.
- `testDevDebugUnitTest :app:detektAll :app:lintDevDebug`: BUILD SUCCESSFUL (tasks UP-TO-DATE from the implementer's run, same sources). Unit results on disk: 1407 tests, 0 failures, 0 errors, 0 skipped. The detekt "compiler errors found during analysis" line could not be seen (task was up to date); not compared with master.

## Test design traceability (unit rows)

Present and passing in `ControlsStateMachineTest`, `PlayerVMOverlayAutoHideTest`, `PlayerVideoSurfaceTest`: all P0 rows by name. Differences from the table:
- `episodesTimeoutDuringExit_doesNotStartANextEpisodeCountdown` is named `controlsTimerDuringExit_changesNothing`.
- `pausing_setsIsPlayingFalse_andResuming_setsItTrue` is named `pausing_setsIsPlayingFalse_andResumingSetsItTrue`.
- `lastEpisode_episodesTimeout_exitsThePlayer` is replaced by `lastEpisode_episodesTimeoutAfterEnd_landsOnEndedControlsAndStays` (as the design says).
- Not present: `debugOverlay_followsControlsWhenEnabled` (P2), `afterInitialize_episodesPanelHeldIsReset` (P2, after the other merge), `downFromButtons_...` (declared skipped).

## Device checks

Times are seconds after the key press.

### D1 / AC-14 / AC-3 (Down opens Episodes, closes itself)
Steps: on video press Down (bar), Down again, no input. Expected: row, then clean video after 6 s. Observed: bar shown, second Down showed the Episodes row, the row was gone about 6 s after it opened (visible in frames up to 5.5 s, gone at 6.0 s), clean video, no bar, no flash back. PASS.

### AC-1 (bar 6 s)
Observed in five separate runs: the bar was up 6.0 s (6.0 to 6.2 s) after the opening key and hid. PASS.

### AC-2 (key restarts the 6 s)
Down at 1.1 s (bar on), Left on the bar at 5.1 s, bar hid at 11.25 s, which is 6.2 s after the last key. PASS.

### AC-6 (Episodes button path)
Down (bar), Left x3 to the Episodes button (focus ring checked), OK at 5.07 s: row opened and was gone at about 11.0 s (6 s). PASS.

### D2 / AC-9 (focus after the auto-close; Back)
- Row auto-closed, Left pressed 2 s later: a "-10s" seek indicator appeared and the position moved back; Down brought the bar back, which hid after 6.0 s. PASS.
- Row auto-closed, Back pressed 4.5 s after the close: the player left to the details screen on the first press, no bar flash first. PASS.

### D3 / AC-4 / AC-5 (key restart, hold, context menu)
- Left/Right every 4 s for 20 s on the open row: row stayed throughout, closed 6 s after the last press. PASS.
- Rapid alternating Left/Right (about 6 keys/s) for about 14 s: row stayed. PASS (the keys were separate presses at about 6 per second, not a true auto-repeat).
- Long-press OK on a card: context menu opened. 21 s later row and menu were both still up. Back closed only the menu; the row stayed and closed about 6 s later. PASS.

### D4 / AC-9 stale key
Up pressed 0.4 s before the 6 s timeout: the panel closed, the bar appeared (6.0 s, then hid); never both on screen. PASS.

### D1 / AC-7 / AC-8 (Settings, stream info, About)
- Settings > Stream (stream info): still up after 36 s with no key, readings updating, across the buffering readings. Back returned to the Settings list (still up); next Back closed Settings, the bar appeared and hid after 6.0 s. PASS.
- About (info button): still up after more than 35 s; Back closed it, the bar appeared and hid after 6.0 s (48.25 to 54.25 s). PASS.

### AC-10 (resume dialog)
Dialog stayed up for about 40 s with no key; after "Continue" the video played with no bar. Timer start after the answer was not separately observable. PASS for "stays until answered"; the timer-after-answer part is covered by the unit test `resumeDialog_noHideTimerRunsWhileItIsUp` only.

### AC-12 (paused)
Down, OK on the Pause button: playback paused, bar hid 6.1 s later. PASS.

### AC-15 on the device
`dumpsys window` `mHoldScreenWindow`: null while paused; the app window while playing. PASS.

### AC-16 / D5 (system screensaver): PASS (follow-up round, user authorized `stay_on_while_plugged_in 0`)
Original values, read before any change: `global stay_on_while_plugged_in=1`, `secure screensaver_enabled=1`, `screensaver_components=null`, `screensaver_activate_on_sleep=0`, `screensaver_default_component=com.google.android.backdrop/.Backdrop`, `sleep_timeout=86400000`, `system screen_off_timeout=2147483647`. Changed for the test: `stay_on_while_plugged_in=0`, `screensaver_components=com.android.dreams.basic/com.android.dreams.basic.Colors`, `screen_off_timeout=60000`, later `screensaver_activate_on_sleep=1`. Same APK as the first round (no code change since).
- Playing, 80 s idle at the 60 s timeout: `mCurrentDream=null`, `mWakefulness=Awake`, `mHoldScreenWindow` = the app window. The saver did not start during playback.
- Paused (pause button), `mHoldScreenWindow=null`: with `activate_on_sleep=0` the screen went `Asleep` at about 60 s with no dream. After `KEYCODE_WAKEUP` the same activity was on top, the same frame was shown 3 s apart (still paused), and the app was not restarted.
- Paused, `activate_on_sleep=1`: at about 50 s `dumpsys dreams` showed `Colors` as the current dream and `mWakefulness=Dreaming`. One D-pad key (OK) later: `Awake`, `mCurrentDream=null`, same activity on top, the same frame in two screenshots 3 s apart (playback still paused, video frame visible), no crash.
- Note: with the screen asleep (not dreaming) an injected D-pad key did not wake the device; only `KEYCODE_WAKEUP` did. That is the Android power behaviour for a sleeping display, not the app.
- Restored and read back: `stay_on_while_plugged_in=1`, `screensaver_activate_on_sleep=0`, `screensaver_components=null` (deleted, as originally unset), `screen_off_timeout=2147483647`; `screensaver_enabled=1` and `sleep_timeout=86400000` were never touched.

### AC-11 and the D13 case (Episodes open across the end): NOT CHECKED
Attempted on the last episode of the series (season 2, episode 4) by seeking to the end with repeated Right presses (the seek step grows with repeats and overshot every time; three attempts). On every end the player left to the details screen by itself, with no controls and no panel. That matches existing code: `onPlaybackEnded` calls `exitPlayer()` for a film or the last episode when no panel is open, and sets `exitWhenPanelCloses` when a panel is open (PlayerVM.kt `onPlaybackEnded`). The ended controls only appear for a non-last episode with the next-episode countdown dismissed; reaching that needs a non-last episode to play to its end, which would also autoplay or mark more history, so it was not attempted. AC-11 and the D13 landing stay covered by unit tests only (`onHideTimeout_afterPlaybackEnded_keepsControlsUp`, `endedControls_stayUpAfterBufferingEndAndAfterAKeyAndTime`, `lastEpisode_episodesTimeoutAfterEnd_landsOnEndedControlsAndStays`).

### Not checked on the device
AC-11 (above) and AC-13: unit tests only (they pass).

## Observation (not an AC)
After Up closes the Episodes row, the bar comes back with no visible focus ring and OK on it does not open the Episodes panel (it just restarts the 6 s). With focus placed by Down then Left the ring is visible. Traced by the conductor to code that predates this branch, so not a defect of this change; deferred to board row `player-focus-after-panel-close`.

## Side effects
Playback progress of the title changed on the account: about 10 minutes of season 2 episode 3 played in the first round (one accidental Previous restarted it); in the follow-up round season 2 episode 4 (the last) was seeked to its end three times, so it is now marked watched (the season counter went from 2 to 3 watched) and its saved position was overwritten. No settings changed that remain.
