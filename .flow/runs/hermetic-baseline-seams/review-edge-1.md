# Review edge, round 1

LENS: edge  ROUND: 1  BASE: b53bf631

No findings.

### Unproven
- KinoPubConfig.kt:81 a pin set or cleared after Koin has started: the lazy HttpStack single keeps its DNS choice and a running unpinned autoResolveWorkingDomain walk would still write prefs; real only if setPinnedEndpoint/clearPinnedEndpoint is called anywhere other than configureRuntime().
- ApiDomainInteractor.kt:81 while pinned, saveCustomDomain returns Success(getState()) even for invalid input and switchToBuiltInDomain returns a state for an unknown domain; real only if a pinned build shows the domain dialogs.
- ApiDomainInteractor.kt:71 while pinned, getState() reports the snapshot/default domain, not the pinned one (kept on purpose, Q3).
- PuberApp.kt:81 shouldInitializeRuntime() == false leaves Koin unstarted while newImageLoader and Activities still call get<...>(); real only if such a process loads an image or starts an Activity.
- HttpStack.kt:34 with useDoh = false the DnsCache exists but nothing resolves through it; real only if something other than the guarded mutators reads or evicts it under a pin.
