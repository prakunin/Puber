# Test design: rail-handoff-nothing-focusable

Base c9e98129. Inputs: [brief.md](brief.md), [design-architect.md](design-architect.md), [research.md](research.md). Paths under `app/src/main/java/com/kino/puber/` unless they start with `app/`. perf track: off.

Levels (`.flow/roles/test-designer.md`): `unit` (JUnit 5, `./tools/agentw testDevDebugUnitTest`), `instrumented` (`make DEVICE=192.168.1.110:5555 itest TESTS=<fqcn>` under a lease, run by qa-device), `device` (remote input on the box, evidence file).

## Risk profile

| # | Risk (source) | P | I | Score | Covered by |
|---|---|---|---|---|---|
| R1 | Exit trap stays, or an armed HandingOff Back exits on a stray press (brief Risks 3, design Risk 2) | 2 | 3 | 6 | `backTwiceFromRailAtNeverFocusableRoot_exitsDuringHandoff`, `backDuringDismissHandoffAfterArmExpired_isSwallowed`, `MainRailBackTest` (AC-9), device D1 |
| R2 | Initial-to-TabPick restart lets the outgoing first tab confirm, or keeps the cold-start budget (brief Risks 4, design Risk 3) | 2 | 3 | 6 | `pickDuringInitialHandoff_restartsAsTabPick_outgoingTabCannotSettle`, DrawerStateTest restart cases |
| R3 | Failed Dismiss/Initial at a root leaves the rail collapsed with nothing focused and no way back (design Risk 1) | 2 | 2 | 4 | AC-2, AC-3, AC-6 tests assert Left and Back reveal the rail |
| R4 | Content that loads after a Closed fail does not take focus (design Risk 1) | 2 | 2 | 4 | AC-6 test covers `rememberFocusRequesterOnLaunch` only; the id-latching readers (`BrowseAllEntry.kt:50`, `FocusTargetState.kt:81`, `ShowAllScreenContent.kt:349`) stay unverified at a root, accepted: a slow root load is not reproducible on the box |
| R5 | Kind misassigned at a caller: re-pick of the selected tab as TabPick hangs 5 s over focusable content then reopens (design Risk 4) | 1 | 3 | 3 | `rePickOfSelectedTab_closesRailOntoContent` |
| R6 | Re-pick at a never-focusable root now ends `Closed` (design Risk 4, Open question 1 accepted) | 1 | 2 | 2 | accepted; `rePickOfSelectedTabAtNeverFocusableRoot_failedHandoffLeavesRailClosed` P2 |
| R7 | Dialog re-entry is Initial and now fails `Closed` (design Risk 5) | 1 | 2 | 2 | accepted; `InitialDrawerContentFocusTest` guard (AC-9) |
| R8 | Exit gate loses its `handoffKind == Dismiss` check (exit during an armed Initial/TabPick) | 1 | 3 | 3 | no test: the only armed non-Dismiss HandingOff is a dialog re-entry, which the harness cannot raise; code review |
| R9 | Harness seam differs from the app (selection updated after `openTab`, unlike `ui/feature/main/vm/MainVM.kt:131-135`) and hides a defect | 2 | 2 | 4 | seam order is fixed below; reviewed with the harness diff |
| R10 | Baseline profile entry `DrawerState.beginHandoff(Z)` stale (design Risk 6) | 3 | 1 | 3 | none; regenerate with the next profile run |
| R11 | Worktree build drops the box to pairing (brief Risks 5) | 2 | 2 | 4 | device precondition D0 |

## Harness seams (`MainHostHarness.kt`)

New instrumented tests drive only keys, Back and these seams; none of them references `HandoffKind`, so the class compiles against base production code for the red runs.

| Seam | Behaviour |
|---|---|
| dynamic selection | `ProbeState.selectedTab` snapshot state builds `MainViewState` (`isSelected`, `selectedTab`); `ItemSelected` sets it **before** `openTab` (MainVM order); default Movies keeps existing tests |
| `host.railPicks` | count of `CommonAction.ItemSelected` the rail emitted: a refused Center leaves it unchanged |
| `host.advanceExitClock(ms)` | `ExitConfirmation(nowMillis = { ProbeState.exitNowMillis })`; default 0 keeps the frozen clock of existing tests |
| `host.setLoadingRoot(name)` | changes `ProbeState.loadingRoot` mid-test (root drops or gains focusable content) |
| `startMain(loadingFollowsPick = true)` | on `ItemSelected` the picked tab becomes `loadingRoot` in the same action, before `openTab`, so the outgoing tab turns focusable while the swap is in flight |
| `host.focusSelectedRailItem()` | `requestFocus` on the selected rail item by `isSelectable() and isSelected()` (labels are absent while collapsed) |

