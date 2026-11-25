CODEX: ran: review, adversarial
CONFIRMED:
- [major] ShowAllRailCloseFocusTest.kt:238-246 - `tabPickHandoff_arrivingEmptyResetTakesFocus` does not exercise the handoff: `setUpScreen()` starts Closed and `beginHandoff(TabPick)` returns null from Closed (ModalNavigationDrawer.kt:164); `expectsNewContent` stays false; passes with the old guard. Fix: open the rail first (`initial = Open` or D-pad Left), assert `beginHandoff` non-null, then swap to Empty.
- [minor] ShowAllScreenContent.kt:191-200 - `bornDuringRequest` is recorded when `ShowAllEmpty` enters composition. If the user applies a filter, quickly opens the rail and picks a tab, and the outgoing grid's filtered page resolves to Empty while the request is pending, the new `ShowAllEmpty` records the pending id and is treated as the arrival; it can request focus on the outgoing Reset before Voyager replaces the screen. Narrow timing window; `ContentFocusHandoffEffect` uses the same "born during request" rule, so this is a known limit of the pattern; the effect is a brief focus grab on content about to be replaced.
REJECTED:
- Codex rated the late-Empty finding "medium" - re-graded to minor (narrow window, transient result).
- Not verified on a device - process remark.
VERDICT: changes needed
