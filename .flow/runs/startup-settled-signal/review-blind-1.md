LENS: blind  ROUND: 1  BASE: 7bcbfd83
### F1 [minor] A relaunch in the same process skips the startup wait while the splash covers Home again
- Where: app/src/main/java/com/kino/puber/core/startup/StartupSettled.kt:35-45, app/src/main/java/com/kino/puber/MainActivity.kt:77-79
- Problem: `StartupSettled` is a singleton latch for the whole process, and it completes only once. `StartupReveal.onActivityCreated(restored = false)` calls `RevealGate.begin()`, and `begin()` covers the app again for every new activity. On the second new activity the latch is already complete: `onActivityCreated(false)` does nothing and `onSplashGone()` is a no-op. So every `awaitSlot` waits only for its fixed offset, counted from when the new `MainVM` or `UpdatePromptVM` starts.
- Failure scenario: The user presses Back on Main, which finishes the activity while the process stays alive, then opens the app from the TV launcher. `savedInstanceState` is null, the splash covers, and Home loads beneath it. Meanwhile: the TV-home refresh fires at 0.5 s; the update check at 1 s; the device-info report at 1.5 s; the watch-state history walk at 2 s; `DetailsPrefetcher` warms right away. All of these compete with Home's own requests for OkHttp's per-host budget. The watch-state sync used to wait a fixed 5 s on every new `MainVM`, so it now starts earlier on these warm relaunches, which is the contention the deleted `StartupSyncDelay` KDoc was there to avoid. The class KDoc says "the first start", so this may be intended, but the change does not say that the old 5 s guard is gone for warm starts.
- Fix: Re-arm the latch for each new `begin()`. One way is to replace the `CompletableDeferred` with a new one in `onActivityCreated(restored = false)` once the previous one has completed, plus a new fallback. The other is to state the warm-start behaviour explicitly and keep a minimum delay before the watch-state slot.

### F2 [minor] A new test relies on a real-time `delay(200)` to order a race
- Where: app/src/test/kotlin/com/kino/puber/ui/feature/home/vm/HomeVMIncrementalPublishTest.kt (`aSectionArrivingAfterAClearPublishesEvenWhenAStalePublishWasSkipped`, `withContext(Dispatchers.Default) { delay(200) }`)
- Problem: The test needs the new catalogue's request to be counted before the stale publish is released, and only a 200 ms wall-clock sleep guarantees that.
- Failure scenario: On a loaded CI host the count can land after `releaseMapping.countDown()`. The test then exercises a different ordering and can pass without covering the race it is named for. The reverse can also happen: a slow pool can make the 2 s `newRowShown` await flaky.
- Fix: Replace the sleep with a signal from the code under test. One option is to wait until `requestedPublishes` rises, through a `@VisibleForTesting` read. Another is to have the new `observeWatchingItems` flow count down a latch when it is collected.

### F3 [nit] One `StartupSettled` test cannot fail
- Where: app/src/test/kotlin/com/kino/puber/core/startup/StartupSettledTest.kt (`settlingTwiceResumesEachWaiterOnce`)
- Problem: A `CompletableDeferred` resumes each waiter exactly once whatever the latch logic does, so this test passes for any implementation built on it. Only the `isSettled` and timing tests check behaviour that the change itself adds.
- Fix: Drop the test, or have it assert something that could regress, for example that `onSplashGone()` before `onActivityCreated(false)` leaves no fallback coroutine running.

### Unproven
- app/src/main/java/com/kino/puber/ui/feature/root/component/StartupSplashHost.kt:36-41 `onGone` depends on `finishedListener` running with exactly `0f`. If the composition is torn down mid-fade, or animations are disabled on the TV, it may not run, and only the 5 s fallback settles the latch. A device trace with the animator scale set to 0, showing that `onSplashGone` is never called, would make this real.
