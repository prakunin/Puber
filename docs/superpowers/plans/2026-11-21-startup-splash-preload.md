# Use the splash as loading time - plan

**Spec:** `docs/superpowers/specs/2026-11-21-startup-splash-preload.md`
**Goal:** Cold and warm starts load domain, token and Home data while the splash covers a composed Main+Home, then fade it out on readiness or at 2.5 s, dropping D-pad/OK until then.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`; full gate: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`. Both run in `/home/user1/orca/workspaces/Puber/startup-splash-preload`, never `./gradlew` there; set `AGENT_GRADLE_SERIAL=1` when another worktree may build.
- One task = green fast gate + green tests of the touched code + one commit, message: one imperative English sentence, no prefixes, never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- Production paths are relative to `app/src/main/java/com/kino/puber/`; unit tests to `app/src/test/kotlin/com/kino/puber/` (prefix `test/`); instrumented tests to `app/src/androidTest/kotlin/com/kino/puber/` (prefix `androidTest/`). Line numbers are at base `a22c03bc`.
- Test-first: write each `P0 red` test before the production change. Where the test needs a symbol that does not exist yet, add it with its final signature and an inert body first, so the test compiles and fails on its assertion; record each red result under **Red run**.
- Unit tests: JUnit 5, `@RegisterExtension MainDispatcherExtension` (`test/util/MainDispatcherExtension.kt:12`), mockk. Every test class that touches `StartupReveal` calls `StartupReveal.resetForTests()` in `@AfterEach`, as `test/core/ui/PuberVMTelemetryTest.kt:87-91` does with `Telemetry`.
- Instrumented tests: compile with `./tools/agentw :app:compileDevDebugAndroidTestKotlin`; run with an emulator lease (`tools/emulator-resource-lock.sh`, released when done) and `make DEVICE=<serial> GRADLE=./tools/agentw itest TESTS=<fully.qualified.Class>`. No free emulator: record it under **Blocked**, keep the compile green, go on with the next task. Harness as in `androidTest/ui/feature/update/component/UpdatePromptOverlayFocusTest.kt:46-49`: `createComposeRule()` from `androidx.compose.ui.test.junit4.v2`, `PuberTheme`, `mainClock` instead of sleeps.
- Device rows M1-M10 and the Perf track (AC-13) are qa-device's after Task 8; no task here installs or launches the app.
- Out of scope (spec): P1 deferral and publish coalescing (`startup-settled-signal`), baseline profile, R8, `reportFullyDrawn`. `TvHomeSyncCoordinator` (`PuberApp.kt:51`) and the window background (`res/values/themes.xml:6`) stay as they are (spec A-Q1, A-Q2).

## Peer audit (perf-audit-2026-11-21.md)

Absorbed:

| Item | How |
|---|---|
| 1. `PuberApp.kt:91` builds half the DI graph on main | `AppStartup` takes `() -> ApiDomainInteractor` and `() -> HomeInteractor` providers and calls them only on `ioDispatcher`; resolving `AppStartup` on main builds only itself and `AppCoroutineScope` (Task 6, Task 8) |
| 2. JSON decode on Main, warm-up part only | the warm-up collects on `AppCoroutineScope` (`Dispatchers.Default`), so its decode and Room I/O never touch main (Task 6) |

Not absorbed (each a candidate idea run):

| Item | One line |
|---|---|
| 2 (rest) | HomeVM/DetailsVM section collectors decode on `viewModelScope` (Main); `flowOn(Default)` on `ContentCacheRepository` flows |
| 1 (rest) | `WatchStateRepository` starts a full read and `clearAllPending()` write on construction, now on IO but still competing with startup I/O |
| 3 | Same payload decoded 3-6 times per load; JSON-in-JSON string encoding in `CachedFeed` |
| 4 | `ensureLegacyRowsRemoved` runs 6 `LIKE` full-table deletes per process under the cache mutex; `cached_payload` grows unbounded |
| 5 | Hot/Fresh fetch movies then series sequentially; `FreshSectionPager` walks types sequentially |
| 6 | DoH answers never cached; Coil, IntroDb and TheIntroDb build separate HTTP stacks |
| 7 | Baseline profile stale since 1.0.0 (owner: separate run) |
| 8 | R8 keep rules broad; unused coil-video and AppCompat (owner: separate run) |
| 9 | `largeHeap` inflates Coil memory cache and player buffer tier |
| 10 | Two prefs files read on main before first frame; no StrictMode in debug |
| 11 | 60 s connect / 120 s request timeouts and 3 retries delay failover far past the 2.5 s cap |
| FYI focus | `FocusTargetState` reads focus in composition and Home reads `focusedTarget` at screen level: every D-pad step recomposes all visible cards/rows |
| FYI focus | `LocalContentFocusActive` is a `staticCompositionLocalOf` that changes at runtime |
| FYI player | Main playback writes every byte to the 512 MB `SimpleCache` |
| FYI player | `markStale` on every 30 s save forces a details refetch before the next episode |
| FYI player | TV home refresh on every 30 s save; `ON_STOP` only pauses; all subtitles side-loaded |
| FYI collections | `onLoadMore()` called from composition without an in-flight guard |

Home publish coalescing (audit FYI) belongs to `startup-settled-signal`.

---

## Tasks

### Task 1: Add the revealed startup mark and emit the startup line once both marks are in

**Covers:** AC-9
**Files:**
- Modify: `core/telemetry/StartupMarks.kt` - keep `firstContentMs` and new `revealedMs` as `AtomicLong(NOT_REACHED)` set first-value-only (pattern `markAppComposed` `:20-22`); `markFirstContent()` (`:24`) and new `markRevealed()` emit only when both are reached; `onTimeout()` (`:26`) emits with whatever exists; `emit` (`:28-32`) keeps the `emitted` CAS and passes both values.
- Modify: `core/telemetry/TelemetryEvent.kt:130-138` - `Startup` gains last parameter `val revealedMs: Long? = null`.
- Modify: `core/telemetry/TelemetryEncoder.kt:67-72` - `event.revealedMs?.let { put("revealed", it - event.procStartMs) }`.
- Modify (tests whose assertion ran at `first_content` emission): `test/core/ui/PuberVMTelemetryTest.kt:133-178` (three tests) and `test/ui/feature/contentlist/vm/SectionVMTest.kt:196-226` - call `Telemetry.startupMarks?.onTimeout()` immediately before the `startups.map { it.firstContentMs }` assertion; asserted values unchanged.
- Test: `test/core/telemetry/StartupMarksTest.kt`, `test/core/telemetry/TelemetryEncoderTest.kt`

**Consumes:** `FakeTelemetryClock` fixture as in `StartupMarksTest.kt:9-11`.
**Produces:**
```kotlin
fun StartupMarks.markRevealed()            // keeps the first value
data class TelemetryEvent.Startup(..., val firstContentMs: Long?, val revealedMs: Long? = null)
```

- [x] Write red: `StartupMarksTest.lineWaitsForRevealedAndCarriesBothMarks`, `StartupMarksTest.oneLineWhenRevealedComesBeforeFirstContent`, `StartupMarksTest.fallbackLineHasNoRevealedWhenItNeverCame`, `TelemetryEncoderTest.startupWritesRevealed` (asserts `revealed` = `revealedMs - procStartMs`, pattern `:117-125`).
- [x] Add P1 `StartupMarksTest.revealedKeepsTheFirstValue`.
- [x] Implement; keep guards `StartupMarksTest.fallbackLineAfterSixtySecondsWithMarksSoFar` (`:40`) and `TelemetryEncoderTest.startupOmitsMarksNotReached` (`:128`) unchanged and green.
- [x] Fast gate; run `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.core.telemetry.*' --tests 'com.kino.puber.core.ui.PuberVMTelemetryTest' --tests 'com.kino.puber.ui.feature.contentlist.vm.SectionVMTest'`; commit "Add the revealed startup mark and emit the startup line once first content and reveal are both in".

**Done when:** the test command above exits 0 and `rg -n '"revealed"' app/src/main` hits `TelemetryEncoder.kt` only.

---

### Task 2: Add the reveal gate, its process holder and the covered-key filter

