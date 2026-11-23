Round 1

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; tasks were up to date, so :app:testDevDebugUnitTest was re-run with --rerun: 2426 tests, 0 failures, 0 skipped; detekt and lint green)
DEVICE: 192.168.1.110:5555 (box, leased and released). Session expired mid-run, see UNAVAILABLE. Emulator not leased or used on purpose, but see DEFECTS 2.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | unit catchUp_readsPastTheMarkOverAnIndexSyncedWithinTheHour, ..sendsNothingOverAnIndexWithNoMark, ..doesNotTreatAnEmptyPageAsHavingCaughtUp, ..runsNothingWithoutTheSerialsList, ..keepsGoingWhenOnlyTheMoviesListFails all green in rerun; on box a cold start read history page 1 with perpage and stopped |
| AC-2 | PASS | unit catchUp_readsFromPageOneWhileAWalkIsParkedBetweenChunks, ..leavesTheWalkCursorAndThePruneAlone, aWalkResumedAfterACatchUpStillWalksFromItsBookmark green |
| AC-3 | PASS | unit catchUp_readsThirtyFivePages.., ..readsPastTheWalksPageBudget, ..writesNoHistoryAndKeepsTheMarkWhenAPageFails, recordCatchUpPage_writesTheRowsAndRaisesTheMarkTogether, instrumented WatchStateDaoTest (11/11) incl. raiseHistoryNewestSeenNeverLowersTheMark on the box |
| AC-4 | PASS (unit only) | unit catchUp_asksDetailsForASeriesThatLeftTheWatchingList, ..leavesASeriesStillInTheWatchingList.., ..skipsASeriesThatWasOnlyBrowsed, ..OncePerProcess, ..TenMostRecent.., ..carriesOnPastAFailedDetailsRequest, ItemDetailsRepository refreshRecordsTheFreshVerdictInTheWatchStateIndex; no on-device observation |
| AC-5 | PASS (unit) / start observed on device | MainVMWatchStateSyncTest (onStart_catchesUp.., onStart_doesNotCatchUpWhile.., resumed_.., tabSelected_x3), SI floor tests, PlayerVM backPressed_x2, PlayerInteractor requestWatchStateCatchUp_..; on the box the cold start requested a catch-up (page 1, perpage). Tab entry and player exit not observed on device |
| AC-6 | PASS | unit recordCatchUpPage_rowsCarryTheCurrentGeneration.., catchUp_throwsAwayWhatItWroteWhenTheSessionEndedMidRun, invalidate_letsTheNextCatchUpRunInsideTheFloor |
| AC-7 | PASS | unit catchUp_writesNoHistoryWhenNothingIsNewerThanTheMark, syncWindow_announcesNothingForACatchUpThatChangedNothing, syncWindow_publishesACatchUpsChangeWhileAWalksWindowIsStillOpen |
| AC-8 | PASS | box, Settings > Data > Rebuild index, perpage=50: history order pages 1-75 each entries=50 ordered=true (check includes the page boundaries); no ordered=false line anywhere in the buffer; the walk was then cut by the 401 below |
| AC-9 | NOT CHECKED | needs an account mutation on another client; not done by me |
| AC-10 | PASS | check.full green (above); MigrationTest 4/4 and WatchStateDaoTest 11/11 on the box |
Extra (Znieczulenie on the Series tab after the catch-up): NOT CHECKED. The box session's access token expired (history request 401 at 08:35 device time) and the refresh was refused (incorrect_client_credentials) because a worktree build has no client secret, so after that the app only shows the loading spinner. I never reached the Series tab before that, and no later screenshot is possible.
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/watch-index-catch-up/docs/superpowers/evidence/2026-11-23-watch-index-catch-up-device.md (audit passed; committed 888f3b6e)
DEFECTS:
1. None in the change. Environment effect: the box now cannot start com.kino.puber.stage with this worktree build (access token expired, refresh needs the client secret). Not caused by the catch-up code; it is the known limit in AGENTS.md Build And Worktrees. Stored tokens are still present (key names checked, values not read), so a credentialed build installed by the owner or conductor should recover the session. Not done by me: I did not use credentials or a main-checkout build.
2. Process deviation: make DEVICE=192.168.1.110:5555 itest ran on both the box and the host emulator (Gradle: "Running tests on devices: puber_tv_36(AVD) - 16, KM6 - 10"), although the private adb server was meant to see only the box. Tests were instrumented tests of the stage package only (the emulator had no lease from me; it was unlocked at the time). The emulator run showed one testRunFailed entry. The Makefile claim that itest sees only the given serial did not hold here and should be looked at.
UNAVAILABLE: Series-tab observation and AC-9 (session expired, see above). Auth snapshot: make auth-save found nothing to save (run-as refused on the previously installed non-debuggable build) before the install, so there is no fresh snapshot; an older /mnt/data/projects/PetProjets/Puber/.auth/com.kino.puber.stage.tar exists (2026-11-22).
MANUAL:
1. Install a build with the client secret on the box (owner/conductor action) or restore the session (make DEVICE=192.168.1.110:5555 auth-restore with the 2026-11-22 snapshot if tokens are gone), then open the Series tab with Hide watched on and note whether Znieczulenie is listed, without opening its Details.
2. AC-9: Series tab showing a series X, HOME; watch or mark X's remaining episodes on another client; resume, Movies then Series tab, wait up to 60 s; X must be gone without opening Details. Repeat with a cold start for a second series Y.

Round 2

CHECK: not re-run (green at 210f055e in review round 7, nothing changed in production code or tests)
DEVICE: 192.168.1.110:5555 (box, leased and released)
Rounds 1 results for AC-1, AC-3, AC-5, AC-6, AC-8, AC-10 carried (see Round 1 above); AC-8 re-observed (157 ordered=true lines, 0 ordered=false).
| AC | Status | Evidence |
|----|--------|----------|
| AC-2 | PASS (device + unit) | box: rebuild walk parked after p15, tab entry read p1 at 17:06:31, walk resumed at p16; unit tests carried |
| AC-4 | PASS (unit only) | no on-device signal without an account mutation |
| AC-7 | PASS (unit + device observation) | box: catch-ups read page 1 only, no deeper page, grid unchanged, floor respected |
| AC-9 | NOT CHECKED | needs a mutation on another client: owner |
EVIDENCE: /home/dev/orca/workspaces/Puber/watch-index-catch-up-2/docs/superpowers/evidence/2026-11-23-watch-index-catch-up-device.md
TESTS ADDED: none
DEFECTS: 1. tools/android-apk-install-preserve rejects apksigner 36.0.0 output ("Signer #1 certificate SHA-256 digest" has no colon; CERT_DIGEST regex wants "Signer #N: certificate"). Installs ran through a temporary SDK shim outside the repo. Not a defect of the change.
UNAVAILABLE: none
MANUAL: AC-4/AC-9: Series tab with Hide watched on, note series X; HOME; on another client watch or mark X's remaining episodes; resume, Movies then Series, wait up to 60 s: X must be gone without opening Details. Repeat on a cold start with a second series Y.
