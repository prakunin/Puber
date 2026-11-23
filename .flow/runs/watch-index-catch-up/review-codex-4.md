CODEX: ran: review, adversarial
CONFIRMED:
- none
REJECTED:
- Standard and adversarial reviews (verdict "approve") reported no findings, including the flagged case (empty catch-up closing outside a walk leaves `catchUpEmissionPending` set; effect: the next emission is published at once, or the walk's end clears it; matches the documented trade-off).
- New window kinds: begin/end synchronized; cancellation closes windows through `finally`.
- Read-only; no tests run.
VERDICT: clean
