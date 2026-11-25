# Gate - rail-handoff-nothing-focusable - round 1

**Decision:** CONCERNS
**Head:** 24c264aa   **Base:** c9e98129

## Done as asked

Production diff (`git diff c9e98129...HEAD -- app/src/main`, unchanged since a746c00f) is the spec's Option A and nothing else: `HandoffKind { Initial, TabPick, Dismiss }` in `DrawerState` (`ModalNavigationDrawer.kt`), `beginHandoff(kind)` restarting only TabPick over a non-Dismiss handoff, `failHandoff` reopening only for TabPick, `acceptsPickOf` keyed on kind, and the armed-Dismiss exit in `RailOpenBackHandler` (`MainScreenComponent.kt`). No visual change; the out-of-scope readers (BrowseAllEntry, FocusTargetState, ShowAllScreenContent) are untouched.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `MainRailHandoffKindTest.backTwiceFromRailAtNeverFocusableRoot_exitsDuringHandoff` | red at base :46, green at HEAD, M1/M3/M12 killed (evidence R, G, M); device D3 conditional on an empty Favourites, which is not empty | PASS |
| AC-2 | `failedBackHandoffAtNeverFocusableRoot_railStaysClosed_leftReopensIt`, `..._expiredArmBackReopensRail` | red at base :243, green at HEAD | PASS |
| AC-3 | `rightFromRailAtNeverFocusableRoot_failedHandoffLeavesRailClosed` | red at base :91, green at HEAD | PASS |
| AC-4 | `pickDuringInitialHandoff_switchesToPickedTab`, `pickDuringInitialHandoff_restartsAsTabPick_outgoingTabCannotSettle` | red at base; qa round 2: M9-same-id red 3/3 at :130 after the E3 hardening | PASS |
| AC-5 | `centerOnSelectedTabDuringInitialHandoff_switchesNothing` | guard green, M7 killed | PASS |
| AC-6 | `coldStartIntoNeverFocusableTab_failedInitialHandoffLeavesRailClosed_thenContentTakesFocus` | red at base :154, green at HEAD | PASS |
| AC-7 | `centerOnNowSelectedTabDuringTabPick_isRefused_thirdTabSwitches` | guard green, M7 killed | PASS |
| AC-8 | `tabPickIntoNeverFocusableTab_failedHandoffReopensRail`, `MainSideMenuFocusTraversalTest.selectingATabWhoseContentNeverTakesFocus_reopensTheRail` | green, M5 killed | PASS |
| AC-9 | group G (six classes) | 46/46 green at a746c00f; round 2 reran MainRailHandoffKindTest and MainTabPushRailFocusTest 18/18 at 8f119fa2 | PASS |
| AC-10 | device D1 on the box | Back x3 exits on Home (2/2), History, Movies | PASS |
| AC-11 | device D2 on the box | pick Movies, card focused, rail collapsed; Left, Back returns to the card | PASS |
| AC-12 | `DrawerStateTest` + `check.full` | 25/25; check.full re-run by the judge at 24c264aa: BUILD SUCCESSFUL (lintAnalyzeDevDebugAndroidTest re-executed) | PASS |

Sampled: production diff, `MainRailHandoffKindTest.kt:37-140`, `DrawerStateTest` diff, evidence file; `tools/mobile-evidence-audit.sh` on the evidence dir: passed.

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| E1 stale `beginHandoff(Z)` in baseline profile | blind F1 | rejected, accepted | Generated file; plan line 19 lists the stale entries for the next `tools/generate-baseline-profile.sh`; `baseline-prof.txt:29811` confirmed. Carried to Concerns |
| E2 outdated comment | blind F2 | fixed | 60705da9 |
| E3 outgoing-tab test never exercised the guard | codex | fixed | b163ed42; qa round 2 M9-same-id red 3/3 |
| E4 real tab root loading >5 s after cold start ends Closed with nothing focused | edge unproven | rejected, accepted | Spec Out of scope names it as an accepted, untested risk. Carried to Concerns |
| E5 dialog re-entry over emptied content ends Closed | edge unproven | rejected, accepted | Brief intended-behaviour table: Initial fail ends Closed (Q1) |
| E6 double Center within one frame, intermediate tab settles, wall-clock margin | codex, blind, edge unproven | rejected, accepted | Clicks fire on key-up; timing green 3/3 and 18/18 on the box |
| R8 `handoffKind == Dismiss` part of the exit gate untested | readiness-1 item 2, test-design R8, qa note 2 | not dispositioned in any triage | state-path-1 traced it correct by reading. Carried to Concerns |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | One extra snapshot field; no new composition or main-thread work. Baseline profile entries for `DrawerState` stale until regenerated (E1) |
| Reliability | `handoffKind` cleared with `pendingHandoffId` in settle and fail; not saved, restore gives Closed. A failed Dismiss/Initial at a root now leaves nothing focused until Left, Back or self-focusing content (brief Risks; E4) |
| Maintainability | Boolean replaced by an enum with a derived `handoffExpectsNewContent`; KDoc updated; `require(kind != Initial)` guards the API; no dead code left |
| Security / privacy | No secrets or account data in the diff; evidence audit passed; screenshots kept local |
| TV | Back exit, Left reveal and pick behaviour covered by instrumented tests and D1/D2 on the box |

## Manual checks for the owner

none

## Must fix

none

## Concerns for the owner

1. R8: the `handoffKind == Dismiss` condition in `RailOpenBackHandler` keeps one Back from closing the app during a dialog re-entry with a live arm. No test covers it and no triage recorded a decision; it is correct by code reading only.
2. E4 (accepted out-of-scope risk): if Home or Movies takes over 5 s to load on cold start, the rail now ends collapsed with nothing focused instead of reopening; recovery is Left or Back. Not reproduced on a device.
3. E1: `baseline-prof.txt:29811,29819,29822` and `startup-prof.txt:18854,18857` name removed `DrawerState` signatures; regenerate the profiles before the next release.
