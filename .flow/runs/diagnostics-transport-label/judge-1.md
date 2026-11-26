# Gate - diagnostics-transport-label - round 1

**Decision:** PASS
**Head:** 58773294   **Base:** 4c398e07

## Done as asked

The diff (`git -C /home/dev/orca/workspaces/Puber/diagnostics-transport-label diff 4c398e07...HEAD`) adds `ConnectionTransportProvider` with five outcomes (`core/system/ConnectionTransportProvider.kt`), reads it off main in `onStart` and after the `running` guard in `startRun`, cancelling the previous read (`NetworkDiagnosticsVM.kt:53-65`), and renders one non-focusable `bodyMedium`/`onSurfaceVariant` line inside `state.transport?.let` (`NetworkDiagnosticsContent.kt:148-156`). Strings in `values` and `values-en` match spec Q2. No live network callback, no design stand, no global `ConnectivityManager` binding: Out of scope respected.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `ConnectionTransportProviderTest` four precedence cases | qa-report round 3 check.full green, 7/7 | PASS |
| AC-2 | `transport_isOther_whenNoMappedTransport` | same run | PASS |
| AC-3 | `transport_isNone_whenActiveNetworkNull`, `transport_isNone_whenNoCapabilities` | same run | PASS |
| AC-4 | `state_transportNull_beforeStart`, `onStart_setsTransportInState_whenProviderReturnsWifi`, `onStart_readsTransportOnIoDispatcher_notOnMain` | qa round 3, NetworkDiagnosticsVMTest 11/11 | PASS |
| AC-5 | `startRun_rereadsTransport_whenNetworkChangedSinceStart`, `start_doesNotRereadTransport_whileRunning`, `startRun_keepsNewerTransport_whenAnOlderReadLandsLast`; `NetworkDiagnosticsScreenModuleTest` | same run | PASS |
| AC-6 | itest `transportLine_isAbsent_whenTransportNull` on 192.168.1.110:5555 | evidence `2026-11-26-diagnostics-transport-label-device.md` round 2; Content unchanged since 03265fbc | PASS |
| AC-7 | itest `transportLine_isShownAndNotFocused_whenEthernet` + first-focus guards (PASS, round 2 evidence); M1/M2 through Settings on the box | box account `Unauthorized: user_banned`, Settings unreachable | NOT CHECKED (device part; manual item 1) |
| AC-8 | M3, owner's visual call | no screenshots possible | NOT CHECKED (manual item 2) |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| Koin block in `buildModule` has no automated check (major) | review-tests-1 F1 | fixed | 32682fc3, `NetworkDiagnosticsScreenModuleTest` resolves the VM through the real `buildModule` |
| Throw test passes without the try/catch (minor) | review-tests-1 F2 | fixed | 7f81684c: `verify(exactly = 0) { errorHandler.proceedInvoke(any(), any()) }` in VMTest |
| Older transport read can overwrite a newer one (minor) | codex-1, blind/edge/state-path-1 | fixed | 7f81684c, `transportJob?.cancel()`; test `startRun_keepsNewerTransport_whenAnOlderReadLandsLast` |
| Label not updated during a run | blind-1 | rejected, accepted | Spec Out of scope (live callback) |
| VPN reads as Other | blind-1 | rejected, accepted | Spec AC-2 names a pure VPN -> `Other` as intended |
| `getSystemService` may return null | blind-1, edge-1 | rejected, accepted (spot-checked) | `data/di/modules.kt:78` makes the same call for `KinoPubApiClient` |
| `assertNotEquals(true, Focused)` vacuous | tests-1 | rejected, accepted (spot-checked) | Same test asserts `RequestFocus` absent and Amsterdam `assertIsFocused()` |
| `serverCard_startsThatServerOnPress` red on the box | qa rounds 1-2 | deferred | Fails identically on base 4c398e07; run `.flow/runs/diagnostics-server-card-itest-red` exists |
| AC-7 not verified through Settings on a device | codex-2 | carried | Manual item 1 below |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | Binder call on `Dispatchers.IO`; one read per open and per run; stable enum field in an `@Immutable` state; one 24 dp layout step on open |
| Reliability | Read failure caught and logged, label stays as before; cancellation rethrown; null transport renders nothing; state not saved, re-read on next `onStart` |
| Maintainability | Provider in `core/system`, screen-scoped binding in `buildModule`, `puberViewModel` in `DIScope`, strings in `strings.xml`; JUnit 5 + MockK tests; no dead code |
| Security / privacy | No secrets or account data in code or logs; evidence audited (`evidence_audit_status=passed`, qa round 2) |

check.full not re-run: qa round 3 (07:46) postdates head 58773294 (07:45) and covers 03265fbc..58773294.

## Manual checks for the owner

Precondition for both: the KinoPub ban on the box account is lifted. Refresh the stage session with a master build and `make DEVICE=192.168.1.110:5555 auth-save`, lease the box, install the worktree dev APK with `tools/android-apk-install-preserve` (`com.kino.puber.stage` only), box on Ethernet.

| # | AC | Steps | Expected |
|---|----|-------|----------|
| 1 | AC-7 | App language ru; drawer -> Settings -> network diagnostics with the D-pad; wait 2 s; `KEYCODE_DPAD_UP` twice, `KEYCODE_DPAD_DOWN`; screenshot; start a run on the focused card, screenshot at the end. Repeat in en | Screen opens without a crash; `Подключение: Ethernet` / `Connection: Ethernet` under the subtitle; first focus on the current server card; D-pad up never lands on the line; gauge and both cards unclipped |
| 2 | AC-8 | Compare the item 1 screenshots with title and subtitle | Line reads as the subtitle's twin in size and colour; owner accepts |

## Must fix

None.

## Concerns for the owner

None.
