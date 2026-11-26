# Triage round 1: baseline-generation-hardening

Reviewed head: 6063ddec (base b27d8e40). Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md, review-codex-1.md (ran, review + adversarial), review-antigravity-1.md (clean). qa round 1: qa-report.md (all PASS, M4 on the box PASS).
Checked against the code by the conductor.

| # | Finding | Sources | Severity | Verdict | Reason / action |
|---|---------|---------|----------|---------|-----------------|
| 1 | Rail check (D8) passes whenever the rail is open; does not check the opened/selected entry; commit and comment overclaim | blind F1, edge F2, tests F2 | minor | fix | Try By.text(title).selected(true) / a selected or focused ancestor within main_rail; prove with the M2 scratch run on the emulator (must fail when LEFT is skipped after Series and when an entry is repeated); if no selector works, reword comment to "the rail reopened" and record AC-7 base half as unproved. Regenerate and recommit profiles only if the content test passes and the journal is clean |
| 2 | Script sets generation_started before make's port guard, so a refused port's server is killed | codex, edge F1 | minor | fix | Same guard in the script before the flag (exit 2) + script-level test |
| 3 | Recipe has no checked cp/sed; partial .bak can be restored over the profile | codex, edge unproven | minor | fix | cp to .bak.tmp && mv || exit 1 before the trap; abort on failed final sed |
| 4 | Stale leftover .bak overwrites a newer HEAD profile | edge F3 | minor | fix | Restore only when the profile is the stripped copy; otherwise delete with an "out of date" notice; test |
| 5 | Reconnect test passes without the preflight reconnect | tests F1 | minor | fix | Count only preflight connects (port= ) or gate the fake on a connect |
| 6 | Two make refusal paths not in the port-only test | tests F4 | minor | fix | Add the 60 s timeout and extra-serial refusals |
| 7 | Tooling test not executable and run by no gate | blind F2, tests F3 | minor | fix | chmod +x; the conductor adds it to check.full at ship (as D18 did) |
| 8 | Concurrent runs, ddmlib port reuse, unauthorized advice, adb devices failure message | codex adversarial, edge/blind unproven | - | reject | Unproven; single leased emulator, no such caller |

Deferred: none (rail-left-focus-nearest-entry already created for the pre-existing focus behaviour). Accepted: none.
