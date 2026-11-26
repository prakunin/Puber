# Readiness 2: hermetic-baseline-seams

Verdict: **READY**

Spec and plan at worktree HEAD 87554a09 (base b53bf631): `/home/dev/orca/workspaces/Puber/hermetic-baseline-seams/docs/superpowers/specs/2026-11-26-hermetic-baseline-seams.md`, `/home/dev/orca/workspaces/Puber/hermetic-baseline-seams/docs/superpowers/plans/2026-11-26-hermetic-baseline-seams.md`.

## Round-1 fix list

| Fix | Status | Where |
|---|---|---|
| 1. `autoResolveWorkingDomain_leavesNoWalkInFlight_whenPinned` under `runBlocking` with real-time `withTimeout` | Fixed | spec Test design AC-2 P1 row; plan Task 2 test table (cites `ApiDomainInteractorTest.kt:291`, `:308`, `ApiDomainInteractor.kt:143`) |
| 2. AC-5 row `guard` with named defect | Fixed | spec Traceability AC-5 `P0 guard`; plan Task 4 |
| 3. AC-1 existing guard cases named | Fixed | spec Traceability first AC-1 row; plan Task 2 last step with line numbers |

## Checks

| Check | Result |
|---|---|
| Every AC has a plan task and a Test design row | Pass: AC-1, AC-2 Tasks 1-3; AC-3 Task 3; AC-4, AC-5 Task 4; AC-6 NOT CHECKED (owner, no device) |
| Citations resolve (sampled in worktree) | Pass, see below |
| No task leaves the build red; dependency order | Pass: Task 1 API, Tasks 2-3 consume it, Task 4 independent; compile-red tests fixed inside their task |
| P0 rows: defect named, `red`/`guard`, test in backticks | Pass |
| Inside Out of scope; blocking questions answered | Pass: no `app/src/main` caller of the setter, no flavor; Q1 answered in `state.md` |

| Citation (worktree) | Resolves to |
|---|---|
| `ApiDomainInteractorTest.kt:121`, `:128`, `:147`, `:494` | `resetToDefault_evictsTheDnsCacheBeforeReturning`, `saveCustomDomain_evictsTheDnsCacheBeforeReturning`, `autoResolve_failoverToAMirror_evictsTheDnsCache`, `switchToBuiltInDomain_appliesTheMirror_whenItIsBuiltIn` |
| `ApiDomainInteractorTest.kt:291`; `:44-63`, `:67-87` | `runBlocking` + `withTimeout(10.seconds)` case; fakes; `mockkObject(KinoPubConfig)` setup with built-ins `service-kp.test`, `api.alador.test` |
| `ApiDomainInteractor.kt:61`, `:78`, `:90`, `:98`, `:117`, `:126`, `:129-130`, `:143`, `:200` | the seven mutators, `while (true)` and `walkLock`, `withContext(Dispatchers.IO)` walk |
| `KinoPubConfig.kt:20-23`, `:52-53`, `:59`, `:72-74`; `:36-42` | `Snapshot`, `activeEndpoint`, `CUSTOM_API_DOMAIN`, `setDomainOverride`; `api.alador.space` built-in used by the pinned `switchToBuiltInDomain` case |
| `HttpStack.kt:9-13`, `:21`, `:24-37`; `data/di/modules.kt:65`, `:70-72`; `PuberApp.kt:68`, `:73-75`, `:77` | KDoc, `createDnsCache`, `create`; `apiModule`, `HttpStack`/`OkHttpClient`/`DnsCache` singles; class, `attachBaseContext`, `onCreate` |

## Notes (no fix required)

- Brief and spec analyst Risks row "No device" still cites "AC-4 unit test"; AC-4 is a diff check (manual check 1). Test design states it correctly.
