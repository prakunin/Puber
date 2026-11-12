# Device evidence: next-episode-end-stall

Date: 2026-11-12. Round 1. Worktree HEAD `1239fd91`, base `4db9c0d4`.

## Build and device

- Build: `./tools/agentw :app:assembleDevDebug` from the worktree, `com.kino.puber.stage`, versionName 1.10.0-dev, versionCode 2090000000.
- APK sha256 `6c427e36e4a5c0324c32d4615bd8319cc3d7aa0a4a8f6f5c8ce5be1cd689d9b1`; the same hash was read back from the installed `base.apk` on the device.
- Install: `tools/android-apk-install-preserve install-preserve`, outcome `installed`, classification `compatible_replace`, `destructive_action` false, signer unchanged.
- Device: `emulator-5554` (AVD `puber_tv_36`), already signed in, under lease. Driven only with `adb -s emulator-5554 shell input keyevent`; frames were read from screenshots, nothing else was recorded.
- Series titles are left out on purpose. Three series were used: A (ending on episode 14 of 14 and episode 13), B (season 2, 7 episodes) and C (season 1 of series A, 16 episodes).

## What the prompt looks like on this build

The next-episode prompt appears about 20-30 s before the end, in the credits, as a pill with an X button to its left and a countdown of about 5 s. With the controls hidden, `DPAD_LEFT` / `DPAD_RIGHT` seek (and a seek resets the dismissal, AC-4). `BACK` on the prompt dismisses it and does not leave the player.

## Results

| Check | Steps | Expected | Observed | Result |
|---|---|---|---|---|
| M4 (AC-3) | Series A episode 13, seek to the last minute, touch nothing | Prompt counts down, next episode starts | Prompt counted down 5 to 0, then "Season 2, episode 14" loaded and played from 0:00 (seen twice, series A and series B episode 6 to 7). The finished episode showed as watched in the details list afterwards (13 of 14 watched). | PASS |
| M2 (AC-2, AC-8) | Series A episode 13 (position about -2:17), prompt up with the countdown at 3, `BACK` | Prompt goes away, player stays, next episode starts at the end, no ended controls, no spinner left | After `BACK` the prompt vanished and the picture continued; about 7-10 s later the title read "Season 2, episode 14", position 0:00, then 0:06, intro skip, playing at 1:17 with the seek bar moving. No ended controls, no second countdown, no exit. | PASS |
| M1 (AC-1, AC-8, cancel with the X button) | Prompt up, `DPAD_LEFT` / `DPAD_UP` to reach the X button, `DPAD_CENTER` | X cancels | Not achieved. With the controls hidden `DPAD_LEFT` seeked 10 s back (dismissal reset, prompt came back); `DPAD_UP` did nothing visible. Two attempts; both ended with the countdown running out. The cancel itself (`cancelCountdown()`) is the same function `BACK` reaches, so M2 covers the production path; the X button was not reachable with the D-pad in the 5 s window. | NOT CHECKED |
| M3 (AC-6) | Cancel with `BACK`, then open the Episodes row at about -0:10, let the episode end | Next episode starts when the row closes | Episodes row opened (focus went to the Episodes opener, then CENTER). The row closed itself after its 6 s timeout before the end (credits visible with no panel), then episode 16 loaded after the end with no manual step. The episode did not end while the row was open, so the "ended under an open panel" branch was not exercised on the device. The timeout-close then end sequence matches AC-1, not AC-6. | NOT CHECKED (AC-6 rests on unit tests) |

## Notes

- Playback created watch progress on the owner's account for the episodes above (marks and positions). No uninstall, data clear, downgrade or signer change took place.
- A "Playback error" screen with Retry appeared once when 70 `DPAD_RIGHT` presses were sent in one burst with no pause; Retry resumed playback. The seek batch size, not this change, caused it (seeking beyond the buffer on the emulator); it was not seen with batches of 30.
- The update-available dialog on first launch was dismissed with "Not now".

## Round 2

Worktree HEAD `a7027fa1` (adds `afb8b9b3`: the switch after a dismissed end runs after the player's ended callback returns). Same device and method as round 1.

### Build and device

- Build: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug :app:assembleDevDebug`, BUILD SUCCESSFUL.
- APK sha256 `4d103c2093dc6a0d5b0ab0cd70c59d5a539188f83ebc2ff48cebad0bc89c9b55`; the same hash was read back from the installed `base.apk`.
- Install: `tools/android-apk-install-preserve install-preserve`, outcome `installed`, classification `compatible_replace`, `destructive_action` false, signer unchanged (same signer as the candidate).
- Device: `emulator-5554` (AVD `puber_tv_36`), signed in, under lease, released afterwards. Driven with `adb -s emulator-5554 shell input keyevent`; frames were read from screenshots and from frame-by-frame pixel checks of screenshots, no account data kept.
- Series titles are left out. Series A: 16 + 14 episodes; series B: 7 episodes. Several episodes were resumed from a saved position a few seconds before the end, so the prompt came up at once.

### Results

| Check | Steps | Expected | Observed | Result |
|---|---|---|---|---|
| M2 (AC-1, AC-2, AC-8) | Series A season 2, episode 13, resume from 1:05:09 of 1:05:22 with Continue. About 6 s later, while the "Next episode" pill was counting down, `BACK`. Then touch nothing. | Prompt goes away, the player stays, the next episode loads and plays from 0:00 when the episode runs out; no ended controls, no spinner on the finished episode, no exit. | The frame right after `BACK` showed the squeezed credits with no pill and the player still open (`BACK` on a hidden-controls player with no prompt leaves the player, so it was consumed by the prompt). About 5 s later the title read "Season 2, episode 14", position 0:00 with the intro skip pill counting down, then 0:43 with the seek bar moving. No ended controls, no second countdown, no exit, same app process (pid unchanged). | PASS |
| M4 (AC-3), no cancel | Series B season 2, episode 6, resume 43:15 of 43:29, Continue, touch nothing. | Countdown runs out, next episode plays. | Pill "Next episode" counted 6 to 1 over the credits, then "Season 2, episode 7" loaded from 0:00 and played (seen in six frames; no ended controls). Also seen without any input on series A (episode 14 to 15, 15 to 16, season 2 episode 13 to 14). | PASS |
| M1 (AC-1, AC-8, cancel with the X button) | Not tried again. | - | With the controls hidden, `DPAD_LEFT` seeks instead of moving focus to the X, and the pill lives about 5 s; the X is not reachable with the D-pad inside that window. `cancelCountdown()` is the same function `BACK` reaches (M2 above). | NOT CHECKED |
| M3 (AC-6) | Not reached. Opening the Episodes row only in the last few seconds needs about 3 more D-pad presses inside the 5 s of the prompt plus the panel's 6 s auto-hide; every attempt to land the episode end under an open row missed the window. | - | - | NOT CHECKED (AC-6 rests on unit tests) |

### Log

`logcat` filtered for `FATAL`, `AndroidRuntime` crashes, `IllegalStateException` and `ANR` around the M2 switch (buffer cleared right before): no match. The app process id was the same before and after. Earlier in the session, around an unattended episode switch, one `W LegacyMessageQueue: IllegalStateException: Handler (Choreographer$FrameHandler) sending message to a Handler on a dead thread` warning appeared (framework log of a released codec thread, logged at W, no crash, playback continued); it did not recur in the M2 run.

### Notes

- Playback created watch progress on the owner's account for the episodes above. No uninstall, data clear, downgrade or signer change took place.
- The update-available dialog on launch sits in front of the home screen; keys sent while it was up reached the screen behind it and opened a film, which was left with `BACK`.
