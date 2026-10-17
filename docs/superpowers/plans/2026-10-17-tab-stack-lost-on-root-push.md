# Keep a tab's stack across a root push and Back - plan

**Spec:** `docs/superpowers/specs/2026-10-17-tab-stack-lost-on-root-push.md`
**Goal:** Make the tab host own the tab subtree's saved state, so that a tab-local stack (for example Show all) survives a root push (Details) and Back. Flip `TabHostTelemetry`'s `reappearingTop` to the live top to match.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`. Full gate: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`. Run both from the worktree root `/home/user1/orca/workspaces/Puber/tab-stack-lost-on-root-push`. Never use `./gradlew` there. Set `AGENT_GRADLE_SERIAL=1` when another worktree may build.
- AndroidTest compile gate, for every task that touches `app/src/androidTest`: `./tools/agentw :app:compileDevDebugAndroidTestKotlin`.
- Instrumented tests are run only by qa-device (lease, exact serial), never by the implementer: `make GRADLE=./tools/agentw DEVICE=<serial> itest TESTS=<fqcn>`. The two classes:
  - `com.kino.puber.core.ui.navigation.component.TabHostTelemetryWiringTest`
  - `com.kino.puber.core.ui.navigation.component.TabFlowLifecycleTest`
  Never run `connected*AndroidTest` directly.
- One task means: a green fast gate, green tests of the touched code, and one commit.
  - The commit message is one imperative English sentence with no prefix (commit_style).
  - Never add a `Co-Authored-By` line.
- The plan is self-contained. The implementer reads this file, the spec, and the files cited below, and nothing else.
- Production paths below are relative to `app/src/main/java/com/kino/puber/`.
- Test paths are given in full from the repo root.
- Every `path:line` was re-read in the worktree at HEAD `a7c20c16` (base `f14dcc84`).
- Do not touch the root `Navigator` at `core/ui/navigation/component/FlowComponent.kt:123-126` (D3, out of scope).
- Do not add a `Telemetry.isEnabled` gate to `TabHostTelemetry.unbind` (`core/ui/navigation/component/NavTelemetry.kt:42-47`) (AC-6).
- **Stop point.** Tasks 1 and 2 change test code only. After Task 2's commit the implementer stops. The conductor has qa-device run both instrumented classes on that commit. That run is the failing-first proof and the device baseline. The implementer resumes at Task 3 only when the conductor says so.
- **Expected outcome on the Task 2 commit** (pre-fix), `TabHostTelemetryWiringTest`:
  - Red: `tabLocalPushThenRootPushThenBackNamesTheScreenShownAfterBack` (T1), `rootPushAndBackRestoresBothTabStacks` (T2), `resumeIsDeliveredToRestoredTabLocalScreenAfterRootPushAndBack` (T5b).
  - Green: the five other existing cases, `replacedHostStartsAtTabRootsWhenShownAgain` (T4), `resumeIsDeliveredToTabRootAfterRootPushAndBack` (T5a).
  - `TabFlowLifecycleTest` (T6): record the result as it comes (risk K7). A red there before the fix is independent of this run and goes into the run notes.
- **Expected outcome after Task 3:** both classes are entirely green.

### Background the implementer needs (verified)

- **Where the stack lives today.** `PuberCurrentTab` (`core/ui/navigation/component/TabComponent.kt:128-138`) stores the tab subtree with `tabNavigator.saveableState(currentTab.key)` (`:134`).
  - Voyager registers `"Tab:<screen>"` keys for it in the tab list navigator's `stateKeys`.
  - On a root push, the root `Navigator`'s default nested dispose removes those keys. That root navigator is at `FlowComponent.kt:123-126`.
  - The removed state includes the tab-flow `Navigator` stack (`FlowComponent.kt:583-587`). On Back, the tab restarts at `TabRootScreen` (`FlowComponent.kt:537-542`).
