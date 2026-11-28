# Readiness 1: dependency-patch-bump-nov

Verdict: **READY**

Spec `docs/superpowers/specs/2026-11-28-dependency-patch-bump-nov.md` (commit 9a935e73), plan `docs/superpowers/plans/2026-11-28-dependency-patch-bump-nov.md` (untracked in wt), base b1b3c51a.

## Coverage

| AC | Plan task | Test design row | OK |
|---|---|---|---|
| AC-1 | 3 | catalog diff | yes |
| AC-2 | 3 | properties diff, jar sha256, `gradlew`/`gradlew.bat` stat and `ls-files --eol` | yes |
| AC-3 | 1 (base log), 3 (diff) | `help --warning-mode all` diff | yes |
| AC-4 | 1 (base, C1), 3 (C3) | `_Impl` listing vs `ksp-impl-base.txt`, `app/schemas` clean | yes |
| AC-5 | 2 (unit), 3 (build) | `data.api` tests, `check.full`, `:app:assembleProdRelease` | yes |
| AC-6 | qa-device after 3 | `make itest` per class in `data/db/` | yes |
| AC-7 | qa-device after 3 | M1-M5 | yes |

## Citations sampled (at b1b3c51a / wt HEAD)

| Citation | Resolves to | OK |
|---|---|---|
| `gradle/libs.versions.toml:2,4,7,11,15` | `agp` 9.5.1, `ksp` 2.3.12, `composeBom` 2026.09.00, `lifecycleRuntimeKtx` 2.11.0, `ktor` 3.6.1 | yes |
| `gradle-wrapper.properties:3`; jar sha | `gradle-9.9.1-bin.zip`; `621008af...cdaf8`, equals plan value | yes |
| `app/build.gradle.kts:75`, `:171-178`, `:318-336`, `:375`, `:405`, `:423` | `abortOnError = true`, debug signing fallback, detekt classpath override, lifecycle-runtime-ktx, kotlinx-datetime, `ksp(room.compiler)` | yes |
| `PuberDatabase.kt:19-23`, `:27-31`, `:36`, `:48` | three AutoMigrations, three DAOs, `create`, `fallbackToDestructiveMigration` | yes |
| `ImageHttpClient.kt:9`, `modules.kt:79`, `PuberApp.kt:153` | `createImageHttpClient`, qualifier binding, `KtorNetworkFetcherFactory` | yes |
| `KinoPubApiClient.kt:1006-1018`, `:1027-1029`, `:181`; `KtorPlugins.kt:87`, `:106` | streamed `prepareGet`, temp delete + `Result.failure`, debug `CurlLogger`, `readTextLimited`, `readRemaining` | yes |
| `DeviceSettingsContent.kt:213-214` | two `currentStateFlow.first` calls | yes |
| Test names at `KinoPubApiClientDownloadTest.kt:54`, `ImageHttpClientTest.kt:37`, `KinoPubApiClientAuthProbeTest.kt:76`, `PuberDatabaseMigrationTest.kt:126` | named functions exist | yes |
| `Makefile:23` | `GRADLE ?= ./gradlew` | yes |
| `.gitattributes` | `*.bat eol=crlf`, `gradlew eol=lf`; both `i/lf` | yes |

`git diff --stat a546f5a5 b1b3c51a -- gradle/ gradlew gradlew.bat buildSrc app/build.gradle.kts` is empty, so ACs citing a546f5a5 hold against b1b3c51a.

## Checks

| Check | Result |
|---|---|
| Build green per task, dependency order | yes: each task ends on the fast gate; Task 3 consumes Task 1's base logs; wrapper before AGP with a fast gate between |
| P0 rows: defect named, `guard`, test in backticks | yes (AC-1, 2, 4, 5, 6, 7; no source change, so no `red`) |
| Inside Out of scope | yes: `composeBom`, `Versions.kt:12`, baseline profiles, `distributionSha256Sum` listed as not edited |
| Blocking questions answered | yes: Q1 answered; Q2-Q4 non-blocking and answered |

## Notes (non-blocking)

1. Spec Problem section still cites `libs.versions.toml:2,4,12,16` and `:8`; Q4 records the architect's lines (`:2,4,11,15`, `:7`) as authoritative.
2. The plan file is not committed yet; commit it with the spec before Task 1.
