# Readiness 1: diagnostics-transport-label

Verdict: **NOT READY**

Base 4c398e07. Read: spec and plan in the worktree (plan untracked at the time of reading), `brief.md`, source commits 3209128b and f037c052.

## Checks

| Check | Result |
|---|---|
| Every AC has a plan task and a Test design row | Pass: AC-1..AC-3 Task 1; AC-4, AC-5 Task 2; AC-6, AC-7 Task 3 (device part with qa-device M1, M2); AC-8 is M3 (owner) |
| Citations resolve in the code | Pass, 14 sampled (table below) |
| Build stays green after every task; dependency order | Pass: Task 1 adds a standalone class; Task 2 changes the VM constructor, the Screen module and `vm()` in VMTest in one commit; Task 3 depends only on Task 2's `transport` field |
| P0 rows name defect, `red`/`guard` and automated test | **Fail**: one row, fix 1 |
| Stays inside Out of scope; blocking questions answered | Pass: no network callback, no global `ConnectivityManager` binding, no stand; Q1 (blocking) answered |

## Citations sampled

| Citation | Claim | Found at 4c398e07 |
|---|---|---|
| `NetworkDiagnosticsScreen.kt:25`, `:13` | `viewModelOf(::NetworkDiagnosticsVM)`, its import | yes |
| `NetworkDiagnosticsVM.kt:16-20`, `:26`, `:28-35`, `:47` | ctor, `onStart`, try/catch shape, `running` guard | yes |
| `NetworkDiagnosticsVM.kt:61-78` | `publish` | yes |
| `NetworkDiagnosticsViewState.kt:14-20` | `@Immutable` state, no `transport` | yes |
| `NetworkDiagnosticsContent.kt:142-147`, `:40`, `:580`, `:389-393`, `:106-114` | subtitle, 12 dp spacer, `testTag` import, `SpeedTestServer.title()`, `initialFocusRequester`, launch focus | yes |
| `NetworkDiagnosticsTestTags.kt:3`, `NetworkDiagnosticsPreview.kt:18` | tags object, ready preview | yes |
| `values/strings.xml:53`, `values-en/strings.xml:397` | `diagnostics_screen_subtitle` | yes |
| `data/di/modules.kt:78` | inline `ConnectivityManager` | yes |
| `LauncherVM.kt:18` | injected `ioDispatcher` | yes |
| `PuberVM.kt:65`, `:108`, `:146` | `testStateValue`, `launch`, `testOnStart` | yes |
| `NetworkDiagnosticsVMTest.kt:39-46`, `:53`, `:65`, `:91`, `:92-104` | `vm()`, three cases, `Channel` setup | yes |
| FocusTest `:51`, `:66`, `:103`, `:125`, `:143`, `:160`, `:174`, `:183` | six cases incl. the three named guards, `setContent`, `awaitFocus` | yes |
| `MainDispatcherExtension` | `UnconfinedTestDispatcher` | yes |
| 3209128b provider; f037c052 Screen and VM | `Unknown` for nulls and `else`; explicit `viewModel { }`, `refreshTransport` with `withContext(ioDispatcher)` | yes |

## Fix list

1. Spec Test design, Traceability row `AC-7 | device | P0 | M1, M2`: a P0 row with no `red`/`guard` mark, no automated test and no named defect. Either lower it to P1 (manual), or keep P0 and state the defects it catches (R1 Koin crash on open, R2 focus, wording) and mark it as a manual check outside the P0 automated set. Copy the change to the plan if it cites the row.
