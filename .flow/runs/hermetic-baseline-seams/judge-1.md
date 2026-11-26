# Gate - hermetic-baseline-seams - round 1

**Decision:** CONCERNS
**Head:** 4268236c   **Base:** b53bf631

## Done as asked

The diff (`git -C /home/dev/orca/workspaces/Puber/hermetic-baseline-seams diff b53bf631...HEAD`) matches the spec's Design: a separate volatile `pinnedEndpoint` read first in `activeEndpoint` (`KinoPubConfig.kt:56`), a guard as the first statement of all seven `ApiDomainInteractor` mutators, `HttpStack.create(listeners, useDoh = true)` with the Koin single passing `!IS_PINNED_ENDPOINT` (`data/di/modules.kt:71-76`), and `open class PuberApp` with three hooks in the specified `onCreate` order. Nothing from Out of scope: no flavor, mock backend or profile regeneration. The two profile rules are hand-edited to the new descriptor, which `javap` on the prodRelease `HttpStack$Companion.class` confirms: `create(Lkotlin/jvm/functions/Function1;Z)`.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `ApiDomainInteractorTest` (four new unpinned cases plus the existing ones), `KinoPubConfigTest`, `HttpStackTest.create_resolvesThroughDoh_byDefault`, `ApiModuleHttpStackTest` (not in the diff) | judge check.full at 4268236c: 2654 tests, 0 failures, errors or skips | PASS |
| AC-2 | `KinoPubConfigTest` pin cases (3), `ApiDomainInteractorPinnedEndpointTest` (8); `clearPinnedEndpoint()` in `@AfterEach` of all three pinning classes (rg) | same run; the test names appear in the result XML | PASS |
| AC-3 | `HttpStackTest.create_usesSystemDns_withoutDoh`, `create_keepsTheAppEventListenerAndRequestsNoDohOne_withoutDoh`, `ApiModulePinnedEndpointTest.pinnedEndpoint_buildsTheAppClientWithoutDoh` | same run | PASS |
| AC-4 | manual check 1 (`PuberApp.kt` diff) | judge read: gate after `super.onCreate()`, `configureRuntime()` directly before `initDi()`, `+ runtimeModules()` last, `attachBaseContext`/`newImageLoader` untouched | PASS |
| AC-5 | manual check 2 | `rg setPinnedEndpoint app/src/main`: only `KinoPubConfig.kt:84`; judge `:app:assembleDevDebug :app:assembleProdRelease` exit 0; check.full exit 0 | PASS |
| AC-6 | manual check 4 (device launch) | none: owner decision, no device | NOT CHECKED |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| Profile rules name the old `create(Function1)` | codex-1 | fixed | 5a688a1b; javap above |
| `saveCustomDomain` Empty/Invalid untested | tests-1 F1 | fixed | 0d62af49 |
| App event listener without DoH untested | tests-1 F2 | fixed | 0d62af49 |
| Unused `dnsCache` KDoc, always-true assert | blind-1 F2 | fixed | f5adb9c9, 0d62af49 |
| Seams have no caller | blind-1 F1 | fixed (KDoc states it) | f5adb9c9 |
| `getState()`/`saveCustomDomain` report default or Success while pinned | blind, edge, codex | rejected, accepted | spec Q3; no pin caller in `app/src/main` (rg) |
| Pin changed after `HttpStack` built or mid-walk | edge, blind, codex | rejected, accepted | design risk 4: pin only from `configureRuntime()`; no caller |
| `shouldInitializeRuntime() == false` with images | edge | rejected, accepted | default `true`; design risk 5 |
| Pinned-test coverage of moved guards | tests-1 unproven | rejected, accepted | every guard is the first statement (diff) |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | One extra volatile read per URL getter; startup rule kept in AOT via the edited profile lines |
| Reliability | Unpinned paths unchanged (diff + AC-1 tests); default `useDoh = true` and hook defaults keep dev/prod startup as today |
| Maintainability | Follows Koin and detekt conventions; detekt and lint green; no dead code beyond the intended seams, which KDoc labels |
| Security / privacy | No secrets or account data; pin URLs are `127.0.0.1` test fixtures; no device evidence produced |

## Manual checks for the owner

1. AC-6, once a device is allowed: in the worktree build `:app:assembleDevDebug` with `./tools/agentw`, lease `192.168.1.110:5555`, install the dev APK with `tools/android-apk-install-preserve`, launch `com.kino.puber.stage`. Expected: the same first screen as the master dev build, no crash. Do not touch `com.kino.puber`.

## Must fix

none

## Concerns for the owner

1. The spec's Test design AC-3 row (`docs/superpowers/specs/2026-11-26-hermetic-baseline-seams.md:164`) and the plan's `:127` still name `HttpStackTest.create_usesSystemDnsAndKeepsTheDnsCache_withoutDoh`, which fix round 1 renamed to `create_usesSystemDns_withoutDoh` (rename recorded only at plan `:220`). A one-line spec edit before ship keeps the named-test trace true.
