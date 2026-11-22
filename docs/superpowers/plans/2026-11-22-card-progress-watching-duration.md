# Card progress readers take the movie length and position from the shared Details rule - plan

**Spec:** `docs/superpowers/specs/2026-11-22-card-progress-watching-duration.md`
**Goal:** One movie rule (position, length, watched verdict) in `Models.kt`, used by the card readers, the watch-state index writer and the Details resume line.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`; full gate: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`. Run both inside the worktree; never `./gradlew` there. Prefix `AGENT_GRADLE_SERIAL=1` when another worktree may build.
- Touched tests: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests '<fully.qualified.Class>'`.
- One task = green fast gate + green tests of the touched code + one commit, message per commit_style (one imperative English sentence, no prefixes), never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- Out of scope: the DAO length guard (`data/db/WatchStateDao.kt`), the `resolve()` order (`WatchStateRepository.kt:227`), the `Item.watching` KDoc (`Models.kt:89-93`, verify fix-up per spec Q4).
- Detekt limits that the new code must fit: `ComplexCondition` allows 3 conditions (`config/detekt/detekt.yml:81-83`), `ReturnCount` max 3 (`config/detekt/detekt.yml:756-759`). Split long boolean chains into named `val`s or private helpers.
- Paths: production under `app/src/main/java/com/kino/puber/`, tests under `app/src/test/kotlin/com/kino/puber/`. Abbreviations: WSR = `data/repository/WatchStateRepositoryTest.kt`, VIM = `core/ui/model/VideoItemUIMapperTest.kt`, IWS = `data/api/models/ItemWatchStateTest.kt`, DSM = `ui/feature/details/model/DetailsScreenUIMapperTest.kt`.

## Shared rule (produced by Task 1)

Public top-level functions in `data/api/models/Models.kt`, placed after `Item.isFullyWatched()` (`:163-177`):

| Signature | Result |
|---|---|
| `fun Item.moviePosition(): WatchingInfo?` | `videos?.firstOrNull()?.watching ?: watching` |
| `fun Item.movieLength(): Int?` | `videos?.firstOrNull()?.duration` if > 0, else `duration?.total` if > 0, else null |
| `fun WatchingInfo.lengthOr(mediaDuration: Int?): Int?` | `duration` if > 0, else `mediaDuration` if > 0, else null |

Movie branch of `Item.isFullyWatched()` (`Models.kt:165-167`): true when `watched > 0`, or `watching?.status == WATCHED_STATUS` (`:143`), or `videos[0].watched > 0`, or `videos[0].watching?.status == WATCHED_STATUS`. Series branch unchanged.

---

## Tasks

### Task 1: Move movie card readers and the index writer onto the shared rule

**Covers:** AC-1, AC-2, AC-3, AC-4, AC-5
**Files:**
- Modify: `data/api/models/Models.kt` - add the three helpers above; extend the movie branch of `Item.isFullyWatched()` (`:163-177`, constant `WATCHED_STATUS` `:143`, `Video.watched`/`Video.watching` `:266-267`, `Video.duration` `:262`, `Duration.total` `:194`); in its KDoc (`:150-162`) name `videos[0]` (`watched` or `watching.status`) as the third place a movie's flag can sit.
- Modify: `data/repository/WatchStateRepository.kt` - `Item.toWatchStateOrNull()` (`:496-510`): `private` -> `internal`; gate (`:499`) also passes, for non-series items only, when `videos[0].watched` or `videos[0].watching` is non-null (keep the `total` comment `:497-498`); movie rows take `progressTime = moviePosition()?.time` and `progressDuration = moviePosition()?.lengthOr(movieLength())`; series rows keep `watching?.time` / `watching?.duration`. `resolve` (`:227`) and `recordFromServer` (`:255-260`) stay as they are.
- Modify: `core/ui/model/VideoItemUIMapper.kt` - delete `toLocalWatchState` (`:115-127`); `watchState` (`:111-113`) returns `watchStateRepository?.resolve(item) ?: item.toWatchStateOrNull()` (import `com.kino.puber.data.repository.toWatchStateOrNull`); drop the now unused `isFullyWatched` import (`:10`); `isSeriesLike` (`:11`) stays (used at `:46`).
- Test: IWS, VIM, WSR (table below).

**Consumes:** `WatchState.progressPercent` (`WatchStateRepository.kt:418-431`), `recordFromHistory` (`:289-301`), WSR helpers `repository(...)` (`WSR:51-61`), `eagerScope()` (`:67-68`), `historyEntry(...)` (`:678-694`), `entity(...)` (`:696-701`), `var now` (`:39`), `FakeWatchStateDao.rows` (`:710`); VIM `testItem(...)` (`VIM:286-326`); IWS `movie(...)` (`IWS:45-51`); `com.kino.puber.util.FakeResourceProvider`.
**Produces:** the shared rule above; `internal fun Item.toWatchStateOrNull(): WatchState?`.

Test helper changes: VIM `testItem` gets `videos: List<Video>? = null` passed to `Item(videos = ...)`; IWS `movie()` gets `video: Video? = null`, passed as `videos = video?.let(::listOf)`.

Tests (names and fixtures from the spec's Test design, Traceability):

| File | Test | New / rewritten | Fixture and assertion | AC | Priority |
|---|---|---|---|---|---|
| WSR | `movieWithPlaybackProgress_isRecordedAsPartiallyWatched` (`:595-615`) | rewritten | Item id 3, MOVIE, no item-level `watched`/`watching`, `videos = listOf(Video(id = 1, duration = 120, watching = WatchingInfo(time = 30)))`; row not fully watched, 30 / 120 | AC-1 | P0 red |
| VIM | `mapShortItem_progress_forPartiallyWatchedMovie` (`:110-118`) | rewritten | Same shape via `testItem(videos = ...)`, mapper without repository; `progressPercent == 0.25f` | AC-2 | P0 red |
| WSR | `resolve_readsAMoviesPositionAndLengthFromItsFirstVideo` | new | Empty index, AC-1 item; `repository.resolve(item)?.progressPercent == 0.25f` and `VideoItemUIMapper(FakeResourceProvider(), watchStateRepository = repository).mapShortItem(item).progressPercent == 0.25f` | AC-2 | P0 red |
| VIM | `mapShortItem_progress_forMovieWithItemLevelPositionUsesTheVideoLength` | new | `watching = WatchingInfo(time = 30)`, `videos = listOf(Video(id = 1, duration = 120))`; 0.25 | AC-3 | P0 red |
| VIM | `mapShortItem_progress_forMovieWithItemLevelPositionFallsBackToTheTotalDuration` | new | `watching = WatchingInfo(time = 30)`, no `videos`, `duration = Duration(total = 120)`; 0.25 | AC-3 | P0 red |
| VIM | `mapShortItem_progress_null_whenMovieHasNoLengthAnywhere` | new | `watching = WatchingInfo(time = 30)`, no `videos`, no `duration`; null | AC-3 | P0 guard |
| WSR | `recordFromServer_keepsAMoviesBarAfterADetailsReadWithoutALength` | new | `recordFromHistory(listOf(historyEntry(itemId = 5, type = MOVIE, time = 30, duration = 120)), emptySet())`; set `now = 100`; `recordFromServer` of Item id 5, MOVIE, `watching = WatchingInfo(time = 30)`, `videos = listOf(Video(id = 1, duration = 120))`; row `progressDuration == 120`, `resolve(item)?.progressPercent == 0.25f` | AC-3 | P0 red |
| IWS | `movie_isFinishedWhenItsFirstVideoWasWatched` | new | `movie(watched = null, video = Video(id = 1, watched = 1))` true; `watched = 0` false | AC-4 | P0 red |
| IWS | `movie_isFinishedWhenItsFirstVideoStatusIsWatched` | new | `movie(watched = null, video = Video(id = 1, watching = WatchingInfo(status = WATCHED_STATUS)))` true; `status = 0` false | AC-4 | P0 red |
| VIM | `mapShortItem_progress_null_whenMoviesVideoIsWatched` | rewrite of `mapShortItem_progress_null_whenMovieIsWatched` (`:126-134`) | No item-level fields, `videos = listOf(Video(id = 1, duration = 120, watched = 1, watching = WatchingInfo(time = 118)))`; `isWatched == true`, `progressPercent == null` | AC-4 | P0 red |
| WSR | `recordFromServer_takesAMoviesWatchedVerdictFromItsFirstVideo` | new | Item MOVIE, no item-level fields, `videos = listOf(Video(id = 1, duration = 120, watched = 1))`; single row `isFullyWatched == true` | AC-4 | P1 red |
| WSR | `resolve_fallsBackToTheIndexForAMovieWhoseVideoCarriesNoWatchFields` | new | `dao.rows.value = listOf(entity(1, false).copy(isSeriesLike = false, progressTime = 30, progressDuration = 120))`, `testScheduler.advanceUntilIdle()`; `resolve(Item(id = 1, type = MOVIE, videos = listOf(Video(id = 1, duration = 120))))` equals `repository.get(1)`, percent 0.25 | AC-5 | P0 guard |

Existing guards that must stay green unchanged (AC-5): WSR `recordFromServer_skipsItemsWithoutAnyWatchFields` (`:70-82`), `resolve_prefersTheItemsOwnFieldsOverTheIndex` (`:225-238`), `episodeCountAloneIsNotTreatedAsWatchState` (`:360`); VIM series progress and `isWatched` cases (`:23-108`); IWS `series_*` (`:26-43`).

- [x] Change the test helpers, write the twelve tests above, and run WSR, VIM, IWS on the unchanged production code: the ten `red` rows fail, the two `guard` rows pass. Record the run in the Implementation record.
- [x] Add the shared rule and the extended verdict to `Models.kt`.
- [x] Change `toWatchStateOrNull` and `VideoItemUIMapper` as listed.
- [x] Run WSR, VIM, IWS, DSM and the fast gate; commit.

**Done when:** `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.data.repository.WatchStateRepositoryTest' --tests 'com.kino.puber.core.ui.model.VideoItemUIMapperTest' --tests 'com.kino.puber.data.api.models.ItemWatchStateTest' --tests 'com.kino.puber.ui.feature.details.model.DetailsScreenUIMapperTest'` passes, the fast gate passes, and `grep -rn toLocalWatchState app/src` finds nothing.

