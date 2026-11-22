# Test design: card-progress-watching-duration

Base c506d0bd. ACs: `brief.md#acceptance-criteria`; rule and helpers: `design-architect.md#decisions`. Test paths are under `app/src/test/kotlin/com/kino/puber/`. Levels per `.flow/roles/test-designer.md`. Perf track off.

## Risk profile

| # | Risk (source) | P | I | Score | Covered by |
|---|---|---|---|---|---|
| R1 | A Details read with an item-level position and no `watching.duration` overwrites the history length, card bar disappears (brief root cause, step 5) | 2 | 3 | 6 | `recordFromServer_keepsAMoviesBarAfterADetailsReadWithoutALength` |
| R2 | Documented shape (`videos[0].watching` only) never reaches the index or the card (brief root cause, last step) | 3 | 2 | 6 | AC-1, AC-2 rows |
| R3 | Widened gate makes a movie whose `videos[0]` carries no watch fields resolve to a synthetic "not watched" instead of the index (design: non-null gate) | 2 | 3 | 6 | `resolve_fallsBackToTheIndexForAMovieWhoseVideoCarriesNoWatchFields` |
| R4 | Finished movie shows a near-100% bar instead of the watched badge when only `videos[0]` carries the verdict (research risk 3) | 2 | 2 | 4 | AC-4 rows |
| R5 | `videos[0].watching = {status 0, time 0}` overrides a watched history row on a Details read (design risk 1) | 1 | 3 | 3 | AC-7 observation; the verdict half by `recordFromServer_takesAMoviesWatchedVerdictFromItsFirstVideo` |
| R6 | `resolve()` prefers a cached Details state over a fresh local play (design risk 2, brief risk 2) | 2 | 2 | 4 | AC-7 step 4 |
| R7 | Preferring `videos[0].watching` hides a newer item-level position (brief risk 1) | 1 | 2 | 2 | Accepted; AC-7 records which fields exist |
| R8 | Extended `isFullyWatched` changes catalogue hiding (design risk 3) | 1 | 2 | 2 | Accepted: list payloads carry no `videos`; existing `ItemWatchStateTest` series/movie cases stay green |
| R9 | Multi-part movie judged by `videos[0]` only (design risk 4) | 1 | 1 | 1 | Accepted |
| R10 | Rows already overwritten stay wrong until a replay (brief risk 4) | 2 | 1 | 2 | Accepted; brief open question 3 |
| R11 | Movie rule leaks into series rows (design: series rows unchanged) | 1 | 3 | 3 | Existing series tests (AC-5 guard rows) |

## Traceability

