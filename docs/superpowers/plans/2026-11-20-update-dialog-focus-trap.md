# Update prompt at launch leaves D-pad focus on the screen beneath - plan

**Spec:** `docs/superpowers/specs/2026-11-20-update-dialog-focus-trap.md`
**Goal:** The update prompt takes and keeps D-pad focus, and Back dismisses it, whether it appears before or after Main and Home.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`; full gate: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`. Both run in `/home/user1/orca/workspaces/Puber/update-dialog-focus-trap`; never `./gradlew` there. Set `AGENT_GRADLE_SERIAL=1`.
- One task = green fast gate + green tests of the touched code + one commit, message: one imperative English sentence, no prefixes, never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- Production paths are relative to `app/src/main/java/com/kino/puber/`; test paths to `app/src/androidTest/kotlin/com/kino/puber/`. Line numbers are at base `0bc3ab0e`.
- Instrumented tests: compile with `./tools/agentw :app:compileDevDebugAndroidTestKotlin`; run with an emulator lease (`tools/emulator-resource-lock.sh acquire ...`, release when done) and `make DEVICE=<serial> GRADLE=./tools/agentw itest TESTS=<fully.qualified.Class>`. No free emulator: record it under Blocked and stop; do not skip the run.
- Harness style as in `ui/feature/showall/content/ShowAllRailCloseFocusTest.kt:57-60`: `createComposeRule()` from `androidx.compose.ui.test.junit4.v2`, `PuberTheme`, `mainClock` instead of sleeps, `performKeyInput { keyDown; keyUp }` on the focused node. `UpdatePromptOverlay` and `TvDialogOverlay` are `internal` and reachable from androidTest.
- Prompt fixture: `UpdatePromptViewState.Available(AvailableUpdate(version = AppVersion(9, 9, 9), tagName = "v9.9.9", title = "", releaseNotes = null, apkAssetName = "", apkDownloadUrl = "", apkSizeBytes = null, checksumDownloadUrl = null, releasePageUrl = null))` (`data/repository/AvailableUpdate.kt:3`, `data/repository/AppVersion.kt:3`). Buttons are found by `R.string.update_prompt_update` and `R.string.update_prompt_not_now` text.
- Do not change `currentVersion` in `app/build.gradle.kts:19`; the lowered evidence build is qa-device's, uncommitted (spec, Manual checks).

---

## Tasks

### Task 1: Stop a scheduled launch focus request when the gate closes, and add ContentFocusGate

**Covers:** AC-1
**Files:**
- Modify: `core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt` - D4 in `rememberFocusRequesterOnLaunch` (`:43-67`): `val latestAutoFocusEnabled by rememberUpdatedState(autoFocusEnabled)` next to `:47`; after `delay` (`:58`) request only when `!isFocusRequested && latestAutoFocusEnabled`, setting `isFocusRequested = true` only then (`:59-62`). Add `ContentFocusGate` (D2) below `LocalContentFocusActive` (`:30`), reading both locals (`:23`, `:30`) as outer values.
- Test: `core/ui/uikit/component/modifier/ContentFocusGateTest.kt` (new)

**Consumes:** `LocalAutoFocusOnLaunchEnabled` (`FocusOnLaunchRequester.kt:23`), `LocalContentFocusActive` (`:30`), `FOCUS_ON_LAUNCH_DELAY_MILLIS` (`:40`).
**Produces:**
```kotlin
@Composable
internal fun ContentFocusGate(active: Boolean, content: @Composable () -> Unit)
// provides LocalContentFocusActive = outer && active, LocalAutoFocusOnLaunchEnabled = outer && active
```

