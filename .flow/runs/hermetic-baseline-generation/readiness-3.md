# Readiness 3: hermetic-baseline-generation

Verdict: **READY**

Scope: fix-round Tasks 9-17 of `/home/dev/orca/workspaces/Puber/hermetic-baseline-generation/docs/superpowers/plans/2026-11-26-hermetic-baseline-generation.md` (section "Round 3: fix tasks"), spec `/home/dev/orca/workspaces/Puber/hermetic-baseline-generation/docs/superpowers/specs/2026-11-26-hermetic-baseline-generation.md`, at worktree HEAD c0704a3f. `git diff --stat 7d639a9b..HEAD` touches only the spec and the plan, so every citation below was read in code that Tasks 9-17 have not changed yet.

## Checks

| Check | Result |
|---|---|
| Every AC has a task and a Test design row | Pass. Covers lines: AC-1 T16; AC-2 T12, T14, T15, T16; AC-3 T12, T14, T16; AC-4 T13, T16; AC-5 T16; AC-6 T13, T16 (qa M3); AC-7 T16 (qa M5); AC-8 T17; AC-9 T16 (qa M6); AC-10 T9, T11; AC-11 T10. Every R3 test name in `test-design.md` is in the spec Traceability table |
| Citations resolve (sampled more than 30) | Pass: `DnsOverHttpsFactory.kt:19-23` google URL and bootstrap hosts; `HttpStack.kt:30` `useDoh`, `:34-39` system branch, `:45` factory call; `KinoPubConfig.kt:26` `pinnedEndpoint`, `:63` `IS_PINNED_ENDPOINT`, `:84-90` set/clear; `modules.kt:74` `useDoh = !IS_PINNED_ENDPOINT`, `:97` `singleOf(::AppUpdateRepository)`; `ApiDomainInteractor.kt:136` pinned early return, `:191-208` `switchToFound`, `:257-261` `buildAutoResolveCandidates`; `KinoPubApiClient.kt:959-965` hard-coded GitHub URL; `AppUpdateRepository.kt:18`; `AppUpdateRepositoryTest.kt` stubs at :33, :69, :80, :88, :108, :127 and `coVerify` at :121; `KinoPubApiClientSessionEndTest.kt:391-417` interceptor and `client()`; `CryptoPreferenceRepository.kt:41` and `:69-73` (clear writes an encrypted ""); `ICryptoPreferenceRepository.kt:5-19` (15 methods); `BaselineProfileApp.kt:50-80` and `:82-104`; `BaselineNetworkBlocker.kt:19-40`; `BaselineMockBackend.kt:97` lock, `:145`, `:148`, `:259-262` NAMES, `:281-300` `JourneyCheck`; `BaselineProfileTestControl.kt:72-88`; `BaselineProfileGenerator.kt:24-32`, `:197-211`; `BaselineMockSelfTest.kt:28-30`, `:48-61`; `HistoryPageResponse.kt:32-43` wire shape; `ContinueWatchingSource.kt:26-34`; `libs.versions.toml:88` okhttp-doh; `make-baseline-mock-cert.sh:42` SAN; `docs/release.md:117-124`, `:137` |
| D27 guard counts | Pass: all 11 substrings re-counted with `git show`, the same in both files: 1,1,1,4,1,1,1,2,1,13,1 at 5cd3e37e and 0 at 7d639a9b, so `profilesCoverProdColdStartPaths` is red before Task 16 as planned |
| No task leaves the build red; dependency order | Pass. Each signature change updates its callers in the same task: `HttpStack.create` (only `modules.kt` and `HttpStackTest` pass `useDoh`), `BaselineNetworkBlocker.install` and `BaselineInstrumentationEnvironment.configure` (T12, callers in the flavor), `AppUpdateRepository` (T11 also edits `modules.kt:97`). Order 9 -> 12 -> 13 and 11 -> 13, 12 -> 14 -> 15 -> 16 -> 17 matches each task's Consumes line. The new profile guard enters only in T16, together with the regenerated profiles |
| P0 rows | Pass: every R3 P0 row names its defect, is `red` or `guard`, and names its test in backticks; manual rows (M1, M3, M5, M6) are `guard` |
| Out of scope; blocking questions | Pass: main-source edits only in T9-T11 (Q5 answered yes); T12-T17 stay in the flavor, `:baselineprofile`, `tools/` and docs; Makefile, `railShows`, `StartupBenchmarks`, PR workflows untouched. Q5, Q6, Q7 and the architect's wording question are answered in `state.md` |

## Notes for the implementer (not blocking)

| # | Note |
|---|---|
| 1 | Spec D25 still describes `history.json` as `item`/`video`/`time`; the plan's "Corrections" table (wire shape `HistoryEntryResponse`, `media`) is the one to follow. Sync the spec row before the gate |
| 2 | Task 9 abbreviates the second `HttpStackTest` rename; the full name is `create_keepsTheAppEventListenerAndRequestsNoDohOne_whenResolverIsNull` (test-design AC-10 row) |
| 3 | Task 16 only allows route-table and rail-step fixes. Two D27 rules depend on device behaviour the plan cannot pin: `HttpEndpointProbe;->isReachable` is skipped when `isKnownReachable` (`ApiDomainInteractor.kt:176`) already trusts the pin, and the tvprovider rules need the emulator launcher to accept the publish. If either stays at 0, report it as blocked; do not loosen the guard |
