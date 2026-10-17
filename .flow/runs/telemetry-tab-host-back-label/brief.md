# Brief: telemetry-tab-host-back-label

kind: bug | size: M | tracks: none | base: bdd2c792
Research: `research.md` (read first; cited below as research.md: section).

## Request
Back from a root-pushed screen (Details) onto the tab host (MainScreen) records `nav to=Main` and `scr=Main` for 130-380 ms until the tab re-announces itself. Also wanted: a Compose-level test of the tab-host binding wiring and of the root runner's `navChanged` choice.

## Code found
- `core/ui/navigation/component/NavTelemetry.kt:26-39` `TabHostTelemetry`: single `@Volatile` binding; `unbind(handle)` clears it; `topOf(host)` is host-identity checked.
- `NavTelemetry.kt:41-44` `navClassOf`: a `TabHostScreen` resolves via `topOf`, else the container class (`Main`).
- `NavTelemetry.kt:47-52` `recordNav` is the only writer of `Telemetry.screen`.
- `FlowComponent.kt:588-591` `TabFlowNavigator` `DisposableEffect` binds `navigator.lastItemOrNull` and unbinds on dispose.
- `FlowComponent.kt:228,272` root runner: `recordNavCommand(event, navFrom, navChanged.lastItemOrNull)` runs right after the pop, before MainScreen recomposes (`CurrentScreen`, `FlowComponent.kt:491-505`, composes only the top screen, so the host's tab navigators are disposed while Details is on top).
- `TabComponent.kt:108-118` the re-announce: `TabRouter` replays (`replay = 1`) the last `Open`; the restarted `LaunchedEffect` writes `nav tab Home->Home` and resets `Telemetry.screen` (`TabRouter.kt`).
- Tests: `NavTelemetryTest.kt:100-155` (`clearedBindingGivesTheFallback` at `:140-146` pins the old behaviour), `NavigatorChangedByTest.kt`; harness `androidTest/.../TabFlowLifecycleTest.kt` (not a `TabHostScreen`, no `LocalTabHostScreen`).

## Root cause (confirmed by code trace; timing and `from === event.tab` identity unverified on device)
1. The tab binding is cleared on dispose while the host is covered, so the Back record (synchronous in the `router.events()` collector) falls back to `MainScreen.class` -> `Main`.
2. The label is only corrected later by the replayed `Open` in `TabComponent`, which also emits a redundant `nav tab X->X` line and names the tab root, not the tab navigator's top (a tab-local push followed by a root push and Back would be overwritten with `Home`).

`act Resumed` labelled `Main` is correct by design (`MainScreenComponent.kt:74-78`, `TelemetryWriter.kt:110-115`, labelled by VM) and is NOT part of the defect and not an AC.

## Chosen approach (research.md: option A)
Freeze the tab top on dispose (keep host-identity check; no reference to the disposed `Navigator`), skip the `recordNav` in `TabComponent` when the replayed `Open` equals the current tab, and cover the wiring with one new instrumented Compose test plus JVM tests. No Robolectric (build-infra decision, out of scope).

## Acceptance criteria
- AC-1. Given a tab host whose tab binding was disposed with Home on top, When a Back command to the same host is recorded, Then `Nav.to` and `Telemetry.screen` are the Home content class, not `MainScreen`. (JVM test; replaces `clearedBindingGivesTheFallback`.)
- AC-2. Given a frozen binding of host A, When a Back/NewRoot records a different (fresh) host B, Then B falls back to its container class (`Main`) and A's tab is never reported. (JVM test.)
- AC-3. Given a frozen binding, When the host re-composes and binds a live navigator, Then the live top wins; and on a tab switch (old tab disposes, new binds) the new tab's top wins. (JVM test for handle ordering both ways; instrumented test includes a tab switch.)
- AC-4. Given Home is the current tab and the host re-enters composition after Back from Details, When `TabComponent` receives the replayed `Open(Home)`, Then no `nav tab Home->Home` event is recorded and `Telemetry.screen` is unchanged. (Instrumented test.)
- AC-5. Given the user selects a different tab, or re-selects the current tab from the rail (a new `PuberTab` instance), When the `Open` arrives, Then `nav tab` is still recorded and `Telemetry.screen` is set to it. (Instrumented or JVM test.)
- AC-6. Given a tab-local push (e.g. Show all), then a root push (Details), When Back, Then `nav back to=` and `Telemetry.screen` are the tab-local screen, and no later line overwrites it with `Home`. (Instrumented test.)
- AC-7. Given the real `FlowComponent` runner with a `TabHostScreen` host and a recording `TelemetryRecorder`, When Details is pushed from Home and Back is pressed, Then the first nav event after Back has `to` = Home content (not `Main`) and no event or frame window records `Main` as `scr`. This covers the `DisposableEffect`, `LocalTabHostScreen` provider and the `:272` `navChanged` choice. (New androidTest class, own fixture, run via `make DEVICE=<serial> itest TESTS=<class>`.)
- AC-8. Given the change, When `check.full` runs, Then unit tests, detekt and lint pass with the existing `NavigatorChangedByTest` and the other `NavTelemetryTest` cases unchanged.
- AC-9. Given telemetry off, When navigation runs, Then no binding or freeze work records anything (`Telemetry.isEnabled` guard intact).
- AC-10. Given the device repro (open a title from Home, Back), When the session log is read, Then `nav back` shows `to=Home` and `scr=Home` throughout, `act Resumed` still reads `Main`.

## Device verification
Required (yes): navigation and screen wiring in `FlowComponent.kt` and `TabComponent.kt`; the instrumented test needs a leased emulator.

## Risks
- Forget/remember ordering in one Compose apply (tab switch) decides which binding wins; handle-identity guard makes both orders safe, covered by AC-3.
- The replay guard assumes `navigator.current` is the same `PuberTab` instance after re-entry (Voyager saveable restore, unverified); after process death one extra line is harmless; the test must pin it.
- `clearedBindingGivesTheFallback` is changed deliberately; reviewers must not read it as a weakened test.
- A frozen binding lingers after logout/NewRoot until the next bind; inert via the host check, pins one small object.
- `TabFlowLifecycleTest` is red on master (tab-flow-lifecycle-itest); the new test must live in its own class and run with `TESTS=`. The instrumented test is outside `check.full`.
- Test recorder helpers (`RecordingTelemetryRecorder`) are not visible to androidTest; use an inline recorder.

## Open questions
1. Record the tab's top at cover time (frozen) vs wait for recomposition? Recommended: frozen. Blocks: no.
2. Skip the `nav tab X->X` replay line when the tab is unchanged? Recommended: yes (AC-4). Blocks: no.
3. Wiring test layer: instrumented (recommended) vs adding Robolectric. Recommended: instrumented; Robolectric is a separate infra run. Blocks: no (answering Robolectric changes scope).

## Plan
Not provided: size is M (navigation wiring risk area); the planner takes it from here.
