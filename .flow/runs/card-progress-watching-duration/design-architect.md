# Design (architect): card-progress-watching-duration

Base c506d0bd. Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/`. ACs: `brief.md#acceptance-criteria`.

## Decisions

| Question | Decision | Why |
|---|---|---|
| Where the movie rule lives | Three public extensions in `data/api/models/Models.kt`, next to `Item.isFullyWatched()` (`:163`): `Item.moviePosition(): WatchingInfo?` = `videos[0].watching ?: watching`; `Item.movieLength(): Int?` = `videos[0].duration > 0 ?: duration.total > 0`; `WatchingInfo.lengthOr(mediaDuration: Int?): Int?` = `duration > 0 ?: mediaDuration > 0` | The data layer and the UI mappers both read it; API models are used directly by mapping (AGENTS.md), so the model file is the one place both reach |
| How the two card readers share it | `Item.toWatchStateOrNull()` (`data/repository/WatchStateRepository.kt:496`) becomes `internal`; `VideoItemUIMapper.toLocalWatchState` (`core/ui/model/VideoItemUIMapper.kt:116-127`) is deleted and `watchState` (`:111-113`) falls back to `item.toWatchStateOrNull()` | Removes the duplicate instead of fixing it twice; AC-2 then holds by construction with and without a repository |
| When a movie item counts as carrying watch fields | Non-null gate (`WatchStateRepository.kt:499`) adds, for non-series items only, `videos[0].watched` and `videos[0].watching` | Same "non-null means the server said something" rule the item-level fields already follow; AC-1 needs the documented shape to record, AC-5 keeps the bare item on the index |
| Movie row numbers | `progressTime = moviePosition()?.time`, `progressDuration = moviePosition()?.lengthOr(movieLength())`; series rows keep `watching?.time` / `watching?.duration` | AC-1, AC-3; series unchanged (AC-5) |
| Watched verdict (brief Q2) | Movie branch of `Item.isFullyWatched()` (`Models.kt:165-167`) adds `videos[0].watched > 0 \|\| videos[0].watching?.status == WATCHED_STATUS` | AC-4; one verdict serves cards, Details (`DetailsScreenUIMapper.kt:55,372` via `isItemWatched`) and catalogue hiding |
| Details mapper | `buildMovieResumeLine` (`ui/feature/details/model/DetailsScreenUIMapper.kt:368-369`) uses `item.moviePosition().timeLeft(item.movieLength())`; private `WatchingInfo.length` (`:402-405`) keeps its `time <= 0` guard and delegates the rest to `lengthOr` | AC-6; the episode callers (`:140`, `:350`) keep passing `episode.duration` |

## Changes by layer

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

## Alternatives rejected

| Alternative | Reason |
|---|---|
| Keep `toLocalWatchState` and point both copies at the helpers | Two builders of one rule drift again; the duplicate is the defect's origin |
| Helpers in `WatchStateRepository.kt` | `DetailsScreenUIMapper` would import repository-file helpers for a model rule |
| DAO length guard (option B) | Out of scope (`brief.md#scope`) |
| Gate video fields on "meaningful" values (time > 0, status != 0) | An un-mark made elsewhere would never reach the index through Details; diverges from the item-level gate |

## Risks

| # | Risk | Lives in |
|---|---|---|
| 1 | Opening Details of a movie whose payload carries `videos[0].watching = {status 0, time 0}` now writes a not-watched row, overriding a watched or in-progress index row from the history walk whenever the Details fetch is newer; correct if the server is right, a regression if `videos[0]` lags the history | `WatchStateRepository.kt:255-260`, `data/db/WatchStateDao.kt:132-169`, `data/repository/ItemDetailsRepository.kt:26-27` |
| 2 | `resolve()` now returns the Details payload state for movies instead of the index, so a cached Details value can show an older bar or verdict than a fresh local play or toggle (shared with `collection-watched-mark-lag`) | `WatchStateRepository.kt:227` |
| 3 | The extended verdict changes `isFullyWatched` for every caller; catalogue hiding (`domain/interactor/contentlist/ContentListInteractor.kt:177`) only changes for items that carry `videos`, which list payloads do not | `Models.kt:163-177` |
| 4 | A movie with several videos (parts) is judged by `videos[0]` only, as the Details fix already does | `Models.kt` helpers |
| 5 | Opening an untouched movie's Details now inserts a row and ticks the snapshot once; the no-op skip clause (`WatchStateDao.kt:146-155`) keeps repeat opens silent | `WatchStateDao.kt:66-83` |

## Open questions

1. The `Item.watching` KDoc (`Models.kt:89-94`) claims item details carry it; correct it once AC-7 records the real field names? Recommended yes, in the verify fix-up if AC-7 shows no item-level field. Blocks: no.
