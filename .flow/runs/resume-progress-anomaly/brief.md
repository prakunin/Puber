# Brief: resume-progress-anomaly

Base 2fd851a4. Analyst, 2026-11-22. Builds on `research.md` (cited as research.md: section).

kind: bug. size: M. tracks: none. Device verification: **yes** (networking, ViewModel outcome, resume behaviour only shows at runtime). Device: the owner's TV box `192.168.1.110:5555`, package `com.kino.puber.stage`; no emulator.

Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/`.

## Request
Progress saves to `watching/marktime` return HTTP 200 but fail to decode (`ApiError.Serialization`, "Malformed response") on every account and device, so the app treats every save as failed. Explain it, and say whether it explains the original C7 symptom (no resume dialog and no Details progress after reopening).

## Root cause (plausible: traced in code and docs; the real body on the owner's accounts was not observed)
1. `data/api/KinoPubApiClient.kt:455-464`: `setWatchingTime` decodes the answer as `WatchingStatus` through `apiCall` (`:1023-1038`, `response.body<T>()`).
2. `data/api/models/Models.kt:373-379`: `WatchingStatus.id` and `.status` have no default, so both are required. The API documents the answer as `{"status": 200}` only (research.md: External findings), so `id` is missing and decoding throws; `data/api/ApiFailures.kt:88` maps it to `ApiError.Serialization`.
3. `domain/interactor/player/PlayerInteractor.kt:273-275`: `saveWatchingTime` calls `getOrThrow()` (since 1.5.0, `1b7646af`; research.md: Related work).
4. `ui/feature/player/vm/PlayerVM.kt:1920-1933`: only a successful save sets `detailsStaleOwed` and `markContentChanged(PlaybackProgress)`; the failure branch logs a fixed string (`ui/feature/player/vm/PlayerProgressDiagnostics.kt:12`). So `flushOwedInvalidation` (`PlayerVM.kt:1980-1988`) never marks details stale nor refreshes TV home.
5. The server does store the position. Reopening within the 10 min details TTL reads the cached pre-playback `watching.time`, which is 0, and `buildResumeDialog` (`PlayerVM.kt:366`) builds no dialog. This explains C7 (research.md: Understanding).

`Result<Unit>` through `apiCall` is the existing pattern for mutating calls whose body the app ignores (`KinoPubApiClient.kt:412`, `:432`, `:509`, `:523`); Ktor discards the body for `Unit`, so any 2xx body decodes.

## Scope
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

## Acceptance criteria
- AC-1. Given a local server answering `GET /v1/watching/marktime` with HTTP 200 and `{"status":200}`, When `setWatchingTime(42, 1, 120, 2)` runs, Then the result is a success and the request carried `id=42`, `video=1`, `time=120`, `season=2` (unit test, red on base).
- AC-2. Given the same server answering 200 with `{"status":200,"id":42,"time":120}` or `{}`, When `setWatchingTime` runs, Then the result is a success (unit test).
- AC-3. Given the server answers marktime with HTTP 500, When `setWatchingTime` runs, Then the result is a failure, and `ApiRetryPolicyTest.kt:32` stays green (marktime is not repeated).
- AC-4. Given a progress save that throws `ApiError.Serialization`, When the player reports it, Then exactly one log line holds `PROGRESS_SAVE_FAILURE_DIAGNOSTIC` and the class name, and none of item id, title, time or the error message (unit test).
- AC-5. Given the stage build on the box, When a title is played from Details, sought past 5 min, held until a save, and left with Back, Then the session logs no `Playback progress save failed` and the Details screen shows the saved progress (box observation, D-pad driven).
- AC-6. Given AC-5's title, When it is reopened from Details within 10 min, Then the resume dialog offers the saved position (box observation).
- AC-7. Given a title played from Home (no Details below) and left after a save, When its Details opens within 10 min, Then it shows the saved progress (box observation; closes `cache-targeted-invalidation/brief.md` AC-6).
- AC-8. Existing tests stay green: `PlayerVMInvalidationTest`, `PlayerInteractorTest`, unit suite, detekt, lint.

## Risks
- Since 1.5.0 no save has succeeded in production, so every session will now mark details stale and refresh TV home at exit or background for the first time. Exit cost was measured with a shim on the box (`docs/superpowers/evidence/2026-11-22-cache-targeted-invalidation-verify-box.md`, AC-10).
- Per-episode `watching.time` may be absent from the item payload: the cache-targeted-invalidation shim build on the emulator saw no per-episode progress on Details after a successful mark (`.flow/runs/cache-targeted-invalidation/review-codex-1.md:4`). If the box shows the same for a series, AC-5 to AC-7 fail for episodes for a second reason. Verify a movie and an episode; a failure on episodes only becomes a new run.
- `PlayerProgressDiagnostics` was built to take no throwable so logs cannot carry viewing identity (`PlayerProgressDiagnostics.kt:7-10`). The class name only is passed; AC-4 guards it.
- Merge overlap on the `PlayerVM.kt` save path with `player-vm-split` (stage idea).

## Open questions
1. `Result<Unit>` or a small `MarkTimeResponse(status: Int? = null)`? Recommended `Result<Unit>`: it is the pattern of the other mutating calls, no caller reads the value, and AC-1/AC-2 prove the decode. Blocks: no.
2. Should the failure log name the error class (`ApiError.Serialization`, `IOException`)? Recommended yes, the simple class name only: a fixed string hid this bug for months, and a class name carries no account data. Blocks: yes (AC-4).
3. Verify on the box with one movie and one series episode (AC-5 to AC-7)? Recommended yes; an episode-only failure is recorded and split into a new run, not fixed here. Blocks: no.
