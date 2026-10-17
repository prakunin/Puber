# Keep a tab's stack across a root push and Back

Status: approved
Date: 2026-10-17
Slug: tab-stack-lost-on-root-push
Kind: bug
Size: M
Tracks: none

## Problem / Why

### Request
Bug. A tab-local stack (Concerts -> "Show all" grid) is lost after a root push (Details) and Back: the tab returns at its root and focus is lost. The telemetry coupling from run telemetry-tab-host-back-label must flip with the fix.

### Root cause (plausible, strong: read from Voyager bytecode)
1. `core/ui/navigation/component/FlowComponent.kt:123-126`: root `Navigator` uses the default `NavigatorDisposeBehavior(disposeNestedNavigators = true)`.
2. The tab list navigator (inside `TabNavigator`, `TabComponent.kt:100-106`) is a child of the root, so Voyager installs `NavigatorDisposableEffect` on it. When Details is pushed, MainScreen leaves composition and that effect disposes the current `PuberTab`: `SaveableStateHolder.removeState` for every key starting with the tab key.
3. `TabComponent.kt:128-137` `PuberCurrentTab` stores the tab subtree with `tabNavigator.saveableState(currentTab.key)`, which registers `"Tab:<screen>"` keys in exactly that navigator's `stateKeys`. The tab-flow `Navigator` stack (`FlowComponent.kt:583-587`, `rememberSaveable`) lives under that key, so it is wiped; on Back it restarts at `TabRootScreen` (`FlowComponent.kt:537-542`).
4. Tab switches keep stacks because nothing is disposed (`disposeSteps = false`).

Status: confirmed by bytecode reading and upstream source; not reproduced on a device. The implementer's first device step should reproduce it (before) as the baseline.

### Chosen approach
Tab host owns the saved state: in `TabComponent.kt` remember a `SaveableStateHolder` (`rememberSaveableStateHolder()`), provide it to `PuberCurrentTab`, and use `holder.SaveableStateProvider(currentTab.key) { currentTab.Content() }` instead of `tabNavigator.saveableState`. Voyager's dispose of the covered `PuberTab` then finds no `"Tab:"` keys. The holder dies with MainScreen, so there is no leak after logout/NewRoot, and the root flag stays untouched (rejects option A: leak and stale-revival class, research.md: 6A/7.1; option C: second source of truth).

### Risks (analyst)
1. Lifecycle owner change: `PuberTab` no longer has its own Voyager `ScreenLifecycleOwner`; tab-flow screens' owner parent becomes MainScreen's (unverified). Guards: AC-9, AC-10.
2. Focus after Back depends on `focusRestorer` / `saveFocusedChild` over a recreated subtree; `rootAnchorCaptureRegistry` covers only `LazyListState` screens, ShowAll uses a grid state (`ShowAllScreenContent.kt:71-72`). May need a ShowAll hook.
3. Telemetry: stack restored but `reappearingTop` not flipped gives a wrong Back label; the flipped itest guards it. Never re-add a `Telemetry.isEnabled` gate in `unbind`.
4. Koin DIScope closes on root push while retained VMs are reused (exists today; harmless for tab-flow screens via `TabFlowSession` routers, risky for nested `FlowComponent` routers such as Settings).
5. Removed tabs keep a small saved state until MainScreen dies (minor; optional `removeState`).
6. `TabFlowLifecycleTest` has been red on master, so an unrelated failure may surface behind the first assertion.
7. Voyager version is a pre-release (2.2.21-1.10.3); behaviour was read from bytecode, so device confirmation is mandatory.

## Acceptance criteria

- AC-1. Given Concerts (or Home) tab, a "Show all" grid opened and scrolled, a card focused; When that card opens Details (root push) and the user presses Back; Then the Show all grid is shown (not the tab root), at its previous scroll position.
- AC-2. Given AC-1; When Back lands; Then focus is inside the Show all grid on the card that opened Details. Fallback if the device shows only grid-level focus: accepted only if a ShowAll-local fix is not small; then record a follow-up (owner judges; see Q2).
- AC-3. Given a tab stack of depth 2 on tab X and another stack on tab Y; When a root push and Back happen; Then both stacks are intact (the current tab at its live top, the other tab on switching).
- AC-4 (telemetry flip). Given a tab-local push, then a root push, then Back; Then `TabHostTelemetry` names the tab-local screen: `reappearingTop` resolves to `navigator.lastItemOrNull` (`FlowComponent.kt:588-599`), `Telemetry.screen` is that screen, and no later line overwrites it with the tab root.
- AC-5. `TabHostTelemetryWiringTest` AC-6 test (`tabLocalPushThenRootPushThenBackNamesTheScreenShownAfterBack`) expects the tab-local screen (`WiringChildScreen`), is green on a device via `make GRADLE=./tools/agentw DEVICE=<serial> itest TESTS=...`, and the other cases in that class stay green.
- AC-6. `NavTelemetry.unbind` (`NavTelemetry.kt:32-55`) has no `Telemetry.isEnabled` gate (check by grep/read; `NavTelemetryTest` stays green).
- AC-7 (no leak). Given a tab stack deeper than root, When the user logs out or the session expires and `MainScreen` is replaced by auth and later by a fresh MainScreen; Then the new MainScreen starts at tab roots (no previous stack restored) and the old `PuberTab` saved state is not retained (observed on device and/or a unit/itest on holder lifetime).
- AC-8. Tab switches (rail) still keep each tab's stack as before, and the Settings tab after a root push (hotkey) does not show a stuck spinner. (If it still does, record in `settings-tab-spinner-stuck`; not required to fix.)
- AC-9. Content `LifecycleAction` users (e.g. ContentList refresh) still receive `ON_RESUME` when returning from a root push (observed).
- AC-10. `TabFlowLifecycleTest` passes on a device with `substring = true` and its "one live VM" check after the root push.
- AC-11. `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` is green; `docs/architecture/navigation.md` Tabs describes the holder ownership.

