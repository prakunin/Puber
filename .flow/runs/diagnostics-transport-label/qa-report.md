Round 3

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; ConnectionTransportProviderTest 7/7, NetworkDiagnosticsVMTest 11/11, NetworkDiagnosticsScreenModuleTest 1/1, 0 failures)
DEVICE: not needed this round: fix commits 03265fbc..58773294 changed a VM job cancel, a visibility change of buildModule and JVM tests; device-side tests are unchanged; the diagnostics screen is unreachable (account ban, user_banned)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | ConnectionTransportProviderTest precedence cases green |
| AC-2 | PASS | ConnectionTransportProviderTest Other case green |
| AC-3 | PASS | ConnectionTransportProviderTest null network/capabilities green |
| AC-4 | PASS | NetworkDiagnosticsVMTest (null before start, set on start, ioDispatcher) |
| AC-5 | PASS | NetworkDiagnosticsVMTest (re-read on start, none while running; 11th test covers the transportJob cancel); NetworkDiagnosticsScreenModuleTest resolves the VM from the screen module |
| AC-6 | PASS | round 2 itest transportLine_isAbsent_whenTransportNull on the box (test unchanged) |
| AC-7 | NOT CHECKED (device part) | itests from round 2 passed and are unchanged; M1/M2 on the box unreachable (user_banned) |
| AC-8 | NOT CHECKED | owner's visual call (M3); no screenshots possible |
TESTS ADDED: none (the fix commits added NetworkDiagnosticsScreenModuleTest and a VM test; I added none)
EVIDENCE: n/a this round (no device); round 2 evidence stays at docs/superpowers/evidence/2026-11-26-diagnostics-transport-label-device.md
DEFECTS: none new. Known and unrelated: serverCard_startsThatServerOnPress fails identically on base 4c398e07.
UNAVAILABLE: M1/M2 (Settings unreachable, account ban)
MANUAL: as round 1 item 2.

---
Round 2

CHECK: ./tools/agentw :app:compileDevDebugKotlin -> pass (BUILD SUCCESSFUL). Full check not re-run (test-only fix; round 1 full check passed).
DEVICE: 192.168.1.110:5555 (lease released)
ACS: AC-1..AC-5 PASS (round 1 unit tests). AC-6 PASS (itest). AC-7 PARTIAL: transportLine_isShownAndNotFocused_whenEthernet now PASS, first-focus guards PASS; M1/M2 on the box still unreachable (account ban). AC-8 NOT CHECKED (owner's visual call, M3).
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/diagnostics-transport-label/docs/superpowers/evidence/2026-11-26-diagnostics-transport-label-device.md (audit + commit: evidence_audit_status=passed
evidence_dir=/home/dev/orca/workspaces/Puber/diagnostics-transport-label/docs/superpowers/evidence
files_scanned=361
fc1a6271 Record round 2 device evidence for the transport label)
DEFECTS: 1 resolved. Remaining: serverCard_startsThatServerOnPress fails (expected Start(server=Moscow), was empty), identical on base 4c398e07, not caused by this change.
UNAVAILABLE: M1/M2 (Settings unreachable, user_banned; not retried)
MANUAL: as round 1 item 2.

---
Round 1 note (kept below)

Round 1

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; ConnectionTransportProviderTest 7/7, NetworkDiagnosticsVMTest 10/10)
DEVICE: 192.168.1.110:5555 (lease released; install-preserve outcome installed, own dev APK, no uninstall; final sha matches the worktree APK)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | ConnectionTransportProviderTest, four precedence cases green |
| AC-2 | PASS | ConnectionTransportProviderTest Other case green |
| AC-3 | PASS | ConnectionTransportProviderTest null network and null capabilities cases green |
| AC-4 | PASS | NetworkDiagnosticsVMTest: null before start, set on start, read via ioDispatcher |
| AC-5 | PASS | NetworkDiagnosticsVMTest: re-read on start, no re-read while running |
| AC-6 | PASS | itest transportLine_isAbsent_whenTransportNull passed on the box |
| AC-7 | NOT CHECKED | itest first-focus guards pass; transportLine_isShownAndNotFocused_whenEthernet FAILS (test defect, see 1); M1/M2 on the box impossible (see UNAVAILABLE) |
| AC-8 | NOT CHECKED | owner's visual call (M3); no screenshots could be taken |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/diagnostics-transport-label/docs/superpowers/evidence/2026-11-26-diagnostics-transport-label-device.md (audit passed; committed b29ca6c8)
DEFECTS:
1. Test defect: NetworkDiagnosticsContentFocusTest.transportLine_isShownAndNotFocused_whenEthernet (androidTest NetworkDiagnosticsContentFocusTest.kt:194) fails on the box, twice. The node is present with text "Подключение: Ethernet", but a non-focusable Text has no Focused semantics key, and assertIsNotFocused() demands Focused = false. Production looks right. Fix: assert the key is absent or false, e.g. node.config.getOrNull(SemanticsProperties.Focused) != true (the file already imports getOrNull). Reproduce: make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.ui.feature.device.diagnostics.NetworkDiagnosticsContentFocusTest
2. Not caused by this change: serverCard_startsThatServerOnPress fails identically on base 4c398e07 (checked by building base app/src, then restored). 
UNAVAILABLE: M1/M2. Diagnostics opens only from device settings (DeviceSettingsVM.kt:245), whose load returns Unauthorized: user_banned on the box account (the ban from 2026-11-25 still stands). Settings shows its error screen, so the diagnostics screen is unreachable. No workaround tried (no mirror change, no language switch); the app stayed logged in (no pairing screen), nothing uninstalled or cleared. Auth snapshot saved at .auth/com.kino.puber.stage.tar.
MANUAL: 1. Fix defect 1 and re-run the itest. 2. Once the account is usable, open Settings -> network diagnostics on the box in ru and en and check "Подключение: Ethernet" / "Connection: Ethernet", first focus on a server card, D-pad up never on the line, and judge size and colour (M3). Do not expect gauge or card clipping: the height budget is 24 dp.
