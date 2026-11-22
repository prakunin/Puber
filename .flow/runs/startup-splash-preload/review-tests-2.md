# Review tests round 2 (verbatim, condensed formatting)

LENS: tests  ROUND: 2  BASE: 8f8793d3 (incremental to e6e4431c; full-diff base 8428c946)

### F1 [blocker] The unit suite now passes or fails depending on test class order: markReady() reaches Looper.getMainLooper() in JVM tests
- Where: StartupReveal.kt:11 and :53-58 (frameHook = ::afterTwoFrames), reached from PuberVM.kt:94
- Problem: since b4448e0d, StartupReveal.markReady() calls afterTwoFrames, which builds Handler(Looper.getMainLooper()). Only resetForTests() swaps in the immediate hook, for the rest of the JVM. revealsOnContentState defaults to true (PuberVM.kt:40) and ten view-state types implement StartupContentState, so any VM test publishing a content state without resetForTests() throws "Method getMainLooper in android.os.Looper not mocked" (no isReturnDefaultValues). check.full is green only because a resetting class runs earlier in the same JVM.
- Failure scenario: ./tools/agentw :app:testDevDebugUnitTest --tests com.kino.puber.core.ui.PuberVMTelemetryTest --tests com.kino.puber.ui.feature.contentlist.vm.SectionVMTest -> 24 tests, 14 failed, BUILD FAILED (stack Looper.getMainLooper <- StartupReveal.afterTwoFrames <- frameHook <- RevealGate.markReady <- PuberVM.updateViewState). Filtered run, other class order, forkEvery or parallel forks turn check.full red. Same for Search, History, Favorites, Collections, Auth, Details, Player, Bookmarks tests.
- Fix: keep Android out of the default path for JVM tests: default the frame hook to immediate and install afterTwoFrames from MainActivity.onCreate or PuberApp (installFrameHook), or catch the missing Looper and run at once. Re-run one non-Home VM test class alone.

### F2 [minor] The retry path in the shared domain walk has no test: an owner that is cancelled or throws while another caller waits
- Where: ApiDomainInteractor.kt:133-146
- Problem: no test makes the owner fail while a second caller waits (finally cancel/clear, isCancelled continue, second caller becoming owner). Dropping the isCancelled check (getCompleted throws CancellationException into HomeVM) or not clearing inFlightWalk (later callers spin on a dead walk) would keep every test green.
- Failure scenario: warm-up owns the walk, saveApiDomain throws (keystore) in switchToFound while Home waits; a refactor breaking this path passes the suite.
- Fix: test where the owner's probe throws or the owner is cancelled after the second caller joined; assert the second caller walks itself and returns, and a third call walks again.

### F3 [minor] Only saveCustomDomain is tested for stopping the walk from overwriting a newer domain
- Where: ApiDomainInteractor.kt:194 (resetToDefault), :229 (applyEndpoint via switchToBuiltInDomain and detect*)
- Failure scenario: delete domainVersion.incrementAndGet() from resetToDefault: suite green; a diagnostics reset during a startup walk is overwritten.
- Fix: parameterize autoResolve_doesNotOverwriteADomainSetWhileItWalks over saveCustomDomain, resetToDefault, switchToBuiltInDomain.

### F4 [minor] The fixes for T4 and T7 have no test that covers the production code they change
- Where: StartupImageLoaderTest.kt:11-18; StartupReveal.kt:53-58; PuberApp.kt:131
- Problem: the mocked-builder test only repeats the extension; removing .reportingToStartupReveal() from newImageLoader keeps tests green; every test replaces the two-frame wait with an immediate hook, so neither the Choreographer path nor its premise (two frames enough for Details to issue image requests) is tested.
- Fix: qa verify on the rebased head must include device evidence for the image-aware reveal (Home cold start and a content-link start into Details, revealed after the first poster decode), or a Robolectric test of newImageLoader().eventListener.

### Unproven
- ApiDomainInteractor.kt:143-145 walk.cancel() before inFlightWalk = null: inline resume on the same thread would loop without end (becomes real with two Main-scoped callers).
- ApiDomainInteractor.kt:167-170 version check and switchToFound not atomic.

Suite state: check.full not run this round. Incremental tests (RevealGateTest, StartupRevealTest, StartupImageLoaderTest, ApiDomainInteractorTest, --rerun) green. PuberVMTelemetryTest and SectionVMTest alone are red (F1). Red runs recorded for T1, T2, T3, T4 (compile-level) check out against the base.
