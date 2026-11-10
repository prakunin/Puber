# QA report: seek-blocked-by-skip-prompt

## Round 1

Worktree HEAD `35f9866e` before the evidence commit `b6660c74`; production up to `d053f7db`; base `6b087697`.

```
CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; tasks up to date, results XML read: PlayerVMSeekBurstTest 4/4, PlayerVideoSurfaceTest 5/5, PlayerVMPromptDismissalTest 6/6, SeekHandlerTest 5/5, 0 failures)
CHECK: AGENT_GRADLE_SERIAL=1 make DEVICE=emulator-5554 itest GRADLE=./tools/agentw TESTS=...PlayerPromptFocusAndKeysTest -> pass (12 tests, 0 failed, 0 skipped)
DEVICE: emulator-5554 (AVD puber_tv_36), lease taken and released; worktree APK sha256 36a564e1...db6 installed with install-preserve (outcome installed, data kept), on-device base.apk hash equal to the built APK before and after
```

| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | Instrumented `skipPrompt_leftAndRightSeekAndFocusStaysOnAction`, `skipPrompt_heldRightSeeksOnEveryRepeat` green (were red on base, `red-run-task3.md`). `SeekHandlerTest` green. Device: Left and Right with the next-episode prompt up seek (`-10s`, `+10s`), M1 in the evidence file. The skip prompt itself was not reachable on the device (M3) |
| AC-2 | PASS | Instrumented `nextEpisodePrompt_leftAndRightSeekAndFocusStaysOnAction` green; `PlayerVMPromptDismissalTest` 6/6; device: Left and Right on the next-episode prompt seek and take the prompt down (M1, M2) |
| AC-3 | PASS | `PlayerVMSeekBurstTest` 4/4 (intro, credits lead-in, window restart, skip landing). Device: no prompt while a Right burst ran through the credits lead-in (M1); the prompt came after the burst. The 1.5 s timing was not measurable on the device (buffering after far seeks, 5-20 s) |
| AC-4 | PASS | Instrumented `skipPromptHidden_nextRightSeeks` and `nextEpisodePromptHidden_nextRightSeeks` green (also green on base, kept as guards). Device: first press after a hide seeks after Left, Back and the countdown running out (M2). OK-then-new-episode not observed (NOT CHECKED, missed in time) |
| AC-5 | PASS | Instrumented `skipPrompt_appearsWithActionFocused_okSkips`, `skipPrompt_backCancels` green. Device: the next-episode action appears focused (chip at 1.1 scale); the skip prompt NOT CHECKED on the device (no segment data, see below) |
| AC-6 | PASS | Instrumented `promptAppearingUnderControls_seekBarKeepsFocusAndRightSeeks`, `controlsHidingWhilePromptIsUp_focusesThePromptAction` green. Device (M4): prompt under the controls with the chip idle (72 px high), after auto-hide the chip focused (80 px, ratio 1.11). A Right under the controls was not pressed in time (episode ended): NOT CHECKED on the device for that step |
| AC-7 | PASS | Instrumented `skipPrompt_heldLeftAndRightFireNeitherSkipNorCancel` and `skipPrompt_okUpWithoutDownDoesNotSkip` green. Device: a long press of Right on the prompt fired neither skip nor cancel (one `+10s`); a multi-second hold is NOT CHECKED on the device (adb sends one long press) |
| AC-8 | PASS | Full unit suite green (check.full); `PlayerVMPromptDismissalTest` 6/6, `PlayerVideoSurfaceTest` 5/5 including the two `seekActionForKeyCode_*` tests |
| AC-9 | NOT CHECKED | Owner sequence needs both an intro and the credits lead-in. The credits lead-in half ran on the device (M1: burst through it, prompt after it, Left and Right both seek). The intro half cannot run on a worktree build (M3). The owner judges the prompt flash during a burst: none was seen in the credits burst |

M1-M5 detail and images: `docs/superpowers/evidence/2026-11-10-seek-blocked-by-skip-prompt-device.md`.

| Manual | Status |
|---|---|
| M1 | PASS for keys and focus; 1.5 s timing NOT CHECKED (buffering) |
| M2 | PASS for Left, Back, countdown out; OK NOT CHECKED |
| M3 | NOT CHECKED |
| M4 | PASS for idle chip and focus after auto-hide; Right under controls NOT CHECKED on the device |
| M5 | NOT CHECKED for a multi-second hold; a single long press PASS |

## Skip-segment source for M3

