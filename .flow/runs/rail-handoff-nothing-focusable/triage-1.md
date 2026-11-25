# Triage round 1: rail-handoff-nothing-focusable

Reviewed: c9e98129..fda01538. Lenses: blind, edge (no findings), tests (no findings), codex (ran: review + adversarial), antigravity (ran, clean), state-path (no findings). Each finding checked against the code by the conductor.

| ID | Source | Severity | Finding | Decision | Reason |
|----|--------|----------|---------|----------|--------|
| E1 | blind F1 | minor | baseline-prof.txt:29811 still names beginHandoff(Z); the new signature and HandoffKind are not profiled | reject | Generated file; the plan (decision 3) keeps repository practice: profiles are not hand-edited and are refreshed by tools/generate-baseline-profile.sh. Stale lines listed in the plan; listed for the owner at handover |
| E2 | blind F2 | nit | MainTabPushRailFocusTest.kt:94 comment still says a Back handoff over Loading reopens at 5 s | fix | Confirmed; one comment |
| E3 | codex minor | minor | pickDuringInitialHandoff_restartsAsTabPick_outgoingTabCannotSettle swaps the tab in the same action that makes the outgoing root focusable, so it may never exercise the outgoing-tab guard (M9-same-id survived 3/3) | fix | Confirmed by the qa mutation result. Hold the swap in the harness until the outgoing root has rendered focusable cards, then release; qa reruns M9-same-id 3 times |
| E4 | edge unproven 1 | — | A real tab root loading for more than 5 s after cold start may end with nothing focused (base reopened the rail) | reject (unproven), listed for the owner | Needs a throttled cold start, which is not authorised on the box (no network changes); recorded as a manual check for the owner |
| E5 | edge unproven 2 | — | Dialog re-entry over content that just became empty now ends Closed (base Open) | reject | Intended per the spec's per-kind table (Initial fail ends Closed, Q1); Left and Back recover |
| E6 | codex rejected, blind/edge unproven | — | Intermediate tab settles the latest pick; double Center within one frame; wall-clock margin | reject | Unproven; clicks fire on key-up; timing passed 3/3 on the box |

Fix round: E2, E3 to the implementer; incremental review round 2 (edge, tests, codex, antigravity); qa round 2 on MainRailHandoffKindTest with M9-same-id x3.
