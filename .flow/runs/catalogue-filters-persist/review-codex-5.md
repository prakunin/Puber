CODEX: ran: review, adversarial

Mutations not run (instrumented, no device, read-only); answers from tracing ShowAllScreenContent.kt, ContentFocusHandoff.kt and ModalNavigationDrawer.kt.

CONFIRMED:
- None. Both new tests fail under their mutation and pass on the fix.
  - `tabPickHandoff_outgoingEmptyResetDoesNotTakeFocus`: empty-state «Сброс» is [1] (two nodes asserted); `reveal()` opens the rail and ModalNavigationDrawer.kt:291 moves focus into it; `beginHandoff(TabPick)` accepted from Open, HandingOff flips `contentFocusActive`; without `!outgoingDuringTabPick` the effect focuses «Сброс» and `assertIsNotFocused` fails; with it the effect skips; HandingOff holds because the outer effect is outgoing.
  - `tabPickHandoff_arrivingEmptyResetTakesFocus`: rail opened before the pick, so the call is accepted; `beginHandoff` and `host.state = Empty` in one `runOnIdle`; `bornDuringRequest == pendingRequestId`, the request runs, «Сброс» [1] focused; `redirectFocusToContent` fires only when the rail gains focus; under the round-2 mutation both effects skip and `waitUntil` times out.

REJECTED:
- Host `restartKey = Unit` keeps the outer effect outgoing, so production recovery is not exercised (adversarial, medium) - test fidelity, not a defect; the behaviour under test is `ShowAllEmpty`'s own check.
- `HandingOff` read after `runOnIdle` races with settling (adversarial, medium) - unproven; nothing can settle that request.
- Plain review: no findings.

VERDICT: clean
