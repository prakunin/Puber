# Brief: tab-stack-lost-on-root-push

Base `f14dcc84` (master). Paths relative to `app/src/main/java/com/kino/puber/` unless they start with `app/`, `docs/`. Builds on `research.md` (cited as `research.md: <section>`); I re-read the lines I rely on.

## Request

Bug. A tab-local stack (Concerts -> "Show all" grid) is lost after a root push (Details) and Back: the tab returns at its root and focus is lost. The telemetry coupling from run telemetry-tab-host-back-label must flip with the fix.

## Kind / size / tracks

- kind: bug
- size: M (navigation, ViewModel/Koin lifetimes and TV focus are risk areas; about 3 production files, plus tests and docs)
- tracks: none. No screen changes how it looks; focus restoration is behaviour. (No `ux-designer` needed.)
- Device verification: yes (navigation, focus, saved state, lifecycle owners).

## Root cause (plausible, strong: read from Voyager bytecode, not run; research.md: 3)

1. `core/ui/navigation/component/FlowComponent.kt:123-126`: root `Navigator` uses the default `NavigatorDisposeBehavior(disposeNestedNavigators = true)`.
2. The tab list navigator (inside `TabNavigator`, `TabComponent.kt:100-106`) is a child of the root, so Voyager installs `NavigatorDisposableEffect` on it. When Details is pushed, MainScreen leaves composition and that effect disposes the current `PuberTab`: `SaveableStateHolder.removeState` for every key starting with the tab key.
3. `TabComponent.kt:128-137` `PuberCurrentTab` stores the tab subtree with `tabNavigator.saveableState(currentTab.key)`, which registers `"Tab:<screen>"` keys in exactly that navigator's `stateKeys`. The tab-flow `Navigator` stack (`FlowComponent.kt:583-587`, `rememberSaveable`) lives under that key, so it is wiped; on Back it restarts at `TabRootScreen` (`FlowComponent.kt:537-542`).
4. Tab switches keep stacks because nothing is disposed (`disposeSteps = false`).

Status: confirmed by bytecode reading and upstream source; not reproduced on a device. The implementer's first device step should reproduce it (before) as the baseline.

## Chosen approach (option B, research.md: 6)

Tab host owns the saved state: in `TabComponent.kt` remember a `SaveableStateHolder` (`rememberSaveableStateHolder()`), provide it to `PuberCurrentTab`, and use `holder.SaveableStateProvider(currentTab.key) { currentTab.Content() }` instead of `tabNavigator.saveableState`. Voyager's dispose of the covered `PuberTab` then finds no `"Tab:"` keys. The holder dies with MainScreen, so there is no leak after logout/NewRoot, and the root flag stays untouched (rejects option A: leak and stale-revival class, research.md: 6A/7.1; option C: second source of truth).

## Plan

1. `core/ui/navigation/component/TabComponent.kt`: remember the holder in `TabComponent` (the composable around `:100-106`, inside `TabNavigator` content or provided via a CompositionLocal) and change `PuberCurrentTab` (`:128-137`) to `SaveableStateProvider(currentTab.key)` on it. Keep `key(currentTab.key)`. Optional: `removeState` for tabs that leave the tab list.
2. `core/ui/navigation/component/FlowComponent.kt:588-599`: set `reappearingTop = { navigator.lastItemOrNull }` (live top), keep the two-lambda API, keep `unbind` ungated, rewrite the comment (it names this run).
3. `app/src/androidTest/kotlin/com/kino/puber/core/ui/navigation/component/TabHostTelemetryWiringTest.kt:149-173`: flip `tabLocalPushThenRootPushThenBackNamesTheScreenShownAfterBack` back to the original AC-6: wait for `CHILD_TAG` after Back, `WiringChildScreen` expected in `navs.first().to`, `screensAfter` and `Telemetry.screen`, no later `WiringHomeScreen`.
4. `app/src/androidTest/.../TabFlowLifecycleTest.kt:122`: add `substring = true` to `assertTextContains("content=0")` so the root push lifecycle checks (`:150-160`) run; run it green.
5. `app/src/test/.../NavTelemetryTest.kt`: check `unbindFreezesTheReappearingTopNotTheLiveTop` still describes `NavTelemetry.kt:32-55` semantics (it tests unbind with a supplied lambda; adjust only if the name or comment implies "root").
6. Docs: `docs/architecture/navigation.md` "Tabs" (state who owns tab-subtree saved state and why); spec `docs/superpowers/specs/2026-10-17-telemetry-tab-host-back-label.md` amendment note (`:232`) and AC-6 (`:48`) marked as restored.
7. Verify on device through qa-device: root push over a tab stack, Back, logout/relogin, Settings tab after a root push, and the `ON_RESUME` behaviour of `LifecycleAction` users.

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

## Risks

1. Lifecycle owner change: `PuberTab` no longer has its own Voyager `ScreenLifecycleOwner`; tab-flow screens' owner parent becomes MainScreen's (unverified). Guards: AC-9, AC-10.
2. Focus after Back depends on `focusRestorer` / `saveFocusedChild` over a recreated subtree; `rootAnchorCaptureRegistry` covers only `LazyListState` screens, ShowAll uses a grid state (`ShowAllScreenContent.kt:71-72`). May need a ShowAll hook.
3. Telemetry: stack restored but `reappearingTop` not flipped gives a wrong Back label; the flipped itest guards it. Never re-add a `Telemetry.isEnabled` gate in `unbind`.
4. Koin DIScope closes on root push while retained VMs are reused (exists today; harmless for tab-flow screens via `TabFlowSession` routers, risky for nested `FlowComponent` routers such as Settings).
5. Removed tabs keep a small saved state until MainScreen dies (minor; optional `removeState`).
6. `TabFlowLifecycleTest` has been red on master, so an unrelated failure may surface behind the first assertion.
7. Voyager version is a pre-release (2.2.21-1.10.3); behaviour was read from bytecode, so device confirmation is mandatory.

## Open questions

1. Focus precision: is AC-2 "focus on the card that opened Details" required, or is "grid restored at its scroll position with focus in the grid" enough? Recommended: AC-2 as written, with a small ShowAll-local fix allowed; otherwise defer to a follow-up. Blocks: no (AC-2 is soft-marked).
2. Fold the `TabFlowLifecycleTest` `substring = true` fix (run tab-flow-lifecycle-itest) into this run and close that idea as covered? Recommended: yes. Blocks: no (AC-10 assumes yes).
3. Treat `settings-tab-spinner-stuck` as fixed if the device check passes? Recommended: no; keep it a separate idea, record the post-fix observation (AC-8). Blocks: no.

Option choice (A/B/C) is decided here as B on research.md's recommendation; it is not re-asked.
