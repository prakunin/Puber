# Research: tab-stack-lost-on-root-push

Base `f14dcc84`. Paths are relative to `app/src/main/java/com/kino/puber/` unless they start with `app/`, `docs/` or `.flow/`. I read the Voyager claims from the `2.2.21-1.10.3` class jars in `~/.gradle/caches` with `javap -c`, and checked them against the upstream `main` source. This is bytecode I read, not code I ran. Nothing here was built or run on a device.

## 1. Request

> Slug: tab-stack-lost-on-root-push. Request (bug, user-visible navigation): a tab-local stack (e.g. Home/Concerts tab -> "Show all" grid) is lost after a root push (Details) and Back: the tab comes back at its root instead of the Show all grid, and the focus position is lost with it. Root cause hypothesis from the previous run (read from Voyager 2.2.21-1.10.3 bytecode, not run): the root Navigator in FlowComponent.kt:~123-125 uses Voyager's default disposeNestedNavigators behaviour; when Details is pushed at the root, MainScreen leaves composition and Voyager disposes the current PuberTab and its saved state, which holds the tab navigator's stack (TabComponent.kt PuberCurrentTab ~128-137); on Back the tab list is restored (same PuberTab instance) but the tab navigator starts at its root. Tab switches keep stacks because the TabNavigator is created with disposeNestedNavigators = false. TabAppRouterHolder recreation is ruled out.
> When fixed, the telemetry must follow: TabHostTelemetry reappearingTop in FlowComponent.kt (~593-597) must flip to the live top, and TabHostTelemetryWiringTest's AC-6 test (tabLocalPushThenRootPushThenBackNamesTheScreenShownAfterBack) must flip back to expect the tab-local screen; the spec of telemetry-tab-host-back-label (docs/superpowers/specs/2026-10-17-telemetry-tab-host-back-label.md, Design delta + amendment) explains this. Do NOT reintroduce a Telemetry.isEnabled gate in TabHostTelemetry.unbind.
> Risks to research: what disposing nested navigators controls (ScreenModels, Koin DI scopes via DIScope / buildModule(scopeId, parentScope), focus restore, memory of covered tabs), whether Details screens etc. can leak; how other Voyager apps keep tab stacks across a root push; is there a cheaper fix (e.g. rememberSaveable keys, SaveableStateHolder). Also check the related idea runs rail-content-mismatch-rapid-back and tab-flow-lifecycle-itest (red TabFlowLifecycleTest on master) for overlap.

## 2. Understanding

Suppose a user opens something inside a tab, for example Concerts and then "Show all". They then open a title, which pushes Details onto the root navigator, and press Back. The tab comes back at its root row list, and the grid and its focus are gone. The reason: the whole MainScreen leaves composition while Details is on top. Voyager then disposes the tab that was showing, and that wipes the saved state holding the tab's own navigation stack. Switching tabs does not do this, so it only happens when a root screen covers the tab host.

The fix must keep that saved state without leaking or reviving state that should die, for example after logout. The telemetry label that the previous run pinned to "tab root" must then follow the live stack again.

## 3. Current state in code