## Out of scope

- Clearing saved state of tabs removed from the rail (follow-up only on evidence).
- Generalising grid focus restore to other grid screens (follow-up idea if the ShowAll fix is needed).
- Fixing settings-tab-spinner-stuck (separate run; the post-fix observation is recorded there).
- Changing the root navigator's nested-dispose behaviour.

## Design

Base `f14dcc84`, worktree `/home/user1/orca/workspaces/Puber/tab-stack-lost-on-root-push`. Production paths are relative to `app/src/main/java/com/kino/puber/`. Every `path:line` below was re-read in the worktree. The Voyager claims come from `javap -c` on `voyager-{navigator,tab-navigator,core}-android 2.2.21-1.10.3`. They were read, not run.

Two Voyager facts were re-checked for this design, beyond research.md:
- `TabNavigator.saveableState(key, tab, content)` delegates to `Navigator.saveableState(key, tab, content)` on the tab list navigator.
- That method does three things: it adds `"${screen.key}:$key"` to `stateKeys`, calls `rememberScreenLifecycleOwner(screen)` plus `getNavigatorScreenLifecycleProvider(screen)`, and then calls `stateHolder.SaveableStateProvider`.
- So today `PuberCurrentTab` gives the tab subtree two things: a `"Tab:..."` saved-state entry, which Voyager removes when it disposes the covered `PuberTab`, and a per-tab `AndroidScreenLifecycleOwner`.
- Every `Navigator` shares one `SaveableStateHolder`, which the root provides through `LocalNavigatorStateHolder` (`NavigatorKt` calls `rememberSaveableStateHolder` once).

### Decisions

