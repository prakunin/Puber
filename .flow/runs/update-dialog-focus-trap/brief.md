# Brief: update-dialog-focus-trap

Base 0bc3ab0e. Analyst, 2026-11-20. Builds on `research.md`; its code references were re-read at base and hold. Absorbs `update-prompt-focus` (Back opened the rail instead of dismissing).

kind: bug. size: M. tracks: none. Device verification: **yes** (TV focus, D-pad, Back, overlay over Main; the bug shows only at runtime).

Size reason: one goal, but it crosses the root host (`App.kt`), Main (`MainScreenComponent.kt`) and a shared component (`TvDialogOverlay.kt`), all in the TV-focus risk area.

## Request
At launch the "new version" prompt appears but D-pad focus stays on the screen beneath; Update cannot be pressed (owner, 2026-11-20). Seen earlier: D-pad reached Main and the first Back moved focus to the rail (`update-prompt-focus`). v1.12.3 tag waits for this fix.

## Root cause (plausible: traced in code, not reproduced on a device)
Paths relative to `app/src/main/java/com/kino/puber/`.

| # | Cause | Where |
|---|---|---|
| 1 | The prompt asks for focus once, 100 ms after the state turns `Available`; the result is ignored, no retry, no re-claim. | `ui/feature/update/component/UpdatePromptOverlay.kt:56-61` |
| 2 | The check starts in the root VM at app start (`ui/feature/update/vm/UpdatePromptVM.kt:37-56`) while `LauncherScreen` is shown for at least 500 ms (`ui/feature/root/component/LauncherScreen.kt:19`, `:40-43`). A fast answer shows the prompt before Main exists. | as cited |
| 3 | When Main then composes, its launch requester takes focus 100 ms later (`ui/feature/main/component/MainScreenComponent.kt:95`, `core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:41-51`), and Home's hero and row claims take it again when their data publishes (research.md: 3, "What takes focus beneath"). Nothing tells Main a modal is open: `MainScreenComponent.kt:138` provides `LocalContentFocusActive` from the rail alone, and `LocalAutoFocusOnLaunchEnabled` stays `true`. | as cited |
| 4 | `TvDialogOverlay` is an in-tree `Box` with `focusGroup()` only; no `onExit` trap, so D-pad search also leaves it for focusables under the scrim. | `core/ui/uikit/component/TvDialogOverlay.kt:46-54` |
| 5 | Back: `OnBackPressedDispatcher` gives Back to the last enabled callback added. The overlay's `BackHandler` (`TvDialogOverlay.kt:43-45`) is added when the prompt appears; if Main composes later, `RailClosedBackHandler` (`MainScreenComponent.kt:160-175`, enabled while the rail is closed) is added after it and wins, so Back calls `drawerState.reveal()` and focus moves to the rail. | as cited |

Order decides the outcome: prompt after Main's launch focus and Home's first publish works; prompt before either fails. This matches the intermittent reports.

## Scope
In: research.md option B (research.md: 6). Root gate while the prompt is not `Hidden` (`LocalContentFocusActive = false`, `LocalAutoFocusOnLaunchEnabled = false` around the navigator, AND-combined at `MainScreenComponent.kt:138`); a directional-only `onExit` trap in `TvDialogOverlay`; a retried initial claim in `UpdatePromptOverlay` (pattern `core/ui/uikit/component/TvContextMenu.kt:77-90`); Back that dismisses the prompt whatever the handler order; focus handed back to Main on dismiss.
Out: window-level `Dialog` (option C); a general root-modal API; fixing `home-rows-context-menu-focus-loss` and `favourites-launch-focus-ignores-rail` beyond what the gate covers by itself.

## Acceptance criteria
- AC-1. Given the prompt becomes `Available` before Main composes (launch race), When Main and Home then load, Then focus is on the prompt's Update button and stays there after Home's hero and rows publish (instrumented test with the overlay host and a child that claims focus late; device observation).
- AC-2. Given Home already holds focus, When the prompt becomes `Available`, Then focus moves to Update within 1 s (instrumented test; device observation).
- AC-3. Given the prompt holds focus, When the user presses Up, Down, Left or Right on every button, Then focus stays inside the prompt and the rail does not open (instrumented test; device observation).
- AC-4. Given the prompt is shown in either order of AC-1 and AC-2, When the user presses Back once, Then the prompt is dismissed and the rail stays closed (device observation; test where the harness allows).
- AC-5. Given the prompt is shown, Then Home beneath does not scroll or change its focused card while it is open (device screencap before and after).
- AC-6. Given the prompt is dismissed by Later/Close or by Back, Then focus lands on a visible Home item (hero or the restored card) with the rail closed, and the next D-pad press moves within Home (device observation).
- AC-7. Given Update is pressed, Then `UpdatePromptAction.UpdateClicked` reaches the VM (instrumented test with a fake; no real download or install on the device).
- AC-8. Given the manual check from Device settings, When the result state shows, Then its primary button holds focus, D-pad stays inside, and after Close focus returns to a visible item on the settings screen (device observation).
- AC-9. Given other `TvDialogOverlay` users, Then the Bookmarks context menu still opens with focus on its first action and restores focus on close, and `ApiDomainDialog`'s text field still accepts IME Done and Back (`TvContextMenuLongPressTest`, `BookmarksScreenContentFocusTest` green; device observation for `ApiDomainDialog`).
- AC-10. The full check passes in the worktree.

## Risks
| Risk | Guard |
|---|---|
| `onExit` trap in a shared component breaks text input exits (`ApiDomainDialog.kt:100`) | cancel directional moves only; AC-9 |
| Whether `onExit` also blocks a programmatic `requestFocus()` outside the group is `unverified` (research.md: 5) | the root gate stops the requests at their source; AC-1 |
| `LocalContentFocusActive` and `LocalAutoFocusOnLaunchEnabled` are `staticCompositionLocalOf` (`FocusOnLaunchRequester.kt:23`, `:30`): flipping them recomposes the whole navigator subtree | flips twice per prompt only; watch for lost Home state on dismiss (AC-6) |
| `rememberFocusRequesterOnLaunch` marks itself requested only when it runs (`FocusOnLaunchRequester.kt:41-51`); with the gate off at launch, Main's launch request runs on dismiss, and the hero claim may race it | AC-6 on device; one owner of focus on dismiss in the spec |
| Back fix depends on handler order or on key handling inside the focused overlay | AC-4 in both orders |
| Launch race needs a real `Available`: dev build is `1.12.3-dev` (`app/build.gradle.kts:19`, `:226`), and no newer release exists | Q2 |

## Open questions
1. `onExit` trap in `TvDialogOverlay` for all its users, or in the update prompt only? Recommended all users, directional moves only (research.md: 8 Q2). Blocks: no (AC-9 written for it).
2. Device evidence for the launch race: allow one evidence build in the worktree with `currentVersion` temporarily lowered (uncommitted, versionCode unchanged so no downgrade), then a final smoke on the committed build? Recommended yes; the read-only GitHub check is the only external call, and Update is not pressed on the device (AC-7). Blocks: no.
3. Root gate as one boolean for the update prompt only, or a general root-modal flag? Recommended the prompt only (research.md: 8 Q3). Blocks: no.
