# One startup-settled signal that defers background work and coalesces Home publishes - plan

**Spec:** `docs/superpowers/specs/2026-11-21-startup-settled-signal.md`
**Goal:** Hold the startup background work until a process-wide settled latch completes, then release it in fixed 500 ms slots, and fold Home publish requests queued behind an in-flight publish into one follow-up.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `cd /home/user1/orca/workspaces/Puber/startup-settled-signal && AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin`; full gate: `cd /home/user1/orca/workspaces/Puber/startup-settled-signal && AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`.
- Touched tests: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests "<FQCN>"`. Instrumented tests are compiled here (`./tools/agentw :app:compileDevDebugAndroidTestKotlin`) and run only by qa-device (`make DEVICE=<serial> itest TESTS=<fqcn>`); never touch a device or `./gradlew`.
- One task = green fast gate + green tests of the touched code + one commit, message per `One imperative English sentence, no prefixes; no Co-Authored-By line`, never a `Co-Authored-By` line.
- Stage files by explicit path. The untracked `docs/superpowers/evidence/2026-11-21-startup-settled-signal-measure.patch` and any `SettledMeasure` line belong to qa and never enter a task commit (spec Manual check 4).
- Line numbers are at base 7bcbfd83. Main paths are under `app/src/main/java/com/kino/puber/`, unit tests under `app/src/test/kotlin/com/kino/puber/`, instrumented tests under `app/src/androidTest/kotlin/com/kino/puber/`.
- Test fixtures (spec: Test design, Fixtures): a real `StartupSettled` in every test, never a mock; settle it with `onActivityCreated(restored = true)` or `onSplashGone()`; "unsettled" means constructed and never fed.
- Out of scope (spec): `TvHomeSyncCoordinator` itself, decode/JIT/main-thread I/O, real-TV measurement, the reveal held to the cap (run `startup-reveal-held-to-cap`).
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.

---

## Tasks

### Task 1: Add the startup-settled latch and bind it in Koin

**Covers:** AC-1, AC-2, AC-3 (unit)
**Files:**
- Create: `core/startup/StartupSettled.kt`
- Modify: `domain/di/modules.kt` - add `single { StartupSettled(scope = get<AppCoroutineScope>()) }` next to `single { AppCoroutineScope() }` (`domain/di/modules.kt:80`; `AppCoroutineScope` is a `CoroutineScope`, `core/coroutine/AppCoroutineScope.kt:14-17`)
- Test: `core/startup/StartupSettledTest.kt` (new)

**Consumes:** `AppCoroutineScope` (`core/coroutine/AppCoroutineScope.kt:14`).
**Produces** (package `com.kino.puber.core.startup`):

```kotlin
internal val STARTUP_SLOT_GAP: Duration = 500.milliseconds

/** Q2 order; prefetch has no slot, it uses await(). */
internal enum class StartupSlot {
    TvHome, UpdateCheck, DeviceInfo, WatchState;
    val offset: Duration get() = STARTUP_SLOT_GAP * (ordinal + 1)
}

