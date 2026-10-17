# QA report - showall-focus-restore-hardening

## Round 1

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; tasks UP-TO-DATE from the implementer's identical run at 4aa7bb0e, `lintDevDebug` re-executed; stored `GridFocusMemoryTest` result: 21 tests, 0 failures)
DEVICE: emulator-5554 (puber_tv_36), leased, released, left running
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS (JVM only) | `GridFocusMemoryTest` U-AC1a/b/c green (21/21); red on the Step A code per the Implementation record. The race cannot be forced on a device |
| AC-2 | PASS | instrumented `showAllFocusMovesToTheNeighbourWhenTheFocusedCardLeftTheListBeforeReentry` green, and red under M-AC2 (`ComposeTimeoutException ... 5000 ms`); JVM U-AC2a green |
| AC-3 | PASS | resolver cases in `GridFocusMemoryTest`, green |
| AC-4 | PASS | U-AC4 green; red-once recorded by the implementer (`expected 1 but was 3`). Not re-run by qa (JVM, no mutation by qa) |
| AC-5 | PASS | U-AC5a/b/c green |
| AC-6 | PASS | U-AC6a-e green |
| AC-7 | PASS | `showAllFocusReturnsToACardBelowTheFirstViewportAfterReentry` green; M-AC7 (`repeat(2)`) red with `AssertionError: focus moved below the first viewport`; 5 Down presses leave card 1 off screen on this emulator (the assertion held) |
| AC-8 | PASS | class run 5/5 green on the committed head and again after both reverts; M-AC2 and M-AC7 each seen red once, each reverted with `git diff --exit-code` empty |
| AC-9 | PASS (M1/M2); opener-removed path PARTIAL | D-M1 passed 4 times incl. deep cards; D-M2 rail opened right after Back and kept focus (6 s), tab switch away and back kept the grid focused without re-opening the rail. D-M3: trigger found (Hide watched content, a local preference, Show all kept on the Movies tab stack, opener = watched card at about row 64): after return a grid card was focused with no key press, rail closed, grid live on the next DOWN. The exact clamped neighbour could not be read off the screen because the grid re-paged; exact-neighbour logic rests on the JVM tests and the instrumented AC-2 test |
| AC-10 | PASS | check.full green |

TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/showall-focus-restore-hardening/docs/superpowers/evidence/2026-10-17-showall-focus-restore-hardening-device.md (committed d6b51a2e; `mobile-evidence-audit.sh` passed)
DEFECTS: none
UNAVAILABLE: none
MANUAL: none required. Optional, for the owner: the AC-1 race window itself and the exact neighbour after a re-paged reload are not reproducible on the emulator.

Observations (no defect):
- A LEFT key sent 150 ms after BACK from Details was absorbed during the transition (the rail did not open, nothing was stolen); at 600 ms it opened the rail. Timing of key delivery, not related to this change.
- Two navigation slips of mine sent keys inside Details (down presses and one DPAD_CENTER). Details stayed on screen, `MainActivity` stayed on top, "Not started" unchanged: no playback, no state change.
- The setting touched, "Hide watched content", was OFF at the start and is OFF again (verified on screen). No uninstall, data clear or downgrade; the install was `compatible_replace`, destructive_action false, base.apk sha256 equal to the built APK.

## Review round 1 fixes

Head cc66ec96 (evidence commit on top). Triage rows #1-#3.

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; unit/detekt tasks UP-TO-DATE from the implementer's run at the same head, lint re-executed; `GridFocusMemoryTest` 23 tests, 0 failures)
DEVICE: emulator-5554 (puber_tv_36), leased, released, left running
ACS:
| Item | Status | Evidence |
|------|--------|----------|
| #1 retry on content focus active (JVM) | PASS | `GridFocusMemoryTest` 23/23 green incl. the new inactive-then-active case |
| #2 pendingEntry gate on fallback C, live path (JVM) | PASS | same class, new case green |
| #3 docs note (C wiring checked by review only) | NOT CHECKED | docs-only row, not a qa item |
| AC-8 instrumented `ShowAllScreenContentFocusTest` | PASS | 5/5 green on the device |
| D-M1 Back restores the same card | PASS | deep right-column card, same card and scroll after Back with no key |
| D-M2 rail opened 0.6 s after Back not stolen | PASS | rail focused ("Movies") at 0.8 s and at about 6 s |
| Rail open/close over a moved grid focus: no jump back to an older card | PASS | 5 open/close cycles with DOWN between them, no older card was ever re-focused, grid never took focus while the rail was open |
| Rail close landing position | NOT CHECKED (open observation) | see below |

TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/showall-focus-restore-hardening/docs/superpowers/evidence/2026-10-17-showall-focus-restore-hardening-device-round1-fixes.md (committed; `mobile-evidence-audit.sh` passed)
DEFECTS: none confirmed
UNAVAILABLE: none (APK install `installed`, `compatible_replace`, sha256 on device equals the build)
MANUAL: none required

Open observation (not attributed to this change): in the Show all grid, closing the rail with RIGHT put focus on the card one row above the one that was focused when the rail was opened, 5 of 5 times (e.g. opened from "Korol Interneta", closed onto "Terapiya Kamino"). The History grid keeps the same card for the same open/close. By code reading `GridFocusMemory.reconcile()` returns early once the grid has had focus (pendingEntry false, no card left), so the new `contentFocusActive` key does nothing in this sequence, which points to behaviour that predates the change (rail-to-grid focus entry), but a base build comparison was not possible for qa (no production edits, no build outside the worktree). Suggested: the conductor compares on the base APK, or opens it as a follow-up run next to showall-first-entry-rail-focus.
Other: a background trailer preview on the Movies home plays by itself (app behaviour, not started by qa); two navigation slips opened a Details screen (Back pressed, no playback, no state change).

## Base comparison: rail close focus

Result: base also shifts. The one-row-up landing on rail close predates this change.

- Base build: master HEAD 35bfbbec, main checkout `./gradlew :app:assembleDevDebug`, installed on emulator-5554 with `install-preserve` (`installed`, `compatible_replace`, destructive_action false, same signer).
- Sequence, 3 repeats, D-pad only: Movies tab, last row "All", RIGHT to "Show all", CENTER, RIGHT into the grid, DOWN to a card in the left column, LEFT (rail opens), RIGHT (rail closes). Between repeats DOWN x2.
- Observed on base:
  1. Opened from "Sherlok Kholms ... Krovavaya nadpis", closed onto "Sherlok Kholms ... Korol shantazha" (one row above).
  2. Opened from "Tolko otvazhnye / None But the Brave", closed onto "Krovavaya nadpis" (one row above).
  3. Opened from "Pokhoronennye zazhivo", closed onto "Tolko otvazhnye" (one row above).
  Count: 3/3 one row above, 0/3 same card. This matches the 5/5 on HEAD cc66ec96.
- Conclusion: not a regression of this run. Candidate for a follow-up run next to showall-first-entry-rail-focus (rail-to-grid focus entry in the Show all grid; History grid keeps the same card).
- Device end state: the worktree build (cc66ec96 APK, sha256 d84456fc...) reinstalled with `install-preserve` (`installed`, `compatible_replace`); on-device base.apk sha256 equals the worktree APK. App launched, lease released.
- Slips: the first DOWN presses on Home landed on the hero carousel and CENTER opened a Details screen ("Mayday"); BACK closed it, no playback, no state change. No uninstall, data clear or downgrade. No code changed, nothing committed.
