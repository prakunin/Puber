# Seams for hermetic baseline profile generation

Status: approved
Date: 2026-11-26
Slug: hermetic-baseline-seams
Kind: improvement
Size: M
Tracks: none

<!--
Each section has exactly one owning role; nobody else edits it. The conductor
copies each section verbatim from its owner's output and commits this file in
the worktree after each stage. Cite code as `path:line`; a claim with no
source is a guess.
-->

## Problem / Why

### Request

Split item 1 of `hermetic-baseline-profiles` (`../hermetic-baseline-profiles/brief.md: Split`, AC-1..AC-3 there). Add three inert seams that the later `instrumentation` flavor uses, with dev and prod unchanged: (a) a pinned API endpoint in `KinoPubConfig` that the seven `ApiDomainInteractor` mutators leave alone; (b) no DNS-over-HTTPS in `HttpStack` while pinned; (c) `PuberApp` hooks that run before `startKoin`. Hand re-port of `upstream-sync-triage` a57a3238 and 183f2b7b.

### Findings

Code map, mutator table, test impact and branch-to-master mapping: `research.md: Current state in code`. Option A recommended (`research.md: Options`). Facts this brief relies on, rechecked at b53bf631 (paths under `app/src/main/java/com/kino/puber/`):

| Where | Fact |
|---|---|
| `data/api/config/KinoPubConfig.kt:20-23`, `:52-53`, `:72-74` | One volatile `Snapshot`; every URL getter reads `activeEndpoint`; `setDomainOverride` replaces the snapshot whole |
| `domain/interactor/api/ApiDomainInteractor.kt:61`, `:78`, `:90`, `:98`, `:117`, `:126`, `:200` | The seven mutators; `autoResolveWorkingDomain` enters `walkLock` at `:130`, so its guard goes before the loop |
| `data/api/network/HttpStack.kt:24-37` | `create(listeners)` always installs `DnsOverHttpsFactory` (`dns.google`) on the app client |
| `data/di/modules.kt:70-72` | `HttpStack` is a lazy Koin single; `OkHttpClient` and `DnsCache` singles derive from it, so a pin set before `startKoin` is seen at build time |
| `PuberApp.kt:68`, `:77-101` | Final class; `onCreate` = `TelemetryRuntime.install`, `StartupReveal.installFrameWait`, `initDi` (six modules, `AppStartup.start`, `CachedPayloadSweeper.start`), `initLogger` |
| `app/build.gradle.kts:138-186` | Release signing falls back to debug-keystore credentials, so `assembleProdRelease` builds in a worktree without a keystore |

### Size

M: one goal, but it touches three risk areas from `.flow/roles/analyst.md` (networking, startup and Koin wiring, the baseline profile route) across config, domain, network and application layers.

### Risks (analyst)

| Risk | Mitigation |
|---|---|
| A guard or the DoH switch fires while unpinned and breaks domain fallback or DNS for users | AC-1 per mutator; default `useDoh = true`; AC-5 grep |
| Reordering `onCreate` shifts telemetry startup marks (`appCreateMs`) | AC-4 keeps today's order; gate defaults to true |
| `KinoPubConfig` is a JVM singleton; a pin leaking between tests flips other suites | `clearPinnedEndpoint()` in `@AfterEach` of every test that pins |
| No device: a startup crash in the dev build ships unseen | AC-6 recorded NOT CHECKED; AC-4 unit test and AC-5 build are the only runtime proxies |

## Acceptance criteria