Fixture "rail open over a never-focusable root": `startMain()`, `awaitFocused(ROOT_CARD_FIRST)`, `setLoadingRoot("first")`, `assertNothingFocused()`, `sendLeftThroughInputPipeline()`, `awaitDrawer(Open)`, `awaitRailItemFocused()`. Identical on base and after the change.

## Traceability

New instrumented tests live in `app/src/androidTest/kotlin/com/kino/puber/ui/feature/main/component/MainRailHandoffKindTest.kt` (class `MainRailHandoffKindTest`, harness as in `MainRailBackTest`). Unit cases are in `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/drawer/DrawerStateTest.kt`. Base: `red` fails on c9e98129 production code; `red (M#)` cannot compile on base (new API) and is proven red by the named mutation run; `guard` passes on base.

| AC | Level | Priority | Base | Test | Proves |
|---|---|---|---|---|---|
| AC-1 | instrumented | P0 | red | `MainRailHandoffKindTest.backTwiceFromRailAtNeverFocusableRoot_exitsDuringHandoff`: fixture; Back -> `HandingOff`, `isExitArmed`; Back while still `HandingOff` -> `isFinishing` | fails if the armed Dismiss exit branch is removed (base swallows every HandingOff Back) |
| AC-1 | instrumented | P0 | guard | `MainRailHandoffKindTest.backDuringDismissHandoffAfterArmExpired_isSwallowed`: fixture; Back; `advanceExitClock(3_001)`; Back -> not finishing, still `HandingOff` | fails if the exit ignores `isArmed` (M3) |
| AC-2 | instrumented | P0 | red | `MainRailHandoffKindTest.failedBackHandoffAtNeverFocusableRoot_railStaysClosed_leftReopensIt`: fixture; `startSampling`; Back; `advanceExitClock(3_001)`; `awaitDrawer(Closed)`; no `Open` sampled after Back; `assertNoRailItemFocused`; Left through the input pipeline -> `Open` | fails if a failed Dismiss reopens at a root (base `Open`), or the fail leaves a focused item in the collapsed rail |
| AC-2 | instrumented | P0 | red | `MainRailHandoffKindTest.failedBackHandoffAtNeverFocusableRoot_expiredArmBackReopensRail`: as above to `Closed`; Back -> `Open`, not finishing | fails if the fail ends `Open` (base), or Back after an expired arm exits |
| AC-3 | instrumented | P0 | red | `MainRailHandoffKindTest.rightFromRailAtNeverFocusableRoot_failedHandoffLeavesRailClosed`: fixture; Right -> `HandingOff`, not armed; `awaitDrawer(Closed)`, no `Open` sampled | fails if Right starts a TabPick kind or the fail ignores the kind (base `Open`) |
| AC-4 | instrumented | P0 | red | `MainRailHandoffKindTest.pickDuringInitialHandoff_switchesToPickedTab`: `startMain(loadingRoot = "first")`; `assertDrawer(HandingOff)`; `focusSelectedRailItem`; Down; Center -> `awaitRoot("other")`, `assertRootAbsent("first")`, `awaitFocused(ROOT_CARD_FIRST)`, `awaitDrawer(Closed)`, `railPicks == 1` | fails if `acceptsPickOf` refuses picks during Initial (base: `handoffExpectsNewContent` false) |
| AC-4 | instrumented | P0 | red | `MainRailHandoffKindTest.pickDuringInitialHandoff_restartsAsTabPick_outgoingTabCannotSettle`: `startMain(loadingRoot = "first", loadingFollowsPick = true)`; wait 1.5 s; `startSampling`; focus rail, Down, Center; `awaitRoot("other")`; still `HandingOff` 4 s after Center (past the cold-start 5 s); then `awaitDrawer(Open)`; no `Closed` sampled between Center and `Open` | fails if the pick keeps the Initial id (the outgoing first tab, now focusable, settles it: `Closed`), keeps the cold-start budget, or keeps kind Initial (fail ends `Closed`) |
| AC-5 | instrumented | P1 | guard | `MainRailHandoffKindTest.centerOnSelectedTabDuringInitialHandoff_switchesNothing`: `startMain(loadingRoot = "first")`; `focusSelectedRailItem`; Center -> `railPicks == 0`, root `first` shown, `HandingOff` | fails if the `!tab.isSelected` clause is dropped (M7) |
| AC-6 | instrumented | P0 | red | `MainRailHandoffKindTest.coldStartIntoNeverFocusableTab_failedInitialHandoffLeavesRailClosed_thenContentTakesFocus`: `startSampling` before `startMain(loadingRoot = "first")`; `awaitDrawer(HandingOff)` then `awaitDrawer(Closed)`; `setLoadingRoot(null)`; `awaitFocused(ROOT_CARD_FIRST)`; drawer `Closed`; no `Open` sampled | fails if a failed Initial reopens at a root (base `Open`) or late content does not focus after a Closed fail |
| AC-7 | instrumented | P1 | guard | `MainRailHandoffKindTest.centerOnNowSelectedTabDuringTabPick_isRefused_thirdTabSwitches`: `startMain(loadingRoot = "other")`; open rail; pick History; `awaitRoot("other")`, `HandingOff`, `railPicks == 1`; `focusSelectedRailItem` (History); Center -> `railPicks == 1`; Down, Center -> `awaitRoot("third")`, `awaitFocused(ROOT_CARD_FIRST)`, `railPicks == 2` | fails if `!tab.isSelected` is dropped (M7) or the probe selection does not follow picks |
| AC-8 | instrumented | P1 | guard | `MainRailHandoffKindTest.tabPickIntoNeverFocusableTab_failedHandoffReopensRail`: `startMain(loadingRoot = "other")`; open rail; pick History; `awaitDrawer(Open)` after the fail | fails if TabPick fail ends `Closed` at a root (M5) |
| AC-8 | instrumented | P1 | guard | `MainSideMenuFocusTraversalTest.selectingATabWhoseContentNeverTakesFocus_reopensTheRail` (existing) | same, at the drawer without Main |
| AC-9 | instrumented | P0 | guard | `MainRailBackTest`, `MainTabPushRailFocusTest`, `InitialDrawerContentFocusTest`, `MainSideMenuFocusTraversalTest`, `ShowAllRailCloseFocusTest` (existing, whole classes) | fails on any regression of pushed-screen fail (A5), Center during Dismiss (`centerOnCollapsedRail_duringHandoff_doesNotSwitchTab`, M8), corrective pick, root exit, dialog re-entry |
| AC-9 | instrumented | P1 | guard | `MainRailHandoffKindTest.rePickOfSelectedTab_closesRailOntoContent`: `startMain()`; open rail from `ROOT_CARD_FIRST`; Center on the selected item -> `awaitDrawer(Closed)` within 1 s, `awaitFocused(ROOT_CARD_FIRST)`, no `Open` sampled | fails if a re-pick is classified TabPick (content composed before the pick cannot confirm; 5 s then `Open`) (M10) |
| AC-10 | device | P0 | guard | D1 | exit sequence at a root with content unchanged |
| AC-11 | device | P0 | guard | D2 | tab pick and Back over Movies unchanged |
| AC-1 | device | P2 | red | D3 (only if Favourites is visible and `Empty`) | the trap on the real app |
| AC-12 | unit | P0 | red (M4) | `` DrawerStateTest.`a failed dismiss handoff leaves the rail closed even when reopening is allowed` `` | fails if `failHandoff` ignores the kind |
| AC-12 | unit | P0 | red (M4) | `` DrawerStateTest.`a failed initial handoff leaves the rail closed even when reopening is allowed` `` | same, Initial |
| AC-12 | unit | P0 | guard | `` DrawerStateTest.`a failed tab pick handoff reopens the rail` `` (renames `a failed handoff returns the rail to open`) | fails if every fail ends `Closed` (M5) |
| AC-12 | unit | P0 | guard | `` DrawerStateTest.`a failed tab pick handoff that must not reopen leaves the rail closed` `` (renames `a failed handoff that must not reopen leaves the rail closed`) | fails if `reopen = false` is ignored for TabPick |
| AC-12 | unit | P0 | guard | `` DrawerStateTest.`settling the active handoff closes the rail` `` as `@ParameterizedTest @EnumSource(HandoffKind::class)` (Initial started from `Closed` via `beginInitialHandoff`) | fails if settle depends on the kind or leaves `handoffKind` set |
| AC-12 | unit | P0 | red (M9) | `` DrawerStateTest.`a tab pick during the initial handoff restarts it as a tab pick with a new id` `` | fails if `beginHandoff(TabPick)` from an Initial HandingOff returns null or reuses the id; asserts `handoffKind == TabPick`, `handoffExpectsNewContent` |
| AC-12 | unit | P0 | red (M9) | `` DrawerStateTest.`the superseded initial id can neither settle nor fail the tab pick` `` | fails if the old id still matches |
| AC-12 | unit | P0 | red (M9) | `` DrawerStateTest.`a tab pick during a tab pick handoff restarts it with a new id` `` | fails if a corrective pick keeps the mistaken tab's id |
| AC-12 | unit | P0 | guard | `` DrawerStateTest.`a tab pick during a dismiss handoff is refused` `` | fails if `beginHandoff` restarts a Dismiss (returns non-null, kind changes) |
| AC-12 | unit | P1 | guard | `` DrawerStateTest.`a dismiss is refused unless the rail is open` `` | fails if Dismiss restarts a pending handoff |
| AC-12 | unit | P1 | guard | `` DrawerStateTest.`only a tab pick handoff waits on new content` `` (`@EnumSource`; replaces the two `handoffExpectsNewContent` cases) | fails if the derived flag is true for Dismiss or Initial |
| AC-12 | unit | P2 | guard | `` DrawerStateTest.`an initial handoff cannot be begun as a rail handoff` `` | fails if `require(kind != Initial)` is removed |
| AC-12 | unit | P2 | guard | `` DrawerStateTest.`a failed handoff clears its kind` `` | fails if `handoffKind` survives `failHandoff` |
| AC-12 | unit | P0 | guard | `check.full` (`./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`) green | compile fix at `MainSideMenuFocusTraversalTest.kt:150`, detekt, lint |
| R6 | instrumented | P2 | red | `MainRailHandoffKindTest.rePickOfSelectedTabAtNeverFocusableRoot_failedHandoffLeavesRailClosed`: fixture; Center on selected item; `awaitDrawer(Closed)` | records Open question 1 (base `Open`) |

