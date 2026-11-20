I found nothing reachable in the incremental diff `5994eedd..a3bcee75` (app/ only).

LENS: edge  ROUND: 2  BASE: 0bc3ab0e
No findings.

### Unproven
- app/src/main/java/com/kino/puber/ui/feature/details/component/DetailsScreenContent.kt:394 gate reopening re-fires the ActionRow claim - if the automatic update result lands after the user has moved below the action row on a Details opened by content link, dismissing the prompt sends focus to the first button for one frame. GateFocusRestoreEffect (FlowComponent.kt:665-679) restores the saved child one frame later, so the only effect is a scroll jump. Evidence that would make it real: a device recording of a prompt arriving more than about 2 s after a cold start on Details.
- app/src/main/java/com/kino/puber/ui/feature/update/component/UpdatePromptOverlay.kt:59 state swap inside the prompt (Downloading or Checking to a state with buttons, or back) - the focused node is removed and nothing is claimed for FocusDelayMs (100 ms). TvDialogOverlay's onExit trap does not apply while nothing inside is focused, so a D-pad press in that window could land beneath the scrim. The same gap already existed on base between states with buttons, and these swaps are not user-driven. Evidence that would make it real: a press landing beneath the scrim during Downloading to PermissionRequired on a device.

Notes on what I checked:
- Fixes #1 and #2 from round 1: Checking and Downloading now hold focus inside the prompt. The focus claim retries in every state, and a new progress value only re-claims the holder that already has focus. Back still dismisses the prompt from both states, and the holder handles no keys.
- Fix #3: the launch claims in Details (ActionRow), the Player focus effects, ErrorOverlay and ResumeDialog now wait for the gate and re-run when it opens.
  - The other focus claims in Player and Details only follow a user action, so they can't fire under the prompt. These are the About, Settings and Episodes panels, PanelFocusRequester and TrailerOverlay. `trailerUrl` is set only when the user opens the trailer (DetailsVM.kt:320-325).
  - Player in Loading state when the gate opens: Content arrives later and its focus effects run then.
- Gate reopen versus restoring the saved focus: when the prompt is dismissed, GateFocusRestoreEffect brings back the focus it saved one frame later. On a cold start nothing was saved, so the screen's own claim stands.
- Fixes #5 to #7 (tests): the new ContentFocusGateTest lines, PlayerLaunchFocusGateTest and the UpdatePromptOverlayFocusTest additions each cover the branch they target. The retry test only fails if retries are removed, because entry is refused until 300 ms.
- also_consider list: nothing new under lifecycle/DI, Compose performance, conventions or structure. FocusHolder adds no copy of an existing helper.
