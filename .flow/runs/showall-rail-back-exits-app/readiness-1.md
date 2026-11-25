# Readiness 1: showall-rail-back-exits-app

Final judge, 2026-11-25. Base f88f3079. Inputs: spec and plan in the worktree (`docs/superpowers/specs|plans/2026-11-25-showall-rail-back-exits-app.md`), `test-design.md`.

Verdict: **NOT READY**

## Checks

| Check | Result |
|---|---|
| Every AC has a plan task and a Test design row | pass: plan Coverage maps AC-1..AC-11, R2, R3 to Tasks 1-4 and to Traceability rows |
| Citations resolve (sampled 12) | pass, see below |
| No task leaves the build red; order by dependency | pass: Task 1 changes only visibility plus androidTests (not in `check.full`); Task 2 tests and symbols in one commit; Task 3 consumes Task 2; Task 4 gated on D1 |
| P0 rows: defect or mutation, `red`/`guard`, automated test in backticks | fail: two device rows, fix 2 |
| Inside Out of scope; blocking questions answered | pass: Q1, Q2 answered; Q3 non-blocking with a D1 decision row |
| Design risks covered by a check | fail: fix 1 |

## Citations sampled

| Citation | Resolves to |
|---|---|
| `MainScreenComponent.kt:89` | `private fun DrawerMainContent(` |
| `MainScreenComponent.kt:112-115` | `CompositionLocalProvider(LocalDrawerState …, LocalContentFocusHandoff …)` |
| `MainScreenComponent.kt:119`, `:159` | `RailClosedBackHandler(...)`, `RailOpenBackHandler(...)` calls |
| `MainScreenComponent.kt:179-185`, `:199-208` | RailClosed `finish()`/`reveal()`/`disarm()`; RailOpen HandingOff return, `beginHandoff`, `arm()`, toast |
| `FlowComponent.kt:749-757` | `TabBackHandler`, `BackHandler(enabled = navigator.canPop)` |
| `FlowComponent.kt:577,597,598-609` | `TabFlowNavigator`, `Navigator { navigator ->`, `DisposableEffect(tabHost, navigator)` |
| `FlowComponent.kt:782-786` | `saveFocusedChild()` then `navigator.puberPush` |
| `TabComponent.kt:41-52` | `internal class TabFlowSession` |
| `ModalNavigationDrawer.kt:52,55-68,87` | `LocalDrawerState`, `enum class DrawerValue`, `currentValue` |
| `ModalNavigationDrawer.kt:240-245,281-284,288` | Open-state `requestFocus`, `redirectFocusToContent`, `reveal()`, `.focusGroup()` |
| `ExitConfirmation.kt:12,39-45`; `ContentFocusHandoff.kt:24`; `FocusOnLaunchRequester.kt:61,64`; `TabRouter.kt:19`; `Makefile:23` | window 3_000L, `arm`/`disarm`; `HandoffTimeoutMillis = 5_000L`; `rememberFocusRequesterOnLaunch`, `isDrawerOpen`; `openTab`; `GRADLE ?= ./gradlew` |
| `PlayerPromptFocusAndKeysTest.kt:48,160`; `TabFlowLifecycleTest.kt:189-218` | compose rule, `onBackPressedDispatcher.onBackPressed()`; `ProbeMainHost`, `ProbeMainHostScreen` |

Existing test names cited in AC-9 and AC-11 (`panelBack_closesWithFocusOnFilters`, `showAllMenuOpenAndBackReturnsFocusToTheOpeningCard`, `leftFromFilters_opensTheRail`, `dialogDismissedOverFocusedContent_returnsFocusToContentNotTheRail`, `ExitConfirmationTest`, `DrawerStateTest`) exist in `app/src/{test,androidTest}`.

## Fix list

1. The `onEnter` gate (Task 4) also changes screens that stay with nothing focusable, not only the Show all `Loading` gap of design Risk 1 / R7: an empty tab (empty Bookmarks or History) or an error state without a button. On base focus falls to the rail; with the gate nothing is focused, and Left reaches the rail only through the framework's default-focus restore, which enters with a non-Left direction and is refused. Add a risk row and a check (M11 step or an instrumented probe: tab root with no focusable node, Left then Back) with the expected result, or a design decision that accepts it.
2. Test design rows `AC-7 device P0 M7` and `AC-8 device P0 M8` name no defect or mutation and no automated test. Name the defect each catches (lost root arm; rail reveal on a non-Left entry) or lower them below P0, since the instrumented P0 rows already carry those ACs.

## Notes for the implementer (not blocking)

- D1 has no decision row for a `guard` row that fails on base (for example `backWithRailClosedOnPushedScreen_popsToTabRoot_inEveryHistory`, if a pop on base lets focus fall to the rail). Report it as a deviation before Task 3.
- `make itest` depends on `build`; pass `GRADLE=./tools/agentw` as the plan does, or the guard hook refuses it.
