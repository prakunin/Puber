LENS: tests  ROUND: 2  BASE: c9e98129

No findings.

Reviewed fda01538..8f119fa2 (E2, E3; no production code).
- androidTest compiles at HEAD (:app:compileDevDebugAndroidTestKotlin). Instrumented run left to qa round 2.
- The E3 test now covers its case: with the swap held the outgoing root composes and focuses ROOT_CARD_FIRST before the swap; assertRootAbsent(OTHER_TAB_ROOT) after awaitFocused shows focus is on the outgoing root; if a handoff accepts the outgoing tab it settles within one 250 ms retry and the 1 s window fails at :130 (the sampled Closed-before-Open assertion would also catch it). Releasing well before 5 s keeps the budget-restart assertions.
- The seam stays inside this test (reset() clears it; default false; MainVM order kept).
- No assertions removed or weakened; E2 is comment-only.

Open: qa must record the 3 M9-same-id failures at :130 in the evidence.
