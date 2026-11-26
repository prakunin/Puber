# Show the connection transport on the network diagnostics screen

Status: approved
Date: 2026-11-26
Slug: diagnostics-transport-label
Kind: improvement
Size: M
Tracks: ui

<!--
Each section has exactly one owning role; nobody else edits it. The conductor
copies each section verbatim from its owner's output and commits this file in
the worktree after each stage. Cite code as `path:line`; a claim with no
source is a guess.
-->

## Problem / Why

### Request

Show the active connection transport (Ethernet / Wi-Fi / cellular / other / none) on the network diagnostics screen. Port P3 of `upstream-sync-triage` (theme T9); already ported on that branch as 3209128b (provider) and f037c052 (label).

### Findings

Code map, branch commits, cherry-pick check, external API notes and options: `research.md: Current state in code`, `External findings`, `Options` (option B recommended and taken here). Checked at 4c398e07 (paths under `app/src/main/java/com/kino/puber/`):

| Where | Fact |
|---|---|
| `ui/feature/device/diagnostics/NetworkDiagnosticsScreen.kt:23-26` | Screen scope holds only `viewModelOf(::NetworkDiagnosticsVM)` |
| `ui/feature/device/diagnostics/vm/NetworkDiagnosticsVM.kt:16-20`, `:26`, `:46` | Ctor, `onStart`, `startRun`; no transport |
| `ui/feature/device/diagnostics/model/NetworkDiagnosticsViewState.kt:14-20` | No `transport` field |
| `ui/feature/device/diagnostics/NetworkDiagnosticsContent.kt:137-148` | Title, subtitle, 12 dp spacer: the label goes after the subtitle |
| `data/di/modules.kt:78` | `ConnectivityManager` built inline for `KinoPubApiClient`; no global binding |
| 3209128b `core/system/ConnectionTransportProvider.kt` | `enum { Ethernet, Wifi, Cellular, Unknown }`; null network, null capabilities and unmapped transports all map to `Unknown` |
| 3209128b, f037c052 | `app/` paths apply to master with `git diff <c>^ <c> -- app \| git apply`; plain `git cherry-pick` conflicts on a plan file master lacks |

### Size

M: one goal, but it adds a screen-scoped Koin binding and a ViewModel state field (risk areas in `.flow/roles/analyst.md`). About nine production and test files plus two `strings.xml`; no design stand covers diagnostics (`docs/design/`), the change is one non-focusable `Text`.

### Risks (analyst)

- `getNetworkCapabilities` is a snapshot that can go stale (`research.md: External findings`); re-read at `startRun` (AC-5) covers the moment it matters.
- A worktree build can drop the box session (memory note): install with `tools/android-apk-install-preserve` after a master-build refresh and auth-save.
- VPN over Ethernet reads as Ethernet, since a VPN carries its underlying transports; intended.

## Acceptance criteria

| ID | Given | When | Then |
|---|---|---|---|
| AC-1 | capabilities with Ethernet and Wi-Fi; Wi-Fi only; Wi-Fi and cellular; cellular only | `ConnectionTransportProvider.current()` is called | it returns Ethernet, Wi-Fi, Wi-Fi, Cellular (provider unit test) |
| AC-2 | an active network whose capabilities have none of Ethernet, Wi-Fi, cellular (for example Bluetooth or a pure VPN) | `current()` is called | it returns `Other` (provider unit test) |
| AC-3 | `activeNetwork` is null; separately, `getNetworkCapabilities` returns null | `current()` is called | it returns `None` (provider unit test) |
| AC-4 | the screen opens | `onStart` runs | state `transport` holds the provider's value, read off the main dispatcher (VM unit test) |
| AC-5 | the screen is open and the provider now returns a different transport | a speed test is started | state `transport` is the new value (VM unit test) |
| AC-6 | `transport` is null | the content renders | no transport line is shown (preview or Compose test) |
| AC-7 | the box on Ethernet, ru and en locales | the diagnostics screen is opened with the D-pad | one line `Подключение: Ethernet` / `Connection: Ethernet` sits under the subtitle; D-pad focus never lands on it and first focus is unchanged (box screenshot; `NetworkDiagnosticsContentFocusTest` passes) |
| AC-8 | the line on the TV | seen next to title and subtitle | its size and colour fit the header (owner judges) |

## Out of scope

