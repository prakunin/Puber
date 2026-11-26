# Research: diagnostics-transport-label

## Request

show the active connection transport (Ethernet / Wi-Fi / cellular / other / none) on the network diagnostics screen. Port P3 of upstream-sync-triage (theme T9): upstream rovkinmax/Puber has ConnectionTransportProvider (activeNetwork + NetworkCapabilities.hasTransport) with a unit test, commits dfd94862..569a1589 (remote 'upstream', fetched). Our fork already ported it on local branch upstream-sync-triage: 3209128b (provider), f037c052 (label on the screen); see also that branch's evidence docs/superpowers/evidence/2026-10-11-upstream-sync-triage-device.md (D-3) and spec (git show upstream-sync-triage:docs/superpowers/specs/2026-10-11-upstream-sync-triage-design.md, row T9, port P3, ACs). Open from that run: the label wording (owner never answered). Master moved 376+ commits since; check whether those commits still cherry-pick and what changed on the diagnostics screen (NetworkDiagnostics*), its design stand under docs/design/ if any, strings (res/values*/strings.xml - which locales exist), DI.

## Understanding

The speed test screen (`NetworkDiagnosticsScreen`) should show one line naming how the box is connected right now. The work is already written and device-checked on branch `upstream-sync-triage` (two commits); it still applies cleanly to master. What is left: land it in a fresh worktree, settle the label wording the owner never answered, and decide whether "other" and "none" are two outcomes (the request) or one `Unknown` (the ported code).

## Current state in code

Master at 4c398e07; the branch's merge-base with master is 358b0ad6, 380 master commits since.

| Item | Where | State on master |
|------|-------|-----------------|
| Screen + DI | `app/src/main/java/com/kino/puber/ui/feature/device/diagnostics/NetworkDiagnosticsScreen.kt:23-26` | screen scope holds only `viewModelOf(::NetworkDiagnosticsVM)` |
| ViewModel | `.../diagnostics/vm/NetworkDiagnosticsVM.kt:16-20` (ctor), `:26` (`onStart`), `:46` (`startRun`) | no transport; `handleAction` (renamed from `onAction` by 97a0fca2, the only master commit in this folder since the branch) |
| View state | `.../diagnostics/model/NetworkDiagnosticsViewState.kt:14-20` | no `transport` field |
| Content | `.../diagnostics/NetworkDiagnosticsContent.kt:137-148` (title, subtitle, 12 dp spacer) | label would go after the subtitle; enum-to-string pattern `SpeedTestServer.title()` at `:580` |
| Previews | `.../diagnostics/NetworkDiagnosticsPreview.kt` | three previews, none with a transport |
| Transport mapper | none; `app/src/main/java/com/kino/puber/core/system/` holds AndroidResourceProvider, AppLocale, ContentSharer, ResourceProvider | `ConnectivityPlugin.kt:35-38` only checks `NET_CAPABILITY_INTERNET` |
| ConnectivityManager | `app/src/main/java/com/kino/puber/data/di/modules.kt:78` built inline for `KinoPubApiClient`; no global binding | |
| Permission | `app/src/main/AndroidManifest.xml:11` `ACCESS_NETWORK_STATE` | present |
| Dispatcher injection | `LauncherVM.kt:18` (`ioDispatcher: CoroutineDispatcher = Dispatchers.IO`), `HomeVM.kt:62` | precedent for the branch's `ioDispatcher` ctor param |
| Strings | `app/src/main/res/values/strings.xml` (ru, default), `values-en/strings.xml:394-413` (`diagnostics_*`) | two locales only |
| Tests | `app/src/test/kotlin/.../diagnostics/vm/NetworkDiagnosticsVMTest.kt`; `app/src/androidTest/kotlin/.../diagnostics/NetworkDiagnosticsContentFocusTest.kt` | test helpers `testOnStart`/`testStateValue` still in `core/ui/PuberVM.kt:65,146` |
| Design stand | `docs/design/` has catalogue, content, player-controls, showall | none for diagnostics |
| SDK | `buildSrc/src/main/kotlin/Versions.kt:6` MinSdk 24, CompileSdk 37 | `activeNetwork` is API 23 |

Branch commits:

| Commit | Files | Content |
|--------|-------|---------|
| 3209128b | `core/system/ConnectionTransportProvider.kt` (34 lines), its JUnit 5 + MockK test (6 cases) | `enum ConnectionTransport { Ethernet, Wifi, Cellular, Unknown }`; precedence Ethernet > Wi-Fi > cellular; null network or null capabilities -> `Unknown` |
| f037c052 | Screen, VM, ViewState, Content, both strings.xml, VMTest | `scoped { ConnectionTransportProvider(...) }` + explicit `viewModel { }` with `ioDispatcher = Dispatchers.IO`; read in `onStart` and again in `startRun`; `transport: ConnectionTransport? = null` (null renders nothing); five strings `diagnostics_transport_*` |

Cherry-pick check (read-only `git diff <c>^ <c> -- app | git apply --check` against master): both commits apply; `values-en/strings.xml` hunk at offset +43. A plain `git cherry-pick` of either commit conflicts (modify/delete) on `docs/superpowers/plans/2026-10-11-upstream-sync-triage.md`, which master does not have; take the `app/` paths only.

