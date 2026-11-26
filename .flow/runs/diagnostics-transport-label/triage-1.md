# Triage round 1: diagnostics-transport-label

Reviewed head: 03265fbc (base 4c398e07). Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md, review-codex-1.md (ran, review + adversarial), review-antigravity-1.md (ran, clean), review-state-path-1.md (clean), review-ux-1.md (no findings; on-device look NOT CHECKED). qa rounds 1-2: qa-report.md.
Each finding was checked against the code in the worktree by the conductor.

| # | Finding | Sources | Severity | Verdict | Reason / action |
|---|---------|---------|----------|---------|-----------------|
| 1 | The hand-written Koin block in NetworkDiagnosticsScreen.kt:28-43 (scoped provider + viewModel { }) has no automated check, and the device check M1 is blocked by the account ban | tests F1, codex rejected (coverage) | major | fix | Highest risk R1 (crash on screen open). JVM module tests exist in app/src/test/kotlin/com/kino/puber/data/di/. Add a test that loads the screen's module with mocked deps (interactor, ErrorHandler, AppRouter, androidContext returning a mocked ConnectivityManager) and resolves NetworkDiagnosticsVM from the screen scope; make buildModule internal (and drop @Suppress("unused")) if needed |
| 2 | onStart_keepsLoadingServer_whenTransportReadThrows passes without the try/catch | tests F2 | minor | fix | Cheap: keep the ErrorHandler mock and verify(exactly = 0) { proceedInvoke } |
| 3 | Overlapping transport reads: an older read can land last and overwrite a newer one | codex (confirmed), blind, edge, state-path (unproven) | minor | fix | Cheap and named by four lenses: keep one transportJob, cancel it before relaunching; add a test where the first read is held and the second completes first |
| 4 | Label not updated during a run | blind unproven | - | reject | Out of scope by spec (live callback, research option C) |
| 5 | VPN active network may read Other | blind unproven | - | reject | Platform behaviour documented in research.md; VPN reports the underlying transports on supported Android TV versions; Other is a correct label otherwise |
| 6 | getSystemService(ConnectivityManager) could return null | blind, edge unproven | - | reject | Same assumption as data/di/modules.kt:78; no Android TV target without the service |
| 7 | assertNotEquals(true, Focused) is vacuous on a non-focusable Text | tests unproven | - | reject | The test also asserts no RequestFocus action and Amsterdam focus; that is the real guard |
| 8 | serverCard_startsThatServerOnPress fails on the box, also on base 4c398e07 | qa rounds 1-2 | - | defer | Pre-existing, not caused by this change; new run `diagnostics-server-card-itest-red` |

Deferred: #8 -> diagnostics-server-card-itest-red. Accepted: none.
NOT CHECKED carried to the gate: AC-7 device part (M1, M2) and AC-8 (M3): Settings returns Unauthorized: user_banned on the box.
