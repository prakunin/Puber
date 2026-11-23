# Review triage - round 2

Run: release-startup-compilation   Base: e4c3b5aa   Reviewed head: 2d1c7395 (incremental from a4c19ba4)

Sources: review-edge-2.md, review-tests-2.md, review-codex-2.md (skipped: Codex usage limit until 2026-11-25 07:38 on both models; the owner chose to skip Codex for this round). Lens blind not re-run (incremental round).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | edge F1 | minor | `AuthEventLog.kt:28-31` `hasValue` | Fix #6 of round 1 does not hold: a cleared token is a sealed empty string (IV + tag), still non-empty, so it logs present=true | Read `hasValue` call sites and the round-1 clear path (`saveString(name, null)` -> encrypt("")) | defer | new run baseline-generation-hardening (generation-only diagnostics; the same finding returned after its fix, flagged to the owner) |
| 2 | edge F2 | minor | `BaselineProfileGenerator.kt:116-124` | Rail check can pass falsely after Series because the Series screen shows the 4K section title | Read the check against `TabTypeConfig.standardSections`; the regenerated profile has all rail-tab rules, so this profile is not affected | defer | new run baseline-generation-hardening |
| 3 | edge F3 | minor | `Makefile:166-168` | Backup unsafe after an aborted run; HUP not trapped; .bak not ignored | Read the recipe | defer | new run baseline-generation-hardening; `BaselineProfileContentTest.baselineProfileMethodRulesAreHot` still catches a committed S/P-only profile in check.full |
| 4 | edge F4 | minor | `tools/generate-baseline-profile.sh:27-35` | Preflight refuses a just-connected emulator still offline | Read the preflight; a re-run works | defer | new run baseline-generation-hardening |
| 5 | edge F5 | minor | `tools/generate-baseline-profile.sh:94-99,125` | Cleanup kills the 5038 private adb server even when generation never started | Read cleanup and trap | defer | new run baseline-generation-hardening |
| 6 | tests F1 | minor | `test-design.md` AC-5 P2 row | Row describes the old procedure of the executor test | Read the row and the test | accept | bookkeeping in the run folder; listed at handover |
| 7 | tests F2 | minor | `test-design.md` AC-3 P0 row | Row names the old test name and omits the rail tabs | Read the row and the renamed test | accept | bookkeeping; listed at handover |
| 8 | tests F3 | minor | `TelemetryRuntimeTest.kt:342` | `assertTrue(x != ...)` fails without a message | Read the test | accept | readability only; listed at handover |

## Rejected codex claims

- none (Codex did not run this round)

## Next

- No blockers or majors: no fix round; go to the judge after the owner-approved AC-4 extra sample (30 more S1 starts, p95 over 45). Unproven lines: 5 (edge 2, tests 3).
