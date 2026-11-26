# Seams for hermetic baseline profile generation - plan

**Spec:** `docs/superpowers/specs/2026-11-26-hermetic-baseline-seams.md`
**Goal:** Add an inert pinned API endpoint, a DoH switch tied to it, and `PuberApp` hooks before `startKoin`, with dev and prod behaving as at b53bf631.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin`; full gate: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`. Both run inside the worktree `/home/dev/orca/workspaces/Puber/hermetic-baseline-seams`; never `./gradlew` there.
- Touched tests: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests '<fully.qualified.Class>'`.
- One task = green fast gate + green tests of the touched code + one commit, message per commit_style "One imperative English sentence, no prefixes", never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- No device (owner, 2026-11-26): no emulator, no TV box, no install. Never probe the live KinoPub API; every test uses fakes or `127.0.0.1`.
- Out of scope: the `instrumentation` flavor, mock backend, synthetic auth, egress blocker, profile regeneration, any caller of `setPinnedEndpoint` in `app/src/main`. `app/src/test/kotlin/com/kino/puber/data/di/ApiModuleHttpStackTest.kt` stays byte-identical.
- Main paths below are under `app/src/main/java/com/kino/puber/`, test paths under `app/src/test/kotlin/com/kino/puber/`.
- Test isolation: `KinoPubConfig` is a JVM singleton shared by every test class (one JVM, `app/build.gradle.kts:455-458`). Every class that calls `setPinnedEndpoint` calls `KinoPubConfig.clearPinnedEndpoint()` and `KinoPubConfig.setDomainOverride(null)` in `@AfterEach`.
- Red tests that need an API this run adds fail by not compiling; record that compile error as the red run.

---

## Tasks

### Task 1: Add the pinned endpoint to KinoPubConfig

**Covers:** AC-1, AC-2
**Files:**
- Modify: `data/api/config/KinoPubConfig.kt` - add `@Volatile private var pinnedEndpoint: ApiEndpointPreset? = null` after `snapshot` (`:22-23`); `activeEndpoint` (`:52-53`) becomes `pinnedEndpoint ?: snapshot?.endpoint ?: defaultEndpoint`; add `val IS_PINNED_ENDPOINT: Boolean get() = pinnedEndpoint != null` next to `CUSTOM_API_DOMAIN` (`:59`); add `fun setPinnedEndpoint(endpoint: ApiEndpointPreset)` and `fun clearPinnedEndpoint()` after `setDomainOverride` (`:72-74`). `Snapshot` (`:20`), `setDomainOverride`, `CUSTOM_API_DOMAIN` stay unchanged. No `ApiEndpointMode`.
- Test: `data/api/config/KinoPubConfigTest.kt` - `@AfterEach resetOverride` (`:35-38`) also calls `clearPinnedEndpoint()`; reuse `assertGettersMatch` (`:76-93`) for the unpinned expectations.

**Consumes:** `ApiEndpointPreset` (`data/api/config/KinoPubConfig.kt:5-11`).
**Produces:** `KinoPubConfig.IS_PINNED_ENDPOINT: Boolean`, `KinoPubConfig.setPinnedEndpoint(endpoint: ApiEndpointPreset)`, `KinoPubConfig.clearPinnedEndpoint()`.

Pin fixture used by every pinning test in this plan:

```kotlin
ApiEndpointPreset(
    domain = "127.0.0.1",
    apiHost = "127.0.0.1:8080",
    mainBaseUrl = "http://127.0.0.1:8080/v1/",
    oauthBaseUrl = "http://127.0.0.1:8080/oauth2/",
    extraBaseUrl = "http://127.0.0.1:8080/",
)
```

| Test (`KinoPubConfigTest`) | Asserts |
|---|---|
| `setPinnedEndpoint_pointsEveryGetterAtThePin` | `CURRENT_API_DOMAIN`, `CURRENT_API_HOST`, `CURRENT_ENDPOINT`, `MAIN_API_BASE_URL`, `OAUTH_BASE_URL`, `EXTRA_API_BASE_URL` equal the pin; `IS_PINNED_ENDPOINT` true; `CUSTOM_API_DOMAIN` unchanged (null) |
| `setDomainOverride_leavesTheGettersPinned_whilePinned` | pin, then `setDomainOverride("mirror.example")`: every getter above still the pin |
| `clearPinnedEndpoint_restoresTheOverrideEndpoint` | `setDomainOverride("mirror.example")`, pin, clear: `assertGettersMatch("mirror.example")`, `IS_PINNED_ENDPOINT` false |

- [x] Write the three tests; run `KinoPubConfigTest`: red (does not compile)
- [x] Implement the `KinoPubConfig` change
- [x] Run `com.kino.puber.data.api.config.KinoPubConfigTest` (existing cases unchanged and green) and the fast gate; commit

**Done when:** `KinoPubConfigTest` passes with the three new cases and every pre-existing case unedited; fast gate exits 0.

---

### Task 2: Guard the seven ApiDomainInteractor mutators

**Covers:** AC-1, AC-2
**Files:**
- Modify: `domain/interactor/api/ApiDomainInteractor.kt` - one guard `if (KinoPubConfig.IS_PINNED_ENDPOINT) return <value>` as the first statement of each mutator, before validation, `domainVersion`, prefs, probes and `clearDomainSensitiveCaches` (`:215`):

| Mutator | Line | Guard return |
|---|---|---|
| `initialize` | `:61` | `return` |
| `saveCustomDomain` | `:78` | `ApiDomainUpdateResult.Success(getState())`, before `normalizeDomain` (`:79`) |
| `detectAndSaveWorkingDomain` | `:90` | `return@withContext ApiDomainDetectionResult.Success(getState())`, first statement inside `withContext` |
| `detectAndSaveAlternativeBuiltInDomain` | `:98` | same as above |
| `switchToBuiltInDomain` | `:117` | `return@withContext getState()`, first statement inside `withContext` (`:118`) |
| `autoResolveWorkingDomain` | `:126` | `ApiDomainAutoResolveResult.Success(getState(), changed = false)`, before `while (true)` (`:129`), so no `inFlightWalk` slot (`:130-133`) is taken |
| `resetToDefault` | `:200` | `getState()`, before `domainVersion.incrementAndGet()` (`:201`) |

  `getState()` (`:70-76`) is unchanged.
- Test (new): `domain/interactor/api/ApiDomainInteractorPinnedEndpointTest.kt`
- Test (extend): `domain/interactor/api/ApiDomainInteractorTest.kt` - four unpinned cases, using its existing `mockkObject(KinoPubConfig)` setup (`:67-87`, built-ins `service-kp.test`, `api.alador.test`), `reachableDomains` / `probeCalls` / `evictCalls` fakes (`:44-63`). `IS_PINNED_ENDPOINT` is not stubbed there; `mockkObject` calls through to the real field (false).

**Consumes:** Task 1's `IS_PINNED_ENDPOINT`, `setPinnedEndpoint`, `clearPinnedEndpoint`.
**Produces:** nothing new for later tasks.

`ApiDomainInteractorPinnedEndpointTest` setup: real `KinoPubConfig` (no `mockkObject`); companion `@BeforeAll`/`@AfterAll` mocks `android.util.Base64` as `KinoPubConfigTest.kt:20-33`; relaxed mocks for `ICryptoPreferenceRepository` (`getApiDomain()` returns `"mirror.example"`), `ContentCacheRepository`, `DetailsPrefetcher`; `EndpointProbe` fake that records each domain and returns true for every endpoint (so a missing guard would switch); `EndpointReachability()`; `evictDnsCache` counter. `@BeforeEach`: `setDomainOverride(null)`, `setPinnedEndpoint(pin)`. `@AfterEach`: `clearPinnedEndpoint()`, `setDomainOverride(null)`. A shared helper asserts after each call: `CURRENT_ENDPOINT`, `MAIN_API_BASE_URL`, `OAUTH_BASE_URL`, `EXTRA_API_BASE_URL` are the pin; `CUSTOM_API_DOMAIN` null; no probe call; `saveApiDomain` never called; evict count 0; `contentCache.clear()` and `detailsPrefetcher.invalidate()` never called.

| Test (`ApiDomainInteractorPinnedEndpointTest`) | Asserts besides the helper |
|---|---|
| `initialize_keepsThePin_whenPinned` | `CUSTOM_API_DOMAIN` stays null despite saved `mirror.example` |
| `saveCustomDomain_writesNothing_whenPinned` | `saveCustomDomain("api.custom.example")` returns `ApiDomainUpdateResult.Success(getState())` |
| `detectAndSaveWorkingDomain_probesNothing_whenPinned` | returns `ApiDomainDetectionResult.Success(getState())` |
| `detectAndSaveAlternativeBuiltInDomain_probesNothing_whenPinned` | returns `ApiDomainDetectionResult.Success(getState())` |
| `switchToBuiltInDomain_keepsThePin_whenPinned` | `switchToBuiltInDomain("api.alador.space")` returns `getState()`, not null |
| `autoResolveWorkingDomain_probesNothing_whenPinned` | returns `ApiDomainAutoResolveResult.Success(getState(), changed = false)` |
| `autoResolveWorkingDomain_leavesNoWalkInFlight_whenPinned` | `= runBlocking { ... }` with a real-time `withTimeout(5.seconds)`, not `runTest` (its virtual timeout fires at `withContext(Dispatchers.IO)`, `ApiDomainInteractor.kt:143`; same pattern as `ApiDomainInteractorTest.kt:291`, `:308`): pinned call; `clearPinnedEndpoint()`; an unpinned call inside the timeout returns `Success` (helper not applied after the unpinned call) |
| `resetToDefault_writesNothing_whenPinned` | returns `getState()` |

| Test (`ApiDomainInteractorTest`, unpinned) | Asserts |
|---|---|
| `initialize_appliesTheSavedDomain_whenNotPinned` | `preferences.getApiDomain()` returns `"mirror.example"`; after `initialize()`, `verify { KinoPubConfig.setDomainOverride("mirror.example") }` |
| `detectAndSaveWorkingDomain_savesTheFirstReachableBuiltIn_whenNotPinned` | `reachableDomains = setOf("api.alador.test")`: `Success` with domain `api.alador.test`; `saveApiDomain("api.alador.test")` once; `evictCalls == 1`; `probeCalls == listOf("service-kp.test", "api.alador.test")` |
| `detectAndSaveWorkingDomain_returnsNotFound_whenNothingAnswers` | `reachableDomains` empty: `ApiDomainDetectionResult.NotFound`; `saveApiDomain` never called |
| `detectAndSaveAlternativeBuiltInDomain_skipsTheCurrentDomain_whenNotPinned` | both reachable: `Success` with `api.alador.test`; `probeCalls == listOf("api.alador.test")`; `saveApiDomain("api.alador.test")` once |

- [x] Write the four unpinned cases; run `ApiDomainInteractorTest`: green on the unguarded code (they pin today's behaviour)
- [x] Write `ApiDomainInteractorPinnedEndpointTest`; run it: red on the unguarded code (record which cases fail)
- [x] Add the seven guards
- [x] Run `com.kino.puber.domain.interactor.api.*` (incl. `ApiDomainStartupTest`) and the fast gate; commit. The AC-1 P0 guard for the unpinned path is carried by the existing `ApiDomainInteractorTest` cases `saveCustomDomain_evictsTheDnsCacheBeforeReturning` (`:128`), `switchToBuiltInDomain_appliesTheMirror_whenItIsBuiltIn` (`:494`), `autoResolve_failoverToAMirror_evictsTheDnsCache` (`:147`), `resetToDefault_evictsTheDnsCacheBeforeReturning` (`:121`); they stay green unedited

**Done when:** all of `com.kino.puber.domain.interactor.api.*` passes, every pre-existing `ApiDomainInteractorTest` case unedited; fast gate exits 0.

---

### Task 3: Build the HTTP stack without DoH while pinned

**Covers:** AC-1, AC-3
**Files:**
- Modify: `data/api/network/HttpStack.kt` - `create` (`:24`) gains `useDoh: Boolean = true` after `listeners`. When true: today's body (`:25-37`). When false: no DoH client, `DnsOverHttpsFactory.create` (`data/api/network/DnsOverHttpsFactory.kt:15`) not called, the `"doh"` listener not requested, no `.dns(...)` on the app client (OkHttp default `Dns.SYSTEM`); `dnsCache` still created by `createDnsCache()` (`:21`) and returned. Class KDoc (`:9-12`): the resolver is DoH unless the endpoint is pinned.
- Modify: `data/di/modules.kt` - `:70` becomes `HttpStack.create(if (BuildConfig.DEBUG) NetworkStats::factory else null, useDoh = !KinoPubConfig.IS_PINNED_ENDPOINT)`; import `com.kino.puber.data.api.config.KinoPubConfig`. `OkHttpClient` and `DnsCache` singles (`:71-72`) unchanged.
- Test (new): `data/api/network/HttpStackTest.kt`
- Test (new): `data/di/ApiModulePinnedEndpointTest.kt` - companion mocks `Log` and `Base64` and `startApiModule` copied from `data/di/ApiModuleHttpStackTest.kt:35-66`; pin (Task 1 fixture) set before `koinApplication`; `application.close()` in `finally`; `@AfterEach` clears the pin and the override.

**Consumes:** Task 1's pin API; `HttpStack.client`, `HttpStack.dnsCache` (`data/api/network/HttpStack.kt:13`); `apiModule` (`data/di/modules.kt:65`).
**Produces:** `HttpStack.create(listeners: ((String) -> EventListener.Factory)? = null, useDoh: Boolean = true): HttpStack`.

| Test | Asserts |
|---|---|
| `HttpStackTest.create_resolvesThroughDoh_byDefault` | `HttpStack.create().client.dns is DnsOverHttps` |
| `HttpStackTest.create_usesSystemDns_withoutDoh` | `create(useDoh = false)`: `assertSame(Dns.SYSTEM, client.dns)` |
| `ApiModulePinnedEndpointTest.pinnedEndpoint_buildsTheAppClientWithoutDoh` | `koin.get<OkHttpClient>().dns !is DnsOverHttps`; `assertSame(koin.get<HttpStack>().dnsCache, koin.get<DnsCache>())` |

- [x] Write `HttpStackTest`; run it: red (`useDoh` does not compile)
- [x] Add `useDoh` to `HttpStack.create`; run `HttpStackTest`: green
- [x] Write `ApiModulePinnedEndpointTest`; run it: red (`modules.kt:70` still builds DoH)
- [x] Change `modules.kt:70`
- [x] Run `com.kino.puber.data.api.network.*`, `com.kino.puber.data.di.*` (incl. unchanged `ApiModuleHttpStackTest`, `ImageHttpClientTest`) and the fast gate; commit

**Done when:** those packages pass; `git diff b53bf631 -- app/src/test/kotlin/com/kino/puber/data/di/ApiModuleHttpStackTest.kt` is empty; fast gate exits 0.

---

### Task 4: Open PuberApp with runtime hooks before startKoin

**Covers:** AC-4, AC-5 (AC-6 stays NOT CHECKED: no device)
**Files:**
- Modify: `PuberApp.kt`
  - `class PuberApp` (`:68`) becomes `open class PuberApp`.
  - Add `protected open fun shouldInitializeRuntime(): Boolean = true`, `protected open fun configureRuntime() = Unit`, `protected open fun runtimeModules(): List<Module> = emptyList()`; import `org.koin.core.module.Module`. `= Unit` keeps detekt `EmptyFunctionBlock` (`config/detekt/detekt.yml:217`) quiet; `FunctionOnlyReturningConstant` ignores overridable functions (`config/detekt/detekt.yml:672-674`).
  - `onCreate` (`:77-85`): `appCreateMs`, `super.onCreate()`, `if (!shouldInitializeRuntime()) return`, `TelemetryRuntime.install(this, appCreateMs)`, `StartupReveal.installFrameWait()`, `configureRuntime()`, `initDi()`, `initLogger()`.
  - `initDi` (`:87-101`): `modules(listOf(resourceModule, handlersModule, apiModule, repositoryModule, interactorModule, telemetryModule) + runtimeModules())`; `AppStartup` and `CachedPayloadSweeper` starts (`:99-100`) unchanged.
  - `attachBaseContext` (`:73-75`) and `newImageLoader` (`:109-146`) unchanged.

**Consumes:** nothing from earlier tasks.
**Produces:** `open class PuberApp` with the three `protected open` hooks (used by the later `BaselineProfileApp`, not in this run).

- [x] Edit `PuberApp.kt`
- [x] Run the fast gate; commit
- [x] Run `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:assembleDevDebug :app:assembleProdRelease` and the full gate
- [x] Run spec manual checks 1-3 (`## Test design` → `### Manual checks`) and record their output in the Implementation record

