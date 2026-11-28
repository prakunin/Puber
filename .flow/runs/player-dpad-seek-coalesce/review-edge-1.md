LENS: edge  ROUND: 1  BASE: 8c3117ea

No findings.

### Unproven
- PlayerVM.kt:1021 and PlayerVM.kt:1033 (`selectBufferPreset`, `toggleFastDns`): both pass the raw `playbackController.currentPosition` to `initializePlayer`. They do not commit or drop a pending target, so the throttle job's trailing seek would later land on the newly prepared player. This only becomes real if the viewer can open settings and pick a preset or the DNS toggle within 250 ms of the last Left/Right press. That takes at least two key presses, and nothing in the spec or tests shows it happens.
- PlayerVM.kt:1435 (`onPlaybackEnded`): writes `playbackController.currentPosition` and ignores `pendingSeekTarget`. This only matters if ENDED fires while a target that differs from the end is pending, for example a Left press during the window right after a leading seek to `duration`. No scenario or report shows that.
- PlayerVM.kt:740-769 (`seekBy` and `requestSeek` during `PlayerViewState.Loading` after `switchEpisode`): presses after `resetMediaSession` set a new pending target, and the trailing seek would hit the next episode's player. This only becomes real if `PlayerVideoSurface` sends `SeekForward`/`SeekBackward` while the Loading view is shown. The key mapping at PlayerVideoSurface.kt:216 was not traced to show that it does.
- PlayerVM.kt:744-748: when `duration` is still `C.TIME_UNSET`, `coerceAtMost(duration)` gives a negative target. The base code has the same problem, so this diff did not introduce it. It becomes real only with evidence of seek presses before the player knows the duration.