A live network callback that updates the label while the screen stays open (research option C). Checking Wi-Fi, cellular, VPN-only or disconnected networks on a device (Q3). A design stand for this screen.

## Design

Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/`. Base 4c398e07. Source of the port: branch commits 3209128b (provider) and f037c052 (label), applied as `app/` diffs (`brief.md#Findings`), then changed as below.

### Decisions

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

### Changes by layer

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

### Alternatives rejected

- Global `single<ConnectivityManager>` in `data/di/modules.kt:78`: touches the app graph for one screen.
- `registerDefaultNetworkCallback` flow (research option C): callback lifecycle for a label re-read at every run.
- Provider taking `Context`: needs Robolectric or a context mock in the unit test.
- Keeping `Unknown` (option A): AC-2 and AC-3 require two outcomes.
- Reading the transport synchronously in `onStart`: binder call on main, fails AC-4.

### Risks

| Risk | Area | Where | Mitigation |
|---|---|---|---|
| Label appears one frame after the cards and pushes the `Row` down by a line while first focus is being claimed | focus, layout | `NetworkDiagnosticsContent.kt:149-152` (`Row` with `weight(1f)`), launch focus `:106-114` | Read lands well inside `FOCUS_ON_LAUNCH_DELAY_MILLIS`; focus test keeps first focus (AC-7); box screenshot shows no jump; reserving the line is the ux-designer's call |
| Lost update between `refreshTransport` and `publish` | concurrency | `NetworkDiagnosticsVM.kt:61-78` | Both run on Main; `refreshTransport` must build `stateValue.copy(transport = ...)` after `withContext` returns, never capture `stateValue` before it |
| Snapshot goes stale while the screen stays open | correctness | `ConnectionTransportProvider.current()` | Re-read in `startRun` (AC-5); `onStart` runs once per VM (`core/ui/PuberVM.kt:146-150`), so returning to the screen shows the last read until a run |
| Binder call on Main | main thread | `refreshTransport` | `withContext(ioDispatcher)`; AC-4 test |
| VM outlives provider | Koin scope | `NetworkDiagnosticsScreen.kt:24` | Both in `scope(named(scopeId))`; provider holds only the app-context `ConnectivityManager` |
| Recomposition from the new field | recomposition | `NetworkDiagnosticsViewState` | Enum is stable; class stays `@Immutable` |
| Baseline profile | performance | `app/src/main/generated/baselineProfiles/baseline-prof.txt` (one diagnostics line) | No regeneration for one `Text` |

### Open questions

N/A: brief Q1-Q3 carry this design's open choices; it follows their recommended answers.

### Visual (ux-designer)

Checked against the worktree at 4c398e07. Paths are under
`app/src/main/java/com/kino/puber/`.

### Stand

No `docs/design/` stand governs the network diagnostics screen (stands exist for catalogue,
content, player-controls, showall). The conductor does not need to run `designing-a-screen`
before planning: the skill rules itself out for "a one-line style fix", and this change adds one
non-focusable line that copies the style of the subtitle already above it. It sets no new size,
spacing or colour that a slider would have to settle. If the owner rejects the look at gate 2
(AC-8), that would be the moment to start a stand, not now.

### Agreed numbers that apply

With no stand, the numbers come from the screen's own code
(`ui/feature/device/diagnostics/NetworkDiagnosticsContent.kt:133-148`) and the theme
(`core/ui/uikit/theme/Type.kt`, which does not override `bodyMedium` or `headlineLarge`, so
the tv-material3 defaults apply):

| Element | Style | Size | Colour | Gap before |
|---|---|---|---|---|
| Title | `headlineLarge` | 32 sp / 40 sp line | default `onSurface` | top padding 28 dp |
| Subtitle | `bodyMedium` | 14 sp / 20 sp line | `onSurfaceVariant` | 4 dp |
| **Transport line (new)** | **`bodyMedium`** | **14 sp / 20 sp line** | **`onSurfaceVariant`** | **4 dp** |
| Results row | - | `weight(1f)` | - | 12 dp (unchanged, after the new line) |

The new line matches the subtitle exactly: same style, same colour, same 4 dp step as the
title-to-subtitle gap, left edge on the same 48 dp `ScreenHorizontalPadding`. One line, no
`maxLines` cut (the longest value, `Подключение: Нет подключения`, is about 200 dp at 14 sp, far
under the 864 dp of content width). It must not be bolder, larger, primary-tinted or an icon
chip: next to the title it would compete with the server cards, which carry the screen's
colour. The ported code on `upstream-sync-triage` (f037c052,
`NetworkDiagnosticsContent.kt:148-155`) already does exactly this; take it as is.

