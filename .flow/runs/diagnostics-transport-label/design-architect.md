# Design (architect): diagnostics-transport-label

Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/`. Base 4c398e07. Source of the port: branch commits 3209128b (provider) and f037c052 (label), applied as `app/` diffs (`brief.md#Findings`), then changed as below.

## Decisions

| Question | Decision | Why |
|---|---|---|
| Outcomes of the mapper | `enum class ConnectionTransport { Ethernet, Wifi, Cellular, Other, None }`; `Unknown` from 3209128b is split | Brief Q1 recommended answer; AC-2 and AC-3 name two outcomes |
| Mapping | `activeNetwork == null` or `getNetworkCapabilities(network) == null` -> `None`; Ethernet > Wi-Fi > Cellular by `hasTransport`; any other capability set -> `Other` | AC-1..AC-3; precedence as in 3209128b |
| Where the provider lives | `core/system/ConnectionTransportProvider.kt`, plain class over `ConnectivityManager`, no Android `Context` | Mockable in a JVM test; `core/system` already holds platform adapters (`AndroidResourceProvider`, `ContentSharer`) |
| Its DI binding | `scoped { ConnectionTransportProvider(androidContext().getSystemService(ConnectivityManager::class.java)) }` in the screen scope, no global binding | One consumer; screen dependencies live in `buildModule` (`docs/architecture/screens-and-di.md:79`) |
| VM registration | `viewModelOf(::NetworkDiagnosticsVM)` at `NetworkDiagnosticsScreen.kt:25` becomes an explicit `viewModel { NetworkDiagnosticsVM(..., ioDispatcher = Dispatchers.IO) }` | `viewModelOf` resolves every ctor parameter with `get()` and no `CoroutineDispatcher` is bound |
| Off-main read | `withContext(ioDispatcher) { transportProvider.current() }`; the ctor takes `ioDispatcher: CoroutineDispatcher` | `getNetworkCapabilities` is a binder call; AC-4; injection precedent `ui/feature/root/vm/LauncherVM.kt:18` |
| When it is read | `onStart` (`NetworkDiagnosticsVM.kt:26`) and `startRun` after the `running` guard (`:47`) | AC-4, AC-5; snapshot staleness (Risks) |
| State shape | `transport: ConnectionTransport? = null` on `NetworkDiagnosticsViewState` (`model/NetworkDiagnosticsViewState.kt:14-20`); null until the first read | AC-6: null renders nothing, so no fallback value flashes |
| Read failure | caught and logged like `currentServer` at `NetworkDiagnosticsVM.kt:31-35`; state keeps its previous value | A label never fails the screen |
| Rendering | non-focusable `Text`, `bodyMedium`, `onSurfaceVariant`, 4 dp above, between the subtitle (`NetworkDiagnosticsContent.kt:142-146`) and the 12 dp spacer (`:147`); enum-to-string by a private `@Composable ConnectionTransport.label()` next to `SpeedTestServer.title()` (`:580`) | f037c052 layout; sizes are the ux-designer's (`.flow/roles/architect.md#Visual track`) |
| Test hook for AC-6/AC-7 | add `const val Transport = "diagnostics-transport"` to `NetworkDiagnosticsTestTags` (`NetworkDiagnosticsTestTags.kt:3`) and tag the `Text` | Lets a Compose test assert absence when null and that the node is not focused |

## Changes by layer

