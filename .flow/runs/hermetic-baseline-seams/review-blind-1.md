# Review blind, round 1

LENS: blind  ROUND: 1  BASE: b53bf631

### F1 [minor] Nothing in the change uses the new hooks or sets the pin
- Where: PuberApp.kt:91-97, KinoPubConfig.kt:81
- Problem: three PuberApp hooks, open class, setPinnedEndpoint; nothing in app/ or baselineprofile/ overrides the hooks or calls setPinnedEndpoint (only unit tests); no subclass, source set or manifest entry loads an override.
- Failure scenario: profile generation from this build still starts the real PuberApp, IS_PINNED_ENDPOINT is false, DoH to live domains; the commit name and HttpStack KDoc suggest it is hermetic.
- Fix: add the subclass and wiring, or state plainly in the commit and KDoc that these are unused seams for a later change.

### F2 [nit] The non-DoH HttpStack creates a DNS cache that nothing fills, and its test cannot fail
- Where: HttpStack.kt:33-38; HttpStackTest.kt (assertNotNull(stack.dnsCache))
- Problem: with useDoh = false the dnsCache is not attached to any resolver, so evictDnsCache does nothing; the class KDoc says the resolver's answers live in dnsCache (false on this path); dnsCache is non-null, so assertNotNull always passes.
- Fix: fix the KDoc; replace assertNotNull with an assertion that can fail, or drop it.

### Unproven
- ApiDomainInteractor.kt:71 getState() while pinned reports DEFAULT_API_DOMAIN; domain dialogs would show the live domain once a pinned build shows them.
- ApiDomainInteractor.kt:81 saveCustomDomain returns Success while pinned even for invalid input.
- modules.kt:74 useDoh read once when the HttpStack single is created; changing the pin after startKoin leaves the resolver unchanged.
