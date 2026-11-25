# Triage round 1: showall-rail-back-exits-app

Reviewed: f88f3079..c6b25f49. Lenses: blind (no findings), edge, tests, codex (ran: review + adversarial), antigravity (ran, clean), state-path, ux-designer (VISUAL: match). Each finding checked against the code by the conductor.

| ID | Source | Severity | Finding | Decision | Reason |
|----|--------|----------|---------|----------|--------|
| A1 | tests F1; edge F2, state-path F2, antigravity, codex (on 1fd30c7a) | major | No test is shown to fail if RevealOnUnhandledLeft is removed or its guard broken; 1fd30c7a was made for a test bug | fix | Keep 1fd30c7a (state-path, edge, antigravity, codex: it covers the case where the view keeps focus but no Compose node does; no harm found). Prove the listener: qa runs two mutations on the box (listener removed; guard inverted); leftWithNothingFocused must go red. If it stays green, the implementer makes the test reach the unhandled-key path |
| A2 | codex minor, tests F2 | minor | Nothing fails if the exit is armed when the rail closes over a pushed screen (the next pop disarms it) | fix | Confirmed. Cheap: a test seam so the harness can assert exitConfirmation is not armed right after awaitDrawer(Closed) over a pushed screen; test design AC-2 row corrected |
| A3 | tests F3 | minor | backAtOtherTabRoot test cannot catch the R2 owner-check defect | fix (doc) | Correct the R2 row to name TabStackStateTest.detach_byPreviousOwner_keepsNewAttachment as the guard |
| A4 | tests F4 | minor | AC-6 test can pass vacuously when the 3 s exit window expires | fix | Assert the time from arm to the final Back is under the window (or a fake clock) |
| A5 | edge F1, blind unproven 1 | minor | Rail opened (via unhandled Left) over a pushed screen with nothing focusable: Back cycles HandingOff→Open and never pops; only a tab pick leaves | fix | Confirmed in code (railBackOwner gives Back to RailOpen; handoff fails back to Open). Reachable through the new Left path on Show all Error with filter unavailable. Fix: when the tab can pop, a failed handoff settles Closed so the next Back pops; instrumented case with a never-focusable child |
| A6 | state-path F1 | minor | Exit armed at one tab root survives a tab pick; the next Back at the other root exits | fix | Confirmed; the new KDoc states the invariant. Cheap: disarm on a rail tab pick, with a test |
| A7 | edge F2, state-path F2 | nit | Spec and test design still name the !view.hasFocus() guard | fix (doc) | Update to the Compose-focus guard |
| A8 | codex rejected, edge/blind unproven | — | One-frame window between reveal and handler flip; tab-swap re-attach order; onEnter refusing requestFocus while Closed; overlay outside content; below-API-28 path | reject | Unproven per the reviewers' own conditions; no evidence |
| A9 | codex rejected | — | Loading screen outlasting the 5 s handoff reopens the rail | reject | At a tab root this is base behaviour, deferred to rail-handoff-nothing-focusable; on a pushed screen it is covered by A5 |

Fix round: A1 (test part, if needed), A2, A4, A5, A6 to the implementer; A5/A6 design rows and A7 spec text to the architect; A2/A3/A7 rows to the test-designer; qa: mutations for A1, the new tests, and the affected device checks.
