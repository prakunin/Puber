# Back and focus around the side rail on Show all grids - plan

**Spec:** `docs/superpowers/specs/2026-11-25-showall-rail-back-exits-app.md`
**Goal:** Make exactly one Main-level Back handler enabled in any state, so Back with the rail open over a pushed tab screen closes the rail without arming the exit, and keep a closed rail from taking focus it did not ask for.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin`; full gate: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`.
- androidTest compile: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugAndroidTestKotlin`. Unit tests of a task: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests '<fqcn>'`.
- Never `./gradlew` in the worktree `/home/dev/orca/workspaces/Puber/showall-rail-back-exits-app`.
- One task = green fast gate + green tests of the touched code + one commit, message one imperative English sentence, no prefixes, never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- Paths without a prefix are under `app/src/main/java/com/kino/puber/`; `androidTest/` is `app/src/androidTest/kotlin/com/kino/puber/`; `test/` is `app/src/test/kotlin/com/kino/puber/`. Lines are at base f88f3079.
- The implementer has no device. Instrumented runs and manual checks are qa-device's (Device work); a task whose `Done when` names a D row is finished by the implementer at its commit and confirmed by that row.
- Out of scope: `rail-content-mismatch-rapid-back`; any visual change to the rail or the grid.

---

## Tasks

### Task 1: Expose the Main drawer host to tests and add the red instrumented tests

**Covers:** AC-1 to AC-10 (instrumented rows), R2
**Files:**
- Modify: `ui/feature/main/component/MainScreenComponent.kt:89` - `private fun DrawerMainContent` becomes `internal`; nothing else.
- Create: `androidTest/ui/feature/main/component/MainHostHarness.kt` - shared harness (below).
- Create: `androidTest/ui/feature/main/component/MainRailBackTest.kt` - 10 tests.
- Create: `androidTest/ui/feature/main/component/MainTabPushRailFocusTest.kt` - 5 tests.

**Consumes:** `DrawerMainContent(state: MainViewState, onAction, tabRouter: TabRouter, tabAppRouterHolder: TabAppRouterHolder)` (`MainScreenComponent.kt:89-94`); `MainViewState`, `MainTab` (`ui/feature/main/model/MainViewState.kt:8,15`), tab fixtures as in `androidTest/ui/feature/main/component/MainSideMenuFocusTraversalTest.kt:260-272`; `FlowComponent` (`core/ui/navigation/component/FlowComponent.kt:83`) set up as in `androidTest/core/ui/navigation/component/TabFlowLifecycleTest.kt:88-101`; probe host modelled on `ProbeMainHost` / `ProbeMainHostScreen` (`TabFlowLifecycleTest.kt:189-218`); `TabRouter.openTab` (`core/ui/navigation/TabRouter.kt:19`); `LocalDrawerState` (`core/ui/uikit/component/drawer/ModalNavigationDrawer.kt:52`); `rememberFocusRequesterOnLaunch` (`core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:61`); Back as `rule.activity.onBackPressedDispatcher.onBackPressed()` on the UI thread (`androidTest/ui/feature/player/component/PlayerPromptFocusAndKeysTest.kt:48,160`).
**Produces:** `MainHostHarness` used by both test classes; `internal fun DrawerMainContent`.

Harness (`MainHostHarness.kt`):

| Need | How |
|---|---|
| Rule | `createAndroidComposeRule<ComponentActivity>()` |
| Host | `FlowComponent` root whose probe host screen composes the real `DrawerMainContent` with a two-tab `MainViewState` and a no-op `onAction`; the host captures `LocalDrawerState.current` |
| Probe tab root | focusable cards (first one claims focus with `rememberFocusRequesterOnLaunch`), a card that pushes the probe child in the tab, a card that root-pushes a probe Details (`RootPuberScreen`) |
| Probe child | grid of focusable cards with `rememberFocusRequesterOnLaunch` on the first, as `ui/feature/showall/content/ShowAllScreenContent.kt:215`; optional `Loading` phase with nothing focusable, ended by the test; optional own `BackHandler` that records consumption |
| Cold-start order | `tabRouter.openTab` only after the first `setContent` frame, so the tab composes a pass after Main |
| Tab-switch order | `openTab(other)` then `openTab(first)` |
| Details-return order | root push of the probe Details, then Back |
| Rail open | D-pad Left via `performKeyInput` from a focused card |
| Left with nothing focused | `InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT)` off the UI thread: the real input pipeline. `performKeyInput` goes through Compose and never reaches the view's unhandled-key path (design Risk 7). Precondition asserted first: drawer `Closed` and `onAllNodes(isFocused())` empty |
| Exit | `rule.activity.isFinishing` |
| Rail state | captured `drawerState.currentValue`; `waitUntil` Closed after a close; `MainTabPushRailFocusTest` records the value on every frame (`withFrameNanos` loop) |