Unchanged and green: `RailBackOwnerTest`, `ExitConfirmationTest`.

## Failing-first test

Two defects, written first, red on base before any production edit:
- defect 1: `MainRailHandoffKindTest.backTwiceFromRailAtNeverFocusableRoot_exitsDuringHandoff`; base `MainScreenComponent.kt:221` returns on every HandingOff Back, so `isFinishing` never turns true.
- defect 2: `MainRailHandoffKindTest.pickDuringInitialHandoff_switchesToPickedTab`; base `acceptsPickOf` (`MainScreenComponent.kt:304-308`) requires `handoffExpectsNewContent`, which `beginInitialHandoff` (`ModalNavigationDrawer.kt:142-145`) sets false.

Red run (qa-device, box, lease): every `red` instrumented row with production at base. In the worktree after the harness and test commit: `git checkout c9e98129 -- app/src/main app/src/androidTest/kotlin/com/kino/puber/ui/feature/main/component/MainSideMenuFocusTraversalTest.kt`, `make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.ui.feature.main.component.MainRailHandoffKindTest`, then `git checkout HEAD -- app/src/main app/src/androidTest`. Expected: the `red` rows fail at the assertion named in Proves (not in the fixture), the `guard` rows pass.

## Mutation runs

Each production change is proven by at least one test failing under its mutation. qa-device applies the mutation in the worktree, runs the listed tests, records pass/fail, reverts (`git checkout HEAD -- <file>`). Instrumented runs: `TESTS=com.kino.puber.ui.feature.main.component.MainRailHandoffKindTest` unless named otherwise.