| # | Question | Decision | Why |
|---|----------|----------|-----|
| D1 | Who owns the tab subtree's saved state? | The tab host owns it. `TabComponent` (`core/ui/navigation/component/TabComponent.kt:76-126`) calls `rememberSaveableStateHolder()` once, before the `CompositionLocalProvider` at `:99`. It provides the holder through a new `internal val LocalTabSubtreeStateHolder = staticCompositionLocalOf<SaveableStateHolder?> { null }` next to `LocalTabAppRouterHolder` (`:31`). | Option B, as decided in state.md. The holder's own state is stored in MainScreen's slot of the root holder: `CurrentScreen` → `navigator.saveableState(screenKey)`, `core/ui/navigation/component/FlowComponent.kt:492-503`. So the holder lives exactly as long as the tab host. When MainScreen leaves the root stack it goes, which covers logout and session expiry. A root push leaves it alone. Putting it in `TabComponent` rather than `PuberCurrentTab` ties it to the component that owns the `TabNavigator`, whatever its callers do. |
| D2 | How does `PuberCurrentTab` save the subtree? | `PuberCurrentTab` (`TabComponent.kt:128-138`) keeps `key(currentTab.key)` (`:133`). It replaces `tabNavigator.saveableState(currentTab.key) { … }` (`:134-136`) with `holder.SaveableStateProvider(currentTab.key) { currentTab.Content() }`, where `holder = checkNotNull(LocalTabSubtreeStateHolder.current) { "PuberCurrentTab needs an enclosing TabComponent" }`. | Nothing registers a `"Tab:"` key in the tab list navigator's `stateKeys` any more. Voyager's `NavigatorDisposableEffect` still disposes the covered `PuberTab` on a root push, but its `removeState(startsWith "Tab:<screen>")` now finds nothing to remove. The tab-flow `Navigator` stack (`FlowComponent.kt:583-587`, `rememberSaveable` under that provider) survives. All four callers (`ui/feature/main/component/MainScreenComponent.kt:292-304` and the three androidTest fixtures at `HomeCollectionBackFocusTest.kt:300-310`, `TabHostTelemetryWiringTest.kt:287-292`, `TabFlowLifecycleTest.kt:211-216`) already sit inside `TabComponent`. Failing fast follows the `checkNotNull(LocalPuberKoinScope.current)` pattern (`FlowComponent.kt:100`). The provider key stays `currentTab.key` (`"Tab:<screen>"`, `core/ui/navigation/PuberTab.kt:25,37`), a `String`, so it is Bundle-saveable. |
| D3 | The root navigator's dispose flag | It is not touched. `FlowComponent.kt:123-126` keeps Voyager's default `disposeNestedNavigators = true`. | Option A was rejected (see Alternatives). |
| D4 | Telemetry flip | In `FlowComponent.kt:588-599`, `reappearingTop = { navigator.items.firstOrNull() }` (`:596`) becomes the live top, `{ navigator.lastItemOrNull }`, the same expression as `top`. The two-lambda `bind` API (`core/ui/navigation/component/NavTelemetry.kt:33,39-40`) and the ungated `unbind` (`:42-47`) stay. The comment at `:589-591` is rewritten: the host-owned holder keeps the tab stack across a cover, so the screen that reappears is the live top. | Restores AC-6 of telemetry-tab-host-back-label in its original form (spec `docs/superpowers/specs/2026-10-17-telemetry-tab-host-back-label.md:48`, "Superseded text"). Amendment A1 (`:212`, `:232`) forbids a `Telemetry.isEnabled` gate in `unbind`, so none is added. Keeping two lambdas leaves `NavTelemetryTest` and A2 (`:213`, every bind site chooses explicitly) unchanged. |
| D5 | NavTelemetry KDoc | `NavTelemetry.kt:22-31` loses the sentence "Today that is the tab root, not the top: covering the host makes Voyager dispose the covered tab and its saved stack (run tab-stack-lost-on-root-push)". It now says that the screen kept is the tab's live top, because the tab host keeps the tab's saved state while it is covered. The rest of the KDoc, including the telemetry-off sentence, is unchanged. | The KDoc would otherwise describe the bug as current behaviour. |
| D6 | JVM telemetry tests | Behaviour is unchanged. `NavTelemetryTest.unbindFreezesTheReappearingTopNotTheLiveTop` (`app/src/test/kotlin/com/kino/puber/core/ui/navigation/NavTelemetryTest.kt:211-219`) tests `unbind` semantics with supplied lambdas and stays as is. `unbindWhileOffThenEnabledThenBackNamesTheTabRoot` (`:232`) is renamed to `…NamesTheReappearingTop`. Its body is unchanged. | Its name asserts "tab root", which is no longer what the production caller freezes. The test supplies the lambda itself, so only the name is wrong. |
| D7 | Instrumented telemetry test | `TabHostTelemetryWiringTest.tabLocalPushThenRootPushThenBackNamesTheScreenShownAfterBack` (`app/src/androidTest/kotlin/com/kino/puber/core/ui/navigation/component/TabHostTelemetryWiringTest.kt:148-173`) goes back to the original AC-6: `click(BACK_TAG, CHILD_TAG)`, then `WiringChildScreen` for `navs.first().to`, for every `screensAfter(mark)` and for `Telemetry.screen`, and no `Nav` after the mark names `WiringHomeScreen`. The tab-local PUSH assertions at `:159-160` stay. The pinning comment at `:148-151` is removed. | This test drives the real `FlowComponent`, `TabComponent` and `PuberCurrentTab` (`:191-206`, `:283-295`), so it is both the stack-restore reproducer and the telemetry coupling guard (R11 of the previous run). The test-designer owns any extra cases. |
| D8 | `TabFlowLifecycleTest` | `:122` changes to `assertTextContains("content=0", substring = true)`. Every other `assertTextContains` on `HISTORY_TAG` in that class gets the same fix (`:131`, `:134`, `:137`, `:175`), because the text is `"content=N vm=M"` (`:247`). | Folded in from tab-flow-lifecycle-itest (state.md Q2). These are the only instrumented checks of the root push lifecycle (`openDestinationAndReturn`, `:150-161`) and of "one live VM" (`:153-161`). `:131` would otherwise turn red right after `:122` is fixed. |
| D9 | Exact card focus after Back (AC-2) | Conditional and ShowAll-local. The implementer first observes, on the device, where focus lands after Back. If it lands on the card that opened Details, nothing changes. If it does not, only `ui/feature/showall/content/ShowAllScreenContent.kt` changes, inside `ShowAllContentBody` (`:66-116`). (a) Add `rememberSaveable` state for the last focused item id, written from each card's `Modifier.onFocusChanged` (`VideoItemHorizontal` takes a `modifier`, `core/ui/uikit/component/moviesList/VideoItemHorizontal.kt:48-49`). (b) Add one remembered `FocusRequester`, attached only to the card whose id matches. (c) Add a `LaunchedEffect(Unit)` that runs when the body re-enters composition with a remembered id while `LocalContentFocusActive.current` is true. It tries `requestFocus()` on that requester for up to 3 frames, the same pattern as `requestAfterAnchorRestore`, `core/ui/uikit/component/moviesList/FocusTargetState.kt:295-300`, and on failure leaves focus alone. The first launch keeps `rememberFocusRequesterOnLaunch` (`:71`, `:96`). Budget: about 30 lines in that one file, with no change to `FlowComponent` or `RootAnchorCaptureRegistry`. If more is needed, the change is deferred and a follow-up run is recorded (state.md Q1). | The restored grid gets its scroll back from `rememberLazyGridState()` (`:72`, saveable). The launch flag (`core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:51`) is restored as true, so nothing re-requests focus. The root anchor path only serves `LazyListState` screens (`FlowComponent.kt:437-475`, call sites at `HomeScreenContent.kt:210` and `ContentListScreenContent.kt:139`), and `RestoreTabContentFocusEffect` (`FlowComponent.kt:651-665`) fires only when the tab stack shrinks. So focus after Back will probably not reach a card without (c). The rows already use the same pattern, saveable id plus requester: `rememberReconciledItemFocus`, `FocusTargetState.kt:40-57`. |
| D10 | Removed tabs' saved state | No `removeState` for tabs that leave the tab list in this run. | Each entry is small (one navigator stack plus nested registries). It dies with MainScreen and is not needed for any AC. Listed under Open questions. |
| D11 | Docs | `docs/architecture/navigation.md` "Tabs" (`:83-93`) gets one paragraph. `TabComponent` owns a `SaveableStateHolder` for tab subtrees, and `PuberCurrentTab` provides it per tab key, so a tab's stack survives both tab switches and a root push covering the host. The paragraph says why: Voyager's nested dispose of the covered `PuberTab` would remove a `"Tab:"` state registered on the tab list navigator. It also says the holder dies with the host, for example on logout. In the previous run's spec, AC-6 (`:48`) gets the note "restored by tab-stack-lost-on-root-push", and the amendment note (`:232`) records that `reappearingTop` is now the live top and that `unbind` stays ungated. | AC-11, plus the brief's plan step 6. |

### Changes by layer

