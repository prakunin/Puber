# Test design: update-dialog-focus-trap

Base 0bc3ab0e. Inputs: `brief.md`, `design-architect.md` (D1-D8, R1-R7). Levels from `.flow/roles/test-designer.md`: `unit`, `instrumented` (`make DEVICE=<serial> itest TESTS=<fqcn>`), `device`. Test paths below are under `app/src/androidTest/kotlin/com/kino/puber/`.

## Risk profile

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

## Traceability

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

## Bug: failing first

| Test | Base defect reproduced | Base result |
|---|---|---|
| `launchRequestScheduledBeforeGateClosesDoesNotTakeFocus` | `FocusOnLaunchRequester.kt:57-63` checks only `isFocusRequested` after the delay (`git show 0bc3ab0e:app/src/main/java/com/kino/puber/core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt`) | fails: target focused |
| `updatePromptDpadInAllDirectionsStaysInsidePrompt` | `TvDialogOverlay.kt:46-54` has `focusGroup()` only | fails: sentinel focused |
| `updatePromptShownBeforeContentKeepsFocusOnUpdateAfterLateClaims`, `contentFocusGateKeepsOuterInactiveValue` | gate absent | fails to compile (`ContentFocusGate` is new) |

Written first: `launchRequestScheduledBeforeGateClosesDoesNotTakeFocus`, then `updatePromptDpadInAllDirectionsStaysInsidePrompt`; both run red on the unchanged worktree before any production edit.

## Manual checks (qa-device)

Setup: lease an emulator (`tools/emulator-resource-lock.sh`), all adb as `adb -s <serial>`, install with `tools/android-apk-install-preserve`. Evidence build (Q2): `currentVersion` in `app/build.gradle.kts:19` lowered in the worktree, uncommitted, versionCode unchanged; reverted after M2. Update is never pressed on the device. Screencaps only; no UI dumps or logcat in evidence. Run `tools/mobile-evidence-audit.sh` on the evidence dir.

| # | Build | Steps | Expected | Must not happen |
|---|---|---|---|---|
| M1 launch race | evidence | 1. `am force-stop`, cold `am start`. 2. Wait for the prompt. 3. Screencap A. 4. On Update: DPAD_UP, DOWN, LEFT, RIGHT; screencap after each. 5. DPAD_RIGHT to Later, repeat 4; screencap B. 6. KEYCODE_BACK once. 7. Screencap C, then DPAD_DOWN, screencap D. Repeat 1-7 three times; record per run whether the prompt appeared before Home's hero (screenrecord of first 5 s). | 2-5: Update or Later focused, Home beneath identical in A and B. 6: prompt gone. 7: focus on a visible Home item (hero or first card), rail closed; D moves within Home. | Focus on Home or rail while the prompt is up; rail opening on LEFT or Back; Home scrolling; no focused item after dismiss; crash. |
| M2 dismiss by button | evidence | 1. Cold start; wait until Home's hero is visible and focused, then until the prompt shows (relaunch until this order is seen, max 5; record if never). 2. Screencap A (Home focus before). 3. Center on Later. 4. Screencap B. 5. Relaunch, same order, KEYCODE_BACK once, screencap C. 6. DPAD_DOWN, DPAD_RIGHT; screencap D. | 3, 5: prompt gone, rail closed. B, C: focus on the card focused in A (D8 restore) or the hero. 6: moves within Home. | Different card focused, Home scrolled to top, rail revealed, focus nowhere. |
| M3 manual check (AC-2, AC-8) | committed | 1. Rail, Device settings, run "check for updates". 2. On the result (UpToDate): screencap. 3. DPAD in four directions. 4. Center on Close. 5. Screencap, DPAD_DOWN. | 2: Close focused within 1 s. 3: stays on Close. 5: focus on a visible settings item (the check row), moves within settings. | Focus on settings under the scrim while the result shows; focus nowhere after Close. |
| M4 smoke and other dialogs | committed | 1. Cold start, no prompt: Home gets launch focus. 2. KEYCODE_BACK on Home: rail reveals; Back again: exit prompt toast. 3. Bookmarks: menu key on a card, menu opens on first action; DPAD in four directions; Back: focus on the same card. 4. Device settings: open API domain dialog, type in the field, IME Done; reopen, Back. | 1: Home focused as on base. 2: base behaviour (D7 inactive without prompt). 3: focus stays in the menu, returns to the card. 4: Done submits/closes the IME, Back closes the dialog. | Home without focus; Back doing nothing (R6); D-pad leaving the menu; IME Done or Back stuck in the field. |

## perf track

N/A: perf track off.

## UNTESTABLE

None.
