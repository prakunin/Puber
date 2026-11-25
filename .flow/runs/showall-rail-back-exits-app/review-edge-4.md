LENS: edge  ROUND: 4  BASE: f88f3079

No findings.

Reviewed 3a536877..73371c73. acceptsPickOf handles: Open (pick as before); Back/Right/initial handoff (handoffExpectsNewContent false, Center refused, B3 stands); Closed after a failed handoff (refused); re-picking the selected tab during a tab-pick handoff (refused, no second ItemSelected/refresh); picking a different tab during a tab-pick handoff (pending request reused; PuberCurrentTab key(currentTab.key) composes the new tab fresh, remember(restartKey) records the pending id, its own ContentFocusHandoffEffect starts a new 5 s timeout, the old tab's effect is cancelled); picking back to the starting tab (composes fresh, settles; second catch-up limited by respectFloor = true); several corrections (last tab settles); arriving tab loads while focus is in the rail (settles, closes); process death during HandingOff (persistedValue → Closed, unchanged). Harness changes are test-only; one-Down pick of History still lands on History (Series is last).

### Unproven
- MainScreenComponent.kt:306 A second Center on the newly picked tab before the next frame marks it selected sends a duplicate ItemSelected; real only if a key repeat/double press arrives inside one frame on the box.
- ContentFocusHandoff.kt:140 The outgoing tab's 5 s timeout fires in the milliseconds between an accepted corrective Center and that tab leaving composition; real only with a device trace of a correction at ~5 s into a stuck tab pick.
