# Review triage - round 1

Run: itest-incomplete-run-green   Base: 63771351   Reviewed head: 54a748ac

Sources: review-blind-1.md (F1, F2 minor, F3 nit), review-tests-1.md (F1 minor), review-codex-1.md (ran: review, adversarial; clean). Antigravity: skipped, quota exhausted (owner: do not wait).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | blind F1, tests F1 | minor | `.flow/config.yaml` check.full | the new tooling test is not wired into any check | grep: check.full has no itest-tooling entry; plan task 4 assigns it to the conductor | fix | conductor appends ` && bash tools/tests/test-itest-tooling.sh` to check.full at ship, when the script reaches master (before that, check.full of other worktrees would fail on a missing file) |
| 2 | blind F2 | minor | `Makefile` itest recipe | `testRunFailed` match depends on attribute order (`<testcase name="testRunFailed"`) | read the recipe; real AGP XML writes name first (codex-1), but the order-independent form costs nothing | fix | match `<testcase[^>]*name="testRunFailed"`; add a tooling case with the attribute order swapped |
| 3 | blind F3 | nit | `tools/tests/test-itest-tooling.sh` test_stale_xml_not_counted | the test does not assert the stale XML was removed | read the test | fix | assert the stale XML file no longer exists |

## Rejected codex claims

- Clearing the whole `connected` results dir deletes other variants' reports - regenerated build outputs, brief fixes the scope.
- Failed `rm -rf` leaves the private adb server running - unproven, needs an undeletable build file.
- Truncated report after the first `<testcase` passes - AGP writes the file whole; no case shown.
- Concurrent itest runs in one checkout - forbidden by AGENTS.md lease rules.

## Next

- fix round 1: #2, #3 by the implementer (Makefile + test), then the tooling test; #1 at ship by the conductor; size S, no blocker or major, no second review round; unproven lines: 2 (tests 2)
