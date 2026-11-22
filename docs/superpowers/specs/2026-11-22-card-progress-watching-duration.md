# Card progress readers take the movie length and position from the shared Details rule

Status: approved
Date: 2026-11-22
Slug: card-progress-watching-duration
Kind: bug
Size: M
Tracks: none

## Problem / Why

### Request

research.md: Request.

### Root cause

Status: **plausible**. The mechanism is confirmed from code; whether any real payload triggers it is not observed (no captured `/v1/items/<id>` body; research.md: External findings).

| Step | Where | Status |
|---|---|---|
| Movie readers take the length only from `watching.duration` and ignore `videos[0].watching`, `videos[0].duration`, `duration.total` | `data/repository/WatchStateRepository.kt:496-509`, duplicate in `core/ui/model/VideoItemUIMapper.kt:116-126` | confirmed (code) |
| `WatchingInfo.duration` defaults to 0 and is documented absent from item payloads | `data/api/models/Models.kt:548-553` | confirmed (code, kinoapi docs) |
| A movie with no usable length gets `progressPercent = null` | `WatchStateRepository.kt:418-430` | confirmed (code) |
| `resolve()` prefers the item's own state over the index | `WatchStateRepository.kt:227` | confirmed (code) |
| Each fresh Details emission records the item; `upsertFromServer` overwrites `progress_duration` unconditionally, so a 0 length replaces the history length | `data/repository/ItemDetailsRepository.kt:17-28`, `data/db/WatchStateDao.kt:66-83,132-169` | confirmed (code) |
| Item-level `watching`/`watched` present on a real movie payload | `Models.kt:89-94,150-161` KDocs claim it; kinoapi docs do not show it | unverified |
| When only `videos[0].watching` is sent (documented shape), `toWatchStateOrNull` returns null and the index is used: the card bar survives, but Details never adds movie progress to the index | `WatchStateRepository.kt:499` | confirmed (code) |

Series cards use episode counts and are unaffected (`WatchStateRepository.kt:421-424`).

### Risks (analyst)

| Risk | Guard |
|---|---|
| Preferring `videos[0].watching` hides a newer item-level position if both exist (research.md: Risks 1) | Same trade-off as the shipped Details fix; AC-7 records which fields exist |
| Movies now resolve from the Details payload instead of the index, so a fresh local write can lag behind cached Details (research.md: Risks 2) | Shared with `collection-watched-mark-lag`; AC-7 checks the bar after a play |
| Extending the watched verdict changes `Item.isFullyWatched()` (`Models.kt:163-166`) for every caller, including catalogue hiding | Spec lists the callers; AC-4, AC-5 |
| Index rows already overwritten with no length stay wrong until a replay (research.md: Risks 4) | Unverified; open question 3 |

## Acceptance criteria

- AC-1. Given a movie `Item` with `videos[0] = Video(duration = 120, watching = WatchingInfo(time = 30))` and no item-level `watching`/`watched`, When `recordFromServer` runs, Then the row holds `progressTime = 30`, `progressDuration = 120`, `isFullyWatched = false` (unit test, red on base).
- AC-2. Given the AC-1 item, When `mapShortItem` runs with and without a `WatchStateRepository`, Then `progressPercent` is 0.25 in both (unit test, red on base).
- AC-3. Given a movie with item-level `watching = WatchingInfo(time = 30)` (duration 0), When mapped or recorded, Then the length comes from `videos[0].duration`, else `duration.total`; with neither, `progressPercent` is null (unit test).
- AC-4. Given a movie whose only verdict is `videos[0].watched = 1` or `videos[0].watching.status = 1`, When mapped, Then `isWatched` is true and `progressPercent` is null (unit test).
- AC-5. Given a movie `Item` with no watch fields at item or video level, When `resolve` runs, Then the index row is returned unchanged; a series `Item` maps as on base (unit test, regression).
- AC-6. Given the existing `DetailsScreenUIMapper` tests, When the Details mapper calls the shared rule, Then they stay green with no expectation changed.
- AC-7. Given the stage build on the box and a movie started and left past 5 min, When its card is read on Home, its Details opened and closed with Back, and the app is restarted, Then the card bar is shown each time at the same position (box observation, D-pad driven; evidence records whether `/v1/items/<id>` carries item-level `watching`/`watched`, field names only, no body).
- AC-8. Given the full check, When `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` runs, Then it passes.

## Out of scope

In: research option A, with the watched verdict from `videos[0]` (open question 2). One shared movie rule (position `videos[0].watching ?: watching`; length `watching.duration > 0 ?: videos[0].duration ?: duration.total`; watched `videos[0].watched > 0` or status 1 on either `watching`) used by `toWatchStateOrNull`, `toLocalWatchState` and `DetailsScreenUIMapper.kt:386-405`. Tests at `WatchStateRepositoryTest.kt:596-615` and `VideoItemUIMapperTest.kt:111-135` move to the documented shape.
Out: research option B (DAO length guard, open question 3); the `resolve()` order (`collection-watched-mark-lag`).