- **Tab key.** `PuberTab.key` is `"Tab:${screen.key}"` (`core/ui/navigation/PuberTab.kt:25,37`), a `String`, so it is Bundle-saveable.
- **Telemetry binding today.** The binding is set in `TabFlowNavigator` (`FlowComponent.kt:588-599`), with `reappearingTop = { navigator.items.firstOrNull() }` (`:596`).
- **Call sites of `TabComponent` + `PuberCurrentTab`.** All of them already nest `PuberCurrentTab` inside `TabComponent`:
  - `ui/feature/main/component/MainScreenComponent.kt:292-304`
  - `app/src/androidTest/kotlin/com/kino/puber/ui/feature/home/component/HomeCollectionBackFocusTest.kt:300-310`
  - `app/src/androidTest/kotlin/com/kino/puber/core/ui/navigation/component/TabHostTelemetryWiringTest.kt:287-292`
  - `app/src/androidTest/kotlin/com/kino/puber/core/ui/navigation/component/TabFlowLifecycleTest.kt:211-216`
- **Production logout and relogin** both use `router.newRootScreen(...)`: logout at `ui/feature/root/component/App.kt:101`, relogin at `ui/feature/auth/vm/AuthVM.kt:134`.
  - Each `MainScreen` gets a new `MainVM`, and with it a new `TabAppRouterHolder` (`ui/feature/main/vm/MainVM.kt:37`).
  - The root runner handles `NewRoot` with `puberReplaceAll` (`FlowComponent.kt:247-248`).

---

## Tasks

### Task 1: Flip the tab-local telemetry itest and add the two-tab stack itest (failing first)

**Covers:** AC-1, AC-3, AC-4, AC-5, AC-8 (switch). Tests T1, T2.
**Files:**
- Test: `app/src/androidTest/kotlin/com/kino/puber/core/ui/navigation/component/TabHostTelemetryWiringTest.kt` (test code only)

**Consumes:** the existing fixture: `showHost` `:191-206`, `openTab` `:208-212`, `click` `:214-218`, `waitForTag` `:220-224`, `mark`/`navsAfter`/`screensAfter` `:226-240`, `WiringTabHostScreen` `:281-295`, `WiringOtherScreen` `:305-311`, `WiringChildScreen` `:313-319`, `WiringDetailsScreen` `:321-333`, `WiringTabContent` `:335-360`. Inside tab content, `LocalPuberKoinScope.current.get<AppRouter>()` is the tab router. `navigateTo` a `RootPuberScreen` is forwarded to the root. `back()` pops the tab stack.
**Produces:**
- Tags `OTHER_LOCAL_TAG`, `OTHER_CHILD_TAG`, `OTHER_CHILD_DETAILS_TAG`, `CHILD_BACK_TAG`, `OTHER_CHILD_BACK_TAG`.
- Screen `WiringOtherChildScreen`.
- The `WiringTabContent(tag, title, localTag, localScreen, detailsTag, backTag)` signature, which Task 2 reuses.

- [x] Fixture: change `WiringTabContent` (`:335-360`).
  - Add a `localScreen: PuberScreen?` parameter. The local button navigates to it in place of the hard-coded `WiringChildScreen` (`:348`).
  - Add `backTag: String? = null`. When it is non-null, a button with that tag calls `router.back()`.
  - Update the callers:
    - Home: `localTag = HOME_LOCAL_TAG`, `localScreen = WiringChildScreen`.
    - Other: `localTag = OTHER_LOCAL_TAG`, `localScreen = WiringOtherChildScreen`. Today it passes `localTag = null` at `:309`.
    - Child: `backTag = CHILD_BACK_TAG`.
- [x] Fixture: add `@Parcelize private data object WiringOtherChildScreen : PuberScreen`. It uses `WiringTabContent(OTHER_CHILD_TAG, "Other child", localTag = null, localScreen = null, detailsTag = OTHER_CHILD_DETAILS_TAG, backTag = OTHER_CHILD_BACK_TAG)`. Add the new tag constants next to `:48-56`.
- [x] T1, D7: rewrite `tabLocalPushThenRootPushThenBackNamesTheScreenShownAfterBack` (`:148-173`).
  - Delete the pinning comment `:148-151`.
  - Keep `:154-161` as they are (setup, the tab-local push, and the PUSH assertions at `:159-160`).
  - Then `click(BACK_TAG, CHILD_TAG)`, and assert that no `HOME_TAG` node exists.
  - With `navs = navsAfter(mark)`, assert:
    - `navs.first().cmd == NavCmd.BACK` and `navs.first().to == WiringChildScreen::class.java`.
    - `screensAfter(mark).all { it == WiringChildScreen::class.java }`.
    - `Telemetry.screen == WiringChildScreen::class.java`.
    - `navs.none { it.to == WiringHomeScreen::class.java || it.from == WiringHomeScreen::class.java }`.