---

### Task 2: Make the Details resume line call the shared rule

**Covers:** AC-6, AC-8
**Files:**
- Modify: `ui/feature/details/model/DetailsScreenUIMapper.kt` - `buildMovieResumeLine` (`:367-383`): replace `:368-369` with `val left = item.moviePosition().timeLeft(item.movieLength())`; private `WatchingInfo.length` (`:402-405`) keeps its `time <= 0` guard and returns `lengthOr(mediaDuration)`. `timeLeft` (`:389-393`), `progressFraction` (`:396-400`) and the episode callers (`:140`, `:350`) are unchanged. Import `moviePosition`, `movieLength`, `lengthOr` from `com.kino.puber.data.api.models`; the `Video` import (`:16`) stays (used at `:456`).
- Test: DSM, no change.

**Consumes:** the shared rule from Task 1.
**Produces:** nothing new.

- [x] Edit the mapper as listed.
- [x] Run DSM unchanged, in particular `a movie reads its first video's position before an item-level one` (`DSM:336`), `a movie whose video carries no position reads the item-level one` (`DSM:345`), `a movie with no position says not started with its length` (`DSM:359`).
- [x] Run the full gate (AC-8); commit.

**Done when:** `git diff --stat HEAD~1 -- app/src/test` is empty, DSM passes, and `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` passes in the worktree.