- [x] Write the red tests: `launchRequestScheduledBeforeGateClosesDoesNotTakeFocus` in `ContentFocusGateTest.kt` (base API only: provide `LocalAutoFocusOnLaunchEnabled` from a test-held state, true at composition, false at 50 ms of `mainClock`; target not focused at 500 ms) and `updatePromptDpadInAllDirectionsStaysInsidePrompt` in `ui/feature/update/component/UpdatePromptOverlayFocusTest.kt` (spec, Test design, AC-3 row: `UpdatePromptOverlay(Available)` over focusable sentinels beneath; from Update and from Later press Up, Down, Left, Right; focus never on a sentinel).
- [x] Run both on the unchanged worktree; both fail. Record the commands and failing assertions under Red run. Leave `UpdatePromptOverlayFocusTest.kt` uncommitted for Task 2.
- [x] Implement D4 and `ContentFocusGate`.
- [x] Add `contentFocusGateKeepsOuterInactiveValue` (outer both locals false, gate `active = true`: a reader inside sees false for both) and `launchRequestReArmsWhenGateOpens` (gate closed at composition, opened later: the `rememberFocusRequesterOnLaunch` target gets focus).
- [x] Run the fast gate and `ContentFocusGateTest`, `ui.feature.history.component.HistoryScreenContentTest`, `ui.feature.showall.content.ShowAllRailCloseFocusTest` (R5); commit `ContentFocusGateTest.kt` and `FocusOnLaunchRequester.kt`.

**Done when:** `ContentFocusGateTest` (3 tests), `HistoryScreenContentTest`, `ShowAllRailCloseFocusTest` pass through `make ... itest`; fast gate green.

---

### Task 2: Keep D-pad moves inside TvDialogOverlay

**Covers:** AC-3, AC-9
**Files:**
- Modify: `core/ui/uikit/component/TvDialogOverlay.kt` - D5: before `.focusGroup()` (`:50`) add `.focusProperties { onExit = { if (requestedFocusDirection in TrapDirections) cancelFocusChange() } }`; `private val TrapDirections = setOf(FocusDirection.Up, FocusDirection.Down, FocusDirection.Left, FocusDirection.Right, FocusDirection.Next, FocusDirection.Previous)` next to `DialogScrimAlpha` (`:17`). `Enter` and `Exit` directions pass. Precedents: `ui/feature/device/settings/DeviceSettingsContent.kt:300-304` (`requestedFocusDirection`), `ui/feature/player/component/PlayerSidePanel.kt:142` (`cancelFocusChange`).
- Test: `core/ui/uikit/component/TvDialogOverlayFocusTrapTest.kt` (new); `ui/feature/update/component/UpdatePromptOverlayFocusTest.kt` (from Task 1)

**Consumes:** `TvDialogOverlay` (`TvDialogOverlay.kt:27`).
**Produces:** none.

- [x] Implement D5.
- [x] Add `tvDialogOverlayDpadDoesNotLeaveToContentBeneath` (one inner button, sentinels on all four sides beneath; four directions; focus stays on the button) and `tvDialogOverlayClearFocusStillLeavesTextField` (text field inside `TvDialogOverlay` focused, `LocalFocusManager.current.clearFocus()`: field no longer focused).
- [x] Run `TvDialogOverlayFocusTrapTest`, `UpdatePromptOverlayFocusTest` (the AC-3 test is now green), `core.ui.uikit.component.TvContextMenuLongPressTest`, `ui.feature.bookmarks.component.BookmarksScreenContentFocusTest` (`bookmarksMenuOpenAndBackReturnsFocusToTheOpeningCard` `:72`, `bookmarksItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu` `:40` must pass); fast gate; commit.

**Done when:** those four classes pass; fast gate green.

---

### Task 3: Retry the prompt's initial focus claim until it lands

**Covers:** AC-1, AC-2, AC-7
**Files:**
- Modify: `ui/feature/update/component/UpdatePromptOverlay.kt` - D6: replace `FocusDelayMs` (`:43`) with `private const val FocusDelayMs = 100L`, `FocusRetryDelayMs = 50L`, `FocusAttempts = 16` (last attempt at 850 ms); the `LaunchedEffect(state)` at `:56-61` keeps the `Checking` guard and runs the loop of `core/ui/uikit/component/TvContextMenu.kt:77-91`: `repeat(FocusAttempts) { if (runCatching { primaryFocusRequester.requestFocus() }.getOrDefault(false)) return@LaunchedEffect; delay(FocusRetryDelayMs) }`.
- Test: `ui/feature/update/component/UpdatePromptOverlayFocusTest.kt`

