I found nothing that earns a severity in the round-2 diff. One thing I could not settle from the code is listed under Unproven.

```
LENS: edge  ROUND: 2  BASE: 50809306
No findings.

### Unproven
- app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1430 forward hold clamped to the duration: the end callback fires while `pendingSeekTarget == duration`, so the commit seeks to the position the player already stopped at. The film exits and is marked watched only if that seek makes the player report the end again (PlaybackController.kt:242-246 forwards each STATE_ENDED); if it does not, the film stays on its last frame with no exit and no watched mark. To make it real: a device hold of Right across the end of a film, or Media3 source showing that `seekTo` while ended does not go through STATE_BUFFERING. No test drives `onPlaybackEnded` after a hold clamped to the duration (`aHoldNearTheEndClampsTheTargetToTheDuration` never fires the end).
```

Boundaries I checked against the new early return in `onPlaybackEnded`, all handled:
- **No hold running:** nothing changes, the old end handling runs as before.
- **Pending target behind the end** (the new film test): it lands, there is no exit and no watched mark, and the throttle job wakes later, finds nothing pending and stops.
- **Next-episode countdown already showing:** the commit's seek calls `onSeekPerformed`, which clears the countdown, the same as a manual seek.
- **A panel open:** the early return skips setting the exit and advance flags. That is correct because playback moves away from the end.
- **A pending target with no throttle job:** this state cannot happen. A target is only stored while the job is active, and `dropPendingSeek` clears both together.
- **Seeking inside the player's end callback:** this only seeks; it does not release the player, which the existing `advanceAfterDismissedEnd` note warns against inside that callback.

The two new failover tests, `aStreamFailureCommitsThePendingTargetBeforeTheNextCandidate` and `aLinkRefreshCommitsThePendingTargetBeforeTheNewStream`, are guards over commit sites that already existed. The plan's Implementation record says so, and their `verifyOrder` blocks pin the order: the pending seek lands before the stream switch.

Files read: `/home/dev/orca/workspaces/Puber/player-dpad-seek-coalesce/app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt` and `/home/dev/orca/workspaces/Puber/player-dpad-seek-coalesce/app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt`.