internal class StartupSettled(
    private val scope: CoroutineScope,
    private val fallback: Duration = 5.seconds,
) {
    val isSettled: Boolean                       // settled.isCompleted
    fun onActivityCreated(restored: Boolean)     // restored: settle(); else arm the fallback once
    fun onSplashGone()                           // settle()
    suspend fun await()                          // CompletableDeferred<Unit>.await()
    suspend fun awaitSlot(slot: StartupSlot)     // await(); delay(slot.offset)
}
```

- [x] Write the failing tests in `StartupSettledTest` (JUnit 5, `runTest`, latch on `backgroundScope`): `isNotSettledBeforeAnyActivityIsCreated`, `splashGoneSettlesTheLatch`, `settlingTwiceResumesEachWaiterOnce`, `restoredActivitySettlesAtOnce`, `freshActivitySettlesAtTheFallbackWhenTheSplashNeverReportsGone` (not settled after `advanceTimeBy(fallback - 1 ms)` + `runCurrent()`, settled after 1 ms more), `aSecondFreshActivityDoesNotPostponeTheFallback` (second `onActivityCreated(false)` at +3 s, still settled at +5 s).
- [x] Implement: one private `CompletableDeferred<Unit>`; `settle()` = `complete(Unit)` (idempotent, so once per process); the fallback is armed by an `AtomicBoolean.compareAndSet(false, true)` and only when not yet settled: `scope.launch { delay(fallback); settle() }`. Nothing else settles it, and it never reads `StartupReveal.covered` (`core/startup/RevealGate.kt:16`, `:78`). Keep the three `settle()` call sites (restored, fallback, splash gone) distinct so qa's measure patch can log the reason at each.
- [x] Add the Koin `single`.
- [x] Run the fast gate and `StartupSettledTest`; commit (e.g. "Add a process-wide startup-settled latch with ordered slots").

**Done when:** `StartupSettledTest` passes and the fast gate is green.

---

### Task 2: Report the splash leaving composition and feed the latch from MainActivity

**Covers:** AC-1 (instrumented), AC-2 (wiring)
**Files:**
- Modify: `ui/feature/root/component/StartupSplashHost.kt` - signature `StartupSplashHost(covered: Boolean, onGone: () -> Unit = {}, content: @Composable () -> Unit)` (`:27`); hold `val currentOnGone by rememberUpdatedState(onGone)` and call it in the `finishedListener` next to `splashGone = true` (`:36`). KDoc (`:20-25`) gains one sentence on `onGone`.
- Modify: `MainActivity.kt` - in `onCreate` resolve `val startupSettled = getKoin().get<StartupSettled>()` (pattern: `:126`), call `startupSettled.onActivityCreated(restored = savedInstanceState != null)` right after `StartupReveal.onActivityCreated` (`:76`), pass `onGone = startupSettled::onSplashGone` to `StartupSplashHost` (`:90`).
- Test: `ui/feature/root/component/StartupSplashHostTest.kt` (instrumented; pattern `splashLeavesCompositionAfterTheFade` `:52`)

**Consumes:** `StartupSettled.onActivityCreated`, `onSplashGone` (Task 1).
**Produces:** `StartupSplashHost(..., onGone)`; existing callers (`StartupSplashHostTest.kt:31`, `:54`, `:65`, `:75`; `ui/feature/update/component/UpdatePromptOverlayFocusTest.kt:118`) compile unchanged through the default.

- [x] Write `splashReportsGoneOnceAfterTheFade`: count `onGone` calls; 0 while `covered`, 1 after `covered = false` and `REVEAL_FADE_MS + 32`, still 1 after another `advanceTimeBy(500)`.
- [x] Implement both files.
- [x] Run the fast gate and `./tools/agentw :app:compileDevDebugAndroidTestKotlin`; commit (e.g. "Settle startup when the splash leaves composition or the activity is restored").

**Done when:** both compile tasks are green; the instrumented test is left to qa-device.

---

### Task 3: Hold prefetch warms until startup settles

**Covers:** AC-6
**Files:**
- Modify: `domain/interactor/prefetch/DetailsPrefetcher.kt` - add required `private val awaitSettled: suspend () -> Unit` after `foreground` (`:33-35`); `warm` (`:120`) calls `awaitSettled()` first, before `foreground.awaitForeground()` (`:123`) and `permits.acquire()` (`:125`); extend the `warm` KDoc (`:111-119`) to name the settled wait as a third "not started" point.
- Modify: `domain/di/modules.kt` - `DetailsPrefetcher(details = get(), foreground = get(), awaitSettled = get<StartupSettled>()::await)` (`:74-79`; same shape as `awaitForeground` at `:65`).
- Test: `domain/interactor/prefetch/DetailsPrefetcherTest.kt` - helper `prefetcher` (`:33-42`) gains `settled: StartupSettled` defaulting to a latch settled via `onActivityCreated(restored = true)` on `backgroundScope`, passed as `awaitSettled = settled::await`.
- Guard: `domain/di/DetailsPrefetcherWiringTest.kt` `aScreenScopeResolvesTheGlobalPrefetcher` (`:27`), unchanged; it fails if the Task 1 binding is missing.

**Consumes:** `StartupSettled.await` (Task 1).
**Produces:** the `awaitSettled` constructor parameter.

- [x] Write `noWarmStartsBeforeStartupSettles`, `aWarmHeldForStartupProceedsOnceItSettles` (`aWarmHeldForStartupTakesNoSlot` was dropped in review round 1: the gate is global, so every warm is held alike and the order of gate and permit cannot change what a test can observe); pattern `noWarmStartsWhileTheAppIsOffScreen` (`:243`).
- [x] Implement; existing `DetailsPrefetcherTest` cases stay green through the settled default.
- [x] Run the fast gate, `DetailsPrefetcherTest`, `DetailsPrefetcherWiringTest`; commit (e.g. "Hold details prefetch until startup settles").

**Done when:** both test classes pass and the fast gate is green.

---

### Task 4: Run MainVM startup work in its slots and drop StartupSyncDelay

**Covers:** AC-4, AC-7, AC-8
**Files:**
- Modify: `ui/feature/main/vm/MainVM.kt`
  - constructor (`:27-35`): required `private val startupSettled: StartupSettled` after `watchStateSyncInteractor`, before `tvHomeSyncCoordinator`; `viewModelOf(::MainVM)` (`ui/feature/main/component/MainScreen.kt:32`) resolves it from root, no change there;
  - `onStart` (`:47-61`): replace `tvHomeSyncCoordinator?.requestRefresh(immediate = true)` (`:54`) with `requestTvHomeRefresh()`: returns when the coordinator is null, else `launch { startupSettled.awaitSlot(StartupSlot.TvHome); coordinator.requestRefresh(immediate = true) }`;
  - `syncWatchState` (`:79-89`): `delay(StartupSyncDelay)` becomes `startupSettled.awaitSlot(StartupSlot.WatchState)`; flag `startupSyncDelayServed` (`:44-45`) renamed `startupSlotServed`, KDoc updated; the method KDoc (`:63-78`, mention at `:72`) speaks of the slot wait instead of `StartupSyncDelay`;
  - `reportDeviceInformation` (`:95-101`): `startupSettled.awaitSlot(StartupSlot.DeviceInfo)` as the first statement inside `launch`, before `setDeviceInformation()` is called;
  - companion (`:181-190`): delete `StartupSyncDelay` and its KDoc; drop the now unused `delay` and `seconds` imports (`:22`, `:25`).
- Test: `ui/feature/main/vm/MainVMStartupSlotsTest.kt` (new; `StandardTestDispatcher` + `MainDispatcherExtension(dispatcher)` + `runTest(dispatcher)`, VM builder copied from `MainVMWatchStateSyncTest.vm` `:127-169` with a `mockk<TvHomeSyncCoordinator>(relaxed = true)` and a latch parameter).
- Test: `ui/feature/main/vm/MainVMWatchStateSyncTest.kt` - `vm` (`:127`, construction `:161`) takes a latch; the three `StartupSyncDelay` advances (`:66`, `:103`, `:117`) become: latch settled before `testOnStart()`, `advanceTimeBy(StartupSlot.WatchState.offset.inWholeMilliseconds + 1)`; `onStart_doesNotSyncWatchStateWhileTheStartupRequestsAreStillOut` (`:49`) and `resumed_doesNotSyncWatchStateWhileTheStartupRequestsAreStillOut` (`:80`) keep their assertions on a settled latch.
- Test: `ui/feature/main/vm/MainVMDeviceInfoTest.kt` - `vm` (`:71`, construction `:102`) takes a latch; existing tests (`:43`, `:56`) settle it and advance `StartupSlot.DeviceInfo.offset` + 1 ms before asserting.
- Test: `ui/feature/main/vm/MainVMRefreshLifecycleTest.kt` - constructions `:71` (named, add `startupSettled =`), `:132`, `:172` (positional: the latch is the 7th argument).

**Consumes:** `StartupSettled.awaitSlot`, `StartupSlot` (Task 1).
**Produces:** `MainVM(..., startupSettled, tvHomeSyncCoordinator)`.

- [x] Write the failing tests: `MainVMDeviceInfoTest.onStart_doesNotReportDeviceInformationBeforeStartupSettles`, `MainVMRefreshLifecycleTest.onStart_doesNotRequestTvHomeRefreshBeforeStartupSettles`, `MainVMWatchStateSyncTest.onStart_doesNotSyncWatchStateBeforeStartupSettles` (each: unsettled latch, `advanceTimeBy(10_000)`, call count 0); `MainVMStartupSlotsTest.onStart_runsDeferredWorkInSlotOrderAfterStartupSettles` (settled at t0: `requestRefresh` at +500 ms, `setDeviceInformation` at +1500 ms, `syncIfStale` at +2000 ms, each absent 1 ms earlier), `deferredStartupWorkNeverRunsAfterTheViewModelIsCleared` (`testCancelScope()` before settle, then settle, advance 10 s, none of the three), `onStartAfterStartupSettled_countsSlotsFromMainStart` (settled, 10 s advanced, then `testOnStart()`: TV home at +500 ms, watch-state at +2000 ms from Main start).
- [x] Adapt the existing fixtures as listed; guards `startupAndFirstResume_syncWatchStateOnceBetweenThem`, `resumed_syncsWatchStateWithoutWaitingOnceTheStartupBurstHasPassed`, `onStart_keepsMainScreenUsableWhenDeviceReportFails` keep their assertions.
- [x] Implement `MainVM`.
- [x] Run the fast gate and the four `MainVM*Test` classes; commit (e.g. "Run Main's startup sync work in slots after startup settles").

**Done when:** the four classes pass, the fast gate is green, and `git grep -n StartupSyncDelay` in the worktree prints nothing.

---

### Task 5: Gate the automatic update check on its slot

**Covers:** AC-5, AC-7 (update check), AC-12 (unit slot)
**Files:**
- Modify: `ui/feature/update/vm/UpdatePromptVM.kt` - constructor (`:21-27`) gains `private val startupSettled: StartupSettled` after `updateCheckCoordinator`; the auto-check `launch` (`:48-56`) starts with `startupSettled.awaitSlot(StartupSlot.UpdateCheck)`. The manual path (`:38-42`, `checkForUpdateNow`) is untouched. `viewModelOf(::UpdatePromptVM)` (`ui/feature/root/component/App.kt:62`) resolves the latch from root.
- Test: `ui/feature/update/vm/UpdatePromptVMTest.kt` - the class uses the default `UnconfinedTestDispatcher` (`:33-35`), mostly outside `runTest`. `createVM()` (`:43-55`) and the direct construction (`:61-67`) take a latch built as `StartupSettled(scope = TestScope(mainDispatcher.dispatcher))`, settled by default; add `passUpdateSlot()` = `mainDispatcher.dispatcher.scheduler.advanceTimeBy(StartupSlot.UpdateCheck.offset.inWholeMilliseconds + 1); ...runCurrent()`, called right after `testOnStart()` in every test that relies on the auto-check result (`onStart_*` `:150`, `:159`, `:169`, `dismiss_hidesCurrentUpdateForSession` `:180`, the `updateClicked_*`, `onResume_*` and `dismiss_keepsPromptHidden_whenCanceledDownloadReturnsError` tests).

**Consumes:** `StartupSettled.awaitSlot`, `StartupSlot.UpdateCheck` (Task 1).
**Produces:** `UpdatePromptVM(..., startupSettled, resources)`.

- [x] Write `onStart_doesNotCheckForUpdateBeforeItsStartupSlot` (unsettled: no `checkForUpdate`; settled + 999 ms: none; +1 ms more: one), `manualCheck_runsBeforeStartupSettles` (unsettled latch, `requestManualCheck()` reaches `Available`), `onStart_skipsTheAutoCheckWhenClearedBeforeItsSlot` (`testCancelScope()` before the slot, then settle and advance: no `checkForUpdate`).
- [x] Adapt the fixtures; guards `onStart_showsAvailable_whenUpdateExists` and `onStart_keepsHiddenAndSkipsCheck_whenAutoCheckDisabled` keep their assertions.
- [x] Implement.
- [x] Run the fast gate and `UpdatePromptVMTest`; commit (e.g. "Hold the automatic update check until its startup slot").

**Done when:** `UpdatePromptVMTest` passes and the fast gate is green.

---

### Task 6: Coalesce Home publishes queued behind an in-flight publish

**Covers:** AC-9, AC-10
**Files:**
- Modify: `ui/feature/home/vm/HomeVM.kt`
  - next to `publishMutex` (`:91`): `private val requestedPublishes = MutableStateFlow(0L)` and `private val publishedThrough = MutableStateFlow(0L)`; extend the `publishMutex` KDoc (`:79-90`) with the coalescing rule;
  - `publishSections` (`:467-520`) becomes a block body: `val mine = requestedPublishes.updateAndGet { it + 1 }` before the lock; inside `publishMutex.withLock`: return at once if `publishedThrough.value >= mine`; read `val upTo = requestedPublishes.value` **before** the snapshot fields (`:473-477`), so every request counted in `upTo` had its data written before the snapshot; map as today; on the epoch skip (`:505-511`) set `publishedThrough.value = upTo` before `return@withLock`; after `updateViewState` (`:512-519`) set `publishedThrough.value = upTo`. A cancelled publish sets nothing, so the next queued request still maps. `catalogueEpoch` (`:104`) and the lock span are unchanged;
  - `@VisibleForTesting internal suspend fun awaitPublishesDrained() { publishedThrough.first { it >= requestedPublishes.value } }`.
- Test: `ui/feature/home/vm/HomeVMIncrementalPublishTest.kt` - replace the exact-count drains `verify(exactly = 8, timeout = 2_000)` (`:592`) and `verify(exactly = 6, timeout = 2_000)` (`:694`) with `withContext(Dispatchers.Default) { withTimeout(2_000) { vm.awaitPublishesDrained() } }` (real-time bound: `runTest`'s virtual clock would fire a bare `withTimeout` at once); rewrite the comments above them (`:583-591`, `:687-693`) to the drain.

**Consumes:** `publishMutex` (`:91`), `catalogueEpoch` (`:104`), callers `:178`, `:341`, `:368`, `:379` (unchanged).
**Produces:** `HomeVM.awaitPublishesDrained()` (test only).

- [x] Write the failing `arrivalsQueuedBehindAnInFlightPublishFoldIntoOneFollowUp`: four section flows held as `MutableSharedFlow(replay = 1)` and `mapItemSection` answering `stateFor(type)` (pattern `heroSettledIsSnapshottedWithTheRestOfThePublishSoAStalePublishCannotPairItWithEmptyHeroItems` `:668-774`, gate `:697-725`); drain, record the `mapHeroItems` count, emit the first held section and park its mapping, emit the other three, release, drain: `mapHeroItems` ran twice since the park and the final `Content` holds all four section types (base: four times).
- [x] Write `aCancelledFollowUpLeavesTheNextQueuedRequestToPublish` (a queued request under `loadHomeJob` cancelled through `CommonAction.OnResume` while a `displayChanges` request queues behind it; the latter still publishes) and `aSectionArrivingAfterAClearPublishesEvenWhenAStalePublishWasSkipped` (a stale publish skipped by the epoch check, then a section of the new catalogue arrives and publishes); park pattern `aPublishParkedAtTheHopCannotResurrectRowsAClearAlreadyDropped` (`:562-665`).
- [x] Replace the two drains; implement `HomeVM`.
- [x] Guards stay green unchanged: `theFirstSectionToArrivePublishesContentWithoutWaitingForTheRest` (`:112`), `publishSectionsSerializesConcurrentSectionArrivals` (`:466`), `heroSettledIsSnapshotted...` (`:668`), `aPublishParkedAtTheHop...` (`:562`), `aCacheWipeFromAnotherScreenDropsTheRowsFromThePreviousCatalogue` (`:265`), and `HomeVMTest`.
- [x] Run the fast gate, `HomeVMIncrementalPublishTest` and `HomeVMTest` (three times each, real-thread tests); commit (e.g. "Fold Home publish requests queued behind an in-flight publish into one follow-up").

**Done when:** both classes pass three runs in a row and the fast gate is green.

---

### Task 7: Full gate and hand-off to device verification

**Covers:** AC-13; AC-11 and AC-12 (device) are qa-device's, run on the task head.
**Files:** none planned; a fix the full gate needs goes into one commit.

- [x] Run the full gate; paste its final lines into the Implementation record.
- [x] `git -C <wt> log -p 7bcbfd83..HEAD | grep -c SettledMeasure` prints `0`; `git -C <wt> grep -n StartupSyncDelay` prints nothing.
- [x] Record for qa-device under "For the reviewers": the measure patch hunk in `HomeVM.publishSections` must be re-applied before `updateViewState` at its new line; the settle-reason line (perf metric 5) goes at the three `settle()` call sites in `core/startup/StartupSettled.kt`; instrumented tests to run: `com.kino.puber.ui.feature.root.component.StartupSplashHostTest`, `com.kino.puber.ui.feature.update.component.UpdatePromptOverlayFocusTest` (`updatePromptShownOverFocusedContentTakesFocusWithinOneSecond` `:144`).

**Done when:** the full gate exits 0 in the worktree.

---

## Implementation record

<!-- owner: implementer -->

**Commits:**

| Task | Subject |
|---|---|
| 1 | Add a process-wide startup-settled latch with ordered slots |
| 2 | Settle startup when the splash leaves composition or the activity is restored |
| 3 | Hold details prefetch until startup settles |
| 4 | Run Main's startup sync work in slots after startup settles |
| 5 | Hold the automatic update check until its startup slot |
| 6 | Fold Home publish requests queued behind an in-flight publish into one follow-up |
| 7 | Satisfy detekt in the startup-settled tests and record the implementation |
| fix T2, T3 | Keep the automatic update result off a running manual flow and ignore Update presses just after it appears |
| fix T6 | Replace the settle-twice test with one that fails when a settled latch still arms the fallback |
| fix T4, T5 | (see the round 1 line below) |

**Red run** (P0 `red` tests; run on a stub with the production behaviour unchanged):

- red: `StartupSettledTest.isNotSettledBeforeAnyActivityIsCreated`, `splashGoneSettlesTheLatch`, `freshActivitySettlesAtTheFallbackWhenTheSplashNeverReportsGone` - stub `await()` returned at once, so the waiter resumed / `isSettled` was false.
- green on base: `StartupSettledTest.restoredActivitySettlesAtOnce` - the stub `await()` returned at once, which satisfies the test.
- red (round 1, T1): `StartupSettledTest.restoredActivitySettlesAtOnce` - on a stub where `restored = true` only arms the fallback: `AssertionFailedError at StartupSettledTest.kt:78` (`assertTrue(resumed)`); production restored afterwards.
- red: `DetailsPrefetcherTest.noWarmStartsBeforeStartupSettles` - warm started with the gate closed (`awaitSettled` parameter present, not called).
- red: `MainVMDeviceInfoTest.onStart_doesNotReportDeviceInformationBeforeStartupSettles`, `MainVMRefreshLifecycleTest.onStart_doesNotRequestTvHomeRefreshBeforeStartupSettles`, `MainVMWatchStateSyncTest.onStart_doesNotSyncWatchStateBeforeStartupSettles`, `MainVMStartupSlotsTest.onStart_runsDeferredWorkInSlotOrderAfterStartupSettles`, `deferredStartupWorkNeverRunsAfterTheViewModelIsCleared`, `onStartAfterStartupSettled_countsSlotsFromMainStart` - calls still ran at `onStart` (stub parameter unused).
- red: `UpdatePromptVMTest.onStart_doesNotCheckForUpdateBeforeItsStartupSlot` - check ran with the latch unsettled. `manualCheck_runsBeforeStartupSettles` is a guard (green on base, as designed).
- red: `HomeVMIncrementalPublishTest.arrivalsQueuedBehindAnInFlightPublishFoldIntoOneFollowUp` - with the counters in place and the skip disabled: "the three queued arrivals did not fold into one publish ==> expected 1 but was 2". Measured when the drain returns, which a non-folding publish satisfies after its first follow-up (so 2, not the 3 it ends at).
- Also failed on that skip-disabled stub, for the same reason (a drain that returns before the leftover initial publishes ran): `aPublishParkedAtTheHopCannotResurrectRowsAClearAlreadyDropped`. Green on the real change.
- green on base, P1 rows (not P0 red): `aWarmHeldForStartupProceedsOnceItSettles`, `aCancelledFollowUpLeavesTheNextQueuedRequestToPublish`, `aSectionArrivingAfterAClearPublishesEvenWhenAStalePublishWasSkipped` (they guard against a wrong implementation of the fold, not against base).
- red (round 1, T2): `UpdatePromptVMTest.automaticResult_doesNotReplaceAManualCheckInProgress` - `AssertionFailedError at UpdatePromptVMTest.kt:385` (state became Available over Checking). T3: `updateClicked_isIgnored_whileTheAutomaticPromptIsInItsInputGuard` - `AssertionFailedError at UpdatePromptVMTest.kt:400` (Update press started a download straight after the prompt appeared). Both on a stub with only the `UPDATE_PROMPT_INPUT_GUARD` constant added.
- T6: `aFreshActivityAfterTheSplashIsGoneStartsNoFallback` replaces `settlingTwiceResumesEachWaiterOnce`; fails (`StartupSettledTest.kt:58`, a child coroutine remains) with the `!settled.isCompleted` guard removed from `onActivityCreated`.
- T4: the real-time `delay(200)` in `aSectionArrivingAfterAClearPublishesEvenWhenAStalePublishWasSkipped` now waits on `HomeVM.awaitPublishRequestsBeyond` (new `@VisibleForTesting` hook). T5: `aWarmHeldForStartupTakesNoSlot` dropped, see the plan task 3 line.
- not run: `StartupSplashHostTest.splashReportsGoneOnceAfterTheFade` (instrumented, qa-device; compiled only).

**Deviations from the plan:**

- Test helpers `settledStartup` / `unsettledStartup` in `core/startup/StartupSettledTestSupport.kt` (new test file) are shared by the MainVM and UpdatePromptVM tests instead of four private copies.
- `MainVMRefreshLifecycleTest` gained a `vmWithCoordinator` builder for the TV home test (the existing fixtures pass no coordinator).
- `HomeVMIncrementalPublishTest` carries `@Suppress("LargeClass")` (detekt, same use as `PlayerVM`, `DetailsVM`): the three new tests share its setup, a split would copy it.
- The fold test asserts exactly one `mapHeroItems` call after the park (the park publish itself is already counted), which is the plan's "ran twice since the park" counted from the park.

**Files touched outside the plan:** `core/startup/StartupSettledTestSupport.kt` (test).

**For the reviewers:**

- `HomeVM.publishSections`: the block is re-indented; the logic is the skip at lock entry, `upTo` read before the snapshot, and `publishedThrough = upTo` on both the epoch skip and the publish. Check that every writer of `loadedSections`, `loadedCollections`, `lastWatchedAt`, `heroSettled` writes before its `publishSections()` call (else a counted request's data is not in `upTo`'s snapshot).
- `StartupSettled`: the three `settle` sites are `onActivityCreated(restored = true)`, the fallback `launch`, and `onSplashGone()`.
- For qa-device: the measure patch hunk in `HomeVM.publishSections` goes before `updateViewState(HomeViewState.Content(...))` at its new place inside the `withLock` block; the settle-reason line (perf metric 5) goes at the three sites above in `core/startup/StartupSettled.kt`; instrumented tests to run: `com.kino.puber.ui.feature.root.component.StartupSplashHostTest`, `com.kino.puber.ui.feature.update.component.UpdatePromptOverlayFocusTest` (`updatePromptShownOverFocusedContentTakesFocusWithinOneSecond`).
- Task 7 checks: `git grep -n StartupSyncDelay -- app` prints nothing; `git log -p 7bcbfd83..HEAD -- app | grep -c SettledMeasure` prints 0. The whole-tree grep prints 9, all prose in the spec, plan and baseline evidence docs committed before implementation (no instrumentation line).
- Full gate: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` - BUILD SUCCESSFUL (5m 28s).