- [x] T2: new test `rootPushAndBackRestoresBothTabStacks` (AC-3, AC-8 switch).
  1. `showHost()`; `openTab(homeTab, HOME_TAG)`; `click(HOME_LOCAL_TAG, CHILD_TAG)`.
  2. `openTab(otherTab, OTHER_TAG)`; `click(OTHER_LOCAL_TAG, OTHER_CHILD_TAG)`.
  3. `click(OTHER_CHILD_DETAILS_TAG, DETAILS_TAG)`; `click(BACK_TAG, OTHER_CHILD_TAG)`; assert that no `OTHER_TAG` node exists.
  4. `openTab(homeTab, CHILD_TAG)`; assert that no `HOME_TAG` node exists.
  5. `click(CHILD_BACK_TAG, HOME_TAG)`; assert that no `CHILD_TAG` node exists. This shows the depth is 2, not a child recreated over nothing.
  6. `openTab(otherTab, OTHER_CHILD_TAG)`; `click(OTHER_CHILD_BACK_TAG, OTHER_TAG)`.
  - Use the `composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()` form for every absence check, as `:163` does.
- [x] No sleeps. Waits go only through `waitForTag`/`click`. The other five existing cases stay unchanged.
- [x] Run the androidTest compile gate and the fast gate. Commit: "Expect the tab-local screen after a root push and Back in the tab host wiring test".

**Done when:**
- `./tools/agentw :app:compileDevDebugAndroidTestKotlin` and `./tools/agentw :app:compileDevDebugKotlin` are green.
- `git diff HEAD~1 --stat` shows only `TabHostTelemetryWiringTest.kt`.

---

### Task 2: Add the host-replacement and resume guards and fix the TabFlowLifecycleTest text matches

**Covers:** AC-7 (T4), AC-9 (T5a, T5b), AC-10 (T6).
**Files:**
- Test: `app/src/androidTest/kotlin/com/kino/puber/core/ui/navigation/component/TabHostTelemetryWiringTest.kt`
- Test: `app/src/androidTest/kotlin/com/kino/puber/core/ui/navigation/component/TabFlowLifecycleTest.kt`

**Consumes:**
- Task 1's `WiringTabContent` signature.
- `core/ui/uikit/component/LifecycleAction.kt:10-26`: `LifecycleAction(event, onAction, action)`, observing `LocalLifecycleOwner`.
- `CommonAction.OnResume`, as used at `ui/feature/history/component/HistoryScreen.kt:54-58`.
- `AppRouter.newRootScreen(vararg screen)` (`core/ui/navigation/AppRouter.kt:70`).
- `TabAppRouterHolder(screens)` / `dispose()` (`TabComponent.kt:49-74`).

**Produces:** tags `LOGOUT_TAG`, `AUTH_TAG`, `AUTH_RELOGIN_TAG`; screen `WiringAuthScreen`; object `WiringResumes`.

- [x] T6, D8: in `TabFlowLifecycleTest.kt`, add `substring = true` to every `assertTextContains` on `HISTORY_TAG`:
  - `:122` (`"content=0"`), `:131` (`"content=0"`), `:134` (`"content=1"`), `:137` (`"content=2"`), `:175` (`"content=2"`).
  - The node text is `"content=N vm=M"` (`:247`).
  - Change nothing else in that class.
