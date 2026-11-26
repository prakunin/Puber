# Research: hermetic-baseline-seams

## 1. Request

item 1 of the split of hermetic-baseline-profiles. Add seams for hermetic profile generation, with dev and prod behaving exactly as today: (a) a pinned API endpoint in KinoPubConfig that domain switching (the seven ApiDomainInteractor mutators) leaves alone; (b) skip DNS-over-HTTPS in HttpStack (data/api/network/HttpStack.kt:24-37) when the endpoint is pinned; (c) a PuberApp hook that runs before DI starts so a test application subclass can configure the runtime. Re-port of local branch upstream-sync-triage commits a57a3238 and 183f2b7b onto master.

## 2. Understanding

Three inert hooks in production code that the later `instrumentation` flavor (run `hermetic-baseline-generation`) will use: a way to fix the API endpoint to a loopback mock that no domain logic can move, a DNS path that does not query `dns.google` while pinned, and an overridable `PuberApp` step before `startKoin`. Nothing in dev or prod calls the setter, so every default must be a no-op. The branch commits are the design, but three of the four target files were rewritten on master (KinoPubConfig snapshot, HttpStack, AppStartup and the shared walk), so the port is by hand. Umbrella context: `../hermetic-baseline-profiles/research.md`, ACs: `../hermetic-baseline-profiles/brief.md` AC-1..AC-3.

## 3. Current state in code

Paths under `app/src/main/java/com/kino/puber/` unless given in full. Checked at b53bf631.

### KinoPubConfig (`data/api/config/KinoPubConfig.kt`)

- `:20-23` one `@Volatile Snapshot(override, endpoint)`, replaced whole by `setDomainOverride` (`:72-74`); memoised per override since a90c6a8e (2026-11-21).
- `:52-53` `activeEndpoint = snapshot?.endpoint ?: defaultEndpoint`; every URL getter (`:56-66`) reads it, and all KinoPub paths in `data/api/KinoPubApiClient.kt` (`:117`, `:202`, `:873-896`, `:1072`, `:1091`, `:1198`) go through those getters.
- `:59` `CUSTOM_API_DOMAIN = snapshot?.override`; `ApiDomainInteractor.getState()` (`ApiDomainInteractor.kt:70-76`) reports `override ?: DEFAULT_API_DOMAIN`, so under a pin it would show the stock domain, not the pinned one (branch behaves the same).
- `KinoPubApiClient.kt:1029` reads `CURRENT_API_DOMAIN` per call to report reachability; under a pin it reports the pinned domain string.

### ApiDomainInteractor mutators (`domain/interactor/api/ApiDomainInteractor.kt`)

| Mutator | Line | Writes | Callers |
|---|---|---|---|
| `initialize` | 61 | `setDomainOverride(saved ?: BuildConfig.API_DOMAIN_OVERRIDE)` | `core/startup/AppStartup.kt:40` |
| `saveCustomDomain` | 78 | prefs, config, caches, `domainVersion` | `AuthVM.kt:239`, `HomeVM.kt:597`, `DeviceSettingsVM.kt:537` |
| `detectAndSaveWorkingDomain` | 90 | probes built-ins, then `applyEndpoint` (`:238`) | `AuthVM.kt:258`, `HomeVM.kt:616`, `DeviceSettingsVM.kt:555` |
| `detectAndSaveAlternativeBuiltInDomain` | 98 | probes, `applyEndpoint` | `AuthVM.kt:175` |
| `switchToBuiltInDomain` | 117 | `applyEndpoint`; returns `null` for an unknown name | none in `app/src/main` (tests only) |
| `autoResolveWorkingDomain` | 126 | shared walk under `walkLock` (`:129-151`), probe, `switchToFound` (`:181`) | `AppStartup.kt:62`, `AuthVM.kt:79`, `HomeVM.kt:297` |
| `resetToDefault` | 200 | prefs, config, caches | `AuthVM.kt:277`, `HomeVM.kt:634`, `DeviceSettingsVM.kt:573` |

