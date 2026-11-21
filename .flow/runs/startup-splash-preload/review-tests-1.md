# Review tests round 1 (verbatim)

LENS: tests  ROUND: 1  BASE: a22c03bc

Suite: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug exited 0 at 4d159b7f (UP-TO-DATE from implementer run). 2182 tests, 0 skipped, 0 failed. New classes ran: RevealGateTest 12, AppStartupTest 7, StartupKeyFilterTest 4, StartupImageActivityTest 4, PuberVMRevealTest 4, LaunchFocusSettledRuleTest 5, LauncherVMTest 7. Every P0 red row of test-design.md and of the delta has a test under that name with a red run recorded; base-green P0 tests are the plan's guards. Only changed assertions in existing tests are the onTimeout() calls (AC-9), not weakening.

### F1 [minor] Nothing tests that opening the update prompt reveals the splash at once (AC-12 amended)
- Where: App.kt:180-188; androidTest UpdatePromptOverlayFocusTest.kt:111-141
- Problem: RevealGateTest.revealNowIgnoresContentAndImages covers the gate alone; the instrumented test sets covered = false by hand, never via App's onOpenChange. Only other check is device row M8 (P2, needs a newer release).
- Failure scenario: if (open) StartupReveal.revealNow() reverted to markReady() or deleted -> all tests green, yet a prompt opening under the splash waits for images or the 2.5 s cap with D-pad dropped.
- Fix: extract the onOpenChange lambda and unit-test it (after begin() with images busy, onOpenChange(true) leaves isCovered == false), or have the instrumented test read StartupReveal.covered.

### F2 [minor] The probe-sharing test relies on a 200 ms real-time delay to catch a missing Mutex
- Where: ApiDomainInteractorTest.kt:140-166 (autoResolve_concurrentCallersShareOneProbe)
- Problem: delay(200) on Dispatchers.Default assumes both callers reached the probe; reviewer.md forbids sleeps; without the Mutex it fails only if the second caller reached isEndpointReachable within 200 ms.
- Failure scenario: loaded host, Mutex removed: second async starts after release.countDown(), answers from isKnownReachable, test passes; AC-3 regression unseen.
- Fix: have the second caller announce entering autoResolveWorkingDomain (record isKnownReachable calls in the fake, or a per-caller CompletableDeferred) and wait for that instead of 200 ms.

### F3 [minor] The new image-wait behaviour has no device evidence at head, and its wiring has no test
- Where: PuberApp.kt:126 (.eventListener(StartupReveal.imageActivity)); evidence 2026-11-21-startup-splash-preload-device.md
- Problem: evidence commits precede delta 1; amended AC-4/6/12, new AC-15 and AC-13 re-run have no device run. Listener hookup checked only by an rg Done-when line.
- Failure scenario: .eventListener line dropped -> unit tests green, splash lifts before decode, post-reveal decode burst returns unseen.
- Fix: device verify of the delta before gate 2 (M1 amended, M3, AC-13, AC-15); optionally a unit check that newImageLoader's eventListenerFactory returns StartupReveal.imageActivity.

### Unproven
- AppStartup.kt:62: no test for a warm-up that stops when autoResolveWorkingDomain() is not Success.
- HomeScreen.kt:35: no test for HomeVM's production revealSignal wiring (tests inject {}).
