# Telemetry: label Back onto the tab host with the tab's top screen - plan

**Spec:** `docs/superpowers/specs/2026-10-17-telemetry-tab-host-back-label.md`
**Goal:** Back from a root-pushed screen onto the tab host records the tab navigator's top (not `Main`), and the replayed `TabCommand.Open` no longer writes a redundant `nav tab X->X`.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`; full gate:
  `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`.
- Touched JVM tests: `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.core.ui.navigation.NavTelemetryTest' --tests 'com.kino.puber.core.ui.navigation.NavigatorChangedByTest'`.
- androidTest compile check (no device): `./tools/agentw :app:compileDevDebugAndroidTestKotlin`.
  If Gradle reports no such task, use `./tools/agentw :app:assembleDevDebugAndroidTest` and say so in the record.
- Everything runs in the worktree `/home/user1/orca/workspaces/Puber/telemetry-tab-host-back-label`.
  Never `./gradlew` there. Set `AGENT_GRADLE_SERIAL=1` if another worktree may be building.
- The implementer never touches a device, adb, an emulator lease, or `make itest`. The instrumented
  class runs later through `qa-device` only:
  `make DEVICE=<serial> itest TESTS=com.kino.puber.core.ui.navigation.component.TabHostTelemetryWiringTest`.
- One task = green fast gate + the touched tests in the state the task names + one commit. The message is
  one imperative English sentence with no prefix, and it never has a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- Main sources are under `app/src/main/java/com/kino/puber/` (abbreviated `main/` below). JVM tests are under
  `app/src/test/kotlin/com/kino/puber/` (`test/`). Instrumented tests are under
  `app/src/androidTest/kotlin/com/kino/puber/` (`androidTest/`). Every `path:line` was checked at `8c9143b6`.
- Scope: no change to `FlowComponent.kt`, `TabRouter.kt`, `MainScreen.kt`, `MainVM.kt`, build files,
  resources or docs. `NavigatorChangedByTest` and every `NavTelemetryTest` case except
  `clearedBindingGivesTheFallback` stay unchanged (AC-8).
- Do not edit `androidTest/core/ui/navigation/component/TabFlowLifecycleTest.kt`. It is red on master (run
  tab-flow-lifecycle-itest). Copy its fixture pieces; do not share helpers with it.

### Code the tasks rely on (verified)

- `main/core/ui/navigation/component/NavTelemetry.kt:21-25`: the KDoc of `TabHostTelemetry` ("cleared when it leaves").
- `NavTelemetry.kt:26-39`: `internal object TabHostTelemetry` with `class Binding(val host: Any, val top: () -> Any?)` (`:27`),
  `@Volatile private var binding` (`:29-30`), `fun bind(host, top): Binding` (`:32`),
  `fun unbind(handle: Binding) { if (binding === handle) binding = null }` (`:34-36`),
  `fun topOf(host): Any?` with the host-identity check `it.host === host` (`:38`).
- `NavTelemetry.kt:41-44`: `navClassOf` resolves a `TabHostScreen` through `topOf`, else falls back to the container class.
  `:47-52` `recordNav` returns early when `!Telemetry.isEnabled` (`:48`) and is the only writer of
  `Telemetry.screen` (`:50`). `:55-62` `recordNavCommand(event, from, top)`.
- `main/core/telemetry/Telemetry.kt:33-35` `Telemetry.screen`, `:37` `isEnabled`, `:46-49` `install(recorder, clock)`,
  `:55-62` `resetForTests()`. `:5-8` `internal interface TelemetryRecorder { val isEnabled; fun record(event) }`.
- `main/core/telemetry/TelemetryNames.kt:44-49`: `telemetryClassOf` maps `PuberTab` and `TabRootScreen` to the
  wrapped screen's class.
- `main/core/telemetry/TelemetryEvent.kt:140-147`: `TelemetryEvent.Nav(t, cmd: NavCmd, from: Class<*>?, to: Class<*>?)`.
- `main/core/ui/navigation/component/FlowComponent.kt:588-591`: `DisposableEffect(tabHost, navigator)` binds
  `{ navigator.lastItemOrNull }` and calls `TabHostTelemetry::unbind` on dispose. This file is not changed.
  `:228` `navChanged`, `:272` `recordNavCommand(event, navFrom, navChanged.lastItemOrNull)` in the root runner.
  `:738-746`: the tab runner forwards a `RootPuberScreen` `NavigateTo` to `rootRouter`. `:82-88` is the
  `FlowComponent(scopeName, screen, composableScope, moduleFactory, remoteKeyHandler, content)` signature.
  `:659,665` `TabRootScreen.contentScreenClass`.
- `main/core/ui/navigation/component/TabComponent.kt:34`: `LocalTabHostScreen`. `:77-81` is the
  `TabComponent(tabRouter, tabAppRouterHolder, content)` signature. `:108-119` collects `tabRouter.events()`,
  and `:112-116` handles `TabCommand.Open`: `val from = navigator.current; navigator.current = event.tab;
  recordNav(NavCmd.TAB, from, event.tab)`. `:126` is `PuberCurrentTab()`.
- `main/core/ui/navigation/TabRouter.kt:13` uses `MutableSharedFlow(replay = 1)`. `:19` is `openTab(tab)`.
- `main/core/ui/navigation/PuberTab.kt:16-22`: `PuberTab` is a `@Parcelize data class(screen, tag, instanceKey = "")`.
- `main/core/ui/navigation/TabHostScreen.kt:4`: `internal interface TabHostScreen`.
- `main/core/ui/navigation/PuberScreen.kt:12`: `interface RootPuberScreen : PuberScreen`.
- `main/ui/feature/main/component/MainScreen.kt:23-24,39`: `@Parcelize internal class MainScreen : PuberScreen, TabHostScreen`,
  which provides `LocalTabHostScreen provides this`. This is the model for the probe host.
- `test/core/ui/navigation/NavTelemetryTest.kt`: `recorder` (`:25`), `home`/`other` (`:26-27`), `binding` field (`:28`),
  `FakeActivityScreen` (`:30-34`), `setUp` (`:36-39`), `tearDown` (`:41-45`, which resets Telemetry **first** and
  then unbinds while telemetry is off, so it still clears a live binding), `navs()` (`:47`), `bindTop` (`:101-103`),
  `clearedBindingGivesTheFallback` (`:140-146`).
- `test/util/RecordingTelemetryRecorder.kt`: `isEnabled` is a `var`. `test/core/telemetry/TestScreens.kt:19,21`
  `HomeScreen`, `ScreenScreen`.
- `androidTest/core/ui/navigation/component/TabFlowLifecycleTest.kt`: fixture to copy. `:62-63` `createComposeRule()`
  (`junit4.v2`), `:69-84` setUp/tearDown, `:88-101` `FlowComponent` with `AppLauncher` + `Screens` scoped,
  `:179-187` `ProbeAppLauncher`, `:189-218` the host-holder pattern, `:319-325`/`:349-355` destinations,
  `:357-372` `ProbeDestination` (gets `AppRouter` from `LocalPuberKoinScope`, Back via `router::back`).

---

## Tasks

### Task 1: Pin the defect with JVM tests

**Covers:** AC-1, AC-2, AC-3 (JVM), AC-6 (JVM part), AC-9, AC-8 (replacement is deliberate)
**Files:**
- Test: `test/core/ui/navigation/NavTelemetryTest.kt`

