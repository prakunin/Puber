# Research: card-progress-watching-duration

## Request

"Card progress readers (Item.toWatchStateOrNull, VideoItemUIMapper.toLocalWatchState, WatchStateRepository) take the length from watching.duration, which KinoPub item payloads leave out; check whether cards show wrong/no progress because of it and what the Details fix in resume-progress-anomaly did." Deferred from `resume-progress-anomaly` (`design-architect-2.md`, Open question 1, Risk 3).

## Understanding

Only movie cards are at risk, and only if an item payload carries item-level watch fields. Series cards take progress from episode counts (`watched/total`), not from a length. A movie card normally reads the local index, and the index gets its length from the history walk, which fills `watching.duration`. The gap shows up only when a movie `Item` arrives with `watched` or `watching` set at item level but no `watching.duration`. Then `toWatchStateOrNull` builds a state with no usable length. `resolve()` prefers that state over the index, and opening Details writes it into the index, so the card bar disappears. No document says which endpoint sends item-level fields, and no captured payload exists, so whether cards actually lose progress is **unverified**. The Details fix in `resume-progress-anomaly` (`3c9b8a89`) changed only the Details mapper: it now falls back to the media's own length and reads the movie position from `videos[0].watching`. It did not touch the card readers or the index.

## Current state in code

Paths are under `app/src/main/java/com/kino/puber/`.

| Where | What it does now |
|---|---|
| `data/api/models/Models.kt:89-94` | `Item.watching` KDoc claims movies carry it on "watching endpoints, item details"; kinoapi docs do not show it (see External findings) |
| `data/api/models/Models.kt:150-166` | `Item.isFullyWatched()` for a movie: `watched > 0 \|\| watching.status == WATCHED`; KDoc says endpoints disagree on where they put the flag, which suggests item-level fields were seen on some endpoint (`unverified`) |
| `data/api/models/Models.kt:548-553` | `WatchingInfo.duration` defaults to 0; KDoc added by `3c9b8a89`: absent from item payloads |
| `data/repository/WatchStateRepository.kt:496-509` | `Item.toWatchStateOrNull()`: returns null only if `watched`, `new` and `watching` are all null; `progressDuration = watching?.duration` (0 when absent); ignores `videos[0].watching` and `videos[0].duration` |
| `data/repository/WatchStateRepository.kt:227` | `resolve(item) = item.toWatchStateOrNull() ?: get(item.id)`: item fields win over the index |
| `data/repository/WatchStateRepository.kt:255-259` | `recordFromServer` writes `toWatchStateOrNull()` rows through `dao.upsertAllFromServer` |
| `data/repository/WatchStateRepository.kt:418-430` | `WatchState.progressPercent`: movie needs `progressDuration > 0`, else null; series uses `watchedEpisodes/totalEpisodes` |
| `data/repository/WatchStateRepository.kt:455-467` | History rows: `progressDuration = media.watching?.duration`, filled by `data/api/history/HistoryPageResponse.kt:45-56` from `media.duration` |
| `data/db/WatchStateDao.kt:67-83, 132-169` | `upsertFromServer` overwrites `progress_time` and `progress_duration` unconditionally (no COALESCE) when `updatedAt` is newer, so a 0 or null length from an item replaces the history length |
| `data/db/WatchStateDao.kt:106-120` | `upsertFromHistory` replaces only when `historySeenAt` is not older; an incremental walk does not revisit an old entry, so an overwritten row stays until the movie is played again (`unverified` at runtime) |
| `data/repository/ItemDetailsRepository.kt:17-28` | Each non-stale Details emission calls `recordFromServer(listOf(item))`: the path that can write a movie row with no length |
| `domain/interactor/watchstate/WatchStateSyncInteractor.kt:219-221` | Sync calls `recordFromServer(series)` (counts only, unaffected) and `recordInProgress(movies)` (keeps the stored numbers) |
| `core/ui/model/VideoItemUIMapper.kt:44, 107-126` | Card `progressPercent = watchProgress(item)` -> `resolve(item) ?: toLocalWatchState()`; `toLocalWatchState` duplicates `toWatchStateOrNull` (same `watching?.duration` read), used only without a repository (previews, tests) |
| `ui/feature/details/model/DetailsScreenUIMapper.kt:350, 369, 386-405` | The `resume-progress-anomaly` fix: private `WatchingInfo?.timeLeft/progressFraction/length(mediaDuration)`, length = `watching.duration > 0 ?: media duration`; movie position `videos[0].watching ?: item.watching` |
| `ui/feature/history/model/HistoryUIMapper.kt:23-30` | History cards read `watching.duration` from the history adapter, which fills it; unaffected |

Tests that assume item payloads carry the length, and so pass on a shape the docs do not show:

| Test | Line |
|---|---|
| `app/src/test/kotlin/com/kino/puber/data/repository/WatchStateRepositoryTest.kt` `movieWithPlaybackProgress_isRecordedAsPartiallyWatched` | `:596-615` |
| `app/src/test/kotlin/com/kino/puber/core/ui/model/VideoItemUIMapperTest.kt` `mapShortItem_progress_forPartiallyWatchedMovie`, `..._whenMovieIsWatched` | `:111-135` |

No test covers a movie `Item` with `videos[0].watching` and no item-level `watching`, or the overwrite of a history length by a Details record.

## Related work

