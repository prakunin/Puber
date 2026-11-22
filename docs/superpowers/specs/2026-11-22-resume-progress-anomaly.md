# Progress saves fail to decode the marktime answer, so details stay stale and resume is lost

Status: approved
Date: 2026-11-22
Slug: resume-progress-anomaly
Kind: bug
Size: M
Tracks: none

## Problem / Why

# Brief: resume-progress-anomaly

Base 2fd851a4. Analyst, 2026-11-22. Builds on `research.md` (cited as research.md: section).

kind: bug. size: M. tracks: none. Device verification: **yes** (networking, ViewModel outcome, resume behaviour only shows at runtime). Device: the owner's TV box `192.168.1.110:5555`, package `com.kino.puber.stage`; no emulator.

Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/`.

### Request

Progress saves to `watching/marktime` return HTTP 200 but fail to decode (`ApiError.Serialization`, "Malformed response") on every account and device, so the app treats every save as failed. Explain it, and say whether it explains the original C7 symptom (no resume dialog and no Details progress after reopening).

### Root cause (plausible: traced in code and docs; the real body on the owner's accounts was not observed)

1. `data/api/KinoPubApiClient.kt:455-464`: `setWatchingTime` decodes the answer as `WatchingStatus` through `apiCall` (`:1023-1038`, `response.body<T>()`).
2. `data/api/models/Models.kt:373-379`: `WatchingStatus.id` and `.status` have no default, so both are required. The API documents the answer as `{"status": 200}` only (research.md: External findings), so `id` is missing and decoding throws; `data/api/ApiFailures.kt:88` maps it to `ApiError.Serialization`.
3. `domain/interactor/player/PlayerInteractor.kt:273-275`: `saveWatchingTime` calls `getOrThrow()` (since 1.5.0, `1b7646af`; research.md: Related work).
4. `ui/feature/player/vm/PlayerVM.kt:1920-1933`: only a successful save sets `detailsStaleOwed` and `markContentChanged(PlaybackProgress)`; the failure branch logs a fixed string (`ui/feature/player/vm/PlayerProgressDiagnostics.kt:12`). So `flushOwedInvalidation` (`PlayerVM.kt:1980-1988`) never marks details stale nor refreshes TV home.
5. The server does store the position. Reopening within the 10 min details TTL reads the cached pre-playback `watching.time`, which is 0, and `buildResumeDialog` (`PlayerVM.kt:366`) builds no dialog. This explains C7 (research.md: Understanding).

`Result<Unit>` through `apiCall` is the existing pattern for mutating calls whose body the app ignores (`KinoPubApiClient.kt:412`, `:432`, `:509`, `:523`); Ktor discards the body for `Unit`, so any 2xx body decodes.

### Scope

In: research option A with `Result<Unit>` (Q1): `setWatchingTime` returns `Result<Unit>`; `WatchingStatus` and its import are deleted; a client test serves the documented body; the failure diagnostic names the error class (Q2).
Out: research options B and C.

| File | Change |
|---|---|
| `data/api/KinoPubApiClient.kt:55`, `:455-457` | Return `Result<Unit>`; drop the `WatchingStatus` import |
| `data/api/models/Models.kt:372-379` | Delete `WatchingStatus` (no other user) |
| `ui/feature/player/vm/PlayerProgressDiagnostics.kt:12`, `PlayerVM.kt:1931-1932` | Pass the error's class simple name only (Q2) |
| `app/src/test/kotlin/com/kino/puber/data/api/KinoPubApiClientFailureTest.kt` | New marktime decode test on the local-server fixture (`:139-162`) |
| `app/src/test/kotlin/com/kino/puber/domain/interactor/player/PlayerInteractorTest.kt:651-652` | Stub `Result.success(Unit)` |
| `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMPlaybackFlowTest.kt:190-210` | Assert the class name is logged, nothing private |

### Risks (analyst)

- Since 1.5.0 no save has succeeded in production, so every session will now mark details stale and refresh TV home at exit or background for the first time. Exit cost was measured with a shim on the box (`docs/superpowers/evidence/2026-11-22-cache-targeted-invalidation-verify-box.md`, AC-10).
- Per-episode `watching.time` may be absent from the item payload: the cache-targeted-invalidation shim build on the emulator saw no per-episode progress on Details after a successful mark (`.flow/runs/cache-targeted-invalidation/review-codex-1.md:4`). If the box shows the same for a series, AC-5 to AC-7 fail for episodes for a second reason. Verify a movie and an episode; a failure on episodes only becomes a new run.
- `PlayerProgressDiagnostics` was built to take no throwable so logs cannot carry viewing identity (`PlayerProgressDiagnostics.kt:7-10`). The class name only is passed; AC-4 guards it.
- Merge overlap on the `PlayerVM.kt` save path with `player-vm-split` (stage idea).

## Acceptance criteria

- AC-1. Given a local server answering `GET /v1/watching/marktime` with HTTP 200 and `{"status":200}`, When `setWatchingTime(42, 1, 120, 2)` runs, Then the result is a success and the request carried `id=42`, `video=1`, `time=120`, `season=2` (unit test, red on base).
- AC-2. Given the same server answering 200 with `{"status":200,"id":42,"time":120}` or `{}`, When `setWatchingTime` runs, Then the result is a success (unit test).
- AC-3. Given the server answers marktime with HTTP 500, When `setWatchingTime` runs, Then the result is a failure, and `ApiRetryPolicyTest.kt:32` stays green (marktime is not repeated).
- AC-4. Given a progress save that throws `ApiError.Serialization`, When the player reports it, Then exactly one log line holds `PROGRESS_SAVE_FAILURE_DIAGNOSTIC` and the class name, and none of item id, title, time or the error message (unit test).
- AC-5. Given the stage build on the box, When a title is played from Details, sought past 5 min, held until a save, and left with Back, Then the session logs no `Playback progress save failed` and the Details screen shows the saved progress (box observation, D-pad driven).
- AC-6. Given AC-5's title, When it is reopened from Details within 10 min, Then the resume dialog offers the saved position (box observation).
- AC-7. Given a title played from Home (no Details below) and left after a save, When its Details opens within 10 min, Then it shows the saved progress (box observation; closes `cache-targeted-invalidation/brief.md` AC-6).
- AC-8. Existing tests stay green: `PlayerVMInvalidationTest`, `PlayerInteractorTest`, unit suite, detekt, lint.

## Out of scope

Not given as a separate section by the analyst; the boundary is in Problem / Why: Scope and the Q3 answer (an episode-only failure becomes a new run).

## Design

Base 2fd851a4. Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/`. Root cause and ACs: `brief.md`.