**Covers:** AC-5, AC-7, AC-10 (unit rows)
**Files:**
- Create: `core/startup/RevealGate.kt` - pure class, no Android types.
- Create: `core/startup/StartupReveal.kt` - process object over one `RevealGate` with clock `{ System.nanoTime() / 1_000_000 }` (JVM-safe in unit tests); `resetForTests()` calls `gate.reset()` (pattern `core/telemetry/Telemetry.kt:55-62`).
- Create: `core/startup/StartupKeyFilter.kt` - top-level `isDroppedWhileCovered(keyCode)` for `KeyEvent.KEYCODE_DPAD_UP/DOWN/LEFT/RIGHT/CENTER`, `KEYCODE_ENTER`, `KEYCODE_NUMPAD_ENTER`; class `CoveredKeyFilter` that also drops the repeats and the `ACTION_UP` of a key whose `ACTION_DOWN` it dropped, even after reveal (otherwise an OK pressed under the splash and released after it opens the hero: the select handlers act on `KeyUp`, `core/ui/uikit/component/HeroCarousel.kt:182-196`).
- Test: `test/core/startup/RevealGateTest.kt`, `test/core/startup/StartupKeyFilterTest.kt`, `test/core/telemetry/DispatchWithProbeTest.kt`

**Consumes:** `dispatchWithProbe` (`core/telemetry/KeyProbe.kt:109-119`).
**Produces:**
```kotlin
internal const val REVEAL_CAP_MS = 2_500L
internal class RevealGate(clock: () -> Long, capMs: Long = REVEAL_CAP_MS) {
    val covered: StateFlow<Boolean>   // false until begin()
    fun begin()                       // covered = true, cap counted from now
    fun markReady()                   // covered = false; no-op when not covered
    fun onCap()                       // covered = false
    fun remainingCapMs(): Long        // max(0, began + capMs - now); 0 before begin()
    fun reset()                       // covered = false, not begun
}
internal object StartupReveal {       // same members over one gate, plus:
    val isCovered: Boolean
    @VisibleForTesting fun resetForTests()
}
internal fun isDroppedWhileCovered(keyCode: Int): Boolean
internal class CoveredKeyFilter {     // main thread only
    fun shouldDrop(covered: Boolean, action: Int, keyCode: Int): Boolean
}
```

- [x] Write red: `RevealGateTest.remainingCapMs_countsFromBegin` (fake clock: 2 500 at `begin()`, 1 500 after 1 000 ms), `RevealGateTest.cap_uncoversWithoutReady`, `RevealGateTest.markReady_uncoversImmediately`, `RevealGateTest.begin_coversAgainAfterAPreviousReveal`, `StartupKeyFilterTest.dpadAndOkAreDroppedWhileCovered`, `StartupKeyFilterTest.backAndOtherKeysPass` (BACK, MENU, media play/pause return false).
- [x] Write `StartupKeyFilterTest.upOfAKeyPressedWhileCoveredIsDroppedAfterReveal` (added by this plan, see Implementation record note: DPAD_CENTER down while covered, then up while uncovered: both dropped; a fresh DPAD_CENTER down after that passes).
- [x] Add P1 `DispatchWithProbeTest.aKeyDroppedBySuperIsStillProbed` (super returns `true` without dispatching; probe called with `handled = true`).
- [x] Implement; fast gate; `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.core.startup.*' --tests 'com.kino.puber.core.telemetry.DispatchWithProbeTest'`; commit "Add the startup reveal gate and the filter for keys pressed under the splash".

**Done when:** the test command exits 0; nothing outside `core/startup/` references the new classes yet.

---

### Task 3: Lift the splash on the first content state of any landing screen

**Covers:** AC-6
**Files:**
- Modify: `core/ui/PuberVM.kt` - add `protected open val revealsOnContentState: Boolean get() = true`; in `updateViewState` (`:86-90`) after the assignment at `:88`: `if (revealsOnContentState && viewState is StartupContentState) StartupReveal.markReady()`, outside the `Telemetry.isEnabled` checks (`:87`, `:89`).
- Modify: `ui/feature/details/model/DetailsScreenState.kt:14` and `ui/feature/player/model/PlayerViewState.kt:13` - `Content` also implements `StartupContentState` (`core/telemetry/StartupContentState.kt:8`).
- Modify: `docs/architecture/viewmodel.md:35-47` - one table row: `revealsOnContentState` | `protected open val`, default `true`: publishing a `StartupContentState` lifts the startup splash (`core/startup/StartupReveal.kt`); Home overrides it and reveals on its launch-focus outcome.
- Test: `test/core/ui/PuberVMRevealTest.kt` (new; own private `TestVM`/`OverlayVM`/states, pattern `PuberVMTelemetryTest.kt:30-69`)

**Consumes:** `StartupReveal` (Task 2).
**Produces:** `PuberVM.revealsOnContentState` (Task 4 overrides it).

- [x] Write red: `PuberVMRevealTest.firstStartupContentStateRevealsTheSplash` (recorder enabled, `begin()`, publish content: `isCovered` false), `PuberVMRevealTest.revealDoesNotDependOnTelemetryRecording` (no recorder installed), `PuberVMRevealTest.loadingErrorAndOverlayStatesKeepTheSplash` (loading, error, overlay `Available`: still covered), `PuberVMRevealTest.contentLinkTargetStatesRevealTheSplash` (`DetailsScreenState.Content` and `PlayerViewState.Content` classes are `StartupContentState`, checked with `StartupContentState::class.java.isAssignableFrom(...)` so no fixture is built).
- [x] Implement; keep guard `PuberVMTelemetryTest.theRealLandingStatesAreMarkedAndErrorLoadingAndOverlayStatesAreNot` (`:181`) green.
- [x] Fast gate; `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.core.ui.*'`; commit "Lift the startup splash when a landing screen publishes its first content state".

**Done when:** the test command exits 0.

---

### Task 4: Reveal Home only once its hero focus claim has settled

**Covers:** AC-4
**Files:**
- Modify: `ui/feature/home/model/HomeAction.kt:5-11` - add `data object LaunchFocusSettled : HomeAction`.
- Modify: `ui/feature/home/vm/HomeVM.kt` - constructor (`:47-58`) gains last parameter `private val revealSignal: () -> Unit = StartupReveal::markReady`; `override val revealsOnContentState: Boolean get() = false`; in `handleAction` (`:176-199`) add `HomeAction.LaunchFocusSettled -> revealSignal()` before `else` (`:197`). `ui/feature/home/component/HomeScreen.kt:34-47` stays unchanged (default).
- Modify: `ui/feature/home/component/HomeScreenContent.kt` - in `HomeContent` (`:161`) after `ClaimHeroFocusEffect` (`:194-203`): a `remember`ed `settledSent` flag and `LaunchedEffect(state.heroSettled, state.heroItems.isEmpty(), heroFocusResolved, rowsHeldFocus)` that sends `onAction(HomeAction.LaunchFocusSettled)` once when `state.heroSettled && (state.heroItems.isEmpty() || heroFocusResolved || rowsHeldFocus)`. `rowsHeldFocus` (`:181`, restored on recreation) disables the claim (`:197`); without it in the condition a recreated Home would reveal only at the cap.
- Test: `test/ui/feature/home/vm/HomeVMTest.kt` (add `revealSignal` parameter to `createVM` `:446-459`, add `@AfterEach StartupReveal.resetForTests()`), `androidTest/ui/feature/home/component/HomeLaunchFocusSettledTest.kt` (new)

**Consumes:** `PuberVM.revealsOnContentState` (Task 3), `StartupReveal` (Task 2).
**Produces:** `HomeAction.LaunchFocusSettled`.

- [x] Write red: `HomeVMTest.launchFocusSettled_revealsTheSplash` (counter passed as `revealSignal`, one action: called once), `HomeVMTest.contentState_keepsTheSplashUntilLaunchFocusSettled` (`StartupReveal.begin()`, `testOnStart()` with the stubs of `setup` `:69-98`: state is `Content` and `StartupReveal.isCovered` is true).
- [x] Write `HomeLaunchFocusSettledTest`, harness and fixtures copied from `androidTest/ui/feature/home/component/HomeFocusTraversalTest.kt:42-77` and its helpers (`item()` `:355`, `itemTitle()` `:362`, `heroItem()` `:364`, `HERO_TITLE` `:332`), recording actions into a list: P0 red `heroClaimSendsLaunchFocusSettledWithHeroFocused` (`heroSettled = true`, one hero: action sent, hero focused); P1 `emptyHeroSendsLaunchFocusSettledWithFirstRowFocused` (no hero, `heroSettled = true`: action sent, first row card focused); P1 `unsettledHeroSendsNothing` (`heroSettled = false`, rows only, `mainClock.advanceTimeBy(1_000)`: no action).
- [x] Implement; fast gate; `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.ui.feature.home.*'`; `./tools/agentw :app:compileDevDebugAndroidTestKotlin`; run `com.kino.puber.ui.feature.home.component.HomeLaunchFocusSettledTest` and `com.kino.puber.ui.feature.home.component.HomeFocusTraversalTest` under a lease; commit "Keep the splash over Home until its hero focus claim has settled".