Branch wording (ru / en): label `Подключение: %1$s` / `Connection: %1$s`; `Ethernet`, `Wi-Fi`, `Мобильная сеть` / `Cellular`, `Не определено` / `Unknown`. Upstream 569a1589 uses `Сотовая сеть` and `Неизвестно` (`app/src/main/res/values/speed_test_strings.xml:5-8`) and a sealed interface instead of the enum.

## Related work

| Item | Relation |
|------|----------|
| run `upstream-sync-triage` (stage impl) | source; Log 2026-11-26 moves P3 to this run; branch is now only a cherry-pick source |
| spec on that branch, rows T9 (line 92), P3 (127), AC-10/AC-11 (217-222), D6-D8 (303-305), R10/R11 (535-536), D-3 (632) | ACs and decisions reusable as they are |
| evidence D-3 on that branch | PASS on emulator, en and ru; label never focused; no "unknown" flash seen; second transport not tried. Screenshots `docs/superpowers/evidence/2026-10-11-upstream-sync-triage-d3-{en,ru}.png` exist only on that branch |
| run `network-diagnostics` (done), plan `docs/superpowers/plans/2026-08-21-network-diagnostics.md` | built the screen |
| run `telemetry-ready-data-states` state.md:34 | NetworkDiagnosticsVM writes no `ready` telemetry line; untouched here |
| commit d3fa6576 "Redesign media server speed test", c0663e0d | current screen layout and multi-shard sampling |

Duplicate: none. upstream-sync-triage handed P3 to this run explicitly.

## External findings

| Finding | Source |
|---------|--------|
| `getActiveNetwork()` API 23, needs `ACCESS_NETWORK_STATE`, returns null when there is no default network or it is blocked for the caller | https://developer.android.com/reference/android/net/ConnectivityManager#getActiveNetwork() (read 2026-11-26) |
| `getNetworkCapabilities(Network)` API 21, may return null for an unknown network | same page |
| Docs call `getNetworkCapabilities` a snapshot that "can go stale immediately"; continuous state should come from `registerDefaultNetworkCallback` (API 24+) | https://developer.android.com/reference/android/net/NetworkCapabilities (read 2026-11-26) |
| A VPN keeps `TRANSPORT_VPN` and carries the transports of its underlying network (example: loses CELLULAR, gains WIFI) | https://developer.android.com/develop/connectivity/network-ops/reading-network-state (read 2026-11-26) |
| Transports beyond the three mapped: BLUETOOTH, VPN, WIFI_AWARE (API 26), LOWPAN (27), USB (31), THREAD (34), SATELLITE (35) | NetworkCapabilities reference |

So a VPN over Ethernet reads as Ethernet with the branch's mapper; a pure VPN, Bluetooth or USB tether reads as `Unknown`.

## Options

| # | Option | Trade-offs | Size |
|---|--------|------------|------|
| A | Apply 3209128b + f037c052 (`app/` paths) as they are, settle wording | Fastest, device-checked once; "other" and "none" both show `Unknown`, which the request lists as two outcomes | S |
| B (recommended) | A, plus split `Unknown` into `Other` (network present, unmapped transport) and `None` (no active network or no capabilities); two more strings, provider test cases adjusted | Matches the request; "none" tells the user why the test will fail; same files as A | S |
| C | B, but observe `registerDefaultNetworkCallback` as a Flow so the label updates live | Follows the docs' guidance; adds callback lifecycle, unregister and a flow test for a label that is re-read at every run anyway | M |

Size note: A and B touch nine files across system, DI and UI but change no data shape or contract.

## Risks

| Risk | Mitigation |
|------|------------|
| Snapshot goes stale while the screen is open (docs) | re-read at `startRun` (branch already does); C if live updates matter |
| Label steals focus or flashes a fallback value (spec R10) | non-focusable `Text`, null state renders nothing; covered by D-3 and `NetworkDiagnosticsContentFocusTest` |
| Worktree build drops the box session (memory note) | install-preserve; refresh with a master build and auth-save first |
| Baseline profile lists NetworkDiagnostics classes | regeneration not required for one Text; note only |

## Open questions

| ID | Question | Recommended answer |
|----|----------|--------------------|
| Q1 | Show "other" and "none" separately (option B) or as one "unknown" (A)? | Separately, as the request lists them |
| Q2 | Label wording, ru / en | `Подключение: %1$s` / `Connection: %1$s`; values `Ethernet`, `Wi-Fi`, `Мобильная сеть` / `Cellular`, `Другое` / `Other`, `Нет подключения` / `No connection` |
| Q3 | Device check on the TV box only, or also a second transport (for example Wi-Fi off on the emulator)? | TV box only, one transport (memory note: box-only device work); unit tests cover the mapping |

## Sources

- Files: paths in the tables above; `docs/architecture/screens-and-di.md`, `docs/architecture/viewmodel.md:45-46`.
- Commits: 3209128b, f037c052, 97a0fca2, d3fa6576, c0663e0d; upstream dfd94862..569a1589.
- Branch files: `upstream-sync-triage:docs/superpowers/specs/2026-10-11-upstream-sync-triage-design.md`, `upstream-sync-triage:docs/superpowers/evidence/2026-10-11-upstream-sync-triage-device.md`.
- Runs: upstream-sync-triage, network-diagnostics, telemetry-ready-data-states.
- URLs: https://developer.android.com/reference/android/net/NetworkCapabilities, https://developer.android.com/reference/android/net/ConnectivityManager#getActiveNetwork(), https://developer.android.com/develop/connectivity/network-ops/reading-network-state
