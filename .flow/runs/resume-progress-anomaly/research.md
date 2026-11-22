# Research: resume-progress-anomaly

## Request

the run resume-progress-anomaly (read /mnt/data/projects/PetProjets/Puber/.flow/runs/resume-progress-anomaly/state.md: Next and Log). Main finding to explain: the KinoPub watching/marktime call returns HTTP 200 but KinoPubApiClient fails to decode the body (ApiError.Serialization "Malformed response"), on every account/device tried (emulator and the TV box), so PlayerInteractor.saveWatchingTime throws and the app treats every progress save as failed. Also check whether the original symptom (no resume after reopening) is explained by it or by something else. Find the response model and decode path, the real response shape (API docs/web, project history, the upstream repo if any; do not call the authenticated API with real credentials and never record tokens), when it broke (git history), and options.

## Understanding

`setWatchingTime` decodes the marktime answer into `WatchingStatus`, which requires `id` and `status`. The official KinoPub API documents the answer as `{"status": 200}` only, so decoding fails on every account and every 200 becomes `ApiError.Serialization`. The model has been wrong since 1.0.0. It has caused visible harm since 1.5.0, when `saveWatchingTime` began to call `getOrThrow()` and to invalidate the details cache after the call. The server stores the position, but the app never marks the cached details stale and never reports a content change. So reopening a title within the 10 min details TTL reads the cached pre-playback position and shows no resume dialog. This explains the original C7 symptom.

## Current state in code

- `data/api/KinoPubApiClient.kt:455`: `setWatchingTime(...)` returns `Result<WatchingStatus>` from `GET watching/marktime`. No caller reads the value.
- `data/api/models/Models.kt:373`: `WatchingStatus(id: Int, status: Int, time?, season?, episode?)`. `id` and `status` have no default, so they are required. Nothing else uses this model.
- `data/api/KinoPubApiClient.kt:1032`: `apiCall` calls `response.body<T>()` after `requireSuccessful()`. The Json config (`ignoreUnknownKeys`, `isLenient`) does not help with a missing required field.
- `data/api/ApiFailures.kt:88`: `asApiFailure` maps `SerializationException` / `ContentConvertException` / `NoTransformationFoundException` to `ApiError.Serialization`, whose message is "Malformed response" (`core/error/ApiError.kt:43`).
- `domain/interactor/player/PlayerInteractor.kt:273`: `saveWatchingTime` = `api.setWatchingTime(...).getOrThrow()`.
- `ui/feature/player/vm/PlayerVM.kt:1898`, `:1927`, `:1931`: in the drain loop, only a successful save sets `detailsStaleOwed` and calls `markContentChanged(PlaybackProgress)`. A failure goes to `PlayerProgressDiagnostics.reportSaveFailure()` (`PlayerProgressDiagnostics.kt:12`), which logs a fixed string and drops the cause.
- `PlayerVM.kt:1980`: `flushOwedInvalidation` marks the details stale and refreshes TV home at exit or background, but only if a save succeeded.
- Resume path: `PlayerVM.kt:333` → `ItemDetailsRepository.getItemDetails` (cached, `ContentCacheRepository.kt:197`, TTL `CacheTtl.ItemDetails` = 10 min at `CacheKeys.kt:51`). Then `resolveMedia` takes `episode/video.watching.time` (`PlayerInteractor.kt:101`, `:125`), and `buildResumeDialog` (`PlayerVM.kt:366`) builds no dialog when the time is 0.
- `Models.kt:389`: `WatchlistToggleResponse(status, watching: Boolean)` matches the documented `togglewatchlist` answer. `WatchingToggleResponse` requires only `status`, which the docs include.
- Tests: `PlayerInteractorTest.kt:650`, `:674` mock the API and never decode a real body. `KinoPubApiClientFailureTest.kt:112-159` already runs a local HTTP server with an injected `mainApiBaseUrl`, which can be reused for a decode test of the real body. `ApiRetryPolicyTest.kt:32` covers marktime as non-retryable.

## Related work

| Item | Relevance |
|---|---|
| `88cdf071` Release 1.0.0 (2026-06-28) | Added `WatchingStatus` and `setWatchingTime`. `saveWatchingTime` ignored the `Result`, so the decode failure was silent. |
| `1b7646af` Release 1.5.0 (2026-07-24) | Added `.getOrThrow()` plus `itemDetailsRepository.invalidate(id)` after it. From here every save throws and the cache is never invalidated. This is the start of the visible breakage. |
| `03c2ca31` Release 1.7.1 (2026-08-14) | Changed invalidate to `markStale`, still after `getOrThrow()`. |
| `a1a51cca` (2026-09-05) | Added `asApiFailure`, which gives the failure its `ApiError.Serialization` name. It did not cause the failure. |
| `b0b59a15` (2026-11-22, on master) | cache-targeted-invalidation: per-save mark moved to a mark owed at exit or background. It is still gated on a successful save. |
| run `code-quality-audit` (C7) | Original symptom, `docs/superpowers/evidence/2026-10-12-code-quality-audit-device.md:34`. |
| run `dependency-update-stable` | "Playback progress save failed" twice per playback, on base too (`docs/superpowers/evidence/2026-11-12-dependency-update-stable-device.md:12`). |
| run `cache-targeted-invalidation` (done) | Reproduced the failure on emulator and box. Measured with a Serialization-ignore shim (`.flow/runs/cache-targeted-invalidation/baseline-measure.patch`). AC-6 NOT CHECKED because of this bug. Its spec names this run as a merge-overlap risk on `PlayerVM.kt`. |
| `docs/superpowers/plans/2026-08-13-persistent-content-cache.md:1412` | Plan mocked `setWatchingTime` as `Result.success(Unit)`, so the model was never checked. |