| ID | Given | When | Then |
|---|---|---|---|
| AC-1 | no pinned endpoint (default) | each of the seven mutators runs, `KinoPubConfig` getters are read, and `HttpStack.create()` is built with defaults | results, writes to prefs/config/caches and `domainVersion` equal today's; the app client's `dns` is `DnsOverHttps` (unit tests per mutator, unpinned, incl. new direct cases for `initialize`, `detectAndSaveWorkingDomain`, `detectAndSaveAlternativeBuiltInDomain`; `ApiModuleHttpStackTest` unchanged and green) |
| AC-2 | an endpoint pinned to `http://127.0.0.1:<port>` | each mutator runs | `CURRENT_ENDPOINT` and every base URL stay the pinned ones; no reachability probe, no pref write, no cache clear (`evictDnsCache` not called), no walk slot (unit tests per mutator, pinned) |
| AC-3 | a pin is set | `HttpStack` is built through the Koin path used at runtime | the app client's `dns` is not `DnsOverHttps`, and the `DnsCache` single still resolves (unit test) |
| AC-4 | `PuberApp` with default hooks | `onCreate` runs | telemetry install, frame wait, `startKoin` with the six modules, `AppStartup.start`, sweeper, logger run in today's order; a subclass can run code before `startKoin`, add modules, or skip runtime init (checked in the `PuberApp.kt` diff: hooks are `open` members with no-op defaults, `configureRuntime()` is called before `startKoin`, `runtimeModules()` is appended to its module list) |
| AC-5 | the worktree sources | `rg` for the pin setter and `./tools/agentw :app:assembleDevDebug :app:assembleProdRelease` + `check.full` run | the setter has no caller in `app/src/main`; both APKs build; tests, detekt and lint pass |
| AC-6 | the dev APK from the worktree | installed with `tools/android-apk-install-preserve` on the box and launched | it reaches the same first screen as the master dev build, no crash - NOT CHECKED until the owner allows a device |

AC mapping to the umbrella: AC-1 = umbrella AC-1; AC-2, AC-3 = umbrella AC-2; AC-4..AC-6 = umbrella AC-3.

## Out of scope

Everything in hermetic-baseline-generation (instrumentation flavor, mock backend, synthetic auth, egress blocker, profile regeneration, removal of the auth provider) and baseline-generation-hardening. Any device run (owner decision: no device for now).

## Design

Paths under `app/src/main/java/com/kino/puber/` unless given in full. Citations checked in the worktree at b53bf631. ACs: `brief.md#acceptance-criteria`; Q1-Q3 answered in `state.md`.

### Decisions