The `Spacer(4.dp)` and the `Text` go inside `state.transport?.let { }`, so a null transport
leaves the header byte-for-byte as today (AC-6) and nothing shifts when the value arrives except
the one 24 dp step down of the row below, once, on open.

#### Vertical budget (on a 1080p box, 540 dp tall)

The line takes 24 dp (4 gap + 20 line) out of the `weight(1f)` results row:

| | Today | With the line |
|---|---|---|
| Fixed: 2 x 28 padding, title 40, 4, subtitle 20, 12, status area 64 | 196 dp | 220 dp |
| Results row height | 344 dp | 320 dp |
| Gauge box (`GaugeSize`) | 300 dp | 300 dp, 20 dp spare |

The gauge still fits without clipping; the server cards column is centred and two cards (about
130 dp each plus 12 dp) fit in 320 dp. The visual review must measure the row on the box: if the
gauge canvas top or the lower card edge touches its neighbour, that is a blocker, and the fix is
to fold the line into the subtitle row, not to shrink `GaugeSize`.

#### Still open (owner, AC-8 / Q2)

- Whether `Нет подключения` should stand out (for example `colorScheme.error`). Recommended:
  no, keep `onSurfaceVariant` for every value; the server cards already turn to an error state
  when a test fails, and a red header line would be the only colour above them.
- Wording (brief Q2); not a visual question.

### Focus

The transport line is not focusable: a plain `Text` with no `clickable`, `focusable` or
`Surface` around it. Focus order is unchanged: first focus still lands on the current (or first)
server card through `initialFocusRequester` (`NetworkDiagnosticsContent.kt:389-393`), D-pad up
from the top card goes nowhere (or to the drawer), never to the header. No new focus visibility
is needed. `NetworkDiagnosticsContentFocusTest` must keep passing unchanged (AC-7).

### Shared constants

None are touched. `ScreenHorizontalPadding`, `ScreenVerticalPadding`, `GaugeSize`,
`StatusAreaHeight` and the 12 dp header spacer are private to this file and stay as they are;
`bodyMedium` and `onSurfaceVariant` are theme values read, not retuned. Do not add a
`Typography` override in `Type.kt` for this line.

## Test design

Base 4c398e07. Inputs: `brief.md`, `design-architect.md`, `design-ux.md`. Levels per `.flow/roles/test-designer.md`: `unit` (JUnit 5, MockK, coroutines-test), `instrumented` (`make DEVICE=192.168.1.110:5555 itest TESTS=<fqcn>`), `device` (box, remote input).

Test files:

| Short | Path |
|---|---|
| ProviderTest | `app/src/test/kotlin/com/kino/puber/core/system/ConnectionTransportProviderTest.kt` (new, from 3209128b) |
| VMTest | `app/src/test/kotlin/com/kino/puber/ui/feature/device/diagnostics/vm/NetworkDiagnosticsVMTest.kt` (extend; `vm()` gains `transportProvider`, `ioDispatcher`) |
| FocusTest | `app/src/androidTest/kotlin/com/kino/puber/ui/feature/device/diagnostics/NetworkDiagnosticsContentFocusTest.kt` (extend; existing six cases unchanged) |

### Risk profile