Duplicate: none.

## External findings

| Finding | Source |
|---|---|
| `GET /v1/watching/marktime` (id, video, time, season; season is required for series, otherwise 404). Example response: HTTP 200, JSON `status => 200`, with no other field. | https://kinoapi.com/api_watching.html (API 1.3 docs, undated, read 2026-11-22) |
| `togglewatchlist` returns `status`, `watching: bool`. `toggle` returns `status`, `watched`. | same page |
| A logged real answer from another client: `{u'status': 200}` for a marktime call. | https://github.com/quarckster/kodi.kino.pub/issues/8 (comment 2018-10-19) |
| The changelog has no marktime entry and no change to the response shape. | https://kinoapi.com/changelog.html |

The actual body on the owner's accounts was not observed. The shape is inferred from the docs and the 2018 log, both of which match the decode failure. That the failing exception is a `MissingFieldException` for `id` is `unverified`.

## Options

| # | Option | Trade-offs | Size |
|---|---|---|---|
| A | Change `setWatchingTime` to return a marktime-specific model (`MarkTimeResponse(status: Int? = null)`) or `Result<Unit>`. Delete `WatchingStatus`. Add a client test that serves `{"status":200}` from the existing local-server fixture. Have `reportSaveFailure` log the error class (no message or URL). | Fixes the root cause. Exit marking and the TV home refresh start to run, as `b0b59a15` designed. Small. Whether Ktor decodes `Unit` from a JSON body needs checking (`unverified`), so the model is safer. | S |
| B | Keep the model but give every `WatchingStatus` field a default. | One-line change, but it keeps a model that pretends the API returns fields it does not return. | S |
| C | In `apiCall`, treat a 2xx with an undecodable body as success for `MUTATING_GET_PATHS`. | Generic, but it hides future real shape errors and changes behaviour for `toggle` and `togglewatchlist`, whose callers read the body. | M |

Recommended: A.

## Risks

- After the fix, every playback session marks details stale and refreshes TV home at exit, which has never happened in production since 1.5.0. Exit costs are already measured with the shim on the box (`docs/superpowers/evidence/2026-11-22-cache-targeted-invalidation-verify-box.md`, AC-10).
- Whether the emulator account's item payload carries per-episode `watching.time` at all is unresolved. cache-targeted-invalidation qa round 1 said it does not, but its baseline saw a resume dialog after reopening. If the payload lacks it, resume can still fail after the fix. Device verification has to reopen within 10 min and check the resume dialog and the Details progress.
- Merge overlap on `PlayerVM.kt` save path with `player-vm-split` (cache-targeted-invalidation spec, R13).

## Open questions

| ID | Question | Recommended answer |
|---|---|---|
| Q1 | Return `Result<Unit>` or a small `MarkTimeResponse`? | `MarkTimeResponse(status: Int? = null)`: it needs no Ktor `Unit` behaviour and survives an empty `{}`. |
| Q2 | Should `PlayerProgressDiagnostics` carry the cause type? | Yes, the class name only. That is what hid this bug for months, and the class name holds no secret. |
| Q3 | Device check on the box (reopen within 10 min, resume dialog plus Details progress)? | Yes. It closes AC-6 of cache-targeted-invalidation too. |

## Sources

- Files: `KinoPubApiClient.kt`, `ApiFailures.kt`, `ApiError.kt`, `Models.kt`, `PlayerInteractor.kt`, `PlayerVM.kt`, `PlayerProgressDiagnostics.kt`, `ItemDetailsRepository.kt`, `ContentCacheRepository.kt`, `CacheKeys.kt`, `ApiRetryPolicy.kt`, `KinoPubApiClientFailureTest.kt`, `docs/architecture/api.md`.
- Commits: `88cdf071`, `1b7646af`, `03c2ca31`, `d051c873`, `a1a51cca`, `3c2a2002`, `b0b59a15`, `ecb0f990`.
- Runs: `code-quality-audit`, `dependency-update-stable`, `cache-targeted-invalidation` (state, baseline, qa-report, spec).
- URLs: https://kinoapi.com/api_watching.html, https://kinoapi.com/changelog.html, https://github.com/quarckster/kodi.kino.pub/issues/8