| Layer | File | Change |
|---|---|---|
| API / repository | `core/system/ConnectionTransportProvider.kt` (new) | Enum and provider per Decisions; from 3209128b with `Unknown` split |
| ViewModel state and actions | `ui/feature/device/diagnostics/model/NetworkDiagnosticsViewState.kt:14-20` | Add `transport` field |
| | `ui/feature/device/diagnostics/vm/NetworkDiagnosticsVM.kt:16-20` | Ctor gains `transportProvider`, `ioDispatcher` |
| | same file `:26`, `:46` | Private `refreshTransport()` launched from `onStart` and from `startRun` after `if (stateValue.running) return` |
| Screen and DI module | `ui/feature/device/diagnostics/NetworkDiagnosticsScreen.kt:23-26` | `scoped` provider plus explicit `viewModel { }` (Decisions) |
| Compose content | `ui/feature/device/diagnostics/NetworkDiagnosticsContent.kt:142-147` | `state.transport?.let { Spacer(4.dp); Text(...) }` after the subtitle |
| | same file, after `:580` | `ConnectionTransport.label()` over five strings |
| | `ui/feature/device/diagnostics/NetworkDiagnosticsTestTags.kt:3` | `Transport` tag |
| | `ui/feature/device/diagnostics/NetworkDiagnosticsPreview.kt:18` | Ready preview gets `transport = Ethernet`; the other two stay null (AC-6 preview) |
| Resources | `app/src/main/res/values/strings.xml` after `:53`, `app/src/main/res/values-en/strings.xml` after `:397` | `diagnostics_transport_label` (`%1$s`), `_ethernet`, `_wifi`, `_cellular`, `_other`, `_none`; wording per `brief.md#Open questions` Q2 |
| Tests | `app/src/test/kotlin/com/kino/puber/core/system/ConnectionTransportProviderTest.kt` (new) | From 3209128b: four precedence cases (AC-1), two null cases now expect `None` (AC-3), one capability set with none of the three -> `Other` (AC-2) |
| | `app/src/test/kotlin/com/kino/puber/ui/feature/device/diagnostics/vm/NetworkDiagnosticsVMTest.kt:39-46` | `vm()` passes a mocked provider and `ioDispatcher`; f037c052 cases for null-before-start, `onStart` (AC-4), re-read on start (AC-5). For "off main" in AC-4, pass a `StandardTestDispatcher` sharing `mainDispatcher.dispatcher.scheduler` and assert null before and the value after advancing |
| | `app/src/androidTest/kotlin/com/kino/puber/ui/feature/device/diagnostics/NetworkDiagnosticsContentFocusTest.kt:50` | Existing cases unchanged (AC-7 first focus); add: null -> no `Transport` node (AC-6); `Ethernet` -> node shown and not focused after initial focus settles (AC-7) |

## Alternatives rejected

- Global `single<ConnectivityManager>` in `data/di/modules.kt:78`: touches the app graph for one screen.
- `registerDefaultNetworkCallback` flow (research option C): callback lifecycle for a label re-read at every run.
- Provider taking `Context`: needs Robolectric or a context mock in the unit test.
- Keeping `Unknown` (option A): AC-2 and AC-3 require two outcomes.
- Reading the transport synchronously in `onStart`: binder call on main, fails AC-4.

## Risks

| Risk | Area | Where | Mitigation |
|---|---|---|---|
| Label appears one frame after the cards and pushes the `Row` down by a line while first focus is being claimed | focus, layout | `NetworkDiagnosticsContent.kt:149-152` (`Row` with `weight(1f)`), launch focus `:106-114` | Read lands well inside `FOCUS_ON_LAUNCH_DELAY_MILLIS`; focus test keeps first focus (AC-7); box screenshot shows no jump; reserving the line is the ux-designer's call |
| Lost update between `refreshTransport` and `publish` | concurrency | `NetworkDiagnosticsVM.kt:61-78` | Both run on Main; `refreshTransport` must build `stateValue.copy(transport = ...)` after `withContext` returns, never capture `stateValue` before it |
| Snapshot goes stale while the screen stays open | correctness | `ConnectionTransportProvider.current()` | Re-read in `startRun` (AC-5); `onStart` runs once per VM (`core/ui/PuberVM.kt:146-150`), so returning to the screen shows the last read until a run |
| Binder call on Main | main thread | `refreshTransport` | `withContext(ioDispatcher)`; AC-4 test |
| VM outlives provider | Koin scope | `NetworkDiagnosticsScreen.kt:24` | Both in `scope(named(scopeId))`; provider holds only the app-context `ConnectivityManager` |
| Recomposition from the new field | recomposition | `NetworkDiagnosticsViewState` | Enum is stable; class stays `@Immutable` |
| Baseline profile | performance | `app/src/main/generated/baselineProfiles/baseline-prof.txt` (one diagnostics line) | No regeneration for one `Text` |

## Open questions

N/A: brief Q1-Q3 carry this design's open choices; it follows their recommended answers.
