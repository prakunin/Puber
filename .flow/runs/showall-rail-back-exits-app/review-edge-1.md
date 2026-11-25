LENS: edge  ROUND: 1  BASE: f88f3079

### F1 [minor] Back over a pushed screen that can never take focus keeps the rail open with no way back out by Back
- Where: MainScreenComponent.kt:208-219, with ContentFocusHandoff.kt:24,137 (the 5 s timeout, then fail).
- Problem: with the rail open over a pushed screen, railBackOwner gives Back to RailOpen, which starts a handoff that ends only when the content takes focus. A pushed screen with nothing focusable never does: HandingOff swallows Back for 5 s, then failHandoff sets Open again. TabBackHandler stays disabled, so Back never pops the screen. M11 ("Back collapses it once content can") assumes the content eventually can.
- Failure scenario: open Show all from a shelf (filter Unavailable, ShowAllVM.kt:63) while the network fails. ShowAllViewState.Error shows only Text(state.message) (ShowAllScreenContent.kt:155-157); «Фильтры» is not focusable. Left opens the rail via the unhandled-Left listener. Each Back gives ~5 s of swallowed presses, then the rail reopens. Only picking a tab gets out. On base the cold-start order let that Back reach the exit arm or the pop.
- Fix: in RailOpenBackHandler, when the tab can pop, close the rail without waiting for the content to land (e.g. let ContentFocusHandoff settle to Closed instead of failing back to Open when tabStack.canPop, so the next Back goes to Tab and pops). Or add an instrumented case (child held in Error) and record the trap as accepted in Risk 1.

### F2 [nit] Commit 1fd30c7a changed the unhandled-Left guard to something the spec does not describe, and was not needed for the defect it targeted
- Where: ModalNavigationDrawer.kt:204,229-231,331-348; spec Decisions row "Left with nothing focused" names !view.hasFocus().
- Problem: if the Compose view has Android focus but no Compose node is focused, Compose's own Left search enters the rail anyway (gate allows Left); if the view has no Android focus, both guards agree. Round 1 passed line 90 with !v.hasFocus(). The new guard is a harmless superset (wrapper Box changes no layout; contentHasFocus read only inside the listener), but the code no longer matches the spec.
- Failure scenario: none at runtime; spec and code disagree.
- Fix: keep the code and update the spec's Decisions row; a revert is also correct but buys nothing.

### Unproven
- TabComponent.kt:65-82 Two TabFlowNavigators attaching out of order during a tab swap (outgoing re-attaching after the incoming); real only if a key of that DisposableEffect changes on the outgoing tab during the swap window.
- MainScreenComponent.kt:185,208 and FlowComponent.kt:760 A Back between reveal() and the recomposition that flips the enabled flags still goes to the previous owner; real only with evidence of Back within one frame of Left.
