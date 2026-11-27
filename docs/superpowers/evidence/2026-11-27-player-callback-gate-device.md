# player-callback-gate device evidence

Date: 2026-11-27. Round 1 (qa-device).

## Builds

| Build | sha256 | Use |
|---|---|---|
| worktree dev APK (head ed00b902) | f9be44590318d802ca479727a15d5a5011af7f3870dc685918487969d10c100a | AC-6 on the box |
| master dev APK (main checkout) | 64b7d48fe5b1b7213648d3f593b4cd9f833e85bc44b4c8d2dc4a1ca688e76c8c | session refresh before, restored after |
| worktree instrumentation APKs | built by `make FLAVOR=instrumentation ... itest` | AC-1, AC-4 on the emulator |

On-device sha256 of `base.apk` for `com.kino.puber.stage` matched the build after each of the three install-preserve runs (outcome `installed`, `compatible_replace`, no uninstall, same signer 6b29181257cb...).

## AC-1 (emulator host.docker.internal:5555, sdk_google_atv64_arm64, API 36)

Command: `AGENT_GRADLE_SERIAL=1 make FLAVOR=instrumentation DEVICE=host.docker.internal:5555 GRADLE=./tools/agentw itest TESTS=com.kino.puber.ui.feature.player.vm.PlaybackControllerStaleCallbackTest`, three consecutive runs, `@Ignore` removed, test body unchanged (diff shows only the import, the annotation and KDoc).

| Run | Result XML | Cases |
|---|---|---|
| 1 | tests=3 failures=0 errors=0 skipped=0 (11:41:00) | delayedFailure_reportsStreamFailure_whenStreamIsNotSwitched, switchStreamBeforeDelayedFailure_reportsNoFailureForPreviousStream, switchStreamAfterQueuedFailure_reportsNoFailureForPreviousStream |
| 2 | tests=3 failures=0 errors=0 skipped=0 (11:41:10) | same three |
| 3 | tests=3 failures=0 errors=0 skipped=0 (11:41:20) | same three |

Result: PASS. The implementation run also recorded a red run on the un-ignored test (itest-red.xml: 3 tests, 1 failure) before the fix.

## AC-4 guard classes (same emulator)

- PlaybackControllerResourcesTest: tests=3 failures=0 (XML read).
- PlaybackControllerNetworkTracksTest, PlaybackControllerDeviceTest: make exit 0 each; result XML not kept (overwritten per run). The implementation run recorded NetworkTracks 9 and Device 2 passing in itest-guards.xml (14 cases, 0 failures).

## AC-6 (TV box 192.168.1.110:5555, com.kino.puber.stage only)

Steps and observations:

1. Built master dev APK in the main checkout, install-preserve, launch: home screen with rails, logged in. `make DEVICE=192.168.1.110:5555 auth-save` saved the app data.
2. Built the worktree dev APK, install-preserve, on-device sha256 matched, force-stop and launch: home screen, no pairing screen.
3. Opened a movie from the Novinki rail, Watch. Playback started; overlay showed 0:19 at 1080p (stream 1920x800, quality Auto).
4. Seek +10 s once to 0:49, then to 0:56 at the gear; opened Settings, Quality, picked 720p (11:37:5x). The quality list showed the check on 720p, Settings showed Quality 720p, stream 1280x534, video frames kept changing. Overlay at 11:38:26 showed 1:46 (0:56 at 11:37:34 plus 52 s of wall time is 1:48): resumed within 2 s of the running position, playing, no error screen.
5. Second switch: Settings, Quality, picked 1080p. Overlay at about 11:39:00 showed 2:18 (1:46 at 11:38:26 plus 34 s is 2:20), Settings showed Quality 1080p and stream 1920x800, playing, no error screen, no restart from 0, no crash, no pairing screen.
6. Backed out to home. Reinstalled the master dev APK with install-preserve, on-device sha256 matched, launched: home screen, logged in.

Observation (not an AC): after the first switch the Audio row label read "Track 1" instead of "01. Dubbing (RUS)" and stayed so after the second switch. Not compared with a master build; see the report.

Result: PASS.

## Leases and devices

Box lease taken and released (token matched). Emulator lease taken and released, emulator stopped with `ssh mac-emu stop` after no other lease remained. `com.kino.puber` was never touched.
