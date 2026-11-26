# Brief: diagnostics-transport-label

Kind: improvement   Size: M   Tracks: ui   Device verification: yes (TV UI, ViewModel state, screen DI)

## Request

Show the active connection transport (Ethernet / Wi-Fi / cellular / other / none) on the network diagnostics screen. Port P3 of `upstream-sync-triage` (theme T9); already ported on that branch as 3209128b (provider) and f037c052 (label).

## Findings

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

## Size

M: one goal, but it adds a screen-scoped Koin binding and a ViewModel state field (risk areas in `.flow/roles/analyst.md`). About nine production and test files plus two `strings.xml`; no design stand covers diagnostics (`docs/design/`), the change is one non-focusable `Text`.

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

## Risks

- `getNetworkCapabilities` is a snapshot that can go stale (`research.md: External findings`); re-read at `startRun` (AC-5) covers the moment it matters.
- A worktree build can drop the box session (memory note): install with `tools/android-apk-install-preserve` after a master-build refresh and auth-save.
- VPN over Ethernet reads as Ethernet, since a VPN carries its underlying transports; intended.

## Open questions

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| Q1 | "Other" and "none" as two outcomes (request) or one `Unknown` (ported code) | Two: `Other` and `None`, so "none" tells why the test will fail | yes |
| Q2 | Wording ru / en | `Подключение: %1$s` / `Connection: %1$s`; `Ethernet`, `Wi-Fi`, `Мобильная сеть` / `Cellular`, `Другое` / `Other`, `Нет подключения` / `No connection` | no |
| Q3 | Device check on a second transport | No: box only, Ethernet; unit tests cover the mapping | no |
