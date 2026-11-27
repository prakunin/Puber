# Readiness 2: baseline-mock-protocol-parity

Verdict: **READY**

Spec and plan at worktree commit 28c49373 (worktree clean). Base 08f50b81. Round 1: [readiness-1.md](readiness-1.md).

## Round 1 fix

| # | Fix | Result |
|---|---|---|
| 1 | Phosphoricons Perf row 51 / 69, threshold 56, same in plan Task 4 | Done: spec Perf row `51 | 69 | >= 56`, plan Task 4 `phosphoricons >= 56` with `grep -c 'Lcom/adamglin/phosphoricons/'`. Rerun on the committed profiles: 51 at 08f50b81, 69 at 5cd3e37e |

The other gating baselines also match a rerun of the stated method: `http1` 0 / 51, `serialization/internal` 165 / 234 (threshold 188), `serialization/descriptors` 137 / 203 (threshold 163).

## AC coverage

Unchanged from round 1 (readiness-1.md, AC coverage); every AC-1..AC-8 has a plan task and a Test design row.

## Citations sampled (round 2, new ones)

| Citation | Claim | Result |
|---|---|---|
| `BaselineMockBackend.kt:116-123`, `:125`, `:153-158`, `:213-225`, `:257` | `route(...)`, `json(...)`, item/github/dns routes, text/binary builders, `BaselineScenario` | resolves |
| `Models.kt:57`, `:95-96`, `:164-176` | `duration`, `fps: Float?`, `age_rating`, `Item.isFullyWatched()` (movie: `watched > 0`) | resolves |
| `DetailsInteractor.kt:49-52` | movie bookmark state from non-empty `bookmarks` | resolves |
| `BaselineInstrumentationEnvironmentTest.kt:48-59` | `configure_pinsApiToTheMockHostAndResolver`, DoH on 18765 | resolves |
| `BaselineNetworkBlockerTest.kt:17-21`, `:26-33`, `:46-53`, `:55-63`, `:65-75`, `:77-85`, `:87-95` | allowed set, `assertDeadProxy`, the five named tests | resolves |
| `BaselineFixtureContentTest.kt:25`, `:26`, `:54-64`, `:66-75` | origin literal, `lenientJson` without `isLenient`, URL and banned-key guards | resolves |
| `BaselineMockSelfTest.kt:31-44`, `:47-57`, `:64-65`, `:72` | `setUp`, `dohResolver` on `boundPort`, `get`, `/github` in `hitStartupRoutes` | resolves |
| `BaselineProfileContentTest.kt:19`, `:60-68`; `emulator-resource-lock.sh:17-28` | `hasRuleFor`, image/TLS/HLS test, lock usage | resolves |

## Order, build, scope

Unchanged from round 1: order 1 -> 2 -> 3 -> 4, each task ends with a green fast gate and one commit, no `app/src/main/java` or `app/src/main/res` file, no `imdb`/`trailer` fixture, Q1-Q3 answered and non-blocking, every P0 row names its mutation, is `red` or `guard` (device rows are manual checks) and names its test in backticks.

## Fix list

None.
