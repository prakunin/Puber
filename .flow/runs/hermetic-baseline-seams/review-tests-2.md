# Review tests, round 2

LENS: tests  ROUND: 2  BASE: b53bf631

No findings. HttpStackTest 3 tests and ApiDomainInteractorTest 31 tests pass on their own (0 failures, errors, skipped).
- #2 both new saveCustomDomain tests reach their branch ("https:///path" -> Empty; "bad_host.example" -> Invalid) and check no save and no evict.
- #3 create_keepsTheAppEventListenerAndRequestsNoDohOne_withoutDoh fails if a DoH client is built or the listener dropped; guards of existing behaviour, recorded as such in the plan.
- #4 the always-true assertNotNull is gone; the shared-DnsCache check stays in ApiModulePinnedEndpointTest.
- #1, #5 profile rules and KDoc only; create(Function1;Z) matches the javap record.
- No assertions deleted or weakened elsewhere.
