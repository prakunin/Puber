# Back and focus around the side rail on Show all grids

Status: approved
Date: 2026-11-25
Slug: showall-rail-back-exits-app
Kind: bug
Size: M
Tracks: none
Amended by `2026-11-25-rail-handoff-nothing-focusable.md` (Design, prior rows): Decisions A5 (:98), Alternatives A5 (:145), Risk 1 check (:153), Risk 4 (:156), Open question 1 (:167), manual M11 step 5 (:292).

## Problem / Why

### Request

Show all grid on a tab (Movies «Все фильмы»): with the side rail expanded, Back must close the rail and keep the grid. Today it pops Show all in one session state and closes the rail with the exit toast in another; once Back from the expanded rail left the app. Folded bug: on entry into Show all from the tab root, focus lands on the rail, which expands it.

### Root cause

| # | Defect | Cause | Status |
|---|---|---|---|
| 1 | Same Back press pops Show all or closes the rail depending on history | Three Main-level `BackHandler`s rely on registration order: RailClosed (`ui/feature/main/component/MainScreenComponent.kt:119`), TabBack (`core/ui/navigation/component/FlowComponent.kt:622,749-757`, enabled on `navigator.canPop` only, no rail check), RailOpen (`MainScreenComponent.kt:159`). activity-compose 1.13.0 registers on first entry to composition and the last enabled one wins (research.md: How the dispatcher picks). On cold start and after a tab switch the tab content composes a pass later (`core/ui/navigation/component/TabComponent.kt:109-131,146`), so TabBack registers last and pops Show all with the rail open. After a Details round trip Main re-enters in one pass, RailOpen is last and wins (research.md: Why the order flips) | confirmed: code, library bytecode, device observations `.flow/runs/catalogue-browse-all/qa-report.md:42,65` (one Back pops before a Details trip; grid stays with toast after) |
| 2 | Back from the rail can leave the app | RailOpen arms the exit on any screen, pushed or root (`MainScreenComponent.kt:205-208`); only RailClosed disarms (`:185`); a pop through TabBack does not. Rail open on Show all, three Backs within 3 s (`ui/feature/main/component/ExitConfirmation.kt:12`): close rail + arm, pop Show all, RailClosed sees `isArmed` and calls `finish()` (`:180-181`) | plausible: derived from code (research.md: Path to leaving the app), not reproduced |
| 3 | Entry into Show all puts focus on the rail | The push saves focus and replaces the opener (`FlowComponent.kt:782-786`); Show all in `Loading` has nothing focusable (header `canFocus = false`, `ui/feature/showall/content/ShowAllScreenContent.kt:80-87,107-111`). Focus reaching the rail sheet calls `drawerState.reveal()` (`core/ui/uikit/component/drawer/ModalNavigationDrawer.kt:284`); once Open the content gate (`MainScreenComponent.kt:149`) and `rememberFocusRequesterOnLaunch` (`core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:64,76`) keep the grid from taking focus back. Nothing starts a handoff on an in-tab push, so `redirectFocusToContent` (`ModalNavigationDrawer.kt:281-282`) never runs | reveal-and-stay chain confirmed in code; what moves focus into the rail after the opener leaves, and whether it happens on every entry or only the first, is `unverified` |

### Verifying defect 3 before the fix (implementer, first step; qa repeats on the box)
1. Box, base f88f3079 build: cold start, Movies, Center on «Все фильмы»; screenshot at about 1 s and 3 s. Repeat as a second entry, after a Details round trip, and through a shelf «Показать все» tile. Record per path: rail expanded yes/no.
2. Instrumented test on the same box: Main drawer host plus a tab flow, push a screen that stays in a no-focusable loading state, then supply content; record the focused node per frame and `drawerState.currentValue`. Red on base when step 1 reproduces.
3. If step 1 reproduces but step 2 cannot name the node that takes focus, record that in the spec and move defect 3 back to `showall-first-entry-rail-focus` (see Q3). If step 1 does not reproduce on base, AC-8 is recorded as passing on base and no fix is made for it.

### Intended Back (research.md: Options; Q1, Q2)

| Rail | Tab stack | Back |
|---|---|---|
| Open | pushed (Show all) | close rail, focus back to the card left; no exit arm, no toast |
| Open | root | close rail, arm exit, toast (unchanged) |
| HandingOff | any | swallowed (unchanged) |
| Closed | pushed | screen dispatcher (`ui/feature/showall/vm/ShowAllVM.kt:291`), else pop; disarm exit |
| Closed | root, armed | leave app (unchanged) |
| Closed | root, not armed | open rail (unchanged) |

Approach: research.md Option A (handlers gated by state so at most one Main-level handler is enabled, independent of order); the spec decides between A and B.

### Related

| Run | Relation |
|---|---|
| `rail-content-mismatch-rapid-back` | idea; retest after this ships (research.md: Related work) |
| `showall-rail-close-focus-shift` AC-3, `catalogue-browse-all` AC-7 step 5, `catalogue-filters` qa Manual 2 | deferred checks this run makes passable |

### Baseline on the box (f88f3079)

See `.flow/runs/showall-rail-back-exits-app/baseline.md` (qa-device probe): defect 3 reproduces on four entry paths (focus on the rail Movies item); Back with the rail open over Show all pops the grid after a cold start, a second entry and a tab switch, and closes the rail with the exit toast after a Details round trip.

## Acceptance criteria

Device ACs run on the box by remote input (D-pad, Center, Back) with screenshots; instrumented tests run through `make DEVICE=192.168.1.110:5555 itest TESTS=<class>` under a lease.

