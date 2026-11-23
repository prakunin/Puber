# Review triage - round 6

Run: watch-index-catch-up   Base: e4c3b5aa   Reviewed head: aa19fb51 (incremental c4519fa4..aa19fb51)

Sources: review-edge-6.md, review-codex-6.md (ran: review, adversarial), review-state-path-6.md (extra). Lens tests not run this round (tests unchanged in meaning; the finding is about a test).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | codex (major), edge F1, state-path F1 | major (codex) / minor | `WatchStateRepository.kt:50, 191`; `WatchStateRepositoryModuleTest.kt:49` | `onIndexChanged` reads `windowClock()` on every emission, so the DI-built repository in a JVM test hits the `SystemClock` stub and its index collector dies silently | Read: default `SystemClock::elapsedRealtime`, no `isReturnDefaultValues`, module test uses `repositoryModule`; the comparison runs before the deadline is checked | fix: read the clock only when a deadline is armed (`catchUpEmissionDeadline != NO_DEADLINE && windowClock() <= ...`); have the module test assert that a snapshot change advances `version`; correct the plan's note | fixed (210f055e; also detekt ReturnCount/MaxLineLength cleanups) |

## Rejected codex claims

- none

## Next

- Fix round 6: item 1; then check.fast, check.full (end of review), lens edge + codex + tests on the incremental diff, then qa for the runtime ACs on the box when it is paired again. Unproven lines: 0.