**Blocked:** none.

## Readiness

Verdict: **READY**

Spec `docs/superpowers/specs/2026-11-21-startup-settled-signal.md`, plan `docs/superpowers/plans/2026-11-21-startup-settled-signal.md` (worktree, untracked), test-design.md; base 7bcbfd83.

#### AC coverage

| AC | Plan task | Test design row |
|---|---|---|
| AC-1 | 1 (unit), 2 (instrumented) | `StartupSettledTest` x3, `splashReportsGoneOnceAfterTheFade` |
| AC-2 | 1, 2 (wiring) | `restoredActivitySettlesAtOnce` |
| AC-3 | 1 | `freshActivitySettlesAtTheFallbackWhenTheSplashNeverReportsGone`, `aSecondFreshActivityDoesNotPostponeTheFallback` |
| AC-4 | 4 | three `...BeforeStartupSettles` rows, `onStart_runsDeferredWorkInSlotOrderAfterStartupSettles`, guards |
| AC-5 | 5 | `onStart_doesNotCheckForUpdateBeforeItsStartupSlot`, `manualCheck_runsBeforeStartupSettles` |
| AC-6 | 3 | `noWarmStartsBeforeStartupSettles` + 2, wiring guard |
| AC-7 | 4, 5 | `deferredStartupWorkNeverRunsAfterTheViewModelIsCleared`, `onStart_skipsTheAutoCheckWhenClearedBeforeItsSlot` |
| AC-8 | 4 | `onStartAfterStartupSettled_countsSlotsFromMainStart` |
| AC-9 | 6 | `arrivalsQueuedBehindAnInFlightPublishFoldIntoOneFollowUp` + guards |
| AC-10 | 6 | `aPublishParkedAtTheHop...`, `aCacheWipeFromAnotherScreen...`, `aSectionArrivingAfterAClear...` |
| AC-11 | 7 (hand-off to qa-device) | Perf track metrics 1-5 |
| AC-12 | 5 (unit slot), 7 (hand-off) | `updatePromptShownOverFocusedContentTakesFocusWithinOneSecond`; Manual 2 |
| AC-13 | 7 | full gate command |

