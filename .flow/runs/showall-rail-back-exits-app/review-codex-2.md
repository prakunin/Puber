CODEX: ran: review, adversarial
CONFIRMED:
- [minor] app/src/main/java/com/kino/puber/ui/feature/main/component/MainScreenComponent.kt:309-317 - While a handoff is HandingOff, the rail is drawn collapsed but still holds item focus, and its click handler calls `onAction(ItemSelected)` before `beginHandoff`. `beginHandoff` is refused during HandingOff. Scenario: on a pushed screen with nothing focusable (Show all in Error), the user presses Back and then Center within the up-to-5s handoff timeout (`ContentFocusHandoff.kt:24`). The tab switches unseen, and the old handoff stays pending until it fails. The branch widens this window only slightly; the same sequence already existed for Right followed by Center on a failing tab. Fix by guarding the click on `drawerState.currentValue == Open`, or accept it as pre-existing. The spec ACs do not require it.
REJECTED:
- Codex review [P2]: device evidence covers only 1ed3df50; the two new regression tests have not run on a device - process/evidence gap, not a code defect; run MainRailBackTest at HEAD before gate 2.
- Adversarial "hold shipment" verdict: A5 and A6 device tests unverified - repeats the above.
- Checked, not a finding: closeOnFail = { tabStack.canPop } is read at fail time from Compose state, so the decision is current; first-launch/dialog handoffs over a pushed screen also end Closed; rail focus cleared by clearFocus(force); the closed-rail onEnter gate refuses refocus except from Left. No defect.
VERDICT: changes needed (one minor finding; optional fix)
