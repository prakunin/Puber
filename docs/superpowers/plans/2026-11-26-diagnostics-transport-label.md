# Show the connection transport on the network diagnostics screen - plan

**Spec:** `docs/superpowers/specs/2026-11-26-diagnostics-transport-label.md`
**Goal:** The speed test screen shows one non-focusable line naming the active transport (Ethernet, Wi-Fi, cellular, other, none), read off the main thread on open and at every run start.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`; full gate: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`. Both run inside the worktree `/home/dev/orca/workspaces/Puber/diagnostics-transport-label`; never `./gradlew` there; prefix `AGENT_GRADLE_SERIAL=1` when another worktree may build.
- One task = green fast gate + green tests of the touched code + one commit, message one imperative English sentence, no prefixes, never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/`. Line numbers are at base 4c398e07.
- Source of the port: branch commits 3209128b (provider + test) and f037c052 (label). Take code from them with `git show <c> -- <path>` or `git diff <c>^ <c> -- app | git apply`, never `git cherry-pick` (conflicts on a plan file master lacks); then change it as each task says (`Unknown` is split into `Other` and `None` everywhere).
- Out of scope (spec): a live network callback, device checks on transports other than the box's Ethernet, a design stand. No global `ConnectivityManager` binding in `data/di/modules.kt`.
- Visual numbers are fixed by the spec's Visual section: the line copies the subtitle (`bodyMedium`, `onSurfaceVariant`, 4 dp gap), nothing else changes.

---

## Tasks

### Task 1: Add the connection transport provider

**Covers:** AC-1, AC-2, AC-3
**Files:**
- Create: `core/system/ConnectionTransportProvider.kt` - from 3209128b, enum becomes `Ethernet, Wifi, Cellular, Other, None`; both null returns give `None`, the `else` branch gives `Other`
- Test: `app/src/test/kotlin/com/kino/puber/core/system/ConnectionTransportProviderTest.kt` (new, from 3209128b)

**Consumes:** `android.net.ConnectivityManager.activeNetwork`, `getNetworkCapabilities(Network)`, `NetworkCapabilities.hasTransport`; MockK (as in 3209128b's test).
**Produces:**
```kotlin
enum class ConnectionTransport { Ethernet, Wifi, Cellular, Other, None }
class ConnectionTransportProvider(private val connectivityManager: ConnectivityManager) {
    fun current(): ConnectionTransport
}
```

- [x] Write the test file with these seven cases (3209128b's `givenCapabilities` helper kept; its two `transport_isUnknown_*` cases are renamed and re-targeted):

| Test | Setup | Expects | AC |
|---|---|---|---|
| `transport_isEthernet_whenEthernetAndWifiBothPresent` | ethernet, wifi | `Ethernet` | AC-1 |
| `transport_isWifi_whenOnlyWifi` | wifi | `Wifi` | AC-1 |
| `transport_isWifi_whenWifiAndCellularBothPresent` | wifi, cellular | `Wifi` | AC-1 |
| `transport_isCellular_whenOnlyCellular` | cellular | `Cellular` | AC-1 |
| `transport_isOther_whenNoMappedTransport` | capabilities present, all three `hasTransport` false | `Other` | AC-2 |
| `transport_isNone_whenActiveNetworkNull` | `activeNetwork` null | `None` | AC-3 |
| `transport_isNone_whenNoCapabilities` | `getNetworkCapabilities(network)` null | `None` | AC-3 |

- [x] Red run: with no provider (base) the test fails to compile; with 3209128b's provider applied unchanged it still fails to compile on `Other` / `None`. Record both in the Implementation record.
- [x] Write the provider (3209128b with the split).
- [x] Run the fast gate and `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.core.system.ConnectionTransportProviderTest'`; commit ("Add a connection transport provider").

**Done when:** the seven cases pass and the fast gate is green.

---

### Task 2: Read the transport in the diagnostics ViewModel

