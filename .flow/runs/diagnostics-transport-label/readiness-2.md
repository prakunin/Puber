# Readiness 2: diagnostics-transport-label

Verdict: **NOT READY**

Base 4c398e07. Read: spec and plan at worktree HEAD 3f5d2e14 (clean tree), `readiness-1.md`, `git diff 57c2287a 3f5d2e14`.

## Checks

| Check | Result |
|---|---|
| Every AC has a plan task and a Test design row | Pass, unchanged from `readiness-1.md#Checks` |
| Citations resolve in the code | Pass, 9 re-sampled (table below) |
| Build stays green after every task; dependency order | Pass, unchanged from round 1 |
| P0 rows name defect, `red`/`guard` and automated test | Pass for spec line 250; **Fail** for spec line 416 (fix 1) |
| Stays inside Out of scope; blocking questions answered | Pass; Q1 answered in the spec Open questions table (line 454) |
| Spec is one coherent document | **Fail**: fix 1 |

## Citations sampled

| Citation | Claim | Found at 4c398e07 |
|---|---|---|
| `NetworkDiagnosticsScreen.kt:13`, `:25` | `viewModelOf` import and call | yes |
| `NetworkDiagnosticsVM.kt:16-20`, `:26`, `:28-35`, `:47` | ctor, `onStart`, try/catch, `running` guard | yes |
| `NetworkDiagnosticsContent.kt:142-147` | subtitle `Text`, 12 dp spacer | yes |
| `NetworkDiagnosticsContent.kt:580` | `SpeedTestServer.title()` | yes |
| `NetworkDiagnosticsTestTags.kt:3` | tags object | yes |
| `values/strings.xml:53`, `values-en/strings.xml:397` | `diagnostics_screen_subtitle` | yes |
| `LauncherVM.kt:18` | injected `ioDispatcher` | yes |
| `PuberVM.kt:65`, `:108`, `:146` | `testStateValue`, `launch`, `testOnStart` | yes |
| FocusTest `:51`, `:66`, `:103`, `:174`, `:183` | three guards, `setContent`, `awaitFocus` | yes |

## Fix list

1. Commit 3f5d2e14 inserted 166 lines into the spec instead of editing one row: the spec now holds Visual, Test design and Open questions twice (headings at lines 126/292, 203/369, 288/454). The two copies disagree: line 250 has the fixed AC-7 device row, line 416 still has the round-1 row `AC-7 | device | P0 | M1, M2` with no defect and no manual mark; line 288 says `N/A` for Open questions while line 454 holds the Q1-Q4 table. Restore the spec to 57c2287a and change only the AC-7 device row (as at line 250), so each section appears once and Open questions keeps the Q1-Q4 table.