- `clearDomainSensitiveCaches` (`:215-226`) evicts the DNS cache through the `evictDnsCache` lambda (new since the branch; wired at `domain/di/modules.kt:46-55`), the content cache and the prefetcher.
- `autoResolveWorkingDomain` is no longer wrapped in `withContext(IO)`; a pin guard has to sit before the `walkLock` block so no walk slot is created.

### HttpStack (`data/api/network/HttpStack.kt`)

- `:24-37` `create(listeners)` builds base client, a `DnsCache` (`:21`), a DoH client on its own dispatcher (`:28-31`), `DnsOverHttpsFactory.create` (`data/api/network/DnsOverHttpsFactory.kt:15-27`, `https://dns.google/dns-query`, bootstrap 8.8.4.4/8.8.8.8) and `.dns(doh)` on the app client (`:33-36`).
- Built lazily as a Koin single at `data/di/modules.kt:70`; `OkHttpClient` and `DnsCache` singles derive from it (`:71-72`). Koin singles resolve after `startKoin`, so a pin set before `startKoin` is visible when the stack is built.
- The `DnsCache` single must survive a pinned build: `ApiDomainInteractor` wiring needs it (`domain/di/modules.kt:47`).
- Whether OkHttp 5.5.1 (`gradle/libs.versions.toml:14`) skips `Dns.lookup` for an IP-literal host such as `127.0.0.1`: `unverified`; the bypass makes the answer irrelevant.

### PuberApp and startup order (`PuberApp.kt`)

- `:68` `class PuberApp` is final; manifest `app/src/main/AndroidManifest.xml:22` names `.PuberApp`; no `android:process` anywhere in main.
- `:77-85` `onCreate`: `super.onCreate()`, `TelemetryRuntime.install` (`core/telemetry/TelemetryRuntime.kt:129-177`: prefs, file store under external files dir, activity callbacks, startup marks), `StartupReveal.installFrameWait()` (`core/startup/StartupReveal.kt:60`), `initDi()`, `initLogger()`.
- `:87-101` `initDi`: `startKoin` with six modules (incl. `telemetryModule`, new since the branch), then `AppStartup.start()` and `CachedPayloadSweeper.start()`.
- `core/startup/AppStartup.kt:36-68` runs `initialize()` then, with a token, `autoResolveWorkingDomain()` and the Home warm-up on a background scope; with both mutators guarded, a pinned app warms Home against the pinned endpoint.

### Existing tests

| Test | Covers | Port impact |
|---|---|---|
| `app/src/test/kotlin/com/kino/puber/data/api/config/KinoPubConfigTest.kt` | 3 tests: getters per override, memoisation, switching together; resets with `setDomainOverride(null)` | Add pin cases here; also reset the pin in `@AfterEach` (shared singleton) |
| `.../domain/interactor/api/ApiDomainInteractorTest.kt` | 25 tests; `mockkObject(KinoPubConfig)` stubs 6 members (`:72-82`), others call through | A new `IS_PINNED_ENDPOINT` read calls the real one (false); no test calls `initialize`, `detectAndSaveWorkingDomain` or `detectAndSaveAlternativeBuiltInDomain` directly, so AC-1 needs new unpinned cases for those |
| `.../domain/interactor/api/ApiDomainStartupTest.kt` | `resolveStartupDomain` only | none |
| `.../domain/di/ApiDomainInteractorWiringTest.kt` | interactor evicts the shared `DnsCache` | none if the `DnsCache` single stays |
| `.../data/di/ApiModuleHttpStackTest.kt` | casts `shared.dns as DnsOverHttps`; pool and DNS shared by all clients | Unchanged while unpinned; a pinned case belongs in a new `HttpStack` test |
| `.../core/startup/AppStartupTest.kt` | start order, warm-up, no token | none |
| `.../data/api/network/DnsOverHttpsFactoryTest.kt`, `ImageHttpClientTest.kt` | call `HttpStack.createDnsCache()` / `HttpStack.create()` | Keep `create()` callable with defaults |

No test covers `PuberApp.onCreate` order.

### Branch commits mapped onto master