- [x] Fixture: add a `LOGOUT_TAG` button to `WiringDetailsScreen` (`:321-333`) that calls `router.newRootScreen(WiringAuthScreen)`. There, `router` is the root `AppRouter`.
- [x] Fixture: add `@Parcelize private data object WiringAuthScreen : PuberScreen`.
  - It shows `Text("Auth", Modifier.testTag(AUTH_TAG))`.
  - It has a button `AUTH_RELOGIN_TAG` that calls `requireNotNull(LocalPuberKoinScope.current).get<AppRouter>().newRootScreen(WiringTabHostScreen())`.
  - This mimics `App.kt:101` and `AuthVM.kt:134`.
- [x] Fixture: add `private object WiringResumes`.
  - A synchronized `MutableMap<String, Int>` with `record(tag)`, `of(tag): Int` (0 when absent) and `clear()`.
  - Call `clear()` in `setUp` and in `tearDown`.
  - In `WiringTabContent`, call `LifecycleAction(Lifecycle.Event.ON_RESUME, onAction = { WiringResumes.record(tag) }, action = CommonAction.OnResume)`. This uses the production `LifecycleAction`, so the owner swap (risk K4) is exercised as the app does it.
- [x] T4: new test `replacedHostStartsAtTabRootsWhenShownAgain` (AC-7).
  1. `showHost()`; `openTab(homeTab, HOME_TAG)`; `click(HOME_LOCAL_TAG, CHILD_TAG)`; `click(CHILD_DETAILS_TAG, DETAILS_TAG)`.
  2. `click(LOGOUT_TAG, AUTH_TAG)`; assert that no `HOME_TAG` node and no `CHILD_TAG` node exists.
  3. Mirror the new `MainVM` of a fresh MainScreen. In `composeRule.runOnIdle`:
     - dispose the old `tabAppRouterHolder`;
     - assign `tabAppRouterHolder = TabAppRouterHolder(ScreensImpl)`;
     - call `WiringHost.bind(tabRouter, tabAppRouterHolder)`.
     `tearDown` then disposes the new holder.
  4. `click(AUTH_RELOGIN_TAG, HOME_TAG)`. The tab router replays its last `Open`, so Home opens. Assert that no `CHILD_TAG` node exists.
  - Green before and after the fix. It guards the new retention: the retention must end with the host.
- [x] T5a: new test `resumeIsDeliveredToTabRootAfterRootPushAndBack`.
  - `showHost()`; `openTab(homeTab, HOME_TAG)`.
  - `val before = WiringResumes.of(HOME_TAG)`.
  - `click(HOME_DETAILS_TAG, DETAILS_TAG)`; `click(BACK_TAG, HOME_TAG)`.
  - Inside `composeRule.runOnIdle`, assert `WiringResumes.of(HOME_TAG) > before`.
  - Green before and after.
- [x] T5b: new test `resumeIsDeliveredToRestoredTabLocalScreenAfterRootPushAndBack`.
  - Same as T5a with `click(HOME_LOCAL_TAG, CHILD_TAG)` first.
  - Read `before` from `CHILD_TAG`. Use `CHILD_DETAILS_TAG`. Do `click(BACK_TAG, CHILD_TAG)`.
  - Assert `WiringResumes.of(CHILD_TAG) > before`.
  - Red before the fix: the Child is not restored, so the `CHILD_TAG` wait times out.
- [x] Run the androidTest compile gate and the fast gate. Commit: "Guard host replacement and resume delivery in the tab host wiring test and match lifecycle test text by substring".
- [ ] **Stop.** Report the commit SHA to the conductor. qa-device runs both classes on this commit; the expected outcome is under Global constraints. Wait for the conductor before Task 3.

**Done when:**
- Both compile gates are green.
- `git diff HEAD~1 --stat` shows only the two test files.
- `grep -n 'assertTextContains("content=' app/src/androidTest/kotlin/com/kino/puber/core/ui/navigation/component/TabFlowLifecycleTest.kt` shows `substring = true` on all five lines.

---

### Task 3: Let the tab host own the tab subtree's saved state and freeze the live top for telemetry

