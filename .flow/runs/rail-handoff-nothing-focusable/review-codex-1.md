CODEX: ran: review, adversarial
CONFIRMED:
- [minor] app/src/androidTest/kotlin/com/kino/puber/ui/feature/main/component/MainRailHandoffKindTest.kt:112-115 - The outgoing-tab regression test swaps the tab in the same action that makes the outgoing root focusable. If the arriving tab renders first, the outgoing root never takes focus, so the test passes without exercising the guard. Evidence: the same-ID mutation survived all three instrumented runs; only unit tests cover that guard (the known M9-same-id gap). Fix: hold the swap in the harness until the outgoing root has rendered its focusable cards, then release it.
REJECTED:
- [high] An intermediate tab can settle the latest pick (ContentFocusHandoff.kt:105-122) - unproven: needs two different picks before the first destination mounts and that tab taking focus before the final one mounts; D-pad clicks are well over a frame apart; same single-id structure as before; the branch narrows the old failure.
- Codex review: "no separate production defect confirmed" - no action.
- Known untested R8 - not raised by Codex; not adjudicated here.
VERDICT: changes needed (one minor test-hardening item; no production defects)
