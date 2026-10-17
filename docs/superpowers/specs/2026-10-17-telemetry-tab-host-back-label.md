# Telemetry: label Back onto the tab host with the tab's top screen

Status: approved
Date: 2026-10-17
Slug: telemetry-tab-host-back-label
Kind: bug
Size: M
Tracks: none

## Problem / Why

### Request
Back from a root-pushed screen (Details) onto the tab host (MainScreen) records `nav to=Main` and `scr=Main` for 130-380 ms until the tab re-announces itself. Also wanted: a Compose-level test of the tab-host binding wiring and of the root runner's `navChanged` choice.

### Code found
- `core/ui/navigation/component/NavTelemetry.kt:26-39` `TabHostTelemetry`: single `@Volatile` binding; `unbind(handle)` clears it; `topOf(host)` is host-identity checked.
- `NavTelemetry.kt:41-44` `navClassOf`: a `TabHostScreen` resolves via `topOf`, else the container class (`Main`).
- `NavTelemetry.kt:47-52` `recordNav` is the only writer of `Telemetry.screen`.
- `FlowComponent.kt:588-591` `TabFlowNavigator` `DisposableEffect` binds `navigator.lastItemOrNull` and unbinds on dispose.
- `FlowComponent.kt:228,272` root runner: `recordNavCommand(event, navFrom, navChanged.lastItemOrNull)` runs right after the pop, before MainScreen recomposes (`CurrentScreen`, `FlowComponent.kt:491-505`, composes only the top screen, so the host's tab navigators are disposed while Details is on top).
- `TabComponent.kt:108-118` the re-announce: `TabRouter` replays (`replay = 1`) the last `Open`; the restarted `LaunchedEffect` writes `nav tab Home->Home` and resets `Telemetry.screen` (`TabRouter.kt`).
- Tests: `NavTelemetryTest.kt:100-155` (`clearedBindingGivesTheFallback` at `:140-146` pins the old behaviour), `NavigatorChangedByTest.kt`; harness `androidTest/.../TabFlowLifecycleTest.kt` (not a `TabHostScreen`, no `LocalTabHostScreen`).

### Root cause (confirmed by code trace)
1. The tab binding is cleared on dispose while the host is covered, so the Back record (synchronous in the `router.events()` collector) falls back to `MainScreen.class` -> `Main`.
2. The label is only corrected later by the replayed `Open` in `TabComponent`, which also emits a redundant `nav tab X->X` line and names the tab root, not the tab navigator's top (a tab-local push followed by a root push and Back would be overwritten with `Home`).

`act Resumed` labelled `Main` is correct by design (`MainScreenComponent.kt:74-78`, `TelemetryWriter.kt:110-115`, labelled by VM) and is NOT part of the defect and not an AC.

### Chosen approach
Freeze the tab top on dispose (keep host-identity check; no reference to the disposed `Navigator`), skip the `recordNav` in `TabComponent` when the replayed `Open` equals the current tab, and cover the wiring with one new instrumented Compose test plus JVM tests. No Robolectric (build-infra decision, out of scope).

### Risks (analyst)
- Forget/remember ordering in one Compose apply (tab switch) decides which binding wins; handle-identity guard makes both orders safe, covered by AC-3.
- The replay guard assumes `navigator.current` is the same `PuberTab` instance after re-entry (Voyager saveable restore, unverified); after process death one extra line is harmless; the test must pin it.
- `clearedBindingGivesTheFallback` is changed deliberately; reviewers must not read it as a weakened test.
- A frozen binding lingers after logout/NewRoot until the next bind; inert via the host check, pins one small object.
- `TabFlowLifecycleTest` is red on master (tab-flow-lifecycle-itest); the new test must live in its own class and run with `TESTS=`. The instrumented test is outside `check.full`.
- Test recorder helpers (`RecordingTelemetryRecorder`) are not visible to androidTest; use an inline recorder.

## Acceptance criteria

- AC-1. Given a tab host whose tab binding was disposed with Home on top, When a Back command to the same host is recorded, Then `Nav.to` and `Telemetry.screen` are the Home content class, not `MainScreen`. (JVM test; replaces `clearedBindingGivesTheFallback`.)
- AC-2. Given a frozen binding of host A, When a Back/NewRoot records a different (fresh) host B, Then B falls back to its container class (`Main`) and A's tab is never reported. (JVM test.)
- AC-3. Given a frozen binding, When the host re-composes and binds a live navigator, Then the live top wins; and on a tab switch (old tab disposes, new binds) the new tab's top wins. (JVM test for handle ordering both ways; instrumented test includes a tab switch.)
- AC-4. Given Home is the current tab and the host re-enters composition after Back from Details, When `TabComponent` receives the replayed `Open(Home)`, Then no `nav tab Home->Home` event is recorded and `Telemetry.screen` is unchanged. (Instrumented test.)
- AC-5. Given the user selects a different tab, or re-selects the current tab from the rail (a new `PuberTab` instance), When the `Open` arrives, Then `nav tab` is still recorded and `Telemetry.screen` is set to it. (Instrumented or JVM test.)
- AC-6. (rewritten at gate 1 delta, 2026-10-17) Given a tab-local push (e.g. Show all), then a root push (Details), When Back, Then `nav back to=` and `Telemetry.screen` name the screen the tab shows after Back; today the tab root, because the covered host's tab stack is not restored (run tab-stack-lost-on-root-push owns that); no later line names a different screen. (Instrumented test.) Superseded text: Given a tab-local push (e.g. Show all), then a root push (Details), When Back, Then `nav back to=` and `Telemetry.screen` are the tab-local screen, and no later line overwrites it with `Home`. (Instrumented test.) Restored by tab-stack-lost-on-root-push: Back names the tab-local screen again.
- AC-7. Given the real `FlowComponent` runner with a `TabHostScreen` host and a recording `TelemetryRecorder`, When Details is pushed from Home and Back is pressed, Then the first nav event after Back has `to` = Home content (not `Main`) and no event or frame window records `Main` as `scr`. This covers the `DisposableEffect`, `LocalTabHostScreen` provider and the `:272` `navChanged` choice. (New androidTest class, own fixture, run via `make DEVICE=<serial> itest TESTS=<class>`.)
- AC-8. Given the change, When `check.full` runs, Then unit tests, detekt and lint pass with the existing `NavigatorChangedByTest` and the other `NavTelemetryTest` cases unchanged.
- AC-9. Given telemetry off, When navigation runs, Then no binding or freeze work records anything (`Telemetry.isEnabled` guard intact).
- AC-10. Given the device repro (open a title from Home, Back), When the session log is read, Then `nav back` shows `to=Home` and `scr=Home` throughout, `act Resumed` still reads `Main`.

## Out of scope

- Robolectric / JVM Compose tests (build-infra decision, separate run).
- `act Resumed` labelled `Main` (correct by design, labelled by VM).
- Restoring the tab-local stack after a root push and Back (run tab-stack-lost-on-root-push); AC-6 pins today's lost stack.
- Fixing the red `TabFlowLifecycleTest` (run tab-flow-lifecycle-itest).
- rail-content-mismatch-rapid-back (separate run).

## Design

Read in worktree `/home/user1/orca/workspaces/Puber/telemetry-tab-host-back-label` at base `bdd2c792`. Every `path:line` below was opened and checked at that commit. Paths are relative to `app/src/main/java/com/kino/puber/` unless they start with `app/`.

### Decisions

| # | Question | Decision | Why |
|---|----------|----------|-----|
| D1 | Where does the "frozen at cover time" top live? | Inside `TabHostTelemetry` (`core/ui/navigation/component/NavTelemetry.kt:26-39`). `unbind(handle)` keeps its handle-identity guard (`:34-36`). When the handle is still current **and** `Telemetry.isEnabled`, it replaces the live binding with a frozen `Binding(handle.host) { frozenTop }`, where `frozenTop = handle.top()` is read once at that moment. When telemetry is off it clears the binding as it does today. | One field, one writer, no new API. `bind`, `topOf` and `navClassOf` (`:41-44`) stay as they are, so every other `NavTelemetryTest` case still passes unchanged (AC-8). The frozen lambda captures the screen object only, not the disposed `Navigator`. |
| D2 | Freeze the screen object or its telemetry class? | The screen object, the value `navigator.lastItemOrNull` returned (`FlowComponent.kt:589`). | `topOf` returns `Any?`, which `navClassOf` passes to `telemetryClassOf` (`core/telemetry/TelemetryNames.kt:44-49`). If we froze a `Class`, `telemetryClassOf` would resolve it to `java.lang.Class`, so we would need a second binding shape. A frozen screen is a small parcelable value, at most one per process, and it is replaced on the next `bind`. |
| D3 | Does the host-identity check stay? | Yes, unchanged: `topOf` matches `it.host === host` (`NavTelemetry.kt:38`). | A fresh host after NewRoot or logout still falls back to its container class (AC-2). The existing `staleBindingOfAnEarlierHostGivesTheFallbackForAFreshHost` (`app/src/test/kotlin/com/kino/puber/core/ui/navigation/NavTelemetryTest.kt:133-138`) keeps passing. |
| D4 | Bind/unbind order on a tab switch | No new code. `bind` (`NavTelemetry.kt:32`) always overwrites, frozen or live. `unbind` acts only when its handle is current. Forget-then-remember: the old tab freezes, then the new tab's bind overwrites it. Remember-then-forget: the new tab binds, and the old tab's unbind is a no-op. | Either order ends with the new tab live (AC-3). |
| D5 | How does `TabComponent` tell a replay from a real `Open`? | Reference identity. In `core/ui/navigation/component/TabComponent.kt:112-116`, keep `navigator.current = event.tab` and call `recordNav(NavCmd.TAB, from, event.tab)` only when `from !== event.tab`. | The replayed `Open` (`core/ui/navigation/TabRouter.kt:13`, `replay = 1`) carries the same `PuberTab` instance that was last set as `navigator.current`. Every real open builds a new instance: `MainVM.kt:51,116,125,151,165` all call `mainUIMapper.buildTabContent` (`ui/feature/main/model/MainUIMapper.kt:83-92`, `PuberTab(...)` constructor). So reselecting the current tab from the rail is still recorded (AC-5). `==` would be wrong: `PuberTab` is a `data class` (`core/ui/navigation/PuberTab.kt:20-26`), and an equal-by-value reselect would be dropped. |
| D6 | Should the guard also skip the `navigator.current` write? | No. Only the `recordNav` call is guarded. | Smallest change. Assigning the same tab is today's behaviour, and changing it is not asked for. |
| D7 | Wiring test layer | A new instrumented Compose test class in `app/src/androidTest/kotlin/com/kino/puber/core/ui/navigation/component/`, with its own fixture copied from `TabFlowLifecycleTest.kt` (no shared helpers, because that class is red on master). Plus JVM changes to `NavTelemetryTest`. No build changes. | Owner decision Q3. androidTest already sees `internal` types and uses `junit4.v2.createComposeRule` (`TabFlowLifecycleTest.kt:12,63`). |
| D8 | Telemetry-off path | The freeze work in `unbind` is gated on `Telemetry.isEnabled`. The `recordNav` guard at `NavTelemetry.kt:48` is untouched. The live `bind` in `FlowComponent.kt:588-591` stays unconditional, as today. | AC-9: with the log off nothing is read or recorded. The live bind only allocates a lambda per tab composition, which is pre-existing behaviour and not in scope. |

### Changes by layer

**API / repository**: none.

**ViewModel state and actions**: none. `MainVM` and its `openTab` calls (`ui/feature/main/vm/MainVM.kt:51,116,125,151,165`) are unchanged. `MainAction.Resumed` (`MainVM.kt:107`) and the `act Resumed`=`Main` line are out of scope by design.

**Screen and DI module**: none. `MainScreen` (`ui/feature/main/component/MainScreen.kt:24,39`) keeps providing `LocalTabHostScreen`. The `TabRouter` stays scoped in `MainScreen.buildModule` (`MainScreen.kt:30`).

**Navigation / telemetry glue** (`core/ui/navigation/component`, the layer this bug lives in):

1. `core/ui/navigation/component/NavTelemetry.kt:21-39` (`TabHostTelemetry`)
   - `unbind(handle)` (`:34-36`): if `binding === handle`, set `binding` to `if (Telemetry.isEnabled) Binding(handle.host, frozen-of handle.top()) else null`. `handle.top()` is called once, inside `unbind`. The frozen lambda returns that captured value.
   - KDoc at `:21-25`: replace "cleared when it leaves" with "when it leaves composition, the top it had is kept for the same host until the next bind". The host-identity sentence stays.
   - `bind` (`:32`), `topOf` (`:38`), `navClassOf` (`:41-44`), `recordNav` (`:47-52`) and `recordNavCommand` (`:55-62`): unchanged. `recordNav` stays the only writer of `Telemetry.screen` (`core/telemetry/Telemetry.kt:33-35`).
2. `core/ui/navigation/component/TabComponent.kt:112-116`: guard `recordNav(NavCmd.TAB, from, event.tab)` with `from !== event.tab`, and add a one-line comment saying why (the router replays the last `Open` when the host re-enters composition).
3. `core/ui/navigation/component/FlowComponent.kt:588-591`: no code change. The `DisposableEffect(tabHost, navigator)` still binds `{ navigator.lastItemOrNull }` and calls `TabHostTelemetry::unbind` on dispose, which now freezes. The root runner's `navChanged` / `recordNavCommand` (`:228-229`, `:272`) is unchanged. It now reads the frozen top through `navClassOf` because the host is the `navChanged.lastItemOrNull` after the pop.

**Compose content**: none (no UI or layout change).

**Resources**: none.

**Tests** (the test-designer owns the detail; listed here so the layers are complete):

- `app/src/test/kotlin/com/kino/puber/core/ui/navigation/NavTelemetryTest.kt:140-146` `clearedBindingGivesTheFallback`: replaced by a test that unbinds with Home on top, then records Back to the same host, and expects `HomeScreen` for `Telemetry.screen` and `Nav.to` (AC-1). This change is deliberate: the old test pinned the defect.
- Same file, new cases: frozen host A, then Back/NewRoot to a fresh host B falls back to `MainScreen` (AC-2). A frozen binding is replaced by a live bind (AC-3). Tab-switch order both ways: unbind old then bind new, and bind new then unbind old; the new top wins (AC-3). Unbind with telemetry disabled leaves no frozen top, so a later enabled Back gives the fallback and `handle.top` is not invoked (AC-9). The other cases at `:100-138,148-152` stay unchanged (AC-8).
- New `app/src/androidTest/kotlin/com/kino/puber/core/ui/navigation/component/TabHostTelemetryWiringTest.kt` (name is a suggestion). The real `FlowComponent` hosts a probe `@Parcelize` **class** (not object, so host identity is exercised like `MainScreen`) that implements `PuberScreen, TabHostScreen` and provides `LocalTabHostScreen provides this` around `TabComponent { PuberCurrentTab() }`. It installs an inline `TelemetryRecorder` (`isEnabled = true`, records events plus `Telemetry.screen` at each `record`) through `Telemetry.install` (`Telemetry.kt:46-49`), and calls `Telemetry.resetForTests()` (`:55-62`) in `@After`. The test covers AC-4, AC-5, AC-6, AC-7 and the AC-3 tab switch. It runs only via `make DEVICE=<serial> itest TESTS=com.kino.puber.core.ui.navigation.component.TabHostTelemetryWiringTest`.

**Docs**: none required. `docs/architecture/navigation.md` "Tabs" (`:83-93`) does not describe telemetry.

### Alternatives rejected

- Re-announce on bind (research option B): `nav back to=Main` stays wrong, it writes `Telemetry.screen` outside `recordNav` (`Telemetry.kt:33`), and it does not stop the replay overwrite.
- Freeze a `Class<*>` in a second field or a sealed binding: more API surface and existing tests change, with no observable gain over D2.
- Keep the old `top` lambda after dispose: it reads a disposed `Navigator` and pins it, together with its saveable state.
- Deduplicate the replay in `TabRouter` (for example by consuming the replay cache): this changes navigation semantics that the re-entry relies on to restore the tab after a covered refresh.
- Compare tabs with `==` in D5: it drops a reselect of an equal-by-value tab (AC-5).
- Robolectric local Compose test (option C): build-infra change outside scope (Q3).
- Reuse `TabFlowLifecycleTest` fixtures: that class is red on master (tab-flow-lifecycle-itest).

### Risks (highest first)

1. **Lifecycle / correctness: the tab navigator's items at `onDispose`.** Freezing reads `navigator.lastItemOrNull` inside the `DisposableEffect` dispose at `FlowComponent.kt:590`. If Voyager's `Navigator` dispose (`voyager 2.2.21-1.10.3`, sources not available locally, `unverified`) emptied or changed items before our effect is forgotten, the frozen top would be `null`. The result is the current fallback `Main`, so no regression, but the defect would not be fixed. Compose forgets inner effects before outer ones, so ours should run first. AC-7 (instrumented) is the check.
2. **Correctness: replay identity (D5).** The fix relies on the restored `TabNavigator` (`TabComponent.kt:100-106`, inside the root `saveableState` at `FlowComponent.kt:501`) giving back the same `PuberTab` instance in memory. If Voyager's saver copies items, `from !== event.tab` holds and the `nav tab X->X` line comes back. That is harmless, but AC-4 fails. AC-4 in the instrumented test pins this. After process death the instance differs, and one extra line is acceptable.
3. **Correctness: a stale frozen top after an Open that arrives while the host is covered.** `MainVM` can `openTab` while Details is on top (`MainVM.kt:151,165`, preferences or menu changes). Back then records the frozen old tab. The replayed `Open` is a different instance, so `nav tab old->new` is recorded right after, which is correct and self-healing. Name it so reviewers do not take the brief `old` label for a regression.
4. **Concurrency: dispose order on a tab switch (D4).** It is safe in both orders through the handle guard at `NavTelemetry.kt:35`. Every caller runs on the main thread: the Compose apply and the `router.events()` collector in `LaunchedEffect` (`FlowComponent.kt:225-226`), plus the `TabRouter` emit on `Dispatchers.Main.immediate` (`TabRouter.kt:24`). `@Volatile` stays as belt-and-braces. Covered by JVM tests for both orders and the instrumented tab switch.
5. **Memory / lifecycle: a lingering frozen binding.** After NewRoot or logout, the frozen binding pins the old host and one screen object until the next `bind` (`NavTelemetry.kt:30`). The host-identity check makes it inert (AC-2). Today's live binding already pins the host in the same way.
6. **Test-shape risk: `clearedBindingGivesTheFallback` changes deliberately** (`NavTelemetryTest.kt:140-146`). The spec and the commit should say it pinned the defect, so reviewers do not read it as a weakened test.
7. **Rollout / verification.** The instrumented class is outside `check.full`. It needs a leased emulator and `make itest TESTS=...` (Makefile itest target), and the device repro (AC-10) is required. There is no baseline-profile or recomposition impact: no composable signature, parameter or state read changes.

### Open questions

None blocking. Not in scope, for the record: the unconditional live `bind` allocation when telemetry is off (D8) could be gated later; the `act Resumed`=`Main` label is by design.

### Design delta (round 2, after qa round 1)

Read in worktree `/home/user1/orca/workspaces/Puber/telemetry-tab-host-back-label` at `6f9f162c`. Paths are relative to `app/src/main/java/com/kino/puber/` unless they start with `app/`. The Voyager claims come from disassembling the `voyager-navigator` and `voyager-tab-navigator` 2.2.21-1.10.3 class jars in the Gradle cache with `javap -c`. That is bytecode read, not run, and it matches what the itest and device M2 show.

This delta replaces round-1 design D2 (what gets frozen) and AC-6. D1 and D3-D8 stay as they are.

#### Why the tab-local stack is lost (pre-existing, not caused by this run)

1. The root runner's `Navigator(screen, onBackPressed)` at `core/ui/navigation/component/FlowComponent.kt:123-125` uses the default `NavigatorDisposeBehavior()`, so `disposeNestedNavigators = true` (the default constructor sets both flags to true).
2. `TabComponent` calls `TabNavigator(..., disposeNestedNavigators = false, ...)` (`core/ui/navigation/component/TabComponent.kt:100-104`). Inside, Voyager creates its own `Navigator` for the tab list, and its parent is the root navigator. Voyager's `Navigator` composable runs `NavigatorDisposableEffect(navigator)` whenever `navigator.parent?.disposeBehavior?.disposeNestedNavigators != false`, which is true here because the parent is the root navigator.
3. When Details is pushed at the root, `CurrentScreen` (`FlowComponent.kt:491-505`) stops composing MainScreen, so the TabNavigator's navigator leaves composition. Its `NavigatorDisposableEffect` calls `disposeNavigator`, which calls `Navigator.dispose(screen)` for each item, in this case the current `PuberTab`. `dispose` calls `SaveableStateHolder.removeState` for every state key that starts with `screen.key` (`"Tab:..."`).
4. `PuberCurrentTab` keeps the tab's whole subtree under `tabNavigator.saveableState(currentTab.key)` (`TabComponent.kt:128-137`). That subtree includes the `rememberSaveable` of the tab-flow `Navigator` at `FlowComponent.kt:583-587`, which holds `[TabRoot, ShowAll]`. Step 3 removes it.
5. On Back, MainScreen recomposes. The TabNavigator's own navigator is restored, because it lives under MainScreen's root-level saveable state, which is not removed since MainScreen is still in the root stack. The `current` tab is therefore the same `PuberTab` instance, which is why AC-4 passes. But `Navigator(screen = rootScreen, key = tabFlowNavigatorKey(...))` (`FlowComponent.kt:583-587`) finds no saved state and starts again at `rootScreen` (`TabRootScreen`, `FlowComponent.kt:537-542,659`).

The tab-flow navigator's own `disposeNestedNavigators = false` parent is what keeps tab stacks across tab switches. Only covering the host loses them. `TabAppRouterHolder` being recreated (`TabComponent.kt:84-96`) is a second casualty, not the cause: the itest passes its own holder and still loses the stack.

Conclusion: after Back, the covered host shows its tab root, not the tab navigator's last top. Round 1 froze `navigator.lastItemOrNull`, which names a screen that never comes back.

#### Decisions

| # | Question | Decision | Why |
|---|----------|----------|-----|
| D9 | Option (a), (b) or (c)? | (a): freeze what will actually reappear. | It is the only option that makes `nav back to=` itself correct, synchronously, with no extra log line. It touches the same two lines of glue as round 1. |
| D10 | What is frozen? | The tab-flow navigator's root, `navigator.items.firstOrNull()`, read once at unbind, gated on `Telemetry.isEnabled` as in D1. The live label stays `navigator.lastItemOrNull`. | Root is what step 5 rebuilds. `telemetryClassOf(TabRootScreen)` resolves to the wrapped content class (`core/telemetry/TelemetryNames.kt:44-49`, `FlowComponent.kt:665`), so the label is `ContentList` / `Home`. While the host is composed, a root push from a tab-local screen must still record `from=ShowAll`, which is why the live and frozen values differ. |
| D11 | API shape | `TabHostTelemetry.Binding` gains a second lambda: `class Binding(val host: Any, val top: () -> Any?, val reappearingTop: () -> Any? = top)`. `bind(host, top, reappearingTop = top)` passes it through. `unbind` freezes `handle.reappearingTop()` instead of `handle.top()`. `topOf` reads `top` and is unchanged. | It is still one field and one writer. The default keeps every existing `bind(host) { ... }` call and the JVM `bindTop` helper compiling and behaving as before (AC-8). The lambda form keeps AC-9: when telemetry is off, it is never invoked. |
| D12 | Caller | `FlowComponent.kt:589` becomes `TabHostTelemetry.bind(it, top = { navigator.lastItemOrNull }, reappearingTop = { navigator.items.firstOrNull() })`. `:590` is unchanged. A KDoc line on `reappearingTop`, or a comment at `:589`, says that covering the host drops the tab's saved stack (Voyager disposes the covered `PuberTab`), so the root is what comes back. It also names the follow-up run. | It keeps the coupling to the current Voyager behaviour visible in the one place that must change when the stack starts to survive. |
| D13 | The replay guard (D5) | Kept. | With the root frozen, the skipped replay no longer hides anything: Back already says `Home`. Pre-fix, the replay's `nav tab Home->Home` only accidentally covered for the wrong label. |
| D14 | Option (c), fixing the restore here? | No. Open it as its own run. | It is a user-visible navigation bug, it predates this run (the itest fails at the same wait on `8b84a6cf`, before the fix), and fixing it changes Voyager dispose behaviour for every nested navigator, or moves tab stacks out of saveable state. That affects ScreenModel, Koin scope and focus-restore lifetimes and needs its own device verification. None of it is telemetry. |

#### Changes by layer

**API / repository, ViewModel state and actions, screen and DI module, Compose content, resources**: none.

**Navigation / telemetry glue**:
- `core/ui/navigation/component/NavTelemetry.kt:27-46`: `Binding` (`:28`) and `bind` (`:33`) get `reappearingTop` with default `top`. `unbind` (`:35-43`) reads `handle.reappearingTop()` where it now reads `handle.top()` (`:38`). KDoc `:21-26`: "the screen its navigator will show again (today the tab root, because covering the host drops the tab's saved stack) is kept for that same host until the next bind".
- `core/ui/navigation/component/FlowComponent.kt:589`: pass both lambdas, as in D12. `:590` is unchanged.
- `core/ui/navigation/component/TabComponent.kt:118`: unchanged.

**Tests**:
- `app/src/test/kotlin/com/kino/puber/core/ui/navigation/NavTelemetryTest.kt:198-206` `tabLocalTopIsFrozenAtUnbind`: it is kept as is and still passes, because the default `reappearingTop = top` keeps the read-once property it pins. Add one case, `unbindFreezesTheReappearingTopNotTheLiveTop`: `bind(host, top = { other }, reappearingTop = { home })`. First, Back with the binding live gives `ScreenScreen`, so the live label still uses `top`. Then `unbind`, Back again: `HomeScreen` (new AC-6, JVM part).
- `NavTelemetryTest.kt:208-222` `unbindWithTelemetryOffKeepsNoFrozenTopAndNeverReadsIt`: pass the counting lambda as `reappearingTop` as well, or count both, and assert 0 reads. That way AC-9 covers the new lambda.
- `app/src/androidTest/kotlin/com/kino/puber/core/ui/navigation/component/TabHostTelemetryWiringTest.kt:148-162`: rename it to `tabLocalPushThenRootPushThenBackNamesTheScreenShownAfterBack`. Change `click(BACK_TAG, CHILD_TAG)` to `click(BACK_TAG, HOME_TAG)`, then assert that `CHILD_TAG` has no nodes. This pins today's lost stack on purpose. Expect `WiringHomeScreen` for `navs.first().to`, for every `screensAfter(mark)` entry and for `Telemetry.screen`, and assert no `Nav` after the mark names `WiringChildScreen`. Add a comment: when the stack-restore run makes Child reappear, this test goes red at the `HOME_TAG` wait. That run must then switch D12's `reappearingTop` to the live top and restore the original AC-6 wording. The red test is the coupling guard.
- Pre-fix comparison for evidence: on `8b84a6cf`, the renamed test should go red at the `navs.first().to` assertion (`Main`/host class), not at a wait.
- The device check M2 changes to match: after Back, the screen shows the ContentList row, and the log reads `nav back to=ContentList`, `scr=ContentList`, with no `ShowAll` after Back.

#### Alternatives rejected

- (b) Correct the label on re-bind: `nav back to=ShowAll` stays wrong in the log. The correction would need a new `NavCmd` (a telemetry data-shape change for the summary tool) or a write to `Telemetry.screen` outside `recordNav` (`NavTelemetry.kt:54-59`). It also arrives a frame later, which is the very class of transient label this run removes.
- (c) Fix the restore in this run: out of scope (D14). It belongs to its own run.
- Freeze `rootScreen` captured in `TabFlowNavigator` instead of `navigator.items.firstOrNull()`: same class, but a second source of truth after `replaceTabRootIfChanged` (`FlowComponent.kt:688-699`).
- Drop the freeze and go back to the round-0 fallback for the tab-local case: AC-1, AC-7 and AC-10 would fail.
- Restore the replayed `nav tab X->X` line to "correct" the label: it would bring back AC-4's defect, and the label would still be wrong whenever the stack does survive.

#### Risks (highest first)

1. **Coupling to Voyager's dispose behaviour (correctness, rollout).** If the stack-restore run, or a Voyager upgrade, keeps the tab stack, the frozen root would mislabel Back as `Home` while the screen shows `ShowAll`. This lives at `FlowComponent.kt:589`. The renamed itest goes red at its `HOME_TAG` wait in that case, and the D12 comment names the flip.
2. **Configuration change (lifecycle).** `NavigatorDisposableEffect` uses `DisposableEffectIgnoringConfiguration`, so on activity recreation the stack survives while our `DisposableEffect` still freezes the root. No Back is recorded across a recreation, and the new composition re-binds live before any later Back, so nothing reads the frozen root. TV config changes are rare. Accepted.
3. **Tab switch (concurrency).** The old tab freezes its root, and the new tab's bind overwrites it in the same apply in either order (D4). No change from round 1. Covered by the AC-3 JVM and itest cases.
4. **Test coupling: the itest pins a navigation bug.** Reviewers may read it as endorsing the lost stack. The test comment and the spec must say that it pins telemetry against today's behaviour and that the follow-up run owns the flip.

#### Proposed AC-6 (needs owner approval: it changes an approved AC)

- AC-6. Given a tab-local push (e.g. Show all), then a root push (Details), When Back, Then `nav back to=` and `Telemetry.screen` name the screen the tab shows after Back. Today the covered host's tab stack is not restored (a pre-existing Voyager dispose of the covered tab, follow-up run `tab-stack-lost-on-root-push`), so that is the tab root (ContentList / Home). No later line in the stream names a different screen. (Instrumented test; JVM test for the frozen value.) Restored by tab-stack-lost-on-root-push: Back names the tab-local screen again.

AC-1 to AC-5 and AC-7 to AC-10 are unchanged. In the Out of scope section, add: "Restoring a tab's local stack after a root push and Back (run `tab-stack-lost-on-root-push`)."

#### Open questions

1. Approve the rewritten AC-6 above? Recommended: yes. Blocks: yes, because the plan and implementation follow it.
2. Open a new run `tab-stack-lost-on-root-push` (kind bug: Show all, then Details, then Back lands on the tab root and loses focus context; the cause is the Voyager mechanism above) with `/task:new`? Recommended: yes, as a separate run. Its spec must include flipping `reappearingTop` (D12) and restoring the original AC-6 test. Blocks: no.
3. QA Defect 2 (Settings spinner late in a long session) is not attributed to this change and was not reproduced. Recommended: record it on the board as an idea, not in this run. Blocks: no.

### Design amendment (review round 1)

Code state: worktree `2899ccd3`. Fixes landed in `572e252a` and `b256e3a5` (triage-1 #1, #3, #4). This amendment replaces the spec text listed under "Superseded". Where the two disagree, this amendment wins.

###### Current contract

| # | Question | Decision | Why |
|---|----------|----------|-----|
| A1 | Is the freeze in `unbind` gated on `Telemetry.isEnabled`? | No. `TabHostTelemetry.unbind` (`app/src/main/java/com/kino/puber/core/ui/navigation/component/NavTelemetry.kt:42-47`) always does the same thing when its handle is still current (`:43`): it reads `handle.reappearingTop()` once (`:44`) and stores a frozen `Binding(handle.host, keep, keep)` (`:45-46`). It never records anything and never writes `Telemetry.screen`. The only telemetry-off gate is the one in `recordNav` (`:64`). The KDoc at `:22-31` states this. | This is review-edge-1 F1 (triage-1 #1). If unbind ran while telemetry was off and simply cleared the binding, then turning telemetry on while the host was covered would make Back resolve to the host class (`Main`). The replay guard at `TabComponent.kt:118` drops the replayed `Open`, so that wrong label would never be corrected. Freezing always costs one snapshot-stack read and one allocation per tab-flow dispose. This is defensive: today no UI path reaches a covered host where the session-log toggle can be flipped. The rail Settings item is a tab, and `MainVM.onSettingsClick` (`ui/feature/main/vm/MainVM.kt:173-174`, a root push of `deviceSettings()`) has no caller. |
| A2 | API shape | `Binding(val host: Any, val top: () -> Any?, val reappearingTop: () -> Any?)` (`NavTelemetry.kt:33`) and `bind(host, top, reappearingTop)` (`:39-40`) have no default for `reappearingTop`. The 2-arg overload is gone (review-blind-1 F3). The only production caller passes both lambdas: `FlowComponent.kt:593-597` (`top = { navigator.lastItemOrNull }`, `reappearingTop = { navigator.items.firstOrNull() }`). JVM tests pass the same lambda twice through `NavTelemetryTest.bindSame` (`app/src/test/kotlin/com/kino/puber/core/ui/navigation/NavTelemetryTest.kt:105-106`). | A default would let a caller freeze the live top without noticing, which is the wrong value under today's Voyager behaviour (D10). With no default, every bind site has to choose explicitly. |
| A3 | Test isolation | `TabHostTelemetry.resetForTests()` (`NavTelemetry.kt:49-52`) clears any binding, frozen or live. `NavTelemetryTest.tearDown` calls it (`NavTelemetryTest.kt:42-44`). | Triage-1 #3. A frozen binding no longer survives into the next test (this narrows R9 for JVM tests). |

###### Superseded

- **D1**: drop "**and** `Telemetry.isEnabled`" and "When telemetry is off it clears the binding as it does today". Per A1, the handle-current check is the only condition.
- **D8**: replaced by A1. The `recordNav` guard (`NavTelemetry.kt:64`) and the unconditional live bind in `FlowComponent.kt:592-598` are unchanged. "with the log off nothing is read" no longer holds. What holds is "nothing is recorded".
- **D10**: drop "gated on `Telemetry.isEnabled` as in D1".
- **D11**: replaced by A2. Drop the `= top` default on `Binding` and `bind`, "The default keeps every existing `bind(host) { ... }` call ... compiling", and "when telemetry is off, it is never invoked".
- **Changes by layer, Tests (`:103`)**: "Unbind with telemetry disabled leaves no frozen top, so a later enabled Back gives the fallback and `handle.top` is not invoked (AC-9)" is reversed. A disabled unbind now freezes the reappearing top, and a later enabled Back names it.
- **Round 2 Tests (`:172`)**: `unbindWithTelemetryOffKeepsNoFrozenTopAndNeverReadsIt` no longer exists. It was replaced by:
  - `unbindWithTelemetryOffRecordsNothing` (`NavTelemetryTest.kt:222`): AC-9 wording.
  - `unbindWhileOffThenEnabledThenBackNamesTheTabRoot` (`:232`): regression for triage-1 #1. It fails if the gate returns (the result falls back to `MainScreen`) or if the live top is frozen (`ScreenScreen`).
  - `tabLocalTopIsFrozenAtUnbind` (`:200`) now binds through `bindSame`, not through a default.
- **R7**: restated as "Telemetry off: `unbind` freezes and holds one screen object even though nothing is logged". Likelihood 1, impact 1. Accepted: it records nothing, the object is replaced at the next bind, and the host-identity check in `topOf` (`NavTelemetry.kt:54`) keeps it inert for a fresh host, as in R5. The mitigation is AC-9 through `nothingIsRecordedWhenOff` (`NavTelemetryTest.kt:78`) and `unbindWithTelemetryOffRecordsNothing`, plus the regression test `unbindWhileOffThenEnabledThenBackNamesTheTabRoot`.
- **AC-9 test row (`:251`)**: the test-designer's amendment owns the exact row. The design-side mapping is: primary guard `nothingIsRecordedWhenOff`, wording check `unbindWithTelemetryOffRecordsNothing`, and the regression row `unbindWhileOffThenEnabledThenBackNamesTheTabRoot` (triage-1 #1).

###### Unchanged

The AC text is unchanged. AC-9 ("no binding or freeze work records anything") still holds. D2-D7, D9, D12-D14 and R1-R6, R8-R12 stand. Note for `tab-stack-lost-on-root-push`: do not reintroduce a `Telemetry.isEnabled` gate in `unbind` (A1). Done in tab-stack-lost-on-root-push: `reappearingTop` is the live top (`FlowComponent.kt` `TabFlowNavigator`), and `unbind` stays ungated.

## Test design

Base `bdd2c792`. Levels (Puber addendum): `unit` (JUnit 5, no Robolectric), `instrumented` (only `make DEVICE=<serial> itest TESTS=<fqcn>`), `device` (manual on a leased emulator). Existing JVM style reused: `NavTelemetryTest` (`RecordingTelemetryRecorder`, `FakeTelemetryClock`, `bindTop` helper, `MainScreen()` as a real host with identity, `HomeScreen`/`ScreenScreen` as content classes).

### Risk profile

P = probability 1-3, I = impact 1-3. Score >= 6 needs a test at the level where it fails.

| # | Risk (design-architect) | P | I | Score | Covered by |
|---|---|---|---|---|---|
| R1 | Voyager `Navigator` items are already emptied when our `DisposableEffect` onDispose reads `lastItemOrNull`; frozen top is `null`, bug not fixed (silent fallback to `Main`) | 2 | 3 | 6 | AC-7 instrumented (the only level with real Voyager dispose order); AC-6 renamed instrumented. Round 2: the frozen value is now `navigator.items.firstOrNull()` (the tab root), read through `reappearingTop`; the dispose-order risk is unchanged |
| R2 | Replay identity: restored `TabNavigator` returns a copied `PuberTab`, so `from !== event.tab` holds and `nav tab X->X` returns | 2 | 2 | 4 (AC-4 is a P0 so it is pinned anyway) | AC-4 instrumented |
| R3 | Frozen top stale after an `Open` while covered | 2 | 1 | 2 | Accepted; self-heals through the replayed different-instance `Open`. One assertion in AC-5 instrumented (switch while covered is not added). Note only |
| R4 | Bind/unbind order on a tab switch picks the wrong winner | 2 | 3 | 6 | AC-3 JVM, both orders; AC-3 instrumented tab switch |
| R5 | Frozen binding lingers (NewRoot/logout); leaks the old tab into a fresh host | 2 | 3 | 6 | AC-2 JVM (host identity) |
| R6 | `==` vs `!==` mistake drops a rail reselect of an equal-by-value `PuberTab` | 2 | 2 | 4 | AC-5 (needs an equal-but-distinct instance; see test) |
| R7 | ~~Telemetry off: freeze reads `top()` or `reappearingTop()` or retains state~~ SUPERSEDED (review round 1, triage-1 #1). New R7: `unbind` freezes `reappearingTop()` regardless of telemetry state (the gate was the bug: telemetry enabled while the host is covered, e.g. Device Settings root push, left no frozen top and Back recorded `Main`). Residual risk: a record or `Telemetry.screen` write while off | 1 | 2 | 2 | `NavTelemetryTest.nothingIsRecordedWhenOff` (primary guard for AC-9: nothing recorded while off) and `unbindWithTelemetryOffRecordsNothing`; the freeze itself is guarded by the regression test `unbindWhileOffThenEnabledThenBackNamesTheTabRoot`. Accepted at low level |
| R8 | Test-shape: `clearedBindingGivesTheFallback` replaced, read as a weakened test | 2 | 2 | 4 | Rename and replaced in the same file; commit/spec state it pinned the defect |
| R9 | Static test pollution: after the change `unbind` while enabled leaves a NEW frozen `Binding`, so the existing `tearDown` (`binding?.let(unbind)`) no longer clears it (`binding !== handle`) | 2 | 2 | 4 | Every test uses a fresh `MainScreen()`, so host identity keeps it inert; the new tests must not reuse a host across tests. Review round 1 (triage-1 #3): `tearDown` now calls `TabHostTelemetry.resetForTests()`, so frozen bindings no longer survive between tests |
| R10 | The instrumented class is outside `check.full` and needs an emulator; it can rot or be skipped | 2 | 2 | 4 | AC-10 device run records the `itest` result in evidence |
| R11 | Round 2: coupling to Voyager dispose of the covered tab. When `tab-stack-lost-on-root-push` makes the tab stack survive (or a Voyager upgrade does), the frozen root mislabels Back as `Home` while the screen shows `ShowAll` | 2 | 3 | 6 | AC-6 instrumented renamed test: goes red at its `HOME_TAG` wait (and `CHILD_TAG` no-nodes assertion) the moment Child reappears. That red test is the coupling guard; the D12 comment names the follow-up |
| R12 | Round 2: `reappearingTop` wired wrong (frozen value taken from `top`, or live label taken from `reappearingTop`), so live Back from a tab-local screen records the root as `from`, or the frozen label names the lost screen | 2 | 3 | 6 | New JVM `unbindFreezesTheReappearingTopNotTheLiveTop` (both halves); AC-6 instrumented renamed test, which since review round 1 also asserts the tab-local PUSH (`from == WiringChildScreen`, `to == WiringDetailsScreen`): this covers the live `top` wiring (`FlowComponent.kt:593-597`), so swapping the two lambdas now turns it red (review-tests-1 F1, triage-1 #2) |
| R13 | Round 2: the instrumented test pins a navigation bug (lost tab stack) and can be read as endorsing it | 2 | 1 | 2 | Accepted; test comment and spec state it pins telemetry against today's behaviour and the follow-up run owns the flip |

Highest: 6 (R1, R4, R5, R11, R12), each has a P0 test at the level where it fails.

### Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 | `NavTelemetryTest.disposedBindingKeepsTheTopForTheSameHost` (replaces `clearedBindingGivesTheFallback`): `bind(host){home}`, `unbind(handle)` with telemetry on, `recordNavCommand(Back, other, host)`; assert `Telemetry.screen == HomeScreen::class.java` and `navs().single().to == HomeScreen::class.java` | Back to the covered host reports the frozen tab content, not `MainScreen` |
| AC-2 | unit | P0 | `NavTelemetryTest.frozenBindingOfHostADoesNotLeakIntoFreshHostB`: freeze for host A (Home), then `recordNavCommand(Back, other, MainScreen())` and `NewRoot(listOf(MainScreen()))`; assert `MainScreen::class.java` for `Telemetry.screen` and `Nav.to`, never `HomeScreen` | Host identity check survives freezing; the existing `staleBindingOfAnEarlierHostGivesTheFallbackForAFreshHost` stays untouched |
| AC-3 (live over frozen) | unit | P0 | `NavTelemetryTest.liveBindReplacesAFrozenTop`: freeze with Home, `bind(host){other}`, record Back; expect `ScreenScreen` | Re-compose binds win over the frozen top |
| AC-3 (tab switch, forget then remember) | unit | P0 | `NavTelemetryTest.tabSwitchUnbindOldThenBindNewShowsNewTop`: `h1=bind(host){home}`, `unbind(h1)`, `bind(host){other}`; Back; expect `ScreenScreen` | Old tab freezes, new bind overwrites |
| AC-3 (tab switch, remember then forget) | unit | P0 | `NavTelemetryTest.tabSwitchBindNewThenUnbindOldKeepsNewTop`: `h1=bind(host){home}`, `h2=bind(host){other}`, `unbind(h1)`; Back; expect `ScreenScreen` and that the old `home` lambda is not what is read | Stale handle's unbind is a no-op, it must not freeze over the new live binding |
| AC-3 (instrumented switch) | instrumented | P1 | `TabHostTelemetryWiringTest.tabSwitchThenRootPushAndBackNamesTheNewTab` | Real Compose forget/remember order for a switch followed by cover and Back |
| AC-4 | instrumented | P0 | `TabHostTelemetryWiringTest.backOntoTheHostRecordsNoTabReannounce`: Home current, push Details at root, Back; assert the recorded stream has no `Nav` with `cmd=TAB` after the Back, and `Telemetry.screen` is not rewritten after the Back record | `TabComponent` skips the replayed `Open` for the same instance; also pins R2 (same `PuberTab` instance after re-entry) |
| AC-5 (different tab) | instrumented | P0 | `TabHostTelemetryWiringTest.openingAnotherTabIsRecordedAndSetsTheScreen`: `tabRouter.openTab(newTab)`; assert `Nav TAB` from old to new and `Telemetry.screen` is the new content class | The guard does not swallow real tab switches |
| AC-5 (rail reselect, equal data) | instrumented | P0 | `TabHostTelemetryWiringTest.reselectingTheCurrentTabWithANewInstanceIsRecorded`: open a freshly built `PuberTab` equal by value but not identical to current; assert a `Nav TAB` line is recorded | Pins `!==` over `==` (R6) |
| AC-5 (JVM) | unit | P2 | No JVM test: the guard lives in a Composable (`TabComponent`) and has no pure seam; the design keeps it inline. Covered by the two instrumented rows | n/a |
| ~~AC-6 (round 1)~~ SUPERSEDED | instrumented | - | ~~`tabLocalPushThenRootPushThenBackNamesTheTabLocalScreen`~~ (asserted the tab-local screen after Back; the covered tab stack is lost, so that screen never returns; see qa-report Defect 1). Replaced by the next row | - |
| AC-6 (round 2) | instrumented | P0 | `TabHostTelemetryWiringTest.tabLocalPushThenRootPushThenBackNamesTheScreenShownAfterBack` (renamed): tab-local push, root push Details, Back via `click(BACK_TAG, HOME_TAG)`; assert `CHILD_TAG` has no nodes; `navs.first().to`, every `screensAfter(mark)` entry and `Telemetry.screen` are `WiringHomeScreen`; no `Nav` after the mark names `WiringChildScreen`; after `waitForIdle` nothing changes. Review round 1 adds: the PUSH from the tab-local screen is asserted (`from == WiringChildScreen`, `to == WiringDetailsScreen`), pinning the live `top` wiring (R12). Comment in the test: when the stack-restore run makes Child reappear this goes red at the `HOME_TAG` wait and `reappearingTop` must flip to the live top | `nav back to=` and `scr` name the tab root, the screen actually shown after Back; no later line names another screen; R1, R11. Pre-fix evidence: on `8b84a6cf` it is red at the `navs.first().to` assertion (`Main`/host class), not at a wait |
| ~~AC-6 (JVM part, round 1)~~ | unit | - | ~~`tabLocalTopIsFrozenAtUnbind` as the proof of the tab-local value~~ SUPERSEDED as the AC-6 proof; the test stays unchanged and green (default `reappearingTop = top`) and now only pins "read once at unbind" | - |
| AC-6 (JVM part, round 2) | unit | P0 | `NavTelemetryTest.unbindFreezesTheReappearingTopNotTheLiveTop`: `bind(host, top = { other }, reappearingTop = { home })`; Back while live asserts `ScreenScreen` (live label uses `top`); `unbind`; Back again asserts `HomeScreen`; the `other` lambda is not what the frozen read uses | Live and frozen values differ by design (D10, D11): a root push from a tab-local screen records `from=ShowAll` while composed, Back after unbind names the root |
| AC-6 (JVM, kept) | unit | P1 | `tabLocalPushThenRootBackNamesThePushedScreen` (existing, unchanged) and `tabLocalTopIsFrozenAtUnbind` (existing, unchanged) | Default `reappearingTop = top` keeps behaviour for every existing `bind(host) { }` call (AC-8); read-once property |
| AC-7 | instrumented | P0 | `TabHostTelemetryWiringTest.backFromRootPushedScreenNeverRecordsMain`: real `FlowComponent` with a probe `@Parcelize` class host (`PuberScreen, TabHostScreen`, provides `LocalTabHostScreen`) around `TabComponent { PuberCurrentTab() }`; inline `TelemetryRecorder` capturing events and `Telemetry.screen` at each `record`; push Details, Back; assert the first `Nav` after Back has `to` = Home content class, and no event or captured `screen` equals the probe host class | Covers `DisposableEffect` freeze order (R1), `LocalTabHostScreen` provider and the `:272` `navChanged` choice. Run: `make DEVICE=<serial> itest TESTS=com.kino.puber.core.ui.navigation.component.TabHostTelemetryWiringTest` |
| AC-8 | unit | P0 | `check.full`: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`; `NavigatorChangedByTest` and all other `NavTelemetryTest` cases (`:100-138`, `:148-152`) unchanged | No regression; only `clearedBindingGivesTheFallback` is replaced |
| ~~AC-9 (round 2)~~ SUPERSEDED | unit | - | ~~`unbindWithTelemetryOffKeepsNoFrozenTopAndNeverReadsIt`~~ (asserted the fallback `MainScreen` and 0 reads of both lambdas after an unbind while off; that gate was the triage-1 #1 defect. Test removed) | - |
| AC-9 | unit | P1 | Primary guard `NavTelemetryTest.nothingIsRecordedWhenOff` (existing: no `recordNav` event while off), plus `NavTelemetryTest.unbindWithTelemetryOffRecordsNothing` (replaces the removed test): bind, disable the recorder, `unbind` (which now freezes), record Back; assert the recorder holds no event and `Telemetry.screen` is not written | AC-9 is "nothing recorded while off". It no longer promises "no read, no frozen state": `unbind` freezes regardless of telemetry state. Note review-tests-2 F2: the new test is weak on its own, so `nothingIsRecordedWhenOff` carries the guard |
| AC-9 regression (triage-1 #1) | unit | P0 | `NavTelemetryTest.unbindWhileOffThenEnabledThenBackNamesTheTabRoot`: `bind(host, reappearingTop = { home })`, disable telemetry, `unbind`, enable telemetry, `recordNavCommand(Back, other, host)`; assert `Telemetry.screen == HomeScreen::class.java` and `navs().single().to == HomeScreen::class.java`, never `MainScreen` | Telemetry enabled while the host is covered: Back names the tab root, not `Main`. On `f5bfec56` (gated `unbind` clears the binding) it is red with `MainScreen`; green after the fix. Device path is not reachable today (qa-report, review round 1), so this JVM test is the only evidence |
| AC-10 | device | P0 | Manual check M1 | The repro on a real run log |

Counts of rows by priority below are by distinct tests, not by sub-row.

### Bugs: failing-first test

Defect: Back onto the covered tab host labels `Main`.

- Failing-first: `NavTelemetryTest.disposedBindingKeepsTheTopForTheSameHost` (AC-1). Base `bdd2c792` reading: `TabHostTelemetry.unbind` does `if (binding === handle) binding = null` (`git -C {wt} show bdd2c792:app/src/main/java/com/kino/puber/core/ui/navigation/component/NavTelemetry.kt`), so `topOf(host)` is `null`, `navClassOf` falls to `telemetryClassOf(MainScreen)` and the assertion `HomeScreen` fails with `MainScreen`. It passes after D1.
- Written first, in a commit before the fix. Also written first and red on base: `liveBindReplacesAFrozenTop` is green on base (no freezing), so it is a guard, not a failing test; `tabSwitchUnbindOldThenBindNewShowsNewTop` is green on base too.
- Round 2 (AC-6 rewritten): `unbindFreezesTheReappearingTopNotTheLiveTop` does not compile on base (no `reappearingTop` parameter), so it is red by construction there and green after D11; write it with the signature change. The renamed instrumented test is red on `8b84a6cf` at `navs.first().to`; record that run in evidence.
- Review round 1 (triage-1 #1): failing-first for the gate removal is `unbindWhileOffThenEnabledThenBackNamesTheTabRoot`; red on `f5bfec56`, read from `NavTelemetry.kt` `unbind` (the `Telemetry.isEnabled` branch). It was written with the fix.
- Test helpers after round 1: the 2-arg `bind` overload is removed (triage-1 #4); tests use `bindSame` (same lambda for `top` and `reappearingTop`) or pass `reappearingTop` explicitly.
- The old `clearedBindingGivesTheFallback` passes on base and is deleted deliberately: it asserted the defect.
- The instrumented `backFromRootPushedScreenNeverRecordsMain` (AC-7) and `backOntoTheHostRecordsNoTabReannounce` (AC-4) are red on base as well (Main label and `nav tab Home->Home`), so run them on base once on the emulator to confirm the fixture detects the bug (record both results in evidence).

### Instrumented fixture notes

- Own class `TabHostTelemetryWiringTest` in `app/src/androidTest/kotlin/com/kino/puber/core/ui/navigation/component/`; fixture copied from `TabFlowLifecycleTest` (red on master), no shared helpers, `junit4.v2.createComposeRule`.
- Inline recorder (the JVM `RecordingTelemetryRecorder` is not visible to androidTest) with `isEnabled = true`; install via `Telemetry.install`, `Telemetry.resetForTests()` in `@After`. Capture `Telemetry.screen` inside each `record` call to prove no transient `Main`.
- Probe host must be a class, not an object, so host identity is exercised like `MainScreen`.
- Assert on the public surface (recorded events, `Telemetry.screen`), not on `TabHostTelemetry` internals. No mock of `FlowComponent`, `TabComponent` or `TabHostTelemetry`.
- Assertions on "after Back" use `waitForIdle`, then check the stream order; no sleeps.

### Manual checks

M1 (AC-10, device, leased emulator, worktree APK via install-preserve, telemetry enabled):
1. Cold start, land on Home tab.
2. D-pad to a title, `DPAD_CENTER` to open Details.
3. `KEYCODE_BACK` to return to Home.
4. Read the session log (`adb -s <serial> ...` pull of the telemetry log, only the needed lines, no secrets in evidence).
Expected: `nav back` line has `to=Home` (the Home content class); every `scr=` from the Back until the next navigation is Home; `act Resumed` for the host still reads `Main`; no `nav tab Home->Home` line.
Must not happen: `nav back to=Main`, `scr=Main` at any point after Back, a `nav tab X->X` line, focus loss on Home after Back, a crash.

M2 (AC-6 round 2, device; replaces the round-1 M2): Home, "Show all" (tab-local push), open a title (Details), Back.
Expected: after Back the screen shows the tab root (the ContentList row), not Show all; the log reads `nav back to=ContentList` (the tab root content class) and `scr=ContentList`; no line after the Back names `ShowAll` or `Home`-different/`Main`, and none rewrites `scr`.
Must not happen: `nav back to=ShowAll`, `scr=ShowAll` after Back, `scr=Main` at any point, a `nav tab X->X` line. Note in evidence that the tab root (not Show all) is shown by today's pre-existing lost-stack behaviour (follow-up run `tab-stack-lost-on-root-push`); if Show all reappears, the label is wrong and R11 has fired.

M3 (AC-5, device): from Home, select another tab from the rail, then reselect the current tab.
Expected: a `nav tab` line for each, `scr` equals the selected tab content.
Must not happen: a missing `nav tab` line for the reselect.

M4: run `make DEVICE=<serial> itest TESTS=com.kino.puber.core.ui.navigation.component.TabHostTelemetryWiringTest` on the same lease; record the pass.

### Perf

Track none; no composable signature or state-read change.

### UNTESTABLE

none. Notes: AC-5 has no JVM seam (guard is inline in a Composable), so it is instrumented only; the process-death case (different `PuberTab` instance, one extra `nav tab X->X` line) is accepted per design and not tested.

RISKS: 13, highest 6
P0: 13  P1: 4  P2: 0  MANUAL: 4  (review round 1: +1 P0 `unbindWhileOffThenEnabledThenBackNamesTheTabRoot`, AC-9 test replaced by `unbindWithTelemetryOffRecordsNothing`, still P1; round 2: +1 P0 `unbindFreezesTheReappearingTopNotTheLiveTop`; the renamed instrumented test keeps its P0)

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Record the tab's top frozen at cover time, or wait for recomposition? | no | answered | Frozen (recommended) |
| Q2 | Skip the replayed `nav tab X->X` line when the tab is unchanged? | no | answered | Yes (recommended) |
| Q4 | Approve rewritten AC-6 (label names the screen shown after Back, today the tab root)? | yes | answered | Yes (recommended) |
| Q5 | Open run tab-stack-lost-on-root-push for the lost tab stack? | no | answered | Yes, separate run (recommended) |
| Q6 | Record qa Defect 2 (Settings spinner) as a separate idea? | no | answered | Yes, run settings-tab-spinner-stuck (recommended) |
| Q3 | Wiring test layer: instrumented Compose or Robolectric? | no | answered | Instrumented; Robolectric is a separate run (recommended) |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-10-17 | Q1-Q3 answered with the recommended options | owner (standing instruction: recommended) |
| 2026-10-17 | Gate 1: approved as written (M, tracks none, AC-1..AC-10) | owner (standing instruction: recommended) |
| 2026-10-17 | Gate 1 delta: AC-6 rewritten, design delta D9-D14 (freeze the reappearing tab root) approved; Q4-Q6 recommended | owner (standing instruction: recommended) |
| 2026-10-17 | Review round 1: unbind freezes regardless of telemetry state, 2-arg bind removed (design amendment A1-A3); AC text unchanged | conductor (triage-1 #1, #4) |
