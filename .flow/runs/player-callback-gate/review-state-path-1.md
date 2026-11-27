LENS: state-path  ROUND: 1  BASE: cec5f7fc

No findings.

Note on the qa lead (the Audio row reads "Track 1" after a quality switch). The diff does not cause it, so it is not reported as a finding:
- `notifyTracksUpdated` names a track with `format.label ?: format.language ?: "Track ${index + 1}"` (`/home/dev/orca/workspaces/Puber/player-callback-gate/app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:873`). It reads the live `player.currentTracks`, not the event payload. The diff leaves this function alone, and the gate only decides whether a callback is delivered. It never changes what gets reported.
- The new stream's listener is added in `bindSession` before `setMediaItem` (`PlaybackController.kt:533-534`), so it gets every event from the switch on: the empty masked tracks, which the early return at `:869` skips, and then B's own tracks. The only events dropped are those still addressed to the removed listener of stream A. On master those would also have read the live tracks, which by then belong to B. The last `onTracksUpdated` the VM gets is the same with or without the diff.
- The likely cause is already on master. For Auto (index 0) `PlayerInteractor.selectStreamCandidates` tries `hls4` first. For a manual quality it tries `hls` first (`/home/dev/orca/workspaces/Puber/player-callback-gate/app/src/main/java/com/kino/puber/domain/interactor/player/PlayerInteractor.kt:236` against `:247`). So picking a specific quality loads a different playlist. Its audio formats probably carry no NAME label and no language, and fall through to "Track 1". I could not confirm what the playlists contain without the live API. A master build doing the same Auto → manual-quality switch on the box would settle it.

What I traced and couldn't break:
- **Quality pick:** `selectQuality` → `switchStreamUrl` → `switchStream(B)`.
  - `bindSession` removes A's listener, starts session B (`fromSwitch=true`) and adds B's listener before `setMediaItem(B, savedPosition)` and `prepare`. The VM then sets `selectedQualityIndex`. A's stale READY/ENDED no longer reach `onStreamReady` or reset `streamLinksRefreshAttempted`.
  - **Queued error from A:** it arrives at B's listener and goes to `routeError`. B's playlist has not started loading, so the error is dropped and `prepare()` runs once, with the player IDLE at B's saved position.
  - **Rest of that event batch:** the IDLE and isPlaying=false events that come after `onPlayerError` call `notifyPlaybackState`. It reads the live state, which the nested prepare has already moved to BUFFERING. Media3 delivers events queued during a delivery after the current batch, so the UI shows no flash of IDLE.
  - **Selecting the same quality again:** `selectQuality` returns early, so nothing happens.
- **NEXT_URL fallback:** B's error, with B's playlist load started, is reported with B's URL. The VM's URL check passes and `switchToNextStreamCandidate` calls `switchStream(C)` from inside the callback. The listener is swapped while Media3 is still delivering; Media3 skips the removed listener and does not give the rest of the batch to the new one. The player is IDLE, so C's `prepare` takes effect.
- **Refresh-links path:** the later `switchStream` from the coroutine starts a fresh switch session on the IDLE player, and the re-prepare limit of one is counted per session.
- **Two quick switches (A → B → C):** A's queued error lands in session C, which has not loaded yet, so it gets one re-prepare. If C's URL equals A's (Auto → manual → Auto), A's queued playlist load start could mark C as loaded. That is the same-URL risk the spec already accepts, and with HLS it needs A's master playlist load to still be queued, so it is not reported.
- **`release` and `prepare`:** the session is invalidated and the listener is set to null, so a new player starts with a clean session that never re-prepares.
- **Focus:** the diff makes no Compose or focus change.
