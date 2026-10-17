# Readiness 1 - tab-stack-lost-on-root-push

Judge: final-judge (mode readiness, round 1). Date: 2026-10-17.
Inputs read: spec `docs/superpowers/specs/2026-10-17-tab-stack-lost-on-root-push.md` (approved, committed at a7c20c16), plan `docs/superpowers/plans/2026-10-17-tab-stack-lost-on-root-push.md` (uncommitted in the worktree), `.flow/config.yaml`, `.flow/roles/final-judge.md`, `AGENTS.md`, the cited sources at worktree HEAD a7c20c16, and Voyager `2.2.21-1.10.3` `Navigator.class` (javap).

## Verdict: READY

## 1. AC coverage (plan task + Test design row)

| AC | Plan task | Test design row | OK |
|----|-----------|-----------------|----|
| AC-1 | T1 + T2 (Task 1), production (Task 3), M1 scroll (after Task 3) | AC-1 instrumented T1/T2, AC-1 (scroll) device M1 | yes |
| AC-2 | Task 5 (conditional on M1 steps 7-8), T8 | AC-2 device M1, AC-2 instrumented T8 (P2) | yes |
| AC-3 | T2 (Task 1) | AC-3 T2 | yes |
| AC-4 | T1 (Task 1), D4/D5/D6 (Task 3) | AC-4 instrumented T1, AC-4 unit rename | yes |
| AC-5 | T1 + whole class run by qa-device (Task 3 Done when) | AC-5 | yes |
| AC-6 | Task 3 grep Done-when + global constraint | AC-6 manual read | yes |
| AC-7 | T4 (Task 2), M4 (after Task 3) | AC-7 instrumented T4, device M4 | yes |
| AC-8 | T2 switch (Task 1), M5 + M6 (after Task 3) | AC-8 (switch), AC-8 (Settings) | yes |
| AC-9 | T5a/T5b (Task 2), M3 (after Task 3) | AC-9 instrumented T5, device M3 | yes |
| AC-10 | T6 (Task 2) | AC-10 T6 | yes |
| AC-11 | Task 4 (full gate + navigation.md) | AC-11 | yes |

No AC is uncovered.

## 2. Citation sample (resolved at worktree HEAD a7c20c16)

All checked citations resolve to the claimed symbol. Sampled (16):

1. `core/ui/navigation/component/TabComponent.kt:31` `LocalTabAppRouterHolder`; `:76-126` `TabComponent`; `:99` the `CompositionLocalProvider`; `:104` `disposeNestedNavigators = false`; `:128-138` `PuberCurrentTab` with `tabNavigator.saveableState(currentTab.key)` at `:134`. Correct.
2. `FlowComponent.kt:100` `checkNotNull(LocalPuberKoinScope.current) { "FlowComponent needs an enclosing DIScope" }`; `:123-126` root `Navigator(screen, onBackPressed)` with no dispose behaviour argument. Correct.
3. `FlowComponent.kt:583-599`: tab-flow `Navigator` with `key = tabFlowNavigatorKey(...)`, the `DisposableEffect(tabHost, navigator)` bind, `reappearingTop = { navigator.items.firstOrNull() }` at `:596`, comment at `:589-591`. Correct.
4. `FlowComponent.kt:537-542` `TabRootScreen` remember; `:651-665` `RestoreTabContentFocusEffect`; `:697-709` `replaceTabRootIfChanged`; `:247-248` `NewRoot -> puberReplaceAll`; `:492-503` `CurrentScreen` with `navigator.saveableState(screenKey)`. Correct.
5. `NavTelemetry.kt:22-31` KDoc with the "Today that is the tab root" sentence at `:25-27`; `:33` `Binding`; `:39-40` `bind`; `:42-47` ungated `unbind`; `:64` the only `isEnabled` gate (in `recordNav`). Correct.
6. `PuberTab.kt:25,37` `navigationSlotKey = "Tab:${screen.key}"`, `key = navigationSlotKey`; `:48-68` `Content()` resolves no VM. Correct.
7. `TabHostTelemetryWiringTest.kt:148-173` T1 with pinning comment `:148-151`, PUSH assertions `:159-160`, absence check `:163`; fixtures `:191-206`, `:283-295`, `:305-311`, `:313-319`, `:321-333`, `:335-360`, hard-coded `WiringChildScreen` at `:348`, `localTag = null` at `:309`. Correct.
8. `TabFlowLifecycleTest.kt:122,131,134,137,175` are exactly the five `assertTextContains("content=N")` on `HISTORY_TAG`; text built at `:247` as `"content=$contentGeneration vm=${vm.generation}"`; `:211-216` `TabComponent { PuberCurrentTab() }`. Correct.
9. `NavTelemetryTest.kt:211` `unbindFreezesTheReappearingTopNotTheLiveTop`, `:232` `unbindWhileOffThenEnabledThenBackNamesTheTabRoot`. Correct.
10. `App.kt:101` `router.newRootScreen(router.screens.auth())`; `App.kt:118-120` warm `newRootScreens(listOf(main, target))`; `AuthVM.kt:134` `router.newRootScreen(router.screens.main())`; `MainVM.kt:37` `tabAppRouterHolder = TabAppRouterHolder(router.screens)`; `AppRouter.kt:70` `newRootScreen(vararg)`. Correct.
11. `LifecycleAction.kt:10-26` signature `(event, onAction, action)` on `LocalLifecycleOwner`; `HistoryScreen.kt:54-58` usage with `CommonAction.OnResume` (exists in `core/ui/uikit/model/Actions.kt:29`). Correct.
12. `ShowAllScreenContent.kt:38-64` `internal ShowAllScreenContent`; `:66-116` private `ShowAllContentBody`; `:71` `rememberFocusRequesterOnLaunch`; `:72` `rememberLazyGridState`; `:96` `focusRequester(mainContentFocus)`; `:103-107` `VideoItemHorizontal` call without `modifier`. Correct.
13. `FocusOnLaunchRequester.kt:30` `LocalContentFocusActive`; `:51` saveable `isFocusRequested` keyed by `LocalScreenKey`. `FocusTargetState.kt:40-57` `rememberReconciledItemFocus`; `:295-300` private `requestAfterAnchorRestore`; `:309` constant 3. `VideoItemHorizontal.kt:48-49` `modifier` first param; `:166-167` `state.title`. `VideoItemUIState.kt:50-71` with `id: Int`. Correct.
14. `MainScreenComponent.kt:137-141` `LocalContentFocusActive provides (drawer != Open)`; `:292-304` `TabComponent { ... PuberCurrentTab() }`. `HomeCollectionBackFocusTest.kt:238-250` D-pad helper, `:300-310` `TabComponent`. `NetworkDiagnosticsContentFocusTest.kt:159-170` `StateRestorationTester`. Correct.
15. Previous spec `2026-10-17-telemetry-tab-host-back-label.md:48` and `:194` are the two AC-6 texts, `:212` A1, `:213` A2, `:232` the amendment note. `docs/architecture/navigation.md` "Tabs" at `:83-93`, file ends at `:93`. Correct.
16. Voyager bytecode (`voyager-navigator-release.aar`, `Navigator.class`): `saveableState(String, Screen, Function2)` writes `stateKeys`, calls `ScreenLifecycleKt.rememberScreenLifecycleOwner`, then `SaveableStateHolder.SaveableStateProvider`; `dispose(Screen)` filters `stateKeys` and calls `SaveableStateHolder.removeState`. Matches the spec's root-cause reading. `TabRouter` uses `MutableSharedFlow(replay = 1)`, which T4's relogin step relies on.

