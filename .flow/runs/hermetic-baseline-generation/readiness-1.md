# Readiness 1: hermetic-baseline-generation

Verdict: **NOT READY**

Spec and plan at worktree HEAD 64b351aa (base 5cd3e37e): `/home/dev/orca/workspaces/Puber/hermetic-baseline-generation/docs/superpowers/specs/2026-11-26-hermetic-baseline-generation.md`, `/home/dev/orca/workspaces/Puber/hermetic-baseline-generation/docs/superpowers/plans/2026-11-26-hermetic-baseline-generation.md`.

## Checks

| Check | Result |
|---|---|
| Every AC has a plan task and a Test design row | Fail for AC-9 (fix 2). AC-1 Tasks 4, 8; AC-2 Tasks 3, 5, 6; AC-3 Tasks 2, 4, 5, 8; AC-4 Tasks 5, 8; AC-5 Tasks 6, 8; AC-6 Tasks 1, 2 (+ qa M3); AC-7 Tasks 1, 2, 8; AC-8 Task 7. All nine have Traceability rows |
| Citations resolve (sampled, all hit) | Pass, see below |
| No task leaves the build red; dependency order | Pass: flavor and PEMs (1) before runtime (2); fixtures (3) before backend and wiring (4); generator (5) consumes 4; media (6) only needed at runtime; removal (7) after `requirePairedApp` is gone (5); red content test committed with the regenerated profiles (8). No main source references `app/src/nonMinifiedRelease` |
| P0 rows: defect named, `red`/`guard`, test in backticks | Fail (fix 1) |
| Inside Out of scope; blocking questions answered | Pass: Makefile logic, `railShows`, `StartupBenchmarks`, PR workflows untouched; Q1 and Q3 (blocking) answered in spec and `state.md` |

| Citation (worktree) | Resolves to |
|---|---|
| `PuberApp.kt:69`, `:85`, `:93`, `:96`, `:99`, `:112` | `open class PuberApp`, `configureRuntime()` call before `initDi()`, the three hooks, `+ runtimeModules()` |
| `KinoPubConfig.kt:5-11`, `:63`, `:84`; `data/di/modules.kt:74`, `:102`, `:147` | `ApiEndpointPreset`, `IS_PINNED_ENDPOINT` getter, `setPinnedEndpoint`; `useDoh = !IS_PINNED_ENDPOINT`, `CryptoPreferenceRepository` binding, `StandaloneDatabaseProvider` |
| `ICryptoPreferenceRepository.kt:3-20`, `:17-19`; `IAppUpdateInteractor.kt:8-28`; `domain/di/modules.kt:38`; `AppUpdateDownload.kt:96`; `AppUpdateInstaller.kt:62` | 15 methods incl. three session-end; internal interface; its binding; `StorageUnavailable`; `PermissionRequired` |
| `Models.kt:84`, `:215`, `:274`, `:579`, `:590`; `PlayerInteractor.kt:233-240`; `PuberDatabase.kt:34` | `videos`, `Posters`, `Video`, `VideoFile`, `VideoUrl`; `hls4`→`hls`→`http` order; private `NAME` |
| `PlayerVM.kt:1389`, `:1942`, `:2016`, `:2062`; `KinoPubApiClient.kt:145`, `:515`; `HttpsEnforcingInterceptor.kt:9-13` | `onPlaybackEnded`, `checkAutoMarkWatched`, `exitPlayer`, threshold; `okhttpcache`, `watching/marktime`; http→https rewrite |
| `app/build.gradle.kts:58-61`, `:119`, `:223`, `:342`, `:440-445`, `:446`; `baselineprofile/build.gradle.kts:18-28`, `:41-46`; `libs.versions.toml:14`, `:22`, `:88` | secret fields, dimension, `productFlavors`, `detektAll`, `devReleaseBaselineProfile` hook, `baselineProfile {}`; dev/prod flavors, deps; okhttp 5.5.1, koin 4.2.2, `okhttp-doh` |
| `BaselineProfileGenerator.kt:28-34`, `:47`/`:89`/`:141`/`:176`, `:144-148`, `:183-192`, `:194-200`, `:216` | `requireInPlaceReset`, four `requirePairedApp()` calls, playback KDoc, `requirePairedApp`, `railShows`, `RAIL_MAX_ITERATIONS` |
| `tools/generate-baseline-profile.sh:12-17`, `:20-22`, `:47-50`, `:53-86`, `:109`, `:117-121`, `:130`; `Makefile:141-179`, `:156`; `docs/release.md:99-133` | secret refusal, pairing vars, API 34 refusal, `is_paired`/`ensure_paired`, logcat filter, false warning, call; target, API check; profile section |
| `BaselineProfileContentTest.kt:12-17`, `:19`, `:36-44`; profile counts at base | `profileLines`, `hasRuleFor`, banned list; `Lcom/kino/puber/profile/` 20/20, `coil3/network/` 158, `coil3/decode/` 83, `media3/exoplayer/hls/` 376, `okhttp3/internal/tls/` 30 |
| Branch shas 0b6253a9, c17847cc, 5709c601 | All exist; every `git show 5709c601:<path>` the plan names exists; 12 fixture files as stated |

## Fix list

1. Test design Traceability: the P0 rows for AC-1 (M1), AC-6 (M3), AC-7 (M5) and AC-9 (M6) are marked neither `red` nor `guard`. Mark them `guard` (each already names its defect), as fix 2 of `hermetic-baseline-seams/readiness-1.md` required for the same row shape.
2. AC-9 is in no task's **Covers** line (`grep AC-9` on the plan prints nothing). Add it, for example "AC-9 (qa M6, after Task 8)" to Task 8, so the dev APK launch check on the box has an owner and an order in the plan.

## Notes (no fix required)

- Task 5 adds `@JvmStatic @BeforeClass`/`@AfterClass` to the generator's companion, which is `private companion object` (`BaselineProfileGenerator.kt:202`). JUnit 4 needs public static lifecycle methods; if the private companion yields non-public statics, the failure shows only in the Task 8 device run. Use a non-private companion for them.
- The branch route table (`git show 5709c601:.../BaselineMockBackend.kt`, lines 160-180) already serves `watching/movies`, `history`, `device/notify` POST and `oauth2/device`, which `research.md` lists for every launch but D14 does not require; Task 4 keeps them by re-porting the table.
