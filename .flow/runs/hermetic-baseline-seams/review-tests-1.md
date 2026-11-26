# Review tests, round 1

LENS: tests  ROUND: 1  BASE: b53bf631

### F1 [minor] The Empty and Invalid branches of saveCustomDomain were rewritten and no test covers them
- Where: ApiDomainInteractor.kt:80-93
- Problem: the rewrite into a when makes the Empty and Invalid branches new code; every saveCustomDomain call in ApiDomainInteractorTest (lines 129, 164, 336, 420, 441) passes a valid host; VM tests mock the interactor.
- Failure scenario: swapped conditions or a dropped Invalid arm would save "bad host!" to prefs and clear caches; suite stays green.
- Fix: two unpinned cases: saveCustomDomain("  ") -> Empty, saveCustomDomain("bad host!") -> Invalid, each verifying no saveApiDomain and evictCalls == 0.

### F2 [minor] No test checks that the app client keeps its event listener when DoH is off
- Where: HttpStack.kt:34-39
- Problem: the useDoh = false branch attaches listeners?.invoke("app") separately; HttpStackTest calls create(useDoh = false) without listeners; ApiModulePinnedEndpointTest checks only dns and DnsCache.
- Failure scenario: a pinned instrumentation build loses NetworkStats::factory on the app client; no test catches it.
- Fix: create({ label -> factoryFor(label) }, useDoh = false) and assert stack.client.eventListenerFactory is the "app" factory.

Suite state: check.full exit 0 at c50f506c (tests UP-TO-DATE from the 10:43 run; 2651 tests, 0 skipped/failed). Every P0 row has its named test; every P0 red test has a red run; each of the eight pinned interactor tests fails if its guard is removed; AC-4 by diff; AC-6 NOT CHECKED (owner).

### Unproven
- ApiDomainInteractorPinnedEndpointTest.kt:111 pinned saveCustomDomain passes only valid input; a guard moved after validation would still pass.
- ApiDomainInteractorPinnedEndpointTest.kt:157 leavesNoWalkInFlight catches only a guard between taking the walk slot and the try.
