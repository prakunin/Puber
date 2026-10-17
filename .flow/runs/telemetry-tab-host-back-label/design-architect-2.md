# Design delta, round 2: telemetry-tab-host-back-label

Read in worktree `/home/user1/orca/workspaces/Puber/telemetry-tab-host-back-label` at `6f9f162c`. Paths are relative to `app/src/main/java/com/kino/puber/` unless they start with `app/`. The Voyager claims come from disassembling the `voyager-navigator` and `voyager-tab-navigator` 2.2.21-1.10.3 class jars in the Gradle cache with `javap -c`. That is bytecode read, not run, and it matches what the itest and device M2 show.

This delta replaces round-1 design D2 (what gets frozen) and AC-6. D1 and D3-D8 stay as they are.

## Why the tab-local stack is lost (pre-existing, not caused by this run)

1. The root runner's `Navigator(screen, onBackPressed)` at `core/ui/navigation/component/FlowComponent.kt:123-125` uses the default `NavigatorDisposeBehavior()`, so `disposeNestedNavigators = true` (the default constructor sets both flags to true).
2. `TabComponent` calls `TabNavigator(..., disposeNestedNavigators = false, ...)` (`core/ui/navigation/component/TabComponent.kt:100-104`). Inside, Voyager creates its own `Navigator` for the tab list, and its parent is the root navigator. Voyager's `Navigator` composable runs `NavigatorDisposableEffect(navigator)` whenever `navigator.parent?.disposeBehavior?.disposeNestedNavigators != false`, which is true here because the parent is the root navigator.
3. When Details is pushed at the root, `CurrentScreen` (`FlowComponent.kt:491-505`) stops composing MainScreen, so the TabNavigator's navigator leaves composition. Its `NavigatorDisposableEffect` calls `disposeNavigator`, which calls `Navigator.dispose(screen)` for each item, in this case the current `PuberTab`. `dispose` calls `SaveableStateHolder.removeState` for every state key that starts with `screen.key` (`"Tab:..."`).
4. `PuberCurrentTab` keeps the tab's whole subtree under `tabNavigator.saveableState(currentTab.key)` (`TabComponent.kt:128-137`). That subtree includes the `rememberSaveable` of the tab-flow `Navigator` at `FlowComponent.kt:583-587`, which holds `[TabRoot, ShowAll]`. Step 3 removes it.
5. On Back, MainScreen recomposes. The TabNavigator's own navigator is restored, because it lives under MainScreen's root-level saveable state, which is not removed since MainScreen is still in the root stack. The `current` tab is therefore the same `PuberTab` instance, which is why AC-4 passes. But `Navigator(screen = rootScreen, key = tabFlowNavigatorKey(...))` (`FlowComponent.kt:583-587`) finds no saved state and starts again at `rootScreen` (`TabRootScreen`, `FlowComponent.kt:537-542,659`).

The tab-flow navigator's own `disposeNestedNavigators = false` parent is what keeps tab stacks across tab switches. Only covering the host loses them. `TabAppRouterHolder` being recreated (`TabComponent.kt:84-96`) is a second casualty, not the cause: the itest passes its own holder and still loses the stack.

Conclusion: after Back, the covered host shows its tab root, not the tab navigator's last top. Round 1 froze `navigator.lastItemOrNull`, which names a screen that never comes back.

## Decisions

| # | Question | Decision | Why |
|---|----------|----------|-----|
| D9 | Option (a), (b) or (c)? | (a): freeze what will actually reappear. | It is the only option that makes `nav back to=` itself correct, synchronously, with no extra log line. It touches the same two lines of glue as round 1. |
| D10 | What is frozen? | The tab-flow navigator's root, `navigator.items.firstOrNull()`, read once at unbind, gated on `Telemetry.isEnabled` as in D1. The live label stays `navigator.lastItemOrNull`. | Root is what step 5 rebuilds. `telemetryClassOf(TabRootScreen)` resolves to the wrapped content class (`core/telemetry/TelemetryNames.kt:44-49`, `FlowComponent.kt:665`), so the label is `ContentList` / `Home`. While the host is composed, a root push from a tab-local screen must still record `from=ShowAll`, which is why the live and frozen values differ. |
| D11 | API shape | `TabHostTelemetry.Binding` gains a second lambda: `class Binding(val host: Any, val top: () -> Any?, val reappearingTop: () -> Any? = top)`. `bind(host, top, reappearingTop = top)` passes it through. `unbind` freezes `handle.reappearingTop()` instead of `handle.top()`. `topOf` reads `top` and is unchanged. | It is still one field and one writer. The default keeps every existing `bind(host) { ... }` call and the JVM `bindTop` helper compiling and behaving as before (AC-8). The lambda form keeps AC-9: when telemetry is off, it is never invoked. |
| D12 | Caller | `FlowComponent.kt:589` becomes `TabHostTelemetry.bind(it, top = { navigator.lastItemOrNull }, reappearingTop = { navigator.items.firstOrNull() })`. `:590` is unchanged. A KDoc line on `reappearingTop`, or a comment at `:589`, says that covering the host drops the tab's saved stack (Voyager disposes the covered `PuberTab`), so the root is what comes back. It also names the follow-up run. | It keeps the coupling to the current Voyager behaviour visible in the one place that must change when the stack starts to survive. |
| D13 | The replay guard (D5) | Kept. | With the root frozen, the skipped replay no longer hides anything: Back already says `Home`. Pre-fix, the replay's `nav tab Home->Home` only accidentally covered for the wrong label. |
| D14 | Option (c), fixing the restore here? | No. Open it as its own run. | It is a user-visible navigation bug, it predates this run (the itest fails at the same wait on `8b84a6cf`, before the fix), and fixing it changes Voyager dispose behaviour for every nested navigator, or moves tab stacks out of saveable state. That affects ScreenModel, Koin scope and focus-restore lifetimes and needs its own device verification. None of it is telemetry. |