- AC-1. Given a cold start, Movies tab, Show all grid with a card focused, When Left expands the rail and Back is pressed, Then the rail closes, the grid stays, the card left is focused and no exit toast appears (device; instrumented test red on base).
- AC-2. Given AC-1 after a Details round trip from the grid, When Left then Back, Then the same result as AC-1, without the toast (device; instrumented test red on base).
- AC-3. Given AC-1 after switching tabs through the rail and back to Movies, When Left then Back, Then the same result as AC-1 (device; instrumented test).
- AC-4. Given Show all with the rail closed, in each history of AC-1 to AC-3, When Back is pressed once, Then Show all pops to the tab root with the opener focused and no toast (device).
- AC-5. Given the rail expanded over Show all, When Back is pressed 5 times about 0.5 s apart, Then the sequence is: rail closes; Show all pops; rail opens at the root; rail closes with the toast; app exits on the fifth press only (device; instrumented test on the handler sequence).
- AC-6. Given the toast was just shown at a tab root, When Show all is opened within 3 s and Back is pressed twice, Then the first pops Show all and the second opens the rail; the app stays in the foreground (instrumented test; device).
- AC-7. Given a tab root (Home, Movies, History) with the rail closed, When Back, Back, Back, Then rail opens, rail closes with the toast, app exits: unchanged from base (device).
- AC-8. Given a tab root with a card focused, When Show all is opened by Center (cold first entry, second entry, after a Details round trip, and through a shelf «Показать все» tile), Then the rail stays collapsed while loading and the first card is focused once the list lands; Left from the grid still expands the rail (device; instrumented test from the verification step). Subject to Q3.
- AC-9. Given Show all with the filter panel open, or a card context menu open, When Back, Then only the panel or menu closes and focus returns to «Фильтры» or the card (device; `ShowAllRailCloseFocusTest` stays green).
- AC-10. Given Details or the player pushed over Main, When Back, Then it pops to the screen underneath as on base (device).
- AC-11. Existing tests stay green: `MainSideMenuFocusTraversalTest`, `InitialDrawerContentFocusTest`, `ShowAllRailCloseFocusTest`, `ShowAllScreenContentFocusTest`, `TabFlowLifecycleTest`, `ExitConfirmationTest`, `DrawerStateTest`; unit suite, detekt, lint (`check.full`).

### Risks (brief)

- Exit semantics: the toast and double-Back are the only way out of the app; a wrong gate traps the user at the root or exits on a stray press (AC-5 to AC-7).
- A handoff started on an in-tab push puts the rail in `HandingOff`, where Back is swallowed (`MainScreenComponent.kt:200-202`) for up to 5 s on a slow first page (`core/ui/uikit/component/drawer/ContentFocusHandoff.kt:24`). The fix for defect 3 must not swallow Back while Show all loads.
- Every tab shares the Main handlers; the tab's `canPop` becomes state read by Main. Screen-level handlers (`ui/feature/showall/content/ShowAllFilterPanel.kt:87`, `core/ui/uikit/component/TvDialogOverlay.kt:57`) must still register after and win.
- The cold-start and tab-switch orders only appear when the tab content composes a pass after Main; a test that composes everything in one pass passes on base and proves nothing.
- Device: a worktree build can drop the box to pairing (memory: worktree-build-loses-box-session); refresh with a master build and `make DEVICE=192.168.1.110:5555 auth-save` before installing the worktree APK.

## Out of scope

- rail-content-mismatch-rapid-back (retest after this fix; not fixed here).
- Any visual change to the rail or the grid.

## Design

