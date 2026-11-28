# Device evidence: player-dpad-seek-coalesce

Date: 2026-11-28
Branch head under test: 9e7585bc (base 8c3117ea)
Build: `:app:assembleDevDebug`, `com.kino.puber.stage` 1.12.8-dev, APK sha256 f73a1e44d8a7586b4b7a09da7c77cafc635cbabeee316dbaaa6a47b935cf413a
Install: `tools/android-apk-install-preserve` outcome `installed` (compatible_replace, destructive_action false); on-device base.apk sha256 equals the built APK.
Device: owner's TV box 192.168.1.110:5555, lease held and released. Session protection: installed stage app launched once, then `make DEVICE=192.168.1.110:5555 auth-save` before the install. The app was still logged in after the install.
Title: one HLS film (Quasimodo, 1h46), played from the resume offer at 32:50. No other title opened. Seek bursts are one `adb shell input keyevent` call with eight repeated codes (about 140 ms apart). Pause (127) and play (126) used to read a stable time. Screenshots held outside the repo (they show account data).

## AC-8

| Step | Expected | Observed | Result |
|------|----------|----------|--------|
| Seek bar focused, paused at 38:32, 8x Right | Target = +310 s, thumb follows | 43:48 (see note 1) | see note |
| Paused at 43:48, 1x Right, then 1x Left | +10 s, then -10 s | 43:58, then 43:48 | PASS |
| Paused at 43:48, 8x Right, bar focused | 48:58 (+310 s) | indicator "+60c 48:58" and thumb 48:58 at once | PASS |
| Then 8x Left, bar focused | back to 43:48 | thumb 43:48 | PASS |
| Playing, controls hidden, 8x Right | indicator advances, playback lands on the indicator target +-1 s, no failover | indicator 54:20; pause 6 s later read 54:20 (buffering label cleared, playback went on) | PASS |
| Playing, controls hidden, 8x Left | same, downwards | indicator and thumb 49:24, stable after pause | PASS |
| Playing, 8x media fast-forward (90) | same as Right | indicator 54:49; position after pause 54:49 | PASS |
| Right x8 then Back, Back (the last Back about 140 ms after the last Right), reopen title | resume offer shows the burst's final time | detail screen "46 min left" (burst final about 60:08), resume offer "from 1:00:14"; pre-burst time was about 54:58 | PASS |

Note 1: the 38:32 reading came from a thumb read while the controls had just been shown after auto-hide, and the tick had not refreshed it (the controls-hidden state does not update the thumb). The same burst repeated from a freshly displayed 43:48 gave exactly +310 s, so the 6 s difference is a stale start reading, not a seek error.

No thumb jump back, no playback behind the indicator, no stall failover (stream not changed, spinner cleared well inside 8 s after every burst), no prompt appeared, no crash.

## Not checked on device

- Manual 3 (series episode with an intro segment, burst ending inside the intro, prompt 1.5 s after the last press): the only title opened was a film without segments. Covered in unit tests: `PlayerVMSeekBurstTest` (seven cases, including `aHeldKeyKeepsThePromptDownAndTheTrailingSeekStartsNoCountdown`).
- Fine-grained seek count during a hold (one real seek per 250 ms window): not observable from the UI. Covered by `PlayerVMSeekCoalesceTest.aHeldKeySeeksOncePerWindowAndEndsOnTheFinalTarget` and `aLongHoldKeepsAtLeastOneWindowBetweenSeeks`.

## Round 2: Manual 3 (AC-7 on device)

Installed APK checked first: on-device base.apk sha256 equals the worktree build (f73a1e44d8a7586b4b7a09da7c77cafc635cbabeee316dbaaa6a47b935cf413a), so no reinstall. Lease held on 192.168.1.110:5555 and released. Package com.kino.puber.stage only.

Title: one series from Home, "Nebesnyi aidol / Seongseureoun aodol" (Home row "I watch", season 1 episode 1). Played from the start ("Snachala" in the resume offer). Opened one title only.

| Step | Expected | Observed | Result |
|------|----------|----------|--------|
| Play S1E1 from 0:00, watch 0:00 to about 1:00 | A skip prompt appears if the episode has an intro segment | Studio logo, title sequence, disclaimer card; no prompt (checked by screenshots at about 10 s, 25 s, 35 s, 45 s, and with controls shown at 0:52) | no segment found |
| Step through the episode in 4x Right bursts (70 s each, 3 s dwell, six bursts, roughly 1:00 to 8:00) looking for a prompt | A prompt shows inside any intro segment longer than the 70 s step | No prompt at any stop; one screenshot at the last stop shows plain video | no segment found |

Result: this episode carries no intro segment in the data the app received, so the burst-into-the-intro scenario could not be set up. Manual 3 stays NOT CHECKED. No other series was opened (the only series on Home was this one; searching further would mean browsing more titles). Side effect: the episode's saved position moved from 51:03 (9 min left) to about 8 min because "Snachala" was chosen and playback progress was reported.

## Round 3: end-of-playback with a pending seek target (commit 4da50fd1)

Build: `:app:assembleDevDebug` at 4da50fd1, APK sha256 a0af0bd0f2df1c5b23f99cf4813e69bb7b0eb8e41772965b4e9f2d2e546eac08.
Install: `tools/android-apk-install-preserve` outcome `installed` (compatible_replace, destructive_action false); on-device base.apk sha256 equals the built APK. App stayed logged in. Device: 192.168.1.110:5555, lease held and released. Package com.kino.puber.stage only. Same film as round 1 (Quasimodo, 1h46m13s, duration 1:46:13). Screenshots held outside the repo.
Static gate: check.full passed (unit tests, detekt, lint, profile tests, both tooling scripts).

| Case | Steps | Expected | Observed | Result |
|------|-------|----------|----------|--------|
| A. Burst near the end, no key after | Resumed at 1:00:14, Right bursts (8 presses) up to -6:03, then 6 presses and 3 presses landing the indicator at 1:44:16 (-1:58), then no key for about 2.5 min | Normal end: player exits to detail screen | Credits kept playing past the burst (Russian credits at about +60 s), then the player left to the detail screen showing "Watched" (Prosmotreno) | PASS |
| B. Overshoot burst at about 40-50 s before the end | Restarted from 0:00 (title was watched), Right bursts to 1:45:19 (-0:54), then 8 Right presses (+310 s, target clamps to duration) | Player ends normally, detail screen, marked watched, not stuck on the last frame | Detail screen reached within 2 s of the burst, state "Watched", unchanged after 60 s more | PASS |
| C. Single press | Controls visible, seek bar focused, paused at 0:27: 1x Right, then 1x Left | +10 s then -10 s | 0:37 then 0:27 | PASS |

Notes. Case B started at about 54 s, not 30 s, before the end: the ladder moves in steps of about 30-60 s per few presses and the picked position was the closest I could set without leaving a gap long enough to hide the clamp. The burst target (about +300 s) exceeds the remaining time in either case, so the clamp path is the one exercised. The first attempt at case C read 0:16 to 0:37 (+21 s) because the thumb was read before the pause settled (the 0:16 reading was stale: the next reading after the Left press was 0:27, and a clean Right/Left pair from 0:27 gave exactly +10/-10). Side effect: the title's watch state is "Watched" after case A and B; the last exit was at about 0:27 via Back, Back.