Sources: TheIntroDB (`api.theintrodb.org`, via a TMDB id from the TMDB token) and IntroDB.app (`api.introdb.app/segments?imdb_id=`) in `data/repository/SkipSegmentService.kt`. A worktree build has no TMDB token, so only IntroDB.app is left. The host query with `tt0903747` S5E1 returns intro 261-278 s and outro 2537-2557 s, but the same query with the unpadded `tt903747` (what `IntroDbAppApiClient` builds when the item's `imdb` has no `tt` prefix) returns null segments. On the device no intro prompt appeared at 267 s of S5E1, and the next-episode prompt came at duration - 22 s (the tail fallback), not at the outro start. Not verified: the item's actual `imdb` value. That is a pre-existing lookup issue, outside this change. M3 on the device needs either a build with the TMDB token or a fix to that id; the skip prompt is covered by the instrumented tests.

## Optional ACTION_UP guard

`skipPrompt_okUpWithoutDownDoesNotSkip` is green on `35f9866e` (it was red on base only because its precondition, the action focused, failed). A stray OK `ACTION_UP` does not fire the skip. The optional `ACTION_UP` guard is not needed.

## TESTS ADDED

None. EVIDENCE: `docs/superpowers/evidence/2026-11-10-seek-blocked-by-skip-prompt-device.md` and its image folder, `tools/mobile-evidence-audit.sh` passed, committed as `b6660c74`.

## DEFECTS

None against this change.

## Observations (not defects of this change)

- With the next-episode prompt up and controls hidden, Up does not open the controls (focus sits on the action, Up passes through). Spec lists Up/Down pass-through as a follow-up.
- Skip segments do not arrive on a worktree build (above).

## UNAVAILABLE

None. Device available, install succeeded.

## MANUAL (owner)

1. On a build with the TMDB token (or the main-checkout stage build after merge): play an episode with a known intro, seek into it with Right until the playhead is inside, stop; expect the skip chip focused (hairline, 1.1 scale) about 1.5 s later; Right and Left seek, OK skips, Back dismisses and the next Right seeks (M3, AC-5, AC-9).
2. Hold Right for several seconds on a real remote through an intro and the credits lead-in; seeking continues, nothing skips or cancels (M5).
3. Under an open seek bar press Right with a prompt up; the seek bar keeps focus (M4 last step).

## Round 2

Worktree HEAD `37d0527c` before the evidence commit `1d38134a`; base `6b087697`. Affected ACs after fix round 1 (triage #1, #3, #10, #11).

```
CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; PlayerVMSeekBurstTest 6/6, PlayerVMPromptDismissalTest 6/6, PlayerVideoSurfaceTest 5/5, 0 failures in the player results)
CHECK: AGENT_GRADLE_SERIAL=1 make DEVICE=emulator-5554 itest GRADLE=./tools/agentw TESTS=com.kino.puber.ui.feature.player.component.PlayerPromptFocusAndKeysTest -> pass (12 tests, 0 failed, 0 skipped)
DEVICE: emulator-5554 (AVD puber_tv_36), lease taken and released; APK sha256 4d5196ed...0370 installed with install-preserve (outcome installed, compatible_replace, data kept); on-device base.apk sha256 equal after install, after the itest run and after the session
```

| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | Instrumented skip-prompt Left/Right and held-Right tests green. Device: skip recap prompt, Right `+10s`, Left `-10s`, prompt returns (S1); next-episode prompt under open controls, Right seeks (M4c, M4d) |
| AC-2 | PASS | Instrumented `nextEpisodePrompt_leftAndRightSeekAndFocusStaysOnAction` green. Device: Right on the next-episode prompt seeks and takes the prompt down (M4c, M4d) |
| AC-3 | PASS | `PlayerVMSeekBurstTest` 6/6 (includes the late credits load and `SeekBackward` cases from fix round 1). Device: after Left on the skip recap the prompt came back about 3 s later (S1); the 1.5 s timing without buffering not measurable |
| AC-4 | PASS | Round 1 result stands. Device: Back on the skip recap, then Right seeks (S3) |
| AC-5 | PASS | Instrumented OK and Back tests green. Device, now reachable (segments arrived on Outlander): OK on skip intro jumped to 2:19 (S2); Back dismissed the recap, Right seeked (S3) |
| AC-6 | PASS | Instrumented `promptAppearingUnderControls_seekBarKeepsFocusAndRightSeeks` green. Device: prompt under open controls with no key pressed, seek bar focused, chip and X idle (M4a); one Up goes to the action and not the X (M4b, triage #1 fixed); Right from the bar seeks and the bar keeps focus (M4d) |
| AC-7 | PASS | Round 1 result stands (no production change touches the held-key path). Multi-second hold on the device NOT CHECKED (adb) |
| AC-8 | PASS | check.full green |
| AC-9 | NOT CHECKED | Owner sequence (seek burst through an intro and the credits lead-in, then judge the flash). Parts ran: skip prompt keys and return (S1-S3), next-episode burst and return; the owner judges the flash |

Triage rows: #1 fixed on device (M4b); #3 covered by the unit test, no device observation; #10 re-captured (M4a, M4d); #11 evidence text corrected (screenshot scale, M4 px).

EVIDENCE: `docs/superpowers/evidence/2026-11-10-seek-blocked-by-skip-prompt-device.md` (Round 2 section), images `r2-*.png`; `tools/mobile-evidence-audit.sh` passed; committed `1d38134a`.
TESTS ADDED: none. DEFECTS: none. UNAVAILABLE: none.

Observations: after Up from the seek bar the next-episode action takes focus (1.1 scale), the X stays idle; Right then seeks. The old `m4-prompt-under-controls-chip-idle.png` (X focused, round 1) stays in the folder, marked superseded in the evidence text.

MANUAL (owner): hold Right for several seconds through an intro and the credits lead-in on a real remote (M5, AC-7, AC-9).
