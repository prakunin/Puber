# Triage round 2: startup-settled-signal

Reviewed (incremental): 5686efd0..b846ea7d (fix round 1, T1-T6). Inputs: review-edge-2.md, review-tests-2.md, review-codex-2.md (codex ran: review + adversarial, verdict clean). qa round 2 in qa-report.md: check.full green (2237 tests), no defects.

Round 1 fixes: T1-T6 confirmed closed by the tests lens.

| ID | Source | Sev | Finding | Verified | Mark | Note |
|----|--------|-----|---------|----------|------|------|
| U1 | tests F1 | minor | No test pins that the Update-press guard applies only to the automatic prompt | yes: guard set only in `showAutomaticPrompt`; no manual-path immediate-press test | accept | Round 2 minor; the behaviour is correct today; listed for the owner |
| U2 | edge unproven | - | Guard counted from `Available`, not from focus; focus retry can exceed 500 ms | not proven (overlay first requests focus at 100 ms; instrumented test takes focus within 1 s) | accept | Listed for the owner; needs device evidence of late focus to become real |
| U3 | edge unproven | - | Back unguarded right after the prompt appears; dismisses for the session | not proven | accept | Same behaviour class as base; listed |
| U4 | edge unproven | - | Auto result dropped while a manual dialog is open | not proven | accept | Consequence of T2; the manual check already showed the result |
| U5 | codex rejected | medium | Guard is not gesture-level | rejected by codex-reviewer with code evidence (TvSafeButton KeyUp needs its own KeyDown) | reject | As codex-reviewer |
| U6 | tests unproven | - | Guard flag outlives a dismiss | not proven | accept | Needs a sub-500 ms network check |

No blockers or majors. Review closes after round 2.