| Run / commit | Relation |
|---|---|
| `resume-progress-anomaly` (done, merged `ad0c0af0`) | Source. `design-architect-2.md` root cause and the Q4 deferral; Details fix in `3c9b8a89`; spec `docs/superpowers/specs/2026-11-22-resume-progress-anomaly.md` |
| `collection-watched-mark-lag` (idea) | Same mechanism from the other side: `resolve()` lets server item fields win over the local record (`WatchStateRepository.kt:227`). A fix to the `resolve` order touches both runs |
| `cache-layer-rework` (idea) | May reshape the details cache that feeds `recordFromServer`; no overlap in the mappers |
| `0192929e` | Details redesign that introduced `timeLeft` (per `design-architect-2.md`) |
| `03c2ca31` (Release 1.7.1) | Introduced `resolve()`, `toWatchStateOrNull`'s `watching?.duration` read and the `Item.watching` KDoc |

Duplicate: none.

## External findings

| Finding | Source |
|---|---|
| `GET /v1/items/<id>`: `videos[].watching` is `{status, time}` only; the length is `videos[].duration`; episodes show `duration` and no `watching` in the example; no item-level `watching` or `watched` is documented | https://kinoapi.com/api_video.html, read 2026-11-22 |
| `GET /v1/items` (lists): no `watching` or `watched` fields | same page |
| `GET /v1/watching?id=`: per video/episode `duration`, `time`, `status`, flat (not nested in `watching`); `/v1/watching/movies` returns only id/type/title/posters; `/v1/watching/serials` adds `total`, `watched`, `new` | https://kinoapi.com/api_watching.html, read 2026-11-22 |

The docs are unversioned and the real payloads may differ: the code's KDocs (`Models.kt:89-94`, `:150-161`) claim item-level fields exist somewhere. None of this was checked against a captured payload.

## Options

| | Option | Trade-offs | Size |
|---|---|---|---|
| A (recommended) | Move the Details length rule into one shared helper (`WatchingInfo?.length(mediaDuration)` near `WatchingInfo`), and use it in `toWatchStateOrNull` and `toLocalWatchState`. For movies the position is `videos[0].watching ?: watching` and the length is `watching.duration > 0 ?: videos[0].duration ?: duration.total`. `DetailsScreenUIMapper` calls the same helper | One rule in one place; a Details open now *adds* movie progress to the index instead of possibly erasing it. Changes what `recordFromServer` writes for movies (a visible change: bars appear after opening Details). Tests above must be rewritten to the documented shape | S |
| B | A, plus a DAO guard: `upsertFromServer` keeps the stored `progress_duration` when the incoming one is null or 0 (`COALESCE(NULLIF(:d,0), progress_duration)`) | Protects the history length against any future item source that has no length. Adds an SQL change in a hand-written query that has rules of its own (`WatchStateDao.kt:18-31`) and its no-op skip clause | M |
| C | Verify first: on the box, note a started movie's card bar, open its Details, go back, compare; capture whether `/v1/items/<id>` carries item-level `watching`/`watched` (field names only, no body in Git). Code only if the bar is lost | No code if the gap is theoretical. Needs a device round before design; the payload check touches an authenticated response (AGENTS.md: keep raw bodies out of evidence) | S |

A is the recommendation: it is correct whichever payload shape is real, the same reasoning that let `design-architect-2.md` skip the capture. The C box check still serves as the runtime proof in verify.

## Risks

| # | Risk | Lives in |
|---|---|---|
| 1 | If an item-level `watching` exists with a newer position than `videos[0].watching`, preferring the video hides it (same trade-off as the Details fix, `design-architect-2.md` risk 1) | `WatchStateRepository.kt:496-509` |
| 2 | Reading `videos[0].watching` makes `toWatchStateOrNull` non-null for movies that used to fall through to the index; `resolve()` then prefers the Details payload, and that can lag a fresh write (`collection-watched-mark-lag`) | `WatchStateRepository.kt:227` |
| 3 | `isFullyWatched()` reads only item-level `watched`/`watching.status`; a movie whose only verdict is `videos[0].watched` keeps `isFullyWatched = false` and may now get a bar near 100% instead of a watched badge (the `< 1f` cap hides exactly 100% only) | `Models.kt:163-166`, `WatchStateRepository.kt:430` |
| 4 | Index rows already overwritten with a null length stay wrong until the movie plays again or a full reconciliation rewrites them (`unverified`) | `WatchStateDao.kt:106-120` |

## Open questions

| # | Question | Recommended answer |
|---|---|---|
| Q1 | Fix without first capturing the real `/v1/items/<id>` shape on the box? | Yes (option A); run the card before/after-Details check from option C as verify evidence, field names only |
| Q2 | Should movie cards read `videos[0].watched`/`watching.status` for the watched verdict too (risk 3)? | Yes, in the same helper: a bar at 98% on a finished movie is worse than the current state |
| Q3 | Add the DAO length guard (option B) now? | No; record it as an idea run unless verify shows lost lengths after the fix |

## Sources

- Code: `Models.kt`, `WatchStateRepository.kt`, `WatchStateDao.kt`, `ItemDetailsRepository.kt`, `WatchStateSyncInteractor.kt`, `VideoItemUIMapper.kt`, `DetailsScreenUIMapper.kt`, `HistoryPageResponse.kt`, `HistoryUIMapper.kt`; tests `WatchStateRepositoryTest.kt`, `VideoItemUIMapperTest.kt`
- Commits: `3c9b8a89`, `ad0c0af0`, `03c2ca31`, `0192929e` (via design doc)
- Runs: `resume-progress-anomaly` (`state.md`, `design-architect-2.md`), `collection-watched-mark-lag`, `cache-layer-rework`
- URLs: https://kinoapi.com/api_video.html, https://kinoapi.com/api_watching.html (2026-11-22)