AC-7 (box observation, spec Test design Manual checks) belongs to verify, not to a plan task.

---

## Implementation record

<!-- owner: implementer -->

**Commits:**
- e6e14d44 Read a movie's card progress and watched verdict from the shared first-video rule (Task 1)
- Task 2: see `git log` (Details resume line on the shared rule)

**Red run** (P0 `red` tests on the unchanged code; 10 red, 2 guard):
- red: WSR movieWithPlaybackProgress_isRecordedAsPartiallyWatched - assertion failure (no row recorded)
- red: WSR resolve_readsAMoviesPositionAndLengthFromItsFirstVideo - AssertionFailedError, percent null instead of 0.25
- red: WSR recordFromServer_keepsAMoviesBarAfterADetailsReadWithoutALength - AssertionFailedError, progressDuration 0 instead of 120
- red: WSR recordFromServer_takesAMoviesWatchedVerdictFromItsFirstVideo - fails, no row
- red: VIM mapShortItem_progress_forPartiallyWatchedMovie, ...forMovieWithItemLevelPositionUsesTheVideoLength, ...FallsBackToTheTotalDuration, mapShortItem_progress_null_whenMoviesVideoIsWatched - AssertionFailedError
- red: IWS movie_isFinishedWhenItsFirstVideoWasWatched, movie_isFinishedWhenItsFirstVideoStatusIsWatched - AssertionFailedError
- guard (green on base, as designed): VIM mapShortItem_progress_null_whenMovieHasNoLengthAnywhere, WSR resolve_fallsBackToTheIndexForAMovieWhoseVideoCarriesNoWatchFields

**Review round 1 (tests only, no production change):** WSR `recordFromServer_aNotWatchedFirstVideoReplacesAWatchedHistoryRow` pins R5 (a later Details read with `videos[0].watching = {status 0, time 0}` replaces a watched history row: not watched, time 0, length 120); DSM `a movie whose first video is watched shows the finished line` and `a movie whose first video has zero length takes the catalogue total for the time left` pin the two Details changes. All three pass on the current code, so they guard, not red. AC-7 stays NOT CHECKED for the owner at gate 2.

**Deviations from the plan:** none.

**Files touched outside the plan:** none.