#### Citations sampled (at 7bcbfd83)

| Citation | Resolves to |
|---|---|
| `core/startup/RevealGate.kt:16`, `:78` | `state = MutableStateFlow(false)`; `covers = begun && ...` |
| `core/startup/StartupReveal.kt:35` | `onActivityCreated(restored)` |
| `ui/feature/root/component/StartupSplashHost.kt:27`, `:36` | signature; `finishedListener = { if (it == 0f) splashGone = true }` |
| `MainActivity.kt:76`, `:90`, `:126` | `StartupReveal.onActivityCreated`; `StartupSplashHost(covered)`; `getKoin().get<ContentLaunchCoordinator>()` |
| `domain/di/modules.kt:65`, `:74-79`, `:80` | `awaitForeground` reference; `DetailsPrefetcher` single; `single { AppCoroutineScope() }` (same `interactorModule`, so the wiring guard covers it) |
| `ui/feature/main/vm/MainVM.kt:27-35`, `:45`, `:54`, `:79-89`, `:95-101`, `:190` | constructor; `startupSyncDelayServed`; `requestRefresh(immediate = true)`; `syncWatchState`; `reportDeviceInformation`; `StartupSyncDelay = 5.seconds` |
| `ui/feature/update/vm/UpdatePromptVM.kt:48-56` | auto-check `launch` |
| `domain/interactor/prefetch/DetailsPrefetcher.kt:120`, `:123`, `:125` | `warm`; `awaitForeground()`; `permits.acquire()` |
| `ui/feature/home/vm/HomeVM.kt:91`, `:104`, `:467`, `:505`; callers `:178,:341,:368,:379` | `publishMutex`; `catalogueEpoch`; `publishSections`; epoch check; four `publishSections()` calls |
| `HomeVMIncrementalPublishTest.kt:592`, `:694`, `:112`, `:466`, `:562`, `:668`, `:265` | `verify(exactly = 8 ...)`, `exactly = 6`, and the five named guards |
| `MainVMWatchStateSyncTest.kt:66,:103,:117,:161`; `MainVMDeviceInfoTest.kt:102`; `MainVMRefreshLifecycleTest.kt:71,:132,:172`; `UpdatePromptVMTest.kt:48,:61`; `DetailsPrefetcherTest.kt:33` | every constructor of the changed classes at base (`git grep` finds no other) |
| `StartupSplashHostTest.kt:52`; `UpdatePromptOverlayFocusTest.kt:118`, `:144` | pattern test; splash caller; AC-12 guard |

#### Build order

Task 1 adds the class and binding before any consumer; Tasks 3-5 change each constructor together with its test fixtures; Task 2 resolves a binding Task 1 made. No task leaves the build red.

#### Notes (non-blocking)

| # | Note |
|---|---|
| 1 | AC-10 text names "existing epoch tests in `HomeVMTest`"; they live in `HomeVMIncrementalPublishTest` (`:265`, `:562`), which the test design and Task 6 already use. |
| 2 | AC-11 P0 row names the perf track, not an automated test; it is a device measurement by nature. |
| 3 | The plan file is untracked in the worktree; Task 1's commit or the conductor must add it. |

Open questions: none (state.md Decisions answer Q1-Q4).