### Decisions

| Question | Decision | Why |
|---|---|---|
| Return type of `setWatchingTime` | `Result<Unit>` through the existing `apiCall` (Q1) | Same shape as `clearItemHistory` (`data/api/KinoPubApiClient.kt:412`) and `addBookmarkItem` (`:509`); the only caller discards the value (`domain/interactor/player/PlayerInteractor.kt:274`). |
| Fate of `WatchingStatus` | Delete it (`data/api/models/Models.kt:372-379`) and its imports (`KinoPubApiClient.kt:55`, `app/src/test/kotlin/com/kino/puber/domain/interactor/player/PlayerInteractorTest.kt:12`) | No other reference in `app/src` (grep). |
| What the diagnostic receives | `reportSaveFailure(errorType: KClass<out Throwable>)`; logs `"$PROGRESS_SAVE_FAILURE_DIAGNOSTIC: ${errorType.simpleName ?: "unknown"}"` (Q2) | A `KClass` cannot carry a message, cause or URL, so the boundary keeps the guarantee its KDoc states (`ui/feature/player/vm/PlayerProgressDiagnostics.kt:7-10`); passing a `Throwable` or a free `String` would not. |
| Call site | `catch (error: Throwable) { PlayerProgressDiagnostics.reportSaveFailure(error::class) }` at `ui/feature/player/vm/PlayerVM.kt:1931-1932` | One-line change; the `CancellationException` rethrow above it stays. |
| `PlayerInteractor.saveWatchingTime` | Unchanged: `.getOrThrow()` on `Result<Unit>` | Failure semantics (non-2xx, offline) stay as they are; only a 2xx stops being a failure. |
| Retry policy | Unchanged: `watching/marktime` stays in the non-retried list (`data/api/ApiRetryPolicy.kt:21`) | AC-3. |