**Covers:** AC-1, AC-3, AC-4, AC-5, AC-6, AC-7, AC-9, AC-10 (production side). Design D1, D2, D4, D5, D6.
**Files:**
- Modify: `core/ui/navigation/component/TabComponent.kt`
  - Add the holder and the CompositionLocal: `LocalTabAppRouterHolder` `:31`, `TabComponent` `:76-126`, its `CompositionLocalProvider` `:99`.
  - Change `PuberCurrentTab` `:128-138`.
- Modify: `core/ui/navigation/component/FlowComponent.kt:588-599`. Change only the lambda at `:596` and the comment at `:589-591`.
- Modify: `core/ui/navigation/component/NavTelemetry.kt:22-31`. KDoc only.
- Test: `app/src/test/kotlin/com/kino/puber/core/ui/navigation/NavTelemetryTest.kt:232`. Rename only.

**Consumes:**
- `TabHostTelemetry.bind(host, top, reappearingTop)` (`NavTelemetry.kt:39-40`). The two-lambda API stays.
- The `checkNotNull(LocalPuberKoinScope.current) { ... }` pattern (`FlowComponent.kt:100`).

**Produces:** `internal val LocalTabSubtreeStateHolder: ProvidableCompositionLocal<SaveableStateHolder?>` in `TabComponent.kt`. `PuberCurrentTab` keeps its public signature `fun PuberCurrentTab()`.

- [x] D1: in `TabComponent.kt`:
  - Next to `LocalTabAppRouterHolder` (`:31`), add, with a one-line KDoc ("The tab host's holder for tab subtrees; it lives as long as the host, not as long as the covered tab."):
    ```kotlin
    internal val LocalTabSubtreeStateHolder = staticCompositionLocalOf<SaveableStateHolder?> { null }
    ```
  - In `TabComponent`, before the provider at `:99`, add `val subtreeStateHolder = rememberSaveableStateHolder()`.
  - Provide it together with the router holder: `CompositionLocalProvider(LocalTabAppRouterHolder provides holder, LocalTabSubtreeStateHolder provides subtreeStateHolder) { ... }`.
  - Imports: `androidx.compose.runtime.saveable.SaveableStateHolder`, `androidx.compose.runtime.saveable.rememberSaveableStateHolder`.
- [x] D2: rewrite the body of `PuberCurrentTab`:
  ```kotlin
  val tabNavigator = LocalTabNavigator.current
  val currentTab = tabNavigator.current
  val stateHolder = checkNotNull(LocalTabSubtreeStateHolder.current) {
      "PuberCurrentTab needs an enclosing TabComponent"
  }
  key(currentTab.key) {
      // Not tabNavigator.saveableState: Voyager registers that state on the tab list navigator, and
      // the root navigator's nested dispose removes it when a root push covers the host, losing the
      // tab's stack. The host-owned holder keeps it until the host itself leaves.
      stateHolder.SaveableStateProvider(currentTab.key) {
          currentTab.Content()
      }
  }
  ```
- [x] D4: in `FlowComponent.kt`:
  - Change `:596` to `reappearingTop = { navigator.lastItemOrNull },`.
  - Replace the comment at `:589-591` with: "The tab host keeps this tab's saved stack while a root push covers it, so the screen that reappears after Back is the live top; unbind freezes it."
  - Change nothing else in the file.
- [x] D5: in `NavTelemetry.kt:25-27`, replace the sentence "Today that is the tab root, not the top: covering the host makes Voyager dispose the covered tab and its saved stack (run tab-stack-lost-on-root-push)." with "That is the tab's live top: the tab host keeps the tab's saved state while it is covered."
  - Keep the rest of the KDoc, including the telemetry-off sentence (`:28-30`), word for word.
  - Do not touch `unbind` (`:42-47`).
- [x] D6: rename `unbindWhileOffThenEnabledThenBackNamesTheTabRoot` (`NavTelemetryTest.kt:232`) to `unbindWhileOffThenEnabledThenBackNamesTheReappearingTop`. Leave the body unchanged.
- [x] Run the fast gate. Also run `./tools/agentw testDevDebugUnitTest --tests com.kino.puber.core.ui.navigation.NavTelemetryTest` and the androidTest compile gate.
- [x] Commit: "Keep each tab's saved stack in the tab host so a root push and Back return to it".
- [ ] Report the SHA. qa-device then runs both instrumented classes; all of them are expected green. qa-device also runs device checks M1 (baseline on the Task 2 build if not done, then on this build), M3, M4, M5 and M6 from the spec's Test design. M1 steps 7-8 decide Task 5.

