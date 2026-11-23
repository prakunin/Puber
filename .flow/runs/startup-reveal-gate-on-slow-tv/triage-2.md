# Review triage - round 2

Run: startup-reveal-gate-on-slow-tv   Base: 9158cf82   Reviewed: 854a4981..0800302d (KDoc, evidence verdicts, spec AC-7 and test design)

Sources: review-edge-2.md. Codex: skipped, usage limit (owner: do not wait). Lens tests not run (no test change).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | edge F1 | major | evidence :130, :111 | AC-7 PASS claims the focus clause for every start on one screenshot | Read the verdict row and the AC-8 section | fix: split the AC-7 verdict (latency PASS 0/25; focus checked on one named start, other 24 NOT CHECKED); carried to the owner at gate 2 | open |
| 2 | edge F2 | minor | evidence :129 | AC-6 limit mixes clocks; about 2330 ms on the line's clock, set 1 rows 1/5/8 vs 6 | Read RevealGate.kt:105, StartupReveal.kt:69 and the set-1 table | fix in the same edit (factual error in evidence the judge reads) | open |
| 3 | edge F3 | minor | evidence :129 | wrong ready range for set 2 and control | Read the tables | fix in the same edit | open |
| 4 | edge F4 | minor | RevealObserver.kt:29, StartupMarks.kt:29 | KDoc states a guarantee the gate does not give | Read RevealGate.kt:123 | fix (comment only, cheap; it corrects text this round added) | open |
| 5 | edge F5 | nit | test design Manual step 4 | K2 attached to the control starts | Read | fix (one sentence, test-designer) | open |

Round 2 normally fixes blockers and majors only; items 2-5 correct factual errors in text this fix round itself added, so they are fixed in the same edits rather than accepted.

## Next

- qa-device #1-#3 (evidence only, no device), implementer #4, test-designer #5; then lens edge on the incremental diff.
