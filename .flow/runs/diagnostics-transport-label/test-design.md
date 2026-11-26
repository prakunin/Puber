# Test design: diagnostics-transport-label

Base 4c398e07. Inputs: `brief.md`, `design-architect.md`, `design-ux.md`. Levels per `.flow/roles/test-designer.md`: `unit` (JUnit 5, MockK, coroutines-test), `instrumented` (`make DEVICE=192.168.1.110:5555 itest TESTS=<fqcn>`), `device` (box, remote input).

Test files:

| Short | Path |
|---|---|
| ProviderTest | `app/src/test/kotlin/com/kino/puber/core/system/ConnectionTransportProviderTest.kt` (new, from 3209128b) |
| VMTest | `app/src/test/kotlin/com/kino/puber/ui/feature/device/diagnostics/vm/NetworkDiagnosticsVMTest.kt` (extend; `vm()` gains `transportProvider`, `ioDispatcher`) |
| FocusTest | `app/src/androidTest/kotlin/com/kino/puber/ui/feature/device/diagnostics/NetworkDiagnosticsContentFocusTest.kt` (extend; existing six cases unchanged) |

## Risk profile

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

## Traceability

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

### Notes for the implementer

- `onStart_readsTransportOnIoDispatcher_notOnMain`: `ioDispatcher = StandardTestDispatcher(mainDispatcher.dispatcher.scheduler)`. After `testOnStart()`: `transport` is null and `verify(exactly = 0) { transportProvider.current() }`; after `testScheduler.advanceUntilIdle()`: `transport == Wifi`. With main `Unconfined`, a synchronous read calls `current()` before the advance.
- `onStart_keepsCurrentServer_whenTransportArrivesAfterIt`: same `ioDispatcher`; `coEvery { interactor.currentServer() } coAnswers { gate.await() }` with a `CompletableDeferred`. `testOnStart()`, complete `gate` with Amsterdam, then advance. Assert `currentServer == Amsterdam` and `transport == Wifi`. The gate makes the mutation fail whichever launch runs first.
- `onStart_keepsLoadingServer_whenTransportReadThrows`: `every { current() } throws IllegalStateException()`; assert `transport` null and `currentServer == Amsterdam`.
- Other VM tests keep `ioDispatcher = mainDispatcher.dispatcher` (as in f037c052).
- FocusTest new cases tag the line with `NetworkDiagnosticsTestTags.Transport`; AC-7 case waits for Amsterdam focus with `awaitFocus`, then asserts the Transport node exists, `assertIsNotFocused()`, and Amsterdam `assertIsFocused()`. AC-6 case uses `onAllNodesWithTag(Transport).assertCountEquals(0)`.
- Red check: every `red` row fails on 4c398e07 because `ConnectionTransportProvider`, `transport` and the `Transport` tag do not exist there (compile failure counts). Before writing production code, also confirm each ProviderTest case fails against the 3209128b provider as applied, for AC-2 and AC-3 (it returns `Unknown`).

## Bugs

N/A: improvement, no defect on the base commit.

## Manual checks

Preconditions: lease `192.168.1.110:5555` through `tools/emulator-resource-lock.sh`; refresh the session with a master build and `make DEVICE=192.168.1.110:5555 auth-save` (R7); install the worktree dev APK with `tools/android-apk-install-preserve`; only `com.kino.puber.stage`. Box on Ethernet. All input by `adb -s 192.168.1.110:5555 shell input keyevent ...`.

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| M1 | 1. App language ru (device settings screen). 2. Open network diagnostics with D-pad from the drawer. 3. Wait 2 s, note focus; press `KEYCODE_DPAD_UP` twice, then `KEYCODE_DPAD_DOWN`. 4. Screenshot. 5. `KEYCODE_DPAD_CENTER` on the focused card, wait for the run to end, screenshot | Screen opens; one line `Подключение: Ethernet` under the subtitle, same style as the subtitle; first focus on the current (or first) server card; up never lands on the line; gauge and both cards fully visible; line still `Ethernet` after the run | Crash or Koin error on open (R1); focus on the header or lost; a value flashing before `Ethernet`; gauge top or lower card edge touching a neighbour (R9) |
| M2 | Repeat M1 steps 1-4 with app language en | `Connection: Ethernet` | Russian text, raw `%1$s`, missing line |
| M3 | Owner views M1/M2 screenshots next to title and subtitle | Owner accepts size and colour (AC-8) | Line bolder, larger or tinted than the subtitle (`design-ux.md#Agreed numbers that apply`) |

Release the lease when done. Screenshots go through `tools/mobile-evidence-audit.sh` before commit.

## UNTESTABLE

- AC-1..AC-3 on a real Wi-Fi, cellular, VPN-only or disconnected network: only the box on Ethernet is used (`brief.md` Q3); unit tests cover the mapping.

## Perf track

N/A: perf track is off.
