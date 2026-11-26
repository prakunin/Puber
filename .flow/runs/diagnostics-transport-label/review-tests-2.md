# Review tests, round 2 (03265fbc..58773294)

LENS: tests  ROUND: 2  BASE: 4c398e07

No findings.
Ran: testDevDebugUnitTest --tests 'com.kino.puber.ui.feature.device.diagnostics.*' (NetworkDiagnosticsScreenModuleTest 1, NetworkDiagnosticsVMTest 11, 0 failed, XML from this run) and :app:detektAll, both pass.
- #1: NetworkDiagnosticsScreenModuleTest loads the real buildModule and resolves the VM from the screen scope; fails if the scoped provider or viewModel {} is dropped; red run recorded (NoDefinitionFoundException).
- #2: without the try/catch the exception reaches PuberVM.launch's default handler (PuberVM.kt:108-121) and errorHandler.proceedInvoke; the new verify(exactly = 0) then fails.
- #3: startRun_keepsNewerTransport_whenAnOlderReadLandsLast releases reads by hand; with the cancel held[0] does not call the provider; without it Ethernet overwrites Wifi; red on the old code, not timing-dependent.
Earlier tests still pass; no assertions removed.
Carried over: AC-7 device part (M1, M2) and AC-8 (M3) NOT CHECKED, account banned on the box.