| # | Risk | P | I | Score | Covered by |
|---|---|---|---|---|---|
| R1 | Explicit `viewModel { }` in `NetworkDiagnosticsScreen.kt:23-26` misses a parameter or the provider binding: Koin crash on screen open. No unit test resolves screen-scoped modules | 2 | 3 | 6 | M1 step 2 (device, where it fails) |
| R2 | The line arrives one frame after the cards, shifts the `Row` and first focus lands elsewhere or is lost | 2 | 3 | 6 | `transportLine_isShownAndNotFocused_whenEthernet`, existing FocusTest guards, M1 step 3 |
| R3 | Mapper precedence wrong, or `Other` and `None` merged as in 3209128b | 2 | 2 | 4 | ProviderTest rows AC-1..AC-3 |
| R4 | Binder call `current()` runs on Main | 2 | 2 | 4 | `onStart_readsTransportOnIoDispatcher_notOnMain` |
| R5 | Lost update: `refreshTransport` captures `stateValue` before `withContext` and wipes `currentServer` | 2 | 2 | 4 | `onStart_keepsCurrentServer_whenTransportArrivesAfterIt` |
| R6 | Provider throws and the screen fails instead of hiding the line | 1 | 3 | 3 | `onStart_keepsLoadingServer_whenTransportReadThrows` |
| R7 | Worktree install drops the box session (memory note) | 2 | 2 | 4 | M1 preconditions; not a code test |
| R8 | Stale snapshot while the screen stays open | 2 | 1 | 2 | Accepted: AC-5 re-read at each run |
| R9 | Line steals height so the gauge or lower card clips (`design-ux.md#Vertical budget`) | 1 | 2 | 2 | Accepted; M1 step 4 screenshot |
| R10 | Missing `values-en` string or bad `%1$s` | 1 | 2 | 2 | Accepted; `lintDevDebug` in check.full, M1/M2 |
| R11 | VPN over Ethernet reads as Ethernet | 1 | 1 | 1 | Accepted: intended (`brief.md#Risks`) |

### Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 red | `transport_isEthernet_whenEthernetAndWifiBothPresent` | fails if Wi-Fi is checked before Ethernet |
| AC-1 | unit | P0 red | `transport_isWifi_whenOnlyWifi` | fails if the Wi-Fi branch is dropped or maps elsewhere |
| AC-1 | unit | P0 red | `transport_isWifi_whenWifiAndCellularBothPresent` | fails if cellular is checked before Wi-Fi |
| AC-1 | unit | P0 red | `transport_isCellular_whenOnlyCellular` | fails if the cellular branch is dropped |
| AC-2 | unit | P0 red | `transport_isOther_whenNoMappedTransport` (capabilities present, all three `hasTransport` false) | fails if the fallback is `None` or the old `Unknown` |
| AC-3 | unit | P0 red | `transport_isNone_whenActiveNetworkNull` | fails if a null network maps to `Other` or throws |
| AC-3 | unit | P0 red | `transport_isNone_whenNoCapabilities` | fails if null capabilities map to `Other` or throw |
| AC-4 | unit | P1 | `state_transportNull_beforeStart` | fails if the initial state carries a default transport (would flash a value) |
| AC-4 | unit | P0 red | `onStart_setsTransportInState_whenProviderReturnsWifi` | fails if `onStart` does not read the provider |
| AC-4 | unit | P0 red | `onStart_readsTransportOnIoDispatcher_notOnMain` | fails if `withContext(ioDispatcher)` is removed (see Notes) |
| AC-5 | unit | P0 red | `startRun_rereadsTransport_whenNetworkChangedSinceStart` | fails if `startRun` does not re-read |
| AC-5 | unit | P2 | `start_doesNotRereadTransport_whileRunning` | fails if the re-read sits above the `running` guard |
| AC-6 | instrumented | P1 | `transportLine_isAbsent_whenTransportNull` | fails if the line renders a fallback for null |
| AC-7 | instrumented | P0 red | `transportLine_isShownAndNotFocused_whenEthernet` | fails if the tagged node is missing, focusable, or takes focus from the Amsterdam card |
| AC-7 | instrumented | P0 guard | `initialStateStartsAmsterdamFromTheFocusedCard`, `launchFocusRecovers_whenTheRailTakesFocusDuringTheSettleDelay`, `focusedServerCardKeepsFocus_whenTheRunStarts` | fail if the header change breaks first focus or focus during a run |
| AC-7 | device | P0 manual (outside the automated P0 set) | M1, M2 | fails on a Koin crash or error when the screen opens (R1: `viewModel { }` misses a parameter or the provider binding); on first focus landing on the header or lost, or D-pad up reaching the line (R2); on wrong or untranslated wording (`Подключение: Ethernet` / `Connection: Ethernet`, raw `%1$s`) |
| AC-8 | manual | P1 | M3 | owner's judgement of size and colour |
| R5 | unit | P1 | `onStart_keepsCurrentServer_whenTransportArrivesAfterIt` | fails if `refreshTransport` copies a `stateValue` captured before `withContext` |
| R6 | unit | P1 | `onStart_keepsLoadingServer_whenTransportReadThrows` | fails if the exception escapes or blocks `currentServer` |

#### Notes for the implementer