| Question | Decision | Why |
|---|---|---|
| Where the pin lives | Separate `@Volatile private var pinnedEndpoint: ApiEndpointPreset?` in `KinoPubConfig`, read first in `activeEndpoint` (`data/api/config/KinoPubConfig.kt:52-53`): `pinnedEndpoint ?: snapshot?.endpoint ?: defaultEndpoint` | `Snapshot` and `setDomainOverride` (`:20-23`, `:72-74`) stay byte-identical, so unpinned reads are today's; a pinned read never touches the Base64 `defaultDomain` |
| Pin API surface | `val IS_PINNED_ENDPOINT: Boolean`, `fun setPinnedEndpoint(endpoint: ApiEndpointPreset)`, `fun clearPinnedEndpoint()`; no `ApiEndpointMode` / `CURRENT_ENDPOINT_MODE` | Item 2 calls the setter from `src/instrumentation`, so it is public; clear is for test teardown; mode enum has no reader (Q3) |
| `CUSTOM_API_DOMAIN` and `getState()` under a pin | Unchanged (`KinoPubConfig.kt:59`, `domain/interactor/api/ApiDomainInteractor.kt:70-76`) | Q3 |
| Guard form | First statement of each mutator: `if (KinoPubConfig.IS_PINNED_ENDPOINT) return <value>`, before validation, `domainVersion`, prefs, probes and `clearDomainSensitiveCaches` | One read per call; nothing below the guard runs, which is what AC-2 asserts |
| Guard return values | `initialize` (`:61`): `return`; `saveCustomDomain` (`:78`): `ApiDomainUpdateResult.Success(getState())`; `detectAndSaveWorkingDomain` (`:90`), `detectAndSaveAlternativeBuiltInDomain` (`:98`): `ApiDomainDetectionResult.Success(getState())` as the first statement inside `withContext`; `switchToBuiltInDomain` (`:117`): `getState()`; `autoResolveWorkingDomain` (`:126`): `ApiDomainAutoResolveResult.Success(getState(), changed = false)` before `while (true)` (`:129`); `resetToDefault` (`:200`): `getState()` | Branch a57a3238 values; callers (`AppStartup.kt:62`, `HomeVM`, `AuthVM`) see "nothing changed, proceed"; the `autoResolve` guard ahead of `walkLock` (`:130`) creates no `inFlightWalk` slot |
| DoH switch | `HttpStack.create(listeners, useDoh: Boolean = true)` (`data/api/network/HttpStack.kt:24`); when false, no DoH client and no `.dns(...)` on the app client (system DNS), `dnsCache` still created and returned | `HttpStack` stays free of global state and both ways are testable; existing `HttpStack.create()` callers (`ImageHttpClientTest.kt:38`) unchanged; `DnsCache` single (`data/di/modules.kt:72`) and `evictDnsCache` wiring (`domain/di/modules.kt:46-55`) keep resolving |
| Who decides `useDoh` | Koin single at `data/di/modules.kt:70`: `HttpStack.create(listeners, useDoh = !KinoPubConfig.IS_PINNED_ENDPOINT)` | Lazy single resolves after `startKoin`, so a pin set in `configureRuntime()` is seen; this is the "Koin path used at runtime" of AC-3 |
| `PuberApp` shape | `open class PuberApp` (`PuberApp.kt:68`) with `protected open fun shouldInitializeRuntime(): Boolean = true`, `protected open fun configureRuntime() = Unit`, `protected open fun runtimeModules(): List<Module> = emptyList()` | Q1; `= Unit` avoids detekt `EmptyFunctionBlock` (`config/detekt/detekt.yml:217`); `FunctionOnlyReturningConstant` ignores overridable functions (`:672-674`) |
| `onCreate` order | `appCreateMs`, `super.onCreate()`, `if (!shouldInitializeRuntime()) return`, `TelemetryRuntime.install`, `StartupReveal.installFrameWait()`, `configureRuntime()`, `initDi()`, `initLogger()` (`PuberApp.kt:77-85`) | Q2 gate after `super.onCreate()`; dev/prod steps keep today's order and `appCreateMs`; the hook sits directly before `startKoin` |
| Module list | `initDi` (`PuberApp.kt:87-101`) passes `listOf(resourceModule, handlersModule, apiModule, repositoryModule, interactorModule, telemetryModule) + runtimeModules()` | Appended last so item 2's definitions override (Koin 4.2.2, `gradle/libs.versions.toml:22`, allows override by default) |

### Changes by layer

| Layer | File | Change |
|---|---|---|
| API / repository | `data/api/config/KinoPubConfig.kt` | `pinnedEndpoint` field next to `snapshot` (`:22-23`); `activeEndpoint` (`:52-53`) reads it first; `IS_PINNED_ENDPOINT` with the getters (`:55-59`); `setPinnedEndpoint` / `clearPinnedEndpoint` after `setDomainOverride` (`:72-74`) |
| API / repository | `data/api/network/HttpStack.kt` | `useDoh` parameter on `create` (`:24`); DoH client and `.dns(doh)` (`:28-34`) only when true; class KDoc (`:9-12`) says the resolver is DoH unless the endpoint is pinned |
| API / repository | `data/di/modules.kt` | `:70` passes `useDoh = !KinoPubConfig.IS_PINNED_ENDPOINT`; import `com.kino.puber.data.api.config.KinoPubConfig` |
| Domain | `domain/interactor/api/ApiDomainInteractor.kt` | Seven guards at `:61`, `:78`, `:90`, `:98`, `:117`, `:126`, `:200` as in Decisions |
| Application / DI | `PuberApp.kt` | `open`, three hooks, gate and hook in `onCreate` (`:77-85`), module list in `initDi` (`:88-98`); import `org.koin.core.module.Module`; `attachBaseContext` (`:73-75`) and `newImageLoader` (`:109-146`) unchanged |
| ViewModel, Compose, resources | none | N/A: no screen, state or string changes |
| Tests | `app/src/test/kotlin/com/kino/puber/data/api/config/KinoPubConfigTest.kt`; `.../domain/interactor/api/` (new pinned/unpinned interactor test); `.../data/di/` (new pinned `apiModule` test) | Test design owns names and counts; `ApiModuleHttpStackTest.kt` stays unchanged (AC-1) |