**Covers:** AC-4, AC-5 (plus risks R1, R5, R6 of the test design)
**Files:**
- Modify: `ui/feature/device/diagnostics/model/NetworkDiagnosticsViewState.kt:14-20` - add last field `val transport: ConnectionTransport? = null` to `NetworkDiagnosticsViewState` (class stays `@Immutable`)
- Modify: `ui/feature/device/diagnostics/vm/NetworkDiagnosticsVM.kt` - as in f037c052:
  - ctor `:16-20` gains `private val transportProvider: ConnectionTransportProvider` and `private val ioDispatcher: CoroutineDispatcher` (no default) after `interactor`;
  - new `private fun refreshTransport()`: `launch { try { val transport = withContext(ioDispatcher) { transportProvider.current() }; updateViewState(stateValue.copy(transport = transport)) } catch CancellationException rethrow; catch Throwable log(error, "Failed to read the connection transport for the speed test") }`, the same try/catch shape as `onStart` at `:28-35`. Read `stateValue` only after `withContext` returns (R5);
  - call it first in `onStart` (`:26`) and in `startRun` right after `if (stateValue.running) return` (`:47`).
- Modify: `ui/feature/device/diagnostics/NetworkDiagnosticsScreen.kt:23-26` - replace `viewModelOf(::NetworkDiagnosticsVM)` (`:25`) with f037c052's block: `scoped { ConnectionTransportProvider(androidContext().getSystemService(ConnectivityManager::class.java)) }` and `viewModel { NetworkDiagnosticsVM(interactor = get(), transportProvider = get(), errorHandler = get(), router = get(), ioDispatcher = Dispatchers.IO) }`; imports `org.koin.android.ext.koin.androidContext`, `org.koin.core.module.dsl.viewModel` (drop `viewModelOf` at `:13`), `android.net.ConnectivityManager`, `kotlinx.coroutines.Dispatchers`.
- Test: `app/src/test/kotlin/com/kino/puber/ui/feature/device/diagnostics/vm/NetworkDiagnosticsVMTest.kt`

**Consumes:** Task 1's `ConnectionTransport` and `ConnectionTransportProvider`; `PuberVM.launch` (`core/ui/PuberVM.kt:108`), `testOnStart` (`:146`), `testStateValue` (`:65`); `MainDispatcherExtension.dispatcher` (`app/src/test/kotlin/com/kino/puber/util/MainDispatcherExtension.kt`, an `UnconfinedTestDispatcher`). Injection precedent: `ui/feature/root/vm/LauncherVM.kt:18`.
**Produces:** `NetworkDiagnosticsViewState.transport: ConnectionTransport?`, null until the first read.

- [x] In the test, add `private val transportProvider = mockk<ConnectionTransportProvider> { every { current() } returns ConnectionTransport.Wifi }`; `vm()` (`:39-46`) takes `ioDispatcher: CoroutineDispatcher = mainDispatcher.dispatcher` and passes both. The three existing cases (`:53`, `:65`, `:91`) stay unchanged.
- [x] Write the seven new cases (Notes below), then run them red: they fail to compile without the field and ctor parameters.

