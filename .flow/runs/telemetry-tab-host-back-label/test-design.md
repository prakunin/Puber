# Test design: telemetry-tab-host-back-label

Base `bdd2c792`. Levels (Puber addendum): `unit` (JUnit 5, no Robolectric), `instrumented` (only `make DEVICE=<serial> itest TESTS=<fqcn>`), `device` (manual on a leased emulator). Existing JVM style reused: `NavTelemetryTest` (`RecordingTelemetryRecorder`, `FakeTelemetryClock`, `bindTop` helper, `MainScreen()` as a real host with identity, `HomeScreen`/`ScreenScreen` as content classes).

## Risk profile

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

## Traceability

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

## Bugs: failing-first test

Defect: Back onto the covered tab host labels `Main`.

- Failing-first: `NavTelemetryTest.disposedBindingKeepsTheTopForTheSameHost` (AC-1). Base `bdd2c792` reading: `TabHostTelemetry.unbind` does `if (binding === handle) binding = null` (`git -C {wt} show bdd2c792:app/src/main/java/com/kino/puber/core/ui/navigation/component/NavTelemetry.kt`), so `topOf(host)` is `null`, `navClassOf` falls to `telemetryClassOf(MainScreen)` and the assertion `HomeScreen` fails with `MainScreen`. It passes after D1.
- Written first, in a commit before the fix. Also written first and red on base: `liveBindReplacesAFrozenTop` is green on base (no freezing), so it is a guard, not a failing test; `tabSwitchUnbindOldThenBindNewShowsNewTop` is green on base too.
- Round 2 (AC-6 rewritten): `unbindFreezesTheReappearingTopNotTheLiveTop` does not compile on base (no `reappearingTop` parameter), so it is red by construction there and green after D11; write it with the signature change. The renamed instrumented test is red on `8b84a6cf` at `navs.first().to`; record that run in evidence.
- Review round 1 (triage-1 #1): failing-first for the gate removal is `unbindWhileOffThenEnabledThenBackNamesTheTabRoot`; red on `f5bfec56`, read from `NavTelemetry.kt` `unbind` (the `Telemetry.isEnabled` branch). It was written with the fix.
- Test helpers after round 1: the 2-arg `bind` overload is removed (triage-1 #4); tests use `bindSame` (same lambda for `top` and `reappearingTop`) or pass `reappearingTop` explicitly.
- The old `clearedBindingGivesTheFallback` passes on base and is deleted deliberately: it asserted the defect.
- The instrumented `backFromRootPushedScreenNeverRecordsMain` (AC-7) and `backOntoTheHostRecordsNoTabReannounce` (AC-4) are red on base as well (Main label and `nav tab Home->Home`), so run them on base once on the emulator to confirm the fixture detects the bug (record both results in evidence).

## Instrumented fixture notes

- Own class `TabHostTelemetryWiringTest` in `app/src/androidTest/kotlin/com/kino/puber/core/ui/navigation/component/`; fixture copied from `TabFlowLifecycleTest` (red on master), no shared helpers, `junit4.v2.createComposeRule`.
- Inline recorder (the JVM `RecordingTelemetryRecorder` is not visible to androidTest) with `isEnabled = true`; install via `Telemetry.install`, `Telemetry.resetForTests()` in `@After`. Capture `Telemetry.screen` inside each `record` call to prove no transient `Main`.
- Probe host must be a class, not an object, so host identity is exercised like `MainScreen`.
- Assert on the public surface (recorded events, `Telemetry.screen`), not on `TabHostTelemetry` internals. No mock of `FlowComponent`, `TabComponent` or `TabHostTelemetry`.
- Assertions on "after Back" use `waitForIdle`, then check the stream order; no sleeps.

## Manual checks

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

## Perf

Track none; no composable signature or state-read change.

## UNTESTABLE

none. Notes: AC-5 has no JVM seam (guard is inline in a Composable), so it is instrumented only; the process-death case (different `PuberTab` instance, one extra `nav tab X->X` line) is accepted per design and not tested.

RISKS: 13, highest 6
P0: 13  P1: 4  P2: 0  MANUAL: 4  (review round 1: +1 P0 `unbindWhileOffThenEnabledThenBackNamesTheTabRoot`, AC-9 test replaced by `unbindWithTelemetryOffRecordsNothing`, still P1; round 2: +1 P0 `unbindFreezesTheReappearingTopNotTheLiveTop`; the renamed instrumented test keeps its P0)
