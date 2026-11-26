# Readiness 1: hermetic-baseline-seams

Verdict: **NOT READY**

Spec and plan: `/home/dev/orca/workspaces/Puber/hermetic-baseline-seams/docs/superpowers/specs/2026-11-26-hermetic-baseline-seams.md`, `/home/dev/orca/workspaces/Puber/hermetic-baseline-seams/docs/superpowers/plans/2026-11-26-hermetic-baseline-seams.md` (worktree at 92717583, base b53bf631).

## Checks

| Check | Result |
|---|---|
| Every AC has a plan task and a Test design row | Pass: AC-1, AC-2 in Tasks 1-3; AC-3 in Task 3; AC-4, AC-5 in Task 4; AC-6 NOT CHECKED (owner, no device) |
| Citations resolve (sampled, all hit) | Pass, see below |
| No task leaves the build red; dependency order | Pass: Task 1 API, Tasks 2-3 consume it, Task 4 independent; compile-red tests are fixed inside the same task before its commit |
| P0 rows: defect named, `red`/`guard`, test named in backticks | Fail: fixes 2 and 3 |
| Inside Out of scope; blocking questions answered | Pass: no `app/src/main` caller of the setter, no flavor; Q1 (blocking) answered in `state.md` |

Citations checked in the worktree:

| Citation | Resolves to |
|---|---|
| `data/api/config/KinoPubConfig.kt:20-23`, `:52-53`, `:59`, `:72-74` | `Snapshot`, `snapshot`, `activeEndpoint`, `CUSTOM_API_DOMAIN`, `setDomainOverride` |
| `domain/interactor/api/ApiDomainInteractor.kt:61`, `:78`, `:90`, `:98`, `:117`, `:126`, `:129-133`, `:200`, `:215` | the seven mutators, `while (true)` and `walkLock` slot, `clearDomainSensitiveCaches` |
| `data/api/network/HttpStack.kt:9-13`, `:21`, `:24-37`; `DnsOverHttpsFactory.kt:15` | KDoc, class, `createDnsCache`, `create`, `DnsOverHttpsFactory.create` |
| `data/di/modules.kt:65`, `:70-72`; `domain/di/modules.kt:46-55` | `apiModule`, `HttpStack`/`OkHttpClient`/`DnsCache` singles; `evictDnsCache` wiring |
| `PuberApp.kt:68`, `:73-75`, `:77-85`, `:87-101`, `:139` | class, `attachBaseContext`, `onCreate`, `initDi`, `get<HttpClient>` in `newImageLoader` |
| `config/detekt/detekt.yml:217`, `:672-674`; `gradle/libs.versions.toml:22`; `app/build.gradle.kts:455-458` | `EmptyFunctionBlock`, `FunctionOnlyReturningConstant.ignoreOverridableFunction`, koin 4.2.2, test task (no parallel) |
| `KinoPubConfigTest.kt:22-32`, `:35-38`, `:76-93`; `ApiModuleHttpStackTest.kt:35-66`; `ApiDomainInteractorTest.kt:44-63`, `:67-87`; `ImageHttpClientTest.kt:38` | Base64 mock, `resetOverride`, `assertGettersMatch`, statics + `startApiModule`, fakes, `mockkObject` setup, `HttpStack.create()` caller |

## Fix list

1. `autoResolveWorkingDomain_leavesNoWalkInFlight_whenPinned` (plan Task 2, spec Traceability) wraps the unpinned call in `withTimeout(5.seconds)` without saying how the test runs. Every other case in `ApiDomainInteractorTest` uses `runTest`; under `runTest` the timeout is virtual and fires as soon as the body waits on `withContext(Dispatchers.IO)` (`ApiDomainInteractor.kt:143`), so the green case fails. Specify `runBlocking` with a real-time `withTimeout`, as `ApiDomainInteractorTest.kt:291` and `:308` already do for the same reason.
2. AC-5 row in Test design is P0 but marked neither `red` nor `guard` and names no defect. Mark it `guard` and name what it catches: a `setPinnedEndpoint` call site in `app/src/main`, or an assemble/detekt/lint break from `open class PuberApp` and the hooks.
3. The first AC-1 P0 `guard` row cites "existing `ApiDomainInteractorTest` cases for `saveCustomDomain`, `switchToBuiltInDomain`, `autoResolve`, `resetToDefault`" without names. Name the cases that carry the guard, for example `saveCustomDomain_evictsTheDnsCacheBeforeReturning`, `switchToBuiltInDomain_appliesTheMirror_whenItIsBuiltIn`, `autoResolve_failoverToAMirror_evictsTheDnsCache`, `resetToDefault_evictsTheDnsCacheBeforeReturning`.

## Notes (no fix required)

- Brief and spec Risks row "No device" cites "AC-4 unit test"; AC-4 is a diff check (manual check 1). The Test design risk row states this correctly.
