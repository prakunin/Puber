# Research: telemetry-tab-host-back-label

## 1. Request

> Back from a root-pushed screen (e.g. Details) onto the tab host (MainScreen) records nav `to=Main` and the screen label `scr=Main` for 130-380 ms until the tab re-announces itself (`nav tab Home->Home`); one 2-frame window and one `act Resumed` line carry Main, then it settles on Home. Cause per prior review: the tab binding (DisposableEffect in FlowComponent.kt ~588-591, NavTelemetry.kt ~33-46) is disposed while the host is covered, and the root runner records before the tab re-binds. Also wanted: a Compose-level (or Robolectric/unit) test of the tab-host binding composition wiring and the runner's navChanged choice (currently tests drive bind/recordNavCommand by hand).

## 2. Understanding

When the user opens a title from a tab (Home), Details is pushed on the root navigator and the whole tab host (MainScreen, with its tab navigator) leaves composition. Its telemetry binding, which tells the logger which tab screen the host is showing, is cleared on dispose. On Back the root runner records the nav event straight after the pop, before MainScreen composes again, so the logger falls back to the container name `Main`. The label is only corrected by accident, when the tab rail's `TabRouter` replays its last `Open` command to the re-created `TabComponent` and writes a spurious `nav tab Home->Home` line. The fix must make the Back record the tab's real top screen (Home, or a screen pushed inside the tab) and stop the replay from writing a misleading line. A test must also cover the composition wiring, which no test covers now.

## 3. Current state in code

- `app/src/main/java/com/kino/puber/core/ui/navigation/component/NavTelemetry.kt:26-39`: `TabHostTelemetry` holds one `@Volatile` `Binding(host, top: () -> Any?)`. `bind` overwrites it, `unbind(handle)` clears it only if it is still that handle, and `topOf(host)` returns `top()` only for the same host instance.
- `NavTelemetry.kt:41-44`: `navClassOf` resolves a `TabHostScreen` through `topOf`, otherwise `telemetryClassOf(screen)`. With no binding this is `MainScreen.class`, which the writer shows as `Main`.
- `NavTelemetry.kt:47-52`: `recordNav` sets `Telemetry.screen = target` (all commands except ACTIVITY) and records `TelemetryEvent.Nav`.
- `app/src/main/java/com/kino/puber/core/ui/navigation/component/FlowComponent.kt:588-591`: in `TabFlowNavigator`, `DisposableEffect(tabHost, navigator)` binds `{ navigator.lastItemOrNull }` and unbinds in `onDispose`. `tabHost` comes from `LocalTabHostScreen` (`FlowComponent.kt:582`).
- `FlowComponent.kt:225-273`, the root `FlowCommandRunner`: `navChanged = navigatorChangedBy(event, navigator)` (`:228`) and `navFrom` (`:229`) are taken before the command. `recordNavCommand(event, navFrom, navChanged.lastItemOrNull)` (`:272`) runs synchronously after the pop, inside the `router.events()` collector, before any recomposition.
- `FlowComponent.kt:314-319`: `navigatorChangedBy` picks the navigator that Back or FinishFlow actually shrinks. It is unit-tested in `NavigatorChangedByTest`, but the call at `:228`/`:272` is not.
- `FlowComponent.kt:491-505`: `CurrentScreen` composes only `navigator.lastItem`. So once a `RootPuberScreen` (Details) is on top, `MainScreen.Content` is disposed, and with it `TabComponent` and every `TabFlowNavigator`.
- `app/src/main/java/com/kino/puber/ui/feature/main/component/MainScreen.kt:24,39`: `MainScreen : TabHostScreen` provides `LocalTabHostScreen provides this`. The host instance is the one held in the root stack, so it survives the cover (same object on return).
- **Where the re-announce comes from** (not stated in the prior reviews):
  - `app/src/main/java/com/kino/puber/core/ui/navigation/TabRouter.kt`: `MutableSharedFlow<TabCommand>(replay = 1)`.
  - `app/src/main/java/com/kino/puber/core/ui/navigation/component/TabComponent.kt:108-118`: `LaunchedEffect(tabRouter)` collects `tabRouter.events()`. On every `Open` it sets `navigator.current` and calls `recordNav(NavCmd.TAB, from, event.tab)`. When MainScreen recomposes after Back, this effect restarts, receives the replayed last `Open`, and records `nav tab Home->Home`. This is what sets `Telemetry.screen` back to Home, 130-380 ms later. The delay is MainScreen's re-composition plus the effect launch.
  - Consequence (inferred from code, `unverified` on device): the replayed `Open` names the tab root's content (`PuberTab.contentScreenClass`, `TelemetryNames.kt:44-49`), not the tab navigator's top. After a tab-local push (Show all), then a root push, then Back, the replay would overwrite even a correct label with `Home`. Every return to the host also adds one redundant `nav tab X->X` line.
