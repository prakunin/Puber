# Update prompt at launch leaves D-pad focus on the screen beneath

Status: approved
Date: 2026-11-20
Slug: update-dialog-focus-trap
Kind: bug
Size: M
Tracks: none

## Problem / Why

### Request
At launch the "new version" prompt appears but D-pad focus stays on the screen beneath; Update cannot be pressed (owner, 2026-11-20). Seen earlier: D-pad reached Main and the first Back moved focus to the rail (`update-prompt-focus`). v1.12.3 tag waits for this fix.

### Root cause (plausible: traced in code, not reproduced on a device)
Paths relative to `app/src/main/java/com/kino/puber/`.

| # | Cause | Where |
|---|---|---|
| 1 | The prompt asks for focus once, 100 ms after the state turns `Available`; the result is ignored, no retry, no re-claim. | `ui/feature/update/component/UpdatePromptOverlay.kt:56-61` |
| 2 | The check starts in the root VM at app start (`ui/feature/update/vm/UpdatePromptVM.kt:37-56`) while `LauncherScreen` is shown for at least 500 ms (`ui/feature/root/component/LauncherScreen.kt:19`, `:40-43`). A fast answer shows the prompt before Main exists. | as cited |
| 3 | When Main then composes, its launch requester takes focus 100 ms later (`ui/feature/main/component/MainScreenComponent.kt:95`, `core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:41-51`), and Home's hero and row claims take it again when their data publishes (research.md: 3, "What takes focus beneath"). Nothing tells Main a modal is open: `MainScreenComponent.kt:138` provides `LocalContentFocusActive` from the rail alone, and `LocalAutoFocusOnLaunchEnabled` stays `true`. | as cited |
| 4 | `TvDialogOverlay` is an in-tree `Box` with `focusGroup()` only; no `onExit` trap, so D-pad search also leaves it for focusables under the scrim. | `core/ui/uikit/component/TvDialogOverlay.kt:46-54` |
| 5 | Back: `OnBackPressedDispatcher` gives Back to the last enabled callback added. The overlay's `BackHandler` (`TvDialogOverlay.kt:43-45`) is added when the prompt appears; if Main composes later, `RailClosedBackHandler` (`MainScreenComponent.kt:160-175`, enabled while the rail is closed) is added after it and wins, so Back calls `drawerState.reveal()` and focus moves to the rail. | as cited |

Order decides the outcome: prompt after Main's launch focus and Home's first publish works; prompt before either fails. This matches the intermittent reports.

### Risks
| Risk | Guard |
|---|---|
| `onExit` trap in a shared component breaks text input exits (`ApiDomainDialog.kt:100`) | cancel directional moves only; AC-9 |
| Whether `onExit` also blocks a programmatic `requestFocus()` outside the group is `unverified` (research.md: 5) | the root gate stops the requests at their source; AC-1 |
| `LocalContentFocusActive` and `LocalAutoFocusOnLaunchEnabled` are `staticCompositionLocalOf` (`FocusOnLaunchRequester.kt:23`, `:30`): flipping them recomposes the whole navigator subtree | flips twice per prompt only; watch for lost Home state on dismiss (AC-6) |
| `rememberFocusRequesterOnLaunch` marks itself requested only when it runs (`FocusOnLaunchRequester.kt:41-51`); with the gate off at launch, Main's launch request runs on dismiss, and the hero claim may race it | AC-6 on device; one owner of focus on dismiss in the spec |
| Back fix depends on handler order or on key handling inside the focused overlay | AC-4 in both orders |
| Launch race needs a real `Available`: dev build is `1.12.3-dev` (`app/build.gradle.kts:19`, `:226`), and no newer release exists | Q2 |

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

## Out of scope

### Scope
In: research.md option B (research.md: 6). Root gate while the prompt is not `Hidden` (`LocalContentFocusActive = false`, `LocalAutoFocusOnLaunchEnabled = false` around the navigator, AND-combined at `MainScreenComponent.kt:138`); a directional-only `onExit` trap in `TvDialogOverlay`; a retried initial claim in `UpdatePromptOverlay` (pattern `core/ui/uikit/component/TvContextMenu.kt:77-90`); Back that dismisses the prompt whatever the handler order; focus handed back to Main on dismiss.
Out: window-level `Dialog` (option C); a general root-modal API; fixing `home-rows-context-menu-focus-loss` and `favourites-launch-focus-ignores-rail` beyond what the gate covers by itself.

## Design

### Decisions

