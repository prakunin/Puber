# QA report

## Round 1

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; up-to-date results: SectionVMRefreshDepthTest 11/11, ShowAllVMTest 10/10, 0 failures)
DEVICE: emulator-5554 (leased, released); worktree APK installed via install-preserve, outcome `installed`, on-device sha256 matches.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | `refresh_afterAPageThatReadAhead_doesNotReRequestAServerPageItAlreadyHolds` and `..._walksOnFromTheLastServerPageRead` green |
| AC-2 | PASS | `refresh_withOverlappingPages_publishesEachIdOnceInFirstOccurrenceOrder`, `ShowAllVMTest.retry_ofAGridWithOverlappingPages_publishesEachIdOnce` green |
| AC-3 | PASS | existing refresh-depth cases green, unchanged |
| AC-4 | PASS | three AC-4 unit tests green |
| AC-5 | PASS | `refresh_throughEmptyPagesThatReadAhead_stopsAfterTheEmptyPageBudget` green |
| AC-6 | PASS | `refresh_whoseLaterPageFails_leavesTheRowAsItWas` green |
| AC-7 | PASS (partial) | "show anime" off; Popular row 84 cards, all distinct, after open title + BACK; focus returned to the opened card; no `was already used`, no FATAL. No save/unsave (not authorized). The row refresh itself was not observed. |
| AC-8 | PASS (partial) | Popular grid, 81 cards, all distinct before and after open title + BACK; focus restored to the opened card. Same limits. |

TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/carousel-row-duplicate-cards/docs/superpowers/evidence/2026-11-11-carousel-row-duplicate-cards-device.md (commit afc1e706; mobile-evidence-audit passed)
DEFECTS: none
UNAVAILABLE: none
MANUAL: Manual 3 ("show anime" on) not run. The device runs do not prove that the BACK refresh reached restore depth, and the bug did not reproduce on base for comparison; the owner can add a save/unsave on a deep card (an account mutation, not authorized here).

## Round 2

CHECK: not rerun (production code unchanged since afc1e706; `git diff afc1e706..HEAD -- app/src/main` empty)
DEVICE: emulator-5554 (leased, released); round 1 APK reused, on-device sha256 equals the fresh build.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-7 | PASS | "Show watched indicators" flip with the row at depth: log shows Popular pages 1..6 requested once each (reload ran); 80 distinct titles walked, none repeated; no crash. Focus landed on the first card (tab rebuilt), not the pre-flip card. |
| AC-8 | PASS | Same flip with the grid open at row 24: pages 1..6 requested once by the row VM and once by the grid VM; focus stayed on the same card; 85 distinct titles to the last row, none repeated. |

TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/carousel-row-duplicate-cards/docs/superpowers/evidence/2026-11-11-carousel-row-duplicate-cards-device.md (round 2 section; audit passed)
DEFECTS: none
UNAVAILABLE: none
MANUAL: Row focus after the reload is the first card because the tab is rebuilt; owner may decide whether that is wanted. The bug did not reproduce on base, so no red-to-green comparison exists on device. Settings restored to original values.
