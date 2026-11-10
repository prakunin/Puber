# Device evidence: seek-blocked-by-skip-prompt

Round 1: worktree HEAD `35f9866e` (production up to `d053f7db`), base `6b087697`. Round 2 (below, after the Manual checks table): HEAD `37d0527c`.

## Build and device

| Item | Value |
|---|---|
| Device | `emulator-5554`, AVD `puber_tv_36` (API 36 TV), 1920x1080 screen, 1 dp = 2 px; lease taken and released. Every screenshot in the image folder is downscaled to 1280 px wide (1 dp = 1.33 px) or is a contact sheet of such frames (1280x360, 1280x480, 1920x360); measure dp from the 1280 scale |
| APK | `app/build/outputs/apk/dev/debug/app-dev-debug.apk`, sha256 `36a564e1a06304ed2b7a72e0239f323583e4a3f79374a038f90151c5e8636db6` |
| Install | `tools/android-apk-install-preserve`: `outcome: installed`, `compatible_replace`, signer unchanged, `destructive_action: false` |
| On-device check | `base.apk` sha256 on the device equals the built APK (before and after the session) |
| Host load | load average 13-17 during the manual runs; screenshots took 3-6 s, so all timing-sensitive steps used a poll loop, not fixed sleeps |

## Instrumented tests

`AGENT_GRADLE_SERIAL=1 make DEVICE=emulator-5554 itest GRADLE=./tools/agentw TESTS=com.kino.puber.ui.feature.player.component.PlayerPromptFocusAndKeysTest` -> exit 0, `Finished 12 tests`, 0 failed, 0 skipped. `skipPrompt_okUpWithoutDownDoesNotSkip` is among them and passes.

## Skip-segment data source for M3

The player takes segments from `SkipSegmentService`: TheIntroDB (`api.theintrodb.org`, needs a TMDB id resolved with the TMDB token) and, for types TheIntroDB lacks, IntroDB.app (`api.introdb.app/segments?imdb_id=...`). On this worktree build the TMDB token is empty, so TheIntroDB is out. IntroDB.app answers for `tt0903747` S5E1-E3 (checked from the host: intro 261-278 s, outro 2537-2557 s for E1), but the app asked for `tt` + the item's `imdb` field; the host query with the unpadded id `tt903747` returns `intro: null, outro: null`. On the device no skip-intro prompt appeared when the playhead sat inside the E1 intro (267 s, 11 s left, playing), and the next-episode prompt came from the tail fallback (duration - 22 s), not from the outro segment. So no skip-segment prompt could be produced on the device: M3 is NOT CHECKED on the device (see report). Whether the unpadded id is the cause was not verified (the item's `imdb` value was not read).

## Method note

The Breaking Bad series (already fully watched on the paired account) was played from season 5. Seeks were reached with one `input keyevent 22 22 ...` call (47-49 Rights, 130 s then 60 s steps), then single presses. The next-episode prompt (the only prompt reachable) appears at duration - 22 s. Playback was left to advance across episodes; nothing else on the account was changed by the runs beyond the watch progress that playback itself records.

## Manual checks

| # | Steps run | Expected | Observed | Result |
|---|---|---|---|---|
| M1 | Burst of Rights (under 1.5 s apart) to 46:52 of 47:16 with controls hidden; waited for the prompt; Left with the prompt up; waited for it to return; Right | No prompt during the burst; prompt about 1.5 s after the last press; the seeks work; focus stays on the action | No prompt while the burst ran. Prompt up (`m1-next-episode-prompt-focused-controls-hidden.png`). Left with the prompt up gave the `-10s` seek indicator and the prompt came down (`m1-left-on-prompt-seeks-prompt-comes-down.png`). The prompt came back, Right gave `+10s` (`m1-prompt-returns-then-right-seeks.png`). The prompt appeared 5-20 s after landing, not 1.5 s: after a far seek the player buffers on this host and the tick only runs while playing | PASS for the key path; the 1.5 s timing is NOT CHECKED (buffering) |
| M2 countdown | Let the countdown run out; first Right in the new episode | Right seeks | `+10s 0:56` (`m2-countdown-out-new-episode-right-seeks.png`) | PASS |
| M2 Back | Back with the prompt at "Next episode 1"; Left | Prompt hides, player stays, next Left seeks | Prompt gone, player still on the episode, `-10s 46:49` (`m2-back-hides-prompt-then-left-seeks.png`) | PASS |
| M2 Left | See M1: Left with the prompt up, then Right | The first press after the hide seeks | `-10s`, then `+10s` | PASS |
| M2 OK | Prompt not caught in time (the episode ended while screenshots were taken) | - | no observation | NOT CHECKED |
| M3 | - | - | no skip-segment prompt can be produced on this build (see above) | NOT CHECKED |
| M4 | Up, burst of Rights, Up taps to keep the controls up; prompt appeared under the controls; controls auto-hid | Seek bar keeps focus while the prompt shows idle; after auto-hide the action is focused | Round 1 shots: chip idle under the controls (`m4-prompt-under-controls-chip-idle.png`, chip 175x52 px at 1280 = 131x39 dp) and chip scaled after the controls hid (`m4-controls-auto-hidden-chip-focused.png`, 193x57 px at 1280 = 145x43 dp, ratio 1.10). The first shot was taken after Up taps and shows the cancel X focused (review finding, fixed in `d95aa91d`); it does not show "the seek bar keeps focus". Superseded by the round 2 captures below | Round 2 supersedes |
| M5 | `input keyevent --longpress 22` with the prompt up | Seeking continues; no prompt action fires | One `+10s` seek, no skip, no cancel, the prompt returned (`m5-longpress-right-on-prompt.png`). adb sends one long-press down, not a held auto-repeat for seconds | NOT CHECKED for a multi-second hold; instrumented `heldRight` covers repeats |