| # | Question | Decision | Why |
|---|---|---|---|
| D1 | Where does "prompt open" live? | `App()` holds `var updatePromptOpen by remember { mutableStateOf(false) }`; `UpdatePromptHost` reports `state != Hidden` through a new `onOpenChange: (Boolean) -> Unit` from a `SideEffect`. | The VM is resolved inside the root `DIScope` (`ui/feature/root/component/App.kt:130`), the navigator is built before `content()` (`core/ui/navigation/component/FlowComponent.kt:103-109`); the flag must cross from the second to the first. One boolean for this prompt only (Q3). |
| D2 | Where is the gate provided? | New `FlowComponent` parameter `screenFocusActive: Boolean = true`, passed to `FlowNavigator`, which adds both locals to its existing provider at `FlowComponent.kt:131-137` through a new `ContentFocusGate(active, content)` in `core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt`. The gate ANDs with the outer values: `LocalContentFocusActive provides (outer && active)`, `LocalAutoFocusOnLaunchEnabled provides (outer && active)`. | Gates the screens only; the prompt in `content()` stays outside. ANDing keeps the nested `FlowComponent` (`ui/feature/device/settings/flow/DeviceSettingsFlowScreen.kt:27`) and the default `true` a no-op. A named composable is the seam the instrumented tests drive. |
| D3 | Main overrides the outer value | `DrawerMainContent` reads `val outerActive = LocalContentFocusActive.current` and provides `outerActive && drawerState.currentValue != DrawerValue.Open` at `ui/feature/main/component/MainScreenComponent.kt:138`. | Same AND as `ui/feature/bookmarks/component/BookmarksScreenContent.kt:77`; without it the root gate is lost under Main (brief cause 3). |
| D4 | A launch request already scheduled when the gate closes | `rememberFocusRequesterOnLaunch` (`core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:43-67`) re-reads `LocalAutoFocusOnLaunchEnabled` via `rememberUpdatedState` after the delay at `:58`; when false it does not request and does not set `isFocusRequested`, so it re-arms on the recomposition that lifts the gate. | The coroutine at `:57-63` checks only `isFocusRequested`; a request scheduled up to 100 ms before the prompt opens would still steal focus. |
| D5 | D-pad trap | `TvDialogOverlay` adds `.focusProperties { onExit = { if (requestedFocusDirection in TrapDirections) cancelFocusChange() } }` before `.focusGroup()` at `core/ui/uikit/component/TvDialogOverlay.kt:50`; `TrapDirections` = Up, Down, Left, Right, Next, Previous. | Q1: all dialogs, directional moves only. `Enter` (programmatic `requestFocus`) and `Exit` (`clearFocus`, IME Done in `core/ui/uikit/component/ApiDomainDialog.kt`) pass, so the trap's behaviour on programmatic requests (research.md: 5, unverified) is not relied on. Precedent: `ui/feature/device/settings/DeviceSettingsContent.kt:300-304`. |
| D6 | Initial claim | `UpdatePromptOverlay.kt:56-61` becomes the `TvContextMenu.kt:77-91` loop: delay 100 ms, then up to N attempts 50 ms apart, stop on the first `requestFocus()` that returns true. Constants private to the file; N chosen so the last attempt is under 1 s (AC-2). | Covers the request that lands before the button is attached. No re-claim loop: with D2-D4 nothing gated retries against it. |
| D7 | Back whatever the handler order | `RailClosedBackHandler` and `RailOpenBackHandler` (`MainScreenComponent.kt:160-197`) take `outerActive` (D3) and AND it into `enabled` at `:167` and `:186`. | The only callbacks added after the prompt in the launch race are Main's (`:108`, `:148`); `TabBackHandler` is disabled with one screen (`FlowComponent.kt:719`). Disabled callbacks are skipped by `OnBackPressedDispatcher`, so the overlay's `BackHandler` (`TvDialogOverlay.kt:43-45`) gets Back in both orders. |
| D8 | One owner of focus on dismiss | `FlowNavigator` runs `LaunchedEffect(screenFocusActive)`: on true->false it calls `contentFocusRequester.saveFocusedChild()` and keeps the result; on false->true, after one `withFrameNanos {}`, it calls `restoreFocusedChild()` only if the save returned true. No `requestFocus()` fallback. | Content held focus before the prompt (AC-2, AC-8): the saved child is restored, same API pair as `FlowComponent.kt:236` and `:658`. Launch race (AC-1): nothing was saved, and the re-armed launch request (D4) plus the hero claim (`ui/feature/home/component/HomeScreenContent.kt:194-203`) run as on a normal launch. A blind `requestFocus()` could enter the rail. |

