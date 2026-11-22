LENS: tests  ROUND: 1  BASE: c506d0bd

### F1 [major] AC-7 has no device observation and no test
- Where: /home/user1/orca/workspaces/Puber/card-progress-watching-duration/docs/superpowers/evidence/card-progress-watching-duration/2026-11-22-card-progress-watching-duration-device.md:13-19
- Problem: The evidence file marks AC-7 `Status: NOT CHECKED`. The account has no movie in progress. Playing one past 5 min would change the owner's account, and the standing authorization does not cover that. The field names of `/v1/items/<id>` were not recorded either. The test design sends two risks to this check alone: R5 (a `videos[0].watching = {status 0, time 0}` Details read overrides a watched or in-progress row from the history walk) and R6 (`resolve()` prefers cached Details over a fresh local play). No unit test covers either. Spec Q4 (fix the `Item.watching` KDoc) also depends on the field names AC-7 was meant to record.
- Failure scenario: A real movie payload carries `videos[0].watching` with status 0 and time 0, newer than the history row. Opening Details then writes a not-watched row, and the card loses its bar or watched badge. No test and no observation would catch this before ship.
- Fix: Either get the owner's explicit authorization for one movie playback on the box and run Manual check 1-5, or have the owner decide at gate 2 to accept AC-7 as not observed. If the second, add a WSR unit test for R5: a history row stamped watched, then `recordFromServer` of a movie with `videos[0].watching = WatchingInfo(status = 0, time = 0)` at a later `now`, asserting the resulting row so the behaviour is at least pinned.

### F2 [minor] Two Details behaviour changes are not pinned by any DSM test
- Where: /home/user1/orca/workspaces/Puber/card-progress-watching-duration/app/src/main/java/com/kino/puber/ui/feature/details/model/DetailsScreenUIMapper.kt:371 and app/src/main/java/com/kino/puber/data/api/models/Models.kt:189-190
- Problem: AC-6 is guarded only by "DSM unchanged and green". The diff changes two things on Details that no DSM fixture reaches:
  - **Watched verdict:** `buildMovieResumeLine` calls `itemMapper.isItemWatched(item)`, which now also reads `videos[0].watched` / `videos[0].watching.status`. A movie whose only verdict sits on `videos[0]` now gets `video_details_resume_finished` where base showed the time left.
  - **Zero length:** `movieLength()` skips a `videos[0].duration` of 0 and falls back to `duration.total`. Base fell back only on null (readiness note 2).

  Both look intended, but DSM has no fixture with `Video(watched = 1)` and none with a 0 video duration. A regression on the Details line would stay green.
- Failure scenario: Someone later reverts `isMovieWatchedAnywhere` or the `takeIf { it > 0 }`. The card tests in VIM catch the first, but the Details resume line has no test for either.
- Fix: Add two DSM cases: (1) a movie with `videos = listOf(Video(id = 1, duration = 120, watched = 1, watching = WatchingInfo(time = 118)))` gives the finished line; (2) a movie with `Video(duration = 0, watching = WatchingInfo(time = 30))` and `duration = Duration(total = 120)` gives the time-left line based on 120.

The full check passed when I ran it at HEAD 92d557be: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`, exit 0, BUILD SUCCESSFUL. Gradle reused cached results, so the unit tests and detekt did not re-execute; the state matches qa's green run.

I checked every P0 row in the test design against the tests in the diff and the base code (`git show c506d0bd:`). Each P0 row has a test under its stated name. The ten `red` tests would fail on base, as the Implementation record's red run says. The two `guard` tests pass on base and are marked `guard`. No assertion was removed or weakened, nothing mocks the class under test, and time is the deterministic `now` var. AC-8 passes as shown above.

### Unproven
- /home/user1/orca/workspaces/Puber/card-progress-watching-duration/app/src/main/java/com/kino/puber/data/repository/WatchStateRepository.kt:509 The `seriesLike` guard on the gate and the series branch for `progressTime`/`progressDuration` have no test: no fixture is a series with `videos[0].watching` or with a series `watching`. This becomes real if a series payload with `videos` exists, or if a reader uses a series row's progressTime/progressDuration.