### Changes by layer

| Layer | File | Change |
|---|---|---|
| API / repository | `data/api/KinoPubApiClient.kt:455-457` | `): Result<Unit> = apiCall {`; request block (`:458-463`) untouched. Drop import `:55`. |
| API / repository | `data/api/models/Models.kt:372-379` | Delete `@Serializable data class WatchingStatus`. |
| ViewModel | `ui/feature/player/vm/PlayerProgressDiagnostics.kt:11-15` | New signature per Decisions; KDoc updated to "accepts only the failure's type". Import `kotlin.reflect.KClass`. |
| ViewModel | `ui/feature/player/vm/PlayerVM.kt:1931-1932` | Name the caught throwable and pass `error::class`. |
| Screen and DI, Compose, resources | N/A: no change. |
| Tests | `app/src/test/kotlin/com/kino/puber/data/api/KinoPubApiClientFailureTest.kt` | New tests on `withServer` (`:141-157`), `client` (`:112-139`) and `respond` (`:159`) for `/v1/watching/marktime`: body `{"status":200}` succeeds and the captured query has `id=42&video=1&time=120&season=2` (AC-1, red on base); bodies `{"status":200,"id":42,"time":120}` and `{}` succeed (AC-2); HTTP 500 fails (AC-3). |
| Tests | `app/src/test/kotlin/com/kino/puber/domain/interactor/player/PlayerInteractorTest.kt:651-652` | Stub `Result.success(Unit)`; drop import `:12`. |
| Tests | `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMPlaybackFlowTest.kt:183-211` | `failedFinalProgressSave_emitsIdentityFreeDiagnostic` throws `ApiError.Serialization(IllegalStateException(<private message>))`, keeps the private-data asserts and adds `output.contains("Serialization")` (AC-4). |

The details refresh needs no change once a save succeeds: `exitPlayer` (`PlayerVM.kt:2030-2035`) awaits the drain, `flushOwedInvalidation` (`:1980-1988`) marks details stale, and `DetailsVM.onReturnedContentChanges` (`ui/feature/details/vm/DetailsVM.kt:552-558`) reloads with `forceRefresh = true` on `PlaybackProgress` for its item. Resume reads `resolved.watchingTime` into `buildResumeDialog` (`PlayerVM.kt:366`, defined `:405`).

### Alternatives rejected

- `MarkTimeResponse(status: Int? = null)`: a model no caller reads; Q1 chose `Result<Unit>`.
- Defaults on every `WatchingStatus` field (research option B): keeps a model of fields the API does not send.
- Treat undecodable 2xx as success in `apiCall` for mutating paths (option C): hides real shape errors for `toggle` and `togglewatchlist`, whose callers read the body.
- `reportSaveFailure(error: Throwable)` or `(name: String)`: reopens the path for messages carrying item id or time into logs.

### Risks

