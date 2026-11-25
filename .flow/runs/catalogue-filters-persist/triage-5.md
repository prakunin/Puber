# Review triage - round 5

Run: catalogue-filters-persist   Base: ef5987ec   Reviewed head: 3e5248d0 (incremental d6ec6134..3e5248d0, test-only)

Sources: review-tests-5.md, review-edge-5.md, review-codex-5.md (ran: review, adversarial), review-antigravity-5.md (ran, clean). state-path not run: no production change in this diff.

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| - | all lenses | - | - | No findings; both new tests fail under their mutations and pass on the fix by static trace | four independent traces agree | - | qa-device to record the red/green runs on the box (triage-4 item 3) |

## Rejected codex claims

- Host `restartKey = Unit` does not exercise production recovery - test fidelity; the unit under test is ShowAllEmpty's own check.
- HandingOff read races with settle - nothing can settle that request in the host.

## Next

- No fixes. qa-device round 3: check.full; red runs under each mutation for ShowAllFilterFocusTest (cold-open Reset gate, empty-state Reset request, header-held guard) and ShowAllRailCloseFocusTest (outgoing, arriving); green classes; re-run M1-M4 on the final APK. Then judge. Unproven lines: 2
