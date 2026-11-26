# Test design: hermetic-baseline-seams

ACs: `brief.md#acceptance-criteria`. Design: `design-architect.md`. Base b53bf631. Paths under `app/src/test/kotlin/com/kino/puber/`. Level `unit` = JUnit 5 + MockK on the JVM; `manual` = a command or diff check recorded in the evidence. Unit tests run sequentially in one JVM (`app/build.gradle.kts:455-458`, no parallel config), so the `KinoPubConfig` singleton is shared across classes.

## Risk profile

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

## Traceability

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

## Bugs

N/A: improvement; the `red` rows fail on b53bf631 because the pin API and `useDoh` do not exist.

## Manual checks

1. `git -C <wt> diff b53bf631 -- app/src/main/java/com/kino/puber/PuberApp.kt`. Expected: `open class PuberApp`; `protected open` `shouldInitializeRuntime() = true`, `configureRuntime() = Unit`, `runtimeModules() = emptyList()`; `onCreate` order `appCreateMs`, `super.onCreate()`, gate return, `TelemetryRuntime.install`, `StartupReveal.installFrameWait()`, `configureRuntime()`, `initDi()`, `initLogger()`; `initDi` modules list `+ runtimeModules()`. Must not: move `appCreateMs` or telemetry after the hook, change `attachBaseContext` or `newImageLoader`.
2. In the worktree: `rg -n "setPinnedEndpoint" app/src/main` prints only the definition in `KinoPubConfig.kt`; `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:assembleDevDebug :app:assembleProdRelease` and `check.full` exit 0. Must not: any call site in `app/src/main`, a skipped or disabled test.
3. `rg -l "setPinnedEndpoint" app/src/test`: every listed class calls `clearPinnedEndpoint()` in `@AfterEach`. Must not: a pinning class without it.
4. AC-6, once the owner allows a device: install the worktree dev APK with `tools/android-apk-install-preserve` on `192.168.1.110:5555`, launch `com.kino.puber.stage`. Expected: same first screen as the master dev build. Must not: crash, pairing screen caused by the change, touching `com.kino.puber`.

## UNTESTABLE

AC-6: needs a device; owner decision 2026-11-26 (`state.md: Decisions`) allows none in this run.

## perf

N/A: track off.
