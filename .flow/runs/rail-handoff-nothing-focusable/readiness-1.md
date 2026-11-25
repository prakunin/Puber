# Readiness 1: rail-handoff-nothing-focusable

Verdict: READY

Checked against worktree HEAD 0550d0a5 (base c9e98129 + spec commit); plan read from the worktree.

## AC coverage

| AC | Plan task | Test design row |
|---|---|---|
| AC-1 | 1, 3; D3 | `backTwiceFromRailAtNeverFocusableRoot_exitsDuringHandoff` (red), `backDuringDismissHandoffAfterArmExpired_isSwallowed` |
| AC-2 | 1, 2 | `failedBackHandoffAtNeverFocusableRoot_railStaysClosed_leftReopensIt`, `..._expiredArmBackReopensRail` (red) |
| AC-3 | 1, 2 | `rightFromRailAtNeverFocusableRoot_failedHandoffLeavesRailClosed` (red) |
| AC-4 | 1, 2, 3 | `pickDuringInitialHandoff_switchesToPickedTab`, `..._restartsAsTabPick_outgoingTabCannotSettle` (red) |
| AC-5 | 1, 3 | `centerOnSelectedTabDuringInitialHandoff_switchesNothing` |
| AC-6 | 1, 2 | `coldStartIntoNeverFocusableTab_failedInitialHandoffLeavesRailClosed_thenContentTakesFocus` (red) |
| AC-7 | 1, 3 | `centerOnNowSelectedTabDuringTabPick_isRefused_thirdTabSwitches` |
| AC-8 | 1, 2 | `tabPickIntoNeverFocusableTab_failedHandoffReopensRail`, existing `selectingATabWhoseContentNeverTakesFocus_reopensTheRail` |
| AC-9 | 1, 2, 3; Device work G | five existing classes, `rePickOfSelectedTab_closesRailOntoContent` |
| AC-10 | Device work D1 | D1 |
| AC-11 | Device work D2 | D2 |
| AC-12 | 2, 3 | DrawerStateTest cases, `check.full` |

## Citations sampled (at c9e98129)

| Citation | Claimed | Found |
|---|---|---|
| `ModalNavigationDrawer.kt:136-139` | `beginHandoff` returns null outside Open | yes |
| `ModalNavigationDrawer.kt:142-145` | `beginInitialHandoff` sets `expectsNewContent = false` | yes |
| `ModalNavigationDrawer.kt:173-177` | `failHandoff(requestId, reopen)` | yes |
| `ModalNavigationDrawer.kt:292` | Right key `beginHandoff(expectsNewContent = false)` | yes |
| `MainScreenComponent.kt:221,223,226-229` | HandingOff swallow, Back handoff, arm at root | yes |
| `MainScreenComponent.kt:304-308,329` | `acceptsPickOf`, item click | yes |
| `MainScreenComponent.kt:107,124` | `closeOnFail = { tabStack.canPop }`, dialog re-entry | yes |
| `ContentFocusHandoff.kt:24,71,108,114-118` | 5 s budget, `reopen = !closeOnFail()`, outgoing-tab guard | yes |
| `RailBackOwner.kt:25-30`, `ExitConfirmation.kt:12,34-46` | HandingOff is RailOpen; 3 s window, `isArmed`/`disarm` | yes |
| `MainHostHarness.kt:102,126,402,406` | `loadingRoot`, frozen clock, static selection | yes |
| `MainSideMenuFocusTraversalTest.kt:150` | `beginHandoff(expectsNewContent = false)` | yes |
| `DrawerStateTest.kt` ranges in Task 2 | named cases | yes, each name at the cited line |
| `baseline-prof.txt:29811,29819,29822`, `startup-prof.txt:18854,18857` | stale `DrawerState` entries | yes |
| prior spec `:8,:98,:145,:153,:156,:167,:292` | rows Task 4 points at | yes |
| `Makefile:23,129-137` | `GRADLE ?= ./gradlew`, `TESTS` to runner `class` | yes |

## Build order

Task 1 touches only androidTest and compiles on base (no `HandoffKind` reference). Task 2 changes the `beginHandoff` signature together with every caller found by `grep -rn beginHandoff app/src` (`MainScreenComponent.kt:223,329`, `ModalNavigationDrawer.kt:292`, `MainSideMenuFocusTraversalTest.kt:150`, `DrawerStateTest`); no other module references it. Task 3 consumes Task 2's API; Task 4 is docs only.

## P0 rows

Every P0 row is `red` or `guard`, names the defect or mutation (M1, M3-M12) and the backticked test. AC-10 and AC-11 are device checks D1 and D2, with steps in Manual device checks.

## Scope and questions

The plan stays inside Out of scope (id-latching readers, visuals, `railBackOwner`). Q1 and Q2 (blocking) are answered; plan question 1 (prior spec `:292`) is answered and carried out in Task 4.

## Notes for the conductor

1. The plan file is untracked in the worktree (`?? docs/superpowers/plans/2026-11-25-rail-handoff-nothing-focusable.md`); commit it before Task 1.
2. R8 (the `handoffKind == Dismiss` part of the exit gate) has no test and relies on code review; reviewers should check it.
