LENS: blind  ROUND: 1  BASE: be8395a4

### F1 [minor] A subtitle pick left over from the previous media can still turn text on when the new media reports no audio track
- Where: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:234-237 (`onTracksChanged`), :562-578 (`release` never clears `pendingSubtitleTrack`), :852 (`notifyTracksUpdated` returns early with no audio groups); app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:211-221, 544-553
- Problem: The new "re-send the picker's own row" fix in `restoreTrackPreferences` only clears the stale pick indirectly. It runs only from `onTracksUpdated`, and the controller sends `onTracksUpdated` only when the media has at least one audio group. The controller keeps `pendingSubtitleTrack` across `release()`/`prepare()`. Right after `notifyTracksUpdated`, `onTracksChanged` calls `applyPendingSubtitleSelection()`. That call clears the text overrides, calls `setTrackTypeDisabled(TEXT, false)` and sets the previous media's preferred language. So it undoes the new default of text being off at prepare.
- Failure scenario: The viewer picks Russian subtitles on episode 1, then switches to media whose tracks have no audio group (or whose audio group is not exposed yet when the text tracks appear). `restoreTrackPreferences` never runs, the stale pending track is applied, and Russian text shows while the picker says Off. When the audio tracks arrive in a later `onTracksChanged`, the VM fixes it, but only after a visible flash.
- Fix: Reset `pendingSubtitleTrack = null` in `prepare()` (or in `release()`). The VM already re-sends the live or restored row on every rebuild, so a rebuild loses nothing. Alternatively, call `applyPendingSubtitleSelection()` only once the VM has restored the tracks.

### F2 [minor] Three player-behaviour changes in PlaybackController have no test that would catch a regression
- Where: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:390 (`setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)` at prepare), :611 (`setSelectionFlags(FORCED)`), :893-901 (positional fallback removed)
- Problem: The three changes:
  - Text is now off by default when the player is built.
  - Forced subtitles now get the FORCED selection flag.
  - The "API row N is text track N" fallback is gone.

  No test in the diff covers any of them, and there is no PlaybackController unit test in `app/src/test/.../player/vm/`. The VM tests mock `playbackController`, so they only check which row the VM sends, not what the track selector receives.
- Failure scenario: Someone removes the default-disabled line, or restores the positional fallback. Every unit test still passes, and an HLS DEFAULT rendition, or the wrong manifest subtitle, shows under the picker again.
- Fix: Extract building the default parameters and choosing the text track (`findTextTrack`) into pure functions, or test them with a fake `Tracks`. At minimum, assert that the parameters built at prepare have TEXT disabled, and that a track with no matching id or label gets no override, not a positional one.

No other findings survived checking:
- Koin wiring: `PlayerUIMapper(get(), get())` resolves the `single<ResourceProvider>`.
- `configuration.locales` is fine with minSdk 24.
- `initialize()` dropping `activePanel` without `PanelClosed` is safe: `resetMediaSession` clears `exitWhenPanelCloses`, and the factory rebuilds `debugInfo`.
- The rewritten `controlsTimerDuringExit_changesNothing` still exercises the `closing` guard, because `onPlaybackEnded` with no next episode calls `exitPlayer`.
- `isO3Language` throwing is caught for the `ai` code and unknown codes.
