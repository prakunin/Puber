# Review triage - round 2

Run: seek-blocked-by-skip-prompt   Base: 6b087697   Reviewed head: 1d38134a (incremental from b6660c74)

Sources: review-edge-2.md, review-tests-2.md, review-codex-2.md (ran: review, adversarial; clean), review-ux-designer-2.md. qa round 2 in qa-report.md.

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | tests F1 | minor | `component/PlayerTimedActionOverlay.kt:379` | The X-unfocusable fix has no automated test | Read the androidTest class: no case presses Up under the controls; device M4b covers it | accept | Round 2, minor: device evidence M4b (`r2-m4-after-one-up-chip-focused-x-idle.png`) shows it; listed for the owner |
| 2 | tests F2 | minor | `red-run-b7854fbb.md` header, plan record | Red-run counts "11 tests, 6 fail" vs table 12 tests, 7 fail / 5 pass | Counted the table: 12 rows, 7 FAIL | accept | Record-only inaccuracy; the per-test table is correct; listed for the owner |
| 3 | tests F3 | nit | plan, Fix round 1 | Mutation-check lines cite test headers, not assertions | Reviewer gives the assertion lines | accept | Record-only |
| 4 | edge F1 | nit | `component/PlayerTimedActionOverlay.kt:328-330,407-414` | Stale comment ("either of them can hold the focus") and an unreachable focused tint for the X | Read the code: `iconOnly` buttons are `canFocus = false` | accept | Comment-only; listed for the owner |
| 5 | ux F1 | nit | evidence device.md | Contact-sheet frames are 640 px wide, not on the 1280 scale | ux measured | accept | Evidence text only |

## Rejected codex claims

- none (codex reported no finding).

## Next

- No blockers or majors; go to judge. Unproven lines: 1 (tests).
