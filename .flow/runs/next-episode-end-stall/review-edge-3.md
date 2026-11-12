LENS: edge  ROUND: 3  BASE: ce2444ea

No findings.

The two changes in `advanceAfterDismissedEnd()` (`/home/user1/orca/workspaces/Puber/next-episode-end-stall/app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1417-1432`) hold against the boundaries I checked:

- **Panel opened inside the 1 ms wait (triage-2 #1):** the wait now does what `onPlaybackEnded` (line 1393, line 1404) does for a panel that was already open at the end. It calls `markPlaybackEnded()` and sets `advanceWhenPanelCloses = true`. Because `countdownDismissed` is already known to be true at that point, setting the flag to `true` gives the same result as setting it to `countdownDismissed`.
  - Closing the panel goes through `onPanelClosed` (line 865), which calls `playNextEpisode()`.
  - Picking an episode from the panel still clears the flag before `closePanel()` (line 1004), so the advance cannot fire on top of the viewer's choice.
  - A countdown that is running still returns without changing anything, as before.
- **Leaving inside the wait (triage-2 #2):** `closing` is set in `exitPlayer()` (line 1987), and the check now reads it before any side effect. The block does not suspend after the check, so it cannot be cancelled halfway. A late return after `exitPlayer()` therefore leaves nothing half done.
- **Panel opened and closed inside the wait:** the check then sees `activePanel == None` and advances, which is correct.
- **Tests:** both new tests drive the real paths (`OpenSettingsPanel` then `ClosePanel`; Back, Back then `router.back`). Each asserts the outcome that failed before the fix (`release()` or the switch), and the plan's fix-round-2 table records a red run for each.

### Unproven
- PlayerVM.kt:1424 seek or restart that resumes playback inside the 1 ms wait while a panel is open. `onPlaybackResumed` (line 814) would clear the flag first, and the deferral would then set it again on media that is playing. To make this real, there would need to be a resume event delivered within the same 1 ms as a panel open; the device cannot time that, and triage-2 already lists it as unproven.
