# Review triage - round 2

Run: catalogue-filters-country-year-status   Base: 90e69aff   Reviewed head: e286d189 (incremental 1f6b03b0..e286d189)

Sources: review-edge-2.md (no findings), review-tests-2.md, review-codex-2.md (ran: review, adversarial; clean), extras: review-ux-2.md, review-antigravity-2.md (clean), review-state-path-2.md (no findings).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | tests F1, edge note, codex note | minor | `ShowAllFilterFocusTest.kt:560` | the new off-list year focus test was only compiled | plan Fix round 1 row 3 says compile-only; it needs no KinoPub session | fix | qa-device round 2 runs ShowAllFilterFocusTest on the box (verify step, not code) |
| 2 | tests F2 | nit | `ShowAllFilterFocusTest.kt:560-571` | off-list year test does not assert selected state or click | read: only a focus wait | accept | round 2 nit; the row is `selected = true` unconditionally and the VM path is unit-tested |
| 3 | ux F1 | nit | `docs/design/showall/README.md:201` | stand README does not describe the off-list year row (466 dp) | read | accept | round 2 nit; listed for the owner's AC-11 look |
| 4 | round-1 row 1 (tests F1) | major | qa-report | M1-M6 and AC-11 not run: box account 403 user_banned | carried | fix | waits for the owner to restore the box session; NOT CHECKED until then |

## Rejected codex claims

- [P3] missing assertIsSelected on the off-list row - test-strength wish, merged with row 2.
- [medium, adversarial] focus loss when the off-list row is removed - the `if` block is its own Compose group in a plain Column, the preset loop keeps its slots; no mechanism given. The device run (row 1) covers the transition.

## Next

- qa-device round 2: check.full and ShowAllFilterFocusTest on the box (affected AC-4, AC-10). Row 4 stays blocked on the box session. Unproven lines: 1.
