LENS: state-path  ROUND: 1  BASE: f88f3079

### F1 [minor] An exit armed at one tab root survives a switch to another tab root, so the next Back there closes the app
- Where: MainScreenComponent.kt:228-231 (ExitDisarmOnTabPush); the rail's tab pick at :302-307
- Problem: the KDoc says the exit is disarmed "so an arm made at the root does not let a later Back leave the app from a different place", but the effect runs only when tabStack.canPop changes; root-to-root tab switch keeps it false, and a tab pick does not touch ExitConfirmation. Base behaves the same, but the diff states the invariant and does not hold it here.
- Failure scenario: Movies root, rail closed. Back opens the rail; Back closes it, toast, arm. Within 3 s: Left opens the rail, Down, Center on Home; handoff settles, rail Closed on Home root. Back reaches RailClosedBackHandler, isArmed true, finish(). User expected the rail to open.
- Fix: exitConfirmation.disarm() when a rail item is picked (DrawerMainContent keyed on state.selectedTab, or a LaunchedEffect keyed on the selected tab next to ExitDisarmOnTabPush), or narrow the KDoc.
- Trace: RailClosed reveal → Open; RailOpen (canPop false) beginHandoff + arm(); settle Closed; Left → Open; item onClick ItemSelected + beginHandoff(true); Home composes, detach(A)/attach(B), canPop false→false (no restart); settle Closed; RailClosed isArmed → finish().

### F2 [nit] Spec and test design still describe the !view.hasFocus() guard the code no longer uses. Verdict on 1fd30c7a: keep it
- Where: ModalNavigationDrawer.kt:229, :333-341; stale text in the spec lines 97, 144, 210 and test-design.md:55.
- Why keep: the guards differ only when the Compose view holds view focus but no content node has Compose focus; then the old guard leaves Left dead until content loads, the new one opens the rail as AC-8/R11 ask. It does not widen the listener elsewhere (overlays inside the content Box keep contentHasFocus true; the update prompt puts the drawer in HandingOff; Main leaves composition on a root push). The wrapper only observes focus. Round 3's 5/5 green run is at this code.
- Fix: replace !view.hasFocus() in the spec and test design with "no Compose focus inside the drawer content (onFocusChanged on the content wrapper)". Keep the code.

Other paths trace as intended at c6b25f49: one enabled handler per snapshot; one-frame window on Main re-entry where Tab wins and a pushed screen still pops; tab switch dispose/attach order and owner check; AC-5/AC-6 hold; focus after each press passes the gate as intended.