### Changes by layer

| Layer | Change |
|---|---|
| API / repository | N/A: no data change. |
| ViewModel state and actions | N/A: `UpdatePromptVM` unchanged; `UpdatePromptAction.UpdateClicked` already reaches `handleAction` (`ui/feature/update/vm/UpdatePromptVM.kt:61`). |
| Screen and DI module | `ui/feature/root/component/App.kt`: D1 state in `App()` (`:146`), `screenFocusActive = !updatePromptOpen` on `FlowComponent` (`:153`), `UpdatePromptHost` (`:129-143`) gains `onOpenChange`. `core/ui/navigation/component/FlowComponent.kt`: parameter on `FlowComponent` (`:82`) and `FlowNavigator` (`:113`), gate in the provider (`:131-137`), D8 effect next to `RootFlowReturnEffect` (`:166`). |
| Compose content | `core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt`: `ContentFocusGate` (D2), launch re-check (D4). `ui/feature/main/component/MainScreenComponent.kt`: D3 at `:138`, D7 at `:108`, `:148`, `:160-197`. `core/ui/uikit/component/TvDialogOverlay.kt`: D5. `ui/feature/update/component/UpdatePromptOverlay.kt`: D6, `FocusDelayMs` (`:43`) replaced by the retry constants. |
| Resources | N/A: no strings or assets. |

Test seams: `ContentFocusGate` + `UpdatePromptOverlay` with a late-claiming child (AC-1, AC-2, AC-3, AC-7); `TvDialogOverlay` users through `TvContextMenuLongPressTest`, `BookmarksScreenContentFocusTest` (AC-9). Host-with-gate precedent: `app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllRailCloseFocusTest.kt:243`.

### Alternatives rejected

- Window-level `Dialog` (option C): changes the visual stand and bypasses `AppRemoteHotkeyHandler` (`FlowComponent.kt:141-147`); out of scope.
- Wrapping the whole `FlowComponent` call in `App` with the locals: the prompt and root handlers would sit under the gate too.
- Consuming `KEYCODE_BACK` in the overlay's key handler: depends on Back reaching the view tree, which predictive back may bypass; D7 needs no platform assumption.
- Overlay re-claim on focus loss: two retrying claimants can fight; D2-D4 stop the sources instead.

### Risks

