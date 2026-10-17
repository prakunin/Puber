LENS: tests  ROUND: 1  BASE: bdd2c792

Suite state: `check.full` (`./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`) passed at HEAD f5bfec56: BUILD SUCCESSFUL, exit 0. That is 1995 unit tests with 0 failures and 0 skipped, and NavTelemetryTest ran 17 of 17. Nearly every task was up to date, so the result is the one cached from the same inputs qa built in round 2. I did not run the instrumented class, because it needs a leased device. qa round 2 reports it 6 of 6 green at e38d19fe, and the new AC-6 test red at 70200dee (expected WiringHomeScreen, got WiringChildScreen).

Coverage:
- Every P0 row in the Test design has a test under its name.
- Removing `clearedBindingGivesTheFallback` is deliberate and correctly replaced by `disposedBindingKeepsTheTopForTheSameHost`, which fails on base because base `unbind` nulls the binding.
- Nothing else was weakened; the existing NavTelemetryTest cases are unchanged.
- AC-10 has the device check M1 in the evidence.

### F1 [minor] Nothing tests the live `top` lambda in the FlowComponent wiring (R12 is claimed covered but is not)
- Where: app/src/main/java/com/kino/puber/core/ui/navigation/component/FlowComponent.kt:593-597; app/src/androidTest/kotlin/com/kino/puber/core/ui/navigation/component/TabHostTelemetryWiringTest.kt (`tabLocalPushThenRootPushThenBackNamesTheScreenShownAfterBack`)
- Problem: The spec maps R12 (score 6: "live Back or push from a tab-local screen records the root") to `unbindFreezesTheReappearingTopNotTheLiveTop` and to the renamed itest. The JVM test only checks `TabHostTelemetry` itself, not what FlowComponent passes in. The itest calls `mark()` after `click(CHILD_DETAILS_TAG, DETAILS_TAG)`, so it never checks the PUSH record where the live `top` matters (`from=` the tab-local screen). Every other itest pushes from the tab root, where `lastItemOrNull` and `items.firstOrNull()` are the same screen.
- Failure scenario: someone changes line 595 to `top = { navigator.items.firstOrNull() }`, or swaps the two lambdas. Then Home, Show all, Details records `nav push from=ContentList` instead of `from=ShowAll`, and the whole JVM suite plus all 6 itests stay green.
- Fix: in that itest, take a mark before `click(CHILD_DETAILS_TAG, DETAILS_TAG)`. Then assert that the PUSH nav recorded after it has `from == WiringChildScreen::class.java` and `to == WiringDetailsScreen::class.java`.

### F2 [nit] JVM tests that call `TabHostTelemetry.bind` directly leave state in the static singleton
- Where: app/src/test/kotlin/com/kino/puber/core/ui/navigation/NavTelemetryTest.kt:42-46, plus the new tests that call `TabHostTelemetry.bind(...)` directly rather than through `bindTop`
- Problem: `tearDown` unbinds only the field `binding`, which only `bindTop` sets. Tests such as `tabLocalTopIsFrozenAtUnbind`, `unbindFreezesTheReappearingTopNotTheLiveTop` and `frozenBindingOfHostADoesNotLeakIntoFreshHostB` leave a frozen `Binding` in the object for the next test and the next class. Today it is harmless because every test uses a fresh `MainScreen()` (R9), but a later test that reuses a shared host would depend on test order.
- Failure scenario: a future test declares the host as a class field, or reuses a host. The previous test's frozen top for that host then shows up as its `Nav.to`, and only in some test orders.
- Fix: add an `internal fun resetForTests()` on `TabHostTelemetry` that sets `binding = null`, and call it in `tearDown`.
