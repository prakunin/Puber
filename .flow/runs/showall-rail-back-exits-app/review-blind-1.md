LENS: blind  ROUND: 1  BASE: f88f3079

No findings.

### Unproven
- MainScreenComponent.kt:208 Back with the rail open over a pushed screen that has nothing focusable: RailOpenBackHandler starts a handoff, TabBackHandler is disabled while HandingOff, failHandoff reopens the rail after 5 s; Back cycles Open/HandingOff/Open and never pops. The rail gets there through the new unhandled-Left path. Real only if a ShowAll Error or unfiltered Empty state can show with filter.available == false (ShowAllScreenContent.kt:158 renders only Text; the «Фильтры» header not focusable either).
- ModalNavigationDrawer.kt:301 While Closed, the onEnter gate refuses every entry except Left, including requestFocus() (Enter). A bug only if code requests focus on a rail item while Closed; none found.
- TabComponent.kt:65 TabStackState follows only the last navigator that attached; wrong canPop only if two pushing tab navigators re-attach while composed at once; only case seen is a tab swap, where the outgoing detach correctly does nothing.
