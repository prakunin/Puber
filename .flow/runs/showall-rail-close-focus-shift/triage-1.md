# Review triage - round 1

Run: showall-rail-close-focus-shift   Base: 6b087697   Reviewed head: 97be2417

Sources: review-blind-1.md, review-tests-1.md, review-codex-1.md (ran: review, clean). Size S: no edge lens.

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | blind F1 | minor | `ShowAllScreenContent.kt:235-241` | Handoff marked answered before the restore succeeds; an interrupted attempt is not retried | Read `onHandoff` and `ModalNavigationDrawer.kt:129-132`: content becomes inactive mid-attempt only when the rail reopens within the 3 restore frames, and the next close of an open rail always starts a new handoff id (`beginHandoff` -> `startHandoff`), which is answered afresh. Retrying the same id after a dialog closes would take focus from the user, which AC-5 forbids | reject | unproven: no ordinary path reactivates content under the same handoff id; a retry would contradict AC-5 |
| 2 | tests F2, conductor check | minor | `ShowAllRailCloseFocusTest.kt` | P1 instrumented tests from test-design.md not written: T-I2, T-I3, T-I4; T-I1b name differs and has no scrolled case. `check-named-tests.py` reports 4 MISSING | Ran `check-named-tests.py`: MISSING AC-2 `showAllRailCloseKeepsFocusOnTheTopRowCardAndAfterScrolling`, AC-4 `showAllRailCloseWithoutMemoryBehavesAsBase`, AC-5 `showAllHandoffResponseDoesNotStealFocusFromADialog`, AC-5 `showAllRecompositionAfterSettleDoesNotRefocus` | fix | implementer fix round 1, then qa runs the class on the device |
| 3 | tests F3 | nit | `ShowAllRailCloseFocusTest.kt` top-row test | Top-row test passes on base, not labelled as a guard | Read test-design R6 and qa-report AC-2 note | fix | folded into #2 (comment in the renamed test) |
| 4 | tests F1 | minor | brief AC-3 | Back half of AC-3 unreachable (Back pops Show all, same as base); re-pick has device evidence only | qa-report AC-3 row; T-I6 is P2 and optional by design | reject (as a code finding) | no code change; AC-3 Back half carried to the gate as NOT CHECKED exactly as qa recorded; rewording an approved AC is the owner's call at gate 2 |

## Rejected codex claims

- none (codex clean)

## Next

- Fix round 1: implementer writes T-I1b (renamed, with scrolled case), T-I2, T-I3, T-I4 in `ShowAllRailCloseFocusTest.kt`; tests only, no production change. Then check.fast, qa-device runs `ShowAllRailCloseFocusTest` via `make DEVICE=<serial> itest`, lens tests on the incremental diff. Unproven lines: 3 (blind 2, tests 1).