**API / repository**: none.

**ViewModel state and actions**: none. `ShowAllVM`, `MainVM` and `TabAppRouterHolder` are untouched. The retained tab-flow VMs, for example `ShowAllScreen_<id>`, `ui/feature/showall/ShowAllScreen.kt:33`, are now reached again by the restored stack, where today they are orphaned.

**Screen and DI module**: no Koin module changes. Navigation glue:
- `core/ui/navigation/component/TabComponent.kt`:
  - New `LocalTabSubtreeStateHolder` near `:31`.
  - `rememberSaveableStateHolder()` in `TabComponent` (`:76-126`), provided together with `LocalTabAppRouterHolder` in the provider at `:99`.
  - `PuberCurrentTab` (`:128-138`) uses `holder.SaveableStateProvider(currentTab.key)` under the existing `key(currentTab.key)`.
  - New imports: `androidx.compose.runtime.saveable.SaveableStateHolder` and `rememberSaveableStateHolder`.
- `core/ui/navigation/component/FlowComponent.kt:588-599`: `reappearingTop` becomes `{ navigator.lastItemOrNull }` and the comment at `:589-591` is rewritten. Nothing else in this file changes. In particular, the root `Navigator` at `:123-126` stays as it is.
- `core/ui/navigation/component/NavTelemetry.kt:22-31`: KDoc only.

**Compose content**: `ui/feature/showall/content/ShowAllScreenContent.kt` `ShowAllContentBody` (`:66-116`) changes only if D9's device check shows the card is not focused. The changes are a saveable focused id, a requester on the matching card (the `VideoItemHorizontal` call at `:103-107` gains a `modifier`), and a re-entry focus effect. Content stays pure, with state plus `onAction`, and the new state is UI-local.

**Resources**: none.

**Tests**:
- `TabHostTelemetryWiringTest.kt:148-173` is flipped (D7).
- `TabFlowLifecycleTest.kt:122,131,134,137,175` get `substring = true` (D8).
- `NavTelemetryTest.kt:232` is renamed (D6).
- New cases belong to the test-designer. Suggested coverage:
  - AC-3: two tabs with stacks, root push, Back, switch.
  - AC-7: the host is replaced by a non-host root screen and then a fresh host, and the fresh host starts at the tab root.
  - Both can reuse the `TabHostTelemetryWiringTest` fixture (`WiringTabHostScreen`, `:283-295`).

**Docs**: `docs/architecture/navigation.md` Tabs, and the previous spec at `:48` and `:232` (D11).

### Alternatives rejected

