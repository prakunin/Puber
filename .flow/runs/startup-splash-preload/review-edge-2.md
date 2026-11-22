# Review edge round 2 (verbatim)

LENS: edge  ROUND: 2  BASE: 8f8793d3

### F1 [blocker] A cancelled walk owner can trap a joiner on the main thread in a loop that never ends
- Where: ApiDomainInteractor.kt:135-145
- Problem: in the owner's finally, walk.cancel() runs before inFlightWalk = null is cleared under walkLock. Cancelling a CompletableDeferred completes it on the spot, so the joiner's join() continuation resumes during cancel(). The joiner sees isCancelled and loops; inFlightWalk is still the same cancelled deferred, so it joins again; join() on a finished job returns without suspending, so the loop never yields. With both coroutines on main under viewModelScope (Dispatchers.Main.immediate), the resume runs inline, the owner never gets past walk.cancel() to clear the field, and the main thread spins forever. On another thread the joiner busy-spins until the owner clears the field.
- Failure scenario: Home in Content, user returns after 15 min idle (reachability verdict expired). ON_RESUME -> silentRefresh -> loadHome, job A owns the walk and probes on IO for seconds. A second ON_RESUME (open a card and come back) or a domain save/detect/reset in the dialog (HomeVM.kt:560/582/594 call loadHome()) cancels A and starts B, which joins A's walk. A's probe returns, A resumes on main, finally calls walk.cancel(), B resumes inline and loops on the stale cancelled deferred: main thread hangs, ANR. Warm-up-owned walk does not hang (scope never cancelled), which is why round-1 tests stay green.
- Fix: in finally release the slot before signalling: synchronized(walkLock) { if (inFlightWalk === walk) inFlightWalk = null } then walk.cancel(); optionally the same before walk.complete(result). Test: cancel the owner while a second caller is joined, both on one thread (UnconfinedTestDispatcher or single-thread), assert the joiner runs its own walk and returns.

### F2 [minor] The domain-version check and the write that follows it are not atomic
- Where: ApiDomainInteractor.kt:167-170 (and switchToFound)
- Problem: the walk compares domainVersion on IO and only afterwards calls saveApiDomain and setDomainOverride; a saveCustomDomain/resetToDefault/applyEndpoint can land between check and write.
- Failure scenario: warm-up walk finds api.alador just as the user confirms a custom domain; user's write after the check but before switchToFound -> prefs and override end as api.alador.
- Fix: one critical section for bump-and-write in the four mutators and check-and-write in switchToFound, or compareAndSet(versionAtStart, versionAtStart + 1) before writing.

RevealGate.markReady generation check holds up.

### Unproven
- StartupReveal.kt:56 afterTwoFrames while the activity is stopped during the splash: no frames, mark delayed to the cap.
- StartupReveal.kt:56 Handler(Looper.getMainLooper()) reached by a JVM unit test that publishes a StartupContentState without resetForTests().