| Branch change | Branch target | Master target | Port |
|---|---|---|---|
| a57a3238 `ApiEndpointMode`, `pinnedEndpoint` field, `IS_PINNED_ENDPOINT`, `CURRENT_ENDPOINT_MODE`, `setPinnedEndpoint`/`clearPinnedEndpoint` | `KinoPubConfig` with plain `domainOverride` | Snapshot `KinoPubConfig` (`:20-23`, `:52-53`) | Rewrite: separate volatile read first in `activeEndpoint`, or a pin inside `Snapshot` (Options) |
| a57a3238 guard in 7 mutators (early return with `getState()` / `Success(changed=false)`) | `ApiDomainInteractor` before the shared walk | `:61`, `:78`, `:90`, `:98`, `:117`, `:126`, `:200` | Same guards; `autoResolve` guard before `walkLock`; guards return before `domainVersion` and cache clears |
| a57a3238 `if (!IS_PINNED_ENDPOINT) dns(DoH)` | `data/di/modules.kt` OkHttpClient single | `HttpStack.create` (`:24-37`) | Rewrite against HttpStack |
| a57a3238 `KinoPubConfigTest` (new file), `ApiDomainInteractorPinnedEndpointTest` (14 tests, 7 pinned + 7 unpinned) | new files | master already has `KinoPubConfigTest` | Merge pin cases into master's file; port the interactor test with the `evictDnsCache` argument and assert it is not called when pinned |
| 183f2b7b `open class PuberApp`, `shouldInitializeRuntime`, `configureRuntime`, `runtimeModules` | `onCreate` without telemetry, `initDi` calling `initialize()` | `PuberApp.kt:68-101` | Rewrite: decide gate and hook placement relative to `TelemetryRuntime.install` and `StartupReveal` |

Consumer of the seams on the branch: `app/src/instrumentation/java/com/kino/puber/profile/BaselineProfileApp.kt` (overrides all three hooks; `runtimeModules` replaces `ICryptoPreferenceRepository` and `IAppUpdateInteractor`; skips runtime in process `:baseline_profile_control`) and `BaselineInstrumentationEnvironment.kt` (`domain = "127.0.0.1:<port>"`, `http://` URLs).

## 4. Related work

| Item | Relation |
|---|---|
| `hermetic-baseline-profiles` (umbrella) | This run is split item 1; AC-1..AC-3 |
| `hermetic-baseline-generation` (idea) | Item 2; only consumer of these seams |
| Branch spec `upstream-sync-triage:docs/superpowers/specs/2026-10-11-upstream-sync-triage-design.md` D14, D19, AC-15 | Seam design: all defaults no-op, setter only under `src/instrumentation`, tests pinned and unpinned for all seven |
| 4f72f028 (2026-11-21) | Introduced `HttpStack` and the `evictDnsCache` lambda; run `http-stack-consolidation` is `dropped`, work landed elsewhere |
| a90c6a8e (2026-11-21) | Snapshot `KinoPubConfig` |
| 50e791be (2026-11-21) | `AppStartup` and the shared walk |
| a8b74f38 (2026-10-15) | `TelemetryRuntime.install` in `onCreate` |
| `app-coroutine-scope` (idea) | Touches startup scopes; no overlap with the hooks |

Duplicate: none. No other run or spec mentions a pinned endpoint or `configureRuntime`.

## 5. External findings

External findings: off (local research only).

## 6. Options

| Option | KinoPubConfig shape | DoH bypass | PuberApp | Trade-offs | Size |
|---|---|---|---|---|---|
| A (recommended) | Separate `@Volatile pinnedEndpoint`, read first in `activeEndpoint`; `IS_PINNED_ENDPOINT`, `setPinnedEndpoint`, `clearPinnedEndpoint` (clear for tests); drop `ApiEndpointMode` (no caller) | `HttpStack.create(listeners, useDoh = true)`; `data/di/modules.kt:70` passes `!KinoPubConfig.IS_PINNED_ENDPOINT`; `DnsCache` still created | `open`; `shouldInitializeRuntime()` checked first in `onCreate` (skips telemetry, reveal, DI, logger); `configureRuntime()` right before `initDi()`; `runtimeModules()` appended to `startKoin` | Closest to branch D14; HttpStack stays free of global state and testable both ways; one extra volatile read per URL getter; item 2 needs no further main-source change | M (DI startup and networking are risk areas) |
| B | Pin stored inside `Snapshot` (`Snapshot(override, endpoint, pinned)`), `setDomainOverride` keeps a pin | `HttpStack.create` reads `KinoPubConfig.IS_PINNED_ENDPOINT` itself | as A | Keeps one volatile read; config enforces the pin even without interactor guards; `setDomainOverride` semantics change, and HttpStack gains a global dependency | M |
| C | as A | as A | Only `open` + `configureRuntime()` (literal request (c)); `shouldInitializeRuntime` and `runtimeModules` left to item 2 | Smallest diff now; item 2 must reopen `PuberApp.kt` in main source, which this run exists to settle | S |

