# Review tests round 3 (verbatim, condensed formatting)

LENS: tests  ROUND: 3  BASE: e6e4431c

No findings.

Ran at e5a579a1 (./tools/agentw :app:testDevDebugUnitTest): ApiDomainInteractorTest (21), StartupRevealTest (5), PuberVMTelemetryTest (10), SectionVMTest (14), PuberVMRevealTest (4), HomeVMTest (20): BUILD SUCCESSFUL, none skipped or failed. New test autoResolve_joinerWalksItselfWhenTheOwnerFailsAndAThirdCallWalksAgain passed in 0.165 s.

- Owner's finally (ApiDomainInteractor.kt:139-154): slot emptied before walk.cancel() (order T8 needs); the new test catches a regression (cancel() resumes the joiner inline on the dispatcher thread; old order -> spin -> withTimeout(10 s) fails; red run recorded); third call checks the slot is empty after the joiner's walk (T10); on the success path clearing before complete() is not needed to avoid the hang, so no test depends on it.
- ImmediateThreadDispatcher: behaves like Dispatchers.Main.immediate; idle() parks the joiner in join() before the probe is released; shutdownNow() in finally.
- StartupReveal.installFrameWait: frame hook defaults to immediate, so JVM suites no longer depend on class order (they run green together; recorded red alone before the fix); installFrameWait() in PuberApp.onCreate before initDi(); wiring has no unit test, accepted as A3 (device evidence).

### Unproven
- ApiDomainInteractor.kt:152: the inFlightWalk === walk identity check has no test; matters only if a new caller sets up a walk between the success-path clear and the finally.