## Observation outside the acceptance criteria

With the next-episode prompt up and the controls hidden, Up does not open the controls (the prompt action holds focus and Up passes through the prompt). This is the Up/Down pass-through the spec lists as out of scope (architect Q1, follow-up idea).

## Round 2 (HEAD `37d0527c`)

Build: `app-dev-debug.apk` sha256 `4d5196edc4515d8cfc11f74c1f63ae8519c68563eb4f84457c03d5c7a3390370`, installed with `tools/android-apk-install-preserve` (`outcome: installed`, `compatible_replace`, signer unchanged, `destructive_action: false`); on-device `base.apk` sha256 equal to it after the install and after the instrumented run and the manual session. Instrumented `PlayerPromptFocusAndKeysTest`: 12 tests, 0 failed, 0 skipped. Method: Outlander season 1 episodes, played from the continue-watching row; seeks reached with bursts of `input keyevent 22`; a script polled raw screencaps (about 0.4 s each) and pressed the next key right after the prompt was detected. Playback progress of those episodes changed as a side effect of playing them.

| # | Steps | Expected | Observed | Result |
|---|---|---|---|---|
| M4a | Down, Up (seek bar focused), bursts of Right to 20 s before the end, no key; prompt came 4.2 s after the last key | Prompt shows over open controls, seek bar keeps focus, chip and X idle | `r2-m4-prompt-under-controls-no-key-pressed.png`: controls fully visible, "Next episode" chip idle (174 px wide at 1280 = 130 dp), X idle, no button highlighted, time label purple (bar focused) | PASS |
| M4b | Same, then one Up | X is not focused (triage #1) | `r2-m4-after-one-up-chip-focused-x-idle.png`: chip scaled to 191 px (ratio 1.10, focused), X unchanged, idle. Up from the bar goes to the action only | PASS |
| M4c | Then Right | Seeks | `r2-m4-right-after-up-seeks-prompt-down.png`: 54:53 to 55:05, prompt gone | PASS |
| M4d (AC-6) | Second run: same as M4a, then Right without Up | Seek bar still focused, Right seeks | `r2-m4-run2-prompt-under-controls-no-key-pressed.png` (59:05) then `r2-m4-run2-right-on-seek-bar-seeks-prompt-down.png` (59:17, prompt gone, controls still open, no button highlighted) | PASS |
| S1 | Skip recap prompt up at 0:03 of an episode; Right, then Left | Both seek, prompt hides, returns after the burst, focus stays on the action | `r2-skip-recap-left-right-prompt-returns.png`: `+10s 0:15`, `-10s 0:07`, prompt back about 3 s later (buffering) | PASS (AC-1, AC-3) |
| S2 | Skip intro prompt up; OK | Skips the intro | `r2-skip-intro-ok-skips.png`: playhead 2:19 right after OK, prompt gone | PASS (AC-5) |
| S3 | Skip recap prompt up; Back, then Right | Prompt dismissed, player stays, Right seeks | `r2-skip-recap-back-dismisses-right-seeks.png`: prompt gone, still playing, `+10s 0:15` | PASS (AC-5, AC-4) |
| N1 | Next-episode prompt with controls open; Right | Seeks, prompt hides | M4c and M4d above | PASS (AC-2) |

Skip segments did arrive in round 2 (recap and intro prompts on Outlander season 1), so the skip prompt was reachable on a worktree build; round 1 used a series without segment data. Not observed: Left or Right held for seconds; the 1.5 s burst timing under no buffering (buffering after far seeks dominated).