The toast is not asserted; an arm is observed as the exit it allows.

Tests (names exact; expectations per `.flow/runs/showall-rail-back-exits-app/test-design.md` Traceability):

| Class | Test | Base |
|---|---|---|
| `MainRailBackTest` | `railOpenOnPushedScreen_backClosesRailAndKeepsScreen_afterColdStart` | red |
| `MainRailBackTest` | `railOpenOnPushedScreen_backClosesRailWithoutArmingExit_afterDetailsRoundTrip` | red |
| `MainRailBackTest` | `railOpenOnPushedScreen_backClosesRailAndKeepsScreen_afterTabSwitch` | red |
| `MainRailBackTest` | `backWithRailClosedOnPushedScreen_popsToTabRoot_inEveryHistory` | guard |
| `MainRailBackTest` | `fiveBacksFromRailOverPushedScreen_exitOnlyOnFifth` | red |
| `MainRailBackTest` | `armedAtRoot_thenPushAndPop_nextBackOpensRailNotExit` | red |
| `MainRailBackTest` | `backAtTabRoot_opensRailThenArmsThenExits` | guard |
| `MainRailBackTest` | `screenBackHandlerOnPushedScreen_winsOverMainHandlers` | guard |
| `MainRailBackTest` | `backOnRootPushedDetails_popsToMain` | guard |
| `MainRailBackTest` | `backAtOtherTabRoot_whileFirstTabHasPushedScreen_opensRail` | guard |
| `MainTabPushRailFocusTest` | `pushIntoLoadingScreen_railStaysClosedAndFirstCardFocusedOnceLoaded` | red |
| `MainTabPushRailFocusTest` | `pushWithContentReady_railStaysClosed` | red |
| `MainTabPushRailFocusTest` | `pushAfterDetailsRoundTrip_railStaysClosed` | red |
| `MainTabPushRailFocusTest` | `leftFromPushedGrid_opensRail` | guard |
| `MainTabPushRailFocusTest` | `leftWithNothingFocused_opensRailAndBackReturnsToContent` | red (precondition: on base the rail is already `Open` after the push) |

- [x] Make `DrawerMainContent` internal
- [x] Write the harness and the 15 tests above
- [x] Run the fast gate and the androidTest compile; commit "Add instrumented tests for Back and focus around the rail over a pushed tab screen"
- [ ] Hand off to D1; do not start Task 3 before D1's outcome is recorded

**Done when:** both gradle commands succeed; D1 shows the `red` rows failing with the base results of the Failing first table in `test-design.md` and the `guard` rows passing.

### Task 2: Add the Back owner rule and the tab stack state

**Covers:** design rule (`brief.md` Intended Back), R2
**Files:**
- Create: `core/ui/navigation/component/RailBackOwner.kt`
- Modify: `core/ui/navigation/component/TabComponent.kt` - add `TabStackState` and `LocalTabStackState` after `TabFlowSession` (`:41-52`)
- Test: `test/core/ui/navigation/component/RailBackOwnerTest.kt`, `test/core/ui/navigation/component/TabStackStateTest.kt` (JUnit 5)

**Consumes:** `DrawerValue` (`core/ui/uikit/component/drawer/ModalNavigationDrawer.kt:55-68`).
**Produces:**

```kotlin
internal enum class RailBackOwner { RailClosed, Tab, RailOpen }

// Open, HandingOff -> RailOpen; Closed -> Tab if tabCanPop else RailClosed; null -> Tab if tabCanPop else null
internal fun railBackOwner(drawer: DrawerValue?, tabCanPop: Boolean): RailBackOwner?

@Stable
internal class TabStackState {
    val canPop: Boolean               // false when nothing is attached; reads the provider, so snapshot reads inside it are tracked
    fun attach(owner: Any, canPop: () -> Boolean)
    fun detach(owner: Any)            // clears only when owner is the attached one
}

internal val LocalTabStackState = staticCompositionLocalOf<TabStackState?> { null }
```

