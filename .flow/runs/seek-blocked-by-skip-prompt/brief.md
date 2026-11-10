# Brief: seek-blocked-by-skip-prompt

Base 6b087697. Analyst, 2026-11-10. Builds on `research.md`. No brief template exists in the plugin templates; this follows the analyst job layout. Paths are relative to `app/src/main/java/com/kino/puber/ui/feature/player/`. Line numbers are from master; several in research.md: 3 (`PlayerTimedActionOverlay.kt:254, 262-270`) do not match master and are corrected here.

kind: bug. size: M. tracks: ui. Device verification: **yes** (TV focus, D-pad input, overlays, VM state).

## Request
In the player, Left/Right seeking stops once the playhead lands in a "Skip intro" or "Next episode" section. The owner has to dismiss the prompt and start seeking again, and the same prompt comes back.

## Root cause (code trace, not reproduced on a device)
| Step | Where | Status |
|---|---|---|
| 1. A seek clears both prompts (`onSeekPerformed`); the next 500 ms tick, while playing, raises the prompt again if the playhead is in a segment (2 s lead-in) or in the credits lead-in. | `vm/PlayerVM.kt:1670-1677`, `:1510-1515`, `:1679-1689`, `:1543-1558` | confirmed (code) |
| 2. Every time the prompt becomes visible it takes focus: X for skip, the action for next episode. Nothing checks for a seek in progress or for visible controls. | `component/PlayerTimedActionOverlay.kt:191-197`; `component/SkipSegmentOverlay.kt:28`; `component/NextEpisodeOverlay.kt:26` | confirmed (code) |
| 3. Left/Right seek only from the `onKeyEvent` of the video surface or the seek bar. The prompt is their sibling (`component/PlayerOverlayLayers.kt:113,127`), and its button `Row` has no key handler, so Left/Right become focus moves between X and the action. Seeking stops. | `component/PlayerVideoSurface.kt:76-82, 210-226`; `component/PlayerProgressBar.kt:200`; `component/PlayerTimedActionOverlay.kt:266-300` | confirmed (code) |
| 4. When the prompt goes away (cancel, seek, skip), the focused button leaves the composition. `PlayerFocusEffects` only re-requests focus when `controlsVisible`, `activePanel` or the resume dialog change, so no node owns focus. The first D-pad press probably only puts focus back on the surface, which matches "start seeking again". | `component/PlayerScreenContent.kt:72-89` | plausible (Compose behaviour on removing the focused node unverified) |
| 5. "Same popup again": after a cancel, the next seek resets `countdownDismissed`, so the next-episode prompt returns by design (pinned by `PlayerVMPromptDismissalTest.kt:81`). A cancelled skip type stays down for 60 s (`vm/PlayerVM.kt:1798-1809`, `model/PlayerCountdowns.kt:77`). | `vm/PlayerVM.kt:1676`, `:1384-1390` | confirmed (code); which prompt the owner saw unverified |

## Scope
In: research.md: 6, option 1.
- (a) The prompt forwards Left/Right and media FF/RW/skip keys to `SeekBackward`/`SeekForward` and consumes them. It reuses `playerActionForKeyCode` (`component/PlayerVideoSurface.kt:213`).
- (b) The skip prompt focuses its action button (Q1).
- (c) The VM raises no prompt while a seek burst is running (the `SeekHandler` reset window, `vm/SeekHandler.kt:7`) and raises it once the burst ends.
- (d) When a prompt hides, focus returns to the video surface, or to the controls target when the controls are up.
- (e) With the controls up, a prompt that appears does not take focus (Q3).

Out: the "cancel" / dismissal policy (Q2), the prompt's look, and the shared focus helper (`focus-request-helper`).

## Acceptance criteria
- AC-1. Given the skip prompt is visible and focused, When Left or Right is pressed (also held, repeated `ACTION_DOWN`), Then each press seeks 10/10/20/30/60 s like on the bare video, and focus does not move between the prompt's buttons. Checked by a Compose test and on the device.
- AC-2. Given the next-episode prompt is visible and focused, When Left or Right is pressed, Then playback seeks as in AC-1 (and the countdown is cancelled, as today, `vm/PlayerVM.kt:1671`).
- AC-3. Given a Left/Right burst whose seeks land inside a segment or the credits lead-in, When position ticks arrive less than the reset window after the last seek, Then no prompt is raised; When the window passes and playback is still inside, Then the prompt is raised once. VM unit test.
- AC-4. Given a prompt hides through a seek, a cancel (Back or X), the action, or the countdown running out, with the controls hidden and no panel open, Then the video surface holds focus, and the very next Left/Right seeks. Checked on the device and, if feasible, by a Compose test.
- AC-5. Given the skip prompt appears, Then its action button has focus; OK skips the segment and Back dismisses it. (Depends on Q1.)
- AC-6. Given the controls are up with the seek bar focused, When a prompt appears, Then focus stays on the seek bar and Left/Right keep seeking. (Depends on Q3.)
- AC-7. Given a prompt is visible, When OK is held or Left/Right auto-repeats, Then neither the prompt action nor its cancel fires from a seek key.
- AC-8. The existing player VM tests stay green, `PlayerVMPromptDismissalTest` included (the next-episode prompt still returns after a seek that follows a cancel, `:81`), and so do `PlayerVideoSurfaceTest`.
- AC-9. On the device, an owner-style sequence: seek with Left/Right through an intro and through the credits lead-in without stopping, then let the prompt appear and press Right twice. Both presses seek. (owner judges whether the prompt flashing during a burst is acceptable.)

## Risks
- Risk area: TV focus and D-pad, VM state and playback, so the size is M (`.flow/roles/analyst.md`).
- Focus timing: a same-frame `requestFocus()` can fail silently (`player-focus-after-panel-close`). Restoring focus on hide (d) may need retries across frames, as `player-overlays-port` does with `requestAcrossFrames`.
- Same files as the `player-overlays-port` branch (`92a9441d`: `PlayerTimedActionOverlay.kt`, `PlayerScreenContent.kt`). Recommended: land this first as the smaller change, and rebase that branch on it.
- `SeekHandler` reads `System.currentTimeMillis()` (`vm/SeekHandler.kt:23`). AC-3 needs a clock that the VM tests can control, or a burst marker set by the VM on the test dispatcher.
- Device data: skip segments need TMDB/IntroDB data, and a worktree build has no TMDB token (AGENTS.md). The next-episode prompt in a series is the reliable on-device case. Skip-prompt ACs may need a fixture or a main-checkout-provided session, and qa-device must say which.
- After (a), the X button can no longer be reached with Left/Right. Back stays the way to dismiss (`component/PlayerTimedActionOverlay.kt:181`).

## Open questions
1. Which button should the skip prompt focus, X (today) or the action? Recommended: the action, like next episode. Back dismisses, and Left/Right now seek. Blocks: yes (AC-5).
2. Should the next-episode prompt still come back after a seek that follows a cancel? Recommended: yes, keep the current tested behaviour. Once it no longer blocks seeking, it is harmless. Blocks: no.
3. When the controls are up and the seek bar has focus, should a prompt that appears take focus? Recommended: no. Focus stays on the seek bar, and the prompt shows above the bar. Blocks: yes (AC-6).
