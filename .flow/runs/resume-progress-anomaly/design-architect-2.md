# Design delta 2: resume-progress-anomaly (architect)

Base 2fd851a4, worktree HEAD 594372e3. Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/`. Defect: `qa-report.md` round 1, defect 1. Round 1 design: `design-architect.md` (unchanged).

## Root cause

`WatchingInfo.duration` defaults to 0 (`data/api/models/Models.kt:548-553`) and the item payload does not send it: `/v1/items/<id>` documents `videos[].watching` as `{"status", "time"}` only, with the length on the video itself (`videos[].duration`), https://kinoapi.com/api_video.html, read 2026-11-22. `DetailsScreenUIMapper.timeLeft()` (`ui/feature/details/model/DetailsScreenUIMapper.kt:391-394`) returns null when `duration <= 0`, so:

| Line | Code | Observed on the box |
|---|---|---|
| Movie | `buildMovieResumeLine` `:373-389`: `left == null`, not watched, `item.duration.total > 0` gives `video_details_resume_not_started` | "Not started · 1 h 55 min" |
| Series | `buildSeriesResumeLine` `:353-371`: `left == null` gives `video_details_resume_series_next` (no time) | "Up next, season 1, episode N" |
| Episode card bar | `mapEpisode` `:140-146`: `duration > 0` else null | not checked; same cause |

Status: **confirmed by elimination** for the series line, **confirmed in docs, payload not observed** for the field absence.
- The Details screen and the player read the same cache entry (`ui/feature/details/vm/DetailsVM.kt:100` -> `domain/interactor/details/DetailsInteractor.kt:22-24`, and `PlayerVM.kt:333` -> `PlayerInteractor.kt:70-72` -> `data/repository/ItemDetailsRepository.kt:58-60`). The resume dialog after that same Details open read `episode.watching.time > 0` (`PlayerInteractor.kt:101`). For the series line, `timeLeft()` can only be null on that same object if `duration <= time`, so the duration is missing or 0.
- For the movie there is a second possible cause. The line reads `item.watching` first (`:374`), but the player reads `videos.first().watching` (`PlayerInteractor.kt:116-125`). An `item.watching` with `time = 0` would hide the video's position. The item page does not document `item.watching`. The fix below covers both causes.

This is unrelated to the marktime decode fix. It was hidden until now because no Details screen had ever shown a saved position: since 1.5.0 the cache was never marked stale. Introduced by `0192929e` (2026-08-26, the details redesign that added `timeLeft`). Unit fixtures always set `duration` on `WatchingInfo` (e.g. `app/src/test/kotlin/com/kino/puber/core/ui/model/VideoItemUIMapperTest.kt:115`), so no test caught it.

No device capture is needed: the fix does not depend on which of the two movie causes is true. qa round 2 re-runs device steps 1-3 of `test-design.md`.

## Decisions

| Question | Decision | Why |
|---|---|---|
| Where the length comes from | `watching.duration` if it is > 0, else the media's own length: `Episode.duration` (`Models.kt:538`), `Video.duration` (`Models.kt:262`), and `item.duration.total` (`Models.kt:192-195`) as the movie's last fallback | Matches the documented payload. Keeps the History path, which does fill `watching.duration` (`data/api/history/HistoryPageResponse.kt:55-59`) |
| Movie position source | `item.videos?.firstOrNull()?.watching ?: item.watching` | The same object the resume dialog reads (`PlayerInteractor.kt:116-125`), so the line and the dialog cannot disagree |
| Where the fix lives | The mapper only. `WatchingInfo` keeps `duration = 0` and gets a KDoc saying the item payload omits it | No repository or cache change. Other readers of `watching.duration` keep their behaviour |
| Episode card progress bar | Same fallback via one helper | Same Details screen, same cause, one line |

## Changes by layer

| Layer | File | Change |
|---|---|---|
| API / repository | `data/api/models/Models.kt:550` | KDoc on `duration`: absent from item payloads (`videos[]`, `episodes[]`). Read the media's own `duration`. No code change |
| ViewModel state (UI mapper) | `ui/feature/details/model/DetailsScreenUIMapper.kt:391-394` | `WatchingInfo?.timeLeft(mediaDuration: Int?)`: null if `this == null` or `time <= 0`; length = `duration.takeIf { it > 0 } ?: mediaDuration?.takeIf { it > 0 } ?: return null`; return `(length - time).takeIf { it > 0 }`. Add `WatchingInfo?.progressFraction(mediaDuration: Int?): Float?` on the same length rule |
| ViewModel state (UI mapper) | same file `:374` | `val video = item.videos?.firstOrNull()`; `left = (video?.watching ?: item.watching).timeLeft(video?.duration ?: item.duration?.total)` |
| ViewModel state (UI mapper) | same file `:356` | `next.episode.watching.timeLeft(next.episode.duration)` |
| ViewModel state (UI mapper) | same file `:140-146` | `progressPercent = episode.watching.progressFraction(episode.duration)` |

N/A for the other layers: the screen, DI, Compose content and resources do not change. The strings (`app/src/main/res/values-en/strings.xml:406-410`) stay as they are.

## Tests

All new, in `app/src/test/kotlin/com/kino/puber/ui/feature/details/model/DetailsScreenUIMapperTest.kt`. They use the `movie(...)` (`:342`) and `series(...)` (`:359`) fixtures; `movie` gains an optional `watching: WatchingInfo?` and `duration: Duration?`. Each assertion compares `info.resumeLine` (set at mapper `:267`), or the episode's `progressPercent`, with the string the mapper builds from the same resource id.

| Test | Fixture | Expects | Base |
|---|---|---|---|
| `a started movie whose position carries no length shows the time left from the video length` | video `duration = 6900`, `watching = WatchingInfo(time = 1703)` | `video_details_resume_movie` | red |
| `a movie reads its first video's position before an item-level one` | `item.watching = WatchingInfo()`, video as above | `video_details_resume_movie` | red |
| `a started episode whose position carries no length shows the time left from the episode length` | S1E1 `duration = 2700`, `watched = 0`, `watching = WatchingInfo(time = 627)` | `video_details_resume_series` | red |
| `an episode card fills its bar from the episode length when the position carries none` | same episode | `progressPercent == 627f / 2700` | red |
| `a movie with no position says not started with its length` | `duration = Duration(total = 6900)`, video without `watching` | `video_details_resume_not_started` | green guard |