Owner and provider are held in `mutableStateOf` so a change of attachment recomposes readers.

| Class | Tests |
|---|---|
| `RailBackOwnerTest` | `railBackOwner_isRailOpen_whenDrawerOpenOrHandingOff`, `railBackOwner_isTab_whenDrawerClosedAndTabCanPop`, `railBackOwner_isRailClosed_whenDrawerClosedAndTabAtRoot`, `railBackOwner_isTabOrNull_withoutRailHost` |
| `TabStackStateTest` | `canPop_isFalse_whenNothingAttached`, `canPop_followsAttachedProvider`, `detach_byPreviousOwner_keepsNewAttachment` |

- [x] Write the 7 unit tests first; they fail to compile without the symbols
- [x] Add `RailBackOwner.kt`, `TabStackState`, `LocalTabStackState`
- [x] Run the fast gate and both test classes; commit "Add the rule that picks one Back owner between the rail and the tab stack"

**Done when:** `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.core.ui.navigation.component.RailBackOwnerTest' --tests 'com.kino.puber.core.ui.navigation.component.TabStackStateTest'` passes 7 tests.

### Task 3: Gate the three Main-level Back handlers by the owner rule

**Covers:** AC-1 to AC-7, AC-9, AC-10, R2
**Files:**
- Modify: `core/ui/navigation/component/FlowComponent.kt:577-657` `TabFlowNavigator` - inside the `Navigator` content (`:597`), next to the existing `DisposableEffect(tabHost, navigator)` (`:598-609`): `val tabStack = LocalTabStackState.current`; `DisposableEffect(tabStack, navigator) { tabStack?.attach(navigator) { navigator.canPop }; onDispose { tabStack?.detach(navigator) } }`
- Modify: `core/ui/navigation/component/FlowComponent.kt:749-757` `TabBackHandler` - `enabled = railBackOwner(LocalDrawerState.current?.currentValue, navigator.canPop) == RailBackOwner.Tab`; body unchanged
- Modify: `ui/feature/main/component/MainScreenComponent.kt:89-161` `DrawerMainContent` - `val tabStack = remember { TabStackState() }`; add `LocalTabStackState provides tabStack` to the `CompositionLocalProvider` (`:112-115`); pass `tabStack` to both handlers (`:119`, `:159`); add a private composable `ExitDisarmOnTabPush(tabStack, exitConfirmation)` doing `LaunchedEffect(tabStack.canPop) { exitConfirmation.disarm() }`, called inside the provider. Do not read `tabStack.canPop` in `DrawerMainContent`'s own scope (design Risk 5)
- Modify: `MainScreenComponent.kt:171-187` `RailClosedBackHandler(drawerState, tabStack, exitConfirmation, outerActive)` - `enabled = outerActive && railBackOwner(drawerState.currentValue, tabStack.canPop) == RailBackOwner.RailClosed`; body unchanged
- Modify: `MainScreenComponent.kt:191-210` `RailOpenBackHandler(drawerState, tabStack, exitConfirmation, outerActive)` - `enabled = outerActive && railBackOwner(drawerState.currentValue, tabStack.canPop) == RailBackOwner.RailOpen`; keep the `HandingOff` return (`:202`) and `beginHandoff` (`:204`); `arm()` and the toast (`:207-208`) only when `!tabStack.canPop`
- Modify comments: `MainScreenComponent.kt:116-118`, `:156-158` (registration order no longer decides; the owner rule enables one handler); `ui/feature/main/component/ExitConfirmation.kt:22-23` (armed only by a Back that closed the rail at a tab root)

**Consumes:** Task 2 `railBackOwner`, `RailBackOwner`, `TabStackState`, `LocalTabStackState`; `LocalDrawerState` (`ModalNavigationDrawer.kt:52`); `ExitConfirmation.arm/disarm` (`ExitConfirmation.kt:39-45`).
**Produces:** order-independent Back at Main level.

- [x] Apply the changes above
- [x] Run the fast gate, the androidTest compile and `ExitConfirmationTest`, `RailBackOwnerTest`, `TabStackStateTest`; commit "Enable only the Back handler that owns the current rail and tab stack state"

**Done when:** the gradle commands succeed; D2 shows all 10 `MainRailBackTest` tests and `TabFlowLifecycleTest` passing.