| # | Risk | Area | Code | Guard |
|---|---|---|---|---|
| R1 | An ungated focus request under the gate still steals focus (the drawer's `LaunchedEffect`, a tab handoff) and D6 does not re-claim. | focus | `core/ui/uikit/component/drawer/ModalNavigationDrawer.kt:198`, `FlowComponent.kt:640-645` | AC-1 device run in the launch-race order; add a re-claim only on evidence. |
| R2 | On dismiss after AC-2, D8's restore and Home's row effects (gated by `LocalContentFocusActive`, `core/ui/uikit/component/moviesList/FocusTargetState.kt:118-137`) both request focus; a different card wins or Home scrolls. | focus | `FlowComponent.kt` D8 effect, `FocusTargetState.kt:75` | AC-6 device observation; both target the remembered card. |
| R3 | The trap in a shared component changes `TvContextMenuDialog` and `ApiDomainDialog`: a dialog whose content relied on D-pad leaving it now cannot. | focus, lifecycle | `TvDialogOverlay.kt:46-54`, `TvContextMenu.kt:95`, `ApiDomainDialog.kt:100` | AC-9. |
| R4 | Flipping two `staticCompositionLocalOf` locals recomposes the whole root navigator subtree twice per prompt; remembered state survives (no structural change) but every reader re-runs. | recomposition | `FocusOnLaunchRequester.kt:23`, `:30` | Flips only on prompt open/close; AC-5, AC-6. |
| R5 | D4 changes every `rememberFocusRequesterOnLaunch` caller: one scheduled while the local is true and flipped false before 100 ms no longer fires. No production code provides `false` today besides D2. | focus | `FocusOnLaunchRequester.kt:55-63` | `HistoryScreenContentTest`, `ShowAllRailCloseFocusTest` stay green (check.full does not run them; device suite). |
| R6 | D7 disables Back for the rail while the gate is closed; if the prompt state stuck non-Hidden without the overlay composed, Back on Main would do nothing. | lifecycle | `App.kt` D1, `UpdatePromptOverlay.kt:51-53` | Gate derives from the same `state` that composes the overlay. |
| R7 | `saveFocusedChild` is called when focus may already be in transit (prompt opened mid-navigation), saving nothing; dismiss then leaves focus on no node. | focus | `FlowComponent.kt` D8 effect | AC-6, AC-8 device observation. |

## Test design

### Risk profile

| Risk | Source | P | I | Score | Covered by |
|---|---|---|---|---|---|
| Late claim beneath (launch request, hero, rows) takes focus from the prompt | brief cause 3, D2-D4 | 3 | 3 | 9 | `updatePromptShownBeforeContentKeepsFocusOnUpdateAfterLateClaims`, `launchRequestScheduledBeforeGateClosesDoesNotTakeFocus`, M1 |
| Root gate lost under Main (D3 AND missing) | brief cause 3, D3 | 2 | 3 | 6 | M1 (DrawerMainContent is private; device only) |
| D-pad leaves the prompt for content under the scrim | brief cause 4, D5 | 3 | 3 | 9 | `updatePromptDpadInAllDirectionsStaysInsidePrompt`, `tvDialogOverlayDpadDoesNotLeaveToContentBeneath`, M1 |
| Back reveals the rail instead of dismissing (handler order) | brief cause 5, D7 | 3 | 3 | 9 | M1, M2 |
| Ungated request (drawer `LaunchedEffect`, tab handoff) still steals focus | R1 | 2 | 3 | 6 | M1 |
| Trap breaks IME Done / Back / clearFocus in `ApiDomainDialog` or the context menu | brief, R3 | 2 | 3 | 6 | `tvDialogOverlayClearFocusStillLeavesTextField`, AC-9 guards, M4 |
| Dismiss leaves focus nowhere or on the rail | R7, brief | 2 | 3 | 6 | M1, M2, M3 |
| D8 restore and row effects race: other card focused or Home scrolls | R2 | 2 | 2 | 4 | M2 |
| D4 changes every `rememberFocusRequesterOnLaunch` caller | R5 | 1 | 3 | 3 | `launchRequestReArmsWhenGateOpens`, `HistoryScreenContentTest`, `ShowAllRailCloseFocusTest` |
| Static locals recompose the navigator subtree, Home state lost on dismiss | R4 | 1 | 2 | 2 | accepted: M2 would show it |
| Gate stuck closed without overlay, Back on Main dead | R6 | 1 | 3 | 3 | M4 step 2 |
| Initial claim misses an unattached button | brief cause 1, D6 | 2 | 2 | 4 | `updatePromptShownOverFocusedContentTakesFocusWithinOneSecond`, M1 |

### Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | instrumented | P0 red | `ui/feature/update/component/UpdatePromptOverlayFocusTest.kt`: `updatePromptShownBeforeContentKeepsFocusOnUpdateAfterLateClaims` | Host: `UpdatePromptOverlay(Available)` beside `ContentFocusGate(active = state == Hidden)`; content composes after the prompt with `rememberFocusRequesterOnLaunch` and a hero stand-in that claims on publish only when `LocalContentFocusActive` is true. Update stays focused 2 s after publish. Fails if the gate is not applied or does not cover `LocalAutoFocusOnLaunchEnabled`. |
| AC-1 | instrumented | P0 red | `core/ui/uikit/component/modifier/ContentFocusGateTest.kt`: `launchRequestScheduledBeforeGateClosesDoesNotTakeFocus` | `LocalAutoFocusOnLaunchEnabled` true at composition, flipped false at 50 ms (`mainClock`): the target is not focused at 500 ms. Fails on base and if D4's re-read after the delay is removed. Uses only base API. |
| AC-1 | instrumented | P0 red | `ContentFocusGateTest`: `contentFocusGateKeepsOuterInactiveValue` | Outer `LocalContentFocusActive = false`, gate `active = true`: a reader inside sees false, both locals. Fails if the gate overrides instead of AND. |
| AC-1 | instrumented | P1 guard | `ContentFocusGateTest`: `launchRequestReArmsWhenGateOpens` | Gate closed at composition, opened later: the launch target gets focus. Fails if D4 sets `isFocusRequested` while gated (dismiss in the launch race would leave focus nowhere). |
| AC-1 | device | P0 | M1 | Real launch race, Main and Home. |
| AC-2 | instrumented | P0 guard | `UpdatePromptOverlayFocusTest`: `updatePromptShownOverFocusedContentTakesFocusWithinOneSecond` | Content card focused, state set to `Available`: Update focused before `mainClock` passes 1000 ms. Fails if D6's retry is slower than 1 s or stops before success. |
| AC-2 | device | P1 | M3 | Content held focus first (manual check path). |
| AC-3 | instrumented | P0 red | `UpdatePromptOverlayFocusTest`: `updatePromptDpadInAllDirectionsStaysInsidePrompt` | From Update and from Later, Up/Down/Left/Right: focused node is a prompt button, a focusable sentinel beneath the scrim is never focused. Fails on base (no trap) and if the `onExit` cancel is removed. |
| AC-3 | instrumented | P1 red | `core/ui/uikit/component/TvDialogOverlayFocusTrapTest.kt`: `tvDialogOverlayDpadDoesNotLeaveToContentBeneath` | Same at the shared component with one inner button and sentinels on all four sides. Fails if the trap moves out of `TvDialogOverlay` (Q1). |
| AC-3 | device | P0 | M1 steps 4-5 | Rail stays closed on Left. |
| AC-4 | device | P0 | M1 step 6, M2 step 5 | Back dismisses in both orders; fails if D7's `outerActive` is not ANDed into `RailClosedBackHandler`. |
| AC-5 | device | P0 | M1 steps 3, 5 | Screencaps: Home beneath unchanged while open. |
| AC-6 | device | P0 | M1 step 7, M2 steps 5-6 | Focus on a visible Home item, rail closed, next D-pad moves within Home. |
| AC-7 | instrumented | P1 guard | `UpdatePromptOverlayFocusTest`: `updatePromptUpdateButtonSendsUpdateClicked` | Center on focused Update: the recording `onAction` holds exactly `UpdatePromptAction.UpdateClicked`. Fails if the retried claim targets another button or the click is lost. |
| AC-8 | device | P0 | M3 | Manual check result: focus, trap, return to settings. |
| AC-9 | instrumented | P0 guard | `TvContextMenuLongPressTest` (whole class), `BookmarksScreenContentFocusTest`: `bookmarksMenuOpenAndBackReturnsFocusToTheOpeningCard`, `bookmarksItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu` | Context menu still opens on its first action and restores on close with the trap in place. |
| AC-9 | instrumented | P1 guard | `TvDialogOverlayFocusTrapTest`: `tvDialogOverlayClearFocusStillLeavesTextField` | Text field inside `TvDialogOverlay` focused, `focusManager.clearFocus()`: field no longer focused. Fails if the trap cancels `Exit` (IME Done path of `ApiDomainDialog`). |
| AC-9 | instrumented | P1 guard | `HistoryScreenContentTest`, `ShowAllRailCloseFocusTest` (whole classes) | R5: launch focus unchanged for other callers. |
| AC-9 | device | P0 | M4 | `ApiDomainDialog` IME Done and Back. |
| AC-10 | check.full | P0 | `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` in the worktree | Unit suite incl. `UpdatePromptVMTest`, detekt, lint. |

Instrumented harness style: `createComposeRule()` (`createAndroidComposeRule<ComponentActivity>()` where Back is pressed through `onBackPressedDispatcher`), `PuberTheme`, `mainClock` instead of sleeps, `performKeyInput { keyDown; keyUp }` on the focused node, as in `ui/feature/showall/content/ShowAllRailCloseFocusTest.kt`. `UpdatePromptOverlay` and `TvDialogOverlay` are `internal`, reachable from the same module's androidTest.

### Bug: failing first

| Test | Base defect reproduced | Base result |
|---|---|---|
| `launchRequestScheduledBeforeGateClosesDoesNotTakeFocus` | `FocusOnLaunchRequester.kt:57-63` checks only `isFocusRequested` after the delay (`git show 0bc3ab0e:app/src/main/java/com/kino/puber/core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt`) | fails: target focused |
| `updatePromptDpadInAllDirectionsStaysInsidePrompt` | `TvDialogOverlay.kt:46-54` has `focusGroup()` only | fails: sentinel focused |
| `updatePromptShownBeforeContentKeepsFocusOnUpdateAfterLateClaims`, `contentFocusGateKeepsOuterInactiveValue` | gate absent | fails to compile (`ContentFocusGate` is new) |

Written first: `launchRequestScheduledBeforeGateClosesDoesNotTakeFocus`, then `updatePromptDpadInAllDirectionsStaysInsidePrompt`; both run red on the unchanged worktree before any production edit.

### Manual checks (qa-device)

Setup: lease an emulator (`tools/emulator-resource-lock.sh`), all adb as `adb -s <serial>`, install with `tools/android-apk-install-preserve`. Evidence build (Q2): `currentVersion` in `app/build.gradle.kts:19` lowered in the worktree, uncommitted, versionCode unchanged; reverted after M2. Update is never pressed on the device. Screencaps only; no UI dumps or logcat in evidence. Run `tools/mobile-evidence-audit.sh` on the evidence dir.

| # | Build | Steps | Expected | Must not happen |
|---|---|---|---|---|
| M1 launch race | evidence | 1. `am force-stop`, cold `am start`. 2. Wait for the prompt. 3. Screencap A. 4. On Update: DPAD_UP, DOWN, LEFT, RIGHT; screencap after each. 5. DPAD_RIGHT to Later, repeat 4; screencap B. 6. KEYCODE_BACK once. 7. Screencap C, then DPAD_DOWN, screencap D. Repeat 1-7 three times; record per run whether the prompt appeared before Home's hero (screenrecord of first 5 s). | 2-5: Update or Later focused, Home beneath identical in A and B. 6: prompt gone. 7: focus on a visible Home item (hero or first card), rail closed; D moves within Home. | Focus on Home or rail while the prompt is up; rail opening on LEFT or Back; Home scrolling; no focused item after dismiss; crash. |
| M2 dismiss by button | evidence | 1. Cold start; wait until Home's hero is visible and focused, then until the prompt shows (relaunch until this order is seen, max 5; record if never). 2. Screencap A (Home focus before). 3. Center on Later. 4. Screencap B. 5. Relaunch, same order, KEYCODE_BACK once, screencap C. 6. DPAD_DOWN, DPAD_RIGHT; screencap D. | 3, 5: prompt gone, rail closed. B, C: focus on the card focused in A (D8 restore) or the hero. 6: moves within Home. | Different card focused, Home scrolled to top, rail revealed, focus nowhere. |
| M3 manual check (AC-2, AC-8) | committed | 1. Rail, Device settings, run "check for updates". 2. On the result (UpToDate): screencap. 3. DPAD in four directions. 4. Center on Close. 5. Screencap, DPAD_DOWN. | 2: Close focused within 1 s. 3: stays on Close. 5: focus on a visible settings item (the check row), moves within settings. | Focus on settings under the scrim while the result shows; focus nowhere after Close. |
| M4 smoke and other dialogs | committed | 1. Cold start, no prompt: Home gets launch focus. 2. KEYCODE_BACK on Home: rail reveals; Back again: exit prompt toast. 3. Bookmarks: menu key on a card, menu opens on first action; DPAD in four directions; Back: focus on the same card. 4. Device settings: open API domain dialog, type in the field, IME Done; reopen, Back. | 1: Home focused as on base. 2: base behaviour (D7 inactive without prompt). 3: focus stays in the menu, returns to the card. 4: Done submits/closes the IME, Back closes the dialog. | Home without focus; Back doing nothing (R6); D-pad leaving the menu; IME Done or Back stuck in the field. |

### perf track

N/A: perf track off.

### UNTESTABLE

None.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Should the D-pad trap go into TvDialogOverlay for all dialogs? | no | answered | Yes, D-pad moves only |
| Q2 | Allow one uncommitted worktree build with currentVersion lowered for the launch-race device check? | no | answered | Yes, versionCode unchanged; Update is never pressed; final smoke on the committed build |
| Q3 | Should the focus gate cover only the update prompt? | no | answered | Yes, only the update prompt |
| Q4 | Leave the Checking state without a focus trap? | no | answered | First yes (AC-8 starts at the result state); revised after review round 1: Checking and Downloading hold a focus target inside the prompt (triage-1 #1-#2) |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-20 | update-prompt-focus folded into this run as a duplicate | owner (standing instruction: recommended) |
| 2026-11-20 | Q1-Q4 answered with the recommended answers | owner (standing instruction: recommended) |
| 2026-11-20 | Round caps lifted | owner (standing instruction) |
| 2026-11-20 | Gate 1: approved (size M, no tracks) | owner (standing instruction: recommended) |
| 2026-11-20 | Q4 revised: Checking and Downloading get a focus target inside the prompt | conductor (review finding, standing instruction) |
| 2026-11-20 | Details and Player launch focus claims read the content focus gate (triage-1 #3) | conductor (review finding, standing instruction) |
| 2026-11-20 | Accepted minors: progress ticks restart the Downloading focus claim delay; the Player Content-state gate check has no test (triage-2) | conductor (standing instruction) |
| 2026-11-20 | Gate 2: ship (judge CONCERNS, no must-fix) | owner (standing instruction: recommended) |
