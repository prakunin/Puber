# Readiness 1: dependency-update-stable

Plan: `docs/superpowers/plans/2026-11-12-dependency-update-stable.md` (worktree, untracked). Spec at 619fbd8d. Test design: `test-design.md`.

**Verdict: READY**

## Coverage

| AC | Plan task | Test design row |
|---|---|---|
| AC-1 | 4 | build P0 guard, `git diff 4db9c0d4 -- gradle/...` |
| AC-2 | 1 (base log), 4 (diff) | build P1, `--warning-mode all` diff |
| AC-3 | 3, 4 | build P0 guard, full gate + `--stat` / `-G@Suppress` diff |
| AC-4 | 2 | unit P0 guard, `KinoPubApiClientAuthProbeTest.sessionExpiredFailureRecordsAuthErrThenThrowsUnauthorized`, `...nothingIsRecordedWhenTheLogIsOff`; build P1 grep |
| AC-5 | 4 | build P0 guard, `:app:assembleProdRelease`; device P2 M6 |
| AC-6 | 1, 4; qa-device | build P0 guard, `_Impl` listing; instrumented P1 `WatchStateDaoTest`, `CachedPayloadDaoTest` |
| AC-7 to AC-10 | qa-device after Task 4 | device P0 guard, M1 to M5 |

## Citations sampled (worktree at 619fbd8d)

| Citation | Resolves to |
|---|---|
| `gradle/libs.versions.toml:2-36` (all Target rows), `:13`, `:47-49`, `:75` | every From value and line matches; `detekt-compose-rules` 1.4.0 at `:75` |
| `gradle/wrapper/gradle-wrapper.properties:3` | `gradle-9.7.0-bin.zip`, no `distributionSha256Sum` |
| `app/build.gradle.kts:74-75`, `:110`, `:171-174`, `:183-184`, `:307-322`, `:439` | `lint {` / `abortOnError = true`, end of `disable`, debug.jks fallback, minify/shrink, `withType<Detekt>` classpath block, `maxAgpVersion = false` |
| `KinoPubApiClient.kt:125-129`, `:150`, `:197-200`, `:1187-1190` | `Json` config, `sendWithoutRequest`, unmarked refresh `post`, `shouldSendKinoPubToken`; no `markAsRefreshTokenRequest` in `app/src/main` |
| `data/di/modules.kt:57-61` | `OkHttpClient` with `DnsOverHttpsFactory` |
| `data/db/PuberDatabase.kt:31`, `:38`; `DatabaseTransaction.kt:22` | `Room.databaseBuilder`, `fallbackToDestructiveMigration`, `withWriteTransaction` |
| `KinoPubApiClientAuthProbeTest.kt:76`, `:88` | the two named tests; refresh refused by an interceptor (500) |
| `buildSrc/src/main/kotlin/Versions.kt:12` | `CompileSdk = 37` |
| Five `_Impl.kt` names | match main checkout `app/build/generated/ksp/{devDebug,prodRelease}/.../data/db/` |
| `Makefile` `GRADLE ?=`, `itest` with `TESTS` | `:23`, `:128-135` |

## Checks

| Check | Result |
|---|---|
| No task leaves the build red; order by dependency | yes: C1-C3 on base toolchain, each ends on fast gate (+ tests); C4 steps gated one by one with D5 stop |
| P0 rows name the defect, `guard`/`red`, named test | yes: all `guard`, build/device rows name the command or M-step; AC-4 names test methods in backticks |
| Inside Out of scope | yes: `:13`, `:75`, `Versions.kt`, `app/schemas/`, baseline profiles listed as not edited |
| Blocking open questions | none; Q1-Q4 answered |

## Notes for the conductor (not blocking)

1. Spec AC-4 cites `KinoPubApiClientAuthProbeTest.kt:76-132`; the tests end at `:95`, the rest is the server and client helpers. The plan cites `:76`, `:88` correctly.
2. Task 4's AC-3 lint-baseline check is prose ("no `lint-baseline.xml` or `baseline =` in `app/`"), and `app/build.gradle.kts:264` already has `baseline = file(...detekt-baseline.xml)`, so a plain grep hits on base. Evidence should quote a diff instead: `git diff 4db9c0d4 -G'baseline' -- app/build.gradle.kts` empty and `ls app/lint*.xml` finds nothing.
3. Spec plus four task commits is five before evidence commits; the owner's under-five rule needs a squash before ship.
