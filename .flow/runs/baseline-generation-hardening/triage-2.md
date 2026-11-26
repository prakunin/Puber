# Triage round 2: baseline-generation-hardening

Reviewed: 6063ddec..4071d839. Sources: review-edge-2.md, review-tests-2.md, review-codex-2.md (ran), review-antigravity-2.md (agy unavailable; reviewer's own read). Plugin behaviour confirmed by edge from bytecode: MergeBaselineProfileTask deletes the output then writes only non-empty rules; a failing journey stops before the merge.

| # | Finding | Sources | Severity | Verdict | Reason / action |
|---|---------|---------|----------|---------|-----------------|
| 1 | startup-prof.txt is neither backed up nor restored; a post-merge failure or signal leaves an old baseline next to a new/missing startup profile | codex (major), antigravity (major), edge F1, tests F1 | major | fix | Back up and restore startup-prof.txt like baseline-prof.txt (absence is a valid state); fake Gradle in the harness rewrites/deletes it; assert both files in failure and signal tests; fix the "restoring the original" wording |
| 2 | Fix-round "out of date" rule deletes the original after a SIGKILL between merge and re-mark | edge F2, codex, tests F2 | minor | fix | Regression from fix round 1 in the same code: treat .bak as current when the profile has no H-marked rule; correct the restore notice; add the test |
| 3 | Daemon merge can write after the trap restored | edge F3 | minor | fix | Cheap: name the window in the recipe comment and docs/release.md (or --no-daemon for that call if trivial) |
| 4 | Exits before the trap leave the 5039 server up on a direct make run | codex, edge unproven | minor | fix | Cheap: install the server-kill trap before the backup steps |
| 5 | Spec AC-1 and D4 still describe an unconditional restore | tests F3 | minor | fix | Analyst amends AC-1, architect amends D4; spec re-copied |
| 6 | Failed-copy test does not assert the temp file is removed | tests F4 | nit | fix | Cheap |

Deferred: none. Accepted: none.
