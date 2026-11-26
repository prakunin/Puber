# Brief: hermetic-baseline-seams

Kind: improvement   Size: M   Tracks: none   Device verification: yes (DI startup, networking); not run - owner decision 2026-11-26, no device (`state.md: Decisions`)

## Request

Split item 1 of `hermetic-baseline-profiles` (`../hermetic-baseline-profiles/brief.md: Split`, AC-1..AC-3 there). Add three inert seams that the later `instrumentation` flavor uses, with dev and prod unchanged: (a) a pinned API endpoint in `KinoPubConfig` that the seven `ApiDomainInteractor` mutators leave alone; (b) no DNS-over-HTTPS in `HttpStack` while pinned; (c) `PuberApp` hooks that run before `startKoin`. Hand re-port of `upstream-sync-triage` a57a3238 and 183f2b7b.

## Findings

Code map, mutator table, test impact and branch-to-master mapping: `research.md: Current state in code`. Option A recommended (`research.md: Options`). Facts this brief relies on, rechecked at b53bf631 (paths under `app/src/main/java/com/kino/puber/`):

| Where | Fact |
|---|---|
| `data/api/config/KinoPubConfig.kt:20-23`, `:52-53`, `:72-74` | One volatile `Snapshot`; every URL getter reads `activeEndpoint`; `setDomainOverride` replaces the snapshot whole |
| `domain/interactor/api/ApiDomainInteractor.kt:61`, `:78`, `:90`, `:98`, `:117`, `:126`, `:200` | The seven mutators; `autoResolveWorkingDomain` enters `walkLock` at `:130`, so its guard goes before the loop |
| `data/api/network/HttpStack.kt:24-37` | `create(listeners)` always installs `DnsOverHttpsFactory` (`dns.google`) on the app client |
| `data/di/modules.kt:70-72` | `HttpStack` is a lazy Koin single; `OkHttpClient` and `DnsCache` singles derive from it, so a pin set before `startKoin` is seen at build time |
| `PuberApp.kt:68`, `:77-101` | Final class; `onCreate` = `TelemetryRuntime.install`, `StartupReveal.installFrameWait`, `initDi` (six modules, `AppStartup.start`, `CachedPayloadSweeper.start`), `initLogger` |
| `app/build.gradle.kts:138-186` | Release signing falls back to debug-keystore credentials, so `assembleProdRelease` builds in a worktree without a keystore |

## Size

M: one goal, but it touches three risk areas from `.flow/roles/analyst.md` (networking, startup and Koin wiring, the baseline profile route) across config, domain, network and application layers.

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

## Risks

| Risk | Mitigation |
|---|---|
| A guard or the DoH switch fires while unpinned and breaks domain fallback or DNS for users | AC-1 per mutator; default `useDoh = true`; AC-5 grep |
| Reordering `onCreate` shifts telemetry startup marks (`appCreateMs`) | AC-4 keeps today's order; gate defaults to true |
| `KinoPubConfig` is a JVM singleton; a pin leaking between tests flips other suites | `clearPinnedEndpoint()` in `@AfterEach` of every test that pins |
| No device: a startup crash in the dev build ships unseen | AC-6 recorded NOT CHECKED; AC-4 unit test and AC-5 build are the only runtime proxies |

## Open questions

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| Q1 | Port all three `PuberApp` hooks (`shouldInitializeRuntime`, `configureRuntime`, `runtimeModules`) or only `configureRuntime` as the umbrella split line says? | All three (research option A): item 2's `BaselineProfileApp` needs each, and this run exists so item 2 adds no main-source change | yes |
| Q2 | Does `shouldInitializeRuntime() == false` also skip `TelemetryRuntime.install` and `StartupReveal`? | Yes: gate right after `super.onCreate()`; the `:baseline_profile_control` process has no UI and must not write telemetry | no |
| Q3 | Keep the branch's `ApiEndpointMode` / `CURRENT_ENDPOINT_MODE`, and should `getState()` report the pinned domain? | Drop `ApiEndpointMode` (no reader on branch or master); `getState()` unchanged, guards return it as on the branch | no |
