LENS: tests  ROUND: 3  BASE: cd16eb47

I ran the full check (`AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`) on HEAD 4ccfb96c and it is green. The worktree is clean and the results were up to date. Unit tests: 1988 passed, 0 failed, 0 errors, 0 skipped. That is 7 more than round 2, and they match the new tests. Detekt and lint are clean. I worked through the mutations below by reasoning and did not run them.

What I checked, and each test fails if its behaviour breaks:
- **#1 `toggleOffBeforeTheQueuedAttachRunsLeavesNoProbeAttached`:** with the `sink.isEnabled` check removed, the queued attach runs after the toggle-off, so the test goes red.
- **CountingProbe:** `attached` is now the result of the last call. The real probes' attach and detach do nothing when repeated (`MemoryProbe` guards on `attached`), so this is the right model.
  - The on, off, on test lost its count asserts. Under the new code the queue runs attach, detach, attach, and that is still checked through `attached` at the end. Nothing is lost: dropping the trailing posted detach only causes a repeated attach, which does nothing.
- **Storage-failure test (0 attaches, 1 detach):** it still guards the posted detach on the failure path; if `detachProbes` stops posting, `detaches` becomes 0. It also guards the new check that skips a queued attach once the sink is off.
- **#2 binding tests:**
  - Live top: goes red if the binding captures the value when it is created instead of reading it each time.
  - Stale host: goes red if the `host ===` check is dropped.
  - Cleared binding: goes red if `unbind` does nothing.
  - TabRootScreen: goes red if `telemetryClassOf` stops unwrapping it.
- **#4 `finishFlowChangesTheParentEvenWhenTheChildCouldPop`:** goes red under `navigatorToPop(navigator) ?: navigator`.
- **#5 `assertShrinks`:** runs the real `onBackWithNavigator` and checks that only the reported navigator's stack shrank, for Back and FinishFlow, with a one-screen and a two-screen child.
- **#6 `pushFromTheHostIsFromTheTabContent`:** goes red if `navClassOf(from)` goes back to `telemetryClassOf`.

### F1 [minor] The binding and the runner wiring are not covered by any test
- Where:
  - app/src/main/java/com/kino/puber/core/ui/navigation/component/FlowComponent.kt:588-591 (the `DisposableEffect` bind/unbind)
  - FlowComponent.kt:272 (`navChanged.lastItemOrNull`)
  - app/src/main/java/com/kino/puber/ui/feature/main/component/MainScreen.kt:39 (`LocalTabHostScreen provides this`)
- Problem: the unit tests drive `TabHostTelemetry.bind` and `recordNavCommand` by hand. No test fails if one of these lines is deleted or changed: the `DisposableEffect` in `TabFlowNavigator`; the `CompositionLocalProvider` in `MainScreen`; the switch at line 272 from `navChanged.lastItemOrNull` back to `navigator.lastItemOrNull`. The implementation record defers only "AC-12 and Back from a tab-local push" to qa. It does not mention the stale host after NewRoot or the Back that pops the parent. qa round 2 already said it could not drive the Back that pops the parent on this build.
- Failure scenario: the `CompositionLocalProvider` wrapper is dropped in a refactor, so `tabHost` is null and nothing is bound. Every Back to the host then logs `to=MainScreen` and `Telemetry.screen = MainScreen`, which is the original #2 bug, and all 1988 tests stay green.
- Fix: in the qa-device round-3 check for AC-12, list each case with the expected nav `to`/`from` and `scr`: a tab switch, then a push from the host, then Back (nav `to` and `scr` are the tab's content, `from` on the push is the tab's content); a tab-local push, then a root-level Back to the host (nav `to` is the pushed screen); NewRoot to a fresh MainScreen (nav `to` is not the old tab). If a device cannot drive these, use a Robolectric/Compose test that composes `MainScreen` -> `TabFlowNavigator` and asserts that `TabHostTelemetry.topOf(host)` follows the tab navigator's top.