### Alternatives rejected

- Pin inside `Snapshot` (research option B): changes `setDomainOverride` semantics and makes `HttpStack` read a global.
- `HttpStack.create` reading `KinoPubConfig.IS_PINNED_ENDPOINT` itself: hides a global dependency in a factory unit tests call directly.
- Enforce the pin in `setDomainOverride` instead of guards: probes, pref writes and cache clears would still run (fails AC-2).
- Only `configureRuntime()` (option C): item 2 would reopen `PuberApp.kt` (Q1).
- `configureRuntime()` before `TelemetryRuntime.install`: no consumer needs it there, and it would put subclass code between `appCreateMs` and the telemetry marks.

### Risks

| Risk | Area | Lives in | Mitigation |
|---|---|---|---|
| A guard taken while unpinned breaks domain fallback for users | Correctness | `ApiDomainInteractor.kt` guards | Guard reads only `pinnedEndpoint != null`; AC-1 unpinned test per mutator incl. the three with no direct test today; AC-5 grep that `setPinnedEndpoint` has no caller in `app/src/main` |
| A pin leaks between tests: `KinoPubConfig` is a JVM singleton, so a missed `clearPinnedEndpoint()` flips `IS_PINNED_ENDPOINT` for later suites (`ApiModuleHttpStackTest`, `ApiDomainInteractorTest`) | Lifecycle | test classes that pin | `clearPinnedEndpoint()` in `@AfterEach` of every pinning test; `mockkObject(KinoPubConfig)` in `ApiDomainInteractorTest.kt:72` calls through to the real field, so a leak is visible there |
| `onCreate` reordering shifts startup telemetry or the reveal | Rollout | `PuberApp.kt:77-85` | Order fixed in Decisions; AC-4 checks it in the diff; AC-6 device launch NOT CHECKED (owner) |
| Pin set after `HttpStack` is built keeps DoH on | Lifecycle | `data/di/modules.kt:70` lazy single | Pin only from `configureRuntime()`, before `startKoin`; nothing resolves `HttpStack` eagerly |
| With `shouldInitializeRuntime() == false`, Koin never starts but `newImageLoader` (`PuberApp.kt:139`) calls `get<HttpClient>` | Lifecycle | `PuberApp.kt:109-146` | Only item 2's UI-less control process returns false; it must load no image. Default `true` leaves dev/prod as today |
| Pinned stack uses system DNS for `127.0.0.1` | Security | `HttpStack.kt` | No `dns.google` query while pinned (the goal); unpinned DoH unchanged, asserted by `ApiModuleHttpStackTest` |
| One more volatile read per URL getter | Performance | `KinoPubConfig.kt:52-53` | Negligible next to the request it builds |

### Open questions

none

## Test design

ACs: `brief.md#acceptance-criteria`. Design: `design-architect.md`. Base b53bf631. Paths under `app/src/test/kotlin/com/kino/puber/`. Level `unit` = JUnit 5 + MockK on the JVM; `manual` = a command or diff check recorded in the evidence. Unit tests run sequentially in one JVM (`app/build.gradle.kts:455-458`, no parallel config), so the `KinoPubConfig` singleton is shared across classes.

### Risk profile