## 7. Risks

| Risk | Mitigation |
|---|---|
| A guard or the DoH switch active while unpinned breaks domain fallback or DNS for users | Unit tests per mutator pinned and unpinned (three mutators have no direct test today); `ApiModuleHttpStackTest` unchanged and green; grep that `setPinnedEndpoint` has no caller in `app/src/main` |
| Pin set after Koin builds `HttpStack` keeps DoH on | Hook placed before `startKoin`; test that `create(useDoh = false)` sets no `DnsOverHttps` and still exposes a `DnsCache` |
| Moving `onCreate` steps changes telemetry startup marks in dev/prod | Keep `appCreateMs`, `TelemetryRuntime.install`, `StartupReveal` in today's order; the gate defaults to true |
| `KinoPubConfig` is a JVM singleton: a pin leaking between tests flips other suites | `clearPinnedEndpoint()` in `@AfterEach` of every test that pins |
| Runtime check (AC-3 launch) not possible: owner decision, no device | AC-3 device part NOT VERIFIED; static part (grep, `assembleDevDebug`/`assembleProdRelease` build) still runs |

## 8. Open questions

| ID | Question | Recommended answer |
|---|---|---|
| Q1 | Port all three `PuberApp` hooks (`shouldInitializeRuntime`, `configureRuntime`, `runtimeModules`) or only `configureRuntime`? | All three (option A), so item 2 adds no main-source change |
| Q2 | Does `shouldInitializeRuntime() == false` also skip `TelemetryRuntime.install` and `StartupReveal`? | Yes: gate right after `super.onCreate()`; a control process has no UI and must not write telemetry |
| Q3 | Keep the branch's `ApiEndpointMode` / `CURRENT_ENDPOINT_MODE`? | No: nothing reads it on the branch or master |
| Q4 | Should `getState()` report the pinned domain while pinned? | No change: no UI exists in the instrumentation app that depends on it; the guards return `getState()` as the branch did |

## 9. Sources

- Files: `app/src/main/java/com/kino/puber/{PuberApp.kt,core/startup/AppStartup.kt,core/startup/StartupReveal.kt,core/telemetry/TelemetryRuntime.kt,data/api/config/KinoPubConfig.kt,data/api/network/HttpStack.kt,data/api/network/DnsOverHttpsFactory.kt,data/api/KinoPubApiClient.kt,data/di/modules.kt,domain/di/modules.kt,domain/interactor/api/ApiDomainInteractor.kt}`, `app/src/main/AndroidManifest.xml`, tests listed in section 3, `gradle/libs.versions.toml`, `docs/architecture/{api,screens-and-di}.md`, `AGENTS.md`
- Commits: a57a3238, 183f2b7b, 4f72f028, a90c6a8e, 50e791be, a8b74f38; merge-base of `upstream-sync-triage` 358b0ad6
- Branch files: `app/src/instrumentation/java/com/kino/puber/profile/{BaselineProfileApp,BaselineInstrumentationEnvironment}.kt`, `app/src/test/.../ApiDomainInteractorPinnedEndpointTest.kt`, spec `docs/superpowers/specs/2026-10-11-upstream-sync-triage-design.md` (D14, D19, AC-15)
- Runs: `hermetic-baseline-profiles`, `hermetic-baseline-generation`, `http-stack-consolidation`, `app-coroutine-scope`, `upstream-sync-triage`