## Design

Base c506d0bd. Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/`. ACs: `brief.md#acceptance-criteria`.

### Decisions

| Question | Decision | Why |
|---|---|---|
| Where the movie rule lives | Three public extensions in `data/api/models/Models.kt`, next to `Item.isFullyWatched()` (`:163`): `Item.moviePosition(): WatchingInfo?` = `videos[0].watching ?: watching`; `Item.movieLength(): Int?` = `videos[0].duration > 0 ?: duration.total > 0`; `WatchingInfo.lengthOr(mediaDuration: Int?): Int?` = `duration > 0 ?: mediaDuration > 0` | The data layer and the UI mappers both read it; API models are used directly by mapping (AGENTS.md), so the model file is the one place both reach |
| How the two card readers share it | `Item.toWatchStateOrNull()` (`data/repository/WatchStateRepository.kt:496`) becomes `internal`; `VideoItemUIMapper.toLocalWatchState` (`core/ui/model/VideoItemUIMapper.kt:116-127`) is deleted and `watchState` (`:111-113`) falls back to `item.toWatchStateOrNull()` | Removes the duplicate instead of fixing it twice; AC-2 then holds by construction with and without a repository |
| When a movie item counts as carrying watch fields | Non-null gate (`WatchStateRepository.kt:499`) adds, for non-series items only, `videos[0].watched` and `videos[0].watching` | Same "non-null means the server said something" rule the item-level fields already follow; AC-1 needs the documented shape to record, AC-5 keeps the bare item on the index |
| Movie row numbers | `progressTime = moviePosition()?.time`, `progressDuration = moviePosition()?.lengthOr(movieLength())`; series rows keep `watching?.time` / `watching?.duration` | AC-1, AC-3; series unchanged (AC-5) |
| Watched verdict (brief Q2) | Movie branch of `Item.isFullyWatched()` (`Models.kt:165-167`) adds `videos[0].watched > 0 \|\| videos[0].watching?.status == WATCHED_STATUS` | AC-4; one verdict serves cards, Details (`DetailsScreenUIMapper.kt:55,372` via `isItemWatched`) and catalogue hiding |
| Details mapper | `buildMovieResumeLine` (`ui/feature/details/model/DetailsScreenUIMapper.kt:368-369`) uses `item.moviePosition().timeLeft(item.movieLength())`; private `WatchingInfo.length` (`:402-405`) keeps its `time <= 0` guard and delegates the rest to `lengthOr` | AC-6; the episode callers (`:140`, `:350`) keep passing `episode.duration` |

### Changes by layer

| Layer | Change |
|---|---|
| API / repository | `Models.kt`: the three helpers above; movie branch of `isFullyWatched` (`:163-177`) extended; KDoc of `isFullyWatched` (`:150-162`) names `videos[0]` as the third place. `WatchStateRepository.kt:496-510`: `internal`, widened gate, movie fields from the helpers |
| ViewModel state and actions | N/A: no VM or state type changes |
| Screen and DI module | N/A: no wiring changes |
| Compose content | N/A: mappers only. `VideoItemUIMapper.kt:111-127` (fallback replaced, imports of `isFullyWatched`/`isSeriesLike` kept only if still used); `DetailsScreenUIMapper.kt:367-405` |
| Resources | N/A |

Tests (QA owns the final list in test design):

| File | Change | AC |
|---|---|---|
| `app/src/test/kotlin/com/kino/puber/data/repository/WatchStateRepositoryTest.kt:595-615` | `movieWithPlaybackProgress_isRecordedAsPartiallyWatched` moves to `videos[0] = Video(duration = 120, watching = WatchingInfo(time = 30))`, no item-level fields; add item-level-position-with-`duration.total` case and a bare-movie `resolve` case | AC-1, AC-3, AC-5 |
| `app/src/test/kotlin/com/kino/puber/core/ui/model/VideoItemUIMapperTest.kt:110-134` | Partial and watched movie cases move to the documented shape; add the same item through a mapper built with a `WatchStateRepository` | AC-2, AC-3, AC-4 |
| `app/src/test/kotlin/com/kino/puber/data/api/models/ItemWatchStateTest.kt` | Add movie verdict from `videos[0].watched = 1` and from `videos[0].watching.status = 1` | AC-4 |
| `app/src/test/kotlin/com/kino/puber/ui/feature/details/model/DetailsScreenUIMapperTest.kt` | No change | AC-6 |

### Alternatives rejected