| Test | AC / risk | Priority |
|---|---|---|
| `state_transportNull_beforeStart` | AC-4 | P1 |
| `onStart_setsTransportInState_whenProviderReturnsWifi` | AC-4 | P0 red |
| `onStart_readsTransportOnIoDispatcher_notOnMain` | AC-4 | P0 red |
| `startRun_rereadsTransport_whenNetworkChangedSinceStart` | AC-5 | P0 red |
| `start_doesNotRereadTransport_whileRunning` | AC-5 | P2 |
| `onStart_keepsCurrentServer_whenTransportArrivesAfterIt` | R5 | P1 |
| `onStart_keepsLoadingServer_whenTransportReadThrows` | R6 | P1 |

  Notes:
  - The first, second and fourth come from f037c052 as written (`startRun_rereads...` uses `returnsMany listOf(Ethernet, Wifi)`).
  - `onStart_readsTransportOnIoDispatcher_notOnMain`: `vm(ioDispatcher = StandardTestDispatcher(mainDispatcher.dispatcher.scheduler))`; after `testOnStart()`: `transport` null and `verify(exactly = 0) { transportProvider.current() }`; after `testScheduler.advanceUntilIdle()`: `transport == Wifi`.
  - `start_doesNotRereadTransport_whileRunning`: the `Channel` setup of `start_isIgnored_whileAnotherServerIsRunning` (`:92-104`); after the ignored second `Start`, `verify(exactly = 2) { transportProvider.current() }` (onStart + first Start).
  - `onStart_keepsCurrentServer_whenTransportArrivesAfterIt`: `StandardTestDispatcher` as above; `val gate = CompletableDeferred<SpeedTestServer>()`, `coEvery { interactor.currentServer() } coAnswers { gate.await() }`; `testOnStart()`, `gate.complete(Amsterdam)`, advance; assert `currentServer == Amsterdam` and `transport == Wifi`.
  - `onStart_keepsLoadingServer_whenTransportReadThrows`: `every { transportProvider.current() } throws IllegalStateException()`; assert `transport` null and `currentServer == Amsterdam`.
- [x] Implement ViewState, VM and Screen changes.
- [x] Run the fast gate and `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.ui.feature.device.diagnostics.vm.NetworkDiagnosticsVMTest'`; commit ("Read the connection transport in the network diagnostics view model").

**Done when:** all ten VMTest cases pass, the fast gate is green, and `grep -n viewModelOf app/src/main/java/com/kino/puber/ui/feature/device/diagnostics/NetworkDiagnosticsScreen.kt` prints nothing.

---

### Task 3: Show the transport line on the diagnostics screen