Minor line drift only: the plan says `WiringTabHostScreen :281-295`; `:281` is the KDoc, the class starts at `:283`. Not material.

## 3. Build state and ordering

- Tasks 1 and 2 touch test code only. Their Done-when steps are the androidTest compile gate and the fast gate. T1, T2 and T5b are meant to be red on the device before the fix. That is the failing-first proof, and the explicit stop point fences it: it is not a red build, because check.full runs JVM tests only.
- Task 3 holds every production change in one commit: holder, `PuberCurrentTab`, telemetry flip, KDoc and JVM test rename. The telemetry flip and the holder therefore land together, so no commit has the stack restored while the Back label is still wrong (risk K2).
- Task 4 (docs) runs after Task 3. Task 5 is conditional and depends on M1 of the Task 3 build. The order follows the dependencies, and each task consumes only what earlier tasks produce (the `WiringTabContent` signature from Task 1 to Task 2).

## 4. Scope and open questions

- Out of scope is respected:
  - The root `Navigator` at `FlowComponent.kt:123-126` is explicitly untouched.
  - Removed tabs get no `removeState` (D10).
  - Grid focus is not generalised: Task 5 is limited to one file, and a follow-up is recorded instead.
  - The Settings spinner is observed only (M5).
- Editing the previous run's spec (Task 4) is decided in D11. It only annotates that spec.
- Q1-Q5 are all answered, and none of them blocks.

## Non-blocking notes for the implementer and the gate

1. T5a and T5b assert `> before`. A re-entering `LifecycleAction` re-adds its observer, and the observer is replayed up to RESUMED, so these tests prove that ON_RESUME is delivered. They do not prove that it is delivered exactly once. Duplicate refreshes (the "two per return" item in M3) rest on the M3 device check alone.
2. The full gate (detekt, lint) first runs in Task 4. A detekt finding in Task 3's production code would only surface there. Running the full gate at the end of Task 3 is cheaper.
3. Pre-existing, not widened by this run, and worth one line in the gate:
   - Tab-flow screens' states in the root `LocalNavigatorStateHolder` are never removed, because the tab-flow navigator is never disposed.
   - Tab-flow screens' `ScreenLifecycleStore` entries are never removed either.
   - A fresh host after relogin can therefore revive a tab root's screen-level saveable state, such as a list's scroll position. T4 checks only that the stack restarts at the root.
4. The plan is still uncommitted in the worktree. Commit it, with the Readiness section pasted in, before Task 1, so that Task 1's `git diff HEAD~1 --stat` check sees only the test file.

## Must fix

none
