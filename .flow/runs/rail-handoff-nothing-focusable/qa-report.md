Round 1

CHECK: AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; tasks were UP-TO-DATE from the implementer's run, so DrawerStateTest was re-run with --rerun separately: 25 tests, 0 failures)
DEVICE: 192.168.1.110:5555 (owner's TV box), lease held and released; package com.kino.puber.stage only
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS (instrumented); device D3 not run | backTwiceFromRailAtNeverFocusableRoot_exitsDuringHandoff red at base (:46 isFinishing), green at HEAD; M1, M3, M12 fail it. D3: not run, Favourites (heart tab) is not empty |
| AC-2 | PASS | failedBackHandoff* two rows red at base (:243 expected Closed was Open), green at HEAD; M4, M12 fail them |
| AC-3 | PASS | rightFromRail... red at base (:91), green at HEAD; M4, M11 |
| AC-4 | PASS | both pickDuringInitialHandoff_* red at base (railPicks 0, expected 1), green at HEAD; M5, M6, M9-null fail them. M9-same-id survives the instrumented row (3 of 3) but fails 3 DrawerStateTest restart cases |
| AC-5 | PASS | centerOnSelectedTabDuringInitialHandoff_switchesNothing guard green; M7 fails it |
| AC-6 | PASS | coldStartIntoNeverFocusableTab_... red at base (:154), green at HEAD; M4 |
| AC-7 | PASS | centerOnNowSelectedTabDuringTabPick_isRefused_thirdTabSwitches guard green; M7 |
| AC-8 | PASS | tabPickIntoNeverFocusableTab_failedHandoffReopensRail and MainSideMenuFocusTraversalTest.selectingATabWhoseContentNeverTakesFocus_reopensTheRail green; M5 fails both |
| AC-9 | PASS | G: 46 tests, 0 failures across MainRailHandoffKindTest, MainRailBackTest, MainTabPushRailFocusTest, MainSideMenuFocusTraversalTest, InitialDrawerContentFocusTest, ShowAllRailCloseFocusTest; M8, M10 fail their rows |
| AC-10 | PASS | box: Back x3 about 0.5 s apart exits to the launcher on Home (2 of 2), History, Movies; Back 1 opens the rail, Back 2 collapses it with the exit toast (screenshots) |
| AC-11 | PASS | box: pick Movies from the rail -> Movies card focused, rail collapsed; LEFT then BACK -> rail collapsed, focus back on the card, unchanged after 6 s |
| AC-12 | PASS | DrawerStateTest 25 tests green; mutations M4, M5, M9 each fail DrawerStateTest cases; check.full green |
| R6 | PASS | rePickOfSelectedTabAtNeverFocusableRoot_... red at base (:220), green at HEAD; M4, M10 |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/rail-handoff-nothing-focusable/docs/superpowers/evidence/2026-11-25-rail-handoff-nothing-focusable-device.md (committed, audit passed); run report /home/dev/Puber/.flow/runs/rail-handoff-nothing-focusable/qa-report.md
DEFECTS: none
UNAVAILABLE: none
MANUAL: none required. Notes for the reviewers: (1) the instrumented outgoing-tab test does not detect the same-id restart variant of M9 (3 of 3 survived); DrawerStateTest does, so the mutation is covered at unit level only. (2) R8, the handoffKind == Dismiss part of the exit gate, has no test (not mutation-proven; M3 covers the isArmed part). (3) D0: the master-build refresh was not needed, the app opened on Home before any further install; auth snapshot taken. (4) Red run: all 8 red rows failed at the assertion named in the plan, 5 guard rows passed. (5) Evidence commit has no Co-Authored-By line, per commit_style.

# Round 2

CHECK: not rerun (test-only round; no production change at HEAD)
DEVICE: 192.168.1.110:5555
ACS: AC-4 PASS (pickDuringInitialHandoff_* green at HEAD; M9 same id red 3/3 at MainRailHandoffKindTest.kt:130, then green after revert)
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/rail-handoff-nothing-focusable/docs/superpowers/evidence/2026-11-25-rail-handoff-nothing-focusable-device.md
DEFECTS: none
UNAVAILABLE: none
MANUAL: none

## Round 2 (test-only changes, triage-1 E3)

Device: 192.168.1.110:5555 (TV box), lease held, auth-saved first, com.kino.puber.stage only. HEAD 8f119fa2, run through `make DEVICE=192.168.1.110:5555 GRADLE=./tools/agentw itest` with AGENT_GRADLE_SERIAL=1.

| Step | Result |
|------|--------|
| 1. MainRailHandoffKindTest (13) + MainTabPushRailFocusTest (5) at HEAD | 18 tests, 0 failures, 0 errors, 0 skipped: PASS |
| 2. M9 same id (ModalNavigationDrawer.kt:163, exact plan edit `{ handoffKind = kind; pendingHandoffId }`), `pickDuringInitialHandoff_restartsAsTabPick_outgoingTabCannotSettle`, 3 runs | run 1 RED, run 2 RED, run 3 RED. Each fails at `MainRailHandoffKindTest.kt:130` `assertDrawer(HandingOff)`: expected HandingOff but was Closed (as the plan predicted). The mutation compiled and the build ran. |
| Revert | `git checkout HEAD -- ModalNavigationDrawer.kt`; `git status --short` empty |
| 3. MainRailHandoffKindTest after revert | 13 tests, 0 failures: PASS |

Verdict round 2: the M9-same-id mutation is killed 3 of 3; no defects.
