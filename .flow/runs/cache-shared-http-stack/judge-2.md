# Gate - cache-shared-http-stack - round 2

**Decision:** CONCERNS
**Head:** d84d47c2   **Base:** 7bcbfd83

## Done as asked

Read `git -C /home/user1/orca/workspaces/Puber/cache-shared-http-stack diff 7bcbfd83...HEAD`. The production changes match spec Design, and judge-1 "Done as asked" still holds. The rebase changed only two things:

- `PuberApp.kt:133-145`: master's `.reportingToStartupReveal()` stays after `.components { }`, which adds `HttpsEnforcingInterceptor` and the `KtorNetworkFetcherFactory` on the Koin `ImageHttpClientQualifier` client.
- 04bb948c: `ApiDomainInteractorTest.interactorWith` (added on master) now passes `evictDnsCache`. That is one added line and changes no assertion.

Commits after 04bb948c touch only the device evidence (`git diff --stat 04bb948c..HEAD`). `git merge-tree --write-tree master HEAD` reports a clean merge. KinoPubApiClient and CachedPayloadSweeper are untouched (AC-5 KEEP), and nothing listed in Out of scope is changed.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `apiClients_shareOneConnectionPoolAndOneDns`, `dohClient_sharesThePoolButResolvesWithoutDohOnItsOwnDispatcher`; M1 on the merged head | Both are in `build/test-results` (2236 tests, 0 failures); evidence "Round 3 / M1 launch" | PASS |
| AC-2 | `lookup_repeatWithinTtl_issuesNoDohQuery`, `lookup_afterAFailedLookup_asksTheResolverAgain` and siblings | Green in the same results | PASS |
| AC-3 | `resetToDefault_stillClearsContentCacheAndPrefetcherWhenDnsEvictFails`, `autoResolve_failoverToAMirror_evictsTheDnsCache`, `apiDomainInteractor_evictsTheSharedDnsCache` | Green. The master walk `switchToFound` ends in `clearDomainSensitiveCaches` (`ApiDomainInteractor.kt:215-218`; edge-3 traces all switch paths) | PASS (unit, as the AC states) |
| AC-4 | `imageRequest_carriesNoKinoPubAuthHeaderOrQuery`, `HttpsEnforcingInterceptorTest`; M2 on the merged head | Green; evidence "Round 3 / M2" | PASS |
| AC-5 | Device M3 counters (round 1) | Evidence "M3": KEEP branch | PASS (keep branch) |
| AC-6 | `currentEndpoint_returnsTheSameInstanceUntilTheNextOverride` and siblings | Green | PASS |
| AC-7 | M5 round 2; reveal A/B in round 3b | Evidence "Round 2 / Counted runs": host lookups 7 to 6, connections 9 to 7. "Round 3b": reveal median head 3922 ms vs master 4640 ms, with overlapping ranges | PASS |
| AC-8 | Existing suites; check.full | `git diff --numstat 7bcbfd83...HEAD -- app/src/test` deletes 0 lines. qa round 3 ran check.full green at 04bb948c, the last code commit, so I did not run it again | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| Rounds 1-2 findings | triage-1, triage-2 | as in judge-1 Findings | judge-1.md |
| Conflict with master in `PuberApp.kt` | judge-1 concern 1 | fixed | Rebase onto 7bcbfd83, 04bb948c, clean `merge-tree` |
| M1/M2 not run on the merged head | judge-1 concern 1 | fixed | d376f671, evidence "Round 3" |
| Startup cost not measured | judge-1 concern 3, triage-3 #1 | fixed | d84d47c2: A/B against master shows no regression |
| An evict during the startup walk drops the CDN answers too | edge-3 unproven | accepted minor | It only costs one DoH lookup. Round 3b shows no reveal regression |
| Coil's service-loaded Ktor factory comes after the manual one, and no test pins that order | edge-3 unproven, tests-2 F1 | accepted minor | Device evidence shows the `image` label on the shared stack and image lookups through DoH (rounds 2 and 3). A Coil upgrade could reopen this |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | One pool, a TTL DNS cache and fewer lookups and connections (AC-7). Reveal A/B shows no regression. All numbers come from a loaded host (load 6-17) |
| Reliability | Failures are not cached (`HttpStack.kt:21`). DoH has its own Dispatcher. The evict runs inside `clearWithoutFailing`. The DnsCache single is resolved inside the interactor single that `AppStartup` builds on IO (edge-3) |
| Maintainability | Follows the Koin DSL. The old private interceptor and the bootstrap builder are removed. `NetworkStats` ships in release but does nothing there (`factoryOrNull`, `BuildConfig.DEBUG`). The branch has 15 commits |
| Security / privacy | The image client has no plugins. I re-ran `tools/mobile-evidence-audit.sh docs/superpowers/evidence com.kino.puber.stage` myself: passed, 56 files. The evidence holds only counts and timings |
| TV | No UI, focus or D-pad change. Splash reveal and Home render on the merged head (Round 3) |

## Manual checks for the owner

1. Open the series watched in round 1 (evidence "Mistake note", episode 8) on the TV account. Check that the resume position and the watched mark of that episode look as expected. If they do not, reset them from the episode menu.

## Must fix

None.

## Concerns for the owner

1. Possible account mutation: qa started playback by mistake for a few seconds in round 1. Watch progress for that title may have been written. See manual check 1.
2. Round 3b segment B ran on a lease that had likely expired. The release was refused with a token mismatch, and no overlapping agent is known (qa-report "Round 3b" caveat 2).
3. Startup numbers come only from a loaded host. A quiet-host re-measure of the splash reveal on the merged head is still open (qa-report round 3 note 1).
4. The branch has 15 commits. Your rule is fewer than 5 per run, so squash before `/task:ship`.