**Done when:** unit command exits 0; both instrumented classes pass (or the run is under **Blocked**).

---

### Task 5: Let concurrent domain auto-resolves share one probe

**Covers:** AC-3
**Files:**
- Modify: `domain/interactor/api/ApiDomainInteractor.kt` - a private `Mutex`; the body of `autoResolveWorkingDomain` inside `withContext(Dispatchers.IO)` (`:117-145`) runs under `mutex.withLock`, so the second caller answers from `isKnownReachable` (`:125`) after the first probe marked the domain reachable (`:194-198`).
- Test: `test/domain/interactor/api/ApiDomainInteractorTest.kt` (fixture `:29-67`)

**Consumes:** nothing new. **Produces:** nothing new.

- [x] Write red `ApiDomainInteractorTest.autoResolve_concurrentCallersShareOneProbe`: a probe that records the call and blocks on a `CountDownLatch` for `service-kp.test` (reachable); start two `async { interactor.autoResolveWorkingDomain() }`, wait in real time (`withContext(Dispatchers.Default) { delay(200) }`) so the second has reached the lock or the probe, release the latch, await both: `probeCalls == listOf("service-kp.test")`, both `Success(changed = false)`.
- [x] Implement; fast gate; `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.domain.interactor.api.*'`; commit "Serialize domain auto-resolve so a concurrent caller reuses the probe in flight".

**Done when:** the test command exits 0, all existing `autoResolve_*` tests (`:120-215`) included.

---

### Task 6: Add the application scope and the startup owner that applies the domain and warms Home

**Covers:** AC-2, AC-3, AC-11
**Files:**
- Create: `core/coroutine/AppCoroutineScope.kt` - `internal class AppCoroutineScope(override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> log(e, "...") }) : CoroutineScope` (`log` from `core/logger`, as used at `ApiDomainInteractor.kt:177`).
- Create: `core/startup/AppStartup.kt`:
  - `start()` (idempotent, `AtomicBoolean`): `scope.launch(ioDispatcher)` { `try { domainInteractor().initialize() }` (`ApiDomainInteractor.kt:53-60`) `catch` non-cancellation → log; `finally { domainInitialized.complete(Unit) }`; then `warmUp()` }.
  - `warmUp()`: token via `withContext(ioDispatcher) { preferences.getAccessToken() }`; null or empty → return; `domainInteractor().autoResolveWorkingDomain()` not `Success` → return; `val home = homeInteractor()`; in `coroutineScope` collect `home.observeHotItems()` (`HomeInteractor.kt:56-62`) and `home.observeWatchingItems()` (`:42-50`) to completion in two `launch`es, each wrapped so a failure is logged and dropped (rethrow `CancellationException`). It holds no rows: joining happens in the shared `ContentCacheRepository`.
- Modify: `domain/di/modules.kt` - in `interactorModule` (`:31`), after `DetailsPrefetcher` (`:71-76`): `single { AppCoroutineScope() }`; `single { AppStartup(domainInteractor = { get() }, preferences = get(), homeInteractor = { HomeInteractor(get(), get(), get(), get(), get(), get(), get()) }, scope = get<AppCoroutineScope>()) }`. All seven `HomeInteractor` dependencies (`HomeInteractor.kt:21-29`) are global singles (`data/di/modules.kt:62-71`, `:90`, `:101`, `:109`; `domain/di/modules.kt:38-39`, `:68`). Not called from anywhere yet.
- Test: `test/core/startup/AppStartupTest.kt` (new)

**Consumes:** `ApiDomainInteractor.initialize`/`autoResolveWorkingDomain` (Task 5 lock), `HomeInteractor`.
**Produces:**
```kotlin
internal class AppStartup(
    domainInteractor: () -> ApiDomainInteractor,
    preferences: ICryptoPreferenceRepository,   // construction is lazy: CryptoPreferenceRepository.kt:33-35
    homeInteractor: () -> HomeInteractor,
    scope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    fun start()
    suspend fun awaitDomainInitialized()
}
```

Fixture for the warm-up rows: real `HomeInteractor` over `ContentCacheRepository(FakePayloadStore())` (`test/util/FakePayloadStore.kt:7`) and a relaxed `KinoPubApiClient` mock, built as in `test/domain/interactor/home/HomeInteractorCacheTest.kt:30-48` with `allowAnime()` (`:189-198`); `ApiDomainInteractor` is a mock with `autoResolveWorkingDomain()` returning `Success(changed = false)`; `scope = backgroundScope`, `ioDispatcher = StandardTestDispatcher(testScheduler)` unless the row says otherwise. Endpoints counted: `api.getItemsByShortcut("hot", type = "movie")`, `("hot", type = "serial")`, `api.getWatchingList(onlySubscribed = true)`.

