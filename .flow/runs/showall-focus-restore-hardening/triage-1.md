# Review triage - round 1

Run: showall-focus-restore-hardening   Base: 35bfbbec   Reviewed head: d6b51a2e

Sources: review-blind-1.md (no findings), review-edge-1.md, review-tests-1.md, review-codex-1.md (ran: review, adversarial; clean). qa round 1: all ACs PASS (AC-1 JVM only; AC-9 opener-removed exact neighbour partial on device).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | edge F1 | minor | `ShowAllScreenContent.kt:166` | A pending entry restore is not retried when content focus turns active (rail closes), so the handoff lands on the first card | Read: `LaunchedEffect(items)` is the only trigger; `contentFocusActive` is not a key; holds | fix | Key the effect on `contentFocusActive` too; add a JVM case (inactive at entry, then active -> restore runs) |
| 2 | tests F1 | minor | `ShowAllScreenContent.kt:213`, `GridFocusMemoryTest` | No test pins the `pendingEntry` gate on fallback C for the live path | Read the tests; holds | fix | Add the JVM case from the review (live path, all attempts fail, no `grid` event) |
| 3 | tests F2 | minor | `ShowAllScreenContent.kt:163` | Compose wiring of `requestGrid` to the grid requester is untested | holds; forcing C deterministically on device is not practical | fix (docs) | Record in the plan's Implementation record that the C wiring is checked by review only |
| 4 | edge F2 | minor | `ShowAllScreenContent.kt:166,198-200,209` | A foreign focus landing plus a list change inside the restore window clears `pendingEntry` and drops the opener restore | Read `onGridFocusChanged` (any landing clears pendingEntry) and the cancel-on-items behaviour; holds; narrow window | defer | New run showall-entry-restore-foreign-landing |

## Next

- Implementer fix mode #1-#3, check.fast, lens edge + tests + codex on the increment, qa on AC-6/D-M2 (rail) since runtime code changed.
