LENS: edge  ROUND: 1  BASE: cec5f7fc

No findings.

### Lead from qa: Audio row shows "Track 1" after a quality switch
This change does not cause it. The text comes from code the diff does not touch. In `/home/dev/orca/workspaces/Puber/player-callback-gate/app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:873`, `notifyTracksUpdated` builds each audio row's name like this:

```kotlin
val label = format.label ?: format.language ?: "Track ${index + 1}"
```

`PlayerVM.onTracksUpdated` (`PlayerVM.kt:576`) then swaps the API's names (`mapAudioTracks`, e.g. "01. Дубляж (RUS)") for whatever list the player reports, as long as that list is not empty. So "Track 1" shows whenever the stream's audio has neither a label nor a language. That happens with audio carried inside the video stream, or with an HLS stream that has no named audio entries. It happens the same way on master, at the first READY and after any switch.

The diff only decides which session's listener gets to call `notifyTracksUpdated`. It does not change what that function reports. It also drops no tracks update for the current session:
- `bindSession` runs before `setMediaItem`, so the new listener is already registered when the new media's events are queued.
- When the player can't start the new stream until the old error comes in, that error is answered with a re-prepare, and the new stream's tracks then reach the new listener.
- ExoPlayer delivers each call's events before the call returns, so nothing from the old stream is still waiting at switch time and lost when the old listener is removed.

### What I checked and found handled
- **Error belongs to the old stream:** `routeError` checks it before the BehindLiveWindow and AC3 branches. It re-prepares at most once per switch session and reports the next error against the session's URL.
- **Detecting the new stream's load:** `gate.onLoadStarted` compares the URL as requested (`dataSpec.uri`, before any redirect), which `stream.url` matches exactly. Subtitle and redirect loads don't match. The warm-up player has its own listeners and never reaches the gate.
- **Re-entrant switch:** when a failure callback leads the VM to switch inside the callback (`PlayerVM.kt:275`), the listener removed mid-dispatch gets no more events. The player is already IDLE at that point, so that switch's `prepare()` really starts.
- **Release:** `release()` and a `prepare()` that releases the old player first remove the session listener and end the gate session. The re-prepare uses `exoPlayer?` and runs only for the current session.
- **Retries inside one session:** `recoverBehindLiveWindow` and `disableAc3AndRetry` re-prepare within the same session, which has already loaded its URL, so they are not mistaken for an old-stream error.
- **Ready or ended events from the old stream after a switch:** ExoPlayer holds back its own internal state updates until the switch has been processed, and the old listener is removed anyway.

### Unproven
- `PlaybackCallbackGate.kt:43` switch to the same URL as the stream that is failing: the old stream's queued load counts as the new stream's load, so the old error is reported and nothing re-prepares (the spec accepts this as R12). It would become a real finding only if a refresh of the stream links, or two qualities, can hand back the exact URL of a stream that is currently failing.
- `PlaybackController.kt:873` "Track N" fallback (not in the diff): it would be a separate bug if the stream the box picked has audio with no label or language. Compare master on the same item and quality to confirm.
