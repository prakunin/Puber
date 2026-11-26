# Gate - hermetic-baseline-generation - round 1

**Decision:** PASS
**Head:** 811fde6b   **Base:** 5cd3e37e

## Done as asked

The diff (`git diff 5cd3e37e...HEAD`) adds the `instrumentation` flavor (`app/build.gradle.kts:237-247`, sources only under `app/src/instrumentation/`), the HTTPS loopback mock and per-iteration checks in `:baselineprofile`, deletes `app/src/nonMinifiedRelease/` and both `*NonMinifiedRelease` manifests, strips pairing from `tools/generate-baseline-profile.sh` and `BaselineProfileGenerator.kt`, rewrites the profile section of `docs/release.md` and regenerates both profiles. The main-source changes are the Q5-approved D21, D22, D24 seams (`KinoPubConfig.DNS_RESOLVER`, `DohResolver`, `AppUpdateRepository(githubApiBaseUrl)`, pinned-only candidate list in `ApiDomainInteractor.buildAutoResolveCandidates`); unpinned defaults are unchanged. StartupBenchmarks and Makefile F2-F5 hardening stay out, as the brief says. The `check.full` extension (Q3) is a ship-time edit of `.flow/config.yaml` (D18); it is not in the config yet.

## Traceability

Evidence: `ev1` = `docs/superpowers/evidence/2026-11-26-hermetic-baseline-generation-device.md`, `ev2` = `...-r2-device.md` (worktree). "Re-checked" marks what I verified at 811fde6b.

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | M1, emulator generation run | ev2 AC-1: 4 journeys pass, 17 tests 0 failures; re-checked: 0 non-`H` method rules in `baseline-prof.txt`, `startup-prof.txt` keeps S/P | PASS |
| AC-2 | `BaselineProfileGenerator` per-iteration `JourneyCheck` and late checks | ev2: 35 iterations missing=[] unknown=[], journal empty, 9 playback iterations with `.m3u8`, 6 `.ts`, `watching/marktime` | PASS |
| AC-3 | `BaselineNetworkBlockerTest`, `BaselineInstrumentationEnvironmentTest`, `BaselineMockSelfTest` | qa round 2: 19 instrumentation unit tests green; self-test 11/11 on device | PASS |
| AC-4 | `BaselineProfileContentTest` (`profilesHaveNoInstrumentationOrGeneratorClasses`, `profilesCoverProdColdStartPaths`) | qa round 2 green; re-checked: 0 `profile/`/`baselineprofile/` rules in both files, `CryptoPreferenceRepository;->decrypt` and `okhttp3/dnsoverhttps/` present in `startup-prof.txt` | PASS |
| AC-5 | `baselineProfileCoversImageTlsAndHlsPaths`, per-package counts | ev2 AC-4/AC-5 table with drops over 20 % listed | PASS |
| AC-6 | APK inspection of `assembleProdRelease`, `assembleDevRelease` | ev2 AC-6 table, positive control on the instrumentation APK | PASS |
| AC-7 | extended `check.full` | qa round 2 CHECK: 2726 + 19 tests, detekt and lint for both variants | PASS |
| AC-8 | greps | re-checked: no `CLIENT_SECRET`, `pair`, `PuberAuth`, `profile.auth` in the script, generator, `Makefile` `baseline-profile` target or `release.md` profile section (`release.md:61` is the CI secret list); no `NonMinified` source sets | PASS |
| AC-9 | M6 on the box | ev2 AC-9: install-preserve same signer, sha256 match, COLD and HOT launch on Home, crash buffer empty | PASS |
| AC-10 | `HttpStackTest`, `DnsOverHttpsFactoryTest`, `ApiModulePinnedEndpointTest`, `AppUpdateRepositoryTest` | qa round 2 green; re-checked in diff: `DohResolver.GOOGLE` = `https://dns.google/dns-query`, 8.8.4.4, 8.8.8.8; `GITHUB_API_BASE_URL` default | PASS |
| AC-11 | `ApiDomainInteractorPinnedEndpointTest` | qa round 2: 9/9 green | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| Profiles drop prod cold-start paths | review-blind-1 F1 (major) | fixed | 7f3917e8..9b7934a3; guard `profilesCoverProdColdStartPaths` |
| Blocker install untested; NSC anchors unchecked | review-tests-1 F1 | fixed | `BaselineInstrumentationEnvironmentTest`, `BaselineNetworkSecurityConfigTest` |
| Late requests unchecked | review-edge-1 F1 | fixed | aeaa2a09 (`checkLate` in `@Before` and `@AfterClass`) |
| AC-9 profile not embedded | review-codex-1, review-tests-1 F2 | fixed (AC amended, Q6) | 43d68e7e |
| AC-1 all-H wording | review-tests-1 F3 | fixed (AC amended, Q7) | 43d68e7e |
| Spec D7, D14, R15 drift | review-edge-1 F2, review-tests-1 F4 | fixed | spec rows 269-271 supersede rows 93, 100 |
| New JVM tests in no CI gate | review-blind-1 F2 | rejected, accepted: Q3 keeps PR checks unchanged | state.md Q3 |
| StartupBenchmarks without backend | review-blind-1 F3 | rejected, accepted: owned by `startup-benchmark-entry` (Q4) | `docs/release.md:145` |
| ProxySelector bypass, `result=-1` substring, missing seg | codex-1, blind/edge unproven | rejected, accepted (spot-checked): no `.proxy(` or `HttpURLConnection` in `app/src/main/java`; NSC trusts only the mock root with cleartext off | `app/src/instrumentation/res/xml/network_security_config.xml` |
| Late check before resets cascades failures | review-edge-2 F1 | accepted minor | triage-2 #1 |
| Pinned-resolver test asserts type only | review-tests-2 F1 | accepted minor | triage-2 #2 |
| Hardcoded port 18765 in test | review-codex-2 | accepted minor | triage-2 #3 |
| Truncated DoH query throws in mock | review-antigravity-2 | accepted minor | triage-2 #4 |
| `okhttp3/internal/http1` 51 -> 0, serialization drops | qa round 2 | deferred | run `baseline-mock-protocol-parity` (stage idea) |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | Prod cold-start paths guarded by 11 substrings in both profiles; AC-5 packages grew. `okhttp3/internal/http1` lost from the profile (deferred run) |
| Reliability | Blocker fails closed (dead proxy) and journals; generation fails on any unknown route or journal line. Unpinned prod paths unchanged |
| Maintainability | No dead code: `useDoh`, `AuthEventLog`, `profile.auth` have no remaining references. `Makefile` API 34 refusal still cites "keeps app data", now not the reason |
| Security / privacy | Committed leaf private key and root cert are loopback-only test material, absent from release APKs (ev2 AC-6); root key discarded. Evidence audit re-run: `evidence_audit_status=passed`; box screenshots not saved |

## Manual checks for the owner

none

## Must fix

none

## Concerns for the owner

none
