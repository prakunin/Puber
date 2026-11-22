Round 1

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; tests re-run with --rerun: 2340 tests, 0 failed, 0 skipped; detekt and lint green)
DEVICE: 192.168.1.110:5555 (stage APK installed, sha256 of on-device base.apk equals built APK 94ca5259...)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | WatchStateRepositoryTest movieWithPlaybackProgress_isRecordedAsPartiallyWatched (documented shape) green in rerun; red-on-base not re-run by me |
| AC-2 | PASS | VideoItemUIMapperTest mapShortItem_progress_forPartiallyWatchedMovie; WSR resolve_readsAMoviesPositionAndLengthFromItsFirstVideo |
| AC-3 | PASS | VIM ...UsesTheVideoLength, ...FallsBackToTheTotalDuration, null_whenMovieHasNoLengthAnywhere; WSR recordFromServer_keepsAMoviesBarAfterADetailsReadWithoutALength |
| AC-4 | PASS | ItemWatchStateTest movie_isFinishedWhenItsFirstVideoWasWatched / ...StatusIsWatched; VIM mapShortItem_progress_null_whenMoviesVideoIsWatched; WSR recordFromServer_takesAMoviesWatchedVerdictFromItsFirstVideo |
| AC-5 | PASS | WSR resolve_fallsBackToTheIndexForAMovieWhoseVideoCarriesNoWatchFields; existing series and skip tests green |
| AC-6 | PASS | DetailsScreenUIMapperTest whole class green, no expectation changed |
| AC-7 | NOT CHECKED | The Home continue-watching row on the box holds only series (about 16 cards scanned); no movie with a bar exists. Producing one means playing a movie past 5 min, which writes progress to the owner's account; the standing authorization does not cover playback, so I did not. Field names of /v1/items/<id> not recorded. |
| AC-8 | PASS | check.full green, see CHECK |
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/card-progress-watching-duration/docs/superpowers/evidence/card-progress-watching-duration/2026-11-22-card-progress-watching-duration-device.md (audit passed; committed 92d557be; screenshots deleted since they showed the watching list)
DEFECTS: none
UNAVAILABLE: none (box online; lease acquired and released)
MANUAL: AC-7 needs the owner's go-ahead to play a movie. Either say so explicitly and I (or the conductor) run steps 1-5 of Manual check 1, or do it by hand on the box: install is already done (stage APK, head db292ad0); play a never-watched movie past 5 min, BACK to Home, note the card bar, open its Details and BACK, force-stop and relaunch com.kino.puber.stage, compare the bar; note whether /v1/items/<id> carries item-level watching/watched.
