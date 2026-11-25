# Gate - showall-rail-back-exits-app - round 1

**Decision:** PASS
**Head:** 8721db44   **Base:** f88f3079

## Done as asked

Production diff (`git diff f88f3079...HEAD -- app/src/main`) matches spec Design: `railBackOwner` (`RailBackOwner.kt`) gates TabBack, RailClosed and RailOpen; `TabStackState` attached by `TabFlowNavigator`; arm and toast only when `!tabStack.canPop`; `ExitDisarmOnTabPush`; `onEnter` gate on the closed rail; `RevealOnUnhandledLeft` with the Compose-focus guard; `failHandoff(reopen)` via `closeOnFail`; focus clear on `Closed`; disarm on a rail pick (A6); `acceptsPickOf` (B3, C3). No visual change; ux-designer VISUAL: match (`review-ux-designer-1.md`). `rail-content-mismatch-rapid-back` untouched.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `railOpenOnPushedScreen_backClosesRailAndKeepsScreen_afterColdStart` (asserts card refocused, exit not armed, `MainRailBackTest.kt:289-301`); M1 | qa round 7 at 73371c73, red on base (`d1-d2.md`); M1 on box rounds 4, 6, 7 | PASS |
| AC-2 | `..._backClosesRailWithoutArmingExit_afterDetailsRoundTrip`; M2 | qa round 7; M2 round 1; red on base | PASS |
| AC-3 | `..._afterTabSwitch`; M3 | qa round 7; M3 round 1; red on base | PASS |
| AC-4 | `backWithRailClosedOnPushedScreen_popsToTabRoot_inEveryHistory`; M4 | qa round 7; M4 round 6 | PASS |
| AC-5 | `fiveBacksFromRailOverPushedScreen_exitOnlyOnFifth`; M5 | qa round 7; M5 round 1; red on base | PASS |
| AC-6 | `armedAtRoot_thenPushAndPop_nextBackOpensRailNotExit`, `armedAtRoot_thenTabPick_nextBackOpensRailNotExit`; M6, M12 | qa round 7; A4, A6 mutations killed (round 4-5); M12 round 4 | PASS |
| AC-7 | `backAtTabRoot_opensRailThenArmsThenExits`; M7 | qa round 7; M7 rounds 2, 4 | PASS |
| AC-8 entries 1-3, Left from grid | `MainTabPushRailFocusTest` (5 tests); M8 steps 1-3, 5 | qa round 7; red on base (`d1-d2.md`); A1a/A1b mutations killed (round 4) | PASS |
| AC-8 shelf tile entry, Left during real Loading | M8 step 4, step 6 | none on device (cached data, no short-list row); covered by instrumented `pushIntoLoadingScreen_...`, `leftWithNothingFocused_...` | NOT CHECKED (manual, section 6) |
| AC-9 | `ShowAllRailCloseFocusTest`, `ShowAllFilterFocusTest`; M9 | qa round 4; M9 round 2 | PASS |
| AC-10 | `backOnRootPushedDetails_popsToMain`; M10 | qa round 4; M10 round 1 (player Back by the unplanned playback) | PASS |
| AC-11 | `check.full` (--rerun-tasks) and the named guard classes | qa round 7 at 73371c73; only `docs/superpowers/evidence/2026-11-25-showall-rail-back-exits-app-device.md` changed since | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| A1 | triage-1 major | fixed | A1a/A1b mutations killed (qa round 4); 1fd30c7a kept with reasons |
| A2, A4, A5, A6 | triage-1 minor | fixed | ea5eef7a, 00be3dcb, 8d0fb937 + 4b346104, 4a962b04 |
| A3, A7 | triage-1 minor, nit | fixed (doc) | 76e28b2b, test design rows |
| A8, A9 | triage-1 | rejected, accepted | A9 spot-checked: root case is base behaviour, run `rail-handoff-nothing-focusable` exists; pushed case fixed by A5 |
| B1, C1, D1 | triage-2..4 major | resolved | mutation kills, qa rounds 5-7 |
| B2 | triage-2 major | fixed | df0c3447; mutation killed at `MainRailBackTest.kt:174` (qa round 6) |
| B3, C2, C3 | triage-2, triage-3 | fixed | 15f0f3a5, 4d64a1b7, 6c44c4e9; kills at `:203`, `:223` |
| B4 | triage-2 nit | fixed | 6724d512 |
| B5, C4, D7 | unproven | rejected, accepted | no evidence offered by the reviewers |
| D2, D3, D4 | triage-4 minor | accepted minor | D2 worst case is a duplicate `ItemSelected`, as on base |
| D5 | triage-4 | rejected, accepted | spot-checked: `beginHandoff` is refused unless Open (`DrawerStateTest`), so the id stays; the outgoing tab's effect is cancelled by `key(currentTab.key)` (`TabComponent.kt:178`); the new tab's `bornDuringRequest` equals the pending id and runs a fresh window (`ContentFocusHandoff.kt:104-145`) |
| D6 | triage-4 minor | deferred | run `.flow/runs/rail-pick-during-initial-handoff/` |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | `canPop` read only in the small handler composables and `ExitDisarmOnTabPush`; `contentHasFocus` is read in a key listener, not in composition. No main-thread or hot-path cost found |
| Reliability | Failed handoff over a pushed screen settles Closed (R13 test); listener removed on dispose; `HandingOff` persisted as Closed (`DrawerStateTest`). Nothing-focusable screen on device not checked (section 6) |
| Maintainability | Follows AGENTS.md conventions; `DrawerMainContent` made `internal` as the test seam; no dead code (`isOpen` still used, `MainScreenComponent.kt:263`); detekt and lint green |
| Security / privacy | No secrets in the diff; `tools/mobile-evidence-audit.sh` on the evidence dir: passed (run at gate). qa round 1 started an episode on the owner's account for about 3 s (`docs/superpowers/evidence/2026-11-25-showall-rail-back-exits-app-device.md:34`), a possible 0:04 progress record; recorded, not repeated in later rounds |
| TV | Back, Left, Center on the collapsed rail covered by instrumented tests and box checks; owner box only, `com.kino.puber.stage`, install-preserve, no authorised `install -r` |

## Manual checks for the owner

Box `192.168.1.110:5555`, worktree dev APK, keys by `adb -s 192.168.1.110:5555 shell input keyevent` (Left 21, Center 23, Down 20, Back 4).

| # | Item | Steps | Expected |
|---|------|-------|----------|
| 1 | AC-8 shelf entry | On a tab root find a row ending in a «Показать все» tile, Center on it; screenshots at about 1 s and 3 s | Rail collapsed; first card focused once loaded |
| 2 | AC-8 Left during Loading | On a slow network or an uncached Show all, Center on «Все фильмы», press Left while the spinner shows, then Back after the grid lands | Left expands the rail with Movies focused; Back collapses it; app stays on Show all |
| 3 | R11 step 5 | On a screen with nothing focusable (empty Bookmarks/History, or Show all in Error): Up, Down, Right, then Left, then Back | Up/Down/Right do nothing; Left expands the rail; over a pushed screen the rail ends collapsed with no item focused and the next Back pops |
| 4 | Corrective pick | Cold start; Left, pick a tab, then at once Down, Center while it still loads | The second tab opens; rail collapses; no reopen after 5 s |

## Must fix

none

## Concerns for the owner

none