AC-5 is a P0 guard (manual check 2): it fails on a `setPinnedEndpoint` call site in `app/src/main`, or an assemble, detekt or lint break from `open class PuberApp` and its hooks.

**Done when:** manual check 1 matches the order above; `rg -n "setPinnedEndpoint" app/src/main` prints only the definition in `data/api/config/KinoPubConfig.kt`; every file from `rg -l "setPinnedEndpoint" app/src/test` calls `clearPinnedEndpoint()` in `@AfterEach`; both assemble tasks and the full gate exit 0 with no skipped or disabled test.

---

## Implementation record

<!-- owner: implementer -->

**Commits:**

| sha | subject |
|---|---|
| cb770a09 | Add an inert pinned endpoint to KinoPubConfig |
| 5f65975b | Guard the seven ApiDomainInteractor mutators while an endpoint is pinned |
| 73de7415 | Build the HTTP stack without DNS-over-HTTPS while an endpoint is pinned |
| 40be4cb6 | Open PuberApp with runtime hooks before startKoin |
| next | Restructure saveCustomDomain so the pinned guard fits detekt's return limit |

**Red run** (P0 `red` tests on the unchanged code):

- red: KinoPubConfigTest.setPinnedEndpoint_pointsEveryGetterAtThePin, setDomainOverride_leavesTheGettersPinned_whilePinned, clearPinnedEndpoint_restoresTheOverrideEndpoint - compile error `Unresolved reference 'clearPinnedEndpoint'` (KinoPubConfigTest.kt:39)
- red: ApiDomainInteractorPinnedEndpointTest.initialize_keepsThePin_whenPinned - AssertionFailedError at assertNothingTouched (a probe or write happened, no guard)
- red: ...saveCustomDomain_writesNothing_whenPinned, detectAndSaveWorkingDomain_probesNothing_whenPinned, detectAndSaveAlternativeBuiltInDomain_probesNothing_whenPinned, switchToBuiltInDomain_keepsThePin_whenPinned, autoResolveWorkingDomain_probesNothing_whenPinned, autoResolveWorkingDomain_leavesNoWalkInFlight_whenPinned - AssertionFailedError at assertNothingTouched
- red: ...resetToDefault_writesNothing_whenPinned - AssertionError (MockK: `saveApiDomain` verify failed, the unguarded code wrote preferences)
- green on base (by design, guards): the four unpinned ApiDomainInteractorTest cases, passing on unguarded code
- red: HttpStackTest.create_usesSystemDns_withoutDoh - compile error `No parameter with name 'useDoh' found`
- red: ApiModulePinnedEndpointTest.pinnedEndpoint_buildsTheAppClientWithoutDoh - AssertionFailedError at the `assertFalse(dns is DnsOverHttps)` line (modules.kt still built DoH)