- A: root `Navigator(disposeNestedNavigators = false)` (`FlowComponent.kt:123`). It applies to every direct child of the root: the MainScreen tab list, a root-pushed `DeviceSettingsFlowScreen`, and whatever comes later. On logout nothing would dispose the tab-flow screens, so they would survive into the next session and be revived under stable keys (Voyager #402 class). It would also need manual disposal through the internal `children` registry.
- C: keep each tab stack in `TabFlowSession` and reseed on re-entry. That creates a second source of truth next to Voyager's saver. Process death still depends on `rememberSaveable`, and it conflicts with `replaceTabRootIfChanged` (`FlowComponent.kt:697-709`).
- The holder inside `PuberCurrentTab` instead of `TabComponent`. It works today, but its lifetime would then depend on where callers compose `PuberCurrentTab`, not on the host.
- A CompositionLocal fallback (`?: rememberSaveableStateHolder()`) in `PuberCurrentTab`. It would hide a missing host and silently give a holder with the wrong lifetime.
- Collapsing `top` and `reappearingTop` into one lambda, or adding a default. That is API churn that amendment A2 rejected on purpose, and no AC needs it.
- A generic tab-level focus restore in `TabFlowNavigator` or `RootAnchorCaptureRegistry` for grids. It is larger than the ShowAll-local fix and touches every tab screen. It is deferred if D9 is not enough.

### Risks (highest first)

1. **Focus and D-pad after Back (AC-2).** The restored ShowAll grid may end up with focus nowhere, or on the grid's first card, because the launch flag is restored and no anchor restore covers grids (`FlowComponent.kt:437-475`, `FlowComponent.kt:651-665`, `FocusOnLaunchRequester.kt:51`). If D9(c) runs while the rail is open, it could also steal focus from the rail. That is why it is gated on `LocalContentFocusActive` (`MainScreenComponent.kt:137-141`). Lives in `ShowAllScreenContent.kt:66-116`. Guard: a device check of Concerts → Show all → scroll → card → Details → Back, and a rail-open check.
2. **Lifecycle owner change.** The tab subtree no longer gets a per-`PuberTab` `AndroidScreenLifecycleOwner` (Voyager `Navigator.saveableState` → `rememberScreenLifecycleOwner`). Tab-flow screens' owners now take `LocalLifecycleOwner` from MainScreen's owner. Production code reads `LocalLifecycleOwner` only inside screens (`core/ui/uikit/component/LifecycleAction.kt:16`, `ui/feature/device/settings/DeviceSettingsContent.kt:202`, trailer players). No VM is resolved between `PuberCurrentTab` and the tab-flow `CurrentScreen` (`PuberTab.kt:48-68`, `FlowComponent.kt:526-565`, `core/di/DIScope.kt:30-51`). Expected to be equivalent, but unverified. Lives in `TabComponent.kt:128-138`. Guard: AC-9 (ON_RESUME for `HomeScreen`/`HistoryScreen` `LifecycleAction` after a root Back) and AC-10.
3. **Telemetry coupling.** If the stack is restored but `reappearingTop` is not flipped, Back is labelled with the tab root while ShowAll is on screen. Also, if Voyager emptied `navigator.items` before `onDispose` ran, the frozen top would be `null` and Back would read `Main` (R1 of the previous run). The tab-flow navigator is not disposed (its parent passes `disposeNestedNavigators = false`, `TabComponent.kt:104`), so items should be intact. Lives in `FlowComponent.kt:588-599`. Guard: D7 itest (AC-5). Do not add a `Telemetry.isEnabled` gate to `NavTelemetry.kt:42-47` (AC-6).
4. **Koin scope versus retained VMs (pre-existing, now reached more often).** On a root push the `DIScope`s inside MainScreen close (`core/di/ScopeModuleManager.kt`, `remember`/`onForgotten`). Restored screens then reuse VMs that hold dependencies resolved from closed scopes. Tab-flow routers are safe because they come from `TabFlowSession` (`TabComponent.kt:36-74`). A nested `FlowComponent` router, as in Settings (`FlowComponent.kt:100-101`, recreated per composition), is not. With the stack restored, the Settings tab after a root push now restores its nested flow instead of restarting at `LoadingScreen`, which may change the `settings-tab-spinner-stuck` symptom in either direction. Guard: AC-8 device check. Record what you see there; do not fix it here.
5. **Saved state lifetime and revival.** The holder lives in MainScreen's root-holder slot:
   - Logout and session expiry replace MainScreen with Auth (`ui/feature/auth/vm/AuthVM.kt:134`, `ui/feature/root/component/App.kt:101`), so it is removed, which satisfies AC-7.
   - A warm launch does `newRootScreens([main, target])` (`App.kt:118-120`) with the same key `"MainScreen"` (`core/ui/navigation/PuberScreen.kt:8-9`). That keeps the holder, so a warm launch over an open tab stack now returns to that stack. Same account; this matches what the tab list itself already does.
   - Separately and unchanged: tab-flow screens' `ScreenLifecycleStore` entries are never disposed, because their parent navigator does not dispose nested navigators. That predates this change and is not widened by it.
   Guard: AC-7 on the device, or an itest.
6. **Recomposition and saved-state shape.** `SaveableStateProvider` keys must be unique among active providers and Bundle-saveable. There is one current tab under `key(currentTab.key)`, and the key is a `String`. On a `contentInstanceKey` change, `replaceTabRootIfChanged` (`FlowComponent.kt:697-709`) still resets the restored stack. That is intended, but a refresh while the host is covered will land on the new root, not the restored stack. Guard: `TabFlowLifecycleTest` (refresh cases at `:133-148`).
7. **Memory.** Every tab ever shown keeps its subtree state until MainScreen dies: Bundle-sized navigator stacks and screen registries, no compositions. It is retained for the current tab during a root push, and for removed tabs (D10). This is negligible.
8. **Voyager pre-release.** The dispose path was read from `2.2.21-1.10.3` bytecode. A Voyager upgrade that changes `saveableState` or the dispose rules could make D1 redundant, but not wrong. Guard: D7 itest on every run of `make itest`.
9. **Baseline profile.** The startup and tab-switch path changes slightly (one holder per host). No regeneration is needed for correctness. Mentioned only for completeness.

### Open questions

1. Should `TabComponent` call `holder.removeState(key)` for tabs that drop out of the tab list, for example when the tab configuration changes? Recommended: no, keep it as a follow-up idea only if memory or revival evidence appears. Blocks: no.
2. If D9's ShowAll-local fix turns out to be needed, the same "focus nowhere after root Back" gap probably affects other grid or non-`LazyListState` tab-local screens. Recommended: record a follow-up idea naming the screens observed. Do not generalise in this run. Blocks: no.

## Test design

Base `f14dcc84`. Worktree `/home/user1/orca/workspaces/Puber/tab-stack-lost-on-root-push` (read only here). Production paths relative to `app/src/main/java/com/kino/puber/`. Inputs: brief.md (11 ACs), design-architect.md (D1-D11, R1-R9), state.md Decisions.

### Levels in play

- `unit`: JUnit 5 under `app/src/test`. Only the telemetry JVM tests apply (`NavTelemetryTest`). The bug lives in Compose, Voyager and `SaveableStateHolder`, and Robolectric is not allowed, so no new unit test can fail on the base commit for it. Said once so the gap is not read as an omission.
- `instrumented`: JUnit 4 + `createComposeRule`, the harness style of the existing androidTest classes. Run only by qa-device with `make GRADLE=./tools/agentw DEVICE=<serial> itest TESTS=<fqcn>`. Harnesses: `TabHostTelemetryWiringTest` (real `FlowComponent` + `TabComponent` + `PuberCurrentTab`, host `WiringTabHostScreen`, root `WiringDetailsScreen`) and `TabFlowLifecycleTest`.
- `device`: manual D-pad observation on an emulator, recorded in the evidence file.

### Risk profile

P = probability 1-3, I = impact 1-3. Source: brief R1-R7, design R1-R9.

| # | Risk | P | I | Score | Test at the level where it fails |
|---|---|---|---|---|---|
| K1 | The tab-local stack is still lost after a root push, because the holder swap in D1/D2 does not stop Voyager's nested dispose (read from bytecode, never run) | 2 | 3 | 6 | `instrumented` T1, T2, T3 (fail on base, see Failing-first) plus `device` M1 |
| K2 | `reappearingTop` not flipped: stack restored but Back is labelled with the tab root while ShowAll is shown (design R3) | 2 | 3 | 6 | `instrumented` T1 (asserts `navs.first().to`, `screensAfter`, `Telemetry.screen`) |
| K3 | Focus after Back lands nowhere or on the first card, because the launch flag is restored and no anchor covers grids (design R1) | 3 | 2 | 6 | `device` M1 (AC-2), `instrumented` T8 only if D9 is implemented |
| K4 | Lifecycle owner change: `PuberTab` loses its per-tab `AndroidScreenLifecycleOwner`, so `ON_RESUME` for `LifecycleAction` users or the "one live VM" count changes (design R2) | 2 | 3 | 6 | `instrumented` T5 (ON_RESUME), T6 (`TabFlowLifecycleTest`), `device` M3 |
| K5 | Saved state of a replaced MainScreen leaks into the next session: logout then fresh host restores the old stack (design R5, AC-7) | 2 | 3 | 6 | `instrumented` T4 plus `device` M4 |
| K6 | Rail tab switch regresses: the holder keyed by `currentTab.key` drops or mixes a tab's stack (design R6) | 1 | 3 | 3 | `instrumented` T2 and the existing `TabFlowLifecycleTest` tab-switch steps |
| K7 | The `TabFlowLifecycleTest` fix (`substring = true`) uncovers an unrelated red behind the first assertion (brief R6) | 2 | 2 | 4 | `instrumented` T6, run green before and after the production change to separate causes |
| K8 | A `Telemetry.isEnabled` gate is re-added to `unbind` (design R3, spec A1) | 1 | 2 | 2 | Accepted: `NavTelemetryTest.unbindWithTelemetryOffRecordsNothing` and `unbindWhileOffThen...` stay green; AC-6 is a read/grep check |
| K9 | Settings tab after a root push changes spinner behaviour, either way (design R4) | 2 | 2 | 4 | `device` M5 (observe and record only; not a fix target) |
| K10 | Removed tabs keep a small saved state; memory (design R7, D10) | 1 | 1 | 1 | Accepted with a note: dies with MainScreen, bounded by tab count |
| K11 | Voyager pre-release changes its dispose rules (design R8) | 1 | 2 | 2 | Accepted: T1 runs on every `make itest` |
| K12 | Baseline profile shifts (design R9) | 1 | 1 | 1 | Accepted: no correctness effect |
| K13 | D9 focus effect steals focus from the rail when it fires while the rail is open | 2 | 2 | 4 | `device` M2, only if D9 is implemented |

### Traceability

Test names are the proposal; the implementer may adjust naming but not the asserted behaviour. All new instrumented cases go into `app/src/androidTest/kotlin/com/kino/puber/core/ui/navigation/component/TabHostTelemetryWiringTest.kt` and reuse its fixture (see "Fixture additions").

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | instrumented | P0 | T1 `tabLocalPushThenRootPushThenBackNamesTheScreenShownAfterBack` (flipped, existing) and T2 `rootPushAndBackRestoresBothTabStacks`. Both wait for `CHILD_TAG` after Back and assert the tab root `HOME_TAG` is absent | The tab-local screen (stand-in for Show all) is shown after a root push and Back, not the tab root |
| AC-1 (scroll) | device | P0 | M1 steps 5-6 | Scroll position of the real grid survives. Not instrumentable cheaply: `ShowAllContentBody` is private, scroll comes from `rememberLazyGridState` (saveable) and is restored with the stack |
| AC-2 | device | P1 | M1 steps 7-8 | Focus is on the card that opened Details, or at least inside the grid (fallback per Q1) |
| AC-2 | instrumented | P2 (conditional on D9) | T8 `showAllFocusReturnsToTheFocusedCardAfterReentry`, new class `ShowAllScreenContentFocusTest` using `StateRestorationTester` as in `NetworkDiagnosticsContentFocusTest.kt:161`. Needs `ShowAllContentBody` made `internal` or its public wrapper driven with a `ShowAllViewState.Content` of 20+ items | After recreation with a remembered id, the card of that id is focused while `LocalContentFocusActive` is true, and nothing is focused when it is false |
| AC-3 | instrumented | P0 | T2 `rootPushAndBackRestoresBothTabStacks`: Home pushes Child, switch to Other, Other pushes `WiringOtherChildScreen`, open Details, Back; then assert Other-child shown; switch to Home, assert Child shown; Back-stack depth: `router back` on each lands on the tab root | Both stacks are intact after a root push and Back, and after switching |
| AC-4 | instrumented | P0 | T1 (same test, assertions: `navs.first().cmd == BACK`, `navs.first().to == WiringChildScreen`, every `screensAfter(mark)` is `WiringChildScreen`, `Telemetry.screen == WiringChildScreen`, no `Nav` after the mark names `WiringHomeScreen`) | `reappearingTop` is the live top, and no later line overwrites it with the tab root |
| AC-4 | unit | P1 | `NavTelemetryTest.unbindWhileOffThenEnabledThenBackNamesTheReappearingTop` (rename of `:232`, body unchanged) and `unbindFreezesTheReappearingTopNotTheLiveTop` unchanged | Semantics of the supplied lambda are still frozen at `unbind`; the name no longer claims "tab root" |
| AC-5 | instrumented | P0 | T1 plus the other five existing `TabHostTelemetryWiringTest` cases, whole class run through `itest TESTS=com.kino.puber.core.ui.navigation.component.TabHostTelemetryWiringTest` | Flipped AC-6 is green and the sibling cases stay green |
| AC-6 | manual (read) | P1 | `grep -n "isEnabled" NavTelemetry.kt`: the only hit is the gate at `:64` outside `unbind` (`:32-55`); `NavTelemetryTest` green | `unbind` has no `Telemetry.isEnabled` gate |
| AC-7 | instrumented | P0 | T4 `replacedHostStartsAtTabRootsWhenShownAgain`: Home pushes Child, a root screen (`WiringAuthScreen`) replaces the host via the root navigator `replaceAll`, then a fresh `WiringTabHostScreen` replaces it; assert `HOME_TAG` and `CHILD_TAG` absent. Also assert `tabAppRouterHolder` reuse does not matter by creating a new one | The holder dies with the host: a fresh MainScreen restores nothing |
| AC-7 | device | P1 | M4 | Same through the real logout and relogin path |
| AC-8 (switch) | instrumented | P0 | T2 (switch step) and the existing `TabFlowLifecycleTest` tab-switch steps (`:117-125`) | Rail switching still keeps each tab's stack |
| AC-8 (Settings) | device | P2 | M5 | Settings tab after a root push: observed and recorded, not required to be fixed |
| AC-9 | instrumented | P1 | T5 `resumeIsDeliveredToTabScreenAfterRootPushAndBack`: add a `LifecycleEventEffect(ON_RESUME)` counter to `WiringHomeScreen` and `WiringChildScreen`; after root push and Back assert the counter of the shown screen increased past its pre-push value | Lifecycle owner swap (K4) keeps `ON_RESUME` for tab screens |
| AC-9 | device | P1 | M3 | Real `LifecycleAction` user (ContentList refresh) |
| AC-10 | instrumented | P0 | T6 `TabFlowLifecycleTest` with `assertTextContains(..., substring = true)` at `:122,131,134,137,175` | The root push lifecycle checks and "one live VM" run and pass |
| AC-11 | unit + static | P0 | `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` (qa-device static gate); doc check: `docs/architecture/navigation.md` Tabs names the holder owner, lifetime and why | Green gate and docs |

Counts: see Return block in the handoff; summary: P0 = 8 rows (AC-1 x2, AC-3, AC-4, AC-5, AC-7, AC-8 switch, AC-10, AC-11 counted as written), distinct new or changed tests T1, T2, T4, T6 are P0.

### Tests, one by one

All instrumented, JUnit 4 + `createComposeRule`, no sleeps (`waitUntil` on tags, as in the class helpers).

- **T1** (changed, existing, P0): D7. Replace the pinning comment at `:148-151`. Steps: `showHost`, `openTab(homeTab, HOME_TAG)`, `click(HOME_LOCAL_TAG, CHILD_TAG)`, `click(CHILD_DETAILS_TAG, DETAILS_TAG)`, keep the PUSH assertions at `:159-160`, `mark()`, `click(BACK_TAG, CHILD_TAG)`. Assert `HOME_TAG` node absent. Navs: first is BACK to `WiringChildScreen`; all `screensAfter` are `WiringChildScreen`; `Telemetry.screen == WiringChildScreen`; none names `WiringHomeScreen`.
- **T2** (new, P0): `rootPushAndBackRestoresBothTabStacks`, AC-3 and AC-8 switch. Described in the traceability row. Also asserts Back (`tabRouter`/in-tab back) from the restored Child reaches that tab's root, so the stack depth is 2 and not a recreated child over nothing.
- **T4** (new, P0): `replacedHostStartsAtTabRootsWhenShownAgain`, AC-7. Needs `WiringAuthScreen` fixture (see below). On the base commit this passes (nothing is restored either way); it is a guard for the new retention, not a failing-first test. It proves the opposite direction of T1: retention must end with the host.
- **T5** (new, P1): ON_RESUME counter. Passes on the base for the Home root (stack lost, Home recreated: counter is a fresh composition), so it is a guard for K4 and not failing-first.
- **T6** (changed, existing, P0): D8. Only the five `substring = true` edits. Run it twice for K7: first with only the test edit on the base commit (record whether it is green; if red, the cause is independent of this fix and goes to the run notes), then after the production change.
- **T8** (conditional, P2): only if D9's ShowAll-local fix is implemented.

### Fixture additions (TabHostTelemetryWiringTest.kt, test code only)

- `WiringOtherScreen` gets a `localTag` (`OTHER_LOCAL_TAG`) that pushes `WiringOtherChildScreen` (tag `OTHER_CHILD_TAG`, its own details button `OTHER_CHILD_DETAILS_TAG`). Distinct screen so T2 can tell the two stacks apart. Existing cases are unaffected (they never click it).
- `WiringAuthScreen`: a non-host root `PuberScreen` with a button (`AUTH_TAG`, `AUTH_RELOGIN_TAG`) that `replaceAll`s the root `Navigator` to a new `WiringTabHostScreen`. A "logout" button (`LOGOUT_TAG`) on `WiringDetailsScreen` or on the child content does `replaceAll(WiringAuthScreen)`. It mimics `AuthVM.kt:134` / `App.kt:101` without the real app.
- `WiringHomeScreen`/`WiringChildScreen`: a resume counter exposed through a test object, for T5.

### Failing-first

`TabHostTelemetryWiringTest.tabLocalPushThenRootPushThenBackNamesTheScreenShownAfterBack`, flipped per D7, written first (before the production change).

Check by reading the old code, base `f14dcc84`: `TabComponent.kt:128-137` stores the subtree with `tabNavigator.saveableState(currentTab.key)`, so Voyager's dispose of the covered `PuberTab` removes the tab-flow stack. `FlowComponent.kt:537-542` then restarts at `TabRootScreen`. After Back the node with `CHILD_TAG` never appears: `waitForTag(CHILD_TAG)` times out (the old test itself waits for `HOME_TAG` instead, which is the documented pin). Also on the base `reappearingTop = { navigator.items.firstOrNull() }` (`FlowComponent.kt:596`) would name `WiringHomeScreen`, so the telemetry assertions fail even if the wait were skipped. After the fix both hold. T2 fails on the base for the same reason (Child and Other-child are lost). T1 is the first one to write and run red on the base before any production edit.

Order for the implementer: (1) flip T1 and write T2, run both red on the base through qa-device (this is also the device reproduction of the bug for the baseline); (2) apply D1/D2/D4/D5; (3) run green; (4) T6 edit and run; (5) T4, T5 guards.

### Manual device checks

Run by qa-device with a lease, `adb -s <serial>`, the worktree's own APK installed through `tools/android-apk-install-preserve`, D-pad keyevents only. Evidence file records each step; no credentials, no raw logcat.

**M1. Show all after a root push (AC-1, AC-2).** Run first on the base build as the baseline, then on the fix.
1. Launch, focus the rail, select Concerts (or Home).
2. D-pad down into content, open a "Show all" grid (DPAD_CENTER on a row header or button).
3. Scroll down the grid at least three rows (DPAD_DOWN x3 or more) and stop on a card, note its title.
4. Press DPAD_CENTER on that card: Details opens.
5. Press BACK once.
6. Expected: the Show all grid is shown (not the tab root), at the same scroll position, the noted card still on screen.
7. Expected: the noted card has the focus ring.
8. Press DPAD_RIGHT then DPAD_LEFT: focus moves between neighbouring cards.
9. Press BACK: the tab root shows; BACK again goes to the rail behaviour as before.
Must not happen: tab root shown after step 5; no focus ring anywhere (D-pad dead); focus on the first card while the grid was scrolled; crash; a Back that needs two presses to leave Details.
Fallback (Q1): focus is inside the grid but on a different card: record exactly which and whether a ShowAll-local fix fits the D9 budget; if it does not, record a follow-up idea.

**M2. Rail open (K13, only if D9 implemented).**
1. Repeat M1 steps 1-4, then in Details press BACK so the grid returns.
2. At once press DPAD_LEFT to the rail, open the rail, and wait 3 seconds.
Expected: focus stays on the rail after the restore effect would have run; DPAD_CENTER on a rail item switches the tab. Must not happen: focus pulled back into the grid while the rail is open.

**M3. Lifecycle (AC-9).**
1. Open a screen using `LifecycleAction(ON_RESUME)` (ContentList with refresh, History or Home) in its tab, open an item to Details, press BACK.
Expected: the screen refreshes or runs its resume work as it did on the base. Must not happen: stale data with no refresh; duplicated refreshes (two per return); crash from a missing `LifecycleOwner`.
Also: open a trailer or player from a tab screen, return, confirm the tab is unchanged.

**M4. Logout (AC-7).**
1. Open a tab-local push (Show all) in a tab, leave it open.
2. Trigger the session end through the app's own logout or a session expiry path that does not need re-pairing. If the only way requires data clear or re-pairing on a build that cannot log in again, stop and ask the owner: do not clear data or uninstall.
3. After the auth screen, log back in and land on Main.
Expected: the tab opens at its root. Must not happen: the previous Show all grid shown. If the worktree build cannot complete login (no credentials, see AGENTS.md), report M4 as not runnable on device and rely on T4.

**M5. Settings tab (AC-8, observe and record).**
1. Open the Settings tab, press DPAD_CENTER on an entry that opens a root screen (or use the remote hotkey), then BACK.
2. Expected: Settings content visible. Record what happens: stuck spinner or not, and whether it differs from the base.
Must not happen: a crash. A spinner is not a failure of this run: record it in `settings-tab-spinner-stuck`.

**M6. Tab switch (AC-8).**
1. Push a local screen in tab X, switch the rail to tab Y, push a local screen, switch back to X, then to Y.
Expected: each tab shows its own pushed screen. Must not happen: a tab root shown, or the wrong tab's screen.

### UNTESTABLE

- AC-1 scroll position and AC-2 exact card focus: not testable at `unit` or in the wiring harness (the grid and its focus are real UI state). Covered by M1 on a device, and by T8 only if D9 is built. Not silently dropped.
- AC-7 through the real logout path: depends on a device that can log in again; if the build cannot (no credentials in a worktree), M4 is a documented gap and T4 stands in.

### Handoff summary

P0: T1, T2, T4, T6 plus the static gate (AC-1, AC-3, AC-4, AC-5, AC-7, AC-8 switch, AC-10, AC-11). P1: T5, M3, M4, AC-4 unit rename, AC-6 read, AC-2 device. P2: T8 (conditional), M5.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Exact card focus or grid-level? | no | answered | Exact card if a small ShowAll-local fix suffices, else defer (recommended) |
| Q2 | Fold the TabFlowLifecycleTest substring fix (tab-flow-lifecycle-itest)? | no | answered | Yes (recommended) |
| Q3 | Close settings-tab-spinner-stuck with this run? | no | answered | No, keep separate (recommended) |
| Q4 | Clear removed tabs' saved state? | no | answered | No (recommended) |
| Q5 | Record a follow-up for other grid screens if the ShowAll focus fix is needed? | no | answered | Yes (recommended) |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-10-17 | Option B: the tab host owns a SaveableStateHolder for tab subtrees | owner (standing instruction: recommended) |
| 2026-10-17 | Q1-Q5 answered with the recommended options | owner (standing instruction: recommended) |
| 2026-10-17 | Gate 1: approved as written (M, tracks none, AC-1..AC-11) | owner (standing instruction: recommended) |