- **`act Resumed` is not part of the defect.** `MainScreenComponent.kt:74-78` dispatches `MainAction.Resumed` on ON_RESUME. `TelemetryWriter.kt:110-115` labels `Act` events by their VM (`names.owner(event.vm)`), so that line is `Main` by design (MainVM belongs to MainScreen) and stays `Main` after any fix. Only `nav to=`, `Telemetry.screen`, and the frames/key/mem lines that read it (`FrameProbe.kt:27`, `KeyProbe.kt:127`, `MemoryProbe.kt:99`) are wrong.
- Existing tests:
  - `app/src/test/kotlin/com/kino/puber/core/ui/navigation/NavTelemetryTest.kt:100-155` drives `bind`/`recordNavCommand` by hand. `clearedBindingGivesTheFallback` (`:140-146`) **pins the current behaviour** (unbind, then Back to the same host, gives `MainScreen`), so a fix will deliberately change that test.
  - `app/src/test/kotlin/com/kino/puber/core/ui/navigation/component/NavigatorChangedByTest.kt` covers `navigatorChangedBy` and the real pop routine.
- **Test infrastructure:**
  - Unit tests are JUnit 5 Jupiter only: `junit5 = "6.1.3"` in `gradle/libs.versions.toml:20`, `useJUnitPlatform()` in `app/build.gradle.kts:443-446`, and test dependencies at `app/build.gradle.kts:414-418` (junit, coroutines-test, mockk, ktor-mock).
  - **No Robolectric, no vintage engine, no Compose ui-test in `testImplementation`.** The frames-keys-memory spec D6 states the same: "none is configured".
  - Compose UI tests exist only as instrumented tests. `androidTestImplementation` has ui-test-junit4 and androidx test (`app/build.gradle.kts:422-426`).
- **Reusable harness:** `app/src/androidTest/kotlin/com/kino/puber/core/ui/navigation/component/TabFlowLifecycleTest.kt` already composes the real `FlowComponent` → probe tab host (`TabComponent` + `PuberCurrentTab`) → `TabFlowComponent`. It pushes a tab-local child, a `FullscreenPuberScreen`, a `RootPuberScreen` "Details" through the real routers, and presses Back. It uses `createComposeRule()` (the `junit4.v2` package) and accesses `internal` main types, so androidTest sees `internal`. Its `ProbeMainHostScreen` is not a `TabHostScreen` and provides no `LocalTabHostScreen`. A telemetry variant needs both, plus a recording `TelemetryRecorder`. The existing `RecordingTelemetryRecorder`/`FakeTelemetryClock` live in `app/src/test/.../util/` and are not visible to androidTest.
- Instrumented tests run only through `make DEVICE=<serial> itest [TESTS=...]` (`Makefile:126-137`, AGENTS.md). They are not part of `check.full`.

## 4. Related work