**Deviations from the plan:**

- `saveCustomDomain` returns through a `when` instead of early returns: with the guard it had 4 returns and detekt `ReturnCount` (limit 3) failed. Behaviour unchanged; the guard is still the first statement, before `normalizeDomain`.
- `HttpStack.create` with `useDoh = false` returns early with the app client built from `base` without `.dns(...)`; an extra KDoc line on `ApiDomainInteractor` states the pinned rule.

**Files touched outside the plan:** none.

**For the reviewers:**

- `ApiDomainInteractor.saveCustomDomain` restructure (above) is the only change to the unpinned path besides the seven guard lines.
- The detekt run reports "78 compiler errors found during analysis" as an accuracy note; the gate passes (same on base).

**Manual checks** (AC-6 NOT CHECKED, owner: no device):

1. `git diff b53bf631 -- PuberApp.kt`: `open class`; three `protected open` hooks with the defaults `true`, `Unit`, `emptyList()`; `onCreate` order `appCreateMs`, `super.onCreate()`, gate return, telemetry, `StartupReveal`, `configureRuntime()`, `initDi()`, `initLogger()`; module list `+ runtimeModules()`; `attachBaseContext` and `newImageLoader` untouched. Matches.
2. `rg -n "setPinnedEndpoint" app/src/main` prints only `KinoPubConfig.kt:81`. `:app:assembleDevDebug :app:assembleProdRelease` BUILD SUCCESSFUL; `check.full` (testDevDebugUnitTest, detektAll, lintDevDebug) BUILD SUCCESSFUL; no disabled test added.
3. `rg -l "setPinnedEndpoint" app/src/test`: ApiModulePinnedEndpointTest, KinoPubConfigTest, ApiDomainInteractorPinnedEndpointTest; each calls `clearPinnedEndpoint()` in `@AfterEach`.