**Consumes:** `TabHostTelemetry.bind/unbind/topOf` (`NavTelemetry.kt:32-38`), `recordNavCommand` (`:55-62`),
`Telemetry.screen`, the `bindTop`/`navs()` helpers.
**Produces:** nothing for production code.

- [x] Delete `clearedBindingGivesTheFallback` (`:140-146`). It asserted the defect: a binding disposed while the
      host is covered gives `MainScreen`.
- [x] Add `disposedBindingKeepsTheTopForTheSameHost` (the failing-first test, AC-1):
      `val host = MainScreen(); TabHostTelemetry.unbind(TabHostTelemetry.bind(host) { home }); recordNavCommand(Command.Back, other, host)`;
      assert `HomeScreen::class.java` for both `Telemetry.screen` and `navs().single().to`.
- [x] Add `frozenBindingOfHostADoesNotLeakIntoFreshHostB` (AC-2): freeze host A (`MainScreen()`) with `home`, then
      `recordNavCommand(Command.Back, other, MainScreen())` and
      `recordNavCommand(Command.NewRoot(listOf(MainScreen())), other, MainScreen())`. Assert both navs have
      `to == MainScreen::class.java`, `Telemetry.screen == MainScreen::class.java`, and no nav has `HomeScreen`.
- [x] Add `liveBindReplacesAFrozenTop` (AC-3): freeze `host` with `home`, `bindTop(host) { other }`, then Back to
      `host`; expect `ScreenScreen`.
- [x] Add `tabSwitchUnbindOldThenBindNewShowsNewTop` (AC-3): `h1 = bind(host){home}`, `unbind(h1)`,
      `bindTop(host){other}`, then Back; expect `ScreenScreen`.
- [x] Add `tabSwitchBindNewThenUnbindOldKeepsNewTop` (AC-3): `h1 = bind(host){ homeReads++; home }`,
      `bindTop(host){other}`, `unbind(h1)`, then Back; expect `ScreenScreen` and `homeReads == 0`.
- [x] Add `tabLocalTopIsFrozenAtUnbind` (AC-6, JVM part): `var top: Any? = home`, `h = bind(host){ top }`,
      `unbind(h)`, `top = other`, then Back; expect `HomeScreen` (the value read at unbind).
- [x] Add `unbindWithTelemetryOffKeepsNoFrozenTopAndNeverReadsIt` (AC-9): `h = bind(host){ reads++; home }`,
      `recorder.isEnabled = false`, `unbind(h)`, `recorder.isEnabled = true`, then Back; expect
      `MainScreen::class.java` and `reads == 0`.
- [x] Use a fresh `MainScreen()` host in every test (R9: a frozen binding is not cleared by `tearDown`, and host
      identity is what makes it inert). Keep `tearDown` as it is. Do not touch the other cases.
- [x] Run the touched JVM tests. Expected on this commit: `disposedBindingKeepsTheTopForTheSameHost` and
      `tabLocalTopIsFrozenAtUnbind` fail with `MainScreen`. Every other case passes, including all pre-existing
      ones and `NavigatorChangedByTest`. Record the red output in the Implementation record.
- [x] Fast gate green; commit (for example "Pin the tab host Back label defect with telemetry tests").

**Done when:** the fast gate is green, the touched test run shows exactly the two named failures and they fail
with `MainScreen`, and there is one commit.

---

### Task 2: Add the instrumented wiring test for the tab host

**Covers:** AC-3 (instrumented switch), AC-4, AC-5, AC-6, AC-7
**Files:**
- Create: `androidTest/core/ui/navigation/component/TabHostTelemetryWiringTest.kt`
  (package `com.kino.puber.core.ui.navigation.component`)

**Consumes:** `FlowComponent` (`FlowComponent.kt:82-88`), `TabComponent` (`TabComponent.kt:77-81`),
`PuberCurrentTab` (`:126`), `LocalTabHostScreen` (`:34`), `TabAppRouterHolder`, `TabRouter.openTab`,
`TabHostScreen`, `RootPuberScreen`, `Telemetry.install/resetForTests/screen`, `TelemetryRecorder`,
`TelemetryEvent.Nav`, `NavCmd`, `ScreensImpl`, `TabType`.
**Produces:** test class `com.kino.puber.core.ui.navigation.component.TabHostTelemetryWiringTest`.

Fixture. Everything is `private` to the file. Use unique tag constants (`wiring_*`) and distinct type names
(`Wiring*`) so nothing clashes with `TabFlowLifecycleTest`'s private declarations in the same package.
- Inline recorder: `private class WiringRecorder : TelemetryRecorder { override val isEnabled = true;
  val records = Collections.synchronizedList(mutableListOf<Pair<TelemetryEvent, Class<*>?>>());
  override fun record(event) { records += event to Telemetry.screen } }`. `recordNav` writes `Telemetry.screen`
  before it records, so each pair holds the screen after that event. Install it in `@Before` with
  `Telemetry.install(recorder, JvmTelemetryClock)` (`main/core/telemetry/TelemetryClock.kt:11`, `internal object`,
  visible to androidTest). In `@After`,
  call `Telemetry.resetForTests()` after `tabAppRouterHolder.dispose()` (copy `TabFlowLifecycleTest.kt:79-84`).
- Host holder object (copy `:189-205`) carrying `tabRouter` and `tabAppRouterHolder`.
- Probe host: `@Parcelize private class WiringTabHostScreen : PuberScreen, TabHostScreen`. It must be a **class**,
  not an object, like `MainScreen`, so host identity is exercised. Its `Content()` is
  `CompositionLocalProvider(LocalTabHostScreen provides this) { TabComponent(tabRouter = ..., tabAppRouterHolder = ...) { PuberCurrentTab() } }`.
- Tab contents: `@Parcelize private data object WiringHomeScreen : PuberScreen` and `WiringOtherScreen`. Each shows
  a tag text plus two buttons that get `AppRouter` from `requireNotNull(LocalPuberKoinScope.current).get<AppRouter>()`,
  the tab router (pattern `:357-372`): "local" -> `navigateTo(WiringChildScreen)` (tab-local push) and
  "details" -> `navigateTo(WiringDetailsScreen)` (forwarded to the root by `FlowComponent.kt:738-746`).
- `WiringChildScreen : PuberScreen` (tab-local) has the same "details" button. `WiringDetailsScreen : RootPuberScreen`
  has a Back button using its scope's `AppRouter`, which is the root router.
- `setContent { FlowComponent(scopeName = "TabHostTelemetryWiringTest", screen = host, moduleFactory = {...AppLauncher + Screens...}) }`
  (copy `:88-101`). Keep the `host` instance in a field.
- Tabs: `homeTab = PuberTab(WiringHomeScreen, TabType.Home)`, `otherTab = PuberTab(WiringOtherScreen, TabType.History)`.
  Open with `composeRule.runOnIdle { tabRouter.openTab(...) }`, then wait for the tab's tag.
- Helpers: `navsAfter(mark)` = `records.drop(mark)` filtered to `TelemetryEvent.Nav`; `screensAfter(mark)` = the
  captured screens. Take `mark = records.size` inside `runOnIdle` right before the click. After the click, wait for
  the destination tag, then `composeRule.waitForIdle()`. No sleeps.