## Changes by layer

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

## Alternatives rejected

- (b) Correct the label on re-bind: `nav back to=ShowAll` stays wrong in the log. The correction would need a new `NavCmd` (a telemetry data-shape change for the summary tool) or a write to `Telemetry.screen` outside `recordNav` (`NavTelemetry.kt:54-59`). It also arrives a frame later, which is the very class of transient label this run removes.
- (c) Fix the restore in this run: out of scope (D14). It belongs to its own run.
- Freeze `rootScreen` captured in `TabFlowNavigator` instead of `navigator.items.firstOrNull()`: same class, but a second source of truth after `replaceTabRootIfChanged` (`FlowComponent.kt:688-699`).
- Drop the freeze and go back to the round-0 fallback for the tab-local case: AC-1, AC-7 and AC-10 would fail.
- Restore the replayed `nav tab X->X` line to "correct" the label: it would bring back AC-4's defect, and the label would still be wrong whenever the stack does survive.

## Risks (highest first)

1. **Coupling to Voyager's dispose behaviour (correctness, rollout).** If the stack-restore run, or a Voyager upgrade, keeps the tab stack, the frozen root would mislabel Back as `Home` while the screen shows `ShowAll`. This lives at `FlowComponent.kt:589`. The renamed itest goes red at its `HOME_TAG` wait in that case, and the D12 comment names the flip.
2. **Configuration change (lifecycle).** `NavigatorDisposableEffect` uses `DisposableEffectIgnoringConfiguration`, so on activity recreation the stack survives while our `DisposableEffect` still freezes the root. No Back is recorded across a recreation, and the new composition re-binds live before any later Back, so nothing reads the frozen root. TV config changes are rare. Accepted.
3. **Tab switch (concurrency).** The old tab freezes its root, and the new tab's bind overwrites it in the same apply in either order (D4). No change from round 1. Covered by the AC-3 JVM and itest cases.
4. **Test coupling: the itest pins a navigation bug.** Reviewers may read it as endorsing the lost stack. The test comment and the spec must say that it pins telemetry against today's behaviour and that the follow-up run owns the flip.

## Proposed AC-6 (needs owner approval: it changes an approved AC)

- AC-6. Given a tab-local push (e.g. Show all), then a root push (Details), When Back, Then `nav back to=` and `Telemetry.screen` name the screen the tab shows after Back. Today the covered host's tab stack is not restored (a pre-existing Voyager dispose of the covered tab, follow-up run `tab-stack-lost-on-root-push`), so that is the tab root (ContentList / Home). No later line in the stream names a different screen. (Instrumented test; JVM test for the frozen value.)

AC-1 to AC-5 and AC-7 to AC-10 are unchanged. In the Out of scope section, add: "Restoring a tab's local stack after a root push and Back (run `tab-stack-lost-on-root-push`)."

## Open questions

1. Approve the rewritten AC-6 above? Recommended: yes. Blocks: yes, because the plan and implementation follow it.
2. Open a new run `tab-stack-lost-on-root-push` (kind bug: Show all, then Details, then Back lands on the tab root and loses focus context; the cause is the Voyager mechanism above) with `/task:new`? Recommended: yes, as a separate run. Its spec must include flipping `reappearingTop` (D12) and restoring the original AC-6 test. Blocks: no.
3. QA Defect 2 (Settings spinner late in a long session) is not attributed to this change and was not reproduced. Recommended: record it on the board as an idea, not in this run. Blocks: no.
