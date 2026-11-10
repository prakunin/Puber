LENS: edge  ROUND: 2  BASE: 6b087697

### F1 [nit] Leftover focus-only code and a stale comment on the now-unfocusable X
- Where: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerTimedActionOverlay.kt:328-330, 407-414
- Problem: The X button can no longer take focus, because `iconOnly` gets `canFocus = false`. So the `focused` tint branch in `HaloCloseIcon` never runs for the X. The comment above `describeTimeLeft` still says "either of them can hold the focus", which is no longer true.
- Failure scenario: Nothing goes wrong at runtime. The next reader is misled about which button can hold focus.
- Fix: Change the comment to say only the action button takes D-pad focus. Optionally drop the `focused` branch for `iconOnly`.

Checked, no blocker or major:
- #1, the X can't be focused. Both callers (`SkipSegmentOverlay` and `NextEpisodeOverlay`) pass `compactCancel = true`, so both prompts lose the X as a focus target. They are dismissed with Back (the `BackHandler` stays on while the prompt is visible). The action button keeps its `focusRequester`, so the prompt still has one focus target.
- #3, late credits during a seek burst. Once the burst ends, the tick at `PlayerVM.kt:1527` calls `checkEarlyNextEpisode`, which matches `shouldStartNextEpisodeForLoadedCredits` field for field. So the prompt that was held down does come back. If playback is paused when the burst ends, the prompt only appears once playback resumes, as before.
- #6 and #7, the tests. In the new credits test, segments load after 500 ms, while the burst is running; on the old code the `assertNull` at 1 000 ms would fail. The `SeekBackward` test drives the shared `startSeekBurst()` path. The added asserts in `everySeekInTheBurstRestartsTheWindow` now catch a window that doesn't restart.