| Risk | P | I | Score | Covered by |
|---|---|---|---|---|
| A guard fires while unpinned and breaks domain fallback for users | 2 | 3 | 6 | AC-1 unpinned tests per mutator (existing `ApiDomainInteractorTest` + three new) |
| A mutator misses its guard: under a pin it probes, writes prefs or clears caches, so item 2's generation reaches the live API (account-ban risk) | 2 | 3 | 6 | AC-2 pinned test per mutator on the real `KinoPubConfig` |
| Koin single does not pass `useDoh`: pinned stack still queries `dns.google` | 2 | 2 | 4 | AC-3 Koin test |
| `useDoh` default flipped: users lose DoH | 1 | 3 | 3 | `ApiModuleHttpStackTest` unchanged + `HttpStackTest` default case |
| `onCreate` reorder or a hook in the wrong place shifts telemetry or skips DI | 2 | 2 | 4 | AC-4 diff check (no JVM level: `Application` stubs throw, no Robolectric) |
| A pin leaks between test classes and flips later suites | 2 | 2 | 4 | `clearPinnedEndpoint()` in `@AfterEach` of every pinning class; manual check 3 |
| Startup crash in the dev APK ships unseen (no device) | 1 | 3 | 3 | AC-5 build; AC-6 NOT CHECKED |
| Pin set after `HttpStack` is built keeps DoH | 1 | 2 | 2 | Accepted: only item 2 pins, from `configureRuntime()` |
| `shouldInitializeRuntime() == false` then `newImageLoader` needs Koin | 1 | 2 | 2 | Accepted: default `true`; item 2's control process loads no image |

### Traceability

