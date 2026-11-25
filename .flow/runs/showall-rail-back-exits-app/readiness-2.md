# Readiness 2: showall-rail-back-exits-app

Final judge, 2026-11-25. Base f88f3079. Inputs: spec (commit 5dce4155) and plan (untracked) in the worktree, `test-design.md`, `design-architect.md`, `readiness-1.md`.

Verdict: **READY**

## Readiness-1 fixes

| Fix | Result |
|---|---|
| 1. Nothing-focusable screens after the `onEnter` gate | closed: spec Decisions "Left with nothing focused" (unhandled-Left listener), design Risk 1, test-design R11, P0 red `MainTabPushRailFocusTest.leftWithNothingFocused_opensRailAndBackReturnsToContent`, M8 step 6, M11 step 5 with expected results; plan Task 4 change 2 and D4. The 5 s handoff reopen with nothing focusable is Open question 1, non-blocking, deferred to run `rail-handoff-nothing-focusable` (folder exists) |
| 2. Device rows M7, M8 at P0 | closed: both P1, each names the defect it catches |

## Checks

| Check | Result |
|---|---|
| Every AC has a plan task and a Test design row | pass: plan Coverage maps AC-1..AC-11, R2, R3 to Tasks 1-4 and to Traceability rows; R11 rows in Task 1 and Task 4 |
| Citations resolve (sampled 16) | pass, see below |
| No task leaves the build red; order by dependency | pass: Task 1 touches visibility plus androidTests (outside `check.full`); Task 2 self-contained; Task 3 consumes Task 2; Task 4 gated on D1 with the Q3 fallback |
| P0 rows: defect or mutation, `red`/`guard`, automated test in backticks | pass |
| Inside Out of scope; blocking questions answered | pass: Q1, Q2 answered; Q3 answered with a D1 decision row; no visual change |

## Citations sampled (worktree at base)

| Citation | Resolves to |
|---|---|
| `MainScreenComponent.kt:89-94` | `private fun DrawerMainContent(state, onAction, tabRouter, tabAppRouterHolder)` |
| `MainScreenComponent.kt:199-208` | RailOpen `BackHandler`, `HandingOff` return, `beginHandoff`, `arm()`, toast |
| `MainScreenComponent.kt:228-230,245` | `fallbackFocusItem` (selected tab), `.focusRestorer(fallbackFocusItem)` |
| `ModalNavigationDrawer.kt:87`, `:115-119` | `currentValue`; `reveal()` Closed to Open |
| `ModalNavigationDrawer.kt:209-221` | `DrawerSheet` composed before `content()` |
| `ModalNavigationDrawer.kt:230,240-245,273-288` | `DrawerSheet`; Open-state `requestFocus`; `onFocusChanged` with `redirectFocusToContent` and `reveal()`; `.focusGroup()` |
| `ContentFocusHandoff.kt:24`, `:120-137` | `HandoffTimeoutMillis = 5_000L`; timeout loop, blind `requestFocus` (:127-129), `handoff.fail` (:137) |
| `FlowComponent.kt:83`, `:502-504` | `FlowComponent(scopeName, …)`; `CurrentScreen` composes `navigator.lastItem` only |
| `MainActivity.kt:36`, `:59-66` | `MainActivity : ComponentActivity`; `dispatchKeyEvent` calls `super.dispatchKeyEvent` |
| `ShowAllScreenContent.kt:80-87`, `:215` | `filtersCanFocus` false until the first list; `rememberFocusRequesterOnLaunch()` |
| `BrowseAllEntry.kt:53-67` (note :59-60) | entry focus retry; "the side rail, which Android focuses when the grid leaves" |
| `MainViewState.kt:8,15`; `PuberScreen.kt:12` | `tabs`, `MainTab.type`; `interface RootPuberScreen` |
| `MainSideMenuFocusTraversalTest.kt:260-272`; `TabFlowLifecycleTest.kt:88-101` | `testTabs` fixtures; `FlowComponent` with `ProbeMainHostScreen` setup |
| `gradle/libs.versions.toml:5`; `Makefile:23` | `coreKtx = "1.19.1"`; `GRADLE ?= ./gradlew` |

Existing tests named in AC-9, AC-11 and Traceability (`ShowAllFilterFocusTest`, `showAllMenuOpenAndBackReturnsFocusToTheOpeningCard`, `leftFromFilters_opensTheRail`, `dialogDismissedOverFocusedContent_returnsFocusToContentNotTheRail`) exist in `app/src/androidTest`.

## Notes for the implementer and qa (not blocking)

| # | Note |
|---|---|
| 1 | The unhandled-Left path below API 28 (`androidx.core` `KeyEventDispatcher`, minSdk 24) has no test; the box covers only its own API level. Record the box API level in the D4 evidence |
| 2 | `leftWithNothingFocused_…` is red on base only through its precondition; the listener itself is proven by the post-fix mutation named in its row. Report it in the Red run as such |
| 3 | The plan file is untracked in the worktree; commit it with Task 1 or before |
| 4 | Carried from readiness-1: a `guard` row that fails on base at D1 is reported as a deviation before Task 3 |
