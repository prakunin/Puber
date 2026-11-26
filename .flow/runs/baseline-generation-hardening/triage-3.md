# Triage round 3: baseline-generation-hardening

Reviewed: 4071d839..91a9b7ba. Sources: review-edge-3.md, review-tests-3.md, review-codex-3.md (ran), review-antigravity-3.md (ran).
Conductor check of codex's claim: app/build/intermediates/baselineprofiles/main/merged/baseline-prof.txt has 1183 H-marked and 29970 non-H method rules, so a fresh merge result DOES carry H rules; the "has an H rule => stale" discriminator misclassifies a SIGKILL between merge and re-mark (the case triage-2 #2 meant to fix).

| # | Finding | Sources | Severity | Verdict | Reason / action |
|---|---------|---------|----------|---------|-----------------|
| 1 | Stale discriminator wrong: merged output has H rules, so a SIGKILL after merge before re-mark drops the originals | codex (confirmed by conductor), edge unproven | minor | fix | Treat the backups as stale only when baseline-prof.txt has NO non-H method rule (a completed run re-marks every rule); otherwise restore. Fake merge in the harness emits mixed H and non-H rules |
| 2 | Signal between the success-path rm calls leaves a mixed pair | edge F1 | minor | fix | Re-arm the EXIT trap without restore before deleting the backups |
| 3 | Leftover restore ignores a failed mv and the next line overwrites the original backup | edge F2 | minor | fix | restore returns non-zero on failure; restore || exit 1 in the leftover branch |
| 4 | Spec AC-1/D10 say per-file; recipe decides once from baseline-prof.txt (correct); temp name .new.bak | tests F1, codex | minor | fix | Analyst/architect amend AC-1 and D10 |
| 5 | Stale test does not seed startup backups; absent-startup test lacks exit/notice checks | tests F2, F3, edge F3 | minor | fix | Cheap test additions |
| 6 | SIGKILL inside restore(); daemon window; two adjacent rm | antigravity, codex rejected, unproven | - | reject | Double fault or accepted by D12 |

Deferred: none. Accepted: none.
