# Review triage - round 7

Run: watch-index-catch-up   Base: e4c3b5aa   Reviewed head: 210f055e (incremental aa19fb51..210f055e)

Sources: review-edge-7.md, review-tests-7.md (check.full green at 210f055e: 2442 tests, 0 failed; detektAll and lintDevDebug pass), review-state-path-7.md (extra). Codex: skipped, usage limit until 2026-11-25 07:38 (owner, 2026-11-23: do not wait for Codex).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|

No findings. Triage-6 item 1 holds (edge, state-path and tests agree).

## Unproven, not acted on

- edge: `WatchStateRepository.kt:253` still reads the clock when a catch-up window closes; only a JVM test that closes a catch-up window on a DI-built repository would hit the stub. No such test or caller exists.

## Rejected codex claims

- none (codex not run)

## Next

- qa-device verify round 2 on the runtime ACs after the fix rounds (AC-2, AC-4, AC-7, AC-9) on the box, then the final judge.