### Fix round 1 (triage-1 #1-#5)

| sha | subject |
|---|---|
| 0d62af49 | Pin the saveCustomDomain Empty and Invalid results and the app event listener without DoH |
| f5adb9c9 | State that the pinned endpoint seams have no production caller and fix the HttpStack KDoc |
| 5a688a1b | Point the checked-in baseline profile rules at the new HttpStack create signature |

| # | Result |
|---|---|
| 1 | javap on `HttpStack$Companion`: `create(Lkotlin/jvm/functions/Function1;Z)Lcom/kino/puber/data/api/network/HttpStack;` plus `create$default(...)`. The only main call site (`modules.kt`) passes both arguments, so only the `create` rule changed, in `startup-prof.txt` (S, P flags) and `baseline-prof.txt` (H, S, P flags). `create$default` is not added: no main caller. |
| 2 | Added `saveCustomDomain_returnsEmptyAndSavesNothing_whenTheInputHasNoHost` and `..._returnsInvalidAndSavesNothing_whenTheHostIsMalformed`. Green on base: they pin existing unpinned behaviour (no save, no evict). |
| 3 | Added `HttpStackTest.create_keepsTheAppEventListenerAndRequestsNoDohOne_withoutDoh`. Green on base: guard of existing behaviour. |
| 4 | HttpStack KDoc corrected; the always-true `assertNotNull` dropped and the test renamed `create_usesSystemDns_withoutDoh` (the shared-cache identity stays asserted in `ApiModulePinnedEndpointTest`). |
| 5 | Seam KDoc on `KinoPubConfig.setPinnedEndpoint` and a comment above the three `PuberApp` hooks. |

