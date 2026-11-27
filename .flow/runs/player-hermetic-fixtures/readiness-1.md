# Readiness 1: player-hermetic-fixtures

Verdict: **READY**

Inputs: spec and plan in the worktree at df3a8725 (base f53f2144, plan uncommitted), `test-design.md`, `design-architect.md`, upstream 1d3ec39e.

## AC coverage

| AC | Plan task | Test design |
|---|---|---|
| AC-1 | Task 1 | T1-T4, M1 |
| AC-2 | Task 3 | T9 |
| AC-3 | Task 2 | T5-T8 |
| AC-4 | Tasks 3, 5; device step 2 | T12-T14, T18, T19, M2 |
| AC-5 | Task 4; device steps 2, 4 | T15, T16, M2, M4 |
| AC-6 | Tasks 5, 6 | T17 |
| AC-7 | Task 3 | T10 |
| AC-8 | Task 6 | M3 |
| AC-9 | Task 6; device step 1 (conductor, A2) | T11 |

## Citations sampled

All resolve to the claimed symbol in the code:

| Citation | Symbol |
|---|---|
| `PlaybackController.kt:160`, `:204`, `:260-264`, `:342-345`, `:499-519`, `:581-598`, `:624` | constructor, `player`, `onPlayerError` attribution to `currentStreamUrl`, `ParserException` -> `NEXT_URL`, `switchStream`, `release`, `okHttpClient.newBuilder()` |
| `PlaybackErrorPolicy.kt:30-36`, `PlaybackNetworkTuning.kt:18`, `:24` | no retry for `ParserException` only; `READ_TIMEOUT_SECONDS = 5L`; `MINIMUM_RETRY_COUNT = 5` |
| `BaselineNetworkBlocker.kt:27-42`, `BaselineInstrumentationEnvironment.kt:28-33`, `:54` | `selector`, `allowedOrigins()`, `install` call |
| `BaselineNetworkBlockerTest.kt:21`, `:45-53`; `BaselineNetworkSecurityConfigTest.kt:27` | `other = maxOf(h1, h2) + 1`; port-differs denial; no `domain-config` |
| `app/build.gradle.kts:246-247`, `:439-444`; `Makefile:133-141`; `pr-checks.yml:95` | `BASELINE_MOCK_*` fields; `androidTestImplementation` block; `itest`; `testProdDebugUnitTest` |
| upstream `HermeticTestServer.kt:48-55`, `:333-357`, `:480` | `start()`, `Delayed` (status sent, body gated), 10 s gate await |
| upstream test names | 5 + 9 + 6 in the three upstream device classes, matching the 20 rows of test-design "Upstream cases" |

## Order and build

Tasks 1 -> 2 -> 3 -> 4/5 -> 6 follow Consumes/Produces. Each task ends on the fast gate; tasks adding androidTest sources also run `:app:compileInstrumentationDebugAndroidTestKotlin`, whose base state Task 1 records first.

## Test design

Every P0 row (T1-T3, T5-T7, T9, T10, T12-T16) names its defect, is `red` or `guard`, and names the test or gate command in backticks. T16 guards T15 against a vacuous pass. Task 4 picks the malformed-body branch of T15 with a reason (`Delayed` sends the status before the gate).

## Scope and questions

The plan stays inside Out of scope: no `app/src/main` edit, no `.flow/config.yaml` edit on the branch, no Part B, no gate fix. Q1 (blocking) is answered: emulator `host.docker.internal:5555`, owner, 2026-11-27.

## Notes for the gate

1. Spec AC-6 still reads "not ported: needs a production change", and AC-9 says `check.full` passes in the worktree. Decisions widen AC-6 to "not ported: <reason>" and move the `check.full` edit to the conductor. The gate judges both ACs by the Decisions.
2. The Q1 answer puts device evidence on the emulator, not the TV box. The owner gave it explicitly for this run.