**Covers:** AC-6, AC-7 (AC-7 device part and AC-8 are settled by QA's M1-M3 and the owner, not by this task)
**Files:**
- Modify: `ui/feature/device/diagnostics/NetworkDiagnosticsContent.kt`
  - between the subtitle `Text` (`:142-146`) and `Spacer(modifier = Modifier.height(12.dp))` (`:147`), f037c052's block plus a tag: `state.transport?.let { transport -> Spacer(Modifier.height(4.dp)); Text(text = stringResource(R.string.diagnostics_transport_label, transport.label()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag(NetworkDiagnosticsTestTags.Transport)) }`. No `focusable`, `clickable` or `Surface`. `testTag` is already imported (`:40`);
  - after `SpeedTestServer.title()` (`:580-585`): `@Composable private fun ConnectionTransport.label(): String` mapping the five entries to the five value strings;
  - import `com.kino.puber.core.system.ConnectionTransport`.
- Modify: `ui/feature/device/diagnostics/NetworkDiagnosticsTestTags.kt:3-8` - add `const val Transport = "diagnostics-transport"`.
- Modify: `ui/feature/device/diagnostics/NetworkDiagnosticsPreview.kt:18-22` - the ready preview passes `transport = ConnectionTransport.Ethernet`; the running and recommendation previews stay null.
- Modify: `app/src/main/res/values/strings.xml` after `:53` (`diagnostics_screen_subtitle`) and `app/src/main/res/values-en/strings.xml` after `:397` (same key):

| Key | ru (`values`) | en (`values-en`) |
|---|---|---|
| `diagnostics_transport_label` | `Подключение: %1$s` | `Connection: %1$s` |
| `diagnostics_transport_ethernet` | `Ethernet` | `Ethernet` |
| `diagnostics_transport_wifi` | `Wi-Fi` | `Wi-Fi` |
| `diagnostics_transport_cellular` | `Мобильная сеть` | `Cellular` |
| `diagnostics_transport_other` | `Другое` | `Other` |
| `diagnostics_transport_none` | `Нет подключения` | `No connection` |

- Test: `app/src/androidTest/kotlin/com/kino/puber/ui/feature/device/diagnostics/NetworkDiagnosticsContentFocusTest.kt`

**Consumes:** Task 2's `NetworkDiagnosticsViewState.transport`; in the test, `setContent` (`:174`), `awaitFocus` (`:183`).
**Produces:** tag `NetworkDiagnosticsTestTags.Transport`.

- [x] Add two cases; the six existing cases (`:51`, `:66`, `:103`, `:125`, `:143`, `:160`) stay unchanged and guard first focus (AC-7):

| Test | Steps | AC | Priority |
|---|---|---|---|
| `transportLine_isAbsent_whenTransportNull` | `setContent(NetworkDiagnosticsViewState())`; `awaitFocus` Amsterdam; `onAllNodesWithTag(Transport).assertCountEquals(0)` | AC-6 | P1 |
| `transportLine_isShownAndNotFocused_whenEthernet` | `setContent(NetworkDiagnosticsViewState(transport = Ethernet))`; `awaitFocus` Amsterdam; Transport node `assertExists()` and `assertIsNotFocused()`; Amsterdam `assertIsFocused()` | AC-7 | P0 red |

- [x] Implement content, tag, preview and strings.
- [x] Run the fast gate, `./tools/agentw :app:compileDevDebugAndroidTestKotlin`, then the full gate; commit ("Show the connection transport on the network diagnostics screen").

**Done when:** fast gate, androidTest compile and full gate are green. `NetworkDiagnosticsContentFocusTest` on the box (`make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.ui.feature.device.diagnostics.NetworkDiagnosticsContentFocusTest`, eight cases) and manual checks M1-M3 (spec Test design) run in verification under a lease, by qa-device.

---

## Implementation record

<!-- owner: implementer -->

**Commits:**

- Add a connection transport provider
- Read the connection transport in the network diagnostics view model
- Show the connection transport on the network diagnostics screen

**Red run** (P0 `red` tests on the unchanged code):

- red: the seven `ConnectionTransportProviderTest` cases - at base the test does not compile (`Unresolved reference 'ConnectionTransport'`); with 3209128b's provider applied unchanged it still does not compile (`Unresolved reference 'Other'` / `'None'`)
- red: `onStart_setsTransportInState_whenProviderReturnsWifi` - `expected: <Wifi> but was: <null>` (field, ctor parameters and a no-op `refreshTransport` stubbed so the test runs)
- red: `startRun_rereadsTransport_whenNetworkChangedSinceStart` - `expected: <Ethernet> but was: <null>` (same stub)
- red: `onStart_readsTransportOnIoDispatcher_notOnMain` - `expected: <Wifi> but was: <null>` (same stub; the on-main discrimination was not mutation-checked)
- also red on the stub: `onStart_keepsCurrentServer_whenTransportArrivesAfterIt` (Wifi null), `start_doesNotRereadTransport_whileRunning` (`current()` was not called)
- `transportLine_isShownAndNotFocused_whenEthernet`: instrumented, not run in this stage (no device); red at base by construction (the `Transport` tag does not exist)

**Deviations from the plan:**

- Red run for the VM tests used a stub instead of a compile failure, as the implementer rules ask.
- `NetworkDiagnosticsVM` ctor parameter order is `interactor, transportProvider, ioDispatcher, errorHandler, router` (spec plan said both after `interactor`); the Koin block uses named arguments.

- QA defect 1: `transportLine_isShownAndNotFocused_whenEthernet` used `assertIsNotFocused()` on a non-focusable `Text` (no `Focused` key, so it throws); it now asserts `Focused != true` and no `RequestFocus` action on the node.

**Fix round 1** (triage-1 #1-#3; `buildModule` is now `internal`, `@Suppress("unused")` kept: detekt flags the unused `parentScope` otherwise):

- #1 fixed (Resolve the diagnostics view model from the screen module in a JVM test): `NetworkDiagnosticsScreenModuleTest` loads the screen module with a mocked interactor, ErrorHandler, AppRouter and `androidContext` and resolves `NetworkDiagnosticsVM` from the screen scope. red: with the scoped `ConnectionTransportProvider` binding removed it fails with `InstanceCreationException` caused by `NoDefinitionFoundException`.
- #2 fixed (same commit as #3's test file edit): `onStart_keepsLoadingServer_whenTransportReadThrows` also verifies `errorHandler.proceedInvoke` is never called.
- #3 fixed (Cancel the previous transport read before starting a new one): `transportJob` is cancelled before each `refreshTransport`. `startRun_keepsNewerTransport_whenAnOlderReadLandsLast` holds both reads on a capturing dispatcher and releases the newer first. red: without the cancel it fails on the assertion (Wifi expected, Ethernet landed last); green with it.

**Files touched outside the plan:**

None.

**For the reviewers:**

- `NetworkDiagnosticsScreen.kt` Koin block: resolved by `NetworkDiagnosticsScreenModuleTest` since fix round 1 (R1); device check M1 still stands for the on-screen look.
- `refreshTransport` reads `stateValue` after `withContext` returns (R5).
- `check.full` (testDevDebugUnitTest, detektAll, lintDevDebug) and `compileDevDebugAndroidTestKotlin`: BUILD SUCCESSFUL. `NetworkDiagnosticsContentFocusTest` not run (device is qa's).

**Blocked:**

None.

## Readiness

Verdict: **READY**

Base 4c398e07. Read: spec and plan at worktree HEAD e46e2192 (clean tree), `readiness-1.md`, `readiness-2.md`, `git diff 57c2287a e46e2192`.

### Checks

| Check | Result |
|---|---|
| Every AC has a plan task and a Test design row | Pass, unchanged from `readiness-1.md#Checks` |
| Citations resolve in the code | Pass, 9 re-sampled at 4c398e07 (table below) |
| Build stays green after every task; dependency order | Pass, unchanged from round 1 |
| P0 rows name defect, `red`/`guard` and automated test | Pass: AC-1..AC-5 and AC-7 instrumented rows are `red`/`guard` with backticked tests; the AC-7 device row is marked manual outside the automated P0 set and names R1, R2 and wording defects (readiness-1 fix 1) |
| Stays inside Out of scope; blocking questions answered | Pass; Q1 (blocking) answered in spec `## Open questions` |
| Spec is one coherent document | Pass: the spec diff against 57c2287a is the AC-7 device row only (plus a dropped final newline); each section heading appears once (readiness-2 fix 1) |

### Citations sampled

| Citation | Claim | Found at 4c398e07 |
|---|---|---|
| `NetworkDiagnosticsScreen.kt:13`, `:23-26` | `viewModelOf` import, scope with `viewModelOf(::NetworkDiagnosticsVM)` | yes |
| `NetworkDiagnosticsVM.kt:16-20`, `:26`, `:28-35`, `:46-47` | ctor, `onStart`, try/catch, `startRun` and `running` guard | yes |
| `NetworkDiagnosticsContent.kt:142-147` | subtitle `Text`, 12 dp spacer | yes |
| `NetworkDiagnosticsContent.kt:580-585` | `SpeedTestServer.title()` | yes |
| `NetworkDiagnosticsTestTags.kt:3-8` | tags object | yes |
| `values/strings.xml:53`, `values-en/strings.xml:397` | `diagnostics_screen_subtitle` | yes |
| `LauncherVM.kt:18` | injected `ioDispatcher` | yes |
| `NetworkDiagnosticsVMTest.kt:39-46` | `vm()` | yes |
| FocusTest `:51`, `:66`, `:103`, `:125`, `:143`, `:160`, `:174`, `:183` | six cases incl. the three guards, `setContent`, `awaitFocus` | yes |

### Fix list

N/A: no fixes.

