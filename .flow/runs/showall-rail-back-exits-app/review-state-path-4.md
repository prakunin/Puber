LENS: state-path  ROUND: 4  BASE: f88f3079

Traces clean for: pick while Open; tab pick vs Back/Right handoff; Closed; corrective pick (onTabSelected switches and opens the tab; beginHandoff null outside Open, pending request reused; key(currentTab.key) disposes History and cancels its loop; Series composed during the request settles it; picking back to the original tab behaves the same); duplicate pick of the selected tab refused by !tab.isSelected; Back/Right handoffs still refuse picks (expectsNewContent false; B3 holds).

### F1 [minor] The cold-start handoff still refuses a rail pick while the first tab loads
- Where: MainScreenComponent.kt:304-308 (acceptsPickOf); ModalNavigationDrawer.kt:142-145 (beginInitialHandoff)
- Problem: beginInitialHandoff also starts with expectsNewContent = false, so it is treated like Back/Right although it is the C3 situation (tab loading, rail item focused). Before round 3 a pick here worked; dropped since the B3 guard; C3 leaves it dropped.
- Failure scenario: cold start, first tab's data slow, rail HandingOff, content has nothing focusable; first D-pad press puts focus on a rail item; Down, Center on another tab: refused; after 5 s failHandoff reopens the rail. User recovers; the pick was silently ignored.
- Fix: store a handoff kind (Initial / TabPick / Exit) and accept HandingOff -> kind != Exit && !tab.isSelected, or document the refusal in the KDoc and the spec; test with startMain(loadingRoot = "first") and Center on History before the first tab settles.
- Trace: LaunchedEffect -> beginInitialHandoff() -> startHandoff(expectsNewContent=false) {HandingOff, pendingId=1}; Down (rail item, redirect fails); Center -> acceptsPickOf false -> no ItemSelected; timeout -> fail(1) -> failHandoff(reopen = true) {Open}.
