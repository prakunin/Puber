# Review triage - round 2

Run: tab-stack-lost-on-root-push   Base: f14dcc84   Reviewed head: b5919b85 (increment 59cdb9c3..b5919b85)

Sources: review-edge-2.md, review-tests-2.md, review-codex-2.md (ran: review, adversarial). qa on affected ACs (qa-report.md "Review round 1 fixes", evidence 62825360): ShowAllScreenContentFocusTest 5/5, TabHostTelemetryWiringTest 10/10, TabFlowLifecycleTest 1/1; AC-1, AC-2, M2 PASS on device; opener-removed fallback NOT CHECKED on device (needs playing to the end), itest green. Base comparison (qa-report.md): Settings sub-selection resets on base too, so triage-1 #4 is pre-existing.

Triage-1 rows #1-#3 confirmed fixed by edge, tests, codex and qa (b5919b85).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | edge F1, codex | minor | `ShowAllScreenContent.kt:194-204` | A reload without the opener that lands within the ~3-frame entry restore window leaves no card focused (`restoreId` fixed, `inGrid` false) | Read `GridFocusMemory`: `restoreId` resolved once, live fallback gated on `inGrid`; holds; needs a reload in flight across the root push that publishes within ~50 ms of re-entry | defer | Review cap reached (2 rounds); new run showall-focus-restore-hardening |
| 2 | tests F1 | minor | `ShowAllScreenContentFocusTest.kt:87-110` | The "removed before re-entry" case runs the live-grid path; the entry fallback has no failing test | Read the test: `shown` is snapshot state changed while composed; holds | defer | showall-focus-restore-hardening |
| 3 | tests F2 | minor | `ShowAllScreenContent.kt` `requestRestoreFocus`; test :137-161 | No test fails if the per-attempt `contentFocusActive` re-check is reverted | holds; device M2 covers the behaviour (PASS) | defer | showall-focus-restore-hardening |
| 4 | tests F3 | nit | `ShowAllScreenContentFocusTest.kt:75` | Deep-card guard `index >= DeepRows` does not prove an off-viewport card | holds; device run used a card many rows deep | defer | showall-focus-restore-hardening |

## Next

- Review cap reached; no fixes this round. Judge with #1-#4 deferred and listed for the owner.