**Consumes:** `ContentFocusGate` (Task 1), `rememberFocusRequesterOnLaunch` (`FocusOnLaunchRequester.kt:43`), `UpdatePromptAction.UpdateClicked` (`ui/feature/update/model/UpdatePromptAction.kt`).
**Produces:** none.

- [x] Implement D6.
- [x] Add to `UpdatePromptOverlayFocusTest`: `updatePromptShownBeforeContentKeepsFocusOnUpdateAfterLateClaims` (host: `UpdatePromptOverlay(Available)` beside `ContentFocusGate(active = state == Hidden)`; content composes after the prompt with a `rememberFocusRequesterOnLaunch` target and a hero stand-in that requests focus on a later "publish" only when `LocalContentFocusActive.current` is true; Update stays focused 2 s after publish), `updatePromptShownOverFocusedContentTakesFocusWithinOneSecond` (content card focused, state switched to `Available`: Update focused before `mainClock` has advanced 1000 ms), `updatePromptUpdateButtonSendsUpdateClicked` (Center on focused Update: the recording `onAction` list equals `[UpdatePromptAction.UpdateClicked]`).
- [x] Run `UpdatePromptOverlayFocusTest` (4 tests); fast gate; commit.

**Done when:** `UpdatePromptOverlayFocusTest` 4/4 pass; fast gate green.

---

### Task 4: Gate the root screens while the update prompt is open

**Covers:** AC-1, AC-4, AC-5, AC-6, AC-8
**Files:**
- Modify: `ui/feature/root/component/App.kt` - D1: in `App()` (`:146`) `var updatePromptOpen by remember { mutableStateOf(false) }`; pass `screenFocusActive = !updatePromptOpen` to `FlowComponent` (`:153`); `UpdatePromptHost` (`:129-143`) takes `onOpenChange: (Boolean) -> Unit`, holds it with `rememberUpdatedState`, and calls it from `SideEffect` with `state != UpdatePromptViewState.Hidden`; call site `:169`.
- Modify: `core/ui/navigation/component/FlowComponent.kt` - D2: `screenFocusActive: Boolean = true` on `FlowComponent` (`:82`, before `content` at `:88`), passed at `:103-108` to `FlowNavigator` (`:113`); inside the provider (`:131-137`) wrap the `CurrentScreen` box (`:149-156`) in `ContentFocusGate(active = screenFocusActive)`. D8: in `FlowNavigator` next to `RootFlowReturnEffect` (`:166`), `LaunchedEffect(screenFocusActive)`: on false keep `contentFocusRequester.saveFocusedChild()` (`contentFocusRequester` at `:119`, same call as `:236`) in a remembered flag; on true, if the flag is set, `withFrameNanos {}`, clear it, `contentFocusRequester.restoreFocusedChild()` (as `:658`). No `requestFocus()` fallback.
- Modify: `ui/feature/main/component/MainScreenComponent.kt` - D3: in `DrawerMainContent` (`:88`) read `val outerActive = LocalContentFocusActive.current`; provide `outerActive && drawerState.currentValue != DrawerValue.Open` at `:138`. D7: `RailClosedBackHandler` (`:160`) and `RailOpenBackHandler` (`:179`) take `outerActive: Boolean`, ANDed into `enabled` at `:167` and `:186`; call sites `:108`, `:148`.

**Consumes:** `ContentFocusGate` (Task 1); `UpdatePromptViewState.Hidden` (`ui/feature/update/model/UpdatePromptViewState.kt:9`).
**Produces:** `FlowComponent(..., screenFocusActive: Boolean = true, content)`.

- [x] Implement D1, D2 wiring, D8, D3, D7.
- [x] Run the fast gate, then the full gate, then the instrumented classes of Tasks 1-3 and `ui.feature.showall.content.ShowAllRailCloseFocusTest`; commit.

