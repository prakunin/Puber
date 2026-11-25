LENS: state-path  ROUND: 2  BASE: f88f3079

### F1 [minor] For the 5 s before a handoff fails, focus sits on a collapsed rail item, and Center there switches tabs
- Where: ModalNavigationDrawer.kt (new Closed clear in DrawerSheet's LaunchedEffect(currentValue); onPreviewKeyEvent swallows only Right in HandingOff); MainScreenComponent.kt MainSideMenuItem.onClick.
- Problem: the clear runs only when the handoff fails and the value becomes Closed. During HandingOff (up to 5 s) the rail is drawn collapsed and focus is still on a rail item; redirectFocusToContent runs only when the rail group's hasFocus changes. Back and Right are swallowed; Up, Down, Center are not. Center calls ItemSelected → MainVM.onTabSelected → openTab; its own beginHandoff returns null; the new tab confirms the old pending handoff.
- Failure scenario: Show all in Error, nothing focusable. Left opens the rail, Back starts HandingOff. User presses Down then Center: the tab switches (History) while the rail looks collapsed; Show all and its stack are replaced. User expected the next Back to pop Show all.
- Fix: in HandingOff swallow every D-pad key and Center in the rail's onPreviewKeyEvent, or guard MainSideMenuItem.onClick with drawerState.currentValue == Open.
- Trace: Back → RailOpenBackHandler beginHandoff(false), HandingOff, no arm; retry loop, rail item stays focused, drawn collapsed; Down moves inside the rail group (no redirect); Center → disarm, onTabSelected → openTab(History), beginHandoff null; History root settles the pending id: Closed on History, Show all gone.

### F2 [nit] M11 step 5 expected result contradicts the code on a pushed screen
- Where: plan M11 row step 5.
- Fix: split: at a tab root the rail reopens after 5 s; over Show all it ends Closed with no rail item focused and the next Back pops.

Other paths behave as intended at 773b60f2: Back over a never-focusable pushed screen ends Closed, no arm, next Back pops and disarms; the deviation (first launch/dialog over a pushed screen) reads a stable canPop; the focus clear is only reached after reopen = false and a racing Left skips it; tab pick disarm runs before onTabSelected; arm only at a root. Not new: at a root whose content never takes focus Back loops (deferred to rail-handoff-nothing-focusable).