| # | Risk | Area | Lives in |
|---|---|---|---|
| 1 | Per-episode `watching.time` may be absent from the item payload, so AC-5 to AC-7 can still fail for an episode after the fix (`.flow/runs/cache-targeted-invalidation/review-codex-1.md:4`). Verify a movie and an episode (Q3); an episode-only failure becomes a new run. | correctness | `domain/interactor/player/PlayerInteractor.kt:101`, `:125` |
| 2 | Every session now marks details stale and refreshes TV home at exit and background for the first time since 1.5.0; exit latency grows by one cache write and a refresh request. Measured with the shim (`docs/superpowers/evidence/2026-11-22-cache-targeted-invalidation-verify-box.md`, AC-10). | performance, rollout | `PlayerVM.kt:1980-1988`, `:2030-2035` |
| 3 | `Result<Unit>` depends on Ktor 3.6.1 plus `ContentNegotiation` (`KinoPubApiClient.kt:176`, Json `ignoreUnknownKeys` `:132`) accepting any JSON body as `Unit`. AC-1/AC-2 run the real client against a local server and settle it. | data shape | `KinoPubApiClient.kt:1023-1038` |
| 4 | In minified `release` (`app/build.gradle.kts:187`) `simpleName` may be an obfuscated name; the `dev` stage build is not minified. Diagnostic value only. | rollout | `PlayerProgressDiagnostics.kt` |
| 5 | Merge overlap on the save path in `PlayerVM.kt` with `player-vm-split` (stage idea). | rollout | `PlayerVM.kt:1898-1940` |

### Open questions

N/A: Q1 to Q3 answered in `state.md`.

### Design delta 2 (qa round 1 defect 1)

Base 2fd851a4, worktree HEAD 594372e3. Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/`. Defect: `qa-report.md` round 1, defect 1. Round 1 design: `design-architect.md` (unchanged).

#### Root cause

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

#### Decisions

| Question | Decision | Why |
|---|---|---|
| Where the length comes from | `watching.duration` if it is > 0, else the media's own length: `Episode.duration` (`Models.kt:538`), `Video.duration` (`Models.kt:262`), and `item.duration.total` (`Models.kt:192-195`) as the movie's last fallback | Matches the documented payload. Keeps the History path, which does fill `watching.duration` (`data/api/history/HistoryPageResponse.kt:55-59`) |
| Movie position source | `item.videos?.firstOrNull()?.watching ?: item.watching` | The same object the resume dialog reads (`PlayerInteractor.kt:116-125`), so the line and the dialog cannot disagree |
| Where the fix lives | The mapper only. `WatchingInfo` keeps `duration = 0` and gets a KDoc saying the item payload omits it | No repository or cache change. Other readers of `watching.duration` keep their behaviour |
| Episode card progress bar | Same fallback via one helper | Same Details screen, same cause, one line |

#### Changes by layer

| Layer | File | Change |
|---|---|---|
| API / repository | `data/api/models/Models.kt:550` | KDoc on `duration`: absent from item payloads (`videos[]`, `episodes[]`). Read the media's own `duration`. No code change |
| ViewModel state (UI mapper) | `ui/feature/details/model/DetailsScreenUIMapper.kt:391-394` | `WatchingInfo?.timeLeft(mediaDuration: Int?)`: null if `this == null` or `time <= 0`; length = `duration.takeIf { it > 0 } ?: mediaDuration?.takeIf { it > 0 } ?: return null`; return `(length - time).takeIf { it > 0 }`. Add `WatchingInfo?.progressFraction(mediaDuration: Int?): Float?` on the same length rule |
| ViewModel state (UI mapper) | same file `:374` | `val video = item.videos?.firstOrNull()`; `left = (video?.watching ?: item.watching).timeLeft(video?.duration ?: item.duration?.total)` |
| ViewModel state (UI mapper) | same file `:356` | `next.episode.watching.timeLeft(next.episode.duration)` |
| ViewModel state (UI mapper) | same file `:140-146` | `progressPercent = episode.watching.progressFraction(episode.duration)` |

N/A for the other layers: the screen, DI, Compose content and resources do not change. The strings (`app/src/main/res/values-en/strings.xml:406-410`) stay as they are.

#### Tests

All new, in `app/src/test/kotlin/com/kino/puber/ui/feature/details/model/DetailsScreenUIMapperTest.kt`. They use the `movie(...)` (`:342`) and `series(...)` (`:359`) fixtures; `movie` gains an optional `watching: WatchingInfo?` and `duration: Duration?`. Each assertion compares `info.resumeLine` (set at mapper `:267`), or the episode's `progressPercent`, with the string the mapper builds from the same resource id.

| Test | Fixture | Expects | Base |
|---|---|---|---|
| `a started movie whose position carries no length shows the time left from the video length` | video `duration = 6900`, `watching = WatchingInfo(time = 1703)` | `video_details_resume_movie` | red |
| `a movie reads its first video's position before an item-level one` | `item.watching = WatchingInfo()`, video as above | `video_details_resume_movie` | red |
| `a started episode whose position carries no length shows the time left from the episode length` | S1E1 `duration = 2700`, `watched = 0`, `watching = WatchingInfo(time = 627)` | `video_details_resume_series` | red |
| `an episode card fills its bar from the episode length when the position carries none` | same episode | `progressPercent == 627f / 2700` | red |
| `a movie with no position says not started with its length` | `duration = Duration(total = 6900)`, video without `watching` | `video_details_resume_not_started` | green guard |