**For the reviewers:** `toWatchStateOrNull` gate for movies (videos[0] watched/watching); `HomeInteractor.kt:165` filter (readiness note 1); `movieLength()` skips a 0 `videos[0].duration` where base Details fell back only on null (readiness note 2).

**Blocked:** none.

## Readiness

**Verdict: READY**

Base c506d0bd; worktree HEAD 7a768c24 (spec only, production code equals base). Plan read uncommitted from the worktree.

### AC coverage

| AC | Plan task | Test design row |
|---|---|---|
| AC-1 | Task 1 | WSR `movieWithPlaybackProgress_isRecordedAsPartiallyWatched` (P0 red) |
| AC-2 | Task 1 | VIM `mapShortItem_progress_forPartiallyWatchedMovie`, WSR `resolve_readsAMoviesPositionAndLengthFromItsFirstVideo` (P0 red) |
| AC-3 | Task 1 | three VIM rows, WSR `recordFromServer_keepsAMoviesBarAfterADetailsReadWithoutALength` |
| AC-4 | Task 1 | IWS two rows, VIM `mapShortItem_progress_null_whenMoviesVideoIsWatched`, WSR P1 row |
| AC-5 | Task 1 | WSR `resolve_fallsBackToTheIndexForAMovieWhoseVideoCarriesNoWatchFields` plus existing guards |
| AC-6 | Task 2 (and DSM run in Task 1) | DSM whole class unchanged (P0 guard) |
| AC-7 | verify (plan, end of Task 2) | Manual check 1-5, box `192.168.1.110:5555` |
| AC-8 | Task 2 | full gate |

### Citations sampled

| Citation | Resolves to | Result |
|---|---|---|
| `WatchStateRepository.kt:496-510` | `private fun Item.toWatchStateOrNull()`, gate at `:499` | ok |
| `WatchStateRepository.kt:227` | `fun resolve(item)` | ok |
| `WatchStateRepository.kt:418-431` | `progressPercent` getter | ok |
| `WatchStateRepository.kt:289-301` | `recordFromHistory` | ok |
| `Models.kt:143`, `:163-177` | `WATCHED_STATUS`, `Item.isFullyWatched()` | ok |
| `Models.kt:548-553` | `WatchingInfo`, `duration` default 0 with KDoc | ok |
| `VideoItemUIMapper.kt:111-113`, `:116-127` | `watchState`, `toLocalWatchState` | ok |
| `DetailsScreenUIMapper.kt:367-383`, `:402-405` | `buildMovieResumeLine`, private `WatchingInfo.length` | ok |
| `WatchStateDao.kt:132-169` | `upsertFromServer` UPDATE, overwrites `progress_duration` | ok |
| `detekt.yml:81-83`, `:756-759` | `ComplexCondition` 3, `ReturnCount` 3 | ok |
| WSR `:39`, `:595-615`, `:678-694`; DSM `:336`, `:345`, `:359` | `var now = 42L`, the rewritten test, `historyEntry`, the three named DSM tests | ok |
| `Models.kt:262`, `:266-267` (plan Task 1) | `Video.duration` is `:261`; `watched`/`watching` are `:265-266` | off by one, symbol unambiguous |

### Build and order

Task 1 adds the helpers, makes `toWatchStateOrNull` `internal` and deletes `toLocalWatchState` in one commit; the red tests use only test-helper parameters and existing production APIs (IWS has its own `WATCHED_STATUS` at `IWS:7`), so they compile on base. Task 2 consumes only Task 1's helpers.

Red claims checked against base code: AC-1/AC-2/AC-4 items carry no item-level fields, so the base gate returns null; the R1 test writes at `now = 100` over a history row stamped 42, and the fake DAO (`WSR:770-783`) overwrites as the SQL does, so base stores length 0. The two guards pass on base.

### Test design P0 rows

Every automated P0 row names its test in backticks, is marked `red` or `guard`, and names the defect it catches. AC-7 (device) and AC-8 (check) are P0 without a red/guard mark; neither is an automated unit test.

### Scope and questions

Plan stays out of the DAO guard and the `resolve()` order. Q2 (blocking) answered; Q1, Q3, Q4 answered.

### Notes for the implementer (non-blocking)

| # | Note |
|---|---|
| 1 | `HomeInteractor.kt:165` also filters on `watchStateRepository::isFullyWatched`; spec risk 3 names only `ContentListInteractor.kt:177`. Same reasoning applies (list payloads carry no `videos`); reviewers should confirm. |
| 2 | Base Details uses `video?.duration ?: item.duration?.total` (null-only fallback); `movieLength()` skips a 0 `videos[0].duration`. DSM fixtures do not hit a 0 length, so AC-6 does not see the difference. |

