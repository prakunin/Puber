LENS: edge  ROUND: 2  BASE: 4db9c0d4 (incremental 3bf1209d..a7027fa1)

### F1 [minor] A panel opened in the 1 ms window drops the dismissed-end advance, bringing the original stall back
- Where: `/home/user1/orca/workspaces/Puber/next-episode-end-stall/app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1422`
- Problem: when the deferred block finds `activePanel != ActivePanel.None`, it just returns. It does not hand the decision to the panel close. Only the synchronous `onPlaybackEnded` branch sets `advanceWhenPanelCloses` (line 1404), and it only does that when the panel was already open at the moment of ENDED. On close, `onPanelClosed` (865-870) finds `advanceWhenPanelCloses == false`. It then calls `checkEarlyNextEpisode`, which is gated on `!countdownDismissed` (1615), so it does nothing. `controlsStateMachine.markPlaybackEnded()` was not called either.
- Failure scenario: the viewer cancels the prompt. A key press that opens the settings or info panel is already in the main looper queue, or arrives within 1 ms, when ExoPlayer posts STATE_ENDED. The panel opens before the delayed resume, so the block returns. The viewer closes the panel and is left on the ended frame with nothing advancing, which is the bug this run fixes. The window is narrow: it takes an input message already queued ahead of the 1 ms delayed message. That is why this is minor.
- Fix: in that early return, store the decision instead of dropping it: `if (content.activePanel != ActivePanel.None) { advanceWhenPanelCloses = true; controlsStateMachine.markPlaybackEnded(); return@launch }`. Keep the plain return for `nextEpisodeCountdown != null`.

Checked, with no finding:
- The deferral really does leave the callback. `PuberVM.launch` uses `viewModelScope`, which runs on `Dispatchers.Main.immediate`. The block runs inline up to `delay(1)`, and on the Android main dispatcher that becomes a `postDelayed`. So `release()` runs from a later looper message, outside the STATE_ENDED dispatch at `PlaybackController.kt:231-234`.
- `onCleared`/exit: `viewModelScope` is cancelled, so the block never runs.
- A user-started switch (next/previous) in the window: `episodeSwitchInProgress` is checked, and the state is `Loading`, so the block returns.
- A countdown starting in the window cannot happen: `checkEarlyNextEpisode` requires `!countdownDismissed`.
- Mark-watched order: `autoMarkCurrentAsWatched` still runs synchronously before the deferred release. `theFinishedEpisodeIsMarkedWatchedBeforeTheNextOneLoads` covers this.
- The fixes for #2-#4 match the triage: the R7 no-panel setup, the countdown-branch guard with `release()` verified zero times, and `assertNull(nextEpisodeCountdown)`. The new deferral test asserts no switch before `advanceTimeBy(2)`, so it would fail if the switch went back to being synchronous.

### Unproven
- `PlayerVM.kt:1418` A seek back or restart from the ended frame inside the 1 ms window still switches episodes, because `countdownDismissed` stays true and nothing re-checks the player state. This becomes real if seek or play input can land within 1 ms of STATE_ENDED. Re-checking `playbackController.playbackState == STATE_ENDED` in the block would close it.
- `PlayerVM.kt:1418` Back pressed inside the window, when `OnBackPressed` calls `router.back` but the VM is not yet cleared during the screen transition: the block could still release the player and start preparing the next episode on a screen that is leaving. This becomes real if a back key can be queued ahead of the delayed resume.
