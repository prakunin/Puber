LENS: state-path  ROUND: 1  BASE: 8c3117ea

No findings.

I traced these paths and found no sequence that ends in a wrong final state or loses focus:

- **The hold itself.** Each press runs `seekBy`, which resets the ladder only when the burst is idle. It restarts the burst, steps from `pendingSeekTarget ?: currentPosition` and calls `requestSeek`. With no window open, the seek happens at once (`onSeekPerformed` runs) and the throttle loop starts. A press inside the window only overwrites the pending target. `showSeekIndicator` writes the position and the indicator in one `updateContent`. At the end of the window the loop takes the pending target and seeks, or stops when there is none. No later write undoes an earlier one:
  - The position tick at `PlayerVM.kt:1627` reads the pending target first.
  - `onSeekPerformed` (`:1797`) writes only the prompt fields, never the position.
  - ExoPlayer masking keeps `currentPosition` at each real seek's target, so a step from the masked position adds up correctly.
- **Back mid-hold.** With controls up, Back only hides them and the trailing seek still lands. Back that exits runs `exitPlayer`: it pauses, `saveCurrentPosition` saves the pending target, then `dropPendingSeek` runs with no suspension in between, so the closing player gets no seek. Any further press would seek a paused player, the same as before the change.
- **Episode switch and retry.** `switchEpisode` saves first (with the pending target), then calls `release()` and then `resetMediaSession` → `dropPendingSeek` (`:1094-1096`). `retryPlayback` releases and then calls `resetMediaSession`. Neither path suspends, so the throttle loop cannot fire on the released player or on the next episode.
- **Stream switches.** All three sites (`:458`, `:479`, `:532`, the last one after the refresh suspension) commit the target right before `switchStream`, which reads `player.currentPosition` (`PlaybackController.kt:532`). The throttle loop stays alive and exits on its next pass with nothing pending. A press after the switch lands on the new stream.
- **Resume, start-over and skip-segment.** These drop the pending target before their own seek, so no D-pad target lands after it. Skip-segment within 250 ms of a press can't happen anyway: the leading seek clears the prompt and the burst keeps it hidden.
- **Re-initialising the player.** `selectBufferPreset` and `toggleFastDns` (`:1021`, `:1033`) take the position from the player instead of the pending target. Two reasons this is not a finding:
  - It needs the settings panel opened and used within 250 ms of a press, and Left/Right drive the panel at that point, not seeking.
  - Even then, the trailing seek still lands on the pending target.
- **Ended playback.** A trailing seek after `onPlaybackEnded` clears the countdown, the same as a manual seek does today. The spec accepts this as R14, so it is not new behaviour.
- **Ladder reset.** `seekBurstJob` is restarted only by `startSeekBurst`, which only `seekBy` calls, and nothing else cancels it. So "burst inactive" means what the old "more than 1 500 ms since the last press" check meant, now on the dispatcher clock.
- **Focus.** No composable changed. The prompt nodes are removed on a real seek as before, now on the first seek of a hold rather than on every press, which leaves a removed focused prompt no more likely than before.

The code is in `/home/dev/orca/workspaces/Puber/player-dpad-seek-coalesce/app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt` and `/home/dev/orca/workspaces/Puber/player-dpad-seek-coalesce/app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt`.