**The mechanism.** I confirmed it against the bytecode. The previous run's hypothesis is correct, with one refinement: the effect that wipes the state belongs to the tab list's navigator, not to the tab-flow navigator.
- `core/ui/navigation/component/FlowComponent.kt:123-126`: the root `Navigator(screen, onBackPressed)` passes no `disposeBehavior`. Voyager's default is `NavigatorDisposeBehavior(disposeNestedNavigators = true, disposeSteps = true)`.
- Voyager `Navigator` composable: if `navigator.parent?.disposeBehavior?.disposeNestedNavigators != false`, it installs `NavigatorDisposableEffect(navigator)`. That is a `DisposableEffectIgnoringConfiguration` which calls `disposeNavigator`, and `disposeNavigator` calls `Navigator.dispose(item)` for every item in that navigator's stack. It does not run on a configuration change.
- `Navigator.dispose(screen)` does two things. It calls `ScreenLifecycleStore.remove(screen)`, which clears that screen's `AndroidScreenLifecycleOwner` and its `ViewModelStore`. It also calls `SaveableStateHolder.removeState` for every key in that navigator's `stateKeys` that `startsWith(screen.key)`. State keys have the form `"${screen.key}:$key"`.
- `core/ui/navigation/component/TabComponent.kt:100-106`: `TabNavigator(tab = LoadingTab, disposeNestedNavigators = false, key = scopeName)`. Its inner `Navigator` (the tab list, which holds only the current `PuberTab`) has the root as its parent. So `NavigatorDisposableEffect` is installed on the tab list's navigator.
- `TabComponent.kt:128-137` `PuberCurrentTab`: `tabNavigator.saveableState(currentTab.key) { currentTab.Content() }`. The key is `"Tab:<screen>:..."`. That registers the key in the tab list navigator's `stateKeys`, so a dispose of the current `PuberTab` removes it.
- `core/ui/navigation/PuberTab.kt:25,37,48-68`: `key = "Tab:${screen.key}"`. `Content()` runs `TabFlowComponent`.
- `FlowComponent.kt:583-587`: the tab-flow `Navigator(rootScreen, key = "TabFlow:<slot>")`. Its stack lives in a `rememberSaveable` under the `"Tab:..."` provider, so the dispose above removes it. When MainScreen comes back, this navigator restarts at `TabRootScreen` (`FlowComponent.kt:537-542`).
- The tab-flow navigator's parent is the tab list navigator (`disposeNestedNavigators = false`). Its own screens, `TabRootScreen` and `ShowAllScreen`, are therefore never disposed on a root push. Their ViewModels survive, and only the stack that points at them is lost. Today the ShowAll VM is left orphaned in `ScreenLifecycleStore` under key `ShowAllScreen_<id>` (`ui/feature/showall/ShowAllScreen.kt:33`). Opening the same section again silently reuses it. This is an inference from the bytecode (unverified on device).
- Compose `SaveableStateHolderImpl.removeState` (runtime-saveable 1.12.0, bytecode): it drops the active registry if there is one, otherwise the entry in `savedStates`. The map is flat, keyed by provider key. So the wipe happens whichever order the dispose callbacks run in.

**Why tab switches keep stacks.** A tab switch disposes nothing in the tab list, because `navigator.current = tab` replaces the item and `disposeSteps = false`. The old tab's `"Tab:..."` state stays in the holder (`TabComponent.kt:100-106`; Voyager `TabNavigator` passes `NavigatorDisposeBehavior(disposeNestedNavigators, disposeSteps = false)`, and `tabDisposable` defaults to `null`).

**Lifetimes that are not controlled by Voyager dispose**
- Koin scopes are bound to composition, not to Voyager. `core/di/ScopeModuleManager.kt:35-53,60-75`: `remember` plus a `RememberObserver` closes the scope in `onForgotten`. So every `DIScope` inside MainScreen (the tab flows, ShowAll, ContentList) closes on a root push and is recreated on Back, whatever the dispose flag is.
- ViewModels are bound to Voyager screen owners. `core/di/DIScope.kt:18-28` calls `koinViewModel(scope = …)`, which uses `LocalViewModelStoreOwner`, the per-screen `AndroidScreenLifecycleOwner`. Koin 4.2.2 `getViewModelKey(qualifier, key, className)` does not include the scope. So a retained VM is reused with dependencies taken from the closed scope. This already happens for MainVM (`ui/feature/main/vm/MainVM.kt:30,37`) and for tab roots today. It is harmless for tab-flow screens, because their `AppRouter` is the `TabFlowSession.router` that `TabAppRouterHolder` (MainVM-owned) keeps alive (`FlowComponent.kt:545-551`, `TabComponent.kt:49-74`).
- Focus restore is not tied to Voyager dispose:
  - `FlowComponent.kt:236,241` calls `saveFocusedChild` on root pushes.
  - `:630-632` calls `onBeforeRootNavigate`, which runs `rootAnchorCaptureRegistry.capture`. Only `LazyListState` screens register anything, so ContentList and Home do (`ui/feature/contentlist/content/ContentListScreenContent.kt:139`, `ui/feature/home/component/HomeScreenContent.kt:210`).
  - ShowAll uses `rememberLazyGridState()` and `rememberFocusRequesterOnLaunch()` (`ui/feature/showall/content/ShowAllScreenContent.kt:71-72`). Its launch flag is `rememberSaveable` (`core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:51`), so restoring the saved state also restores grid scroll and suppresses the re-focus on launch. Which card gets focus after Back then depends on the `focusRestorer` chain (`FlowComponent.kt:149-153,614-621`, `MainScreenComponent.kt:296-301`). Unverified; this needs a device check.