**Done when:** full gate green (AC-10); the listed instrumented classes pass. AC-1 on device, AC-4, AC-5, AC-6, AC-8 and AC-9 for `ApiDomainDialog` are settled by qa-device runs M1-M4 (spec, Test design, Manual checks).

---

## Implementation record

<!-- owner: implementer -->

**Commits:**

| sha | subject |
|---|---|
| 17334922 | Stop a scheduled launch focus request when its gate closes and add ContentFocusGate |
| d845c534 | Keep D-pad moves inside TvDialogOverlay |
| e0e857c6 | Retry the update prompt's initial focus claim until it lands |
| 0f7c81b5 | Gate the root screens while the update prompt is open |

**Red run** (P0 `red` tests on the unchanged code, `make DEVICE=emulator-5554 GRADLE=./tools/agentw itest TESTS=<class>`):

- red: `launchRequestScheduledBeforeGateClosesDoesNotTakeFocus` - `Failed to assert the following: (Focused = 'false')`.
- red: `updatePromptDpadInAllDirectionsStaysInsidePrompt` - `Focus left the prompt on press #0 (Up): [left]` (also red at `TvDialogOverlay` level: `tvDialogOverlayDpadDoesNotLeaveToContentBeneath` - `Focus left the dialog on Up: [top]`). Its first form (re-keying the prompt per direction, then `requestFocus` on a text node) was red for the wrong reason and was rewritten as one composition and a fixed key sequence.
- `contentFocusGateKeepsOuterInactiveValue`, `updatePromptShownBeforeContentKeepsFocusOnUpdateAfterLateClaims`: fail to compile on base (`ContentFocusGate` is new); green after Task 1.
- green on base: `tvDialogOverlayClearFocusStillLeavesTextField` - guard row. It needs a second focusable inside the dialog, else the window gives focus straight back to the only field.

**Deviations from the plan:** none in design. `updatePromptShownOverFocusedContentTakesFocusWithinOneSecond` is green with the retry loop only; it does not discriminate the single-attempt base (the button is attached by then in the test), as its test-design row already says (guard).

**Files touched outside the plan:** none.

**For the reviewers:**