### Task 4: Keep a closed rail from taking focus it did not ask for, and let Left open it when nothing is focused

Run only when D1 recorded `pushIntoLoadingScreen_railStaysClosedAndFirstCardFocusedOnceLoaded` red with the drawer reaching `Open` while no Left was pressed. Otherwise do the Q3 fallback row of Device work instead.

**Covers:** AC-8, AC-11, R3, design Risk 1
**Files:**
- Modify: `core/ui/uikit/component/drawer/ModalNavigationDrawer.kt:230-295` `DrawerSheet`, two changes in one commit (the gate alone leaves Left dead on screens with nothing focusable):

1. Between `.onFocusChanged { … }` (`:273-287`) and `.focusGroup()` (`:288`):

```kotlin
.focusProperties {
    onEnter = {
        if (drawerState.currentValue == DrawerValue.Closed &&
            requestedFocusDirection != FocusDirection.Left
        ) cancelFocusChange()
    }
}
```

   Comment: a closed rail refuses entries other than Left, so focus that falls when a screen leaves does not reveal it; `Open` and `HandingOff` allow every entry so the rail's own request (`:240-245`) and the handoff redirect (`:281-282`) keep working.

2. After the `LaunchedEffect` (`:240-245`):

```kotlin
val view = LocalView.current
DisposableEffect(view, drawerState) {
    val listener = ViewCompat.OnUnhandledKeyEventListenerCompat { v, event ->
        if (event.action == KeyEvent.ACTION_DOWN &&
            event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT &&
            drawerState.currentValue == DrawerValue.Closed &&
            !v.hasFocus()
        ) {
            drawerState.reveal()
            true
        } else {
            false
        }
    }
    ViewCompat.addOnUnhandledKeyEventListener(view, listener)
    onDispose { ViewCompat.removeOnUnhandledKeyEventListener(view, listener) }
}
```

   Comment: with nothing focused a D-pad key reaches Compose only as the framework's default-focus restore, which enters as `FOCUS_DOWN` exactly like the refocus the gate exists to refuse; Left is the user asking for the rail, so it is taken from the keys no view handled, and the rail's own request in `Open` then focuses it. Imports: `android.view.KeyEvent`, `androidx.compose.runtime.DisposableEffect`, `androidx.compose.ui.platform.LocalView`, `androidx.core.view.ViewCompat`, `androidx.compose.ui.focus.FocusDirection`, `androidx.compose.ui.focus.focusProperties`.

**Consumes:** `DrawerState.currentValue` (`ModalNavigationDrawer.kt:87`), `DrawerState.reveal()` (`:115-119`); `ViewCompat.addOnUnhandledKeyEventListener` / `removeOnUnhandledKeyEventListener` (androidx.core 1.19.1, `libs.androidx.core.ktx`); `MainActivity.dispatchKeyEvent` keeps calling `super` (`MainActivity.kt:59-66`), which the listener needs below API 28.
**Produces:** nothing new.

- [x] Apply both changes
- [x] Run the fast gate and the androidTest compile; then the full gate; commit "Refuse non-Left focus entry into the closed side rail and open it on an unhandled Left"

**Done when:** fast and full gates succeed; D3 shows all 5 `MainTabPushRailFocusTest` tests, `MainRailBackTest` and the AC-11 androidTest classes passing.

---

## Device work