**Done when:**
- The fast gate, the androidTest compile gate and `NavTelemetryTest` are green.
- `grep -n "saveableState" app/src/main/java/com/kino/puber/core/ui/navigation/component/TabComponent.kt` has no hits.
- `grep -n "items.firstOrNull" app/src/main/java/com/kino/puber/core/ui/navigation/component/FlowComponent.kt` has no hit in `TabFlowNavigator`.
- `grep -n "isEnabled" app/src/main/java/com/kino/puber/core/ui/navigation/component/NavTelemetry.kt` shows only the `recordNav` gate (`:64` today) (AC-6).
- Settled by qa-device: both instrumented classes are green on this commit (AC-5, AC-10).

---

### Task 4: Describe the host-owned tab state in the architecture docs and annotate the previous spec

**Covers:** AC-11. Design D11.
**Files:**
- Modify: `docs/architecture/navigation.md`. Append one paragraph to "Tabs" (`:83-93`, which ends the file at `:93`).
- Modify: `docs/superpowers/specs/2026-10-17-telemetry-tab-host-back-label.md`. Annotate AC-6 at `:48` (and its copy at `:194`), plus the amendment note at `:232`.

**Consumes:** Task 3's `LocalTabSubtreeStateHolder` and `PuberCurrentTab` behaviour.
**Produces:** nothing in code.

- [x] Write the `navigation.md` paragraph in the doc's style (claims name their file). It says:
  - `TabComponent` (`core/ui/navigation/component/TabComponent.kt`) owns a `SaveableStateHolder` for tab subtrees.
  - `PuberCurrentTab` provides it per tab key, so a tab's stack survives both a tab switch and a root push that covers the host.
  - Why: registering the state through Voyager's `tabNavigator.saveableState` would put it on the tab list navigator. Voyager's nested dispose of the covered `PuberTab` would then remove it.
  - The holder dies with the host: logout or session expiry replaces `MainScreen`, and a fresh host starts at tab roots.
  - `TabHostTelemetry` therefore freezes the tab's live top on `unbind`.
- [x] In the previous spec, append to AC-6 at `:48`, and to the AC-6 copy at `:194`: " Restored by tab-stack-lost-on-root-push: Back names the tab-local screen again."
- [x] At `:232`, append: " Done in tab-stack-lost-on-root-push: `reappearingTop` is the live top (`FlowComponent.kt` `TabFlowNavigator`), and `unbind` stays ungated."
- [x] Run the fast gate and the full gate.
- [ ] Commit: "Document that the tab host owns the tab subtree saved state".

**Done when:**
- `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` is green.
- `grep -n "SaveableStateHolder" docs/architecture/navigation.md` has a hit.
- `grep -n "Restored by tab-stack-lost-on-root-push" docs/superpowers/specs/2026-10-17-telemetry-tab-host-back-label.md` has two hits.

---

### Task 5 (conditional): Return focus to the card that opened Details in the restored Show all grid

**Run only if** qa-device's M1 on the Task 3 build shows that after Back focus is NOT on the card that opened Details: it is on another card, on the grid only, or nowhere. Then:
- **Focus is on the noted card:** skip this task. Record "Task 5 skipped: M1 shows focus on the noted card" in the Implementation record.
- **Task is run:** the conductor records the follow-up idea for other grid and non-`LazyListState` tab screens (spec Q5). The implementer does not.

**Covers:** AC-2. Design D9. Tests T8, plus device checks M1 steps 7-8 and M2 (K13).
**Files:**
- Modify: `ui/feature/showall/content/ShowAllScreenContent.kt`. Only inside `ShowAllContentBody` (`:66-116`), plus file-private constants.
  - Card call: `VideoItemHorizontal` at `:103-107` gains `modifier`. It accepts one: `core/ui/uikit/component/moviesList/VideoItemHorizontal.kt:48-49`.
  - Launch focus: `rememberFocusRequesterOnLaunch` stays at `:71` and `:96`. Its saveable flag (`core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:51`) is restored as true, which is why nothing refocuses today.