| # | Production change | Mutation | Must fail |
|---|---|---|---|
| M1 | `RailOpenBackHandler` armed Dismiss exit | delete the `finish()` branch (HandingOff always swallowed) | `backTwiceFromRailAtNeverFocusableRoot_exitsDuringHandoff` |
| M3 | same, `isArmed` gate | finish on every Dismiss HandingOff Back | `backDuringDismissHandoffAfterArmExpired_isSwallowed` |
| M4 | `failHandoff` outcome per kind | `Open` whenever `reopen` (base rule) | DrawerStateTest dismiss and initial fail cases; `failedBackHandoffAtNeverFocusableRoot_*`, `rightFromRailAtNeverFocusableRoot_failedHandoffLeavesRailClosed`, `coldStartIntoNeverFocusableTab_failedInitialHandoffLeavesRailClosed_thenContentTakesFocus` |
| M5 | same, TabPick keeps reopening | always `Closed` | `a failed tab pick handoff reopens the rail`; `tabPickIntoNeverFocusableTab_failedHandoffReopensRail`; `pickDuringInitialHandoff_restartsAsTabPick_outgoingTabCannotSettle` |
| M6 | `acceptsPickOf` kind rule | restore `handoffExpectsNewContent && !tab.isSelected` | `pickDuringInitialHandoff_switchesToPickedTab` |
| M7 | `acceptsPickOf` `!tab.isSelected` | drop the clause | `centerOnNowSelectedTabDuringTabPick_isRefused_thirdTabSwitches`, `centerOnSelectedTabDuringInitialHandoff_switchesNothing` |
| M8 | `acceptsPickOf` `!= Dismiss` | drop the clause | `MainRailBackTest.centerOnCollapsedRail_duringHandoff_doesNotSwitchTab` |
| M9 | `beginHandoff` restart from HandingOff | return null outside `Open` (base); second variant: restart with the same id | DrawerStateTest restart cases; `pickDuringInitialHandoff_restartsAsTabPick_outgoingTabCannotSettle` (run 3 times: detection needs the outgoing tab to compose one frame before the swap) |
| M10 | item click kind | `TabPick` for the selected tab | `rePickOfSelectedTab_closesRailOntoContent` |
| M11 | Right key kind | `TabPick` | `rightFromRailAtNeverFocusableRoot_failedHandoffLeavesRailClosed` |
| M12 | `RailOpenBackHandler` kind | `TabPick` | `failedBackHandoffAtNeverFocusableRoot_railStaysClosed_leftReopensIt` (fail ends `Open`) |

