# Readiness 1: update-dialog-focus-trap

Verdict: **READY**

Inputs: spec and plan in the worktree at `fc047533`; the worktree has no code change, so the plan's line numbers are checked against the code at `0bc3ab0e`.

## AC coverage

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

## Citations sampled (at `0bc3ab0e`)

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

## Order and build

| Check | Result |
|---|---|
| Task order | 1 (`ContentFocusGate`, D4) -> 2 (trap) -> 3 (claim; test consumes `ContentFocusGate`) -> 4 (wiring). Each consumes only earlier output. |
| Build red between tasks | No: Task 1 adds an `internal` composable used only by tests until Task 4; new `FlowComponent` parameter has a default, so `DeviceSettingsFlowScreen.kt:27` compiles unchanged; detekt `LongParameterList` is inactive. |
| Instrumented runs | `make DEVICE=<serial> GRADLE=./tools/agentw itest TESTS=...` matches `Makefile:23`, `:128-136`; no `./gradlew` in the worktree. |
| Red first | `launchRequestScheduledBeforeGateClosesDoesNotTakeFocus` and `updatePromptDpadInAllDirectionsStaysInsidePrompt` use base API only and run before any production edit (Task 1). |

## Test design P0 rows

Automated P0 rows each name the mutation they catch, carry `red` or `guard`, and name the test in backticks. Device P0 rows (M1-M4) name their failure under "Must not happen" and are manual by nature; AC-10 is the check.full command.

## Scope and questions

The plan stays inside the spec's In list: no window `Dialog`, no general root-modal API, `currentVersion` left alone. Q1-Q4 answered (spec, Open questions); none blocks.

## Notes for the implementer (not blocking)

1. Use `MainScreenComponent.kt:166` and `:185` for the D7 `enabled` edits.
2. Task 1 leaves `UpdatePromptOverlayFocusTest.kt` uncommitted for Task 2: stage files by name in the Task 1 commit.
3. D1, D2 wiring, D3, D7 and D8 have no automated test (`DrawerMainContent` is private); M1 and M2 are their only evidence, so qa-device must record per run which order (prompt before or after Home) was seen.