- Test: new `app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllScreenContentFocusTest.kt`.

**Consumes:**
- `LocalContentFocusActive` (`core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:30`).
- `ShowAllScreenContent(state, onAction)` (`ShowAllScreenContent.kt:38-64`, `internal`, so visible to androidTest).
- `ShowAllViewState.Content(items)` (`ui/feature/showall/model/ShowAllViewState.kt:14-17`).
- `VideoItemUIState(id, title, imageUrl, bigImageUrl, ...)` (`core/ui/uikit/component/moviesList/VideoItemUIState.kt:50-71`). The card always renders `state.title` (`VideoItemHorizontal.kt:166-167`).
- Pattern references:
  - Retry loop: `requestAfterAnchorRestore` (`core/ui/uikit/component/moviesList/FocusTargetState.kt:295-300`, 3 attempts at `:309`). It is private there, so copy the pattern; do not import it.
  - Guarded request: `runCatching { requestFocus() }` (`FlowComponent.kt:660`).
  - Saveable id: `rememberReconciledItemFocus` (`FocusTargetState.kt:40-57`).
**Produces:** nothing outside the file.

- [x] Write T8 `ShowAllScreenContentFocusTest` first.
  - JUnit 4 + `createComposeRule`, with `StateRestorationTester` as in `app/src/androidTest/kotlin/com/kino/puber/ui/feature/device/diagnostics/NetworkDiagnosticsContentFocusTest.kt:159-170`.
  - Content: `PuberTheme { CompositionLocalProvider(LocalContentFocusActive provides active) { ShowAllScreenContent(ShowAllViewState.Content(items), onAction = {}) } }`, with 24 items of distinct ids and titles.
  - Find cards with `isFocused() and hasText(title)`, and move with D-pad, as `app/src/androidTest/kotlin/com/kino/puber/ui/feature/home/component/HomeCollectionBackFocusTest.kt:236-250` does.
  - Case `showAllFocusReturnsToTheFocusedCardAfterReentry`:
    1. `active = true`. Wait for the launch focus.
    2. Move with D-pad to a card that is not first (for example DOWN then RIGHT), and note its title.
    3. `emulateSavedInstanceStateRestore()`.
    4. Await focus on that title.
  - Case `showAllDoesNotTakeFocusAfterReentryWhileContentFocusIsInactive`: the same, but `active` is a `mutableStateOf` that the test sets to `false` before the restore. Assert that no node is focused afterwards.
- [x] Implement inside `ShowAllContentBody`, about 30 lines; this is the budget.
  - (a) Last focused id: `val lastFocusedId = rememberSaveable { mutableStateOf<Int?>(null) }`. Write it from each card's `Modifier.onFocusChanged { if (it.hasFocus) lastFocusedId.value = item.id }`.
    - Capture the restore target once on entry: `val restoreId = remember { Snapshot.withoutReadObservation { lastFocusedId.value } }`. Composition then does not observe the id, and focus moves do not recompose the grid.
  - (b) Requester: `val restoreFocus = remember { FocusRequester() }`. Attach `Modifier.focusRequester(restoreFocus)` only to the card with `item.id == restoreId`.
  - (c) Re-entry effect: `val contentFocusActive = LocalContentFocusActive.current`, then `LaunchedEffect(Unit) { if (restoreId != null && contentFocusActive) repeat(3) { withFrameNanos { }; if (runCatching { restoreFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect } }`. On failure, focus is left alone.
  - Use a file-private constant for 3.
  - Change nothing outside this file. Do not touch `FlowComponent` or `RootAnchorCaptureRegistry`.