These are for the test-designer to add to `test-design.md` as rows for AC-5 and AC-7 (unit level, P0). The device steps there stay unchanged and are the runtime proof.

#### Alternatives rejected

- Copy the length into `WatchingInfo` during decode or in `fetchItem` (`ItemDetailsRepository.kt:96-104`): this changes the cached shape and every reader of `watching.duration`, which is more than the defect needs.
- Keep `item.watching` first for movies: the line can keep disagreeing with the resume dialog.
- Capture the payload on the box before fixing: the fix holds whichever field is missing, and the capture needs an authenticated body.

#### Risks

| # | Risk | Area | Lives in |
|---|---|---|---|
| 1 | If `item.watching` is the only position some endpoint fills for a movie, preferring `videos.first().watching` hides it. The fallback still applies when the video has no `watching` | correctness | `DetailsScreenUIMapper.kt:374` |
| 2 | Episode bars that were empty now fill on series Details. This is a visible change, but uses no new layout | data shape | `DetailsScreenUIMapper.kt:140` |
| 3 | Another reader of `watching.duration` hits the same gap (see Open questions) | correctness | `core/ui/model/VideoItemUIMapper.kt:125`, `data/repository/WatchStateRepository.kt:508` |

#### Open questions

1. `Item.toWatchStateOrNull` (`data/repository/WatchStateRepository.kt:497-509`) and `VideoItemUIMapper.toLocalWatchState` (`core/ui/model/VideoItemUIMapper.kt:116-126`) take `progressDuration` from `item.watching.duration`. If that field is missing in list payloads too, card progress can be lost. Recommended: leave it out of this run and record it as an idea run; no AC here covers cards. Blocks: no.

## Test design

Base 2fd851a4. ACs: `brief.md#acceptance-criteria`. Decisions and risks R1-R5: `design-architect.md`. Levels: `.flow/roles/test-designer.md`.
Test paths are under `app/src/test/kotlin/com/kino/puber/`.

### Risk profile