- **telemetry-frames-keys-memory** (origin): `triage-3.md` rows #1 and #2 deferred into this run. Also `review-edge-3.md` F1 (cause, two fix options), `review-codex-3.md` (same cause), `review-tests-3.md` F1 (no wiring test; suggests a Robolectric/Compose test of MainScreen → TabFlowNavigator), and `qa-report.md` Round 3 AC-12 FAIL (transient, 4 repros) / Defect 1.
- Commit `602928b0` (2026-10-16) "Attribute navigation telemetry to screens through flow and tab components" introduced `TabHostScreen`, `TabHostTelemetry`, the DisposableEffect, the `navChanged` choice and the tests named above. `97a0fca2` added the first nav probes.
- **tab-flow-lifecycle-itest** (stage idea): `TabFlowLifecycleTest.kt:122` fails on master (`assertTextContains("content=0")` vs `content=0 vm=1`). A new test that copies this harness should live in its own class and not depend on that fixture until that run lands.
- **rail-content-mismatch-rapid-back** (idea, triage-3 #3): same area (tab host, rapid Back), different symptom (UI state, not telemetry). Not a duplicate.
- Specs: `docs/superpowers/specs/2026-10-15-telemetry-frames-keys-memory.md` (D4 writer `scr` rules, D6 "no Robolectric", D10 frame windows close on screen change) and `2026-10-15-telemetry-core.md`.
- Possible duplicate: none.

## 5. External findings

- **JUnit 6 Vintage engine** (needed to run Robolectric's JUnit 4 runner on the JUnit Platform). Deprecated since 6.0. It reports an INFO discovery issue whenever it finds JUnit 4 classes, and is meant "only temporarily while migrating". Removal is not announced; 6.0.1 added `junit.vintage.discovery.issue.reporting.enabled=false`. The project is on 6.1.3, whose deprecated list includes `VintageTestEngine`. Sources: https://docs.junit.org/6.0.0/release-notes/, https://docs.junit.org/6.1.3/api/deprecated-list.html, https://github.com/junit-team/junit-framework/issues/5030
- **Robolectric + Compose**: the Compose ui-test library runs the same `createAndroidComposeRule`/`runComposeUiTest` code on device and on the JVM under Robolectric, and the documented examples are JUnit 4 (`@RunWith`). The latest version listed on robolectric.org is 4.17. Whether Robolectric supports AGP 9.3 / the project's compileSdk is `unverified`. Source: https://robolectric.org/blog/2026/07/03/designing-local-testing-libraries/ (2026-07-03)
- Robolectric does not run under Jupiter; JUnit 5 projects need the vintage engine (community posts only, no primary source found). Sources: https://blog.kinto-technologies.com/posts/2024-12-13-Introducing-Screenshot-Testing-in-UnlimitedApp/, https://habr.com/p/773080
- Android Compose testing guide: `ui-test-junit4` as `androidTestImplementation`, `ui-test-manifest` for `createComposeRule()`. The page links a "v2" test configuration guide (the project's androidTest already uses `junit4.v2.createComposeRule`). Source: https://developer.android.com/develop/ui/compose/testing (updated 2026-10-16)

## 6. Options

Fix and test can be combined independently; the options below pair them.

**A. Freeze the tab top on dispose, drop the replay line, and add a JVM test plus one instrumented Compose test. Size S-M. (recommended)**
- Fix: in `TabHostTelemetry.unbind` (or a new `release(handle)` used by `onDispose`), when the handle is still current, replace it with a frozen binding for the same host. It keeps the top it had at dispose: the screen object or its telemetry class, read once from `navigator.lastItemOrNull`, and no reference to the disposed `Navigator`. `topOf(host)` keeps the host-identity check, so a fresh host after NewRoot still falls back. The live binding replaces the frozen one on re-composition. A tab switch disposes the old tab and binds the new one in the same apply, so the new one wins either way.
- In `TabComponent.kt:112-116`, do not `recordNav` when the `Open` is the replay of the tab already current (`from === event.tab`; identity on return is `unverified`, and the test must pin it). This removes the spurious `nav tab X->X` line and the overwrite of a tab-local top.
- Tests: change `clearedBindingGivesTheFallback` into "dispose, then Back to the same host names the frozen top", and add a test that a fresh host still falls back. Add a new androidTest class modelled on `TabFlowLifecycleTest`: the probe host is a `TabHostScreen` and provides `LocalTabHostScreen`, and an inline recording `TelemetryRecorder` is installed. It asserts that the Back from a root-pushed Details records `to` equal to the tab content and `Telemetry.screen` equal to it, that no `nav tab` is recorded on return, and the tab-local push case. That covers the DisposableEffect, the `CompositionLocalProvider` and the `:272` `navChanged` choice through the real runner.
- Trade-offs: about 3 production files (NavTelemetry, FlowComponent, TabComponent) plus 2 test files. No build changes. The Compose test runs only under `make itest` on a leased emulator, not in `check.full`. It keeps one screen/class reference per covered host, which is negligible.

**B. Re-announce on bind (edge F1 option 2). Size S.**
- Keep the unbind. In the bind effect, if the log is on and `Telemetry.screen == host.javaClass`, set `Telemetry.screen` to the top's class.
- Trade-offs: smallest diff. The `nav back to=Main` line stays wrong, so the nav graph in the session log still shows `Details -> Main`. It relies on a side write to `Telemetry.screen` outside `recordNav`, against the "written only by recordNav" contract in `Telemetry.kt:33`. Frames between the pop and the first composition may still say Main. It does not fix the replay overwrite.

**C. Option A's fix, with Robolectric local Compose tests instead of an instrumented test. Size M.**
- Add Robolectric, `ui-test-junit4`, `junit:junit` and `junit-vintage-engine` to `testImplementation`, and write the wiring test as a JVM test that runs in `check.full`.
- Trade-offs: it gates every build, with no emulator lease. But it changes the build (risk area): JUnit 4 tests inside a Jupiter-only suite, a deprecated engine on JUnit 6.1.3, a new heavy dependency, and AGP 9.3 compatibility `unverified`. No precedent in the repo, and the testing doc would need a new convention. It is a project-wide infrastructure decision bundled into a minor telemetry bug.

Recommended: **A**.

## 7. Risks

- Order of forget and remember in one Compose apply (tab switch) decides which binding wins. Compose dispatches forgets before remembers (`unverified` for this Compose BOM), and the handle-identity guard in `unbind` makes either order safe. The Compose test should include a tab switch.
- The replay guard depends on `navigator.current` being the same `PuberTab` instance after the host re-enters composition: Voyager's in-memory saveable restore, `voyager 2.2.21-1.10.3`, `unverified`. After process death the instance differs, and the line would be recorded once, which is harmless. Rail re-selection of the current tab produces a new `PuberTab`, so it is still recorded.
- Changing `clearedBindingGivesTheFallback` is intentional. The reviewer must not read it as a weakened test.
- A frozen binding is left after logout or NewRoot until the next bind. The host check makes it inert, and it pins one small screen object.
- Instrumented test: it needs a device lease and `make itest`. `TabFlowLifecycleTest` is red on master (tab-flow-lifecycle-itest), so run the new class with `TESTS=`.
- On-device verification is required: the change touches screen wiring and navigation in `FlowComponent.kt` and `TabComponent.kt`. qa repeats the AC-12 Back repro and checks that `act Resumed` stays `Main` (expected).

## 8. Open questions

1. Should the Back onto the host record the tab's top at the moment it was covered (frozen) or wait for re-composition? **Recommended: frozen (option A).** It keeps the nav line and `scr` correct at the Back instant.
2. Should the replayed `Open` on return stop writing a `nav tab X->X` line? **Recommended: yes, skip it when the tab is unchanged.** It is noise, and it would mislabel a tab-local top.
3. Which layer should the wiring test use: an instrumented Compose test (`make itest`) or adding Robolectric to unit tests? **Recommended: instrumented**, reusing the `TabFlowLifecycleTest` harness, plus JVM tests for the binding semantics. Robolectric is a separate infrastructure run if the owner wants it.
4. Should `act Resumed` labelled `Main` count as part of the bug? **Recommended: no.** Act lines are labelled by their VM by design (`TelemetryWriter.kt:113`), and MainVM's action is correctly `Main`.

## 9. Sources

- Files:
  - `app/src/main/java/com/kino/puber/core/ui/navigation/component/{NavTelemetry.kt, FlowComponent.kt, TabComponent.kt}`
  - `app/src/main/java/com/kino/puber/core/ui/navigation/{TabRouter.kt, TabHostScreen.kt, PuberTab.kt}`
  - `app/src/main/java/com/kino/puber/ui/feature/main/component/{MainScreen.kt, MainScreenComponent.kt}`
  - `app/src/main/java/com/kino/puber/ui/feature/main/vm/MainVM.kt`
  - `app/src/main/java/com/kino/puber/core/telemetry/{Telemetry.kt, TelemetryNames.kt, TelemetryWriter.kt, FrameProbe.kt, KeyProbe.kt, MemoryProbe.kt, TelemetryEvent.kt}`
  - `app/src/test/kotlin/com/kino/puber/core/ui/navigation/NavTelemetryTest.kt`
  - `app/src/test/kotlin/com/kino/puber/core/ui/navigation/component/NavigatorChangedByTest.kt`
  - `app/src/androidTest/kotlin/com/kino/puber/core/ui/navigation/component/TabFlowLifecycleTest.kt`
  - `app/build.gradle.kts`, `gradle/libs.versions.toml`, `Makefile`
  - `docs/architecture/testing.md`, `docs/superpowers/specs/2026-10-15-telemetry-frames-keys-memory.md`
- Commits: `602928b0`, `97a0fca2`, base `bdd2c792`.
- Runs:
  - telemetry-frames-keys-memory: triage-3, review-edge-3, review-codex-3, review-tests-3, qa-report Round 3
  - tab-flow-lifecycle-itest
  - rail-content-mismatch-rapid-back
- URLs:
  - https://docs.junit.org/6.0.0/release-notes/
  - https://docs.junit.org/6.1.3/api/deprecated-list.html
  - https://github.com/junit-team/junit-framework/issues/5030
  - https://robolectric.org/blog/2026/07/03/designing-local-testing-libraries/
  - https://developer.android.com/develop/ui/compose/testing
  - https://blog.kinto-technologies.com/posts/2024-12-13-Introducing-Screenshot-Testing-in-UnlimitedApp/
  - https://habr.com/p/773080