- [x] If the fix does not fit the budget or this one file, stop. Do not commit production changes. Record it under **Blocked**; the conductor defers and records a follow-up (spec Q1).
- [x] Run the androidTest compile gate, the fast gate and the full gate.
- [x] Commit: "Return focus to the card that opened Details when the Show all grid is restored".
- [ ] Report the SHA. qa-device runs T8 (`TESTS=com.kino.puber.ui.feature.showall.content.ShowAllScreenContentFocusTest`), M1 steps 5-9 and M2 (rail open: focus must stay on the rail).

**Done when:**
- The compile and full gates are green.
- `git diff HEAD~1 --stat` shows only `ShowAllScreenContent.kt` and the new test.
- Settled by qa-device:
  - T8 is green.
  - M1 shows the focus ring on the noted card after Back.
  - M2 shows focus staying on the rail.

---

## Implementation record

<!-- owner: implementer -->

**Commits:**

- Task 1: `9b2e0f50` Expect the tab-local screen after a root push and Back in the tab host wiring test
- Task 2: `57f15531` Guard host replacement and resume delivery in the tab host wiring test and match lifecycle test text by substring
- Task 3: `e1a0b4d1` Keep each tab's saved stack in the tab host so a root push and Back return to it
- Task 4: `b35239c3` Document that the tab host owns the tab subtree saved state
- Task 5 run after qa round 1 (focus landed on the first card of the first partially visible row, not the opener): `ShowAllContentBody` remembers the focused card id (saveable), and refocuses it on entry while `LocalContentFocusActive` is true, 3 frame-delayed attempts. T8 `ShowAllScreenContentFocusTest` added (two cases). androidTest compile, `check.full` green; not run on a device (qa-device's M1 steps 7-8 and M2). The conductor records the follow-up idea for other grids (spec Q5).
- Review round 1 fixes (triage-1 #1-#3), `ShowAllScreenContent.kt` and `ShowAllScreenContentFocusTest.kt` only: (#1) the restore loop reads the current `LocalContentFocusActive` (`rememberUpdatedState`) before every `requestFocus`; (#2) the focused index is saved next to the id, and `resolveRestoreId` falls back to the item at `min(index, lastIndex)` when the opener left the list, on entry and also while the grid has focus when the card disappears (list change effect, gated by a flag that ignores focus lost through the card's removal); the logic moved into a private `GridFocusMemory` to stay under the detekt complexity limit; (#3) three focus cases added: a card below the first viewport after re-entry, neighbour focus when the card is gone before re-entry, neighbour focus when the card leaves a live grid. androidTest compile and `check.full` green; instrumented cases not run (no device by order), so the red half of #3 is not recorded. Not reused: `resolveFocusedItemId` / `rememberReconciledItemFocus` (row-specific, would need a wider change).

**Deviations from the plan:**

- None for Tasks 1 and 2. The Task 2 test commit is the SHA for the qa red run; the plan tick and this record are a docs-only commit on top of it.
- Task 3: the D2 code comment in `PuberCurrentTab` names `tabNavigator.saveableState`, so the Done-when grep for `saveableState` hits that comment only (no call remains). The `items.firstOrNull` grep hits only `FlowComponent.kt:300`, outside `TabFlowNavigator`.

**Files touched outside the plan:**

**For the reviewers:**

- Expected pre-fix on `57f15531`, `TabHostTelemetryWiringTest`: red are T1, T2 `rootPushAndBackRestoresBothTabStacks` and T5b `resumeIsDeliveredToRestoredTabLocalScreenAfterRootPushAndBack` (CHILD_TAG wait times out); green are the five other existing cases, T4 `replacedHostStartsAtTabRootsWhenShownAgain` and T5a. `TabFlowLifecycleTest`: record as it comes (K7).
- Only the androidTest compile gate and the fast gate were run; no instrumented run.
- Task 3: full gate (testDevDebugUnitTest, detektAll, lintDevDebug) and androidTest compile green at `e1a0b4d1`; Task 4 is docs-only, fast gate rerun. `unbind` untouched, `isEnabled` only in `recordNav`. Instrumented runs and M1/M3-M6 are for qa-device.

**Blocked:**

## Readiness

<!-- owner: final-judge, mode readiness; the conductor pastes readiness-<n>.md here. READY | NOT READY and why. -->


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
