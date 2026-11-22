# Test design: startup-settled-signal

Base 7bcbfd83. Implements brief.md ACs against design-architect.md. Test paths are under `app/src/test/kotlin/com/kino/puber/` (unit) and `app/src/androidTest/kotlin/com/kino/puber/` (instrumented, run only via `make DEVICE=<serial> itest TESTS=<fqcn>`).

## Fixtures

| Fixture | Rule |
|---|---|
| Latch in VM and prefetcher tests | A real `StartupSettled(scope = backgroundScope)` on the test's `StandardTestDispatcher`; settle it through its public inputs (`onActivityCreated(restored = true)` or `onSplashGone()`), never a mock |
| Existing tests that construct the changed classes (design-architect.md: Changes by layer, fixture list) | Get a latch settled before `testOnStart()` and advance past the relevant slot, so their current assertions keep their meaning |
| `StartupSyncDelay` advances in `MainVMWatchStateSyncTest` | Replaced by settling the latch and advancing `StartupSlot.WatchState` offset + 1 ms |
| Exact-count drains (`HomeVMIncrementalPublishTest.kt:592`, `:694`) | Replaced by `awaitPublishesDrained()` (design-architect.md: Test drain) |
| Parking a publish | Reuse the `CountDownLatch` + `AtomicBoolean` gate on `mapper.mapItemSection` and the real thread pool from `publishSectionsSerializesConcurrentSectionArrivals` |

## Risk profile

| # | Risk (source) | P | I | Score | Covered by |
|---|---|---|---|---|---|
| 1 | Latch complete before `begin()` (brief) | 2 | 3 | 6 | `isNotSettledBeforeAnyActivityIsCreated` |
| 2 | Latch never completes (brief; design 4) | 2 | 3 | 6 | `freshActivitySettlesAtTheFallbackWhenTheSplashNeverReportsGone` |
| 3 | `onGone` not wired in `MainActivity`, so every start settles only at the 5 s fallback | 2 | 2 | 4 | `splashReportsGoneOnceAfterTheFade`; perf metric 5 |
| 4 | Coalescing delays the hero publish and so the reveal (brief) | 2 | 3 | 6 | `theFirstSectionToArrivePublishesContentWithoutWaitingForTheRest`; perf metric 2 |
| 5 | A folded publish request lost, a row unshown (brief; design 2) | 2 | 3 | 6 | `arrivalsQueuedBehindAnInFlightPublishFoldIntoOneFollowUp`, `aCancelledFollowUpLeavesTheNextQueuedRequestToPublish`, `aSectionArrivingAfterAClearPublishesEvenWhenAStalePublishWasSkipped` |
| 6 | Deferred work runs after logout / domain switch (brief; design 3) | 2 | 2 | 4 | `deferredStartupWorkNeverRunsAfterTheViewModelIsCleared` |
| 7 | Post-login watch-state starts 2 s after Main start (brief; design 5) | 2 | 1 | 2 | Accepted (Q2); timing pinned by `onStartAfterStartupSettled_countsSlotsFromMainStart` |
| 8 | Emulator noise hides the gain (brief) | 3 | 1 | 3 | Count-based perf metrics 1 and 3 gate; `key_lat` reported only |
| 9 | Update prompt over a revealed, focused Home does not take focus or Back does not return it (design 1) | 2 | 3 | 6 | `updatePromptShownOverFocusedContentTakesFocusWithinOneSecond`; manual 2 |
| 10 | Counter reads from the test thread race main-dispatcher writes (design 6) | 1 | 2 | 2 | Accepted: counters are `MutableStateFlow` |
| 11 | Baseline instrumentation (`SettledMeasure`) lands in a task commit (design 7) | 2 | 2 | 4 | Manual 4 |
| 12 | `StartupSettled` missing from Koin: MainVM/UpdatePromptVM crash at resolution | 1 | 3 | 3 | `aScreenScopeResolvesTheGlobalPrefetcher`; manual 1 |

## Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 red | `StartupSettledTest.isNotSettledBeforeAnyActivityIsCreated` | Fails if the latch is derived from `covered` or completes at construction |
| AC-1 | unit | P0 red | `StartupSettledTest.splashGoneSettlesTheLatch` | Fails if `onSplashGone()` does not resume a waiter on `await()` |
| AC-1 | unit | P1 | `StartupSettledTest.settlingTwiceResumesEachWaiterOnce` | Two waiters each resume exactly once after `onSplashGone()` twice plus the fallback elapsing; fails if a settle re-arms or resets the latch |
| AC-1 | instrumented | P1 | `ui/feature/root/component/StartupSplashHostTest.splashReportsGoneOnceAfterTheFade` | `onGone` count is 0 while covered, 1 after the fade, still 1 after further frames; fails if not called from the `finishedListener` or called per recomposition |
| AC-2 | unit | P0 red | `StartupSettledTest.restoredActivitySettlesAtOnce` | `await()` returns with no virtual time advanced; fails if a restored activity waits for a splash or the fallback |
| AC-3 | unit | P0 red | `StartupSettledTest.freshActivitySettlesAtTheFallbackWhenTheSplashNeverReportsGone` | Not settled at fallback - 1 ms, settled at fallback; fails if the fallback is not armed or uses another bound |
| AC-3 | unit | P2 | `StartupSettledTest.aSecondFreshActivityDoesNotPostponeTheFallback` | Fails if each `onActivityCreated(false)` restarts the timer |
| AC-4 | unit | P0 red | `MainVMDeviceInfoTest.onStart_doesNotReportDeviceInformationBeforeStartupSettles` | Unsettled latch, 10 s advanced: `setDeviceInformation` not called; fails if the await sits after the call or is missing |
| AC-4 | unit | P0 red | `MainVMRefreshLifecycleTest.onStart_doesNotRequestTvHomeRefreshBeforeStartupSettles` | Unsettled latch, 10 s advanced: `requestRefresh` not called |
| AC-4 | unit | P0 red | `MainVMWatchStateSyncTest.onStart_doesNotSyncWatchStateBeforeStartupSettles` | Unsettled latch, 10 s advanced: `syncIfStale` not called; fails while a fixed 5 s delay remains |
| AC-4 | unit | P0 red | `MainVMStartupSlotsTest.onStart_runsDeferredWorkInSlotOrderAfterStartupSettles` | Settled at t0: TV home at +500 ms, device info at +1500 ms, watch-state at +2000 ms, each absent 1 ms earlier; fails if the order, the gap or the reference point changes |
| AC-4 | unit | P0 guard | `MainVMWatchStateSyncTest.startupAndFirstResume_syncWatchStateOnceBetweenThem` | Fails if `MainAction.Resumed` during the slot wait starts its own run |
| AC-4 | unit | P1 guard | `MainVMWatchStateSyncTest.resumed_syncsWatchStateWithoutWaitingOnceTheStartupBurstHasPassed` | Fails if a later resume pays the slot again (`startupSlotServed` lost) |
| AC-4 | unit | P1 guard | `MainVMDeviceInfoTest.onStart_keepsMainScreenUsableWhenDeviceReportFails` | Fails if a failed report after the slot breaks Main state |
| AC-5 | unit | P0 red | `UpdatePromptVMTest.onStart_doesNotCheckForUpdateBeforeItsStartupSlot` | Unsettled: no `checkForUpdate`; settled + 999 ms: none; fails if the auto check is not gated |
| AC-5 | unit | P0 guard | `UpdatePromptVMTest.manualCheck_runsBeforeStartupSettles` | Unsettled latch: manual check reaches `Available`; fails if the gate is put on the shared check path |
| AC-5 | unit | P1 guard | `UpdatePromptVMTest.onStart_showsAvailable_whenUpdateExists` | With a settled latch and the slot advanced, an update still shows `Available` |
| AC-5 | unit | P1 guard | `UpdatePromptVMTest.onStart_keepsHiddenAndSkipsCheck_whenAutoCheckDisabled` | Fails if the slot path ignores the auto-check preference |
| AC-6 | unit | P0 red | `DetailsPrefetcherTest.noWarmStartsBeforeStartupSettles` | Unsettled gate, dwell elapsed: no details request; fails if `warm` does not call `awaitSettled` first |
| AC-6 | unit | P1 | `DetailsPrefetcherTest.aWarmHeldForStartupProceedsOnceItSettles` | The focused card is warmed after settle; fails if a held warm is dropped |
| AC-6 | unit | P2 | `DetailsPrefetcherTest.aWarmHeldForStartupTakesNoSlot` | With the gate closed, no permit is held; fails if the gate sits after the permit |
| AC-6 | unit | P1 guard | `domain/di/DetailsPrefetcherWiringTest.aScreenScopeResolvesTheGlobalPrefetcher` | Fails if `StartupSettled` is not bound in the global module |
| AC-7 | unit | P0 red | `MainVMStartupSlotsTest.deferredStartupWorkNeverRunsAfterTheViewModelIsCleared` | Cancel the VM scope before settle, then settle and advance 10 s: none of the three calls; fails if any await moves to app scope or a call precedes its await |
| AC-7 | unit | P1 | `UpdatePromptVMTest.onStart_skipsTheAutoCheckWhenClearedBeforeItsSlot` | Same for the update check |
| AC-8 | unit | P0 red | `MainVMStartupSlotsTest.onStartAfterStartupSettled_countsSlotsFromMainStart` | Latch settled, 10 s advanced, then Main starts: TV home at +500 ms and watch-state at +2000 ms from Main start; fails if offsets count from settle time or a 5 s wait remains |
| AC-9 | unit | P0 guard | `HomeVMIncrementalPublishTest.theFirstSectionToArrivePublishesContentWithoutWaitingForTheRest` | Fails if coalescing adds a window before the leading publish |
| AC-9 | unit | P0 red | `HomeVMIncrementalPublishTest.arrivalsQueuedBehindAnInFlightPublishFoldIntoOneFollowUp` | First publish parked, three sections arrive, release, drain: `mapHeroItems` runs twice from the park on and the final `Content` holds all four section types; base maps four times |
| AC-9 | unit | P0 guard | `HomeVMIncrementalPublishTest.publishSectionsSerializesConcurrentSectionArrivals` | Fails if `publishMutex` stops covering snapshot through state update |
| AC-9 | unit | P1 | `HomeVMIncrementalPublishTest.aCancelledFollowUpLeavesTheNextQueuedRequestToPublish` | A queued request under `loadHomeJob` cancelled (via `CommonAction.OnResume`) while a `cardDisplayChanges` request queues behind it: the latter still publishes; fails if `publishedThrough` moves on cancel or the skip compares the live counter instead of `mine` |
| AC-9 | unit | P1 guard | `HomeVMIncrementalPublishTest.heroSettledIsSnapshottedWithTheRestOfThePublishSoAStalePublishCannotPairItWithEmptyHeroItems` | Fails if `upTo` or `heroSettled` is read outside the snapshot |
| AC-10 | unit | P0 guard | `HomeVMIncrementalPublishTest.aPublishParkedAtTheHopCannotResurrectRowsAClearAlreadyDropped` | Fails if the epoch check is dropped or moved before mapping |
| AC-10 | unit | P0 guard | `HomeVMIncrementalPublishTest.aCacheWipeFromAnotherScreenDropsTheRowsFromThePreviousCatalogue` | Fails if a folded publish carries pre-wipe rows |
| AC-10 | unit | P1 | `HomeVMIncrementalPublishTest.aSectionArrivingAfterAClearPublishesEvenWhenAStalePublishWasSkipped` | Fails if the epoch-skip exit sets `publishedThrough` to the current `requestedPublishes` instead of the captured `upTo` |
| AC-11 | device | P0 red | Perf track below | Fails if publishes do not drop or reveal/cap regress |
| AC-12 | instrumented | P0 guard | `ui/feature/update/component/UpdatePromptOverlayFocusTest.updatePromptShownOverFocusedContentTakesFocusWithinOneSecond` | Fails if a prompt opened over focused content leaves focus beneath |
| AC-12 | device | P1 | Manual 2 | Prompt after reveal, focus on Update, Back returns to Home |
| AC-13 | manual | P0 guard | `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` in the worktree | Exit 0 |