| # | Risk | P | I | Score | Covered by |
|---|---|---|---|---|---|
| 1 | Ktor + ContentNegotiation does not accept a JSON body as `Unit` (design R3) | 2 | 3 | 6 | AC-1, AC-2 unit tests on the real client and a local server |
| 2 | Per-episode `watching.time` missing from the item payload, so resume still fails for an episode (design R1) | 2 | 3 | 6 | Device steps 2 (episode run); an episode-only failure becomes a new run |
| 3 | Details screen renders as a white frame in `adb screencap` on the box (`docs/superpowers/evidence/2026-11-22-cache-targeted-invalidation-verify-box.md`, AC-6), so the Details progress of AC-5/AC-7 cannot be captured | 3 | 2 | 6 | Device steps use the resume dialog, which reads the same cached details (`PlayerVM.kt:333`, `:366`), as the proof; Details is noted as observed or not capturable |
| 4 | The diagnostic starts carrying a message, id or time into logs (brief risk 3) | 1 | 3 | 3 | AC-4 unit test |
| 5 | Exit now marks details stale and refreshes TV home every session (design R2) | 3 | 1 | 3 | Measured already (verify-box AC-10); guarded by `PlayerVMInvalidationTest` (AC-8) |
| 6 | Merge overlap with `player-vm-split` on the save path (design R5) | 2 | 2 | 4 | AC-4 and `PlayerVMInvalidationTest` fail if the merge drops the success branch or the diagnostic |
| 7 | `simpleName` obfuscated in minified release (design R4) | 2 | 1 | 2 | Accepted: diagnostic value only |

### Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 red | `data/api/KinoPubApiClientFailureTest`: `aMarkTimeAnswerWithOnlyAStatusIsASuccessAndCarriesThePosition` | Serves `{"status":200}` on `/v1/watching/marktime`, asserts `isSuccess` and the captured query holds `id=42`, `video=1`, `time=120`, `season=2`. Fails on base (`WatchingStatus.id` required, `ApiError.Serialization`); fails if a parameter is dropped or renamed |
| AC-2 | unit | P0 red | `KinoPubApiClientFailureTest`: `anEmptyMarkTimeAnswerIsASuccess` | Body `{}` succeeds. Fails on base and if a required-field model returns |
| AC-2 | unit | P1 | `KinoPubApiClientFailureTest`: `aMarkTimeAnswerWithExtraFieldsIsASuccess` | Body `{"status":200,"id":42,"time":120}` succeeds; green on base; fails if the `Unit` decode or `ignoreUnknownKeys` breaks |
| AC-3 | unit | P0 guard | `KinoPubApiClientFailureTest`: `aMarkTimeServerErrorIsAFailureAndIsSentOnce` | HTTP 500 gives `ApiError.Http` with `statusCode` 500 and the server counts exactly one request. Fails if `Result<Unit>` swallows non-2xx or marktime leaves the non-retried list |
| AC-3 | unit | P0 guard | `data/api/ApiRetryPolicyTest`: `aFailedMarkTimeIsIssuedOnceBecauseRepeatingItWouldMoveThePositionAgain` | Existing; unchanged |
| AC-4 | unit | P0 red | `ui/feature/player/vm/PlayerVMPlaybackFlowTest`: `failedFinalProgressSave_emitsIdentityFreeDiagnostic` | Save throws `ApiError.Serialization(IllegalStateException(<private title, id, time>))`; asserts one log entry, holds `PROGRESS_SAVE_FAILURE_DIAGNOSTIC` and `Serialization`, holds neither the private id, title, time, the cause message nor `Malformed response`. Fails on base (no class name); fails if a `Throwable` or message is logged |
| AC-4 | unit | P1 | `PlayerVMPlaybackFlowTest`: `failedFinalProgressSave_returnsEmptyChanges` | Existing; a failed save still reports no content change |
| AC-5, AC-6, AC-7 | device | P0 | Device steps 1-3 below | Box account reproduced the failure on base (verify-box step 0: `saveFail ... ApiError$Serialization` on every tick); fails if any save still fails or resume reads stale details |
| AC-8 | unit | P0 guard | `ui/feature/player/vm/PlayerVMInvalidationTest`: `backPressed_afterSuccessfulSaves_marksAndRefreshesOnceBeforeBack`, `backPressed_withoutSuccessfulSave_marksAndRefreshesNothing` | Existing; a successful save marks details stale once at exit, a failed one marks nothing |
| AC-8 | unit | P1 guard | `domain/interactor/player/PlayerInteractorTest`: `saveWatchingTime_success_doesNotMarkItemDetailsStale`, `saveWatchingTime_failure_doesNotMarkStaleOrInvalidateItemDetailsCache` | Stub becomes `Result.success(Unit)`; a failed `Result` still throws from `saveWatchingTime` |
| AC-8 | unit | P0 guard | check.full: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` | Whole suite, detekt, lint green |

### Bug: failing-first test

`aMarkTimeAnswerWithOnlyAStatusIsASuccessAndCarriesThePosition`, written first. On base `setWatchingTime` returns `Result<WatchingStatus>` (`git -C <wt> show 2fd851a4:app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt`, `:455-464`) and `WatchingStatus.id` has no default (`Models.kt:373-379`), so the body `{"status":200}` yields `ApiError.Serialization` and the `isSuccess` assert fails.

### Device checks (TV box `192.168.1.110:5555`, `com.kino.puber.stage`, D-pad only)

The box's account is the one where marktime failed before (verify-box evidence, step 0), so the base side of this bug is already recorded there and is not re-run.

Setup: lease through `tools/emulator-resource-lock.sh`, `adb connect 192.168.1.110:5555`, install the worktree's dev APK with `tools/android-apk-install-preserve`, check the on-device sha256, `adb -s 192.168.1.110:5555 logcat -c`. Log evidence is `logcat -d | grep -cF "Playback progress save failed"` (a count), never a raw dump.

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| 1 | Movie (AC-5, AC-6). From Details press DPAD_CENTER on Play; DPAD_RIGHT until the position passes 5:00; wait 40 s (one 30 s save tick); BACK, BACK. Screenshot Details. Within 10 min DPAD_CENTER on Play again | Grep count 0. Details shows the saved progress, or is a white frame (risk 3: record "not capturable"). The resume dialog appears and offers the position within 30 s of the one left | Count > 0; playback restarts near 0:00 with no dialog; crash; focus lost after BACK |
| 2 | Series episode (AC-5, AC-6, Q3). Step 1 on one episode of a series | As step 1 | As step 1. A failure here only, with step 1 passing, is recorded and becomes a new run |
| 3 | From Home (AC-7). `logcat -c`; on Home start a title not opened in steps 1-2 with DPAD_CENTER (Player over Home, no Details below); DPAD_RIGHT past 5:00; wait 40 s; BACK, BACK. Within 10 min open its Details from Home and press Play | Grep count 0. Details shows the saved progress or a white frame (record which); the resume dialog offers the saved position | Count > 0; no dialog; crash |

Release the lease after step 3; run `tools/mobile-evidence-audit.sh` on the evidence directory.

### perf track

N/A: perf track off; exit cost was measured in verify-box AC-10.

### UNTESTABLE

AC-5 and AC-7, Details progress as a screenshot: the box's Details screen captures as a white frame and its uiautomator dump is empty (verify-box AC-6). The resume dialog in device steps 1-3 is the substitute proof.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | setWatchingTime returns Result<Unit> or a MarkTimeResponse model? | no | answered | Result<Unit> |
| Q2 | Progress-save failure log names the error class? | yes (AC-4) | answered | yes, simple class name only |
| Q3 | Check a movie and a series episode on the box? | no | answered | yes; an episode-only failure becomes a new run |
| Q4 | Fix card progress readers of watching.duration here too? | no | answered | no, separate run card-progress-watching-duration |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-22 | Q1: Result<Unit> | owner (standing instruction: recommended) |
| 2026-11-22 | Q2: log the simple error class name only | owner (standing instruction: recommended) |
| 2026-11-22 | Q3: movie and series episode on the box | owner (standing instruction: recommended) |
| 2026-11-22 | Gate 1: approve | owner (standing instruction: recommended) |
| 2026-11-22 | Design delta 2 approved; Q4: card progress readers deferred to card-progress-watching-duration | owner (standing instruction: recommended) |
