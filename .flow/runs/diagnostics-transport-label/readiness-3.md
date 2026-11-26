# Readiness 3: diagnostics-transport-label

Verdict: **READY**

Base 4c398e07. Read: spec and plan at worktree HEAD e46e2192 (clean tree), `readiness-1.md`, `readiness-2.md`, `git diff 57c2287a e46e2192`.

## Checks

| Check | Result |
|---|---|
| Every AC has a plan task and a Test design row | Pass, unchanged from `readiness-1.md#Checks` |
| Citations resolve in the code | Pass, 9 re-sampled at 4c398e07 (table below) |
| Build stays green after every task; dependency order | Pass, unchanged from round 1 |
| P0 rows name defect, `red`/`guard` and automated test | Pass: AC-1..AC-5 and AC-7 instrumented rows are `red`/`guard` with backticked tests; the AC-7 device row is marked manual outside the automated P0 set and names R1, R2 and wording defects (readiness-1 fix 1) |
| Stays inside Out of scope; blocking questions answered | Pass; Q1 (blocking) answered in spec `## Open questions` |
| Spec is one coherent document | Pass: the spec diff against 57c2287a is the AC-7 device row only (plus a dropped final newline); each section heading appears once (readiness-2 fix 1) |

## Citations sampled

| Citation | Claim | Found at 4c398e07 |
|---|---|---|
| `NetworkDiagnosticsScreen.kt:13`, `:23-26` | `viewModelOf` import, scope with `viewModelOf(::NetworkDiagnosticsVM)` | yes |
| `NetworkDiagnosticsVM.kt:16-20`, `:26`, `:28-35`, `:46-47` | ctor, `onStart`, try/catch, `startRun` and `running` guard | yes |
| `NetworkDiagnosticsContent.kt:142-147` | subtitle `Text`, 12 dp spacer | yes |
| `NetworkDiagnosticsContent.kt:580-585` | `SpeedTestServer.title()` | yes |
| `NetworkDiagnosticsTestTags.kt:3-8` | tags object | yes |
| `values/strings.xml:53`, `values-en/strings.xml:397` | `diagnostics_screen_subtitle` | yes |
| `LauncherVM.kt:18` | injected `ioDispatcher` | yes |
| `NetworkDiagnosticsVMTest.kt:39-46` | `vm()` | yes |
| FocusTest `:51`, `:66`, `:103`, `:125`, `:143`, `:160`, `:174`, `:183` | six cases incl. the three guards, `setContent`, `awaitFocus` | yes |

## Fix list

N/A: no fixes.
