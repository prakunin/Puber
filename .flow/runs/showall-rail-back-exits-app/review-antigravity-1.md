ANTIGRAVITY: ran
CONFIRMED:
- none
REJECTED:
- agy reported no concrete defects. It cleared Back ownership (RailBackOwner, TabStackState, ExitDisarmOnTabPush, RailOpenBackHandler), the rail entry gate and RevealOnUnhandledLeft in ModalNavigationDrawer.kt, and the four new tests. It gave no scenarios, so nothing was adjudicated.
- Commit 1fd30c7a (judged by the reviewer from its diff only): View.hasFocus() on the Compose host view can stay true after Compose focus has left the content, so the old guard !v.hasFocus() could block the unhandled-Left reveal. Reading Compose focus is the more reliable check; lean towards keeping it. Revert only if a device run shows the reveal still works with the view-focus guard. Side effect to confirm in QA: the extra Box(fillMaxSize) wrapper should not change layout.
VERDICT: clean