qa-device, box `192.168.1.110:5555` under a lease; master build and `make DEVICE=192.168.1.110:5555 auth-save` before the first worktree install (test-design R10). Instrumented runs from the worktree: `AGENT_GRADLE_SERIAL=1 make DEVICE=192.168.1.110:5555 GRADLE=./tools/agentw itest TESTS=<fqcn>` (the Makefile's default `GRADLE` is `./gradlew`, `Makefile:23`).

| # | After | Run | Expected | Decision |
|---|---|---|---|---|
| D1 | Task 1 commit | `com.kino.puber.ui.feature.main.component.MainRailBackTest`, `com.kino.puber.ui.feature.main.component.MainTabPushRailFocusTest` | `red` rows fail as in `test-design.md` Failing first; `guard` rows pass | AC-1 or AC-3 test passes: the harness composes in one pass; implementer fixes the harness (Task 1 amend), D1 repeats. Q3: `pushIntoLoadingScreen_…` passes, or fails without the drawer reaching `Open` by a non-Left entry: skip Task 4, delete `MainTabPushRailFocusTest.kt` in its own commit, conductor moves AC-8 to `showall-first-entry-rail-focus`; the Back fix ships alone |
| D2 | Task 3 commit | `MainRailBackTest`, `com.kino.puber.core.ui.navigation.component.TabFlowLifecycleTest` | all pass | a failure returns to Task 3 |
| D3 | Task 4 commit (or the fallback commit) | `MainTabPushRailFocusTest` (unless fallback), `MainRailBackTest`, `MainSideMenuFocusTraversalTest`, `core.ui.uikit.component.drawer.InitialDrawerContentFocusTest`, `ui.feature.showall.content.ShowAllRailCloseFocusTest`, `ShowAllScreenContentFocusTest`, `ShowAllFilterFocusTest`, `TabFlowLifecycleTest` | all pass | a failure returns to the task owning the file |
| D4 | D3 green | Manual M1-M11 of `test-design.md` on the worktree dev APK, installed with `tools/android-apk-install-preserve` | as `test-design.md` Manual checks; M8 steps 1-4 only when Task 4 shipped. With Task 4 shipped, also the nothing-focusable step of design Risk 1: on a tab root or pushed screen with nothing focusable (empty Bookmarks or History if the account has one, else Show all during `Loading`), Left, then Back; expected: Left expands the rail with the selected tab focused, Back collapses it once content can take focus, nothing focusable: At a tab root with nothing focusable the rail reopens after 5 s (`ContentFocusHandoff.kt:24,137`). Over a pushed screen (Show all during Loading or Error) a failed handoff ends Closed with no rail item focused, and the next Back pops to the root (A5) | evidence under `docs/superpowers/`, audited with `tools/mobile-evidence-audit.sh` |

## Coverage

| AC | Task | Tests (test-design row) |
|---|---|---|
| AC-1 | 1, 3 | `railOpenOnPushedScreen_backClosesRailAndKeepsScreen_afterColdStart`; M1 |
| AC-2 | 1, 3 | `railOpenOnPushedScreen_backClosesRailWithoutArmingExit_afterDetailsRoundTrip`; M2 |
| AC-3 | 1, 3 | `railOpenOnPushedScreen_backClosesRailAndKeepsScreen_afterTabSwitch`; M3 |
| AC-4 | 1, 3 | `backWithRailClosedOnPushedScreen_popsToTabRoot_inEveryHistory`; M4 |
| AC-5 | 1, 3 | `fiveBacksFromRailOverPushedScreen_exitOnlyOnFifth`; M5 |
| AC-6 | 1, 3 | `armedAtRoot_thenPushAndPop_nextBackOpensRailNotExit`; M6 |
| AC-7 | 1, 3 | `backAtTabRoot_opensRailThenArmsThenExits`; M7 |
| AC-8 | 1, 4 | 5 `MainTabPushRailFocusTest` tests; M8 |
| AC-9 | 1, 3 | `screenBackHandlerOnPushedScreen_winsOverMainHandlers`, `ShowAllFilterFocusTest.panelBack_closesWithFocusOnFilters`, `ShowAllRailCloseFocusTest.showAllMenuOpenAndBackReturnsFocusToTheOpeningCard`; M9 |
| AC-10 | 1, 3 | `backOnRootPushedDetails_popsToMain`; M10 |
| AC-11 | 4 | D3 classes; `ExitConfirmationTest`, `DrawerStateTest`, full gate |
| R2 | 2, 3 | `TabStackStateTest`, `backAtOtherTabRoot_whileFirstTabHasPushedScreen_opensRail` |
| R3 | 4 | `leftFromPushedGrid_opensRail`, `leftWithNothingFocused_opensRailAndBackReturnsToContent`, AC-11 drawer tests; M11 and the design Risk 1 step |

---

## Implementation record

<!-- owner: implementer -->

**Commits:** Tasks 1-4 and the harness fix.

| Task | Commit |
|---|---|
| 1 | `13e46676` Add instrumented tests for Back and focus around the rail over a pushed tab screen |
| 2 | `ee9d59fb` Add the rule that picks one Back owner between the rail and the tab stack |
| 3 | `5a152b4c` Enable only the Back handler that owns the current rail and tab stack state |
| harness fix | see `git log` (Find the focused rail item in the unmerged semantics tree) |
| 4 | see `git log` (Refuse non-Left focus entry into the closed side rail and open it on an unhandled Left) |

**Red run** (P0 `red` tests on the unchanged code):

- Unit (Task 2, on stubs returning `null` / `false`): red: `RailBackOwnerTest` all 4 tests, `TabStackStateTest.canPop_followsAttachedProvider`, `detach_byPreviousOwner_keepsNewAttachment` - assertion failures. green on base: `canPop_isFalse_whenNothingAttached` - the stub already returns false; it guards the default.
- Instrumented (Task 1, 15 tests): pending qa-device (D1). Compile only; no device. The 7 `red` rows are listed in Task 1 and in `test-design.md` "Failing first".

**Deviations from the plan:**

- Rail value sampling in `MainTabPushRailFocusTest` is a background thread reading `drawerState.currentValue` every 2 ms (`MainHostHarness.startSampling`), not a `withFrameNanos` loop: the drawer state is created inside `DrawerMainContent`, so no always-composed probe sits above it. A transient `Open` shorter than 2 ms would be missed.
- `MainHostHarness` captures the drawer from the probe tab screens (`LocalDrawerState`), not from the host screen.
- `ExitConfirmationTest` has no case for the new arm-only-at-root rule: it lives in a handler, covered by `MainRailBackTest`.

**Files touched outside the plan:** none.

**For the reviewers:**

- Guard rows' focus assertions that rest on production restore behaviour: `popChildToRoot` expects `root_push_child` focused after the pop (AC-4 "opener focused"); a failure there at D1 may be a harness focus assumption, not the handlers.
- `ExitDisarmOnTabPush` also disarms once at first composition; harmless (nothing armed yet).
- `TabBackHandler` now reads `LocalDrawerState` where there is no rail host (other tab hosts): `railBackOwner(null, canPop)` gives `Tab` iff `canPop`, the old behaviour.

**Harness fix (D1 guards red on base):** `backAtTabRoot_opensRailThenArmsThenExits` and `leftFromPushedGrid_opensRail` both stop in `awaitRailItemFocused` after the drawer reached `Open`. Cause: `railItemFocused` matched `isFocused() and hasAnyDescendant(hasText(title))` on the merged tree, where the clickable `NavigationDrawerItem` merges its label into itself and has no descendant carrying the text. `MainSideMenuFocusTraversalTest.focusedItem` uses the same matcher with `useUnmergedTree = true` and passes. Fix: the same flag; the assertion (a focused rail item) is unchanged. No production bug: the drawer opened in both tests. Not run on a device; D3 confirms.

**Task 4:** both changes in `DrawerSheet` as planned, compiled with the androidTest sources. `leftWithNothingFocused_...` stays red on base only through its precondition; the listener is proven by the post-fix mutation in its test-design row.

**QA round 1 fixes** (none run on a device; qa re-runs on the box):

| Defect | Commit | Cause and fix |
|---|---|---|
| 1 | see `git log` (Wait for the pushed screen's initial focus ...) | Harness race: the probe child claims its first card after the push, and `focus()` / `openRailFrom` right after it raced that claim. `assertRailBackKeepsChild` and `fiveBacks...` now `awaitFocused(CHILD_CARD_FIRST)` first. Assertions unchanged. |
| 2 | see `git log` (Wait for the other tab root's initial focus ...) | Diagnosed from code, not reproduced. No production regression found: `canPop` and the Back owner are unchanged by f4ec56aa. Before the gate, focus falling when the tab switched revealed the rail, so `awaitDrawer(Open)` passed without the Back; with the gate the rail stays Closed and the test depends on Back. Back right after `awaitRoot("other")` can land before the root's own initial focus claim; that claim then takes focus from the rail and closes it (`focusExited`), a transient `Open` the poll misses. The test now awaits `ROOT_CARD_FIRST` focused before Back. If it still fails on the box, the next suspect is production (rail `requestFocus` after `reveal` on the other tab). |
| 3 | see `git log` (Read the unhandled-Left guard ...) | Production, AC-8/R11. The unhandled-key listener is reached before the framework's focus search, but its guard `!v.hasFocus()` read the Android view's focus. The view keeps its focus after the Compose node that held it leaves, so the guard vetoed the very case (nothing focused in Compose). Now a `onFocusChanged` on a wrapper around the content (`ModalNavigationDrawer`) tracks whether content holds Compose focus, and the guard reads that. Left with focus in the content is still not taken by the listener. Compiled only. |

**QA round 2 defect 3** (not run on a device):

| Commit | Change |
|---|---|
| `2bf56b89` | Test order. The timeout at `MainTabPushRailFocusTest.kt:94` is `awaitDrawer(Closed)` after Back, not `awaitDrawer(Open)` after Left (line 90): the file is unchanged since `13e46676`, and both QA rounds stopped at 94, so the real Left opened the rail and focused a rail item in both rounds. Back with the rail `Open` begins a handoff (`MainScreenComponent.kt:213`); the child is still `Loading`, so the rail stays `HandingOff` and returns to `Open` at the 5 s timeout (`ContentFocusHandoff.kt:24,137`), never `Closed` within the 10 s wait. The test supplied the content only after that wait; test-design R11 and design Risk 1 order it Back, content supplied, then `Closed`. Now: Back, assert `HandingOff`, end loading, await `Closed`, first card focused, child shown. No assertion removed; `HandingOff` after Back is added. |
| `b3933ff8` | Comment only. `1fd30c7a` was not needed for this defect (round 1 also passed line 90 with `!v.hasFocus()`); its claim that the view keeps focus and vetoes the case is unverified, so it is dropped. The Compose-focus guard stays. |

**Review round 1 fixes** (triage-1; compiled, `check.full` green at `4a962b04`; no instrumented run, no device):

| Item | Commit | Change | Red run |
|---|---|---|---|
| A2 | `ea5eef7a` | `DrawerMainContent` takes `exitConfirmation` (default `remember { ExitConfirmation() }`); the harness passes its own and exposes `isExitArmed`; `assertRailBackKeepsChild` and `fiveBacks...` assert it false right after `awaitDrawer(Closed)`, before the pop | green on base: the `!tabStack.canPop` guard holds; red only under the mutation "arm unconditionally" (`MainScreenComponent.kt` RailOpenBackHandler) |
| A4 | `00be3dcb` | Harness exit clock frozen (`ExitConfirmation(nowMillis = { 0L })`): no arm expires during a run; AC-6 test asserts the arm before the push | green on base: the disarm exists; red under the mutation "empty `ExitDisarmOnTabPush`" (`MainScreenComponent.kt:240`) |
| A5 | `8d0fb937`, `4b346104`, `876ea858` | As the Design section: `DrawerState.failHandoff(requestId, reopen = true)`, `ContentFocusHandoff(closeOnFail = { false })` whose `fail` passes `reopen = !closeOnFail()`, `DrawerMainContent` passes `{ tabStack.canPop }`. `DrawerSheet`'s value effect calls `clearFocus(force = true)` when `Closed` with focus in the rail. `8d0fb937` had put the flag on `beginHandoff` and had no clear; `4b346104` replaces it | red: `DrawerStateTest.a failed handoff that must not reopen leaves the rail closed` - `expected: <Closed> but was: <Open>` (stub param, at `8d0fb937`). Instrumented `MainRailBackTest.backOverRailOnNeverFocusablePushedScreen_popsAfterFailedHandoff`: expected red at `00be3dcb` on `awaitDrawer(Closed)`, and at `8d0fb937` on `assertNoRailItemFocused`; not run |
| A6 | `4a962b04` | `DrawerMainContent` wraps the rail's `onAction`: `CommonAction.ItemSelected` disarms the exit; the harness maps a rail pick to `tabRouter.openTab` and adds `pickRailTabBelow` | `MainRailBackTest.armedAtRoot_thenTabPick_nextBackOpensRailNotExit`: expected red at `8d0fb937` on `assertFalse(host.isExitArmed)` after the pick, not run |
| A1 | none | Test and listener unchanged. The repo code cannot settle whether `leftWithNothingFocused_...` reaches the unhandled-key path: if the Compose view keeps Android focus after the focused node leaves, Compose's own Left search may enter the rail as `FocusDirection.Left` (the gate allows it) before any listener runs; the Compose sources are not in the Gradle cache. qa's two mutations decide it | - |

For the reviewers:

- A5: `closeOnFail` is read when the handoff fails, not when Back begins it, so the initial and dialog handoffs over a pushed screen also settle `Closed` on failure (design: "every caller that does not pass it" means `ContentFocusHandoff` callers).
- A5 clear: after it nothing is focused and the gate refuses the framework refocus; Left reveals the rail through the unhandled-key listener.
- A6 disarms on any rail pick, the active tab included. `DrawerMainContent`'s `exitConfirmation` parameter (from A2) is the harness's Exit arm seam, not in the design's change table.
- `backOverRailOnNeverFocusable...` also opens the rail with the unhandled Left, so it shares `leftWithNothingFocused_...`'s dependency on the listener. It awaits the opener focused after the pop (design Risk 8 check); with nothing focused on the child before the pop that rests on the root's own restore.
- Named-test check: 0 missing; 2 SKIPPED false positives (`...NotExit(` matches the checker's `xit(` marker): `armedAtRoot_thenPushAndPop_...`, `armedAtRoot_thenTabPick_...`.
- Mutation rows in test-design.md cite `ModalNavigationDrawer.kt:258` and `:338`; they are now `:269` (`RevealOnUnhandledLeft(...)` call) and `:349` (guard).

**Review round 2 fixes** (triage-2; compiled, `check.full` green at `15f0f3a5`; no instrumented run, no device):

| Item | Commit | Change | Red run |
|---|---|---|---|
| B2 | `df0c3447` | The A5 clear is live. `NavigationDrawerItem` (tv-material 1.1.0 bytecode) draws its label inside `AnimatedVisibility(visible = hasFocus)`, so on a collapsed rail the label matcher of `railItemFocused` found nothing whether or not an item held focus: mutation (b) proved only that the matcher was blind. Focus stays on the item during `HandingOff`: the retry loop's `requestFocus` on a content group with nothing focusable moves nothing, and `redirectFocusToContent` runs only on a rail focus change. `railItemFocused` now matches `isFocused() and isSelectable()` (the list item's `selected` semantics; probe cards have none). The test asserts a rail item focused 2.5 s into `HandingOff` and none after `Closed` | expected red under mutation (b) at `assertNoRailItemFocused()` after `awaitDrawer(Closed)`; not run |
| B3 | `15f0f3a5` | `MainSideMenuItem.onClick` acts only when `drawerState.currentValue == Open`; new `MainRailBackTest.centerOnCollapsedRail_duringHandoff_doesNotSwitchTab` (Loading child, Left, Back, Down, Center: child stays, root `other` absent); harness `assertRailItemFocused`, `assertRootAbsent` | expected red on `15f0f3a5^` at `assertShown(CHILD_SCREEN)` (the History root takes focus and settles the handoff); not run |

**Review round 3 fixes** (triage-3; compiled with the androidTest sources, `check.full` green at `6c44c4e9`; no instrumented run, no device):

| Item | Commit | Change | Red run |
|---|---|---|---|
| C2 | `4d64a1b7` | `centerOnCollapsedRail_duringHandoff_doesNotSwitchTab` asserts after Down that the focused rail item is not the selected one (`isFocused() and isSelectable() and isNotSelected()`, harness `assertUnselectedRailItemFocused`) | not run; expected red on `15f0f3a5^` at `assertShown(CHILD_SCREEN)` now that Down is proven to leave Movies |
| C3 | `6c44c4e9` | `MainSideMenuItem.onClick` asks `DrawerState.acceptsPickOf(tab)`: `Open` accepts; `HandingOff` accepts only when the handoff expects new content (tab pick) and the tab is not the selected one; `Closed` refuses. Harness: third rail tab Series (root `third`, last in the rail, so the one-Down picks of History are unchanged) and `startMain(loadingRoot)`, a tab root that stays in Loading with nothing focusable. New `MainRailBackTest.correctivePickDuringTabPickHandoff_switchesToTheSecondTab`: History in Loading, pick History, Down, Center on Series: root `third` shown, `other` gone, rail `Closed`, its first card focused | not run; mutation for qa: `acceptsPickOf` back to `currentValue == DrawerValue.Open` (the `15f0f3a5` guard): expected red at `awaitRoot("third")` (Center ignored, History's handoff fails at 5 s and reopens the rail). The B3 test stays green: its Back handoff expects no new content |

For the reviewers:

- C3 refuses a re-pick of the selected tab during `HandingOff`: `MainVM.onTabSelected` would send a duplicate `ItemSelected` with its catch-up refresh, and the pending handoff already waits on that tab.
- The corrective pick reuses the pending request: `beginHandoff` returns `null` outside `Open`, and the second tab, composed during that request, settles it.

**Blocked:** none.

## Readiness

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