- [x] Write red: `AppStartupTest.start_appliesTheSavedDomainOffTheMainThread` (`ioDispatcher` = single-thread executor named `startup-io`; mocked `initialize()` records the thread name and completes a `CompletableDeferred`; name is `startup-io`; executor closed in `finally`), `AppStartupTest.warmUpStartsHotAndWatchingWithoutAnyScreen` (token present, `start()`, `advanceUntilIdle()`: each endpoint called once), `AppStartupTest.warmUpAndHomeShareOneRequestPerEndpoint` (endpoints gated on a `CompletableDeferred`; after `start()` and `runCurrent()` a second `HomeInteractor` over the same `contentCache` collects both flows in `backgroundScope`; open the gate; `advanceUntilIdle()`: each endpoint `coVerify(exactly = 1)` and the second collector received the values), `AppStartupTest.aWarmUpLandingAfterAStoreWipeIsNotServedToHome` (first `getWatchingList` gated and answers item 1, later calls item 2; `start()`, `runCurrent()`, `contentCache.clear()`, open the gate, `advanceUntilIdle()`; a new collector's last value is item 2).
- [x] Add P1: `AppStartupTest.awaitDomainInitialized_completesWhenInitializeThrows`, `AppStartupTest.warmUpRequestsNothingWithoutAnAccessToken` (no token: `autoResolveWorkingDomain` and all endpoints never called).
- [x] Implement; keep guards `HomeVMIncrementalPublishTest.aCacheWipeFromAnotherScreenDropsTheRowsFromThePreviousCatalogue` (`test/ui/feature/home/vm/HomeVMIncrementalPublishTest.kt:265`) and `CachedFeedTest.aLoaderThatLandsAfterAWipeCannotRefillTheMemoryTier` (`test/data/cache/CachedFeedTest.kt:726`) green.
- [x] Fast gate; `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.core.startup.*' --tests 'com.kino.puber.domain.*' --tests 'com.kino.puber.data.cache.*' --tests 'com.kino.puber.ui.feature.home.*'` (`domain.*` covers `test/domain/di/DetailsPrefetcherWiringTest.kt`); commit "Add an application scope and a startup owner that applies the saved domain and warms the Home feeds".

**Done when:** the test command exits 0. If `warmUpAndHomeShareOneRequestPerEndpoint` shows two requests, stop and report under **Blocked**: the join premise (`data/cache/CachedFeed.kt:495-499` -> `core/collections/TypedTtlCache.kt:84-101`) does not hold and the design needs revisiting.

---

### Task 7: Compose the app under a fading splash overlay and drop D-pad and OK while it covers

**Covers:** AC-4, AC-5, AC-7, AC-8, AC-10, AC-12
**Files:**
- Create: `ui/feature/root/component/StartupSplashHost.kt` - `internal const val STARTUP_SPLASH_TAG = "startup_splash"`, `internal const val REVEAL_FADE_MS = 250`; `StartupSplashHost`: `Box(fillMaxSize)` { `content()`; unless `splashGone` (`remember`): `val alpha = animateFloatAsState(if (covered) 1f else 0f, tween(REVEAL_FADE_MS, easing = FastOutLinearInEasing), finishedListener = { if (it == 0f) splashGone = true })` and `SplashContent(Modifier.testTag(STARTUP_SPLASH_TAG).graphicsLayer { this.alpha = alpha.value })` }. Alpha is read in the layer block, not in composition; the overlay is not focusable and holds no `FocusRequester`. `SplashContent` (`SplashContent.kt:40`) is unchanged.
- Modify: `MainActivity.kt`:
  - field `private val coveredKeys = CoveredKeyFilter()`; `dispatchKeyEvent` (`:51-54`) passes `{ if (coveredKeys.shouldDrop(StartupReveal.isCovered, event.action, event.keyCode)) true else super.dispatchKeyEvent(event) }` as `superDispatch`; the probe lambda is unchanged, so dropped keys are still probed with `handled = true`.
  - `onCreate` (`:56`): `StartupReveal.begin()` before `setContent`; inside `AppLanguageProvider` (`:62`): `val covered by StartupReveal.covered.collectAsState()`; `StartupSplashHost(covered)` wraps the existing `appComposed` block (`:67-72`); the standalone `SplashContent()` at `:65` is removed; `LaunchedEffect(Unit) { delay(StartupReveal.remainingCapMs()); StartupReveal.onCap() }`; `LaunchedEffect(covered) { if (!covered) Telemetry.startupMarks?.markRevealed() }` (fade start, UX-Q2).
- Modify: `ui/feature/root/component/App.kt:179` - `UpdatePromptHost(onOpenChange = { open -> updatePromptOpen = open; if (open) StartupReveal.markReady() })` (an open prompt closes the gate, so Home's claim cannot run: `HomeScreenContent.kt:197`).
- Test: `androidTest/ui/feature/root/component/StartupSplashHostTest.kt` (new), `androidTest/ui/feature/update/component/UpdatePromptOverlayFocusTest.kt`

**Consumes:** `StartupReveal`, `CoveredKeyFilter` (Task 2), `StartupMarks.markRevealed` (Task 1).
**Produces:**
```kotlin
@Composable internal fun StartupSplashHost(covered: Boolean, content: @Composable () -> Unit)
```

- [x] Write `StartupSplashHostTest`: P0 red `contentUnderTheSplashTakesLaunchFocus` (covered host over a `Box` with `rememberFocusRequesterOnLaunch()` + `focusable()` + test tag; after `mainClock.advanceTimeBy(500)` that box is focused), P0 red `splashLeavesCompositionAfterTheFade` (`covered` turned false, `advanceTimeBy(REVEAL_FADE_MS + 32L)`: `onNodeWithTag(STARTUP_SPLASH_TAG).assertDoesNotExist()`), P1 `splashStaysWhileCovered` (covered, `advanceTimeBy(3_000)`: tag exists), P1 `splashIsDrawnInTheFirstFrame` (`autoAdvance = false` before `setContent`: tag exists at frame 0).
- [x] Write P0 red `UpdatePromptOverlayFocusTest.updatePromptOpenedUnderTheSplashHasFocusOnUpdateAfterReveal`: `StartupSplashHost(covered)` over `Box { ContentFocusGate(active = state == Hidden) { launch-focus box }; UpdatePromptOverlay(state, onAction = { actions += it }) }` (pattern `:76-104`, fixture `available` `:62-74`); set `state = available` while covered, then `covered = false`, advance past the fade; `assertFocusOn(updateText)` (`:256-258`); Back via `InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)` (pattern `androidTest/ui/feature/favorites/content/FavoriteScreenContentFocusTest.kt:167-169`) records `UpdatePromptAction.DismissClicked` (`TvDialogOverlay` `BackHandler`, `UpdatePromptOverlay.kt:70`).
- [x] Implement; fast gate; `./tools/agentw :app:compileDevDebugAndroidTestKotlin`; `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.core.*'`; run `com.kino.puber.ui.feature.root.component.StartupSplashHostTest` and `com.kino.puber.ui.feature.update.component.UpdatePromptOverlayFocusTest` under a lease; commit "Compose the app under a fading splash and drop D-pad and OK while it covers the screen".

**Done when:** compile and unit commands exit 0; both instrumented classes pass (or **Blocked**); `rg -n 'SplashContent\(' app/src/main/java/com/kino/puber/MainActivity.kt` finds nothing.

---

### Task 8: Route the launcher without a dwell after the domain is applied, and start the warm-up at process start

**Covers:** AC-1, AC-2, AC-3, AC-14
**Files:**
- Modify: `ui/feature/root/vm/LauncherVM.kt` - constructor (`:11-15`) gains `private val appStartup: AppStartup, private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO`; `handleAction` override (`:24-29`) removed; `override fun onStart() { launch { appStartup.awaitDomainInitialized(); val token = withContext(ioDispatcher) { cryptoPreferenceRepository.getAccessToken() }; openStartScreen(!token.isNullOrEmpty()) } }`; `openStartScreen` (`:31-52`) takes `isAuthenticated` instead of reading at `:35`, keeps the `startScreenOpened` guard and the three outcomes.
- Delete: `ui/feature/root/model/LauncherAction.kt`.
- Modify: `ui/feature/root/component/LauncherScreen.kt` - delete `MIN_VISIBLE_MS` (`:19`), the `LaunchedEffect`/`delay` and `SplashContent()` (`:40-45`) and the KDoc (`:31-34`); `viewModelOf(::LauncherVM)` (`:27`) becomes `viewModel { LauncherVM(get(), get(), get(), get()) }` (`org.koin.core.module.dsl.viewModel`, as `HomeScreen.kt:19`); `Content()` keeps only `puberViewModel` + `collectViewState()`.
- Modify: `PuberApp.kt:91` - `koinApplication.koin.get<AppStartup>().start()`; drop the `ApiDomainInteractor` import (`:33`).
- Test: `test/ui/feature/root/vm/LauncherVMTest.kt` - delete `start_keepsSplashOnScreenUntilItsAnimationHasPlayed` (`:29-38`); rewrite `splashShown_*` (`:40-81`) into the rows below; helpers `router()`/`preferences()`/`coordinator()` (`:83-95`) stay; add `startup(gate: CompletableDeferred<Unit> = CompletableDeferred(Unit))` = mockk `AppStartup` with `coEvery { awaitDomainInitialized() } coAnswers { gate.await() }`; pass `ioDispatcher = dispatcher` (`:22`) and `advanceUntilIdle()` after `testOnStart()` unless the row says otherwise.

**Consumes:** `AppStartup` (Task 6).
**Produces:** nothing new.

- [x] Write red: `LauncherVMTest.start_opensMainScreenWithoutASplashAction` (no action sent: Main opened), `LauncherVMTest.start_readsTheAccessTokenOnTheIoDispatcher` (`ioDispatcher` = single-thread executor named `launcher-io`; `getAccessToken()` records the thread name and completes a `CompletableDeferred` the test awaits; name is `launcher-io`), `LauncherVMTest.start_waitsForDomainInitBeforeReadingTheToken` (gate held: `getAccessToken` and `newRootScreen` never called; gate completed: Main opened).
- [x] Write guards: `start_opensMainScreenForAuthenticatedSession`, `start_opensAuthScreenWithoutAccessToken`, `start_opensMainAndTheContentLinkTarget_whenALinkIsPending` (accept a link on `coordinator()` before start; `newRootScreens(listOf(main, target))`), and P1 `startTwice_opensStartScreenOnlyOnce` (two `testOnStart()`: one route).
- [x] Implement; fast gate; `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.ui.feature.root.*'`; commit "Route the launcher as soon as the saved domain is applied and start the Home warm-up at process start".
- [x] Run the full gate; fix what it reports inside this task's commit (amend only before the commit is shared) or as one further commit "Fix full-gate findings of the startup splash change".

**Done when:** full gate exits 0; `rg -n 'MIN_VISIBLE_MS|LauncherAction|get<ApiDomainInteractor>\(\)\.initialize' app/src` finds nothing.

---

## Delta 1 (spec: Design, Architect delta 1; gate-1 delta in spec Decisions)

Constraints above hold. Line numbers in Tasks 9-10 are at `948a279a` (HEAD after Task 8 and its fixes), not at `a22c03bc`. Test design: spec, Architect delta 1, **Tests to add**. Device rows (M1 for amended AC-4, M3, M8, AC-13, AC-15) are qa-device's after Task 10.

### Task 9: Hold the reveal while images are in flight, latch it once revealed, and reveal at once for the update prompt

**Covers:** AC-4, AC-5, AC-6, AC-7, AC-10, AC-12 (as amended)
**Files:**
- Modify: `core/startup/RevealGate.kt:10-48` - replace the single `state` writes with inputs `begun`, `ready`, `forced`, `imagesBusy` and latch `revealedOnce`, all written under one `synchronized(lock)` that ends in `recompute()`: `val c = begun && !forced && !revealedOnce && !(ready && !imagesBusy)`; `if (!c && begun) revealedOnce = true`; `state.value = c`. `begin()` (`:21-24`) sets `begun`, clears `ready`, `forced`, `revealedOnce` and keeps `imagesBusy`; `markReady()` (`:26-28`) sets `ready`; `onCap()` (`:30-32`) and new `revealNow()` set `forced`; new `onImagesBusy(busy: Boolean)` sets `imagesBusy`; `reset()` (`:40-43`) clears every input including `imagesBusy`. `covered` (`:15`) and `remainingCapMs()` (`:34-38`) keep their signatures.
- Modify: `core/startup/StartupReveal.kt:15` - add `fun revealNow() = gate.revealNow()` next to `markReady`.
- Modify: `ui/feature/root/component/App.kt:185` - `if (open) StartupReveal.revealNow()`; adjust the comment at `:183-184` to "reveal at once, without waiting for content or images".
- Test: `test/core/startup/RevealGateTest.kt` - add six tests below; the existing six (`:12-63`) stay unchanged and green, `markReady_uncoversImmediately` (`:32-37`) being the AC-6 guard with no images in flight.

**Consumes:** nothing new. **Produces:** `RevealGate.onImagesBusy(Boolean)`, `RevealGate.revealNow()`, `StartupReveal.revealNow()` (Task 10 feeds `onImagesBusy`).

| Test | Steps | Red on the inert stubs |
|---|---|---|
| `readyWaitsForImagesInFlight` | `begin()`, `onImagesBusy(true)`, `markReady()`: covered | red |
| `imagesSettlingAfterReadyUncovers` | as above, assert covered, then `onImagesBusy(false)`: not covered | red (first assert) |
| `capUncoversWhileImagesInFlight` | `begin()`, `onImagesBusy(true)`, `onCap()`: not covered | guard |
| `revealNowIgnoresContentAndImages` | `begin()`, `onImagesBusy(true)`, `revealNow()` with no `markReady()`: not covered | red |
| `imagesBusyAfterRevealDoNotCoverAgain` | `begin()`, `markReady()`, then `onImagesBusy(true)`, `(false)`, `(true)`: not covered after each | guard (fails on an implementation without the latch) |
| `beginKeepsImagesBusyFromBeforeIt` | `onImagesBusy(true)`, `begin()`, `markReady()`: covered; `onImagesBusy(false)`: not covered | red |

- [x] Add `onImagesBusy` and `revealNow` with inert bodies; write the six tests; run `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.core.startup.RevealGateTest'`; record each red under **Red run**.
- [x] Implement `RevealGate`, `StartupReveal.revealNow`, `App.kt:185`; fast gate; `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.core.startup.*' --tests 'com.kino.puber.core.ui.PuberVMRevealTest' --tests 'com.kino.puber.ui.feature.home.vm.HomeVMTest'`; commit "Hold the startup reveal while images are in flight and let the update prompt reveal it at once".

**Done when:** the test command above exits 0; `rg -n 'StartupReveal.markReady' app/src/main/java/com/kino/puber/ui/feature/root/component/App.kt` finds nothing.

### Task 10: Report the image loader's requests in flight to the reveal gate

**Covers:** AC-4, AC-5, AC-6 (as amended), AC-14
**Files:**
- Create: `core/startup/StartupImageActivity.kt` - `internal class StartupImageActivity(private val onBusyChanged: (Boolean) -> Unit) : coil3.EventListener()` (public no-arg constructor in coil-core 3.6.3). A `Collections.newSetFromMap(IdentityHashMap<ImageRequest, Boolean>())` guarded by `synchronized(lock)`; `resolveSizeEnd(request, size)` adds; `onSuccess(request, result)`, `onError(request, result)`, `onCancel(request)` remove; `onBusyChanged` is called inside the lock and only when the set turns non-empty or empty (removal of an absent request reports nothing). `fun reset()` empties the set without reporting.
- Modify: `core/startup/StartupReveal.kt` - `val imageActivity = StartupImageActivity(gate::onImagesBusy)` after `gate` (`:8`); `resetForTests()` (`:21-22`) calls `imageActivity.reset()` then `gate.reset()`.
- Modify: `PuberApp.kt:100-126` - in `newImageLoader`, `.eventListener(StartupReveal.imageActivity)` on the builder before `.build()` (`:125`).
- Test: `test/core/startup/StartupImageActivityTest.kt` (new) - requests are `mockk<ImageRequest>()` (distinct instances), results `mockk<SuccessResult>()` / `mockk<ErrorResult>()`, size `coil3.size.Size.ORIGINAL`; `onBusyChanged` appends to a `MutableList<Boolean>` the test asserts.

**Consumes:** `RevealGate.onImagesBusy` (Task 9). **Produces:** `StartupReveal.imageActivity`.

| Test | Steps and expected reports | Red on the inert stub |
|---|---|---|
| `requestIsInFlightFromResolvedSizeUntilSuccess` | `onStart(a)`: `[]`; `resolveSizeEnd(a)`: `[true]`; `onSuccess(a)`: `[true, false]` | red |
| `errorAndCancelEndARequest` | resolve `a`, `b`; `onError(a)`: `[true]`; `onCancel(b)`: `[true, false]` | red |
| `cancelWithoutResolvedSizeLeavesIdle` | `onStart(a)`, `onCancel(a)`, `onSuccess(b)` for an unresolved `b`: `[]` | guard |
| `busyIsReportedOnlyOnEdges` | resolve `a`, `b`, `c`: `[true]`; end `a`, `b`: `[true]`; end `c`: `[true, false]`; `onSuccess(c)` again: `[true, false]` | red |

- [x] Create `StartupImageActivity` with inert overrides; write the four tests; run `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.core.startup.StartupImageActivityTest'`; record reds under **Red run**.
- [x] Implement; wire `StartupReveal.imageActivity` and `PuberApp.newImageLoader`; fast gate; the Task 9 test command plus `StartupImageActivityTest`; commit "Report the image loader's requests in flight to the startup reveal gate".
- [x] Run the full gate; fix what it reports in this commit or one further commit "Fix full-gate findings of the startup reveal image delta".

**Done when:** full gate exits 0; `rg -n 'eventListener\(StartupReveal.imageActivity\)' app/src/main/java/com/kino/puber/PuberApp.kt` finds one line.

---

## Implementation record

<!-- owner: implementer -->

**Commits:** one per task (shas below), plus one gate-fix commit.

- 010e9649 Add the revealed startup mark and emit the startup line once first content and reveal are both in
- 7cf9ea52 Add the startup reveal gate and the filter for keys pressed under the splash
- 671f5c19 Lift the startup splash when a landing screen publishes its first content state
- 4e60ac69 Keep the splash over Home until its hero focus claim has settled
- 5e3b3cec Serialize domain auto-resolve so a concurrent caller reuses the probe in flight
- 7be6f0cf Add an application scope and a startup owner that applies the saved domain and warms the Home feeds
- 0e00f178 Compose the app under a fading splash and drop D-pad and OK while it covers the screen
- Fix full-gate findings of the startup splash change (HomeContent complexity: the settled report moved into `ReportLaunchFocusSettledOnce`; three long test lines)

**Red run** (P0 `red` tests on the unchanged code; production symbols were stubbed inert where the test needed them):

- Task 1: red: StartupMarksTest.lineWaitsForRevealedAndCarriesBothMarks - line emitted at first content (assertEquals(0, out.size) failed, StartupMarksTest.kt:33); red: oneLineWhenRevealedComesBeforeFirstContent - AssertionFailedError :53; red: TelemetryEncoderTest.startupWritesRevealed - NoSuchElementException (no "revealed" key); red: revealedKeepsTheFirstValue (P1) - AssertionFailedError :74; green on base: fallbackLineHasNoRevealedWhenItNeverCame - behaviour already holds with the inert stub (revealedMs null), treated as guard.
- Task 2: red: RevealGateTest.remainingCapMs_countsFromBegin - expected 2500 but was 0 (:16); cap_uncoversWithoutReady - covered not true after begin (:26); markReady_uncoversImmediately - :34; begin_coversAgainAfterAPreviousReveal - :52; StartupKeyFilterTest.dpadAndOkAreDroppedWhileCovered - key not dropped (:25); upOfAKeyPressedWhileCoveredIsDroppedAfterReveal - :56. green on base: StartupKeyFilterTest.backAndOtherKeysPass - the stub drops nothing, so it only guards the filter; DispatchWithProbeTest.aKeyDroppedBySuperIsStillProbed - dispatchWithProbe already probes whatever super returns (P1 guard).
- Task 3: red: PuberVMRevealTest.firstStartupContentStateRevealsTheSplash - still covered (:64); revealDoesNotDependOnTelemetryRecording - :73; loadingErrorAndOverlayStatesKeepTheSplash - still covered after the final content publish (:87); contentLinkTargetStatesRevealTheSplash - Details/Player Content not StartupContentState (:92). The keep-the-splash half of loadingErrorAndOverlay is green on base by construction; the test ends with a content publish so it fails on the unchanged code.
- Task 4: red: HomeVMTest.launchFocusSettled_revealsTheSplash - expected 1 but was 0 (:114); HomeVMTest.contentState_keepsTheSplashUntilLaunchFocusSettled - splash already lifted by the content state (:118); LaunchFocusSettledRuleTest.* (3 true-cases incl. the rowsHeldFocus term) - expected true (stub returned false); HomeLaunchFocusSettledTest.heroClaimSendsLaunchFocusSettledWithHeroFocused - expected:<[LaunchFocusSettled]> but was:<[]> (emulator-5554, effect disabled); P1 emptyHeroSendsLaunchFocusSettledWithFirstRowFocused - same failure; green on base: HomeLaunchFocusSettledTest.unsettledHeroSendsNothing (guard, nothing is sent without the effect).
- Task 5: red: ApiDomainInteractorTest.autoResolve_concurrentCallersShareOneProbe - probeCalls had two entries instead of listOf("service-kp.test") (:141), run on the unchanged interactor with a synchronized list.
- Task 6 (against the inert AppStartup stub): red: start_appliesTheSavedDomainOffTheMainThread - TimeoutCancellationException (initialize never called); warmUpStartsHotAndWatchingWithoutAnyScreen - Verification failed, getItemsByShortcut(hot, movie) was not called; warmUpAndHomeShareOneRequestPerEndpoint - Verification failed, no hot request; aWarmUpLandingAfterAStoreWipeIsNotServedToHome - NoSuchElementException (no value reached Home); P1 awaitDomainInitialized_waitsUntilTheDomainIsApplied (added) - AssertionFailedError. green on base: awaitDomainInitialized_completesWhenInitializeThrows and warmUpRequestsNothingWithoutAnAccessToken (the stub completes at once and requests nothing; P1 guards). The join premise holds: with the real AppStartup warmUpAndHomeShareOneRequestPerEndpoint sees exactly one request per endpoint.
- Task 7 (against an inert StartupSplashHost that draws only the content; emulator-5554): red: StartupSplashHostTest.contentUnderTheSplashTakesLaunchFocus, splashLeavesCompositionAfterTheFade, splashStaysWhileCovered, splashIsDrawnInTheFirstFrame - all "AssertionError: Failed: assertExists" on STARTUP_SPLASH_TAG; red: UpdatePromptOverlayFocusTest.updatePromptOpenedUnderTheSplashHasFocusOnUpdateAfterReveal - same assertExists failure. Note: without the splash the focus assertions alone would pass, so each test first asserts the splash exists while covered; that is what makes them discriminate.
- Task 8: red: all LauncherVMTest rows failed on the unchanged routing (it waited for LauncherAction.SplashShown): start_opensMainScreenWithoutASplashAction (:39), start_readsTheAccessTokenOnTheIoDispatcher (TimeoutCancellationException, token never read), start_waitsForDomainInitBeforeReadingTheToken (:76); guards start_opensMainScreenForAuthenticatedSession (:98), start_opensAuthScreenWithoutAccessToken (:112), start_opensMainAndTheContentLinkTarget_whenALinkIsPending (:126), startTwice_opensStartScreenOnlyOnce (:145) failed on base for the same reason (they no longer send the action), so none is green on base.

**Deviations from the plan:**

- Task 1: `StartupMarks` keeps `firstContentMs` as a field and `markFirstContent` no longer emits on its own, as planned; `fallbackLineHasNoRevealedWhenItNeverCame` was green on base (guard).
- Task 4: the `rowsHeldFocus` term is a pure function `isLaunchFocusSettled` in `HomeScreenContent.kt` with its own unit test `LaunchFocusSettledRuleTest` (readiness note 2); the effect lives in `ReportLaunchFocusSettledOnce` (detekt complexity).
- Task 5: the new test builds its own interactor with a synchronized probe list (readiness note 4) instead of changing the shared `probeCalls`.
- Task 6: fixture uses `FakePayloadStore` (`test/util`), not `InMemoryPayloadStore`: that class is private to `HomeInteractorCacheTest`, and `FakePayloadStore` has the same `clear()` generation semantics. The test scope is the `runTest` scope itself, not `backgroundScope`: `advanceUntilIdle` does not run background-scope tasks. Added P1 `awaitDomainInitialized_waitsUntilTheDomainIsApplied`. `ApiDomainInteractor.initialize()` is not a suspend function; `AppStartup` calls it on the IO dispatcher.
- Task 6 join premise holds: `warmUpAndHomeShareOneRequestPerEndpoint` sees one request per endpoint; no block.
- Task 7: the two instrumented tests first assert the splash exists while covered; without that they pass on an inert host and would not discriminate.
- Thread-name assertions use `startsWith` because coroutine debug mode appends ` @coroutine#N` to the thread name.

**Files touched outside the plan:** none beyond the plan's files (`StartupKeyFilterTest`, `LaunchFocusSettledRuleTest`, `PuberVMRevealTest`, `AppStartupTest` are new tests it names or the readiness notes ask for).

**For the reviewers:** `StartupKeyFilterTest.upOfAKeyPressedWhileCoveredIsDroppedAfterReveal` is added by the plan beyond test-design.md (AC-7, M1 "a detail screen opened by the dropped OK"). `CoveredKeyFilter` cannot see `repeatCount`, so it treats any `ACTION_DOWN` of a key it dropped, before that key's `ACTION_UP`, as a repeat; a lost `ACTION_UP` would drop the next fresh press of that key once. Check `MainActivity.dispatchKeyEvent` and `HomeScreenContent.kt` (`rowsHeldFocus` term). Instrumented runs on emulator-5554 under a lease (released): `HomeLaunchFocusSettledTest` 3/3, `HomeFocusTraversalTest` 4/4, `StartupSplashHostTest` 4/4, `UpdatePromptOverlayFocusTest` 8/8. Intermediate commits between Task 1 and Task 7 emit the `startup` line only at the 60 s fallback (readiness note 1).

**Blocked:** none.

## Readiness

Final judge, 2026-11-21. Inputs: spec and plan (worktree, plan untracked at HEAD 7ed16cdb), `test-design.md`, `state.md` Decisions. Citations read with `git show a22c03bc:<path>`.

**Verdict: READY**

## AC coverage

| AC | Plan task | Test design rows |
|---|---|---|
| AC-1 | 8 | `LauncherVMTest.start_opensMainScreenWithoutASplashAction`, M1 |
| AC-2 | 6, 8 | `LauncherVMTest.start_*`, `AppStartupTest.start_appliesTheSavedDomainOffTheMainThread` |
| AC-3 | 5, 6, 8 | `ApiDomainInteractorTest.autoResolve_concurrentCallersShareOneProbe`, `AppStartupTest.warmUp*`, M7 |
| AC-4 | 4, 7 | `HomeVMTest`, `HomeLaunchFocusSettledTest`, `StartupSplashHostTest.contentUnderTheSplashTakesLaunchFocus`, M1 |
| AC-5 | 2, 7 | `RevealGateTest.remainingCapMs_countsFromBegin`, `RevealGateTest.cap_uncoversWithoutReady`, M2 |
| AC-6 | 3 | `PuberVMRevealTest.*`, M3 |
| AC-7 | 2, 7 | `StartupKeyFilterTest.*`, `RevealGateTest.markReady_uncoversImmediately`, M1, M4 |
| AC-8 | 7 | `StartupSplashHostTest.splashLeavesCompositionAfterTheFade`, M6 |
| AC-9 | 1 | `StartupMarksTest.*`, `TelemetryEncoderTest.*`, M10 |
| AC-10 | 2, 7 | `RevealGateTest.begin_coversAgainAfterAPreviousReveal`, M5 |
| AC-11 | 6 | `AppStartupTest.aWarmUpLandingAfterAStoreWipeIsNotServedToHome` + two guards |
| AC-12 | 7 | `UpdatePromptOverlayFocusTest.updatePromptOpenedUnderTheSplashHasFocusOnUpdateAfterReveal`, M8 |
| AC-13 | qa-device after Task 8 (plan, Global constraints) | Perf track; target in spec Decisions (revealed <= 2502 ms, key_lat p95 < 199 ms) |
| AC-14 | 8 | full gate |

Every automated name in test-design.md appears in the plan (by class or inside its class's task). The plan adds `StartupKeyFilterTest.upOfAKeyPressedWhileCoveredIsDroppedAfterReveal`, accepted by the owner (state.md, Plan Q1).

## Citations sampled (at a22c03bc)

| Citation | Resolves to |
|---|---|
| `MainActivity.kt:51-54`, `:65` | `dispatchKeyEvent` via `dispatchWithProbe`; standalone `SplashContent()` |
| `LauncherScreen.kt:19`, `:27`, `:40-45` | `MIN_VISIBLE_MS`; `viewModelOf(::LauncherVM)`; `delay` + `SplashShown` + `SplashContent()` |
| `LauncherVM.kt:11-15`, `:24-29`, `:35` | 3-param constructor; `handleAction`; `getAccessToken()` on caller thread |
| `PuberApp.kt:33`, `:91` | `ApiDomainInteractor` import; `get<ApiDomainInteractor>().initialize()` |
| `PuberVM.kt:86-90` | `updateViewState` with `Telemetry.isEnabled` checks |
| `HomeVM.kt:47-58`, `:176-199`, `:273` | constructor; `handleAction` with `else` at 197; `autoResolveWorkingDomain()` |
| `HomeScreenContent.kt:181`, `:191-203`, `:319-345` | `rowsHeldFocus` (rememberSaveable); `heroFocusResolved` + `ClaimHeroFocusEffect` call; 5-attempt claim |
| `ApiDomainInteractor.kt:53-60`, `:117-145`, `:125`, `:177` | `initialize`; `autoResolveWorkingDomain`; `isKnownReachable`; `log(error, ...)` |
| `App.kt:179` | `UpdatePromptHost(onOpenChange = ...)` |
| `KeyProbe.kt:109-119` | `dispatchWithProbe` |
| `StartupMarks.kt:20-32`, `TelemetryEvent.kt:130-138`, `TelemetryEncoder.kt:67-72` | emit CAS; `Startup` data class; `putStartup` |
| `HeroCarousel.kt:182-196` | select acts on `KeyUp` |
| `CachedFeed.kt:495-499` -> `TypedTtlCache.kt:84-101` | `reload`/`getOrPut`; flight join via `Lookup.Wait` |
| `CryptoPreferenceRepository.kt:33-35` (`data/repository/`) | lazy `sharedPreferences` |
| `domain/di/modules.kt:31`, `:71-76`; `data/di/modules.kt:62-71`, `:90`, `:101`, `:109` | `interactorModule`; `DetailsPrefetcher`; `HomeInteractor` deps are singles |
| `DetailsScreenState.kt:14`, `PlayerViewState.kt:13`, `StartupContentState.kt:8` | `Content` classes; marker interface |
| Test anchors: `PuberVMTelemetryTest.kt:87-91`, `:133-178`, `:181`; `SectionVMTest.kt:196-226`; `LauncherVMTest.kt:22`, `:29-38`, `:83-95`; `HomeVMTest.kt:446-459`; `StartupMarksTest.kt:40`; `TelemetryEncoderTest.kt:128`; `HomeVMIncrementalPublishTest.kt:265`; `CachedFeedTest.kt:726`; `UpdatePromptOverlayFocusTest.kt:46-49`, `:62-104`, `:256-258`; `FavoriteScreenContentFocusTest.kt:167-169`; `HomeFocusTraversalTest.kt:332`, `:355-364` | all as claimed |

Inexact: Task 6 fixture says "as in `HomeInteractorCacheTest.kt:30-48`" with `FakePayloadStore`; that test uses `InMemoryPayloadStore()` (`:34`). `FakePayloadStore` exists (`test/util/FakePayloadStore.kt:7`), so the plan builds.

## Build and order

Dependencies hold: T3 needs T2; T4 needs T3; T6 needs T5; T7 needs T1, T2, T4; T8 needs T6. Each task compiles and keeps its tests green; Task 1 lists every existing test that asserts emission at `first_content` (`PuberVMTelemetryTest` x3, `SectionVMTest`); `StartupMarksTest.oneLineWithAscendingMarksOnFirstContent` calls `onTimeout()` and still passes.

## Notes for implementation and review (non-blocking)

| # | Note |
|---|---|
| 1 | Between Task 1 and Task 7 no code calls `markRevealed()`, so a build from those commits emits the `startup` line only at the 60 s fallback (`TelemetryRuntime.kt:55`). Do not measure intermediate commits. |
| 2 | The `rowsHeldFocus` term the plan adds to Home's settled condition (Task 4, beyond the spec's rule) has no test; a recreated Home falls back to the cap if it is wrong. Reviewers check it; M5 observes it. |
| 3 | Device P0 rows (M1, M2, M4, M5, Perf track) carry no red/guard mark; they are manual by nature and name their expected and forbidden outcomes. |
| 4 | `autoResolve_concurrentCallersShareOneProbe`: `probeCalls` (`ApiDomainInteractorTest.kt:33`) is a plain list appended from IO threads; the red run without the `Mutex` may race. Use a synchronized list in the new test. |

## Open questions

None blocking: spec Open questions all answered; state.md Open questions empty.

## Implementation record (delta 1)

<!-- owner: implementer -->

**Commits:**

- 51352276 Hold the startup reveal while images are in flight and let the update prompt reveal it at once
- 952c26f2 Report the image loader's requests in flight to the startup reveal gate

**Red run** (inert stubs of `onImagesBusy`, `revealNow` and the `StartupImageActivity` overrides, tests on them):

- Task 9: red: RevealGateTest.readyWaitsForImagesInFlight - AssertionFailedError at :61 (uncovered by markReady); red: imagesSettlingAfterReadyUncovers - :69 (first assert); red: revealNowIgnoresContentAndImages - :87; red: beginKeepsImagesBusyFromBeforeIt - :107; green on base: capUncoversWhileImagesInFlight and imagesBusyAfterRevealDoNotCoverAgain - guards as the plan marks them (inert stubs also pass them).
- Task 10: red: StartupImageActivityTest.requestIsInFlightFromResolvedSizeUntilSuccess - :23; errorAndCancelEndARequest - :35; busyIsReportedOnlyOnEdges - :56; green on base: cancelWithoutResolvedSizeLeavesIdle - guard.

**Deviations:** none. `RevealGate.update` is a private inline helper that runs each write and `recompute` under one lock; `reset()` also clears `revealedOnce`.

**Files outside the plan:** none.

**Instrumented runs:** none needed by Tasks 9-10 (unit rows only). Device rows (M1 amended, M3, M8, AC-13, AC-15) not run: emulator-5554 holds another run's APK with Room schema v4 and this branch is v3, so an install would be a downgrade; qa-device decides.

**Check:** `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` - BUILD SUCCESSFUL in 6m 2s.

**For the reviewers:** the `ready` flag is not cleared by `onImagesBusy`, so images that settle before `markReady()` do not reveal on their own (content readiness is still required); `imagesBusy` survives `begin()` and is cleared only by `reset()`. Check that no request in the app's flow ends without a terminal event while holding `resolveSizeEnd` (it would hold the splash to the cap).

## AC coverage (delta 1)

| AC | Plan task | Test design rows (spec, Architect delta 1, Tests to add) |
|---|---|---|
| AC-4 (amended) | 9, 10 | `RevealGateTest.readyWaitsForImagesInFlight`, `.imagesSettlingAfterReadyUncovers`, `.imagesBusyAfterRevealDoNotCoverAgain`; `StartupImageActivityTest.requestIsInFlightFromResolvedSizeUntilSuccess`, `.errorAndCancelEndARequest`, `.busyIsReportedOnlyOnEdges`; M1 frame by frame |
| AC-5 | 9, 10 | `RevealGateTest.capUncoversWhileImagesInFlight`; `StartupImageActivityTest.errorAndCancelEndARequest`, `.cancelWithoutResolvedSizeLeavesIdle`; M2 |
| AC-6 (amended) | 9, 10 | `RevealGateTest.readyWaitsForImagesInFlight`, `.imagesSettlingAfterReadyUncovers`, existing `.markReady_uncoversImmediately`; M3 |
| AC-7 | 9 | `RevealGateTest.imagesBusyAfterRevealDoNotCoverAgain` |
| AC-10 | 9 | `RevealGateTest.beginKeepsImagesBusyFromBeforeIt`; M5 |
| AC-12 (amended) | 9 | `RevealGateTest.revealNowIgnoresContentAndImages`; M8 |
| AC-13 | qa-device after Task 10 | Perf track, measured together with AC-15 |
| AC-14 | 10 | full gate |
| AC-15 (new) | qa-device after Task 10 | Fixed-anchor interleaved A/B and one trace (spec, AC delta) |

## Readiness (delta 1)

Final judge, 2026-11-21. Scope: plan Tasks 9-10 against spec `Architect delta 1` and the gate-1 delta decision (spec Decisions, state.md). Tasks 1-8: `readiness-1.md`. Citations read with `git show 948a279a:<path>`; Coil API read with `javap` on `coil-core-api.jar` 3.6.3.

**Verdict: READY**

## AC coverage

The plan's `AC coverage (delta 1)` table holds: every amended or new AC (AC-4, AC-5, AC-6, AC-7, AC-10, AC-12, AC-13, AC-14, AC-15) maps to Task 9, Task 10 or qa-device after Task 10. All ten names in spec `Tests to add` appear in the Task 9 and Task 10 tables, each marked `red` or `guard` with steps that state the defect (missing latch, inert `onImagesBusy`, edge reporting, unbalanced removal).

## Citations sampled (at 948a279a)

| Citation | Resolves to |
|---|---|
| `core/startup/RevealGate.kt:10-48`, `:15`, `:21-24`, `:26-28`, `:30-32`, `:34-38`, `:40-43` | class; `covered`; `begin`; `markReady`; `onCap`; `remainingCapMs`; `reset` |
| `core/startup/StartupReveal.kt:8`, `:15`, `:21-22` | `gate`; `markReady`; `resetForTests` |
| `ui/feature/root/component/App.kt:183-185` | prompt comment; `if (open) StartupReveal.markReady()` |
| `PuberApp.kt:100-126`, `:125` | `newImageLoader`; `.build()` |
| `test/.../core/startup/RevealGateTest.kt:12-63`, `:32-37` | six existing tests; `markReady_uncoversImmediately` |
| `MainActivity.kt:62`, `:76-84` | covered-key drop; `begin()` and cap effect |
| `core/ui/PuberVM.kt:94` | `StartupReveal.markReady()` on `StartupContentState` |
| coil3 `EventListener` | abstract class, public no-arg constructor; `resolveSizeEnd(ImageRequest, Size)`, `onSuccess`, `onError`, `onCancel(ImageRequest)`; `ImageLoader.Builder.eventListener(EventListener)`; `Size.ORIGINAL` |

`mockk` is on `testImplementation` (`app/build.gradle.kts:421`), so the `mockk<ImageRequest>()` fixtures build.

## Build and order

Task 9 compiles alone (`revealNow` and its `App.kt` caller land together); Task 10 consumes `RevealGate.onImagesBusy` from Task 9. The existing `RevealGateTest` rows stay green under the new rule: `markReady_doesNothingBeforeBegin` and `reset_uncoversAndForgetsTheStart` hold because `covered` needs `begun` and `reset()` clears it. `PuberVMRevealTest` and `HomeVMTest` share the global `StartupReveal`; `resetForTests()` clearing `imagesBusy` and the in-flight set (Task 10) keeps them independent.

## Notes for implementation and qa (non-blocking)

| # | Note |
|---|---|
| 1 | AC-15 and the AC-4/6/12 amendments live only in the spec `AC delta (gate 1)` table; `brief.md` and the spec AC list still show the old text. The gate reads the delta table as the AC of record. |
| 2 | Device rows (M1 amended, M3, M8, AC-13, AC-15) need emulator-5554 or another AVD; state.md Log records a Room v4 install there, so a head install would be a schema downgrade. qa-device checks the on-device DB version first and asks the owner before installing over v4. |
| 3 | A Home that keeps issuing image requests (pager or LazyRow prefetch) holds `imagesBusy` until the cap; AC-13 `revealed` median is the check, and the evidence reports how many starts revealed at the cap. |

## Open questions

None blocking: delta Q1-Q3 answered (spec Decisions, 2026-11-21 gate 1 delta, delta Q2, delta Q3).

### Fix round 1

Rebase onto master 8428c946 (cache-storage-bounds): old head 4d159b7f, new head 8f8793d3. One conflict, in PuberApp.kt: kept `AppStartup.start()` (it calls `ApiDomainInteractor.initialize()`) plus `CachedPayloadSweeper.start()` and the memory-cache cap next to our event listener. check.fast green after the rebase.

| ID | Fix | Commit | Red run |
|----|-----|--------|---------|
| T1 | `StartupReveal.onActivityCreated(restored)`: a restored MainActivity reveals at once instead of calling begin() | 5ee1bd54 | red: onActivityCreated_restoredActivityDoesNotCoverAgainAfterAReveal and ..NeverCoversInAFreshProcess - covered stayed true with begin() on restore |
| T2 + T6 | One in-flight walk shared by concurrent callers, NotFound included; the test rendezvous is a latch with undispatched callers, no delay | c4b131cc | red: autoResolve_concurrentCallersShareAWalkThatFoundNothing - the second caller probed again |
| T3 | `domainVersion` bumped by user domain changes; a walk whose version moved drops its write | 28f288f0 | red: autoResolve_doesNotOverwriteADomainSetWhileItWalks - the walk saved api.alador.test over the custom domain |
| T4 | `RevealGate.markReady` takes effect after two drawn frames (Choreographer in production, a seam in tests); a mark from before a later begin/reset is dropped | b4448e0d | compile-level red only: the `afterFrame` seam did not exist, so the new gate tests could not run on the base |
| T5 | `StartupReveal.onUpdatePromptOpenChanged` extracted from App.kt and tested | 29dd34c9 | green on base: new test over an extracted function, behaviour unchanged |
| T7 | `ImageLoader.Builder.reportingToStartupReveal()` extracted and tested with a mocked builder; PuberApp calls it | 2118f95c | green on base: extraction, behaviour unchanged |
| - | detekt fix of the T2/T3 code (no catch of cancellation, return count) | 522fa7be | n/a |

Deviations: T4 waits two frames, not one (composition and layout in the first, the requests they start in the second). T7 verifies the extension, not `newImageLoader` itself (no Robolectric); the wiring is covered by the device run. T2/T3 detekt follow-up is a separate commit for the conductor to squash. check.full: BUILD SUCCESSFUL (testDevDebugUnitTest, detektAll, lintDevDebug).
Reviewers: the join/owner loop in `ApiDomainInteractor.autoResolveWorkingDomain`; the generation check in `RevealGate.markReady`; `StartupReveal.resetForTests` now also installs an immediate frame hook.

### Fix round 2

| ID | Fix | Commit | Red run |
|----|-----|--------|---------|
| T8 + T10 | `autoResolveWorkingDomain` empties `inFlightWalk` (only if it is still this walk) before it completes or cancels the walk, so a joiner resumed inline does not re-join the dead walk | c2acd654 | red: autoResolve_joinerWalksItselfWhenTheOwnerFailsAndAThirdCallWalksAgain - timed out, the joiner spun on the cancelled walk (an immediate main-like dispatcher is needed; Unconfined and a plain inline dispatcher queue the resume and hide the bug) |
| T9 | `StartupReveal` frame hook defaults to immediate; `installFrameWait()` installs the Choreographer two-frame wait and is called from `PuberApp.onCreate` | 313a0434 | red: PuberVMTelemetryTest and SectionVMTest run alone - RuntimeException from the Looper stub (5+ tests failed); green after |

Deviations: the T8 test needed a detekt follow-up (explicit Unit return types) squashed into the T8 commit. check.full: BUILD SUCCESSFUL (testDevDebugUnitTest, detektAll, lintDevDebug).
Reviewers: the order in the owner's `finally` of `autoResolveWorkingDomain`; `ImmediateThreadDispatcher` in the test.