- Puber uses no Voyager `ScreenModel` or `LifecycleEffectOnce` (grep). `LocalLifecycleOwner` is read by `core/ui/uikit/component/LifecycleAction.kt`, which has 5 call sites, and by `ui/feature/device/settings/DeviceSettingsContent.kt`.

**Other navigators that are direct children of the root.** Changing the root flag would affect all of these.
- MainScreen's tab list (`ui/feature/main/component/MainScreen.kt:38-42` → `MainScreenComponent.kt:286-305`).
- The nested `FlowComponent` of `DeviceSettingsFlowScreen` (`ui/feature/device/settings/flow/DeviceSettingsFlowScreen.kt:26-33`), when it is pushed at the root by `MainVM.kt:174` (QA says no UI caller is left) or by the Settings hotkey (`core/ui/navigation/AppRemoteHotkeyHandler.kt:27`). It is also a tab root (`ui/feature/main/model/MainUIMapper.kt:112`).
- `PuberTab.Content`'s `Navigator(screen)` fallback when there is no holder (`PuberTab.kt:51-53`). It is not used in production.
- Root stack replacements that dispose MainScreen: `ui/feature/auth/vm/AuthVM.kt:134-136`, `ui/feature/root/vm/LauncherVM.kt:39-50` and `ui/feature/root/component/App.kt:101,118` (session expiry goes to auth, warm launch goes to `[main, target]`). Note: replacing MainScreen with a new `MainScreen()` keeps the same key `"MainScreen"` (`core/ui/navigation/PuberScreen.kt:8-9`), so Voyager does not dispose it on that replace.

**Telemetry coupling that must flip**
- `FlowComponent.kt:588-599`: `TabHostTelemetry.bind(it, top = { navigator.lastItemOrNull }, reappearingTop = { navigator.items.firstOrNull() })`, plus a comment that names this run.
- `core/ui/navigation/component/NavTelemetry.kt:32-55`: `unbind` freezes `handle.reappearingTop()`. It has no `Telemetry.isEnabled` gate, and that must stay so (amendment A1).
- `app/src/androidTest/kotlin/com/kino/puber/core/ui/navigation/component/TabHostTelemetryWiringTest.kt:149-173`: the AC-6 test. It waits for `HOME_TAG` after Back and asserts `CHILD_TAG` is absent. It drives the real `FlowComponent`, `TabComponent` and `PuberCurrentTab` (`:191-206`), so it goes red on any real fix. That red is intended.

**Existing tests around the area**
- `app/src/test/kotlin/com/kino/puber/core/ui/navigation/component/TabNavigatorChildRetentionTest.kt` (JVM; root replace and child registry).
- `app/src/test/.../NavigatorChangedByTest.kt`.
- `app/src/test/.../TabAppRouterHolderTest.kt`.
- `app/src/test/kotlin/com/kino/puber/core/ui/navigation/NavTelemetryTest.kt` (`unbindFreezesTheReappearingTopNotTheLiveTop` and the AC-9 cases).
- Instrumented: `TabFlowLifecycleTest.kt`, `TabHostTelemetryWiringTest.kt` and `FlowComponentRemoteHotkeyTest.kt`, all in `app/src/androidTest/kotlin/com/kino/puber/core/ui/navigation/component/`.

**History**
- `PuberCurrentTab`'s `saveableState(currentTab.key)` has been there since `88cdf071` (Release 1.0.0, 2026-06-28).
- `disposeNestedNavigators = false` on the TabNavigator and the tab-flow key came with `e3101ddc` (Release 1.6.0, 2026-07-31) and `1674417d` (navigation redesign, 2026-08-25).
- The latest touches are `df9e53cc` (the frozen tab root label) and `602928b0` (telemetry attribution).

