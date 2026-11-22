# Design: resume-progress-anomaly (architect)

Base 2fd851a4. Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/`. Root cause and ACs: `brief.md`.

## Decisions

| Question | Decision | Why |
|---|---|---|
| Return type of `setWatchingTime` | `Result<Unit>` through the existing `apiCall` (Q1) | Same shape as `clearItemHistory` (`data/api/KinoPubApiClient.kt:412`) and `addBookmarkItem` (`:509`); the only caller discards the value (`domain/interactor/player/PlayerInteractor.kt:274`). |
| Fate of `WatchingStatus` | Delete it (`data/api/models/Models.kt:372-379`) and its imports (`KinoPubApiClient.kt:55`, `app/src/test/kotlin/com/kino/puber/domain/interactor/player/PlayerInteractorTest.kt:12`) | No other reference in `app/src` (grep). |
| What the diagnostic receives | `reportSaveFailure(errorType: KClass<out Throwable>)`; logs `"$PROGRESS_SAVE_FAILURE_DIAGNOSTIC: ${errorType.simpleName ?: "unknown"}"` (Q2) | A `KClass` cannot carry a message, cause or URL, so the boundary keeps the guarantee its KDoc states (`ui/feature/player/vm/PlayerProgressDiagnostics.kt:7-10`); passing a `Throwable` or a free `String` would not. |
| Call site | `catch (error: Throwable) { PlayerProgressDiagnostics.reportSaveFailure(error::class) }` at `ui/feature/player/vm/PlayerVM.kt:1931-1932` | One-line change; the `CancellationException` rethrow above it stays. |
| `PlayerInteractor.saveWatchingTime` | Unchanged: `.getOrThrow()` on `Result<Unit>` | Failure semantics (non-2xx, offline) stay as they are; only a 2xx stops being a failure. |
| Retry policy | Unchanged: `watching/marktime` stays in the non-retried list (`data/api/ApiRetryPolicy.kt:21`) | AC-3. |

## Changes by layer

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

## Alternatives rejected

- `MarkTimeResponse(status: Int? = null)`: a model no caller reads; Q1 chose `Result<Unit>`.
- Defaults on every `WatchingStatus` field (research option B): keeps a model of fields the API does not send.
- Treat undecodable 2xx as success in `apiCall` for mutating paths (option C): hides real shape errors for `toggle` and `togglewatchlist`, whose callers read the body.
- `reportSaveFailure(error: Throwable)` or `(name: String)`: reopens the path for messages carrying item id or time into logs.

## Risks

| # | Risk | Area | Lives in |
|---|---|---|---|
| 1 | Per-episode `watching.time` may be absent from the item payload, so AC-5 to AC-7 can still fail for an episode after the fix (`.flow/runs/cache-targeted-invalidation/review-codex-1.md:4`). Verify a movie and an episode (Q3); an episode-only failure becomes a new run. | correctness | `domain/interactor/player/PlayerInteractor.kt:101`, `:125` |
| 2 | Every session now marks details stale and refreshes TV home at exit and background for the first time since 1.5.0; exit latency grows by one cache write and a refresh request. Measured with the shim (`docs/superpowers/evidence/2026-11-22-cache-targeted-invalidation-verify-box.md`, AC-10). | performance, rollout | `PlayerVM.kt:1980-1988`, `:2030-2035` |
| 3 | `Result<Unit>` depends on Ktor 3.6.1 plus `ContentNegotiation` (`KinoPubApiClient.kt:176`, Json `ignoreUnknownKeys` `:132`) accepting any JSON body as `Unit`. AC-1/AC-2 run the real client against a local server and settle it. | data shape | `KinoPubApiClient.kt:1023-1038` |
| 4 | In minified `release` (`app/build.gradle.kts:187`) `simpleName` may be an obfuscated name; the `dev` stage build is not minified. Diagnostic value only. | rollout | `PlayerProgressDiagnostics.kt` |
| 5 | Merge overlap on the save path in `PlayerVM.kt` with `player-vm-split` (stage idea). | rollout | `PlayerVM.kt:1898-1940` |

## Open questions

N/A: Q1 to Q3 answered in `state.md`.
