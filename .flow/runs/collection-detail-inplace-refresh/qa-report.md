## Round 1

CHECK: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` -> pass (BUILD SUCCESSFUL; `CollectionDetailVMTest` 16 of 16, 0 failures)
DEVICE: emulator-5554 (AVD puber_tv_36), lease released

ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | unit `returnedChanges_publishRefetchedContentWithoutLoading`, `playerReturn_publishRefetchedContentWithoutLoading`; device Manual 1: no spinner frame in 18 of 18 returns on HEAD, 4 of 4 on base VM show it |
| AC-2 | PASS | device: same card `[662,415][1259,751]` after 18 of 18 (watched) and 6 of 6 (bookmark) returns, grid not reset; base VM shows the full-screen spinner 4 of 4 (focus was already kept on base, so the spinner is the reproduced symptom); instrumented `...StaysOnTheFocusedCardWhenContentIsReplaced` 3 of 3 |
| AC-3 | PASS | unit `firstLoad_publishesLoadingBeforeContent`, `retry_publishesLoadingAfterError`; device Manual 4: spinner then grid, first card `[32,144][630,480]`, 3 of 3 |
| AC-4 | PASS | unit `failedRefresh_keepsContentAndShowsMessage`; no device run (a failed fetch not provoked) |
| AC-5 | PASS | unit `overlappingRefreshes_publishOnlyTheLatestFetch` |
| AC-6 | PASS | unit: 5 tests (`toggleDuringRefresh_*` x2, `failedToggleDuringRefresh_*`, `refreshAfterPublishedToggle_*`, `gridToggleBeforeReturn_*`, `toggleBeforeFailedRefresh_*`); device Manual 2: card menu shows the Details bookmark value after every return, 6 of 6 |
| AC-7 | FAIL | `...MovesToTheNeighbourWhenTheFocusedCardLeavesReplacedContent` fails 3 of 3 (`ComposeTimeoutException` 5000 ms, test line 124); the "stays on the card" half passes 3 of 3 |
| AC-8 | PASS | unit `emptyReturnedChanges_doNotRefetch`, `returnedChanges_areForwardedToParentOnBack`, `back_waitsForSaveAndForwardsBookmarkChange` |
| Manual 3 (player return, AC-1/2 via `ItemPlayed`) | NOT CHECKED | playback writes watch progress to the account and a toggle cannot undo it; the unit test `playerReturn_*` covers the listener only |

TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/collection-detail-inplace-refresh/docs/superpowers/evidence/2026-11-11-collection-detail-inplace-refresh-device.md (audit passed; not committed, as asked)
DEFECTS:
1. AC-7 neighbour guard fails. Reproduce: `make DEVICE=emulator-5554 itest TESTS=com.kino.puber.ui.feature.collections.detail.component.CollectionDetailScreenContentFocusTest`; `collectionDetailFocusMovesToTheNeighbourWhenTheFocusedCardLeavesReplacedContent` times out waiting for the neighbour to be focused, 3 of 3 runs. `CollectionDetailScreenContent.kt` is unchanged by the run, so either the composable does not move focus to the neighbour when the focused card leaves (spec risk 5, `resolveFocusedItemId`) or the test's expectation is wrong. Not diagnosed which. Device-side effect: the removal case did not occur in Manual 1-2, since a toggle never drops a card.
2. Observation, not a confirmed defect: once in 18 watched-toggle returns on HEAD the grid card still showed the old watched state 2.6 s after BACK (Details had shown "unwatched"); the next return was consistent and 12 further returns matched. Possible race between the Details request and the refetch; the `isSaved` patch does not cover watched. Cause not established.
UNAVAILABLE: none
MANUAL: Manual 3 on a real playback session (owner decides whether the account may take a progress write); decide whether defect 1 is a test or a composable bug.


## Round 2

HEAD d1dea378 (Task 3: `FocusTargetState.kt`).

CHECK: `./tools/agentw testDevDebugUnitTest` -> pass (up to date, BUILD SUCCESSFUL); full `check.full` green from the implementer, not re-run
DEVICE: emulator-5554 (AVD puber_tv_36), lease released. The emulator was down at the start; qa started it headless (cold boot, no wipe), app and pairing intact. It is still running.

ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | unchanged from Round 1; device Manual 1 re-run on d1dea378: no spinner frame at 0.4 s, 4 of 4 |
| AC-2 | PASS | unchanged from Round 1; re-run on d1dea378: same card `[662,144][1259,480]` after 4 of 4 (watched) and 4 of 4 (bookmark) returns |
| AC-3 | PASS | unchanged from Round 1 |
| AC-4 | PASS | unchanged from Round 1 |
| AC-5 | PASS | unchanged from Round 1 |
| AC-6 | PASS | unchanged from Round 1; re-run: card menu shows the Details bookmark value after every return, 4 of 4 |
| AC-7 | PASS | `CollectionDetailScreenContentFocusTest` 5 of 5, 3 runs of 3, including `...MovesToTheNeighbourWhenTheFocusedCardLeavesReplacedContent` (Round 1: red 3 of 3) |
| AC-8 | PASS | unchanged from Round 1 |
| Manual 3 (player return) | NOT CHECKED | unchanged: playback writes watch progress, a toggle cannot undo it |
| Shared helper, Home/SectionRow/VideoGrid | PASS | `HomeFocusTraversalTest` 4/4, `HomeCollectionBackFocusTest` 2/2, `SectionRowFocusTraversalTest` 6/6, `VideoGridInitialFocusTest` 7/7, extra `ShowAllScreenContentFocusTest` 5/5 and `CollectionsScreenContentFocusTest` 1/1; device: Home "Continue watching" and "My bookmarks" card, Details, Back, same card focused, 4 of 4 |
| Shared helper, `HomeSectionRemovalFocusTest` | FAIL, pre-existing | `removingFocusedFirstCardFocusesTheRightNeighborAndNotSearch` fails 3 of 3 on d1dea378 and 2 of 2 with the helper restored from cad9f616 (same assertion); cad9f616 carries the base helper, so Task 3 did not cause it |
| Shared helper, Bookmarks | NOT CHECKED | no Bookmarks focus test in androidTest; the Bookmarks tab is not enabled in the side menu and Settings was not touched |

TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/collection-detail-inplace-refresh/docs/superpowers/evidence/2026-11-11-collection-detail-inplace-refresh-device.md (Round 2 section appended; audit passed; not committed, as asked)
DEFECTS:
1. Not a Task 3 regression, found on the way: `HomeSectionRemovalFocusTest.removingFocusedFirstCardFocusesTheRightNeighborAndNotSearch` is red on the base helper. Reproduce: `make GRADLE=./tools/agentw DEVICE=emulator-5554 itest TESTS=com.kino.puber.ui.feature.home.component.HomeSectionRemovalFocusTest`. Not run on the base commit 0e4449e2 itself.
2. Round 1 observation (stale watched badge once in 18 returns) did not repeat in 4 of 4 returns; still unexplained.
UNAVAILABLE: none
MANUAL: Manual 3 on a real playback session (owner decides on the progress write); Bookmarks screen focus after Details/Back, which needs the Bookmarks tab enabled in Settings.


## Review fix round 1

HEAD 48ff9085 (Task 4: `FocusTargetState.kt` opt-in flag, `CollectionDetailVM` active-save ids, two VideoGrid guards).

CHECK: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` -> pass (BUILD SUCCESSFUL)
DEVICE: emulator-5554 (AVD puber_tv_36), lease released

ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | unit tests unchanged and green; device Manual 1 on 48ff9085: grid frame at 0.4 s (no spinner), 4 of 4 |
| AC-2 | PASS | device: same card `[662,144][1259,480]` after 4 of 4 (watched) and 4 of 4 (bookmark) returns; instrumented `...StaysOnTheFocusedCardWhenContentIsReplaced` 3 of 3 |
| AC-3 | PASS | unchanged from Round 1 (unit `firstLoad_publishesLoadingBeforeContent`, `retry_publishesLoadingAfterError`); not re-run on device |
| AC-4 | PASS | unchanged: unit `failedRefresh_keepsContentAndShowsMessage` |
| AC-5 | PASS | unchanged: unit `overlappingRefreshes_publishOnlyTheLatestFetch` |
| AC-6 | PASS | unit suite green incl. the strengthened test; device Manual 2: card menu matches Details after every return, 4 of 4 |
| AC-7 | PASS | `CollectionDetailScreenContentFocusTest` 5 of 5, 3 runs of 3 |
| AC-8 | PASS | unchanged: unit `emptyReturnedChanges_doNotRefetch`, `returnedChanges_areForwardedToParentOnBack`, `back_waitsForSaveAndForwardsBookmarkChange` |
| Task 4 guards red on a021e92c | PASS | helper plus `CollectionDetailScreenContent.kt` restored from a021e92c (the helper alone does not compile: the screen passes `holdPendingFocusTarget`): 7 of 9 pass, exactly `changedInitialFocusedItemIdWithChangedItemsRefocusesTheRetainedRow` and `removedCardInAnUntargetedRowDoesNotPinFocusAfterContentFocusReturns` fail; on HEAD 9 of 9 |
| Task 4 helper regression | PASS | `HomeFocusTraversalTest` 4/4, `HomeCollectionBackFocusTest` 2/2, `SectionRowFocusTraversalTest` 6/6, `ShowAllScreenContentFocusTest` 5/5, `CollectionsScreenContentFocusTest` 1/1; device Home Continue watching card 2 Details/Back 3 of 3, My bookmarks 1 of 1, Collections opener restored |
| `HomeSectionRemovalFocusTest` | FAIL, pre-existing | 3 of 4; `removingFocusedFirstCardFocusesTheRightNeighborAndNotSearch` fails as in Round 2 (run `home-section-removal-focus-test-red`) |
| Manual 3 (player return) | NOT CHECKED | playback writes watch progress, a toggle cannot undo it |
| Bookmarks screen focus | NOT CHECKED | no focus test; Bookmarks tab not enabled |

TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/collection-detail-inplace-refresh/docs/superpowers/evidence/2026-11-11-collection-detail-inplace-refresh-device.md (Review fix round 1 section appended; audit passed; not committed, as asked)
DEFECTS: none new. `HomeSectionRemovalFocusTest` failure is pre-existing. Round 1 stale watched badge not seen in 4 of 4.
UNAVAILABLE: none
MANUAL: Manual 3 on a real playback session (owner decides on the progress write); Bookmarks screen focus after Details/Back.