Not mutation-proven: the `handoffKind == Dismiss` part of the exit gate (R8).

## Manual device checks

Box `192.168.1.110:5555`, package `com.kino.puber.stage` only, lease held, worktree dev APK via `tools/android-apk-install-preserve`, keys by `adb -s 192.168.1.110:5555 shell input keyevent <code>` (BACK 4, DPAD_LEFT 21, DPAD_DOWN 20, DPAD_CENTER 23), a screenshot after each expected state. Must not happen in any check: adding or removing a favourite, a long press or context menu on a card, touching `com.kino.puber`, uninstall or data clear.

D0. Precondition (R11): before installing the worktree APK, refresh the session with a master build and `make DEVICE=192.168.1.110:5555 auth-save`. Expected: the app opens on Home, not pairing.

D1 (AC-10). Cold start, wait until Home content is focused. BACK, BACK, BACK about 0.5 s apart. Expected: rail opens; rail collapses and the "press Back again" toast shows; the launcher is shown. Must not: rail reopening by itself, an ignored Back, exit on the first or second Back. Repeat on Movies and History (pick them from the rail first, wait for content focus).

D2 (AC-11). Cold start, Home content focused. DPAD_LEFT (rail opens), DPAD_DOWN to Movies, DPAD_CENTER. Expected: Movies shown, a Movies card focused, rail collapsed. DPAD_LEFT, then BACK. Expected: rail collapses, focus on the card it was opened from. Must not: rail open again after 5 s, nothing focused, Home still shown.

D3 (AC-1, AC-2; only if the Favourites tab is visible and its screen shows the empty text; otherwise record "not run: Favourites not empty" and stop). Pick Favourites from the rail; wait 6 s (TabPick fail: rail reopens, AC-8). BACK. Expected: rail collapses, toast. BACK within 2 s. Expected: launcher. Relaunch, pick Favourites, wait 6 s, BACK, wait 6 s. Expected: rail stays collapsed, nothing focused, no toast reappearing. DPAD_LEFT. Expected: rail opens. Must not: rail reopening by itself after the collapse, Back doing nothing.

## UNTESTABLE

None. AC-2 to AC-8 are instrumented-only on the device side (brief: Device feasibility).