| Alternative | Reason |
|---|---|
| Keep `toLocalWatchState` and point both copies at the helpers | Two builders of one rule drift again; the duplicate is the defect's origin |
| Helpers in `WatchStateRepository.kt` | `DetailsScreenUIMapper` would import repository-file helpers for a model rule |
| DAO length guard (option B) | Out of scope (`brief.md#scope`) |
| Gate video fields on "meaningful" values (time > 0, status != 0) | An un-mark made elsewhere would never reach the index through Details; diverges from the item-level gate |

### Risks

| # | Risk | Lives in |
|---|---|---|
| 1 | Opening Details of a movie whose payload carries `videos[0].watching = {status 0, time 0}` now writes a not-watched row, overriding a watched or in-progress index row from the history walk whenever the Details fetch is newer; correct if the server is right, a regression if `videos[0]` lags the history | `WatchStateRepository.kt:255-260`, `data/db/WatchStateDao.kt:132-169`, `data/repository/ItemDetailsRepository.kt:26-27` |
| 2 | `resolve()` now returns the Details payload state for movies instead of the index, so a cached Details value can show an older bar or verdict than a fresh local play or toggle (shared with `collection-watched-mark-lag`) | `WatchStateRepository.kt:227` |
| 3 | The extended verdict changes `isFullyWatched` for every caller; catalogue hiding (`domain/interactor/contentlist/ContentListInteractor.kt:177`) only changes for items that carry `videos`, which list payloads do not | `Models.kt:163-177` |
| 4 | A movie with several videos (parts) is judged by `videos[0]` only, as the Details fix already does | `Models.kt` helpers |
| 5 | Opening an untouched movie's Details now inserts a row and ticks the snapshot once; the no-op skip clause (`WatchStateDao.kt:146-155`) keeps repeat opens silent | `WatchStateDao.kt:66-83` |


## Test design

Base c506d0bd. ACs: `brief.md#acceptance-criteria`; rule and helpers: `design-architect.md#decisions`. Test paths are under `app/src/test/kotlin/com/kino/puber/`. Levels per `.flow/roles/test-designer.md`. Perf track off.

### Risk profile

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

### Traceability

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

### Bugs

Failing-first test: WSR `movieWithPlaybackProgress_isRecordedAsPartiallyWatched` in the AC-1 shape. On base `Item.toWatchStateOrNull` (`WatchStateRepository.kt:499`, read via `git show c506d0bd:`) returns null when `watched`, `new` and `watching` are all null, so no row is written and `dao.rows.value.single()` throws. Written and run red before any production change, together with the other `red` rows.

### Manual checks

Device `192.168.1.110:5555`, package `com.kino.puber.stage`, worktree dev APK installed with `tools/android-apk-install-preserve`, lease held, every adb call with `-s 192.168.1.110:5555`. Keys: `input keyevent DPAD_* / DPAD_CENTER / BACK`.

| # | Step | Expected | Must not happen |
|---|---|---|---|
| 1 | Pick a movie never watched; start playback with DPAD_CENTER, let it run past 5 min, BACK out of the player | Player closes to Details | Crash |
| 2 | BACK to Home; D-pad to the movie's card (Continue watching or the row it sits in); screenshot | Card shows a progress bar at roughly the position left | No bar, a full bar, or a watched badge |
| 3 | DPAD_CENTER to open Details, wait for the resume line, BACK; screenshot the same card | Bar at the same position as step 2 | Bar gone or reset (R1), focus lost from the card |
| 4 | Force-stop and relaunch `com.kino.puber.stage`; D-pad to the card; screenshot | Bar at the same position | Bar gone (index overwritten) or older position (R6) |
| 5 | Record in the evidence whether `/v1/items/<id>` carries item-level `watching`/`watched` and `videos[0].watching`/`watched` | Field names only | Any response body, token or broad log in evidence; run `tools/mobile-evidence-audit.sh` on the evidence dir |

### perf track

N/A: track off.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Fix without capturing the real /v1/items/<id> payload first? | no | answered | Yes; AC-7 records the field names |
| Q2 | Read the movie watched verdict from videos[0] too? | yes | answered | Yes, in the same shared rule (AC-4) |
| Q3 | Add the DAO length guard (option B) now? | no | answered | No; idea run if AC-7 shows a lost length |
| Q4 | Correct the Item.watching KDoc (Models.kt:89-94) once AC-7 records the real field names? | no | answered | Yes, in the verify fix-up, if AC-7 shows no item-level field |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-22 | Q1-Q4 answered on the recommended answers | owner (standing instruction: recommended) |
| 2026-11-22 | codex: required on every round; caps lifted; device evidence on the TV box 192.168.1.110:5555 only | owner |
| 2026-11-22 | Gate 1: approved (size M, tracks none) | owner (standing instruction: recommended) |
