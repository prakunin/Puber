# Design: telemetry-tab-host-back-label

Read in worktree `/home/user1/orca/workspaces/Puber/telemetry-tab-host-back-label` at base `bdd2c792`. Every `path:line` below was opened and checked at that commit. Paths are relative to `app/src/main/java/com/kino/puber/` unless they start with `app/`.

## Decisions

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

## Changes by layer

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

## Alternatives rejected

- Re-announce on bind (research option B): `nav back to=Main` stays wrong, it writes `Telemetry.screen` outside `recordNav` (`Telemetry.kt:33`), and it does not stop the replay overwrite.
- Freeze a `Class<*>` in a second field or a sealed binding: more API surface and existing tests change, with no observable gain over D2.
- Keep the old `top` lambda after dispose: it reads a disposed `Navigator` and pins it, together with its saveable state.
- Deduplicate the replay in `TabRouter` (for example by consuming the replay cache): this changes navigation semantics that the re-entry relies on to restore the tab after a covered refresh.
- Compare tabs with `==` in D5: it drops a reselect of an equal-by-value tab (AC-5).
- Robolectric local Compose test (option C): build-infra change outside scope (Q3).
- Reuse `TabFlowLifecycleTest` fixtures: that class is red on master (tab-flow-lifecycle-itest).

## Risks (highest first)

1. **Lifecycle / correctness: the tab navigator's items at `onDispose`.** Freezing reads `navigator.lastItemOrNull` inside the `DisposableEffect` dispose at `FlowComponent.kt:590`. If Voyager's `Navigator` dispose (`voyager 2.2.21-1.10.3`, sources not available locally, `unverified`) emptied or changed items before our effect is forgotten, the frozen top would be `null`. The result is the current fallback `Main`, so no regression, but the defect would not be fixed. Compose forgets inner effects before outer ones, so ours should run first. AC-7 (instrumented) is the check.
2. **Correctness: replay identity (D5).** The fix relies on the restored `TabNavigator` (`TabComponent.kt:100-106`, inside the root `saveableState` at `FlowComponent.kt:501`) giving back the same `PuberTab` instance in memory. If Voyager's saver copies items, `from !== event.tab` holds and the `nav tab X->X` line comes back. That is harmless, but AC-4 fails. AC-4 in the instrumented test pins this. After process death the instance differs, and one extra line is acceptable.
3. **Correctness: a stale frozen top after an Open that arrives while the host is covered.** `MainVM` can `openTab` while Details is on top (`MainVM.kt:151,165`, preferences or menu changes). Back then records the frozen old tab. The replayed `Open` is a different instance, so `nav tab old->new` is recorded right after, which is correct and self-healing. Name it so reviewers do not take the brief `old` label for a regression.
4. **Concurrency: dispose order on a tab switch (D4).** It is safe in both orders through the handle guard at `NavTelemetry.kt:35`. Every caller runs on the main thread: the Compose apply and the `router.events()` collector in `LaunchedEffect` (`FlowComponent.kt:225-226`), plus the `TabRouter` emit on `Dispatchers.Main.immediate` (`TabRouter.kt:24`). `@Volatile` stays as belt-and-braces. Covered by JVM tests for both orders and the instrumented tab switch.
5. **Memory / lifecycle: a lingering frozen binding.** After NewRoot or logout, the frozen binding pins the old host and one screen object until the next `bind` (`NavTelemetry.kt:30`). The host-identity check makes it inert (AC-2). Today's live binding already pins the host in the same way.
6. **Test-shape risk: `clearedBindingGivesTheFallback` changes deliberately** (`NavTelemetryTest.kt:140-146`). The spec and the commit should say it pinned the defect, so reviewers do not read it as a weakened test.
7. **Rollout / verification.** The instrumented class is outside `check.full`. It needs a leased emulator and `make itest TESTS=...` (Makefile itest target), and the device repro (AC-10) is required. There is no baseline-profile or recomposition impact: no composable signature, parameter or state read changes.

## Open questions

None blocking. Not in scope, for the record: the unconditional live `bind` allocation when telemetry is off (D8) could be gated later; the `act Resumed`=`Main` label is by design.
