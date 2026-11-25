CODEX: ran: review
CONFIRMED:
- [minor] MainScreenComponent.kt:325-329 - A corrective pick while HandingOff calls beginHandoff, which returns null unless Open (ModalNavigationDrawer.kt:136-139); the original request id stays pending. Scenario: pick History (slow), Center on Series while History loads; if History's focus retry times out and calls failHandoff with the old id, the rail reopens; Series composes with no pending request; the user presses Right once more. Bounded and recoverable; timing unverified.
- [minor] MainHostHarness.kt:402-406 - ProbeMainHostScreen hardcodes isSelected = true on Movies and never updates it; acceptsPickOf reads tab.isSelected, so the corrective-pick test always sees History as not selected; a loosened guard would still pass.
REJECTED:
- Note that the device run covers the base commit and not the changed D-pad behaviour - process remark, no scenario.
VERDICT: changes needed
