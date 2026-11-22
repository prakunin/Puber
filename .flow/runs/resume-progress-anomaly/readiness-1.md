# Readiness 1: resume-progress-anomaly

Verdict: **READY**

Base 2fd851a4. Read: spec, plan, `test-design.md`, `state.md` (Q1-Q3 answered). All citations checked in the worktree at base.

## AC coverage

| AC | Plan task | Test design row |
|---|---|---|
| AC-1 | 1 | `aMarkTimeAnswerWithOnlyAStatusIsASuccessAndCarriesThePosition` (P0 red) |
| AC-2 | 1 | `anEmptyMarkTimeAnswerIsASuccess` (P0 red), `aMarkTimeAnswerWithExtraFieldsIsASuccess` (P1) |
| AC-3 | 1 | `aMarkTimeServerErrorIsAFailureAndIsSentOnce`, `aFailedMarkTimeIsIssuedOnceBecauseRepeatingItWouldMoveThePositionAgain` (P0 guard) |
| AC-4 | 2 | `failedFinalProgressSave_emitsIdentityFreeDiagnostic` (P0 red) |
| AC-5, AC-6, AC-7 | Hand-off (qa-device) | Device steps 1-3 (P0) |
| AC-8 | 1, 2, full gate | `PlayerVMInvalidationTest`, `PlayerInteractorTest` rows, check.full |

## Citations sampled

| Citation | Resolves to |
|---|---|
| `KinoPubApiClient.kt:55` | `import ...models.WatchingStatus` |
| `KinoPubApiClient.kt:455-464` | `setWatchingTime`, `Result<WatchingStatus>` at :457, request block :458-463 |
| `KinoPubApiClient.kt:412` | `clearItemHistory(id): Result<Unit> = apiCall` |
| `KinoPubApiClient.kt:1023-1038` | `apiCall`, `response.body<T>()` at :1032 |
| `Models.kt:372-379` | `WatchingStatus`, `id` and `status` without default |
| `PlayerProgressDiagnostics.kt:5`, `:7-10`, `:12-14` | constant, KDoc, `reportSaveFailure()` |
| `PlayerVM.kt:1927-1933` | success branch, `CancellationException` rethrow, `catch (_: Throwable)` |
| `PlayerVM.kt:333`, `:366`, `:1980-1988` | `getItemDetails`, `buildResumeDialog(resolved.watchingTime, ...)`, `flushOwedInvalidation` |
| `PlayerInteractor.kt:273-275` | `saveWatchingTime ... .getOrThrow()` |
| `ApiRetryPolicy.kt:21` | `"watching/marktime"` |
| `ApiError.kt:27`, `:43` | `Http(statusCode, ...)`, `Serialization` "Malformed response" |
| `KinoPubApiClientFailureTest.kt:112-139`, `:141-157`, `:159-164` | `client`, `withServer`, `respond` |
| `PlayerInteractorTest.kt:12`, `:650-653` | `WatchingStatus` import, success stub |
| `PlayerVMPlaybackFlowTest.kt:172`, `:183-211`, `:209` | both named tests; `failure.message` assert |
| `PlayerVMTestFixture.kt:49`, `ApiRetryPolicyTest.kt:32`, `detekt.yml:260` | `CollectingLogTree`, the marktime retry test, `SwallowedException` |

`rg -w WatchingStatus app/src` at base: only the four sites the plan deletes or edits.

## Build and order

| Task | Green at commit |
|---|---|
| 1 | Model, both imports and the `PlayerInteractorTest` stub change in one commit; no other `WatchingStatus` user |
| 2 | Sole caller `PlayerVM.kt:1932` changes with the signature; named `error` used, so `SwallowedException` (`allowedExceptionNameRegex` `_|(ignore|expected).*`) does not fire |

Task 2 does not depend on Task 1; the order follows the failing-first test.

## Scope

Plan Global constraints list Out of scope (options B and C, `PlayerInteractor`, `ApiRetryPolicy`, per-episode `watching.time`, UI, DI); no task crosses it. No blocking question is open.

## Notes for the gate

1. The device P0 row (steps 1-3) carries no `red`/`guard` marker; it is manual by nature, so the gate reads it from the box evidence.
2. AC-5 and AC-7 "Details shows the saved progress" may end as "not capturable" (Test design risk 3). The resume dialog substitute reads the same cached details (`PlayerVM.kt:333`); the gate carries the Details part as `NOT CHECKED` if the evidence records a white frame.
3. The spec has no Out of scope section of its own; the plan's list is the boundary the gate holds the diff to.