These are for the test-designer to add to `test-design.md` as rows for AC-5 and AC-7 (unit level, P0). The device steps there stay unchanged and are the runtime proof.

## Alternatives rejected

- Copy the length into `WatchingInfo` during decode or in `fetchItem` (`ItemDetailsRepository.kt:96-104`): this changes the cached shape and every reader of `watching.duration`, which is more than the defect needs.
- Keep `item.watching` first for movies: the line can keep disagreeing with the resume dialog.
- Capture the payload on the box before fixing: the fix holds whichever field is missing, and the capture needs an authenticated body.

## Risks

| # | Risk | Area | Lives in |
|---|---|---|---|
| 1 | If `item.watching` is the only position some endpoint fills for a movie, preferring `videos.first().watching` hides it. The fallback still applies when the video has no `watching` | correctness | `DetailsScreenUIMapper.kt:374` |
| 2 | Episode bars that were empty now fill on series Details. This is a visible change, but uses no new layout | data shape | `DetailsScreenUIMapper.kt:140` |
| 3 | Another reader of `watching.duration` hits the same gap (see Open questions) | correctness | `core/ui/model/VideoItemUIMapper.kt:125`, `data/repository/WatchStateRepository.kt:508` |

## Open questions

1. `Item.toWatchStateOrNull` (`data/repository/WatchStateRepository.kt:497-509`) and `VideoItemUIMapper.toLocalWatchState` (`core/ui/model/VideoItemUIMapper.kt:116-126`) take `progressDuration` from `item.watching.duration`. If that field is missing in list payloads too, card progress can be lost. Recommended: leave it out of this run and record it as an idea run; no AC here covers cards. Blocks: no.
