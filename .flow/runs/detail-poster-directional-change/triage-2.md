# Review triage - round 2

Run: detail-poster-directional-change   Base: 01d1d305   Reviewed head: 4d9725b9 (incremental from 6eb4d15c)

Sources: review-edge-2.md, review-tests-2.md, review-codex-2.md (ran: review, adversarial), qa-report.md (round 2).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | edge F1, codex | major | `VideoItemGridDetails.kt:887, :1001-1006, :1018-1030` | A change that interrupts a running motion seeds its outgoing layer from the predecessor's partial still alpha/offset, fixed for its life; once the predecessor's own layer ends, a see-through, shifted layer is left alone until the new poster loads (up to 3 s) | read handover: `outgoingAlpha` constant, predecessor clears on its own clock; regression from fix round 1 | fix | round 3 (cap reached; one more round chosen because it is a regression from the last fix round, owner standing instruction: recommended) |
| 2 | tests F1 | minor | `VideoItemGridDetails.kt:653-661` | The failed-last-URL branch in onError has no test that fails if it is removed | read: the branch lives in the composable lambda | accept | round 2 minor; the rule is two lines and was checked by edge round 2; listed for the owner |
| 3 | tests F2 | minor | `VideoItemGridDetails.kt:964-968, :1014-1032` | Interruption filter and draw order untested | read | accept | the round 3 fix for #1 adds a handover test over the same records; remainder listed for the owner |
| 4 | tests F3 | nit | `PosterTransitionTest.kt:56` | An assertion compares a constructor value with itself | read | accept | harmless; listed for the owner |

QA round 2: all checkable ACs PASS on catalogue and the real Favourites side-by-side screen; AC-10 NOT CHECKED (owner on the TV, incl. the remaining opaque seam on Left presses, option c if unacceptable); AC-5 refresh path NOT CHECKED. Round 1 rail note: not a defect (rail switches on Enter).

## Rejected codex claims

- none

## Next

- Round 3: implementer fixes #1 with a unit test of the handover from a ready mid-motion predecessor; then check.fast, lens edge + codex on the incremental diff, lens tests, qa on AC-1/AC-2/AC-3 retarget. Unproven lines: 2.