- `onStart_readsTransportOnIoDispatcher_notOnMain`: `ioDispatcher = StandardTestDispatcher(mainDispatcher.dispatcher.scheduler)`. After `testOnStart()`: `transport` is null and `verify(exactly = 0) { transportProvider.current() }`; after `testScheduler.advanceUntilIdle()`: `transport == Wifi`. With main `Unconfined`, a synchronous read calls `current()` before the advance.
- `onStart_keepsCurrentServer_whenTransportArrivesAfterIt`: same `ioDispatcher`; `coEvery { interactor.currentServer() } coAnswers { gate.await() }` with a `CompletableDeferred`. `testOnStart()`, complete `gate` with Amsterdam, then advance. Assert `currentServer == Amsterdam` and `transport == Wifi`. The gate makes the mutation fail whichever launch runs first.
- `onStart_keepsLoadingServer_whenTransportReadThrows`: `every { current() } throws IllegalStateException()`; assert `transport` null and `currentServer == Amsterdam`.
- Other VM tests keep `ioDispatcher = mainDispatcher.dispatcher` (as in f037c052).
- FocusTest new cases tag the line with `NetworkDiagnosticsTestTags.Transport`; AC-7 case waits for Amsterdam focus with `awaitFocus`, then asserts the Transport node exists, `assertIsNotFocused()`, and Amsterdam `assertIsFocused()`. AC-6 case uses `onAllNodesWithTag(Transport).assertCountEquals(0)`.
- Red check: every `red` row fails on 4c398e07 because `ConnectionTransportProvider`, `transport` and the `Transport` tag do not exist there (compile failure counts). Before writing production code, also confirm each ProviderTest case fails against the 3209128b provider as applied, for AC-2 and AC-3 (it returns `Unknown`).

### Bugs

N/A: improvement, no defect on the base commit.

### Manual checks

Preconditions: lease `192.168.1.110:5555` through `tools/emulator-resource-lock.sh`; refresh the session with a master build and `make DEVICE=192.168.1.110:5555 auth-save` (R7); install the worktree dev APK with `tools/android-apk-install-preserve`; only `com.kino.puber.stage`. Box on Ethernet. All input by `adb -s 192.168.1.110:5555 shell input keyevent ...`.

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| M1 | 1. App language ru (device settings screen). 2. Open network diagnostics with D-pad from the drawer. 3. Wait 2 s, note focus; press `KEYCODE_DPAD_UP` twice, then `KEYCODE_DPAD_DOWN`. 4. Screenshot. 5. `KEYCODE_DPAD_CENTER` on the focused card, wait for the run to end, screenshot | Screen opens; one line `Подключение: Ethernet` under the subtitle, same style as the subtitle; first focus on the current (or first) server card; up never lands on the line; gauge and both cards fully visible; line still `Ethernet` after the run | Crash or Koin error on open (R1); focus on the header or lost; a value flashing before `Ethernet`; gauge top or lower card edge touching a neighbour (R9) |
| M2 | Repeat M1 steps 1-4 with app language en | `Connection: Ethernet` | Russian text, raw `%1$s`, missing line |
| M3 | Owner views M1/M2 screenshots next to title and subtitle | Owner accepts size and colour (AC-8) | Line bolder, larger or tinted than the subtitle (`design-ux.md#Agreed numbers that apply`) |

Release the lease when done. Screenshots go through `tools/mobile-evidence-audit.sh` before commit.

### UNTESTABLE

- AC-1..AC-3 on a real Wi-Fi, cellular, VPN-only or disconnected network: only the box on Ethernet is used (`brief.md` Q3); unit tests cover the mapping.

### Perf track

N/A: perf track is off.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Show "other" and "none" as two outcomes or one Unknown? | yes | answered | Two: Other and None |
| Q2 | Label wording ru/en? | no | answered | "Подключение: %1$s" / "Connection: %1$s"; Ethernet, Wi-Fi, Мобильная сеть / Cellular, Другое / Other, Нет подключения / No connection |
| Q3 | Check a second transport on a device? | no | answered | No; TV box on Ethernet only, unit tests cover the mapping |
| Q4 | Should "Нет подключения" stand out (for example red)? (ux-designer) | no | answered | No; same muted colour for every value |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-26 | Q1-Q4 as recommended | owner (standing instruction: recommended) |
| 2026-11-26 | Gate 1: spec approved | owner (standing instruction: recommended) |