**Docs**
- `docs/architecture/navigation.md` "Tabs" says nothing about tab stack retention or saved state.

## 4. Related work

- **telemetry-tab-host-back-label** (shipped, merge `c185cc70`). The source of this run. Relevant files:
  - `.flow/runs/telemetry-tab-host-back-label/design-architect-2.md`: the root cause, D9-D14 and R11.
  - `qa-report.md`: Defect 1 is this bug. Defect 2 is the Settings spinner.
  - The spec `docs/superpowers/specs/2026-10-17-telemetry-tab-host-back-label.md`: AC-6 at `:48`, which keeps the superseded original text, and the amendment note at `:232`.
  - What this run inherits: flip `reappearingTop` to the live top (D12/R11), restore the original AC-6 wording ("Back names the tab-local screen; no later line overwrites it with Home"), and keep `unbind` ungated (A1).
- **settings-tab-spinner-stuck** (idea, not attributed). This may share the root cause; it is an unverified hypothesis.
  - The Settings tab root is `TabRootScreen(DeviceSettingsFlowScreen)`. Its `DeviceSettingsFlowVM` runs `router.newRootScreen(DeviceSettingsScreen())` only once, from `onStart` (`ui/feature/device/settings/flow/vm/DeviceSettingsFlowVM.kt:11-13`; `core/ui/PuberVM.kt:49,137-142`).
  - Suppose a root push covers the host while Settings is the current tab, for example through a hotkey or a stray player. The `"Tab:..."` state is wiped, so the nested flow navigator restarts at `LoadingScreen` (`FlowComponent.kt:84,518-523`). Meanwhile the retained `TabRootScreen` owner hands back the same VM, whose `onStart` already ran. That would leave a spinner forever.
  - Option B or A below would probably remove this path. A device check could confirm it.
  - Not a duplicate.
