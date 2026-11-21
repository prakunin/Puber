# Review edge round 3 (verbatim, condensed formatting)

LENS: edge  ROUND: 3  BASE: e6e4431c

No findings.

Checked at e5a579a1:
- Owner's finally order in ApiDomainInteractor.autoResolveWorkingDomain (:125-154): owner fails/cancelled -> slot cleared under walkLock then walk cancelled; inline-resumed joiner finds cancelled walk, loops, finds slot empty, becomes owner; owner's finally does nothing to the new walk. Owner succeeds -> slot cleared then completed; joiner takes the result; second clear only empties the slot if it still holds this walk; cancel() on a completed walk does nothing. Joiner cancelled -> join() throws, nothing left in slot. Success cancelled as it resumes (withContext prompt cancellation) -> result dropped, finally cancels, joiners retry (as before). Callers on different threads read the slot under the lock; only window is a caller arriving between clear and complete() (Unproven).
- ImmediateThreadDispatcher (ApiDomainInteractorTest.kt): dispatch needed checked against the executor's own thread; the owner's resume after withContext(IO) is posted, so the joiner resumes inline as with Dispatchers.Main.immediate; idle() lets the joiner reach join() before release; withTimeout stops the old spin; third-call assertion fails if a completed walk were left in the slot; shutdownNow in finally.
- StartupReveal.installFrameWait from PuberApp.onCreate (PuberApp.kt:78, StartupReveal.kt:27-29): set on main before initDi(), so before AppStartup.start(), the sweeper, any Activity and the lazy Coil newImageLoader; non-volatile is fine; instrumented tests and the baseline profile run the real PuberApp; JVM tests keep the immediate hook, resetForTests restores it.

### Unproven
- ApiDomainInteractor.kt:144-145: between clearInFlightWalk and walk.complete, a caller on another thread starts a second walk instead of taking the result; real only if a device trace shows two catalogue probes in one startup; usually stops at isKnownReachable.
