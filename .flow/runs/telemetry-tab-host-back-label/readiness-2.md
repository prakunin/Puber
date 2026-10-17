# Readiness 2 (delta, Tasks 5-8): telemetry-tab-host-back-label

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