## Bugs

N/A: kind improvement.

## Manual checks

Emulator, lease held, worktree devBenchmarkRelease APK installed with `tools/android-apk-install-preserve`.

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| 1 | `am force-stop`, cold start; on reveal press `DPAD_RIGHT` x3, `DPAD_DOWN` x2 within 3 s | Splash fades, Home focus moves with each key | Crash, `NoDefinitionFoundException`, focus loss, Home left on the splash past the 2.5 s cap |
| 2 | Only if a newer release than the installed build exists: cold start, wait | Prompt opens after the reveal (about 1 s), focus on Update; `DPAD_LEFT/RIGHT/UP/DOWN` stay inside; `BACK` closes it and focus is back on Home | Prompt under the splash, focus on Home behind the prompt, Back exiting the app |
| 3 | Restored start: `HOME`, `am kill <installed package>` (background kill, no data clear), relaunch from recents | Content shown with no splash; first `DPAD` keys respond | Splash shown, 5 s stall before Home responds |
| 4 | `git -C <wt> log -p 7bcbfd83..HEAD \| grep -c SettledMeasure`; `git -C <wt> grep -n StartupSyncDelay` | `0`; no match | Instrumentation or the old constant in a task commit |

## Perf track

Device and build: the same emulator and devBenchmarkRelease, 5 cold starts (`am force-stop` then launch) at 7bcbfd83 (baseline, captured by qa-device before implementation with the measure patch) and at the task head with the same patch.

| # | Metric | Measured by | Pass |
|---|---|---|---|
| 1 | Home publishes per start | Count of `SettledMeasure homePublish` lines per start | Median after < median before |
| 2 | `revealed` | Existing startup telemetry mark, ms from process start | Median after <= median before + max(100 ms, baseline max - min) |
| 3 | Starts revealed by the cap | Telemetry cap reason per start | After count <= before count |
| 4 | `key_lat` p95, keys in the first 3 s after `revealed` | Parent run's key-latency method (startup-splash-preload `qa-report.md`) | Reported only (emulator noise, Q3) |
| 5 | Settle reason | A `SettledMeasure settled` line with `splash`/`restored`/`fallback` in the after patch only | 5 of 5 cold starts settle by `splash` |