Pinned tests use the real `KinoPubConfig` (Base64 mocked as in `data/api/config/KinoPubConfigTest.kt:22-32`), pin `ApiEndpointPreset` with `http://127.0.0.1:8080/` URLs, make every built-in domain reachable through the fake `EndpointProbe` so a missing guard would switch, and reset with `clearPinnedEndpoint()` + `setDomainOverride(null)` in `@AfterEach`. Each pinned mutator test asserts: the return value from the design's guard table, `CURRENT_ENDPOINT` and the three base URLs still the pin, `CUSTOM_API_DOMAIN` unchanged, no probe call, no `saveApiDomain`, `evictDnsCache` / `contentCache.clear()` / `detailsPrefetcher.invalidate()` not called.

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 guard | existing `ApiDomainInteractorTest.saveCustomDomain_evictsTheDnsCacheBeforeReturning`, `ApiDomainInteractorTest.switchToBuiltInDomain_appliesTheMirror_whenItIsBuiltIn`, `ApiDomainInteractorTest.autoResolve_failoverToAMirror_evictsTheDnsCache`, `ApiDomainInteractorTest.resetToDefault_evictsTheDnsCacheBeforeReturning` | fail if a guard fires unpinned (`IS_PINNED_ENDPOINT` inverted or reading the snapshot): writes, evicts and switches stop |
| AC-1 | unit | P0 guard | `ApiDomainInteractorTest.initialize_appliesTheSavedDomain_whenNotPinned` | saved `mirror.example` reaches `setDomainOverride`; fails if the `initialize` guard fires unpinned |
| AC-1 | unit | P1 guard | `ApiDomainInteractorTest.saveCustomDomain_returnsEmptyAndSavesNothing_whenTheInputHasNoHost` | input without a host returns the empty result, no `saveApiDomain`; fails if the unpinned validation is lost |
| AC-1 | unit | P1 guard | `ApiDomainInteractorTest.saveCustomDomain_returnsInvalidAndSavesNothing_whenTheHostIsMalformed` | malformed host returns the invalid result, no `saveApiDomain`; fails if the unpinned validation is lost |
| AC-1 | unit | P0 guard | `ApiDomainInteractorTest.detectAndSaveWorkingDomain_savesTheFirstReachableBuiltIn_whenNotPinned` | only the mirror reachable: `Success` with it, `saveApiDomain(mirror)`, one evict, probes in built-in order |
| AC-1 | unit | P1 guard | `ApiDomainInteractorTest.detectAndSaveWorkingDomain_returnsNotFound_whenNothingAnswers` | `NotFound`, no write; fails if the guard's `Success` leaks into the unpinned path |
| AC-1 | unit | P0 guard | `ApiDomainInteractorTest.detectAndSaveAlternativeBuiltInDomain_skipsTheCurrentDomain_whenNotPinned` | all reachable: picks the mirror, current never probed, mirror saved |
| AC-1 | unit | P0 guard | existing `KinoPubConfigTest` (all cases) | unpinned getters byte-identical to today; fails if `activeEndpoint` or `setDomainOverride` changed |
| AC-1 | unit | P0 guard | `HttpStackTest.create_resolvesThroughDoh_byDefault` (new file `data/api/network/HttpStackTest.kt`) | `create()` client `dns is DnsOverHttps`; fails if the `useDoh` default is `false` |
| AC-1 | unit | P0 guard | existing `ApiModuleHttpStackTest`, file unchanged | Koin path unpinned still DoH with one shared pool |
| AC-2 | unit | P0 red | `KinoPubConfigTest.setPinnedEndpoint_pointsEveryGetterAtThePin` | domain, host, `CURRENT_ENDPOINT`, three base URLs equal the pin, `IS_PINNED_ENDPOINT` true, `CUSTOM_API_DOMAIN` unchanged; fails if `activeEndpoint` ignores the pin |
| AC-2 | unit | P0 red | `KinoPubConfigTest.setDomainOverride_leavesTheGettersPinned_whilePinned` | fails if the snapshot is read before `pinnedEndpoint` |
| AC-2 | unit | P1 red | `KinoPubConfigTest.clearPinnedEndpoint_restoresTheOverrideEndpoint` | override `mirror.example`, pin, clear: getters back to the mirror, `IS_PINNED_ENDPOINT` false; fails if clear also wipes the snapshot or does not clear |
| AC-2 | unit | P0 red | `ApiDomainInteractorPinnedEndpointTest.initialize_keepsThePin_whenPinned` (new file `domain/interactor/api/`) | saved domain `mirror.example`: `CUSTOM_API_DOMAIN` stays null; fails if the `initialize` guard is removed |
| AC-2 | unit | P0 red | `ApiDomainInteractorPinnedEndpointTest.saveCustomDomain_writesNothing_whenPinned` | valid input returns `Success(getState())`; fails if the guard is removed or placed after `saveApiDomain` |
| AC-2 | unit | P0 red | `ApiDomainInteractorPinnedEndpointTest.detectAndSaveWorkingDomain_probesNothing_whenPinned` | `Success(getState())`; fails if the guard is removed |
| AC-2 | unit | P0 red | `ApiDomainInteractorPinnedEndpointTest.detectAndSaveAlternativeBuiltInDomain_probesNothing_whenPinned` | as above for the alternative walk |
| AC-2 | unit | P0 red | `ApiDomainInteractorPinnedEndpointTest.switchToBuiltInDomain_keepsThePin_whenPinned` | target `api.alador.space` returns `getState()`, not null; fails if the guard is removed |
| AC-2 | unit | P0 red | `ApiDomainInteractorPinnedEndpointTest.autoResolveWorkingDomain_probesNothing_whenPinned` | `Success(getState(), changed = false)`; fails if the guard is removed |
| AC-2 | unit | P1 red | `ApiDomainInteractorPinnedEndpointTest.autoResolveWorkingDomain_leavesNoWalkInFlight_whenPinned` | `runBlocking` with a real-time `withTimeout(5.seconds)` (as `ApiDomainInteractorTest.kt:291`, `:308`; under `runTest` the virtual timeout fires at `withContext(Dispatchers.IO)`): pinned call, clear pin, then an unpinned call returns within the timeout; fails if the guard sits after the walk slot is taken |
| AC-2 | unit | P0 red | `ApiDomainInteractorPinnedEndpointTest.resetToDefault_writesNothing_whenPinned` | returns `getState()`; fails if the guard is removed |
| AC-3 | unit | P0 red | `HttpStackTest.create_usesSystemDns_withoutDoh` | `create(useDoh = false)`: `client.dns` is `Dns.SYSTEM`; fails if `useDoh = false` still builds `DnsOverHttps` |
| AC-3 | unit | P1 guard | `HttpStackTest.create_keepsTheAppEventListenerAndRequestsNoDohOne_withoutDoh` | `create(useDoh = false)` keeps the app event listener and requests no DoH client; fails if the no-DoH branch drops the listener or still builds one |
| AC-3 | unit | P0 red | `ApiModulePinnedEndpointTest.pinnedEndpoint_buildsTheAppClientWithoutDoh` (new file `data/di/`, `startApiModule` as in `ApiModuleHttpStackTest.kt:54-66`, pin set before `koinApplication`) | `get<OkHttpClient>().dns` is not `DnsOverHttps`, `get<DnsCache>()` is `get<HttpStack>().dnsCache`; fails if `data/di/modules.kt:70` does not pass `useDoh` |
| AC-4 | manual | P1 | manual check 1 | hooks are `open` with no-op defaults, gate after `super.onCreate()`, `configureRuntime()` directly before `initDi()`, `runtimeModules()` appended last, today's order otherwise |
| AC-5 | manual | P0 guard | manual check 2 | fails on a `setPinnedEndpoint` call site in `app/src/main`, or an assemble, detekt or lint break from `open class PuberApp` and its hooks |
| AC-6 | manual | P2 | manual check 4 | NOT CHECKED (owner: no device) |

