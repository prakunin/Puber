# Review tests, round 1 (03265fbc)

LENS: tests  ROUND: 1  BASE: 4c398e07

### F1 [major] The new Koin block is the highest-scored risk, and no automated test or device run has checked it
- Where: app/src/main/java/com/kino/puber/ui/feature/device/diagnostics/NetworkDiagnosticsScreen.kt:28-43
- Problem: viewModelOf(::NetworkDiagnosticsVM) replaced by scoped { ConnectionTransportProvider(...) } and viewModel { NetworkDiagnosticsVM(...) }. Test design scores R1 at 6 and gives it only to manual M1, which could not run (box: Unauthorized: user_banned). The project already has JVM Koin module tests (app/src/test/kotlin/com/kino/puber/data/di/EpisodeScheduleRepositoryModuleTest.kt, ApiModuleHttpStackTest.kt, WatchStateRepositoryModuleTest.kt).
- Failure scenario: a missing binding or wrong get() type keeps the suite green and crashes Settings -> network diagnostics with NoDefinitionFoundException.
- Fix: JVM test that starts a koinApplication with mocked NetworkDiagnosticsInteractor, ErrorHandler, AppRouter and an androidContext returning a mocked ConnectivityManager, loads the screen's module and resolves NetworkDiagnosticsVM from the screen scope (buildModule is private @Suppress("unused"): reach it as DIScope does, or make it internal).

### F2 [minor] onStart_keepsLoadingServer_whenTransportReadThrows passes even without the try/catch it is meant to check
- Where: app/src/test/kotlin/com/kino/puber/ui/feature/device/diagnostics/vm/NetworkDiagnosticsVMTest.kt:126-136
- Problem: without the catch, the exception goes to PuberVM.launch's DefaultExceptionHandler (core/ui/PuberVM.kt:108-121) and the relaxed ErrorHandler mock; currentServer loads in a separate coroutine; both assertions still hold.
- Fix: keep the ErrorHandler mock in a field and verify(exactly = 0) { errorHandler.proceedInvoke(any(), any()) }.

Suite state: check.full on 03265fbc BUILD SUCCESSFUL (unit tests UP-TO-DATE from the earlier green run). AC-1..AC-5 and R5 tests fail when behaviour breaks; AC-4 test does tell main from IO (UnconfinedTestDispatcher main). AC-6 passed on the box. AC-7 rewritten test not run since 03265fbc. AC-8 owner judgement. Red runs recorded for every P0 red row.

### Unproven
- NetworkDiagnosticsContentFocusTest.kt:189 assertNotEquals(true, Focused) always holds on a non-focusable Text; focus is guarded by the RequestFocus check and Amsterdam assertIsFocused.