Tests (from the spec's Test design):
- [x] `backFromRootPushedScreenNeverRecordsMain` (AC-7): Home open, "details", mark, Back, idle. The first `Nav`
      after the mark has `cmd == NavCmd.BACK` and `to == WiringHomeScreen::class.java`. No `Nav.to`/`Nav.from` and no
      captured screen after the mark equals `WiringTabHostScreen::class.java`.
- [x] `backOntoTheHostRecordsNoTabReannounce` (AC-4): same path. After the mark, no `Nav` with `cmd == NavCmd.TAB`.
      Every captured screen after the mark is `WiringHomeScreen::class.java`, and
      `Telemetry.screen == WiringHomeScreen::class.java` at the end.
- [x] `openingAnotherTabIsRecordedAndSetsTheScreen` (AC-5): Home open, mark, `openTab(otherTab)`. There is one `Nav TAB`
      with `from == WiringHomeScreen`, `to == WiringOtherScreen`, and `Telemetry.screen == WiringOtherScreen::class.java`.
- [x] `reselectingTheCurrentTabWithANewInstanceIsRecorded` (AC-5, R6): Home open, mark,
      `openTab(PuberTab(WiringHomeScreen, TabType.Home))`. Assert that instance `== homeTab` and `!== homeTab`.
      Then assert one `Nav TAB` after the mark with `to == WiringHomeScreen`.
- [x] `tabLocalPushThenRootPushThenBackNamesTheTabLocalScreen` (AC-6): Home, "local" (wait for the child tag),
      "details", mark, Back, idle. The first `Nav` after the mark is `BACK` with `to == WiringChildScreen`. Every
      captured screen after the mark, and `Telemetry.screen` at the end, is `WiringChildScreen` (never Home, never the host).
- [x] `tabSwitchThenRootPushAndBackNamesTheNewTab` (AC-3): Home, `openTab(otherTab)`, "details" from Other, mark, Back,
      idle. The first `Nav` after the mark is `BACK` with `to == WiringOtherScreen`, and no captured screen after the
      mark is the host or Home.
- [x] Run `./tools/agentw :app:compileDevDebugAndroidTestKotlin` (fallback in Global constraints) and the fast gate;
      both green. Do not run the tests. On this commit (fixes not yet in) the expected device result, which `qa-device`
      records, is red for AC-7, AC-4, AC-6 and AC-3 and green for the two AC-5 tests.
- [x] Commit (for example "Add an instrumented test of the tab host telemetry wiring").

**Done when:** androidTest compiles, the fast gate is green, the class has the six tests named above, and there is
one commit. The device run belongs to `qa-device`.

---

### Task 3: Freeze the tab top when the tab binding leaves composition

**Covers:** AC-1, AC-2, AC-3, AC-6 (JVM), AC-8, AC-9
**Files:**
- Modify: `main/core/ui/navigation/component/NavTelemetry.kt`. Change `unbind` (`:34-36`) and the KDoc (`:21-25`).

**Consumes:** `Telemetry.isEnabled` (`Telemetry.kt:37`), `Binding` (`NavTelemetry.kt:27`).
**Produces:** `TabHostTelemetry.unbind(handle)` with the new contract. When `handle` is the current binding and
telemetry is on, the binding becomes `Binding(handle.host) { frozen }` with `frozen = handle.top()` read once. When
telemetry is off, the binding is cleared to `null`. A stale handle is still a no-op. The signatures of `bind`, `topOf`,
`navClassOf`, `recordNav` and `recordNavCommand` stay unchanged.

- [x] Rewrite `unbind`:
      ```kotlin
      fun unbind(handle: Binding) {
          if (binding !== handle) return
          binding = if (Telemetry.isEnabled) {
              val frozen = handle.top()
              Binding(handle.host) { frozen }
          } else {
              null
          }
      }
      ```
      The new lambda captures only the screen value, never `handle` or the navigator.
- [x] KDoc: replace "cleared when it leaves" with: when the navigator leaves composition (the host is covered), the
      top it had is kept for that same host until the next bind. Keep the host-identity sentence.
- [x] Run the touched JVM tests. All `NavTelemetryTest` cases and `NavigatorChangedByTest` must be green, including
      the two that were red in Task 1.
- [x] Fast gate green; commit (for example "Keep the tab host's last tab top when its tab navigator is covered").
      The commit body may say that `clearedBindingGivesTheFallback` was replaced because it pinned the defect.

**Done when:** the touched JVM tests are all green, the fast gate is green, and there is one commit.

---

### Task 4: Skip the telemetry record for a replayed tab Open

**Covers:** AC-4, AC-5
**Files:**
- Modify: `main/core/ui/navigation/component/TabComponent.kt:112-116`

**Consumes:** `TabRouter` replay (`TabRouter.kt:13`). Each real open builds a new `PuberTab`; the replay carries the
instance already in `navigator.current`.
**Produces:** no new API.

- [x] Keep `val from = navigator.current` and `navigator.current = event.tab`. Wrap only the record:
      `if (from !== event.tab) recordNav(NavCmd.TAB, from, event.tab)`. Add a one-line comment: the router replays
      its last `Open` when the host re-enters composition, and that replay is not a navigation. Use reference
      identity on purpose: `PuberTab` is a data class, and `==` would drop a rail reselect.
- [x] Do not guard the `navigator.current` assignment.
- [x] Run the fast gate, `./tools/agentw :app:compileDevDebugAndroidTestKotlin`, then the full gate
      `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`. All green (AC-8).
- [x] Commit (for example "Skip the telemetry nav line for a replayed tab open").

**Done when:** the full gate is green, androidTest compiles, and there is one commit. The final device evidence
(`make itest` for `TabHostTelemetryWiringTest` all green, and the AC-10 repro: `nav back to=Home`, `scr=Home`,
`act Resumed` still `Main`, no `nav tab Home->Home`) comes from `qa-device` after this task, not from the implementer.

---

## Round 2: freeze the tab root that reappears (design delta D9-D14, rewritten AC-6)

Tasks 1-4 are done. Read the spec section "Design delta (round 2, after qa round 1)" and the rewritten AC-6
before you start. In short: covering the host (a root push) makes Voyager dispose the covered `PuberTab`, which
drops the tab-flow navigator's saved stack. After Back the tab shows its **root** again, not the tab-local top.
Round 1 froze `navigator.lastItemOrNull`, which names a screen that never comes back (qa round 1: the log said
`nav back to=ShowAll`, but the screen showed the ContentList row). Round 2 freezes `navigator.items.firstOrNull()`
(the tab root). The live label stays `navigator.lastItemOrNull`. Restoring the lost stack is out of scope; run
`tab-stack-lost-on-root-push` owns it.

### Round 2 constraints (they override the round-1 Scope line where they differ)

- Gates as in Global constraints: fast `./tools/agentw :app:compileDevDebugKotlin`, full
  `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`, androidTest compile
  `./tools/agentw :app:compileDevDebugAndroidTestKotlin`. Touched JVM tests:
  `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.core.ui.navigation.NavTelemetryTest' --tests 'com.kino.puber.core.ui.navigation.NavigatorChangedByTest'`.
  Worktree only, never `./gradlew`; `AGENT_GRADLE_SERIAL=1` if another worktree may build.
- Files that may change in round 2: `main/core/ui/navigation/component/NavTelemetry.kt`,
  `main/core/ui/navigation/component/FlowComponent.kt` (the bind call at `:588-591` and a comment there, nothing
  else; this replaces round 1's "no change to `FlowComponent.kt`"), `test/core/ui/navigation/NavTelemetryTest.kt`,
  `androidTest/core/ui/navigation/component/TabHostTelemetryWiringTest.kt`. No change to `TabComponent.kt`,
  `TabRouter.kt`, `MainScreen.kt`, `MainVM.kt`, build files, resources or `docs/architecture/`.
- `TabComponent.kt:116` (the replay guard `if (from !== event.tab) recordNav(...)`) stays as it is (D13).
- Every existing `NavTelemetryTest` case stays unchanged except `unbindWithTelemetryOffKeepsNoFrozenTopAndNeverReadsIt`
  (Task 5 widens its counter). `tabLocalTopIsFrozenAtUnbind` stays as it is and stays green (AC-8).
- Every new JVM test uses a fresh `MainScreen()` host (R9: a frozen binding outlives `tearDown` and stays inert only
  through host identity).
- One task = green fast gate + the touched tests in the state the task names + one commit. The message is one
  imperative English sentence with no prefix and never a `Co-Authored-By` line.
- The implementer never touches a device, adb, a lease, or `make itest`. Device work is `qa-device`'s.

**API correction to D11 (same contract, different Kotlin shape).** D11 proposes a default parameter
`reappearingTop: () -> Any? = top` after `top`. In Kotlin a trailing lambda always binds to the **last**
parameter, so every existing `TabHostTelemetry.bind(host) { ... }` call (`NavTelemetryTest.kt:144,152,165,175,186,201,212`
and `FlowComponent.kt:589`) would bind its lambda to `reappearingTop` and fail with "No value passed for parameter
'top'". The same applies to `Binding(handle.host) { frozen }` at `NavTelemetry.kt:39`. So:
- `Binding` takes both lambdas with **no** default: `internal class Binding(val host: Any, val top: () -> Any?, val reappearingTop: () -> Any?)`.
- `bind` becomes two overloads. The two-argument one keeps today's signature and passes `top` for both, so all
  existing callers compile and behave as before (AC-8):
  `fun bind(host: Any, top: () -> Any?): Binding = bind(host, top, top)` and
  `fun bind(host: Any, top: () -> Any?, reappearingTop: () -> Any?): Binding = Binding(host, top, reappearingTop).also { binding = it }`.
  `bind(host) { x }` resolves only to the first (the second needs three arguments); a named three-argument call
  resolves only to the second.
- `topOf` keeps reading `top`, unchanged.

### Code round 2 relies on (verified at `9e3b0a3a`)

- `main/core/ui/navigation/component/NavTelemetry.kt:21-26`: KDoc of `TabHostTelemetry` ("When the navigator leaves
  composition (the host is covered), the top it had is kept for that same host until the next bind.").
  `:27` `internal object TabHostTelemetry`. `:28` `internal class Binding(val host: Any, val top: () -> Any?)`.
  `:30-31` `@Volatile private var binding`. `:33` `fun bind(host: Any, top: () -> Any?): Binding`.
  `:35-43` `unbind`: `if (binding !== handle) return`, then if `Telemetry.isEnabled`, `val frozen = handle.top()`
  (`:38`) and `Binding(handle.host) { frozen }` (`:39`), else `null`. `:45` `topOf(host)` with `it.host === host`
  and `?.top?.invoke()`. `:48-51` `navClassOf`. `:54-59` `recordNav` (`:55` isEnabled guard, `:57` the only
  `Telemetry.screen` write). `:62-69` `recordNavCommand(event, from, top)`.
- `main/core/ui/navigation/component/FlowComponent.kt:583-587` the tab-flow `Navigator(screen = rootScreen,
  onBackPressed = null, key = tabFlowNavigatorKey(navigationSlotKey)) { navigator ->`. `:588-591`
  `DisposableEffect(tabHost, navigator) { val binding = tabHost?.let { TabHostTelemetry.bind(it) { navigator.lastItemOrNull } }; onDispose { binding?.let(TabHostTelemetry::unbind) } }`
  (`:589` bind, `:590` onDispose). `:537-542` `rootScreen = remember(...) { TabRootScreen(delegate = screen, tabInstanceKey = contentInstanceKey) }`.
  `:659-665` `TabRootScreen` with `contentScreenClass` (`:665`). `:688-699` `replaceTabRootIfChanged` (calls
  `navigator.puberReplaceAll(rootScreen)`, so `items.firstOrNull()` is always the current tab root). `navigator.items`
  is Voyager's stack list, already used at `:167,199,300,628`.
- `main/core/telemetry/TelemetryNames.kt:44-49` `telemetryClassOf`: `TabRootScreen` -> `contentScreenClass`, so a
  frozen tab root is labelled with the wrapped content class (`WiringHomeScreen` in the itest, `ContentList`/`Home`
  in the app).
- `main/core/ui/navigation/component/TabComponent.kt:100-104` `TabNavigator(..., disposeNestedNavigators = false, ...)`;
  `:128-137` `PuberCurrentTab` keeps the tab subtree under `tabNavigator.saveableState(currentTab.key)` (the state
  Voyager removes when the host is covered).
- `test/core/ui/navigation/NavTelemetryTest.kt`: `recorder` `:26`, `home` `:27` (`HomeScreen`), `other` `:28`
  (`ScreenScreen`), `binding` `:29`, `FakeActivityScreen` `:31-35`, `setUp` `:37-40`, `tearDown` `:42-46`, `navs()`
  `:48`, `bindTop` `:102-104` (positional `bind(host, top)`), `tabLocalTopIsFrozenAtUnbind` `:197-206`,
  `unbindWithTelemetryOffKeepsNoFrozenTopAndNeverReadsIt` `:208-222`, last test ends at `:228`, class closes `:229`.
  Imports `:3-23` already include `assertEquals`, `MainScreen`, `HomeScreen`, `ScreenScreen`, `Command` (same package).
- `androidTest/core/ui/navigation/component/TabHostTelemetryWiringTest.kt`: tags `:48-56` (`HOME_TAG`, `CHILD_TAG`,
  `BACK_TAG`, ...), `tabLocalPushThenRootPushThenBackNamesTheTabLocalScreen` `:148-162` (the `click(BACK_TAG, CHILD_TAG)`
  at `:155`), `click` `:203-207`, `mark` `:215-219`, `navsAfter` `:221-224`, `screensAfter` `:226-229`. Imports
  `:12` `onAllNodesWithTag`, `:38-41` `assertEquals`, `assertFalse`, `assertTrue` are already present.

---

### Task 5: Add the reappearing top to the tab binding and pin it with a failing test

**Covers:** AC-6 (JVM part, red first), AC-9, AC-8
**Files:**
- Modify: `main/core/ui/navigation/component/NavTelemetry.kt:28,33,38-39`
- Test: `test/core/ui/navigation/NavTelemetryTest.kt` (new case after `:206`; counter change in `:208-222`)

**Consumes:** `Binding` (`NavTelemetry.kt:28`), `bind` (`:33`), `unbind` (`:35-43`), `recordNavCommand` (`:62-69`),
the test helpers `navs()` (`NavTelemetryTest.kt:48`) and `FakeActivityScreen` (`:31-35`).
**Produces:** `Binding(host, top, reappearingTop)` (three properties, no default);
`TabHostTelemetry.bind(host, top)` (unchanged behaviour) and `TabHostTelemetry.bind(host, top, reappearingTop)`.
`unbind` still freezes `handle.top()` in this task, so runtime behaviour is unchanged.

- [x] `NavTelemetry.kt:28`: `internal class Binding(val host: Any, val top: () -> Any?, val reappearingTop: () -> Any?)`.
- [x] `NavTelemetry.kt:33`: replace with the two overloads from "API correction to D11" above. Give the three-argument
      one a KDoc line: "[reappearingTop] is what the host's tab will show when it is uncovered; [unbind] freezes it."
- [x] `NavTelemetry.kt:38-39`: keep reading `handle.top()` in this task, and build the frozen binding with both
      lambdas so it compiles: `val frozen = handle.top()` then `val keep = { frozen }` and `Binding(handle.host, keep, keep)`.
      The lambda captures only the value, never `handle` or the navigator.
- [x] Add, after `tabLocalTopIsFrozenAtUnbind` (`:206`), the failing-first test:
      ```kotlin
      @Test
      fun unbindFreezesTheReappearingTopNotTheLiveTop() {
          val host = MainScreen()
          val handle = TabHostTelemetry.bind(host, top = { other }, reappearingTop = { home })
          recordNavCommand(Command.Back, FakeActivityScreen(), host)
          TabHostTelemetry.unbind(handle)
          recordNavCommand(Command.Back, other, host)
          assertEquals(listOf<Class<*>?>(ScreenScreen::class.java, HomeScreen::class.java), navs().map { it.to })
          assertEquals(HomeScreen::class.java, Telemetry.screen)
      }
      ```
      The first Back proves the live label still reads `top`; the second proves the freeze reads `reappearingTop`.
- [x] `unbindWithTelemetryOffKeepsNoFrozenTopAndNeverReadsIt` (`:208-222`): count reads of both lambdas. Replace the
      `bind(host) { reads++; home }` call (`:212-215`) with
      `TabHostTelemetry.bind(host, top = { reads++; home }, reappearingTop = { reads++; home })` (multi-line blocks
      as today, so detekt's 120-column limit holds). The assertions `MainScreen` and `reads == 0` stay.
- [x] Run the touched JVM tests. Expected on this commit: exactly one failure,
      `unbindFreezesTheReappearingTopNotTheLiveTop`, at the list assertion, expected `[ScreenScreen, HomeScreen]`,
      actual `[ScreenScreen, ScreenScreen]`. Every other case passes, including `tabLocalTopIsFrozenAtUnbind`, the
      widened AC-9 case and `NavigatorChangedByTest`. Record the red output in the Implementation record.
- [x] Fast gate green; commit (for example "Add the reappearing tab top to the tab host telemetry binding").

**Done when:** the fast gate is green, the touched JVM run shows that one named failure with that actual value and
nothing else red, and there is one commit.

---

### Task 6: Freeze the reappearing top when the tab binding leaves composition

**Covers:** AC-6 (JVM part), AC-1, AC-2, AC-3, AC-8, AC-9
**Files:**
- Modify: `main/core/ui/navigation/component/NavTelemetry.kt:21-26` (KDoc), `:38` (the read in `unbind`)

**Consumes:** `Binding.reappearingTop` (Task 5), `Telemetry.isEnabled`.
**Produces:** `unbind(handle)`: when `handle` is the current binding and telemetry is on, the binding becomes a
frozen `Binding(handle.host, keep, keep)` whose value is `handle.reappearingTop()` read once; telemetry off clears
it to `null` without invoking either lambda; a stale handle is a no-op. Callers that use `bind(host, top)` get the
same result as before (`reappearingTop` is `top`).

- [x] `NavTelemetry.kt:38`: `val frozen = handle.reappearingTop()`. Nothing else in `unbind` changes.
- [x] KDoc `:21-26`: replace the second sentence with: "When the navigator leaves composition (the host is covered),
      the screen it will show again is kept for that same host until the next bind. Today that is the tab root,
      not the top: covering the host makes Voyager dispose the covered tab and its saved stack (run
      tab-stack-lost-on-root-push)." Keep the host-identity sentence.
- [x] Run the touched JVM tests: all green, including `unbindFreezesTheReappearingTopNotTheLiveTop` and
      `tabLocalTopIsFrozenAtUnbind`.
- [x] Fast gate green; commit (for example "Freeze the tab root that reappears when the tab host is covered").

**Done when:** the touched JVM tests are all green, the fast gate is green, and there is one commit. The app's
runtime behaviour is still round 1's until Task 8 (`FlowComponent.kt:589` still calls the two-argument `bind`).

---

### Task 7: Rewrite the instrumented AC-6 test to name the screen shown after Back

**Covers:** AC-6 (instrumented), R1 of the round-2 delta (coupling guard)
**Files:**
- Modify: `androidTest/core/ui/navigation/component/TabHostTelemetryWiringTest.kt:148-162`

**Consumes:** the fixture as it is (`showHost`, `openTab`, `click`, `mark`, `navsAfter`, `screensAfter`, the tags).
**Produces:** test `tabLocalPushThenRootPushThenBackNamesTheScreenShownAfterBack` replacing
`tabLocalPushThenRootPushThenBackNamesTheTabLocalScreen`. The class keeps six tests.

- [x] Rename the test and rewrite its body:
      ```kotlin
      // Pins today's behaviour on purpose: covering the host drops the tab's local stack, so Back shows
      // the tab root (Home), not Child. When run tab-stack-lost-on-root-push makes Child reappear, this
      // test goes red at the HOME_TAG wait; that run must then switch FlowComponent's reappearingTop to
      // the live top and restore the original AC-6 (Back names WiringChildScreen).
      @Test
      fun tabLocalPushThenRootPushThenBackNamesTheScreenShownAfterBack() {
          showHost()
          openTab(homeTab, HOME_TAG)
          click(HOME_LOCAL_TAG, CHILD_TAG)
          click(CHILD_DETAILS_TAG, DETAILS_TAG)
          val mark = mark()
          click(BACK_TAG, HOME_TAG)
          assertTrue(composeRule.onAllNodesWithTag(CHILD_TAG).fetchSemanticsNodes().isEmpty())

          val navs = navsAfter(mark)
          assertEquals(NavCmd.BACK, navs.first().cmd)
          assertEquals(WiringHomeScreen::class.java, navs.first().to)
          assertTrue(navs.none { it.to == WiringChildScreen::class.java || it.from == WiringChildScreen::class.java })
          assertTrue(screensAfter(mark).all { it == WiringHomeScreen::class.java })
          assertEquals(WiringHomeScreen::class.java, Telemetry.screen)
      }
      ```
      Keep lines within 120 columns (wrap the `navs.none` lambda if detekt asks).
- [x] Do not change any other test, helper or fixture declaration in the file.
- [x] Run `./tools/agentw :app:compileDevDebugAndroidTestKotlin` and the fast gate; both green. Do not run the tests.
      Expected device result on this commit (FlowComponent not yet changed, so the frozen value is still the live
      top `WiringChildScreen`): this test red at the `navs.first().to` assertion (expected `WiringHomeScreen`,
      actual `WiringChildScreen`), not at a wait; the other five green.
- [x] Commit (for example "Pin the tab root as the screen named after Back in the tab host wiring test").

**Done when:** androidTest compiles, the fast gate is green, the old test name no longer exists in the file, and
there is one commit.

---

### Task 8: Bind the tab root as the reappearing top in the tab flow

**Covers:** AC-6, AC-7, AC-10 (runtime), AC-8 (full gate)
**Files:**
- Modify: `main/core/ui/navigation/component/FlowComponent.kt:588-591`

**Consumes:** `TabHostTelemetry.bind(host, top, reappearingTop)` (Task 5), `unbind` freezing `reappearingTop`
(Task 6), Voyager `Navigator.items` and `lastItemOrNull`.
**Produces:** no new API. The tab-flow binding reads `lastItemOrNull` while live and freezes the root on cover.

- [x] Replace the `DisposableEffect` body at `:588-591` with:
      ```kotlin
      DisposableEffect(tabHost, navigator) {
          // Covering the host makes Voyager dispose the covered PuberTab and this navigator's saved stack,
          // so after Back the tab shows its root again: freeze the root, not the top. When run
          // tab-stack-lost-on-root-push keeps the stack, reappearingTop becomes the live top.
          val binding = tabHost?.let {
              TabHostTelemetry.bind(
                  it,
                  top = { navigator.lastItemOrNull },
                  reappearingTop = { navigator.items.firstOrNull() },
              )
          }
          onDispose { binding?.let(TabHostTelemetry::unbind) }
      }
      ```
      Indentation follows the surrounding block. Only the bind call and the comment change; `onDispose` is as before.
- [x] Run the fast gate, `./tools/agentw :app:compileDevDebugAndroidTestKotlin`, then the full gate
      `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`. All green.
- [x] Commit (for example "Label Back onto a covered tab host with the tab root it shows again").

**Done when:** the full gate is green, androidTest compiles, `FlowComponent.kt` differs from `9e3b0a3a` only in
`:588-591` (`git diff 9e3b0a3a -- app/src/main/java/com/kino/puber/core/ui/navigation/component/FlowComponent.kt`),
and there is one commit. Device evidence comes from `qa-device` after this task, not from the implementer.

---

### Device checks for qa-device, round 2 (after Task 8)

Same rules as round 1: lease via `tools/emulator-resource-lock.sh`, `adb -s <serial>` only, the worktree's own
dev APK built with `./tools/agentw` and installed with `tools/android-apk-install-preserve`, telemetry enabled, only
the needed log lines in evidence (`tools/mobile-evidence-audit.sh`). Append the results to
`docs/superpowers/evidence/2026-10-17-telemetry-tab-host-back-label-device.md`.

- **M4 (itest):** `make DEVICE=<serial> itest TESTS=com.kino.puber.core.ui.navigation.component.TabHostTelemetryWiringTest`.
  Expected: 6/6 green, including `tabLocalPushThenRootPushThenBackNamesTheScreenShownAfterBack`.
  Optional red proof: the same class on the Task 7 commit is red only in that test, at the `navs.first().to`
  assertion (actual `WiringChildScreen`), not at a wait.
- **M2 (AC-6, updated):** a tab with a "Show all" row (the qa round-1 path: Concerts, the "All" row, Show all),
  then open a title (Details), then `KEYCODE_BACK`.
  Expected: the screen after Back is the tab's ContentList (the tab root; the Show all screen is not restored,
  which is the known pre-existing defect owned by run `tab-stack-lost-on-root-push`). The log reads
  `nav back from=Details to=ContentList` and `scr=ContentList` from the Back until the next navigation.
  Must not happen: `to=ShowAll` or `scr=ShowAll` after the Back, `scr=Main` at any point after the Back, a
  `nav tab X->X` line, a crash. Before the Back, the push of Details from Show all must still read
  `from=ShowAll` (the live label is unchanged).
- **M1 (AC-10, regression):** Home, a title, Details, Back. Expected as round 1: `nav back to=Home`, `scr=Home`,
  `act Resumed` still `Main`, no `nav tab Home->Home`, focus kept on Home.
- **M3 (AC-5, regression):** select another tab from the rail, then reselect the current tab; a `nav tab` line
  for each, `scr` equals the selected tab content.

---

## Implementation record

<!-- owner: implementer -->

**Commits:**
- d1768a57 Pin the tab host Back label defect with telemetry tests
- 60495089 Add an instrumented test of the tab host telemetry wiring
- 7482ad51 Keep the tab host's last tab top when its tab navigator is covered (Task 3)
- Task 4: Skip the telemetry nav line for a replayed tab open (sha in git log)
- Round 2 (tasks 5-8):
  - 58c58b4b Add the reappearing tab top to the tab host telemetry binding
  - 287414a3 Freeze the tab root that reappears when the tab host is covered
  - 70200dee Pin the tab root as the screen named after Back in the tab host wiring test
  - f7862f5f Label Back onto a covered tab host with the tab root it shows again

Review round 1 fixes (triage-1.md rows 1-4): #1 `unbind` freezes `reappearingTop()` whether or not telemetry is on
(it records nothing), so telemetry enabled while the host is covered still labels Back with the tab root; the old
"off keeps no frozen top" test became `unbindWithTelemetryOffRecordsNothing`, plus the JVM case
`unbindWhileOffThenEnabledThenBackNamesTheTabRoot`. #2 the AC-6 itest asserts the PUSH from `WiringChildScreen` to
`WiringDetailsScreen` (itest compiled, not run: device work belongs to qa-device). #3 `TabHostTelemetry.resetForTests()`
called in `NavTelemetryTest.tearDown`. #4 the two-argument `bind` is removed; tests use a `bindSame` helper that passes
`reappearingTop` explicitly. Full gate green. Look at: `NavTelemetry.kt` unbind, the itest PUSH assertion.

Round 2: Task 5 red run on 58c58b4b: 17 tests, 1 failed, `NavTelemetryTest > unbindFreezesTheReappearingTopNotTheLiveTop()`
at NavTelemetryTest.kt:215 (the list assertion); all other cases green. After Task 6 all JVM cases green.
Task 7 commit (qa-device red itest run): 70200dee. Task 8: fast gate, androidTest compile and the full gate
(testDevDebugUnitTest, detektAll, lintDevDebug) green; detektAll/lint print "compiler errors found during
analysis" notices, the build is successful.

Tasks 3-4: touched JVM tests (NavTelemetryTest, NavigatorChangedByTest) all green after Task 3. After Task 4:
fast gate, `:app:compileDevDebugAndroidTestKotlin`, and the full gate (testDevDebugUnitTest, detektAll,
lintDevDebug) are green.

Task 1 red run on d1768a57 (NavTelemetryTest + NavigatorChangedByTest, 16 tests, 2 failed):
`NavTelemetryTest > tabLocalTopIsFrozenAtUnbind() FAILED (NavTelemetryTest.kt:205)` and
`NavTelemetryTest > disposedBindingKeepsTheTopForTheSameHost() FAILED (NavTelemetryTest.kt:146)`;
both asserted `HomeScreen` and got `MainScreen`. All other cases passed.
Task 2: `:app:compileDevDebugAndroidTestKotlin` and the fast gate are green; the instrumented class was not run
(device work belongs to qa-device).

**Deviations from the plan:**
- Task 2: per the readiness note the reselect test also asserts `Telemetry.screen == WiringHomeScreen`.
- Task 2: the wiring test builds a fresh `WiringTabHostScreen()` instance per test in `setContent`, and uses
  per-screen button tags (`wiring_home_details`, `wiring_other_details`, `wiring_child_details`).

**Files touched outside the plan:**

**For the reviewers:**
- Round 2: the Task 7 itest comment pins today's lost-stack behaviour on purpose; FlowComponent's `reappearingTop` is the one
  line to flip when `tab-stack-lost-on-root-push` lands.
- AC-6 instrumented test: pre-fix qa run timed out in `click(BACK_TAG, CHILD_TAG)` (1 s default `waitUntil`)
  rather than at the telemetry assertion. AC-3's identical wait on OTHER_TAG passed, so this is not plainly
  a fixture timing issue; the tab-local Child may not reappear after Back (tab navigator re-created). I did not
  change the test. qa-device must see whether it is green after the fix; if not, it is a real behaviour question.

**Blocked:**

## Readiness

<!-- owner: final-judge, mode readiness; the conductor pastes readiness-<n>.md here. READY | NOT READY and why. -->


Verdict: **READY**

Read at worktree `/home/user1/orca/workspaces/Puber/telemetry-tab-host-back-label`, HEAD `8c9143b6`
(spec commit on base `bdd2c792`; the plan is untracked in the worktree). Spec status `approved`, gate 1 recorded.

## AC coverage

| AC | Plan task | Test design row | Covered |
|----|-----------|-----------------|---------|
| AC-1 | T1 (`disposedBindingKeepsTheTopForTheSameHost`), T3 fix | unit P0 | yes |
| AC-2 | T1 (`frozenBindingOfHostADoesNotLeakIntoFreshHostB`), T3 | unit P0 | yes |
| AC-3 | T1 (three ordering tests), T2 (`tabSwitchThenRootPushAndBackNamesTheNewTab`), T3 | unit P0 + instrumented P1 | yes |
| AC-4 | T2 (`backOntoTheHostRecordsNoTabReannounce`), T4 fix | instrumented P0 | yes |
| AC-5 | T2 (two tests, incl. equal-but-distinct `PuberTab`), T4 | instrumented P0 | yes |
| AC-6 | T1 (`tabLocalTopIsFrozenAtUnbind`), T2 (`tabLocalPushThenRootPushThenBackNamesTheTabLocalScreen`) | unit P1 + instrumented P0 | yes |
| AC-7 | T2 (`backFromRootPushedScreenNeverRecordsMain`) | instrumented P0 | yes |
| AC-8 | T4 full gate; T1 keeps other cases untouched | unit P0 | yes |
| AC-9 | T1 (`unbindWithTelemetryOffKeepsNoFrozenTopAndNeverReadsIt`), T3 | unit P1 | yes |
| AC-10 | handed to qa-device after T4 ("Done when" of T4) | manual M1 | yes (device, qa-device) |

## Citations sampled (all resolved in code at 8c9143b6)

1. `NavTelemetry.kt:34-36` `unbind` with `if (binding === handle) binding = null`; `:38` `topOf` with `it.host === host`; `:21-25` KDoc "cleared when it leaves"; `:47-52` `recordNav`, `:48` isEnabled guard, `:50` the only `Telemetry.screen` write. Correct.
2. `FlowComponent.kt:588-591` `DisposableEffect(tabHost, navigator)` bind/unbind; `:228` `navChanged`; `:272` `recordNavCommand(event, navFrom, navChanged.lastItemOrNull)`; `:738-746` RootPuberScreen forwarded to `rootRouter`; `:82` `FlowComponent` signature; `:659,665` `TabRootScreen.contentScreenClass`. Correct.
3. `TabComponent.kt:34` `LocalTabHostScreen`; `:77-81` signature; `:108-119` collector; `:112-116` `Open` handling with `recordNav(NavCmd.TAB, from, event.tab)`; `:126` `PuberCurrentTab`. Correct.
4. `TabRouter.kt:13` `MutableSharedFlow(replay = 1)`, `:19` `openTab`, `:24` `Dispatchers.Main.immediate`. Correct.
5. `Telemetry.kt:5-8` `TelemetryRecorder`, `:33-35` `screen`, `:37` `isEnabled`, `:46-49` `install`, `:55-62` `resetForTests` (sets `screen = null`); `TelemetryClock.kt:11` `internal object JvmTelemetryClock`; `TelemetryNames.kt:44-49` `telemetryClassOf`; `TelemetryEvent.kt:140-147` `Nav`. Correct.
6. `NavTelemetryTest.kt:25-28,36-45,47,101-103,133-138,140-146`; `tearDown` resets Telemetry before unbinding (so after T3 the off-path clears a live binding). `RecordingTelemetryRecorder.isEnabled` is a `var`. Correct.
7. `MainScreen.kt:23-24,39` (`@Parcelize internal class`, `LocalTabHostScreen provides this`); `PuberScreen.kt:12` `RootPuberScreen`; `TabHostScreen.kt:4`; `TabType` has `Home` and `History`; `MainVM.kt:51,116,125,151,165` all open a freshly built `PuberTab` (`MainUIMapper.kt:83-92`). Correct.
8. `TabFlowLifecycleTest.kt:62-63,69-84,88-101,179-187,189-218,319-325,349-355,357-372`. Correct.

Only drift found: the spec's D5 cites `PuberTab.kt:20-26`; the real declaration is `:16-22` (`@Stable @Parcelize data class PuberTab` at `:18`). The plan cites `:16-22` correctly. Cosmetic.

## Build state and order

- T1 -> T2 -> T3 -> T4 follow the dependencies: failing-first tests, then the wiring test, then the two fixes. Each task needs a green fast gate (compile).
- T1 and T2 deliberately leave two JVM tests red (`disposedBindingKeepsTheTopForTheSameHost`, `tabLocalTopIsFrozenAtUnbind`) until T3. I accept this as the bug-run failing-first convention: compilation stays green, T1's "Done when" names exactly those two failures, and the commits are squashed before ship. I checked the expected base outcome of every new T1 case against the base `unbind`. Only those two fail; the other five pass on base, as the plan states.
- T4 runs the full gate and the androidTest compile.

## Scope

The plan stays inside Out of scope: no Robolectric, no `act Resumed` change, `TabFlowLifecycleTest` is not edited (fixture copied, no shared helpers), no `FlowComponent.kt`, `TabRouter.kt` or `MainVM.kt` change. No open question blocks (Q1-Q3 answered).

## Must fix

None.

## Notes for the conductor (non-blocking)

1. Failing-first proof for the instrumented class: the test design asks to run `TabHostTelemetryWiringTest` once on the pre-fix code (expected red for AC-7, AC-4, AC-6 and AC-3, green for both AC-5 tests). The plan only says qa-device "records" it and schedules no device step between T2 and T3. Without that run, a fixture where the tab navigator never disposes would pass AC-7 on the old code too, and the top risk R1 would go unchecked. Either dispatch qa-device on the T2 commit before T3, or have qa-device record why it was skipped. AC-10 on the real app partly covers this.
2. The plan does not assign the manual checks M2 (AC-6 on device) and M3 (AC-5 on device) to anyone. Include them in the qa-device brief with M1 and M4.
3. The test design wants the AC-5 instrumented reselect to assert both the `Nav TAB` and `Telemetry.screen`. Plan T2 asserts only the `Nav TAB` for the reselect. Adding `Telemetry.screen == WiringHomeScreen` is cheap.
4. R9: frozen bindings created without `bindTop` survive `tearDown`. They stay inert only because every test uses a fresh `MainScreen()`. Reviewers should check that no new test reuses a host across tests.

## Readiness (round 2)

<!-- owner: final-judge, mode readiness; the conductor pastes readiness-2.md here. READY | NOT READY and why. -->


Verdict: **READY**

Read at worktree `/home/user1/orca/workspaces/Puber/telemetry-tab-host-back-label`, HEAD `9e3b0a3a`. The plan's
round-2 section (Tasks 5-8 and the round-2 device checks) is uncommitted in the worktree. The spec has the rewritten
AC-6 and design delta D9-D14, and gate 1 delta is recorded in the spec's Decisions table and in `state.md`. Only
Tasks 5-8 are judged here. Tasks 1-4 were judged READY in round 1 and are implemented.

## AC coverage (round 2)

| AC | Plan task | Test / check | Covered |
|----|-----------|--------------|---------|
| AC-6 (rewritten) JVM | T5 red, T6 green | `NavTelemetryTest.unbindFreezesTheReappearingTopNotTheLiveTop` (live Back -> `ScreenScreen`, frozen Back -> `HomeScreen`) | yes |
| AC-6 instrumented | T7 (red on T7 commit, at the `to` assertion), T8 green | `TabHostTelemetryWiringTest.tabLocalPushThenRootPushThenBackNamesTheScreenShownAfterBack`, M4 | yes |
| AC-6 device | after T8 | round-2 M2 (updated: `to=ContentList`, `from=ShowAll` still live before Back) | yes (qa-device) |
| AC-9 | T5 | `unbindWithTelemetryOffKeepsNoFrozenTopAndNeverReadsIt` counts reads of both lambdas | yes |
| AC-8 | T5 (other cases unchanged, two-argument overload), T8 full gate | `check.full` | yes |
| AC-1/2/3 | T6 (unchanged behaviour through the two-argument `bind`) | existing JVM cases | yes |
| AC-7, AC-10, AC-5, AC-4 regression | T8 + qa-device | M4 6/6, M1, M3 | yes |

## Citations sampled (resolved in code at 9e3b0a3a)

1. `NavTelemetry.kt:21-26` KDoc, `:27` object, `:28` `Binding(val host: Any, val top: () -> Any?)`, `:33` `bind`,
   `:35-43` `unbind` with `:38` `val frozen = handle.top()` and `:39` `Binding(handle.host) { frozen }`, `:45` `topOf`.
   Correct.
2. `FlowComponent.kt:583-587` tab-flow `Navigator(screen = rootScreen, ..., key = tabFlowNavigatorKey(...))`,
   `:588-591` `DisposableEffect` (`:589` bind, `:590` onDispose), `:659-665` `TabRootScreen`/`contentScreenClass`,
   `:688-699` `replaceTabRootIfChanged` -> `puberReplaceAll(rootScreen)`, `navigator.items` used at `:167,199,300,628`.
   Correct. `rootScreen = remember(...)` is at `:536-541`, not `:537-542` (off by one, cosmetic).
3. `TelemetryNames.kt:44-49` `telemetryClassOf`, `TabRootScreen -> contentScreenClass` at `:47`. Correct.
4. `TabComponent.kt:100-104` `TabNavigator(... disposeNestedNavigators = false ...)`, `:128-137` `PuberCurrentTab` with
   `saveableState(currentTab.key)`. Correct. The plan's "`TabComponent.kt:116` replay guard" is at `:118`
   (`:115-117` is the comment). Cosmetic: the constraint is "no change to `TabComponent.kt`" anyway.
5. `NavTelemetryTest.kt`: `:26-29` fields, `:31-35` `FakeActivityScreen`, `:37-46` setUp/tearDown, `:48` `navs()`,
   `:102-104` `bindTop` (positional `bind(host, top)`), `:197-206` `tabLocalTopIsFrozenAtUnbind`, `:208-222` the AC-9
   case with `bind(host) {` at `:212-215`, end `:228-229`. Trailing-lambda callers at `:144,152,165,175,186,201,212`
   and `FlowComponent.kt:589`, as the plan lists. Correct.
6. `TabHostTelemetryWiringTest.kt`: tags `:48-56`, the AC-6 test `:148-162` with `click(BACK_TAG, CHILD_TAG)` at `:155`,
   `click` `:203-207`, `mark` `:215-219`, `navsAfter` `:221-224`, `screensAfter` `:226-229`, imports
   `onAllNodesWithTag` `:12`, `assertEquals/False/True` `:38-41`; 6 `@Test` methods. Correct.

## Logic checked

- Overload correction to D11: correct. With a default after `top`, `bind(host) { x }` would bind the trailing lambda to
  `reappearingTop` and leave `top` unpassed. With two overloads, `bind(host) { x }` and positional `bind(host, top)`
  resolve only to the two-argument form, and the named three-argument calls resolve only to the three-argument form.
  `Binding(handle.host) { frozen }` (`:39`) is the only other constructor call, and T5 rewrites it. The owner accepted it (state.md).
- T5's red claim: the live Back reads `top` (`ScreenScreen`). `unbind` still freezes `top()`, so the second Back is
  `ScreenScreen`, and the stated actual is `[ScreenScreen, ScreenScreen]`. `tabLocalTopIsFrozenAtUnbind` and the
  widened AC-9 case stay green. After T6, `[ScreenScreen, HomeScreen]`. Correct.
- T7's red claim on the T7 commit: `FlowComponent` still uses the two-argument `bind`, so the frozen value is the live
  top (`WiringChildScreen`). The stack is lost, so the `HOME_TAG` wait and the `CHILD_TAG`-empty assertion pass, and
  the test fails at `navs.first().to`. Correct, and that gives a real failing-first proof for the instrumented AC-6.
- T8: `navigator.items.firstOrNull()` is read in `onDispose`. Round-1 device evidence (`to=ShowAll`) shows the items
  are still intact when our effect is disposed, so the root is readable. `replaceTabRootIfChanged` keeps index 0 equal
  to the current root.

## Build state and order

T5 -> T6 -> T7 -> T8 follow the dependencies. Each task has a green fast gate. T5 deliberately leaves one named JVM
failure, under the same failing-first convention accepted in round 1 (squash before ship). T7 compiles androidTest
and does not run it. T8 runs the full gate and the androidTest compile, and limits the `FlowComponent.kt` diff to
`:588-591`.

## Scope

The plan stays inside the delta. The `FlowComponent.kt` change is limited to the bind call and a comment, which D12
allows. There are no changes to `TabComponent.kt`, `TabRouter.kt`, `MainVM.kt`, resources or build files. Restoring
the stack is left to run `tab-stack-lost-on-root-push`. No open question blocks: Q4 is answered, and Q5 and Q6 do not block.

## Must fix

None.

## Notes for the conductor (non-blocking)

1. The spec was only partly updated for the delta. These sections are stale: (a) `## Out of scope` lacks the line D-delta asked for:
   "Restoring a tab's local stack after a root push and Back (run `tab-stack-lost-on-root-push`)". (b) The Test
   design traceability AC-6 rows still name `tabLocalPushThenRootPushThenBackNamesTheTabLocalScreen` and expect
   the tab-local screen. (c) Manual check M2 in the spec still expects `nav back to=` the Show-all screen. The plan's
   round-2 M2 is the correct one. Point qa-device at the plan's "Device checks for qa-device, round 2", and update the
   three spec spots before the gate, so that gate traceability does not compare against superseded text.
2. Ask qa-device to run the optional T7-commit red proof for M4. It is the only failing-first evidence for the rewritten
   instrumented AC-6.
3. Citation drift (cosmetic): `rootScreen` is at `FlowComponent.kt:536-541`, and the replay guard is at `TabComponent.kt:118`.
