# Review triage - round 4

Run: watch-index-catch-up   Base: e4c3b5aa   Reviewed head: 9910c26e (incremental c55c5d3c..9910c26e)

Sources: review-edge-4.md, review-tests-4.md, review-codex-4.md (ran: review, adversarial; clean), review-state-path-4.md (extra).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | edge F1, state-path F1, tests F1 | minor | `WatchStateRepository.kt:243` | `catchUpEmissionPending` is a plain boolean armed on the wrong condition: armed after a no-change catch-up it leaks into a later walk's first chunk (edge, state-path); not armed when the catch-up already held a change, so its last in-flight emission is held by a walk that opens right after (tests) | Read the line and `onIndexChanged`; the three scenarios follow from the condition and from StateFlow dropping equal snapshots | fix (one change for all three): arm on every catch-up close, but as a short deadline (well under the time a walk needs for its watching lists and first chunk) instead of a boolean; clear it when consumed, in the mark branch and at walk end; tests for each of the three orders | fixed (c4519fa4) |

## Rejected codex claims

- none (codex clean)

## Next

- Fix round 4: item 1; then check.fast, lens edge + state-path + tests + codex on the incremental diff. Unproven lines: 2.
