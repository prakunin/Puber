# Design (architect): hermetic-baseline-seams

Paths under `app/src/main/java/com/kino/puber/` unless given in full. Citations checked in the worktree at b53bf631. ACs: `brief.md#acceptance-criteria`; Q1-Q3 answered in `state.md`.

## Decisions

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

## Changes by layer

| Layer | File | Change |
|---|---|---|
| API / repository | `data/api/config/KinoPubConfig.kt` | `pinnedEndpoint` field next to `snapshot` (`:22-23`); `activeEndpoint` (`:52-53`) reads it first; `IS_PINNED_ENDPOINT` with the getters (`:55-59`); `setPinnedEndpoint` / `clearPinnedEndpoint` after `setDomainOverride` (`:72-74`) |
| API / repository | `data/api/network/HttpStack.kt` | `useDoh` parameter on `create` (`:24`); DoH client and `.dns(doh)` (`:28-34`) only when true; class KDoc (`:9-12`) says the resolver is DoH unless the endpoint is pinned |
| API / repository | `data/di/modules.kt` | `:70` passes `useDoh = !KinoPubConfig.IS_PINNED_ENDPOINT`; import `com.kino.puber.data.api.config.KinoPubConfig` |
| Domain | `domain/interactor/api/ApiDomainInteractor.kt` | Seven guards at `:61`, `:78`, `:90`, `:98`, `:117`, `:126`, `:200` as in Decisions |
| Application / DI | `PuberApp.kt` | `open`, three hooks, gate and hook in `onCreate` (`:77-85`), module list in `initDi` (`:88-98`); import `org.koin.core.module.Module`; `attachBaseContext` (`:73-75`) and `newImageLoader` (`:109-146`) unchanged |
| ViewModel, Compose, resources | none | N/A: no screen, state or string changes |
| Tests | `app/src/test/kotlin/com/kino/puber/data/api/config/KinoPubConfigTest.kt`; `.../domain/interactor/api/` (new pinned/unpinned interactor test); `.../data/di/` (new pinned `apiModule` test) | Test design owns names and counts; `ApiModuleHttpStackTest.kt` stays unchanged (AC-1) |

## Alternatives rejected

- Pin inside `Snapshot` (research option B): changes `setDomainOverride` semantics and makes `HttpStack` read a global.
- `HttpStack.create` reading `KinoPubConfig.IS_PINNED_ENDPOINT` itself: hides a global dependency in a factory unit tests call directly.
- Enforce the pin in `setDomainOverride` instead of guards: probes, pref writes and cache clears would still run (fails AC-2).
- Only `configureRuntime()` (option C): item 2 would reopen `PuberApp.kt` (Q1).
- `configureRuntime()` before `TelemetryRuntime.install`: no consumer needs it there, and it would put subclass code between `appCreateMs` and the telemetry marks.

## Risks

| Risk | Area | Lives in | Mitigation |
|---|---|---|---|
| A guard taken while unpinned breaks domain fallback for users | Correctness | `ApiDomainInteractor.kt` guards | Guard reads only `pinnedEndpoint != null`; AC-1 unpinned test per mutator incl. the three with no direct test today; AC-5 grep that `setPinnedEndpoint` has no caller in `app/src/main` |
| A pin leaks between tests: `KinoPubConfig` is a JVM singleton, so a missed `clearPinnedEndpoint()` flips `IS_PINNED_ENDPOINT` for later suites (`ApiModuleHttpStackTest`, `ApiDomainInteractorTest`) | Lifecycle | test classes that pin | `clearPinnedEndpoint()` in `@AfterEach` of every pinning test; `mockkObject(KinoPubConfig)` in `ApiDomainInteractorTest.kt:72` calls through to the real field, so a leak is visible there |
| `onCreate` reordering shifts startup telemetry or the reveal | Rollout | `PuberApp.kt:77-85` | Order fixed in Decisions; AC-4 checks it in the diff; AC-6 device launch NOT CHECKED (owner) |
| Pin set after `HttpStack` is built keeps DoH on | Lifecycle | `data/di/modules.kt:70` lazy single | Pin only from `configureRuntime()`, before `startKoin`; nothing resolves `HttpStack` eagerly |
| With `shouldInitializeRuntime() == false`, Koin never starts but `newImageLoader` (`PuberApp.kt:139`) calls `get<HttpClient>` | Lifecycle | `PuberApp.kt:109-146` | Only item 2's UI-less control process returns false; it must load no image. Default `true` leaves dev/prod as today |
| Pinned stack uses system DNS for `127.0.0.1` | Security | `HttpStack.kt` | No `dns.google` query while pinned (the goal); unpinned DoH unchanged, asserted by `ApiModuleHttpStackTest` |
| One more volatile read per URL getter | Performance | `KinoPubConfig.kt:52-53` | Negligible next to the request it builds |

## Open questions

none