Files: WSR = `data/repository/WatchStateRepositoryTest.kt`, VIM = `core/ui/model/VideoItemUIMapperTest.kt`, IWS = `data/api/models/ItemWatchStateTest.kt`, DSM = `ui/feature/details/model/DetailsScreenUIMapperTest.kt`.

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 red | WSR `movieWithPlaybackProgress_isRecordedAsPartiallyWatched` (rewritten: `videos = listOf(Video(id = 1, duration = 120, watching = WatchingInfo(time = 30)))`, no item-level fields) | Row 30/120, not watched; fails if the gate ignores `videos[0]` or the length is read from `watching.duration` |
| AC-2 | unit | P0 red | VIM `mapShortItem_progress_forPartiallyWatchedMovie` (rewritten to the AC-1 shape, mapper without repository) | `progressPercent == 0.25f`; fails if the fallback keeps its own copy of the old rule |
| AC-2 | unit | P0 red | WSR `resolve_readsAMoviesPositionAndLengthFromItsFirstVideo` (empty index, AC-1 item; also through `VideoItemUIMapper(FakeResourceProvider(), watchStateRepository = repository)`.`mapShortItem`) | 0.25 through the repository path the cards use; fails if `resolve` and the fallback disagree |
| AC-3 | unit | P0 red | VIM `mapShortItem_progress_forMovieWithItemLevelPositionUsesTheVideoLength` (`watching = WatchingInfo(time = 30)`, `videos[0].duration = 120`) | 0.25; fails if `videos[0].duration` is not consulted |
| AC-3 | unit | P0 red | VIM `mapShortItem_progress_forMovieWithItemLevelPositionFallsBackToTheTotalDuration` (no `videos`, `duration = Duration(total = 120)`) | 0.25; fails if `duration.total` is dropped from the chain |
| AC-3 | unit | P0 guard | VIM `mapShortItem_progress_null_whenMovieHasNoLengthAnywhere` | null with position but no length; fails if a 0 length divides or a default length is invented |
| AC-3 | unit | P0 red | WSR `recordFromServer_keepsAMoviesBarAfterADetailsReadWithoutALength` (history row 30/120 via `recordFromHistory`; advance `now`; record an item with `watching = WatchingInfo(time = 30)`, `videos[0] = Video(id = 1, duration = 120)`) | Row keeps `progressDuration = 120`, `resolve` gives 0.25; fails on base, where the record writes 0 (R1) |
| AC-4 | unit | P0 red | IWS `movie_isFinishedWhenItsFirstVideoWasWatched` | `videos[0].watched = 1` gives true, `0` gives false; fails if the verdict ignores `videos[0].watched` |
| AC-4 | unit | P0 red | IWS `movie_isFinishedWhenItsFirstVideoStatusIsWatched` | `videos[0].watching.status = 1` gives true, `status = 0` gives false; fails if any `videos[0].watching` counts as watched |
| AC-4 | unit | P0 red | VIM `mapShortItem_progress_null_whenMoviesVideoIsWatched` (`Video(id = 1, duration = 120, watched = 1, watching = WatchingInfo(time = 118))`) | `isWatched == true`, `progressPercent == null` (rewrite of `mapShortItem_progress_null_whenMovieIsWatched`); fails on a 98% bar (R4) |
| AC-4 | unit | P1 red | WSR `recordFromServer_takesAMoviesWatchedVerdictFromItsFirstVideo` | Row `isFullyWatched = true`; fails if the index path reads a different verdict than the card |
| AC-5 | unit | P0 guard | WSR `resolve_fallsBackToTheIndexForAMovieWhoseVideoCarriesNoWatchFields` (index row 30/120; item `videos = listOf(Video(id = 1, duration = 120))`) | Returns the index row unchanged; fails if the widened gate fires on a bare `videos[0]` (R3) |
| AC-5 | unit | P0 guard | WSR `recordFromServer_skipsItemsWithoutAnyWatchFields`, `resolve_prefersTheItemsOwnFieldsOverTheIndex`, `episodeCountAloneIsNotTreatedAsWatchState`; VIM series progress and `isWatched` cases; IWS `series_*` | Existing, unchanged; fail if the movie rule reaches series or bare items (R11) |
| AC-6 | unit | P0 guard | DSM whole class, unchanged; in particular `a movie reads its first video's position before an item-level one`, `a movie whose video carries no position reads the item-level one`, `a movie with no position says not started with its length` | Details resume line identical after delegating to the shared helpers; fails if the `time <= 0` guard or the episode-length callers change |
| AC-7 | device | P0 | Manual check 1 | Card bar survives Home, a Details visit and a restart on the stage build |
| AC-8 | check | P0 | `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` in the worktree | Passes |

Helper changes the tests need: VIM `testItem` gets a `videos: List<Video>? = null` parameter; IWS `movie()` gets a `video: Video? = null` parameter. The fake DAO stays private to WSR, so repository-backed cases live there.

## Bugs

Failing-first test: WSR `movieWithPlaybackProgress_isRecordedAsPartiallyWatched` in the AC-1 shape. On base `Item.toWatchStateOrNull` (`WatchStateRepository.kt:499`, read via `git show c506d0bd:`) returns null when `watched`, `new` and `watching` are all null, so no row is written and `dao.rows.value.single()` throws. Written and run red before any production change, together with the other `red` rows.

## Manual checks

Device `192.168.1.110:5555`, package `com.kino.puber.stage`, worktree dev APK installed with `tools/android-apk-install-preserve`, lease held, every adb call with `-s 192.168.1.110:5555`. Keys: `input keyevent DPAD_* / DPAD_CENTER / BACK`.

| # | Step | Expected | Must not happen |
|---|---|---|---|
| 1 | Pick a movie never watched; start playback with DPAD_CENTER, let it run past 5 min, BACK out of the player | Player closes to Details | Crash |
| 2 | BACK to Home; D-pad to the movie's card (Continue watching or the row it sits in); screenshot | Card shows a progress bar at roughly the position left | No bar, a full bar, or a watched badge |
| 3 | DPAD_CENTER to open Details, wait for the resume line, BACK; screenshot the same card | Bar at the same position as step 2 | Bar gone or reset (R1), focus lost from the card |
| 4 | Force-stop and relaunch `com.kino.puber.stage`; D-pad to the card; screenshot | Bar at the same position | Bar gone (index overwritten) or older position (R6) |
| 5 | Record in the evidence whether `/v1/items/<id>` carries item-level `watching`/`watched` and `videos[0].watching`/`watched` | Field names only | Any response body, token or broad log in evidence; run `tools/mobile-evidence-audit.sh` on the evidence dir |

## perf track

N/A: track off.
