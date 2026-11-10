LENS: blind  ROUND: 1  BASE: 6b087697

### F1 [major] The prompt's Cancel button can no longer be reached with the D-pad
- Where: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerTimedActionOverlay.kt:265-284
- Problem: Focus now lands on the primary button (`focusRequester` is attached only to it), and Cancel sits to its left. The new `onPreviewKeyEvent` on the Row takes `DPAD_LEFT`/`DPAD_RIGHT`, returns true and sends them out as seeks. That was the only key that moved focus within the Row, so focus can never get to Cancel. Cancel lost its `focusRequester`, and nothing else ever focuses it. The diff does not say this was intended.
- Failure scenario: The skip-intro or next-episode prompt is up and the viewer presses Left to reach the visible ✕. Playback seeks back 10 s and the plate goes away (`onSeekPerformed`). The ✕ can only be activated with Back, which a viewer cannot learn from the screen.
- Fix: If the ✕ should stay reachable, consume Left as a seek only while Cancel is not the next focus target (for example, take Left on the primary button to mean "focus Cancel" and seek only from Cancel). If Back is meant to be the only way to cancel, drop the ✕ as a focusable control, so the plate does not show a button nobody can press.

### F2 [minor] Pressing OK on the skip prompt now skips; before, it cancelled
- Where: app/src/main/java/com/kino/puber/ui/feature/player/component/SkipSegmentOverlay.kt:32 (was `focusPrimaryAction = false`)
- Problem: The skip prompt used to put focus on Cancel. Now the shared requester sits on the primary button, so the default OK press skips the segment. The diff states nothing about this behaviour change; it shows up only as the assertion in `skipPrompt_appearsWithActionFocused_okSkips`.
- Failure scenario: A viewer who has learned "OK dismisses the skip prompt" presses OK on an intro or recap prompt. The player jumps to the end of the segment and the viewer misses content they meant to watch.
- Fix: Confirm this is intended and record it in the run notes. Otherwise attach the requester to the Cancel button for the skip prompt only.

### F3 [minor] The seek burst does not hold back the next-episode prompt on playback end or when segments load late
- Where: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1377, 1646 (gate only at 1527)
- Problem: `seekBurstJob` is checked only in the position tick. `onPlaybackEnded` and the late-credits path in `loadSkipSegments` call `startNextEpisodeCountdown()` with no burst check.
- Failure scenario: The viewer holds Right near the end of an episode. `seekForward` clamps to `duration`, playback ends, and the countdown appears during the burst. Focus moves to the prompt, and the next repeat goes through the prompt as `SeekForward`. That causes `seekTo(duration)` and then `onSeekPerformed`, which clears the countdown, and then the player ends again and the countdown comes back. The prompt flickers on every repeat while the key is held, which is the thing this change is meant to prevent.
- Fix: In `onPlaybackEnded` and the late-credits branch, skip `startNextEpisodeCountdown()` while `seekBurstJob?.isActive == true` (the tick offers it once the burst ends). Alternatively, put the gate inside `startNextEpisodeCountdown` for every caller except the explicit user paths.

### Unproven
- PlayerVM.kt:714 `seekBurstJob` is not cancelled when the episode changes. After a seek followed by an episode switch within 1.5 s, the new episode's prompt checks are suppressed for the rest of the window. This would matter only if a prompt can be due within 1.5 s of an episode starting (an intro segment with `startMs` below about 1.5 s plus the lead-in).
- PlayerScreenContent.kt:110 `requestFocusOrRetryNextFrame` retries only once. It would be a problem only if the prompt's `AnimatedContent` can take more than one frame to attach the button node (an enter transition with a delay, or very slow device composition); no such case is shown in device evidence.
