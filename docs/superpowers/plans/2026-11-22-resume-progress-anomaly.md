# Marktime decode fix - plan

**Spec:** `docs/superpowers/specs/2026-11-22-resume-progress-anomaly.md`
**Goal:** Let `watching/marktime` succeed on any 2xx body by returning `Result<Unit>`, and make the progress-save failure log name the error class without any viewing identity.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Work in `/home/user1/orca/workspaces/Puber/resume-progress-anomaly` (branch `prakunin/resume-progress-anomaly`, base 2fd851a4). Never `./gradlew` there; every Gradle call is prefixed `AGENT_GRADLE_SERIAL=1`.
- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`; full gate: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`.
- Touched tests: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests '<fully.qualified.Class>'`.
- One task = green fast gate + green tests of the touched code + one commit, message one imperative English sentence, no prefixes, never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- Paths: `M/` = `app/src/main/java/com/kino/puber/`, `T/` = `app/src/test/kotlin/com/kino/puber/`. Line numbers are at base 2fd851a4.
- Out of scope: research options B and C (defaults on `WatchingStatus`, lenient decode in `apiCall`), `PlayerInteractor` (`M/domain/interactor/player/PlayerInteractor.kt:273-275` stays `.getOrThrow()`), `ApiRetryPolicy` (`M/data/api/ApiRetryPolicy.kt:21` keeps `watching/marktime`), per-episode `watching.time` (spec Design, Risks 1: an episode-only failure becomes a new run), UI, DI.

## AC coverage

| AC | Task | Tests (spec Test design) |
|---|---|---|
| AC-1, AC-2, AC-3 | 1 | `KinoPubApiClientFailureTest`: 4 new; `ApiRetryPolicyTest` unchanged |
| AC-4 | 2 | `PlayerVMPlaybackFlowTest`: 1 changed, 1 kept |
| AC-8 | 1, 2, full gate | `PlayerInteractorTest` stub changed; `PlayerVMInvalidationTest` unchanged |
| AC-5, AC-6, AC-7 | Hand-off (qa-device) | Spec Test design, Device checks 1-3 |

---

## Tasks

### Task 1: Return `Result<Unit>` from `setWatchingTime` and delete `WatchingStatus`

**Covers:** AC-1, AC-2, AC-3, AC-8
**Files:**
- Test: `T/data/api/KinoPubApiClientFailureTest.kt` - four new tests on the existing fixtures `client(cacheDir, baseUrl)` (`:112-139`), `withServer(path, handler, block)` (`:141-157`), `HttpExchange.respond(status, body)` (`:159-164`); server path `/v1/watching/marktime`. Add `import java.util.concurrent.atomic.AtomicInteger`.
- Modify: `M/data/api/KinoPubApiClient.kt` - `setWatchingTime` (`:455-464`): return type `Result<WatchingStatus>` (`:457`) becomes `Result<Unit>`; request block `:458-463` untouched; delete import `:55`. Decoding goes through `apiCall` (`:1023-1038`), as for `clearItemHistory` (`:412`).
- Modify: `M/data/api/models/Models.kt` - delete `@Serializable data class WatchingStatus` (`:372-379`).
- Modify: `T/domain/interactor/player/PlayerInteractorTest.kt` - in `saveWatchingTime_success_doesNotMarkItemDetailsStale` (`:650`) the stub at `:651-653` returns `Result.success(Unit)`; delete import `:12`.

**Consumes:** `ApiError.Http` (`M/core/error/ApiError.kt:27`) with `statusCode`.
**Produces:** `suspend fun setWatchingTime(id: Int, videoNumber: Int, time: Int, season: Int? = null): Result<Unit>`.

New tests (all `@TempDir cacheDir: Path`, `runTest`, call `client(cacheDir, baseUrl).setWatchingTime(42, 1, 120, 2)`):

| Test | Server answer | Asserts |
|---|---|---|
| `aMarkTimeAnswerWithOnlyAStatusIsASuccessAndCarriesThePosition` | 200, `{"status":200}`; handler stores `exchange.requestURI.rawQuery` in an `AtomicReference<String>` | `isSuccess` (message `exceptionOrNull()?.stackTraceToString()`); the query split on `&` and `=` into a map equals `mapOf("id" to "42", "video" to "1", "time" to "120", "season" to "2")` |
| `anEmptyMarkTimeAnswerIsASuccess` | 200, `{}` | `isSuccess` |
| `aMarkTimeAnswerWithExtraFieldsIsASuccess` | 200, `{"status":200,"id":42,"time":120}` | `isSuccess` |
| `aMarkTimeServerErrorIsAFailureAndIsSentOnce` | 500, `{"error":"x"}`; handler increments an `AtomicInteger` | `exceptionOrNull()` is `ApiError.Http` with `statusCode` 500; counter is 1 |

- [x] Write the four tests first; run `KinoPubApiClientFailureTest` on the unchanged code and record the red run (the first two fail with `ApiError.Serialization`; the other two pass)
- [x] Change `setWatchingTime`, delete `WatchingStatus` and both imports, change the `PlayerInteractorTest` stub
- [x] `rg -w WatchingStatus app/src` prints nothing
- [x] Run the fast gate and `com.kino.puber.data.api.KinoPubApiClientFailureTest`, `com.kino.puber.data.api.ApiRetryPolicyTest`, `com.kino.puber.domain.interactor.player.PlayerInteractorTest`; commit

**Done when:** the three suites are green and the fast gate passes on the commit.

### Task 2: Name the error class in the progress-save failure log

**Covers:** AC-4, AC-8
**Files:**
- Test: `T/ui/feature/player/vm/PlayerVMPlaybackFlowTest.kt` - `failedFinalProgressSave_emitsIdentityFreeDiagnostic` (`:183-211`): build `val cause = IllegalStateException("Failed to save $privateTitle for item=$privateItemId at time=$privateTime")` and throw `ApiError.Serialization(cause)` from the stub (`:191`); keep `entryCount == 1` and the id, title and time asserts; replace the `failure.message` assert (`:209`) with `assertFalse(output.contains(cause.message.orEmpty()))` and `assertFalse(output.contains("Malformed response"))`; add `assertTrue(output.contains("Serialization"), output)`. Add `import com.kino.puber.core.error.ApiError`. `failedFinalProgressSave_returnsEmptyChanges` (`:172`) stays as is.
- Modify: `M/ui/feature/player/vm/PlayerProgressDiagnostics.kt` - `reportSaveFailure()` (`:12-14`) becomes `fun reportSaveFailure(errorType: KClass<out Throwable>)` logging `"$PROGRESS_SAVE_FAILURE_DIAGNOSTIC: ${errorType.simpleName ?: "unknown"}"`; KDoc (`:7-10`) says it accepts only the failure's type, never a throwable, message or media parameter; `import kotlin.reflect.KClass`. `PROGRESS_SAVE_FAILURE_DIAGNOSTIC` (`:5`) keeps its value.
- Modify: `M/ui/feature/player/vm/PlayerVM.kt` - `catch (_: Throwable)` at `:1931-1932` becomes `catch (error: Throwable) { PlayerProgressDiagnostics.reportSaveFailure(error::class) }`; the `CancellationException` rethrow at `:1929-1930` and the success branch `:1927-1928` stay.

**Consumes:** `ApiError.Serialization(cause: Throwable?)` (`M/core/error/ApiError.kt:43`, message "Malformed response"); `CollectingLogTree` (`T/ui/feature/player/vm/PlayerVMTestFixture.kt:49`), whose `output()` includes any logged throwable's stack trace.
**Produces:** `PlayerProgressDiagnostics.reportSaveFailure(errorType: KClass<out Throwable>)`, sole caller `PlayerVM.kt:1932`.

- [x] Change the test first; run `PlayerVMPlaybackFlowTest` on the unchanged code and record the red run (`Serialization` missing)
- [x] Change `PlayerProgressDiagnostics` and the `PlayerVM` catch
- [x] Run the fast gate and `com.kino.puber.ui.feature.player.vm.PlayerVMPlaybackFlowTest`, `com.kino.puber.ui.feature.player.vm.PlayerVMInvalidationTest`; then the full gate (detekt `SwallowedException`, `config/detekt/detekt.yml:260`, sees the now-named `error`); commit

**Done when:** both suites and the full gate are green on the commit.

### Hand-off: device checks (qa-device)

**Covers:** AC-5, AC-6, AC-7
Spec Test design, Device checks 1-3, on the TV box `192.168.1.110:5555`, package `com.kino.puber.stage`, with the APK built from this worktree after Task 2. Evidence: `docs/superpowers/evidence/2026-11-22-resume-progress-anomaly-box.md`, audited with `tools/mobile-evidence-audit.sh`.

**Done when:** the evidence records grep count 0 and the resume dialog for steps 1-3; an episode-only failure in step 2 is recorded as a new run.

---

## Implementation record

<!-- owner: implementer -->

**Commits:**

```
76db652b Name the error class in the playback progress save failure log
83bb0b6c Let the marktime answer decode on any 2xx body by returning Result<Unit> from setWatchingTime
9fa9d2bc Paste the READY verdict into the marktime decode fix plan
0995cebc Add the marktime decode fix plan with its READY verdict
08740a15 Add the spec of the marktime decode fix
```

**Red run** (P0 `red` tests on the unchanged code):

- red: aMarkTimeAnswerWithOnlyAStatusIsASuccessAndCarriesThePosition - isSuccess assert failed (KinoPubApiClientFailureTest.kt:114)
- red: anEmptyMarkTimeAnswerIsASuccess - isSuccess assert failed (:134)
- green on base (guards): aMarkTimeAnswerWithExtraFieldsIsASuccess, aMarkTimeServerErrorIsAFailureAndIsSentOnce
- red: failedFinalProgressSave_emitsIdentityFreeDiagnostic - `Serialization` missing from the log output

**Deviations from the plan:** none

**Files touched outside the plan:** none

**For the reviewers:** the log line is now `Playback progress save failed: <SimpleName>`; check.full green (tests, detekt, lint). Device checks AC-5 to AC-7 are open for qa-device.

**Blocked:** none

### Design delta 2 (qa round 1 defect 1: Details ignores the saved position)

Commit: see the fix commit "Read the Details resume line and episode bar length from the media when the position carries none". Design: `design-architect-2.md`. The spec edit went in as its own docs commit.

**Red run** (`DetailsScreenUIMapperTest`, unchanged mapper, all fail on the assertion with an expected/actual string or float mismatch):

- red: a started movie whose position carries no length shows the time left from the video length - "Not started" line instead of the time left
- red: a movie reads its first video's position before an item-level one - same, item-level `WatchingInfo()` wins on base
- red: a movie whose video carries no position reads the item-level one - same, no length without `watching.duration`
- red: a started episode whose position carries no length shows the time left from the episode length - "Up next" line without time
- red: an episode card fills its bar from the episode length when the position carries none - null on base
- green on base (guard): a movie with no position says not started with its length

**Deviations:** `progressFraction` returns null at `time <= 0` (base gave 0f when `watching.duration > 0`); the bar is empty either way.

**Files outside the plan:** none (`Models.kt` KDoc only, as the design says).

**For the reviewers:** movie position now reads `videos.first().watching` before `item.watching` (design-2 risk 1). Card progress readers of `watching.duration` are out of this run (`card-progress-watching-duration`). check.fast, the details and mapper suites and check.full (tests, detekt, lint) are green. No device work done; qa round 2 re-runs device steps 1-3.

## Readiness

<!-- owner: final-judge, mode readiness; the conductor pastes readiness-<n>.md here. READY | NOT READY and why. -->

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