Gates: `compileDevDebugKotlin testDevDebugUnitTest :app:detektAll :app:lintDevDebug :app:assembleProdRelease` BUILD SUCCESSFUL.

**Blocked:** none.

## Readiness

Verdict: **READY**

Spec and plan at worktree HEAD 87554a09 (base b53bf631): `/home/dev/orca/workspaces/Puber/hermetic-baseline-seams/docs/superpowers/specs/2026-11-26-hermetic-baseline-seams.md`, `/home/dev/orca/workspaces/Puber/hermetic-baseline-seams/docs/superpowers/plans/2026-11-26-hermetic-baseline-seams.md`.

### Round-1 fix list

| Fix | Status | Where |
|---|---|---|
| 1. `autoResolveWorkingDomain_leavesNoWalkInFlight_whenPinned` under `runBlocking` with real-time `withTimeout` | Fixed | spec Test design AC-2 P1 row; plan Task 2 test table (cites `ApiDomainInteractorTest.kt:291`, `:308`, `ApiDomainInteractor.kt:143`) |
| 2. AC-5 row `guard` with named defect | Fixed | spec Traceability AC-5 `P0 guard`; plan Task 4 |
| 3. AC-1 existing guard cases named | Fixed | spec Traceability first AC-1 row; plan Task 2 last step with line numbers |

### Checks

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

### Notes (no fix required)

- Brief and spec analyst Risks row "No device" still cites "AC-4 unit test"; AC-4 is a diff check (manual check 1). Test design states it correctly.