Architect, 2026-11-25. Base f88f3079; rows marked "at c6b25f49" (triage-1.md A5-A7) cite lines at that commit. Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/`. Brief: `brief.md`; research: `research.md`; box probe: `baseline.md`.

### Decisions

| Question | Decision | Why |
|---|---|---|
| Option A or B (brief: Approach) | A: the three Main-level handlers stay; one pure rule decides which of them is enabled, so at most one is enabled in any state and registration order stops mattering | Smallest change that is order-independent; keeps the generic tab flow's own Back path; the rule is unit-testable |
| The rule | `railBackOwner(drawer: DrawerValue?, tabCanPop: Boolean)` returns `RailOpen` when drawer is `Open` or `HandingOff`; `Tab` when drawer is `Closed` or `null` (no rail host) and the tab can pop; `RailClosed` when drawer is `Closed` and the tab cannot pop; `null` when drawer is `null` and the tab cannot pop. Each handler is enabled only when it is the owner | Encodes brief: Intended Back rows 1-6 in one place |
| How Main learns the tab stack | New `TabStackState` (`@Stable`): `attach(owner, canPop: () -> Boolean)`, `detach(owner)` (clears only when `owner` is the attached one), `val canPop` (false when nothing is attached). Created by `remember` in `DrawerMainContent`, provided through `LocalTabStackState` (default `null`); `TabFlowNavigator` attaches `{ navigator.canPop }` with `owner = navigator` | Voyager's `canPop` is snapshot state (TabBack already recomposes on it, `core/ui/navigation/component/FlowComponent.kt:752`), so Main reads the same value TabBack reads; no VM involvement |
| Exit arm (Q2) | `RailOpenBackHandler` arms and shows the toast only when `!tabStack.canPop`; on a pushed screen it only begins the handoff | Q1/Q2 answers (state.md Decisions 2026-11-25) |
| Disarm on pop (Q2, AC-6) | A `LaunchedEffect(tabStack.canPop)` in Main calls `exitConfirmation.disarm()` | Arming happens only at a root, so the root-to-pushed transition is the only one that can carry an armed state; a pop back to root then cannot exit |
| Defect 3 mover (Q3) | Named; defect 3 stays in this run. Chain: the opener card detaches (`core/ui/navigation/component/FlowComponent.kt:786`); Compose ui 1.12.1 `FocusTargetNode.onDetach` clears focus without clearing the view; `FocusInvalidationManager.invalidateOwnerFocusState` then finds no active node and calls `FocusOwner.clearOwnerFocus` → `AndroidComposeView.clearOwnerFocus` → `ViewGroup.clearFocus`; the framework re-focuses the root view (non-touch mode), which reaches `AndroidComposeView.requestFocus(int, Rect)` → `requestFocusBypassUnfocusableComposeView` (`ComposeUiFlags.isBypassUnfocusableComposeViewEnabled` = true) → a Compose `focusSearch` from no active node, which lands on the rail sheet (composed ahead of the content, `core/ui/uikit/component/drawer/ModalNavigationDrawer.kt:209-221`; its restorer picks the selected tab, `ui/feature/main/component/MainScreenComponent.kt:228-230,245`), and `onFocusChanged` reveals it (`ModalNavigationDrawer.kt:284`) | Compose steps read from ui-android 1.12.1 bytecode in the Gradle cache; the framework refocus is AOSP `View.clearFocus`; matches the box (rail Movies item on P1, P2, P3, P5, `baseline.md`) and the existing note `ui/feature/contentlist/content/BrowseAllEntry.kt:59-60` ("the side rail, which Android focuses when the grid leaves"). Cached content (P2) fails too: nothing in Show all is focusable at the end of the apply that removes the card |
| Defect 3 fix | The rail refuses focus it did not ask for: on the `DrawerSheet` focus group, `focusProperties { onEnter = { if (drawerState.currentValue == DrawerValue.Closed && requestedFocusDirection != FocusDirection.Left) cancelFocusChange() } }`. While `Open` or `HandingOff` every entry is allowed, so the rail's own request (`ModalNavigationDrawer.kt:240-245`) and the handoff redirect (`:281-282`) keep working | Removes the reveal at its source for every push and pop, not only Show all; Show all's own launch request (`ui/feature/showall/content/ShowAllScreenContent.kt:215`) then focuses the grid because the drawer never reads `Open` (`core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:64,76`). `onEnter` runs on `requestFocus` as well as on D-pad search (`FocusTransactionsKt.performCustomRequestFocus` → `performCustomEnter` in 1.12.1) |
| Left with nothing focused (readiness-1 fix 1; A7) | `DrawerSheet` registers `ViewCompat.addOnUnhandledKeyEventListener` on `LocalView.current` for its composition (`RevealOnUnhandledLeft`, `ModalNavigationDrawer.kt:331-348` at c6b25f49): a `KEYCODE_DPAD_LEFT` `ACTION_DOWN` that no view handled, while the drawer is `Closed` and no node inside the drawer content holds Compose focus (`contentHasFocus`, written by `onFocusChanged` on the content wrapper `Box`, `:229`), calls `drawerState.reveal()` and is consumed. The rail then takes focus by its own request in `Open` (`ModalNavigationDrawer.kt:240-245`), which the gate allows: the same path Back at a root uses (`MainScreenComponent.kt:183`) | With nothing focused a D-pad key reaches Compose only through the framework's default-focus restore, `requestFocus(FOCUS_DOWN)`; the refocus after `clearFocus` that causes defect 3 is also `FOCUS_DOWN`, and both arrive in `AndroidComposeView.requestFocusBypassUnfocusableComposeView` (ui 1.12.1 bytecode; `isViewFocusFixEnabled` is false) with the same direction, so no `onEnter` rule can let one through and refuse the other. The unhandled-key path runs after the view tree declines the key and before that restore (API 28+: `ViewRootImpl`; below 28: `androidx.core.app.ComponentActivity.dispatchKeyEvent` → `KeyEventDispatcher`, core 1.19.1; `MainActivity.kt:59-66` keeps `super`). Compose focus, not `view.hasFocus()`: the two differ only when the Compose view holds view focus but no content node has Compose focus (a screen in `Loading` after the refocus), where the view flag leaves Left dead until content loads (review-state-path-1.md F2). It does not widen the listener: overlays inside the content `Box` keep `contentHasFocus` true, the update prompt puts the drawer in `HandingOff`, Main leaves composition on a root push |
| Failed handoff over a pushed screen (A5) | `DrawerState.failHandoff(requestId, reopen: Boolean = true)` settles `Closed` when `reopen` is false. `ContentFocusHandoff` gains `closeOnFail: () -> Boolean = { false }` and `fail` passes `reopen = !closeOnFail()`; `DrawerMainContent` passes `{ tabStack.canPop }`. At a tab root, and in every caller that does not pass it, a failed handoff reopens the rail as on base | Over a pushed screen with nothing focusable, Back from the rail (`HandingOff`, 5 s) otherwise fails back to `Open` and the next Back repeats it; `Closed` with the tab able to pop gives Back to `Tab` (`railBackOwner`), which pops |
| Rail focus after that settle (A5) | `DrawerSheet`'s `LaunchedEffect(drawerState.currentValue)` (`ModalNavigationDrawer.kt:251-256` at c6b25f49) also calls `LocalFocusManager.current.clearFocus(force = true)` when the value is `Closed` and `focusState?.hasFocus == true` | The rail took focus while `Open` and keeps it through the failed redirect; left there, a collapsed rail shows a focused item and Up, Down and Center act in it. After the clear the framework refocus is refused by the gate, leaving the nothing-focused state of Risk 1, where Left reveals the rail. No other path reaches `Closed` with focus in the rail: `settleHandoff` needs the content focused, `focusExited` runs on the rail losing focus, a Left entry reveals at once |
| Disarm on a tab pick (A6) | `DrawerMainContent` passes `MainSideMenuContent` a remembered wrapper of `onAction` that calls `exitConfirmation.disarm()` on `CommonAction.ItemSelected` before forwarding it; the pick itself (`MainScreenComponent.kt:302-307` at c6b25f49) is unchanged | An arm at one root otherwise survives a pick (root to root leaves `canPop` false, so `ExitDisarmOnTabPush` does not run) and the next Back at the new root exits. Synchronous with the pick, covers re-picking the active tab, and leaves the `MainSideMenuContent` signature (two androidTest callers, baseline profile entry) as it is |
| Q3 fallback | If the implementer's red test (brief: Verifying defect 3, step 2) does not show the rail gaining focus by a non-Left entry while `Closed`, the `onEnter` gate is dropped and defect 3 moves back to `showall-first-entry-rail-focus`; the Back fix ships alone | Q3 answer |
| Test seam | `DrawerMainContent` (`MainScreenComponent.kt:89`) becomes `internal` so an androidTest composes the real handlers, drawer and a tab flow in the LoadingTab-then-Open order | The order bug only shows when the tab composes a pass after Main (brief: Risks) |

### Changes by layer

API / repository: N/A: no data path changes.

ViewModel state and actions: N/A: `ShowAllVM.onBackPressed` (`ui/feature/showall/vm/ShowAllVM.kt:291`) is still reached through the tab router.

Screen and DI module: N/A: no Koin or screen key change.

Resources: N/A: the toast string `main_press_back_again_to_exit` is reused.

Compose content:

| File | Change |
|---|---|
| `core/ui/navigation/component/RailBackOwner.kt` (new) | `internal enum class RailBackOwner { RailClosed, Tab, RailOpen }` and `internal fun railBackOwner(drawer: DrawerValue?, tabCanPop: Boolean): RailBackOwner?` per Decisions |
| `core/ui/navigation/component/TabComponent.kt:41-52` (next to `TabFlowSession`) | `TabStackState` and `LocalTabStackState` per Decisions |
| `core/ui/navigation/component/FlowComponent.kt:749-757` `TabBackHandler` | `enabled = railBackOwner(LocalDrawerState.current?.currentValue, navigator.canPop) == RailBackOwner.Tab`; outside Main (`LocalDrawerState` null, `ModalNavigationDrawer.kt:52`) behaviour is unchanged |
| `core/ui/navigation/component/FlowComponent.kt:577-657` `TabFlowNavigator` | `DisposableEffect(stackState, navigator)` attaching `{ navigator.canPop }` with `owner = navigator` and detaching on dispose |
| `ui/feature/main/component/MainScreenComponent.kt:95-115` `DrawerMainContent` | `remember { TabStackState() }`, provided with `LocalTabStackState` in the existing `CompositionLocalProvider` (:112-115); disarm effect per Decisions inside a small composable so the `canPop` read does not recompose the drawer call; `private` → `internal` |
| `MainScreenComponent.kt:171-187` `RailClosedBackHandler` | takes `TabStackState`; `enabled = outerActive && railBackOwner(drawerState.currentValue, tabStack.canPop) == RailClosed`; body unchanged |
| `MainScreenComponent.kt:191-210` `RailOpenBackHandler` | takes `TabStackState`; enabled via the rule (same result as :199); `arm()` and the toast (:207-208) only when `!tabStack.canPop` |
| `MainScreenComponent.kt:116-118,156-158`, `ui/feature/main/component/ExitConfirmation.kt:22-23` | comments: order no longer carries meaning; armed only by a Back that closed the rail at a tab root |
| `core/ui/uikit/component/drawer/ModalNavigationDrawer.kt:230-295` `DrawerSheet` | `focusProperties { onEnter = … }` before `.focusGroup()` (:288); the unhandled-Left listener in a `DisposableEffect(view, drawerState)` after the `LaunchedEffect` (:240-245), guarded by `contentHasFocus` from `onFocusChanged` on the content wrapper in `ModalNavigationDrawer`, per Decisions |
| `ModalNavigationDrawer.kt:251-256` `DrawerSheet` effect (at c6b25f49) | clear focus when `Closed` with focus in the rail, per Decisions (A5) |
| `ModalNavigationDrawer.kt:169-173` `DrawerState.failHandoff` (at c6b25f49) | `reopen: Boolean = true` parameter; `false` sets `Closed` |
| `core/ui/uikit/component/drawer/ContentFocusHandoff.kt:55-67` `ContentFocusHandoff` (at c6b25f49) | `closeOnFail: () -> Boolean = { false }` constructor parameter; `fail` passes `reopen = !closeOnFail()` |
| `MainScreenComponent.kt:99-106` `DrawerMainContent` (at c6b25f49) | `tabStack` remembered before `handoff`; `ContentFocusHandoff(drawerState, mainContentFocus, closeOnFail = { tabStack.canPop })` (A5); `onAction` wrapper for `MainSideMenuContent` at :137-141 (A6) |

### Alternatives rejected

| Alternative | Reason |
|---|---|
| B: one Back coordinator in Main | Main would need the tab's `Navigator` (lives in `TabFlowNavigator`) and Back would leave the generic tab flow; larger diff for the same table |
| C: register RailOpen with a navigationevent priority | Priority semantics unverified; RailClosed vs TabBack would still depend on composition order |
| Start a content handoff on an in-tab push (research.md: Options, A first-entry part) | `HandingOff` swallows Back (`MainScreenComponent.kt:200-202`) for up to 5 s (`core/ui/uikit/component/drawer/ContentFocusHandoff.kt:24`), `failHandoff` then opens the rail on a slow page, and the blind request (`ContentFocusHandoff.kt:127-129`) can land on «Фильтры» |
| Make Show all focusable while `Loading` | Header focus is withheld on purpose (`ShowAllScreenContent.kt:80-87`); P2 shows cached content fails too, and other pushes and pops keep the same bounce |
| Refuse non-Left entries only for a window after a tab push or pop | Needs a deadline to end the window; an empty or failed pushed screen outlives any deadline, and the window still drops Left inside it |
| Let `FOCUS_DOWN` entries through the gate | That is the defect-3 entry itself (Decisions: Left with nothing focused) |
| Accept Left doing nothing where nothing is focusable | Strands D-pad navigation on empty and error screens until Back |
| Split defect 3 back out | Mover is named (Decisions); Q3 fallback still applies |
| A5: Back on an `Open` rail over a pushed screen with nothing focusable pops at once | Contradicts Q1 (Back closes the rail only); needs a focusability probe in the Back handler |
| A5: settle `Closed` on every failed handoff | At a tab root that is the deferred base behaviour (`rail-handoff-nothing-focusable`) and would leave Back to arm the exit with nothing on screen |
| A5: leave the rail holding focus after the `Closed` settle | Collapsed rail with a focused item; Center picks a tab the user cannot see |
| A6: `LaunchedEffect(state.selectedTab)` disarm | Runs a frame after the pick and misses re-picking the active tab |

### Risks

| # | Area | Risk | Lives in | Check |
|---|---|---|---|---|
| 1 | Focus and D-pad | The gate refuses every non-Left entry into a `Closed` rail on all tabs. A screen left with nothing focusable (Show all `Loading`, empty Bookmarks or History, an error state without a button) now has no focused view instead of a revealed rail. Left reaches the rail only through the unhandled-key listener; Up, Down and Right do nothing there until a screen request lands (on base each revealed the rail). Screens whose fallback relied on the bounce: `ShowAllScreenContent.kt:97-102,230-238`, `ui/feature/contentlist/content/BrowseAllEntry.kt:53-67` | `ModalNavigationDrawer.kt:230-295` | `MainTabPushRailFocusTest.leftWithNothingFocused_opensRailAndBackReturnsToContent` (child held in `Loading`, nothing focused, drawer `Closed`; real `KEYCODE_DPAD_LEFT` through `Instrumentation.sendKeyDownUpSync`; drawer `Open` with a rail item focused; Back; content supplied; first card focused, drawer `Closed`, child still on top). M11 step: on a tab root or pushed screen with nothing focusable (empty Bookmarks or History if the account has one, else Show all during `Loading`), Left, then Back. Expected: Left expands the rail with the selected tab focused; Back collapses it once content can take focus; nothing focusable: At a tab root with nothing focusable the rail reopens after 5 s (`ContentFocusHandoff.kt:24,137`). Over a pushed screen (Show all during Loading or Error) a failed handoff ends Closed with no rail item focused, and the next Back pops to the root (A5) |
| 2 | Exit semantics | A `TabStackState` that reads stale (no attach, wrong owner after a tab switch) leaves `RailClosed` enabled on a pushed screen (armed: `finish()`) or no Main handler enabled at a root (framework default finishes the activity) | `TabStackState` attach/detach in `FlowComponent.kt:577-657` | AC-5 to AC-7 tests; `backAtOtherTabRoot_whileFirstTabHasPushedScreen_opensRail` |
| 3 | Focus and D-pad | The unhandled-Left listener reveals the rail where it should not: under a dialog, over a root-pushed screen, or with a content node focused. Bounded by `contentHasFocus` (Compose focus inside the drawer content), `Closed`, and the listener's lifetime: Main leaves composition on a root push (`FlowComponent.kt:502-504` composes only the top item) | `ModalNavigationDrawer.kt:230-295` | AC-9, AC-10 tests; M9, M10 |
| 4 | Focus and D-pad | Back during `HandingOff` on a pushed screen is now always swallowed (base sometimes popped); bounded by the 5 s handoff budget | `MainScreenComponent.kt:200-202`, `ContentFocusHandoff.kt:24` | M1 |
| 5 | Focus and D-pad | A push while a handoff is still pending keeps today's redirect path (gate allows entry in `HandingOff`); the cold-entry frame at 1 s in `baseline.md` (P1: rail collapsed, item highlighted) is not explained by the chain and may be this case | `ModalNavigationDrawer.kt:281-282` | per-frame focus and drawer value in `pushIntoLoadingScreen_railStaysClosedAndFirstCardFocusedOnceLoaded` |
| 6 | Recomposition | Reading `tabStack.canPop` in `DrawerMainContent`'s own scope would recompose the drawer call on every push and pop | `MainScreenComponent.kt:95-161` | review: read only inside the two handlers and the disarm composable |
| 7 | Test validity | A test that composes Main and the tab in one pass passes on base; a Left sent by `performKeyInput` goes through Compose and never reaches the unhandled-key path | harness | AC-1, AC-3 red on base; the nothing-focused test uses `sendKeyDownUpSync` |
| 8 | Focus and D-pad | The A5 clear misses (stale `focusState`, or focus re-enters the rail): a collapsed rail keeps a focused item and Center picks a tab; or the clear fires on a `Closed` path not listed in Decisions and drops content focus | `ModalNavigationDrawer.kt:251-256` at c6b25f49 | Instrumented case (test design): pushed child that never becomes focusable; Left, Back, handoff times out; drawer `Closed`, no rail item focused, activity not finishing; Back pops to the root with the opener focused. Unit: `DrawerStateTest` failed handoff with `reopen = false` is `Closed`, pending id cleared. Existing `InitialDrawerContentFocusTest`, `MainSideMenuFocusTraversalTest` stay green |
| 9 | Exit semantics | An arm survives a tab pick (A6 regresses) and Back at the new root leaves the app | `MainScreenComponent.kt:99-141` at c6b25f49 | Instrumented case (test design): arm at the tab-A root, pick tab B from the rail within 3 s, Back opens the rail, activity not finishing |

### Open questions

| # | Question | Recommended | Blocks |
|---|---|---|---|
| 1 | At a tab root with nothing focusable, Back from the open rail starts a handoff that fails after 5 s and reopens the rail (base behaviour, `ContentFocusHandoff.kt:120-137`), so the user leaves only by picking a tab; over a pushed screen A5 settles `Closed` instead | new idea run; not this run | no |

## Test design

Test designer, 2026-11-25. Base f88f3079. Inputs: `brief.md`, `design-architect.md`, `baseline.md`; revision 2 after `triage-1.md` (A1-A7). Paths: `unit` under `app/src/test/kotlin/com/kino/puber/`, `instrumented` under `app/src/androidTest/kotlin/com/kino/puber/`. Instrumented runs: `make DEVICE=192.168.1.110:5555 itest TESTS=<fqcn>` under a lease.

### Risk profile

| # | Risk (source) | P | I | Score | Covered by |
|---|---|---|---|---|---|
| R1 | Wrong gate traps the user at a root or exits on a stray press (brief: Risks 1; design 2) | 2 | 3 | 6 | AC-5, AC-6, AC-7 instrumented; M5-M7 |
| R2 | `TabStackState` stale after a tab switch: `RailClosed` enabled on a pushed screen, or no Main handler at a root (design 2) | 2 | 3 | 6 | Guard: `TabStackStateTest.detach_byPreviousOwner_keepsNewAttachment` (owner check). `backAtOtherTabRoot_whileFirstTabHasPushedScreen_opensRail` catches only a stale tab A attachment that wins, not a cleared one |
| R3 | `onEnter` gate refuses entries the rail needs: Left, its own reveal request, the handoff bounce, on every tab (design 1) | 2 | 3 | 6 | `leftFromPushedGrid_opensRail`, AC-7 test focus assertion, existing drawer guards; M11 steps 1-4 |
| R4 | Harness composes Main and the tab in one pass and passes on base, or sends Left through Compose where the unhandled-key path never runs (brief: Risks 4; design 7) | 3 | 2 | 6 | Harness rules below; AC-1, AC-3 and `leftWithNothingFocused_opensRailAndBackReturnsToContent` run red on base first |
| R5 | Screen-level handlers (filter panel, menu, dialog) stop winning (brief: Risks 3) | 1 | 3 | 3 | `screenBackHandlerOnPushedScreen_winsOverMainHandlers`; existing AC-9 guards |
| R6 | Back swallowed in `HandingOff` on a pushed screen, up to 5 s (brief: Risks 2; design 4) | 1 | 2 | 2 | Accepted: bounded by the handoff budget; observed in M1 |
| R7 | Up, Down or Right pressed with nothing focused does nothing until a screen request lands; base revealed the rail (design 1) | 2 | 1 | 2 | Accepted: observed in M11 step 5 |
| R8 | Cold-entry frame at 1 s not explained by the chain (design 5) | 2 | 2 | 4 | Per-frame drawer value in `pushIntoLoadingScreen_railStaysClosedAndFirstCardFocusedOnceLoaded` |
| R9 | `canPop` read in `DrawerMainContent` scope recomposes the drawer on each push (design 6) | 1 | 1 | 1 | Accepted: review item |
| R10 | Worktree build drops the box to pairing (brief: Risks 5) | 2 | 2 | 4 | Procedure: master build and `auth-save` before install (AGENTS.md) |
| R11 | With nothing focusable (Show all `Loading`, empty Bookmarks or History, error without a button) the gate leaves Left unable to reach the rail (design 1; readiness-1 fix 1) | 2 | 3 | 6 | `leftWithNothingFocused_opensRailAndBackReturnsToContent`, proven by two mutation runs on the box (see Mutation runs); M8 step 6; M11 step 5 |
| R13 | Rail opened by Left over a pushed screen with nothing focusable: Back cycles `HandingOff` -> `Open` and never pops (triage A5) | 2 | 3 | 6 | `backOverRailOnNeverFocusablePushedScreen_popsAfterFailedHandoff` |
| R14 | Exit armed at one tab root survives a rail tab pick; the next Back at the other root exits (triage A6) | 2 | 3 | 6 | `armedAtRoot_thenTabPick_nextBackOpensRailNotExit`; M12 |
| R12 | Unhandled-Left listener reveals the rail under a dialog, over a root-pushed screen or with a view focused (design 3) | 1 | 3 | 3 | AC-9, AC-10 instrumented; `leftFromPushedGrid_opensRail` (focused card: one reveal, not two); M9, M10 |

### Harness

`ui/feature/main/component/MainHostHarness.kt` (instrumented, shared): `createAndroidComposeRule<ComponentActivity>()`; `FlowComponent` root with a probe host screen that composes the real `DrawerMainContent` (design: Test seam) over `TabComponent`, modelled on `ProbeMainHost` in `core/ui/navigation/component/TabFlowLifecycleTest.kt:189-219`. Probe tab root: focusable cards and a push to a probe child (grid of cards, optional `Loading` with nothing focusable, optional own `BackHandler`); a root push to a probe Details. The probe captures `LocalDrawerState`.

| Need | How |
|---|---|
| Cold-start order | `tabRouter.openTab` only after the first `setContent` frame, so the tab composes a pass after Main |
| Tab-switch order | `openTab(other)` then `openTab(first)` |
| Details-return order | root push of the probe Details, then Back |
| Back | `activity.onBackPressedDispatcher.onBackPressed()` on the UI thread (`PlayerPromptFocusAndKeysTest.kt:160`) |
| Rail open | D-pad Left via `performKeyInput` from a focused card |
| Left with nothing focused | `InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT)` off the UI thread; `performKeyInput` never reaches the view's unhandled-key path (R4). Precondition asserted first: drawer `Closed`, `onAllNodes(isFocused())` empty |
| Exit | `activity.isFinishing` |
| Exit arm | Harness seam reading the Main host's `ExitConfirmation` armed state (`ExitConfirmation.kt`); asserted right after `awaitDrawer(Closed)`, before any pop, because a pop disarms through `ExitDisarmOnTabPush` |
| Exit window | Harness records the arm time; tests that rely on an arm assert arm-to-final-Back is under the window (`ExitConfirmation.kt:12`) and fail, not pass, when it is exceeded |
| Never-focusable child | Probe child with no focusable node and no `Loading` end, so the handoff budget (`ContentFocusHandoff.kt:24`) runs out |
| Rail state | captured `drawerState.currentValue`, `waitUntil` Closed after a close |

The toast itself is not asserted; the arm behind it is, through the Exit arm seam.

### Traceability

| AC | Level | Priority | Base | Test | Proves |
|---|---|---|---|---|---|
| AC-1 | instrumented | P0 | red | `MainRailBackTest.railOpenOnPushedScreen_backClosesRailAndKeepsScreen_afterColdStart` | Rail Closed, child still shown, the card Left was pressed from is focused; fails if `TabBackHandler` stays enabled with the rail open (base: pops, `baseline.md` P1) |
| AC-2 | instrumented | P0 | red | `MainRailBackTest.railOpenOnPushedScreen_backClosesRailWithoutArmingExit_afterDetailsRoundTrip` | Right after `awaitDrawer(Closed)`, child still on top: exit not armed (seam). Then Back pops, Back at root opens the rail, not finishing. Fails if `if (!tabStack.canPop)` in `RailOpenBackHandler` (`MainScreenComponent.kt:216-219`) is dropped; the later pop disarms, so only the seam assertion catches it (base: child popped on press 1) |
| AC-2 | device | P1 | red | M1, M2 | Toast absent when the rail closes over Show all |
| AC-3 | instrumented | P0 | red | `MainRailBackTest.railOpenOnPushedScreen_backClosesRailAndKeepsScreen_afterTabSwitch` | As AC-1 after a tab switch; fails if the new tab's navigator is not attached to `TabStackState` (base: pops, P5) |
| AC-4 | instrumented | P0 | guard | `MainRailBackTest.backWithRailClosedOnPushedScreen_popsToTabRoot_inEveryHistory` | Cold, Details-return and tab-switch histories each pop to the root, opener focused, rail Closed; fails if `railBackOwner` gives `RailClosed` on a pushed screen |
| AC-5 | instrumented | P0 | red | `MainRailBackTest.fiveBacksFromRailOverPushedScreen_exitOnlyOnFifth` | Per press: rail Closed with child and exit not armed (seam); root; rail Open; rail Closed; finishing only after press 5; fails on any reordering of the handlers or an arm on the pushed screen |
| AC-6 | instrumented | P0 | red | `MainRailBackTest.armedAtRoot_thenPushAndPop_nextBackOpensRailNotExit` | Arm at root, push, Back pops, Back opens the rail, not finishing; arm-to-final-Back asserted under the window (`ExitConfirmation.kt:12`), so the run cannot pass by expiry. Fails if the `LaunchedEffect(tabStack.canPop)` disarm is removed (base: `finish()`, defect 2) |
| AC-6 | instrumented | P0 | red | `MainRailBackTest.armedAtRoot_thenTabPick_nextBackOpensRailNotExit` | Arm at tab A root, Left, pick tab B from the rail, Back opens the rail, not finishing; arm-to-Back under the window. Fails if a rail tab pick does not disarm (R14; base and c6b25f49: finishing) |
| AC-7 | instrumented | P0 | guard | `MainRailBackTest.backAtTabRoot_opensRailThenArmsThenExits` | Rail Open with focus inside the rail; rail Closed; finishing. Fails if the root arm is lost or the `onEnter` gate refuses the rail's own focus request after the reveal (R3) |
| AC-7 | device | P1 | guard | M7 | Home, Movies, History on the real build; catches a lost root arm (no toast or no exit on press 3) |
| AC-8 | instrumented | P0 | red | `MainTabPushRailFocusTest.pushIntoLoadingScreen_railStaysClosedAndFirstCardFocusedOnceLoaded` | Push by Center from a focused card into a `Loading` child, content supplied later; drawer value never `Open` on any frame, first card focused once loaded; fails if the `onEnter` gate is removed (base: rail Movies item, P1) |
| AC-8 | instrumented | P1 | red | `MainTabPushRailFocusTest.pushWithContentReady_railStaysClosed` | Cached-page path (P2); fails without the gate |
| AC-8 | instrumented | P1 | red | `MainTabPushRailFocusTest.pushAfterDetailsRoundTrip_railStaysClosed` | Details-return path (P3) |
| AC-8 | instrumented | P0 | guard | `MainTabPushRailFocusTest.leftFromPushedGrid_opensRail` | Left from a grid card reveals the rail and focuses it; fails if the gate refuses `FocusDirection.Left` |
| AC-8 | instrumented | P0 | red | `MainTabPushRailFocusTest.leftWithNothingFocused_opensRailAndBackReturnsToContent` | Child held in `Loading`, precondition nothing focused and drawer `Closed`; real Left; drawer `Open` with a rail item focused; Back; content supplied; first card focused, drawer `Closed`, child on top. Fails if `RevealOnUnhandledLeft` is removed or its `Closed`/`!contentHasFocus()` guard (`ModalNavigationDrawer.kt:338`, Compose focus of the content wrapper at :229) is inverted (R11); shown by the Mutation runs |
| AC-1 | instrumented | P0 | red | `MainRailBackTest.backOverRailOnNeverFocusablePushedScreen_popsAfterFailedHandoff` | Never-focusable child pushed, real Left opens the rail; Back: child still on top; drawer settles `Closed` after the handoff budget, not `Open`; next Back pops to the root. Fails if a failed handoff with `tabStack.canPop` returns the rail to `Open` (R13; base: pops on press 1; c6b25f49: Back cycles, never pops) |
| AC-8 | device | P1 | red | M8 | Four entry paths on the box; catches a rail reveal by a non-Left entry on a path the probe does not model |
| AC-9 | instrumented | P1 | guard | `MainRailBackTest.screenBackHandlerOnPushedScreen_winsOverMainHandlers` | Child's own handler consumes Back; stack and rail unchanged; fails if a Main handler is registered after or wins over screen handlers |
| AC-9 | instrumented | P0 | guard | `ShowAllFilterFocusTest.panelBack_closesWithFocusOnFilters`, `ShowAllRailCloseFocusTest.showAllMenuOpenAndBackReturnsFocusToTheOpeningCard` | Existing; panel and menu Back unchanged |
| AC-10 | instrumented | P1 | guard | `MainRailBackTest.backOnRootPushedDetails_popsToMain` | Back on the probe Details returns to Main with the tab child still on top; fails if Main handlers outlive Main's composition |
| AC-10 | device | P1 | guard | M10 | Details and player |
| AC-11 | instrumented | P0 | guard | `MainSideMenuFocusTraversalTest`, `InitialDrawerContentFocusTest` (incl. `dialogDismissedOverFocusedContent_returnsFocusToContentNotTheRail`, the `HandingOff` bounce the gate must allow), `ShowAllRailCloseFocusTest` (incl. `leftFromFilters_opensTheRail`), `ShowAllScreenContentFocusTest`, `TabFlowLifecycleTest` | Existing, unchanged; fail if the gate or handler change breaks rail traversal, handoff or tab lifecycle |
| AC-11 | unit | P0 | guard | `ExitConfirmationTest`, `DrawerStateTest`, `check.full` | Existing, unchanged |
| R2 | unit | P1 | guard | `TabStackStateTest.detach_byPreviousOwner_keepsNewAttachment` | Fails if `detach` ignores the owner and tab A's late `detach` clears tab B's attachment |
| R2 | instrumented | P2 | guard | `MainRailBackTest.backAtOtherTabRoot_whileFirstTabHasPushedScreen_opensRail` | Tab A child pushed, switch to tab B root, Back opens the rail; fails only if tab A's stale attachment wins over tab B |
| design rule | unit | P1 | new | `core/ui/navigation/component/RailBackOwnerTest`: `railBackOwner_isRailOpen_whenDrawerOpenOrHandingOff`, `railBackOwner_isTab_whenDrawerClosedAndTabCanPop`, `railBackOwner_isRailClosed_whenDrawerClosedAndTabAtRoot`, `railBackOwner_isTabOrNull_withoutRailHost` | Every row of `brief.md` Intended Back; fails on any swapped branch |
| design rule | unit | P1 | new | `core/ui/navigation/component/TabStackStateTest`: `canPop_isFalse_whenNothingAttached`, `canPop_followsAttachedProvider` | Fails if `canPop` does not follow the attached provider |

Base `new`: the symbol does not exist at f88f3079.

### Failing first

The implementer runs every `red` row on the unchanged worktree first. Expected on base, from `MainScreenComponent.kt:119,159,171-210` and `FlowComponent.kt:749-757` at f88f3079:

| Test | Base result |
|---|---|
| `railOpenOnPushedScreen_backClosesRailAndKeepsScreen_afterColdStart` | child popped (`TabBackHandler` registered last) |
| `railOpenOnPushedScreen_backClosesRailAndKeepsScreen_afterTabSwitch` | child popped |
| `railOpenOnPushedScreen_backClosesRailWithoutArmingExit_afterDetailsRoundTrip` | activity finishing on press 3 |
| `fiveBacksFromRailOverPushedScreen_exitOnlyOnFifth` | child popped on press 1 |
| `armedAtRoot_thenPushAndPop_nextBackOpensRailNotExit` | activity finishing |
| `pushIntoLoadingScreen_railStaysClosedAndFirstCardFocusedOnceLoaded` | drawer `Open`, focus in the rail |
| `leftWithNothingFocused_opensRailAndBackReturnsToContent` | drawer `Open` after the push (non-Left refocus revealed the rail); the nothing-focused precondition fails |
| `backOverRailOnNeverFocusablePushedScreen_popsAfterFailedHandoff` | base: child popped on press 1; c6b25f49: drawer back to `Open`, child never popped |
| `armedAtRoot_thenTabPick_nextBackOpensRailNotExit` | activity finishing (base and c6b25f49) |

If the AC-1 or AC-3 test passes on base, the harness composes in one pass (R4); fix the harness, not the assertion. If `pushIntoLoadingScreen_railStaysClosedAndFirstCardFocusedOnceLoaded` passes on base while M8 reproduces, the design's Q3 fallback applies: the gate, `MainTabPushRailFocusTest` and AC-8 move to `showall-first-entry-rail-focus`.

### Mutation runs (R11)

On the box, `leftWithNothingFocused_opensRailAndBackReturnsToContent` against each mutation of the fixed worktree, reverted after; both results recorded in the qa report.

| Mutation | Expected |
|---|---|
| `RevealOnUnhandledLeft(...)` call removed (`ModalNavigationDrawer.kt:258`) | red at `awaitDrawer(Open)` after the real Left |
| Guard inverted to `contentHasFocus()` (`ModalNavigationDrawer.kt:338`) | red at `awaitDrawer(Open)` |

Green under either mutation means the test does not reach the unhandled-key path: the implementer changes the test until it does; the assertion stays.

### Manual checks (device)

Box 192.168.1.110:5555, worktree dev APK, keys by `adb -s 192.168.1.110:5555 shell input keyevent` (Left 21, Right 22, Center 23, Back 4), screenshot after each step. Never across all checks: app leaves the foreground before the step that expects it, focus lost (no highlighted node), rail and content both looking focused.

| # | AC | Steps | Expected |
|---|---|---|---|
| M1 | AC-1 | 1. Cold start, Movies via rail. 2. Center on «Все фильмы», wait for the grid. 3. Right to a card, Left. 4. Back. | Step 3 rail expands. Step 4 rail collapses, grid stays, the card from step 3 focused, no toast. A Back during the collapse animation is swallowed, not a pop (R6) |
| M2 | AC-2 | 1. From the Movies root open a Details page by Center, Back. 2. Repeat M1 steps 2-4. | As M1, no toast |
| M3 | AC-3 | 1. Rail: Home, then Movies. 2. Repeat M1 steps 2-4. | As M1 |
| M4 | AC-4 | In each history of M1-M3, rail collapsed on the grid, Back once. | Tab root, «Все фильмы» focused, no toast, rail collapsed |
| M5 | AC-5 | M1 steps 1-3, then Back 5 times about 0.5 s apart. | Rail closes; Show all pops; rail opens; rail closes with the toast; app exits on press 5 only |
| M6 | AC-6 | 1. Movies root, Back (rail opens), Back (toast). 2. Within 3 s Right, Center on «Все фильмы». 3. Back, Back. | Press 1 of step 3 pops Show all; press 2 opens the rail; app stays in the foreground |
| M7 | AC-7 | On Home, Movies and History roots, rail collapsed: Back, Back, Back. | Rail opens; rail closes with the toast; app exits (as base) |
| M8 | AC-8 | Center on «Все фильмы» from the Movies root: 1. cold first entry, 2. second entry, 3. after a Details round trip, 4. through a shelf «Показать все» tile (a row with a short list; NOT REACHED if none, as in `baseline.md` P4). Screenshots at about 1 s and 3 s. 5. Left from the first card. 6. During `Loading` of entry 1, press Left once and record the result. | Steps 1-4: rail collapsed at 1 s and 3 s, first card focused once loaded. Step 5: rail expands. Step 6: rail expands with Movies focused (R11); the app must not leave Show all |
| M9 | AC-9 | 1. Show all, Up to «Фильтры», Center (panel), Back. 2. Long-press Center on a card (context menu), Back. | Panel closes, focus on «Фильтры»; menu closes, focus on the card; Show all stays, rail collapsed |
| M10 | AC-10 | 1. Show all card, Center (Details), Back. 2. Start a trailer or episode, Back until the player closes. | Returns to the screen underneath with its card focused, as base; no toast, rail collapsed |
| M12 | AC-6 (R14) | 1. Movies root, Back (rail opens), Back (toast). 2. Within 3 s Left, pick Home. 3. Back. | Step 3 opens the rail; app stays in the foreground |
| M11 | R3, R7, R11 | 1-4. On Home, Movies, History, Bookmarks, Settings: Left opens the rail; Right closes it with focus back on content; pick another tab from the rail; open and dismiss a dialog. 5. On a screen with nothing focusable (empty Bookmarks or History if the account has one, else Show all during `Loading`): Up, Down, Right, then Left, then Back. | Steps 1-4: rail opens and closes as base; focus lands in content after a tab pick and after the dialog; rail never expands without a Left. Step 5: Up, Down, Right do nothing (R7); Left expands the rail with the selected tab focused; Back collapses it once content can take focus; nothing focusable: At a tab root with nothing focusable the rail reopens after 5 s (`ContentFocusHandoff.kt:24,137`). Over a pushed screen (Show all during Loading or Error) a failed handoff ends Closed with no rail item focused, and the next Back pops to the root (A5). Must not: app exit, tab pop |

### perf

N/A: perf track off.

### UNTESTABLE

Toast text in AC-2, AC-5, AC-6: no instrumented hook for `Toast`; checked on the device (M1, M2, M5, M6); the arm behind it is asserted through the Exit arm seam.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Back with the rail open over a pushed screen: close the rail only, or close and pop? | yes | answered | close the rail only; the next Back pops |
| Q2 | When does the exit toast appear? | yes | answered | only when Back closes the rail at a tab root; never on a pushed screen; any pop disarms |
| Q3 | If the cause of focus moving to the rail cannot be named | no | answered | split it back to showall-first-entry-rail-focus and ship the Back fix alone (design names the cause; the red test confirms it) |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-25 | showall-first-entry-rail-focus folded into this run | owner |
| 2026-11-25 | Q1-Q3 on the recommended answers; design Option A | owner (standing instruction: recommended) |
| 2026-11-25 | Readiness 1 NOT READY: design adds the unhandled-Left listener (decision "Left with nothing focused"); M7/M8 lowered to P1; handoff with nothing focusable deferred to rail-handoff-nothing-focusable | owner (standing instruction: recommended) |
| 2026-11-25 | Review round 1: A5 failed handoff over a pushed screen settles Closed (with a rail focus clear), A6 rail tab pick disarms the exit, A7 Compose-focus guard; 1fd30c7a kept | owner (standing instruction: recommended) |
| 2026-11-25 | Gate 1: approved as designed (size M, no tracks, AC-1..AC-11) | owner (standing instruction: recommended) |