- **tab-flow-lifecycle-itest** (idea). `TabFlowLifecycleTest.kt:122` uses `assertTextContains("content=0")` against `"content=0 vm=N"` (`:247`) without `substring = true`, so the test is red at its first assertion. Nothing after it, including the Player and Details root push and the "one live VM" check at `:150-160`, has run green recently. That is exactly the regression net this fix needs. Overlap: high for verification, none for the fix itself.
- **rail-content-mismatch-rapid-back** (idea). After rapid BACKs the rail shows Home while History content is on screen. It touches the same host/tab restore path (TabRouter replay, `TabComponent.kt:108-121`, and MainVM's selected tab), but there is no evidence of a shared cause. Low overlap. Not a duplicate.
- **flow-component-split** and **side-rail-focus-handoff** (earlier runs) touched the same files. Nothing in them is deferred to this run (not opened in detail).
- Possible duplicate: none.

## 5. External findings

- Voyager `2.2.21-1.10.3` is an official pre-release, "Kotlin 2.2.21 and CMP 1.10.3", with the new combined versioning. Maven Central `lastUpdated` is 2026-06-08. The latest stable is 1.0.1. The disposal APIs (`NavigatorDisposeBehavior`, "dispose screens only when transition ends", `LifecycleEffectOnce onDispose`) came with 1.1.0-beta01..03. Sources: https://github.com/adrielcafe/voyager/releases and https://repo1.maven.org/maven2/cafe/adriel/voyager/voyager-navigator/maven-metadata.xml.
- Upstream `Navigator.kt` (`main`) confirms what I read in the bytecode:
  - `NavigatorDisposeBehavior(disposeNestedNavigators = true, disposeSteps = true)` has no KDoc.
  - The `NavigatorDisposableEffect` condition is `parent?.disposeBehavior?.disposeNestedNavigators != false`.
  - `dispose` filters keys with `startsWith(screen.key)`.
  - The state key is `"${screen.key}:$key"`.
  - Source: https://raw.githubusercontent.com/adrielcafe/voyager/main/voyager-navigator/src/commonMain/kotlin/cafe/adriel/voyager/navigator/Navigator.kt.
- Voyager docs recommend a `TabNavigator` with one `Navigator` per tab, and `CurrentTab` "because it will save the Tab's subtree" via `SaveableStateHolder`. They say nothing about a parent push. Sources: https://voyager.adriel.cafe/navigation/tab-navigation/ and https://voyager.adriel.cafe/navigation/nested-navigation.
- Voyager issue #390 (2024-04-08, open, no maintainer reply): "TabDisposable with nested/parent navigation loses Screen state". Navigating to an outer navigator disposes the tabs, and list scroll is lost, "even when the outer Navigator has disposeNestedNavigators = false". Our version differs: `tabDisposable` defaults to `null` here (bytecode), so the loss comes from the parent-flag path instead. https://github.com/adrielcafe/voyager/issues/390
- Voyager issue #402 (2024-04-29, open): nested navigators inside tabs are not disposed when the TabNavigator is disposed, so ScreenModels leak. The workarounds are a custom TabDisposable and disposing nested tabs from `LifecycleEffectOnce(onDispose)`. There is no upstream fix. This is the leak class that a blanket `disposeNestedNavigators = false` at the root would widen. https://github.com/adrielcafe/voyager/issues/402
- I found no established upstream recipe for keeping tab stacks across a parent push. What users do is turn off nested disposal and dispose by hand, or keep their own saved state.

## 6. Options

**A. Root navigator `disposeNestedNavigators = false`** (`FlowComponent.kt:123`, possibly as a `FlowComponent` parameter so that only the App root uses it). Size: S as a single line. It becomes M once the leak compensation it needs is added.
- Pros:
  - This is the fix the hypothesis points to.
  - The tab list's `NavigatorDisposableEffect` is no longer installed, so the tab subtree state survives root pushes.
- Cons:
  - It applies to every direct child of the root, now and in future.
  - When MainScreen is removed (logout or session expiry to auth), nothing disposes the tab list, the tab-flow navigators or their screens. Their VMs, lifecycle owners and saved `"Tab:..."`/`"TabRoot:..."` states then stay in memory.
  - Because keys are stable (`MainScreen`, `Tab:<screen>`, `ShowAllScreen_<id>`), the next MainScreen after re-login can restore the previous account's tab stacks and reuse stale VMs.
  - The same applies to a root-pushed `DeviceSettingsFlowScreen`: after a pop, its `DeviceSettingsScreen` VM (with a dead flow `AppRouter`) would be reused on the next open.
  - Fixing that needs manual disposal on host removal, which is the #402 class of work and relies on the internal `children`.

**B. Recommended: the tab host owns a `SaveableStateHolder` for the tab subtrees.** In `PuberCurrentTab` (`TabComponent.kt:128-137`), replace `tabNavigator.saveableState(currentTab.key)` with a `rememberSaveableStateHolder()` that `TabComponent` remembers and provides, and `holder.SaveableStateProvider(currentTab.key) { currentTab.Content() }`. Size: M. It is about 3 files, but it is in the navigation risk area.
- Pros:
  - Voyager's dispose of the covered `PuberTab` then finds no `"Tab:"` keys in the tab list's `stateKeys`, so the tab-flow stack and screen states survive the root push.
  - The holder itself is saved in MainScreen's slot of the root holder, so it dies when MainScreen is removed from the root stack. That is better cleanup than today's for non-current tabs.
  - The root's behaviour, `DeviceSettingsFlowScreen` and logout are not touched.
  - It uses the same mechanism Voyager's own `CurrentTab` uses.
- Cons:
  - The `PuberTab` no longer gets a Voyager `ScreenLifecycleOwner` (per-tab `LocalLifecycleOwner`, `LocalViewModelStoreOwner` and `SavedStateRegistryOwner`). Nothing in the tab-flow glue outside `CurrentScreen` resolves a VM. The screens inside get their own owners, now parented to MainScreen's owner rather than the tab's. I expect lifecycle events to be equivalent, but that is unverified, and `LifecycleAction` ON_RESUME must be checked on device.
  - Tabs that drop out of the tab list keep their small saved state until MainScreen dies. An optional `removeState` would fix that.
  - The pre-existing orphaned VMs (section 3) are unchanged.

**C. Keep each tab stack in `TabFlowSession`** (MainVM-owned) and seed the tab-flow `Navigator` from it on re-entry. Size: M.
- Pros:
  - Independent of Voyager's dispose rules.
- Cons:
  - A second source of truth beside Voyager's saver. Process death still relies on `rememberSaveable`.
  - Screen-level saved state (grid scroll, the launch-focus flag) survives only because those keys happen not to be removed.
  - More code, and harder to reason about with `replaceTabRootIfChanged` (`FlowComponent.kt:697-709`).

All options share these steps:
- Flip `FlowComponent.kt:588-597` to `reappearingTop = { navigator.lastItemOrNull }`. Equivalently, pass the same lambda as `top`, keeping the two-lambda API and the ungated `unbind`. Update the comment.
- Restore the original AC-6 test: wait for `CHILD_TAG` after Back, expect `WiringChildScreen` for `navs.first().to`, `screensAfter` and `Telemetry.screen`, and no later `WiringHomeScreen`.
- Update the spec note.
- Add an itest for the stack itself, or extend the AC-6 one.
- Fix `TabFlowLifecycleTest` (`substring = true`) so that its root push lifecycle checks run.
- Update `docs/architecture/navigation.md` Tabs.
- Verify on device, which the AGENTS.md rules require: navigation, focus and state.

## 7. Risks

1. **Leaks and stale revival** (option A mainly). Undisposed nested screens survive host removal and come back with stale VMs and dead routers under stable keys. Option B avoids it by not touching the root flag.
2. **Lifecycle owner change** (option B). Tab-flow screens' lifecycle parent moves from the PuberTab owner to the MainScreen owner. Risk to ON_RESUME/ON_STOP handling inside tabs, for example ContentList refresh-on-resume via `LifecycleAction`. Mitigation: device check and `TabFlowLifecycleTest`.
3. **Focus after Back.** Grid scroll comes back from saved state, but the focused card depends on `focusRestorer` and `saveFocusedChild` across a recreated subtree. The `rootAnchorCaptureRegistry` covers only `LazyListState` screens (`FlowComponent.kt:437-475`). The request asks for focus to be preserved, so this needs device evidence and may need a ShowAll grid hook. That would be scope growth; see Q2.
4. **Telemetry coupling.** If the stack is restored but `reappearingTop` is not flipped, Back is labelled as the root while ShowAll is shown (R11 of the previous run). The red AC-6 itest is the guard. Do not reintroduce the `Telemetry.isEnabled` gate in `unbind`.
5. **Stale VM reuse across a closed Koin scope** (exists today). Restored tab-flow screens reuse VMs whose scoped dependencies came from the closed DIScope. Their routers are safe (`TabFlowSession`). Nested `FlowComponent` routers (Settings) are recreated per composition, so a reused VM holds a dead router. Today this is masked or causes the spinner. After the fix, check the Settings tab after a root push.
6. **Memory.** The current tab's saved state is now kept while a root screen (Details, the player) is on top. It is Bundle-sized state, not compositions. Negligible.
7. **Test debt.** `TabFlowLifecycleTest` is red at its first assertion on master, so the root push lifecycle path has no working instrumented guard until it is fixed.

## 8. Open questions

1. **Which fix: A (root flag), B (host-owned SaveableStateHolder) or C (session-held stack)?** Recommended: B. It fixes the observed path locally, uses Voyager's own tab-subtree pattern, and does not widen the #402 leak class to logout and the root-pushed Settings flow.
2. **Is "focus position preserved" an acceptance criterion with exact-card precision, or "grid restored at its scroll position with focus inside the grid"?** Recommended: AC = the Show all grid reappears at its scroll position and focus lands on the card that opened Details. If the device shows only grid-level focus, fix that in this run only if it is at most a small ShowAll-local change. Otherwise defer it to a follow-up.
3. **Fold the `TabFlowLifecycleTest` assertion fix (run tab-flow-lifecycle-itest) into this run?** Recommended: yes. It is a one-token fix (`substring = true`), it is the instrumented guard for root push lifecycles this change needs, and the idea run can then be closed as covered.
4. **Treat settings-tab-spinner-stuck as fixed by this run if the device check passes?** Recommended: no. Keep it a separate idea and record the hypothesis and the post-fix observation in its state, because its trigger was never reproduced.

## 9. Sources

Files (base `f14dcc84`):
- `AGENTS.md`, `docs/architecture/navigation.md`
- `core/ui/navigation/component/FlowComponent.kt`, `core/ui/navigation/component/TabComponent.kt`, `core/ui/navigation/component/NavTelemetry.kt`
- `core/ui/navigation/PuberTab.kt`, `core/ui/navigation/PuberScreen.kt`, `core/ui/navigation/TabRouter.kt`, `core/ui/navigation/AppRemoteHotkeyHandler.kt`
- `core/di/DIScope.kt`, `core/di/ScopeModuleManager.kt`, `core/ui/PuberVM.kt`, `core/ui/PuberFlowVM.kt`
- `core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt`, `core/ui/uikit/component/moviesList/FocusTargetState.kt`
- `ui/feature/main/component/MainScreen.kt`, `ui/feature/main/component/MainScreenComponent.kt`, `ui/feature/main/vm/MainVM.kt`, `ui/feature/main/model/MainUIMapper.kt`
- `ui/feature/showall/ShowAllScreen.kt`, `ui/feature/showall/content/ShowAllScreenContent.kt`
- `ui/feature/device/settings/flow/DeviceSettingsFlowScreen.kt`, `ui/feature/device/settings/flow/vm/DeviceSettingsFlowVM.kt`
- `ui/feature/root/component/App.kt`, `ui/ScreensImpl.kt`
- `gradle/libs.versions.toml` (Voyager 2.2.21-1.10.3, Koin 4.2.2, Compose BOM 2026.08.00, Kotlin 2.4.10)
- `app/src/test/.../TabNavigatorChildRetentionTest.kt`
- `app/src/androidTest/.../TabHostTelemetryWiringTest.kt`, `app/src/androidTest/.../TabFlowLifecycleTest.kt`

Bytecode (javap, Gradle cache):
- `voyager-navigator-android`, `voyager-tab-navigator-android`, `voyager-core-android` 2.2.21-1.10.3: `NavigatorKt`, `NavigatorKt$Navigator$6`/`$6$1`, `Navigator`, `NavigatorDisposableKt` with its dispose lambdas, `NavigatorSaverInternalKt`, `TabNavigatorKt` with its defaults, `ScreenLifecycleStore`, `AndroidScreenLifecycleOwner`.
- `koin-core-viewmodel-android` 4.2.2: `GetViewModelKt`.
- `runtime-saveable-android` 1.12.0: `SaveableStateHolderImpl.removeState`.

Commits: `88cdf071`, `e3101ddc`, `1674417d`, `602928b0`, `df9e53cc`, `c185cc70`, `f14dcc84`.

Runs:
- `.flow/runs/telemetry-tab-host-back-label/` (`state.md`, `design-architect-2.md`, `qa-report.md`)
- `.flow/runs/settings-tab-spinner-stuck/state.md`, `.flow/runs/tab-flow-lifecycle-itest/state.md`, `.flow/runs/rail-content-mismatch-rapid-back/state.md`

Spec: `docs/superpowers/specs/2026-10-17-telemetry-tab-host-back-label.md`.

URLs (accessed 2026-10-17):
- https://github.com/adrielcafe/voyager/releases
- https://repo1.maven.org/maven2/cafe/adriel/voyager/voyager-navigator/maven-metadata.xml
- https://raw.githubusercontent.com/adrielcafe/voyager/main/voyager-navigator/src/commonMain/kotlin/cafe/adriel/voyager/navigator/Navigator.kt
- https://voyager.adriel.cafe/navigation/tab-navigation/
- https://voyager.adriel.cafe/navigation/nested-navigation
- https://github.com/adrielcafe/voyager/issues/390
- https://github.com/adrielcafe/voyager/issues/402
