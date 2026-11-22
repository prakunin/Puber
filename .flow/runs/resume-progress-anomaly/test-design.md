# Test design: resume-progress-anomaly

Base 2fd851a4. ACs: `brief.md#acceptance-criteria`. Decisions and risks R1-R5: `design-architect.md`; delta 2: `design-architect-2.md`. Levels: `.flow/roles/test-designer.md`.
Test paths are under `app/src/test/kotlin/com/kino/puber/`.

## Risk profile

| # | Risk | P | I | Score | Covered by |
|---|---|---|---|---|---|
| 1 | Ktor + ContentNegotiation does not accept a JSON body as `Unit` (design R3) | 2 | 3 | 6 | AC-1, AC-2 unit tests on the real client and a local server |
| 2 | Per-episode `watching.time` missing from the item payload, so resume still fails for an episode (design R1) | 2 | 3 | 6 | Device steps 2 (episode run); an episode-only failure becomes a new run |
| 3 | Details screen renders as a white frame in `adb screencap` on the box (`docs/superpowers/evidence/2026-11-22-cache-targeted-invalidation-verify-box.md`, AC-6), so the Details progress of AC-5/AC-7 cannot be captured | 3 | 2 | 6 | Device steps use the resume dialog, which reads the same cached details (`PlayerVM.kt:333`, `:366`), as the proof; Details is noted as observed or not capturable |
| 4 | The diagnostic starts carrying a message, id or time into logs (brief risk 3) | 1 | 3 | 3 | AC-4 unit test |
| 5 | Exit now marks details stale and refreshes TV home every session (design R2) | 3 | 1 | 3 | Measured already (verify-box AC-10); guarded by `PlayerVMInvalidationTest` (AC-8) |
| 6 | Merge overlap with `player-vm-split` on the save path (design R5) | 2 | 2 | 4 | AC-4 and `PlayerVMInvalidationTest` fail if the merge drops the success branch or the diagnostic |
| 7 | `simpleName` obfuscated in minified release (design R4) | 2 | 1 | 2 | Accepted: diagnostic value only |
| 8 | Details resume line and episode bar read `watching.duration`, absent from item payloads, so they say "Not started" / "Up next" after a saved position (`design-architect-2.md#root-cause`, qa round 1 defect 1) | 3 | 3 | 9 | AC-5, AC-7 mapper unit rows; device steps 1-3 |
| 9 | Preferring `videos.first().watching` hides a movie position that only `item.watching` carries (design-2 R1) | 1 | 2 | 2 | P1 row `a movie whose video carries no position reads the item-level one` |
| 10 | Card progress readers of `watching.duration` hit the same gap (design-2 R3) | 2 | 2 | 4 | Out of this run: `card-progress-watching-duration` (state Q4) |

## Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 red | `data/api/KinoPubApiClientFailureTest`: `aMarkTimeAnswerWithOnlyAStatusIsASuccessAndCarriesThePosition` | Serves `{"status":200}` on `/v1/watching/marktime`, asserts `isSuccess` and the captured query holds `id=42`, `video=1`, `time=120`, `season=2`. Fails on base (`WatchingStatus.id` required, `ApiError.Serialization`); fails if a parameter is dropped or renamed |
| AC-2 | unit | P0 red | `KinoPubApiClientFailureTest`: `anEmptyMarkTimeAnswerIsASuccess` | Body `{}` succeeds. Fails on base and if a required-field model returns |
| AC-2 | unit | P1 | `KinoPubApiClientFailureTest`: `aMarkTimeAnswerWithExtraFieldsIsASuccess` | Body `{"status":200,"id":42,"time":120}` succeeds; green on base; fails if the `Unit` decode or `ignoreUnknownKeys` breaks |
| AC-3 | unit | P0 guard | `KinoPubApiClientFailureTest`: `aMarkTimeServerErrorIsAFailureAndIsSentOnce` | HTTP 500 gives `ApiError.Http` with `statusCode` 500 and the server counts exactly one request. Fails if `Result<Unit>` swallows non-2xx or marktime leaves the non-retried list |
| AC-3 | unit | P0 guard | `data/api/ApiRetryPolicyTest`: `aFailedMarkTimeIsIssuedOnceBecauseRepeatingItWouldMoveThePositionAgain` | Existing; unchanged |
| AC-4 | unit | P0 red | `ui/feature/player/vm/PlayerVMPlaybackFlowTest`: `failedFinalProgressSave_emitsIdentityFreeDiagnostic` | Save throws `ApiError.Serialization(IllegalStateException(<private title, id, time>))`; asserts one log entry, holds `PROGRESS_SAVE_FAILURE_DIAGNOSTIC` and `Serialization`, holds neither the private id, title, time, the cause message nor `Malformed response`. Fails on base (no class name); fails if a `Throwable` or message is logged |
| AC-4 | unit | P1 | `PlayerVMPlaybackFlowTest`: `failedFinalProgressSave_returnsEmptyChanges` | Existing; a failed save still reports no content change |
| AC-5 | unit | P0 red | `ui/feature/details/model/DetailsScreenUIMapperTest`: `a started movie whose position carries no length shows the time left from the video length` | Video `duration = 6900`, `watching = WatchingInfo(time = 1703)`; `resumeLine` equals `video_details_resume_movie` with 5197 s. Fails on base (`timeLeft()` needs `watching.duration`, gives "Not started"); fails if the media-length fallback is removed |
| AC-5 | unit | P0 red | `ui/feature/details/model/DetailsScreenUIMapperTest`: `a movie reads its first video's position before an item-level one` | `item.watching = WatchingInfo()`, video as above; line is `video_details_resume_movie`. Fails on base (`item.watching` first, `:374`); fails if the source order flips back |
| AC-5 | unit | P0 guard | `ui/feature/details/model/DetailsScreenUIMapperTest`: `a movie with no position says not started with its length` | `duration = Duration(total = 6900)`, video without `watching`; line is `video_details_resume_not_started`. Fails if the fallback invents a position |
| AC-5 | unit | P1 red | `ui/feature/details/model/DetailsScreenUIMapperTest`: `a movie whose video carries no position reads the item-level one` | No video `watching`, `item.watching = WatchingInfo(time = 1703)`, `duration.total = 6900`; line is `video_details_resume_movie`. Fails if the `?: item.watching` fallback or the `item.duration.total` length is dropped (risk 9) |
| AC-5, AC-7 | unit | P0 red | `ui/feature/details/model/DetailsScreenUIMapperTest`: `a started episode whose position carries no length shows the time left from the episode length` | S1E1 `duration = 2700`, `watched = 0`, `watching = WatchingInfo(time = 627)`; line is `video_details_resume_series` with 2073 s. Fails on base ("Up next" line, no time); fails if `episode.duration` is not passed |
| AC-5, AC-7 | unit | P0 red | `ui/feature/details/model/DetailsScreenUIMapperTest`: `an episode card fills its bar from the episode length when the position carries none` | Same episode; `progressPercent == 627f / 2700`. Fails on base (null, `:140-146`); fails if the bar keeps its own length rule |
| AC-5, AC-6, AC-7 | device | P0 | Device steps 1-3 below | Box account reproduced the failure on base (verify-box step 0: `saveFail ... ApiError$Serialization` on every tick); fails if any save still fails, resume reads stale details, or Details ignores the saved position (qa round 1) |
| AC-8 | unit | P0 guard | `ui/feature/player/vm/PlayerVMInvalidationTest`: `backPressed_afterSuccessfulSaves_marksAndRefreshesOnceBeforeBack`, `backPressed_withoutSuccessfulSave_marksAndRefreshesNothing` | Existing; a successful save marks details stale once at exit, a failed one marks nothing |
| AC-8 | unit | P1 guard | `domain/interactor/player/PlayerInteractorTest`: `saveWatchingTime_success_doesNotMarkItemDetailsStale`, `saveWatchingTime_failure_doesNotMarkStaleOrInvalidateItemDetailsCache` | Stub becomes `Result.success(Unit)`; a failed `Result` still throws from `saveWatchingTime` |
| AC-8 | unit | P0 guard | check.full: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` | Whole suite, detekt, lint green |

## Bug: failing-first test

`aMarkTimeAnswerWithOnlyAStatusIsASuccessAndCarriesThePosition`, written first. On base `setWatchingTime` returns `Result<WatchingStatus>` (`git -C <wt> show 2fd851a4:app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt`, `:455-464`) and `WatchingStatus.id` has no default (`Models.kt:373-379`), so the body `{"status":200}` yields `ApiError.Serialization` and the `isSuccess` assert fails.

Delta 2: `a started movie whose position carries no length shows the time left from the video length`, written first. On base `timeLeft()` returns null when `watching.duration <= 0` (`git -C <wt> show 2fd851a4:app/src/main/java/com/kino/puber/ui/feature/details/model/DetailsScreenUIMapper.kt`, `:391-394`), so the line is `video_details_resume_not_started`.

## Device checks (TV box `192.168.1.110:5555`, `com.kino.puber.stage`, D-pad only)

The box's account is the one where marktime failed before (verify-box evidence, step 0), so the base side of this bug is already recorded there and is not re-run.

Setup: lease through `tools/emulator-resource-lock.sh`, `adb connect 192.168.1.110:5555`, install the worktree's dev APK with `tools/android-apk-install-preserve`, check the on-device sha256, `adb -s 192.168.1.110:5555 logcat -c`. Log evidence is `logcat -d | grep -cF "Playback progress save failed"` (a count), never a raw dump.

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| 1 | Movie (AC-5, AC-6). From Details press DPAD_CENTER on Play; DPAD_RIGHT until the position passes 5:00; wait 40 s (one 30 s save tick); BACK, BACK. Screenshot Details. Within 10 min DPAD_CENTER on Play again | Grep count 0. Details shows the saved progress, or is a white frame (risk 3: record "not capturable"). The resume dialog appears and offers the position within 30 s of the one left | Count > 0; playback restarts near 0:00 with no dialog; Details reads "Not started" (movie) or "Up next" with no time (episode); crash; focus lost after BACK |
| 2 | Series episode (AC-5, AC-6, Q3). Step 1 on one episode of a series | As step 1 | As step 1. A failure here only, with step 1 passing, is recorded and becomes a new run |
| 3 | From Home (AC-7). `logcat -c`; on Home start a title not opened in steps 1-2 with DPAD_CENTER (Player over Home, no Details below); DPAD_RIGHT past 5:00; wait 40 s; BACK, BACK. Within 10 min open its Details from Home and press Play | Grep count 0. Details shows the saved progress or a white frame (record which); the resume dialog offers the saved position | Count > 0; no dialog; Details reads "Not started" or "Up next" with no time; crash |

Release the lease after step 3; run `tools/mobile-evidence-audit.sh` on the evidence directory.

## perf track

N/A: perf track off; exit cost was measured in verify-box AC-10.

## UNTESTABLE

AC-5 and AC-7, Details progress as a screenshot: the box's Details screen captures as a white frame and its uiautomator dump is empty (verify-box AC-6). The resume dialog in device steps 1-3 and the `DetailsScreenUIMapperTest` rows are the substitute proof.