- `HistoryScreenContentTest` has 10 failures that are identical on base (checked with the D4 change stashed); not caused by this run.
- detekt prints "7 compiler errors found during analysis" in `detektDevDebugUnitTest`; the task passes.
- AC-1 on device, AC-4, AC-5, AC-6, AC-8 and `ApiDomainDialog` (AC-9) are unverified here: no device checks M1-M4 were run (qa-device's). Gate wiring in `App.kt`, `FlowComponent.kt` and `MainScreenComponent.kt` has no instrumented test; `DrawerMainContent` is private.
- `GateFocusRestoreEffect` (D8) saves the focused child when the gate closes and restores it one frame after it opens; no `requestFocus()` fallback.
- check.full: `BUILD SUCCESSFUL in 4m 19s` (testDevDebugUnitTest, detektAll, lintDevDebug). Instrumented: ContentFocusGateTest (4), TvDialogOverlayFocusTrapTest (2), UpdatePromptOverlayFocusTest (4), ShowAllRailCloseFocusTest, TvContextMenuLongPressTest, BookmarksScreenContentFocusTest: 21 tests, 0 failures.

**Blocked:** none. The emulator lease (emulator-5554) was released after the runs.

### Fix round 1 (verify round 1: QA defects 1 and 2)

| Finding | Status | Commit |
|---|---|---|
| Defect 1: cold start, Update never focused, D-pad moves Home beneath | fixed | b3307eb7 |
| Defect 2: dismiss leaves focus on the opened rail | fixed | 59a2ebb8 |

Root cause, found on emulator-5554 with temporary logging (not committed; the lowered `currentVersion` was restored): the prompt did take focus (`requestFocus()` returned true at about +0.4 s) and lost it about 2.4 s later to `ContentFocusHandoffEffect`. Main starts an initial handoff (`ModalNavigationDrawer.kt:199`, `beginInitialHandoff`) that retries `restoreFocusedChild()`/`requestFocus()` on the tab content every frame for up to 5 s until the content holds focus. It reads neither `LocalContentFocusActive` nor the root gate, so the first focusable Home published under the prompt took focus, and the QA runs that saw the prompt over a loaded Home were this same steal arriving late. The harness lacked a handoff, so the instrumented tests passed. Had the 5 s budget run out, `failHandoff` would also have opened the rail.

Dismissal (defect 2, also reproduced from Device settings): removing the focused prompt node sends focus to the rail, which reveals it; `restoreFocusedChild()` in D8 then returned false (`gate restore=false` in the log).

Fix:
- `ContentFocusHandoffEffect` reads `LocalContentFocusActive` and is keyed on it: while the gate is closed it neither requests focus nor spends its timeout, and it restarts with a fresh budget when the gate opens.
- `DrawerMainContent` starts a handoff (`beginInitialHandoff`) when `outerActive` turns false. The handoff stays pending under the prompt; when the prompt node is removed, the rail's existing HandingOff bounce guard (`redirectFocusToContent`) sends focus into the content, whose `focusRestorer` places it, and the handoff settles. D8 stays; on the device its restore returns false and the handoff does the work.

Red run:
- red: `initialHandoffWaitsWhileContentIsGatedThenFocusesContent` - `Failed to assert the following: (Focused = 'true')` on the dialog node (the handoff took focus when the content published). Its first form (dialog focused after the content published) was red for the wrong reason (the handoff had already settled) and was reordered.
- guard (green on base): `dialogDismissedOverFocusedContent_returnsFocusToContentNotTheRail` - the harness repeats Main's one-line effect, so it guards the handoff contract, not Main's wiring; `DrawerMainContent` is private. The wiring is covered by the device runs below.

Test harness repair: the two existing tests of `InitialDrawerContentFocusTest` used zero-size focusables, which Compose does not focus; `initiallyClosedDrawer_focusesContentThatAppearsAfterLaunch` failed on the emulator for that reason before this round. Boxes now have a size; assertions unchanged.

Device verification (evidence build, `currentVersion` 1.12.1 uncommitted and restored, Update never pressed):
- Cold start x2: Update focused at +0.4 s and stays 15 s; Left/Up/Right/Down stay on Update/Not now; Back dismisses, rail closed, focus on the Home hero, next Down moves within Home; Not now: same.
- Device settings, Check for updates now, Not now: focus on the settings section list (visible settings item), rail closed, Down/Right move within settings. Not the exact row the check started from.
- Committed build 1.12.3-dev, no prompt: hero focused, Back opens the rail, Back again returns to the hero.
- Instrumented (6 classes, 20 tests): InitialDrawerContentFocusTest, MainSideMenuFocusTraversalTest, ShowAllRailCloseFocusTest, UpdatePromptOverlayFocusTest, ContentFocusGateTest, TvDialogOverlayFocusTrapTest, all pass.
- check.fast: `BUILD SUCCESSFUL`. check.full not run (fix mode).

Environment: the emulator process died during the round (no qemu, adb empty); it was restarted with `emulator -avd puber_tv_36 -no-window -read-only` under the held lease; the app and its data were intact.

### Review fix round 1 (triage rows 1, 2, 3, 5, 6, 7)

| row | fix | sha |
|---|---|---|
| 1, 2 | `UpdatePromptOverlay.kt`: Checking and Downloading wrap their content in a `FocusHolder` (focusable, no key handling) that `primaryFocusRequester` points to; the Checking skip of the claim is gone. No new action or string. | cf8938fb |
| 3 | Details `ActionRow`, Player controls/bare-video effects, `ErrorOverlay` and `ResumeDialog` read `LocalContentFocusActive`, key their effect on it and return early while it is false. Cold start with the Player as top root screen: it sits under the root gate (`FlowComponent`), so it waits for the prompt and claims on dismissal; with no prompt the local is true and nothing changes. | 0fe2ffd7 |
| 5, 6, 7 | `ContentFocusGateTest` reopens the gate and asserts focus; the D-pad test asserts Later after Right and Update after the last Left; new retry test. | 87842639 |

**Red run** (`make DEVICE=emulator-5554 GRADLE=./tools/agentw itest`):

- red: `updatePromptKeepsFocusInsideWhenUpdateStartsTheDownload` - `Focus left the prompt: [top]`
- red: `updatePromptCheckingTakesFocusFromContentBeneath` - no focused node in the prompt (`assertExists` failed)
- red: `PlayerLaunchFocusGateTest.errorOverlayWaitsForTheGateBeforeTakingFocus`, `resumeDialogWaitsForTheGateBeforeTakingFocus` - a node was focused while the gate was closed
- red (mutation): `updatePromptRetriesTheFocusClaimUntilTheButtonCanTakeIt` fails with `FocusAttempts = 1` (no node holds focus after the gate opens), passes with 16. A parent `onEnter { cancelFocusChange() }` stands in for the late attach: `canFocus = false` and an unplaced child did not stop the first claim.
- green on base: the extended `ContentFocusGateTest` re-arm assertion and the D-pad Later/Update assertions; they pin behaviour the base already had (guards).
- No test for the Details `ActionRow` claim: the composable is private and the screen needs a full ViewModel state. Its evidence is the content-link launch that qa adds to M1.

**Runs:** `UpdatePromptOverlayFocusTest`, `ContentFocusGateTest`, `PlayerLaunchFocusGateTest`, `PlayerPromptFocusAndKeysTest`, `TvDialogOverlayFocusTrapTest`, `InitialDrawerContentFocusTest`: 29 tests green on emulator-5554 (lease released). check.fast green; check.full is the conductor's.

**Look at:** the Downloading focus holder is plain `focusable()` with no focus indicator; on device, check that Available -> Downloading keeps focus in the prompt without a bounce into the rail.

## Readiness

### Readiness 1: update-dialog-focus-trap

Verdict: **READY**

Inputs: spec and plan in the worktree at `fc047533`; the worktree has no code change, so the plan's line numbers are checked against the code at `0bc3ab0e`.

#### AC coverage

| AC | Plan task | Test design row |
|---|---|---|
| AC-1 | 1, 3, 4 | `launchRequestScheduledBeforeGateClosesDoesNotTakeFocus`, `contentFocusGateKeepsOuterInactiveValue`, `launchRequestReArmsWhenGateOpens`, `updatePromptShownBeforeContentKeepsFocusOnUpdateAfterLateClaims`, M1 |
| AC-2 | 3 | `updatePromptShownOverFocusedContentTakesFocusWithinOneSecond`, M3 |
| AC-3 | 2 | `updatePromptDpadInAllDirectionsStaysInsidePrompt`, `tvDialogOverlayDpadDoesNotLeaveToContentBeneath`, M1 |
| AC-4 | 4 | M1 step 6, M2 step 5 |
| AC-5 | 4 | M1 steps 3, 5 |
| AC-6 | 4 | M1 step 7, M2 steps 5-6 |
| AC-7 | 3 | `updatePromptUpdateButtonSendsUpdateClicked` |
| AC-8 | 4 | M3 |
| AC-9 | 2 | `TvContextMenuLongPressTest`, `BookmarksScreenContentFocusTest`, `tvDialogOverlayClearFocusStillLeavesTextField`, M4 |
| AC-10 | 4 | check.full |

#### Citations sampled (at `0bc3ab0e`)

| Citation | Resolves to | Result |
|---|---|---|
| `FocusOnLaunchRequester.kt:23`, `:30`, `:40`, `:43`, `:47`, `:58-62` | the two `staticCompositionLocalOf` locals, `FOCUS_ON_LAUNCH_DELAY_MILLIS`, `rememberFocusRequesterOnLaunch`, `autoFocusEnabled`, the delayed request that checks only `isFocusRequested` | ok |
| `TvDialogOverlay.kt:17`, `:27`, `:43-45`, `:50` | `DialogScrimAlpha`, `TvDialogOverlay`, `BackHandler`, `.focusGroup()` | ok |
| `UpdatePromptOverlay.kt:43`, `:56-61` | `FocusDelayMs`, the single `runCatching { requestFocus() }` | ok |
| `TvContextMenu.kt:77-91` | the retry loop | ok |
| `FlowComponent.kt:82`, `:103-108`, `:113`, `:119`, `:131-137`, `:149-156`, `:166`, `:236`, `:658`, `:719` | `FlowComponent`, `FlowNavigator` call and declaration, `contentFocusRequester`, the provider, the `CurrentScreen` box, `RootFlowReturnEffect`, `saveFocusedChild`, `restoreFocusedChild`, `TabBackHandler` `enabled` | ok |
| `MainScreenComponent.kt:88`, `:95`, `:108`, `:138`, `:148`, `:160` | `DrawerMainContent`, launch requester, both handler call sites, the rail-only provider, `RailClosedBackHandler` | ok |
| `MainScreenComponent.kt:167`, `:186` (plan Task 4, spec D7) | the `BackHandler(enabled = ...)` lines are `:166` and `:185` | off by one; the enclosing function names make the target unambiguous |
| `App.kt:129-143`, `:146`, `:153`, `:169` | `UpdatePromptHost`, `App`, `FlowComponent` call, `UpdatePromptHost()` call | ok |
| `DeviceSettingsContent.kt:300-304`, `PlayerSidePanel.kt:142` | `onExit` with `requestedFocusDirection`; `cancelFocusChange()` | ok |
| `ShowAllRailCloseFocusTest.kt:57-60`, `:243`; `BookmarksScreenContentFocusTest` `:40`, `:72` | `createComposeRule()` from `junit4.v2`; the gated provider; both named tests | ok |
| `AvailableUpdate.kt:3`, `AppVersion.kt:3`, `UpdatePromptViewState.kt:9`, `UpdatePromptVM.kt:61`, `strings.xml` `update_prompt_update` / `update_prompt_not_now` | the fixture's constructors (all nine fields match), `Hidden`, `UpdateClicked` handling, button texts | ok |

#### Order and build

| Check | Result |
|---|---|
| Task order | 1 (`ContentFocusGate`, D4) -> 2 (trap) -> 3 (claim; test consumes `ContentFocusGate`) -> 4 (wiring). Each consumes only earlier output. |
| Build red between tasks | No: Task 1 adds an `internal` composable used only by tests until Task 4; new `FlowComponent` parameter has a default, so `DeviceSettingsFlowScreen.kt:27` compiles unchanged; detekt `LongParameterList` is inactive. |
| Instrumented runs | `make DEVICE=<serial> GRADLE=./tools/agentw itest TESTS=...` matches `Makefile:23`, `:128-136`; no `./gradlew` in the worktree. |
| Red first | `launchRequestScheduledBeforeGateClosesDoesNotTakeFocus` and `updatePromptDpadInAllDirectionsStaysInsidePrompt` use base API only and run before any production edit (Task 1). |

#### Test design P0 rows

Automated P0 rows each name the mutation they catch, carry `red` or `guard`, and name the test in backticks. Device P0 rows (M1-M4) name their failure under "Must not happen" and are manual by nature; AC-10 is the check.full command.

#### Scope and questions

The plan stays inside the spec's In list: no window `Dialog`, no general root-modal API, `currentVersion` left alone. Q1-Q4 answered (spec, Open questions); none blocks.

#### Notes for the implementer (not blocking)

1. Use `MainScreenComponent.kt:166` and `:185` for the D7 `enabled` edits.
2. Task 1 leaves `UpdatePromptOverlayFocusTest.kt` uncommitted for Task 2: stage files by name in the Task 1 commit.
3. D1, D2 wiring, D3, D7 and D8 have no automated test (`DrawerMainContent` is private); M1 and M2 are their only evidence, so qa-device must record per run which order (prompt before or after Home) was seen.