### Bugs

N/A: improvement; the `red` rows fail on b53bf631 because the pin API and `useDoh` do not exist.

### Manual checks

1. `git -C <wt> diff b53bf631 -- app/src/main/java/com/kino/puber/PuberApp.kt`. Expected: `open class PuberApp`; `protected open` `shouldInitializeRuntime() = true`, `configureRuntime() = Unit`, `runtimeModules() = emptyList()`; `onCreate` order `appCreateMs`, `super.onCreate()`, gate return, `TelemetryRuntime.install`, `StartupReveal.installFrameWait()`, `configureRuntime()`, `initDi()`, `initLogger()`; `initDi` modules list `+ runtimeModules()`. Must not: move `appCreateMs` or telemetry after the hook, change `attachBaseContext` or `newImageLoader`.
2. In the worktree: `rg -n "setPinnedEndpoint" app/src/main` prints only the definition in `KinoPubConfig.kt`; `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:assembleDevDebug :app:assembleProdRelease` and `check.full` exit 0. Must not: any call site in `app/src/main`, a skipped or disabled test.
3. `rg -l "setPinnedEndpoint" app/src/test`: every listed class calls `clearPinnedEndpoint()` in `@AfterEach`. Must not: a pinning class without it.
4. AC-6, once the owner allows a device: install the worktree dev APK with `tools/android-apk-install-preserve` on `192.168.1.110:5555`, launch `com.kino.puber.stage`. Expected: same first screen as the master dev build. Must not: crash, pairing screen caused by the change, touching `com.kino.puber`.

### UNTESTABLE

AC-6: needs a device; owner decision 2026-11-26 (`state.md: Decisions`) allows none in this run.

### perf

N/A: track off.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Port all three PuberApp hooks (shouldInitializeRuntime, configureRuntime, runtimeModules)? | yes | answered | Yes, all three |
| Q2 | Does shouldInitializeRuntime()==false also skip TelemetryRuntime.install and StartupReveal? | no | answered | Yes; gate right after super.onCreate() |
| Q3 | Keep ApiEndpointMode and make getState() report the pinned domain? | no | answered | Drop ApiEndpointMode; getState() unchanged |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-26 | No device for now (neither emulator nor box); AC-6 stays NOT CHECKED | owner |
| 2026-11-26 | Q1-Q3 as recommended | owner (standing instruction: recommended) |
| 2026-11-26 | Gate 1: spec approved | owner (standing instruction: recommended) |
